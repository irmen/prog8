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
    PUSH,           // push constant value (any type)
    PUSH_MEM,       // push byte value from memory to stack
    PUSH_MEM_W,     // push word value from memory to stack
    PUSH_MEM_F,     // push float value from memory to stack
    PUSH_VAR,       // push a variable
    DUP,            // push topmost value once again

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
    NOT,

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
    SWAP,
    SEC,        // set carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    CLC,        // clear carry status flag  NOTE: is mostly fake, carry flag is not affected by any numeric operations
    SEI,        // set irq-disable status flag
    CLI,        // clear irq-disable status flag
    NOP,        // do nothing
    BREAKPOINT, // breakpoint
    TERMINATE,  // end the program
    LINE        // track source file line number
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
        val opcodesWithVarArgument = setOf(
                Opcode.INC_VAR, Opcode.DEC_VAR,
                Opcode.SHR_VAR, Opcode.SHL_VAR, Opcode.ROL_VAR, Opcode.ROR_VAR,
                Opcode.ROL2_VAR, Opcode.ROR2_VAR, Opcode.POP_VAR, Opcode.PUSH_VAR)
        val result =
                when {
                    opcode==Opcode.LINE -> "_line  $callLabel"
                    opcode==Opcode.SYSCALL -> {
                        val syscall = Syscall.values().find { it.callNr==arg!!.numericValue() }
                        "syscall  $syscall"
                    }
                    opcodesWithVarArgument.contains(opcode) -> {
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

        if(variables.contains("A") ||
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
        irqStartInstruction = program.labels["irq.irq"]
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
                        TODO("mem init with string")
                        //mem.setString(address, value.stringvalue!!)
                        //address += value.stringvalue.length+1
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
                            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                                TODO("print stringvalue")
                            }
                            DataType.ARRAY, DataType.ARRAY_W -> {
                                TODO("print array value")
                            }
                            DataType.MATRIX -> {
                                TODO("print matrix value")
                            }
                        }
                    }
                    Syscall.INPUT_STR -> {
                        val maxlen = evalstack.pop().integerValue()
                        val input = readLine()?.substring(0, maxlen)  ?: ""
                        TODO("input_str opcode should put the string in a given heap location (overwriting old string)")
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
                        val textPtr = evalstack.pop()
                        val color = evalstack.pop()
                        val (y, x) = evalstack.pop2()
                        val text = heap.get(textPtr.heapId)
                        canvas?.writeText(x.integerValue(), y.integerValue(), text.str!!, color.integerValue())
                    }
                    Syscall.FUNC_RND -> evalstack.push(Value(DataType.BYTE, rnd.nextInt() and 255))
                    Syscall.FUNC_RNDW -> evalstack.push(Value(DataType.WORD, rnd.nextInt() and 65535))
                    Syscall.FUNC_RNDF -> evalstack.push(Value(DataType.FLOAT, rnd.nextDouble()))
                    Syscall.FUNC_LEN -> {
                        val value = evalstack.pop()
                        TODO("func_len")
//                        when(value.type) {
//                            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS ->
//                                evalstack.push(Value(DataType.WORD, value.stringvalue!!.length))
//                            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX ->
//                                evalstack.push(Value(DataType.WORD, value.arrayvalue!!.size))
//                            else -> throw VmExecutionException("cannot get length of $value")
//                        }
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
                        val iterable = evalstack.pop()
                        val dt =
                                when(iterable.type) {
                                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
                                    DataType.ARRAY, DataType.MATRIX -> DataType.BYTE
                                    DataType.ARRAY_W -> DataType.WORD
                                    else -> throw VmExecutionException("uniterable value $iterable")
                                }
                        TODO("func_max on array/matrix/string")
                    }
                    Syscall.FUNC_MIN -> {
                        val iterable = evalstack.pop()
                        val dt =
                                when(iterable.type) {
                                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
                                    DataType.ARRAY, DataType.MATRIX -> DataType.BYTE
                                    DataType.ARRAY_W -> DataType.WORD
                                    else -> throw VmExecutionException("uniterable value $iterable")
                                }
                        TODO("func_min on array/matrix/string")
                    }
                    Syscall.FUNC_AVG -> {
                        val iterable = evalstack.pop()
                        TODO("func_avg")
//                        evalstack.push(Value(DataType.FLOAT, array.arrayvalue!!.average()))
                    }
                    Syscall.FUNC_SUM -> {
                        val iterable = evalstack.pop()
                        TODO("func_sum")
//                        evalstack.push(Value(DataType.WORD, array.arrayvalue!!.sum()))
                    }
                    Syscall.FUNC_ANY -> {
                        val iterable = evalstack.pop()
                        TODO("func_any")
//                        evalstack.push(Value(DataType.BYTE, if(array.arrayvalue!!.any{ v -> v != 0}) 1 else 0))
                    }
                    Syscall.FUNC_ALL -> {
                        val iterable = evalstack.pop()
                        TODO("func_all")
//                        evalstack.push(Value(DataType.BYTE, if(array.arrayvalue!!.all{ v -> v != 0}) 1 else 0))
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
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                evalstack.push(variable)
            }
            Opcode.POP_VAR -> {
                val value = evalstack.pop()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type!=value.type)
                    throw VmExecutionException("value datatype ${value.type} is not the same as variable datatype ${variable.type} for var ${ins.callLabel}")
                variables[ins.callLabel!!] = value
            }
            Opcode.SHL_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.shl()
            }
            Opcode.SHR_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.shr()
            }
            Opcode.ROL_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROL2_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.rol2()
            }
            Opcode.ROR2_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.ror2()
            }
            Opcode.INC_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.DEC_VAR -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                variables[ins.callLabel!!] = variable.dec()
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
            Opcode.NOT -> {
                val value = evalstack.pop()
                evalstack.push(value.not())
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
                sourceLine = ins.callLabel!!
            }
            else -> throw VmExecutionException("unimplemented opcode: ${ins.opcode}")
        }

        if(traceOutput!=null) {
            traceOutput?.println(" evalstack (size=${evalstack.size}):")
            evalstack.printTop(4, traceOutput!!)
        }

        return ins.next
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
