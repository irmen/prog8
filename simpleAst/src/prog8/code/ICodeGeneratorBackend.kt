package prog8.code

import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter

interface ICodeGeneratorBackend {
    fun generate(program: PtProgram,
                 symbolTable: SymbolTable,
                 options: CompilationOptions,
                 errors: IErrorReporter
    ): IAssemblyProgram?
}


interface IAssemblyProgram {
    val name: String
    fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean
}
