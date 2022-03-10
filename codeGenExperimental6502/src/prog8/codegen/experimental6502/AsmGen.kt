package prog8.codegen.experimental6502

import prog8.code.ast.PtProgram
import prog8.compilerinterface.*


class AsmGen(internal val program: PtProgram,
             internal val errors: IErrorReporter,
             internal val symbolTable: SymbolTable,
             internal val options: CompilationOptions
): IAssemblyGenerator {

    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental 65(c)02 code generator **\n")

        symbolTable.print()
        symbolTable.flat.forEach { println(it) }
        program.print()

        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }
}