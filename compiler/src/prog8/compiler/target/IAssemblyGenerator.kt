package prog8.compiler.target

import prog8.compiler.CompilationOptions

internal interface IAssemblyGenerator {
    fun compileToAssembly(optimize: Boolean): IAssemblyProgram
}

internal interface IAssemblyProgram {
    val name: String
    fun assemble(options: CompilationOptions)
}
