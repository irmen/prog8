package prog8.stackvm

import prog8.ast.*
import prog8.astvm.BitmapScreenPanel
import prog8.astvm.Memory
import prog8.compiler.RuntimeValue
import prog8.compiler.HeapValues
import prog8.compiler.IntegerOrAddressOf
import prog8.compiler.intermediate.Instruction
import prog8.compiler.intermediate.Opcode
import prog8.compiler.target.c64.Petscii
import prog8.compiler.toHex
import java.io.File
import java.io.PrintStream
import java.util.*
import kotlin.math.*


enum class Syscall(val callNr: Short) {
    VM_WRITE_MEMCHR(10),           // print a single char from the memory address popped from stack
    VM_WRITE_MEMSTR(11),           // print a 0-terminated petscii string from the memory address popped from stack
    VM_WRITE_NUM(12),              // pop from the evaluation stack and print it as a number
    VM_WRITE_CHAR(13),             // pop from the evaluation stack and print it as a single petscii character
    VM_WRITE_STR(14),              // pop from the evaluation stack and print it as a string
    VM_INPUT_STR(15),              // user input a string onto the stack, with max length (truncated) given by value on stack
    VM_GFX_PIXEL(16),              // plot a pixel at (x,y,color) pushed on stack in that order
    VM_GFX_CLEARSCR(17),           // clear the screen with color pushed on stack
    VM_GFX_TEXT(18),               // write text on screen at cursor position (x,y,color,text) pushed on stack in that order  (pixel pos= x*8, y*8)
    VM_GFX_LINE(19),               // draw line on screen at (x1,y1,x2,y2,color) pushed on stack in that order

    FUNC_SIN(60),
    FUNC_SIN8(61),
    FUNC_SIN8U(62),
    FUNC_SIN16(63),
    FUNC_SIN16U(64),
    FUNC_COS(65),
    FUNC_COS8(66),
    FUNC_COS8U(67),
    FUNC_COS16(68),
    FUNC_COS16U(69),
    FUNC_ABS(70),
    FUNC_TAN(71),
    FUNC_ATAN(72),
    FUNC_LN(73),
    FUNC_LOG2(74),
    FUNC_SQRT16(75),
    FUNC_SQRT(76),
    FUNC_RAD(77),
    FUNC_DEG(78),
    FUNC_ROUND(79),
    FUNC_FLOOR(80),
    FUNC_CEIL(81),
    FUNC_RND(89),                // push a random byte on the stack
    FUNC_RNDW(90),               // push a random word on the stack
    FUNC_RNDF(91),               // push a random float on the stack (between 0.0 and 1.0)
    FUNC_LEN_STR(105),
    FUNC_LEN_STRS(106),
    FUNC_STRLEN(107),
    FUNC_ANY_B(109),
    FUNC_ANY_W(110),
    FUNC_ANY_F(111),
    FUNC_ALL_B(112),
    FUNC_ALL_W(113),
    FUNC_ALL_F(114),
    FUNC_MAX_UB(115),
    FUNC_MAX_B(116),
    FUNC_MAX_UW(117),
    FUNC_MAX_W(118),
    FUNC_MAX_F(119),
    FUNC_MIN_UB(120),
    FUNC_MIN_B(121),
    FUNC_MIN_UW(122),
    FUNC_MIN_W(123),
    FUNC_MIN_F(124),
    FUNC_SUM_UB(130),
    FUNC_SUM_B(131),
    FUNC_SUM_UW(132),
    FUNC_SUM_W(133),
    FUNC_SUM_F(134),
    FUNC_MEMCOPY(138),
    FUNC_MEMSET(139),
    FUNC_MEMSETW(140),
    FUNC_READ_FLAGS(141),

    // note: not all builtin functions of the Prog8 language are present as functions:
    // some of them are straight opcodes (such as MSB, LSB, LSL, LSR, ROL_BYTE, ROR, ROL2, ROR2, and FLT)!

    // vm intercepts of system routines:
    SYSCALLSTUB(200),
    SYSASM_c64scr_plot(201),
    SYSASM_c64scr_print(202),
    SYSASM_c64scr_print_ub0(203),
    SYSASM_c64scr_print_ub(204),
    SYSASM_c64scr_print_b(205),
    SYSASM_c64scr_print_ubhex(206),
    SYSASM_c64scr_print_ubbin(207),
    SYSASM_c64scr_print_uwbin(208),
    SYSASM_c64scr_print_uwhex(209),
    SYSASM_c64scr_print_uw0(210),
    SYSASM_c64scr_print_uw(211),
    SYSASM_c64scr_print_w(212),
    SYSASM_c64scr_setcc(213),
    SYSASM_c64flt_print_f(214),
}


val syscallNames = enumValues<Syscall>().map { it.name }.toSet()

val syscallsForStackVm = setOf(
        Syscall.VM_WRITE_MEMCHR,
        Syscall.VM_WRITE_MEMSTR,
        Syscall.VM_WRITE_NUM,
        Syscall.VM_WRITE_CHAR,
        Syscall.VM_WRITE_STR,
        Syscall.VM_INPUT_STR,
        Syscall.VM_GFX_PIXEL,
        Syscall.VM_GFX_CLEARSCR,
        Syscall.VM_GFX_TEXT,
        Syscall.VM_GFX_LINE
)

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
    val mem = Memory(::memread, ::memwrite)
    var P_carry: Boolean = false
        private set
    var P_zero: Boolean = true
        private set
    var P_negative: Boolean = false
        private set
    var P_irqd: Boolean = false
        private set
    var variables = mutableMapOf<String, RuntimeValue>()     // all variables (set of all vars used by all blocks/subroutines) key = their fully scoped name
        private set
    var memoryPointers = mutableMapOf<String, Pair<Int, DataType>>()        // all named pointers
        private set
    var evalstack = MyStack<RuntimeValue>()
        private set
    var callstack = MyStack<Int>()
        private set
    private var program = listOf<Instruction>()
    private var labels = emptyMap<String, Int>()
    private var heap = HeapValues()
    private var traceOutput = if(traceOutputFile!=null) PrintStream(File(traceOutputFile), "utf-8") else null
    private var canvas: BitmapScreenPanel? = null
    private val rnd = Random()
    private val bootTime = System.currentTimeMillis()
    private var rtcOffset = bootTime
    private var currentInstructionPtr: Int = -1
    private var irqStartInstructionPtr: Int = -1
    private var registerSaveX: RuntimeValue = RuntimeValue(DataType.UBYTE, 0)
    var sourceLine: String = ""
        private set

    fun memread(address: Int, value: Short): Short {
        //println("MEM READ  $address  -> $value")
        return value
    }

    fun memwrite(address: Int, value: Short): Short {
        if(address==0xa0 || address==0xa1 || address==0xa2) {
            // a write to the jiffy clock, update the clock offset for the irq
            val time_hi = if(address==0xa0) value else mem.getUByte_DMA(0xa0)
            val time_mid = if(address==0xa1) value else mem.getUByte_DMA(0xa1)
            val time_lo = if(address==0xa2) value else mem.getUByte_DMA(0xa2)
            val jiffies = (time_hi.toInt() shl 16) + (time_mid.toInt() shl 8) + time_lo
            rtcOffset = bootTime - (jiffies*1000/60)
        }
        return value
    }

    fun load(program: Program, canvas: BitmapScreenPanel?) {
        this.program = program.program + Instruction(Opcode.RETURN)  // append a RETURN for use in the IRQ handler
        this.labels = program.labels
        this.heap = program.heap
        this.canvas = canvas
        canvas?.requestFocusInWindow()
        variables = program.variables.toMutableMap()
        memoryPointers = program.memoryPointers.toMutableMap()

        if("A" in variables || "X" in variables || "Y" in variables)
            throw VmExecutionException("program contains variable(s) for the reserved registers A/X/Y")
        // define the 'registers'
        variables["A"] = RuntimeValue(DataType.UBYTE, 0)
        variables["X"] = RuntimeValue(DataType.UBYTE, 0)
        variables["Y"] = RuntimeValue(DataType.UBYTE, 0)

        initMemory(program.memory)
        evalstack.clear()
        callstack.clear()
        P_carry = false
        P_irqd = false
        sourceLine = ""
        currentInstructionPtr = 0
        irqStartInstructionPtr = labels["irq.irq"] ?: -1     // set to first instr of irq routine, if any

        initBlockVars()
    }

    private fun initBlockVars() {
        // initialize the global variables in each block.
        // this is done by calling the special init subroutine of each block that has one.
        val initVarsSubs = labels.filter { it.key.endsWith("."+ initvarsSubName) }
        for(init in initVarsSubs) {
            currentInstructionPtr = init.value
            try {
                step(Int.MAX_VALUE)
            } catch(x: VmTerminationException) {
                // init subroutine finished
            }
        }
        currentInstructionPtr = 0
    }

    fun step(instructionCount: Int = 5000) {
        // step is invoked every 1/100 sec
        // we execute 5k instructions in one go so we end up doing 0.5 million vm instructions per second
        val start = System.currentTimeMillis()
        for(i:Int in 1..instructionCount) {
            try {
                dispatch()
                if (evalstack.size > 128)
                    throw VmExecutionException("too many values on evaluation stack")
                if (callstack.size > 128)
                    throw VmExecutionException("too many nested/recursive calls")
            } catch (bp: VmBreakpointException) {
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

    private fun initMemory(memory: Map<Int, List<RuntimeValue>>) {
        mem.clear()

        for (meminit in memory) {
            var address = meminit.key
            for (value in meminit.value) {
                when(value.type) {
                    DataType.UBYTE -> {
                        mem.setUByte(address, value.integerValue().toShort())
                        address += 1
                    }
                    DataType.BYTE -> {
                        mem.setSByte(address, value.integerValue().toShort())
                        address += 1
                    }
                    DataType.UWORD -> {
                        mem.setUWord(address, value.integerValue())
                        address += 2
                    }
                    DataType.WORD -> {
                        mem.setSWord(address, value.integerValue())
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

        // observe the jiffyclock
        mem.observe(0xa0, 0xa1, 0xa2)
    }

    private fun checkDt(value: RuntimeValue?, vararg expected: DataType) {
        if (value == null)
            throw VmExecutionException("expected value")
        if (value.type !in expected)
            throw VmExecutionException("encountered value of wrong type ${value.type} expected ${expected.joinToString("/")}")
    }


    private fun getVar(name: String): RuntimeValue {
        val result = variables[name]
        if(result!=null)
            return result
        if(name in memoryPointers) {
            throw VmExecutionException("variable is memory-mapped: $name = ${memoryPointers[name]}")
        }
        throw VmExecutionException("unknown variable: $name")
    }

    private fun dispatch() {
        val ins = program[currentInstructionPtr]
        traceOutput?.println("\n$ins")
        when (ins.opcode) {
            Opcode.NOP -> {}
            Opcode.START_PROCDEF, Opcode.END_PROCDEF -> {}
            Opcode.PUSH_BYTE -> {
                checkDt(ins.arg, DataType.UBYTE, DataType.BYTE)
                evalstack.push(ins.arg)
            }
            Opcode.PUSH_WORD -> {
                checkDt(ins.arg, *(setOf(DataType.UWORD, DataType.WORD) + IterableDatatypes).toTypedArray())
                evalstack.push(ins.arg)
            }
            Opcode.PUSH_FLOAT -> {
                checkDt(ins.arg, DataType.FLOAT)
                evalstack.push(ins.arg)
            }
            Opcode.PUSH_MEM_UB -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(RuntimeValue(DataType.UBYTE, mem.getUByte(address)))
            }
            Opcode.PUSH_MEM_B -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(RuntimeValue(DataType.BYTE, mem.getSByte(address)))
            }
            Opcode.PUSH_MEM_UW -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(RuntimeValue(DataType.UWORD, mem.getUWord(address)))
            }
            Opcode.PUSH_MEM_W -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(RuntimeValue(DataType.WORD, mem.getSWord(address)))
            }
            Opcode.PUSH_MEM_FLOAT -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(RuntimeValue(DataType.FLOAT, mem.getFloat(address)))
            }
            Opcode.PUSH_MEMREAD -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                TODO("push_memread from $address")
            }
            Opcode.DISCARD_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE)
            }
            Opcode.DISCARD_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD)
            }
            Opcode.DISCARD_FLOAT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
            }
            Opcode.POP_MEM_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.BYTE, DataType.UBYTE)
                val address = ins.arg!!.integerValue()
                if(value.type==DataType.BYTE)
                    mem.setSByte(address, value.integerValue().toShort())
                else
                    mem.setUByte(address, value.integerValue().toShort())
                setFlags(value)
            }
            Opcode.POP_MEM_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.WORD, DataType.UWORD)
                val address = ins.arg!!.integerValue()
                if(value.type==DataType.WORD)
                    mem.setSWord(address, value.integerValue())
                else
                    mem.setUWord(address, value.integerValue())
                setFlags(value)
            }
            Opcode.POP_MEM_FLOAT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val address = ins.arg!!.integerValue()
                mem.setFloat(address, value.numericValue().toDouble())
                setFlags(value)
            }
            Opcode.POP_MEMWRITE -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE)
                TODO("pop_memwrite $value to $address")
                setFlags(value)
            }
            Opcode.POP_INC_MEMORY -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                TODO("pop_inc_memory $address + flags")
            }
            Opcode.POP_DEC_MEMORY -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                TODO("pop_dec_memory $address + flags")
            }
            Opcode.ADD_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value = second.add(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ADD_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.add(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ADD_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val value=second.add(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ADD_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val value=second.add(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ADD_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val value=second.add(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SUB_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.sub(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SUB_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.sub(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SUB_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val value=second.sub(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SUB_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val value=second.sub(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.CMP_UB -> {
                val value = evalstack.pop()
                val other = ins.arg!!
                checkDt(value, DataType.UBYTE)
                checkDt(other, DataType.UBYTE)
                val comparison = value.compareTo(other)
                P_zero = comparison==0
                P_negative = comparison<0
                P_carry = comparison>=0
            }
            Opcode.CMP_UW -> {
                val value = evalstack.pop()
                val other = ins.arg!!
                checkDt(value, DataType.UWORD)
                checkDt(other, DataType.UWORD)
                val comparison = value.compareTo(other)
                P_zero = comparison==0
                P_negative = comparison<0
                P_carry = comparison>=0
            }
            Opcode.CMP_B -> {
                val value = evalstack.pop()
                val other = ins.arg!!
                checkDt(value, DataType.BYTE)
                checkDt(other, DataType.BYTE)
                val comparison = value.compareTo(other)
                P_zero = comparison==0
                P_negative = comparison<0
                P_carry = comparison>=0
            }
            Opcode.CMP_W -> {
                val value = evalstack.pop()
                val other = ins.arg!!
                checkDt(value, DataType.WORD)
                checkDt(other, DataType.WORD)
                val result = value.sub(other)
                val comparison = value.compareTo(other)
                P_zero = comparison==0
                P_negative = comparison<0
                P_carry = comparison>=0
            }
            Opcode.SUB_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val value=second.sub(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MUL_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.mul(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MUL_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.mul(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MUL_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val value=second.mul(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MUL_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val value=second.mul(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MUL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val value=second.mul(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.IDIV_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.div(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.IDIV_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.div(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.IDIV_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val value=second.div(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.IDIV_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val value=second.div(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.DIV_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val value=second.div(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.REMAINDER_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.remainder(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.REMAINDER_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.remainder(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.POW_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val value=second.pow(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.NEG_B -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                val value=v.neg()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.NEG_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                val value=v.neg()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.NEG_F -> {
                val v = evalstack.pop()
                checkDt(v, DataType.FLOAT)
                val value=v.neg()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ABS_B -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                val value=v.abs()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ABS_W -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                val value=v.abs()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ABS_F -> {
                val v = evalstack.pop()
                checkDt(v, DataType.FLOAT)
                val value=v.abs()
                evalstack.push(value)
                setFlags(value)
            }

            Opcode.SHIFTEDL_BYTE, Opcode.SHL_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE, DataType.BYTE)
                P_carry = (v.integerValue() and 0x80)!=0
                val value=v.shl()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SHIFTEDL_WORD, Opcode.SHL_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD, DataType.WORD)
                P_carry = (v.integerValue() and 0x8000)!=0
                val value=v.shl()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SHIFTEDR_UBYTE, Opcode.SHR_UBYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                P_carry = (v.integerValue() and 0x01)!=0
                val value=v.shr()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SHIFTEDR_SBYTE, Opcode.SHR_SBYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.BYTE)
                P_carry = (v.integerValue() and 0x01)!=0
                val value=v.shr()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SHIFTEDR_UWORD, Opcode.SHR_UWORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                P_carry = (v.integerValue() and 0x01)!=0
                val value=v.shr()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SHIFTEDR_SWORD, Opcode.SHR_SWORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.WORD)
                P_carry = (v.integerValue() and 0x01)!=0
                val value=v.shr()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ROL_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val (result, newCarry) = v.rol(P_carry)
                evalstack.push(result)
                setFlags(result)
                this.P_carry = newCarry
            }
            Opcode.ROL_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val (result, newCarry) = v.rol(P_carry)
                evalstack.push(result)
                setFlags(result)
                this.P_carry = newCarry
            }
            Opcode.ROL2_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val value=v.rol2()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ROL2_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val value=v.rol2()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ROR_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val (result, newCarry) = v.ror(P_carry)
                evalstack.push(result)
                setFlags(result)
                this.P_carry = newCarry
            }
            Opcode.ROR_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val (result, newCarry) = v.ror(P_carry)
                evalstack.push(result)
                setFlags(result)
                this.P_carry = newCarry
            }
            Opcode.ROR2_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val value=v.ror2()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.ROR2_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val value=v.ror2()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITAND_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value = second.bitand(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITAND_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value = second.bitand(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value = second.bitor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value = second.bitor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITXOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value = second.bitxor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.BITXOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value = second.bitxor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.INV_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val value = v.inv()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.INV_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val value = v.inv()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.SYSCALL -> dispatchSyscall(ins)
            Opcode.SEC -> P_carry = true
            Opcode.CLC -> P_carry = false
            Opcode.SEI -> P_irqd = true
            Opcode.CLI -> P_irqd = false
            Opcode.TERMINATE -> throw VmTerminationException("terminate instruction")
            Opcode.BREAKPOINT -> throw VmBreakpointException()
            Opcode.LINE -> {
                sourceLine = ins.callLabel!!
            }

            Opcode.SHL_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.shl()
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
            }
            Opcode.SHL_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.shl()
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
            }
            Opcode.SHR_MEM_UBYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.shr()
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
            }
            Opcode.SHR_MEM_SBYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.BYTE, mem.getSByte(addr))
                val newValue = value.shr()
                mem.setSByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
            }
            Opcode.SHR_MEM_UWORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.shr()
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
            }
            Opcode.SHR_MEM_SWORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.WORD, mem.getSWord(addr))
                val newValue = value.shr()
                mem.setSWord(addr, newValue.integerValue())
                setFlags(newValue)
            }
            Opcode.ROL_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
                P_carry = newCarry
            }
            Opcode.ROL_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
                P_carry = newCarry
            }
            Opcode.ROR_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
                P_carry = newCarry
            }
            Opcode.ROR_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
                P_carry = newCarry
            }
            Opcode.ROL2_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.rol2()
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
            }
            Opcode.ROL2_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.rol2()
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
            }
            Opcode.ROR2_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.ror2()
                mem.setUByte(addr, newValue.integerValue().toShort())
                setFlags(newValue)
            }
            Opcode.ROR2_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = RuntimeValue(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.ror2()
                mem.setUWord(addr, newValue.integerValue())
                setFlags(newValue)
            }
            Opcode.JUMP -> {
                currentInstructionPtr = determineBranchInstr(ins)
                return
            }
            Opcode.BCS -> {
                if (P_carry) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.BCC -> {
                if (P_carry) currentInstructionPtr++
                else currentInstructionPtr = determineBranchInstr(ins)
                return
            }
            Opcode.BZ -> {
                if (P_zero) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.BNZ -> {
                if (P_zero) currentInstructionPtr++
                else currentInstructionPtr = determineBranchInstr(ins)
                return
            }
            Opcode.BNEG -> {
                if (P_negative) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.BPOS -> {
                if (P_negative) currentInstructionPtr++
                else currentInstructionPtr = determineBranchInstr(ins)
                return
            }
            Opcode.BVS, Opcode.BVC -> throw VmExecutionException("stackVM doesn't support the 'overflow' cpu flag")
            Opcode.JZ -> {
                val value = evalstack.pop().integerValue() and 255
                if (value==0) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.JNZ -> {
                val value = evalstack.pop().integerValue() and 255
                if (value!=0) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.JZW -> {
                val value = evalstack.pop().integerValue()
                if (value==0) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.JNZW -> {
                val value = evalstack.pop().integerValue()
                if (value!=0) currentInstructionPtr = determineBranchInstr(ins)
                else currentInstructionPtr++
                return
            }
            Opcode.CALL -> {
                callstack.push(currentInstructionPtr + 1)
                currentInstructionPtr = determineBranchInstr(ins)
                return
            }
            Opcode.RETURN -> {
                if(callstack.empty())
                    throw VmTerminationException("return instruction with empty call stack")
                currentInstructionPtr = callstack.pop()
                return
            }
            Opcode.PUSH_VAR_BYTE -> {
                val value = getVar(ins.callLabel!!)
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_WORD -> {
                val value = getVar(ins.callLabel!!)
                checkDt(value, *(setOf(DataType.UWORD, DataType.WORD) + IterableDatatypes).toTypedArray())
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_FLOAT -> {
                val value = getVar(ins.callLabel!!)
                checkDt(value, DataType.FLOAT)
                evalstack.push(value)
            }
            Opcode.PUSH_REGAX_WORD -> {
                val a=variables.getValue("A").integerValue()
                val x=variables.getValue("X").integerValue()
                evalstack.push(RuntimeValue(DataType.UWORD, x * 256 + a))
            }
            Opcode.PUSH_REGAY_WORD -> {
                val a=variables.getValue("A").integerValue()
                val y=variables.getValue("Y").integerValue()
                evalstack.push(RuntimeValue(DataType.UWORD, y * 256 + a))
            }
            Opcode.PUSH_REGXY_WORD -> {
                val x=variables.getValue("X").integerValue()
                val y=variables.getValue("Y").integerValue()
                evalstack.push(RuntimeValue(DataType.UWORD, y * 256 + x))
            }
            Opcode.POP_REGAX_WORD -> {
                val value=evalstack.pop().integerValue()
                val valueA: RuntimeValue
                val valueX: RuntimeValue
                if(value>=0) {
                    valueA = RuntimeValue(DataType.UBYTE, value and 255)
                    valueX = RuntimeValue(DataType.UBYTE, value shr 8)
                } else {
                    val value2c = 65536+value
                    valueA = RuntimeValue(DataType.UBYTE, value2c and 255)
                    valueX = RuntimeValue(DataType.UBYTE, value2c shr 8)
                }
                variables["A"] = valueA
                variables["X"] = valueX
                setFlags(valueA.bitor(valueX))
            }
            Opcode.POP_REGAY_WORD -> {
                val value=evalstack.pop().integerValue()
                val valueA: RuntimeValue
                val valueY: RuntimeValue
                if(value>=0) {
                    valueA = RuntimeValue(DataType.UBYTE, value and 255)
                    valueY = RuntimeValue(DataType.UBYTE, value shr 8)
                } else {
                    val value2c = 65536+value
                    valueA = RuntimeValue(DataType.UBYTE, value2c and 255)
                    valueY = RuntimeValue(DataType.UBYTE, value2c shr 8)
                }
                variables["A"] = valueA
                variables["Y"] = valueY
                setFlags(valueA.bitor(valueY))
            }
            Opcode.POP_REGXY_WORD -> {
                val value=evalstack.pop().integerValue()
                val valueX: RuntimeValue
                val valueY: RuntimeValue
                if(value>=0) {
                    valueX = RuntimeValue(DataType.UBYTE, value and 255)
                    valueY = RuntimeValue(DataType.UBYTE, value shr 8)
                } else {
                    val value2c = 65536+value
                    valueX = RuntimeValue(DataType.UBYTE, value2c and 255)
                    valueY = RuntimeValue(DataType.UBYTE, value2c shr 8)
                }
                variables["X"] = valueX
                variables["Y"] = valueY
                setFlags(valueX.bitor(valueY))
            }
            Opcode.POP_VAR_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE, DataType.BYTE)
                if(value.type!=variable.type) {
                    if(ins.callLabel !in Register.values().map { it.name }) {
                        throw VmExecutionException("datatype mismatch")
                    }
                }
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.POP_VAR_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD, DataType.WORD, DataType.STR, DataType.STR_S)
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD, DataType.WORD, DataType.STR, DataType.STR_S)
                if(value.type!=variable.type)
                    throw VmExecutionException("datatype mismatch")
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.POP_VAR_FLOAT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.FLOAT)
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHL_VAR_BYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.shl()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHL_VAR_WORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.shl()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHR_VAR_UBYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.shr()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHR_VAR_SBYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.BYTE)
                val value = variable.shr()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHR_VAR_UWORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.shr()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.SHR_VAR_SWORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.WORD)
                val value = variable.shr()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.ROL_VAR_BYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel] =newValue
                P_carry = newCarry
                setFlags(newValue)
            }
            Opcode.ROL_VAR_WORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel] =newValue
                P_carry = newCarry
                setFlags(newValue)
            }
            Opcode.ROR_VAR_BYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel] =newValue
                P_carry = newCarry
                setFlags(newValue)
            }
            Opcode.ROR_VAR_WORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel] =newValue
                P_carry = newCarry
                setFlags(newValue)
            }
            Opcode.ROL2_VAR_BYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.rol2()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.ROL2_VAR_WORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.rol2()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.ROR2_VAR_BYTE -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.ror2()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.ROR2_VAR_WORD -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.ror2()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_VAR_UB -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.inc()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_VAR_B -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.BYTE)
                val value = variable.inc()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_VAR_UW -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.inc()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_VAR_W -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.WORD)
                val value = variable.inc()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_VAR_F -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.FLOAT)
                val value = variable.inc()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.DEC_VAR_UB -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UBYTE)
                val value = variable.dec()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.DEC_VAR_B -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.BYTE)
                val value = variable.dec()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.DEC_VAR_UW -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.UWORD)
                val value = variable.dec()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.DEC_VAR_W -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.WORD)
                val value = variable.dec()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.DEC_VAR_F -> {
                val variable = getVar(ins.callLabel!!)
                checkDt(variable, DataType.FLOAT)
                val value = variable.dec()
                variables[ins.callLabel] = value
                setFlags(value)
            }
            Opcode.INC_INDEXED_VAR_B,Opcode.INC_INDEXED_VAR_UB,Opcode.INC_INDEXED_VAR_W,Opcode.INC_INDEXED_VAR_UW,Opcode.INC_INDEXED_VAR_FLOAT -> {
                val index = evalstack.pop().integerValue()
                val variable = getVar(ins.callLabel!!)
                val array = heap.get(variable.heapId!!)
                TODO("INC_INDEXED_VAR + flags")
            }
            Opcode.DEC_INDEXED_VAR_B,Opcode.DEC_INDEXED_VAR_UB,Opcode.DEC_INDEXED_VAR_W,Opcode.DEC_INDEXED_VAR_UW,Opcode.DEC_INDEXED_VAR_FLOAT -> {
                val index = evalstack.pop().integerValue()
                val variable = getVar(ins.callLabel!!)
                val array = heap.get(variable.heapId!!)
                TODO("DEC_INDEXED_VAR + flags")
            }
            Opcode.INC_MEMORY -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                TODO("inc_memory $address + flags")
            }
            Opcode.DEC_MEMORY -> {
                val address = evalstack.pop()
                checkDt(address, DataType.UWORD)
                TODO("dec_memory $address + flags")
            }
            Opcode.MSB -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD, DataType.WORD)
                val value=v.msb()
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.MKWORD -> {
                val msb = evalstack.pop()
                val lsb = evalstack.pop()
                checkDt(lsb, DataType.UBYTE)
                checkDt(msb, DataType.UBYTE)
                val value = RuntimeValue(DataType.UWORD, (msb.integerValue() shl 8) or lsb.integerValue())
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.AND_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.and(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.AND_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.and(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.OR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.or(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.OR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.or(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.XOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val value=second.xor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.XOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val value=second.xor(top)
                evalstack.push(value)
                setFlags(value)
            }
            Opcode.NOT_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE)
                val result=value.not()
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.NOT_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD)
                val result=value.not()
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESS_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second < top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESS_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second < top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESS_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val result = RuntimeValue(DataType.UBYTE, if (second < top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESS_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val result = RuntimeValue(DataType.UBYTE, if (second < top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESS_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result = RuntimeValue(DataType.UBYTE, if (second < top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATER_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second > top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATER_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second > top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATER_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val result = RuntimeValue(DataType.UBYTE, if (second > top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATER_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val result = RuntimeValue(DataType.UBYTE, if (second > top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATER_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result = RuntimeValue(DataType.UBYTE, if (second > top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESSEQ_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val result= RuntimeValue(DataType.UBYTE, if (second <= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESSEQ_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second <= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESSEQ_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val result= RuntimeValue(DataType.UBYTE, if (second <= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESSEQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val result = RuntimeValue(DataType.UBYTE, if (second <= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.LESSEQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result = RuntimeValue(DataType.UBYTE, if (second <= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATEREQ_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                val result= RuntimeValue(DataType.UBYTE, if (second >= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATEREQ_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                val result= RuntimeValue(DataType.UBYTE, if (second >= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATEREQ_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                val result = RuntimeValue(DataType.UBYTE, if (second >= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATEREQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                val result= RuntimeValue(DataType.UBYTE, if (second >= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.GREATEREQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result = RuntimeValue(DataType.UBYTE, if (second >= top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.EQUAL_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE, DataType.UBYTE)
                checkDt(second, DataType.BYTE, DataType.UBYTE)
                val result = RuntimeValue(DataType.UBYTE, if (second == top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.EQUAL_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD, DataType.UWORD)
                checkDt(second, DataType.WORD, DataType.UWORD)
                val result = RuntimeValue(DataType.UBYTE, if (second == top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.EQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result= RuntimeValue(DataType.UBYTE, if (second == top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.NOTEQUAL_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE, DataType.UBYTE)
                checkDt(second, DataType.BYTE, DataType.UBYTE)
                val result= RuntimeValue(DataType.UBYTE, if (second != top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.NOTEQUAL_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD, DataType.UWORD)
                checkDt(second, DataType.UWORD, DataType.UWORD)
                val result= RuntimeValue(DataType.UBYTE, if (second != top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.NOTEQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                val result= RuntimeValue(DataType.UBYTE, if (second != top) 1 else 0)
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.READ_INDEXED_VAR_BYTE -> {
                // put the byte value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val result =
                        if(ins.callLabel in memoryPointers) {
                            val variable = memoryPointers.getValue(ins.callLabel!!)
                            val address = variable.first + index
                            when(variable.second) {
                                DataType.ARRAY_UB -> RuntimeValue(DataType.UBYTE, mem.getUByte(address))
                                DataType.ARRAY_B -> RuntimeValue(DataType.BYTE, mem.getSByte(address))
                                else -> throw VmExecutionException("not a proper array/string variable with byte elements")
                            }
                        } else {
                            val variable = getVar(ins.callLabel!!)
                            if (variable.type == DataType.UWORD) {
                                // assume the variable is a pointer (address) and get the ubyte value from that memory location
                                RuntimeValue(DataType.UBYTE, mem.getUByte(variable.integerValue()))
                            } else {
                                // get indexed byte element from the arraysize
                                val array = heap.get(variable.heapId!!)
                                when (array.type) {
                                    DataType.ARRAY_UB -> RuntimeValue(DataType.UBYTE, array.array!![index].integer!!)
                                    DataType.ARRAY_B -> RuntimeValue(DataType.BYTE, array.array!![index].integer!!)
                                    DataType.STR, DataType.STR_S -> RuntimeValue(DataType.UBYTE, Petscii.encodePetscii(array.str!![index].toString(), true)[0])
                                    else -> throw VmExecutionException("not a proper array/string variable with byte elements")
                                }
                            }
                        }
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.READ_INDEXED_VAR_WORD -> {
                // put the word value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val result=
                        if(ins.callLabel in memoryPointers) {
                            val variable = memoryPointers.getValue(ins.callLabel!!)
                            val address = variable.first + index*2
                            when(variable.second) {
                                DataType.ARRAY_UW -> RuntimeValue(DataType.UWORD, mem.getUWord(address))
                                DataType.ARRAY_W -> RuntimeValue(DataType.WORD, mem.getSWord(address))
                                else -> throw VmExecutionException("not a proper arraysize var with word elements")
                            }
                        } else {
                            // normal variable
                            val variable = getVar(ins.callLabel!!)
                            if(variable.type==DataType.UWORD) {
                                // assume the variable is a pointer (address) and get the word value from that memory location
                                RuntimeValue(DataType.UWORD, mem.getUWord(variable.integerValue()))
                            } else {
                                // get indexed word element from the arraysize
                                val array = heap.get(variable.heapId!!)
                                when(array.type){
                                    DataType.ARRAY_UW -> {
                                        val value = array.array!![index]
                                        when {
                                            value.integer!=null -> RuntimeValue(DataType.UWORD, value.integer)
                                            value.addressOf!=null -> {
                                                val heapId = variables.getValue(value.addressOf.scopedname!!).heapId!!
                                                if(heapId<0)
                                                    throw VmExecutionException("expected variable on heap")
                                                evalstack.push(RuntimeValue(DataType.UWORD, heapId))       // push the "address" of the string or array variable (this is taken care of properly in the assembly code generator)
                                            }
                                            else -> throw VmExecutionException("strange array value")
                                        }
                                    }
                                    DataType.ARRAY_W -> RuntimeValue(DataType.WORD, array.array!![index].integer!!)
                                    else -> throw VmExecutionException("not a proper arraysize var with word elements")
                                }
                            }
                        }
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.READ_INDEXED_VAR_FLOAT -> {
                // put the float value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val result =
                        if(ins.callLabel in memoryPointers) {
                            val variable = memoryPointers.getValue(ins.callLabel!!)
                            val address = variable.first + index*5
                            if(variable.second==DataType.ARRAY_F)
                                RuntimeValue(DataType.FLOAT, mem.getFloat(address))
                            else
                                throw VmExecutionException("not a proper arraysize var with float elements")
                        } else {
                            val variable = getVar(ins.callLabel!!)
                            if (variable.type == DataType.UWORD) {
                                // assume the variable is a pointer (address) and get the float value from that memory location
                                RuntimeValue(DataType.UWORD, mem.getFloat(variable.integerValue()))
                            } else {
                                // get indexed float element from the arraysize
                                val array = heap.get(variable.heapId!!)
                                if (array.type != DataType.ARRAY_F)
                                    throw VmExecutionException("not a proper arraysize var with float elements")
                                RuntimeValue(DataType.FLOAT, array.doubleArray!![index])
                            }
                        }
                evalstack.push(result)
                setFlags(result)
            }
            Opcode.WRITE_INDEXED_VAR_BYTE -> {
                // store byte value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                val varname = ins.callLabel!!
                val memloc = memoryPointers[varname]
                if(memloc!=null) {
                    // variable is the name of a pointer, write the byte value to that memory location
                    if(value.type==DataType.UBYTE) {
                        if(memloc.second!=DataType.ARRAY_UB)
                            throw VmExecutionException("invalid memory pointer type $memloc")
                        mem.setUByte(memloc.first, value.integerValue().toShort())
                    }
                    else {
                        if(memloc.second!=DataType.ARRAY_B)
                            throw VmExecutionException("invalid memory pointer type $memloc")
                        mem.setSByte(memloc.first, value.integerValue().toShort())
                    }
                } else {
                    val variable = getVar(varname)
                    if (variable.type == DataType.UWORD) {
                        // assume the variable is a pointer (address) and write the byte value to that memory location
                        if(value.type==DataType.UBYTE)
                            mem.setUByte(variable.integerValue(), value.integerValue().toShort())
                        else
                            mem.setSByte(variable.integerValue(), value.integerValue().toShort())
                    } else {
                        // set indexed byte element in the arraysize
                        val array = heap.get(variable.heapId!!)
                        when (array.type) {
                            DataType.ARRAY_UB -> array.array!![index] = IntegerOrAddressOf(value.integerValue(), null)
                            DataType.ARRAY_B -> array.array!![index] = IntegerOrAddressOf(value.integerValue(), null)
                            DataType.STR, DataType.STR_S -> {
                                val chars = array.str!!.toCharArray()
                                val ps = Petscii.decodePetscii(listOf(value.integerValue().toShort()), true)[0]
                                if(ps=='\ufffe')        // undefined
                                    chars[index] = '\u0000'
                                else
                                    chars[index] = ps
                                heap.update(variable.heapId, chars.joinToString(""))
                            }
                            else -> throw VmExecutionException("not a proper array/string var with byte elements")
                        }
                    }
                }
                setFlags(value)
            }
            Opcode.WRITE_INDEXED_VAR_WORD -> {
                // store word value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD, DataType.WORD)
                val varname = ins.callLabel!!
                val memloc = memoryPointers[varname]
                if(memloc!=null) {
                    // variable is the name of a pointer, write the word value to that memory location
                    if(value.type==DataType.UWORD) {
                        if(memloc.second!=DataType.ARRAY_UW)
                            throw VmExecutionException("invalid memory pointer type $memloc")
                        mem.setUWord(memloc.first+index*2, value.integerValue())
                    }
                    else {
                        if(memloc.second!=DataType.ARRAY_W)
                            throw VmExecutionException("invalid memory pointer type $memloc")
                        mem.setSWord(memloc.first+index*2, value.integerValue())
                    }
                } else {
                    val variable = getVar(varname)
                    if (variable.type == DataType.UWORD) {
                        // assume the variable is a pointer (address) and write the word value to that memory location
                        if(value.type==DataType.UWORD)
                            mem.setUWord(variable.integerValue()+index*2, value.integerValue())
                        else
                            mem.setSWord(variable.integerValue()+index*2, value.integerValue())
                    } else {
                        // set indexed word element in the arraysize
                        val array = heap.get(variable.heapId!!)
                        when (array.type) {
                            DataType.ARRAY_UW -> array.array!![index] = IntegerOrAddressOf(value.integerValue(), null)
                            DataType.ARRAY_W -> array.array!![index] = IntegerOrAddressOf(value.integerValue(), null)
                            else -> throw VmExecutionException("not a proper arraysize var with word elements")
                        }
                    }
                }
                setFlags(value)
            }
            Opcode.WRITE_INDEXED_VAR_FLOAT -> {
                // store float value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val varname = ins.callLabel!!
                val memloc = memoryPointers[varname]
                if(memloc!=null) {
                    // variable is the name of a pointer, write the float value to that memory location
                    if(memloc.second!=DataType.ARRAY_F)
                        throw VmExecutionException("invalid memory pointer type $memloc")
                    mem.setFloat(memloc.first+index*5, value.numericValue().toDouble())
                } else {
                    val variable = getVar(varname)
                    if (variable.type == DataType.UWORD) {
                        // assume the variable is a pointer (address) and write the float value to that memory location
                        mem.setFloat(variable.integerValue()+index*5, value.numericValue().toDouble())
                    } else {
                        // set indexed float element in the arraysize
                        val array = heap.get(variable.heapId!!)
                        if (array.type != DataType.ARRAY_F)
                            throw VmExecutionException("not a proper arraysize var with float elements")
                        array.doubleArray!![index] = value.numericValue().toDouble()
                    }
                }
                setFlags(value)
            }
            Opcode.RSAVE -> {
                evalstack.push(RuntimeValue(DataType.UBYTE, if (P_irqd) 1 else 0))
                evalstack.push(RuntimeValue(DataType.UBYTE, if (P_carry) 1 else 0))
                evalstack.push(variables["X"])
                evalstack.push(variables["Y"])
                evalstack.push(variables["A"])
            }
            Opcode.RRESTORE -> {
                variables["A"] = evalstack.pop()
                variables["X"] = evalstack.pop()
                variables["Y"] = evalstack.pop()
                P_carry = evalstack.pop().asBoolean
                P_irqd = evalstack.pop().asBoolean
            }
            Opcode.RSAVEX -> registerSaveX = variables.getValue("X")
            Opcode.RRESTOREX -> variables["X"] = registerSaveX
            Opcode.INLINE_ASSEMBLY -> throw VmExecutionException("stackVm doesn't support executing inline assembly code $ins")
            Opcode.INCLUDE_FILE -> throw VmExecutionException("stackVm doesn't support including a file $ins")
            Opcode.PUSH_ADDR_HEAPVAR -> {
                val heapId = variables.getValue(ins.callLabel!!).heapId!!
                if(heapId<0)
                    throw VmExecutionException("expected variable on heap")
                evalstack.push(RuntimeValue(DataType.UWORD, heapId))       // push the "address" of the string or array variable (this is taken care of properly in the assembly code generator)
            }
            Opcode.CAST_UB_TO_B -> typecast(DataType.UBYTE, DataType.BYTE)
            Opcode.CAST_W_TO_B -> typecast(DataType.WORD, DataType.BYTE)
            Opcode.CAST_UW_TO_B -> typecast(DataType.UWORD, DataType.BYTE)
            Opcode.CAST_F_TO_B -> typecast(DataType.FLOAT, DataType.BYTE)
            Opcode.CAST_B_TO_UB-> typecast(DataType.BYTE, DataType.UBYTE)
            Opcode.CAST_W_TO_UB -> typecast(DataType.WORD, DataType.UBYTE)
            Opcode.CAST_UW_TO_UB -> typecast(DataType.UWORD, DataType.UBYTE)
            Opcode.CAST_F_TO_UB -> typecast(DataType.FLOAT, DataType.UBYTE)
            Opcode.CAST_UB_TO_UW -> typecast(DataType.UBYTE, DataType.UWORD)
            Opcode.CAST_B_TO_UW -> typecast(DataType.BYTE, DataType.UWORD)
            Opcode.CAST_W_TO_UW -> typecast(DataType.WORD, DataType.UWORD)
            Opcode.CAST_F_TO_UW -> typecast(DataType.FLOAT, DataType.UWORD)
            Opcode.CAST_UB_TO_W -> typecast(DataType.UBYTE, DataType.WORD)
            Opcode.CAST_B_TO_W -> typecast(DataType.BYTE, DataType.WORD)
            Opcode.CAST_UW_TO_W -> typecast(DataType.UWORD, DataType.WORD)
            Opcode.CAST_F_TO_W -> typecast(DataType.FLOAT, DataType.WORD)
            Opcode.CAST_UB_TO_F -> typecast(DataType.UBYTE, DataType.FLOAT)
            Opcode.CAST_B_TO_F -> typecast(DataType.BYTE, DataType.FLOAT)
            Opcode.CAST_UW_TO_F -> typecast(DataType.UWORD, DataType.FLOAT)
            Opcode.CAST_W_TO_F -> typecast(DataType.WORD, DataType.FLOAT)
            Opcode.CARRY_TO_A -> variables["A"] = if(P_carry) RuntimeValue(DataType.UBYTE, 1) else RuntimeValue(DataType.UBYTE, 0)

            //else -> throw VmExecutionException("unimplemented opcode: ${ins.opcode}")
        }

        if(traceOutput!=null) {
            traceOutput?.println(" evalstack (size=${evalstack.size}):")
            evalstack.printTop(4, traceOutput!!)
        }

        currentInstructionPtr++
    }

    private fun determineBranchInstr(ins: Instruction): Int {
        if(ins.branchAddress!=null) {
            TODO("call to a system memory routine at ${ins.branchAddress!!.toHex()}")
            throw VmExecutionException("stackVm doesn't support branching to a memory address")
        }
        return if(ins.callLabel==null)
            throw VmExecutionException("requires label to branch to")
        else {
            when(ins.callLabel) {
                "c64.CLEARSCR" -> {
                    canvas?.clearScreen(mem.getUByte(0xd021))
                    callstack.pop()
                }
                "c64.CHROUT" -> {
                    val sc=variables.getValue("A").integerValue()
                    canvas?.printChar(sc.toShort())
                    callstack.pop()
                }
                "c64.GETIN" -> {
                    variables["A"] = RuntimeValue(DataType.UBYTE, 0)  // TODO keyboard input
                    callstack.pop()
                }
                else -> {
                    labels.getValue(ins.callLabel)
                }
            }
        }
    }

    private fun setFlags(value: RuntimeValue?) {
        if(value!=null) {
            when(value.type) {
                DataType.UBYTE -> {
                    val int = value.integerValue()
                    P_negative = int>127
                    P_zero = int==0
                }
                DataType.UWORD -> {
                    val int = value.integerValue()
                    P_negative = int>32767
                    P_zero = int==0
                }
                DataType.BYTE, DataType.WORD -> {
                    val int = value.integerValue()
                    P_negative = int<0
                    P_zero = int==0
                }
                DataType.FLOAT -> {
                    val flt = value.numericValue().toDouble()
                    P_negative = flt < 0.0
                    P_zero = flt==0.0
                }
                else -> {
                    // no flags for non-numeric type
                }
            }
        }
    }

    private fun typecast(from: DataType, to: DataType) {
        val value = evalstack.pop()
        checkDt(value, from)
        val cv = value.cast(to)
        evalstack.push(cv)
    }

    private fun dispatchSyscall(ins: Instruction) {
        val callId = ins.arg!!.integerValue().toShort()
        when (val syscall = Syscall.values().first { it.callNr == callId }) {
            Syscall.VM_WRITE_MEMCHR -> {
                val address = evalstack.pop().integerValue()
                print(Petscii.decodePetscii(listOf(mem.getUByte(address)), true))
            }
            Syscall.VM_WRITE_MEMSTR -> {
                val address = evalstack.pop().integerValue()
                print(mem.getString(address))
            }
            Syscall.VM_WRITE_NUM -> {
                print(evalstack.pop().numericValue())
            }
            Syscall.VM_WRITE_CHAR -> {
                print(Petscii.decodePetscii(listOf(evalstack.pop().integerValue().toShort()), true))
            }
            Syscall.VM_WRITE_STR -> {
                val heapId = evalstack.pop().integerValue()
                print(heap.get(heapId).str?.substringBefore('\u0000'))
            }
            Syscall.VM_INPUT_STR -> {
                val heapId = evalstack.pop().integerValue()
                val value = heap.get(heapId)
                val maxlen = value.str!!.length
                val input = readLine() ?: ""
                heap.update(heapId, input.padEnd(maxlen, '\u0000').substring(0, maxlen))
            }
            Syscall.VM_GFX_PIXEL -> {
                // plot pixel at (x, y, color) from stack
                val color = evalstack.pop()
                val (y, x) = evalstack.pop2()
                canvas?.setPixel(x.integerValue(), y.integerValue(), color.integerValue().toShort())
            }
            Syscall.VM_GFX_LINE -> {
                // draw line at (x1, y1, x2, y2, color) from stack
                val color = evalstack.pop()
                val (y2, x2) = evalstack.pop2()
                val (y1, x1) = evalstack.pop2()
                canvas?.drawLine(x1.integerValue(), y1.integerValue(), x2.integerValue(), y2.integerValue(), color.integerValue().toShort())
            }
            Syscall.VM_GFX_CLEARSCR -> {
                val color = evalstack.pop()
                canvas?.clearScreen(color.integerValue().toShort())
            }
            Syscall.VM_GFX_TEXT -> {
                val textPtr = evalstack.pop().integerValue()
                val color = evalstack.pop()
                val (cy, cx) = evalstack.pop2()
                val text = heap.get(textPtr)
                canvas?.writeText(cx.integerValue(), cy.integerValue(), text.str!!, color.integerValue().toShort(), true)
            }
            Syscall.FUNC_RND -> evalstack.push(RuntimeValue(DataType.UBYTE, rnd.nextInt() and 255))
            Syscall.FUNC_RNDW -> evalstack.push(RuntimeValue(DataType.UWORD, rnd.nextInt() and 65535))
            Syscall.FUNC_RNDF -> evalstack.push(RuntimeValue(DataType.FLOAT, rnd.nextDouble()))
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
            Syscall.FUNC_MEMSET -> {
                val value = evalstack.pop()
                val address = evalstack.pop().integerValue()
                val numbytes = evalstack.pop().integerValue()
                val bytevalue = value.integerValue().toShort()
                when {
                    value.type==DataType.UBYTE -> for(addr in address until address+numbytes)
                        mem.setUByte(addr, bytevalue)
                    value.type==DataType.BYTE -> for(addr in address until address+numbytes)
                        mem.setSByte(addr, bytevalue)
                    else -> throw VmExecutionException("(u)byte value expected")
                }
            }
            Syscall.FUNC_MEMSETW -> {
                val value = evalstack.pop()
                val address = evalstack.pop().integerValue()
                val numwords = evalstack.pop().integerValue()
                val wordvalue = value.integerValue()
                when {
                    value.type==DataType.UWORD -> for(addr in address until address+numwords*2 step 2)
                        mem.setUWord(addr, wordvalue)
                    value.type==DataType.WORD -> for(addr in address until address+numwords*2 step 2)
                        mem.setSWord(addr, wordvalue)
                    else -> throw VmExecutionException("(u)word value expected")
                }
            }
            Syscall.SYSCALLSTUB -> throw VmExecutionException("unimplemented sysasm called: ${ins.callLabel}  Create a Syscall enum for this and implement the vm intercept for it.")
            Syscall.SYSASM_c64scr_plot -> {
                val x = variables.getValue("Y").integerValue()
                val y = variables.getValue("A").integerValue()
                canvas?.setCursorPos(x, y)
            }
            Syscall.SYSASM_c64scr_print -> {
                val straddr = variables.getValue("A").integerValue() + 256*variables.getValue("Y").integerValue()
                val str = heap.get(straddr).str!!
                canvas?.printText(str, 1, true)
            }
            Syscall.SYSASM_c64scr_print_ub -> {
                val num = variables.getValue("A").integerValue()
                canvas?.printText(num.toString(), 1, true)
            }
            Syscall.SYSASM_c64scr_print_ub0 -> {
                val num = variables.getValue("A").integerValue()
                canvas?.printText("%03d".format(num), 1, true)
            }
            Syscall.SYSASM_c64scr_print_b -> {
                val num = variables.getValue("A").integerValue()
                if(num<=127)
                    canvas?.printText(num.toString(), 1, true)
                else
                    canvas?.printText("-${256-num}", 1, true)
            }
            Syscall.SYSASM_c64scr_print_uw -> {
                val lo = variables.getValue("A").integerValue()
                val hi = variables.getValue("Y").integerValue()
                val number = lo+256*hi
                canvas?.printText(number.toString(), 1, true)
            }
            Syscall.SYSASM_c64scr_print_uw0 -> {
                val lo = variables.getValue("A").integerValue()
                val hi = variables.getValue("Y").integerValue()
                val number = lo+256*hi
                canvas?.printText("%05d".format(number), 1, true)
            }
            Syscall.SYSASM_c64scr_print_uwhex -> {
                val prefix = if(this.P_carry) "$" else ""
                val lo = variables.getValue("A").integerValue()
                val hi = variables.getValue("Y").integerValue()
                val number = lo+256*hi
                canvas?.printText("$prefix${number.toString(16).padStart(4, '0')}", 1, true)
            }
            Syscall.SYSASM_c64scr_print_w -> {
                val lo = variables.getValue("A").integerValue()
                val hi = variables.getValue("Y").integerValue()
                val number = lo+256*hi
                if(number<=32767)
                    canvas?.printText(number.toString(), 1, true)
                else
                    canvas?.printText("-${65536-number}", 1, true)
            }
            Syscall.SYSASM_c64flt_print_f -> {
                val number = variables.getValue("c64flt.print_f.value").numericValue()
                canvas?.printText(number.toString(), 1, true)
            }
            Syscall.SYSASM_c64scr_setcc -> {
                val x = variables.getValue("c64scr.setcc.column").integerValue()
                val y = variables.getValue("c64scr.setcc.row").integerValue()
                val char = variables.getValue("c64scr.setcc.char").integerValue()
                val color = variables.getValue("c64scr.setcc.color").integerValue()
                canvas?.setChar(x, y, char.toShort(), color.toShort())
            }
            else -> throw VmExecutionException("unimplemented syscall $syscall")
        }
    }

    fun irq(timestamp: Long) {
        // 60hz IRQ handling
        if(P_irqd)
            return      // interrupt is disabled

        P_irqd=true
        swapIrqExecutionContexts(true)

        val jiffies = min((timestamp-rtcOffset)*60/1000, 24*3600*60-1)
        // update the C-64 60hz jiffy clock in the ZP addresses:
        mem.setUByte_DMA(0x00a0, (jiffies ushr 16).toShort())
        mem.setUByte_DMA(0x00a1, (jiffies ushr 8 and 255).toShort())
        mem.setUByte_DMA(0x00a2, (jiffies and 255).toShort())

        if(irqStartInstructionPtr>=0) {
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

    private var irqStoredEvalStack = MyStack<RuntimeValue>()
    private var irqStoredCallStack = MyStack<Int>()
    private var irqStoredCarry = false
    private var irqStoredTraceOutputFile: String? = null
    private var irqStoredMainInstructionPtr = -1

    private fun swapIrqExecutionContexts(startingIrq: Boolean) {
        if(startingIrq) {
            irqStoredMainInstructionPtr = currentInstructionPtr
            irqStoredCallStack = callstack
            irqStoredEvalStack = evalstack
            irqStoredCarry = P_carry
            irqStoredTraceOutputFile = traceOutputFile

            if(irqStartInstructionPtr>=0)
                currentInstructionPtr = irqStartInstructionPtr
            else {
                if(program.last().opcode!=Opcode.RETURN)
                    throw VmExecutionException("last instruction in program should be RETURN for irq handler")
                currentInstructionPtr = program.size-1
            }
            callstack = MyStack()
            evalstack = MyStack()
            P_carry = false
            traceOutputFile = null
        } else {
            if(evalstack.isNotEmpty())
                throw VmExecutionException("irq: eval stack is not empty at exit from irq program")
            if(callstack.isNotEmpty())
                throw VmExecutionException("irq: call stack is not empty at exit from irq program")
            currentInstructionPtr = irqStoredMainInstructionPtr
            callstack = irqStoredCallStack
            evalstack = irqStoredEvalStack
            P_carry = irqStoredCarry
            traceOutputFile = irqStoredTraceOutputFile
        }
    }
}
