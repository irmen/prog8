package prog8.ast

import prog8.compiler.CompilationOptions
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.FLOAT_MAX_POSITIVE
import prog8.functions.BuiltinFunctions
import prog8.parser.ParsingFailedError

/**
 * General checks on the Ast
 */

fun Module.checkValid(globalNamespace: INameScope, compilerOptions: CompilationOptions, heap: HeapValues) {
    val checker = AstChecker(globalNamespace, compilerOptions, heap)
    this.process(checker)
    printErrors(checker.result(), name)
}


fun printErrors(errors: List<Any>, moduleName: String) {
    val reportedMessages = mutableSetOf<String>()
    errors.forEach {
        val msg = it.toString()
        if(msg !in reportedMessages) {
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


class AstChecker(private val namespace: INameScope,
                 private val compilerOptions: CompilationOptions,
                 private val heap: HeapValues) : IAstProcessor {
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
        val startSub = mainBlock?.subScopes()?.get("start") as? Subroutine
        if(startSub==null) {
            checkResult.add(SyntaxError("missing program entrypoint ('start' subroutine in 'main' block)", module.position))
        } else {
            if(startSub.parameters.isNotEmpty() || startSub.returntypes.isNotEmpty())
                checkResult.add(SyntaxError("program entrypoint subroutine can't have parameters and/or return values", startSub.position))
        }

        // there can be an optional 'irq' block with a 'irq' subroutine in it,
        // which will be used as the 60hz irq routine in the vm if it's present.
        val irqBlock = module.statements.singleOrNull { it is Block && it.name=="irq" } as? Block?
        val irqSub = irqBlock?.subScopes()?.get("irq") as? Subroutine
        if(irqSub!=null) {
            if(irqSub.parameters.isNotEmpty() || irqSub.returntypes.isNotEmpty())
                checkResult.add(SyntaxError("irq entrypoint subroutine can't have parameters and/or return values", irqSub.position))
        }
    }

    override fun process(returnStmt: Return): IStatement {
        val expectedReturnValues = (returnStmt.definingScope() as? Subroutine)?.returntypes ?: emptyList()
        if(expectedReturnValues.size != returnStmt.values.size) {
            // if the return value is a function call, check the result of that call instead
            if(returnStmt.values.size==1 && returnStmt.values[0] is FunctionCall) {
                val dt = (returnStmt.values[0] as FunctionCall).resultingDatatype(namespace, heap)
                if(dt!=null && expectedReturnValues.isEmpty())
                    checkResult.add(SyntaxError("number of return values doesn't match subroutine return spec", returnStmt.position))
            } else
                checkResult.add(SyntaxError("number of return values doesn't match subroutine return spec", returnStmt.position))
        }

        for (rv in expectedReturnValues.withIndex().zip(returnStmt.values)) {
            if(rv.first.value!=rv.second.resultingDatatype(namespace, heap))
                checkResult.add(ExpressionError("type of return value #${rv.first.index+1} doesn't match subroutine return type ${rv.first.value}", rv.second.position))
        }
        return super.process(returnStmt)
    }

    override fun process(forLoop: ForLoop): IStatement {
        if(forLoop.body.isEmpty())
            printWarning("for loop body is empty", forLoop.position)

        if(forLoop.iterable is LiteralValue)
            checkResult.add(SyntaxError("currently not possible to loop over a literal value directly, use a variable instead", forLoop.position))      // todo loop over literals

        if(!forLoop.iterable.isIterable(namespace, heap)) {
            checkResult.add(ExpressionError("can only loop over an iterable type", forLoop.position))
        } else {
            val iterableDt = forLoop.iterable.resultingDatatype(namespace, heap)
            if (forLoop.loopRegister != null) {
                printWarning("using a register as loop variable is risky (it could get clobbered in the body)", forLoop.position)
                // loop register
                when (forLoop.loopRegister) {
                    Register.A, Register.X, Register.Y -> {
                        if (iterableDt != DataType.UBYTE && iterableDt!=DataType.ARRAY_UB && iterableDt!=DataType.MATRIX_UB && iterableDt !in StringDatatypes)
                            checkResult.add(ExpressionError("register can only loop over bytes", forLoop.position))
                    }
                    Register.AX, Register.AY, Register.XY -> {
                        if (iterableDt != DataType.UWORD && iterableDt != DataType.UBYTE && iterableDt !in StringDatatypes &&
                                iterableDt !=DataType.ARRAY_UB && iterableDt!=DataType.ARRAY_UW && iterableDt!=DataType.MATRIX_UB)
                            checkResult.add(ExpressionError("register pair can only loop over bytes or words", forLoop.position))
                    }
                }
            } else {
                // loop variable
                val loopvar = forLoop.loopVar!!.targetStatement(namespace) as? VarDecl
                if(loopvar==null || loopvar.type==VarDeclType.CONST) {
                    checkResult.add(SyntaxError("for loop requires a variable to loop with", forLoop.position))

                } else {
                    when (loopvar.datatype) {
                        DataType.UBYTE -> {
                            if(iterableDt!=DataType.UBYTE && iterableDt!=DataType.ARRAY_UB && iterableDt!=DataType.MATRIX_UB && iterableDt !in StringDatatypes)
                                checkResult.add(ExpressionError("byte loop variable can only loop over bytes", forLoop.position))
                        }
                        DataType.UWORD -> {
                            if(iterableDt!=DataType.UBYTE && iterableDt!=DataType.UWORD && iterableDt !in StringDatatypes &&
                                    iterableDt !=DataType.ARRAY_UB && iterableDt!=DataType.ARRAY_UW && iterableDt!=DataType.MATRIX_UB)
                                checkResult.add(ExpressionError("word loop variable can only loop over bytes or words", forLoop.position))
                        }
                        // there's no support for a floating-point loop variable
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

        return super.process(block)
    }

    override fun process(label: Label): IStatement {
        // scope check
        if(label.parent !is Block && label.parent !is Subroutine && label.parent !is AnonymousScope) {
            checkResult.add(SyntaxError("Labels can only be defined in the scope of a block, a loop body, or within another subroutine", label.position))
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

        if(subroutine.name in BuiltinFunctions)
            err("cannot redefine a built-in function")

        val uniqueNames = subroutine.parameters.asSequence().map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names must be unique")

        super.process(subroutine)

        // user-defined subroutines can only have zero or one return type
        if(!subroutine.isAsmSubroutine && subroutine.returntypes.size>1)
            err("subroutine has more than one return value")

        // subroutine must contain at least one 'return' or 'goto'
        // (or if it has an asm block, that must contain a 'rts' or 'jmp')
        if(subroutine.statements.count { it is Return || it is Jump } == 0) {
            val amount = subroutine.statements
                    .asSequence()
                    .filter { it is InlineAssembly }
                    .map { (it as InlineAssembly).assembly }
                    .count { "rts" in it || "\trts" in it || "jmp" in it || "\tjmp" in it }
            if (amount == 0) {
                if(subroutine.returntypes.isNotEmpty()) {
                    // for asm subroutines with an address, no statement check is possible.
                    if(subroutine.asmAddress==null)
                        err("subroutine has result value(s) and thus must have at least one 'return' or 'goto' in it (or 'rts' / 'jmp' in case of %asm)")
                }
                // if there's no return statement, we add the implicit one at the end, but only if it's not a kernel routine.
                if(subroutine.asmAddress==null)
                    subroutine.statements.add(Return(emptyList(), subroutine.position))
            }
        }

        // scope check
        if(subroutine.parent !is Block && subroutine.parent !is Subroutine) {
            err("subroutines can only be defined in the scope of a block or within another subroutine")
        }

        if(subroutine.isAsmSubroutine) {
            if(subroutine.asmParameterRegisters.size != subroutine.parameters.size)
                err("number of asm parameter registers is not the same as number of parameters")
            if(subroutine.asmReturnvaluesRegisters.size != subroutine.returntypes.size)
                err("number of return registers is not the same as number of return values")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                if(param.second.register==Register.A || param.second.register==Register.X ||
                        param.second.register==Register.Y || param.second.statusflag!=null) {
                    if(param.first.type!=DataType.UBYTE)
                        err("parameter '${param.first.name}' should be ubyte")
                }
                if(param.second.register==Register.AX || param.second.register==Register.AY ||
                        param.second.register==Register.XY) {
                    if(param.first.type!=DataType.UWORD && param.first.type !in StringDatatypes && param.first.type !in ArrayDatatypes)
                        err("parameter '${param.first.name}' should be uword/str/array")
                }
            }
            for(ret in subroutine.returntypes.withIndex().zip(subroutine.asmReturnvaluesRegisters)) {
                if(ret.second.register==Register.A || ret.second.register==Register.X ||
                        ret.second.register==Register.Y || ret.second.statusflag!=null) {
                    if(ret.first.value!=DataType.UBYTE)
                        err("return value #${ret.first.index+1} should be ubyte")
                }
                if(ret.second.register==Register.AX || ret.second.register==Register.AY ||
                        ret.second.register==Register.XY) {
                    if(ret.first.value!=DataType.UWORD && ret.first.value != DataType.UBYTE &&
                            ret.first.value !in StringDatatypes && ret.first.value !in ArrayDatatypes)
                        err("return value #${ret.first.index+1} should be uword/ubyte/string/array")
                }
            }

            val regCounts = mutableMapOf<Register, Int>().withDefault { 0 }
            val statusflagCounts = mutableMapOf<Statusflag, Int>().withDefault { 0 }
            fun countRegisters(from: Iterable<RegisterOrStatusflag>) {
                regCounts.clear()
                statusflagCounts.clear()
                for(p in from) {
                    if (p.register != null) {
                        when(p.register) {
                            Register.A, Register.X, Register.Y -> regCounts[p.register] = regCounts.getValue(p.register) + 1
                            Register.AX -> {
                                regCounts[Register.A] = regCounts.getValue(Register.A) + 1
                                regCounts[Register.X] = regCounts.getValue(Register.X) + 1
                            }
                            Register.AY -> {
                                regCounts[Register.A] = regCounts.getValue(Register.A) + 1
                                regCounts[Register.Y] = regCounts.getValue(Register.Y) + 1
                            }
                            Register.XY -> {
                                regCounts[Register.X] = regCounts.getValue(Register.X) + 1
                                regCounts[Register.Y] = regCounts.getValue(Register.Y) + 1
                            }
                        }
                    }
                    else if(p.statusflag!=null)
                        statusflagCounts[p.statusflag] = statusflagCounts.getValue(p.statusflag) + 1
                }
            }
            countRegisters(subroutine.asmParameterRegisters)
            if(regCounts.any{it.value>1})
                err("a register is used multiple times in the parameters")
            if(statusflagCounts.any{it.value>1})
                err("a status flag is used multiple times in the parameters")
            countRegisters(subroutine.asmReturnvaluesRegisters)
            if(regCounts.any{it.value>1})
                err("a register is used multiple times in the return values")
            if(statusflagCounts.any{it.value>1})
                err("a status flag is used multiple times in the return values")

            if(subroutine.asmClobbers.intersect(regCounts.keys).isNotEmpty())
                err("a return register is also in the clobber list")
        }
        return subroutine
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

        // it is not possible to assign a new array to something.
        if(assignment.value.resultingDatatype(namespace, heap) in ArrayDatatypes)
            checkResult.add(SyntaxError("it's not possible to assign an array literal value to something, use it as a variable decl initializer instead", assignment.position))

        if(assignment.aug_op!=null) {
            // check augmented assignment:
            // A /= 3  -> check as if it was A = A / 3
            val target: IExpression =
                    if(assignment.target.register!=null)
                        RegisterExpr(assignment.target.register!!, assignment.target.position)
                    else if(assignment.target.identifier!=null)
                        assignment.target.identifier!!
                    else if(assignment.target.arrayindexed!=null) {
                        assignment.target.arrayindexed!!
                    } else throw FatalAstException("strange assignment")

            val expression = BinaryExpression(target, assignment.aug_op.substringBeforeLast('='), assignment.value, assignment.position)
            expression.linkParents(assignment.parent)
            val assignment2 = Assignment(assignment.target, null, expression, assignment.position)
            assignment2.linkParents(assignment.parent)
            return process(assignment2)
        }

        val targetDatatype = assignment.target.determineDatatype(namespace, heap, assignment)
        if(targetDatatype!=null) {
            val constVal = assignment.value.constValue(namespace, heap)
            if(constVal!=null) {
                val arrayspec = if(assignment.target.identifier!=null) {
                    val targetVar = namespace.lookup(assignment.target.identifier!!.nameInSource, assignment) as? VarDecl
                    targetVar?.arrayspec
                } else null
                checkValueTypeAndRange(targetDatatype,
                        arrayspec ?: ArraySpec(LiteralValue.optimalInteger(-1, assignment.position), null, assignment.position),
                        constVal, heap)
            } else {
                val sourceDatatype: DataType? = assignment.value.resultingDatatype(namespace, heap)
                if(sourceDatatype==null) {
                    if(assignment.value is FunctionCall)
                        checkResult.add(ExpressionError("function call doesn't return a suitable value to use in assignment", assignment.value.position))
                    else
                        checkResult.add(ExpressionError("assignment value is invalid or has no proper datatype", assignment.value.position))
                }
                else {
                    checkAssignmentCompatible(targetDatatype, sourceDatatype, assignment.value, assignment.position)
                }
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

        // CONST can only occur on simple types (byte, word, float)
        if(decl.type==VarDeclType.CONST) {
            if (decl.datatype !in NumericDatatypes)
                err("const modifier can only be used on numeric types (byte, word, float)")
        }

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                if (decl.value == null) {
                    when {
                        decl.datatype in NumericDatatypes -> {
                            // initialize numeric var with value zero by default.
                            val litVal = LiteralValue(DataType.UBYTE, 0, position = decl.position)
                            litVal.parent = decl
                            decl.value = litVal
                        }
                        else -> err("var/const declaration needs a compile-time constant initializer value for this type")  // const fold should have provided it!
                    }
                    return super.process(decl)
                }
                when {
                    decl.value is RangeExpr -> checkValueTypeAndRange(decl.datatype, decl.arrayspec, decl.value as RangeExpr)
                    decl.value is LiteralValue -> {
                        val arraySpec = decl.arrayspec ?: (
                                if((decl.value as LiteralValue).isArray)
                                    ArraySpec.forArray(decl.value as LiteralValue, heap)
                                else
                                    ArraySpec(LiteralValue.optimalInteger(-2, decl.position), null, decl.position)
                                )
                        checkValueTypeAndRange(decl.datatype, arraySpec, decl.value as LiteralValue, heap)
                    }
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
                    err("invalid zp type, expected basicsafe, kernalsafe, or full")
            }
            "%zpreserved" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=2 || directive.args[0].int==null || directive.args[1].int==null)
                    err("requires two addresses (start, end)")
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
        val arrayspec =
                if(literalValue.isArray)
                    ArraySpec.forArray(literalValue, heap)
                else
                    ArraySpec(LiteralValue.optimalInteger(-3, literalValue.position), null, literalValue.position)
        checkValueTypeAndRange(literalValue.type, arrayspec, literalValue, heap)

        val lv = super.process(literalValue)
        when(lv.type) {
            in StringDatatypes -> {
                if(lv.heapId==null)
                    throw FatalAstException("string should have been moved to heap at ${lv.position}")
            }
            in ArrayDatatypes -> {
                if(lv.heapId==null)
                    throw FatalAstException("array/matrix should have been moved to heap at ${lv.position}")
            }
            else -> {}
        }
        return lv
    }

    override fun process(expr: BinaryExpression): IExpression {
        when(expr.operator){
            "/", "//", "%" -> {
                val numeric = expr.right.constValue(namespace, heap)?.asNumericValue?.toDouble()
                if(numeric==0.0)
                    checkResult.add(ExpressionError("division by zero", expr.right.position))
            }
        }
        return super.process(expr)
    }

    override fun process(range: RangeExpr): IExpression {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, range.position))
        }
        super.process(range)
        val from = range.from.constValue(namespace, heap)
        val to = range.to.constValue(namespace, heap)
        val stepLv = range.step.constValue(namespace, heap) ?: LiteralValue(DataType.UBYTE, 1, position = range.position)
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
            checkFunctionCall(targetStatement, functionCall.arglist, functionCall.position)
        return super.process(functionCall)
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
        val targetStatement = checkFunctionOrLabelExists(functionCall.target, functionCall)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCall.arglist, functionCall.position)
        if(targetStatement is Subroutine && targetStatement.returntypes.isNotEmpty())
            if(!targetStatement.isAsmSubroutine)
                printWarning("result value of subroutine call is discarded", functionCall.position)
        return super.process(functionCall)
    }

    private fun checkFunctionCall(target: IStatement, args: List<IExpression>, position: Position) {
        if(target is Label && args.isNotEmpty())
            checkResult.add(SyntaxError("cannot use arguments when calling a label", position))

        if(target is BuiltinFunctionStatementPlaceholder) {
            // it's a call to a builtin function.
            val func = BuiltinFunctions[target.name]!!
            if(args.size!=func.parameters.size)
                checkResult.add(SyntaxError("invalid number of arguments", position))
            else {
                for (arg in args.withIndex().zip(func.parameters)) {
                    if(arg.first.value.resultingDatatype(namespace, heap) !in arg.second.possibleDatatypes)
                        checkResult.add(ExpressionError("argument ${arg.first.index+1} has invalid type, expected ${arg.second.possibleDatatypes}", position))
                }
            }
        } else if(target is Subroutine) {
            if(args.size!=target.parameters.size)
                checkResult.add(SyntaxError("invalid number of arguments", position))
            else {
                for (arg in args.withIndex().zip(target.parameters)) {
                    if(arg.first.value.resultingDatatype(namespace, heap) != arg.second.type)
                        checkResult.add(ExpressionError("argument ${arg.first.index+1} has invalid type, expected ${arg.second.type}", position))
                }
            }
        }
    }

    override fun process(postIncrDecr: PostIncrDecr): IStatement {
        if(postIncrDecr.target.identifier!=null) {
            val targetName = postIncrDecr.target.identifier!!.nameInSource
            val target = namespace.lookup(targetName, postIncrDecr)
            if(target==null) {
                checkResult.add(SyntaxError("undefined symbol: ${targetName.joinToString(".")}", postIncrDecr.position))
            } else {
                if(target !is VarDecl || target.type==VarDeclType.CONST) {
                    checkResult.add(SyntaxError("can only increment or decrement a variable", postIncrDecr.position))
                } else if(target.datatype !in NumericDatatypes) {
                    checkResult.add(SyntaxError("can only increment or decrement a byte/float/word variable", postIncrDecr.position))
                }
            }
        } else if(postIncrDecr.target.arrayindexed!=null) {
            val indexedRegister = postIncrDecr.target.arrayindexed?.register
            if(indexedRegister!=null) {
                if(indexedRegister==Register.A || indexedRegister==Register.X || indexedRegister==Register.Y)
                    checkResult.add(SyntaxError("array indexing on registers requires register pair variable", postIncrDecr.position))
            } else {
                val target = postIncrDecr.target.arrayindexed?.identifier?.targetStatement(namespace)
                if(target==null) {
                    checkResult.add(SyntaxError("undefined symbol", postIncrDecr.position))
                }
                else {
                    val dt = (target as VarDecl).datatype
                    if(dt !in NumericDatatypes)
                        checkResult.add(SyntaxError("can only increment or decrement a byte/float/word", postIncrDecr.position))
                }
            }
        }
        return super.process(postIncrDecr)
    }

    override fun process(arrayIndexedExpression: ArrayIndexedExpression): IExpression {
        val reg=arrayIndexedExpression.register
        if(reg==null) {
            val target = arrayIndexedExpression.identifier!!.targetStatement(namespace)
            if(target is VarDecl) {
                if(target.datatype !in IterableDatatypes)
                    checkResult.add(SyntaxError("array indexing requires an iterable variable", arrayIndexedExpression.position))
                val arraysize = target.arrayspec?.size()
                if(arraysize!=null) {
                    // check out of bounds
                    if((arrayIndexedExpression.array.y as? LiteralValue)?.asIntegerValue != null) {
                        throw FatalAstException("constant y dimension of index should have been const-folded with x into one value")
                    }
                    val index = (arrayIndexedExpression.array.x as? LiteralValue)?.asIntegerValue
                    if(index!=null && (index<0 || index>=arraysize))
                        checkResult.add(ExpressionError("array index out of bounds", arrayIndexedExpression.array.position))
                }
            } else
                checkResult.add(SyntaxError("array indexing requires a variable to act upon", arrayIndexedExpression.position))
        } else if(reg==Register.A || reg==Register.X || reg==Register.Y) {
            checkResult.add(SyntaxError("array indexing on registers requires register pair variable", arrayIndexedExpression.position))
        } else if(arrayIndexedExpression.array.y!=null) {
            checkResult.add(SyntaxError("array indexing on registers can only use one index dimension", arrayIndexedExpression.position))
        }

        return super.process(arrayIndexedExpression)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: IStatement): IStatement? {
        val targetStatement = target.targetStatement(namespace)
        if(targetStatement is Label || targetStatement is Subroutine || targetStatement is BuiltinFunctionStatementPlaceholder)
            return targetStatement
        checkResult.add(SyntaxError("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", statement.position))
        return null
    }

    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArraySpec?, range: RangeExpr) : Boolean {
        val from = range.from.constValue(namespace, heap)
        val to = range.to.constValue(namespace, heap)
        if(from==null || to==null) {
            checkResult.add(SyntaxError("range from and to values must be constants", range.position))
            return false
        }

        when(targetDt) {
            in NumericDatatypes -> {
                checkResult.add(SyntaxError("can't assign a range to a scalar type", range.position))
                return false
            }
            in StringDatatypes -> {
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
            in ArrayDatatypes -> {
                // range and length check bytes
                val expectedSize = arrayspec!!.size()
                val rangeSize=range.size()
                if(rangeSize!=null && rangeSize != expectedSize) {
                    checkResult.add(ExpressionError("range size doesn't match array/matrix size, expected $expectedSize found $rangeSize", range.position))
                    return false
                }
                return true
            }
            else -> throw FatalAstException("invalid targetDt")
        }
    }

    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArraySpec, value: LiteralValue, heap: HeapValues) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(ExpressionError(msg, value.position))
            return false
        }
        when (targetDt) {
            DataType.FLOAT -> {
                val number = when(value.type) {
                    DataType.UBYTE, DataType.BYTE -> value.bytevalue!!.toDouble()
                    DataType.UWORD, DataType.WORD -> value.wordvalue!!.toDouble()
                    DataType.FLOAT -> value.floatvalue!!
                    else -> return err("numeric value expected")
                }
                if (number > 1.7014118345e+38 || number < -1.7014118345e+38)
                    return err("value '$number' out of range for MFLPT format")
            }
            DataType.UBYTE -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned byte value expected instead of float; possible loss of precision")
                else
                    err("unsigned byte value expected")
                if (number < 0 || number > 255)
                    return err("value '$number' out of range for unsigned byte")
            }
            DataType.BYTE -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("byte value expected instead of float; possible loss of precision")
                else
                    err("byte value expected")
                if (number < -128 || number > 127)
                    return err("value '$number' out of range for byte")
            }
            DataType.UWORD -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned word value expected instead of float; possible loss of precision")
                else
                    err("unsigned word value expected")
                if (number < 0 || number > 65535)
                    return err("value '$number' out of range for unsigned word")
            }
            DataType.WORD -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("word value expected instead of float; possible loss of precision")
                else
                    err("word value expected")
                if (number < -32768 || number > 32767)
                    return err("value '$number' out of range for word")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                if(!value.isString)
                    return err("string value expected")
                val str = value.strvalue ?: heap.get(value.heapId!!).str!!
                if (str.isEmpty() || str.length > 255)
                    return err("string length must be 1 to 255")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                // value may be either a single byte, or a byte array (of all constant values)
                if(value.type==targetDt) {
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).array!!.size
                    val arraySpecSize = arrayspec.size()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        val constX = arrayspec.x.constValue(namespace, heap)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                        return true
                    }
                }
                return err("invalid array initialization value ${value.type}, expected $targetDt")
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                // value may be either a single word, or a word array
                if(value.type==targetDt) {
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).array!!.size
                    val arraySpecSize = arrayspec.size()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        val constX = arrayspec.x.constValue(namespace, heap)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                        return true
                    }
                }
                return err("invalid array initialization value ${value.type}, expected $targetDt")
            }
            DataType.ARRAY_F -> {
                // value may be either a single float, or a float array
                if(value.type==targetDt) {
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).doubleArray!!.size
                    val arraySpecSize = arrayspec.size()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        val constX = arrayspec.x.constValue(namespace, heap)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                    }
                    // check if the floating point values are all within range
                    val doubles = if(value.arrayvalue!=null)
                        value.arrayvalue.map {it.constValue(namespace, heap)?.asNumericValue!!.toDouble()}.toDoubleArray()
                    else
                        heap.get(value.heapId!!).doubleArray!!
                    if(doubles.any { it < FLOAT_MAX_NEGATIVE || it> FLOAT_MAX_POSITIVE})
                        return err("floating point value overflow")
                    return true
                }
                return err("invalid array initialization value ${value.type}, expected $targetDt")
            }
            DataType.MATRIX_UB, DataType.MATRIX_B -> {
                // value can only be a single byte, or a byte array (which represents the matrix)
                if(value.type==targetDt) {
                    val arraySpecSize = arrayspec.size()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        val constX = arrayspec.x.constValue(namespace, heap)
                        val constY = arrayspec.y!!.constValue(namespace, heap)
                        if (constX?.asIntegerValue == null || constY?.asIntegerValue == null)
                            return err("matrix size specifiers must be constant integer values")
                        val matrix = heap.get(value.heapId!!).array!!
                        val expectedSize = constX.asIntegerValue * constY.asIntegerValue
                        if (matrix.size != expectedSize)
                            return err("initializer matrix size mismatch (expecting $expectedSize, got ${matrix.size} elements)")
                    }
                }
                return err("invalid matrix initialization value $value")
            }
        }
        return true
    }

    private fun checkAssignmentCompatible(targetDatatype: DataType,
                                          sourceDatatype: DataType,
                                          sourceValue: IExpression,
                                          position: Position) : Boolean {

        if(sourceValue is RangeExpr)
            checkResult.add(SyntaxError("can't assign a range value", position))

        val result =  when(targetDatatype) {
            DataType.BYTE -> sourceDatatype==DataType.BYTE
            DataType.UBYTE -> sourceDatatype==DataType.UBYTE
            DataType.WORD -> sourceDatatype==DataType.BYTE || sourceDatatype==DataType.WORD
            DataType.UWORD -> sourceDatatype==DataType.UBYTE || sourceDatatype==DataType.UWORD
            DataType.FLOAT -> sourceDatatype in NumericDatatypes
            DataType.STR -> sourceDatatype==DataType.STR
            DataType.STR_S -> sourceDatatype==DataType.STR_S
            DataType.STR_P -> sourceDatatype==DataType.STR_P
            DataType.STR_PS -> sourceDatatype==DataType.STR_PS
            else -> checkResult.add(SyntaxError("cannot assign new value to variable of type $targetDatatype", position))
        }

        if(result)
            return true

        if((sourceDatatype==DataType.UWORD || sourceDatatype==DataType.WORD) && (targetDatatype==DataType.UBYTE || targetDatatype==DataType.BYTE))
            checkResult.add(ExpressionError("cannot assign word to byte, use msb() or lsb()?", position))
        else if(sourceDatatype==DataType.FLOAT && targetDatatype in IntegerDatatypes)
            checkResult.add(ExpressionError("cannot assign float to ${targetDatatype.toString().toLowerCase()}; possible loss of precision. Suggestion: round the value or revert to byte/word arithmetic", position))
        else
            checkResult.add(ExpressionError("cannot assign ${sourceDatatype.toString().toLowerCase()} to ${targetDatatype.toString().toLowerCase()}", position))

        return false
    }
}
