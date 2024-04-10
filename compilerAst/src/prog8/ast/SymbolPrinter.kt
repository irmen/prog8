package prog8.ast

import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.DataType
import prog8.code.core.ZeropageWish
import prog8.code.core.toHex


fun printSymbols(program: Program) {
    println()
    val printer = SymbolPrinter(::print, program, false)
    printer.visit(program)
    println()
}


class SymbolPrinter(val output: (text: String) -> Unit, val program: Program, val skipLibraries: Boolean): IAstVisitor {
    private fun outputln(text: String) = output(text + "\n")

    override fun visit(module: Module) {
        if(!module.isLibrary || !skipLibraries) {
            if(module.source.isFromFilesystem || module.source.isFromResources) {
                val moduleName = "LIBRARY MODULE NAME: ${module.source.name}"
                outputln(moduleName)
                outputln("-".repeat(moduleName.length))
                super.visit(module)
                output("\n")
            }
        }
    }

    override fun visit(block: Block) {
        val (vars, subs) = block.statements.filter{ it is Subroutine || it is VarDecl }.partition { it is VarDecl }
        if(vars.isNotEmpty() || subs.isNotEmpty()) {
            outputln("${block.name}  {")
            for (variable in vars.sortedBy { (it as VarDecl).name }) {
                output("    ")
                variable.accept(this)
            }
            for (subroutine in subs.sortedBy { (it as Subroutine).name }) {
                output("    ")
                subroutine.accept(this)
            }
            outputln("}\n")
        }
    }

    private fun datatypeString(dt: DataType): String {
        return when (dt) {
            DataType.BOOL -> "bool"
            DataType.UBYTE -> "ubyte"
            DataType.BYTE -> "byte"
            DataType.UWORD -> "uword"
            DataType.WORD -> "word"
            DataType.LONG -> "long"
            DataType.FLOAT -> "float"
            DataType.STR -> "str"
            DataType.ARRAY_UB -> "ubyte["
            DataType.ARRAY_B -> "byte["
            DataType.ARRAY_UW -> "uword["
            DataType.ARRAY_W -> "word["
            DataType.ARRAY_F -> "float["
            DataType.ARRAY_BOOL -> "bool["
            DataType.ARRAY_UW_SPLIT -> "@split uword["
            DataType.ARRAY_W_SPLIT -> "@split word["
            DataType.UNDEFINED -> throw IllegalArgumentException("wrong dt")
        }
    }

    override fun visit(decl: VarDecl) {
        if(decl.origin==VarDeclOrigin.SUBROUTINEPARAM)
            return

        when(decl.type) {
            VarDeclType.VAR -> {}
            VarDeclType.CONST -> output("const ")
            VarDeclType.MEMORY -> output("&")
        }

        output(datatypeString(decl.datatype))
        if(decl.arraysize!=null) {
            decl.arraysize!!.indexExpr.accept(this)
        }
        if(decl.isArray)
            output("]")

        if(decl.zeropage == ZeropageWish.REQUIRE_ZEROPAGE)
            output(" @requirezp")
        else if(decl.zeropage == ZeropageWish.PREFER_ZEROPAGE)
            output(" @zp")
        if(decl.sharedWithAsm)
            output(" @shared")

        output(" ")
        if(decl.names.size>1)
            output(decl.names.joinToString(prefix=" "))
        else
            output(" ${decl.name} ")
        output("\n")
    }

    override fun visit(subroutine: Subroutine) {
        if(subroutine.isAsmSubroutine) {
            output("${subroutine.name}  (")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                val reg =
                        when {
                            param.second.registerOrPair!=null -> param.second.registerOrPair.toString()
                            param.second.statusflag!=null -> param.second.statusflag.toString()
                            else -> "?????"
                        }
                output("${datatypeString(param.first.type)} ${param.first.name} @$reg")
                if(param.first!==subroutine.parameters.last())
                    output(", ")
            }
        }
        else {
            output("${subroutine.name}  (")
            for(param in subroutine.parameters) {
                output("${datatypeString(param.type)} ${param.name}")
                if(param!==subroutine.parameters.last())
                    output(", ")
            }
        }
        output(") ")
        if(subroutine.asmClobbers.isNotEmpty()) {
            output("-> clobbers (")
            val regs = subroutine.asmClobbers.toList().sorted()
            for(r in regs) {
                output(r.toString())
                if(r!==regs.last())
                    output(",")
            }
            output(") ")
        }
        if(subroutine.returntypes.any()) {
            if(subroutine.asmReturnvaluesRegisters.isNotEmpty()) {
                val rts = subroutine.returntypes.zip(subroutine.asmReturnvaluesRegisters).joinToString(", ") {
                    val dtstr = datatypeString(it.first)
                    if(it.second.registerOrPair!=null)
                        "$dtstr @${it.second.registerOrPair}"
                    else
                        "$dtstr @${it.second.statusflag}"
                }
                output("-> $rts ")
            } else {
                val rts = subroutine.returntypes.joinToString(", ") { datatypeString(it) }
                output("-> $rts ")
            }
        }
        if(subroutine.asmAddress!=null)
            output("= ${subroutine.asmAddress.toHex()}")

        output("\n")
    }
}
