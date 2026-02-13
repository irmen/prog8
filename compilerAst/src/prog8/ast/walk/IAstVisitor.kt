package prog8.ast.walk

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*

interface IAstVisitor {
    fun visit(program: Program) {
        program.modules.forEach { it.accept(this) }
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

    fun visit(alias: Alias) {
        alias.target.accept(this)
    }

    fun visit(containment: ContainmentCheck) {
        containment.element.accept(this)
        containment.iterable.accept(this)
    }

    fun visit(block: Block) {
        block.statements.forEach { it.accept(this) }
    }

    fun visit(decl: VarDecl) {
        decl.value?.accept(this)
        decl.arraysize?.accept(this)
    }

    fun visit(struct: StructDecl) {
    }

    fun visit(field: StructFieldRef) {
    }

    fun visit(swap: Swap) {
        swap.t1.accept(this)
        swap.t2.accept(this)
    }

    fun visit(subroutine: Subroutine) {
        subroutine.asmAddress?.varbank?.accept(this)
        subroutine.statements.forEach { it.accept(this) }
    }

    fun visit(functionCallExpr: FunctionCallExpression) {
        functionCallExpr.target.accept(this)
        functionCallExpr.args.forEach { it.accept(this) }
    }

    fun visit(initializer: StaticStructInitializer) {
        initializer.structname.accept(this)
        initializer.args.forEach { it.accept(this) }
    }

    fun visit(functionCallStatement: FunctionCallStatement) {
        functionCallStatement.target.accept(this)
        functionCallStatement.args.forEach { it.accept(this) }
    }

    fun visit(identifier: IdentifierReference) {
    }

    fun visit(jump: Jump) {
        jump.target.accept(this)
    }

    fun visit(ifElse: IfElse) {
        ifElse.condition.accept(this)
        ifElse.truepart.accept(this)
        ifElse.elsepart.accept(this)
    }

    fun visit(branch: ConditionalBranch) {
        branch.truepart.accept(this)
        branch.elsepart.accept(this)
    }

    fun visit(range: RangeExpression) {
        range.from.accept(this)
        range.to.accept(this)
        range.step.accept(this)
    }

    fun visit(ifExpr: IfExpression) {
        ifExpr.condition.accept(this)
        ifExpr.truevalue.accept(this)
        ifExpr.falsevalue.accept(this)
    }

    fun visit(branchExpr: BranchConditionExpression) {
        branchExpr.truevalue.accept(this)
        branchExpr.falsevalue.accept(this)
    }

    fun visit(label: Label) {
    }

    fun visit(numLiteral: NumericLiteral) {
    }

    fun visit(char: CharLiteral) {
    }

    fun visit(string: StringLiteral) {
    }

    fun visit(array: ArrayLiteral) {
        array.value.forEach { v->v.accept(this) }
    }

    fun visit(assignment: Assignment) {
        assignment.target.accept(this)
        assignment.value.accept(this)
    }

    fun visit(breakStmt: Break) {
    }

    fun visit(continueStmt: Continue) {
    }

    fun visit(forLoop: ForLoop) {
        forLoop.loopVar.accept(this)
        forLoop.iterable.accept(this)
        forLoop.body.accept(this)
    }

    fun visit(whileLoop: WhileLoop) {
        whileLoop.condition.accept(this)
        whileLoop.body.accept(this)
    }

    fun visit(repeatLoop: RepeatLoop) {
        repeatLoop.iterations?.accept(this)
        repeatLoop.body.accept(this)
    }

    fun visit(unrollLoop: UnrollLoop) {
        unrollLoop.body.accept(this)
    }

    fun visit(untilLoop: UntilLoop) {
        untilLoop.condition.accept(this)
        untilLoop.body.accept(this)
    }

    fun visit(returnStmt: Return) {
        returnStmt.values.forEach { it.accept(this) }
    }

    fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
        arrayIndexedExpression.plainarrayvar?.accept(this)
        arrayIndexedExpression.pointerderef?.accept(this)
        arrayIndexedExpression.indexer.accept(this)
    }

    fun visit(assignTarget: AssignTarget) {
        assignTarget.arrayindexed?.accept(this)
        assignTarget.identifier?.accept(this)
        assignTarget.memoryAddress?.accept(this)
        assignTarget.pointerDereference?.accept(this)
        assignTarget.arrayIndexedDereference?.accept(this)
        assignTarget.multi?.forEach { it.accept(this) }
    }

    fun visit(scope: AnonymousScope) {
        scope.statements.forEach { it.accept(this) }
    }

    fun visit(defer: Defer) {
        defer.scope.accept(this)
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
        addressOf.identifier?.accept(this)
        addressOf.arrayIndex?.accept(this)
        addressOf.dereference?.accept(this)
    }

    fun visit(inlineAssembly: InlineAssembly) {
    }

    fun visit(whenStmt: When) {
        whenStmt.condition.accept(this)
        whenStmt.choices.forEach { it.accept(this) }
    }

    fun visit(whenChoice: WhenChoice) {
        whenChoice.values?.forEach { it.accept(this) }
        whenChoice.statements.accept(this)
    }

    fun visit(chainedAssignment: ChainedAssignment) {
        chainedAssignment.target.accept(this)
        chainedAssignment.nested.accept(this)
    }

    fun visit(onGoto: OnGoto) {
        onGoto.index.accept(this)
        onGoto.labels.forEach { it.accept(this) }
        onGoto.elsepart?.accept(this)
    }

    fun visit(deref: PtrDereference) {
    }

    fun visit(deref: ArrayIndexedPtrDereference) {
        deref.chain.forEach { it.second?.accept(this) }
    }
}
