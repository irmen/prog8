package prog8.codegen.new6502

import prog8.code.ICodeGeneratorBackend
import prog8.code.SymbolTable
import prog8.code.assembly.AssemblyProgram6502
import prog8.code.assembly.IAssemblyProgram
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
        if (!irCodeGen.wasPackingApplied)
            irProgram.verifyRegisterTypes(irCodeGen.registerTypes())

        IRFileWriter(irProgram, null).write()

        val gen = AsmGen(irProgram, irProgram.options.compTarget)
        val success = gen.generate()
        if(!success)
            return null

        return AssemblyProgram6502(irProgram.name, irProgram.options.outputDir, irProgram.options.compTarget)
    }
}
