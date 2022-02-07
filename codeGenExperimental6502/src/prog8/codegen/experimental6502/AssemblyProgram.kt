package prog8.codegen.experimental6502

import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IAssemblyProgram


internal class AssemblyProgram(override val name: String) : IAssemblyProgram
{
    override fun assemble(options: CompilationOptions): Boolean {
        println("..todo: assemble code into binary..")
        return false
    }
}
