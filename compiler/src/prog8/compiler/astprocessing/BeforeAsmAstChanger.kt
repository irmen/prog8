package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.getTempVar
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import prog8.code.target.VMTarget

internal class BeforeAsmAstChanger(val program: Program,
                                   private val options: CompilationOptions,
                                   private val errors: IErrorReporter
) : AstWalker() {

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("break should have been replaced by goto $breakStmt")
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("while should have been converted to jumps")
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("do..until should have been converted to jumps")
    }

    override fun after(containment: ContainmentCheck, parent: Node): Iterable<IAstModification> {
        if(containment.iterable !is IdentifierReference)
            throw InternalCompilerException("iterable in containmentcheck should be identifier (referencing string or array)")
        return noModifications
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        // move all subroutines to the bottom of the block
        val subs = block.statements.filterIsInstance<Subroutine>()
        block.statements.removeAll(subs)
        block.statements.addAll(subs)

        // adjust global variables initialization
        if(options.dontReinitGlobals) {
            block.statements.asSequence().filterIsInstance<VarDecl>().forEach {
                if(it.type==VarDeclType.VAR) {
                    it.zeropage = ZeropageWish.NOT_IN_ZEROPAGE
                    it.findInitializer(program)?.let { initializer ->
                        it.value = initializer.value     // put the init value back into the vardecl
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(!options.dontReinitGlobals) {
            if (decl.type == VarDeclType.VAR && decl.value != null && decl.datatype in NumericDatatypes)
                throw InternalCompilerException("vardecls for variables, with initial numerical value, should have been rewritten as plain vardecl + assignment $decl")
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // Try to replace A = B <operator> Something  by A= B, A = A <operator> Something
        // this triggers the more efficent augmented assignment code generation more often.
        // But it can only be done if the target variable IS NOT OCCURRING AS AN OPERAND ITSELF.

        if(options.compTarget.name==VMTarget.NAME)   // don't apply this optimization for Vm target
            return noModifications

        if(!assignment.isAugmentable
            && assignment.target.identifier != null
            && !assignment.target.isIOAddress(options.compTarget.machine)) {
            val binExpr = assignment.value as? BinaryExpression

            if(binExpr!=null && binExpr.inferType(program) istype DataType.FLOAT && !options.optimizeFloatExpressions)
                return noModifications

            if (binExpr != null && binExpr.operator !in ComparisonOperators) {
                if (binExpr.left !is BinaryExpression) {
                    if (binExpr.right.referencesIdentifier(assignment.target.identifier!!.nameInSource)) {
                        // the right part of the expression contains the target variable itself.
                        // we can't 'split' it trivially because the variable will be changed halfway through.
                        if(binExpr.operator in AssociativeOperators) {
                            // A = <something-without-A>  <associativeoperator>  <otherthing-with-A>
                            // use the other part of the expression to split.
                            val sourceDt = binExpr.right.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
                            val (_, right) = binExpr.right.typecastTo(assignment.target.inferType(program).getOrElse { throw AssemblyError(
                                "unknown dt"
                            )
                            }, sourceDt, implicit=true)
                            val assignRight = Assignment(assignment.target, right, AssignmentOrigin.BEFOREASMGEN, assignment.position)
                            return listOf(
                                IAstModification.InsertBefore(assignment, assignRight, parent as IStatementContainer),
                                IAstModification.ReplaceNode(binExpr.right, binExpr.left, binExpr),
                                IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr)
                            )
                        }
                    } else {
                        val sourceDt = binExpr.left.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
                        val (_, left) = binExpr.left.typecastTo(assignment.target.inferType(program).getOrElse { throw AssemblyError(
                            "unknown dt"
                        )
                        }, sourceDt, implicit=true)
                        val assignLeft = Assignment(assignment.target, left, AssignmentOrigin.BEFOREASMGEN, assignment.position)
                        return listOf(
                            IAstModification.InsertBefore(assignment, assignLeft, parent as IStatementContainer),
                            IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr)
                        )
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        if(scope.statements.any { it is VarDecl || it is IStatementContainer })
            throw FatalAstException("anonymousscope may no longer contain any vardecls or subscopes")
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // Most code generation targets only support subroutine inlining on asmsub subroutines
        // So we reset the flag here to be sure it doesn't cause problems down the line in the codegen.
        if(!subroutine.isAsmSubroutine && options.compTarget.name!=VMTarget.NAME)
            subroutine.inline = false

        val mods = mutableListOf<IAstModification>()

        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernal routine.
        // and if an assembly block doesn't contain a rts/rti, and some other situations.
        if (!subroutine.isAsmSubroutine) {
            if(subroutine.statements.isEmpty() ||
                (!subroutine.hasRtsInAsm(options.compTarget)
                        && subroutine.statements.lastOrNull { it !is VarDecl } !is Return
                        && subroutine.statements.last() !is Subroutine
                        && subroutine.statements.last() !is Return)) {
                val returnStmt = Return(null, subroutine.position)
                mods += IAstModification.InsertLast(returnStmt, subroutine)
            }
        }

        // precede a subroutine with a return to avoid falling through into the subroutine from code above it
        val outerScope = subroutine.definingScope
        val outerStatements = outerScope.statements
        val subroutineStmtIdx = outerStatements.indexOf(subroutine)
        if (subroutineStmtIdx > 0) {
            val prevStmt = outerStatements[subroutineStmtIdx-1]
            if(outerScope !is Block
                && (prevStmt !is Jump)
                && prevStmt !is Subroutine
                && prevStmt !is Return
            ) {
                val returnStmt = Return(null, subroutine.position)
                mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope)
            }
        }

        if (!subroutine.inline || !options.optimize) {
            if (subroutine.isAsmSubroutine && subroutine.asmAddress==null && !subroutine.hasRtsInAsm(options.compTarget)) {
                // make sure the NOT INLINED asm subroutine actually has a rts at the end
                // (non-asm routines get a Return statement as needed, above)
                val instruction = if(options.compTarget.name==VMTarget.NAME) "  return\n" else "  rts\n"
                mods += IAstModification.InsertLast(InlineAssembly(instruction, Position.DUMMY), subroutine)
            }
        }

        return mods
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val binExpr = ifElse.condition as? BinaryExpression
        if(binExpr==null) {
            // if x  ->  if x!=0
            val booleanExpr = BinaryExpression(
                ifElse.condition,
                "!=",
                NumericLiteral.optimalInteger(0, ifElse.condition.position),
                ifElse.condition.position
            )
            return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
        }

        if(binExpr.operator !in ComparisonOperators) {
            val constRight = binExpr.right.constValue(program)
            if(constRight!=null) {
                if (binExpr.operator == "+") {
                    // if x+5  ->  if x != -5
                    val number = NumericLiteral(constRight.type, -constRight.number, constRight.position)
                    val booleanExpr = BinaryExpression(binExpr.left,"!=", number, ifElse.condition.position)
                    return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
                }
                else if (binExpr.operator == "-") {
                    // if x-5  ->  if x != 5
                    val number = NumericLiteral(constRight.type, constRight.number, constRight.position)
                    val booleanExpr = BinaryExpression(binExpr.left,"!=", number, ifElse.condition.position)
                    return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
                }
            }

            // if x*5  ->  if x*5 != 0
            val booleanExpr = BinaryExpression(
                ifElse.condition,
                "!=",
                NumericLiteral.optimalInteger(0, ifElse.condition.position),
                ifElse.condition.position
            )
            return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
        }

        if((binExpr.left as? NumericLiteral)?.number==0.0 &&
            (binExpr.right as? NumericLiteral)?.number!=0.0)
            throw InternalCompilerException("0==X should have been swapped to if X==0")

        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {

        val containingStatement = getContainingStatement(arrayIndexedExpression)
        if(getComplexArrayIndexedExpressions(containingStatement).size > 1) {
            errors.err("it's not possible to use more than one complex array indexing expression in a single statement; break it up via a temporary variable for instance", containingStatement.position)
            return noModifications
        }

        if(options.compTarget.name!=VMTarget.NAME) {    // don't apply this optimization for Vm target
            val index = arrayIndexedExpression.indexer.indexExpr
            if (index !is NumericLiteral && index !is IdentifierReference) {
                // replace complex indexing expression with a temp variable to hold the computed index first
                return getAutoIndexerVarFor(arrayIndexedExpression)
            }
        }

        return noModifications
    }

    private fun getComplexArrayIndexedExpressions(stmt: Statement): List<ArrayIndexedExpression> {

        class Searcher : IAstVisitor {
            val complexArrayIndexedExpressions = mutableListOf<ArrayIndexedExpression>()
            override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
                val ix = arrayIndexedExpression.indexer.indexExpr
                if(ix !is NumericLiteral && ix !is IdentifierReference)
                    complexArrayIndexedExpressions.add(arrayIndexedExpression)
            }

            override fun visit(branch: ConditionalBranch) {}

            override fun visit(forLoop: ForLoop) {}

            override fun visit(ifElse: IfElse) {
                ifElse.condition.accept(this)
            }

            override fun visit(untilLoop: UntilLoop) {
                untilLoop.condition.accept(this)
            }
        }

        val searcher = Searcher()
        stmt.accept(searcher)
        return searcher.complexArrayIndexedExpressions
    }

    private fun getContainingStatement(expression: Expression): Statement {
        var node: Node = expression
        while(node !is Statement)
            node = node.parent

        return node
    }

    private fun getAutoIndexerVarFor(expr: ArrayIndexedExpression): MutableList<IAstModification> {
        val modifications = mutableListOf<IAstModification>()
        val statement = expr.containingStatement
        val dt = expr.indexer.indexExpr.inferType(program)
        val (tempVarName, _) = program.getTempVar(dt.getOrElse { throw FatalAstException("invalid dt") })
        val target = AssignTarget(IdentifierReference(tempVarName, expr.indexer.position), null, null, expr.indexer.position)
        val assign = Assignment(target, expr.indexer.indexExpr, AssignmentOrigin.BEFOREASMGEN, expr.indexer.position)
        modifications.add(IAstModification.InsertBefore(statement, assign, statement.parent as IStatementContainer))
        modifications.add(
            IAstModification.ReplaceNode(
                expr.indexer.indexExpr,
                target.identifier!!.copy(),
                expr.indexer
            )
        )
        return modifications
    }
}
