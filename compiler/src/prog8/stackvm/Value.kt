package prog8.stackvm

import prog8.ast.DataType
import prog8.ast.IterableDatatypes
import prog8.ast.NumericDatatypes
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class Value(val type: DataType, numericvalueOrHeapId: Number) {
    private var byteval: Short? = null
    private var wordval: Int? = null
    private var floatval: Double? = null
    var heapId: Int = 0
        private set
    val asBooleanValue: Boolean

    init {
        when(type) {
            DataType.UBYTE -> {
                byteval = (numericvalueOrHeapId.toInt() and 255).toShort()        // ubyte wrap around 0..255
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.BYTE -> {
                byteval = limitByte(numericvalueOrHeapId.toInt())
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.UWORD -> {
                wordval = numericvalueOrHeapId.toInt() and 65535      // uword wrap around 0..65535
                asBooleanValue = wordval != 0
            }
            DataType.WORD -> {
                wordval = limitWord(numericvalueOrHeapId.toInt())
                asBooleanValue = wordval != 0
            }
            DataType.FLOAT -> {
                floatval = numericvalueOrHeapId.toDouble()
                asBooleanValue = floatval != 0.0
            }
            else -> {
                if(numericvalueOrHeapId !is Int)
                    throw VmExecutionException("for non-numeric types, the value should be an integer heapId")
                heapId = numericvalueOrHeapId
                asBooleanValue=true
            }
        }
    }

    companion object {
        fun limitByte(value: Int): Short {
            var bval: Int
            if(value < 0) {
                bval = -(abs(value) and 127)
                if(bval==0) bval=-128
            }
            else
                bval = value and 127
            return bval.toShort()
        }
        fun limitWord(value: Int): Int {
            var bval: Int
            if(value < 0) {
                bval = -(abs(value) and 32767)
                if(bval==0) bval=-32768
            }
            else
                bval = value and 32767
            return bval
        }
    }

    override fun toString(): String {
        return when(type) {
            DataType.UBYTE -> "ub:%02x".format(byteval)
            DataType.BYTE -> "b:%02x".format(byteval)
            DataType.UWORD -> "uw:%04x".format(wordval)
            DataType.WORD -> "w:%04x".format(wordval)
            DataType.FLOAT -> "f:$floatval"
            else -> "heap:$heapId"
        }
    }

    fun numericValue(): Number {
        return when(type) {
            DataType.UBYTE, DataType.BYTE -> byteval!!
            DataType.UWORD, DataType.WORD -> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw VmExecutionException("invalid datatype for numeric value: $type")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            DataType.UBYTE, DataType.BYTE -> byteval!!.toInt()
            DataType.UWORD, DataType.WORD -> wordval!!
            DataType.FLOAT -> throw VmExecutionException("float to integer loss of precision")
            else -> throw VmExecutionException("invalid datatype for integer value: $type")
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
            else throw VmExecutionException("comparison can only be done between two numeric values")
    }

    private fun arithResult(leftDt: DataType, result: Number, rightDt: DataType, op: String): Value {
        if(leftDt!=rightDt)
            throw VmExecutionException("left and right datatypes are not the same")
        if(result.toDouble() < 0 ) {
            return when(leftDt) {
                DataType.UBYTE, DataType.UWORD -> throw VmExecutionException("arithmetic error: cannot store a negative value in a $leftDt")
                DataType.BYTE -> Value(DataType.BYTE, limitByte(result.toInt()))
                DataType.WORD -> Value(DataType.WORD, limitWord(result.toInt()))
                DataType.FLOAT -> Value(DataType.FLOAT, result)
                else -> throw VmExecutionException("$op on non-numeric type")
            }
        }

        return when(leftDt) {
            DataType.UBYTE -> Value(DataType.UBYTE, result.toInt() and 255)
            DataType.BYTE -> Value(DataType.BYTE, limitByte(result.toInt()))
            DataType.UWORD -> Value(DataType.UWORD, result.toInt() and 65535)
            DataType.WORD -> Value(DataType.WORD, limitWord(result.toInt()))
            DataType.FLOAT -> Value(DataType.FLOAT, result)
            else -> throw VmExecutionException("$op on non-numeric type")
        }
    }

    fun add(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw VmExecutionException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() + v2.toDouble()
        return arithResult(type, result, other.type, "add")
    }

    fun sub(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw VmExecutionException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() - v2.toDouble()
        return arithResult(type, result, other.type, "sub")
    }

    fun mul(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw VmExecutionException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() * v2.toDouble()
        return arithResult(type, result, other.type, "mul")
    }

    fun div(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw VmExecutionException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        if(v2.toDouble()==0.0) {
            if (type == DataType.UBYTE)
                return Value(DataType.UBYTE, 255)
            else if(type == DataType.UWORD)
                return Value(DataType.UWORD, 65535)
        }
        val result = v1.toDouble() / v2.toDouble()
        // NOTE: integer division returns integer result!
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, result)
            DataType.UWORD -> Value(DataType.UWORD, result)
            DataType.FLOAT -> Value(DataType.FLOAT, result)
            else -> throw VmExecutionException("div on non-numeric type")
        }
    }

    fun floordiv(other: Value): Value {
        if(other.type == DataType.FLOAT && (type!= DataType.FLOAT))
            throw VmExecutionException("floating point loss of precision on type $type")
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = floor(v1.toDouble() / v2.toDouble())
        // NOTE: integer division returns integer result!
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, result)
            DataType.UWORD -> Value(DataType.UWORD, result)
            DataType.FLOAT -> Value(DataType.FLOAT, result)
            else -> throw VmExecutionException("div on non-numeric type")
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
        return Value(type, v shl 1)
    }

    fun shr(): Value {
        val v = integerValue()
        return Value(type, v ushr 1)
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
            else -> throw VmExecutionException("rol can only work on byte/word")
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
            else -> throw VmExecutionException("ror2 can only work on byte/word")
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
            else -> throw VmExecutionException("rol2 can only work on byte/word")
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
            else -> throw VmExecutionException("ror2 can only work on byte/word")
        }
    }

    fun neg(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, -(byteval!!))
            DataType.UWORD -> Value(DataType.UWORD, -(wordval!!))
            DataType.FLOAT -> Value(DataType.FLOAT, -(floatval)!!)
            else -> throw VmExecutionException("neg can only work on byte/word/float")
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
            else -> throw VmExecutionException("inv can only work on byte/word")
        }
    }

    fun inc(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, (byteval!! + 1) and 255)
            DataType.UWORD -> Value(DataType.UWORD, (wordval!! + 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! + 1)
            else -> throw VmExecutionException("inc can only work on byte/word/float")
        }
    }

    fun dec(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, (byteval!! - 1) and 255)
            DataType.UWORD -> Value(DataType.UWORD, (wordval!! - 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! - 1)
            else -> throw VmExecutionException("dec can only work on byte/word/float")
        }
    }

    fun lsb(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, byteval!!)
            DataType.UWORD -> Value(DataType.UBYTE, wordval!! and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }

    fun msb(): Value {
        return when(type) {
            DataType.UBYTE -> Value(DataType.UBYTE, 0)
            DataType.UWORD -> Value(DataType.UBYTE, wordval!! ushr 8 and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }
}