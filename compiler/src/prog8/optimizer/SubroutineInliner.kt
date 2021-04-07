package prog8.optimizer

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter


internal class SubroutineInliner(private val program: Program, val errors: IErrorReporter, private val compilerOptions: CompilationOptions): AstWalker() {
    private var callsToInlinedSubroutines = mutableListOf<Pair<IFunctionCall, Node>>()

    fun fixCallsToInlinedSubroutines() {
        for((call, parent) in callsToInlinedSubroutines) {
            val sub = call.target.targetSubroutine(program)!!
            val intermediateReturnValueVar = sub.statements.filterIsInstance<VarDecl>().singleOrNull { it.name.endsWith(retvarName) }
            if(intermediateReturnValueVar!=null) {
                val scope = parent.definingScope()
                if(!scope.statements.filterIsInstance<VarDecl>().any { it.name==intermediateReturnValueVar.name}) {
                    val decl = intermediateReturnValueVar.copy()
                    scope.statements.add(0, decl)
                    decl.linkParents(scope as Node)
                }
            }
        }
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        return if(compilerOptions.optimize && subroutine.inline && !subroutine.isAsmSubroutine)
            annotateInlinedSubroutineIdentifiers(subroutine)
        else
            noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return after(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)
    }

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        return after(functionCall as IFunctionCall, parent, functionCall.position)
    }

    private fun after(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {
        val sub = functionCall.target.targetSubroutine(program)
        if(sub != null && compilerOptions.optimize && sub.inline && !sub.isAsmSubroutine)
            callsToInlinedSubroutines.add(Pair(functionCall, parent))

        return noModifications
    }

    private fun annotateInlinedSubroutineIdentifiers(sub: Subroutine): List<IAstModification> {
        // this adds name prefixes to the identifiers used in the subroutine,
        // so that the statements can be inlined (=copied) in the call site and still reference
        // the correct symbols as seen from the scope of the subroutine.

        class Annotator: AstWalker() {
            var numReturns=0

            override fun before(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
                val stmt = identifier.targetStatement(program)!!
                if(stmt is BuiltinFunctionStatementPlaceholder)
                    return noModifications

                val prefixed = stmt.makeScopedName(identifier.nameInSource.last()).split('.')
                val withPrefix = IdentifierReference(prefixed, identifier.position)
                return listOf(IAstModification.ReplaceNode(identifier, withPrefix, parent))
            }

            override fun before(returnStmt: Return, parent: Node): Iterable<IAstModification> {
                numReturns++
                if(parent !== sub || sub.indexOfChild(returnStmt)<sub.statements.size-1)
                    errors.err("return statement must be the very last statement in the inlined subroutine", sub.position)
                return noModifications
            }

            fun theModifications(): List<IAstModification> {
                return this.modifications.map { it.first }.toList()
            }
        }

        val annotator = Annotator()
        sub.accept(annotator, sub.parent)
        if(annotator.numReturns>1) {
            errors.err("inlined subroutine can only have one return statement", sub.position)
            return noModifications
        }
        return annotator.theModifications()
    }

}
