package prog8.code.ast

import prog8.code.StNodeType
import prog8.code.SymbolTable
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.code.core.Position


fun findBankSelectorExtsubs(program: PtProgram, symbolTable: SymbolTable): List<PtAsmSub> {
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
    return eligible
}


fun writeBankedCallsFile(program: PtProgram, symbolTable: SymbolTable, options: CompilationOptions, errors: IErrorReporter) {
    val eligible = findBankSelectorExtsubs(program, symbolTable)
    if (eligible.isEmpty())
        return

    val file = options.outputDir.resolve("${program.name}.bankedcalls")

    // check existing file for ID changes that might break overlay managers
    val existingFile = file.toFile()
    if (existingFile.exists()) {
        val oldIds = mutableMapOf<String, Int>()
        existingFile.readLines().drop(2).forEach { line ->
            val parts = line.split(Regex("\\s{2,}"))
            if (parts.size >= 3) {
                val id = parts[0].toIntOrNull()
                val name = parts[2].trim()
                if (id != null && name.isNotEmpty())
                    oldIds[name] = id
            }
        }
        val changes = eligible.mapIndexedNotNull { index, node ->
            val oldId = oldIds[node.scopedName]
            if (oldId != null && oldId != index)
                "${node.scopedName}: was ID ${oldId}, now ${index}"
            else
                null
        }
        if (changes.isNotEmpty())
            errors.warn("banked call-site IDs have changed:\n  " + changes.joinToString("\n  "), Position.DUMMY)
    }

    existingFile.outputStream().bufferedWriter().use { writer ->
        val maxNameLen = eligible.maxOf { it.scopedName.length }
        val nameColLen = maxOf(maxNameLen, 4)
        val header = "ID  " + "Address".padEnd(8) + "Name".padEnd(nameColLen + 2) + "BankSelector\n"
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
