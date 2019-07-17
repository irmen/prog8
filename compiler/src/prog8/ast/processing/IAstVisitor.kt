package prog8.ast.processing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*

interface IAstVisitor {
    fun visit(program: Program) {
        program.modules.forEach { visit(it) }
    }

    fun visit(module: Module) {
        module.statements.forEach{ it.accept(this) }
    }

    fun visit(expr: PrefixExpression) {
        expr.expression.accept(this)
    }

    fun visit(expr: BinaryExpression) {
        expr.left.accept(this)
        expr.right.accept(this)
    }

    fun visit(directive: Directive) {
    }

    fun visit(block: Block) {
        block.statements.forEach { it.accept(this) }
    }

    fun visit(decl: VarDecl) {
        decl.value?.accept(this)
        decl.arraysize?.accept(this)
    }

    fun visit(subroutine: Subroutine) {
        subroutine.statements.forEach { it.accept(this) }
    }

    fun visit(functionCall: FunctionCall) {
        functionCall.target.accept(this)
        functionCall.arglist.forEach { it.accept(this) }
    }

    fun visit(functionCallStatement: FunctionCallStatement) {
        functionCallStatement.target.accept(this)
        functionCallStatement.arglist.forEach { it.accept(this) }
    }

    fun visit(identifier: IdentifierReference) {
    }

    fun visit(jump: Jump) {
        jump.identifier?.accept(this)
    }

    fun visit(ifStatement: IfStatement) {
        ifStatement.condition.accept(this)
        ifStatement.truepart.accept(this)
        ifStatement.elsepart.accept(this)
    }

    fun visit(branchStatement: BranchStatement) {
        branchStatement.truepart.accept(this)
        branchStatement.elsepart.accept(this)
    }

    fun visit(range: RangeExpr) {
        range.from.accept(this)
        range.to.accept(this)
        range.step.accept(this)
    }

    fun visit(label: Label) {
    }

    fun visit(numLiteral: NumericLiteralValue) {
    }

    fun visit(refLiteral: ReferenceLiteralValue) {
        refLiteral.array?.let { it.forEach { v->v.accept(this) }}
    }

    fun visit(assignment: Assignment) {
        assignment.target.accept(this)
        assignment.value.accept(this)
    }

    fun visit(postIncrDecr: PostIncrDecr) {
        postIncrDecr.target.accept(this)
    }

    fun visit(contStmt: Continue) {
    }

    fun visit(breakStmt: Break) {
    }

    fun visit(forLoop: ForLoop) {
        forLoop.loopVar?.accept(this)
        forLoop.iterable.accept(this)
        forLoop.body.accept(this)
    }

    fun visit(whileLoop: WhileLoop) {
        whileLoop.condition.accept(this)
        whileLoop.body.accept(this)
    }

    fun visit(repeatLoop: RepeatLoop) {
        repeatLoop.untilCondition.accept(this)
        repeatLoop.body.accept(this)
    }

    fun visit(returnStmt: Return) {
        returnStmt.value?.accept(this)
    }

    fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
        arrayIndexedExpression.identifier.accept(this)
        arrayIndexedExpression.arrayspec.accept(this)
    }

    fun visit(assignTarget: AssignTarget) {
        assignTarget.arrayindexed?.accept(this)
        assignTarget.identifier?.accept(this)
        assignTarget.memoryAddress?.accept(this)
    }

    fun visit(scope: AnonymousScope) {
        scope.statements.forEach { it.accept(this) }
    }

    fun visit(typecast: TypecastExpression) {
        typecast.expression.accept(this)
    }

    fun visit(memread: DirectMemoryRead) {
        memread.addressExpression.accept(this)
    }

    fun visit(memwrite: DirectMemoryWrite) {
        memwrite.addressExpression.accept(this)
    }

    fun visit(addressOf: AddressOf) {
        addressOf.identifier.accept(this)
    }

    fun visit(inlineAssembly: InlineAssembly) {
    }

    fun visit(registerExpr: RegisterExpr) {
    }

    fun visit(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder) {
    }

    fun visit(nopStatement: NopStatement) {
    }

    fun visit(whenStatement: WhenStatement) {
        whenStatement.condition.accept(this)
        whenStatement.choices.forEach { it.accept(this) }
    }

    fun visit(whenChoice: WhenChoice) {
        whenChoice.values?.forEach { it.accept(this) }
        whenChoice.statements.accept(this)
    }

    fun visit(structDecl: StructDecl) {
        structDecl.statements.forEach { it.accept(this) }
    }

    fun visit(structLv: StructLiteralValue) {
        structLv.values.forEach { it.accept(this) }
    }
}
