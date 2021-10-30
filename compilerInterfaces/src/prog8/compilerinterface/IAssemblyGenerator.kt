package prog8.compilerinterface

interface IAssemblyGenerator {
    fun compileToAssembly(): IAssemblyProgram
}

const val generatedLabelPrefix = "_prog8_label_"
const val subroutineFloatEvalResultVar1 = "_prog8_float_eval_result1"
const val subroutineFloatEvalResultVar2 = "_prog8_float_eval_result2"

interface IAssemblyProgram {
    val valid: Boolean
    val name: String
    fun assemble(quiet: Boolean, options: CompilationOptions): Int
}
