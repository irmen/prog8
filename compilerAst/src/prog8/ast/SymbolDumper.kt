package prog8.ast

import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import java.io.PrintStream


fun printSymbols(program: Program) {
    println()
    val symbols = SymbolDumper(false)
    symbols.visit(program)
    symbols.write(System.out)
    println()
}


private class SymbolDumper(val skipLibraries: Boolean): IAstVisitor {
    private val moduleOutputs = mutableMapOf<Module, MutableList<String>>()

    private var currentModule = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated("dummy"))
    private fun output(line: String) {
        var lines = moduleOutputs[currentModule]
        if(lines == null) {
            lines = mutableListOf()
            moduleOutputs[currentModule] = lines
        }
        lines.add(line)
    }
    private fun outputln(line: String) = output(line + '\n')

    fun write(out: PrintStream) {
        for((module, lines) in moduleOutputs.toSortedMap(compareBy { it.name })) {
            if(lines.any()) {
                val moduleName = "LIBRARY MODULE NAME: ${module.source.name}"
                out.println()
                out.println(moduleName)
                out.println("-".repeat(moduleName.length))
                out.println()
                for (line in lines) {
                    out.print(line)
                }
            }
        }
    }

    override fun visit(module: Module) {
        if(!module.isLibrary || !skipLibraries) {
            if(module.source.isFromFilesystem || module.source.isFromResources) {
                currentModule = module
                super.visit(module)
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
        if(subroutine.asmAddress!=null) {
            val bank = if(subroutine.asmAddress.constbank!=null) "@bank ${subroutine.asmAddress.constbank}"
            else if(subroutine.asmAddress.varbank!=null) "@bank ${subroutine.asmAddress.varbank?.nameInSource?.joinToString(".")}"
            else ""
            val address = subroutine.asmAddress.address
            val addrString = if(address is NumericLiteral) address.number.toHex() else "<non-const-address>"
            output("$bank = $addrString")
        }

        output("\n")
    }
}
