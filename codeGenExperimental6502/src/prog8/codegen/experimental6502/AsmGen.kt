package prog8.codegen.experimental6502

import prog8.ast.Program
import prog8.compilerinterface.*
import prog8.sim.Simulator

class AsmGen(internal val program: Program,
             internal val errors: IErrorReporter,
             internal val symbolTable: SymbolTable,
             internal val options: CompilationOptions): IAssemblyGenerator {

    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental 65(c)02 code generator **\n")

        symbolTable.print()
        symbolTable.flat.forEach { println(it) }

        // TODO temporary location to do this:
        val intermediateAst = IntermediateAstMaker(program).transform()
        intermediateAst.print()

        val sim = Simulator(intermediateAst, symbolTable)
        sim.run()

        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }
}