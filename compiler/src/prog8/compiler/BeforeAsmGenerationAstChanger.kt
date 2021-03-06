package prog8.compiler

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.compiler.target.ICompilationTarget


internal class BeforeAsmGenerationAstChanger(val program: Program, val errors: IErrorReporter, private val compTarget: ICompilationTarget) : AstWalker() {

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        subroutineVariables.add(decl.name to decl)
        if (decl.value == null && !decl.autogeneratedDontRemove && decl.type == VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            // A numeric vardecl without an initial value is initialized with zero,
            // unless there's already an assignment below, that initializes the value.
            // This allows you to restart the program and have the same starting values of the variables
            if(decl.allowInitializeWithZero)
            {
                val nextAssign = decl.definingScope().nextSibling(decl) as? Assignment
                if (nextAssign != null && nextAssign.target isSameAs IdentifierReference(listOf(decl.name), Position.DUMMY))
                    decl.value = null
                else {
                    decl.value = decl.zeroElementValue()
                }
            }
        }
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // Try to replace A = B <operator> Something  by A= B, A = A <operator> Something
        // this triggers the more efficent augmented assignment code generation more often.
        // But it can only be done if the target variable IS NOT OCCURRING AS AN OPERAND ITSELF.
        if(!assignment.isAugmentable
                && assignment.target.identifier != null
                && compTarget.isInRegularRAM(assignment.target, program)) {
            val binExpr = assignment.value as? BinaryExpression
            if (binExpr != null && binExpr.operator !in comparisonOperators) {
                if (binExpr.left !is BinaryExpression) {
                    if (binExpr.right.referencesIdentifier(*assignment.target.identifier!!.nameInSource.toTypedArray())) {
                        // the right part of the expression contains the target variable itself.
                        // we can't 'split' it trivially because the variable will be changed halfway through.
                        if(binExpr.operator in associativeOperators) {
                            // A = <something-without-A>  <associativeoperator>  <otherthing-with-A>
                            // use the other part of the expression to split.
                            val assignRight = Assignment(assignment.target, binExpr.right, assignment.position)
                            return listOf(
                                    IAstModification.InsertBefore(assignment, assignRight, assignment.definingScope()),
                                    IAstModification.ReplaceNode(binExpr.right, binExpr.left, binExpr),
                                    IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                        }
                    } else {
                        val assignLeft = Assignment(assignment.target, binExpr.left, assignment.position)
                        return listOf(
                                IAstModification.InsertBefore(assignment, assignLeft, assignment.definingScope()),
                                IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                    }
                }
            }
        }
        return noModifications
    }

    private val subroutineVariables = mutableListOf<Pair<String, VarDecl>>()
    private val addedIfConditionVars = mutableSetOf<Pair<Subroutine, String>>()

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        subroutineVariables.clear()
        addedIfConditionVars.clear()
        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        val decls = scope.statements.filterIsInstance<VarDecl>().filter { it.type == VarDeclType.VAR }
        subroutineVariables.addAll(decls.map { it.name to it })

        val sub = scope.definingSubroutine()
        if (sub != null) {
            // move any remaining vardecls of the scope into the upper scope. Make sure the position remains the same!
            val replacements = mutableListOf<IAstModification>()
            val movements = mutableListOf<IAstModification.InsertFirst>()

            for(decl in decls) {
                if(decl.value!=null && decl.datatype in NumericDatatypes) {
                    val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                    val assign = Assignment(target, decl.value!!, decl.position)
                    replacements.add(IAstModification.ReplaceNode(decl, assign, scope))
                    decl.value = null
                    decl.allowInitializeWithZero = false
                } else {
                    replacements.add(IAstModification.Remove(decl, scope))
                }
                movements.add(IAstModification.InsertFirst(decl, sub))
            }
            return replacements + movements
        }
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val firstDeclarations = mutableMapOf<String, VarDecl>()
        for(decl in subroutineVariables) {
            val existing = firstDeclarations[decl.first]
            if(existing!=null && existing !== decl.second) {
                errors.err("variable ${decl.first} already defined in subroutine ${subroutine.name} at ${existing.position}", decl.second.position)
            } else {
                firstDeclarations[decl.first] = decl.second
            }
        }


        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernal routine.
        // and if an assembly block doesn't contain a rts/rti, and some other situations.
        val mods = mutableListOf<IAstModification>()
        val returnStmt = Return(null, subroutine.position)
        if (subroutine.asmAddress == null
                && !subroutine.inline
                && subroutine.statements.isNotEmpty()
                && subroutine.amountOfRtsInAsm() == 0
                && subroutine.statements.lastOrNull { it !is VarDecl } !is Return
                && subroutine.statements.last() !is Subroutine) {
            mods += IAstModification.InsertLast(returnStmt, subroutine)
        }

        // precede a subroutine with a return to avoid falling through into the subroutine from code above it
        val outerScope = subroutine.definingScope()
        val outerStatements = outerScope.statements
        val subroutineStmtIdx = outerStatements.indexOf(subroutine)
        if (subroutineStmtIdx > 0
                && outerStatements[subroutineStmtIdx - 1] !is Jump
                && outerStatements[subroutineStmtIdx - 1] !is Subroutine
                && outerStatements[subroutineStmtIdx - 1] !is Return
                && outerScope !is Block) {
            mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope)
        }
        return mods
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove superfluous typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword
        // Also the special typecast of a reference type (str, array) to an UWORD will be changed into address-of.
        val sourceDt = typecast.expression.inferType(program).typeOrElse(DataType.UNDEFINED)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
                || typecast.type in WordDatatypes && sourceDt in WordDatatypes) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }


        // Note: for various reasons (most importantly, code simplicity), the code generator assumes/requires
        // that the types of assignment values and their target are the same,
        // and that the types of both operands of a binaryexpression node are the same.
        // So, it is not easily possible to remove the typecasts that are there to make these conditions true.
        // The only place for now where we can do this is for:
        //    asmsub register pair parameter.

        if(sourceDt in PassByReferenceDatatypes) {
            if(typecast.type==DataType.UWORD) {
                if(typecast.expression is IdentifierReference) {
                    return listOf(IAstModification.ReplaceNode(
                            typecast,
                            AddressOf(typecast.expression as IdentifierReference, typecast.position),
                            parent
                    ))
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

    override fun after(ifStatement: IfStatement, parent: Node): Iterable<IAstModification> {
        val binExpr = ifStatement.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // if x  ->  if x!=0,    if x+5  ->  if x+5 != 0
            val booleanExpr = BinaryExpression(ifStatement.condition, "!=", NumericLiteralValue.optimalInteger(0, ifStatement.condition.position), ifStatement.condition.position)
            return listOf(IAstModification.ReplaceNode(ifStatement.condition, booleanExpr, ifStatement))
        }

        if((binExpr.operator=="==" || binExpr.operator=="!=") &&
            (binExpr.left as? NumericLiteralValue)?.number==0 &&
            (binExpr.right as? NumericLiteralValue)?.number!=0)
            throw CompilerException("if 0==X should have been swapped to if X==0")

        // split the conditional expression into separate variables if the operand(s) is not simple.
        // DISABLED FOR NOW AS IT GENEREATES LARGER CODE IN THE SIMPLE CASES LIKE    IF X {...}  or  IF NOT X {...}
//        val modifications = mutableListOf<IAstModification>()
//        if(!binExpr.left.isSimple) {
//            val sub = binExpr.definingSubroutine()!!
//            val (variable, isNew, assignment) = addIfOperandVar(sub, "left", binExpr.left)
//            if(isNew)
//                modifications.add(IAstModification.InsertFirst(variable, sub))
//            modifications.add(IAstModification.InsertBefore(ifStatement, assignment, parent as INameScope))
//            modifications.add(IAstModification.ReplaceNode(binExpr.left, IdentifierReference(listOf(variable.name), binExpr.position), binExpr))
//            addedIfConditionVars.add(Pair(sub, variable.name))
//        }
//        if(!binExpr.right.isSimple) {
//            val sub = binExpr.definingSubroutine()!!
//            val (variable, isNew, assignment) = addIfOperandVar(sub, "right", binExpr.right)
//            if(isNew)
//                modifications.add(IAstModification.InsertFirst(variable, sub))
//            modifications.add(IAstModification.InsertBefore(ifStatement, assignment, parent as INameScope))
//            modifications.add(IAstModification.ReplaceNode(binExpr.right, IdentifierReference(listOf(variable.name), binExpr.position), binExpr))
//            addedIfConditionVars.add(Pair(sub, variable.name))
//        }
//        return modifications
        return noModifications
    }

//    private fun addIfOperandVar(sub: Subroutine, side: String, operand: Expression): Triple<VarDecl, Boolean, Assignment> {
//        val dt = operand.inferType(program).typeOrElse(DataType.UNDEFINED)
//        val varname = "prog8_ifvar_${side}_${dt.name.toLowerCase()}"
//        val tgt = AssignTarget(IdentifierReference(listOf(varname), operand.position), null, null, operand.position)
//        val assign = Assignment(tgt, operand, operand.position)
//        if(Pair(sub, varname) in addedIfConditionVars) {
//            val vardecl = VarDecl(VarDeclType.VAR, dt, ZeropageWish.DONTCARE, null, varname, null, null, false, true, operand.position)
//            return Triple(vardecl, false, assign)
//        }
//        val existing = sub.statements.firstOrNull { it is VarDecl && it.name == varname} as VarDecl?
//        return if (existing == null) {
//            val vardecl = VarDecl(VarDeclType.VAR, dt, ZeropageWish.DONTCARE, null, varname, null, null, false, true, operand.position)
//            Triple(vardecl, true, assign)
//        } else {
//            Triple(existing, false, assign)
//        }
//    }

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        val binExpr = untilLoop.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // until x  ->  until x!=0,    until x+5  ->  until x+5 != 0
            val booleanExpr = BinaryExpression(untilLoop.condition, "!=", NumericLiteralValue.optimalInteger(0, untilLoop.condition.position), untilLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(untilLoop.condition, booleanExpr, untilLoop))
        }
        return noModifications
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        val binExpr = whileLoop.condition as? BinaryExpression
        if(binExpr==null || binExpr.operator !in comparisonOperators) {
            // while x  ->  while x!=0,    while x+5  ->  while x+5 != 0
            val booleanExpr = BinaryExpression(whileLoop.condition, "!=", NumericLiteralValue.optimalInteger(0, whileLoop.condition.position), whileLoop.condition.position)
            return listOf(IAstModification.ReplaceNode(whileLoop.condition, booleanExpr, whileLoop))
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        if(functionCallStatement.target.nameInSource==listOf("cmp")) {
            // if the datatype of the arguments of cmp() are different, cast the byte one to word.
            val arg1 = functionCallStatement.args[0]
            val arg2 = functionCallStatement.args[1]
            val dt1 = arg1.inferType(program).typeOrElse(DataType.UNDEFINED)
            val dt2 = arg2.inferType(program).typeOrElse(DataType.UNDEFINED)
            if(dt1 in ByteDatatypes) {
                if(dt2 in ByteDatatypes)
                    return noModifications
                val cast1 = TypecastExpression(arg1, if(dt1==DataType.UBYTE) DataType.UWORD else DataType.WORD, true, functionCallStatement.position)
                return listOf(IAstModification.ReplaceNode(arg1, cast1, functionCallStatement))
            } else {
                if(dt2 in WordDatatypes)
                    return noModifications
                val cast2 = TypecastExpression(arg2, if(dt2==DataType.UBYTE) DataType.UWORD else DataType.WORD, true, functionCallStatement.position)
                return listOf(IAstModification.ReplaceNode(arg2, cast2, functionCallStatement))
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

            override fun visit(branchStatement: BranchStatement) {}

            override fun visit(forLoop: ForLoop) {}

            override fun visit(ifStatement: IfStatement) {
                ifStatement.condition.accept(this)
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
        val statement = expr.containingStatement()
        val dt = expr.indexer.indexExpr.inferType(program)
        val register = if(dt.istype(DataType.UBYTE) || dt.istype(DataType.BYTE)) "r9L" else "r9"
        // replace the indexer with just the variable (simply use a cx16 virtual register r9, that we HOPE is not used for other things in the expression...)
        // assign the indexing expression to the helper variable, but only if that hasn't been done already
        val target = AssignTarget(IdentifierReference(listOf("cx16", register), expr.indexer.position), null, null, expr.indexer.position)
        val assign = Assignment(target, expr.indexer.indexExpr, expr.indexer.position)
        modifications.add(IAstModification.InsertBefore(statement, assign, statement.definingScope()))
        modifications.add(IAstModification.ReplaceNode(expr.indexer.indexExpr, target.identifier!!.copy(), expr.indexer))
        return modifications
    }

}
