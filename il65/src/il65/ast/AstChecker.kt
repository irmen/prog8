package il65.ast

import il65.compiler.CompilationOptions
import il65.functions.BuiltinFunctionNames
import il65.parser.ParsingFailedError

/**
 * General checks on the Ast
 */

fun Module.checkValid(globalNamespace: INameScope, compilerOptions: CompilationOptions) {
    val checker = AstChecker(globalNamespace, compilerOptions)
    this.process(checker)
    val checkResult = checker.result()
    checkResult.forEach {
        System.err.println(it)
    }
    if(checkResult.isNotEmpty())
        throw ParsingFailedError("There are ${checkResult.size} errors in module '$name'.")
}


/**
 * todo check subroutine call parameters against signature
 * todo check subroutine return values against the call's result assignments
 */

class AstChecker(private val globalNamespace: INameScope, val compilerOptions: CompilationOptions) : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    fun result(): List<SyntaxError> {
        return checkResult
    }

    override fun process(module: Module) {
        super.process(module)
        val directives = module.statements.filter { it is Directive }.groupBy { (it as Directive).directive }
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

    override fun process(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val targetStatement = checkFunctionOrLabelExists(jump.identifier, jump)
            if(targetStatement!=null)
                jump.targetStatement = targetStatement      // link to actual jump target
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

    /**
     * Check subroutine definition
     */
    override fun process(subroutine: Subroutine): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, subroutine.position))
        }

//        // subroutines may only be defined directly inside a block    @todo NAH, why should we restrict that?
//        if(subroutine.parent !is Block)
//            err("subroutines can only be defined in a block (not in other scopes)")

        if(BuiltinFunctionNames.contains(subroutine.name))
            err("cannot redefine a built-in function")

        val uniqueNames = subroutine.parameters.map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names should be unique")
        val uniqueParamRegs = subroutine.parameters.map {it.register}.toSet()
        if(uniqueParamRegs.size!=subroutine.parameters.size)
            err("parameter registers should be unique")
        val uniqueResultRegisters = subroutine.returnvalues.filter{it.register!=null}.map {it.register.toString()}.toMutableSet()
        uniqueResultRegisters.addAll(subroutine.returnvalues.filter{it.statusflag!=null}.map{it.statusflag.toString()})
        if(uniqueResultRegisters.size!=subroutine.returnvalues.size)
            err("return registers should be unique")

        super.process(subroutine)

        // subroutine must contain at least one 'return' or 'goto'
        // (or if it has an asm block, that must contain a 'rts' or 'jmp')
        if(subroutine.statements.count { it is Return || it is Jump } == 0) {
            if(subroutine.address==null) {
            val amount = subroutine.statements
                    .filter { it is InlineAssembly }
                    .map {(it as InlineAssembly).assembly}
                    .count { it.contains(" rts") || it.contains("\trts") ||
                             it.contains(" jmp") || it.contains("\tjmp")}
            if(amount==0 )
                err("subroutine must have at least one 'return' or 'goto' in it (or 'rts' / 'jmp' in case of %asm)")
            }
        }

        return subroutine
    }

    /**
     * Assignment target must be register, or a variable name
     * for constant-value assignments, check the datatype as well
     */
    override fun process(assignment: Assignment): IStatement {
        if(assignment.target.identifier!=null) {
            val targetSymbol = globalNamespace.lookup(assignment.target.identifier!!.nameInSource, assignment)
            if(targetSymbol !is VarDecl) {
                checkResult.add(SyntaxError("assignment LHS must be register or variable", assignment.position))
                return super.process(assignment)
            } else if(targetSymbol.type==VarDeclType.CONST) {
                checkResult.add(SyntaxError("cannot assign new value to a constant", assignment.position))
                return super.process(assignment)
            }
        }

        if(assignment.value is LiteralValue) {
            val targetDatatype = assignment.target.determineDatatype(globalNamespace, assignment)
            if(checkValueType(targetDatatype, assignment.value as LiteralValue, assignment.position)) {
                checkValueRange(targetDatatype, assignment.value as LiteralValue, assignment.position)
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

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                when {
                    decl.value == null ->
                        err("var/const declaration needs a compile-time constant initializer value")
                    decl.value !is LiteralValue ->
                        err("var/const declaration needs a compile-time constant initializer value, found: ${decl.value!!::class.simpleName}")
                    decl.isScalar -> {
                        if(checkValueType(decl, decl.value as LiteralValue, decl.position)) {
                            checkValueRange(decl.datatype, decl.value as LiteralValue, decl.position)
                        }
                    }
                    decl.isArray || decl.isMatrix -> {
                        checkConstInitializerValueArray(decl)
                    }
                }
            }
            VarDeclType.MEMORY -> {
                if(decl.value !is LiteralValue) {
                    err("value of memory var decl is not a literal (it is a ${decl.value!!::class.simpleName}).", decl.value?.position)
                } else {
                    val value = decl.value as LiteralValue
                    if (value.intvalue == null || value.intvalue < 0 || value.intvalue > 65535) {
                        err("memory address must be valid integer 0..\$ffff")
                    }
                }
            }
        }

        return super.process(decl)
    }

    /**
     * check if condition
     */
    override fun process(ifStatement: IfStatement): IStatement {
        val constvalue = ifStatement.condition.constValue(globalNamespace)
        if(constvalue!=null) {
            val msg = if (constvalue.asBooleanValue) "condition is always true" else "condition is always false"
            println("${ifStatement.position} Warning: $msg")
        }

        return super.process(ifStatement)
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
        if(!compilerOptions.floats && literalValue.isFloat) {
            checkResult.add(SyntaxError("floating point value used, but floating point is not enabled via options", literalValue.position))
        }
        return super.process(literalValue)
    }

    private fun checkConstInitializerValueArray(decl: VarDecl) {
        val value = decl.value as LiteralValue
        // init value should either be a scalar or an array with the same dimensions as the arrayspec.

        if(decl.isArray) {
            if(value.arrayvalue==null) {
                checkValueRange(decl.datatype, value.constValue(globalNamespace)!!, value.position)
            }
            else {
                val expected = decl.arraySizeX(globalNamespace)
                if (value.arrayvalue.size != expected)
                    checkResult.add(SyntaxError("initializer array size mismatch (expecting $expected, got ${value.arrayvalue.size})", decl.position))
                else {
                    for(v in value.arrayvalue) {
                        if(!checkValueRange(decl.datatype, v.constValue(globalNamespace)!!, v.position))
                            break
                    }
                }
            }
        }

        if(decl.isMatrix) {
            if(value.arrayvalue==null) {
                checkValueRange(decl.datatype, value.constValue(globalNamespace)!!, value.position)
            }
            else {
                val expected = decl.arraySizeX(globalNamespace)!! * decl.arraySizeY(globalNamespace)!!
                if (value.arrayvalue.size != expected)
                    checkResult.add(SyntaxError("initializer array size mismatch (expecting $expected, got ${value.arrayvalue.size})", decl.position))
                else {
                    for(v in value.arrayvalue) {
                        if(!checkValueRange(decl.datatype, v.constValue(globalNamespace)!!, v.position))
                            break
                    }
                }
            }
        }
    }

    override fun process(range: RangeExpr): IExpression {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, range.position))
        }
        super.process(range)
        val from = range.from.constValue(globalNamespace)
        val to = range.to.constValue(globalNamespace)
        if(from!=null && to != null) {
            when {
                from.intvalue!=null && to.intvalue!=null -> {
                    if(from.intvalue > to.intvalue)
                        err("range from is larger than to value")
                }
                from.strvalue!=null && to.strvalue!=null -> {
                    if(from.strvalue.length!=1 || to.strvalue.length!=1)
                        err("range from and to must be a single character")
                    if(from.strvalue[0] > to.strvalue[0])
                        err("range from is larger than to value")
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
        if(targetStatement!=null) {
            functionCall.targetStatement = targetStatement      // link to the actual target statement
            checkBuiltinFunctionCall(functionCall, functionCall.position)
        }
        return super.process(functionCall)
    }

    override fun process(functionCall: FunctionCallStatement): IStatement {
        val targetStatement = checkFunctionOrLabelExists(functionCall.target, functionCall)
        if(targetStatement!=null) {
            functionCall.targetStatement = targetStatement      // link to the actual target statement
            checkBuiltinFunctionCall(functionCall, functionCall.position)
        }
        return super.process(functionCall)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: IStatement): IStatement? {
        if(target.nameInSource.size==1 && BuiltinFunctionNames.contains(target.nameInSource[0])) {
            return BuiltinFunctionStatementPlaceholder
        }
        val targetStatement = globalNamespace.lookup(target.nameInSource, statement)
        if(targetStatement is Label || targetStatement is Subroutine)
            return targetStatement
        checkResult.add(SyntaxError("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", statement.position))
        return null
    }

    private fun checkBuiltinFunctionCall(call: IFunctionCall, position: Position?) {
        if(call.target.nameInSource.size==1 && BuiltinFunctionNames.contains(call.target.nameInSource[0])) {
            val functionName = call.target.nameInSource[0]
            if(functionName=="P_carry" || functionName=="P_irqd") {
                // these functions allow only 0 or 1 as argument
                if(call.arglist.size!=1 || call.arglist[0] !is LiteralValue) {
                    checkResult.add(SyntaxError("$functionName requires one argument, 0 or 1", position))
                } else {
                    val value = call.arglist[0] as LiteralValue
                    if(value.intvalue==null || value.intvalue < 0 || value.intvalue > 1) {
                        checkResult.add(SyntaxError("$functionName requires one argument, 0 or 1", position))
                    }
                }
            }
        }
    }

    private fun checkValueRange(datatype: DataType, value: LiteralValue, position: Position?) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(SyntaxError(msg, position))
            return false
        }
        when (datatype) {
            DataType.FLOAT -> {
                val number = value.floatvalue
                        ?: return err("floating point value expected")
                if (number > 1.7014118345e+38 || number < -1.7014118345e+38)
                    return err("floating point value '$number' out of range for MFLPT format")
            }
            DataType.BYTE -> {
                val number = value.intvalue
                        ?: return err("byte integer value expected")
                if (number < 0 || number > 255)
                    return err("value '$number' out of range for unsigned byte")
            }
            DataType.WORD -> {
                val number = value.intvalue
                        ?: return err("word integer value expected")
                if (number < 0 || number > 65535)
                    return err("value '$number' out of range for unsigned word")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                val str = value.strvalue
                        ?: return err("string value expected")
                if (str.isEmpty() || str.length > 65535)
                    return err("string length must be 1..65535")
            }
        }
        return true
    }

    private fun checkValueType(vardecl: VarDecl, value: LiteralValue, position: Position?) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(SyntaxError(msg, position))
            return false
        }
        when {
            vardecl.isScalar -> checkValueType(vardecl.datatype, value, position)
            vardecl.isArray -> if(value.arrayvalue==null) return err("array value expected")
            vardecl.isMatrix -> TODO()
        }
        return true
    }

    private fun checkValueType(targetType: DataType, value: LiteralValue, position: Position?) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(SyntaxError(msg, position))
            return false
        }
        when(targetType) {
            DataType.FLOAT -> {
                if (value.floatvalue == null)
                    return err("floating point value expected")
            }
            DataType.BYTE -> {
                if (value.intvalue == null)
                    return err("byte integer value expected")
            }
            DataType.WORD -> {
                if (value.intvalue == null)
                    return err("word integer value expected")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                if (value.strvalue == null)
                    return err("string value expected")
            }
        }
        return true
    }
}
