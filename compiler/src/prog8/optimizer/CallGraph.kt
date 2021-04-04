package prog8.optimizer

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.FunctionCall
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compiler.IErrorReporter
import java.nio.file.Path

private val asmJumpRx = Regex("""[\-+a-zA-Z0-9_ \t]+(jmp|jsr|bra)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)
private val asmRefRx = Regex("""[\-+a-zA-Z0-9_ \t]+(...)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)


class CallGraph(private val program: Program, private val asmFileLoader: (filename: String, source: Path)->String) : IAstVisitor {

    val imports = mutableMapOf<Module, List<Module>>().withDefault { mutableListOf() }
    val importedBy = mutableMapOf<Module, List<Module>>().withDefault { mutableListOf() }
    val calls = mutableMapOf<Subroutine, List<Subroutine>>().withDefault { mutableListOf() }
    val calledBy = mutableMapOf<Subroutine, List<Node>>().withDefault { mutableListOf() }

    init {
        visit(program)
    }

    override fun visit(program: Program) {
        super.visit(program)

        program.modules.forEach {
            it.importedBy.clear()
            it.imports.clear()

            it.importedBy.addAll(importedBy.getValue(it))
            it.imports.addAll(imports.getValue(it))
        }

        val rootmodule = program.modules.first()
        rootmodule.importedBy.add(rootmodule)       // don't discard root module
    }

    override fun visit(directive: Directive) {
        val thisModule = directive.definingModule()
        if (directive.directive == "%import") {
            val importedModule: Module = program.modules.single { it.name == directive.args[0].name }
            imports[thisModule] = imports.getValue(thisModule).plus(importedModule)
            importedBy[importedModule] = importedBy.getValue(importedModule).plus(thisModule)
        } else if (directive.directive == "%asminclude") {
            val asm = asmFileLoader(directive.args[0].str!!, thisModule.source)
            val scope = directive.definingSubroutine()
            if(scope!=null) {
                scanAssemblyCode(asm, directive, scope)
            }
        }

        super.visit(directive)
    }

    override fun visit(functionCall: FunctionCall) {
        val otherSub = functionCall.target.targetSubroutine(program)
        if (otherSub != null) {
            functionCall.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(functionCall)
            }
        }
        super.visit(functionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val otherSub = functionCallStatement.target.targetSubroutine(program)
        if (otherSub != null) {
            functionCallStatement.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(functionCallStatement)
            }
        }
        super.visit(functionCallStatement)
    }

    override fun visit(addressOf: AddressOf) {
        val otherSub = addressOf.identifier.targetSubroutine(program)
        if(otherSub!=null) {
            addressOf.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(thisSub)
            }
        }
        super.visit(addressOf)
    }

    override fun visit(jump: Jump) {
        val otherSub = jump.identifier?.targetSubroutine(program)
        if (otherSub != null) {
            jump.definingSubroutine()?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub).plus(otherSub)
                calledBy[otherSub] = calledBy.getValue(otherSub).plus(jump)
            }
        }
        super.visit(jump)
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        // parse inline asm for subroutine calls (jmp, jsr, bra)
        val scope = inlineAssembly.definingSubroutine()
        scanAssemblyCode(inlineAssembly.assembly, inlineAssembly, scope)
        super.visit(inlineAssembly)
    }

    private fun scanAssemblyCode(asm: String, context: Statement, scope: Subroutine?) {
        asm.lines().forEach { line ->
            val matches = asmJumpRx.matchEntire(line)
            if (matches != null) {
                val jumptarget = matches.groups[2]?.value
                if (jumptarget != null && (jumptarget[0].isLetter() || jumptarget[0] == '_')) {
                    val node = program.namespace.lookup(jumptarget.split('.'), context)
                    if (node is Subroutine) {
                        if(scope!=null)
                            calls[scope] = calls.getValue(scope).plus(node)
                        calledBy[node] = calledBy.getValue(node).plus(context)
                    } else if (jumptarget.contains('.')) {
                        // maybe only the first part already refers to a subroutine
                        val node2 = program.namespace.lookup(listOf(jumptarget.substringBefore('.')), context)
                        if (node2 is Subroutine) {
                            if(scope!=null)
                                calls[scope] = calls.getValue(scope).plus(node2)
                            calledBy[node2] = calledBy.getValue(node2).plus(context)
                        }
                    }
                }
            } else {
                val matches2 = asmRefRx.matchEntire(line)
                if (matches2 != null) {
                    val target = matches2.groups[2]?.value
                    if (target != null && (target[0].isLetter() || target[0] == '_')) {
                        if (target.contains('.')) {
                            val node = program.namespace.lookup(listOf(target.substringBefore('.')), context)
                            if (node is Subroutine) {
                                if(scope!=null)
                                    calls[scope] = calls.getValue(scope).plus(node)
                                calledBy[node] = calledBy.getValue(node).plus(context)
                            }
                        }
                    }
                }
            }
        }
    }

    fun checkRecursiveCalls(errors: IErrorReporter) {
        val cycles = recursionCycles()
        if(cycles.any()) {
            errors.warn("Program contains recursive subroutine calls. These only works in very specific limited scenarios!", Position.DUMMY)
            val printed = mutableSetOf<Subroutine>()
            for(chain in cycles) {
                if(chain[0] !in printed) {
                    val chainStr = chain.joinToString(" <-- ") { "${it.name} at ${it.position}" }
                    errors.warn("Cycle in (a subroutine call in) $chainStr", Position.DUMMY)
                    printed.add(chain[0])
                }
            }
        }
    }

    private fun recursionCycles(): List<List<Subroutine>> {
        val chains = mutableListOf<MutableList<Subroutine>>()
        for(caller in calls.keys) {
            val visited = calls.keys.associateWith { false }.toMutableMap()
            val recStack = calls.keys.associateWith { false }.toMutableMap()
            val chain = mutableListOf<Subroutine>()
            if(hasCycle(caller, visited, recStack, chain))
                chains.add(chain)
        }
        return chains
    }

    private fun hasCycle(sub: Subroutine, visited: MutableMap<Subroutine, Boolean>, recStack: MutableMap<Subroutine, Boolean>, chain: MutableList<Subroutine>): Boolean {
        // mark current node as visited and add to recursion stack
        if(recStack[sub]==true)
            return true
        if(visited[sub]==true)
            return false

        // mark visited and add to recursion stack
        visited[sub] = true
        recStack[sub] = true

        // recurse for all neighbours
        for(called in calls.getValue(sub)) {
            if(hasCycle(called, visited, recStack, chain)) {
                chain.add(called)
                return true
            }
        }

        // pop from recursion stack
        recStack[sub] = false
        return false
    }

    fun unused(stmt: ISymbolStatement): Boolean {
        if(stmt is Subroutine)
            return calledBy[stmt].isNullOrEmpty()   // TODO also check inline assembly if it uses the subroutine

        // TODO implement algorithm to check usages of other things:
        // stmt can be: Block, Label, VarDecl (including ParameterVarDecl), Subroutine, StructDecl.
        // NOTE also check inline assembly blocks that may reference a name.
        return false
    }
}
