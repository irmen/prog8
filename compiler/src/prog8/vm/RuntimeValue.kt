package prog8.vm

import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.WordDatatypes
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.StringLiteralValue
import prog8.compiler.target.c64.Petscii
import prog8.vm.astvm.VmExecutionException
import java.util.*
import kotlin.math.abs
import kotlin.math.pow


/**
 * Rather than a literal value (NumericLiteralValue) that occurs in the parsed source code,
 * this runtime value can be used to *execute* the parsed Ast (or another intermediary form)
 * It contains a value of a variable during run time of the program and provides arithmetic operations on the value.
 */

abstract class RuntimeValueBase(val type: DataType) {
    abstract fun numericValue(): Number
    abstract fun integerValue(): Int
}


class RuntimeValueNumeric(type: DataType, num: Number): RuntimeValueBase(type) {

    val byteval: Short?
    val wordval: Int?
    val floatval: Double?
    val asBoolean: Boolean

    companion object {
        fun fromLv(literalValue: NumericLiteralValue): RuntimeValueNumeric {
            return RuntimeValueNumeric(literalValue.type, num = literalValue.number)
        }
    }

    init {
        when (type) {
            DataType.UBYTE -> {
                val inum = num.toInt()
                require(inum in 0..255) { "invalid value for ubyte: $inum" }
                byteval = inum.toShort()
                wordval = null
                floatval = null
                asBoolean = byteval != 0.toShort()
            }
            DataType.BYTE -> {
                val inum = num.toInt()
                require(inum in -128..127) { "invalid value for byte: $inum" }
                byteval = inum.toShort()
                wordval = null
                floatval = null
                asBoolean = byteval != 0.toShort()
            }
            DataType.UWORD -> {
                val inum = num.toInt()
                require(inum in 0..65535) { "invalid value for uword: $inum" }
                wordval = inum
                byteval = null
                floatval = null
                asBoolean = wordval != 0
            }
            DataType.WORD -> {
                val inum = num.toInt()
                require(inum in -32768..32767) { "invalid value for word: $inum" }
                wordval = inum
                byteval = null
                floatval = null
                asBoolean = wordval != 0
            }
            DataType.FLOAT -> {
                floatval = num.toDouble()
                byteval = null
                wordval = null
                asBoolean = floatval != 0.0
            }
            else -> throw VmExecutionException("not a numeric value")
        }
    }

    override fun toString(): String {
        return when (type) {
            DataType.UBYTE -> "ub:%02x".format(byteval)
            DataType.BYTE -> {
                if (byteval!! < 0)
                    "b:-%02x".format(abs(byteval.toInt()))
                else
                    "b:%02x".format(byteval)
            }
            DataType.UWORD -> "uw:%04x".format(wordval)
            DataType.WORD -> {
                if (wordval!! < 0)
                    "w:-%04x".format(abs(wordval))
                else
                    "w:%04x".format(wordval)
            }
            DataType.FLOAT -> "f:$floatval"
            else -> "???"
        }
    }

    override fun numericValue(): Number {
        return when (type) {
            in ByteDatatypes -> byteval!!
            in WordDatatypes -> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw ArithmeticException("invalid datatype for numeric value: $type")
        }
    }

    override fun integerValue(): Int {
        return when (type) {
            in ByteDatatypes -> byteval!!.toInt()
            in WordDatatypes -> wordval!!
            DataType.FLOAT -> throw ArithmeticException("float to integer loss of precision")
            else -> throw ArithmeticException("invalid datatype for integer value: $type")
        }
    }

    override fun hashCode(): Int = Objects.hash(byteval, wordval, floatval, type)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RuntimeValueNumeric)
            return false
        return compareTo(other) == 0      // note: datatype doesn't matter
    }

    operator fun compareTo(other: RuntimeValueNumeric): Int = numericValue().toDouble().compareTo(other.numericValue().toDouble())

    private fun arithResult(leftDt: DataType, result: Number, rightDt: DataType, op: String): RuntimeValueNumeric {
        if (leftDt != rightDt)
            throw ArithmeticException("left and right datatypes are not the same")
        if (result.toDouble() < 0) {
            return when (leftDt) {
                DataType.UBYTE, DataType.UWORD -> {
                    // storing a negative number in an unsigned one is done by storing the 2's complement instead
                    val number = abs(result.toDouble().toInt())
                    if (leftDt == DataType.UBYTE)
                        RuntimeValueNumeric(DataType.UBYTE, (number xor 255) + 1)
                    else
                        RuntimeValueNumeric(DataType.UWORD, (number xor 65535) + 1)
                }
                DataType.BYTE -> {
                    val v = result.toInt() and 255
                    if (v < 128)
                        RuntimeValueNumeric(DataType.BYTE, v)
                    else
                        RuntimeValueNumeric(DataType.BYTE, v - 256)
                }
                DataType.WORD -> {
                    val v = result.toInt() and 65535
                    if (v < 32768)
                        RuntimeValueNumeric(DataType.WORD, v)
                    else
                        RuntimeValueNumeric(DataType.WORD, v - 65536)
                }
                DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, result)
                else -> throw ArithmeticException("$op on non-numeric type")
            }
        }

        return when (leftDt) {
            DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, result.toInt() and 255)
            DataType.BYTE -> {
                val v = result.toInt() and 255
                if (v < 128)
                    RuntimeValueNumeric(DataType.BYTE, v)
                else
                    RuntimeValueNumeric(DataType.BYTE, v - 256)
            }
            DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, result.toInt() and 65535)
            DataType.WORD -> {
                val v = result.toInt() and 65535
                if (v < 32768)
                    RuntimeValueNumeric(DataType.WORD, v)
                else
                    RuntimeValueNumeric(DataType.WORD, v - 65536)
            }
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, result)
            else -> throw ArithmeticException("$op on non-numeric type")
        }
    }

    fun add(other: RuntimeValueNumeric): RuntimeValueNumeric {
        if (other.type == DataType.FLOAT && (type != DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() + v2.toDouble()
        return arithResult(type, result, other.type, "add")
    }

    fun sub(other: RuntimeValueNumeric): RuntimeValueNumeric {
        if (other.type == DataType.FLOAT && (type != DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() - v2.toDouble()
        return arithResult(type, result, other.type, "sub")
    }

    fun mul(other: RuntimeValueNumeric): RuntimeValueNumeric {
        if (other.type == DataType.FLOAT && (type != DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() * v2.toDouble()
        return arithResult(type, result, other.type, "mul")
    }

    fun div(other: RuntimeValueNumeric): RuntimeValueNumeric {
        if (other.type == DataType.FLOAT && (type != DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        if (v2.toDouble() == 0.0) {
            when (type) {
                DataType.UBYTE -> return RuntimeValueNumeric(DataType.UBYTE, 255)
                DataType.BYTE -> return RuntimeValueNumeric(DataType.BYTE, 127)
                DataType.UWORD -> return RuntimeValueNumeric(DataType.UWORD, 65535)
                DataType.WORD -> return RuntimeValueNumeric(DataType.WORD, 32767)
                else -> {
                }
            }
        }
        val result = v1.toDouble() / v2.toDouble()
        // NOTE: integer division returns integer result!
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, result)
            DataType.BYTE -> RuntimeValueNumeric(DataType.BYTE, result)
            DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, result)
            DataType.WORD -> RuntimeValueNumeric(DataType.WORD, result)
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, result)
            else -> throw ArithmeticException("div on non-numeric type")
        }
    }

    fun remainder(other: RuntimeValueNumeric): RuntimeValueNumeric {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() % v2.toDouble()
        return arithResult(type, result, other.type, "remainder")
    }

    fun pow(other: RuntimeValueNumeric): RuntimeValueNumeric {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble().pow(v2.toDouble())
        return arithResult(type, result, other.type, "pow")
    }

    fun shl(): RuntimeValueNumeric {
        val v = integerValue()
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(type, (v shl 1) and 255)
            DataType.UWORD -> RuntimeValueNumeric(type, (v shl 1) and 65535)
            DataType.BYTE -> {
                val value = v shl 1
                if (value < 128)
                    RuntimeValueNumeric(type, value)
                else
                    RuntimeValueNumeric(type, value - 256)
            }
            DataType.WORD -> {
                val value = v shl 1
                if (value < 32768)
                    RuntimeValueNumeric(type, value)
                else
                    RuntimeValueNumeric(type, value - 65536)
            }
            else -> throw ArithmeticException("invalid type for shl: $type")
        }
    }

    fun shr(): RuntimeValueNumeric {
        val v = integerValue()
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(type, v ushr 1)
            DataType.BYTE -> RuntimeValueNumeric(type, v shr 1)
            DataType.UWORD -> RuntimeValueNumeric(type, v ushr 1)
            DataType.WORD -> RuntimeValueNumeric(type, v shr 1)
            else -> throw ArithmeticException("invalid type for shr: $type")
        }
    }

    fun rol(carry: Boolean): Pair<RuntimeValueNumeric, Boolean> {
        // 9 or 17 bit rotate left (with carry))
        return when (type) {
            DataType.UBYTE, DataType.BYTE -> {
                val v = byteval!!.toInt()
                val newCarry = (v and 0x80) != 0
                val newval = (v and 0x7f shl 1) or (if (carry) 1 else 0)
                Pair(RuntimeValueNumeric(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD, DataType.WORD -> {
                val v = wordval!!
                val newCarry = (v and 0x8000) != 0
                val newval = (v and 0x7fff shl 1) or (if (carry) 1 else 0)
                Pair(RuntimeValueNumeric(DataType.UWORD, newval), newCarry)
            }
            else -> throw ArithmeticException("rol can only work on byte/word")
        }
    }

    fun ror(carry: Boolean): Pair<RuntimeValueNumeric, Boolean> {
        // 9 or 17 bit rotate right (with carry)
        return when (type) {
            DataType.UBYTE, DataType.BYTE -> {
                val v = byteval!!.toInt()
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if (carry) 0x80 else 0)
                Pair(RuntimeValueNumeric(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD, DataType.WORD -> {
                val v = wordval!!
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if (carry) 0x8000 else 0)
                Pair(RuntimeValueNumeric(DataType.UWORD, newval), newCarry)
            }
            else -> throw ArithmeticException("ror2 can only work on byte/word")
        }
    }

    fun rol2(): RuntimeValueNumeric {
        // 8 or 16 bit rotate left
        return when (type) {
            DataType.UBYTE, DataType.BYTE -> {
                val v = byteval!!.toInt()
                val carry = (v and 0x80) ushr 7
                val newval = (v and 0x7f shl 1) or carry
                RuntimeValueNumeric(DataType.UBYTE, newval)
            }
            DataType.UWORD, DataType.WORD -> {
                val v = wordval!!
                val carry = (v and 0x8000) ushr 15
                val newval = (v and 0x7fff shl 1) or carry
                RuntimeValueNumeric(DataType.UWORD, newval)
            }
            else -> throw ArithmeticException("rol2 can only work on byte/word")
        }
    }

    fun ror2(): RuntimeValueNumeric {
        // 8 or 16 bit rotate right
        return when (type) {
            DataType.UBYTE, DataType.BYTE -> {
                val v = byteval!!.toInt()
                val carry = v and 1 shl 7
                val newval = (v ushr 1) or carry
                RuntimeValueNumeric(DataType.UBYTE, newval)
            }
            DataType.UWORD, DataType.WORD -> {
                val v = wordval!!
                val carry = v and 1 shl 15
                val newval = (v ushr 1) or carry
                RuntimeValueNumeric(DataType.UWORD, newval)
            }
            else -> throw ArithmeticException("ror2 can only work on byte/word")
        }
    }

    fun neg(): RuntimeValueNumeric {
        return when (type) {
            DataType.BYTE -> RuntimeValueNumeric(DataType.BYTE, -(byteval!!))
            DataType.WORD -> RuntimeValueNumeric(DataType.WORD, -(wordval!!))
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, -(floatval)!!)
            else -> throw ArithmeticException("neg can only work on byte/word/float")
        }
    }

    fun abs(): RuntimeValueNumeric {
        return when (type) {
            DataType.BYTE -> RuntimeValueNumeric(DataType.BYTE, abs(byteval!!.toInt()))
            DataType.WORD -> RuntimeValueNumeric(DataType.WORD, abs(wordval!!))
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, abs(floatval!!))
            else -> throw ArithmeticException("abs can only work on byte/word/float")
        }
    }

    fun bitand(other: RuntimeValueNumeric): RuntimeValueNumeric {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 and v2
        return RuntimeValueNumeric(type, result)
    }

    fun bitor(other: RuntimeValueNumeric): RuntimeValueNumeric {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 or v2
        return RuntimeValueNumeric(type, result)
    }

    fun bitxor(other: RuntimeValueNumeric): RuntimeValueNumeric {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 xor v2
        return RuntimeValueNumeric(type, result)
    }

    fun and(other: RuntimeValueNumeric) = RuntimeValueNumeric(DataType.UBYTE, if (this.asBoolean && other.asBoolean) 1 else 0)
    fun or(other: RuntimeValueNumeric) = RuntimeValueNumeric(DataType.UBYTE, if (this.asBoolean || other.asBoolean) 1 else 0)
    fun xor(other: RuntimeValueNumeric) = RuntimeValueNumeric(DataType.UBYTE, if (this.asBoolean xor other.asBoolean) 1 else 0)
    fun not() = RuntimeValueNumeric(DataType.UBYTE, if (this.asBoolean) 0 else 1)

    fun inv(): RuntimeValueNumeric {
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(type, byteval!!.toInt().inv() and 255)
            DataType.UWORD -> RuntimeValueNumeric(type, wordval!!.inv() and 65535)
            DataType.BYTE -> RuntimeValueNumeric(type, byteval!!.toInt().inv())
            DataType.WORD -> RuntimeValueNumeric(type, wordval!!.inv())
            else -> throw ArithmeticException("inv can only work on byte/word")
        }
    }

    fun inc(): RuntimeValueNumeric {
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(type, (byteval!! + 1) and 255)
            DataType.UWORD -> RuntimeValueNumeric(type, (wordval!! + 1) and 65535)
            DataType.BYTE -> {
                val newval = byteval!! + 1
                if (newval == 128)
                    RuntimeValueNumeric(type, -128)
                else
                    RuntimeValueNumeric(type, newval)
            }
            DataType.WORD -> {
                val newval = wordval!! + 1
                if (newval == 32768)
                    RuntimeValueNumeric(type, -32768)
                else
                    RuntimeValueNumeric(type, newval)
            }
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, floatval!! + 1)
            else -> throw ArithmeticException("inc can only work on numeric types")
        }
    }

    fun dec(): RuntimeValueNumeric {
        return when (type) {
            DataType.UBYTE -> RuntimeValueNumeric(type, (byteval!! - 1) and 255)
            DataType.UWORD -> RuntimeValueNumeric(type, (wordval!! - 1) and 65535)
            DataType.BYTE -> {
                val newval = byteval!! - 1
                if (newval == -129)
                    RuntimeValueNumeric(type, 127)
                else
                    RuntimeValueNumeric(type, newval)
            }
            DataType.WORD -> {
                val newval = wordval!! - 1
                if (newval == -32769)
                    RuntimeValueNumeric(type, 32767)
                else
                    RuntimeValueNumeric(type, newval)
            }
            DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, floatval!! - 1)
            else -> throw ArithmeticException("dec can only work on numeric types")
        }
    }

    fun msb(): RuntimeValueNumeric {
        return when (type) {
            in ByteDatatypes -> RuntimeValueNumeric(DataType.UBYTE, 0)
            in WordDatatypes -> RuntimeValueNumeric(DataType.UBYTE, wordval!! ushr 8 and 255)
            else -> throw ArithmeticException("msb can only work on (u)byte/(u)word")
        }
    }

    fun cast(targetType: DataType): RuntimeValueNumeric {
        return when (type) {
            DataType.UBYTE -> {
                when (targetType) {
                    DataType.UBYTE -> this
                    DataType.BYTE -> {
                        val nval = byteval!!.toInt()
                        if (nval < 128)
                            RuntimeValueNumeric(DataType.BYTE, nval)
                        else
                            RuntimeValueNumeric(DataType.BYTE, nval - 256)
                    }
                    DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, numericValue())
                    DataType.WORD -> {
                        val nval = numericValue().toInt()
                        if (nval < 32768)
                            RuntimeValueNumeric(DataType.WORD, nval)
                        else
                            RuntimeValueNumeric(DataType.WORD, nval - 65536)
                    }
                    DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.BYTE -> {
                when (targetType) {
                    DataType.BYTE -> this
                    DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, integerValue() and 255)
                    DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, integerValue() and 65535)
                    DataType.WORD -> RuntimeValueNumeric(DataType.WORD, integerValue())
                    DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.UWORD -> {
                when (targetType) {
                    DataType.BYTE -> {
                        val v = integerValue()
                        if (v < 128)
                            RuntimeValueNumeric(DataType.BYTE, v)
                        else
                            RuntimeValueNumeric(DataType.BYTE, v - 256)
                    }
                    DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, integerValue() and 255)
                    DataType.UWORD -> this
                    DataType.WORD -> {
                        val v = integerValue()
                        if (v < 32768)
                            RuntimeValueNumeric(DataType.WORD, v)
                        else
                            RuntimeValueNumeric(DataType.WORD, v - 65536)
                    }
                    DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.WORD -> {
                when (targetType) {
                    DataType.BYTE -> {
                        val v = integerValue() and 255
                        if (v < 128)
                            RuntimeValueNumeric(DataType.BYTE, v)
                        else
                            RuntimeValueNumeric(DataType.BYTE, v - 256)
                    }
                    DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, integerValue() and 65535)
                    DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, integerValue())
                    DataType.WORD -> this
                    DataType.FLOAT -> RuntimeValueNumeric(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.FLOAT -> {
                when (targetType) {
                    DataType.BYTE -> {
                        val integer = numericValue().toInt()
                        if (integer in -128..127)
                            RuntimeValueNumeric(DataType.BYTE, integer)
                        else
                            throw ArithmeticException("overflow when casting float to byte: $this")
                    }
                    DataType.UBYTE -> RuntimeValueNumeric(DataType.UBYTE, numericValue().toInt())
                    DataType.UWORD -> RuntimeValueNumeric(DataType.UWORD, numericValue().toInt())
                    DataType.WORD -> {
                        val integer = numericValue().toInt()
                        if (integer in -32768..32767)
                            RuntimeValueNumeric(DataType.WORD, integer)
                        else
                            throw ArithmeticException("overflow when casting float to word: $this")
                    }
                    DataType.FLOAT -> this
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            else -> throw ArithmeticException("invalid type cast from $type to $targetType")
        }
    }
}


class RuntimeValueString(type: DataType, val str: String, val heapId: Int?): RuntimeValueBase(type) {
    companion object {
        fun fromLv(string: StringLiteralValue): RuntimeValueString {
            return RuntimeValueString(string.type, string.value, string.heapId!!)
        }
    }

    override fun toString(): String {
        return when (type) {
            DataType.STR -> "str:$str"
            DataType.STR_S -> "str_s:$str"
            else -> "???"
        }
    }

    override fun hashCode(): Int = Objects.hash(type, str)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RuntimeValueString)
            return false
        return type == other.type && str == other.str
    }

    fun iterator(): Iterator<Number> = Petscii.encodePetscii(str, true).iterator()

    override fun numericValue(): Number {
        throw VmExecutionException("string is not a number")
    }

    override fun integerValue(): Int {
        throw VmExecutionException("string is not a number")
    }
}


open class RuntimeValueArray(type: DataType, val array: Array<Number>, val heapId: Int?): RuntimeValueBase(type) {

    companion object {
        fun fromLv(array: ArrayLiteralValue): RuntimeValueArray {
            return if (array.type == DataType.ARRAY_F) {
                val doubleArray = array.value.map { (it as NumericLiteralValue).number }.toTypedArray()
                RuntimeValueArray(array.type, doubleArray, array.heapId!!)
            } else {
                val resultArray = mutableListOf<Number>()
                for (elt in array.value.withIndex()) {
                    if (elt.value is NumericLiteralValue)
                        resultArray.add((elt.value as NumericLiteralValue).number.toInt())
                    else {
                        TODO("ADDRESSOF ${elt.value}")
                    }
                }
                RuntimeValueArray(array.type, resultArray.toTypedArray(), array.heapId!!)
            }
        }
    }

    override fun toString(): String {
        return when (type) {
            DataType.ARRAY_UB -> "array_ub:..."
            DataType.ARRAY_B -> "array_b:..."
            DataType.ARRAY_UW -> "array_uw:..."
            DataType.ARRAY_W -> "array_w:..."
            DataType.ARRAY_F -> "array_f:..."
            else -> "???"
        }
    }

    override fun hashCode(): Int = Objects.hash(type, array)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is RuntimeValueArray)
            return false
        return type == other.type && array.contentEquals(other.array)
    }

    open fun iterator(): Iterator<Number> = array.iterator()

    override fun numericValue(): Number {
        throw VmExecutionException("array is not a number")
    }

    override fun integerValue(): Int {
        throw VmExecutionException("array is not a number")
    }
}


class RuntimeValueRange(type: DataType, val range: IntProgression): RuntimeValueArray(type, range.toList().toTypedArray(), null) {
    override fun iterator(): Iterator<Number> {
        return range.iterator()
    }
}
