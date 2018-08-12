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

    /**
     * check the arguments of the directive
     */
    override fun process(directive: Directive): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, directive))
        }
        when(directive.directive) {
            "%output" -> {
                if(directive.args.size!=1 || directive.args[0].name != "raw" && directive.args[0].name != "prg")
                    err("invalid output directive type, expected raw or prg")
            }
            "%launcher" -> {
                if(directive.args.size!=1 || directive.args[0].name != "basic" && directive.args[0].name != "none")
                    err("invalid launcher directive type, expected basic or none")
            }
            "%zp" -> {
                if(directive.args.size!=1 ||
                        directive.args[0].name != "compatible" &&
                        directive.args[0].name != "full" &&
                        directive.args[0].name != "full-restore")
                    err("invalid zp directive style, expected compatible, full or full-restore")
            }
            "%address" -> {
                if(directive.args.size!=1 || directive.args[0].int == null)
                    err("invalid address directive, expected numeric address argument")
            }
            "%import" -> {
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
