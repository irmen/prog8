package prog8.codegen.experimental6502

import prog8.ast.Program
import prog8.compilerinterface.*

class AsmGen(internal val program: Program,
             internal val errors: IErrorReporter,
             internal val variables: IVariablesAndConsts,
             internal val options: CompilationOptions): IAssemblyGenerator {

    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental 65(c)02 code generator **\n")

        val stMaker = SymbolTableMaker()
        val symbolTable = stMaker.make(program)
        symbolTable.print()

        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }
}