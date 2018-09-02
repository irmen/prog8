package il65.stackvm

import il65.ast.DataType
import il65.compiler.Mflpt5
import il65.compiler.Petscii
import java.io.File
import java.io.PrintWriter
import java.util.*
import kotlin.math.max
import kotlin.math.pow

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH,           // push constant byte value
    PUSH_VAR,       // push a variable
    DUP,            // push topmost value once again

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD,        // discard X bytes from the top of the stack
    POP_MEM,        // pop value into destination memory address
    POP_VAR,        // pop value into variable

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
    SHL_VAR,
    SHR,
    SHR_MEM,
    SHR_MEM_W,
    SHR_VAR,
    ROL,
    ROL_MEM,
    ROL_MEM_W,
    ROL_VAR,
    ROR,
    ROR_MEM,
    ROR_MEM_W,
    ROR_VAR,
    ROL2,
    ROL2_MEM,
    ROL2_MEM_W,
    ROL2_VAR,
    ROR2,
    ROR2_MEM,
    ROR2_MEM_W,
    ROR2_VAR,
    AND,
    OR,
    XOR,
    INV,

    // logical operations (?)

    // increment, decrement
    INC,
    INC_MEM,
    INC_MEM_W,
    INC_VAR,
    DEC,
    DEC_MEM,
    DEC_MEM_W,
    DEC_VAR,

    // comparisons (?)

    // branching
    JUMP,
    BCS,
    BCC,
    //BEQ,      // @todo not implemented status flag Z
    //BNE,      // @todo not implemented status flag Z
    //BVS,      // @todo not implemented status flag V
    //BVC,      // @todo not implemented status flag V
    //BMI,      // @todo not implemented status flag N
    //BPL,      // @todo not implemented status flag N

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    SWAP,
    SEC,
    CLC,
    TERMINATE
}

enum class Syscall(val callNr: Short) {
    WRITE_MEMCHR(10),           // print a single char from the memory
    WRITE_MEMSTR(11),           // print a 0-terminated petscii string from the memory
    WRITE_NUM(12),              // pop from the evaluation stack and print it as a number
    WRITE_CHAR(13),             // pop from the evaluation stack and print it as a single petscii character
    WRITE_VAR(14),              // print the number or string from the given variable
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


data class Value(val type: DataType, private val numericvalue: Number?, val stringvalue: String?=null) {
    private var byteval: Short? = null
    private var wordval: Int? = null
    private var floatval: Double? = null

    init {
        when(type) {
            DataType.BYTE -> byteval = (numericvalue!!.toInt() and 255).toShort()
            DataType.WORD -> wordval = numericvalue!!.toInt() and 65535
            DataType.FLOAT -> floatval = numericvalue!!.toDouble()
            DataType.STR -> if(stringvalue==null) throw VmExecutionException("expect stringvalue for STR type")
            else -> throw VmExecutionException("invalid datatype $type")
        }
    }

    override fun toString(): String {
        return "$type: $numericvalue $stringvalue"
    }

    fun numericValue(): Number {
        return when(type) {
            DataType.BYTE -> byteval!!
            DataType.WORD-> wordval!!
            DataType.FLOAT -> floatval!!
            else -> throw VmExecutionException("invalid datatype for numeric value: $type")
        }
    }

    fun integerValue(): Int {
        return when(type) {
            DataType.BYTE -> byteval!!.toInt()
            DataType.WORD-> wordval!!
            DataType.FLOAT -> floatval!!.toInt()
            else -> throw VmExecutionException("invalid datatype for integer value: $type")
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


data class Instruction(val opcode: Opcode,
                       val args: List<Value> = emptyList(),
                       val subArgAllocations: List<String> = emptyList(),
                       val callLabel: String? = null) {
    lateinit var next: Instruction
    var nextAlt: Instruction? = null

    init {
        if(callLabel!=null) {
            if(args.size!=subArgAllocations.size)
                throw VmExecutionException("for $opcode the subArgAllocations size is not the same as the arg list size")
        }
    }
    override fun toString(): String {
        return if(callLabel==null)
            "$opcode  $args"
        else
            "$opcode  $callLabel  $args  $subArgAllocations"
    }
}


private class VmExecutionException(msg: String?) : Exception(msg)

private class VmTerminationException(msg: String?) : Exception(msg)

private class MyStack<T> : Stack<T>() {
    fun peek(amount: Int) : List<T> {
        return this.toList().subList(max(0, size-amount), size)
    }

    fun pop2() : Pair<T, T> = Pair(pop(), pop())

    fun printTop(amount: Int, output: PrintWriter) {
        peek(amount).reversed().forEach { output.println("  $it") }
    }
}

class Program (prog: List<Instruction>, labels: Map<String, Instruction>, val variables: Map<String, Value>) {

    val program: List<Instruction> = prog.plus(listOf(
            Instruction(Opcode.TERMINATE, listOf()),
            Instruction(Opcode.TERMINATE, listOf())
    ))

    init {
        connect(labels)
    }

    private fun connect(labels: Map<String, Instruction>) {
        val it1 = program.iterator()
        val it2 = program.iterator()
        it2.next()

        while(it1.hasNext() && it2.hasNext()) {
            val instr = it1.next()
            val nextInstr = it2.next()
            when(instr.opcode) {
                Opcode.TERMINATE -> instr.next = instr          // won't ever execute a next instruction
                Opcode.RETURN -> instr.next = instr             // kinda a special one, in actuality the return instruction is dynamic
                Opcode.JUMP -> labels[instr.callLabel]?.let {instr.next=it}
                Opcode.BCC -> labels[instr.callLabel]?.let {instr.next=it}
                Opcode.BCS -> labels[instr.callLabel]?.let {instr.next=it}
                Opcode.CALL -> {
                    val jumpInstr = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                    instr.next=jumpInstr
                    instr.nextAlt = nextInstr  // instruction to return to
                }
                else -> instr.next = nextInstr
            }
        }
    }
}


class StackVm(val traceOutputFile: String?) {
    val mem = Memory()
    private val evalstack = MyStack<Value>()   // evaluation stack
    private val callstack = MyStack<Instruction>()    // subroutine call stack        (@todo maybe use evalstack as well for this?)
    private var variables = mutableMapOf<String, Value>()     // all variables (set of all vars used by all blocks/subroutines) key = their fully scoped name
    private var carry: Boolean = false
    private var program = listOf<Instruction>()
    private var traceOutput = if(traceOutputFile!=null) File(traceOutputFile).printWriter() else null

    fun run(program: Program) {
        this.program = program.program
        this.variables = program.variables.toMutableMap()
        var ins = this.program[0]

        try {
            while (true) {
                ins = dispatch(ins)

                if(evalstack.size > 128)
                    throw VmExecutionException("too many values on evaluation stack")
                if(callstack.size > 128)
                    throw VmExecutionException("too many nested/recursive calls")
            }
        } catch (x: VmTerminationException) {
            println("\n\nExecution terminated.")
        } finally {
            traceOutput?.close()
        }

    }

    fun dispatch(ins: Instruction) : Instruction {
        traceOutput?.println("\n$ins")
        when (ins.opcode) {
            Opcode.PUSH -> evalstack.push(ins.args[0])
            Opcode.DUP -> evalstack.push(evalstack.peek())
            Opcode.DISCARD -> evalstack.pop()
            Opcode.SWAP -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(top)
                evalstack.push(second)
            }
            Opcode.POP_MEM -> {
                val value = evalstack.pop()
                val address = ins.args[0].integerValue()
                when (value.type) {
                    DataType.BYTE -> mem.setByte(address, value.integerValue().toShort())
                    DataType.WORD -> mem.setWord(address, value.integerValue())
                    DataType.FLOAT -> mem.setFloat(address, value.numericValue().toDouble())
                    else -> throw VmExecutionException("can only manipulate byte/word/float on stack")
                }
            }
            Opcode.ADD -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.add(top))
            }
            Opcode.SUB -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.sub(top))
            }
            Opcode.MUL -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.mul(top))
            }
            Opcode.DIV -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.div(top))
            }
            Opcode.POW -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.pow(top))
            }
            Opcode.NEG -> {
                val v = evalstack.pop()
                evalstack.push(v.neg())
            }
            Opcode.SHL -> {
                val v = evalstack.pop()
                evalstack.push(v.shl())
            }
            Opcode.SHR -> {
                val v = evalstack.pop()
                evalstack.push(v.shr())
            }
            Opcode.ROL -> {
                val v = evalstack.pop()
                val (result, newCarry) = v.rol(carry)
                this.carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL2 -> {
                val v = evalstack.pop()
                evalstack.push(v.rol2())
            }
            Opcode.ROR -> {
                val v = evalstack.pop()
                val (result, newCarry) = v.ror(carry)
                this.carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR2 -> {
                val v = evalstack.pop()
                evalstack.push(v.ror2())
            }
            Opcode.AND -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.and(top))
            }
            Opcode.OR -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.or(top))
            }
            Opcode.XOR -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.xor(top))
            }
            Opcode.INV -> {
                val v = evalstack.pop()
                evalstack.push(v.inv())
            }
            Opcode.INC -> {
                val v = evalstack.pop()
                evalstack.push(v.inc())
            }
            Opcode.DEC -> {
                val v = evalstack.pop()
                evalstack.push(v.dec())
            }
            Opcode.SYSCALL -> {
                val callId = ins.args[0].integerValue().toShort()
                val syscall = Syscall.values().first { it.callNr == callId }
                when (syscall) {
                    Syscall.WRITE_MEMCHR -> print(Petscii.decodePetscii(listOf(mem.getByte(ins.args[1].integerValue())), true))
                    Syscall.WRITE_MEMSTR -> print(mem.getString(ins.args[1].integerValue()))
                    Syscall.WRITE_NUM -> print(evalstack.pop().numericValue())
                    Syscall.WRITE_CHAR -> print(Petscii.decodePetscii(listOf(evalstack.pop().integerValue().toShort()), true))
                    Syscall.WRITE_VAR -> {
                        val varname = ins.args[1].stringvalue ?: throw VmExecutionException("$syscall expects string argument (the variable name)")
                        val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                        when(variable.type) {
                            DataType.BYTE, DataType.WORD, DataType.FLOAT -> print(variable.numericValue())
                            DataType.STR -> print(variable.stringvalue)
                            else -> throw VmExecutionException("invalid datatype")
                        }
                    }
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

            Opcode.JUMP -> {}   // do nothing; the next instruction is wired up already to the jump target
            Opcode.BCS -> return if(carry) ins.next else ins.nextAlt!!
            Opcode.BCC -> return if(carry) ins.nextAlt!! else ins.next
            Opcode.CALL -> callstack.push(ins.nextAlt)
            Opcode.RETURN -> return callstack.pop()
            Opcode.PUSH_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                evalstack.push(variable)
            }
            Opcode.POP_VAR -> {
                val value = evalstack.pop()
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                if(variable.type!=value.type) throw VmExecutionException("value datatype ${value.type} is not the same as variable datatype ${variable.type}")
                variables[varname] = value
            }
            Opcode.SHL_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.shl()
            }
            Opcode.SHR_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.shr()
            }
            Opcode.ROL_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                val (newValue, newCarry) = variable.rol(carry)
                variables[varname] = newValue
                carry = newCarry
            }
            Opcode.ROR_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                val (newValue, newCarry) = variable.ror(carry)
                variables[varname] = newValue
                carry = newCarry
            }
            Opcode.ROL2_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.rol2()
            }
            Opcode.ROR2_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.ror2()
            }
            Opcode.INC_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.inc()
            }
            Opcode.DEC_VAR -> {
                val varname = ins.args[0].stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.dec()
            }
        }

        if(traceOutput!=null) {
            traceOutput?.println(" evalstack (size=${evalstack.size}):")
            evalstack.printTop(4, traceOutput!!)
        }

        return ins.next
    }
}


fun main(args: Array<String>) {
    val vm = StackVm(traceOutputFile = "vmtrace.txt")
    vm.mem.setString(0x1000, "Hallo!\n")

    val instructions = listOf(
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_VAR.callNr), Value(DataType.STR, null, stringvalue = "main.var1"))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_MEMSTR.callNr), Value(DataType.WORD, 0x1000))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_VAR.callNr), Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.PUSH, listOf(Value(DataType.WORD, 12345))),
            Instruction(Opcode.POP_VAR, listOf(Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_VAR.callNr), Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.DEC_VAR, listOf(Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_VAR.callNr), Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.SHR_VAR, listOf(Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.SYSCALL, listOf(Value(DataType.BYTE, Syscall.WRITE_VAR.callNr), Value(DataType.STR, null, "main.var2"))),
            Instruction(Opcode.TERMINATE)
    )

    val labels = listOf(Pair("derp", instructions[0])).toMap()
    val variables = mapOf(
            "main.var1" to Value(DataType.STR, null, "Dit is variabele main.var1!\n"),
            "main.var2" to Value(DataType.WORD, 9999)
            )

    val program = Program(instructions, labels, variables)
    vm.run(program)
}