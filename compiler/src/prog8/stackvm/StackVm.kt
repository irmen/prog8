package prog8.stackvm

import prog8.ast.DataType
import prog8.compiler.target.c64.Petscii
import prog8.compiler.target.c64.Mflpt5
import java.io.File
import java.io.PrintStream
import java.util.*
import java.util.regex.Pattern
import kotlin.math.*

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH,           // push constant value (any type)
    PUSH_MEM,       // push byte value from memory to stack
    PUSH_MEM_W,     // push word value from memory to stack
    PUSH_MEM_F,     // push float value from memory to stack
    PUSH_VAR,       // push a variable
    DUP,            // push topmost value once again
    ARRAY,          // create arrayvalue of number of integer values on the stack, given in argument

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD,        // discard top value
    POP_MEM,        // pop value into destination memory address
    POP_VAR,        // pop value into variable

    // numeric and bitwise arithmetic
    ADD,
    SUB,
    MUL,
    DIV,
    FLOORDIV,
    REMAINDER,
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
    BITAND,
    BITOR,
    BITXOR,
    INV,
    LSB,
    MSB,

    // numeric type conversions not covered by other opcodes
    B2WORD,         // convert a byte into a word where it is the lower eight bits $00xx
    MSB2WORD,       // convert a byte into a word where it is the upper eight bits $xx00
    B2FLOAT,        // convert byte into floating point
    W2FLOAT,        // convert word into floating point

    // logical operations
    AND,
    OR,
    XOR,

    // increment, decrement
    INC,
    INC_MEM,
    INC_MEM_W,
    INC_VAR,
    DEC,
    DEC_MEM,
    DEC_MEM_W,
    DEC_VAR,

    // comparisons
    LESS,
    GREATER,
    LESSEQ,
    GREATEREQ,
    EQUAL,
    NOTEQUAL,

    // branching
    JUMP,
    BCS,
    BCC,
    BEQ,        // branch if value on top of stack is zero
    BNE,        // branch if value on top of stack is not zero
    BMI,        // branch if value on top of stack < 0
    BPL,        // branch if value on top of stack >= 0
    // BVS,      // status flag V (overflow) not implemented
    // BVC,      // status flag V (overflow) not implemented

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    SWAP,
    SEC,        // set carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    CLC,        // clear carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    SEI,        // set irq-disable status flag
    CLI,        // clear irq-disable status flag
    NOP,
    BREAKPOINT, // breakpoint
    TERMINATE,  // end the program
    LINE        // record source file line number
}

enum class Syscall(val callNr: Short) {
    WRITE_MEMCHR(10),           // print a single char from the memory address popped from stack
    WRITE_MEMSTR(11),           // print a 0-terminated petscii string from the memory address popped from stack
    WRITE_NUM(12),              // pop from the evaluation stack and print it as a number
    WRITE_CHAR(13),             // pop from the evaluation stack and print it as a single petscii character
    WRITE_STR(14),              // pop from the evaluation stack and print it as a string
    INPUT_STR(15),              // user input a string onto the stack, with max length (truncated) given by value on stack
    GFX_PIXEL(16),              // plot a pixel at (x,y,color) pushed on stack in that order
    GFX_CLEARSCR(17),           // clear the screen with color pushed on stack
    GFX_TEXT(18),               // write text on screen at (x,y,color,text) pushed on stack in that order

    FUNC_SIN(66),
    FUNC_COS(67),
    FUNC_ABS(68),
    FUNC_ACOS(69),
    FUNC_ASIN(70),
    FUNC_TAN(71),
    FUNC_ATAN(72),
    FUNC_LN(73),
    FUNC_LOG2(74),
    FUNC_LOG10(75),
    FUNC_SQRT(76),
    FUNC_RAD(77),
    FUNC_DEG(78),
    FUNC_ROUND(79),
    FUNC_FLOOR(80),
    FUNC_CEIL(81),
    FUNC_MAX(82),
    FUNC_MIN(83),
    FUNC_AVG(84),
    FUNC_SUM(85),
    FUNC_LEN(86),
    FUNC_ANY(87),
    FUNC_ALL(88),
    FUNC_RND(89),                // push a random byte on the stack
    FUNC_RNDW(90),               // push a random word on the stack
    FUNC_RNDF(91),               // push a random float on the stack (between 0.0 and 1.0)

    // note: not all builtin functions of the Prog8 language are present as functions:
    // some of them are already opcodes (such as MSB and ROL and FLT)!
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
        return mem[address] + 256*mem[address+1]
    }

    fun setWord(address: Int, value: Int) {
        if(value<0 || value>65535) throw VmExecutionException("word value not 0..65535")
        mem[address] = value.and(255).toShort()
        mem[address+1] = (value / 256).toShort()
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

    fun clear() {
        for(i in 0..65535) mem[i]=0
    }
}


class Value(val type: DataType, numericvalue: Number?, val stringvalue: String?=null, val arrayvalue: IntArray?=null) {
    private var byteval: Short? = null
    private var wordval: Int? = null
    private var floatval: Double? = null
    val asBooleanValue: Boolean

    init {
        when(type) {
            DataType.BYTE -> {
                byteval = (numericvalue!!.toInt() and 255).toShort()        // byte wrap around 0..255
                asBooleanValue = byteval != (0.toShort())
            }
            DataType.WORD -> {
                wordval = numericvalue!!.toInt() and 65535      // word wrap around 0..65535
                asBooleanValue = wordval != 0
            }
            DataType.FLOAT -> {
                floatval = numericvalue!!.toDouble()
                asBooleanValue = floatval != 0.0
            }
            DataType.ARRAY, DataType.ARRAY_W -> {
                asBooleanValue = arrayvalue!!.isNotEmpty()
            }
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                if(stringvalue==null) throw VmExecutionException("expect stringvalue for STR type")
                asBooleanValue = stringvalue.isNotEmpty()
            }
            else -> throw VmExecutionException("invalid datatype $type")
        }
    }

    override fun toString(): String {
        return when(type) {
            DataType.BYTE -> "b:%02x".format(byteval)
            DataType.WORD -> "w:%04x".format(wordval)
            DataType.FLOAT -> "f:$floatval"
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> "\"$stringvalue\""
            DataType.ARRAY -> TODO("tostring array")
            DataType.ARRAY_W -> TODO("tostring word array")
            DataType.MATRIX -> TODO("tostring matrix")
        }
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

    override fun hashCode(): Int {
        val bh = byteval?.hashCode() ?: 0x10001234
        val wh = wordval?.hashCode() ?: 0x01002345
        val fh = floatval?.hashCode() ?: 0x00103456
        val ah = arrayvalue?.hashCode() ?: 0x11119876
        return bh xor wh xor fh xor ah xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is Value)
            return false
        return compareTo(other)==0      // note: datatype doesn't matter
    }

    operator fun compareTo(other: Value): Int {
        if(stringvalue!=null && other.stringvalue!=null)
            return stringvalue.compareTo(other.stringvalue)
        return numericValue().toDouble().compareTo(other.numericValue().toDouble())
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
        return Value(DataType.FLOAT, result)
    }

    fun floordiv(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = (v1.toDouble() / v2.toDouble()).toInt()
        return if(this.type==DataType.BYTE)
            Value(DataType.BYTE, (result and 255).toShort())
        else
            Value(DataType.WORD, result and 65535)
    }

    fun remainder(other: Value): Value? {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble() % v2.toDouble()
        return Value(type, result)
    }

    fun pow(other: Value): Value {
        val v1 = numericValue()
        val v2 = other.numericValue()
        val result = v1.toDouble().pow(v2.toDouble())
        return Value(type, result)      // @todo datatype of pow is now always float, maybe allow byte/word results as well
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

    fun and(other: Value) = Value(DataType.BYTE, if(this.asBooleanValue && other.asBooleanValue) 1 else 0)
    fun or(other: Value) = Value(DataType.BYTE, if(this.asBooleanValue || other.asBooleanValue) 1 else 0)
    fun xor(other: Value) = Value(DataType.BYTE, if(this.asBooleanValue xor other.asBooleanValue) 1 else 0)

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
            DataType.WORD -> Value(DataType.WORD, wordval!! and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }

    fun msb(): Value {
        return when(type) {
            DataType.BYTE -> Value(DataType.BYTE, 0)
            DataType.WORD -> Value(DataType.WORD, wordval!! ushr 8 and 255)
            else -> throw VmExecutionException("not can only work on byte/word")
        }
    }
}


open class Instruction(val opcode: Opcode,
                       val arg: Value? = null,
                       val callLabel: String? = null)
{
    lateinit var next: Instruction
    var nextAlt: Instruction? = null

    override fun toString(): String {
        val argStr = arg?.toString() ?: ""
        val result =
                when {
                    opcode==Opcode.LINE -> "_line ${arg!!.stringvalue}"
                    opcode==Opcode.SYSCALL -> {
                        val syscall = Syscall.values().find { it.callNr==arg!!.numericValue() }
                        "syscall $syscall"
                    }
                    callLabel==null -> "${opcode.toString().toLowerCase()} $argStr"
                    else -> "${opcode.toString().toLowerCase()} $callLabel $argStr"
                }
                .trimEnd()

        return "    $result"
    }
}

class LabelInstr(val name: String) : Instruction(opcode = Opcode.NOP) {
    override fun toString(): String {
        return "$name:"
    }
}

class VmExecutionException(msg: String?) : Exception(msg)

class VmTerminationException(msg: String?) : Exception(msg)

class VmBreakpointException : Exception("breakpoint")

class MyStack<T> : Stack<T>() {
    fun peek(amount: Int) : List<T> {
        return this.toList().subList(max(0, size-amount), size)
    }

    fun pop2() : Pair<T, T> = Pair(pop(), pop())

    fun printTop(amount: Int, output: PrintStream) {
        peek(amount).reversed().forEach { output.println("  $it") }
    }
}

class Program (val name: String,
               prog: MutableList<Instruction>,
               val labels: Map<String, Instruction>,
               val variables: Map<String, Value>,
               val memory: Map<Int, List<Value>>)
{
    companion object {
        fun load(filename: String): Program {
            val lines = File(filename).readLines().withIndex().iterator()
            var memory = mapOf<Int, List<Value>>()
            var vars = mapOf<String, Value>()
            var instructions = mutableListOf<Instruction>()
            var labels = mapOf<String, Instruction>()
            while(lines.hasNext()) {
                val (lineNr, line) = lines.next()
                if(line.startsWith(';') || line.isEmpty())
                    continue
                else if(line=="%memory")
                    memory = loadMemory(lines)
                else if(line=="%variables")
                    vars = loadVars(lines)
                else if(line=="%instructions") {
                    val (insResult, labelResult) = loadInstructions(lines)
                    instructions = insResult
                    labels = labelResult
                }
                else throw VmExecutionException("syntax error at line ${lineNr+1}")
            }
            return Program(filename, instructions, labels, vars, memory)
        }

        private fun loadInstructions(lines: Iterator<IndexedValue<String>>): Pair<MutableList<Instruction>, Map<String, Instruction>> {
            val instructions = mutableListOf<Instruction>()
            val labels = mutableMapOf<String, Instruction>()
            val splitpattern = Pattern.compile("\\s+")
            val nextInstructionLabels = Stack<String>()     // more than one label can occur on the same line

            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_instructions")
                    return Pair(instructions, labels)
                if(!line.startsWith(' ') && line.endsWith(':')) {
                    nextInstructionLabels.push(line.substring(0, line.length-1))
                } else if(line.startsWith(' ')) {
                    val parts = line.trimStart().split(splitpattern, limit = 2)
                    val opcodeStr = parts[0].toUpperCase()
                    val opcode=Opcode.valueOf(if(opcodeStr.startsWith('_')) opcodeStr.substring(1) else opcodeStr)
                    val args = if(parts.size==2) parts[1] else null
                    val instruction = when(opcode) {
                        Opcode.LINE -> Instruction(opcode, Value(DataType.STR, null, stringvalue = args))
                        Opcode.JUMP, Opcode.CALL, Opcode.BMI, Opcode.BPL,
                        Opcode.BEQ, Opcode.BNE, Opcode.BCS, Opcode.BCC -> {
                            if(args!!.startsWith('$')) {
                                Instruction(opcode, Value(DataType.WORD, args.substring(1).toInt(16)))
                            } else {
                                Instruction(opcode, callLabel = args)
                            }
                        }
                        Opcode.INC_VAR, Opcode.DEC_VAR,
                        Opcode.SHR_VAR, Opcode.SHL_VAR, Opcode.ROL_VAR, Opcode.ROR_VAR,
                        Opcode.ROL2_VAR, Opcode.ROR2_VAR, Opcode.POP_VAR, Opcode.PUSH_VAR -> {
                            val withoutQuotes =
                                    if(args!!.startsWith('"') && args.endsWith('"'))
                                        args.substring(1, args.length-1) else args
                            Instruction(opcode, Value(DataType.STR, null, withoutQuotes))
                        }
                        Opcode.SYSCALL -> {
                            val call = Syscall.valueOf(args!!)
                            Instruction(opcode, Value(DataType.BYTE, call.callNr))
                        }
                        else -> {
                            // println("INSTR $opcode at $lineNr  args=$args")
                            Instruction(opcode, getArgValue(args))
                        }
                    }
                    instructions.add(instruction)
                    while(nextInstructionLabels.isNotEmpty()) {
                        val label = nextInstructionLabels.pop()
                        labels[label] = instruction
                    }
                } else throw VmExecutionException("syntax error at line ${lineNr+1}")
            }
        }

        private fun getArgValue(args: String?): Value? {
            if(args==null)
                return null
            if(args[0]=='"' && args[args.length-1]=='"') {
                // it's a string.
                return Value(DataType.STR, null, unescape(args.substring(1, args.length-1)))
            }
            val (type, valueStr) = args.split(':')
            return when(type) {
                "b" -> Value(DataType.BYTE, valueStr.toShort(16))
                "w" -> Value(DataType.WORD, valueStr.toInt(16))
                "f" -> Value(DataType.FLOAT, valueStr.toDouble())
                else -> throw VmExecutionException("invalid datatype $type")
            }
        }

        private fun loadVars(lines: Iterator<IndexedValue<String>>): Map<String, Value> {
            val vars = mutableMapOf<String, Value>()
            val splitpattern = Pattern.compile("\\s+")
            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_variables")
                    return vars
                val (name, type, valueStr) = line.split(splitpattern, limit = 3)
                if(valueStr[0] !='"' && valueStr[1]!=':')
                    throw VmExecutionException("missing value type character")
                val value = when(type) {
                    "byte" -> Value(DataType.BYTE, valueStr.substring(2).toShort(16))
                    "word" -> Value(DataType.WORD, valueStr.substring(2).toInt(16))
                    "float" -> Value(DataType.FLOAT, valueStr.substring(2).toDouble())
                    "str", "str_p", "str_s", "str_ps" -> {
                        if(valueStr.startsWith('"') && valueStr.endsWith('"'))
                            Value(DataType.STR, null, unescape(valueStr.substring(1, valueStr.length-1)))
                        else
                            throw VmExecutionException("str should be enclosed in quotes at line ${lineNr+1}")
                    }
                    else -> throw VmExecutionException("invalid datatype at line ${lineNr+1}")
                }
                vars[name] = value
            }
        }

        private fun unescape(st: String): String {
            val result = mutableListOf<Char>()
            val iter = st.iterator()
            while(iter.hasNext()) {
                val c = iter.nextChar()
                if(c=='\\') {
                    val ec = iter.nextChar()
                    result.add(when(ec) {
                        '\\' -> '\\'
                        'b' -> '\b'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                        }
                        else -> throw VmExecutionException("invalid escape char: $ec")
                    })
                } else {
                    result.add(c)
                }
            }
            return result.joinToString("")
        }

        private fun loadMemory(lines: Iterator<IndexedValue<String>>): Map<Int, List<Value>> {
            val memory = mutableMapOf<Int, List<Value>>()
            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_memory")
                    return memory
                val address = line.substringBefore(' ').toInt(16)
                val rest = line.substringAfter(' ').trim()
                if(rest.startsWith('"')) {
                    memory[address] = listOf(Value(DataType.STR, null, unescape(rest.substring(1, rest.length - 1))))
                } else {
                    val valueStrings = rest.split(' ')
                    val values = mutableListOf<Value>()
                    valueStrings.forEach {
                        when(it.length) {
                            2 -> values.add(Value(DataType.BYTE, it.toShort(16)))
                            4 -> values.add(Value(DataType.WORD, it.toInt(16)))
                            else -> throw VmExecutionException("invalid value at line $lineNr+1")
                        }
                    }
                    memory[address] = values
                }
            }
        }
    }

    val program: List<Instruction>

    init {
        prog.add(Instruction(Opcode.TERMINATE))
        prog.add(Instruction(Opcode.NOP))
        program = prog
        connect()
    }

    private fun connect() {
        val it1 = program.iterator()
        val it2 = program.iterator()
        it2.next()

        while(it1.hasNext() && it2.hasNext()) {
            val instr = it1.next()
            val nextInstr = it2.next()
            when(instr.opcode) {
                Opcode.TERMINATE -> instr.next = instr          // won't ever execute a next instruction
                Opcode.RETURN -> instr.next = instr             // kinda a special one, in actuality the return instruction is dynamic
                Opcode.JUMP -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support JUMP to memory address")
                    } else {
                        // jump to label
                        val target = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = target
                    }
                }
                Opcode.BCC, Opcode.BCS, Opcode.BEQ, Opcode.BNE, Opcode.BMI, Opcode.BPL -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support branch to memory address")
                    } else {
                        // branch to label
                        val jumpInstr = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = jumpInstr
                        instr.nextAlt = nextInstr
                    }
                }
                Opcode.CALL -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support CALL to memory address")
                    } else {
                        // call label
                        val jumpInstr = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = jumpInstr
                        instr.nextAlt = nextInstr  // instruction to return to
                    }
                }
                else -> instr.next = nextInstr
            }
        }
    }

    fun print(out: PrintStream, embeddedLabels: Boolean=true) {
        out.println("; stackVM program code for '$name'")
        out.println("%memory")
        if(memory.isNotEmpty()) {
            TODO("print out initial memory load")
        }
        out.println("%end_memory")
        out.println("%variables")
        for (variable in variables) {
            val valuestr = variable.value.toString()
            out.println("${variable.key} ${variable.value.type.toString().toLowerCase()} $valuestr")
        }
        out.println("%end_variables")
        out.println("%instructions")
        val labels = this.labels.entries.associateBy({it.value}) {it.key}
        for(instr in this.program) {
            if(!embeddedLabels) {
                val label = labels[instr]
                if (label != null)
                    out.println("$label:")
            } else {
                out.println(instr)
            }
        }
        out.println("%end_instructions")
    }
}


class StackVm(val traceOutputFile: String?) {
    val mem = Memory()
    val evalstack = MyStack<Value>()   // evaluation stack
    val callstack = MyStack<Instruction>()    // subroutine call stack
    var sourceLine = ""     // meta info about current line in source file
        private set
    var P_carry: Boolean = false
        private set
    var P_irqd: Boolean = false
        private set
    var variables = mutableMapOf<String, Value>()     // all variables (set of all vars used by all blocks/subroutines) key = their fully scoped name
        private set
    private var program = listOf<Instruction>()
    private var traceOutput = if(traceOutputFile!=null) PrintStream(File(traceOutputFile), "utf-8") else null
    private lateinit var currentIns: Instruction
    private var canvas: BitmapScreenPanel? = null
    private val rnd = Random()

    fun load(program: Program, canvas: BitmapScreenPanel?) {
        this.program = program.program
        this.canvas = canvas
        variables = program.variables.toMutableMap()
        if(this.variables.contains("A") ||
                variables.contains("X") ||
                variables.contains("Y") ||
                variables.contains("XY") ||
                variables.contains("AX") ||
                variables.contains("AY"))
            throw VmExecutionException("program contains variable(s) for the reserved registers A,X,...")
        // define the 'registers'
        variables["A"] = Value(DataType.BYTE, 0)
        variables["X"] = Value(DataType.BYTE, 0)
        variables["Y"] = Value(DataType.BYTE, 0)
        variables["AX"] = Value(DataType.WORD, 0)
        variables["AY"] = Value(DataType.WORD, 0)
        variables["XY"] = Value(DataType.WORD, 0)

        initMemory(program.memory)
        evalstack.clear()
        callstack.clear()
        P_carry = false
        P_irqd = false
        sourceLine = ""
        currentIns = this.program[0]
    }

    fun step(instructionCount: Int = 10000) {
        // step is invoked every 1/100 sec
        // we execute 10k instructions in one go so we end up doing 1 million vm instructions per second
        val start = System.currentTimeMillis()
        for(i:Int in 1..instructionCount) {
            try {
                currentIns = dispatch(currentIns)

                if (evalstack.size > 128)
                    throw VmExecutionException("too many values on evaluation stack")
                if (callstack.size > 128)
                    throw VmExecutionException("too many nested/recursive calls")
            } catch (bp: VmBreakpointException) {
                currentIns = currentIns.next
                println("breakpoint encountered, source line: $sourceLine")
                throw bp
            } catch (es: EmptyStackException) {
                System.err.println("stack error!  source line: $sourceLine")
                throw es
            } catch (x: RuntimeException) {
                System.err.println("runtime error!  source line: $sourceLine")
                throw x
            }
        }
        val time = System.currentTimeMillis()-start
        if(time > 100) {
            println("WARNING: vm dispatch step took > 100 msec")
        }
    }

    private fun initMemory(memory: Map<Int, List<Value>>) {
        mem.clear()
        for (meminit in memory) {
            var address = meminit.key
            for (value in meminit.value) {
                when(value.type) {
                    DataType.BYTE -> {
                        mem.setByte(address, value.integerValue().toShort())
                        address += 1
                    }
                    DataType.WORD -> {
                        mem.setWord(address, value.integerValue())
                        address += 2
                    }
                    DataType.FLOAT -> {
                        mem.setFloat(address, value.numericValue().toDouble())
                        address += 5
                    }
                    DataType.STR -> {
                        mem.setString(address, value.stringvalue!!)
                        address += value.stringvalue.length+1
                    }
                    else -> throw VmExecutionException("invalid mem datatype ${value.type}")
                }
            }
        }
    }

    private fun dispatch(ins: Instruction) : Instruction {
        traceOutput?.println("\n$ins")
        when (ins.opcode) {
            Opcode.NOP -> {}
            Opcode.PUSH -> evalstack.push(ins.arg)
            Opcode.PUSH_MEM -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.BYTE, mem.getByte(address)))
            }
            Opcode.PUSH_MEM_W -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.WORD, mem.getWord(address)))
            }
            Opcode.PUSH_MEM_F -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.FLOAT, mem.getFloat(address)))
            }
            Opcode.DUP -> evalstack.push(evalstack.peek())
            Opcode.ARRAY -> {
                val amount = ins.arg!!.integerValue()
                if(amount<=0)
                    throw VmExecutionException("array size must be > 0")
                val array = mutableListOf<Int>()
                var arrayDt = DataType.ARRAY
                for (i in 1..amount) {
                    val value = evalstack.pop()
                    if(value.type!=DataType.BYTE && value.type!=DataType.WORD)
                        throw VmExecutionException("array requires values to be all byte/word")
                    if(value.type==DataType.WORD)
                        arrayDt = DataType.ARRAY_W
                    array.add(0, value.integerValue())
                }
                evalstack.push(Value(arrayDt, null, arrayvalue =  array.toIntArray()))
            }
            Opcode.DISCARD -> evalstack.pop()
            Opcode.SWAP -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(top)
                evalstack.push(second)
            }
            Opcode.POP_MEM -> {
                val value = evalstack.pop()
                val address = ins.arg!!.integerValue()
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
            Opcode.FLOORDIV -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.floordiv(top))
            }
            Opcode.REMAINDER -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.remainder(top))
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
                val (result, newCarry) = v.rol(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL2 -> {
                val v = evalstack.pop()
                evalstack.push(v.rol2())
            }
            Opcode.ROR -> {
                val v = evalstack.pop()
                val (result, newCarry) = v.ror(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR2 -> {
                val v = evalstack.pop()
                evalstack.push(v.ror2())
            }
            Opcode.BITAND -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.bitand(top))
            }
            Opcode.BITOR -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.bitor(top))
            }
            Opcode.BITXOR -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(second.bitxor(top))
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
                val callId = ins.arg!!.integerValue().toShort()
                val syscall = Syscall.values().first { it.callNr == callId }
                when (syscall) {
                    Syscall.WRITE_MEMCHR -> {
                        val address = evalstack.pop().integerValue()
                        print(Petscii.decodePetscii(listOf(mem.getByte(address)), true))
                    }
                    Syscall.WRITE_MEMSTR -> {
                        val address = evalstack.pop().integerValue()
                        print(mem.getString(address))
                    }
                    Syscall.WRITE_NUM -> {
                        print(evalstack.pop().numericValue())
                    }
                    Syscall.WRITE_CHAR -> {
                        print(Petscii.decodePetscii(listOf(evalstack.pop().integerValue().toShort()), true))
                    }
                    Syscall.WRITE_STR -> {
                        val value = evalstack.pop()
                        when(value.type){
                            DataType.BYTE, DataType.WORD, DataType.FLOAT -> print(value.numericValue())
                            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> print(value.stringvalue)
                            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> print(value.arrayvalue)
                        }
                    }
                    Syscall.INPUT_STR -> {
                        val maxlen = evalstack.pop().integerValue()
                        val input = readLine()?.substring(0, maxlen)  ?: ""
                        evalstack.push(Value(DataType.STR, null, input))
                    }
                    Syscall.GFX_PIXEL -> {
                        // plot pixel at (x, y, color) from stack
                        val color = evalstack.pop()
                        val (y, x) = evalstack.pop2()
                        canvas?.setPixel(x.integerValue(), y.integerValue(), color.integerValue())
                    }
                    Syscall.GFX_CLEARSCR -> {
                        val color = evalstack.pop()
                        canvas?.clearScreen(color.integerValue())
                    }
                    Syscall.GFX_TEXT -> {
                        val text = evalstack.pop()
                        val color = evalstack.pop()
                        val (y, x) = evalstack.pop2()
                        canvas?.writeText(x.integerValue(), y.integerValue(), text.stringvalue!!, color.integerValue())
                    }
                    Syscall.FUNC_RND -> evalstack.push(Value(DataType.BYTE, rnd.nextInt() and 255))
                    Syscall.FUNC_RNDW -> evalstack.push(Value(DataType.WORD, rnd.nextInt() and 65535))
                    Syscall.FUNC_RNDF -> evalstack.push(Value(DataType.FLOAT, rnd.nextDouble()))
                    Syscall.FUNC_LEN -> {
                        val value = evalstack.pop()
                        when(value.type) {
                            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS ->
                                evalstack.push(Value(DataType.WORD, value.stringvalue!!.length))
                            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX ->
                                evalstack.push(Value(DataType.WORD, value.arrayvalue!!.size))
                            else -> throw VmExecutionException("cannot get length of $value")
                        }
                    }
                    Syscall.FUNC_SIN -> evalstack.push(Value(DataType.FLOAT, sin(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_COS -> evalstack.push(Value(DataType.FLOAT, cos(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_ROUND -> evalstack.push(Value(DataType.WORD, evalstack.pop().numericValue().toDouble().roundToInt()))
                    Syscall.FUNC_ABS -> {
                        val value = evalstack.pop()
                        val absValue=
                            when(value.type) {
                                DataType.BYTE -> Value(DataType.BYTE, value.numericValue())
                                DataType.WORD -> Value(DataType.WORD, value.numericValue())
                                DataType.FLOAT -> Value(DataType.FLOAT, value.numericValue())
                                else -> throw VmExecutionException("cannot get abs of $value")
                            }
                        evalstack.push(absValue)
                    }
                    Syscall.FUNC_ACOS -> evalstack.push(Value(DataType.FLOAT, acos(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_ASIN -> evalstack.push(Value(DataType.FLOAT, asin(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_TAN -> evalstack.push(Value(DataType.FLOAT, tan(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_ATAN -> evalstack.push(Value(DataType.FLOAT, atan(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_LN -> evalstack.push(Value(DataType.FLOAT, ln(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_LOG2 -> evalstack.push(Value(DataType.FLOAT, log2(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_LOG10 -> evalstack.push(Value(DataType.FLOAT, log10(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_SQRT -> evalstack.push(Value(DataType.FLOAT, sqrt(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_RAD -> evalstack.push(Value(DataType.FLOAT, Math.toRadians(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_DEG -> evalstack.push(Value(DataType.FLOAT, Math.toDegrees(evalstack.pop().numericValue().toDouble())))
                    Syscall.FUNC_FLOOR -> {
                        val value = evalstack.pop()
                        val result =
                                when(value.type) {
                                    DataType.BYTE -> Value(DataType.BYTE, value.numericValue())
                                    DataType.WORD -> Value(DataType.WORD, value.numericValue())
                                    DataType.FLOAT -> Value(DataType.WORD, floor(value.numericValue().toDouble()))
                                    else -> throw VmExecutionException("cannot get floor of $value")
                                }
                        evalstack.push(result)
                    }
                    Syscall.FUNC_CEIL -> {
                        val value = evalstack.pop()
                        val result =
                                when(value.type) {
                                    DataType.BYTE -> Value(DataType.BYTE, value.numericValue())
                                    DataType.WORD -> Value(DataType.WORD, value.numericValue())
                                    DataType.FLOAT -> Value(DataType.WORD, ceil(value.numericValue().toDouble()))
                                    else -> throw VmExecutionException("cannot get ceil of $value")
                                }
                        evalstack.push(result)
                    }
                    Syscall.FUNC_MAX -> {
                        val array = evalstack.pop()
                        val dt =
                                when {
                                    array.type==DataType.ARRAY -> DataType.BYTE
                                    array.type==DataType.ARRAY_W -> DataType.WORD
                                    else -> throw VmExecutionException("invalid array datatype $array")
                                }
                        evalstack.push(Value(dt, array.arrayvalue!!.max()))
                    }
                    Syscall.FUNC_MIN -> {
                        val array = evalstack.pop()
                        val dt =
                                when {
                                    array.type==DataType.ARRAY -> DataType.BYTE
                                    array.type==DataType.ARRAY_W -> DataType.WORD
                                    else -> throw VmExecutionException("invalid array datatype $array")
                                }
                        evalstack.push(Value(dt, array.arrayvalue!!.min()))
                    }
                    Syscall.FUNC_AVG -> {
                        val array = evalstack.pop()
                        evalstack.push(Value(DataType.FLOAT, array.arrayvalue!!.average()))
                    }
                    Syscall.FUNC_SUM -> {
                        val array = evalstack.pop()
                        evalstack.push(Value(DataType.WORD, array.arrayvalue!!.sum()))
                    }
                    Syscall.FUNC_ANY -> {
                        val array = evalstack.pop()
                        evalstack.push(Value(DataType.BYTE, if(array.arrayvalue!!.any{ v -> v != 0}) 1 else 0))
                    }
                    Syscall.FUNC_ALL -> {
                        val array = evalstack.pop()
                        evalstack.push(Value(DataType.BYTE, if(array.arrayvalue!!.all{ v -> v != 0}) 1 else 0))
                    }
                    else -> throw VmExecutionException("unimplemented syscall $syscall")
                }
            }

            Opcode.SEC -> P_carry = true
            Opcode.CLC -> P_carry = false
            Opcode.SEI -> P_irqd = true
            Opcode.CLI -> P_irqd = false
            Opcode.TERMINATE -> throw VmTerminationException("terminate instruction")
            Opcode.BREAKPOINT -> throw VmBreakpointException()

            Opcode.INC_MEM -> {
                val addr = ins.arg!!.integerValue()
                val newValue = Value(DataType.BYTE, mem.getByte(addr)).inc()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.INC_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val newValue = Value(DataType.WORD, mem.getWord(addr)).inc()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.DEC_MEM -> {
                val addr = ins.arg!!.integerValue()
                val newValue = Value(DataType.BYTE, mem.getByte(addr)).dec()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.DEC_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val newValue = Value(DataType.WORD, mem.getWord(addr)).dec()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.SHL_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.shl()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHL_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.shl()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.SHR_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.shr()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHR_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.shr()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.ROL_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setByte(addr, newValue.integerValue().toShort())
                P_carry = newCarry
            }
            Opcode.ROL_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setWord(addr, newValue.integerValue())
                P_carry = newCarry
            }
            Opcode.ROR_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setByte(addr, newValue.integerValue().toShort())
                P_carry = newCarry
            }
            Opcode.ROR_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setWord(addr, newValue.integerValue())
                P_carry = newCarry
            }
            Opcode.ROL2_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.rol2()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROL2_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.rol2()
                mem.setWord(addr, newValue.integerValue())
            }
            Opcode.ROR2_MEM -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.BYTE, mem.getByte(addr))
                val newValue = value.ror2()
                mem.setByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROR2_MEM_W -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.WORD, mem.getWord(addr))
                val newValue = value.ror2()
                mem.setWord(addr, newValue.integerValue())
            }

            Opcode.JUMP -> {}   // do nothing; the next instruction is wired up already to the jump target
            Opcode.BCS ->
                return if(P_carry) ins.next else ins.nextAlt!!
            Opcode.BCC ->
                return if(P_carry) ins.nextAlt!! else ins.next
            Opcode.BEQ ->
                return if(evalstack.pop().numericValue().toDouble()==0.0) ins.next else ins.nextAlt!!
            Opcode.BNE ->
                return if(evalstack.pop().numericValue().toDouble()!=0.0) ins.next else ins.nextAlt!!
            Opcode.BMI ->
                return if(evalstack.pop().numericValue().toDouble()<0.0) ins.next else ins.nextAlt!!
            Opcode.BPL -> {
                return if (evalstack.pop().numericValue().toDouble() >= 0.0) ins.next else ins.nextAlt!!
            }
            Opcode.CALL ->
                callstack.push(ins.nextAlt)
            Opcode.RETURN -> {
                if(callstack.empty())
                    throw VmTerminationException("return instruction with empty call stack")
                return callstack.pop()
            }
            Opcode.PUSH_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                evalstack.push(variable)
            }
            Opcode.POP_VAR -> {
                val value = evalstack.pop()
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                if(variable.type!=value.type) throw VmExecutionException("value datatype ${value.type} is not the same as variable datatype ${variable.type}")
                variables[varname] = value
            }
            Opcode.SHL_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.shl()
            }
            Opcode.SHR_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.shr()
            }
            Opcode.ROL_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[varname] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[varname] = newValue
                P_carry = newCarry
            }
            Opcode.ROL2_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.rol2()
            }
            Opcode.ROR2_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.ror2()
            }
            Opcode.INC_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.inc()
            }
            Opcode.DEC_VAR -> {
                val varname = ins.arg!!.stringvalue ?: throw VmExecutionException("${ins.opcode} expects string argument (the variable name)")
                val variable = variables[varname] ?: throw VmExecutionException("unknown variable: $varname")
                variables[varname] = variable.dec()
            }
            Opcode.LSB -> {
                val v = evalstack.pop()
                evalstack.push(v.lsb())
            }
            Opcode.MSB -> {
                val v = evalstack.pop()
                evalstack.push(v.msb())
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
            Opcode.LESS -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second < top) 1 else 0))
            }
            Opcode.GREATER -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second > top) 1 else 0))
            }
            Opcode.LESSEQ -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second <= top) 1 else 0))
            }
            Opcode.GREATEREQ -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second >= top) 1 else 0))
            }
            Opcode.EQUAL -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second == top) 1 else 0))
            }
            Opcode.NOTEQUAL -> {
                val (top, second) = evalstack.pop2()
                evalstack.push(Value(DataType.BYTE, if(second != top) 1 else 0))
            }
            Opcode.B2WORD -> {
                val byte = evalstack.pop()
                if(byte.type==DataType.BYTE) {
                    evalstack.push(Value(DataType.WORD, byte.integerValue()))
                } else {
                    throw VmExecutionException("attempt to make a word from a non-byte value $byte")
                }
            }
            Opcode.MSB2WORD -> {
                val byte = evalstack.pop()
                if(byte.type==DataType.BYTE) {
                    evalstack.push(Value(DataType.WORD, byte.integerValue() * 256))
                } else {
                    throw VmExecutionException("attempt to make a word from a non-byte value $byte")
                }
            }
            Opcode.B2FLOAT -> {
                val byte = evalstack.pop()
                if(byte.type==DataType.BYTE) {
                    evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
                } else {
                    throw VmExecutionException("attempt to make a float from a non-byte value $byte")
                }
            }
            Opcode.W2FLOAT -> {
                val byte = evalstack.pop()
                if(byte.type==DataType.WORD) {
                    evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
                } else {
                    throw VmExecutionException("attempt to make a float from a non-word value $byte")
                }
            }
            Opcode.LINE -> {
                sourceLine = ins.arg!!.stringvalue!!
            }
            else -> throw VmExecutionException("unimplemented opcode: ${ins.opcode}")
        }

        if(traceOutput!=null) {
            traceOutput?.println(" evalstack (size=${evalstack.size}):")
            evalstack.printTop(4, traceOutput!!)
        }

        return ins.next
    }
}
