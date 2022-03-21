package prog8.codegen.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


internal class AssemblyProgram(override val name: String,
                               private val allocations: VariableAllocator,
                               private val instructions: MutableList<String>
) : IAssemblyProgram {
    override fun assemble(options: CompilationOptions): Boolean {
        val outfile = options.outputDir / ("$name.p8virt")
        println("write code to ${outfile}")
        outfile.bufferedWriter().use {
            allocations.asVmMemory().forEach { alloc -> it.write(alloc + "\n") }
            it.write("------PROGRAM------\n")
            instructions.forEach { ins -> it.write(ins + "\n") }
        }
        return true
    }
}
