package prog8.codegen.experimental

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram


internal class AssemblyProgram(override val name: String) : IAssemblyProgram
{
    override fun assemble(options: CompilationOptions): Boolean {
        println("..todo: assemble code into binary..")
        return true
    }
}
