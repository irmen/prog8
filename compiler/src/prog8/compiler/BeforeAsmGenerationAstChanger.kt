package prog8.compiler

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.compiler.astprocessing.isSubroutineParameter
import prog8.codegen.target.AssemblyError
import prog8.compilerinterface.*
import prog8.optimizer.getTempVarName


internal class BeforeAsmGenerationAstChanger(val program: Program, private val options: CompilationOptions,
                                             private val errors: IErrorReporter) : AstWalker() {

    private val subroutineVariables = mutableMapOf<Subroutine, MutableList<Pair<String, VarDecl>>>()

    private fun rememberSubroutineVar(decl: VarDecl) {
        val sub = decl.definingSubroutine ?: return
        var varsList = subroutineVariables[sub]
        if(varsList==null) {
            varsList = mutableListOf()
            subroutineVariables[sub] = varsList
        }
        varsList.add(decl.name to decl)
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        throw FatalAstException("break should have been replaced by goto $breakStmt")
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        throw FatalAstException("while should have been converted to jumps")
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        throw FatalAstException("do..until should have been converted to jumps")
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        // move all subroutines to the bottom of the block
        val subs = block.statements.filterIsInstance<Subroutine>()
        block.statements.removeAll(subs)
        block.statements.addAll(subs)
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.type==VarDeclType.VAR && decl.value != null && decl.datatype in NumericDatatypes)
            throw FatalAstException("vardecls for variables, with initial numerical value, should have been rewritten as plain vardecl + assignment $decl")
        rememberSubroutineVar(decl)
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // Try to replace A = B <operator> Something  by A= B, A = A <operator> Something
        // this triggers the more efficent augmented assignment code generation more often.
        // But it can only be done if the target variable IS NOT OCCURRING AS AN OPERAND ITSELF.
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
                            val (_, right) = binExpr.right.typecastTo(assignment.target.inferType(program).getOrElse { throw AssemblyError("unknown dt") }, sourceDt, implicit=true)
                            val assignRight = Assignment(assignment.target, right, assignment.position)
                            return listOf(
                                    IAstModification.InsertBefore(assignment, assignRight, parent as IStatementContainer),
                                    IAstModification.ReplaceNode(binExpr.right, binExpr.left, binExpr),
                                    IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                        }
                    } else {
                        val sourceDt = binExpr.left.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
                        val (_, left) = binExpr.left.typecastTo(assignment.target.inferType(program).getOrElse { throw AssemblyError("unknown dt") }, sourceDt, implicit=true)
                        val assignLeft = Assignment(assignment.target, left, assignment.position)
                        return listOf(
                                IAstModification.InsertBefore(assignment, assignLeft, parent as IStatementContainer),
                                IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
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
        val firstDeclarations = mutableMapOf<String, VarDecl>()
        val rememberedSubroutineVars = subroutineVariables.getOrDefault(subroutine, mutableListOf())
        for(decl in rememberedSubroutineVars) {
            val existing = firstDeclarations[decl.first]
            if(existing!=null && existing !== decl.second) {
                errors.err("variable ${decl.first} already defined in subroutine ${subroutine.name} at ${existing.position}", decl.second.position)
            } else {
                firstDeclarations[decl.first] = decl.second
            }
        }
        rememberedSubroutineVars.clear()

        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernal routine.
        // and if an assembly block doesn't contain a rts/rti, and some other situations.
        val mods = mutableListOf<IAstModification>()
        val returnStmt = Return(null, subroutine.position)
        if (subroutine.asmAddress == null && !subroutine.inline) {
            if(subroutine.statements.isEmpty() ||
                (subroutine.amountOfRtsInAsm() == 0
                && subroutine.statements.lastOrNull { it !is VarDecl } !is Return
                && subroutine.statements.last() !is Subroutine)) {
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
                    && prevStmt !is Return) {
                mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope)
            }
        }
        return mods
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove redundant typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword
        // Also the special typecast of a reference type (str, array) to an UWORD will be changed into address-of,
        //   UNLESS it's a str parameter in the containing subroutine - then we remove the typecast altogether
        val sourceDt = typecast.expression.inferType(program).getOr(DataType.UNDEFINED)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
                || typecast.type in WordDatatypes && sourceDt in WordDatatypes) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }

        if(sourceDt in PassByReferenceDatatypes) {
            if(typecast.type==DataType.UWORD) {
                val identifier = typecast.expression as? IdentifierReference
                if(identifier!=null) {
                    return if(identifier.isSubroutineParameter(program)) {
                        listOf(IAstModification.ReplaceNode(
                            typecast,
                            typecast.expression,
                            parent
                        ))
                    } else {
                        listOf(IAstModification.ReplaceNode(
                            typecast,
                            AddressOf(identifier, typecast.position),
                            parent
                        ))
                    }
                } else if(typecast.expression is IFunctionCall) {
                    return listOf(IAstModification.ReplaceNode(
                            typecast,
                            typecast.expression,
                            parent
                    ))
                }
            } else {
                errors.err("cannot cast pass-by-reference value to type ${typecast.type} (only to UWORD)", typecast.position)
            }
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val prefixExpr = ifElse.condition as? PrefixExpression
        if(prefixExpr!=null && prefixExpr.operator=="not") {
            // if not x -> if x==0
            val booleanExpr = BinaryExpression(prefixExpr.expression, "==", NumericLiteralValue.optimalInteger(0, ifElse.condition.position), ifElse.condition.position)
            return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
        }

        val binExpr = ifElse.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in ComparisonOperators) {
            // if x  ->  if x!=0,    if x+5  ->  if x+5 != 0
            val booleanExpr = BinaryExpression(ifElse.condition, "!=", NumericLiteralValue.optimalInteger(0, ifElse.condition.position), ifElse.condition.position)
            return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
        }

        if((binExpr.left as? NumericLiteralValue)?.number==0.0 &&
            (binExpr.right as? NumericLiteralValue)?.number!=0.0)
            throw FatalAstException("0==X should have been swapped to if X==0")

        // simplify the conditional expression, introduce simple assignments if required.
        // NOTE: sometimes this increases code size because additional stores/loads are generated for the
        //       intermediate variables. We assume these are optimized away from the resulting assembly code later.
        val simplify = simplifyConditionalExpression(binExpr)
        val modifications = mutableListOf<IAstModification>()
        if(simplify.rightVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.right, simplify.rightOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(ifElse, simplify.rightVarAssignment, parent as IStatementContainer)
        }
        if(simplify.leftVarAssignment!=null) {
            modifications += IAstModification.ReplaceNode(binExpr.left, simplify.leftOperandReplacement!!, binExpr)
            modifications += IAstModification.InsertBefore(ifElse, simplify.leftVarAssignment, parent as IStatementContainer)
        }

        return modifications
    }

    private class CondExprSimplificationResult(
        val leftVarAssignment: Assignment?,
        val leftOperandReplacement: Expression?,
        val rightVarAssignment: Assignment?,
        val rightOperandReplacement: Expression?
    )

    private fun simplifyConditionalExpression(expr: BinaryExpression): CondExprSimplificationResult {

        // TODO: somehow figure out if the expr will result in stack-evaluation STILL after being split off,
        //       in that case: do *not* split it off but just keep it as it is (otherwise code size increases)
        // TODO: do NOT move this to an earler ast transform phase (such as StatementReorderer or StatementOptimizer) - it WILL result in larger code.
        // TODO: this should be replaced by a general expression-evaluation optimization step.
        //       the actual conditional expression in the statement should be no more than VARIABLE <COMPARISON-OPERATOR> SIMPLE-EXPRESSION

        var leftAssignment: Assignment? = null
        var leftOperandReplacement: Expression? = null
        var rightAssignment: Assignment? = null
        var rightOperandReplacement: Expression? = null

        val separateLeftExpr = !expr.left.isSimple && expr.left !is IFunctionCall
        val separateRightExpr = !expr.right.isSimple && expr.right !is IFunctionCall
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)

        if(!leftDt.isInteger || !rightDt.isInteger) {
            // we can't reasonably simplify non-integer expressions
            return CondExprSimplificationResult(null, null, null, null)
        }

        if(separateLeftExpr) {
            val name = getTempVarName(leftDt)
            leftOperandReplacement = IdentifierReference(name, expr.position)
            leftAssignment = Assignment(
                AssignTarget(IdentifierReference(name, expr.position), null, null, expr.position),
                expr.left,
                expr.position
            )
        }
        if(separateRightExpr) {
            val name = when {
                rightDt istype DataType.UBYTE -> listOf("prog8_lib","retval_interm_ub")
                rightDt istype DataType.UWORD -> listOf("prog8_lib","retval_interm_uw")
                rightDt istype DataType.BYTE -> listOf("prog8_lib","retval_interm_b2")
                rightDt istype DataType.WORD -> listOf("prog8_lib","retval_interm_w2")
                else -> throw AssemblyError("invalid dt")
            }
            rightOperandReplacement = IdentifierReference(name, expr.position)
            rightAssignment = Assignment(
                AssignTarget(IdentifierReference(name, expr.position), null, null, expr.position),
                expr.right,
                expr.position
            )
        }
        return CondExprSimplificationResult(
            leftAssignment, leftOperandReplacement,
            rightAssignment, rightOperandReplacement
        )
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource==listOf("cmp")) {
            // if the datatype of the arguments of cmp() are different, cast the byte one to word.
            val arg1 = functionCallStatement.args[0]
            val arg2 = functionCallStatement.args[1]
            val dt1 = arg1.inferType(program).getOr(DataType.UNDEFINED)
            val dt2 = arg2.inferType(program).getOr(DataType.UNDEFINED)
            if(dt1 in ByteDatatypes) {
                if(dt2 in ByteDatatypes)
                    return noModifications
                val (replaced, cast) = arg1.typecastTo(if(dt1==DataType.UBYTE) DataType.UWORD else DataType.WORD, dt1, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg1, cast, functionCallStatement))
            } else {
                if(dt2 in WordDatatypes)
                    return noModifications
                val (replaced, cast) = arg2.typecastTo(if(dt2==DataType.UBYTE) DataType.UWORD else DataType.WORD, dt2, true)
                if(replaced)
                    return listOf(IAstModification.ReplaceNode(arg2, cast, functionCallStatement))
            }
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {

        val containingStatement = getContainingStatement(arrayIndexedExpression)
        if(getComplexArrayIndexedExpressions(containingStatement).size > 1) {
            errors.err("it's not possible to use more than one complex array indexing expression in a single statement; break it up via a temporary variable for instance", containingStatement.position)
            return noModifications
        }


        val index = arrayIndexedExpression.indexer.indexExpr
        if(index !is NumericLiteralValue && index !is IdentifierReference) {
            // replace complex indexing expression with a temp variable to hold the computed index first
            return getAutoIndexerVarFor(arrayIndexedExpression)
        }

        return noModifications
    }

    private fun getComplexArrayIndexedExpressions(stmt: Statement): List<ArrayIndexedExpression> {

        class Searcher : IAstVisitor {
            val complexArrayIndexedExpressions = mutableListOf<ArrayIndexedExpression>()
            override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
                val ix = arrayIndexedExpression.indexer.indexExpr
                if(ix !is NumericLiteralValue && ix !is IdentifierReference)
                    complexArrayIndexedExpressions.add(arrayIndexedExpression)
            }

            override fun visit(branch: Branch) {}

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
        val tempvar = if(dt.isBytes) listOf("prog8_lib","retval_interm_ub") else listOf("prog8_lib","retval_interm_b")
        val target = AssignTarget(IdentifierReference(tempvar, expr.indexer.position), null, null, expr.indexer.position)
        val assign = Assignment(target, expr.indexer.indexExpr, expr.indexer.position)
        modifications.add(IAstModification.InsertBefore(statement, assign, statement.parent as IStatementContainer))
        modifications.add(IAstModification.ReplaceNode(expr.indexer.indexExpr, target.identifier!!.copy(), expr.indexer))
        return modifications
    }

}
