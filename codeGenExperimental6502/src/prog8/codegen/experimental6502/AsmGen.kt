package prog8.codegen.experimental6502

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyGenerator
import prog8.code.core.IAssemblyProgram
import prog8.code.core.IErrorReporter

/*

    NOTE: The goal is to keep the dependencies as lean as possible! For now, we depend only on:
         - codeAst (the 'lean' new AST and the SymbolTable)
         - codeCore (various base enums and interfaces)

    This *should* be enough to build a complete code generator with. But we'll see :)

 */

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