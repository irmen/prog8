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
import prog8.intermediate.IRProgram
import java.nio.file.Path

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
            val irFile = IRFileWriter(irProgram, null).write()
            val irProgram2 = IRFileReader().read(irFile)
            VmAssemblyProgram(irProgram2.name, irProgram2)
        } else {
            VmAssemblyProgram(irProgram.name, irProgram)
        }
    }

    companion object {
        fun compileIR(irFile: Path): IAssemblyProgram {
            val irProgram = IRFileReader().read(irFile)
            return VmAssemblyProgram(irProgram.name, irProgram)
        }
    }
}


internal class VmAssemblyProgram(override val name: String, private val irProgram: IRProgram): IAssemblyProgram {

    override fun assemble(options: CompilationOptions): Boolean {
        val writtenFile = IRFileWriter(irProgram, null).write()
        println("Wrote intermediate representation to $writtenFile")
        return true
    }
}
