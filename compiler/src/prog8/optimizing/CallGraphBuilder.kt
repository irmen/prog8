package prog8.optimizing

import prog8.ast.*
import prog8.compiler.loadAsmIncludeFile


class CallGraphBuilder(private val program: Program): IAstProcessor {

    private val modulesImporting = mutableMapOf<Module, Set<Module>>().withDefault { mutableSetOf() }
    private val modulesImportedBy = mutableMapOf<Module, Set<Module>>().withDefault { mutableSetOf() }
    private val subroutinesCalling = mutableMapOf<INameScope, Set<Subroutine>>().withDefault { mutableSetOf() }
    private val subroutinesCalledBy = mutableMapOf<Subroutine, Set<INameScope>>().withDefault { mutableSetOf() }

    fun forAllSubroutines(scope: INameScope, sub: (s: Subroutine) -> Unit) {
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
        val thisModule = directive.definingModule()
        if(directive.directive=="%import") {
            val importedModule: Module = program.modules.single { it.name==directive.args[0].name }
            modulesImporting[thisModule] = modulesImporting.getValue(thisModule).plus(importedModule)
            modulesImportedBy[importedModule] = modulesImportedBy.getValue(importedModule).plus(thisModule)
        } else if (directive.directive=="%asminclude") {
            val asm = loadAsmIncludeFile(directive.args[0].str!!, thisModule.source)
            val scope = directive.definingScope()
            scanAssemblyCode(asm, directive, scope)
        }

        return super.process(directive)
    }

    override fun process(functionCall: FunctionCall): IExpression {
        val otherSub = functionCall.target.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            functionCall.definingSubroutine()?.let { thisSub ->
                subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
                subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
            }
        }
        return super.process(functionCall)
    }

    override fun process(functionCallStatement: FunctionCallStatement): IStatement {
        val otherSub = functionCallStatement.target.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            functionCallStatement.definingSubroutine()?.let { thisSub ->
                subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
                subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
            }
        }
        return super.process(functionCallStatement)
    }

    override fun process(jump: Jump): IStatement {
        val otherSub = jump.identifier?.targetSubroutine(program.namespace)
        if(otherSub!=null) {
            jump.definingSubroutine()?.let { thisSub ->
                subroutinesCalling[thisSub] = subroutinesCalling.getValue(thisSub).plus(otherSub)
                subroutinesCalledBy[otherSub] = subroutinesCalledBy.getValue(otherSub).plus(thisSub)
            }
        }
        return super.process(jump)
    }

    override fun process(inlineAssembly: InlineAssembly): IStatement {
        // parse inline asm for subroutine calls (jmp, jsr)
        val scope = inlineAssembly.definingScope()
        scanAssemblyCode(inlineAssembly.assembly, inlineAssembly, scope)
        return super.process(inlineAssembly)
    }

    private fun scanAssemblyCode(asm: String, context: Node, scope: INameScope) {
        val asmJumpRx = Regex("""[\-+a-zA-Z0-9_ \t]+(jmp|jsr)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)
        val asmRefRx = Regex("""[\-+a-zA-Z0-9_ \t]+(...)[ \t]+(\S+).*""", RegexOption.IGNORE_CASE)
        asm.lines().forEach { line ->
            val matches = asmJumpRx.matchEntire(line)
            if (matches != null) {
                val jumptarget = matches.groups[2]?.value
                if (jumptarget != null && (jumptarget[0].isLetter() || jumptarget[0] == '_')) {
                    val node = program.namespace.lookup(jumptarget.split('.'), context)
                    if (node is Subroutine) {
                        subroutinesCalling[scope] = subroutinesCalling.getValue(scope).plus(node)
                        subroutinesCalledBy[node] = subroutinesCalledBy.getValue(node).plus(scope)
                    } else if(jumptarget.contains('.')) {
                        // maybe only the first part already refers to a subroutine
                        val node2 = program.namespace.lookup(listOf(jumptarget.substringBefore('.')), context)
                        if (node2 is Subroutine) {
                            subroutinesCalling[scope] = subroutinesCalling.getValue(scope).plus(node2)
                            subroutinesCalledBy[node2] = subroutinesCalledBy.getValue(node2).plus(scope)
                        }
                    }
                }
            } else {
                val matches2 = asmRefRx.matchEntire(line)
                if (matches2 != null) {
                    val target= matches2.groups[2]?.value
                    if (target != null && (target[0].isLetter() || target[0] == '_')) {
                        val node = program.namespace.lookup(listOf(target.substringBefore('.')), context)
                        if (node is Subroutine) {
                            subroutinesCalling[scope] = subroutinesCalling.getValue(scope).plus(node)
                            subroutinesCalledBy[node] = subroutinesCalledBy.getValue(node).plus(scope)
                        }
                    }
                }
            }
        }
    }
}
