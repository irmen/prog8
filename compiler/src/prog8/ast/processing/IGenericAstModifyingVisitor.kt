package prog8.ast.processing

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*


typealias AstModification = (node: Node, parent: Node) -> Unit


interface IGenericAstModifyingVisitor {

    fun before(addressOf: AddressOf, parent: Node): List<AstModification> = emptyList()
    fun before(array: ArrayLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun before(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): List<AstModification> = emptyList()
    fun before(assignTarget: AssignTarget, parent: Node): List<AstModification> = emptyList()
    fun before(assignment: Assignment, parent: Node): List<AstModification> = emptyList()
    fun before(block: Block, parent: Node): List<AstModification> = emptyList()
    fun before(branchStatement: BranchStatement, parent: Node): List<AstModification> = emptyList()
    fun before(breakStmt: Break, parent: Node): List<AstModification> = emptyList()
    fun before(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder, parent: Node): List<AstModification> = emptyList()
    fun before(contStmt: Continue, parent: Node): List<AstModification> = emptyList()
    fun before(decl: VarDecl, parent: Node): List<AstModification> = emptyList()
    fun before(directive: Directive, parent: Node): List<AstModification> = emptyList()
    fun before(expr: BinaryExpression, parent: Node): List<AstModification> = emptyList()
    fun before(expr: PrefixExpression, parent: Node): List<AstModification> = emptyList()
    fun before(forLoop: ForLoop, parent: Node): List<AstModification> = emptyList()
    fun before(foreverLoop: ForeverLoop, parent: Node): List<AstModification> = emptyList()
    fun before(functionCall: FunctionCall, parent: Node): List<AstModification> = emptyList()
    fun before(functionCallStatement: FunctionCallStatement, parent: Node): List<AstModification> = emptyList()
    fun before(identifier: IdentifierReference, parent: Node): List<AstModification> = emptyList()
    fun before(ifStatement: IfStatement, parent: Node): List<AstModification> = emptyList()
    fun before(inlineAssembly: InlineAssembly, parent: Node): List<AstModification> = emptyList()
    fun before(jump: Jump, parent: Node): List<AstModification> = emptyList()
    fun before(label: Label, parent: Node): List<AstModification> = emptyList()
    fun before(memread: DirectMemoryRead, parent: Node): List<AstModification> = emptyList()
    fun before(memwrite: DirectMemoryWrite, parent: Node): List<AstModification> = emptyList()
    fun before(module: Module, parent: Node): List<AstModification> = emptyList()
    fun before(nopStatement: NopStatement, parent: Node): List<AstModification> = emptyList()
    fun before(numLiteral: NumericLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun before(postIncrDecr: PostIncrDecr, parent: Node): List<AstModification> = emptyList()
    fun before(program: Program, parent: Node): List<AstModification> = emptyList()
    fun before(range: RangeExpr, parent: Node): List<AstModification> = emptyList()
    fun before(registerExpr: RegisterExpr, parent: Node): List<AstModification> = emptyList()
    fun before(repeatLoop: RepeatLoop, parent: Node): List<AstModification> = emptyList()
    fun before(returnStmt: Return, parent: Node): List<AstModification> = emptyList()
    fun before(scope: AnonymousScope, parent: Node): List<AstModification> = emptyList()
    fun before(string: StringLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun before(structDecl: StructDecl, parent: Node): List<AstModification> = emptyList()
    fun before(structLv: StructLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun before(subroutine: Subroutine, parent: Node): List<AstModification> = emptyList()
    fun before(typecast: TypecastExpression, parent: Node): List<AstModification> = emptyList()
    fun before(whenChoice: WhenChoice, parent: Node): List<AstModification> = emptyList()
    fun before(whenStatement: WhenStatement, parent: Node): List<AstModification> = emptyList()
    fun before(whileLoop: WhileLoop, parent: Node): List<AstModification> = emptyList()

    fun after(addressOf: AddressOf, parent: Node): List<AstModification> = emptyList()
    fun after(array: ArrayLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): List<AstModification> = emptyList()
    fun after(assignTarget: AssignTarget, parent: Node): List<AstModification> = emptyList()
    fun after(assignment: Assignment, parent: Node): List<AstModification> = emptyList()
    fun after(block: Block, parent: Node): List<AstModification> = emptyList()
    fun after(branchStatement: BranchStatement, parent: Node): List<AstModification> = emptyList()
    fun after(breakStmt: Break, parent: Node): List<AstModification> = emptyList()
    fun after(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder, parent: Node): List<AstModification> = emptyList()
    fun after(contStmt: Continue, parent: Node): List<AstModification> = emptyList()
    fun after(decl: VarDecl, parent: Node): List<AstModification> = emptyList()
    fun after(directive: Directive, parent: Node): List<AstModification> = emptyList()
    fun after(expr: BinaryExpression, parent: Node): List<AstModification> = emptyList()
    fun after(expr: PrefixExpression, parent: Node): List<AstModification> = emptyList()
    fun after(forLoop: ForLoop, parent: Node): List<AstModification> = emptyList()
    fun after(foreverLoop: ForeverLoop, parent: Node): List<AstModification> = emptyList()
    fun after(functionCall: FunctionCall, parent: Node): List<AstModification> = emptyList()
    fun after(functionCallStatement: FunctionCallStatement, parent: Node): List<AstModification> = emptyList()
    fun after(identifier: IdentifierReference, parent: Node): List<AstModification> = emptyList()
    fun after(ifStatement: IfStatement, parent: Node): List<AstModification> = emptyList()
    fun after(inlineAssembly: InlineAssembly, parent: Node): List<AstModification> = emptyList()
    fun after(jump: Jump, parent: Node): List<AstModification> = emptyList()
    fun after(label: Label, parent: Node): List<AstModification> = emptyList()
    fun after(memread: DirectMemoryRead, parent: Node): List<AstModification> = emptyList()
    fun after(memwrite: DirectMemoryWrite, parent: Node): List<AstModification> = emptyList()
    fun after(module: Module, parent: Node): List<AstModification> = emptyList()
    fun after(nopStatement: NopStatement, parent: Node): List<AstModification> = emptyList()
    fun after(numLiteral: NumericLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun after(postIncrDecr: PostIncrDecr, parent: Node): List<AstModification> = emptyList()
    fun after(program: Program, parent: Node): List<AstModification> = emptyList()
    fun after(range: RangeExpr, parent: Node): List<AstModification> = emptyList()
    fun after(registerExpr: RegisterExpr, parent: Node): List<AstModification> = emptyList()
    fun after(repeatLoop: RepeatLoop, parent: Node): List<AstModification> = emptyList()
    fun after(returnStmt: Return, parent: Node): List<AstModification> = emptyList()
    fun after(scope: AnonymousScope, parent: Node): List<AstModification> = emptyList()
    fun after(string: StringLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun after(structDecl: StructDecl, parent: Node): List<AstModification> = emptyList()
    fun after(structLv: StructLiteralValue, parent: Node): List<AstModification> = emptyList()
    fun after(subroutine: Subroutine, parent: Node): List<AstModification> = emptyList()
    fun after(typecast: TypecastExpression, parent: Node): List<AstModification> = emptyList()
    fun after(whenChoice: WhenChoice, parent: Node): List<AstModification> = emptyList()
    fun after(whenStatement: WhenStatement, parent: Node): List<AstModification> = emptyList()
    fun after(whileLoop: WhileLoop, parent: Node): List<AstModification> = emptyList()

    private fun applyModifications(mods: List<AstModification>, node: Node, parent: Node) {
        mods.forEach { it.invoke(node, parent) }
    }

    fun visit(program: Program) {
        applyModifications(before(program, program), program, program)
        program.modules.forEach { it.accept(this, program) }
        applyModifications(after(program, program), program, program)
    }

    fun visit(module: Module, parent: Node) {
        applyModifications(before(module, parent), module, parent)
        module.statements.forEach{ it.accept(this, module) }
        applyModifications(after(module, parent), module, parent)
    }

    fun visit(expr: PrefixExpression, parent: Node) {
        applyModifications(before(expr, parent), expr, parent)
        expr.expression.accept(this, expr)
        applyModifications(after(expr, parent), expr, parent)
    }

    fun visit(expr: BinaryExpression, parent: Node) {
        applyModifications(before(expr, parent), expr, parent)
        expr.left.accept(this, expr)
        expr.right.accept(this, expr)
        applyModifications(after(expr, parent), expr, parent)
    }

    fun visit(directive: Directive, parent: Node) {
        applyModifications(before(directive, parent), directive, parent)
        applyModifications(after(directive, parent), directive, parent)
    }

    fun visit(block: Block, parent: Node) {
        applyModifications(before(block, parent), block, parent)
        block.statements.forEach { it.accept(this, block) }
        applyModifications(after(block, parent), block, parent)
    }

    fun visit(decl: VarDecl, parent: Node) {
        applyModifications(before(decl, parent), decl, parent)
        decl.value?.accept(this, decl)
        decl.arraysize?.accept(this, decl)
        applyModifications(after(decl, parent), decl, parent)
    }

    fun visit(subroutine: Subroutine, parent: Node) {
        applyModifications(before(subroutine, parent), subroutine, parent)
        subroutine.statements.forEach { it.accept(this, subroutine) }
        applyModifications(after(subroutine, parent), subroutine, parent)
    }

    fun visit(functionCall: FunctionCall, parent: Node) {
        applyModifications(before(functionCall, parent), functionCall, parent)
        functionCall.target.accept(this, functionCall)
        functionCall.args.forEach { it.accept(this, functionCall) }
        applyModifications(after(functionCall, parent), functionCall, parent)
    }

    fun visit(functionCallStatement: FunctionCallStatement, parent: Node) {
        applyModifications(before(functionCallStatement, parent), functionCallStatement, parent)
        functionCallStatement.target.accept(this, functionCallStatement)
        functionCallStatement.args.forEach { it.accept(this, functionCallStatement) }
        applyModifications(after(functionCallStatement, parent), functionCallStatement, parent)
    }

    fun visit(identifier: IdentifierReference, parent: Node) {
        applyModifications(before(identifier, parent), identifier, parent)
        applyModifications(after(identifier, parent), identifier, parent)
    }

    fun visit(jump: Jump, parent: Node) {
        applyModifications(before(jump, parent), jump, parent)
        jump.identifier?.accept(this, jump)
        applyModifications(after(jump, parent), jump, parent)
    }

    fun visit(ifStatement: IfStatement, parent: Node) {
        applyModifications(before(ifStatement, parent), ifStatement, parent)
        ifStatement.condition.accept(this, ifStatement)
        ifStatement.truepart.accept(this, ifStatement)
        ifStatement.elsepart.accept(this, ifStatement)
        applyModifications(after(ifStatement, parent), ifStatement, parent)
    }

    fun visit(branchStatement: BranchStatement, parent: Node) {
        applyModifications(before(branchStatement, parent), branchStatement, parent)
        branchStatement.truepart.accept(this, branchStatement)
        branchStatement.elsepart.accept(this, branchStatement)
        applyModifications(after(branchStatement, parent), branchStatement, parent)
    }

    fun visit(range: RangeExpr, parent: Node) {
        applyModifications(before(range, parent), range, parent)
        range.from.accept(this, range)
        range.to.accept(this, range)
        range.step.accept(this, range)
        applyModifications(after(range, parent), range, parent)
    }

    fun visit(label: Label, parent: Node) {
        applyModifications(before(label, parent), label, parent)
        applyModifications(after(label, parent), label, parent)
    }

    fun visit(numLiteral: NumericLiteralValue, parent: Node) {
        applyModifications(before(numLiteral, parent), numLiteral, parent)
        applyModifications(after(numLiteral, parent), numLiteral, parent)
    }

    fun visit(string: StringLiteralValue, parent: Node) {
        applyModifications(before(string, parent), string, parent)
        applyModifications(after(string, parent), string, parent)
    }

    fun visit(array: ArrayLiteralValue, parent: Node) {
        applyModifications(before(array, parent), array, parent)
        array.value.forEach { v->v.accept(this, array) }
        applyModifications(after(array, parent), array, parent)
    }

    fun visit(assignment: Assignment, parent: Node) {
        applyModifications(before(assignment, parent), assignment, parent)
        assignment.target.accept(this, assignment)
        assignment.value.accept(this, assignment)
        applyModifications(after(assignment, parent), assignment, parent)
    }

    fun visit(postIncrDecr: PostIncrDecr, parent: Node) {
        applyModifications(before(postIncrDecr, parent), postIncrDecr, parent)
        postIncrDecr.target.accept(this, postIncrDecr)
        applyModifications(after(postIncrDecr, parent), postIncrDecr, parent)
    }

    fun visit(contStmt: Continue, parent: Node) {
        applyModifications(before(contStmt, parent), contStmt, parent)
        applyModifications(after(contStmt, parent), contStmt, parent)
    }

    fun visit(breakStmt: Break, parent: Node) {
        applyModifications(before(breakStmt, parent), breakStmt, parent)
        applyModifications(after(breakStmt, parent), breakStmt, parent)
    }

    fun visit(forLoop: ForLoop, parent: Node) {
        applyModifications(before(forLoop, parent), forLoop, parent)
        forLoop.loopVar?.accept(this, forLoop)
        forLoop.iterable.accept(this, forLoop)
        forLoop.body.accept(this, forLoop)
        applyModifications(after(forLoop, parent), forLoop, parent)
    }

    fun visit(whileLoop: WhileLoop, parent: Node) {
        applyModifications(before(whileLoop, parent), whileLoop, parent)
        whileLoop.condition.accept(this, whileLoop)
        whileLoop.body.accept(this, whileLoop)
        applyModifications(after(whileLoop, parent), whileLoop, parent)
    }

    fun visit(foreverLoop: ForeverLoop, parent: Node) {
        applyModifications(before(foreverLoop, parent), foreverLoop, parent)
        foreverLoop.body.accept(this, foreverLoop)
        applyModifications(after(foreverLoop, parent), foreverLoop, parent)
    }

    fun visit(repeatLoop: RepeatLoop, parent: Node) {
        applyModifications(before(repeatLoop, parent), repeatLoop, parent)
        repeatLoop.untilCondition.accept(this, repeatLoop)
        repeatLoop.body.accept(this, repeatLoop)
        applyModifications(after(repeatLoop, parent), repeatLoop, parent)
    }

    fun visit(returnStmt: Return, parent: Node) {
        applyModifications(before(returnStmt, parent), returnStmt, parent)
        returnStmt.value?.accept(this, returnStmt)
        applyModifications(after(returnStmt, parent), returnStmt, parent)
    }

    fun visit(arrayIndexedExpression: ArrayIndexedExpression, parent: Node) {
        applyModifications(before(arrayIndexedExpression, parent), arrayIndexedExpression, parent)
        arrayIndexedExpression.identifier.accept(this, arrayIndexedExpression)
        arrayIndexedExpression.arrayspec.accept(this, arrayIndexedExpression)
        applyModifications(after(arrayIndexedExpression, parent), arrayIndexedExpression, parent)
    }

    fun visit(assignTarget: AssignTarget, parent: Node) {
        applyModifications(before(assignTarget, parent), assignTarget, parent)
        assignTarget.arrayindexed?.accept(this, assignTarget)
        assignTarget.identifier?.accept(this, assignTarget)
        assignTarget.memoryAddress?.accept(this, assignTarget)
        applyModifications(after(assignTarget, parent), assignTarget, parent)
    }

    fun visit(scope: AnonymousScope, parent: Node) {
        applyModifications(before(scope, parent), scope, parent)
        scope.statements.forEach { it.accept(this, scope) }
        applyModifications(after(scope, parent), scope, parent)
    }

    fun visit(typecast: TypecastExpression, parent: Node) {
        applyModifications(before(typecast, parent), typecast, parent)
        typecast.expression.accept(this, typecast)
        applyModifications(after(typecast, parent), typecast, parent)
    }

    fun visit(memread: DirectMemoryRead, parent: Node) {
        applyModifications(before(memread, parent), memread, parent)
        memread.addressExpression.accept(this, memread)
        applyModifications(after(memread, parent), memread, parent)
    }

    fun visit(memwrite: DirectMemoryWrite, parent: Node) {
        applyModifications(before(memwrite, parent), memwrite, parent)
        memwrite.addressExpression.accept(this, memwrite)
        applyModifications(after(memwrite, parent), memwrite, parent)
    }

    fun visit(addressOf: AddressOf, parent: Node) {
        applyModifications(before(addressOf, parent), addressOf, parent)
        addressOf.identifier.accept(this, addressOf)
        applyModifications(after(addressOf, parent), addressOf, parent)
    }

    fun visit(inlineAssembly: InlineAssembly, parent: Node) {
        applyModifications(before(inlineAssembly, parent), inlineAssembly, parent)
        applyModifications(after(inlineAssembly, parent), inlineAssembly, parent)
    }

    fun visit(registerExpr: RegisterExpr, parent: Node) {
        applyModifications(before(registerExpr, parent), registerExpr, parent)
        applyModifications(after(registerExpr, parent), registerExpr, parent)
    }

    fun visit(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder, parent: Node) {
        applyModifications(before(builtinFunctionStatementPlaceholder, parent), builtinFunctionStatementPlaceholder, parent)
        applyModifications(after(builtinFunctionStatementPlaceholder, parent), builtinFunctionStatementPlaceholder, parent)
    }

    fun visit(nopStatement: NopStatement, parent: Node) {
        applyModifications(before(nopStatement, parent), nopStatement, parent)
        applyModifications(after(nopStatement, parent), nopStatement, parent)
    }

    fun visit(whenStatement: WhenStatement, parent: Node) {
        applyModifications(before(whenStatement, parent), whenStatement, parent)
        whenStatement.condition.accept(this, whenStatement)
        whenStatement.choices.forEach { it.accept(this, whenStatement) }
        applyModifications(after(whenStatement, parent), whenStatement, parent)
    }

    fun visit(whenChoice: WhenChoice, parent: Node) {
        applyModifications(before(whenChoice, parent), whenChoice, parent)
        whenChoice.values?.forEach { it.accept(this, whenChoice) }
        whenChoice.statements.accept(this, whenChoice)
        applyModifications(after(whenChoice, parent), whenChoice, parent)
    }

    fun visit(structDecl: StructDecl, parent: Node) {
        applyModifications(before(structDecl, parent), structDecl, parent)
        structDecl.statements.forEach { it.accept(this, structDecl) }
        applyModifications(after(structDecl, parent), structDecl, parent)
    }

    fun visit(structLv: StructLiteralValue, parent: Node) {
        applyModifications(before(structLv, parent), structLv, parent)
        structLv.values.forEach { it.accept(this, structLv) }
        applyModifications(after(structLv, parent), structLv, parent)
    }
}

