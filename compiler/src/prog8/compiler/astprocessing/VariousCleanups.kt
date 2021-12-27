package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.IErrorReporter


internal class VariousCleanups(val program: Program, val errors: IErrorReporter): AstWalker() {

    override fun before(nopStatement: NopStatement, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(nopStatement, parent as IStatementContainer))
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        return if(parent is IStatementContainer)
            listOf(ScopeFlatten(scope, parent as IStatementContainer))
        else
            noModifications
    }

    private class ScopeFlatten(val scope: AnonymousScope, val into: IStatementContainer) : IAstModification {
        override fun perform() {
            val idx = into.statements.indexOf(scope)
            if(idx>=0) {
                into.statements.addAll(idx+1, scope.statements)
                scope.statements.forEach { it.parent = into as Node }
                into.statements.remove(scope)
            }
        }
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node) =
        before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)

    override fun before(functionCall: FunctionCall, parent: Node) =
        before(functionCall as IFunctionCall, parent, functionCall.position)

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {

        // TODO move to CodeDesugarer

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

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.expression is NumericLiteralValue) {
            val value = (typecast.expression as NumericLiteralValue).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt istype typecast.type)
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        if(parent is Assignment) {
            val targetDt = (parent).target.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
            if(sourceDt istype targetDt) {
                // we can get rid of this typecast because the type is already
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val nextAssign = assignment.nextSibling() as? Assignment
        if(nextAssign!=null && nextAssign.target.isSameAs(assignment.target, program)) {
            if(nextAssign.value isSameAs assignment.value)
                return listOf(IAstModification.Remove(assignment, parent as IStatementContainer))
        }

        return noModifications
    }
}
