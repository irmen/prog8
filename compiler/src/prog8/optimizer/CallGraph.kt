package prog8.optimizer

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compiler.IErrorReporter


class CallGraph(private val program: Program) : IAstVisitor {

    val imports = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val importedBy = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val calls = mutableMapOf<Subroutine, Set<Subroutine>>().withDefault { setOf() }
    val calledBy = mutableMapOf<Subroutine, Set<Node>>().withDefault { setOf() }
    private val allIdentifiers = mutableSetOf<IdentifierReference>()

    init {
        visit(program)
    }

    private val usedSubroutines: Set<Subroutine> by lazy {
        // TODO also check inline assembly if it uses the subroutine
        calledBy.keys
    }

    private val usedBlocks: Set<Block> by lazy {
        // TODO also check inline assembly if it uses the block
        val blocksFromSubroutines = usedSubroutines.map { it.definingBlock() }
        val blocksFromLibraries = program.allBlocks().filter { it.isInLibrary }
        val used = mutableSetOf<Block>()

        allIdentifiers.forEach {
            if(it.definingBlock() in blocksFromSubroutines) {
                val target = it.targetStatement(program)!!.definingBlock()
                used.add(target)
            }
        }

        used + blocksFromLibraries + program.entrypoint().definingBlock()
    }

    private val usedModules: Set<Module> by lazy {
        program.modules.toSet() // TODO
    }

    override fun visit(directive: Directive) {
        val thisModule = directive.definingModule()
        if (directive.directive == "%import") {
            val importedModule: Module = program.modules.single { it.name == directive.args[0].name }
            imports[thisModule] = imports.getValue(thisModule).plus(importedModule)
            importedBy[importedModule] = importedBy.getValue(importedModule).plus(thisModule)
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

    override fun visit(identifier: IdentifierReference) {
        allIdentifiers.add(identifier)
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

    fun unused(module: Module) = module !in usedModules

    fun unused(sub: Subroutine) = sub !in usedSubroutines

    fun unused(block: Block) = block !in usedBlocks

    fun unused(decl: VarDecl): Boolean {
        return false    // TODO implement unused check for vardecls, also check inline asm
    }

    fun unused(struct: StructDecl): Boolean {
        return false    // TODO implement unused check for struct decls, also check inline asm
    }

    inline fun unused(label: Label) = false   // just always output labels

    fun unused(stmt: ISymbolStatement): Boolean {
        return when(stmt) {
            is Subroutine -> unused(stmt)
            is Block -> unused(stmt)
            is VarDecl -> unused(stmt)
            is Label -> false   // just always output labels
            is StructDecl -> unused(stmt)
            else -> false
        }
    }
}
