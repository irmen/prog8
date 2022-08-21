package prog8.codegen.experimental

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram

class DummyAssemblyProgram(override val name: String): IAssemblyProgram {

    override fun assemble(options: CompilationOptions): Boolean {
        println("TODO WRITE ASSEMBLY")
        return false
    }
}