package prog8.vm

import java.awt.Toolkit
import java.util.*
import kotlin.math.roundToInt


class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pc: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(val memory: Memory, program: List<Instruction>) {
    val registers = Registers()
    val program: Array<Instruction> = program.toTypedArray()
    val callStack = Stack<Int>()
    val valueStack = Stack<Int>()       // max 128 entries
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
        throw ProgramExitException(registers.getUW(0).toInt())
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
            Opcode.CALL -> InsCALL(ins)
            Opcode.CALLI -> InsCALLI(ins)
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
            Opcode.SEQ -> InsSEQ(ins)
            Opcode.SNE -> InsSNE(ins)
            Opcode.SLT -> InsSLT(ins)
            Opcode.SLTS -> InsSLTS(ins)
            Opcode.SGT -> InsSGT(ins)
            Opcode.SGTS -> InsSGTS(ins)
            Opcode.SLE -> InsSLE(ins)
            Opcode.SLES -> InsSLES(ins)
            Opcode.SGE -> InsSGE(ins)
            Opcode.SGES -> InsSGES(ins)

            Opcode.INC -> InsINC(ins)
            Opcode.DEC -> InsDEC(ins)
            Opcode.NEG -> InsNEG(ins)
            Opcode.ADD -> InsADD(ins)
            Opcode.SUB -> InsSUB(ins)
            Opcode.MUL -> InsMUL(ins)
            Opcode.DIV -> InsDIV(ins)
            Opcode.MOD -> InsMOD(ins)
            Opcode.EXT -> InsEXT(ins)
            Opcode.EXTS -> InsEXTS(ins)
            Opcode.AND -> InsAND(ins)
            Opcode.OR -> InsOR(ins)
            Opcode.XOR -> InsXOR(ins)
            Opcode.ASR -> InsASR(ins)
            Opcode.LSR -> InsLSR(ins)
            Opcode.LSL -> InsLSL(ins)
            Opcode.ROR -> InsROR(ins)
            Opcode.ROL -> InsROL(ins)
            Opcode.SWAP -> InsSWAP(ins)
            Opcode.CONCAT -> InsCONCAT(ins)
            Opcode.PUSH -> InsPUSH(ins)
            Opcode.POP -> InsPOP(ins)
            Opcode.COPY -> InsCOPY(ins)
            Opcode.COPYZ -> InsCOPYZ(ins)
            Opcode.BREAKPOINT -> InsBREAKPOINT()
            else -> throw IllegalArgumentException("invalid opcode ${ins.opcode}")
        }
    }

    private inline fun setResultReg(reg: Int, value: Int, type: VmDataType) {
        when(type) {
            VmDataType.BYTE -> registers.setUB(reg, value.toUByte())
            VmDataType.WORD -> registers.setUW(reg, value.toUShort())
        }
    }

    private fun InsPUSH(i: Instruction) {
        if(valueStack.size>=128)
            throw StackOverflowError("valuestack limit 128 exceeded")

        val value = when(i.type!!) {
            VmDataType.BYTE -> registers.getUB(i.reg1!!).toInt()
            VmDataType.WORD -> registers.getUW(i.reg1!!).toInt()
        }
        valueStack.push(value)
        pc++
    }

    private fun InsPOP(i: Instruction) {
        val value = valueStack.pop()
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSYSCALL(i: Instruction) {
        val call = Syscall.values()[i.value!!]
        SysCalls.call(call, this)
        pc++
    }

    private fun InsBREAKPOINT() {
        pc++
        throw BreakpointException(pc)
    }

    private fun InsLOAD(i: Instruction) {
        setResultReg(i.reg1!!, i.value!!, i.type!!)
        pc++
    }

    private fun InsLOADM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!!))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!!))
        }
        pc++
    }

    private fun InsLOADI(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(registers.getUW(i.reg2!!).toInt()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(registers.getUW(i.reg2!!).toInt()))
        }
        pc++
    }

    private fun InsLOADX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!! + registers.getUW(i.reg2!!).toInt()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!! + registers.getUW(i.reg2!!).toInt()))
        }
        pc++
    }

    private fun InsLOADR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg2!!))
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg2!!))
        }
        pc++
    }

    private fun InsSWAPREG(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val oldR2 = registers.getUB(i.reg2!!)
                registers.setUB(i.reg2, registers.getUB(i.reg1!!))
                registers.setUB(i.reg1, oldR2)
            }
            VmDataType.WORD -> {
                val oldR2 = registers.getUW(i.reg2!!)
                registers.setUW(i.reg2, registers.getUW(i.reg1!!))
                registers.setUW(i.reg1, oldR2)
            }
        }
        pc++
    }

    private fun InsSTOREM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(i.value!!, registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(i.value!!, registers.getUW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREI(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt(), registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt(), registers.getUW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUW(i.reg1!!))
        }
        pc++
    }

    private fun InsSTOREZ(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(i.value!!, 0u)
            VmDataType.WORD -> memory.setUW(i.value!!, 0u)
        }
    }

    private fun InsSTOREZI(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt(), 0u)
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt(), 0u)
        }
        pc++
    }

    private fun InsSTOREZX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + i.value!!, 0u)
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + i.value!!, 0u)
        }
        pc++
    }

    private fun InsJUMP(i: Instruction) {
        pc = i.value!!
    }

    private fun InsJUMPI(i: Instruction) {
        pc = registers.getUW(i.reg1!!).toInt()
    }

    private fun InsCALL(i: Instruction) {
        callStack.push(pc+1)
        pc = i.value!!
    }

    private fun InsCALLI(i: Instruction) {
        callStack.push(pc+1)
        pc = registers.getUW(i.reg1!!).toInt()
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit()
        else
            pc = callStack.pop()
    }

    private fun InsBZ(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)==0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            VmDataType.WORD -> {
                if(registers.getUW(i.reg1!!)==0.toUShort())
                    pc = i.value!!
                else
                    pc++
            }
        }
    }

    private fun InsBNZ(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)!=0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            VmDataType.WORD -> {
                if(registers.getUW(i.reg1!!)!=0.toUShort())
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

    private fun InsSEQ(i: Instruction) {
        val (resultReg: Int, left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left==right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSNE(i: Instruction) {
        val (resultReg: Int, left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left!=right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSLT(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSLTS(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperands(i)
        val value = if(left<right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSGT(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSGTS(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperands(i)
        val value = if(left>right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSLE(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<=right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSLES(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperands(i)
        val value = if(left<=right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++
    }

    private fun InsSGE(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>=right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++

    }

    private fun InsSGES(i: Instruction) {
        val (resultReg, left, right) = getSetOnConditionOperands(i)
        val value = if(left>=right) 1 else 0
        setResultReg(resultReg, value, i.type!!)
        pc++

    }

    private fun InsINC(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1)+1u).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1)+1u).toUShort())
        }
        pc++
    }

    private fun InsDEC(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1)-1u).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1)-1u).toUShort())
        }
        pc++
    }

    private fun InsNEG(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (-registers.getUB(i.reg1).toInt()).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (-registers.getUW(i.reg1).toInt()).toUShort())
        }
        pc++
    }

    private fun InsADD(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> arithByte("+", i.reg1!!, i.reg2!!, i.reg3!!, null)
            VmDataType.WORD -> arithWord("+", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsMUL(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> arithByte("*", i.reg1!!, i.reg2!!, i.reg3!!, null)
            VmDataType.WORD -> arithWord("*", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsDIV(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> arithByte("/", i.reg1!!, i.reg2!!, i.reg3!!, null)
            VmDataType.WORD -> arithWord("/", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsMOD(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> arithByte("%", i.reg1!!, i.reg2!!, i.reg3!!, null)
            VmDataType.WORD -> arithWord("%", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun arithByte(operator: String, reg1: Int, reg2: Int, reg3: Int?, value: UByte?) {
        val left = registers.getUB(reg2)
        val right = value ?: registers.getUB(reg3!!)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> {
                if(right==0.toUByte()) 0xffu
                else left / right
            }
            "%" -> {
                if(right==0.toUByte()) 0xffu
                else left % right
            }
            else -> TODO("operator $operator")
        }
        registers.setUB(reg1, result.toUByte())
    }

    private fun arithWord(operator: String, reg1: Int, reg2: Int, reg3: Int?, value: UShort?) {
        val left = registers.getUW(reg2)
        val right = value ?: registers.getUW(reg3!!)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> {
                if(right==0.toUShort()) 0xffffu
                else left / right
            }
            "%" -> {
                if(right==0.toUShort()) 0xffffu
                else left % right
            }
            else -> TODO("operator $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun InsSUB(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> arithByte("-", i.reg1!!, i.reg2!!, i.reg3!!, null)
            VmDataType.WORD -> arithWord("-", i.reg1!!, i.reg2!!, i.reg3!!, null)
        }
        pc++
    }

    private fun InsEXT(i: Instruction) {
        when(i.type!!){
            VmDataType.BYTE -> registers.setUW(i.reg1!!, registers.getUB(i.reg1).toUShort())
            VmDataType.WORD -> TODO("ext.w requires 32 bits registers")
        }
        pc++
    }

    private fun InsEXTS(i: Instruction) {
        when(i.type!!){
            VmDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg1).toShort())
            VmDataType.WORD -> TODO("ext.w requires 32 bits registers")
        }
        pc++
    }

    private fun InsAND(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left and right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left and right).toUShort())
        }
        pc++
    }

    private fun InsOR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left or right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left or right).toUShort())
        }
        pc++
    }

    private fun InsXOR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left xor right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left xor right).toUShort())
        }
        pc++
    }

    private fun InsASR(i: Instruction) {
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setSB(i.reg1!!, (left shr right).toByte())
            VmDataType.WORD -> registers.setSW(i.reg1!!, (left shr right).toShort())
        }
        pc++
    }

    private fun InsLSR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left shr right.toInt()).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left shr right.toInt()).toUShort())
        }
        pc++
    }

    private fun InsLSL(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left shl right.toInt()).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left shl right.toInt()).toUShort())
        }
        pc++
    }

    private fun InsROR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left.rotateRight(right.toInt()).toUByte()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left.rotateRight(right.toInt()).toUShort()))
        }
        pc++
    }

    private fun InsROL(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left.rotateLeft(right.toInt()).toUByte()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left.rotateLeft(right.toInt()).toUShort()))
        }
        pc++
    }

    private fun InsSWAP(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getUW(i.reg1!!)
                val newValue = 0xea31 // value.toUByte()*256u + (value.toInt() ushr 8).toUInt()
                registers.setUW(i.reg1, newValue.toUShort())
            }
            VmDataType.WORD -> TODO("swap.w requires 32-bits registers")
        }
        pc++
    }

    private fun InsCONCAT(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val msb = registers.getUB(i.reg2!!)
                val lsb = registers.getUB(i.reg3!!)
                registers.setUW(i.reg1!!, ((msb.toInt() shl 8) or lsb.toInt()).toUShort())
            }
            VmDataType.WORD -> TODO("swap.w requires 32-bits registers")
        }
        pc++
    }

    private fun InsCOPY(i: Instruction) = doCopy(i.reg1!!, i.reg2!!, i.reg3!!, false)

    private fun InsCOPYZ(i: Instruction) = doCopy(i.reg1!!, i.reg2!!, null, true)

    private fun doCopy(reg1: Int, reg2: Int, length: Int?, untilzero: Boolean) {
        var from = registers.getUW(reg1).toInt()
        var to = registers.getUW(reg2).toInt()
        if(untilzero) {
            while(true) {
                val char = memory.getUB(from)
                memory.setUB(to, char)
                if(char.toInt()==0)
                    break
                from++
                to++
            }
        } else {
            var len = length!!
            while(len>0) {
                val char = memory.getUB(from)
                memory.setUB(to, char)
                from++
                to++
                len--
            }
        }
        pc++
    }

    private fun getBranchOperands(i: Instruction): Pair<Int, Int> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
            VmDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getBranchOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
            VmDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getLogicalOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getUB(i.reg2!!).toUInt(), registers.getUB(i.reg3!!).toUInt())
            VmDataType.WORD -> Pair(registers.getUW(i.reg2!!).toUInt(), registers.getUW(i.reg3!!).toUInt())
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun getLogicalOperandsS(i: Instruction): Pair<Int, Int> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getSB(i.reg2!!).toInt(), registers.getSB(i.reg3!!).toInt())
            VmDataType.WORD -> Pair(registers.getSW(i.reg2!!).toInt(), registers.getSW(i.reg3!!).toInt())
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }


    private fun getSetOnConditionOperands(ins: Instruction): Triple<Int, Int, Int> {
        return when(ins.type) {
            VmDataType.BYTE -> Triple(ins.reg1!!, registers.getSB(ins.reg2!!).toInt(), registers.getSB(ins.reg3!!).toInt())
            VmDataType.WORD -> Triple(ins.reg1!!, registers.getSW(ins.reg2!!).toInt(), registers.getSW(ins.reg3!!).toInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getSetOnConditionOperandsU(ins: Instruction): Triple<Int, UInt, UInt> {
        return when(ins.type) {
            VmDataType.BYTE -> Triple(ins.reg1!!, registers.getUB(ins.reg2!!).toUInt(), registers.getUB(ins.reg3!!).toUInt())
            VmDataType.WORD -> Triple(ins.reg1!!, registers.getUW(ins.reg2!!).toUInt(), registers.getUW(ins.reg3!!).toUInt())
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private var window: GraphicsWindow? = null

    fun gfx_enable() {
        window = when(registers.getUB(0).toInt()) {
            0 -> GraphicsWindow(320, 240, 3)
            1 -> GraphicsWindow(640, 480, 2)
            else -> throw IllegalArgumentException("invalid screen mode")
        }
        window!!.start()
    }

    fun gfx_clear() {
        window?.clear(registers.getUB(0).toInt())
    }

    fun gfx_plot() {
        window?.plot(registers.getUW(0).toInt(), registers.getUW(1).toInt(), registers.getUB(2).toInt())
    }

    fun gfx_close() {
        window?.close()
    }

    fun waitvsync() {
        Toolkit.getDefaultToolkit().sync()      // not really the same as wait on vsync, but there's noting else
    }
}
