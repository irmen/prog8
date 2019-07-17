package prog8.ast.processing

import prog8.ast.IExpression
import prog8.ast.IStatement
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*

interface IAstModifyingVisitor {
    fun visit(program: Program) {
        program.modules.forEach { visit(it) }
    }

    fun visit(module: Module) {
        module.statements = module.statements.asSequence().map { it.accept(this) }.toMutableList()
    }

    fun visit(expr: PrefixExpression): IExpression {
        expr.expression = expr.expression.accept(this)
        return expr
    }

    fun visit(expr: BinaryExpression): IExpression {
        expr.left = expr.left.accept(this)
        expr.right = expr.right.accept(this)
        return expr
    }

    fun visit(directive: Directive): IStatement {
        return directive
    }

    fun visit(block: Block): IStatement {
        block.statements = block.statements.asSequence().map { it.accept(this) }.toMutableList()
        return block
    }

    fun visit(decl: VarDecl): IStatement {
        decl.value = decl.value?.accept(this)
        decl.arraysize?.accept(this)
        return decl
    }

    fun visit(subroutine: Subroutine): IStatement {
        subroutine.statements = subroutine.statements.asSequence().map { it.accept(this) }.toMutableList()
        return subroutine
    }

    fun visit(functionCall: FunctionCall): IExpression {
        val newtarget = functionCall.target.accept(this)
        if(newtarget is IdentifierReference)
            functionCall.target = newtarget
        functionCall.arglist = functionCall.arglist.map { it.accept(this) }.toMutableList()
        return functionCall
    }

    fun visit(functionCallStatement: FunctionCallStatement): IStatement {
        val newtarget = functionCallStatement.target.accept(this)
        if(newtarget is IdentifierReference)
            functionCallStatement.target = newtarget
        functionCallStatement.arglist = functionCallStatement.arglist.map { it.accept(this) }.toMutableList()
        return functionCallStatement
    }

    fun visit(identifier: IdentifierReference): IExpression {
        // note: this is an identifier that is used in an expression.
        // other identifiers are simply part of the other statements (such as jumps, subroutine defs etc)
        return identifier
    }

    fun visit(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val ident = jump.identifier.accept(this)
            if(ident is IdentifierReference && ident!==jump.identifier) {
                return Jump(null, ident, null, jump.position)
            }
        }
        return jump
    }

    fun visit(ifStatement: IfStatement): IStatement {
        ifStatement.condition = ifStatement.condition.accept(this)
        ifStatement.truepart = ifStatement.truepart.accept(this) as AnonymousScope
        ifStatement.elsepart = ifStatement.elsepart.accept(this) as AnonymousScope
        return ifStatement
    }

    fun visit(branchStatement: BranchStatement): IStatement {
        branchStatement.truepart = branchStatement.truepart.accept(this) as AnonymousScope
        branchStatement.elsepart = branchStatement.elsepart.accept(this) as AnonymousScope
        return branchStatement
    }

    fun visit(range: RangeExpr): IExpression {
        range.from = range.from.accept(this)
        range.to = range.to.accept(this)
        range.step = range.step.accept(this)
        return range
    }

    fun visit(label: Label): IStatement {
        return label
    }

    fun visit(literalValue: NumericLiteralValue): NumericLiteralValue {
        return literalValue
    }

    fun visit(refLiteral: ReferenceLiteralValue): IExpression {
        if(refLiteral.array!=null) {
            for(av in refLiteral.array.withIndex()) {
                val newvalue = av.value.accept(this)
                refLiteral.array[av.index] = newvalue
            }
        }
        return refLiteral
    }

    fun visit(assignment: Assignment): IStatement {
        assignment.target = assignment.target.accept(this)
        assignment.value = assignment.value.accept(this)
        return assignment
    }

    fun visit(postIncrDecr: PostIncrDecr): IStatement {
        postIncrDecr.target = postIncrDecr.target.accept(this)
        return postIncrDecr
    }

    fun visit(contStmt: Continue): IStatement {
        return contStmt
    }

    fun visit(breakStmt: Break): IStatement {
        return breakStmt
    }

    fun visit(forLoop: ForLoop): IStatement {
        forLoop.loopVar?.accept(this)
        forLoop.iterable = forLoop.iterable.accept(this)
        forLoop.body = forLoop.body.accept(this) as AnonymousScope
        return forLoop
    }

    fun visit(whileLoop: WhileLoop): IStatement {
        whileLoop.condition = whileLoop.condition.accept(this)
        whileLoop.body = whileLoop.body.accept(this) as AnonymousScope
        return whileLoop
    }

    fun visit(repeatLoop: RepeatLoop): IStatement {
        repeatLoop.untilCondition = repeatLoop.untilCondition.accept(this)
        repeatLoop.body = repeatLoop.body.accept(this) as AnonymousScope
        return repeatLoop
    }

    fun visit(returnStmt: Return): IStatement {
        returnStmt.value = returnStmt.value?.accept(this)
        return returnStmt
    }

    fun visit(arrayIndexedExpression: ArrayIndexedExpression): IExpression {
        arrayIndexedExpression.identifier.accept(this)
        arrayIndexedExpression.arrayspec.accept(this)
        return arrayIndexedExpression
    }

    fun visit(assignTarget: AssignTarget): AssignTarget {
        assignTarget.arrayindexed?.accept(this)
        assignTarget.identifier?.accept(this)
        assignTarget.memoryAddress?.let { visit(it) }
        return assignTarget
    }

    fun visit(scope: AnonymousScope): IStatement {
        scope.statements = scope.statements.asSequence().map { it.accept(this) }.toMutableList()
        return scope
    }

    fun visit(typecast: TypecastExpression): IExpression {
        typecast.expression = typecast.expression.accept(this)
        return typecast
    }

    fun visit(memread: DirectMemoryRead): IExpression {
        memread.addressExpression = memread.addressExpression.accept(this)
        return memread
    }

    fun visit(memwrite: DirectMemoryWrite) {
        memwrite.addressExpression = memwrite.addressExpression.accept(this)
    }

    fun visit(addressOf: AddressOf): IExpression {
        addressOf.identifier.accept(this)
        return addressOf
    }

    fun visit(inlineAssembly: InlineAssembly): IStatement {
        return inlineAssembly
    }

    fun visit(registerExpr: RegisterExpr): IExpression {
        return registerExpr
    }

    fun visit(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder): IStatement {
        return builtinFunctionStatementPlaceholder
    }

    fun visit(nopStatement: NopStatement): IStatement {
        return nopStatement
    }

    fun visit(whenStatement: WhenStatement): IStatement {
        whenStatement.condition.accept(this)
        whenStatement.choices.forEach { it.accept(this) }
        return whenStatement
    }

    fun visit(whenChoice: WhenChoice) {
        whenChoice.values?.forEach { it.accept(this) }
        whenChoice.statements.accept(this)
    }

    fun visit(structDecl: StructDecl): IStatement {
        structDecl.statements = structDecl.statements.map{ it.accept(this) }.toMutableList()
        return structDecl
    }

    fun visit(structLv: StructLiteralValue): IExpression {
        structLv.values = structLv.values.map { it.accept(this) }
        return structLv
    }
}
