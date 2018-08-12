package il65

import il65.ast.*


fun Module.checkValid() : List<SyntaxError> {
    val checker = AstChecker()
    this.process(checker)
    return checker.result()
}


class AstChecker : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    fun result(): List<SyntaxError> {
        return checkResult
    }

    override fun process(expr: PrefixExpression): IExpression {
        return expr
    }

    override fun process(expr: BinaryExpression): IExpression {
        return expr
    }

    override fun process(block: Block): IStatement {
        if(block.address!=null && (block.address<0 || block.address>65535)) {
            checkResult.add(SyntaxError("block memory address must be valid 0..\$ffff", block))
        }
        block.statements.forEach { it.process(this) }
        return block
    }

    /**
     * Check the variable declarations (values within range etc)
     */
    override fun process(decl: VarDecl): IStatement {
        fun err(msg: String) {
            if(decl.value?.position == null)
                throw AstException("declvalue no pos!")
            checkResult.add(SyntaxError(msg, decl))
        }
        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                val value = decl.value as? LiteralValue
                if(value==null)
                    err("need a compile-time constant initializer value, found: ${decl.value!!::class.simpleName}")
                else
                    when(decl.datatype) {
                        DataType.FLOAT -> {
                            val number = value.asFloat()
                            if(number==null)
                                err("need a const float initializer value")
                            else if(number > 1.7014118345e+38 || number < -1.7014118345e+38)
                                err("floating point value out of range for MFLPT format")
                        }
                        DataType.BYTE -> {
                            val number = value.asInt()
                            if(number==null)
                                err("need a const integer initializer value")
                            else if(number < 0 || number > 255)
                                err("value out of range for unsigned byte")
                        }
                        DataType.WORD -> {
                            val number = value.asInt()
                            if(number==null)
                                err("need a const integer initializer value")
                            else if(number < 0 || number > 65535)
                                err("value out of range for unsigned word")
                        }
                        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                            val str = value.strvalue
                            if(str==null)
                                err("need a const string initializer value")
                            else if(str.isEmpty() || str.length>65535)
                                err("string length must be 1..65535")
                        }
                    }
            }
            VarDeclType.MEMORY -> {
                val value = decl.value as LiteralValue
                if(value.intvalue==null || value.intvalue<0 || value.intvalue>65535) {
                    err("memory address must be valid 0..\$ffff")
                }
            }
        }

        decl.arrayspec?.process(this)
        decl.value?.process(this)
        return decl
    }

    /**
     * check the arguments of the directive
     */
    override fun process(directive: Directive): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, directive))
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
            }
            "%breakpoint" -> {
                if(directive.args.isNotEmpty())
                    err("invalid breakpoint directive, expected no arguments")
            }
            "%asminclude" -> {
                if(directive.args.size!=2 || directive.args[0].str==null || directive.args[1].name==null)
                    err("invalid asminclude directive, expected arguments: \"filename\", scopelabel")
            }
            "%asmbinary" -> {
                val errormsg = "invalid asmbinary directive, expected arguments: \"filename\" [, offset [, length ] ]"
                if(directive.args.isEmpty()) err(errormsg)
                if(directive.args.isNotEmpty() && directive.args[0].str==null) err(errormsg)
                if(directive.args.size>=2 && directive.args[1].int==null) err(errormsg)
                if(directive.args.size==3 && directive.args[2].int==null) err(errormsg)
                if(directive.args.size>3) err(errormsg)
            }
            else -> throw AstException("invalid directive ${directive.directive}")
        }
        return directive
    }

}
