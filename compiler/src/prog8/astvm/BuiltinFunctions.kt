package prog8.astvm

import prog8.ast.base.DataType
import prog8.compiler.RuntimeValue
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import java.util.*
import kotlin.math.*
import kotlin.random.Random


class BuiltinFunctions {

    private val rnd = Random(0)
    private val statusFlagsSave = Stack<StatusFlags>()


    fun performBuiltinFunction(name: String, args: List<RuntimeValue>, statusflags: StatusFlags): RuntimeValue? {
        return when (name) {
            "rnd" -> RuntimeValue(DataType.UBYTE, rnd.nextInt() and 255)
            "rndw" -> RuntimeValue(DataType.UWORD, rnd.nextInt() and 65535)
            "rndf" -> RuntimeValue(DataType.FLOAT, rnd.nextDouble())
            "lsb" -> RuntimeValue(DataType.UBYTE, args[0].integerValue() and 255)
            "msb" -> RuntimeValue(DataType.UBYTE, (args[0].integerValue() ushr 8) and 255)
            "sin" -> RuntimeValue(DataType.FLOAT, sin(args[0].numericValue().toDouble()))
            "sin8" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (127.0 * sin(rad)).toShort())
            }
            "sin8u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * sin(rad)).toShort())
            }
            "sin16" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (32767.0 * sin(rad)).toShort())
            }
            "sin16u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (32768.0 + 32767.5 * sin(rad)).toShort())
            }
            "cos" -> RuntimeValue(DataType.FLOAT, cos(args[0].numericValue().toDouble()))
            "cos8" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (127.0 * cos(rad)).toShort())
            }
            "cos8u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * cos(rad)).toShort())
            }
            "cos16" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (32767.0 * cos(rad)).toShort())
            }
            "cos16u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (32768.0 + 32767.5 * cos(rad)).toShort())
            }
            "tan" -> RuntimeValue(DataType.FLOAT, tan(args[0].numericValue().toDouble()))
            "atan" -> RuntimeValue(DataType.FLOAT, atan(args[0].numericValue().toDouble()))
            "ln" -> RuntimeValue(DataType.FLOAT, ln(args[0].numericValue().toDouble()))
            "log2" -> RuntimeValue(DataType.FLOAT, log2(args[0].numericValue().toDouble()))
            "sqrt" -> RuntimeValue(DataType.FLOAT, sqrt(args[0].numericValue().toDouble()))
            "sqrt16" -> RuntimeValue(DataType.UBYTE, sqrt(args[0].wordval!!.toDouble()).toInt())
            "rad" -> RuntimeValue(DataType.FLOAT, toRadians(args[0].numericValue().toDouble()))
            "deg" -> RuntimeValue(DataType.FLOAT, toDegrees(args[0].numericValue().toDouble()))
            "round" -> RuntimeValue(DataType.FLOAT, round(args[0].numericValue().toDouble()))
            "floor" -> RuntimeValue(DataType.FLOAT, floor(args[0].numericValue().toDouble()))
            "ceil" -> RuntimeValue(DataType.FLOAT, ceil(args[0].numericValue().toDouble()))
            "rol" -> {
                val (result, newCarry) = args[0].rol(statusflags.carry)
                statusflags.carry = newCarry
                return result
            }
            "rol2" -> args[0].rol2()
            "ror" -> {
                val (result, newCarry) = args[0].ror(statusflags.carry)
                statusflags.carry = newCarry
                return result
            }
            "ror2" -> args[0].ror2()
            "lsl" -> args[0].shl()
            "lsr" -> args[0].shr()
            "abs" -> {
                when (args[0].type) {
                    DataType.UBYTE -> args[0]
                    DataType.BYTE -> RuntimeValue(DataType.UBYTE, abs(args[0].numericValue().toDouble()))
                    DataType.UWORD -> args[0]
                    DataType.WORD -> RuntimeValue(DataType.UWORD, abs(args[0].numericValue().toDouble()))
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, abs(args[0].numericValue().toDouble()))
                    else -> TODO("strange abs type")
                }
            }
            "max" -> {
                val numbers = args.map { it.numericValue().toDouble() }
                RuntimeValue(args[0].type, numbers.max())
            }
            "min" -> {
                val numbers = args.map { it.numericValue().toDouble() }
                RuntimeValue(args[0].type, numbers.min())
            }
            "avg" -> {
                val numbers = args.map { it.numericValue().toDouble() }
                RuntimeValue(DataType.FLOAT, numbers.average())
            }
            "sum" -> {
                val sum = args.map { it.numericValue().toDouble() }.sum()
                when (args[0].type) {
                    DataType.UBYTE -> RuntimeValue(DataType.UWORD, sum)
                    DataType.BYTE -> RuntimeValue(DataType.WORD, sum)
                    DataType.UWORD -> RuntimeValue(DataType.UWORD, sum)
                    DataType.WORD -> RuntimeValue(DataType.WORD, sum)
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, sum)
                    else -> TODO("weird sum type")
                }
            }
            "any" -> {
                val numbers = args.map { it.numericValue().toDouble() }
                RuntimeValue(DataType.UBYTE, if (numbers.any { it != 0.0 }) 1 else 0)
            }
            "all" -> {
                val numbers = args.map { it.numericValue().toDouble() }
                RuntimeValue(DataType.UBYTE, if (numbers.all { it != 0.0 }) 1 else 0)
            }
            "swap" ->
                throw VmExecutionException("swap() cannot be implemented as a function")
            "strlen" -> {
                val zeroIndex = args[0].str!!.indexOf(0.toChar())
                if (zeroIndex >= 0)
                    RuntimeValue(DataType.UBYTE, zeroIndex)
                else
                    RuntimeValue(DataType.UBYTE, args[0].str!!.length)
            }
            "memset" -> {
                val target = args[0].array!!
                val amount = args[1].integerValue()
                val value = args[2].integerValue()
                for (i in 0 until amount) {
                    target[i] = value
                }
                null
            }
            "memsetw" -> {
                val target = args[0].array!!
                val amount = args[1].integerValue()
                val value = args[2].integerValue()
                for (i in 0 until amount step 2) {
                    target[i * 2] = value and 255
                    target[i * 2 + 1] = value ushr 8
                }
                null
            }
            "memcopy" -> {
                val source = args[0].array!!
                val dest = args[1].array!!
                val amount = args[2].integerValue()
                for(i in 0 until amount) {
                    dest[i] = source[i]
                }
                null
            }
            "mkword" -> {
                val result = (args[1].integerValue() shl 8) or args[0].integerValue()
                RuntimeValue(DataType.UWORD, result)
            }
            "set_carry" -> {
                statusflags.carry=true
                null
            }
            "clear_carry" -> {
                statusflags.carry=false
                null
            }
            "set_irqd" -> {
                statusflags.irqd=true
                null
            }
            "clear_irqd" -> {
                statusflags.irqd=false
                null
            }
            "read_flags" -> {
                val carry = if(statusflags.carry) 1 else 0
                val zero = if(statusflags.zero) 2 else 0
                val irqd = if(statusflags.irqd) 4 else 0
                val negative = if(statusflags.negative) 128 else 0
                RuntimeValue(DataType.UBYTE, carry or zero or irqd or negative)
            }
            "rsave" -> {
                statusFlagsSave.push(statusflags)
                null
            }
            "rrestore" -> {
                val flags = statusFlagsSave.pop()
                statusflags.carry = flags.carry
                statusflags.negative = flags.negative
                statusflags.zero = flags.zero
                statusflags.irqd = flags.irqd
                null
            }
            else -> TODO("builtin function $name")
        }
    }
}
