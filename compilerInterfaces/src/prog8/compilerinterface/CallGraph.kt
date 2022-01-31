package prog8.compilerinterface

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor


class CallGraph(private val program: Program) : IAstVisitor {

    val imports = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val importedBy = mutableMapOf<Module, Set<Module>>().withDefault { setOf() }
    val calls = mutableMapOf<Subroutine, Set<Subroutine>>().withDefault { setOf() }
    val calledBy = mutableMapOf<Subroutine, Set<Node>>().withDefault { setOf() }
    private val allIdentifiersAndTargets = mutableMapOf<IdentifierReference, Statement>()
    private val allAssemblyNodes = mutableListOf<InlineAssembly>()

    init {
        visit(program)
    }

    val allIdentifiers: Map<IdentifierReference, Statement> = allIdentifiersAndTargets

    private val usedSubroutines: Set<Subroutine> by lazy {
        calledBy.keys + program.entrypoint
    }

    private val usedBlocks: Set<Block> by lazy {
        val blocksFromSubroutines = usedSubroutines.map { it.definingBlock }
        val blocksFromLibraries = program.allBlocks.filter { it.isInLibrary }
        val used = mutableSetOf<Block>()

        allIdentifiersAndTargets.forEach {
            if(it.key.definingBlock in blocksFromSubroutines) {
                val target = it.value.definingBlock
                used.add(target)
            }
        }

        used + blocksFromLibraries + program.entrypoint.definingBlock
    }

    private val usedModules: Set<Module> by lazy {
        usedBlocks.map { it.definingModule }.toSet()
    }

    override fun visit(directive: Directive) {
        val thisModule = directive.definingModule
        if (directive.directive == "%import") {
            val importedModule: Module = program.modules.single { it.name == directive.args[0].name }
            imports[thisModule] = imports.getValue(thisModule) + importedModule
            importedBy[importedModule] = importedBy.getValue(importedModule) + thisModule
        }

        super.visit(directive)
    }

    override fun visit(functionCallExpr: FunctionCallExpression) {
        val otherSub = functionCallExpr.target.targetSubroutine(program)
        if (otherSub != null) {
            functionCallExpr.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + functionCallExpr
            }
        }
        super.visit(functionCallExpr)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val otherSub = functionCallStatement.target.targetSubroutine(program)
        if (otherSub != null) {
            functionCallStatement.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + functionCallStatement
            }
        }
        super.visit(functionCallStatement)
    }

    override fun visit(addressOf: AddressOf) {
        val otherSub = addressOf.identifier.targetSubroutine(program)
        if(otherSub!=null) {
            addressOf.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + thisSub
            }
        }
        super.visit(addressOf)
    }

    override fun visit(jump: Jump) {
        val otherSub = jump.identifier?.targetSubroutine(program)
        if (otherSub != null) {
            jump.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + jump
            }
        }
        super.visit(jump)
    }

    override fun visit(gosub: GoSub) {
        val otherSub = gosub.identifier?.targetSubroutine(program)
        if (otherSub != null) {
            gosub.definingSubroutine?.let { thisSub ->
                calls[thisSub] = calls.getValue(thisSub) + otherSub
                calledBy[otherSub] = calledBy.getValue(otherSub) + gosub
            }
        }
        super.visit(gosub)
    }

    override fun visit(identifier: IdentifierReference) {
        allIdentifiersAndTargets[identifier] = identifier.targetStatement(program)!!
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        allAssemblyNodes.add(inlineAssembly)
    }

    override fun visit(pipe: PipeExpression) {
        processPipe(pipe.expressions, pipe)
        super.visit(pipe)
    }

    override fun visit(pipe: Pipe) {
        processPipe(pipe.expressions, pipe)
        super.visit(pipe)
    }

    private fun processPipe(expressions: Iterable<Expression>, pipe: Node) {
        expressions.forEach {
            if(it is IdentifierReference){
                val otherSub = it.targetSubroutine(program)
                if(otherSub!=null) {
                    pipe.definingSubroutine?.let { thisSub ->
                        calls[thisSub] = calls.getValue(thisSub) + otherSub
                        calledBy[otherSub] = calledBy.getValue(otherSub) + pipe
                    }
                }
            }
        }
    }

    fun checkRecursiveCalls(errors: IErrorReporter) {
        val cycles = recursionCycles()
        if(cycles.any()) {
            errors.warn("Program contains recursive subroutine calls. These only works in very specific limited scenarios!", cycles[0][0].position)
            val printed = mutableSetOf<Subroutine>()
            for(chain in cycles) {
                if(chain[0] !in printed) {
                    val chainStr = chain.joinToString(" <-- ") { "${it.name} at ${it.position}" }
                    errors.warn("Cycle in (a subroutine call in) $chainStr", chain[0].position)
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

    fun unused(sub: Subroutine): Boolean {
        return sub !in usedSubroutines && !nameInAssemblyCode(sub.name)
    }

    fun unused(block: Block): Boolean {
        return block !in usedBlocks && !nameInAssemblyCode(block.name)
    }

    fun unused(decl: VarDecl): Boolean {
        // Don't check assembly just for occurrences of variables, if they're not used in prog8 itself, just kill them
        return usages(decl).isEmpty()
    }

    fun usages(decl: VarDecl): List<IdentifierReference> {
        if(decl.type!=VarDeclType.VAR)
            return emptyList()

        if(decl.definingBlock !in usedBlocks)
            return emptyList()

        return allIdentifiersAndTargets.filter { decl===it.value }.map{ it.key }
    }

    private fun nameInAssemblyCode(name: String) = allAssemblyNodes.any { name in it.names }

    inline fun unused(label: Label) = false   // just always output labels

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
