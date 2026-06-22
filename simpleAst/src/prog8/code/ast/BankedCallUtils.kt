package prog8.code.ast

import prog8.code.StNodeType
import prog8.code.SymbolTable
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import kotlin.io.path.outputStream


fun findBankManagerExtsubs(program: PtProgram, symbolTable: SymbolTable): List<PtAsmSub> {
    val eligible = mutableListOf<PtAsmSub>()
    walkAst(program) { node, _ ->
        if (node is PtAsmSub) {
            val varbank = node.address?.varbank
            if (varbank != null) {
                val bankSymbol = symbolTable.lookup(varbank.name) ?: symbolTable.lookupUnscoped(varbank.name)
                if (bankSymbol?.type == StNodeType.SUBROUTINE || bankSymbol?.type == StNodeType.EXTSUB) {
                    eligible.add(node)
                }
            }
        }
        true
    }
    eligible.sortBy { it.scopedName }
    return eligible
}


fun writeBankedCallsFile(program: PtProgram, symbolTable: SymbolTable, options: CompilationOptions, errors: IErrorReporter) {
    val eligible = findBankManagerExtsubs(program, symbolTable)
    if (eligible.isEmpty())
        return

    val file = options.outputDir.resolve("${program.name}.bankedcalls")
    file.toFile().outputStream().bufferedWriter().use { writer ->
        val maxNameLen = eligible.maxOf { it.scopedName.length }
        val nameColLen = maxOf(maxNameLen, 4)
        val header = "ID  " + "Address".padEnd(8) + "Name".padEnd(nameColLen + 2) + "BankManager\n"
        writer.write(header)
        writer.write("-".repeat(header.length - 1) + "\n")
        eligible.forEachIndexed { index, node ->
            val bankName = node.address?.varbank?.name ?: "?"
            val hexAddr = node.address?.address?.toString(16) ?: "?"
            val addr = if (hexAddr != "?" && !hexAddr.startsWith('$')) "$$hexAddr" else hexAddr
            writer.write(index.toString().padEnd(4) + addr.padEnd(8) + node.scopedName.padEnd(nameColLen + 2) + bankName + "\n")
        }
    }
    errors.info("extsub banking call-site IDs written to $file", Position.DUMMY)
}
