package prog8.code.assembly

import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter

interface IAssemblyProgram {
    val name: String
    val irInstructionCount: Int
    val irChunkCount: Int
    val irRegisterCount: Int
    fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean
}
