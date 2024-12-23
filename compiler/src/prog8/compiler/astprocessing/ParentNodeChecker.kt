package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class ParentNodeChecker: AstWalker() {

    override fun before(addressOf: AddressOf, parent: Node): Iterable<IAstModification> {
        if(addressOf.parent!==parent)
            throw FatalAstException("parent node mismatch at $addressOf")
        return noModifications
    }

    override fun before(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
        if(array.parent!==parent)
            throw FatalAstException("parent node mismatch at $array")
        return noModifications
    }

    override fun before(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        if(arrayIndexedExpression.parent!==parent)
            throw FatalAstException("parent node mismatch at $arrayIndexedExpression")
        return noModifications
    }

    override fun before(assignTarget: AssignTarget, parent: Node): Iterable<IAstModification> {
        if(assignTarget.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignTarget")
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignment")
        return noModifications
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        if(block.parent!==parent)
            throw FatalAstException("parent node mismatch at $block")
        return noModifications
    }

    override fun before(branch: ConditionalBranch, parent: Node): Iterable<IAstModification> {
        if(branch.parent!==parent)
            throw FatalAstException("parent node mismatch at $branch")
        return noModifications
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        if(breakStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $breakStmt")
        return noModifications
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.parent!==parent)
            throw FatalAstException("parent node mismatch at $decl")
        return noModifications
    }

    override fun before(directive: Directive, parent: Node): Iterable<IAstModification> {
        if(directive.parent!==parent)
            throw FatalAstException("parent node mismatch at $directive")
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.parent!==parent)
            throw FatalAstException("parent node mismatch at $expr")
        return noModifications
    }

    override fun before(expr: PrefixExpression, parent: Node): Iterable<IAstModification> {
        if(expr.parent!==parent)
            throw FatalAstException("parent node mismatch at $expr")
        return noModifications
    }

    override fun before(forLoop: ForLoop, parent: Node): Iterable<IAstModification> {
        if(forLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $forLoop")
        return noModifications
    }

    override fun before(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        if(repeatLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $repeatLoop")
        return noModifications
    }

    override fun before(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        if(identifier.parent!==parent)
            throw FatalAstException("parent node mismatch at $identifier")
        return noModifications
    }

    override fun before(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        if(ifElse.parent!==parent)
            throw FatalAstException("parent node mismatch at $ifElse")
        return noModifications
    }

    override fun before(inlineAssembly: InlineAssembly, parent: Node): Iterable<IAstModification> {
        if(inlineAssembly.parent!==parent)
            throw FatalAstException("parent node mismatch at $inlineAssembly")
        return noModifications
    }

    override fun before(jump: Jump, parent: Node): Iterable<IAstModification> {
        if(jump.parent!==parent)
            throw FatalAstException("parent node mismatch at $jump")
        return noModifications
    }

    override fun before(label: Label, parent: Node): Iterable<IAstModification> {
        if(label.parent!==parent)
            throw FatalAstException("parent node mismatch at $label")
        return noModifications
    }

    override fun before(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        if(memread.parent!==parent)
            throw FatalAstException("parent node mismatch at $memread")
        return noModifications
    }

    override fun before(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> {
        if(memwrite.parent!==parent)
            throw FatalAstException("parent node mismatch at $memwrite")
        return noModifications
    }

    override fun before(module: Module, parent: Node): Iterable<IAstModification> {
        if(module.parent!==parent)
            throw FatalAstException("parent node mismatch at $module")
        return noModifications
    }

    override fun before(numLiteral: NumericLiteral, parent: Node): Iterable<IAstModification> {
        if(numLiteral.parent!==parent)
            throw FatalAstException("parent node mismatch at $numLiteral")
        return noModifications
    }

    override fun before(range: RangeExpression, parent: Node): Iterable<IAstModification> {
        if(range.parent!==parent)
            throw FatalAstException("parent node mismatch at $range")
        return noModifications
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        if(untilLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $untilLoop")
        return noModifications
    }

    override fun before(returnStmt: Return, parent: Node): Iterable<IAstModification> {
        if(returnStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $returnStmt")
        return noModifications
    }

    override fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> {
        if(char.parent!==parent)
            throw FatalAstException("parent node mismatch at $char")
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<IAstModification> {
        if(string.parent!==parent)
            throw FatalAstException("parent node mismatch at $string")
        return noModifications
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if(subroutine.parent!==parent)
            throw FatalAstException("parent node mismatch at $subroutine")
        return noModifications
    }

    override fun before(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.parent!==parent)
            throw FatalAstException("parent node mismatch at $typecast")
        return noModifications
    }

    override fun before(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> {
        if(whenChoice.parent!==parent)
            throw FatalAstException("parent node mismatch at $whenChoice")
        return noModifications
    }

    override fun before(whenStmt: When, parent: Node): Iterable<IAstModification> {
        if(whenStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $whenStmt")
        return noModifications
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        if(whileLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $whileLoop")
        return noModifications
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        if(functionCallExpr.parent!==parent)
            throw FatalAstException("parent node mismatch at $functionCallExpr")
        return noModifications
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.parent!==parent)
            throw FatalAstException("parent node mismatch at $functionCallStatement")
        return noModifications
    }

    override fun before(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        if(scope.parent!==parent)
            throw FatalAstException("parent node mismatch at $scope")
        return noModifications
    }
}
