package prog8.optimizing

import prog8.ast.*


class CallGraphBuilder(private val program: Program): IAstProcessor {

    private val modulesImporting = mutableMapOf<Module, Set<Module>>().withDefault { mutableSetOf() }
    private val modulesImportedBy = mutableMapOf<Module, Set<Module>>().withDefault { mutableSetOf() }
    private val subroutinesCalling = mutableMapOf<Subroutine, Set<Subroutine>>().withDefault { mutableSetOf() }
    private val subroutinesCalledBy = mutableMapOf<Subroutine, Set<Subroutine>>().withDefault { mutableSetOf() }

    private fun forAllSubroutines(scope: INameScope, sub: (s: Subroutine) -> Unit) {
        fun findSubs(scope: INameScope) {
            scope.statements.forEach {
                if(it is Subroutine)
                    sub(it)
                if(it is INameScope)
                    findSubs(it)
            }
        }
        findSubs(scope)
    }

    override fun process(program: Program) {
        super.process(program)

        program.modules.forEach {
            it.importedBy.clear()
            it.imports.clear()

            it.importedBy.addAll(modulesImportedBy.getValue(it))
            it.imports.addAll(modulesImporting.getValue(it))

            if(it.isLibraryModule && it.importedBy.isEmpty())
                it.importedBy.add(it)       // don't discard auto-imported library module

            forAllSubroutines(it) { sub ->
                sub.calledBy.clear()
                sub.calls.clear()

                sub.calledBy.addAll(subroutinesCalledBy.getValue(sub))
                sub.calls.addAll(subroutinesCalling.getValue(sub))
            }
        }

        val rootmodule = program.modules.first()
        rootmodule.importedBy.add(rootmodule)       // don't discard root module
    }

    override fun process(directive: Directive): IStatement {
        if(directive.directive=="%import") {
            val importedModule: Module = program.modules.single { it.name==directive.args[0].name }
            val thisModule = directive.definingModule()
            modulesImporting[thisModule] = modulesImporting.getValue(thisModule).plus(importedModule)
            modulesImportedBy[importedModule] = modulesImportedBy.getValue(importedModule).plus(thisModule)
        }
        return super.process(directive)
    }

    override fun process(functionCall: FunctionCall): IExpression {
        val otherSub = functionCall.target.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            val thisSub = functionCall.definingScope() as Subroutine
            subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
            subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
        }
        return super.process(functionCall)
    }

    override fun process(functionCallStatement: FunctionCallStatement): IStatement {
        val otherSub = functionCallStatement.target.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            val thisSub = functionCallStatement.definingScope() as Subroutine
            subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
            subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
        }
        return super.process(functionCallStatement)
    }

    override fun process(jump: Jump): IStatement {
        val otherSub = jump.identifier?.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            val thisSub = jump.definingScope() as Subroutine
            subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
            subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
        }
        return super.process(jump)
    }
}
