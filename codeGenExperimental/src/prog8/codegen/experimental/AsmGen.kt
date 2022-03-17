package prog8.codegen.experimental

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

        println("\n** experimental code generator **\n")

        println("Writing AST into XML form...")
        val xmlConv = AstToXmlConverter(program, symbolTable, options)
        xmlConv.writeXml()

        println("..todo: create assembly program into ${options.outputDir.toAbsolutePath()}..")

        return AssemblyProgram("dummy")
    }
}