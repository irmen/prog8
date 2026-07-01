package prog8.codegen.m68k

import prog8.code.IAssemblyProgram
import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
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

        // The M68K code generator works from the Intermediate Representation
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA)
        val irProgram = irCodeGen.generate()
        irProgram.verifyRegisterTypes(irCodeGen.registerTypes())

        // TODO remove this later: also write the IR program to disk for now for debugging purposes
        IRFileWriter(irProgram, null).write()
        
        val gen = AsmGen()
        return gen.generate(irProgram)
    }
}
