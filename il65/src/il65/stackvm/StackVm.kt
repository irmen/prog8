package il65.stackvm

import il65.ast.DataType
import il65.compiler.Mflpt5
import il65.compiler.Petscii
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.*
import kotlin.math.max
import kotlin.math.pow

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
    POW,
    NEG,
    SHL,
    SHL_MEM,
    SHL_MEM_W,
    SHL_LOCAL,
    SHR,
    SHR_MEM,
    SHR_MEM_W,
    SHR_LOCAL,
    ROL,
    ROL_MEM,
    ROL_MEM_W,
    ROL_LOCAL,
    ROR,
    ROR_MEM,
    ROR_MEM_W,
    ROR_LOCAL,
    ROL2,
    ROL2_MEM,
    ROL2_MEM_W,
    ROL2_LOCAL,
    ROR2,
    ROR2_MEM,
    ROR2_MEM_W,
    ROR2_LOCAL,
    AND,
    OR,
    XOR,
    INV,

    // logical operations (?)

    // increment, decrement
    INC,
    INC_MEM,
    INC_MEM_W,
    INC_LOCAL,
    DEC,
    DEC_MEM,
    DEC_MEM_W,
    DEC_LOCAL,

    // comparisons (?)

    // branching
    JUMP,

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    SEC,
    CLC,
    TERMINATE
}

enum class Syscall(val callNr: Short) {
    WRITECHR(10),
    WRITESTR(11),
    WRITE_NUM(12),
    WRITE_CHAR(13)
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

    fun setString(address: Int, str: String) {
        // lowercase PETSCII
        val petscii = Petscii.encodePetscii(str, true)
        var addr = address
        for (c in petscii) mem[addr++] = c
        mem[addr] = 0
    }

    fun getString(strAddress: Int): String {
        // lowercase PETSCII
        val petscii = mutableListOf<Short>()
        var addr = strAddress
        while(true) {
            val byte = mem[addr++]
            if(byte==0.toShort()) break
            petscii.add(byte)
        }
        return Petscii.decodePetscii(petscii, true)
    }
}


data class Value(val type: DataType, private val value: Number)
{
    private var byteval: Short? = null
    private var wordval: Int? = null
    private var floatval: Double? = null

    init {
        when(type) {
            DataType.BYTE -> byteval = (value.toInt() and 255).toShort()
            DataType.WORD -> wordval = value.toInt() and 65535
            DataType.FLOAT -> floatval = value.toDouble()
            else -> throw VmExecutionException("invalid value datatype $type")
        }
    }

    fun numericValue(): Number {
        return when(type) {
            DataType.BYTE -> byteval!!
            DataType.WORD-> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw VmExecutionException("invalid datatype")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            DataType.BYTE -> byteval!!.toInt()
            DataType.WORD-> wordval!!
            DataType.FLOAT -> floatval!!.toInt()
            else -> throw VmExecutionException("invalid datatype")
        }
    }

    fun add(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() + v2.toDouble()
        return Value(type, result)
    }

    fun sub(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() - v2.toDouble()
        return Value(type, result)
    }

    fun mul(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() * v2.toDouble()
        return Value(type, result)
    }

    fun div(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() / v2.toDouble()
        return Value(type, result)
    }

    fun pow(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble().pow(v2.toDouble())
        return Value(type, result)
    }

    fun shl(): Value {
        val v = integerValue()
        return Value(type, v.shl(1))
    }

    fun shr(): Value {
        val v = integerValue()
        return Value(type, v.ushr(1))
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

    fun and(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1.and(v2)
        return Value(type, result)
    }

    fun or(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1.or(v2)
        return Value(type, result)
    }

    fun xor(other: Value): Value {
        val v1 = integerValue()
        val v2 = other.integerValue()
        val result = v1.xor(v2)
        return Value(type, result)
    }

    fun inv(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, byteval!!.toInt().inv())
            DataType.WORD -> Value(DataType.WORD, wordval!!.inv())
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }

    fun inc(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, (byteval!! + 1).and(255))
            DataType.WORD -> Value(DataType.WORD, (wordval!! + 1).and(65535))
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! + 1)
            else -> throw VmExecutionException("inc can only work on byte/word/float")
        }
    }

    fun dec(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, (byteval!! - 1).and(255))
            DataType.WORD -> Value(DataType.WORD, (wordval!! - 1).and(65535))
            DataType.FLOAT -> Value(DataType.FLOAT, floatval!! - 1)
            else -> throw VmExecutionException("dec can only work on byte/word/float")
        }
    }
}


data class Instruction(val opcode: Opcode, val args: List<Value>) {
    lateinit var next: Instruction
    var nextAlt: Instruction? = null
}


private class VmExecutionException(msg: String?) : Exception(msg)

private class VmTerminationException(msg: String?) : Exception(msg)

private class MyStack<T> : Stack<T>() {
    fun peek(amount: Int) : List<T> {
        return this.toList().subList(max(0, size-amount), size)
    }

    fun pop2() : Pair<T, T> = Pair(pop(), pop())

    fun printTop(amount: Int) {
        peek(amount).reversed().forEach { println("     $it") }
    }
}

class Program {
    var prog: List<Instruction> = listOf()

    fun load(prog: List<Instruction>) {
        this.prog = prog.plus(listOf(
                Instruction(Opcode.TERMINATE, listOf()),
                Instruction(Opcode.TERMINATE, listOf())
        ))
        connectPointers()
    }

    fun connectPointers() {
        val it1 = prog.iterator()
        val it2 = prog.iterator()
        it2.next()

        while(it1.hasNext() && it2.hasNext()) {
            val instr = it1.next()
            val nextInstr = it2.next()
            when(instr.opcode) {
                Opcode.TERMINATE -> instr.next = instr
                Opcode.CALL -> TODO()
                Opcode.JUMP -> TODO()
                Opcode.RETURN -> TODO()
                else -> instr.next = nextInstr
            }
        }
    }
}

class StackVm(val vmOutputFile: String) {
    val mem = Memory()
    private val es = MyStack<Value>()
    private var carry: Boolean = false
    private var program = listOf<Instruction>()
    private var output = PrintWriter(FileOutputStream(vmOutputFile, true))

    fun run(program: Program) {
        this.program = program.prog
        var ins = this.program[0]

        output.println("---- new session at ${Date()} ----")
        try {
            while (true) {
                ins = dispatch(ins)
            }
        } catch (x: VmTerminationException) {
            output.println("---- session ended ----")
            output.flush()
            output.close()
            println("Execution terminated.")
        }
    }

    fun dispatch(ins: Instruction) : Instruction {
        println("dispatching: $ins")
        when (ins.opcode) {
            Opcode.PUSH -> es.push(ins.args[0])
            Opcode.DISCARD -> es.pop()
            Opcode.POP_MEM -> {
                val value = es.pop()
                val address = ins.args[0].integerValue()
                when (value.type) {
                    DataType.BYTE -> mem.setByte(address, value.integerValue().toShort())
                    DataType.WORD -> mem.setWord(address, value.integerValue())
                    DataType.FLOAT -> mem.setFloat(address, value.numericValue().toDouble())
                    else -> throw VmExecutionException("can only manipulate byte/word/float on stack")
                }
            }
            Opcode.ADD -> {
                val (top, second) = es.pop2()
                es.push(second.add(top))
            }
            Opcode.SUB -> {
                val (top, second) = es.pop2()
                es.push(second.sub(top))
            }
            Opcode.MUL -> {
                val (top, second) = es.pop2()
                es.push(second.mul(top))
            }
            Opcode.DIV -> {
                val (top, second) = es.pop2()
                es.push(second.div(top))
            }
            Opcode.POW -> {
                val (top, second) = es.pop2()
                es.push(second.pow(top))
            }
            Opcode.NEG -> {
                val v = es.pop()
                es.push(v.neg())
            }
            Opcode.SHL -> {
                val v = es.pop()
                es.push(v.shl())
            }
            Opcode.SHR -> {
                val v = es.pop()
                es.push(v.shr())
            }
            Opcode.ROL -> {
                val v = es.pop()
                val (result, newCarry) = v.rol(carry)
                this.carry = newCarry
                es.push(result)
            }
            Opcode.ROL2 -> {
                val v = es.pop()
                es.push(v.rol2())
            }
            Opcode.ROR -> {
                val v = es.pop()
                val (result, newCarry) = v.ror(carry)
                this.carry = newCarry
                es.push(result)
            }
            Opcode.ROR2 -> {
                val v = es.pop()
                es.push(v.ror2())
            }
            Opcode.AND -> {
                val (top, second) = es.pop2()
                es.push(second.and(top))
            }
            Opcode.OR -> {
                val (top, second) = es.pop2()
                es.push(second.or(top))
            }
            Opcode.XOR -> {
                val (top, second) = es.pop2()
                es.push(second.xor(top))
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
            Opcode.SYSCALL -> {
                val callId = ins.args[0].integerValue().toShort()
                val syscall = Syscall.values().first { it.callNr == callId }
                when (syscall) {
                    Syscall.WRITECHR -> output.print(Petscii.decodePetscii(listOf(mem.getByte(ins.args[1].integerValue())), true))
                    Syscall.WRITESTR -> output.print(mem.getString(ins.args[1].integerValue()))
                    Syscall.WRITE_NUM -> output.print(es.pop().numericValue())
                    Syscall.WRITE_CHAR -> output.print(Petscii.decodePetscii(listOf(es.pop().integerValue().toShort()), true))
                    else -> throw VmExecutionException("unimplemented syscall $syscall")
                }
            }

            Opcode.SEC -> carry = true
            Opcode.CLC -> carry = false
            Opcode.TERMINATE -> throw VmTerminationException("execution terminated")

            Opcode.INC_MEM -> {
                val addr = ins.args[0].integerValue()
                val newValue = Value(DataType.BYTE, mem.getByte(addr)).inc()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.INC_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val newValue = Value(DataType.WORD, mem.getWord(addr)).inc()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.DEC_MEM -> {
                val addr = ins.args[0].integerValue()
                val newValue = Value(DataType.BYTE, mem.getByte(addr)).dec()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.DEC_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val newValue = Value(DataType.WORD, mem.getWord(addr)).dec()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.SHL_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.shl()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHL_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.shl()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.SHR_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.shr()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHR_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.shr()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.ROL_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val (newValue, newCarry) = value.rol(carry)
                mem.setByte(addr, newValue.integerValue().toShort())
                carry = newCarry
            }
            Opcode.ROL_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val (newValue, newCarry) = value.rol(carry)
                mem.setWord(addr, newValue.integerValue())
                carry = newCarry
            }
            Opcode.ROR_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val (newValue, newCarry) = value.ror(carry)
                mem.setByte(addr, newValue.integerValue().toShort())
                carry = newCarry
            }
            Opcode.ROR_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val (newValue, newCarry) = value.ror(carry)
                mem.setWord(addr, newValue.integerValue())
                carry = newCarry
            }
            Opcode.ROL2_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.rol2()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROL2_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.rol2()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.ROR2_MEM -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.ror2()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROR2_MEM_W -> {
                val addr = ins.args[0].integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.ror2()
                mem.setWord(addr, newValue.integerValue())
            }

            Opcode.JUMP -> {} // do nothing; the next instruction is wired up already
            Opcode.CALL -> TODO()
            Opcode.RETURN -> TODO()
            Opcode.PUSH_LOCAL -> TODO()
            Opcode.POP_LOCAL -> TODO()
            Opcode.INC_LOCAL -> TODO()
            Opcode.DEC_LOCAL -> TODO()
            Opcode.SHL_LOCAL -> TODO()
            Opcode.SHR_LOCAL -> TODO()
            Opcode.ROL_LOCAL -> TODO()
            Opcode.ROR_LOCAL -> TODO()
            Opcode.ROL2_LOCAL -> TODO()
            Opcode.ROR2_LOCAL -> TODO()
        }
        println("  es:")
        es.printTop(5)

        return ins.next
    }
}


fun main(args: Array<String>) {
    val vm = StackVm(vmOutputFile = "vmout.txt")
    val program = Program()
    program.load(listOf(
            Instruction(Opcode.PUSH, listOf(Value(DataType.BYTE, 100))),
            Instruction(Opcode.PUSH, listOf(Value(DataType.BYTE, 133))),
            Instruction(Opcode.ADD, emptyList()),
            Instruction(Opcode.PUSH, listOf(Value(DataType.BYTE, 3))),
            Instruction(Opcode.DIV, emptyList()),
            Instruction(Opcode.INC, emptyList()),
            Instruction(Opcode.PUSH, listOf(Value(DataType.BYTE, 2))),
            Instruction(Opcode.SHR, emptyList()),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITESTR.callNr), Value(DataType.WORD, 0x1000))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_NUM.callNr))),
            Instruction(Opcode.PUSH, listOf(Value(DataType.BYTE, Petscii.encodePetscii("@", true)[0]))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_CHAR.callNr))),

            Instruction(Opcode.CLC, emptyList()),
            Instruction(Opcode.PUSH, listOf(Value(DataType.WORD, 2))),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.ROR2, emptyList()),
            Instruction(Opcode.CLC, emptyList()),
            Instruction(Opcode.PUSH, listOf(Value(DataType.WORD, 16384))),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList()),
            Instruction(Opcode.ROL2, emptyList())
    ))
    vm.mem.setString(0x1000, "Hallo!\n")
    vm.run(program)
}