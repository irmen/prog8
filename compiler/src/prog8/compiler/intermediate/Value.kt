package prog8.compiler.intermediate

import prog8.ast.*
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.pow


class ValueException(msg: String?) : Exception(msg)


class Value(val type: DataType, numericvalueOrHeapId: Number) {
    private var byteval: Short? = null
    private var wordval: Int? = null
    private var floatval: Double? = null
    var heapId: Int = -1
        private set
    val asBooleanValue: Boolean

    init {
        when(type) {
            DataType.UBYTE -> {
                if(numericvalueOrHeapId.toInt() !in 0..255)
                    throw ValueException("value out of range: $numericvalueOrHeapId")
                byteval = numericvalueOrHeapId.toShort()
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.BYTE -> {
                if(numericvalueOrHeapId.toInt() !in -128..127)
                    throw ValueException("value out of range: $numericvalueOrHeapId")
                byteval = numericvalueOrHeapId.toShort()
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.UWORD -> {
                if(numericvalueOrHeapId.toInt() !in 0..65535)
                    throw ValueException("value out of range: $numericvalueOrHeapId")
                wordval = numericvalueOrHeapId.toInt()
                asBooleanValue = wordval != 0
            }
            DataType.WORD -> {
                if(numericvalueOrHeapId.toInt() !in -32768..32767)
                    throw ValueException("value out of range: $numericvalueOrHeapId")
                wordval = numericvalueOrHeapId.toInt()
                asBooleanValue = wordval != 0
            }
            DataType.FLOAT -> {
                floatval = numericvalueOrHeapId.toDouble()
                asBooleanValue = floatval != 0.0
            }
            else -> {
                if(numericvalueOrHeapId !is Int || numericvalueOrHeapId<0)
                    throw ValueException("for non-numeric types, the value should be a integer heapId >= 0")
                heapId = numericvalueOrHeapId
                asBooleanValue=true
            }
        }
    }

    override fun toString(): String {
        return when(type) {
            DataType.UBYTE -> "ub:%02x".format(byteval)
            DataType.BYTE -> {
                if(byteval!!<0)
                    "b:-%02x".format(abs(byteval!!.toInt()))
                else
                    "b:%02x".format(byteval)
            }
            DataType.UWORD -> "uw:%04x".format(wordval)
            DataType.WORD -> {
                if(wordval!!<0)
                    "w:-%04x".format(abs(wordval!!))
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
            else -> throw ValueException("invalid datatype for numeric value: $type")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            in ByteDatatypes -> byteval!!.toInt()
            in WordDatatypes -> wordval!!
            DataType.FLOAT -> throw ValueException("float to integer loss of precision")
            else -> throw ValueException("invalid datatype for integer value: $type")
        }
    }

    override fun hashCode(): Int {
        val bh = byteval?.hashCode() ?: 0x10001234
        val wh = wordval?.hashCode() ?: 0x01002345
        val fh = floatval?.hashCode() ?: 0x00103456
        return bh xor wh xor fh xor heapId.hashCode() xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is Value)
            return false
        if(type==other.type)
            return if (type in IterableDatatypes) heapId==other.heapId else compareTo(other)==0
        return compareTo(other)==0      // note: datatype doesn't matter
    }

    operator fun compareTo(other: Value): Int {
        return if (type in NumericDatatypes && other.type in NumericDatatypes)
                numericValue().toDouble().compareTo(other.numericValue().toDouble())
            else throw ValueException("comparison can only be done between two numeric values")
    }

    private fun arithResult(leftDt: DataType, result: Number, rightDt: DataType, op: String): Value {
        if(leftDt!=rightDt)
            throw ValueException("left and right datatypes are not the isSameAs")
        if(result.toDouble() < 0 ) {
            return when(leftDt) {
                DataType.UBYTE, DataType.UWORD -> {
                    // storing a negative number in an unsigned one is done by storing the 2's complement instead
                    val number = abs(result.toDouble().toInt())
                    if(leftDt==DataType.UBYTE)
                        Value(DataType.UBYTE, (number xor 255) + 1)
                    else
                        Value(DataType.UBYTE, (number xor 65535) + 1)
                }
                DataType.BYTE -> Value(DataType.BYTE, result.toInt())
                DataType.WORD -> Value(DataType.WORD, result.toInt())
                DataType.FLOAT -> Value(DataType.FLOAT, result)
                else -> throw ValueException("$op on non-numeric type")
            }
        }

        return when(leftDt) {
            DataType.UBYTE -> Value(DataType.UBYTE, result.toInt() and 255)
            DataType.BYTE -> Value(DataType.BYTE, result.toInt())
            DataType.UWORD -> Value(DataType.UWORD, result.toInt() and 65535)
            DataType.WORD -> Value(DataType.WORD, result.toInt())
            DataType.FLOAT -> Value(DataType.FLOAT, result)
            else -> throw ValueException("$op on non-numeric type")
        }
    }

    fun add(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ValueException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() + v2.toDouble()
        return arithResult(type, result, other.type, "add")
    }

    fun sub(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ValueException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() - v2.toDouble()
        return arithResult(type, result, other.type, "sub")
    }

    fun mul(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ValueException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() * v2.toDouble()
        return arithResult(type, result, other.type, "mul")
    }

    fun div(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw ValueException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        if(v2.toDouble()==0.0) {
            when (type) {
                DataType.UBYTE -> return Value(DataType.UBYTE, 255)
                DataType.BYTE -> return Value(DataType.BYTE, 127)
                DataType.UWORD -> return Value(DataType.UWORD, 65535)
                DataType.WORD -> return Value(DataType.WORD, 32767)
                else -> {}
            }
        }
        val result = v1.toDouble() / v2.toDouble()
        // NOTE: integer division returns integer result!
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, result)
            DataType.BYTE -> Value(DataType.BYTE, result)
            DataType.UWORD -> Value(DataType.UWORD, result)
            DataType.WORD -> Value(DataType.WORD, result)
            DataType.FLOAT -> Value(DataType.FLOAT, result)
            else -> throw ValueException("div on non-numeric type")
        }
    }

    fun remainder(other: Value): Value? {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() % v2.toDouble()
        return arithResult(type, result, other.type, "remainder")
    }

    fun pow(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble().pow(v2.toDouble())
        return arithResult(type, result, other.type,"pow")
    }

    fun shl(): Value {
        val v = integerValue()
        return when (type) {
            DataType.UBYTE -> return Value(type, (v shl 1) and 255)
            DataType.BYTE -> {
                if(v<0)
                    Value(type, -((-v shl 1) and 255))
                else
                    Value(type, (v shl 1) and 255)
            }
            DataType.UWORD -> return Value(type, (v shl 1) and 65535)
            DataType.WORD -> {
                if(v<0)
                    Value(type, -((-v shl 1) and 65535))
                else
                    Value(type, (v shl 1) and 65535)
            }
            else -> throw ValueException("invalid type for shl: $type")
        }
    }

    fun shr(): Value {
        val v = integerValue()
        return when(type){
            DataType.UBYTE -> Value(type, (v ushr 1) and 255)
            DataType.BYTE -> Value(type, v shr 1)
            DataType.UWORD -> Value(type, (v ushr 1) and 65535)
            DataType.WORD -> Value(type, v shr 1)
            else -> throw ValueException("invalid type for shr: $type")
        }
    }

    fun rol(carry: Boolean): Pair<Value, Boolean> {
        // 9 or 17 bit rotate left (with carry))
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val newCarry = (v and 0x80) != 0
                val newval = (v and 0x7f shl 1) or (if(carry) 1 else 0)
                Pair(Value(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val newCarry = (v and 0x8000) != 0
                val newval = (v and 0x7fff shl 1) or (if(carry) 1 else 0)
                Pair(Value(DataType.UWORD, newval), newCarry)
            }
            else -> throw ValueException("rol can only work on byte/word")
        }
    }

    fun ror(carry: Boolean): Pair<Value, Boolean> {
        // 9 or 17 bit rotate right (with carry)
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x80 else 0)
                Pair(Value(DataType.UBYTE, newval), newCarry)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x8000 else 0)
                Pair(Value(DataType.UWORD, newval), newCarry)
            }
            else -> throw ValueException("ror2 can only work on byte/word")
        }
    }

    fun rol2(): Value {
        // 8 or 16 bit rotate left
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val carry = (v and 0x80) ushr 7
                val newval = (v and 0x7f shl 1) or carry
                Value(DataType.UBYTE, newval)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val carry = (v and 0x8000) ushr 15
                val newval = (v and 0x7fff shl 1) or carry
                Value(DataType.UWORD, newval)
            }
            else -> throw ValueException("rol2 can only work on byte/word")
        }
    }

    fun ror2(): Value {
        // 8 or 16 bit rotate right
        return when(type) {
            DataType.UBYTE -> {
                val v = byteval!!.toInt()
                val carry = v and 1 shl 7
                val newval = (v ushr 1) or carry
                Value(DataType.UBYTE, newval)
            }
            DataType.UWORD -> {
                val v = wordval!!
                val carry = v and 1 shl 15
                val newval = (v ushr 1) or carry
                Value(DataType.UWORD, newval)
            }
            else -> throw ValueException("ror2 can only work on byte/word")
        }
    }

    fun neg(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, -(byteval!!))
            DataType.WORD -> Value(DataType.WORD, -(wordval!!))
            DataType.FLOAT -> Value(DataType.FLOAT, -(floatval)!!)
            else -> throw ValueException("neg can only work on byte/word/float")
        }
    }

    fun abs(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, abs(byteval!!.toInt()))
            DataType.WORD -> Value(DataType.WORD, abs(wordval!!))
            DataType.FLOAT -> Value(DataType.FLOAT, abs(floatval!!))
            else -> throw ValueException("abs can only work on byte/word/float")
        }
    }

    fun bitand(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 and v2
        return Value(type, result)
    }

    fun bitor(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 or v2
        return Value(type, result)
    }

    fun bitxor(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1 xor v2
        return Value(type, result)
    }

    fun and(other: Value) = Value(DataType.UBYTE, if (this.asBooleanValue && other.asBooleanValue) 1 else 0)
    fun or(other: Value) = Value(DataType.UBYTE, if (this.asBooleanValue || other.asBooleanValue) 1 else 0)
    fun xor(other: Value) = Value(DataType.UBYTE, if (this.asBooleanValue xor other.asBooleanValue) 1 else 0)
    fun not() = Value(DataType.UBYTE, if (this.asBooleanValue) 0 else 1)

    fun inv(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, byteval!!.toInt().inv() and 255)
            DataType.UWORD -> Value(DataType.UWORD, wordval!!.inv() and 65535)
            else -> throw ValueException("inv can only work on byte/word")
        }
    }

    fun inc(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, (byteval!! + 1) and 255)
            DataType.UWORD -> Value(DataType.UWORD, (wordval!! + 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! + 1)
            else -> throw ValueException("inc can only work on byte/word/float")
        }
    }

    fun dec(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, (byteval!! - 1) and 255)
            DataType.UWORD -> Value(DataType.UWORD, (wordval!! - 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! - 1)
            else -> throw ValueException("dec can only work on byte/word/float")
        }
    }

    fun msb(): Value {
        return when(type) {
            in ByteDatatypes -> Value(DataType.UBYTE, 0)
            in WordDatatypes -> Value(DataType.UBYTE, wordval!! ushr 8 and 255)
            else -> throw ValueException("msb can only work on (u)byte/(u)word")
        }
    }

    fun cast(targetType: DataType): Value {
        return when (type) {
            DataType.UBYTE -> {
                when (targetType) {
                    DataType.UBYTE -> this
                    DataType.BYTE -> {
                        if(byteval!!<=127)
                            Value(DataType.BYTE, byteval!!)
                        else
                            Value(DataType.BYTE, -(256-byteval!!))
                    }
                    DataType.UWORD -> Value(DataType.UWORD, numericValue())
                    DataType.WORD -> Value(DataType.WORD, numericValue())
                    DataType.FLOAT -> Value(DataType.FLOAT, numericValue())
                    else -> throw ValueException("invalid type cast from $type to $targetType")
                }
            }
            DataType.BYTE -> {
                when (targetType) {
                    DataType.BYTE -> this
                    DataType.UBYTE -> Value(DataType.UBYTE, integerValue() and 255)
                    DataType.UWORD -> Value(DataType.UWORD, integerValue() and 65535)
                    DataType.WORD -> Value(DataType.WORD, integerValue())
                    DataType.FLOAT -> Value(DataType.FLOAT, numericValue())
                    else -> throw ValueException("invalid type cast from $type to $targetType")
                }
            }
            DataType.UWORD -> {
                when (targetType) {
                    in ByteDatatypes -> Value(DataType.UBYTE, integerValue() and 255)
                    DataType.UWORD -> this
                    DataType.WORD -> {
                        if(integerValue()<=32767)
                            Value(DataType.WORD, integerValue())
                        else
                            Value(DataType.WORD, -(65536-integerValue()))
                    }
                    DataType.FLOAT -> Value(DataType.FLOAT, numericValue())
                    else -> throw ValueException("invalid type cast from $type to $targetType")
                }
            }
            DataType.WORD -> {
                when (targetType) {
                    in ByteDatatypes -> Value(DataType.UBYTE, integerValue() and 255)
                    DataType.UWORD -> Value(DataType.UWORD, integerValue() and 65535)
                    DataType.WORD -> this
                    DataType.FLOAT -> Value(DataType.FLOAT, numericValue())
                    else -> throw ValueException("invalid type cast from $type to $targetType")
                }
            }
            DataType.FLOAT -> {
                when (targetType) {
                    DataType.BYTE -> {
                        val integer=numericValue().toInt()
                        if(integer in -128..127)
                            Value(DataType.BYTE, integer)
                        else
                            throw ValueException("overflow when casting float to byte: $this")
                    }
                    DataType.UBYTE -> Value(DataType.UBYTE, numericValue().toInt() and 255)
                    DataType.UWORD -> Value(DataType.UWORD, numericValue().toInt() and 65535)
                    DataType.WORD -> {
                        val integer=numericValue().toInt()
                        if(integer in -32768..32767)
                            Value(DataType.WORD, integer)
                        else
                            throw ValueException("overflow when casting float to word: $this")
                    }
                    DataType.FLOAT -> this
                    else -> throw ValueException("invalid type cast from $type to $targetType")
                }
            }
            else -> throw ValueException("invalid type cast from $type to $targetType")
        }
    }

}
