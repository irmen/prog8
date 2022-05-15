package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.cpu6502.asmsub6502ArgsEvalOrder
import prog8.codegen.cpu6502.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.compiler.BuiltinFunctions

internal class StatementReorderer(val program: Program,
                                  val errors: IErrorReporter,
                                  private val options: CompilationOptions
) : AstWalker() {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - library blocks are put last.
    // - blocks are ordered by address, where blocks without address are placed last.
    // - in every block and module, most directives and vardecls are moved to the top. (not in subroutines!)
    // - the 'start' subroutine is moved to the top.
    // - (syntax desugaring) a vardecl with a non-const initializer value is split into a regular vardecl and an assignment statement.
    // - in-place assignments are reordered a bit so that they are mostly of the form A = A <operator> <rest>
    // - sorts the choices in when statement.
    // - insert AddressOf (&) expression where required (string params to a UWORD function param etc.).
    // - replace subroutine calls (statement) by just assigning the arguments to the parameters and then a GoSub to the routine.

    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%option")

    override fun after(module: Module, parent: Node): Iterable<IAstModification> {
        val (blocks, other) = module.statements.partition { it is Block }
        module.statements = other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: UInt.MAX_VALUE }).toMutableList()

        val mainBlock = module.statements.asSequence().filterIsInstance<Block>().firstOrNull { it.name=="main" }
        if(mainBlock!=null && mainBlock.address==null) {
            module.statements.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        directivesToTheTop(module.statements)
        return noModifications
    }

    private val declsProcessedWithInitAssignment = mutableSetOf<VarDecl>()

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if (decl.type == VarDeclType.VAR) {
            if (decl.datatype in NumericDatatypes) {
                if(decl !in declsProcessedWithInitAssignment) {
                    declsProcessedWithInitAssignment.add(decl)
                    if (decl.value == null) {
                        if (decl.origin==VarDeclOrigin.USERCODE && decl.allowInitializeWithZero) {
                            // A numeric vardecl without an initial value is initialized with zero,
                            // unless there's already an assignment below it, that initializes the value (or a for loop that uses it as loopvar).
                            // This allows you to restart the program and have the same starting values of the variables
                            // So basically consider 'ubyte xx' as a short form for 'ubyte xx; xx=0'
                            decl.value = null
                            if(decl.name.startsWith("tempvar_") && decl.definingScope.name=="prog8_lib") {
                                // no need to zero out the special internal temporary variables.
                                return noModifications
                            }
                            if(decl.findInitializer(program)!=null)
                                return noModifications   // an initializer assignment for a vardecl is already here
                            val nextFor = decl.nextSibling() as? ForLoop
                            val hasNextForWithThisLoopvar = nextFor?.loopVar?.nameInSource==listOf(decl.name)
                            if (!hasNextForWithThisLoopvar) {
                                // Add assignment to initialize with zero
                                // Note: for block-level vars, this will introduce assignments in the block scope. These have to be dealt with correctly later.
                                val identifier = IdentifierReference(listOf(decl.name), decl.position)
                                val assignzero = Assignment(AssignTarget(identifier, null, null, decl.position), decl.zeroElementValue(), AssignmentOrigin.VARINIT, decl.position)
                                return listOf(IAstModification.InsertAfter(
                                    decl, assignzero, parent as IStatementContainer
                                ))
                            }
                        }
                    } else {
                        // Transform the vardecl with initvalue to a plain vardecl + assignment
                        // this allows for other optimizations to kick in.
                        // So basically consider 'ubyte xx=99' as a short form for 'ubyte xx; xx=99'
                        val pos = decl.value!!.position
                        val identifier = IdentifierReference(listOf(decl.name), pos)
                        val assign = Assignment(AssignTarget(identifier, null, null, pos), decl.value!!, AssignmentOrigin.VARINIT, pos)
                        decl.value = null
                        return listOf(IAstModification.InsertAfter(
                            decl, assign, parent as IStatementContainer
                        ))
                    }
                }
            }
            else if(decl.datatype in ArrayDatatypes) {
                // only if the initializer expression is a reference to another array, split it into a separate assignment.
                // this is so that it later can be changed into a memcopy.
                // (that code only triggers on regular assignment, not on variable initializers)
                val ident = decl.value as? IdentifierReference
                if(ident!=null) {
                    val target = ident.targetVarDecl(program)
                    if(target!=null && target.isArray) {
                        val pos = decl.value!!.position
                        val identifier = IdentifierReference(listOf(decl.name), pos)
                        val assign = Assignment(AssignTarget(identifier, null, null, pos), decl.value!!, AssignmentOrigin.VARINIT, pos)
                        decl.value = null
                        return listOf(IAstModification.InsertAfter(
                            decl, assign, parent as IStatementContainer
                        ))
                    }
                }
            }
        }

        return noModifications
    }

    private fun directivesToTheTop(statements: MutableList<Statement>) {
        val directives = statements.filterIsInstance<Directive>().filter {it.directive in directivesToMove}
        statements.removeAll(directives)
        statements.addAll(0, directives)
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        parent as Module
        if(block.isInLibrary) {
            return listOf(
                    IAstModification.Remove(block, parent),
                    IAstModification.InsertLast(block, parent)
            )
        }

        directivesToTheTop(block.statements)
        return noModifications
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if(subroutine.name=="start" && parent is Block) {
            if(parent.statements.asSequence().filterIsInstance<Subroutine>().first().name!="start") {
                return listOf(
                        IAstModification.Remove(subroutine, parent),
                        IAstModification.InsertFirst(subroutine, parent)
                )
            }
        }

        val modifications = mutableListOf<IAstModification>()

        val subs = subroutine.statements.filterIsInstance<Subroutine>()
        if(subs.isNotEmpty()) {
            // all subroutines defined within this subroutine are moved to the end
            // NOTE: this doesn't check if this has already been done!!!
            modifications +=
                subs.map { IAstModification.Remove(it, subroutine) } +
                subs.map { IAstModification.InsertLast(it, subroutine) }
        }

        // change 'str' and 'ubyte[]' parameters into 'uword' (just treat it as an address)
        val stringParams = subroutine.parameters.filter { it.type==DataType.STR || it.type==DataType.ARRAY_UB }
        val parameterChanges = stringParams.map {
            val uwordParam = SubroutineParameter(it.name, DataType.UWORD, it.position)
            IAstModification.ReplaceNode(it, uwordParam, subroutine)
        }

        val varsChanges = mutableListOf<IAstModification>()
        if(!subroutine.isAsmSubroutine) {
            val stringParamsByNames = stringParams.associateBy { it.name }
            varsChanges +=
                if(stringParamsByNames.isNotEmpty()) {
                    subroutine.statements
                        .asSequence()
                        .filterIsInstance<VarDecl>()
                        .filter { it.subroutineParameter!=null && it.name in stringParamsByNames }
                        .map {
                            val newvar = VarDecl(it.type, it.origin, DataType.UWORD,
                                it.zeropage,
                                null,
                                it.name,
                                null,
                                false,
                                it.sharedWithAsm,
                                stringParamsByNames.getValue(it.name),
                                it.position
                            )
                            IAstModification.ReplaceNode(it, newvar, subroutine)
                        }
                }
                else emptySequence()
        }

        return modifications + parameterChanges + varsChanges
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        if(parent !is VarDecl) {
            // don't replace the initializer value in a vardecl - this will be moved to a separate
            // assignment statement soon in after(VarDecl)
            return replacePointerVarIndexWithMemreadOrMemwrite(program, arrayIndexedExpression, parent)
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {

        // ConstValue <associativeoperator> X -->  X <associativeoperator> ConstValue
        // (this should be done by the ExpressionSimplifier when optimizing is enabled,
        //  but the current assembly code generator for IF statements now also depends on it, so we do it here regardless of optimization.)
        if (expr.left.constValue(program) != null && expr.operator in AssociativeOperators && expr.right.constValue(program) == null)
            return listOf(IAstModification.SwapOperands(expr))

        // when using a simple bit shift and assigning it to a variable of a different type,
        // try to make the bit shifting 'wide enough' to fall into the variable's type.
        // with this, for instance, uword x = 1 << 10  will result in 1024 rather than 0 (the ubyte result).
        if(expr.operator=="<<" || expr.operator==">>") {
            val leftDt = expr.left.inferType(program)
            when (parent) {
                is Assignment -> {
                    val targetDt = parent.target.inferType(program)
                    if(leftDt != targetDt) {
                        val cast = TypecastExpression(expr.left, targetDt.getOr(DataType.UNDEFINED), true, parent.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                }
                is VarDecl -> {
                    if(leftDt isnot parent.datatype) {
                        val cast = TypecastExpression(expr.left, parent.datatype, true, parent.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                }
                is IFunctionCall -> {
                    val argnum = parent.args.indexOf(expr)
                    when (val callee = parent.target.targetStatement(program)) {
                        is Subroutine -> {
                            val paramType = callee.parameters[argnum].type
                            if(leftDt isAssignableTo paramType) {
                                val (replaced, cast) = expr.left.typecastTo(paramType, leftDt.getOr(DataType.UNDEFINED), true)
                                if(replaced)
                                    return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                            }
                        }
                        is BuiltinFunctionPlaceholder -> {
                            val func = BuiltinFunctions.getValue(callee.name)
                            val paramTypes = func.parameters[argnum].possibleDatatypes
                            for(type in paramTypes) {
                                if(leftDt isAssignableTo type) {
                                    val (replaced, cast) = expr.left.typecastTo(type, leftDt.getOr(DataType.UNDEFINED), true)
                                    if(replaced)
                                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                                }
                            }
                        }
                        else -> throw FatalAstException("weird callee")
                    }
                }
                else -> return noModifications
            }
        }
        else if(expr.operator in LogicalOperators) {
            // make sure that logical expressions like "var and other-logical-expression
            // is rewritten as "var!=0 and other-logical-expression", to avoid bitwise boolean and
            // generating the wrong results later

            fun wrapped(expr: Expression): Expression =
                BinaryExpression(expr, "!=", NumericLiteral(DataType.UBYTE, 0.0, expr.position), expr.position)

            fun isLogicalExpr(expr: Expression?): Boolean {
                if(expr is BinaryExpression && expr.operator in (LogicalOperators + ComparisonOperators))
                    return true
                if(expr is PrefixExpression && expr.operator in LogicalOperators)
                    return true
                return false
            }

            return if(isLogicalExpr(expr.left)) {
                if(isLogicalExpr(expr.right))
                    noModifications
                else
                    listOf(IAstModification.ReplaceNode(expr.right, wrapped(expr.right), expr))
            } else {
                if(isLogicalExpr(expr.right))
                    listOf(IAstModification.ReplaceNode(expr.left, wrapped(expr.left), expr))
                else {
                    listOf(
                        IAstModification.ReplaceNode(expr.left, wrapped(expr.left), expr),
                        IAstModification.ReplaceNode(expr.right, wrapped(expr.right), expr)
                    )
                }
            }
        }
        return noModifications
    }

    override fun after(whenStmt: When, parent: Node): Iterable<IAstModification> {
        val lastChoiceValues = whenStmt.choices.lastOrNull()?.values
        if(lastChoiceValues?.isNotEmpty()==true) {
            val elseChoice = whenStmt.choices.indexOfFirst { it.values==null || it.values?.isEmpty()==true }
            if(elseChoice>=0)
                errors.err("else choice must be the last one", whenStmt.choices[elseChoice].position)
        }

        val choices = whenStmt.choiceValues(program).sortedBy {
            it.first?.first() ?: Int.MAX_VALUE
        }
        whenStmt.choices.clear()
        choices.mapTo(whenStmt.choices) { it.second }
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program)

        if(targetType.isArray && valueType.isArray) {
            if (assignment.value is ArrayLiteral) {
                errors.err("cannot assign array literal here, use separate assignment per element", assignment.position)
            } else {
                return copyArrayValue(assignment)
            }
        }

        if(valueType.isString && (targetType istype DataType.STR || targetType istype DataType.ARRAY_B || targetType istype DataType.ARRAY_UB))
            return copyStringValue(assignment)

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // rewrite in-place assignment expressions a bit so that the assignment target usually is the leftmost operand
        val binExpr = assignment.value as? BinaryExpression
        if(binExpr!=null) {
            if(binExpr.left isSameAs assignment.target) {
                // A = A <operator> 5, unchanged
                return noModifications
            }

            if(binExpr.operator in AssociativeOperators) {
                if (binExpr.right isSameAs assignment.target) {
                    // A = v <associative-operator> A  ==>  A = A <associative-operator> v
                    return listOf(IAstModification.SwapOperands(binExpr))
                }

                val leftBinExpr = binExpr.left as? BinaryExpression
                if(leftBinExpr?.operator == binExpr.operator) {
                    return if(leftBinExpr.left isSameAs assignment.target) {
                        // A = (A <associative-operator> x) <same-operator> y ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(leftBinExpr.right, binExpr.operator, binExpr.right, binExpr.position)
                        val newValue = BinaryExpression(leftBinExpr.left, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    } else {
                        // A = (x <associative-operator> A) <same-operator> y ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(leftBinExpr.left, binExpr.operator, binExpr.right, binExpr.position)
                        val newValue = BinaryExpression(leftBinExpr.right, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    }
                }
                val rightBinExpr = binExpr.right as? BinaryExpression
                if(rightBinExpr?.operator == binExpr.operator) {
                    return if(rightBinExpr.left isSameAs assignment.target) {
                        // A = x <associative-operator> (A <same-operator> y) ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(binExpr.left, binExpr.operator, rightBinExpr.right, binExpr.position)
                        val newValue = BinaryExpression(rightBinExpr.left, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    } else {
                        // A = x <associative-operator> (y <same-operator> A) ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(binExpr.left, binExpr.operator, rightBinExpr.left, binExpr.position)
                        val newValue = BinaryExpression(rightBinExpr.right, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    }
                }
            }
        }

        return noModifications
    }

    private fun copyArrayValue(assign: Assignment): List<IAstModification> {
        val identifier = assign.target.identifier!!
        val targetVar = identifier.targetVarDecl(program)!!

        if(targetVar.arraysize==null) {
            errors.err("array has no defined size", assign.position)
            return noModifications
        }

        if(assign.value !is IdentifierReference) {
            errors.err("invalid array value to assign to other array", assign.value.position)
            return noModifications
        }
        val sourceIdent = assign.value as IdentifierReference
        val sourceVar = sourceIdent.targetVarDecl(program)!!
        if(!sourceVar.isArray) {
            errors.err("value must be an array", sourceIdent.position)
        } else {
            if (sourceVar.arraysize!!.constIndex() != targetVar.arraysize!!.constIndex())
                errors.err("element count mismatch", assign.position)
            if (sourceVar.datatype != targetVar.datatype)
                errors.err("element type mismatch", assign.position)
        }

        if(!errors.noErrors())
            return noModifications

        val numelements = targetVar.arraysize!!.constIndex()!!
        val eltsize = program.memsizer.memorySize(ArrayToElementTypes.getValue(sourceVar.datatype))
        val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assign.position),
            mutableListOf(
                AddressOf(sourceIdent, assign.position),
                AddressOf(identifier, assign.position),
                NumericLiteral.optimalInteger(numelements*eltsize, assign.position)
            ),
            true,
            assign.position
        )
        return listOf(IAstModification.ReplaceNode(assign, memcopy, assign.parent))
    }

    private fun copyStringValue(assign: Assignment): List<IAstModification> {
        val identifier = assign.target.identifier!!
        val strcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "internal_stringcopy"), assign.position),
            mutableListOf(
                assign.value as? IdentifierReference ?: assign.value,
                identifier
            ),
            true,
            assign.position
        )
        return listOf(IAstModification.ReplaceNode(assign, strcopy, assign.parent))
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        val function = functionCallStatement.target.targetStatement(program)
            ?: throw FatalAstException("no target for $functionCallStatement")
        checkUnusedReturnValues(functionCallStatement, function, program, errors)
        return tryReplaceCallWithGosub(functionCallStatement, parent, program, options)
    }
}


internal fun tryReplaceCallWithGosub(
    functionCallStatement: FunctionCallStatement,
    parent: Node,
    program: Program,
    options: CompilationOptions
): Iterable<IAstModification> {
    val callee = functionCallStatement.target.targetStatement(program)!!
    if(callee is Subroutine) {
        if(callee.inline)
            return emptyList()
        return if(callee.isAsmSubroutine) {
            if(options.compTarget.name==VMTarget.NAME)
                emptyList()
            else
                tryReplaceCallAsmSubWithGosub(functionCallStatement, parent, callee)
        }
        else
            tryReplaceCallNormalSubWithGosub(functionCallStatement, parent, callee, program)
    }
    return emptyList()
}

private fun tryReplaceCallNormalSubWithGosub(call: FunctionCallStatement, parent: Node, callee: Subroutine, program: Program): Iterable<IAstModification> {
    val noModifications = emptyList<IAstModification>()

    if(callee.parameters.isEmpty()) {
        // 0 params -> just GoSub
        return listOf(IAstModification.ReplaceNode(call, GoSub(call.target, call.position), parent))
    }

    if(callee.parameters.size==1) {
        if(callee.parameters[0].type in IntegerDatatypes) {
            // optimization: 1 integer param is passed via register(s) directly, not by assignment to param variable
            return noModifications
        }
    }
    else if(callee.parameters.size==2) {
        if(callee.parameters[0].type in ByteDatatypes && callee.parameters[1].type in ByteDatatypes) {
            // optimization: 2 simple byte param is passed via 2 registers directly, not by assignment to param variables
            return noModifications
        }
    }

    val assignParams =
        callee.parameters.zip(call.args).map {
            var argumentValue = it.second
            val paramIdentifier = IdentifierReference(callee.scopedName + it.first.name, argumentValue.position)
            val argDt = argumentValue.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
            if(argDt in ArrayDatatypes) {
                // pass the address of the array instead
                if(argumentValue is IdentifierReference)
                    argumentValue = AddressOf(argumentValue, argumentValue.position)
            }
            Assignment(AssignTarget(paramIdentifier, null, null, argumentValue.position), argumentValue, AssignmentOrigin.PARAMETERASSIGN, argumentValue.position)
        }
    val scope = AnonymousScope(assignParams.toMutableList(), call.position)
    scope.statements += GoSub(call.target, call.position)
    return listOf(IAstModification.ReplaceNode(call, scope, parent))
}

private fun tryReplaceCallAsmSubWithGosub(
    call: FunctionCallStatement,
    parent: Node,
    callee: Subroutine
): Iterable<IAstModification> {
    val noModifications = emptyList<IAstModification>()

    if(callee.parameters.isEmpty()) {
        // 0 params -> just GoSub
        val scope = AnonymousScope(mutableListOf(), call.position)
        if(callee.shouldSaveX()) {
            scope.statements += FunctionCallStatement(IdentifierReference(listOf("rsavex"), call.position), mutableListOf(), true, call.position)
        }
        scope.statements += GoSub(call.target, call.position)
        if(callee.shouldSaveX()) {
            scope.statements += FunctionCallStatement(IdentifierReference(listOf("rrestorex"), call.position), mutableListOf(), true, call.position)
        }
        return listOf(IAstModification.ReplaceNode(call, scope, parent))
    } else if(!asmsub6502ArgsHaveRegisterClobberRisk(call.args, callee.asmParameterRegisters)) {
        // No register clobber risk, let the asmgen assign values to the registers directly.
        // this is more efficient than first evaluating them to the stack.
        // As complex expressions will be flagged as a clobber-risk, these will be simplified below.
        return noModifications
    } else {
        // clobber risk; evaluate the arguments on the CPU stack first (in reverse order)...
        return makeGosubWithArgsViaCpuStack(call, call.position, parent, callee)
    }
}

private fun makeGosubWithArgsViaCpuStack(
    call: IFunctionCall,
    position: Position,
    parent: Node,
    callee: Subroutine
): Iterable<IAstModification> {

    fun popCall(targetName: List<String>, dt: DataType, position: Position): FunctionCallStatement {
        return FunctionCallStatement(
            IdentifierReference(listOf(if(dt in ByteDatatypes) "pop" else "popw"), position),
            mutableListOf(IdentifierReference(targetName, position)),
            true, position
        )
    }

    fun pushCall(value: Expression, dt: DataType, position: Position): FunctionCallStatement {
        val pushvalue = when(dt) {
            DataType.UBYTE, DataType.UWORD -> value
            in PassByReferenceDatatypes -> value
            DataType.BYTE -> TypecastExpression(value, DataType.UBYTE, true, position)
            DataType.WORD -> TypecastExpression(value, DataType.UWORD, true, position)
            else -> throw FatalAstException("invalid dt $dt    $value")
        }

        return FunctionCallStatement(
            IdentifierReference(listOf(if(dt in ByteDatatypes) "push" else "pushw"), position),
            mutableListOf(pushvalue),
            true, position
        )
    }

    val argOrder = asmsub6502ArgsEvalOrder(callee)
    val scope = AnonymousScope(mutableListOf(), position)
    if(callee.shouldSaveX()) {
        scope.statements += FunctionCallStatement(IdentifierReference(listOf("rsavex"), position), mutableListOf(), true, position)
    }
    argOrder.reversed().forEach {
        val arg = call.args[it]
        val param = callee.parameters[it]
        scope.statements += pushCall(arg, param.type, arg.position)
    }
    // ... and pop them off again into the registers.
    argOrder.forEach {
        val param = callee.parameters[it]
        val targetName = callee.scopedName + param.name
        scope.statements += popCall(targetName, param.type, position)
    }
    scope.statements += GoSub(call.target, position)
    if(callee.shouldSaveX()) {
        scope.statements += FunctionCallStatement(IdentifierReference(listOf("rrestorex"), position), mutableListOf(), true, position)
    }
    return listOf(IAstModification.ReplaceNode(call as Node, scope, parent))
}
