package prog8.astvm

import prog8.ast.DataType
import prog8.compiler.RuntimeValue
import kotlin.random.Random


class BuiltinFunctions {

    private val rnd = Random(0)

    fun performBuiltinFunction(name: String, args: List<RuntimeValue>): RuntimeValue? {
        return when(name) {
            "rnd" -> RuntimeValue(DataType.UBYTE, rnd.nextInt() and 255)
            "rndw" -> RuntimeValue(DataType.UWORD, rnd.nextInt() and 65535)
            "rndf" -> RuntimeValue(DataType.FLOAT, rnd.nextDouble())
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
                    target[i*2] = value and 255
                    target[i*2+1] = value ushr 8
                }
                null
            }
            else -> TODO("builtin function $name")
        }
    }
}


/**


            Syscall.FUNC_LEN_STR, Syscall.FUNC_LEN_STRS -> {
                val strPtr = evalstack.pop().integerValue()
                val text = heap.get(strPtr).str!!
                evalstack.push(RuntimeValue(DataType.UBYTE, text.length))
            }
            Syscall.FUNC_STRLEN -> {
                val strPtr = evalstack.pop().integerValue()
                val text = heap.get(strPtr).str!!
                val zeroIdx = text.indexOf('\u0000')
                val len = if(zeroIdx>=0) zeroIdx else text.length
                evalstack.push(RuntimeValue(DataType.UBYTE, len))
            }
            Syscall.FUNC_READ_FLAGS -> {
                val carry = if(P_carry) 1 else 0
                val zero = if(P_zero) 2 else 0
                val irqd = if(P_irqd) 4 else 0
                val negative = if(P_negative) 128 else 0
                val flags = carry or zero or irqd or negative
                evalstack.push(RuntimeValue(DataType.UBYTE, flags))
            }
            Syscall.FUNC_SIN -> evalstack.push(RuntimeValue(DataType.FLOAT, sin(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_COS -> evalstack.push(RuntimeValue(DataType.FLOAT, cos(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_SIN8 -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.BYTE, (127.0 * sin(rad)).toShort()))
            }
            Syscall.FUNC_SIN8U -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * sin(rad)).toShort()))
            }
            Syscall.FUNC_SIN16 -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.WORD, (32767.0 * sin(rad)).toInt()))
            }
            Syscall.FUNC_SIN16U -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.WORD, (32768.0 + 32767.5 * sin(rad)).toInt()))
            }
            Syscall.FUNC_COS8 -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.BYTE, (127.0 * cos(rad)).toShort()))
            }
            Syscall.FUNC_COS8U -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * cos(rad)).toShort()))
            }
            Syscall.FUNC_COS16 -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.WORD, (32767.0 * cos(rad)).toInt()))
            }
            Syscall.FUNC_COS16U -> {
                val rad = evalstack.pop().numericValue().toDouble() /256.0 * 2.0 * PI
                evalstack.push(RuntimeValue(DataType.WORD, (32768.0 + 32767.5 * cos(rad)).toInt()))
            }
            Syscall.FUNC_ROUND -> evalstack.push(RuntimeValue(DataType.WORD, evalstack.pop().numericValue().toDouble().roundToInt()))
            Syscall.FUNC_ABS -> {
                val value = evalstack.pop()
                val absValue =
                        when (value.type) {
                            DataType.UBYTE -> RuntimeValue(DataType.UBYTE, value.numericValue())
                            DataType.UWORD -> RuntimeValue(DataType.UWORD, value.numericValue())
                            DataType.FLOAT -> RuntimeValue(DataType.FLOAT, value.numericValue())
                            else -> throw VmExecutionException("cannot get abs of $value")
                        }
                evalstack.push(absValue)
            }
            Syscall.FUNC_TAN -> evalstack.push(RuntimeValue(DataType.FLOAT, tan(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_ATAN -> evalstack.push(RuntimeValue(DataType.FLOAT, atan(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_LN -> evalstack.push(RuntimeValue(DataType.FLOAT, ln(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_LOG2 -> evalstack.push(RuntimeValue(DataType.FLOAT, log2(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_SQRT -> evalstack.push(RuntimeValue(DataType.FLOAT, sqrt(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_SQRT16 -> evalstack.push(RuntimeValue(DataType.UBYTE, sqrt(evalstack.pop().numericValue().toDouble()).toInt()))
            Syscall.FUNC_RAD -> evalstack.push(RuntimeValue(DataType.FLOAT, Math.toRadians(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_DEG -> evalstack.push(RuntimeValue(DataType.FLOAT, Math.toDegrees(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_FLOOR -> {
                val value = evalstack.pop()
                if (value.type in NumericDatatypes)
                    evalstack.push(RuntimeValue(DataType.FLOAT, floor(value.numericValue().toDouble())))
                else throw VmExecutionException("cannot get floor of $value")
            }
            Syscall.FUNC_CEIL -> {
                val value = evalstack.pop()
                if (value.type in NumericDatatypes)
                    evalstack.push(RuntimeValue(DataType.FLOAT, ceil(value.numericValue().toDouble())))
                else throw VmExecutionException("cannot get ceil of $value")
            }
            Syscall.FUNC_MAX_UB -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, value.array.map { it.integer!! }.max() ?: 0))
            }
            Syscall.FUNC_MAX_B -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.BYTE, value.array.map { it.integer!! }.max() ?: 0))
            }
            Syscall.FUNC_MAX_UW -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                if(value.array.any {it.addressOf!=null})
                    throw VmExecutionException("stackvm cannot process raw memory pointers")
                evalstack.push(RuntimeValue(DataType.UWORD, value.array.map { it.integer!! }.max() ?: 0))
            }
            Syscall.FUNC_MAX_W -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.WORD, value.array.map { it.integer!! }.max() ?: 0))
            }
            Syscall.FUNC_MAX_F -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.doubleArray!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.FLOAT, value.doubleArray.max() ?: 0.0))
            }
            Syscall.FUNC_MIN_UB -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, value.array.map { it.integer!! }.min() ?: 0))
            }
            Syscall.FUNC_MIN_B -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.BYTE, value.array.map { it.integer!! }.min() ?: 0))
            }
            Syscall.FUNC_MIN_UW -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                if(value.array.any {it.addressOf!=null})
                    throw VmExecutionException("stackvm cannot process raw memory pointers")
                evalstack.push(RuntimeValue(DataType.UWORD, value.array.map { it.integer!! }.min() ?: 0))
            }
            Syscall.FUNC_MIN_W -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.WORD, value.array.map { it.integer!! }.min() ?: 0))
            }
            Syscall.FUNC_MIN_F -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.doubleArray!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.FLOAT, value.doubleArray.min() ?: 0.0))
            }
            Syscall.FUNC_SUM_W, Syscall.FUNC_SUM_B -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.WORD, value.array.map { it.integer!! }.sum()))
            }
            Syscall.FUNC_SUM_UW -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                if(value.array.any {it.addressOf!=null})
                    throw VmExecutionException("stackvm cannot process raw memory pointers")
                evalstack.push(RuntimeValue(DataType.UWORD, value.array.map { it.integer!! }.sum()))
            }
            Syscall.FUNC_SUM_UB -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UWORD, value.array.map { it.integer!! }.sum()))
            }
            Syscall.FUNC_SUM_F -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.doubleArray!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.FLOAT, value.doubleArray.sum()))
            }
            Syscall.FUNC_ANY_B, Syscall.FUNC_ANY_W -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, if (value.array.any { it.integer != 0 }) 1 else 0))
            }
            Syscall.FUNC_ANY_F -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.doubleArray!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, if (value.doubleArray.any { it != 0.0 }) 1 else 0))
            }
            Syscall.FUNC_ALL_B, Syscall.FUNC_ALL_W -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.array!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, if (value.array.all { it.integer != 0 }) 1 else 0))
            }
            Syscall.FUNC_ALL_F -> {
                val length = evalstack.pop().integerValue()
                val heapVarId = evalstack.pop().integerValue()
                val value = heap.get(heapVarId)
                if(length!=value.doubleArray!!.size)
                    throw VmExecutionException("iterable length mismatch")
                evalstack.push(RuntimeValue(DataType.UBYTE, if (value.doubleArray.all { it != 0.0 }) 1 else 0))
            }
            Syscall.FUNC_MEMCOPY -> {
                val numbytes = evalstack.pop().integerValue()
                val to = evalstack.pop().integerValue()
                val from = evalstack.pop().integerValue()
                mem.copy(from, to, numbytes)
            }


        **/
