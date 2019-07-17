package prog8.vm.astvm

import prog8.ast.IExpression
import prog8.ast.Program
import prog8.ast.base.ArrayElementTypes
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.BuiltinFunctionStatementPlaceholder
import prog8.ast.statements.Label
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.vm.RuntimeValue
import prog8.vm.RuntimeValueRange
import kotlin.math.abs


typealias BuiltinfunctionCaller =  (name: String, args: List<RuntimeValue>, flags: StatusFlags) -> RuntimeValue?
typealias SubroutineCaller =  (sub: Subroutine, args: List<RuntimeValue>, startAtLabel: Label?) -> RuntimeValue?


class EvalContext(val program: Program, val mem: Memory, val statusflags: StatusFlags,
                  val runtimeVars: RuntimeVariables,
                  val performBuiltinFunction: BuiltinfunctionCaller,
                  val executeSubroutine: SubroutineCaller)

fun evaluate(expr: IExpression, ctx: EvalContext): RuntimeValue {
    val constval = expr.constValue(ctx.program)
    if(constval!=null)
        return RuntimeValue.fromLv(constval)

    when(expr) {
        is NumericLiteralValue -> {
            return RuntimeValue.fromLv(expr)
        }
        is ReferenceLiteralValue -> {
            return RuntimeValue.fromLv(expr, ctx.program.heap)
        }
        is PrefixExpression -> {
            return when(expr.operator) {
                "-" -> evaluate(expr.expression, ctx).neg()
                "~" -> evaluate(expr.expression, ctx).inv()
                "not" -> evaluate(expr.expression, ctx).not()
                // unary '+' should have been optimized away
                else -> throw VmExecutionException("unsupported prefix operator "+expr.operator)
            }
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
                "**" -> left.pow(right)
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
                "|" -> left.bitor(right)
                "&" -> left.bitand(right)
                "^" -> left.bitxor(right)
                "and" -> left.and(right)
                "or" -> left.or(right)
                "xor" -> left.xor(right)
                else -> throw VmExecutionException("unsupported operator "+expr.operator)
            }
        }
        is ArrayIndexedExpression -> {
            val array = evaluate(expr.identifier, ctx)
            val index = evaluate(expr.arrayspec.index, ctx)
            val value = array.array!![index.integerValue()]
            return RuntimeValue(ArrayElementTypes.getValue(array.type), value)
        }
        is TypecastExpression -> {
            return evaluate(expr.expression, ctx).cast(expr.type)
        }
        is AddressOf -> {
            // we support: address of heap var -> the heap id
            return try {
                val heapId = expr.identifier.heapId(ctx.program.namespace)
                RuntimeValue(DataType.UWORD, heapId)
            } catch( f: FatalAstException) {
                // fallback: use the hash of the name, so we have at least *a* value...
                val address = expr.identifier.hashCode() and 65535
                RuntimeValue(DataType.UWORD, address)
            }
        }
        is DirectMemoryRead -> {
            val address = evaluate(expr.addressExpression, ctx).wordval!!
            return RuntimeValue(DataType.UBYTE, ctx.mem.getUByte(address))
        }
        is RegisterExpr -> return ctx.runtimeVars.get(ctx.program.namespace, expr.register.name)
        is IdentifierReference -> {
            val scope = expr.definingScope()
            val variable = scope.lookup(expr.nameInSource, expr)
            if(variable is VarDecl) {
                when {
                    variable.type==VarDeclType.VAR -> return ctx.runtimeVars.get(variable.definingScope(), variable.name)
                    variable.datatype==DataType.STRUCT -> throw VmExecutionException("cannot process structs by-value.  at ${expr.position}")
                    else -> {
                        val address = ctx.runtimeVars.getMemoryAddress(variable.definingScope(), variable.name)
                        return when(variable.datatype) {
                            DataType.UBYTE -> RuntimeValue(DataType.UBYTE, ctx.mem.getUByte(address))
                            DataType.BYTE -> RuntimeValue(DataType.BYTE, ctx.mem.getSByte(address))
                            DataType.UWORD -> RuntimeValue(DataType.UWORD, ctx.mem.getUWord(address))
                            DataType.WORD -> RuntimeValue(DataType.WORD, ctx.mem.getSWord(address))
                            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, ctx.mem.getFloat(address))
                            DataType.STR -> RuntimeValue(DataType.STR, str = ctx.mem.getString(address))
                            DataType.STR_S -> RuntimeValue(DataType.STR_S, str = ctx.mem.getScreencodeString(address))
                            else -> throw VmExecutionException("unexpected datatype $variable")
                        }
                    }
                }
            } else
                throw VmExecutionException("weird identifier reference $variable")
        }
        is FunctionCall -> {
            val sub = expr.target.targetStatement(ctx.program.namespace)
            val args = expr.arglist.map { evaluate(it, ctx) }
            return when(sub) {
                is Subroutine -> {
                    val result = ctx.executeSubroutine(sub, args, null)
                            ?: throw VmExecutionException("expected a result from functioncall $expr")
                    result
                }
                is BuiltinFunctionStatementPlaceholder -> {
                    val result = ctx.performBuiltinFunction(sub.name, args, ctx.statusflags)
                            ?: throw VmExecutionException("expected 1 result from functioncall $expr")
                    result
                }
                else -> {
                    throw VmExecutionException("unimplemented function call target $sub")
                }
            }
        }
        is RangeExpr -> {
            val cRange = expr.toConstantIntegerRange()
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
            throw VmExecutionException("unimplemented expression node $expr")
        }
    }
}
