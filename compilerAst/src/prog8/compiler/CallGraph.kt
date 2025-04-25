package prog8.compiler

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.IErrorReporter


class CallGraph(private val program: Program) : IAstVisitor {

    val imports = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val importedBy = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val calls = mutableMapOf<Subroutine, Set<Subroutine>>().withDefault { setOf() }
    val calledBy = mutableMapOf<Subroutine, Set<Node>>().withDefault { setOf() }
    val notCalledButReferenced = mutableSetOf<Subroutine>()
    private val allIdentifiersAndTargets = mutableListOf<Pair<IdentifierReference, Statement>>()
    private val allAssemblyNodes = mutableListOf<InlineAssembly>()

    init {
        visit(program)
    }

    val allIdentifiers: List<Pair<IdentifierReference, Statement>> = allIdentifiersAndTargets

    private val usedSubroutines: Set<Subroutine> by lazy {
        calledBy.keys + program.entrypoint + notCalledButReferenced
    }

    private val usedBlocks: Set<Block> by lazy {
        val blocksFromLibraries = program.allBlocks.filter { it.isInLibrary }
        val used = mutableSetOf<Block>()

        allIdentifiersAndTargets.forEach {
            val target = it.second.definingBlock
            used.add(target)
        }

        // warning: it's possible that we still have a faulty program without
        // a proper main.start entrypoint, so be cautiuous about it.
        val main = program.allBlocks.firstOrNull { it.name=="main" }
        if(main?.subScope("start") != null)
            used + blocksFromLibraries + main
        else
            used + blocksFromLibraries
    }

    private val usedModules: Set<Module> by lazy {
        usedBlocks.map { it.definingModule }.toSet()
    }

    override fun visit(directive: Directive) {
        val thisModule = directive.definingModule
        if (directive.directive == "%import") {
            val importedModule = program.modules.singleOrNull { it.name == directive.args[0].string }     // the module may no longer exist at all due to optimizations
            if(importedModule!=null) {
                imports[thisModule] = imports.getValue(thisModule) + importedModule
                importedBy[importedModule] = importedBy.getValue(importedModule) + thisModule
            }
        }
        else if (directive.directive == "%jmptable") {
            for(arg in directive.args) {
                val scopedName = arg.string!!.split('.')
                val target = directive.definingScope.lookup(scopedName)
                if(target is Subroutine) {
                    val identifier = IdentifierReference(scopedName, arg.position)
                    identifier.linkParents(directive)
                    visit(identifier)
                }
            }
        }

        super.visit(directive)
    }

    override fun visit(functionCallExpr: FunctionCallExpression) {
        val otherSub = functionCallExpr.target.targetSubroutine()
        if (otherSub != null) {
            val definingSub = functionCallExpr.definingSubroutine
            if(definingSub!=null) {
                calls[definingSub] = calls.getValue(definingSub) + otherSub
            }
            calledBy[otherSub] = calledBy.getValue(otherSub) + functionCallExpr
        }
        super.visit(functionCallExpr)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val otherSub = functionCallStatement.target.targetSubroutine()
        if (otherSub != null) {
            functionCallStatement.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + functionCallStatement
            }
        }
        super.visit(functionCallStatement)
    }

    override fun visit(addressOf: AddressOf) {
        addressOf.identifier.targetSubroutine()?.let { notCalledButReferenced.add(it) }
        super.visit(addressOf)
    }

    override fun visit(jump: Jump) {
        val otherSub = (jump.target as? IdentifierReference)?.targetSubroutine()
        if (otherSub != null) {
            jump.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + jump
            }
        }
        super.visit(jump)
    }

    override fun visit(identifier: IdentifierReference) {
        val target = identifier.targetStatement(program)
        if(target!=null) {
            allIdentifiersAndTargets.add(identifier to target)
            if(target is Subroutine)
                notCalledButReferenced += target
        }

        // if it's a scoped identifier, the subroutines in the name are also referenced!
        val scope = identifier.definingScope
        val name = ArrayDeque(identifier.nameInSource)
        while(name.size>1) {
            name.removeLast()
            val scopeTarget = scope.lookup(name)
            if(scopeTarget is Subroutine)
                notCalledButReferenced += scopeTarget
        }
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        allAssemblyNodes.add(inlineAssembly)
    }

    fun checkRecursiveCalls(errors: IErrorReporter) {
        val recursiveSubroutines = recursionCycles()
        if(recursiveSubroutines.any()) {
            errors.warn("Program contains recursive subroutines. These only works in very specific limited scenarios!", recursiveSubroutines.first().position)
            for(subroutine in recursiveSubroutines) {
                errors.warn("recursive subroutine '${subroutine.name}'", subroutine.position)
            }
        }
    }

    private fun recursionCycles(): Set<Subroutine> {
        val cycles = mutableSetOf<Subroutine>()

        for(caller in calls.keys) {
            if(hasRecursionCycle(caller))
                cycles.add(caller)
        }
        return cycles
    }

    private fun hasRecursionCycle(sub: Subroutine): Boolean {
        val callCloud = calls.getValue(sub).toMutableSet()
        var previousCloudSize = -1
        while(callCloud.size > previousCloudSize && sub !in callCloud) {
            previousCloudSize = callCloud.size
            for(element in callCloud.toList()) {
                callCloud.addAll(calls.getValue(element))
            }
        }
        return sub in callCloud
    }

    fun unused(module: Module) = module !in usedModules

    fun unused(sub: Subroutine): Boolean {
        if(sub in usedSubroutines)
            return false
        val asms = nameInAssemblyCode(sub.name, listOf("p8s_", ""))
        if(asms.isEmpty())
            return true
        val allUnused = asms.all { asm->
            // check that the asm block that calls us, is inside a subroutine that is unused or not
            val containingSub = asm.definingSubroutine
            if(containingSub != null && containingSub !== sub)
                unused(containingSub)
            else
                false
        }
        return allUnused
    }

    fun unused(block: Block): Boolean {
        return block !in usedBlocks && nameInAssemblyCode(block.name, listOf("p8b_", "")).isEmpty()
    }

    fun unused(decl: VarDecl): Boolean {
        // Don't check assembly just for occurrences of variables, if they're not used in prog8 itself, just kill them.
        // User should use @shared if they want to keep them.
        return usages(decl).isEmpty()
    }

    fun usages(decl: VarDecl): List<Node> {
        if(decl.type!=VarDeclType.VAR)
            return emptyList()

        if(decl.definingBlock !in usedBlocks)
            return emptyList()

        val assemblyBlocks = allAssemblyNodes.filter {
            decl.name in it.names || "p8v_" + decl.name in it.names
        }
        return allIdentifiersAndTargets.filter { decl===it.second }.map{ it.first } + assemblyBlocks
    }

    private val prefixes = listOf("p8b_", "p8v_", "p8s_", "p8c_", "p8l_", "p8_", "")
    private fun nameInAssemblyCode(name: String, knownAsmPrefixes: List<String> = emptyList()): List<InlineAssembly> {
        if(knownAsmPrefixes.isNotEmpty())
            return allAssemblyNodes.filter {
                knownAsmPrefixes.any { prefix -> prefix+name in it.names }
            }

        return allAssemblyNodes.filter {
            prefixes.any { prefix -> prefix+name in it.names }
        }
    }

    fun unused(stmt: INamedStatement): Boolean {
        return when(stmt) {
            is Subroutine -> unused(stmt)
            is Block -> unused(stmt)
            is VarDecl -> unused(stmt)
            is Label -> false   // just always output labels
            else -> false
        }
    }
}
