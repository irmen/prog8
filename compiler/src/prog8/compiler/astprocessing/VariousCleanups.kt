package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.IErrorReporter


internal class VariousCleanups(val program: Program, val errors: IErrorReporter): AstWalker() {

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
                scope.statements.forEach { it.parent = into as Node }
                into.statements.remove(scope)
            }
        }
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        return before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)
    }

    override fun before(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        return before(functionCall as IFunctionCall, parent, functionCall.position)
    }

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {
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
        if(typecast.parent!==parent)
            throw FatalAstException("parent node mismatch at $typecast")

        if(typecast.expression is NumericLiteralValue) {
            val value = (typecast.expression as NumericLiteralValue).cast(typecast.type)
            if(value.isValid)
                return listOf(IAstModification.ReplaceNode(typecast, value.valueOrZero(), parent))
        }

        val sourceDt = typecast.expression.inferType(program)
        if(sourceDt.istype(typecast.type))
            return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))

        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if(subroutine.parent!==parent)
            throw FatalAstException("parent node mismatch at $subroutine")
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignment")
        return noModifications
    }

    override fun after(assignTarget: AssignTarget, parent: Node): Iterable<IAstModification> {
        if(assignTarget.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignTarget")
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.parent!==parent)
            throw FatalAstException("parent node mismatch at $decl")
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        if(scope.parent!==parent)
            throw FatalAstException("parent node mismatch at $scope")
        return noModifications
    }

    override fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        if(returnStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $returnStmt")
        return noModifications
    }

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        if(identifier.parent!==parent)
            throw FatalAstException("parent node mismatch at $identifier")
        return noModifications
    }
}
