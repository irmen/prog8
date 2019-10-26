package prog8.compiler.target

import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage


interface IMachineDefinition {
    val FLOAT_MAX_NEGATIVE: Double
    val FLOAT_MAX_POSITIVE: Double
    val FLOAT_MEM_SIZE: Int

    val opcodeNames: Set<String>

    fun getZeropage(compilerOptions: CompilationOptions): Zeropage
}
