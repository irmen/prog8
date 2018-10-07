package prog8.stackvm

import prog8.ast.DataType
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.Petscii
import java.io.File
import java.io.PrintStream
import java.util.*
import kotlin.math.*

enum class Opcode {

    // pushing values on the (evaluation) stack
    PUSH,           // push byte value
    PUSH_W,         // push word value   (or 'address' of string / array / matrix)
    PUSH_F,         // push float value
    PUSH_MEM,       // push byte value from memory to stack
    PUSH_MEM_W,     // push word value from memory to stack
    PUSH_MEM_F,     // push float value from memory to stack
    PUSH_VAR,       // push byte variable
    PUSH_VAR_W,     // push word variable
    PUSH_VAR_F,     // push float variable

    // popping values off the (evaluation) stack, possibly storing them in another location
    DISCARD,        // discard top byte value
    DISCARD_W,      // discard top word value
    DISCARD_F,      // discard top float value
    POP_MEM,        // pop byte value into destination memory address
    POP_MEM_W,      // pop word value into destination memory address
    POP_MEM_F,      // pop float value into destination memory address
    POP_VAR,        // pop byte value into variable
    POP_VAR_W,      // pop word value into variable
    POP_VAR_F,      // pop float value into variable

    // numeric arithmetic
    ADD_B,
    ADD_W,
    ADD_F,
    SUB_B,
    SUB_W,
    SUB_F,
    MUL_B,
    MUL_W,
    MUL_F,
    DIV_B,
    DIV_W,
    DIV_F,
    FLOORDIV_B,
    FLOORDIV_W,
    FLOORDIV_F,
    REMAINDER_B,
    REMAINDER_W,
    REMAINDER_F,
    POW_B,
    POW_W,
    POW_F,
    NEG_B,
    NEG_W,
    NEG_F,

    // bit shifts and bitwise arithmetic
    SHL,
    SHL_W,
    SHL_MEM,
    SHL_MEM_W,
    SHL_VAR,
    SHL_VAR_W,
    SHR,
    SHR_W,
    SHR_MEM,
    SHR_MEM_W,
    SHR_VAR,
    SHR_VAR_W,
    ROL,
    ROL_W,
    ROL_MEM,
    ROL_MEM_W,
    ROL_VAR,
    ROL_VAR_W,
    ROR,
    ROR_W,
    ROR_MEM,
    ROR_MEM_W,
    ROR_VAR,
    ROR_VAR_W,
    ROL2,
    ROL2_W,
    ROL2_MEM,
    ROL2_MEM_W,
    ROL2_VAR,
    ROL2_VAR_W,
    ROR2,
    ROR2_W,
    ROR2_MEM,
    ROR2_MEM_W,
    ROR2_VAR,
    ROR2_VAR_W,
    BITAND,
    BITAND_W,
    BITOR,
    BITOR_W,
    BITXOR,
    BITXOR_W,
    INV,
    INV_W,

    // numeric type conversions
    LSB,
    MSB,
    B2WORD,         // convert a byte into a word where it is the lower eight bits $00xx
    MSB2WORD,       // convert a byte into a word where it is the upper eight bits $xx00
    B2FLOAT,        // convert byte into floating point
    W2FLOAT,        // convert word into floating point

    // logical operations
    AND,
    AND_W,
    OR,
    OR_W,
    XOR,
    XOR_W,
    NOT,
    NOT_W,

    // increment, decrement
    INC,
    INC_W,
    INC_F,
    INC_VAR,
    INC_VAR_W,
    INC_VAR_F,
    DEC,
    DEC_W,
    DEC_F,
    DEC_VAR,
    DEC_VAR_W,
    DEC_VAR_F,

    // comparisons
    LESS,
    LESS_W,
    LESS_F,
    GREATER,
    GREATER_W,
    GREATER_F,
    LESSEQ,
    LESSEQ_W,
    LESSEQ_F,
    GREATEREQ,
    GREATEREQ_W,
    GREATEREQ_F,
    EQUAL,
    EQUAL_W,
    EQUAL_F,
    NOTEQUAL,
    NOTEQUAL_W,
    NOTEQUAL_F,

    // array access
    READ_INDEXED_VAR,
    READ_INDEXED_VAR_W,
    READ_INDEXED_VAR_F,
    WRITE_INDEXED_VAR,
    WRITE_INDEXED_VAR_W,
    WRITE_INDEXED_VAR_F,

    // branching
    JUMP,
    BCS,
    BCC,
    BZ,          // branch if value on top of stack is zero
    BNZ,         // branch if value on top of stack is not zero
    BNEG,        // branch if value on top of stack < 0
    BPOS,        // branch if value on top of stack >= 0
    // BVS,      // status flag V (overflow) not implemented
    // BVC,      // status flag V (overflow) not implemented

    // subroutine calling
    CALL,
    RETURN,
    SYSCALL,

    // misc
    SEC,        // set carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    CLC,        // clear carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    SEI,        // set irq-disable status flag
    CLI,        // clear irq-disable status flag
    NOP,        // do nothing
    BREAKPOINT, // breakpoint
    TERMINATE,  // end the program
    LINE        // track source file line number
}

val opcodesWithVarArgument = setOf(
        Opcode.INC_VAR, Opcode.INC_VAR_W, Opcode.DEC_VAR, Opcode.DEC_VAR_W,
        Opcode.SHR_VAR, Opcode.SHR_VAR_W, Opcode.SHL_VAR, Opcode.SHL_VAR_W,
        Opcode.ROL_VAR, Opcode.ROL_VAR_W, Opcode.ROR_VAR, Opcode.ROR_VAR_W,
        Opcode.ROL2_VAR, Opcode.ROL2_VAR_W, Opcode.ROR2_VAR, Opcode.ROR2_VAR_W,
        Opcode.POP_VAR, Opcode.POP_VAR_W, Opcode.POP_VAR_F,
        Opcode.PUSH_VAR, Opcode.PUSH_VAR_W, Opcode.PUSH_VAR_F,
        Opcode.READ_INDEXED_VAR, Opcode.READ_INDEXED_VAR_W, Opcode.READ_INDEXED_VAR_F,
        Opcode.WRITE_INDEXED_VAR, Opcode.WRITE_INDEXED_VAR_W, Opcode.WRITE_INDEXED_VAR_F
        )

enum class Syscall(val callNr: Short) {
    WRITE_MEMCHR(10),           // print a single char from the memory address popped from stack
    WRITE_MEMSTR(11),           // print a 0-terminated petscii string from the memory address popped from stack
    WRITE_NUM(12),              // pop from the evaluation stack and print it as a number
    WRITE_CHAR(13),             // pop from the evaluation stack and print it as a single petscii character
    WRITE_STR(14),              // pop from the evaluation stack and print it as a string
    INPUT_STR(15),              // user input a string onto the stack, with max length (truncated) given by value on stack
    GFX_PIXEL(16),              // plot a pixel at (x,y,color) pushed on stack in that order
    GFX_CLEARSCR(17),           // clear the screen with color pushed on stack
    GFX_TEXT(18),               // write text on screen at cursor position (x,y,color,text) pushed on stack in that order  (pixel pos= x*8, y*8)
    GFX_LINE(19),               // draw line on screen at (x1,y1,x2,y2,color) pushed on stack in that order

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
    FUNC_STR2BYTE(92),
    FUNC_STR2WORD(93),
    FUNC_STR2FLOAT(94)

    // note: not all builtin functions of the Prog8 language are present as functions:
    // some of them are straight opcodes (such as MSB, LSB, LSL, LSR, ROL, ROR, ROL2, ROR2, and FLT)!
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
                    opcode==Opcode.LINE -> "_line  $callLabel"
                    opcode==Opcode.SYSCALL -> {
                        val syscall = Syscall.values().find { it.callNr==arg!!.numericValue() }
                        "syscall  $syscall"
                    }
                    opcode in opcodesWithVarArgument -> {
                        // opcodes that manipulate a variable
                        "${opcode.toString().toLowerCase()}  $callLabel"
                    }
                    callLabel==null -> "${opcode.toString().toLowerCase()}  $argStr"
                    else -> "${opcode.toString().toLowerCase()}  $callLabel  $argStr"
                }
                .trimEnd()

        return "    $result"
    }
}

class LabelInstr(val name: String) : Instruction(opcode = Opcode.NOP) {
    override fun toString(): String {
        return "\n$name:"
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


class StackVm(private var traceOutputFile: String?) {
    val mem = Memory()
    var P_carry: Boolean = false
        private set
    var P_irqd: Boolean = false
        private set
    var variables = mutableMapOf<String, Value>()     // all variables (set of all vars used by all blocks/subroutines) key = their fully scoped name
        private set
    var evalstack = MyStack<Value>()
        private set
    var callstack = MyStack<Instruction>()
        private set
    private var program = listOf<Instruction>()
    private var heap = HeapValues()
    private var traceOutput = if(traceOutputFile!=null) PrintStream(File(traceOutputFile), "utf-8") else null
    private var canvas: BitmapScreenPanel? = null
    private val rnd = Random()
    private val bootTime = System.currentTimeMillis()
    private lateinit var currentIns: Instruction
    private var irqStartInstruction: Instruction? = null        // set to first instr of irq routine, if any
    var sourceLine: String = ""
        private set

    fun load(program: Program, canvas: BitmapScreenPanel?) {
        this.program = program.program
        this.heap = program.heap
        this.canvas = canvas
        variables.clear()
        for(variable in program.variables.flatMap { e->e.value.entries })
            variables[variable.key] = variable.value

        if("A" in variables || "X" in variables || "Y" in variables ||
                "XY" in variables || "AX" in variables ||"AY" in variables)
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
        irqStartInstruction = program.labels["irq.irq"]
    }

    fun step(instructionCount: Int = 5000) {
        // step is invoked every 1/100 sec
        // we execute 5k instructions in one go so we end up doing 0.5 million vm instructions per second
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
                        TODO("mem init with string")
                        //mem.setString(address, value.stringvalue!!)
                        //address += value.stringvalue.length+1
                    }
                    else -> throw VmExecutionException("invalid mem datatype ${value.type}")
                }
            }
        }
    }

    private fun checkDt(value: Value?, expected: DataType) {
        if(value==null)
            throw VmExecutionException("expected value")
        if(value.type!=expected)
            throw VmExecutionException("expected $expected value, found ${value.type}")
    }

    private fun checkDt(value: Value?, expected: Set<DataType>) {
        if(value==null)
            throw VmExecutionException("expected value")
        if(value.type !in expected)
            throw VmExecutionException("incompatible type found ${value.type}")
    }

    private fun dispatch(ins: Instruction) : Instruction {
        traceOutput?.println("\n$ins")
        when (ins.opcode) {
            Opcode.NOP -> {}
            Opcode.PUSH -> {
                checkDt(ins.arg, DataType.BYTE)
                evalstack.push(ins.arg)
            }
            Opcode.PUSH_W -> {
                checkDt(ins.arg, setOf(DataType.WORD, DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS, DataType.MATRIX))
                evalstack.push(ins.arg)
            }
            Opcode.PUSH_F -> {
                checkDt(ins.arg, DataType.FLOAT)
                evalstack.push(ins.arg)
            }
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
            Opcode.DISCARD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE)
            }
            Opcode.DISCARD_W -> {
                val value = evalstack.pop()
                checkDt(value, DataType.WORD)
            }
            Opcode.DISCARD_F -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
            }
            Opcode.POP_MEM -> {
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE)
                val address = ins.arg!!.integerValue()
                mem.setByte(address, value.integerValue().toShort())
            }
            Opcode.POP_MEM_W -> {
                val value = evalstack.pop()
                checkDt(value, DataType.WORD)
                val address = ins.arg!!.integerValue()
                mem.setWord(address, value.integerValue())
            }
            Opcode.POP_MEM_F -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val address = ins.arg!!.integerValue()
                mem.setFloat(address, value.numericValue().toDouble())
            }
            Opcode.ADD_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.add(top))
            }
            Opcode.ADD_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.add(top))
            }
            Opcode.ADD_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.add(top))
            }
            Opcode.SUB_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.sub(top))
            }
            Opcode.SUB_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.sub(top))
            }
            Opcode.SUB_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.sub(top))
            }
            Opcode.MUL_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.mul(top))
            }
            Opcode.MUL_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.mul(top))
            }
            Opcode.MUL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.mul(top))
            }
            Opcode.DIV_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.div(top))
            }
            Opcode.DIV_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.div(top))
            }
            Opcode.DIV_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.div(top))
            }
            Opcode.FLOORDIV_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.floordiv(top))
            }
            Opcode.FLOORDIV_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.floordiv(top))
            }
            Opcode.FLOORDIV_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.floordiv(top))
            }
            Opcode.REMAINDER_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.remainder(top))
            }
            Opcode.REMAINDER_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.remainder(top))
            }
            Opcode.REMAINDER_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.remainder(top))
            }
            Opcode.POW_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.pow(top))
            }
            Opcode.POW_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.pow(top))
            }
            Opcode.POW_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(second.pow(top))
            }
            Opcode.NEG_B -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.neg())
            }
            Opcode.NEG_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.neg())
            }
            Opcode.NEG_F -> {
                val v = evalstack.pop()
                checkDt(v, DataType.FLOAT)
                evalstack.push(v.neg())
            }
            Opcode.SHL -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.shl())
            }
            Opcode.SHL_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.shl())
            }
            Opcode.SHR -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.shr())
            }
            Opcode.SHR_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.shr())
            }
            Opcode.ROL -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                val (result, newCarry) = v.rol(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                val (result, newCarry) = v.rol(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL2 -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.rol2())
            }
            Opcode.ROL2_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.rol2())
            }
            Opcode.ROR -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                val (result, newCarry) = v.ror(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                val (result, newCarry) = v.ror(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR2 -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.ror2())
            }
            Opcode.ROR2_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.ror2())
            }
            Opcode.BITAND -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.bitand(top))
            }
            Opcode.BITAND_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.bitand(top))
            }
            Opcode.BITOR -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.bitor(top))
            }
            Opcode.BITOR_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.bitor(top))
            }
            Opcode.BITXOR -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.bitxor(top))
            }
            Opcode.BITXOR_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.bitxor(top))
            }
            Opcode.INV -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.inv())
            }
            Opcode.INV_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.inv())
            }
            Opcode.INC -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.inc())
            }
            Opcode.INC_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.inc())
            }
            Opcode.INC_F -> {
                val v = evalstack.pop()
                checkDt(v, DataType.FLOAT)
                evalstack.push(v.inc())
            }
            Opcode.DEC -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                evalstack.push(v.dec())
            }
            Opcode.DEC_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.dec())
            }
            Opcode.DEC_F -> {
                val v = evalstack.pop()
                checkDt(v, DataType.FLOAT)
                evalstack.push(v.dec())
            }
            Opcode.SYSCALL -> dispatchSyscall(ins)
            Opcode.SEC -> P_carry = true
            Opcode.CLC -> P_carry = false
            Opcode.SEI -> P_irqd = true
            Opcode.CLI -> P_irqd = false
            Opcode.TERMINATE -> throw VmTerminationException("terminate instruction")
            Opcode.BREAKPOINT -> throw VmBreakpointException()

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
            Opcode.BZ ->
                return if(evalstack.pop().numericValue().toDouble()==0.0) ins.next else ins.nextAlt!!
            Opcode.BNZ ->
                return if(evalstack.pop().numericValue().toDouble()!=0.0) ins.next else ins.nextAlt!!
            Opcode.BNEG ->
                return if(evalstack.pop().numericValue().toDouble()<0.0) ins.next else ins.nextAlt!!
            Opcode.BPOS -> {
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
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, DataType.BYTE)
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_W -> {
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, setOf(DataType.WORD, DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS, DataType.MATRIX))
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_F -> {
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, DataType.FLOAT)
                evalstack.push(value)
            }
            Opcode.POP_VAR -> {
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = value
            }
            Opcode.POP_VAR_W -> {
                val value = evalstack.pop()
                checkDt(value, setOf(DataType.WORD, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, setOf(DataType.WORD, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS))
                variables[ins.callLabel!!] = value
            }
            Opcode.POP_VAR_F -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.FLOAT)
                variables[ins.callLabel!!] = value
            }
            Opcode.SHL_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.shl()
            }
            Opcode.SHL_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.shl()
            }
            Opcode.SHR_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.shr()
            }
            Opcode.SHR_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.shr()
            }
            Opcode.ROL_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROL_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROL2_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.rol2()
            }
            Opcode.ROL2_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.rol2()
            }
            Opcode.ROR2_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.ror2()
            }
            Opcode.ROR2_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.ror2()
            }
            Opcode.INC_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.INC_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.INC_VAR_F -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.FLOAT)
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.DEC_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.dec()
            }
            Opcode.DEC_VAR_W -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.WORD)
                variables[ins.callLabel!!] = variable.dec()
            }
            Opcode.DEC_VAR_F -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.FLOAT)
                variables[ins.callLabel!!] = variable.dec()
            }
            Opcode.LSB -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.lsb())
            }
            Opcode.MSB -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                evalstack.push(v.msb())
            }
            Opcode.AND -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.and(top))
            }
            Opcode.AND_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.and(top))
            }
            Opcode.OR -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.or(top))
            }
            Opcode.OR_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.or(top))
            }
            Opcode.XOR -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(second.xor(top))
            }
            Opcode.XOR_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(second.xor(top))
            }
            Opcode.NOT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE)
                evalstack.push(value.not())
            }
            Opcode.NOT_W -> {
                val value = evalstack.pop()
                checkDt(value, DataType.WORD)
                evalstack.push(value.not())
            }
            Opcode.LESS -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second < top) 1 else 0))
            }
            Opcode.LESS_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second < top) 1 else 0))
            }
            Opcode.LESS_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second < top) 1 else 0))
            }
            Opcode.GREATER -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second > top) 1 else 0))
            }
            Opcode.GREATER_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second > top) 1 else 0))
            }
            Opcode.GREATER_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second > top) 1 else 0))
            }
            Opcode.LESSEQ -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second <= top) 1 else 0))
            }
            Opcode.LESSEQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second <= top) 1 else 0))
            }
            Opcode.LESSEQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second <= top) 1 else 0))
            }
            Opcode.GREATEREQ -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second >= top) 1 else 0))
            }
            Opcode.EQUAL -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second == top) 1 else 0))
            }
            Opcode.EQUAL_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second == top) 1 else 0))
            }
            Opcode.EQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second == top) 1 else 0))
            }
            Opcode.NOTEQUAL -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.BYTE, if(second != top) 1 else 0))
            }
            Opcode.NOTEQUAL_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.BYTE, if(second != top) 1 else 0))
            }
            Opcode.NOTEQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.BYTE, if(second != top) 1 else 0))
            }
            Opcode.B2WORD -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.WORD, byte.integerValue()))
            }
            Opcode.MSB2WORD -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.WORD, byte.integerValue() * 256))
            }
            Opcode.B2FLOAT -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
            }
            Opcode.W2FLOAT -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.WORD)
                evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
            }
            Opcode.LINE -> {
                sourceLine = ins.callLabel!!
            }
            Opcode.READ_INDEXED_VAR -> {
                // put the byte value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and get the byte value from that memory location
                    evalstack.push(Value(DataType.BYTE, mem.getByte(variable.integerValue())))
                } else {
                    // get indexed byte element from the array
                    val array = heap.get(variable.heapId)
                    when(array.type) {
                        DataType.ARRAY, DataType.MATRIX -> evalstack.push(Value(DataType.BYTE, array.array!![index]))
                        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> evalstack.push(Value(DataType.BYTE, Petscii.encodePetscii(array.str!![index].toString(), true)[0]))
                        else -> throw VmExecutionException("not a proper array/matrix/string variable with byte elements")
                    }
                }
            }
            Opcode.READ_INDEXED_VAR_W -> {
                // put the word value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and get the word value from that memory location
                    evalstack.push(Value(DataType.WORD, mem.getWord(variable.integerValue())))
                } else {
                    // get indexed word element from the array
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_W)
                        throw VmExecutionException("not a proper array var with word elements")
                    evalstack.push(Value(DataType.WORD, array.array!![index]))
                }
            }
            Opcode.READ_INDEXED_VAR_F -> {
                // put the f;pat value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and get the float value from that memory location
                    evalstack.push(Value(DataType.WORD, mem.getFloat(variable.integerValue())))
                } else {
                    // get indexed float element from the array
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_F)
                        throw VmExecutionException("not a proper array var with float elements")
                    evalstack.push(Value(DataType.FLOAT, array.doubleArray!![index]))
                }
            }
            Opcode.WRITE_INDEXED_VAR -> {
                // store byte value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and write the byte value to that memory location
                    mem.setByte(variable.integerValue(), value.integerValue().toShort())
                } else {
                    // set indexed byte element in the array
                    val array = heap.get(variable.heapId)
                    when(array.type) {
                        DataType.ARRAY, DataType.MATRIX -> {
                            array.array!![index] = value.integerValue()
                        }
                        DataType.STR,
                        DataType.STR_P,
                        DataType.STR_S,
                        DataType.STR_PS -> {
                            val chars = array.str!!.toCharArray()
                            chars[index] = Petscii.decodePetscii(listOf(value.integerValue().toShort()), true)[0]
                            heap.update(variable.heapId, chars.joinToString(""))
                        }
                        else -> throw VmExecutionException("not a proper array/matrix/string var with byte elements")
                    }
                }
            }
            Opcode.WRITE_INDEXED_VAR_W -> {
                // store word value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.WORD)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and write the word value to that memory location
                    mem.setWord(variable.integerValue(), value.integerValue())
                } else {
                    // set indexed word element in the array
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_W)
                        throw VmExecutionException("not a proper array var with word elements")
                    array.array!![index] = value.integerValue()
                }
            }
            Opcode.WRITE_INDEXED_VAR_F -> {
                // store float value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.WORD) {
                    // assume the variable is a pointer (address) and write the float value to that memory location
                    mem.setFloat(variable.integerValue(), value.numericValue().toDouble())
                } else {
                    // set indexed float element in the array
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_F)
                        throw VmExecutionException("not a proper array var with float elements")
                    array.doubleArray!![index] = value.numericValue().toDouble()
                }
            }
            else -> throw VmExecutionException("unimplemented opcode: ${ins.opcode}")
        }

        if(traceOutput!=null) {
            traceOutput?.println(" evalstack (size=${evalstack.size}):")
            evalstack.printTop(4, traceOutput!!)
        }

        return ins.next
    }

    private fun dispatchSyscall(ins: Instruction) {
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
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> print(heap.get(value.heapId).str)
                    DataType.ARRAY, DataType.ARRAY_W -> print(heap.get(value.heapId).array!!.toList())
                    DataType.MATRIX -> print(heap.get(value.heapId).array!!.toList())
                    DataType.ARRAY_F -> print(heap.get(value.heapId).doubleArray!!.toList())
                }
            }
            Syscall.INPUT_STR -> {
                val variable = evalstack.pop()
                val value = heap.get(variable.heapId)
                val maxlen = value.str!!.length
                val input = readLine() ?: ""
                heap.update(variable.heapId, input.padEnd(maxlen, '\u0000').substring(0, maxlen))
            }
            Syscall.GFX_PIXEL -> {
                // plot pixel at (x, y, color) from stack
                val color = evalstack.pop()
                val (y, x) = evalstack.pop2()
                canvas?.setPixel(x.integerValue(), y.integerValue(), color.integerValue())
            }
            Syscall.GFX_LINE -> {
                // draw line at (x1, y1, x2, y2, color) from stack
                val color = evalstack.pop()
                val (y2, x2) = evalstack.pop2()
                val (y1, x1) = evalstack.pop2()
                canvas?.drawLine(x1.integerValue(), y1.integerValue(), x2.integerValue(), y2.integerValue(), color.integerValue())
            }
            Syscall.GFX_CLEARSCR -> {
                val color = evalstack.pop()
                canvas?.clearScreen(color.integerValue())
            }
            Syscall.GFX_TEXT -> {
                val textPtr = evalstack.pop()
                val color = evalstack.pop()
                val (cy, cx) = evalstack.pop2()
                val text = heap.get(textPtr.heapId)
                canvas?.writeText(cx.integerValue(), cy.integerValue(), text.str!!, color.integerValue())
            }
            Syscall.FUNC_RND -> evalstack.push(Value(DataType.BYTE, rnd.nextInt() and 255))
            Syscall.FUNC_RNDW -> evalstack.push(Value(DataType.WORD, rnd.nextInt() and 65535))
            Syscall.FUNC_RNDF -> evalstack.push(Value(DataType.FLOAT, rnd.nextDouble()))
            Syscall.FUNC_LEN -> throw VmExecutionException("len() should have been const-folded away everywhere (it's not possible on non-const values)")
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
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                val resultDt = when(iterable.type) {
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.BYTE
                    DataType.ARRAY, DataType.MATRIX -> DataType.BYTE
                    DataType.ARRAY_W -> DataType.WORD
                    DataType.ARRAY_F -> DataType.FLOAT
                    DataType.BYTE, DataType.WORD, DataType.FLOAT -> throw VmExecutionException("uniterable value $iterable")
                }
                if(value.str!=null) {
                    val result = Petscii.encodePetscii(value.str.max().toString(), true)[0]
                    evalstack.push(Value(DataType.BYTE, result))
                } else {
                    val result = value.array!!.max() ?: 0
                    evalstack.push(Value(resultDt, result))
                }
            }
            Syscall.FUNC_MIN -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                val resultDt = when(iterable.type) {
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.BYTE
                    DataType.ARRAY, DataType.MATRIX -> DataType.BYTE
                    DataType.ARRAY_W -> DataType.WORD
                    DataType.ARRAY_F -> DataType.FLOAT
                    DataType.BYTE, DataType.WORD, DataType.FLOAT -> throw VmExecutionException("uniterable value $iterable")
                }
                if(value.str!=null) {
                    val result = Petscii.encodePetscii(value.str.min().toString(), true)[0]
                    evalstack.push(Value(DataType.BYTE, result))
                } else {
                    val result = value.array!!.min() ?: 0
                    evalstack.push(Value(resultDt, result))
                }
            }
            Syscall.FUNC_AVG -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if(value.str!=null)
                    evalstack.push(Value(DataType.FLOAT, Petscii.encodePetscii(value.str, true).average()))
                else
                    evalstack.push(Value(DataType.FLOAT, value.array!!.average()))
            }
            Syscall.FUNC_SUM -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if(value.str!=null)
                    evalstack.push(Value(DataType.WORD, Petscii.encodePetscii(value.str, true).sum()))
                else
                    evalstack.push(Value(DataType.WORD, value.array!!.sum()))
            }
            Syscall.FUNC_ANY -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if (value.str != null)
                    evalstack.push(Value(DataType.BYTE, if (Petscii.encodePetscii(value.str, true).any { c -> c != 0.toShort() }) 1 else 0))
                else
                    evalstack.push(Value(DataType.BYTE, if (value.array!!.any{v->v!=0}) 1 else 0))
            }
            Syscall.FUNC_ALL -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if (value.str != null)
                    evalstack.push(Value(DataType.BYTE, if (Petscii.encodePetscii(value.str, true).all { c -> c != 0.toShort() }) 1 else 0))
                else
                    evalstack.push(Value(DataType.BYTE, if (value.array!!.all{v->v!=0}) 1 else 0))
            }
            Syscall.FUNC_STR2BYTE -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.BYTE, y.toShort()))
            }
            Syscall.FUNC_STR2WORD -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.BYTE, y.toInt()))
            }
            Syscall.FUNC_STR2FLOAT -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.BYTE, y.toDouble()))
            }
        }
    }

    fun irq(timestamp: Long) {
        // 60hz IRQ handling
        if(P_irqd)
            return      // interrupt is disabled

        P_irqd=true
        swapIrqExecutionContexts(true)

        val jiffies = min((timestamp-bootTime)*60/1000, 24*3600*60-1)
        // update the C-64 60hz jiffy clock in the ZP addresses:
        mem.setByte(0x00a0, (jiffies ushr 16).toShort())
        mem.setByte(0x00a1, (jiffies ushr 8 and 255).toShort())
        mem.setByte(0x00a2, (jiffies and 255).toShort())

        if(irqStartInstruction!=null) {
            try {
                // execute the irq routine
                this.step(Int.MAX_VALUE)
            } catch (vmt: VmTerminationException) {
                // irq routine ended
            }
        }

        swapIrqExecutionContexts(false)
        P_irqd=false
    }

    private var irqStoredEvalStack = MyStack<Value>()
    private var irqStoredCallStack = MyStack<Instruction>()
    private var irqStoredCarry = false
    private var irqStoredTraceOutputFile: String? = null
    private var irqStoredMainInstruction: Instruction = Instruction(Opcode.TERMINATE)

    private fun swapIrqExecutionContexts(startingIrq: Boolean) {
        if(startingIrq) {
            irqStoredMainInstruction = currentIns
            irqStoredCallStack = callstack
            irqStoredEvalStack = evalstack
            irqStoredCarry = P_carry
            irqStoredTraceOutputFile = traceOutputFile

            currentIns = irqStartInstruction ?: Instruction(Opcode.RETURN)
            callstack = MyStack()
            evalstack = MyStack()
            P_carry = false
            traceOutputFile = null
        } else {
            if(evalstack.isNotEmpty())
                throw VmExecutionException("irq: eval stack is not empty at exit from irq program")
            if(callstack.isNotEmpty())
                throw VmExecutionException("irq: call stack is not empty at exit from irq program")
            currentIns = irqStoredMainInstruction
            callstack = irqStoredCallStack
            evalstack = irqStoredEvalStack
            P_carry = irqStoredCarry
            traceOutputFile = irqStoredTraceOutputFile
        }
    }
}
