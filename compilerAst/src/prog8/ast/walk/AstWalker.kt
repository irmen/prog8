package prog8.ast.walk

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*


interface IAstModification {
    fun perform()

    class Remove(val node: Node, val parent: INameScope) : IAstModification {
        override fun perform() {
            if (!parent.statements.remove(node) && parent !is GlobalNamespace)
                throw FatalAstException("attempt to remove non-existing node $node")
        }
    }

    class SetExpression(private val setter: (newExpr: Expression) -> Unit, private val newExpr: Expression, private val parent: Node) :
        IAstModification {
        override fun perform() {
            setter(newExpr)
            newExpr.linkParents(parent)
        }
    }

    class InsertFirst(private val stmt: Statement, private val parent: INameScope) : IAstModification {
        override fun perform() {
            parent.statements.add(0, stmt)
            stmt.linkParents(parent as Node)
        }
    }

    class InsertLast(private val stmt: Statement, private val parent: INameScope) : IAstModification {
        override fun perform() {
            parent.statements.add(stmt)
            stmt.linkParents(parent as Node)
        }
    }

    class InsertAfter(private val after: Statement, private val stmt: Statement, private val parent: INameScope) :
        IAstModification {
        override fun perform() {
            val idx = parent.statements.indexOfFirst { it===after } + 1
            parent.statements.add(idx, stmt)
            stmt.linkParents(parent as Node)
        }
    }

    class InsertBefore(private val before: Statement, private val stmt: Statement, private val parent: INameScope) :
        IAstModification {
        override fun perform() {
            val idx = parent.statements.indexOfFirst { it===before }
            parent.statements.add(idx, stmt)
            stmt.linkParents(parent as Node)
        }
    }

    class ReplaceNode(val node: Node, private val replacement: Node, private val parent: Node) :
        IAstModification {
        override fun perform() {
            parent.replaceChildNode(node, replacement)
            replacement.linkParents(parent)
        }
    }

    class SwapOperands(private val expr: BinaryExpression): IAstModification {
        override fun perform() {
            require(expr.operator in associativeOperators)
            val tmp = expr.left
            expr.left = expr.right
            expr.right = tmp
        }
    }
}


abstract class AstWalker {
    protected val noModifications = emptyList<IAstModification>()

    open fun before(addressOf: AddressOf, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(assignTarget: AssignTarget, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(block: Block, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(branchStatement: BranchStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(directive: Directive, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(expr: PrefixExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(forLoop: ForLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(ifStatement: IfStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(inlineAssembly: InlineAssembly, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(jump: Jump, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(label: Label, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(module: Module, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(nopStatement: NopStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(numLiteral: NumericLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(postIncrDecr: PostIncrDecr, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(program: Program, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(range: RangeExpr, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(returnStmt: Return, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(scope: AnonymousScope, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(string: StringLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> = noModifications

    open fun after(addressOf: AddressOf, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(assignTarget: AssignTarget, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(block: Block, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(branchStatement: BranchStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(breakStmt: Break, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(builtinFunctionStatementPlaceholder: BuiltinFunctionStatementPlaceholder, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(directive: Directive, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(expr: PrefixExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(forLoop: ForLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(ifStatement: IfStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(inlineAssembly: InlineAssembly, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(jump: Jump, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(label: Label, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(memwrite: DirectMemoryWrite, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(module: Module, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(nopStatement: NopStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(numLiteral: NumericLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(postIncrDecr: PostIncrDecr, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(program: Program, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(range: RangeExpr, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(returnStmt: Return, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(char: CharLiteral, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(string: StringLiteralValue, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> = noModifications
    open fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> = noModifications

    protected val modifications = mutableListOf<Triple<IAstModification, Node, Node>>()

    private fun track(mods: Iterable<IAstModification>, node: Node, parent: Node) {
        for (it in mods) {
//            if(it is IAstModification.ReplaceNode) {
//                val replaceKey = Pair(it.node, it.node.position)
//                if(replaceKey in modificationsReplacedNodes)
//                    throw FatalAstException("there already is a node replacement for $replaceKey - optimizer can't deal with multiple replacements for same node yet. Split the ast modification?")
//                else
//                    modificationsReplacedNodes.add(replaceKey)
//            }
            modifications += Triple(it, node, parent)
        }
    }

    fun applyModifications(): Int {
        // check if there are double removes, keep only the last one
        val removals = modifications.filter { it.first is IAstModification.Remove }
        if(removals.isNotEmpty()) {
            val doubles = removals.groupBy { (it.first as IAstModification.Remove).node }.filter { it.value.size>1 }
            doubles.forEach {
                for(doubleRemove in it.value.dropLast(1)) {
                    if(!modifications.removeIf { mod-> mod.first === doubleRemove.first })
                        throw FatalAstException("ast remove problem")
                }
            }
        }

        modifications.forEach {
            it.first.perform()
        }
        val amount = modifications.size
        modifications.clear()
        return amount
    }

    fun visit(program: Program) {
        track(before(program, program), program, program)
        program.modules.forEach { it.accept(this, program) }
        track(after(program, program), program, program)
    }

    fun visit(module: Module, parent: Node) {
        track(before(module, parent), module, parent)
        module.statements.forEach{ it.accept(this, module) }
        track(after(module, parent), module, parent)
    }

    fun visit(expr: PrefixExpression, parent: Node) {
        track(before(expr, parent), expr, parent)
        expr.expression.accept(this, expr)
        track(after(expr, parent), expr, parent)
    }

    fun visit(expr: BinaryExpression, parent: Node) {
        track(before(expr, parent), expr, parent)
        expr.left.accept(this, expr)
        expr.right.accept(this, expr)
        track(after(expr, parent), expr, parent)
    }

    fun visit(directive: Directive, parent: Node) {
        track(before(directive, parent), directive, parent)
        track(after(directive, parent), directive, parent)
    }

    fun visit(block: Block, parent: Node) {
        track(before(block, parent), block, parent)
        block.statements.forEach { it.accept(this, block) }
        track(after(block, parent), block, parent)
    }

    fun visit(decl: VarDecl, parent: Node) {
        track(before(decl, parent), decl, parent)
        decl.value?.accept(this, decl)
        decl.arraysize?.accept(this, decl)
        track(after(decl, parent), decl, parent)
    }

    fun visit(subroutine: Subroutine, parent: Node) {
        track(before(subroutine, parent), subroutine, parent)
        subroutine.statements.forEach { it.accept(this, subroutine) }
        track(after(subroutine, parent), subroutine, parent)
    }

    fun visit(functionCall: FunctionCall, parent: Node) {
        track(before(functionCall, parent), functionCall, parent)
        functionCall.target.accept(this, functionCall)
        functionCall.args.forEach { it.accept(this, functionCall) }
        track(after(functionCall, parent), functionCall, parent)
    }

    fun visit(functionCallStatement: FunctionCallStatement, parent: Node) {
        track(before(functionCallStatement, parent), functionCallStatement, parent)
        functionCallStatement.target.accept(this, functionCallStatement)
        functionCallStatement.args.forEach { it.accept(this, functionCallStatement) }
        track(after(functionCallStatement, parent), functionCallStatement, parent)
    }

    fun visit(identifier: IdentifierReference, parent: Node) {
        track(before(identifier, parent), identifier, parent)
        track(after(identifier, parent), identifier, parent)
    }

    fun visit(jump: Jump, parent: Node) {
        track(before(jump, parent), jump, parent)
        jump.identifier?.accept(this, jump)
        track(after(jump, parent), jump, parent)
    }

    fun visit(ifStatement: IfStatement, parent: Node) {
        track(before(ifStatement, parent), ifStatement, parent)
        ifStatement.condition.accept(this, ifStatement)
        ifStatement.truepart.accept(this, ifStatement)
        ifStatement.elsepart.accept(this, ifStatement)
        track(after(ifStatement, parent), ifStatement, parent)
    }

    fun visit(branchStatement: BranchStatement, parent: Node) {
        track(before(branchStatement, parent), branchStatement, parent)
        branchStatement.truepart.accept(this, branchStatement)
        branchStatement.elsepart.accept(this, branchStatement)
        track(after(branchStatement, parent), branchStatement, parent)
    }

    fun visit(range: RangeExpr, parent: Node) {
        track(before(range, parent), range, parent)
        range.from.accept(this, range)
        range.to.accept(this, range)
        range.step.accept(this, range)
        track(after(range, parent), range, parent)
    }

    fun visit(label: Label, parent: Node) {
        track(before(label, parent), label, parent)
        track(after(label, parent), label, parent)
    }

    fun visit(numLiteral: NumericLiteralValue, parent: Node) {
        track(before(numLiteral, parent), numLiteral, parent)
        track(after(numLiteral, parent), numLiteral, parent)
    }

    fun visit(char: CharLiteral, parent: Node) {
        track(before(char, parent), char, parent)
        track(after(char, parent), char, parent)
    }

    fun visit(string: StringLiteralValue, parent: Node) {
        track(before(string, parent), string, parent)
        track(after(string, parent), string, parent)
    }

    fun visit(array: ArrayLiteralValue, parent: Node) {
        track(before(array, parent), array, parent)
        array.value.forEach { v->v.accept(this, array) }
        track(after(array, parent), array, parent)
    }

    fun visit(assignment: Assignment, parent: Node) {
        track(before(assignment, parent), assignment, parent)
        assignment.target.accept(this, assignment)
        assignment.value.accept(this, assignment)
        track(after(assignment, parent), assignment, parent)
    }

    fun visit(postIncrDecr: PostIncrDecr, parent: Node) {
        track(before(postIncrDecr, parent), postIncrDecr, parent)
        postIncrDecr.target.accept(this, postIncrDecr)
        track(after(postIncrDecr, parent), postIncrDecr, parent)
    }

    fun visit(breakStmt: Break, parent: Node) {
        track(before(breakStmt, parent), breakStmt, parent)
        track(after(breakStmt, parent), breakStmt, parent)
    }

    fun visit(forLoop: ForLoop, parent: Node) {
        track(before(forLoop, parent), forLoop, parent)
        forLoop.loopVar.accept(this, forLoop)
        forLoop.iterable.accept(this, forLoop)
        forLoop.body.accept(this, forLoop)
        track(after(forLoop, parent), forLoop, parent)
    }

    fun visit(whileLoop: WhileLoop, parent: Node) {
        track(before(whileLoop, parent), whileLoop, parent)
        whileLoop.condition.accept(this, whileLoop)
        whileLoop.body.accept(this, whileLoop)
        track(after(whileLoop, parent), whileLoop, parent)
    }

    fun visit(repeatLoop: RepeatLoop, parent: Node) {
        track(before(repeatLoop, parent), repeatLoop, parent)
        repeatLoop.iterations?.accept(this, repeatLoop)
        repeatLoop.body.accept(this, repeatLoop)
        track(after(repeatLoop, parent), repeatLoop, parent)
    }

    fun visit(untilLoop: UntilLoop, parent: Node) {
        track(before(untilLoop, parent), untilLoop, parent)
        untilLoop.condition.accept(this, untilLoop)
        untilLoop.body.accept(this, untilLoop)
        track(after(untilLoop, parent), untilLoop, parent)
    }

    fun visit(returnStmt: Return, parent: Node) {
        track(before(returnStmt, parent), returnStmt, parent)
        returnStmt.value?.accept(this, returnStmt)
        track(after(returnStmt, parent), returnStmt, parent)
    }

    fun visit(arrayIndexedExpression: ArrayIndexedExpression, parent: Node) {
        track(before(arrayIndexedExpression, parent), arrayIndexedExpression, parent)
        arrayIndexedExpression.arrayvar.accept(this, arrayIndexedExpression)
        arrayIndexedExpression.indexer.accept(this, arrayIndexedExpression)
        track(after(arrayIndexedExpression, parent), arrayIndexedExpression, parent)
    }

    fun visit(assignTarget: AssignTarget, parent: Node) {
        track(before(assignTarget, parent), assignTarget, parent)
        assignTarget.arrayindexed?.accept(this, assignTarget)
        assignTarget.identifier?.accept(this, assignTarget)
        assignTarget.memoryAddress?.accept(this, assignTarget)
        track(after(assignTarget, parent), assignTarget, parent)
    }

    fun visit(scope: AnonymousScope, parent: Node) {
        track(before(scope, parent), scope, parent)
        scope.statements.forEach { it.accept(this, scope) }
        track(after(scope, parent), scope, parent)
    }

    fun visit(typecast: TypecastExpression, parent: Node) {
        track(before(typecast, parent), typecast, parent)
        typecast.expression.accept(this, typecast)
        track(after(typecast, parent), typecast, parent)
    }

    fun visit(memread: DirectMemoryRead, parent: Node) {
        track(before(memread, parent), memread, parent)
        memread.addressExpression.accept(this, memread)
        track(after(memread, parent), memread, parent)
    }

    fun visit(memwrite: DirectMemoryWrite, parent: Node) {
        track(before(memwrite, parent), memwrite, parent)
        memwrite.addressExpression.accept(this, memwrite)
        track(after(memwrite, parent), memwrite, parent)
    }

    fun visit(addressOf: AddressOf, parent: Node) {
        track(before(addressOf, parent), addressOf, parent)
        addressOf.identifier.accept(this, addressOf)
        track(after(addressOf, parent), addressOf, parent)
    }

    fun visit(inlineAssembly: InlineAssembly, parent: Node) {
        track(before(inlineAssembly, parent), inlineAssembly, parent)
        track(after(inlineAssembly, parent), inlineAssembly, parent)
    }

    fun visit(nopStatement: NopStatement, parent: Node) {
        track(before(nopStatement, parent), nopStatement, parent)
        track(after(nopStatement, parent), nopStatement, parent)
    }

    fun visit(whenStatement: WhenStatement, parent: Node) {
        track(before(whenStatement, parent), whenStatement, parent)
        whenStatement.condition.accept(this, whenStatement)
        whenStatement.choices.forEach { it.accept(this, whenStatement) }
        track(after(whenStatement, parent), whenStatement, parent)
    }

    fun visit(whenChoice: WhenChoice, parent: Node) {
        track(before(whenChoice, parent), whenChoice, parent)
        whenChoice.values?.forEach { it.accept(this, whenChoice) }
        whenChoice.statements.accept(this, whenChoice)
        track(after(whenChoice, parent), whenChoice, parent)
    }
}

