package prog8.compilerinterface


interface IAssemblyGenerator {
    fun compileToAssembly(): IAssemblyProgram?
}

interface IAssemblyProgram {
    val name: String
    fun assemble(options: CompilationOptions): Boolean
}

fun viceMonListName(baseFilename: String) = "$baseFilename.vice-mon-list"
