package prog8.ast.processing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*

interface IAstProcessor {
    fun process(program: Program) {
        program.modules.forEach { process(it) }
    }

    fun process(module: Module) {
        module.statements = module.statements.asSequence().map { it.process(this) }.toMutableList()
    }

    fun process(expr: PrefixExpression): IExpression {
        expr.expression = expr.expression.process(this)
        return expr
    }

    fun process(expr: BinaryExpression): IExpression {
        expr.left = expr.left.process(this)
        expr.right = expr.right.process(this)
        return expr
    }

    fun process(directive: Directive): IStatement {
        return directive
    }

    fun process(block: Block): IStatement {
        block.statements = block.statements.asSequence().map { it.process(this) }.toMutableList()
        return block
    }

    fun process(decl: VarDecl): IStatement {
        decl.value = decl.value?.process(this)
        decl.arraysize?.process(this)
        return decl
    }

    fun process(subroutine: Subroutine): IStatement {
        subroutine.statements = subroutine.statements.asSequence().map { it.process(this) }.toMutableList()
        return subroutine
    }

    fun process(functionCall: FunctionCall): IExpression {
        val newtarget = functionCall.target.process(this)
        if(newtarget is IdentifierReference)
            functionCall.target = newtarget
        functionCall.arglist = functionCall.arglist.map { it.process(this) }.toMutableList()
        return functionCall
    }

    fun process(functionCallStatement: FunctionCallStatement): IStatement {
        val newtarget = functionCallStatement.target.process(this)
        if(newtarget is IdentifierReference)
            functionCallStatement.target = newtarget
        functionCallStatement.arglist = functionCallStatement.arglist.map { it.process(this) }.toMutableList()
        return functionCallStatement
    }

    fun process(identifier: IdentifierReference): IExpression {
        // note: this is an identifier that is used in an expression.
        // other identifiers are simply part of the other statements (such as jumps, subroutine defs etc)
        return identifier
    }

    fun process(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val ident = jump.identifier.process(this)
            if(ident is IdentifierReference && ident!==jump.identifier) {
                return Jump(null, ident, null, jump.position)
            }
        }
        return jump
    }

    fun process(ifStatement: IfStatement): IStatement {
        ifStatement.condition = ifStatement.condition.process(this)
        ifStatement.truepart = ifStatement.truepart.process(this) as AnonymousScope
        ifStatement.elsepart = ifStatement.elsepart.process(this) as AnonymousScope
        return ifStatement
    }

    fun process(branchStatement: BranchStatement): IStatement {
        branchStatement.truepart = branchStatement.truepart.process(this) as AnonymousScope
        branchStatement.elsepart = branchStatement.elsepart.process(this) as AnonymousScope
        return branchStatement
    }

    fun process(range: RangeExpr): IExpression {
        range.from = range.from.process(this)
        range.to = range.to.process(this)
        range.step = range.step.process(this)
        return range
    }

    fun process(label: Label): IStatement {
        return label
    }

    fun process(literalValue: LiteralValue): LiteralValue {
        if(literalValue.arrayvalue!=null) {
            for(av in literalValue.arrayvalue.withIndex()) {
                val newvalue = av.value.process(this)
                literalValue.arrayvalue[av.index] = newvalue
            }
        }
        return literalValue
    }

    fun process(assignment: Assignment): IStatement {
        assignment.targets = assignment.targets.map { it.process(this) }
        assignment.value = assignment.value.process(this)
        return assignment
    }

    fun process(postIncrDecr: PostIncrDecr): IStatement {
        postIncrDecr.target = postIncrDecr.target.process(this)
        return postIncrDecr
    }

    fun process(contStmt: Continue): IStatement {
        return contStmt
    }

    fun process(breakStmt: Break): IStatement {
        return breakStmt
    }

    fun process(forLoop: ForLoop): IStatement {
        forLoop.loopVar?.process(this)
        forLoop.iterable = forLoop.iterable.process(this)
        forLoop.body = forLoop.body.process(this) as AnonymousScope
        return forLoop
    }

    fun process(whileLoop: WhileLoop): IStatement {
        whileLoop.condition = whileLoop.condition.process(this)
        whileLoop.body = whileLoop.body.process(this) as AnonymousScope
        return whileLoop
    }

    fun process(repeatLoop: RepeatLoop): IStatement {
        repeatLoop.untilCondition = repeatLoop.untilCondition.process(this)
        repeatLoop.body = repeatLoop.body.process(this) as AnonymousScope
        return repeatLoop
    }

    fun process(returnStmt: Return): IStatement {
        returnStmt.values = returnStmt.values.map { it.process(this) }
        return returnStmt
    }

    fun process(arrayIndexedExpression: ArrayIndexedExpression): IExpression {
        arrayIndexedExpression.identifier.process(this)
        arrayIndexedExpression.arrayspec.process(this)
        return arrayIndexedExpression
    }

    fun process(assignTarget: AssignTarget): AssignTarget {
        assignTarget.arrayindexed?.process(this)
        assignTarget.identifier?.process(this)
        assignTarget.memoryAddress?.process(this)
        return assignTarget
    }

    fun process(scope: AnonymousScope): IStatement {
        scope.statements = scope.statements.asSequence().map { it.process(this) }.toMutableList()
        return scope
    }

    fun process(typecast: TypecastExpression): IExpression {
        typecast.expression = typecast.expression.process(this)
        return typecast
    }

    fun process(memread: DirectMemoryRead): IExpression {
        memread.addressExpression = memread.addressExpression.process(this)
        return memread
    }

    fun process(memwrite: DirectMemoryWrite): IExpression {
        memwrite.addressExpression = memwrite.addressExpression.process(this)
        return memwrite
    }

    fun process(addressOf: AddressOf): IExpression {
        addressOf.identifier.process(this)
        return addressOf
    }

    fun process(inlineAssembly: InlineAssembly): IStatement {
        return inlineAssembly
    }
}
