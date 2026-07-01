package prog8.codegen.new6502

import prog8.code.IAssemblyProgram
import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter

class New6502CodeGenerator(val retainSSA: Boolean,
                    private val preassignedCallSiteIds: Map<String, UByte> = emptyMap()
): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram? {

        // you could write a code generator directly on the PtProgram AST,
        // but you can also use the Intermediate Representation to build a codegen on:
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors, retainSSA, preassignedCallSiteIds)
        val irProgram = irCodeGen.generate()
        irProgram.verifyRegisterTypes(irCodeGen.registerTypes())

        // TODO remove this later: also write the IR program to disk for now for debugging purposes
        IRFileWriter(irProgram, null).write()

        val gen = AsmGen(irProgram, irProgram.options.compTarget)
        val success = gen.generate()
        if(!success)
            return null

        TODO("construct valid assembly program result")             // TODO reuse AssemblyProgram from regular 6502 code generator 
    }
}
