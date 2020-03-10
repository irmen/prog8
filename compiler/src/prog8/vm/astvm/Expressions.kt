package prog8.vm.astvm

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
import prog8.vm.*


typealias BuiltinfunctionCaller =  (name: String, args: List<RuntimeValueNumeric>, flags: StatusFlags) -> RuntimeValueNumeric?
typealias SubroutineCaller =  (sub: Subroutine, args: List<RuntimeValueNumeric>, startAtLabel: Label?) -> RuntimeValueNumeric?


class EvalContext(val program: Program, val mem: Memory, val statusflags: StatusFlags,
                  val runtimeVars: RuntimeVariables,
                  val performBuiltinFunction: BuiltinfunctionCaller,
                  val executeSubroutine: SubroutineCaller)

fun evaluate(expr: Expression, ctx: EvalContext): RuntimeValueBase {
    val constval = expr.constValue(ctx.program)
    if(constval!=null)
        return RuntimeValueNumeric.fromLv(constval)

    when(expr) {
        is NumericLiteralValue -> return RuntimeValueNumeric.fromLv(expr)
        is StringLiteralValue -> return RuntimeValueString.fromLv(expr)
        is ArrayLiteralValue -> return RuntimeValueArray.fromLv(expr)
        is PrefixExpression -> {
            return when(expr.operator) {
                "-" -> (evaluate(expr.expression, ctx) as RuntimeValueNumeric).neg()
                "~" -> (evaluate(expr.expression, ctx) as RuntimeValueNumeric).inv()
                "not" -> (evaluate(expr.expression, ctx) as RuntimeValueNumeric).not()
                // unary '+' should have been optimized away
                else -> throw VmExecutionException("unsupported prefix operator "+expr.operator)
            }
        }
        is BinaryExpression -> {
            val left = evaluate(expr.left, ctx) as RuntimeValueNumeric
            val right = evaluate(expr.right, ctx) as RuntimeValueNumeric
            return when(expr.operator) {
                "<" -> RuntimeValueNumeric(DataType.UBYTE, if (left < right) 1 else 0)
                "<=" -> RuntimeValueNumeric(DataType.UBYTE, if (left <= right) 1 else 0)
                ">" -> RuntimeValueNumeric(DataType.UBYTE, if (left > right) 1 else 0)
                ">=" -> RuntimeValueNumeric(DataType.UBYTE, if (left >= right) 1 else 0)
                "==" -> RuntimeValueNumeric(DataType.UBYTE, if (left == right) 1 else 0)
                "!=" -> RuntimeValueNumeric(DataType.UBYTE, if (left != right) 1 else 0)
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
            val index = evaluate(expr.arrayspec.index, ctx) as RuntimeValueNumeric
            return when (array) {
                is RuntimeValueString -> {
                    val value = array.str[index.integerValue()]
                    RuntimeValueNumeric(ArrayElementTypes.getValue(array.type), value.toShort())
                }
                is RuntimeValueArray -> {
                    val value = array.array[index.integerValue()]
                    RuntimeValueNumeric(ArrayElementTypes.getValue(array.type), value)
                }
                else -> throw VmExecutionException("weird type")
            }
        }
        is TypecastExpression -> {
            return (evaluate(expr.expression, ctx) as RuntimeValueNumeric).cast(expr.type)
        }
        is AddressOf -> {
            // we support: address of heap var -> the heap id
            return try {
                val heapId = expr.identifier.heapId(ctx.program.namespace)
                RuntimeValueNumeric(DataType.UWORD, heapId)
            } catch( f: FatalAstException) {
                // fallback: use the hash of the name, so we have at least *a* value...
                val address = expr.identifier.hashCode() and 65535
                RuntimeValueNumeric(DataType.UWORD, address)
            }
        }
        is DirectMemoryRead -> {
            val address = (evaluate(expr.addressExpression, ctx) as RuntimeValueNumeric).wordval!!
            return RuntimeValueNumeric(DataType.UBYTE, ctx.mem.getUByte(address))
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
                            DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, ctx.mem.getUByte(address))
                            DataType.BYTE -> RuntimeValueNumeric(DataType.BYTE, ctx.mem.getSByte(address))
                            DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, ctx.mem.getUWord(address))
                            DataType.WORD -> RuntimeValueNumeric(DataType.WORD, ctx.mem.getSWord(address))
                            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, ctx.mem.getFloat(address))
                            DataType.STR -> RuntimeValueString(ctx.mem.getString(address, false), false, null)
                            else -> throw VmExecutionException("unexpected datatype $variable")
                        }
                    }
                }
            } else
                throw VmExecutionException("weird identifier reference $variable")
        }
        is FunctionCall -> {
            val sub = expr.target.targetStatement(ctx.program.namespace)
            val args = expr.args.map { evaluate(it, ctx) as RuntimeValueNumeric }
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
            if(cRange!=null) {
                val dt = expr.inferType(ctx.program)
                if(dt.isKnown)
                    return RuntimeValueRange(dt.typeOrElse(DataType.UBYTE), cRange)
                else
                    throw VmExecutionException("couldn't determine datatype")
            }
            val fromVal = (evaluate(expr.from, ctx) as RuntimeValueNumeric).integerValue()
            val toVal = (evaluate(expr.to, ctx) as RuntimeValueNumeric).integerValue()
            val stepVal = (evaluate(expr.step, ctx) as RuntimeValueNumeric).integerValue()
            val range = makeRange(fromVal, toVal, stepVal)
            val dt = expr.inferType(ctx.program)
            if(dt.isKnown)
                return RuntimeValueRange(dt.typeOrElse(DataType.UBYTE), range)
            else
                throw VmExecutionException("couldn't determine datatype")
        }
        else -> {
            throw VmExecutionException("unimplemented expression node $expr")
        }
    }
}
