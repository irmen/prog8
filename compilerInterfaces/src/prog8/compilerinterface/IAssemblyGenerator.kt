package prog8.compilerinterface

interface IAssemblyGenerator {
    fun compileToAssembly(): IAssemblyProgram
}

interface IAssemblyProgram {
    val valid: Boolean
    val name: String
    fun assemble(options: CompilationOptions): Int
}
