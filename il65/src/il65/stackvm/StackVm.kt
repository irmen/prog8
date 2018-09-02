package il65.stackvm

import il65.ast.DataType
import il65.compiler.Mflpt5
import il65.compiler.Petscii
import java.util.*

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH,           // push constant byte value
    PUSH_LOCAL,     // push block-local variable

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD,        // discard X bytes from the top of the stack
    POP_MEM,        // pop value into destination memory address
    POP_LOCAL,      // pop value into block-local variable

    // numeric and bitwise arithmetic
    ADD,
    SUB,
    MUL,
    DIV,
    NEG,
    SHL,
    SHR,
    ROL,
    ROR,
    AND,
    OR,
    XOR,
    INV,


    // logical operations (?)

    // increment, decrement
    INC,
    INC_MEM,
    INC_MEM_W,
    INC_MEM_F,
    INC_LOCAL,
    DEC,
    DEC_MEM,
    DEC_MEM_W,
    DEC_MEM_F,
    DEC_LOCAL,

    // comparisons (?)

    // branching
    JUMP,

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    TERMINATE
}

enum class Syscall(val callNr: Short) {
    WRITECHR(10),
    WRITESTR(11)
}

class Memory {
    private val mem = ShortArray(65536)         // shorts because byte is signed and we store values 0..255

    fun getByte(address: Int): Short {
        return mem[address]
    }

    fun setByte(address: Int, value: Short) {
        if(value<0 || value>255) throw VmExecutionException("byte value not 0..255")
        mem[address] = value
    }

    fun getWord(address: Int): Int {
        return 256*mem[address] + mem[address+1]
    }

    fun setWord(address: Int, value: Int) {
        if(value<0 || value>65535) throw VmExecutionException("word value not 0..65535")
        mem[address] = (value / 256).toShort()
        mem[address+1] = value.and(255).toShort()
    }

    fun setFloat(address: Int, value: Double) {
        val mflpt5 = Mflpt5.fromNumber(value)
        mem[address] = mflpt5.b0
        mem[address+1] = mflpt5.b1
        mem[address+2] = mflpt5.b2
        mem[address+3] = mflpt5.b3
        mem[address+4] = mflpt5.b4
    }

    fun getFloat(address: Int): Double {
        return Mflpt5(mem[address], mem[address+1], mem[address+2], mem[address+3], mem[address+4]).toDouble()
    }
}


data class Value(val type: DataType,
                 val byteval: Short?,
                 val wordval: Int?,
                 val floatval: Double?)
{

    fun add(other: Value): Value {
        val v1: Number = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            floatval!=null -> floatval
            else -> throw VmExecutionException("add missing value 1")
        }
        val v2: Number = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            other.floatval!=null -> other.floatval
            else -> throw VmExecutionException("add missing value 2")
        }
        val result = v1.toDouble() + v2.toDouble()
        return resultvalue(type, result)
    }

    fun sub(other: Value): Value {
        val v1: Number = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            floatval!=null -> floatval
            else -> throw VmExecutionException("sub missing value 1")
        }
        val v2: Number = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            other.floatval!=null -> other.floatval
            else -> throw VmExecutionException("sub missing value 2")
        }
        val result = v1.toDouble() - v2.toDouble()
        return resultvalue(type, result)
    }

    fun mul(other: Value): Value {
        val v1: Number = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            floatval!=null -> floatval
            else -> throw VmExecutionException("mul missing value 1")
        }
        val v2: Number = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            other.floatval!=null -> other.floatval
            else -> throw VmExecutionException("mul missing value 2")
        }
        val result = v1.toDouble() * v2.toDouble()
        return resultvalue(type, result)
    }

    fun div(other: Value): Value {
        val v1: Number = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            floatval!=null -> floatval
            else -> throw VmExecutionException("div missing value 1")
        }
        val v2: Number = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            other.floatval!=null -> other.floatval
            else -> throw VmExecutionException("div missing value 2")
        }
        val result = v1.toDouble() / v2.toDouble()
        return resultvalue(type, result)
    }

    fun shl(other: Value): Value {
        val v1 = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            else -> throw VmExecutionException("shl can only work on byte/word")
        }
        val v2 = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            else -> throw VmExecutionException("shl can only work on byte/word")
        }
        val result = v1.shl(v2)
        return resultvalue(type, result)
    }

    fun shr(other: Value): Value {
        val v1 = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            else -> throw VmExecutionException("shr can only work on byte/word")
        }
        val v2 = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            else -> throw VmExecutionException("shr can only work on byte/word")
        }
        val result = v1.ushr(v2)
        return resultvalue(type, result)
    }

    fun rol(other: Value): Value = TODO()

    fun ror(other: Value): Value = TODO()

    fun neg(): Value {
        return when {
            byteval!=null -> resultvalue(DataType.BYTE, -byteval)
            wordval!=null -> resultvalue(DataType.WORD, -wordval)
            floatval!=null -> resultvalue(DataType.FLOAT, -floatval)
            else -> throw VmExecutionException("neg can only work on byte/word/float")
        }
    }

    fun and(other: Value): Value {
        val v1 = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            else -> throw VmExecutionException("and can only work on byte/word")
        }
        val v2 = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            else -> throw VmExecutionException("and can only work on byte/word")
        }
        val result = v1.and(v2)
        return resultvalue(type, result)
    }

    fun or(other: Value): Value {
        val v1 = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            else -> throw VmExecutionException("or can only work on byte/word")
        }
        val v2 = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            else -> throw VmExecutionException("or can only work on byte/word")
        }
        val result = v1.or(v2)
        return resultvalue(type, result)
    }

    fun xor(other: Value): Value {
        val v1 = when {
            byteval!=null -> byteval.toInt()
            wordval!=null -> wordval
            else -> throw VmExecutionException("xor can only work on byte/word")
        }
        val v2 = when {
            other.byteval!=null -> other.byteval.toInt()
            other.wordval!=null -> other.wordval
            else -> throw VmExecutionException("xor can only work on byte/word")
        }
        val result = v1.xor(v2)
        return resultvalue(type, result)
    }

    fun inv(): Value {
        return when {
            byteval!=null -> resultvalue(DataType.BYTE, byteval.toInt().inv())
            wordval!=null -> resultvalue(DataType.WORD, wordval.inv())
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }

    fun inc(): Value = TODO()
    fun dec(): Value = TODO()


    private fun resultvalue(type: DataType, value: Number) : Value {
        return when (type) {
            DataType.BYTE -> Value(DataType.BYTE, value.toShort(), null, null)
            DataType.WORD -> Value(DataType.WORD, null, value.toInt(), null)
            DataType.FLOAT -> Value(DataType.FLOAT, null, null, value.toDouble())
            else -> throw VmExecutionException("can only work on byte/word/float")
        }
    }
}


data class Instruction(val opcode: Opcode, val args: List<Value>) {
    lateinit var next: Instruction
    lateinit var nextAlt: Instruction
}


private class VmExecutionException(msg: String?) : Exception(msg)

private class VmTerminationException(msg: String?) : Exception(msg)


class StackVm {
    val mem = Memory()
    private val es = Stack<Value>()

    fun dispatch(ins: Instruction) : Instruction {
        when(ins.opcode) {
            Opcode.PUSH -> es.push(ins.args[0])
            Opcode.DISCARD -> es.pop()
            Opcode.POP_MEM -> {
                val value = es.pop()
                val address = ins.args[0].wordval!!
                when(value.type) {
                    DataType.BYTE -> mem.setByte(address, value.byteval!!)
                    DataType.WORD -> mem.setWord(address, value.wordval!!)
                    DataType.FLOAT -> mem.setFloat(address, value.floatval!!)
                    else -> throw VmExecutionException("can only manipulate byte/word/float on stack")
                }
            }
            Opcode.ADD -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.add(v2))
            }
            Opcode.SUB -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.sub(v2))
            }
            Opcode.MUL -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.mul(v2))
            }
            Opcode.DIV -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.div(v2))
            }
            Opcode.NEG -> {
                val v = es.pop()
                es.push(v.neg())
            }
            Opcode.SHL -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.shl(v2))
            }
            Opcode.SHR -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.shr(v2))
            }
            Opcode.ROL -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.rol(v2))
            }
            Opcode.ROR -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.ror(v2))
            }
            Opcode.AND -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.and(v2))
            }
            Opcode.OR -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.or(v2))
            }
            Opcode.XOR -> {
                val v1 = es.pop()
                val v2 = es.pop()
                es.push(v1.xor(v2))
            }
            Opcode.INV -> {
                val v = es.pop()
                es.push(v.inv())
            }
            Opcode.INC -> {
                val v = es.pop()
                es.push(v.inc())
            }
            Opcode.DEC -> {
                val v = es.pop()
                es.push(v.dec())
            }
            Opcode.JUMP -> TODO()
            Opcode.CALL -> TODO()
            Opcode.RETURN -> TODO()
            Opcode.SYSCALL -> {
                val callId = ins.args[0].byteval
                val syscall = Syscall.values().first { it.callNr==callId }
                when(syscall){
                    Syscall.WRITECHR -> syscallWriteChr(ins.args[0].wordval!!)
                    Syscall.WRITESTR -> syscallWriteStr(ins.args[0].wordval!!)
                }
            }

            Opcode.TERMINATE -> throw VmTerminationException("execution terminated")

            Opcode.PUSH_LOCAL -> TODO()
            Opcode.POP_LOCAL -> TODO()
            Opcode.INC_LOCAL -> TODO()
            Opcode.DEC_LOCAL -> TODO()
            Opcode.INC_MEM -> TODO()
            Opcode.INC_MEM_W -> TODO()
            Opcode.INC_MEM_F -> TODO()
            Opcode.DEC_MEM -> TODO()
            Opcode.DEC_MEM_W -> TODO()
            Opcode.DEC_MEM_F -> TODO()
        }
        return ins.next
    }

    private fun syscallWriteStr(strAddress: Int) {
        val petscii = mutableListOf<Short>()
        var addr = strAddress
        while(true) {
            val byte = mem.getByte(addr++)
            if(byte==0.toShort()) break
            petscii.add(byte)
        }
        print(Petscii.decodePetscii(petscii, true))
    }

    private fun syscallWriteChr(strAddress: Int) {
        val petscii = listOf(mem.getByte(strAddress))
        print(Petscii.decodePetscii(petscii, true))
    }
}

