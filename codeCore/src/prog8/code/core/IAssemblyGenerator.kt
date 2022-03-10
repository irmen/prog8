package prog8.code.core

import java.nio.file.Path


interface IAssemblyGenerator {
    fun compileToAssembly(): IAssemblyProgram?
}

interface IAssemblyProgram {
    val name: String
    fun assemble(options: AssemblerOptions): Boolean
}

fun viceMonListName(baseFilename: String) = "$baseFilename.vice-mon-list"


class AssemblerOptions(
    val output: OutputType,
    var asmQuiet: Boolean = false,
    var asmListfile: Boolean = false,
    var outputDir: Path
)
