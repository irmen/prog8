package prog8.ast

import prog8.compiler.CompilationOptions
import prog8.functions.BuiltinFunctionNames
import prog8.parser.ParsingFailedError

/**
 * General checks on the Ast
 */

fun Module.checkValid(globalNamespace: INameScope, compilerOptions: CompilationOptions) {
    val checker = AstChecker(globalNamespace, compilerOptions)
    this.process(checker)
    printErrors(checker.result(), name)
}


fun printErrors(errors: List<Any>, moduleName: String) {
    val reportedMessages = mutableSetOf<String>()
    errors.forEach {
        val msg = it.toString()
        if(!reportedMessages.contains(msg)) {
            System.err.println(msg)
            reportedMessages.add(msg)
        }
    }
    if(reportedMessages.isNotEmpty())
        throw ParsingFailedError("There are ${reportedMessages.size} errors in module '$moduleName'.")
}


fun printWarning(msg: String, position: Position, detailInfo: String?=null) {
    print("$position Warning: $msg")
    if(detailInfo==null)
        print("\n")
    else
        println(": $detailInfo")
}


/**
 * todo check subroutine call parameters against signature
 * todo check subroutine return values against the call's result assignments
 */

class AstChecker(private val namespace: INameScope, private val compilerOptions: CompilationOptions) : IAstProcessor {
    private val checkResult: MutableList<AstException> = mutableListOf()

    fun result(): List<AstException> {
        return checkResult
    }

    override fun process(module: Module) {
        super.process(module)
        val directives = module.statements.asSequence().filter { it is Directive }.groupBy { (it as Directive).directive }
        directives.filter { it.value.size > 1 }.forEach{ entry ->
            when(entry.key) {
                "%output", "%launcher", "%zeropage", "%address" ->
                    entry.value.mapTo(checkResult) { SyntaxError("directive can just occur once", it.position) }
            }
        }

        // there must be a 'main' block with a 'start' subroutine for the program entry point.
        val mainBlock = module.statements.singleOrNull { it is Block && it.name=="main" } as? Block?
        val startSub = mainBlock?.subScopes()?.get("start")
        if(startSub==null) {
            checkResult.add(SyntaxError("missing program entrypoint ('start' subroutine in 'main' block)", module.position))
        }
    }

    override fun process(forLoop: ForLoop): IStatement {
        if(forLoop.body.isEmpty())
            printWarning("for loop body is empty", forLoop.position)
        if(!forLoop.iterable.isIterable) {
            checkResult.add(ExpressionError("can only loop over an iterable type", forLoop.position))
        } else {
            val iterableDt = forLoop.iterable.resultingDatatype(namespace)
            if (forLoop.loopRegister != null) {
                printWarning("using a register as loop variable is risky (it could get clobbered in the body)", forLoop.position)
                // loop register
                when (forLoop.loopRegister) {
                    Register.A, Register.X, Register.Y -> {
                        if (iterableDt != DataType.BYTE && iterableDt!=DataType.ARRAY && iterableDt!=DataType.MATRIX &&
                                iterableDt != DataType.STR && iterableDt != DataType.STR_P &&
                                iterableDt != DataType.STR_S && iterableDt != DataType.STR_PS)
                            checkResult.add(ExpressionError("register can only loop over bytes", forLoop.position))
                    }
                    Register.AX, Register.AY, Register.XY -> {
                        if (iterableDt != DataType.WORD && iterableDt != DataType.BYTE &&
                                iterableDt != DataType.STR && iterableDt != DataType.STR_P &&
                                iterableDt != DataType.STR_S && iterableDt != DataType.STR_PS &&
                                iterableDt !=DataType.ARRAY && iterableDt!=DataType.ARRAY_W && iterableDt!=DataType.MATRIX)
                            checkResult.add(ExpressionError("register pair can only loop over words", forLoop.position))
                    }
                }
            } else {
                // loop variable
                val loopvar = forLoop.loopVar!!.targetStatement(namespace) as? VarDecl
                if(loopvar==null || loopvar.type==VarDeclType.CONST) {
                    checkResult.add(SyntaxError("for loop requires a variable to loop with", forLoop.position))

                } else {
                    when (loopvar.datatype) {
                        DataType.BYTE -> {
                            if(iterableDt!=DataType.BYTE && iterableDt!=DataType.ARRAY && iterableDt!=DataType.MATRIX &&
                                    iterableDt != DataType.STR && iterableDt != DataType.STR_P &&
                                    iterableDt != DataType.STR_S && iterableDt != DataType.STR_PS)
                                checkResult.add(ExpressionError("can only loop over bytes", forLoop.position))
                        }
                        DataType.WORD -> {
                            if(iterableDt!=DataType.BYTE && iterableDt!=DataType.WORD &&
                                    iterableDt !=DataType.ARRAY && iterableDt!=DataType.ARRAY_W && iterableDt!=DataType.MATRIX)
                                checkResult.add(ExpressionError("can only loop over bytes or words", forLoop.position))
                        }
                        else -> checkResult.add(ExpressionError("loop variable must be byte or word type", forLoop.position))
                    }
                }
            }
        }
        return super.process(forLoop)
    }

    override fun process(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val targetStatement = checkFunctionOrLabelExists(jump.identifier, jump)
            if(targetStatement!=null) {
                if(targetStatement is BuiltinFunctionStatementPlaceholder)
                    checkResult.add(SyntaxError("can't jump to a builtin function", jump.position))
            }
        }

        if(jump.address!=null && (jump.address < 0 || jump.address > 65535))
            checkResult.add(SyntaxError("jump address must be valid integer 0..\$ffff", jump.position))
        return super.process(jump)
    }

    override fun process(block: Block): IStatement {
        if(block.address!=null && (block.address<0 || block.address>65535)) {
            checkResult.add(SyntaxError("block memory address must be valid integer 0..\$ffff", block.position))
        }

        checkSubroutinesPrecededByReturnOrJumpAndFollowedByLabelOrSub(block.statements)
        return super.process(block)
    }

    override fun process(label: Label): IStatement {
        // scope check
        if(label.parent !is Block && label.parent !is Subroutine) {
            checkResult.add(SyntaxError("Labels can only be defined in the scope of a block or within another subroutine", label.position))
        }
        return super.process(label)
    }


    /**
     * Check subroutine definition
     */
    override fun process(subroutine: Subroutine): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, subroutine.position))
        }

        if(BuiltinFunctionNames.contains(subroutine.name))
            err("cannot redefine a built-in function")

        val uniqueNames = subroutine.parameters.asSequence().map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names should be unique")
        val uniqueParamRegs = subroutine.parameters.asSequence().map {it.register}.toSet()
        if(uniqueParamRegs.size!=subroutine.parameters.size)
            err("parameter registers should be unique")
        val uniqueResultRegisters = subroutine.returnvalues.asSequence().filter{it.register!=null}.map {it.register.toString()}.toMutableSet()
        uniqueResultRegisters.addAll(subroutine.returnvalues.asSequence().filter{it.statusflag!=null}.map{it.statusflag.toString()}.toList())
        if(uniqueResultRegisters.size!=subroutine.returnvalues.size)
            err("return registers should be unique")

        super.process(subroutine)
        checkSubroutinesPrecededByReturnOrJumpAndFollowedByLabelOrSub(subroutine.statements)

        // subroutine must contain at least one 'return' or 'goto'
        // (or if it has an asm block, that must contain a 'rts' or 'jmp')
        if(subroutine.statements.count { it is Return || it is Jump } == 0) {
            if(subroutine.address==null) {
            val amount = subroutine.statements
                    .asSequence()
                    .filter { it is InlineAssembly }
                    .map {(it as InlineAssembly).assembly}
                    .count { it.contains(" rts") || it.contains("\trts") ||
                             it.contains(" jmp") || it.contains("\tjmp")}
            if(amount==0 && subroutine.returnvalues.isNotEmpty())
                err("subroutine has result value(s) and thus must have at least one 'return' or 'goto' in it (or 'rts' / 'jmp' in case of %asm)")
                // @todo validate return values versus subroutine signature
            }
        }

        // scope check
        if(subroutine.parent !is Block && subroutine.parent !is Subroutine) {
            err("subroutines can only be defined in the scope of a block or within another subroutine")
        }

        return subroutine
    }

    private fun checkSubroutinesPrecededByReturnOrJumpAndFollowedByLabelOrSub(statements: MutableList<IStatement>) {
        // @todo hmm, or move all the subroutines at the end of the block? (no fall-through execution)
        var preceding: IStatement = BuiltinFunctionStatementPlaceholder
        var checkNext = false
        for (stmt in statements) {
            if(checkNext) {
                if(stmt !is Label && stmt !is Subroutine)
                    checkResult.add(SyntaxError("preceding subroutine definition at line ${preceding.position.line} should be followed here by a label, another subroutine statement, or nothing", stmt.position))
                return
            }
            if(stmt is Subroutine) {
                if(preceding !is Return
                        && preceding !is Jump
                        && preceding !is Subroutine
                        && preceding !is VarDecl
                        && preceding !is BuiltinFunctionStatementPlaceholder) {
                    checkResult.add(SyntaxError("subroutine definition should be preceded by a return, jump, vardecl, or another subroutine statement", stmt.position))
                }
                checkNext=true
            }
            preceding = stmt
        }
    }

    /**
     * Assignment target must be register, or a variable name
     * Also check data type compatibility
     */
    override fun process(assignment: Assignment): IStatement {
        if(assignment.target.identifier!=null) {
            val targetName = assignment.target.identifier!!.nameInSource
            val targetSymbol = namespace.lookup(targetName, assignment)
            when {
                targetSymbol == null -> {
                    checkResult.add(ExpressionError("undefined symbol: ${targetName.joinToString(".")}", assignment.position))
                    return super.process(assignment)
                }
                targetSymbol !is VarDecl -> {
                    checkResult.add(SyntaxError("assignment LHS must be register or variable", assignment.position))
                    return super.process(assignment)
                }
                targetSymbol.type == VarDeclType.CONST -> {
                    checkResult.add(ExpressionError("cannot assign new value to a constant", assignment.position))
                    return super.process(assignment)
                }
            }
        }

        if(assignment.aug_op!=null) {
            // check augmented assignment:
            // A /= 3  -> check is if it was A = A / 3
            val target: IExpression =
                    if(assignment.target.register!=null)
                        RegisterExpr(assignment.target.register!!, assignment.target.position)
                    else
                        assignment.target.identifier!!
            val expression = BinaryExpression(target, assignment.aug_op.substringBeforeLast('='), assignment.value, assignment.position)
            expression.linkParents(assignment.parent)
            val assignment2 = Assignment(assignment.target, null, expression, assignment.position)
            assignment2.linkParents(assignment.parent)
            return process(assignment2)
        }

        val targetDatatype = assignment.target.determineDatatype(namespace, assignment)
        val constVal = assignment.value.constValue(namespace)
        if(constVal!=null) {
            checkValueTypeAndRange(targetDatatype, null, assignment.value as LiteralValue)
        } else {
            val sourceDatatype: DataType? = assignment.value.resultingDatatype(namespace)
            if(sourceDatatype==null) {
                if(assignment.value is FunctionCall)
                    checkResult.add(ExpressionError("function call doesn't return a value to use in assignment", assignment.value.position))
                else
                    checkResult.add(ExpressionError("assignment source ${assignment.value} is no value or has no proper datatype", assignment.value.position))
            }
            else {
                checkAssignmentCompatible(targetDatatype, sourceDatatype, assignment.value, assignment.position)
            }
        }

        return super.process(assignment)
    }


    /**
     * Check the variable declarations (values within range etc)
     */
    override fun process(decl: VarDecl): IStatement {
        fun err(msg: String, position: Position?=null) {
            checkResult.add(SyntaxError(msg, position ?: decl.position))
        }

        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(decl.name) == true||
                decl.arrayspec?.x?.referencesIdentifier(decl.name) == true ||
                decl.arrayspec?.y?.referencesIdentifier(decl.name) == true) {
            err("recursive var declaration")
        }

        if(!compilerOptions.floats && decl.datatype==DataType.FLOAT) {
            err("float var/const declaration, but floating point is not enabled via options")
        }

        // for now, variables can only be declared in a block or subroutine (not in a loop statement block)  @todo fix this
        if(decl.parent !is Block && decl.parent !is Subroutine) {
            err ("variables must be declared at block or subroutine level")
        }

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                if (decl.value == null) {
                    if(decl.datatype == DataType.BYTE || decl.datatype==DataType.WORD || decl.datatype==DataType.FLOAT) {
                        // initialize numeric var with value zero by default.
                        val litVal = LiteralValue(DataType.BYTE, 0, position = decl.position)
                        litVal.parent = decl
                        decl.value = litVal
                    } else {
                        err("var/const declaration needs a compile-time constant initializer value for this type")
                    }
                    return super.process(decl)
                }
                when {
                    decl.value is RangeExpr -> checkValueTypeAndRange(decl.datatype, decl.arrayspec, decl.value as RangeExpr)
                    decl.value is LiteralValue -> checkValueTypeAndRange(decl.datatype, decl.arrayspec, decl.value as LiteralValue)
                    else -> {
                        err("var/const declaration needs a compile-time constant initializer value, or range, instead found: ${decl.value!!::class.simpleName}")
                        return super.process(decl)
                    }
                }
            }
            VarDeclType.MEMORY -> {
                if(decl.value !is LiteralValue) {
                    err("value of memory var decl is not a literal (it is a ${decl.value!!::class.simpleName}).", decl.value?.position)
                } else {
                    val value = decl.value as LiteralValue
                    if (value.asIntegerValue == null || value.asIntegerValue< 0 || value.asIntegerValue > 65535) {
                        err("memory address must be valid integer 0..\$ffff")
                    }
                }
            }
        }

        return super.process(decl)
    }

    /**
     * check the arguments of the directive
     */
    override fun process(directive: Directive): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, directive.position))
        }
        when(directive.directive) {
            "%output" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "raw" && directive.args[0].name != "prg")
                    err("invalid output directive type, expected raw or prg")
            }
            "%launcher" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "basic" && directive.args[0].name != "none")
                    err("invalid launcher directive type, expected basic or none")
            }
            "%zeropage" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 ||
                        directive.args[0].name != "basicsafe" &&
                        directive.args[0].name != "kernalsafe" &&
                        directive.args[0].name != "full")
                    err("invalid zp directive style, expected basicsafe, kernalsafe, or full")
            }
            "%address" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].int == null)
                    err("invalid address directive, expected numeric address argument")
            }
            "%import" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name==null)
                    err("invalid import directive, expected module name argument")
                if(directive.args[0].name == (directive.parent as Module).name)
                    err("invalid import directive, cannot import itself")
            }
            "%breakpoint" -> {
                if(directive.parent is Module) err("this directive may only occur in a block")
                if(directive.args.isNotEmpty())
                    err("invalid breakpoint directive, expected no arguments")
            }
            "%asminclude" -> {
                if(directive.parent is Module) err("this directive may only occur in a block")
                if(directive.args.size!=2 || directive.args[0].str==null || directive.args[1].name==null)
                    err("invalid asminclude directive, expected arguments: \"filename\", scopelabel")
            }
            "%asmbinary" -> {
                if(directive.parent is Module) err("this directive may only occur in a block")
                val errormsg = "invalid asmbinary directive, expected arguments: \"filename\" [, offset [, length ] ]"
                if(directive.args.isEmpty()) err(errormsg)
                if(directive.args.isNotEmpty() && directive.args[0].str==null) err(errormsg)
                if(directive.args.size>=2 && directive.args[1].int==null) err(errormsg)
                if(directive.args.size==3 && directive.args[2].int==null) err(errormsg)
                if(directive.args.size>3) err(errormsg)
            }
            "%option" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "enable_floats")
                    err("invalid option directive argument(s)")
            }
            else -> throw SyntaxError("invalid directive ${directive.directive}", directive.position)
        }
        return super.process(directive)
    }

    override fun process(literalValue: LiteralValue): LiteralValue {
        if(!compilerOptions.floats && literalValue.type==DataType.FLOAT) {
            checkResult.add(SyntaxError("floating point value used, but floating point is not enabled via options", literalValue.position))
        }
        checkValueTypeAndRange(literalValue.type, null, literalValue)
        return super.process(literalValue)
    }

    override fun process(range: RangeExpr): IExpression {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, range.position))
        }
        super.process(range)
        val from = range.from.constValue(namespace)
        val to = range.to.constValue(namespace)
        val stepLv = range.step.constValue(namespace) ?: LiteralValue(DataType.BYTE, 1, position = range.position)
        if (stepLv.asIntegerValue == null || stepLv.asIntegerValue == 0) {
            err("range step must be an integer != 0")
            return range
        }
        val step = stepLv.asIntegerValue
        if(from!=null && to != null) {
            when {
                from.asIntegerValue!=null && to.asIntegerValue!=null -> {
                    if(from.asIntegerValue == to.asIntegerValue)
                        printWarning("range is just a single value, don't use a loop here", range.position)
                    else if(from.asIntegerValue < to.asIntegerValue && step<=0)
                        err("ascending range requires step > 0")
                    else if(from.asIntegerValue > to.asIntegerValue && step>=0)
                        err("descending range requires step < 0")
                }
                from.strvalue!=null && to.strvalue!=null -> {
                    if(from.strvalue.length!=1 || to.strvalue.length!=1)
                        err("range from and to must be a single character")
                    if(from.strvalue[0] == to.strvalue[0])
                        printWarning("range contains just a single character", range.position)
                    else if(from.strvalue[0] < to.strvalue[0] && step<=0)
                        err("ascending range requires step > 0")
                    else if(from.strvalue[0] > to.strvalue[0] && step>=0)
                        err("descending range requires step < 0")
                }
                else -> err("range expression must be over integers or over characters")
            }
        }
        return range
    }

    override fun process(functionCall: FunctionCall): IExpression {
        // this function call is (part of) an expression, which should be in a statement somewhere.
        val stmtOfExpression = findParentNode<IStatement>(functionCall)
                ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCall.position}")

        val targetStatement = checkFunctionOrLabelExists(functionCall.target, stmtOfExpression)
        if(targetStatement!=null)
            checkBuiltinFunctionCall(functionCall, functionCall.position)
        return super.process(functionCall)
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
        val targetStatement = checkFunctionOrLabelExists(functionCall.target, functionCall)
        if(targetStatement!=null)
            checkBuiltinFunctionCall(functionCall, functionCall.position)
        return super.process(functionCall)
    }

    override fun process(postIncrDecr: PostIncrDecr): IStatement {
        if(postIncrDecr.target.register==null) {
            val targetName = postIncrDecr.target.identifier!!.nameInSource
            val target = namespace.lookup(targetName, postIncrDecr)
            if(target==null) {
                checkResult.add(SyntaxError("undefined symbol: ${targetName.joinToString(".")}", postIncrDecr.position))
            } else {
                if(target !is VarDecl || target.type==VarDeclType.CONST) {
                    checkResult.add(SyntaxError("can only increment or decrement a variable", postIncrDecr.position))
                } else if(target.datatype!=DataType.FLOAT && target.datatype!=DataType.WORD && target.datatype!=DataType.BYTE) {
                    checkResult.add(SyntaxError("can only increment or decrement a byte/float/word variable", postIncrDecr.position))
                }
            }
        }
        return super.process(postIncrDecr)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: IStatement): IStatement? {
        val targetStatement = target.targetStatement(namespace)
        if(targetStatement is Label || targetStatement is Subroutine || targetStatement is BuiltinFunctionStatementPlaceholder)
            return targetStatement
        checkResult.add(SyntaxError("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", statement.position))
        return null
    }

    private fun checkBuiltinFunctionCall(call: IFunctionCall, position: Position) {
        if(call.target.nameInSource.size==1 && BuiltinFunctionNames.contains(call.target.nameInSource[0])) {
            val functionName = call.target.nameInSource[0]
            if(functionName=="set_carry" || functionName=="set_irqd" || functionName=="clear_carry" || functionName=="clear_irqd") {
                // these functions have zero arguments
                if(call.arglist.isNotEmpty())
                    checkResult.add(SyntaxError("$functionName has zero arguments", position))
            }
        }
    }


    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArraySpec?, range: RangeExpr) : Boolean {
        val from = range.from.constValue(namespace)
        val to = range.to.constValue(namespace)
        if(from==null || to==null) {
            checkResult.add(SyntaxError("range from and to values must be constants", range.position))
            return false
        }

        when(targetDt) {
            DataType.BYTE, DataType.WORD, DataType.FLOAT -> {
                checkResult.add(SyntaxError("can't assign a range to a scalar type", range.position))
                return false
            }
            DataType.STR,
            DataType.STR_P,
            DataType.STR_S,
            DataType.STR_PS -> {
                // range check bytes (chars)
                if(!from.isString || !to.isString) {
                    checkResult.add(ExpressionError("range for string must have single characters from and to values", range.position))
                    return false
                }
                val rangeSize=range.size()
                if(rangeSize!=null && (rangeSize<0 || rangeSize>255)) {
                    checkResult.add(ExpressionError("size of range for string must be 0..255, instead of $rangeSize", range.position))
                    return false
                }
                return true
            }
            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> {
                // range and length check bytes
                val expectedSize = arrayspec!!.size()
                val rangeSize=range.size()
                if(rangeSize!=null && rangeSize != expectedSize) {
                    checkResult.add(ExpressionError("range size doesn't match array/matrix size, expected $expectedSize found $rangeSize", range.position))
                    return false
                }
                return true
            }
        }
    }

    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArraySpec?, value: LiteralValue) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(ExpressionError(msg, value.position))
            return false
        }
        when (targetDt) {
            DataType.FLOAT -> {
                val number = when(value.type) {
                    DataType.BYTE -> value.bytevalue!!.toDouble()
                    DataType.WORD -> value.wordvalue!!.toDouble()
                    DataType.FLOAT -> value.floatvalue!!
                    else -> return err("numeric value expected")
                }
                if (number > 1.7014118345e+38 || number < -1.7014118345e+38)
                    return err("value '$number' out of range for MFLPT format")
            }
            DataType.BYTE -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned byte integer value expected instead of float; possible loss of precision")
                else
                    err("unsigned byte integer value expected")
                if (number < 0 || number > 255)
                    return err("value '$number' out of range for unsigned byte")
            }
            DataType.WORD -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned word integer value expected instead of float; possible loss of precision")
                else
                    err("unsigned word integer value expected")
                if (number < 0 || number > 65535)
                    return err("value '$number' out of range for unsigned word")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                val str = value.strvalue
                        ?: return err("string value expected")
                if (str.isEmpty() || str.length > 255)
                    return err("string length must be 1 to 255")
            }
            DataType.ARRAY -> {
                // value may be either a single byte, or a byte array
                if(value.type==DataType.ARRAY) {
                    if(arrayspec!=null) {
                        // arrayspec is not always known when checking
                        val constX = arrayspec.x.constValue(namespace)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (value.arrayvalue!!.size != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got ${value.arrayvalue.size})")
                    }
                    for (av in value.arrayvalue!!) {
                        if(av is LiteralValue) {
                            val number = av.bytevalue
                                    ?: return err("array must be all bytes")
                            if (number < 0 || number > 255)
                                return err("value '$number' in array is out of range for unsigned byte")
                        } else if(av is RegisterExpr) {
                            if(av.register!=Register.A && av.register!=Register.X && av.register!=Register.Y)
                                return err("register '$av' in byte array is not a single register")
                        } else {
                            val avDt = av.resultingDatatype(namespace)
                            if(avDt!=DataType.BYTE)
                                return err("array must be all bytes")
                        }
                    }
                } else if(value.type==DataType.ARRAY_W) {
                    return err("initialization value must be an array of bytes")
                } else {
                    val number = value.bytevalue ?: return if (value.floatvalue!=null)
                        err("unsigned byte integer value expected instead of float; possible loss of precision")
                    else
                        err("unsigned byte integer value expected")
                    if (number < 0 || number > 255)
                        return err("value '$number' out of range for unsigned byte")
                }
            }
            DataType.ARRAY_W -> {
                // value may be either a single word, or a word array
                if(value.type==DataType.ARRAY || value.type==DataType.ARRAY_W) {
                    if(arrayspec!=null) {
                        // arrayspec is not always known when checking
                        val constX = arrayspec.x.constValue(namespace)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (value.arrayvalue!!.size != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got ${value.arrayvalue.size})")
                    }
                    for (av in value.arrayvalue!!) {
                        if(av is LiteralValue) {
                            val number = av.asIntegerValue
                                    ?: return err("array must be all integers")
                            if (number < 0 || number > 65535)
                                return err("value '$number' in array is out of range for unsigned word")
                        } else {
                            val avDt = av.resultingDatatype(namespace)
                            if(avDt!=DataType.BYTE && avDt!=DataType.WORD)
                                return err("array must be all integers")
                        }
                    }
                } else {
                    val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                        err("unsigned byte or word integer value expected instead of float; possible loss of precision")
                    else
                        err("unsigned byte or word integer value expected")
                    if (number < 0 || number > 65535)
                        return err("value '$number' out of range for unsigned word")
                }
            }
            DataType.MATRIX -> {
                // value can only be a single byte, or a byte array (which represents the matrix)
                if(value.type==DataType.ARRAY) {
                    for (av in value.arrayvalue!!) {
                        val number = (av as LiteralValue).bytevalue
                                ?: return err("array must be all bytes")
                        val constX = arrayspec!!.x.constValue(namespace)
                        val constY = arrayspec.y!!.constValue(namespace)
                        if(constX?.asIntegerValue==null || constY?.asIntegerValue==null)
                            return err("matrix size specifiers must be constant integer values")

                        val expectedSize = constX.asIntegerValue * constY.asIntegerValue
                        if (value.arrayvalue.size != expectedSize)
                            return err("initializer matrix size mismatch (expecting $expectedSize, got ${value.arrayvalue.size} elements)")

                        if (number < 0 || number > 255)
                            return err("value '$number' in byte array is out of range for unsigned byte")
                    }
                } else {
                    val number = value.bytevalue
                            ?: return err("unsigned byte integer value expected")
                    if (number < 0 || number > 255)
                        return err("value '$number' out of range for unsigned byte")
                }
            }
        }
        return true
    }

    private fun checkAssignmentCompatible(targetDatatype: DataType,
                                          sourceDatatype: DataType,
                                          sourceValue: IExpression,
                                          position: Position) : Boolean {

        if(sourceValue is RangeExpr)
            return checkValueTypeAndRange(targetDatatype, null, sourceValue)

        val result =  when(targetDatatype) {
            DataType.BYTE -> sourceDatatype==DataType.BYTE
            DataType.WORD -> sourceDatatype==DataType.BYTE || sourceDatatype==DataType.WORD
            DataType.FLOAT -> sourceDatatype==DataType.BYTE || sourceDatatype==DataType.WORD || sourceDatatype==DataType.FLOAT
            DataType.STR -> sourceDatatype==DataType.STR
            DataType.STR_P -> sourceDatatype==DataType.STR_P
            DataType.STR_S -> sourceDatatype==DataType.STR_S
            DataType.STR_PS -> sourceDatatype==DataType.STR_PS
            DataType.ARRAY -> sourceDatatype==DataType.ARRAY
            DataType.ARRAY_W -> sourceDatatype==DataType.ARRAY_W
            DataType.MATRIX -> sourceDatatype==DataType.MATRIX
        }

        if(result)
            return true

        if(sourceDatatype==DataType.WORD && targetDatatype==DataType.BYTE)
            checkResult.add(ExpressionError("cannot assign word to byte, use msb() or lsb()?", position))
        else if(sourceDatatype==DataType.FLOAT && (targetDatatype==DataType.BYTE || targetDatatype==DataType.WORD))
            checkResult.add(ExpressionError("cannot assign float to ${targetDatatype.toString().toLowerCase()}; possible loss of precision. Suggestion: round the value or revert to integer arithmetic", position))
        else
            checkResult.add(ExpressionError("cannot assign ${sourceDatatype.toString().toLowerCase()} to ${targetDatatype.toString().toLowerCase()}", position))

        return false
    }
}
