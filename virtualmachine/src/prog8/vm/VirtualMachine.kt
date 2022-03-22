package prog8.vm

import java.util.*
import kotlin.math.roundToInt


class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pc: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(val memory: Memory, program: List<Instruction>) {
    val registers = Registers()
    val program: Array<Instruction> = program.toTypedArray()
    val callStack = Stack<Int>()
    var pc = 0
    var stepCount = 0

    init {
        if(program.size>65536)
            throw IllegalArgumentException("program cannot contain more than 65536 instructions")
    }

    fun run(throttle: Boolean = true) {
        try {
            var before = System.nanoTime()
            var numIns = 0
            while(true) {
                step()
                numIns++

                if(throttle && stepCount and 32767 == 0) {
                    Thread.sleep(0, 10)     // avoid 100% cpu core usage
                }

                if(stepCount and 0xffffff == 0) {
                    val now = System.nanoTime()
                    val duration = now-before
                    before = now
                    val insPerSecond = numIns*1000.0/duration
                    println("${insPerSecond.roundToInt()} MIPS")
                    numIns = 0
                }
            }
        } catch (hx: ProgramExitException) {
            println("\nProgram exit! Statuscode=${hx.status} #steps=${stepCount}")
            gfx_close()
        }
    }

    fun reset() {
        registers.reset()
        memory.reset()
        pc = 0
        stepCount = 0
        callStack.clear()
    }

    fun exit() {
        throw ProgramExitException(registers.getW(0).toInt())
    }

    fun step(count: Int=1) {
        var left=count
        while(left>0) {
            stepCount++
            dispatch()
            left--
        }
    }

    private fun dispatch() {
        if(pc >= program.size)
            exit()
        val ins = program[pc]
        when(ins.opcode) {
            Opcode.NOP -> { pc++ }
            Opcode.LOAD -> InsLOAD(ins)
            Opcode.LOADM -> InsLOADM(ins)
            Opcode.LOADX -> InsLOADX(ins)
            Opcode.LOADI -> InsLOADI(ins)
            Opcode.LOADR -> InsLOADR(ins)
            Opcode.SWAPREG -> InsSWAPREG(ins)
            Opcode.STOREM -> InsSTOREM(ins)
            Opcode.STOREX -> InsSTOREX(ins)
            Opcode.STOREI -> InsSTOREI(ins)
            Opcode.STOREZ -> InsSTOREZ(ins)
            Opcode.STOREZX -> InsSTOREZX(ins)
            Opcode.STOREZI -> InsSTOREZI(ins)
            Opcode.JUMP -> InsJUMP(ins)
            Opcode.JUMPI -> InsJUMPI(ins)
            Opcode.GOSUB -> InsGOSUB(ins)
            Opcode.GOSUBI -> InsGOSUBI(ins)
            Opcode.SYSCALL -> InsSYSCALL(ins)
            Opcode.RETURN -> InsRETURN()
            Opcode.BZ -> InsBZ(ins)
            Opcode.BNZ -> InsBNZ(ins)
            Opcode.BEQ -> InsBEQ(ins)
            Opcode.BNE -> InsBNE(ins)
            Opcode.BLT -> InsBLTU(ins)
            Opcode.BLTS -> InsBLTS(ins)
            Opcode.BGT -> InsBGTU(ins)
            Opcode.BGTS -> InsBGTS(ins)
            Opcode.BLE -> InsBLEU(ins)
            Opcode.BLES -> InsBLES(ins)
            Opcode.BGE -> InsBGEU(ins)
            Opcode.BGES -> InsBGES(ins)
            Opcode.NEG -> InsNEG(ins)
            Opcode.ADD -> InsADD(ins)
            Opcode.SUB -> InsSUB(ins)
            Opcode.MUL -> InsMul(ins)
            Opcode.DIV -> InsDiv(ins)
            Opcode.EXT -> InsEXT(ins)
            Opcode.EXTS -> InsEXTS(ins)
            Opcode.INV -> InsINV(ins)
            Opcode.AND -> InsAND(ins)
            Opcode.OR -> InsOR(ins)
            Opcode.XOR -> InsXOR(ins)
            Opcode.LSR -> InsLSR(ins)
            Opcode.LSL -> InsLSL(ins)
            Opcode.ROR -> InsROR(ins)
            Opcode.ROL -> InsROL(ins)
            Opcode.SWAP -> InsSWAP(ins)
            Opcode.COPY -> InsCOPY(ins)
            Opcode.COPYZ -> InsCOPYZ(ins)
            Opcode.BREAKPOINT -> InsBREAKPOINT()
            else -> throw IllegalArgumentException("invalid opcode ${ins.opcode}")
        }
    }

    private fun InsSYSCALL(ins: Instruction) {
        val call = Syscall.values()[ins.value!!]
        SysCalls.call(call, this)
        pc++
    }

    private fun InsBREAKPOINT() {
        pc++
        throw BreakpointException(pc)
    }

    private fun InsLOAD(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, i.value!!.toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, i.value!!.toUShort())
        }
        pc++
    }

    private fun InsLOADM(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, memory.getB(i.value!!))
            DataType.WORD -> registers.setW(i.reg1!!, memory.getW(i.value!!))
        }
        pc++
    }

    private fun InsLOADI(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, memory.getB(registers.getW(i.reg2!!).toInt()))
            DataType.WORD -> registers.setW(i.reg1!!, memory.getW(registers.getW(i.reg2!!).toInt()))
        }
        pc++
    }
    private fun InsLOADX(i: Instruction) {
        when (i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, memory.getB(i.value!! + registers.getW(i.reg2!!).toInt()))
            DataType.WORD -> registers.setW(i.reg1!!, memory.getW(i.value!! + registers.getW(i.reg2!!).toInt()))
        }
        pc++
    }

    private fun InsLOADR(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, registers.getB(i.reg2!!))
            DataType.WORD -> registers.setW(i.reg1!!, registers.getW(i.reg2!!))
        }
        pc++
    }

    private fun InsSWAPREG(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> {
                val oldR2 = registers.getB(i.reg2!!)
                registers.setB(i.reg2, registers.getB(i.reg1!!))
                registers.setB(i.reg1, oldR2)
            }
            DataType.WORD -> {
                val oldR2 = registers.getW(i.reg2!!)
                registers.setW(i.reg2, registers.getW(i.reg1!!))
                registers.setW(i.reg1, oldR2)
            }
        }
        pc++
    }

    private fun InsSTOREM(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> memory.setB(i.value!!, registers.getB(i.reg1!!))
            DataType.WORD -> memory.setW(i.value!!, registers.getW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREI(i: Instruction) {
        when (i.type!!) {
            DataType.BYTE -> memory.setB(registers.getW(i.reg2!!).toInt(), registers.getB(i.reg1!!))
            DataType.WORD -> memory.setW(registers.getW(i.reg2!!).toInt(), registers.getW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREX(i: Instruction) {
        when (i.type!!) {
            DataType.BYTE -> memory.setB(registers.getW(i.reg2!!).toInt() + i.value!!, registers.getB(i.reg1!!))
            DataType.WORD -> memory.setW(registers.getW(i.reg2!!).toInt() + i.value!!, registers.getW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREZ(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> memory.setB(i.value!!, 0u)
            DataType.WORD -> memory.setW(i.value!!, 0u)
        }
    }

    private fun InsSTOREZI(i: Instruction) {
        when (i.type!!) {
            DataType.BYTE -> memory.setB(registers.getW(i.reg2!!).toInt(), 0u)
            DataType.WORD -> memory.setW(registers.getW(i.reg2!!).toInt(), 0u)
        }
        pc++
    }
    private fun InsSTOREZX(i: Instruction) {
        when (i.type!!) {
            DataType.BYTE -> memory.setB(registers.getW(i.reg2!!).toInt() + i.value!!, 0u)
            DataType.WORD -> memory.setW(registers.getW(i.reg2!!).toInt() + i.value!!, 0u)
        }
        pc++
    }

    private fun InsJUMP(i: Instruction) {
        pc = i.value!!
    }

    private fun InsJUMPI(i: Instruction) {
        pc = registers.getW(i.reg1!!).toInt()
    }

    private fun InsGOSUB(i: Instruction) {
        callStack.push(pc+1)
        pc = i.value!!
    }

    private fun InsGOSUBI(i: Instruction) {
        callStack.push(pc+1)
        pc = registers.getW(i.reg1!!).toInt()
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit()
        else
            pc = callStack.pop()
    }

    private fun InsBZ(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> {
                if(registers.getB(i.reg1!!)==0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            DataType.WORD -> {
                if(registers.getW(i.reg1!!)==0.toUShort())
                    pc = i.value!!
                else
                    pc++
            }
        }
    }

    private fun InsBNZ(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> {
                if(registers.getB(i.reg1!!)!=0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            DataType.WORD -> {
                if(registers.getW(i.reg1!!)!=0.toUShort())
                    pc = i.value!!
                else
                    pc++
            }
        }
    }

    private fun InsBEQ(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left==right)
            pc = i.value!!
        else
            pc++
    }

    private fun getBranchOperands(i: Instruction): Pair<Int, Int> {
        return when(i.type) {
            DataType.BYTE -> Pair(registers.getB(i.reg1!!).toInt(), registers.getB(i.reg2!!).toInt())
            DataType.WORD -> Pair(registers.getW(i.reg1!!).toInt(), registers.getW(i.reg2!!).toInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getBranchOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            DataType.BYTE -> Pair(registers.getB(i.reg1!!).toUInt(), registers.getB(i.reg2!!).toUInt())
            DataType.WORD -> Pair(registers.getW(i.reg1!!).toUInt(), registers.getW(i.reg2!!).toUInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getLogicalOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            DataType.BYTE -> Pair(registers.getB(i.reg2!!).toUInt(), registers.getB(i.reg3!!).toUInt())
            DataType.WORD -> Pair(registers.getW(i.reg2!!).toUInt(), registers.getW(i.reg3!!).toUInt())
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun InsBNE(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left!=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLTU(i: Instruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left<right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLTS(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left<right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBGTU(i: Instruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBGTS(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLEU(i: Instruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left<=right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBLES(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left<=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBGEU(i: Instruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>=right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBGES(i: Instruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsNEG(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (-registers.getB(i.reg2!!).toInt()).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (-registers.getW(i.reg2!!).toInt()).toUShort())
        }
        pc++
    }

    private fun InsADD(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> arithByte("+", i.reg1!!, i.reg2!!, i.reg3!!, null)
            DataType.WORD -> arithWord("+", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsMul(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> arithByte("*", i.reg1!!, i.reg2!!, i.reg3!!, null)
            DataType.WORD -> arithWord("*", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsDiv(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> arithByte("/", i.reg1!!, i.reg2!!, i.reg3!!, null)
            DataType.WORD -> arithWord("/", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun arithByte(operator: String, reg1: Int, reg2: Int, reg3: Int?, value: UByte?) {
        val left = registers.getB(reg2)
        val right = value ?: registers.getB(reg3!!)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> {
                if(right==0.toUByte()) 0xffu
                else left / right
            }
            else -> TODO("operator $operator")
        }
        registers.setB(reg1, result.toUByte())
    }

    private fun arithWord(operator: String, reg1: Int, reg2: Int, reg3: Int?, value: UShort?) {
        val left = registers.getW(reg2)
        val right = value ?: registers.getW(reg3!!)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> {
                if(right==0.toUShort()) 0xffffu
                else left / right
            }
            else -> TODO("operator $operator")
        }
        registers.setW(reg1, result.toUShort())
    }

    private fun InsSUB(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> arithByte("-", i.reg1!!, i.reg2!!, i.reg3!!, null)
            DataType.WORD -> arithWord("-", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsEXT(i: Instruction) {
        when(i.type!!){
            DataType.BYTE -> registers.setW(i.reg1!!, registers.getB(i.reg2!!).toUShort())
            DataType.WORD -> TODO("ext.w requires 32 bits registers")
        }
        pc++
    }

    private fun InsEXTS(i: Instruction) {
        when(i.type!!){
            DataType.BYTE -> registers.setW(i.reg1!!, (registers.getB(i.reg2!!).toByte()).toUShort())
            DataType.WORD -> TODO("ext.w requires 32 bits registers")
        }
        pc++
    }

    private fun InsINV(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, registers.getB(i.reg2!!).inv())
            DataType.WORD -> registers.setW(i.reg1!!, registers.getW(i.reg2!!).inv())
        }
        pc++
    }

    private fun InsAND(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (left and right).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (left and right).toUShort())
        }
        pc++
    }

    private fun InsOR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (left or right).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (left or right).toUShort())
        }
        pc++
    }

    private fun InsXOR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (left xor right).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (left xor right).toUShort())
        }
        pc++
    }

    private fun InsLSR(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (registers.getB(i.reg2!!).toInt() ushr 1).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (registers.getW(i.reg2!!).toInt() ushr 1).toUShort())
        }
        pc++
    }

    private fun InsLSL(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (registers.getB(i.reg2!!).toInt() shl 1).toUByte())
            DataType.WORD -> registers.setW(i.reg1!!, (registers.getW(i.reg2!!).toInt() shl 1).toUShort())
        }
        pc++
    }

    private fun InsROR(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (registers.getB(i.reg2!!).toInt().rotateRight(1).toUByte()))
            DataType.WORD -> registers.setW(i.reg1!!, (registers.getW(i.reg2!!).toInt().rotateRight(1).toUShort()))
        }
        pc++
    }

    private fun InsROL(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> registers.setB(i.reg1!!, (registers.getB(i.reg2!!).toInt().rotateLeft(1).toUByte()))
            DataType.WORD -> registers.setW(i.reg1!!, (registers.getW(i.reg2!!).toInt().rotateLeft(1).toUShort()))
        }
        pc++
    }

    private fun InsCOPY(i: Instruction) = doCopy(i.reg1!!, i.reg2!!, i.reg3!!, false)

    private fun InsCOPYZ(i: Instruction) = doCopy(i.reg1!!, i.reg2!!, null, true)

    private fun doCopy(reg1: Int, reg2: Int, length: Int?, untilzero: Boolean) {
        var from = registers.getW(reg1).toInt()
        var to = registers.getW(reg2).toInt()
        if(untilzero) {
            while(true) {
                val char = memory.getB(from).toInt()
                memory.setB(to, char.toUByte())
                if(char==0)
                    break
                from++
                to++
            }
        } else {
            var len = length!!
            while(len>0) {
                val char = memory.getB(from).toInt()
                memory.setB(to, char.toUByte())
                from++
                to++
                len--
            }
        }
        pc++
    }

    private fun InsSWAP(i: Instruction) {
        when(i.type!!) {
            DataType.BYTE -> {
                val value = registers.getW(i.reg2!!)
                val newValue = value.toUByte()*256u + (value.toInt() ushr 8).toUInt()
                registers.setW(i.reg1!!, newValue.toUShort())
            }
            DataType.WORD -> TODO("swap.w requires 32-bits registers")
        }
        pc++
    }

    private var window: GraphicsWindow? = null

    fun gfx_enable() {
        window = when(registers.getB(0).toInt()) {
            0 -> GraphicsWindow(320, 240, 3)
            1 -> GraphicsWindow(640, 480, 2)
            else -> throw IllegalArgumentException("invalid screen mode")
        }
        window!!.start()
    }

    fun gfx_clear() {
        window?.clear(registers.getB(0).toInt())
    }

    fun gfx_plot() {
        window?.plot(registers.getW(0).toInt(), registers.getW(1).toInt(), registers.getB(2).toInt())
    }

    fun gfx_close() {
        window?.close()
    }
}
