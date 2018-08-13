package il65.ast

import il65.ParsingFailedError


fun Module.checkValid(globalNamespace: INameScope) {
    val checker = AstChecker(globalNamespace)
    this.process(checker)
    val checkResult = checker.result()
    checkResult.forEach {
        it.printError()
    }
    if(checkResult.isNotEmpty())
        throw ParsingFailedError("There are ${checkResult.size} errors in module '$name'.")
}


class AstChecker(private val globalNamespace: INameScope) : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()
    private val blockNames: HashMap<String, Position?> = hashMapOf()

    fun result(): List<SyntaxError> {
        return checkResult
    }

    override fun process(jump: Jump): IStatement {
        super.process(jump)
        if(jump.address!=null && (jump.address < 0 || jump.address > 65535))
            checkResult.add(SyntaxError("jump address must be valid integer 0..\$ffff", jump.position))
        return jump
    }

    override fun process(block: Block): IStatement {
        if(block.address!=null && (block.address<0 || block.address>65535)) {
            checkResult.add(SyntaxError("block memory address must be valid integer 0..\$ffff", block.position))
        }
        val existing = blockNames[block.name]
        if(existing!=null) {
            checkResult.add(SyntaxError("block name conflict, first defined in ${existing.file} line ${existing.line}", block.position))
        } else {
            blockNames[block.name] = block.position
        }

        super.process(block)

        // check if labels are unique
        val labels = block.statements.filter { it is Label }.map { it as Label }
        val labelnames = mutableMapOf<String, Position?>()
        labels.forEach {
            val existing = labelnames[it.name]
            if(existing!=null) {
                checkResult.add(SyntaxError("label name conflict, first defined on line ${existing.line}", it.position))
            } else {
                labelnames[it.name] = it.position
            }
        }

        // check if var names are unique
        val variables = block.statements.filter { it is VarDecl }.map{ it as VarDecl }
        val varnames= mutableMapOf<String, Position?>()
        variables.forEach {
            val existing = varnames[it.name]
            if(existing!=null) {
                checkResult.add(SyntaxError("variable name conflict, first defined on line ${existing.line}", it.position))
            } else {
                varnames[it.name] = it.position
            }
        }

        // check if subroutine names are unique
        val subroutines = block.statements.filter { it is Subroutine }.map{ it as Subroutine }
        val subnames = mutableMapOf<String, Position?>()
        subroutines.forEach {
            val existing = subnames[it.name]
            if(existing!=null) {
                checkResult.add(SyntaxError("subroutine name conflict, first defined on line ${existing.line}", it.position))
            } else {
                subnames[it.name] = it.position
            }
        }

        return block
    }

    /**
     * Check subroutine definition
     */
    override fun process(subroutine: Subroutine): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, subroutine.position))
        }
        val uniqueNames = subroutine.parameters.map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names should be unique")
        val uniqueParamRegs = subroutine.parameters.map {it.register}.toSet()
        if(uniqueParamRegs.size!=subroutine.parameters.size)
            err("parameter registers should be unique")
        val uniqueResults = subroutine.returnvalues.map {it.register}.toSet()
        if(uniqueResults.size!=subroutine.returnvalues.size)
            err("return registers should be unique")

        super.process(subroutine)

        // check if labels are unique
        val labels = subroutine.statements.filter { it is Label }.map { it as Label }
        val labelnames = mutableMapOf<String, Position?>()
        labels.forEach {
            val existing = labelnames[it.name]
            if(existing!=null) {
                checkResult.add(SyntaxError("label name conflict, first defined on line ${existing.line}", it.position))
            } else {
                labelnames[it.name] = it.position
            }
        }

        // check if var names are unique
        val variables = subroutine.statements.filter { it is VarDecl }.map{ it as VarDecl }
        val varnames= mutableMapOf<String, Position?>()
        variables.forEach {
            val existing = varnames[it.name]
            if(existing!=null) {
                checkResult.add(SyntaxError("variable name conflict, first defined on line ${existing.line}", it.position))
            } else {
                varnames[it.name] = it.position
            }
        }

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
     * Check the variable declarations (values within range etc)
     */
    override fun process(decl: VarDecl): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, decl.position))
        }
        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                when {
                    decl.value == null ->
                        err("need a compile-time constant initializer value")
                    decl.value !is LiteralValue ->
                        err("need a compile-time constant initializer value, found: ${decl.value!!::class.simpleName}")
                    decl.isScalar -> {
                        checkConstInitializerValueScalar(decl)
                        checkValueRange(decl.datatype, decl.value as LiteralValue, decl.position)
                    }
                    decl.isArray || decl.isMatrix -> {
                        checkConstInitializerValueArray(decl)
                    }
                }
            }
            VarDeclType.MEMORY -> {
                if(decl.value !is LiteralValue)
                    throw AstException("${decl.value?.position} value of memory var decl is not a literal (it is a ${decl.value!!::class.simpleName}). This is likely a bug in the AstOptimizer")

                val value = decl.value as LiteralValue
                if(value.intvalue==null || value.intvalue<0 || value.intvalue>65535) {
                    err("memory address must be valid integer 0..\$ffff")
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
            "%zp" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 ||
                        directive.args[0].name != "compatible" &&
                        directive.args[0].name != "full" &&
                        directive.args[0].name != "full-restore")
                    err("invalid zp directive style, expected compatible, full or full-restore")
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
                if(directive.parent !is Block) err("this directive may only occur in a block")
                if(directive.args.isNotEmpty())
                    err("invalid breakpoint directive, expected no arguments")
            }
            "%asminclude" -> {
                if(directive.parent !is Block) err("this directive may only occur in a block")
                if(directive.args.size!=2 || directive.args[0].str==null || directive.args[1].name==null)
                    err("invalid asminclude directive, expected arguments: \"filename\", scopelabel")
            }
            "%asmbinary" -> {
                if(directive.parent !is Block) err("this directive may only occur in a block")
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
                    err("invalid option directive argument, expected enable_floats")
            }
            else -> throw AstException("invalid directive ${directive.directive}")
        }
        return super.process(directive)
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
                    checkResult.add(SyntaxError("initializer array size mismatch (expecting $expected)", decl.position))
                else {
                    value.arrayvalue.forEach {
                        checkValueRange(decl.datatype, it.constValue(globalNamespace)!!, it.position)
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
                    checkResult.add(SyntaxError("initializer array size mismatch (expecting $expected)", decl.position))
                else {
                    value.arrayvalue.forEach {
                        checkValueRange(decl.datatype, it.constValue(globalNamespace)!!, it.position)
                    }
                }
            }
        }
    }

    private fun checkValueRange(datatype: DataType, value: LiteralValue, position: Position?) {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, position))
        }
        when (datatype) {
            DataType.FLOAT -> {
                val number = value.asFloat(false)
                if (number!=null && (number > 1.7014118345e+38 || number < -1.7014118345e+38))
                    err("floating point value out of range for MFLPT format")
            }
            DataType.BYTE -> {
                val number = value.asInt(false)
                if (number!=null && (number < 0 || number > 255))
                    err("value out of range for unsigned byte")
            }
            DataType.WORD -> {
                val number = value.asInt(false)
                if (number!=null && (number < 0 || number > 65535))
                    err("value out of range for unsigned word")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                val str = value.strvalue
                if (str!=null && (str.isEmpty() || str.length > 65535))
                    err("string length must be 1..65535")
            }
        }
    }

    private fun checkConstInitializerValueScalar(decl: VarDecl) {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, decl.position))
        }
        val value = decl.value as LiteralValue
        when (decl.datatype) {
            DataType.FLOAT -> {
                val number = value.asFloat(false)
                if (number == null)
                    err("need a const float initializer value")
            }
            DataType.BYTE -> {
                val number = value.asInt(false)
                if (number == null)
                    err("need a const integer initializer value")
            }
            DataType.WORD -> {
                val number = value.asInt(false)
                if (number == null)
                    err("need a const integer initializer value")
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                val str = value.strvalue
                if (str == null)
                    err("need a const string initializer value")
            }
        }
    }
}
