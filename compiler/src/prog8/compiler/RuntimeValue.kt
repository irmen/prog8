package prog8.compiler

import prog8.ast.*
import prog8.compiler.target.c64.Petscii
import kotlin.math.abs
import kotlin.math.pow


/**
 * Rather than a literal value (LiteralValue) that occurs in the parsed source code,
 * this runtime value can be used to *execute* the parsed Ast (or another intermediary form)
 * It contains a value of a variable during run time of the program and provides arithmetic operations on the value.
 */
open class RuntimeValue(val type: DataType, num: Number?=null, val str: String?=null, val array: Array<Number>?=null, val heapId: Int?=null) {

    val byteval: Short?
    val wordval: Int?
    val floatval: Double?
    val asBoolean: Boolean

    companion object {
        fun from(literalValue: LiteralValue, heap: HeapValues): RuntimeValue {
            return when(literalValue.type) {
                in NumericDatatypes -> RuntimeValue(literalValue.type, num = literalValue.asNumericValue!!)
                in StringDatatypes -> from(literalValue.heapId!!, heap)
                in ArrayDatatypes -> from(literalValue.heapId!!, heap)
                else -> TODO("type")
            }
        }

        fun from(heapId: Int, heap: HeapValues): RuntimeValue {
            val value = heap.get(heapId)
            return when {
                value.type in StringDatatypes ->
                    RuntimeValue(value.type, str = value.str!!, heapId = heapId)
                value.type in ArrayDatatypes ->
                    if (value.type == DataType.ARRAY_F) {
                        RuntimeValue(value.type, array = value.doubleArray!!.toList().toTypedArray(), heapId = heapId)
                    } else {
                        val array = value.array!!
                        if (array.any { it.addressOf != null })
                            TODO("addressof values")
                        RuntimeValue(value.type, array = array.map { it.integer!! }.toTypedArray(), heapId = heapId)
                    }
                else -> TODO("weird type on heap")
            }
        }

    }

    init {
        when(type) {
            DataType.UBYTE -> {
                byteval = (num!!.toInt() and 255).toShort()
                wordval = null
                floatval = null
                asBoolean = byteval != 0.toShort()
            }
            DataType.BYTE -> {
                val v = num!!.toInt() and 255
                byteval = (if(v<128) v else v-256).toShort()
                wordval = null
                floatval = null
                asBoolean = byteval != 0.toShort()
            }
            DataType.UWORD -> {
                wordval = num!!.toInt() and 65535
                byteval = null
                floatval = null
                asBoolean = wordval != 0
            }
            DataType.WORD -> {
                val v = num!!.toInt() and 65535
                wordval = if(v<32768) v else v - 65536
                byteval = null
                floatval = null
                asBoolean = wordval != 0
            }
            DataType.FLOAT -> {
                floatval = num!!.toDouble()
                byteval = null
                wordval = null
                asBoolean = floatval != 0.0
            }
            else -> {
                if(heapId==null)
                    throw IllegalArgumentException("for non-numeric types, a heapId should be given")
                byteval = null
                wordval = null
                floatval = null
                asBoolean = true
            }
        }
    }

    fun asLiteralValue(): LiteralValue {
        return when(type) {
            in ByteDatatypes -> LiteralValue(type, byteval, position = Position("", 0, 0, 0))
            in WordDatatypes -> LiteralValue(type, wordvalue = wordval, position = Position("", 0, 0, 0))
            DataType.FLOAT -> LiteralValue(type, floatvalue = floatval, position = Position("", 0, 0, 0))
            in StringDatatypes -> LiteralValue(type, strvalue = str, position = Position("", 0, 0, 0))
            in ArrayDatatypes -> LiteralValue(type,
                    arrayvalue = array?.map { LiteralValue.optimalNumeric(it, Position("", 0, 0, 0)) }?.toTypedArray(),
                    position = Position("", 0, 0, 0))
            else -> TODO("strange type")
        }
    }

    override fun toString(): String {
        return when(type) {
            DataType.UBYTE -> "ub:%02x".format(byteval)
            DataType.BYTE -> {
                if(byteval!!<0)
                    "b:-%02x".format(abs(byteval.toInt()))
                else
                    "b:%02x".format(byteval)
            }
            DataType.UWORD -> "uw:%04x".format(wordval)
            DataType.WORD -> {
                if(wordval!!<0)
                    "w:-%04x".format(abs(wordval))
                else
                    "w:%04x".format(wordval)
            }
            DataType.FLOAT -> "f:$floatval"
            else -> "heap:$heapId"
        }
    }

    fun numericValue(): Number {
        return when(type) {
            in ByteDatatypes -> byteval!!
            in WordDatatypes -> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw ArithmeticException("invalid datatype for numeric value: $type")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            in ByteDatatypes -> byteval!!.toInt()
            in WordDatatypes -> wordval!!
            DataType.FLOAT -> throw ArithmeticException("float to integer loss of precision")
            else -> throw ArithmeticException("invalid datatype for integer value: $type")
        }
    }

    override fun hashCode(): Int {
        val bh = byteval?.hashCode() ?: 0x10001234
        val wh = wordval?.hashCode() ?: 0x01002345
        val fh = floatval?.hashCode() ?: 0x00103456
        return bh xor wh xor fh xor heapId.hashCode() xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is RuntimeValue)
            return false
        if(type==other.type)
            return if (type in IterableDatatypes) heapId==other.heapId else compareTo(other)==0
        return compareTo(other)==0      // note: datatype doesn't matter
    }

    operator fun compareTo(other: RuntimeValue): Int {
        return if (type in NumericDatatypes && other.type in NumericDatatypes)
            numericValue().toDouble().compareTo(other.numericValue().toDouble())
        else throw ArithmeticException("comparison can only be done between two numeric values")
    }

    private fun arithResult(leftDt: DataType, result: Number, rightDt: DataType, op: String): RuntimeValue {
        if(leftDt!=rightDt)
            throw ArithmeticException("left and right datatypes are not the same")
        if(result.toDouble() < 0 ) {
            return when(leftDt) {
                DataType.UBYTE, DataType.UWORD -> {
                    // storing a negative number in an unsigned one is done by storing the 2's complement instead
                    val number = abs(result.toDouble().toInt())
                    if(leftDt==DataType.UBYTE)
                        RuntimeValue(DataType.UBYTE, (number xor 255) + 1)
                    else
                        RuntimeValue(DataType.UBYTE, (number xor 65535) + 1)
                }
                DataType.BYTE -> RuntimeValue(DataType.BYTE, result.toInt())
                DataType.WORD -> RuntimeValue(DataType.WORD, result.toInt())
                DataType.FLOAT -> RuntimeValue(DataType.FLOAT, result)
                else -> throw ArithmeticException("$op on non-numeric type")
            }
        }

        return when(leftDt) {
            DataType.UBYTE -> RuntimeValue(DataType.UBYTE, result.toInt())
            DataType.BYTE -> RuntimeValue(DataType.BYTE, result.toInt())
            DataType.UWORD -> RuntimeValue(DataType.UWORD, result.toInt())
            DataType.WORD -> RuntimeValue(DataType.WORD, result.toInt())
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, result)
            else -> throw ArithmeticException("$op on non-numeric type")
        }
    }

    fun add(other: RuntimeValue): RuntimeValue {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() + v2.toDouble()
        return arithResult(type, result, other.type, "add")
    }

    fun sub(other: RuntimeValue): RuntimeValue {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() - v2.toDouble()
        return arithResult(type, result, other.type, "sub")
    }

    fun mul(other: RuntimeValue): RuntimeValue {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() * v2.toDouble()
        return arithResult(type, result, other.type, "mul")
    }

    fun div(other: RuntimeValue): RuntimeValue {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ArithmeticException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        if(v2.toDouble()==0.0) {
            when (type) {
                DataType.UBYTE -> return RuntimeValue(DataType.UBYTE, 255)
                DataType.BYTE -> return RuntimeValue(DataType.BYTE, 127)
                DataType.UWORD -> return RuntimeValue(DataType.UWORD, 65535)
                DataType.WORD -> return RuntimeValue(DataType.WORD, 32767)
                else -> {}
            }
        }
        val result = v1.toDouble() / v2.toDouble()
        // NOTE: integer division returns integer result!
        return when(type) {
            DataType.UBYTE -> RuntimeValue(DataType.UBYTE, result)
            DataType.BYTE -> RuntimeValue(DataType.BYTE, result)
            DataType.UWORD -> RuntimeValue(DataType.UWORD, result)
            DataType.WORD -> RuntimeValue(DataType.WORD, result)
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, result)
            else -> throw ArithmeticException("div on non-numeric type")
        }
    }

    fun remainder(other: RuntimeValue): RuntimeValue {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() % v2.toDouble()
        return arithResult(type, result, other.type, "remainder")
    }

    fun pow(other: RuntimeValue): RuntimeValue {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble().pow(v2.toDouble())
        return arithResult(type, result, other.type,"pow")
    }

    fun shl(): RuntimeValue {
        val v = integerValue()
        return when (type) {
            DataType.UBYTE,
            DataType.BYTE,
            DataType.UWORD,
            DataType.WORD -> RuntimeValue(type, v shl 1)
            else -> throw ArithmeticException("invalid type for shl: $type")
        }
    }

    fun shr(): RuntimeValue {
        val v = integerValue()
        return when(type){
            DataType.UBYTE -> RuntimeValue(type, v ushr 1)
            DataType.BYTE -> RuntimeValue(type, v shr 1)
            DataType.UWORD -> RuntimeValue(type, v ushr 1)
            DataType.WORD -> RuntimeValue(type, v shr 1)
            else -> throw ArithmeticException("invalid type for shr: $type")
        }
    }

    fun rol(carry: Boolean): Pair<RuntimeValue, Boolean> {
        // 9 or 17 bit rotate left (with carry))
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val newCarry = (v and 0x80) != 0
                val newval = (v and 0x7f shl 1) or (if(carry) 1 else 0)
                Pair(RuntimeValue(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val newCarry = (v and 0x8000) != 0
                val newval = (v and 0x7fff shl 1) or (if(carry) 1 else 0)
                Pair(RuntimeValue(DataType.UWORD, newval), newCarry)
            }
            else -> throw ArithmeticException("rol can only work on byte/word")
        }
    }

    fun ror(carry: Boolean): Pair<RuntimeValue, Boolean> {
        // 9 or 17 bit rotate right (with carry)
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x80 else 0)
                Pair(RuntimeValue(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x8000 else 0)
                Pair(RuntimeValue(DataType.UWORD, newval), newCarry)
            }
            else -> throw ArithmeticException("ror2 can only work on byte/word")
        }
    }

    fun rol2(): RuntimeValue {
        // 8 or 16 bit rotate left
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val carry = (v and 0x80) ushr 7
                val newval = (v and 0x7f shl 1) or carry
                RuntimeValue(DataType.UBYTE, newval)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val carry = (v and 0x8000) ushr 15
                val newval = (v and 0x7fff shl 1) or carry
                RuntimeValue(DataType.UWORD, newval)
            }
            else -> throw ArithmeticException("rol2 can only work on byte/word")
        }
    }

    fun ror2(): RuntimeValue {
        // 8 or 16 bit rotate right
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val carry = v and 1 shl 7
                val newval = (v ushr 1) or carry
                RuntimeValue(DataType.UBYTE, newval)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val carry = v and 1 shl 15
                val newval = (v ushr 1) or carry
                RuntimeValue(DataType.UWORD, newval)
            }
            else -> throw ArithmeticException("ror2 can only work on byte/word")
        }
    }

    fun neg(): RuntimeValue {
        return when(type) {
            DataType.BYTE -> RuntimeValue(DataType.BYTE, -(byteval!!))
            DataType.WORD -> RuntimeValue(DataType.WORD, -(wordval!!))
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, -(floatval)!!)
            else -> throw ArithmeticException("neg can only work on byte/word/float")
        }
    }

    fun abs(): RuntimeValue {
        return when(type) {
            DataType.BYTE -> RuntimeValue(DataType.BYTE, abs(byteval!!.toInt()))
            DataType.WORD -> RuntimeValue(DataType.WORD, abs(wordval!!))
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, abs(floatval!!))
            else -> throw ArithmeticException("abs can only work on byte/word/float")
        }
    }

    fun bitand(other: RuntimeValue): RuntimeValue {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 and v2
        return RuntimeValue(type, result)
    }

    fun bitor(other: RuntimeValue): RuntimeValue {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 or v2
        return RuntimeValue(type, result)
    }

    fun bitxor(other: RuntimeValue): RuntimeValue {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 xor v2
        return RuntimeValue(type, result)
    }

    fun and(other: RuntimeValue) = RuntimeValue(DataType.UBYTE, if (this.asBoolean && other.asBoolean) 1 else 0)
    fun or(other: RuntimeValue) = RuntimeValue(DataType.UBYTE, if (this.asBoolean || other.asBoolean) 1 else 0)
    fun xor(other: RuntimeValue) = RuntimeValue(DataType.UBYTE, if (this.asBoolean xor other.asBoolean) 1 else 0)
    fun not() = RuntimeValue(DataType.UBYTE, if (this.asBoolean) 0 else 1)

    fun inv(): RuntimeValue {
        return when(type) {
            in ByteDatatypes -> RuntimeValue(type, byteval!!.toInt().inv())
            in WordDatatypes -> RuntimeValue(type, wordval!!.inv())
            else -> throw ArithmeticException("inv can only work on byte/word")
        }
    }

    fun inc(): RuntimeValue {
        return when(type) {
            in ByteDatatypes -> RuntimeValue(type, byteval!! + 1)
            in WordDatatypes -> RuntimeValue(type, wordval!! + 1)
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, floatval!! + 1)
            else -> throw ArithmeticException("inc can only work on numeric types")
        }
    }

    fun dec(): RuntimeValue {
        return when(type) {
            in ByteDatatypes -> RuntimeValue(type, byteval!! - 1)
            in WordDatatypes -> RuntimeValue(type, wordval!! - 1)
            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, floatval!! - 1)
            else -> throw ArithmeticException("dec can only work on numeric types")
        }
    }

    fun msb(): RuntimeValue {
        return when(type) {
            in ByteDatatypes -> RuntimeValue(DataType.UBYTE, 0)
            in WordDatatypes -> RuntimeValue(DataType.UBYTE, wordval!! ushr 8 and 255)
            else -> throw ArithmeticException("msb can only work on (u)byte/(u)word")
        }
    }

    fun cast(targetType: DataType): RuntimeValue {
        return when (type) {
            DataType.UBYTE -> {
                when (targetType) {
                    DataType.UBYTE -> this
                    DataType.BYTE -> RuntimeValue(DataType.BYTE, byteval)
                    DataType.UWORD -> RuntimeValue(DataType.UWORD, numericValue())
                    DataType.WORD -> RuntimeValue(DataType.WORD, numericValue())
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.BYTE -> {
                when (targetType) {
                    DataType.BYTE -> this
                    DataType.UBYTE -> RuntimeValue(DataType.UBYTE, integerValue())
                    DataType.UWORD -> RuntimeValue(DataType.UWORD, integerValue())
                    DataType.WORD -> RuntimeValue(DataType.WORD, integerValue())
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.UWORD -> {
                when (targetType) {
                    DataType.BYTE -> RuntimeValue(DataType.BYTE, integerValue())
                    DataType.UBYTE -> RuntimeValue(DataType.UBYTE, integerValue())
                    DataType.UWORD -> this
                    DataType.WORD -> RuntimeValue(DataType.WORD, integerValue())
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.WORD -> {
                when (targetType) {
                    DataType.BYTE -> RuntimeValue(DataType.BYTE, integerValue())
                    DataType.UBYTE -> RuntimeValue(DataType.UBYTE, integerValue())
                    DataType.UWORD -> RuntimeValue(DataType.UWORD, integerValue())
                    DataType.WORD -> this
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, numericValue())
                    else -> throw ArithmeticException("invalid type cast from $type to $targetType")
                }
            }
            DataType.FLOAT -> {
                when (targetType) {
                    DataType.BYTE -> {
                        val integer=numericValue().toInt()
                        if(integer in -128..127)
                            RuntimeValue(DataType.BYTE, integer)
                        else
                            throw ArithmeticException("overflow when casting float to byte: $this")
                    }
                    DataType.UBYTE -> RuntimeValue(DataType.UBYTE, numericValue().toInt())
                    DataType.UWORD -> RuntimeValue(DataType.UWORD, numericValue().toInt())
                    DataType.WORD -> {
                        val integer=numericValue().toInt()
                        if(integer in -32768..32767)
                            RuntimeValue(DataType.WORD, integer)
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

    open fun iterator(): Iterator<Number> {
        return when (type) {
            in StringDatatypes -> {
                Petscii.encodePetscii(str!!, true).iterator()
            }
            in ArrayDatatypes -> {
                array!!.iterator()
            }
            else -> throw IllegalArgumentException("cannot iterate over $this")
        }
    }
}


class RuntimeValueRange(type: DataType, val range: IntProgression): RuntimeValue(type, 0) {
    override fun iterator(): Iterator<Number> {
        return range.iterator()
    }
}
