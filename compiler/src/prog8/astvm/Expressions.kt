package prog8.astvm

import prog8.ast.*
import prog8.compiler.RuntimeValue

fun evaluate(expr: IExpression, program: Program, runtimeVars: RuntimeVariables,
             executeSubroutine: (sub: Subroutine, args: List<RuntimeValue>) -> List<RuntimeValue>): RuntimeValue {
    val constval = expr.constValue(program)
    if(constval!=null)
        return RuntimeValue.from(constval, program.heap)

    when(expr) {
        is LiteralValue -> {
            return RuntimeValue.from(expr, program.heap)
        }
        is PrefixExpression -> {
            TODO("$expr")
        }
        is BinaryExpression -> {
            val left = evaluate(expr.left, program, runtimeVars, executeSubroutine)
            val right = evaluate(expr.right, program, runtimeVars, executeSubroutine)
            return when(expr.operator) {
                "<" -> RuntimeValue(DataType.UBYTE, if (left < right) 1 else 0)
                "<=" -> RuntimeValue(DataType.UBYTE, if (left <= right) 1 else 0)
                ">" -> RuntimeValue(DataType.UBYTE, if (left > right) 1 else 0)
                ">=" -> RuntimeValue(DataType.UBYTE, if (left >= right) 1 else 0)
                "==" -> RuntimeValue(DataType.UBYTE, if (left == right) 1 else 0)
                "!=" -> RuntimeValue(DataType.UBYTE, if (left != right) 1 else 0)
                "+" -> {
                    val result = left.add(right)
                    RuntimeValue(result.type, result.numericValue())
                }
                "-" -> {
                    val result = left.sub(right)
                    RuntimeValue(result.type, result.numericValue())
                }
                else -> TODO("binexpression operator ${expr.operator}")
            }
        }
        is ArrayIndexedExpression -> {
            val array = evaluate(expr.identifier, program, runtimeVars, executeSubroutine)
            val index = evaluate(expr.arrayspec.index, program, runtimeVars, executeSubroutine)
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
            return evaluate(expr.expression, program, runtimeVars, executeSubroutine).cast(expr.type)
        }
        is AddressOf -> {
            // we support: address of heap var -> the heap id
            val heapId = expr.identifier.heapId(program.namespace)
            return RuntimeValue(DataType.UWORD, heapId)
        }
        is DirectMemoryRead -> {
            TODO("$expr")

        }
        is DirectMemoryWrite -> {
            TODO("$expr")

        }
        is RegisterExpr -> {
            TODO("$expr")
        }
        is IdentifierReference -> {
            val scope = expr.definingScope()
            val variable = scope.lookup(expr.nameInSource, expr)
            if(variable is VarDecl) {
                val stmt = scope.lookup(listOf(variable.name), expr)!!
                return runtimeVars.get(stmt.definingScope(), variable.name)
            } else
                TODO("$variable")
        }
        is FunctionCall -> {
            val sub = expr.target.targetStatement(program.namespace)
            val args = expr.arglist.map { evaluate(it, program, runtimeVars, executeSubroutine) }
            when(sub) {
                is Subroutine -> {
                    val results = executeSubroutine(sub, args)
                    if(results.size!=1)
                        throw VmExecutionException("expected 1 result from functioncall $expr")
                    return results[0]
                }
                else -> {
                    TODO("call expr function ${expr.target}")
                }
            }
        }
        is RangeExpr -> {
            TODO("eval range $expr")
        }
        else -> {
            TODO("implement eval $expr")
        }
    }
}
