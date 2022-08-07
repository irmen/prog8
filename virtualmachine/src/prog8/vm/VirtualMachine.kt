package prog8.vm

import prog8.code.target.virtual.IVirtualMachineRunner
import java.awt.Color
import java.awt.Toolkit
import java.util.*
import kotlin.math.*
import kotlin.random.Random


class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pc: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(val memory: Memory, program: List<Instruction>) {
    val registers = Registers()
    val program: Array<Instruction> = program.toTypedArray()
    val callStack = Stack<Int>()
    val valueStack = Stack<UByte>()       // max 128 entries
    var pc = 0
    var stepCount = 0
    var statusCarry = false
    var statusZero = false
    var statusNegative = false

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
                    Thread.sleep(1)  // avoid 100% cpu core usage
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
        // memory.reset()
        pc = 0
        stepCount = 0
        callStack.clear()
        statusCarry = false
        statusNegative = false
        statusZero = false
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
            Opcode.LOADIX -> InsLOADIX(ins)
            Opcode.LOADR -> InsLOADR(ins)
            Opcode.STOREM -> InsSTOREM(ins)
            Opcode.STOREX -> InsSTOREX(ins)
            Opcode.STOREIX -> InsSTOREIX(ins)
            Opcode.STOREI -> InsSTOREI(ins)
            Opcode.STOREZM -> InsSTOREZM(ins)
            Opcode.STOREZX -> InsSTOREZX(ins)
            Opcode.STOREZI -> InsSTOREZI(ins)
            Opcode.JUMP -> InsJUMP(ins)
            Opcode.CALL -> InsCALL(ins)
            Opcode.SYSCALL -> InsSYSCALL(ins)
            Opcode.RETURN -> InsRETURN()
            Opcode.BSTCC -> InsBSTCC(ins)
            Opcode.BSTCS -> InsBSTCS(ins)
            Opcode.BSTEQ -> InsBSTEQ(ins)
            Opcode.BSTNE -> InsBSTNE(ins)
            Opcode.BSTNEG -> InsBSTNEG(ins)
            Opcode.BSTPOS -> InsBSTPOS(ins)
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
            Opcode.INCM -> InsINCM(ins)
            Opcode.DEC -> InsDEC(ins)
            Opcode.DECM -> InsDECM(ins)
            Opcode.NEG -> InsNEG(ins)
            Opcode.NEGM -> InsNEGM(ins)
            Opcode.ADDR -> InsADDR(ins)
            Opcode.ADD -> InsADD(ins)
            Opcode.ADDM -> InsADDM(ins)
            Opcode.SUBR -> InsSUBR(ins)
            Opcode.SUB -> InsSUB(ins)
            Opcode.SUBM -> InsSUBM(ins)
            Opcode.MULR -> InsMULR(ins)
            Opcode.MUL -> InsMUL(ins)
            Opcode.MULM -> InsMULM(ins)
            Opcode.DIVR -> InsDIVR(ins)
            Opcode.DIV -> InsDIV(ins)
            Opcode.DIVM -> InsDIVM(ins)
            Opcode.DIVSR -> InsDIVSR(ins)
            Opcode.DIVS -> InsDIVS(ins)
            Opcode.DIVSM -> InsDIVSM(ins)
            Opcode.MODR -> InsMODR(ins)
            Opcode.MOD -> InsMOD(ins)
            Opcode.SGN -> InsSGN(ins)
            Opcode.CMP -> InsCMP(ins)
            Opcode.RND -> InsRND(ins)
            Opcode.SQRT -> InsSQRT(ins)
            Opcode.EXT -> InsEXT(ins)
            Opcode.EXTS -> InsEXTS(ins)
            Opcode.ANDR -> InsANDR(ins)
            Opcode.AND -> InsAND(ins)
            Opcode.ANDM -> InsANDM(ins)
            Opcode.ORR -> InsORR(ins)
            Opcode.OR -> InsOR(ins)
            Opcode.ORM -> InsORM(ins)
            Opcode.XORR -> InsXORR(ins)
            Opcode.XOR -> InsXOR(ins)
            Opcode.XORM ->InsXORM(ins)
            Opcode.INV -> InsINV(ins)
            Opcode.INVM -> InsINVM(ins)
            Opcode.ASRN -> InsASRN(ins)
            Opcode.LSRN -> InsLSRN(ins)
            Opcode.LSLN -> InsLSLN(ins)
            Opcode.ASR -> InsASR(ins)
            Opcode.LSR -> InsLSR(ins)
            Opcode.LSL -> InsLSL(ins)
            Opcode.ASRNM -> InsASRNM(ins)
            Opcode.LSRNM -> InsLSRNM(ins)
            Opcode.LSLNM -> InsLSLNM(ins)
            Opcode.ASRM -> InsASRM(ins)
            Opcode.LSRM -> InsLSRM(ins)
            Opcode.LSLM -> InsLSLM(ins)
            Opcode.ROR -> InsROR(ins, false)
            Opcode.RORM -> InsRORM(ins, false)
            Opcode.ROXR -> InsROR(ins, true)
            Opcode.ROXRM -> InsRORM(ins, true)
            Opcode.ROL -> InsROL(ins, false)
            Opcode.ROLM -> InsROLM(ins, false)
            Opcode.ROXL -> InsROL(ins, true)
            Opcode.ROXLM -> InsROLM(ins, true)
            Opcode.MSIG -> InsMSIG(ins)
            Opcode.CONCAT -> InsCONCAT(ins)
            Opcode.PUSH -> InsPUSH(ins)
            Opcode.POP -> InsPOP(ins)
            Opcode.BREAKPOINT -> InsBREAKPOINT()
            Opcode.CLC -> { statusCarry = false; pc++ }
            Opcode.SEC -> { statusCarry = true; pc++ }

            Opcode.FFROMUB -> InsFFROMUB(ins)
            Opcode.FFROMSB -> InsFFROMSB(ins)
            Opcode.FFROMUW -> InsFFROMUW(ins)
            Opcode.FFROMSW -> InsFFROMSW(ins)
            Opcode.FTOUB -> InsFTOUB(ins)
            Opcode.FTOSB -> InsFTOSB(ins)
            Opcode.FTOUW -> InsFTOUW(ins)
            Opcode.FTOSW -> InsFTOSW(ins)
            Opcode.FPOW -> InsFPOW(ins)
            Opcode.FABS -> InsFABS(ins)
            Opcode.FSIN -> InsFSIN(ins)
            Opcode.FCOS -> InsFCOS(ins)
            Opcode.FTAN -> InsFTAN(ins)
            Opcode.FATAN -> InsFATAN(ins)
            Opcode.FLN -> InsFLN(ins)
            Opcode.FLOG -> InsFLOG(ins)
            Opcode.FROUND -> InsFROUND(ins)
            Opcode.FFLOOR -> InsFFLOOR(ins)
            Opcode.FCEIL -> InsFCEIL(ins)
            Opcode.FCOMP -> InsFCOMP(ins)
            else -> throw IllegalArgumentException("invalid opcode ${ins.opcode}")
        }
    }

    private inline fun setResultReg(reg: Int, value: Int, type: VmDataType) {
        when(type) {
            VmDataType.BYTE -> registers.setUB(reg, value.toUByte())
            VmDataType.WORD -> registers.setUW(reg, value.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("attempt to set integer result register but float type")
        }
    }

    private fun InsPUSH(i: Instruction) {
        if(valueStack.size>=128)
            throw StackOverflowError("valuestack limit 128 exceeded")

        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!)
                valueStack.push(value)
            }
            VmDataType.WORD -> {
                val value = registers.getUW(i.reg1!!)
                valueStack.push((value and 255u).toUByte())
                valueStack.push((value.toInt() ushr 8).toUByte())
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't PUSH a float")
            }
        }
        pc++
    }

    private fun InsPOP(i: Instruction) {
        val value = when(i.type!!) {
            VmDataType.BYTE -> {
                valueStack.pop().toInt()
            }
            VmDataType.WORD -> {
                val msb = valueStack.pop()
                val lsb = valueStack.pop()
                (msb.toInt() shl 8) + lsb.toInt()
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't POP a float")
            }
        }
        setResultReg(i.reg1!!, value, i.type)
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
        if(i.type==VmDataType.FLOAT)
            registers.setFloat(i.fpReg1!!, i.fpValue!!)
        else
            setResultReg(i.reg1!!, i.value!!, i.type!!)
        pc++
    }

    private fun InsLOADM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!!))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!!))
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.value!!))
        }
        pc++
    }

    private fun InsLOADI(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(registers.getUW(i.reg2!!).toInt()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(registers.getUW(i.reg2!!).toInt()))
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toInt()))
        }
        pc++
    }

    private fun InsLOADX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!! + registers.getUW(i.reg2!!).toInt()))
            VmDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!! + registers.getUW(i.reg2!!).toInt()))
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.value!! + registers.getUW(i.reg1!!).toInt()))
        }
        pc++
    }

    private fun InsLOADIX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                registers.setUB(i.reg1!!, memory.getUB(pointer.toInt()))
            }
            VmDataType.WORD -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                registers.setUW(i.reg1!!, memory.getUW(pointer.toInt()))
            }
            VmDataType.FLOAT -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg1!!)
                registers.setFloat(i.fpReg1!!, memory.getFloat(pointer.toInt()))
            }
        }
        pc++
    }

    private fun InsLOADR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg2!!))
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg2!!))
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!))
        }
        pc++
    }

    private fun InsSTOREM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(i.value!!, registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(i.value!!, registers.getUW(i.reg1!!))
            VmDataType.FLOAT -> memory.setFloat(i.value!!, registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREI(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt(), registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt(), registers.getUW(i.reg1!!))
            VmDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUB(i.reg1!!))
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUW(i.reg1!!))
            VmDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.value!!, registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREIX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                memory.setUB(pointer.toInt(), registers.getUB(i.reg1!!))
            }
            VmDataType.WORD -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                memory.setUW(pointer.toInt(), registers.getUW(i.reg1!!))
            }
            VmDataType.FLOAT -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg1!!)
                memory.setFloat(pointer.toInt(), registers.getFloat(i.fpReg1!!))
            }
        }
        pc++
    }

    private fun InsSTOREZM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(i.value!!, 0u)
            VmDataType.WORD -> memory.setUW(i.value!!, 0u)
            VmDataType.FLOAT -> memory.setFloat(i.value!!, 0f)
        }
        pc++
    }

    private fun InsSTOREZI(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt(), 0u)
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt(), 0u)
            VmDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), 0f)
        }
        pc++
    }

    private fun InsSTOREZX(i: Instruction) {
        when (i.type!!) {
            VmDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt() + i.value!!, 0u)
            VmDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt() + i.value!!, 0u)
            VmDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.value!!, 0f)
        }
        pc++
    }

    private fun InsJUMP(i: Instruction) {
        pc = i.value!!
    }

    private fun InsCALL(i: Instruction) {
        callStack.push(pc+1)
        pc = i.value!!
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit()
        else
            pc = callStack.pop()
    }

    private fun InsBSTCC(i: Instruction) {
        if(!statusCarry)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTCS(i: Instruction) {
        if(statusCarry)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTEQ(i: Instruction) {
        if(statusZero)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTNE(i: Instruction) {
        if(!statusZero)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTNEG(i: Instruction) {
        if(statusNegative)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTPOS(i: Instruction) {
        if(!statusNegative)
            pc = i.value!!
        else
            pc++
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
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
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
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
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
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left==right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSNE(i: Instruction) {
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left!=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLT(i: Instruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLTS(i: Instruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGT(i: Instruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGTS(i: Instruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLE(i: Instruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLES(i: Instruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGE(i: Instruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++

    }

    private fun InsSGES(i: Instruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++

    }

    private fun InsINC(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1)+1u).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1)+1u).toUShort())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1)+1f)
        }
        pc++
    }

    private fun InsINCM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(address, (memory.getUB(address)+1u).toUByte())
            VmDataType.WORD -> memory.setUW(address, (memory.getUW(address)+1u).toUShort())
            VmDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)+1f)
        }
        pc++
    }

    private fun InsDEC(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1)-1u).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1)-1u).toUShort())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1)-1f)
        }
        pc++
    }

    private fun InsDECM(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(i.value!!, (memory.getUB(i.value)-1u).toUByte())
            VmDataType.WORD -> memory.setUW(i.value!!, (memory.getUW(i.value)-1u).toUShort())
            VmDataType.FLOAT -> memory.setFloat(i.value!!, memory.getFloat(i.value)-1f)
        }
        pc++
    }

    private fun InsNEG(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (-registers.getUB(i.reg1).toInt()).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (-registers.getUW(i.reg1).toInt()).toUShort())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, -registers.getFloat(i.fpReg1))
        }
        pc++
    }

    private fun InsNEGM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(address, (-memory.getUB(address).toInt()).toUByte())
            VmDataType.WORD -> memory.setUW(address, (-memory.getUW(address).toInt()).toUShort())
            VmDataType.FLOAT -> memory.setFloat(address, -memory.getFloat(address))
        }
        pc++
    }

    private fun InsADDR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByte("+", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> plusMinusMultAnyWord("+", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "+", right)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsADD(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultConstByte("+", i.reg1!!, i.value!!.toUByte())
            VmDataType.WORD -> plusMinusMultConstWord("+", i.reg1!!, i.value!!.toUShort())
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", i.fpValue!!)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsADDM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByteInplace("+", i.reg1!!, address)
            VmDataType.WORD -> plusMinusMultAnyWordInplace("+", i.reg1!!, address)
            VmDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsSUBR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByte("-", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> plusMinusMultAnyWord("-", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "-", right)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsSUB(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultConstByte("-", i.reg1!!, i.value!!.toUByte())
            VmDataType.WORD -> plusMinusMultConstWord("-", i.reg1!!, i.value!!.toUShort())
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", i.fpValue!!)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsSUBM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByteInplace("-", i.reg1!!, address)
            VmDataType.WORD -> plusMinusMultAnyWordInplace("-", i.reg1!!, address)
            VmDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsMULR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByte("*", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> plusMinusMultAnyWord("*", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "*", right)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsMUL(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultConstByte("*", i.reg1!!, i.value!!.toUByte())
            VmDataType.WORD -> plusMinusMultConstWord("*", i.reg1!!, i.value!!.toUShort())
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.fpValue!!)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsMULM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> plusMinusMultAnyByteInplace("*", i.reg1!!, address)
            VmDataType.WORD -> plusMinusMultAnyWordInplace("*", i.reg1!!, address)
            VmDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsDIVR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModByteUnsigned("/", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> divModWordUnsigned("/", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIV(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModConstByteUnsigned("/", i.reg1!!, i.value!!.toUByte())
            VmDataType.WORD -> divModConstWordUnsigned("/", i.reg1!!, i.value!!.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIVM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> divModByteUnsignedInplace("/", i.reg1!!, address)
            VmDataType.WORD -> divModWordUnsignedInplace("/", i.reg1!!, address)
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIVSR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModByteSigned("/", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> divModWordSigned("/", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "/", right)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsDIVS(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModConstByteSigned("/", i.reg1!!, i.value!!.toByte())
            VmDataType.WORD -> divModConstWordSigned("/", i.reg1!!, i.value!!.toShort())
            VmDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", i.fpValue!!)
                registers.setFloat(i.fpReg1, result)
            }
        }
        pc++
    }

    private fun InsDIVSM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> divModByteSignedInplace("/", i.reg1!!, address)
            VmDataType.WORD -> divModWordSignedInplace("/", i.reg1!!, address)
            VmDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsMODR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModByteUnsigned("%", i.reg1!!, i.reg2!!)
            VmDataType.WORD -> divModWordUnsigned("%", i.reg1!!, i.reg2!!)
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsMOD(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> divModConstByteUnsigned("%", i.reg1!!, i.value!!.toUByte())
            VmDataType.WORD -> divModConstWordUnsigned("%", i.reg1!!, i.value!!.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsSGN(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setSB(i.reg1!!, registers.getSB(i.reg2!!).toInt().sign.toByte())
            VmDataType.WORD -> registers.setSW(i.reg1!!, registers.getSW(i.reg2!!).toInt().sign.toShort())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!).sign)
        }
        pc++
    }

    private fun InsSQRT(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, sqrt(registers.getUB(i.reg2!!).toDouble()).toInt().toUByte())
            VmDataType.WORD -> registers.setUB(i.reg1!!, sqrt(registers.getUW(i.reg2!!).toDouble()).toInt().toUByte())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, sqrt(registers.getFloat(i.fpReg2!!)))
        }
        pc++
    }

    private fun InsRND(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, Random.nextInt().toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, Random.nextInt().toUShort())
            VmDataType.FLOAT -> registers.setFloat(i.fpReg1!!, Random.nextFloat())
        }
        pc++
    }

    private fun InsCMP(i: Instruction) {
        val comparison: Int
        when(i.type!!) {
            VmDataType.BYTE -> {
                val reg1 = registers.getUB(i.reg1!!)
                val reg2 = registers.getUB(i.reg2!!)
                comparison = reg1.toInt() - reg2.toInt()
                statusNegative = (comparison and 0x80)==0x80
            }
            VmDataType.WORD -> {
                val reg1 = registers.getUW(i.reg1!!)
                val reg2 = registers.getUW(i.reg2!!)
                comparison = reg1.toInt() - reg2.toInt()
                statusNegative = (comparison and 0x8000)==0x8000
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        if(comparison==0){
            statusZero = true
            statusCarry = true
        } else if(comparison>0) {
            statusZero = false
            statusCarry = true
        } else {
            statusZero = false
            statusCarry = false
        }
        pc++
    }

    private fun plusMinusMultAnyByte(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getUB(reg1)
        val right = registers.getUB(reg2)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setUB(reg1, result.toUByte())
    }

    private fun plusMinusMultConstByte(operator: String, reg1: Int, value: UByte) {
        val left = registers.getUB(reg1)
        val result = when(operator) {
            "+" -> left + value
            "-" -> left - value
            "*" -> left * value
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setUB(reg1, result.toUByte())
    }

    private fun plusMinusMultAnyByteInplace(operator: String, reg1: Int, address: Int) {
        val memvalue = memory.getUB(address)
        val operand = registers.getUB(reg1)
        val result = when(operator) {
            "+" -> memvalue + operand
            "-" -> memvalue - operand
            "*" -> memvalue * operand
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        memory.setUB(address, result.toUByte())
    }

    private fun divModByteSigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getSB(reg1)
        val right = registers.getSB(reg2)
        val result = when(operator) {
            "/" -> {
                if(right==0.toByte()) 127
                else left / right
            }
            "%" -> {
                if(right==0.toByte()) 127
                else left % right
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setSB(reg1, result.toByte())
    }

    private fun divModConstByteSigned(operator: String, reg1: Int, value: Byte) {
        val left = registers.getSB(reg1)
        val result = when(operator) {
            "/" -> {
                if(value==0.toByte()) 127
                else left / value
            }
            "%" -> {
                if(value==0.toByte()) 127
                else left % value
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setSB(reg1, result.toByte())
    }

    private fun divModByteSignedInplace(operator: String, reg1: Int, address: Int) {
        val left = memory.getSB(address)
        val right = registers.getSB(reg1)
        val result = when(operator) {
            "/" -> {
                if(right==0.toByte()) 127
                else left / right
            }
            "%" -> {
                if(right==0.toByte()) 127
                else left % right
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        memory.setSB(address, result.toByte())
    }

    private fun divModByteUnsigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getUB(reg1)
        val right = registers.getUB(reg2)
        val result = when(operator) {
            "/" -> {
                if(right==0.toUByte()) 0xffu
                else left / right
            }
            "%" -> {
                if(right==0.toUByte()) 0xffu
                else left % right
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setUB(reg1, result.toUByte())
    }

    private fun divModConstByteUnsigned(operator: String, reg1: Int, value: UByte) {
        val left = registers.getUB(reg1)
        val result = when(operator) {
            "/" -> {
                if(value==0.toUByte()) 0xffu
                else left / value
            }
            "%" -> {
                if(value==0.toUByte()) 0xffu
                else left % value
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setUB(reg1, result.toUByte())
    }

    private fun divModByteUnsignedInplace(operator: String, reg1: Int, address: Int) {
        val left = memory.getUB(address)
        val right = registers.getUB(reg1)
        val result = when(operator) {
            "/" -> {
                if(right==0.toUByte()) 0xffu
                else left / right
            }
            "%" -> {
                if(right==0.toUByte()) 0xffu
                else left % right
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        memory.setUB(address, result.toUByte())
    }

    private fun plusMinusMultAnyWord(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getUW(reg1)
        val right = registers.getUW(reg2)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun plusMinusMultConstWord(operator: String, reg1: Int, value: UShort) {
        val left = registers.getUW(reg1)
        val result = when(operator) {
            "+" -> left + value
            "-" -> left - value
            "*" -> left * value
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun plusMinusMultAnyWordInplace(operator: String, reg1: Int, address: Int) {
        val memvalue = memory.getUW(address)
        val operand = registers.getUW(reg1)
        val result = when(operator) {
            "+" -> memvalue + operand
            "-" -> memvalue - operand
            "*" -> memvalue * operand
            else -> throw IllegalArgumentException("operator word $operator")
        }
        memory.setUW(address, result.toUShort())
    }

    private fun divModWordUnsigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getUW(reg1)
        val right = registers.getUW(reg2)
        val result = when(operator) {
            "/" -> {
                if(right==0.toUShort()) 0xffffu
                else left / right
            }
            "%" -> {
                if(right==0.toUShort()) 0xffffu
                else left % right
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun divModConstWordUnsigned(operator: String, reg1: Int, value: UShort) {
        val left = registers.getUW(reg1)
        val result = when(operator) {
            "/" -> {
                if(value==0.toUShort()) 0xffffu
                else left / value
            }
            "%" -> {
                if(value==0.toUShort()) 0xffffu
                else left % value
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun divModWordUnsignedInplace(operator: String, reg1: Int, address: Int) {
        val left = memory.getUW(address)
        val right = registers.getUW(reg1)
        val result = when(operator) {
            "/" -> {
                if(right==0.toUShort()) 0xffffu
                else left / right
            }
            "%" -> {
                if(right==0.toUShort()) 0xffffu
                else left % right
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        memory.setUW(address, result.toUShort())
    }

    private fun divModWordSigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getSW(reg1)
        val right = registers.getSW(reg2)
        val result = when(operator) {
            "/" -> {
                if(right==0.toShort()) 32767
                else left / right
            }
            "%" -> {
                if(right==0.toShort()) 32767
                else left % right
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setSW(reg1, result.toShort())
    }

    private fun divModConstWordSigned(operator: String, reg1: Int, value: Short) {
        val left = registers.getSW(reg1)
        val result = when(operator) {
            "/" -> {
                if(value==0.toShort()) 32767
                else left / value
            }
            "%" -> {
                if(value==0.toShort()) 32767
                else left % value
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setSW(reg1, result.toShort())
    }

    private fun divModWordSignedInplace(operator: String, reg1: Int, address: Int) {
        val left = memory.getSW(address)
        val right = registers.getSW(reg1)
        val result = when(operator) {
            "/" -> {
                if(right==0.toShort()) 32767
                else left / right
            }
            "%" -> {
                if(right==0.toShort()) 32767
                else left % right
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        memory.setSW(address, result.toShort())
    }

    private fun arithFloat(left: Float, operator: String, right: Float): Float = when(operator) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        "/" -> {
            if(right==0f) Float.MAX_VALUE
            else left / right
        }
        "%" -> {
            if(right==0f) Float.MAX_VALUE
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }

    private fun InsEXT(i: Instruction) {
        when(i.type!!){
            VmDataType.BYTE -> registers.setUW(i.reg1!!, registers.getUB(i.reg1).toUShort())
            VmDataType.WORD -> throw IllegalArgumentException("ext.w not yet supported, requires 32 bits registers")
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsEXTS(i: Instruction) {
        when(i.type!!){
            VmDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg1).toShort())
            VmDataType.WORD -> throw IllegalArgumentException("exts.w not yet supported, requires 32 bits registers")
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsANDR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left and right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left and right).toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsAND(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1) and i.value!!.toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1) and i.value!!.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsANDM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left and right)
            }
            VmDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left and right)
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsORR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left or right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left or right).toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsOR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1) or i.value!!.toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1) or i.value!!.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsORM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left or right)
            }
            VmDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left or right)
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXORR(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left xor right).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left xor right).toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXOR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1) xor i.value!!.toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1) xor i.value!!.toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXORM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left xor right)
            }
            VmDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left xor right)
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsINV(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1).inv())
            VmDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1).inv())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsINVM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> memory.setUB(address, memory.getUB(address).inv())
            VmDataType.WORD -> memory.setUW(address, memory.getUW(address).inv())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRN(i: Instruction) {
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        statusCarry = (left and 1)!=0
        when(i.type!!) {
            VmDataType.BYTE -> registers.setSB(i.reg1!!, (left shr right).toByte())
            VmDataType.WORD -> registers.setSW(i.reg1!!, (left shr right).toShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRNM(i: Instruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            VmDataType.BYTE -> {
                val memvalue = memory.getSB(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setSB(address, (memvalue shr operand).toByte())
            }
            VmDataType.WORD -> {
                val memvalue = memory.getSW(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setSW(address, (memvalue shr operand).toShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getSB(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setSB(i.reg1, (value shr 1).toByte())
            }
            VmDataType.WORD -> {
                val value = registers.getSW(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setSW(i.reg1, (value shr 1).toShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = memory.getSB(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setSB(address, (value shr 1).toByte())
            }
            VmDataType.WORD -> {
                val value = memory.getSW(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setSW(address, (value shr 1).toShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRN(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        statusCarry = (left and 1u)!=0u
        when(i.type!!) {
            VmDataType.BYTE -> registers.setUB(i.reg1!!, (left shr right.toInt()).toUByte())
            VmDataType.WORD -> registers.setUW(i.reg1!!, (left shr right.toInt()).toUShort())
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRNM(i: Instruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            VmDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setUB(address, (memvalue shr operand).toUByte())
            }
            VmDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setUW(address, (memvalue shr operand).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSR(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setUB(i.reg1, (value shr 1).toUByte())
            }
            VmDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setUW(i.reg1, (value shr 1).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = memory.getUB(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setUB(address, (value shr 1).toUByte())
            }
            VmDataType.WORD -> {
                val value = memory.getUW(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setUW(address, (value shr 1).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLN(i: Instruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            VmDataType.BYTE -> {
                statusCarry = (left and 0x80u)!=0u
                registers.setUB(i.reg1!!, (left shl right.toInt()).toUByte())
            }
            VmDataType.WORD -> {
                statusCarry = (left and 0x8000u)!=0u
                registers.setUW(i.reg1!!, (left shl right.toInt()).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLNM(i: Instruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            VmDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                statusCarry = (memvalue and 0x80)!=0
                memory.setUB(address, (memvalue shl operand).toUByte())
            }
            VmDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                statusCarry = (memvalue and 0x8000)!=0
                memory.setUW(address, (memvalue shl operand).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSL(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt()
                statusCarry = (value and 0x80)!=0
                registers.setUB(i.reg1, (value shl 1).toUByte())
            }
            VmDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt()
                statusCarry = (value and 0x8000)!=0
                registers.setUW(i.reg1, (value shl 1).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLM(i: Instruction) {
        val address = i.value!!
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = memory.getUB(address).toInt()
                statusCarry = (value and 0x80)!=0
                memory.setUB(address, (value shl 1).toUByte())
            }
            VmDataType.WORD -> {
                val value = memory.getUW(address).toInt()
                statusCarry = (value and 0x8000)!=0
                memory.setUW(address, (value shl 1).toUShort())
            }
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsROR(i: Instruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        when (i.type!!) {
            VmDataType.BYTE -> {
                val orig = registers.getUB(i.reg1!!)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 0x80u else 0x00u
                    (orig.toUInt().rotateRight(1) or carry).toUByte()
                } else
                    orig.rotateRight(1)
                registers.setUB(i.reg1, rotated)
            }
            VmDataType.WORD -> {
                val orig = registers.getUW(i.reg1!!)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 0x8000u else 0x0000u
                    (orig.toUInt().rotateRight(1) or carry).toUShort()
                } else
                    orig.rotateRight(1)
                registers.setUW(i.reg1, rotated)
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROR a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsRORM(i: Instruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        val address = i.value!!
        when (i.type!!) {
            VmDataType.BYTE -> {
                val orig = memory.getUB(address)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 0x80u else 0x00u
                    (orig.toUInt().rotateRight(1) or carry).toUByte()
                } else
                    orig.rotateRight(1)
                memory.setUB(address, rotated)
            }
            VmDataType.WORD -> {
                val orig = memory.getUW(address)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 0x8000u else 0x0000u
                    (orig.toUInt().rotateRight(1) or carry).toUShort()
                } else
                    orig.rotateRight(1)
                memory.setUW(address, rotated)
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROR a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsROL(i: Instruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        when (i.type!!) {
            VmDataType.BYTE -> {
                val orig = registers.getUB(i.reg1!!)
                newStatusCarry = (orig.toInt() and 0x80) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUByte()
                } else
                    orig.rotateLeft(1)
                registers.setUB(i.reg1, rotated)
            }
            VmDataType.WORD -> {
                val orig = registers.getUW(i.reg1!!)
                newStatusCarry = (orig.toInt() and 0x8000) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUShort()
                } else
                    orig.rotateLeft(1)
                registers.setUW(i.reg1, rotated)
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsROLM(i: Instruction, useCarry: Boolean) {
        val address = i.value!!
        val newStatusCarry: Boolean
        when (i.type!!) {
            VmDataType.BYTE -> {
                val orig = memory.getUB(address)
                newStatusCarry = (orig.toInt() and 0x80) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUByte()
                } else
                    orig.rotateLeft(1)
                memory.setUB(address, rotated)
            }
            VmDataType.WORD -> {
                val orig = memory.getUW(address)
                newStatusCarry = (orig.toInt() and 0x8000) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUShort()
                } else
                    orig.rotateLeft(1)
                memory.setUW(address, rotated)
            }
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsMSIG(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val value = registers.getUW(i.reg2!!)
                val newValue = value.toInt() ushr 8
                registers.setUB(i.reg1!!, newValue.toUByte())
            }
            VmDataType.WORD -> throw IllegalArgumentException("msig.w not yet supported, requires 32-bits registers")
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsCONCAT(i: Instruction) {
        when(i.type!!) {
            VmDataType.BYTE -> {
                val lsb = registers.getUB(i.reg1!!)
                val msb = registers.getUB(i.reg2!!)
                registers.setUW(i.reg1, ((msb.toInt() shl 8) or lsb.toInt()).toUShort())
            }
            VmDataType.WORD -> throw IllegalArgumentException("concat.w not yet supported, requires 32-bits registers")
            VmDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsFFROMUB(i: Instruction) {
        registers.setFloat(i.fpReg1!!, registers.getUB(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMSB(i: Instruction) {
        registers.setFloat(i.fpReg1!!, registers.getSB(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMUW(i: Instruction) {
        registers.setFloat(i.fpReg1!!, registers.getUW(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMSW(i: Instruction) {
        registers.setFloat(i.fpReg1!!, registers.getSW(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFTOUB(i: Instruction) {
        registers.setUB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUByte())
        pc++
    }

    private fun InsFTOUW(i: Instruction) {
        registers.setUW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUShort())
        pc++
    }

    private fun InsFTOSB(i: Instruction) {
        registers.setSB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toByte())
        pc++
    }

    private fun InsFTOSW(i: Instruction) {
        registers.setSW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toShort())
        pc++
    }

    private fun InsFPOW(i: Instruction) {
        val value = registers.getFloat(i.fpReg1!!)
        val exponent = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1, value.pow(exponent))
        pc++
    }

    private fun InsFSIN(i: Instruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, sin(angle))
        pc++
    }

    private fun InsFCOS(i: Instruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, cos(angle))
        pc++
    }

    private fun InsFTAN(i: Instruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, tan(angle))
        pc++
    }

    private fun InsFATAN(i: Instruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, atan(angle))
        pc++
    }

    private fun InsFABS(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, abs(value))
        pc++
    }

    private fun InsFLN(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ln(value))
        pc++
    }

    private fun InsFLOG(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, log2(value))
        pc++
    }

    private fun InsFROUND(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, round(value))
        pc++
    }

    private fun InsFFLOOR(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, floor(value))
        pc++
    }

    private fun InsFCEIL(i: Instruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ceil(value))
        pc++
    }

    private fun InsFCOMP(i: Instruction) {
        val left = registers.getFloat(i.fpReg1!!)
        val right = registers.getFloat(i.fpReg2!!)
        val result =
            if(left<right)
                255u        // -1
            else if(left>right)
                1u
            else
                0u
        registers.setUB(i.reg1!!, result.toUByte())
        pc++
    }

    private fun getBranchOperands(i: Instruction): Pair<Int, Int> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
            VmDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getBranchOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
            VmDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getLogicalOperandsU(i: Instruction): Pair<UInt, UInt> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
            VmDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun getLogicalOperandsS(i: Instruction): Pair<Int, Int> {
        return when(i.type) {
            VmDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
            VmDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun getSetOnConditionOperands(ins: Instruction): Pair<Int, Int> {
        return when(ins.type) {
            VmDataType.BYTE -> Pair(registers.getSB(ins.reg1!!).toInt(), registers.getSB(ins.reg2!!).toInt())
            VmDataType.WORD -> Pair(registers.getSW(ins.reg1!!).toInt(), registers.getSW(ins.reg2!!).toInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getSetOnConditionOperandsU(ins: Instruction): Pair<UInt, UInt> {
        return when(ins.type) {
            VmDataType.BYTE -> Pair(registers.getUB(ins.reg1!!).toUInt(), registers.getUB(ins.reg2!!).toUInt())
            VmDataType.WORD -> Pair(registers.getUW(ins.reg1!!).toUInt(), registers.getUW(ins.reg2!!).toUInt())
            VmDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
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

    fun gfx_getpixel() {
        if(window==null)
            registers.setUB(0, 0u)
        else {
            val color = Color(window!!.getpixel(registers.getUW(0).toInt(), registers.getUW(1).toInt()))
            registers.setUB(0, color.green.toUByte())
        }
    }

    fun gfx_close() {
        window?.close()
    }

    fun waitvsync() {
        Toolkit.getDefaultToolkit().sync()      // not really the same as wait on vsync, but there's noting else
    }
}

// probably called via reflection
class VmRunner: IVirtualMachineRunner {
    override fun runProgram(source: String, throttle: Boolean) {
        val (memsrc, programsrc) = source.split("------PROGRAM------".toRegex(), 2)
        val memory = Memory()
        val assembler = Assembler()
        assembler.initializeMemory(memsrc, memory)
        val program = assembler.assembleProgram(programsrc)
        val vm = VirtualMachine(memory, program)
        vm.run(throttle = true)
    }
}