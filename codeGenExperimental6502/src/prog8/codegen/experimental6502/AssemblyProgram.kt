package prog8.codegen.experimental6502

import prog8.code.core.AssemblerOptions
import prog8.code.core.IAssemblyProgram


internal class AssemblyProgram(override val name: String) : IAssemblyProgram
{
    override fun assemble(options: AssemblerOptions): Boolean {
        println("..todo: assemble code into binary..")
        return false
    }
}
