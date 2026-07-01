package prog8.codegen.m68k

import prog8.code.assembly.IAssemblyProgram
import prog8.intermediate.IRProgram

internal class AsmGen {
    fun generate(program: IRProgram): IAssemblyProgram {

        val target = program.options.compTarget
        println("Generating assembly.")
        println("Program name: ${program.name}")
        println("Target system and CPU: ${target.name} / ${target.cpu}")
        
        TODO("m68k assembly generation based on IR")
    }
}
