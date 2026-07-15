package prog8.codegen.m68k

import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.assembly.IAssemblyProgram
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter

class M68kCodeGenerator(val retainSSA: Boolean): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram {

        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA)
        val irProgram = irCodeGen.generate()
        if (!irCodeGen.wasPackingApplied)
            irProgram.verifyRegisterTypes(irCodeGen.registerTypes())        // TODO run an alternative

        IRFileWriter(irProgram, null).write()

        val gen = AsmGen(irProgram, irProgram.options.compTarget)
        if (!gen.generate())
            throw RuntimeException("M68k assembly generation failed")

        return AssemblyProgramM68k(irProgram.name, irProgram.options.outputDir)
    }
}
