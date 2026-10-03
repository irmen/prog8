package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.IErrorReporter
import prog8.compiler.CallGraph


// m68k stack-frame rule (see ideas/m68k-stack-memory-model.md section 10): the address of a
// subroutine-local variable points into the current activation's frame and dangles once the
// subroutine returns. v1 only warns, best-effort: a frame address that is stored to memory
// outside the subroutine, passed to a call, returned from the subroutine, or mentioned in inline
// assembly is flagged. The check is deliberately conservative: it does not track pointer values
// flowing through intermediate locals (so transitive escapes are missed), and it can warn for
// cases that are safe today, because address-taken locals currently keep static storage due to
// the conservative frame-layout classifier.
internal fun checkFrameAddressEscapes(program: Program, errors: IErrorReporter) {
    val checker = FrameAddressEscapeChecker(errors)
    checker.visit(program)
    checker.checkInlineAssemblyMentions()
}

private sealed interface EscapeContext
private data object NoEscape : EscapeContext
private data class StoreEscape(val storageName: String?) : EscapeContext
private data object CallEscape : EscapeContext
private data object ReturnEscape : EscapeContext

private class FrameAddressEscapeChecker(private val errors: IErrorReporter) : IAstVisitor {
    private var currentSubroutine: Subroutine? = null
    private val warnedLocals = mutableSetOf<VarDecl>()
    private val addressTakenLocals = mutableMapOf<Subroutine, MutableSet<VarDecl>>()
    private val asmMentionedNames = mutableMapOf<Subroutine, MutableSet<String>>()

    override fun visit(subroutine: Subroutine) {
        val previous = currentSubroutine
        currentSubroutine = subroutine
        subroutine.asmAddress?.varbank?.accept(this)
        subroutine.statements.forEach { it.accept(this) }
        currentSubroutine = previous
    }

    override fun visit(addressOf: AddressOf) {
        val identifier = addressOf.identifier ?: return
        val decl = identifier.targetVarDecl() ?: return
        val declaringSub = decl.definingSubroutine
        if (declaringSub == null || !isSubroutineLocal(decl, declaringSub))
            return
        if (decl !in warnedLocals) {
            when (val escape = escapeContext(addressOf)) {
                is NoEscape -> {}
                is StoreEscape -> {
                    warnedLocals.add(decl)
                    val storage = escape.storageName?.let { " in '$it'" } ?: " in a location outside the subroutine"
                    errors.warn("the address of local '${decl.name}' in subroutine '${declaringSub.name}' is stored$storage; a stack frame address is only valid while the subroutine is running", addressOf.position)
                }
                is CallEscape -> {
                    warnedLocals.add(decl)
                    errors.warn("the address of local '${decl.name}' in subroutine '${declaringSub.name}' escapes the subroutine (passed to a call); a stack frame address is only valid while the subroutine is running", addressOf.position)
                }
                is ReturnEscape -> {
                    warnedLocals.add(decl)
                    errors.warn("the address of local '${decl.name}' in subroutine '${declaringSub.name}' escapes the subroutine (returned); a stack frame address is only valid while the subroutine is running", addressOf.position)
                }
            }
        }
        addressTakenLocals.getOrPut(declaringSub) { mutableSetOf() }.add(decl)
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        currentSubroutine?.let { sub ->
            asmMentionedNames.getOrPut(sub) { mutableSetOf() }.addAll(inlineAssembly.names)
        }
    }

    // whether an address-taken local is also mentioned in inline assembly can only be decided
    // after the whole program was walked, so the check runs after the visit pass
    fun checkInlineAssemblyMentions() {
        for ((subroutine, locals) in addressTakenLocals) {
            val asmNames = asmMentionedNames[subroutine] ?: continue
            for (decl in locals) {
                if (decl in warnedLocals)
                    continue
                val shortName = "p8v_${decl.name}"
                if (decl.name in asmNames || asmNames.any { it == shortName || it.endsWith(".$shortName") }) {
                    warnedLocals.add(decl)
                    errors.warn("the address of local '${decl.name}' in subroutine '${subroutine.name}' is referenced from inline assembly; a stack frame address is only valid while the subroutine is running", decl.position)
                }
            }
        }
    }

    // walk up from the address-of expression to the statement that consumes the pointer value;
    // the first relevant context decides whether the frame address escapes the subroutine
    private fun escapeContext(addressOf: AddressOf): EscapeContext {
        var node: Node = addressOf
        while (true) {
            when (val parent = node.parent) {
                is FunctionCallExpression, is FunctionCallStatement -> return CallEscape
                is Return -> return ReturnEscape
                is Assignment -> {
                    val target = storeTarget(parent.target)
                    return if (target.escaping) StoreEscape(target.name) else NoEscape
                }
                is VarDecl -> return if (isSubroutineLocal(parent, parent.definingSubroutine)) NoEscape else StoreEscape(null)
                is Statement -> return NoEscape
                else -> node = parent
            }
        }
    }

    private fun storeTarget(target: AssignTarget): StoreTarget {
        if (target.void)
            return StoreTarget(false, null)
        target.multi?.let { multi ->
            for (t in multi) {
                val st = storeTarget(t)
                if (st.escaping)
                    return st
            }
            return StoreTarget(false, null)
        }
        val identifier = target.identifier
        if (identifier != null) {
            val decl = identifier.targetVarDecl()
            if (isSubroutineLocal(decl, currentSubroutine))
                return StoreTarget(false, null)
            return StoreTarget(true, identifier.nameInSource.last())
        }
        val arrayindexed = target.arrayindexed
        if (arrayindexed != null) {
            val baseDecl = arrayindexed.plainarrayvar?.targetVarDecl()
            if (isSubroutineLocal(baseDecl, currentSubroutine))
                return StoreTarget(false, null)
            return StoreTarget(true, arrayindexed.plainarrayvar?.nameInSource?.last())
        }
        // pointer dereference, struct field or fixed-memory targets: not a same-subroutine local,
        // so conservatively treat a frame address stored there as escaping
        return StoreTarget(true, null)
    }

    private fun isSubroutineLocal(decl: VarDecl?, subroutine: Subroutine?): Boolean {
        if (decl == null || subroutine == null)
            return false
        if (decl.type != VarDeclType.VAR)
            return false
        if (decl.sharedWithAsm)      // @shared variables keep static storage
            return false
        return decl.definingSubroutine === subroutine
    }
}

private data class StoreTarget(val escaping: Boolean, val name: String?)


// m68k stack-frame rule (see ideas/m68k-stack-memory-model.md sections 3 and 17.7): the virtual
// registers of re-entrant subroutines live in stack frames, so raw inline assembly that pokes the
// program-static p8_regfile / p8_fregfile blocks would read or write the wrong storage.
internal fun checkRegfileAsmInRecursivePrograms(program: Program, callGraph: CallGraph, errors: IErrorReporter) {
    if (callGraph.calls.keys.none { callGraph.hasRecursionCycle(it) })
        return
    val checker = object : IAstVisitor {
        override fun visit(inlineAssembly: InlineAssembly) {
            val text = inlineAssembly.assembly
            val regfileName = when {
                "p8_regfile" in text -> "p8_regfile"
                "p8_fregfile" in text -> "p8_fregfile"
                else -> return
            }
            errors.warn("inline assembly uses $regfileName but the program contains recursive subroutines whose registers now live in stack frames; this code will not do what it expects", inlineAssembly.position)
        }
    }
    checker.visit(program)
}
