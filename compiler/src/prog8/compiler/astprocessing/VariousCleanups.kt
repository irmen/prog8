package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter


internal class VariousCleanups(private val program: Program, val errors: IErrorReporter, private val compilerOptions: CompilationOptions): AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun before(nopStatement: NopStatement, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(nopStatement, parent as INameScope))
    }

    override fun before(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is INameScope)
            listOf(ScopeFlatten(scope, parent as INameScope))
        else
            noModifications
    }

    class ScopeFlatten(val scope: AnonymousScope, val into: INameScope) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                into.statements.remove(scope)
            }
        }
    }

    override fun before(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.expression is NumericLiteralValue) {
            val value = (typecast.expression as NumericLiteralValue).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        return noModifications
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)
    }

    override fun before(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        return before(functionCall as IFunctionCall, parent, functionCall.position)
    }

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {

        if(compilerOptions.optimize) {
            val sub = functionCall.target.targetSubroutine(program)
            if(sub!=null && sub.inline && !sub.isAsmSubroutine)
                annotateInlinedSubroutineIdentifiers(sub)
        }


        if(functionCall.target.nameInSource==listOf("peek")) {
            // peek(a) is synonymous with @(a)
            val memread = DirectMemoryRead(functionCall.args.single(), position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, memread, parent))
        }
        if(functionCall.target.nameInSource==listOf("poke")) {
            // poke(a, v) is synonymous with @(a) = v
            val tgt = AssignTarget(null, null, DirectMemoryWrite(functionCall.args[0], position), position)
            val assign = Assignment(tgt, functionCall.args[1], position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, assign, parent))
        }
        return noModifications
    }

    private fun annotateInlinedSubroutineIdentifiers(sub: Subroutine) {
        // this adds full name prefixes to all identifiers used in the subroutine,
        // so that the statements can be inlined (=copied) in the call site and still reference
        // the correct symbols as seen from the scope of the subroutine.

        class Annotator: AstWalker() {
            var numReturns=0

            override fun before(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
                val stmt = identifier.targetStatement(program)!!
                val prefixed = stmt.makeScopedName(identifier.nameInSource.last()).split('.')
                val withPrefix = IdentifierReference(prefixed, identifier.position)
                return listOf(IAstModification.ReplaceNode(identifier, withPrefix, identifier.parent))
            }

            override fun before(returnStmt: Return, parent: Node): Iterable<IAstModification> {
                numReturns++
                if(parent !== sub || sub.indexOfChild(returnStmt)<sub.statements.size-1)
                    errors.err("return statement must be the very last statement in the inlined subroutine", sub.position)
                return emptyList()
            }
        }

        val annotator = Annotator()
        sub.accept(annotator, sub.parent)
        if(annotator.numReturns>1)
            errors.err("inlined subroutine can only have one return statement", sub.position)
        else
            annotator.applyModifications()
    }

}
