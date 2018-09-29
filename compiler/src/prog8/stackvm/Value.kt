package prog8.stackvm

import prog8.ast.DataType
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
            DataType.BYTE -> {
                byteval = (numericvalueOrHeapId.toInt() and 255).toShort()        // byte wrap around 0..255
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.WORD -> {
                wordval = numericvalueOrHeapId.toInt() and 65535      // word wrap around 0..65535
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

    override fun toString(): String {
        return when(type) {
            DataType.BYTE -> "b:%02x".format(byteval)
            DataType.WORD -> "w:%04x".format(wordval)
            DataType.FLOAT -> "f:$floatval"
            else -> "heap:$heapId"
        }
    }

    fun numericValue(): Number {
        return when(type) {
            DataType.BYTE -> byteval!!
            DataType.WORD -> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw VmExecutionException("invalid datatype for numeric value: $type")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            DataType.BYTE -> byteval!!.toInt()
            DataType.WORD -> wordval!!
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
        if(type==other.type) {
            return when (type) {
                DataType.STR, DataType.STR_S, DataType.STR_P, DataType.STR_PS, DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> heapId==other.heapId
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> compareTo(other)==0
            }
        }
        return compareTo(other)==0      // note: datatype doesn't matter
    }

    operator fun compareTo(other: Value): Int {
        return when(type) {
            DataType.BYTE, DataType.WORD, DataType.FLOAT -> {
                when(other.type) {
                    DataType.BYTE, DataType.WORD, DataType.FLOAT -> {
                        numericValue().toDouble().compareTo(other.numericValue().toDouble())
                    }
                    else -> throw VmExecutionException("comparison can only be done between two numeric values")
                }
            }
            else -> throw VmExecutionException("comparison can only be done between two numeric values")
        }
    }

    private fun arithResult(leftDt: DataType, result: Number, rightDt: DataType, op: String): Value {
        if(result.toDouble() < 0 ) {
            return when(leftDt) {
                DataType.BYTE -> {
                    // BYTE can become WORD if right operand is WORD, or when value is too large for byte
                    when(rightDt) {
                        DataType.BYTE -> Value(DataType.BYTE, result.toInt() and 255)
                        DataType.WORD -> Value(DataType.WORD, result.toInt() and 65535)
                        DataType.FLOAT -> throw VmExecutionException("floating point loss of precision")
                        else -> throw VmExecutionException("$op on non-numeric result type")
                    }
                }
                DataType.WORD -> Value(DataType.WORD, result.toInt() and 65535)
                DataType.FLOAT -> Value(DataType.FLOAT, result)
                else -> throw VmExecutionException("$op on non-numeric type")
            }
        }

        return when(leftDt) {
            DataType.BYTE -> {
                // BYTE can become WORD if right operand is WORD, or when value is too large for byte
                if(result.toDouble() >= 256)
                    return Value(DataType.WORD, result)
                when(rightDt) {
                    DataType.BYTE -> Value(DataType.BYTE, result)
                    DataType.WORD -> Value(DataType.WORD, result)
                    DataType.FLOAT -> throw VmExecutionException("floating point loss of precision")
                    else -> throw VmExecutionException("$op on non-numeric result type")
                }
            }
            DataType.WORD -> Value(DataType.WORD, result)
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
            if (type == DataType.BYTE)
                return Value(DataType.BYTE, 255)
            else if(type == DataType.WORD)
                return Value(DataType.WORD, 65535)
        }
        val result = v1.toDouble() / v2.toDouble()
        // NOTE: integer division returns integer result!
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, result)
            DataType.WORD -> Value(DataType.WORD, result)
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
            DataType.BYTE -> Value(DataType.BYTE, result)
            DataType.WORD -> Value(DataType.WORD, result)
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
            DataType.BYTE -> {
                val v = byteval!!.toInt()
                val newCarry = (v and 0x80) != 0
                val newval = (v and 0x7f shl 1) or (if(carry) 1 else 0)
                Pair(Value(DataType.BYTE, newval), newCarry)
            }
            DataType.WORD -> {
                val v = wordval!!
                val newCarry = (v and 0x8000) != 0
                val newval = (v and 0x7fff shl 1) or (if(carry) 1 else 0)
                Pair(Value(DataType.WORD, newval), newCarry)
            }
            else -> throw VmExecutionException("rol can only work on byte/word")
        }
    }

    fun ror(carry: Boolean): Pair<Value, Boolean> {
        // 9 or 17 bit rotate right (with carry)
        return when(type) {
            DataType.BYTE -> {
                val v = byteval!!.toInt()
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x80 else 0)
                Pair(Value(DataType.BYTE, newval), newCarry)
            }
            DataType.WORD -> {
                val v = wordval!!
                val newCarry = v and 1 != 0
                val newval = (v ushr 1) or (if(carry) 0x8000 else 0)
                Pair(Value(DataType.WORD, newval), newCarry)
            }
            else -> throw VmExecutionException("ror2 can only work on byte/word")
        }
    }

    fun rol2(): Value {
        // 8 or 16 bit rotate left
        return when(type) {
            DataType.BYTE -> {
                val v = byteval!!.toInt()
                val carry = (v and 0x80) ushr 7
                val newval = (v and 0x7f shl 1) or carry
                Value(DataType.BYTE, newval)
            }
            DataType.WORD -> {
                val v = wordval!!
                val carry = (v and 0x8000) ushr 15
                val newval = (v and 0x7fff shl 1) or carry
                Value(DataType.WORD, newval)
            }
            else -> throw VmExecutionException("rol2 can only work on byte/word")
        }
    }

    fun ror2(): Value {
        // 8 or 16 bit rotate right
        return when(type) {
            DataType.BYTE -> {
                val v = byteval!!.toInt()
                val carry = v and 1 shl 7
                val newval = (v ushr 1) or carry
                Value(DataType.BYTE, newval)
            }
            DataType.WORD -> {
                val v = wordval!!
                val carry = v and 1 shl 15
                val newval = (v ushr 1) or carry
                Value(DataType.WORD, newval)
            }
            else -> throw VmExecutionException("ror2 can only work on byte/word")
        }
    }

    fun neg(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, -(byteval!!))
            DataType.WORD -> Value(DataType.WORD, -(wordval!!))
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

    fun and(other: Value) = Value(DataType.BYTE, if (this.asBooleanValue && other.asBooleanValue) 1 else 0)
    fun or(other: Value) = Value(DataType.BYTE, if (this.asBooleanValue || other.asBooleanValue) 1 else 0)
    fun xor(other: Value) = Value(DataType.BYTE, if (this.asBooleanValue xor other.asBooleanValue) 1 else 0)
    fun not() = Value(DataType.BYTE, if (this.asBooleanValue) 0 else 1)

    fun inv(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, byteval!!.toInt().inv() and 255)
            DataType.WORD -> Value(DataType.WORD, wordval!!.inv() and 65535)
            else -> throw VmExecutionException("inv can only work on byte/word")
        }
    }

    fun inc(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, (byteval!! + 1) and 255)
            DataType.WORD -> Value(DataType.WORD, (wordval!! + 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! + 1)
            else -> throw VmExecutionException("inc can only work on byte/word/float")
        }
    }

    fun dec(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, (byteval!! - 1) and 255)
            DataType.WORD -> Value(DataType.WORD, (wordval!! - 1) and 65535)
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! - 1)
            else -> throw VmExecutionException("dec can only work on byte/word/float")
        }
    }

    fun lsb(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, byteval!!)
            DataType.WORD -> Value(DataType.BYTE, wordval!! and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }

    fun msb(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, 0)
            DataType.WORD -> Value(DataType.BYTE, wordval!! ushr 8 and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }
}