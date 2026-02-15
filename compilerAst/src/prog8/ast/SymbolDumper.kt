package prog8.ast

import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.Position
import prog8.code.core.ZeropageWish
import prog8.code.core.toHex
import prog8.code.source.SourceCode
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

    private val skipModuleNames = setOf("prog8_lib", "prog8_math")

    override fun visit(module: Module) {
        if(module.name !in skipModuleNames)
        if(!module.isLibrary || !skipLibraries) {
            if(module.source.isFromFilesystem || module.source.isFromResources) {
                currentModule = module
                super.visit(module)
            }
        }
    }

    override fun visit(block: Block) {
        val (vars, others) = block.statements.filter{ it is Subroutine || it is Alias || it is VarDecl }.partition { it is VarDecl }
        if(vars.isNotEmpty() || others.isNotEmpty()) {
            outputln("${block.name}  {")
            for (variable in vars.sortedBy { (it as VarDecl).name }) {
                output("    ")
                variable.accept(this)
            }

            val byname = others.map {
                val name = if(it is Alias) it.alias else if(it is Subroutine) it.name else "???"
                name to it
            }

            for((_, thing) in byname.sortedBy { it.first }) {
                when (thing) {
                    is Subroutine -> {
                        output("    ")
                        thing.accept(this)
                    }

                    is Alias -> {
                        output("    ")
                        thing.accept(this)
                    }

                    else -> {
                        outputln("???")
                    }
                }
            }

            outputln("}\n")
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

        output(decl.datatype.sourceString())
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
                output("${param.first.type.sourceString()} ${param.first.name} @$reg")
                if(param.first!==subroutine.parameters.last())
                    output(", ")
            }
        }
        else {
            output("${subroutine.name}  (")
            for(param in subroutine.parameters) {
                output("${param.type.sourceString()} ${param.name}")
                if(param!==subroutine.parameters.last())
                    output(", ")
            }
        }
        output(") ")
        if(subroutine.asmClobbers.isNotEmpty()) {
            output(" clobbers (")
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
                    val dtstr = it.first.sourceString()
                    if(it.second.registerOrPair!=null)
                        "$dtstr @${it.second.registerOrPair}"
                    else
                        "$dtstr @${it.second.statusflag}"
                }
                output("-> $rts ")
            } else {
                val rts = subroutine.returntypes.joinToString(", ") { it.sourceString() }
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

    override fun visit(alias: Alias) {
        output("${alias.alias}   alias for: ${alias.target.nameInSource.joinToString(".")}\n")
    }
}
