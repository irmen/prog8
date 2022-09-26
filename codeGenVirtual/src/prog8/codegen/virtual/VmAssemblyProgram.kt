package prog8.codegen.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import prog8.intermediate.IRFileWriter
import prog8.intermediate.IRProgram


internal class VmAssemblyProgram(override val name: String, private val irProgram: IRProgram): IAssemblyProgram {

    override fun assemble(options: CompilationOptions): Boolean {
        val writtenFile = IRFileWriter(irProgram, null).write()
        println("Wrote intermediate representation to $writtenFile")
        return true
    }
}
