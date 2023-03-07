package prog8.code.core

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram

interface ICodeGeneratorBackend {
    fun generate(program: PtProgram,
                 symbolTable: SymbolTable,
                 options: CompilationOptions,
                 errors: IErrorReporter): IAssemblyProgram?
}


interface IAssemblyProgram {
    val name: String
    fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean
}
