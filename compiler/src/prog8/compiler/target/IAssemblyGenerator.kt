package prog8.compiler.target

import prog8.compiler.CompilationOptions

internal interface IAssemblyGenerator {
    fun compileToAssembly(): IAssemblyProgram
}

internal const val generatedLabelPrefix = "_prog8_label_"
internal const val subroutineFloatEvalResultVar1 = "_prog8_float_eval_result1"
internal const val subroutineFloatEvalResultVar2 = "_prog8_float_eval_result2"

internal interface IAssemblyProgram {
    val name: String
    fun assemble(options: CompilationOptions): Int
}
