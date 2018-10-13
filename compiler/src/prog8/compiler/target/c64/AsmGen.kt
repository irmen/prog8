package prog8.compiler.target.c64

import prog8.compiler.CompilationOptions
import prog8.compiler.intermediate.IntermediateProgram


class AsmGen(val options: CompilationOptions) {
    fun compileToAssembly(program: IntermediateProgram): AssemblyProgram {
        println("\nGenerating assembly code from intermediate code... ")
        // todo generate 6502 assembly
        return AssemblyProgram(program.name)
    }
}


