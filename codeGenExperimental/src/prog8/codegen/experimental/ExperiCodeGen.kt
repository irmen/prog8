package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.IAssemblyProgram
import prog8.code.ICodeGeneratorBackend
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter

class ExperiCodeGen: ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram {

        // you could write a code generator directly on the PtProgram AST,
        // but you can also use the Intermediate Representation to build a codegen on:
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()
        irProgram.verifyRegisterTypes(irCodeGen.registerTypes())

        // this stub only writes the IR program to disk but doesn't generate anything else.
        IRFileWriter(irProgram, null).write()

        println("** experimental codegen stub: no assembly generated **")
        return EmptyProgram
    }
}

private object EmptyProgram : IAssemblyProgram {
    override val name = "<Empty Program>"
    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {
        println("** nothing assembled **")
        return true
    }

}
