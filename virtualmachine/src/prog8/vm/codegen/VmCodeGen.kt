package prog8.vm.codegen

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyGenerator
import prog8.code.core.IAssemblyProgram
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter
import prog8.intermediate.IRProgram

class VmCodeGen(private val program: PtProgram,
                private val symbolTable: SymbolTable,
                private val options: CompilationOptions,
                private val errors: IErrorReporter
): IAssemblyGenerator {
    override fun compileToAssembly(): IAssemblyProgram? {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()

        // no need to check options.keepIR, as the VM file format *is* the IR file.
        return VmAssemblyProgram(irProgram.name, irProgram)
    }
}


internal class VmAssemblyProgram(override val name: String, private val irProgram: IRProgram): IAssemblyProgram {

    override fun assemble(options: CompilationOptions): Boolean {
        // the VM reads the IR file from disk.
        IRFileWriter(irProgram, null).write()
        return true
    }
}
