package prog8.compiler.astprocessing

import prog8.ast.FatalAstException
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstModification
import prog8.ast.walk.AstWalker


internal class ParentNodeChecker: AstWalker() {

    override fun before(addressOf: AddressOf, parent: Node): Iterable<AstModification> {
        if(addressOf.parent!==parent)
            throw FatalAstException("parent node mismatch at $addressOf")
        return noModifications
    }

    override fun before(array: ArrayLiteral, parent: Node): Iterable<AstModification> {
        if(array.parent!==parent)
            throw FatalAstException("parent node mismatch at $array")
        return noModifications
    }

    override fun before(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<AstModification> {
        if(arrayIndexedExpression.parent!==parent)
            throw FatalAstException("parent node mismatch at $arrayIndexedExpression")
        return noModifications
    }

    override fun before(assignTarget: AssignTarget, parent: Node): Iterable<AstModification> {
        if(assignTarget.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignTarget")
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<AstModification> {
        if(assignment.parent!==parent)
            throw FatalAstException("parent node mismatch at $assignment")
        return noModifications
    }

    override fun before(block: Block, parent: Node): Iterable<AstModification> {
        if(block.parent!==parent)
            throw FatalAstException("parent node mismatch at $block")
        return noModifications
    }

    override fun before(branch: ConditionalBranch, parent: Node): Iterable<AstModification> {
        if(branch.parent!==parent)
            throw FatalAstException("parent node mismatch at $branch")
        return noModifications
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<AstModification> {
        if(breakStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $breakStmt")
        return noModifications
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<AstModification> {
        if(decl.parent!==parent)
            throw FatalAstException("parent node mismatch at $decl")
        return noModifications
    }

    override fun before(directive: Directive, parent: Node): Iterable<AstModification> {
        if(directive.parent!==parent)
            throw FatalAstException("parent node mismatch at $directive")
        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<AstModification> {
        if(expr.parent!==parent)
            throw FatalAstException("parent node mismatch at $expr")
        return noModifications
    }

    override fun before(expr: PrefixExpression, parent: Node): Iterable<AstModification> {
        if(expr.parent!==parent)
            throw FatalAstException("parent node mismatch at $expr")
        return noModifications
    }

    override fun before(forLoop: ForLoop, parent: Node): Iterable<AstModification> {
        if(forLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $forLoop")
        return noModifications
    }

    override fun before(repeatLoop: RepeatLoop, parent: Node): Iterable<AstModification> {
        if(repeatLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $repeatLoop")
        return noModifications
    }

    override fun before(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {
        if(identifier.parent!==parent)
            throw FatalAstException("parent node mismatch at $identifier")
        return noModifications
    }

    override fun before(ifElse: IfElse, parent: Node): Iterable<AstModification> {
        if(ifElse.parent!==parent)
            throw FatalAstException("parent node mismatch at $ifElse")
        return noModifications
    }

    override fun before(inlineAssembly: InlineAssembly, parent: Node): Iterable<AstModification> {
        if(inlineAssembly.parent!==parent)
            throw FatalAstException("parent node mismatch at $inlineAssembly")
        return noModifications
    }

    override fun before(jump: Jump, parent: Node): Iterable<AstModification> {
        if(jump.parent!==parent)
            throw FatalAstException("parent node mismatch at $jump")
        return noModifications
    }

    override fun before(label: Label, parent: Node): Iterable<AstModification> {
        if(label.parent!==parent)
            throw FatalAstException("parent node mismatch at $label")
        return noModifications
    }

    override fun before(memread: DirectMemoryRead, parent: Node): Iterable<AstModification> {
        if(memread.parent!==parent)
            throw FatalAstException("parent node mismatch at $memread")
        return noModifications
    }

    override fun before(memwrite: DirectMemoryWrite, parent: Node): Iterable<AstModification> {
        if(memwrite.parent!==parent)
            throw FatalAstException("parent node mismatch at $memwrite")
        return noModifications
    }

    override fun before(module: Module, parent: Node): Iterable<AstModification> {
        if(module.parent!==parent)
            throw FatalAstException("parent node mismatch at $module")
        return noModifications
    }

    override fun before(numLiteral: NumericLiteral, parent: Node): Iterable<AstModification> {
        if(numLiteral.parent!==parent)
            throw FatalAstException("parent node mismatch at $numLiteral")
        return noModifications
    }

    override fun before(range: RangeExpression, parent: Node): Iterable<AstModification> {
        if(range.parent!==parent)
            throw FatalAstException("parent node mismatch at $range")
        return noModifications
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<AstModification> {
        if(untilLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $untilLoop")
        return noModifications
    }

    override fun before(returnStmt: Return, parent: Node): Iterable<AstModification> {
        if(returnStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $returnStmt")
        return noModifications
    }

    override fun before(char: CharLiteral, parent: Node): Iterable<AstModification> {
        if(char.parent!==parent)
            throw FatalAstException("parent node mismatch at $char")
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<AstModification> {
        if(string.parent!==parent)
            throw FatalAstException("parent node mismatch at $string")
        return noModifications
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<AstModification> {
        if(subroutine.parent!==parent)
            throw FatalAstException("parent node mismatch at $subroutine")
        return noModifications
    }

    override fun before(typecast: TypecastExpression, parent: Node): Iterable<AstModification> {
        if(typecast.parent!==parent)
            throw FatalAstException("parent node mismatch at $typecast")
        return noModifications
    }

    override fun before(whenChoice: WhenChoice, parent: Node): Iterable<AstModification> {
        if(whenChoice.parent!==parent)
            throw FatalAstException("parent node mismatch at $whenChoice")
        return noModifications
    }

    override fun before(whenStmt: When, parent: Node): Iterable<AstModification> {
        if(whenStmt.parent!==parent)
            throw FatalAstException("parent node mismatch at $whenStmt")
        return noModifications
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<AstModification> {
        if(whileLoop.parent!==parent)
            throw FatalAstException("parent node mismatch at $whileLoop")
        return noModifications
    }

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        if(functionCallExpr.parent!==parent)
            throw FatalAstException("parent node mismatch at $functionCallExpr")
        return noModifications
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<AstModification> {
        if(functionCallStatement.parent!==parent)
            throw FatalAstException("parent node mismatch at $functionCallStatement")
        return noModifications
    }

    override fun before(scope: AnonymousScope, parent: Node): Iterable<AstModification> {
        if(scope.parent!==parent)
            throw FatalAstException("parent node mismatch at $scope")
        return noModifications
    }
}
