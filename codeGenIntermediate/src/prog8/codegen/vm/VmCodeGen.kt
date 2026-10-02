package prog8.codegen.vm

import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.assembly.IAssemblyProgram
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.code.core.OutputFiles
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.*

class VmCodeGen(val retainSSA: Boolean,
                private val preassignedCallSiteIds: Map<String, UByte> = emptyMap()
): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram {
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA, preassignedCallSiteIds)
        val irProgram = irCodeGen.generate()

        val virtualRegisterTypes = irCodeGen.registerTypes().entries.associate { (num, type) ->
            val register: VirtualRegister = if (type == IRDataType.FLOAT) VirtualRegister.float(num.value) else VirtualRegister.int(num.value)
            register to type
        }
        irProgram.verifyRegisterTypes(virtualRegisterTypes)

        if (options.dumpVariables)
            dumpVariables(irProgram)

        return VmAssemblyProgram(irProgram.name, irProgram)
    }
}


internal class VmAssemblyProgram(
    override val name: String,
    internal val irProgram: IRProgram
): IAssemblyProgram {

    override val irInstructionCount: Int
        get() = irProgram.countCodeElements().first

    override val irChunkCount: Int
        get() = irProgram.countCodeElements().second

    override val irRegisterCount: Int
        get() = irProgram.countUsedRegisters()

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {
        // the VM reads the IR file from disk; for the virtual target the IR file is the program artifact.
        val programFile = OutputFiles.of(options, irProgram.name).programFile
        IRFileWriter(irProgram, programFile).write()
        return true
    }
}
