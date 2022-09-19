package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyGenerator
import prog8.code.core.IAssemblyProgram
import prog8.code.core.IErrorReporter
import prog8.codegen.intermediate.IRCodeGen
import prog8.intermediate.IRFileWriter

class CodeGen(private val program: PtProgram,
              private val symbolTable: SymbolTable,
              private val options: CompilationOptions,
              private val errors: IErrorReporter
): IAssemblyGenerator {
    override fun compileToAssembly(): IAssemblyProgram? {

        // you could write a code generator directly on the PtProgram AST,
        // but you can also use the Intermediate Representation to build a codegen on:
        val irCodeGen = IRCodeGen(program, symbolTable, options, errors)
        val irProgram = irCodeGen.generate()

        // this stub only writes the IR program to disk but doesn't generate anything else.
        IRFileWriter(irProgram).writeFile()

        println("** experimental codegen stub: no assembly generated **")
        return null
    }
}