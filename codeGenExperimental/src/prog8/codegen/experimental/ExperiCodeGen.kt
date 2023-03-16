package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import prog8.code.core.ICodeGeneratorBackend
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter

class ExperiCodeGen: ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram? {

        if(options.useRPN)
            program.transformBinExprToRPN()

        // you could write a code generator directly on the PtProgram AST,
        // but you can also use the Intermediate Representation to build a codegen on:
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()

        // this stub only writes the IR program to disk but doesn't generate anything else.
        IRFileWriter(irProgram, null).write()

        println("** experimental codegen stub: no assembly generated **")
        return null
    }
}