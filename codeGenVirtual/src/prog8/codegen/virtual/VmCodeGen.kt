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
import kotlin.io.path.Path

class VmCodeGen(private val program: PtProgram,
                private val symbolTable: SymbolTable,
                private val options: CompilationOptions,
                private val errors: IErrorReporter
): IAssemblyGenerator {
    override fun compileToAssembly(): IAssemblyProgram? {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()

        return if(options.keepIR) {
            //create IR file on disk and read it back.
            IRFileWriter(irProgram).writeFile()
            val irProgram2 = IRFileReader(options.outputDir, irProgram.name).readFile()
            VmAssemblyProgram(irProgram2.name, irProgram2)
        } else {
            VmAssemblyProgram(irProgram.name, irProgram)
        }
    }

    companion object {
        fun compileIR(listingFilename: String): IAssemblyProgram {
            val irProgram = IRFileReader(Path(""), listingFilename).readFile()
            return VmAssemblyProgram(irProgram.name, irProgram)
        }
    }
}