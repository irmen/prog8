package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyGenerator
import prog8.code.core.IAssemblyProgram
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileReader
import prog8.intermediate.IRFileWriter

class VmCodeGen(private val program: PtProgram,
                private val symbolTable: SymbolTable,
                private val options: CompilationOptions,
                private val errors: IErrorReporter
): IAssemblyGenerator {
    override fun compileToAssembly(): IAssemblyProgram? {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()

        // TODO only write IR file if option is set to do so
        // create IR file on disk and read it back.
        IRFileWriter(irProgram).writeFile()
        val irProgram2 = IRFileReader(options.outputDir, irProgram.name).readFile()
        return VmAssemblyProgram(irProgram2.name, irProgram2)
    }
}