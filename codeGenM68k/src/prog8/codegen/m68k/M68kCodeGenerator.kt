package prog8.codegen.m68k

import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.assembly.IAssemblyProgram
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRDataType
import prog8.intermediate.IRFileWriter
import prog8.intermediate.VirtualRegister
import prog8.intermediate.dumpVariables

class M68kCodeGenerator(val retainSSA: Boolean): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA)
        val irProgram = irCodeGen.generate()
        val virtualRegisterTypes = irCodeGen.registerTypes().entries.associate { (num, type) ->
            val register: VirtualRegister = if (type == IRDataType.FLOAT) VirtualRegister.float(num.value) else VirtualRegister.int(num.value)
            register to type
        }
        irProgram.verifyRegisterTypes(virtualRegisterTypes)

        if (options.dumpVariables)
            dumpVariables(irProgram)

        IRFileWriter(irProgram, null).write()

        val gen = AsmGen(irProgram, irProgram.options.compTarget)
        if (!gen.generate())
            throw RuntimeException("M68k assembly generation failed")

        return AssemblyProgramM68k(irProgram.name, irProgram.options.outputDir)
    }
}
