package prog8.stackvm

import prog8.ast.DataType
import prog8.ast.IterableDatatypes
import prog8.ast.NumericDatatypes
import prog8.compiler.HeapValues
import prog8.compiler.intermediate.Instruction
import prog8.compiler.intermediate.Opcode
import prog8.compiler.intermediate.Value
import prog8.compiler.target.c64.Petscii
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
    FUNC_WRD(92),
    FUNC_UWRD(93),
    FUNC_STR2BYTE(100),
    FUNC_STR2UBYTE(101),
    FUNC_STR2WORD(102),
    FUNC_STR2UWORD(103),
    FUNC_STR2FLOAT(104)

    // note: not all builtin functions of the Prog8 language are present as functions:
    // some of them are straight opcodes (such as MSB, LSB, LSL, LSR, ROL_BYTE, ROR, ROL2, ROR2, and FLT)!
}

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
    val mem = Memory()
    var P_carry: Boolean = false
        private set
    var P_negative: Boolean = false
        private set
    var P_zero: Boolean = false
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
        canvas?.requestFocusInWindow()
        variables = program.variables.toMutableMap()

        if("A" in variables || "X" in variables || "Y" in variables)
            throw VmExecutionException("program contains variable(s) for the reserved registers A/X/Y")
        // define the 'registers'
        variables["A"] = Value(DataType.UBYTE, 0)
        variables["X"] = Value(DataType.UBYTE, 0)
        variables["Y"] = Value(DataType.UBYTE, 0)

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
    }

    private fun checkDt(value: Value?, vararg expected: DataType) {
        if(value==null)
            throw VmExecutionException("expected value")
        if(value.type !in expected)
            throw VmExecutionException("incompatible type ${value.type}")
    }


    private fun dispatch(ins: Instruction) : Instruction {
        traceOutput?.println("\n$ins")
        when (ins.opcode) {
            Opcode.NOP -> {}
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
                evalstack.push(Value(DataType.UBYTE, mem.getUByte(address)))
            }
            Opcode.PUSH_MEM_B -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.BYTE, mem.getSByte(address)))
            }
            Opcode.PUSH_MEM_UW -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.UWORD, mem.getUWord(address)))
            }
            Opcode.PUSH_MEM_W -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.WORD, mem.getSWord(address)))
            }
            Opcode.PUSH_MEM_FLOAT -> {
                val address = ins.arg!!.integerValue()
                evalstack.push(Value(DataType.FLOAT, mem.getFloat(address)))
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
            }
            Opcode.POP_MEM_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.WORD, DataType.UWORD)
                val address = ins.arg!!.integerValue()
                if(value.type==DataType.WORD)
                    mem.setSWord(address, value.integerValue())
                else
                    mem.setUWord(address, value.integerValue())
            }
            Opcode.POP_MEM_FLOAT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val address = ins.arg!!.integerValue()
                mem.setFloat(address, value.numericValue().toDouble())
            }
            Opcode.ADD_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.add(top))
            }
            Opcode.ADD_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.add(top))
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
            Opcode.SUB_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.sub(top))
            }
            Opcode.SUB_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.sub(top))
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
            Opcode.MUL_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.mul(top))
            }
            Opcode.MUL_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.mul(top))
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
            Opcode.DIV_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.div(top))
            }
            Opcode.DIV_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.div(top))
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
            Opcode.FLOORDIV_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.floordiv(top))
            }
            Opcode.FLOORDIV_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.floordiv(top))
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
            Opcode.REMAINDER_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.remainder(top))
            }
            Opcode.REMAINDER_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.remainder(top))
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
            Opcode.POW_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.pow(top))
            }
            Opcode.POW_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.pow(top))
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
            Opcode.SHL_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                evalstack.push(v.shl())
            }
            Opcode.SHL_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.shl())
            }
            Opcode.SHR_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                evalstack.push(v.shr())
            }
            Opcode.SHR_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.shr())
            }
            Opcode.ROL_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val (result, newCarry) = v.rol(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val (result, newCarry) = v.rol(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROL2_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                evalstack.push(v.rol2())
            }
            Opcode.ROL2_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.rol2())
            }
            Opcode.ROR_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                val (result, newCarry) = v.ror(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                val (result, newCarry) = v.ror(P_carry)
                this.P_carry = newCarry
                evalstack.push(result)
            }
            Opcode.ROR2_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                evalstack.push(v.ror2())
            }
            Opcode.ROR2_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.ror2())
            }
            Opcode.BITAND_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.bitand(top))
            }
            Opcode.BITAND_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.bitand(top))
            }
            Opcode.BITOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.bitor(top))
            }
            Opcode.BITOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.bitor(top))
            }
            Opcode.BITXOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.bitxor(top))
            }
            Opcode.BITXOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.bitxor(top))
            }
            Opcode.INV_BYTE -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UBYTE)
                evalstack.push(v.inv())
            }
            Opcode.INV_WORD -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.inv())
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
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.shl()
                mem.setUByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHL_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.shl()
                mem.setUWord(addr, newValue.integerValue())
            }
            Opcode.SHR_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.shr()
                mem.setUByte(addr, newValue.integerValue().toShort())
            }
            Opcode.SHR_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.shr()
                mem.setUWord(addr, newValue.integerValue())
            }
            Opcode.ROL_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setUByte(addr, newValue.integerValue().toShort())
                P_carry = newCarry
            }
            Opcode.ROL_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val (newValue, newCarry) = value.rol(P_carry)
                mem.setUWord(addr, newValue.integerValue())
                P_carry = newCarry
            }
            Opcode.ROR_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setUByte(addr, newValue.integerValue().toShort())
                P_carry = newCarry
            }
            Opcode.ROR_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val (newValue, newCarry) = value.ror(P_carry)
                mem.setUWord(addr, newValue.integerValue())
                P_carry = newCarry
            }
            Opcode.ROL2_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.rol2()
                mem.setUByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROL2_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.rol2()
                mem.setUWord(addr, newValue.integerValue())
            }
            Opcode.ROR2_MEM_BYTE -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UBYTE, mem.getUByte(addr))
                val newValue = value.ror2()
                mem.setUByte(addr, newValue.integerValue().toShort())
            }
            Opcode.ROR2_MEM_WORD -> {
                val addr = ins.arg!!.integerValue()
                val value = Value(DataType.UWORD, mem.getUWord(addr))
                val newValue = value.ror2()
                mem.setUWord(addr, newValue.integerValue())
            }

            Opcode.JUMP -> {}   // do nothing; the next instruction is wired up already to the jump target
            Opcode.BCS ->
                return if(P_carry) ins.next else ins.nextAlt!!
            Opcode.BCC ->
                return if(P_carry) ins.nextAlt!! else ins.next
            Opcode.BZ ->
                return if(P_zero) ins.next else ins.nextAlt!!
            Opcode.BNZ ->
                return if(P_zero) ins.nextAlt!! else ins.next
            Opcode.BNEG ->
                return if(P_negative) ins.next else ins.nextAlt!!
            Opcode.BPOS ->
                return if(P_negative) ins.nextAlt!! else ins.next
            Opcode.BVS, Opcode.BVC -> throw VmExecutionException("stackVM doesn't support the overflow flag")
            Opcode.TEST -> {
                val value=evalstack.pop().numericValue().toDouble()
                P_zero = value == 0.0
                P_negative = value < 0.0
            }
            Opcode.CALL ->
                callstack.push(ins.nextAlt)
            Opcode.RETURN -> {
                if(callstack.empty())
                    throw VmTerminationException("return instruction with empty call stack")
                return callstack.pop()
            }
            Opcode.PUSH_VAR_BYTE -> {
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_WORD -> {
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, *(setOf(DataType.UWORD, DataType.WORD) + IterableDatatypes).toTypedArray())
                evalstack.push(value)
            }
            Opcode.PUSH_VAR_FLOAT -> {
                val value = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(value, DataType.FLOAT)
                evalstack.push(value)
            }
            Opcode.PUSH_REGAX_WORD -> {
                val a=variables["A"]!!.integerValue()
                val x=variables["X"]!!.integerValue()
                evalstack.push(Value(DataType.UWORD, x*256+a))
            }
            Opcode.PUSH_REGAY_WORD -> {
                val a=variables["A"]!!.integerValue()
                val y=variables["Y"]!!.integerValue()
                evalstack.push(Value(DataType.UWORD, y*256+a))
            }
            Opcode.PUSH_REGXY_WORD -> {
                val x=variables["X"]!!.integerValue()
                val y=variables["Y"]!!.integerValue()
                evalstack.push(Value(DataType.UWORD, y*256+x))
            }
            Opcode.POP_REGAX_WORD -> {
                val value=evalstack.pop().integerValue()
                variables["A"] = Value(DataType.UBYTE, value and 255)
                variables["X"] = Value(DataType.UBYTE, value shr 8)
            }
            Opcode.POP_REGAY_WORD -> {
                val value=evalstack.pop().integerValue()
                variables["A"] = Value(DataType.UBYTE, value and 255)
                variables["Y"] = Value(DataType.UBYTE, value shr 8)
            }
            Opcode.POP_REGXY_WORD -> {
                val value=evalstack.pop().integerValue()
                variables["X"] = Value(DataType.UBYTE, value and 255)
                variables["Y"] = Value(DataType.UBYTE, value shr 8)
            }
            Opcode.POP_VAR_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE, DataType.BYTE)
                if(value.type!=variable.type)
                    throw VmExecutionException("datatype mismatch")
                variables[ins.callLabel!!] = value
            }
            Opcode.POP_VAR_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD, DataType.WORD, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD, DataType.WORD, DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS)
                if(value.type!=variable.type)
                    throw VmExecutionException("datatype mismatch")
                variables[ins.callLabel!!] = value
            }
            Opcode.POP_VAR_FLOAT -> {
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.FLOAT)
                variables[ins.callLabel!!] = value
            }
            Opcode.SHL_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.shl()
            }
            Opcode.SHL_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                variables[ins.callLabel!!] = variable.shl()
            }
            Opcode.SHR_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.shr()
            }
            Opcode.SHR_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                variables[ins.callLabel!!] = variable.shr()
            }
            Opcode.ROL_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROL_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                val (newValue, newCarry) = variable.rol(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROR_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                val (newValue, newCarry) = variable.ror(P_carry)
                variables[ins.callLabel!!] = newValue
                P_carry = newCarry
            }
            Opcode.ROL2_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.rol2()
            }
            Opcode.ROL2_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                variables[ins.callLabel!!] = variable.rol2()
            }
            Opcode.ROR2_VAR_BYTE -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.ror2()
            }
            Opcode.ROR2_VAR_WORD -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
                variables[ins.callLabel!!] = variable.ror2()
            }
            Opcode.INC_VAR_UB -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.INC_VAR_B -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.inc()
            }
            Opcode.INC_VAR_UW -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
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
            Opcode.DEC_VAR_UB -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UBYTE)
                variables[ins.callLabel!!] = variable.dec()
            }
            Opcode.DEC_VAR_B -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.BYTE)
                variables[ins.callLabel!!] = variable.dec()
            }
            Opcode.DEC_VAR_UW -> {
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                checkDt(variable, DataType.UWORD)
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
                checkDt(v, DataType.UWORD)
                evalstack.push(v.lsb())
            }
            Opcode.MSB -> {
                val v = evalstack.pop()
                checkDt(v, DataType.UWORD)
                evalstack.push(v.msb())
            }
            Opcode.AND_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.and(top))
            }
            Opcode.AND_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.and(top))
            }
            Opcode.OR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.or(top))
            }
            Opcode.OR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.or(top))
            }
            Opcode.XOR_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(second.xor(top))
            }
            Opcode.XOR_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(second.xor(top))
            }
            Opcode.NOT_BYTE -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE)
                evalstack.push(value.not())
            }
            Opcode.NOT_WORD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD)
                evalstack.push(value.not())
            }
            Opcode.LESS_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second < top) 1 else 0))
            }
            Opcode.LESS_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.UBYTE, if (second < top) 1 else 0))
            }
            Opcode.LESS_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second < top) 1 else 0))
            }
            Opcode.LESS_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.UBYTE, if (second < top) 1 else 0))
            }
            Opcode.LESS_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second < top) 1 else 0))
            }
            Opcode.GREATER_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second > top) 1 else 0))
            }
            Opcode.GREATER_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.UBYTE, if (second > top) 1 else 0))
            }
            Opcode.GREATER_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second > top) 1 else 0))
            }
            Opcode.GREATER_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.UBYTE, if (second > top) 1 else 0))
            }
            Opcode.GREATER_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second > top) 1 else 0))
            }
            Opcode.LESSEQ_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second <= top) 1 else 0))
            }
            Opcode.LESSEQ_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.UBYTE, if (second <= top) 1 else 0))
            }
            Opcode.LESSEQ_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second <= top) 1 else 0))
            }
            Opcode.LESSEQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.UBYTE, if (second <= top) 1 else 0))
            }
            Opcode.LESSEQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second <= top) 1 else 0))
            }
            Opcode.GREATEREQ_UB -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_B -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.BYTE)
                checkDt(second, DataType.BYTE)
                evalstack.push(Value(DataType.UBYTE, if (second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_UW -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_W -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.WORD)
                checkDt(second, DataType.WORD)
                evalstack.push(Value(DataType.UBYTE, if (second >= top) 1 else 0))
            }
            Opcode.GREATEREQ_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second >= top) 1 else 0))
            }
            Opcode.EQUAL_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second == top) 1 else 0))
            }
            Opcode.EQUAL_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second == top) 1 else 0))
            }
            Opcode.EQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second == top) 1 else 0))
            }
            Opcode.NOTEQUAL_BYTE -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UBYTE)
                checkDt(second, DataType.UBYTE)
                evalstack.push(Value(DataType.UBYTE, if (second != top) 1 else 0))
            }
            Opcode.NOTEQUAL_WORD -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.UWORD)
                checkDt(second, DataType.UWORD)
                evalstack.push(Value(DataType.UBYTE, if (second != top) 1 else 0))
            }
            Opcode.NOTEQUAL_F -> {
                val (top, second) = evalstack.pop2()
                checkDt(top, DataType.FLOAT)
                checkDt(second, DataType.FLOAT)
                evalstack.push(Value(DataType.UBYTE, if (second != top) 1 else 0))
            }
            Opcode.B2UB -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.UBYTE, byte.integerValue() and 255))
            }
            Opcode.UB2B -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.UBYTE)
                if(byte.integerValue() > 127) {
                    evalstack.push(Value(DataType.BYTE, -((byte.integerValue() xor 255) + 1)))
                } else
                    evalstack.push(Value(DataType.BYTE, byte.integerValue()))
            }
            Opcode.B2WORD -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.WORD, byte.integerValue()))
            }
            Opcode.UB2UWORD -> {
                val ubyte = evalstack.pop()
                checkDt(ubyte, DataType.UBYTE)
                evalstack.push(Value(DataType.UWORD, ubyte.integerValue()))
            }
            Opcode.B2FLOAT -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.BYTE)
                evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
            }
            Opcode.UB2FLOAT -> {
                val byte = evalstack.pop()
                checkDt(byte, DataType.UBYTE)
                evalstack.push(Value(DataType.FLOAT, byte.integerValue()))
            }
            Opcode.W2FLOAT -> {
                val wrd = evalstack.pop()
                checkDt(wrd, DataType.UWORD)
                evalstack.push(Value(DataType.FLOAT, wrd.integerValue()))
            }
            Opcode.UW2FLOAT -> {
                val uwrd = evalstack.pop()
                checkDt(uwrd, DataType.UWORD)
                evalstack.push(Value(DataType.FLOAT, uwrd.integerValue()))
            }
            Opcode.READ_INDEXED_VAR_BYTE -> {
                // put the byte value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and get the ubyte value from that memory location
                    evalstack.push(Value(DataType.UBYTE, mem.getUByte(variable.integerValue())))
                } else {
                    // get indexed byte element from the arrayspec
                    val array = heap.get(variable.heapId)
                    when(array.type) {
                        DataType.ARRAY_UB-> evalstack.push(Value(DataType.UBYTE, array.array!![index]))
                        DataType.ARRAY_B -> evalstack.push(Value(DataType.BYTE, array.array!![index]))
                        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> evalstack.push(Value(DataType.UBYTE, Petscii.encodePetscii(array.str!![index].toString(), true)[0]))
                        else -> throw VmExecutionException("not a proper array/string variable with byte elements")
                    }
                }
            }
            Opcode.READ_INDEXED_VAR_WORD -> {
                // put the word value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and get the word value from that memory location
                    evalstack.push(Value(DataType.UWORD, mem.getUWord(variable.integerValue())))
                } else {
                    // get indexed word element from the arrayspec
                    val array = heap.get(variable.heapId)
                    when(array.type){
                        DataType.ARRAY_UW -> evalstack.push(Value(DataType.UWORD, array.array!![index]))
                        DataType.ARRAY_W -> evalstack.push(Value(DataType.WORD, array.array!![index]))
                        else -> throw VmExecutionException("not a proper arrayspec var with word elements")
                    }
                }
            }
            Opcode.READ_INDEXED_VAR_FLOAT -> {
                // put the f;pat value of variable[index] onto the stack
                val index = evalstack.pop().integerValue()
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and get the float value from that memory location
                    evalstack.push(Value(DataType.UWORD, mem.getFloat(variable.integerValue())))
                } else {
                    // get indexed float element from the arrayspec
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_F)
                        throw VmExecutionException("not a proper arrayspec var with float elements")
                    evalstack.push(Value(DataType.FLOAT, array.doubleArray!![index]))
                }
            }
            Opcode.WRITE_INDEXED_VAR_BYTE -> {
                // store byte value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and write the byte value to that memory location
                    mem.setUByte(variable.integerValue(), value.integerValue().toShort())
                } else {
                    // set indexed byte element in the arrayspec
                    val array = heap.get(variable.heapId)
                    when(array.type) {
                        DataType.ARRAY_UB -> array.array!![index] = value.integerValue()
                        DataType.ARRAY_B -> array.array!![index] = value.integerValue()
                        DataType.STR,
                        DataType.STR_P,
                        DataType.STR_S,
                        DataType.STR_PS -> {
                            val chars = array.str!!.toCharArray()
                            chars[index] = Petscii.decodePetscii(listOf(value.integerValue().toShort()), true)[0]
                            heap.update(variable.heapId, chars.joinToString(""))
                        }
                        else -> throw VmExecutionException("not a proper array/string var with byte elements")
                    }
                }
            }
            Opcode.WRITE_INDEXED_VAR_WORD -> {
                // store word value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.UWORD, DataType.WORD)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and write the word value to that memory location
                    mem.setUWord(variable.integerValue(), value.integerValue())
                } else {
                    // set indexed word element in the arrayspec
                    val array = heap.get(variable.heapId)
                    when(array.type) {
                        DataType.ARRAY_UW -> array.array!![index] = value.integerValue()
                        DataType.ARRAY_W -> array.array!![index] = value.integerValue()
                        else -> throw VmExecutionException("not a proper arrayspec var with word elements")
                    }

                }
            }
            Opcode.WRITE_INDEXED_VAR_FLOAT -> {
                // store float value on the stack in variable[index]  (index is on the stack as well)
                val index = evalstack.pop().integerValue()
                val value = evalstack.pop()
                checkDt(value, DataType.FLOAT)
                val variable = variables[ins.callLabel] ?: throw VmExecutionException("unknown variable: ${ins.callLabel}")
                if(variable.type==DataType.UWORD) {
                    // assume the variable is a pointer (address) and write the float value to that memory location
                    mem.setFloat(variable.integerValue(), value.numericValue().toDouble())
                } else {
                    // set indexed float element in the arrayspec
                    val array = heap.get(variable.heapId)
                    if(array.type!=DataType.ARRAY_F)
                        throw VmExecutionException("not a proper arrayspec var with float elements")
                    array.doubleArray!![index] = value.numericValue().toDouble()
                }
            }
            Opcode.RSAVE -> {
                evalstack.push(Value(DataType.UBYTE, if(P_irqd) 1 else 0))
                evalstack.push(Value(DataType.UBYTE, if(P_carry) 1 else 0))
                evalstack.push(Value(DataType.UBYTE, if(P_negative) 1 else 0))
                evalstack.push(Value(DataType.UBYTE, if(P_zero) 1 else 0))
                evalstack.push(variables["X"])
                evalstack.push(variables["Y"])
                evalstack.push(variables["A"])
            }
            Opcode.RRESTORE -> {
                variables["A"] = evalstack.pop()
                variables["X"] = evalstack.pop()
                variables["Y"] = evalstack.pop()
                P_zero = evalstack.pop().asBooleanValue
                P_negative = evalstack.pop().asBooleanValue
                P_carry = evalstack.pop().asBooleanValue
                P_irqd = evalstack.pop().asBooleanValue
            }
            Opcode.INLINE_ASSEMBLY -> throw VmExecutionException("stackVm doesn't support executing inline assembly code")
            //else -> throw VmExecutionException("unimplemented opcode: ${ins.opcode}")
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
                val value = evalstack.pop()
                when(value.type){
                    DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT -> print(value.numericValue())
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> print(heap.get(value.heapId).str)
                    DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W -> print(heap.get(value.heapId).array!!.toList())
                    DataType.ARRAY_F -> print(heap.get(value.heapId).doubleArray!!.toList())
                }
            }
            Syscall.VM_INPUT_STR -> {
                val variable = evalstack.pop()
                val value = heap.get(variable.heapId)
                val maxlen = value.str!!.length
                val input = readLine() ?: ""
                heap.update(variable.heapId, input.padEnd(maxlen, '\u0000').substring(0, maxlen))
            }
            Syscall.VM_GFX_PIXEL -> {
                // plot pixel at (x, y, color) from stack
                val color = evalstack.pop()
                val (y, x) = evalstack.pop2()
                canvas?.setPixel(x.integerValue(), y.integerValue(), color.integerValue())
            }
            Syscall.VM_GFX_LINE -> {
                // draw line at (x1, y1, x2, y2, color) from stack
                val color = evalstack.pop()
                val (y2, x2) = evalstack.pop2()
                val (y1, x1) = evalstack.pop2()
                canvas?.drawLine(x1.integerValue(), y1.integerValue(), x2.integerValue(), y2.integerValue(), color.integerValue())
            }
            Syscall.VM_GFX_CLEARSCR -> {
                val color = evalstack.pop()
                canvas?.clearScreen(color.integerValue())
            }
            Syscall.VM_GFX_TEXT -> {
                val textPtr = evalstack.pop()
                val color = evalstack.pop()
                val (cy, cx) = evalstack.pop2()
                val text = heap.get(textPtr.heapId)
                canvas?.writeText(cx.integerValue(), cy.integerValue(), text.str!!, color.integerValue())
            }
            Syscall.FUNC_RND -> evalstack.push(Value(DataType.UBYTE, rnd.nextInt() and 255))
            Syscall.FUNC_RNDW -> evalstack.push(Value(DataType.UWORD, rnd.nextInt() and 65535))
            Syscall.FUNC_RNDF -> evalstack.push(Value(DataType.FLOAT, rnd.nextDouble()))
            Syscall.FUNC_LEN -> throw VmExecutionException("len() should have been const-folded away everywhere (it's not possible on non-const values)")
            Syscall.FUNC_SIN -> evalstack.push(Value(DataType.FLOAT, sin(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_COS -> evalstack.push(Value(DataType.FLOAT, cos(evalstack.pop().numericValue().toDouble())))
            Syscall.FUNC_ROUND -> evalstack.push(Value(DataType.WORD, evalstack.pop().numericValue().toDouble().roundToInt()))
            Syscall.FUNC_ABS -> {
                val value = evalstack.pop()
                val absValue=
                        when(value.type) {
                            DataType.UBYTE -> Value(DataType.UBYTE, value.numericValue())
                            DataType.UWORD -> Value(DataType.UWORD, value.numericValue())
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
                if(value.type in NumericDatatypes)
                    evalstack.push(Value(DataType.WORD, floor(value.numericValue().toDouble()).toInt()))
                else throw VmExecutionException("cannot get floor of $value")
            }
            Syscall.FUNC_CEIL -> {
                val value = evalstack.pop()
                if(value.type in NumericDatatypes)
                    evalstack.push(Value(DataType.WORD, ceil(value.numericValue().toDouble()).toInt()))
                else throw VmExecutionException("cannot get ceil of $value")
            }
            Syscall.FUNC_MAX -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                val resultDt = when(iterable.type) {
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.UBYTE
                    DataType.ARRAY_UB -> DataType.UBYTE
                    DataType.ARRAY_B -> DataType.BYTE
                    DataType.ARRAY_UW -> DataType.UWORD
                    DataType.ARRAY_W -> DataType.WORD
                    DataType.ARRAY_F -> DataType.FLOAT
                    DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT -> throw VmExecutionException("uniterable value $iterable")
                }
                if(value.str!=null) {
                    val result = Petscii.encodePetscii(value.str.max().toString(), true)[0]
                    evalstack.push(Value(DataType.UBYTE, result))
                } else {
                    val result = value.array!!.max() ?: 0
                    evalstack.push(Value(resultDt, result))
                }
            }
            Syscall.FUNC_MIN -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                val resultDt = when(iterable.type) {
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> DataType.UBYTE
                    DataType.ARRAY_UB -> DataType.UBYTE
                    DataType.ARRAY_B -> DataType.BYTE
                    DataType.ARRAY_UW -> DataType.UWORD
                    DataType.ARRAY_W -> DataType.WORD
                    DataType.ARRAY_F -> DataType.FLOAT
                    DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT -> throw VmExecutionException("uniterable value $iterable")
                }
                if(value.str!=null) {
                    val result = Petscii.encodePetscii(value.str.min().toString(), true)[0]
                    evalstack.push(Value(DataType.UBYTE, result))
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
                    evalstack.push(Value(DataType.UWORD, Petscii.encodePetscii(value.str, true).sum()))
                else
                    evalstack.push(Value(DataType.UWORD, value.array!!.sum()))
            }
            Syscall.FUNC_ANY -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if (value.str != null)
                    evalstack.push(Value(DataType.UBYTE, if (Petscii.encodePetscii(value.str, true).any { c -> c != 0.toShort() }) 1 else 0))
                else
                    evalstack.push(Value(DataType.UBYTE, if (value.array!!.any { v -> v != 0 }) 1 else 0))
            }
            Syscall.FUNC_ALL -> {
                val iterable = evalstack.pop()
                val value = heap.get(iterable.heapId)
                if (value.str != null)
                    evalstack.push(Value(DataType.UBYTE, if (Petscii.encodePetscii(value.str, true).all { c -> c != 0.toShort() }) 1 else 0))
                else
                    evalstack.push(Value(DataType.UBYTE, if (value.array!!.all { v -> v != 0 }) 1 else 0))
            }
            Syscall.FUNC_STR2BYTE -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.BYTE, y.toShort()))
            }
            Syscall.FUNC_STR2UBYTE -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                val number = (y.toInt() and 255).toShort()
                evalstack.push(Value(DataType.UBYTE, number))
            }
            Syscall.FUNC_STR2WORD -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.WORD, y.toInt()))
            }
            Syscall.FUNC_STR2UWORD -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                val number = y.toInt() and 65535
                evalstack.push(Value(DataType.UWORD, number))
            }
            Syscall.FUNC_STR2FLOAT -> {
                val strvar = evalstack.pop()
                val str = heap.get(strvar.heapId)
                val y = str.str!!.trim().trimEnd('\u0000')
                evalstack.push(Value(DataType.FLOAT, y.toDouble()))
            }
            Syscall.FUNC_WRD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE, DataType.UWORD)
                when(value.type) {
                    DataType.UBYTE, DataType.BYTE -> evalstack.push(Value(DataType.WORD, value.integerValue()))
                    DataType.UWORD -> {
                        val v2=
                            if(value.integerValue() <= 32767)
                                Value(DataType.WORD, value.integerValue())
                            else
                                Value(DataType.WORD, -((value.integerValue() xor 65535) + 1))
                        evalstack.push(v2)
                    }
                    DataType.WORD -> evalstack.push(value)
                    else -> {}
                }
            }
            Syscall.FUNC_UWRD -> {
                val value = evalstack.pop()
                checkDt(value, DataType.UBYTE, DataType.BYTE, DataType.WORD)
                when(value.type) {
                    DataType.UBYTE -> evalstack.push(Value(DataType.UWORD, value.integerValue()))
                    DataType.UWORD -> evalstack.push(value)
                    DataType.BYTE, DataType.WORD -> {
                        val v2 =
                                if(value.integerValue()>=0)
                                    Value(DataType.UWORD, value.integerValue())
                                else
                                    Value(DataType.UWORD, (abs(value.integerValue()) xor 65535) + 1)
                        evalstack.push(v2)
                    }
                    else -> {}
                }
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
        mem.setUByte(0x00a0, (jiffies ushr 16).toShort())
        mem.setUByte(0x00a1, (jiffies ushr 8 and 255).toShort())
        mem.setUByte(0x00a2, (jiffies and 255).toShort())

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
