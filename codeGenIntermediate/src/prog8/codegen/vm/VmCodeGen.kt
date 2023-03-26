package prog8.codegen.vm

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.*
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter
import prog8.intermediate.IRProgram

class VmCodeGen: ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram? {
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()
        return VmAssemblyProgram(irProgram.name, irProgram)
    }
}


internal class VmAssemblyProgram(override val name: String, internal val irProgram: IRProgram): IAssemblyProgram {

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {
        // the VM reads the IR file from disk.
        IRFileWriter(irProgram, null).write()
        return true
    }
}
