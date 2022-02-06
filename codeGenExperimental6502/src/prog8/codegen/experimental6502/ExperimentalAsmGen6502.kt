package prog8.codegen.experimental6502

import prog8.ast.Program
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IAssemblyGenerator
import prog8.compilerinterface.IAssemblyProgram
import prog8.compilerinterface.IErrorReporter

class ExperimentalAsmGen6502(internal val program: Program,
                 internal val errors: IErrorReporter,
                 internal val options: CompilationOptions
): IAssemblyGenerator {
    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental 65(c)02 code generator **\n")

        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }
}