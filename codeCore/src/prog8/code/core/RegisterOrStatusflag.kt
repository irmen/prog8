package prog8.code.core

data class RegisterOrStatusflag(val registerOrPair: RegisterOrPair?, val statusflag: Statusflag?)

/**
 * The M68K multi-value return convention: non-float values are returned in data registers
 * D0..D7 in declaration order, float values in FPU registers FP0..FP7 in declaration order
 * (independent counters). Single source of truth, shared by the simpleAst signature mapping
 * and the M68K backend's save-set exclusion so the two cannot drift.
 */
fun m68kReturnRegisters(returns: List<DataType>, context: String = ""): List<Pair<String, DataType>> {
    var data = 0
    var float = 0
    val where = if(context.isEmpty()) "" else " $context"
    return returns.map { type ->
        when {
            type.isFloat -> {
                if(float > 7)
                    throw AssemblyError("out of fp registers for return type $type$where")
                "fp${float++}" to type
            }
            type.isNumericOrBool || type.isPointer -> {
                if(data > 7)
                    throw AssemblyError("out of data registers for return type $type$where")
                "d${data++}" to type
            }
            else -> throw AssemblyError("unsupported return type $type$where")
        }
    }
}
