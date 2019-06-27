package prog8.astvm

import prog8.ast.*
import prog8.compiler.RuntimeValue
import prog8.compiler.RuntimeValueRange
import kotlin.math.abs

class EvalContext(val program: Program, val mem: Memory,
                  val runtimeVars: RuntimeVariables, val functions: BuiltinFunctions,
                  val executeSubroutine: (sub: Subroutine, args: List<RuntimeValue>) -> List<RuntimeValue>)

fun evaluate(expr: IExpression, ctx: EvalContext): RuntimeValue {
    val constval = expr.constValue(ctx.program)
    if(constval!=null)
        return RuntimeValue.from(constval, ctx.program.heap)

    when(expr) {
        is LiteralValue -> {
            return RuntimeValue.from(expr, ctx.program.heap)
        }
        is PrefixExpression -> {
            TODO("prefixexpr $expr")
        }
        is BinaryExpression -> {
            val left = evaluate(expr.left, ctx)
            val right = evaluate(expr.right, ctx)
            return when(expr.operator) {
                "<" -> RuntimeValue(DataType.UBYTE, if (left < right) 1 else 0)
                "<=" -> RuntimeValue(DataType.UBYTE, if (left <= right) 1 else 0)
                ">" -> RuntimeValue(DataType.UBYTE, if (left > right) 1 else 0)
                ">=" -> RuntimeValue(DataType.UBYTE, if (left >= right) 1 else 0)
                "==" -> RuntimeValue(DataType.UBYTE, if (left == right) 1 else 0)
                "!=" -> RuntimeValue(DataType.UBYTE, if (left != right) 1 else 0)
                "+" -> left.add(right)
                "-" -> left.sub(right)
                "*" -> left.mul(right)
                "/" -> left.div(right)
                "<<" -> {
                    var result = left
                    repeat(right.integerValue()) {result = result.shl()}
                    result
                }
                ">>" -> {
                    var result = left
                    repeat(right.integerValue()) {result = result.shr()}
                    result
                }
                "%" -> left.remainder(right)
                "|" -> left.or(right)
                "&" -> left.and(right)
                "^" -> left.xor(right)
                else -> TODO("binexpression operator ${expr.operator}")
            }
        }
        is ArrayIndexedExpression -> {
            val array = evaluate(expr.identifier, ctx)
            val index = evaluate(expr.arrayspec.index, ctx)
            val value = array.array!![index.integerValue()]
            return when(array.type) {
                DataType.ARRAY_UB -> RuntimeValue(DataType.UBYTE, num = value)
                DataType.ARRAY_B -> RuntimeValue(DataType.BYTE, num = value)
                DataType.ARRAY_UW -> RuntimeValue(DataType.UWORD, num = value)
                DataType.ARRAY_W -> RuntimeValue(DataType.WORD, num = value)
                DataType.ARRAY_F -> RuntimeValue(DataType.FLOAT, num = value)
                else -> throw VmExecutionException("strange array type ${array.type}")
            }
        }
        is TypecastExpression -> {
            return evaluate(expr.expression, ctx).cast(expr.type)
        }
        is AddressOf -> {
            // we support: address of heap var -> the heap id
            val heapId = expr.identifier.heapId(ctx.program.namespace)
            return RuntimeValue(DataType.UWORD, heapId)
        }
        is DirectMemoryRead -> {
            TODO("memoryread $expr")
        }
        is DirectMemoryWrite -> {
            TODO("memorywrite $expr")
        }
        is RegisterExpr -> return ctx.runtimeVars.get(ctx.program.namespace, expr.register.name)
        is IdentifierReference -> {
            val scope = expr.definingScope()
            val variable = scope.lookup(expr.nameInSource, expr)
            if(variable is VarDecl) {
                if(variable.type==VarDeclType.VAR)
                    return ctx.runtimeVars.get(variable.definingScope(), variable.name)
                else {
                    val address = ctx.runtimeVars.getMemoryAddress(variable.definingScope(), variable.name)
                    return when(variable.datatype) {
                        DataType.UBYTE -> RuntimeValue(DataType.UBYTE, ctx.mem.getUByte(address))
                        DataType.BYTE -> RuntimeValue(DataType.BYTE, ctx.mem.getSByte(address))
                        DataType.UWORD -> RuntimeValue(DataType.UWORD, ctx.mem.getUWord(address))
                        DataType.WORD -> RuntimeValue(DataType.WORD, ctx.mem.getSWord(address))
                        DataType.FLOAT -> RuntimeValue(DataType.FLOAT, ctx.mem.getFloat(address))
                        DataType.STR -> RuntimeValue(DataType.STR, str=ctx.mem.getString(address))
                        DataType.STR_S -> RuntimeValue(DataType.STR_S, str=ctx.mem.getScreencodeString(address))
                        else -> TODO("memvar $variable")
                    }
                }
            } else
                TODO("weird ref $variable")
        }
        is FunctionCall -> {
            val sub = expr.target.targetStatement(ctx.program.namespace)
            val args = expr.arglist.map { evaluate(it, ctx) }
            return when(sub) {
                is Subroutine -> {
                    val results = ctx.executeSubroutine(sub, args)
                    if(results.size!=1)
                        throw VmExecutionException("expected 1 result from functioncall $expr")
                    results[0]
                }
                is BuiltinFunctionStatementPlaceholder -> {
                    val result = ctx.functions.performBuiltinFunction(sub.name, args) ?: throw VmExecutionException("expected 1 result from functioncall $expr")
                    result
                }
                else -> {
                    TODO("call expr function ${expr.target}")
                }
            }
        }
        is RangeExpr -> {
            val cRange = expr.toConstantIntegerRange(ctx.program.heap)
            if(cRange!=null)
                return RuntimeValueRange(expr.inferType(ctx.program)!!, cRange)
            val fromVal = evaluate(expr.from, ctx).integerValue()
            val toVal = evaluate(expr.to, ctx).integerValue()
            val stepVal = evaluate(expr.step, ctx).integerValue()
            val range = when {
                fromVal <= toVal -> when {
                    stepVal <= 0 -> IntRange.EMPTY
                    stepVal == 1 -> fromVal..toVal
                    else -> fromVal..toVal step stepVal
                }
                else -> when {
                    stepVal >= 0 -> IntRange.EMPTY
                    stepVal == -1 -> fromVal downTo toVal
                    else -> fromVal downTo toVal step abs(stepVal)
                }
            }
            return RuntimeValueRange(expr.inferType(ctx.program)!!, range)
        }
        else -> {
            TODO("implement eval $expr")
        }
    }
}
