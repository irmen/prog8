package prog8.vm

import prog8.code.StMemVar
import prog8.code.target.virtual.IVirtualMachineRunner
import prog8.intermediate.*
import java.awt.Color
import java.awt.Toolkit
import java.util.*
import kotlin.math.*
import kotlin.random.Random

/*

Virtual machine specs:

Program to execute is not stored in the system memory, it's just a separate list of instructions.
65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 virtual floating point registers (32 bits single precision floats)  fr0-fr65535
65536 bytes of memory. Thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status flags: Carry, Zero, Negative.   NOTE: status flags are only affected by the CMP instruction or explicit CLC/SEC!!!
                                             logical or arithmetic operations DO NOT AFFECT THE STATUS FLAGS UNLESS EXPLICITLY NOTED!

 */

class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pc: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(irProgram: IRProgram) {
    val memory = Memory()
    val program: Array<IRInstruction>
    val registers = Registers()
    val callStack = Stack<Int>()
    val valueStack = Stack<UByte>()       // max 128 entries
    var pc = 0
    var stepCount = 0
    var statusCarry = false
    var statusZero = false
    var statusNegative = false
    private var randomGenerator = Random(0xa55a7653)
    private var randomGeneratorFloats = Random(0xc0d3dbad)
    private val cx16virtualregsBaseAddress: Int

    init {
        program = VmProgramLoader().load(irProgram, memory).toTypedArray()
        require(program.size<=65536) {"program cannot contain more than 65536 instructions"}
        require(irProgram.st.getAsmSymbols().isEmpty()) { "virtual machine can't yet process asmsymbols defined on command line" }
        cx16virtualregsBaseAddress = (irProgram.st.lookup("cx16.r0") as? StMemVar)?.address?.toInt() ?: 0xff02
    }

    fun run() {
        try {
            var before = System.nanoTime()
            var numIns = 0
            while(true) {
                step()
                numIns++

//                if(stepCount and 32767 == 0) {
//                    Thread.sleep(1)  // avoid 100% cpu core usage
//                }

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
            Opcode.JUMPA -> throw IllegalArgumentException("vm program can't jump to system memory address (JUMPA)")
            Opcode.CALL -> InsCALL(ins)
            Opcode.SYSCALL -> InsSYSCALL(ins)
            Opcode.RETURN -> InsRETURN()
            Opcode.BSTCC -> InsBSTCC(ins)
            Opcode.BSTCS -> InsBSTCS(ins)
            Opcode.BSTEQ -> InsBSTEQ(ins)
            Opcode.BSTNE -> InsBSTNE(ins)
            Opcode.BSTNEG -> InsBSTNEG(ins)
            Opcode.BSTPOS -> InsBSTPOS(ins)
            Opcode.BSTVC, Opcode.BSTVS -> TODO("overflow status flag not yet supported in VM (BSTVC,BSTVS)")
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
            Opcode.BINARYDATA -> TODO("BINARYDATA not yet supported in VM")
            Opcode.LOADCPU -> InsLOADCPU(ins)
            Opcode.STORECPU -> InsSTORECPU(ins)
            Opcode.STOREZCPU -> InsSTOREZCPU(ins)

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

    private inline fun setResultReg(reg: Int, value: Int, type: IRDataType) {
        when(type) {
            IRDataType.BYTE -> registers.setUB(reg, value.toUByte())
            IRDataType.WORD -> registers.setUW(reg, value.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("attempt to set integer result register but float type")
        }
    }

    private fun InsPUSH(i: IRInstruction) {
        if(valueStack.size>=128)
            throw StackOverflowError("valuestack limit 128 exceeded")

        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!)
                valueStack.push(value)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!)
                valueStack.push((value and 255u).toUByte())
                valueStack.push((value.toInt() ushr 8).toUByte())
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't PUSH a float")
            }
        }
        pc++
    }

    private fun InsPOP(i: IRInstruction) {
        val value = when(i.type!!) {
            IRDataType.BYTE -> {
                valueStack.pop().toInt()
            }
            IRDataType.WORD -> {
                val msb = valueStack.pop()
                val lsb = valueStack.pop()
                (msb.toInt() shl 8) + lsb.toInt()
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't POP a float")
            }
        }
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSYSCALL(i: IRInstruction) {
        val call = Syscall.values()[i.value!!]
        SysCalls.call(call, this)
        pc++
    }

    private fun InsBREAKPOINT() {
        pc++
        throw BreakpointException(pc)
    }

    private fun InsLOADCPU(i: IRInstruction) {
        val reg = i.labelSymbol!!
        val value: UInt
        if(reg.startsWith('r')) {
            val regnum = reg.substring(1).toInt()
            val regAddr = cx16virtualregsBaseAddress + regnum*2
            value = memory.getUW(regAddr).toUInt()
        } else {
            value = when(reg) {
                "a" -> registers.cpuA.toUInt()
                "x" -> registers.cpuX.toUInt()
                "y" -> registers.cpuY.toUInt()
                "ax" -> (registers.cpuA.toUInt() shl 8) or registers.cpuX.toUInt()
                "ay" -> (registers.cpuA.toUInt() shl 8) or registers.cpuY.toUInt()
                "xy" -> (registers.cpuX.toUInt() shl 8) or registers.cpuY.toUInt()
                "pc" -> if(statusCarry) 1u else 0u
                "pz" -> if(statusZero) 1u else 0u
                "pn" -> if(statusNegative) 1u else 0u
                "pv" -> throw IllegalArgumentException("overflow status register not supported in VM")
                else -> throw IllegalArgumentException("invalid cpu reg")
            }
        }
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, value.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, value.toUShort())
            else -> throw java.lang.IllegalArgumentException("invalid cpu reg type")
        }
        pc++
    }

    private fun InsSTORECPU(i: IRInstruction) {
        val value: UInt = when(i.type!!) {
            IRDataType.BYTE -> registers.getUB(i.reg1!!).toUInt()
            IRDataType.WORD -> registers.getUW(i.reg1!!).toUInt()
            IRDataType.FLOAT -> throw IllegalArgumentException("there are no float cpu registers")
        }
        StoreCPU(value, i.type!!, i.labelSymbol!!)
        pc++
    }

    private fun InsSTOREZCPU(i: IRInstruction) {
        StoreCPU(0u, i.type!!, i.labelSymbol!!)
        pc++
    }

    private fun StoreCPU(value: UInt, dt: IRDataType, regStr: String) {
        if(regStr.startsWith('r')) {
            val regnum = regStr.substring(1).toInt()
            val regAddr = cx16virtualregsBaseAddress + regnum*2
            when(dt) {
                IRDataType.BYTE -> memory.setUB(regAddr, value.toUByte())
                IRDataType.WORD -> memory.setUW(regAddr, value.toUShort())
                else -> throw IllegalArgumentException("invalid reg dt")
            }
        } else {
            when (regStr) {
                "a" -> registers.cpuA = value.toUByte()
                "x" -> registers.cpuX = value.toUByte()
                "y" -> registers.cpuY = value.toUByte()
                "ax" -> {
                    registers.cpuA = (value and 255u).toUByte()
                    registers.cpuX = (value shr 8).toUByte()
                }
                "ay" -> {
                    registers.cpuA = (value and 255u).toUByte()
                    registers.cpuY = (value shr 8).toUByte()
                }
                "xy" -> {
                    registers.cpuX = (value and 255u).toUByte()
                    registers.cpuY = (value shr 8).toUByte()
                }
                "pc" -> statusCarry = value == 1u
                "pz" -> statusZero = value == 1u
                "pn" -> statusNegative = value == 1u
                "pv" -> throw IllegalArgumentException("overflow status register not supported in VM")
                else -> throw IllegalArgumentException("invalid cpu reg")
            }
        }
    }

    private fun InsLOAD(i: IRInstruction) {
        if(i.type==IRDataType.FLOAT)
            registers.setFloat(i.fpReg1!!, i.fpValue!!)
        else
            setResultReg(i.reg1!!, i.value!!, i.type!!)
        pc++
    }

    private fun InsLOADM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!!))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!!))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.value!!))
        }
        pc++
    }

    private fun InsLOADI(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(registers.getUW(i.reg2!!).toInt()))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(registers.getUW(i.reg2!!).toInt()))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toInt()))
        }
        pc++
    }

    private fun InsLOADX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.value!! + registers.getUW(i.reg2!!).toInt()))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.value!! + registers.getUW(i.reg2!!).toInt()))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.value!! + registers.getUW(i.reg1!!).toInt()))
        }
        pc++
    }

    private fun InsLOADIX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                registers.setUB(i.reg1!!, memory.getUB(pointer.toInt()))
            }
            IRDataType.WORD -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                registers.setUW(i.reg1!!, memory.getUW(pointer.toInt()))
            }
            IRDataType.FLOAT -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg1!!)
                registers.setFloat(i.fpReg1!!, memory.getFloat(pointer.toInt()))
            }
        }
        pc++
    }

    private fun InsLOADR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg2!!))
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg2!!))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!))
        }
        pc++
    }

    private fun InsSTOREM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.value!!, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(i.value!!, registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(i.value!!, registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREI(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt(), registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt(), registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + i.value!!, registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.value!!, registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsSTOREIX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                memory.setUB(pointer.toInt(), registers.getUB(i.reg1!!))
            }
            IRDataType.WORD -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg2!!)
                memory.setUW(pointer.toInt(), registers.getUW(i.reg1!!))
            }
            IRDataType.FLOAT -> {
                val pointer = memory.getUW(i.value!!) + registers.getUB(i.reg1!!)
                memory.setFloat(pointer.toInt(), registers.getFloat(i.fpReg1!!))
            }
        }
        pc++
    }

    private fun InsSTOREZM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.value!!, 0u)
            IRDataType.WORD -> memory.setUW(i.value!!, 0u)
            IRDataType.FLOAT -> memory.setFloat(i.value!!, 0f)
        }
        pc++
    }

    private fun InsSTOREZI(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), 0f)
        }
        pc++
    }

    private fun InsSTOREZX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt() + i.value!!, 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt() + i.value!!, 0u)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.value!!, 0f)
        }
        pc++
    }

    private fun InsJUMP(i: IRInstruction) {
        pc = i.value!!
    }

    private fun InsCALL(i: IRInstruction) {
        callStack.push(pc+1)
        pc = i.value!!
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit()
        else
            pc = callStack.pop()
    }

    private fun InsBSTCC(i: IRInstruction) {
        if(!statusCarry)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTCS(i: IRInstruction) {
        if(statusCarry)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTEQ(i: IRInstruction) {
        if(statusZero)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTNE(i: IRInstruction) {
        if(!statusZero)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTNEG(i: IRInstruction) {
        if(statusNegative)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBSTPOS(i: IRInstruction) {
        if(!statusNegative)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBZ(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)==0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            IRDataType.WORD -> {
                if(registers.getUW(i.reg1!!)==0.toUShort())
                    pc = i.value!!
                else
                    pc++
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBNZ(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)!=0.toUByte())
                    pc = i.value!!
                else
                    pc++
            }
            IRDataType.WORD -> {
                if(registers.getUW(i.reg1!!)!=0.toUShort())
                    pc = i.value!!
                else
                    pc++
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBEQ(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left==right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBNE(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left!=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLTU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left<right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLTS(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left<right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBGTU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBGTS(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBLEU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left<=right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBLES(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left<=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsBGEU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>=right)
            pc = i.value!!
        else
            pc++

    }

    private fun InsBGES(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>=right)
            pc = i.value!!
        else
            pc++
    }

    private fun InsSEQ(i: IRInstruction) {
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left==right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSNE(i: IRInstruction) {
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left!=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLT(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLTS(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGT(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGTS(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLE(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSLES(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++
    }

    private fun InsSGE(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++

    }

    private fun InsSGES(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        pc++

    }

    private fun InsINC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1!!)+1u).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1!!)+1u).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)+1f)
        }
        pc++
    }

    private fun InsINCM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, (memory.getUB(address)+1u).toUByte())
            IRDataType.WORD -> memory.setUW(address, (memory.getUW(address)+1u).toUShort())
            IRDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)+1f)
        }
        pc++
    }

    private fun InsDEC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1!!)-1u).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1!!)-1u).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)-1f)
        }
        pc++
    }

    private fun InsDECM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.value!!, (memory.getUB(i.value!!)-1u).toUByte())
            IRDataType.WORD -> memory.setUW(i.value!!, (memory.getUW(i.value!!)-1u).toUShort())
            IRDataType.FLOAT -> memory.setFloat(i.value!!, memory.getFloat(i.value!!)-1f)
        }
        pc++
    }

    private fun InsNEG(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (-registers.getUB(i.reg1!!).toInt()).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (-registers.getUW(i.reg1!!).toInt()).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, -registers.getFloat(i.fpReg1!!))
        }
        pc++
    }

    private fun InsNEGM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, (-memory.getUB(address).toInt()).toUByte())
            IRDataType.WORD -> memory.setUW(address, (-memory.getUW(address).toInt()).toUShort())
            IRDataType.FLOAT -> memory.setFloat(address, -memory.getFloat(address))
        }
        pc++
    }

    private fun InsADDR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("+", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> plusMinusMultAnyWord("+", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "+", right)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsADD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("+", i.reg1!!, i.value!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("+", i.reg1!!, i.value!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", i.fpValue!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsADDM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("+", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("+", i.reg1!!, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsSUBR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("-", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> plusMinusMultAnyWord("-", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "-", right)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsSUB(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("-", i.reg1!!, i.value!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("-", i.reg1!!, i.value!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", i.fpValue!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsSUBM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("-", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("-", i.reg1!!, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsMULR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("*", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> plusMinusMultAnyWord("*", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "*", right)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsMUL(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("*", i.reg1!!, i.value!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("*", i.reg1!!, i.value!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.fpValue!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsMULM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("*", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("*", i.reg1!!, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsDIVR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsigned("/", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divModWordUnsigned("/", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIV(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteUnsigned("/", i.reg1!!, i.value!!.toUByte())
            IRDataType.WORD -> divModConstWordUnsigned("/", i.reg1!!, i.value!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIVM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsignedInplace("/", i.reg1!!, address)
            IRDataType.WORD -> divModWordUnsignedInplace("/", i.reg1!!, address)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsDIVSR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSigned("/", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divModWordSigned("/", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "/", right)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsDIVS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteSigned("/", i.reg1!!, i.value!!.toByte())
            IRDataType.WORD -> divModConstWordSigned("/", i.reg1!!, i.value!!.toShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", i.fpValue!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        pc++
    }

    private fun InsDIVSM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSignedInplace("/", i.reg1!!, address)
            IRDataType.WORD -> divModWordSignedInplace("/", i.reg1!!, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", right)
                memory.setFloat(address, result)
            }
        }
        pc++
    }

    private fun InsMODR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsigned("%", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divModWordUnsigned("%", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteUnsigned("%", i.reg1!!, i.value!!.toUByte())
            IRDataType.WORD -> divModConstWordUnsigned("%", i.reg1!!, i.value!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsSGN(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, registers.getSB(i.reg2!!).toInt().sign.toByte())
            IRDataType.WORD -> registers.setSW(i.reg1!!, registers.getSW(i.reg2!!).toInt().sign.toShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!).sign)
        }
        pc++
    }

    private fun InsSQRT(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, sqrt(registers.getUB(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.WORD -> registers.setUB(i.reg1!!, sqrt(registers.getUW(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, sqrt(registers.getFloat(i.fpReg2!!)))
        }
        pc++
    }

    private fun InsRND(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, randomGenerator.nextInt().toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, randomGenerator.nextInt().toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, randomGeneratorFloats.nextFloat())
        }
        pc++
    }

    private fun InsCMP(i: IRInstruction) {
        val comparison: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                val reg1 = registers.getUB(i.reg1!!)
                val reg2 = registers.getUB(i.reg2!!)
                comparison = reg1.toInt() - reg2.toInt()
                statusNegative = (comparison and 0x80)==0x80
            }
            IRDataType.WORD -> {
                val reg1 = registers.getUW(i.reg1!!)
                val reg2 = registers.getUW(i.reg2!!)
                comparison = reg1.toInt() - reg2.toInt()
                statusNegative = (comparison and 0x8000)==0x8000
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
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

    private fun InsEXT(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setUW(i.reg1!!, registers.getUB(i.reg1!!).toUShort())
            IRDataType.WORD -> throw IllegalArgumentException("ext.w not yet supported, requires 32 bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsEXTS(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg1!!).toShort())
            IRDataType.WORD -> throw IllegalArgumentException("exts.w not yet supported, requires 32 bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsANDR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left and right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left and right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsAND(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) and i.value!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) and i.value!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsANDM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left and right)
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left and right)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left or right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left or right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsOR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) or i.value!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) or i.value!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsORM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left or right)
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left or right)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left xor right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left xor right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXOR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) xor i.value!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) xor i.value!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsXORM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                memory.setUB(address, left xor right)
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                memory.setUW(address, left xor right)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsINV(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!).inv())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsINVM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, memory.getUB(address).inv())
            IRDataType.WORD -> memory.setUW(address, memory.getUW(address).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRN(i: IRInstruction) {
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        statusCarry = (left and 1)!=0
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, (left shr right).toByte())
            IRDataType.WORD -> registers.setSW(i.reg1!!, (left shr right).toShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRNM(i: IRInstruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getSB(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setSB(address, (memvalue shr operand).toByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getSW(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setSW(address, (memvalue shr operand).toShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getSB(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setSB(i.reg1!!, (value shr 1).toByte())
            }
            IRDataType.WORD -> {
                val value = registers.getSW(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setSW(i.reg1!!, (value shr 1).toShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsASRM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getSB(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setSB(address, (value shr 1).toByte())
            }
            IRDataType.WORD -> {
                val value = memory.getSW(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setSW(address, (value shr 1).toShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRN(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        statusCarry = (left and 1u)!=0u
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left shr right.toInt()).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left shr right.toInt()).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRNM(i: IRInstruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setUB(address, (memvalue shr operand).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                statusCarry = (memvalue and 1)!=0
                memory.setUW(address, (memvalue shr operand).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setUB(i.reg1!!, (value shr 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt()
                statusCarry = (value and 1)!=0
                registers.setUW(i.reg1!!, (value shr 1).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSRM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setUB(address, (value shr 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = memory.getUW(address).toInt()
                statusCarry = (value and 1)!=0
                memory.setUW(address, (value shr 1).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLN(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> {
                statusCarry = (left and 0x80u)!=0u
                registers.setUB(i.reg1!!, (left shl right.toInt()).toUByte())
            }
            IRDataType.WORD -> {
                statusCarry = (left and 0x8000u)!=0u
                registers.setUW(i.reg1!!, (left shl right.toInt()).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLNM(i: IRInstruction) {
        val address = i.value!!
        val operand = registers.getUB(i.reg1!!).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                statusCarry = (memvalue and 0x80)!=0
                memory.setUB(address, (memvalue shl operand).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                statusCarry = (memvalue and 0x8000)!=0
                memory.setUW(address, (memvalue shl operand).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSL(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt()
                statusCarry = (value and 0x80)!=0
                registers.setUB(i.reg1!!, (value shl 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt()
                statusCarry = (value and 0x8000)!=0
                registers.setUW(i.reg1!!, (value shl 1).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsLSLM(i: IRInstruction) {
        val address = i.value!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(address).toInt()
                statusCarry = (value and 0x80)!=0
                memory.setUB(address, (value shl 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = memory.getUW(address).toInt()
                statusCarry = (value and 0x8000)!=0
                memory.setUW(address, (value shl 1).toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsROR(i: IRInstruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = registers.getUB(i.reg1!!)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 0x80u else 0x00u
                    (orig.toUInt().rotateRight(1) or carry).toUByte()
                } else
                    orig.rotateRight(1)
                registers.setUB(i.reg1!!, rotated)
            }
            IRDataType.WORD -> {
                val orig = registers.getUW(i.reg1!!)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 0x8000u else 0x0000u
                    (orig.toUInt().rotateRight(1) or carry).toUShort()
                } else
                    orig.rotateRight(1)
                registers.setUW(i.reg1!!, rotated)
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROR a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsRORM(i: IRInstruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        val address = i.value!!
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = memory.getUB(address)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 0x80u else 0x00u
                    (orig.toUInt().rotateRight(1) or carry).toUByte()
                } else
                    orig.rotateRight(1)
                memory.setUB(address, rotated)
            }
            IRDataType.WORD -> {
                val orig = memory.getUW(address)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 0x8000u else 0x0000u
                    (orig.toUInt().rotateRight(1) or carry).toUShort()
                } else
                    orig.rotateRight(1)
                memory.setUW(address, rotated)
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROR a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsROL(i: IRInstruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = registers.getUB(i.reg1!!)
                newStatusCarry = (orig.toInt() and 0x80) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUByte()
                } else
                    orig.rotateLeft(1)
                registers.setUB(i.reg1!!, rotated)
            }
            IRDataType.WORD -> {
                val orig = registers.getUW(i.reg1!!)
                newStatusCarry = (orig.toInt() and 0x8000) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUShort()
                } else
                    orig.rotateLeft(1)
                registers.setUW(i.reg1!!, rotated)
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsROLM(i: IRInstruction, useCarry: Boolean) {
        val address = i.value!!
        val newStatusCarry: Boolean
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = memory.getUB(address)
                newStatusCarry = (orig.toInt() and 0x80) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUByte()
                } else
                    orig.rotateLeft(1)
                memory.setUB(address, rotated)
            }
            IRDataType.WORD -> {
                val orig = memory.getUW(address)
                newStatusCarry = (orig.toInt() and 0x8000) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUShort()
                } else
                    orig.rotateLeft(1)
                memory.setUW(address, rotated)
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        pc++
        statusCarry = newStatusCarry
    }

    private fun InsMSIG(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUW(i.reg2!!)
                val newValue = value.toInt() ushr 8
                registers.setUB(i.reg1!!, newValue.toUByte())
            }
            IRDataType.WORD -> throw IllegalArgumentException("msig.w not yet supported, requires 32-bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsCONCAT(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val lsb = registers.getUB(i.reg1!!)
                val msb = registers.getUB(i.reg2!!)
                registers.setUW(i.reg1!!, ((msb.toInt() shl 8) or lsb.toInt()).toUShort())
            }
            IRDataType.WORD -> throw IllegalArgumentException("concat.w not yet supported, requires 32-bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        pc++
    }

    private fun InsFFROMUB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUB(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMSB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSB(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMUW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUW(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFFROMSW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSW(i.reg1!!).toFloat())
        pc++
    }

    private fun InsFTOUB(i: IRInstruction) {
        registers.setUB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUByte())
        pc++
    }

    private fun InsFTOUW(i: IRInstruction) {
        registers.setUW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUShort())
        pc++
    }

    private fun InsFTOSB(i: IRInstruction) {
        registers.setSB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toByte())
        pc++
    }

    private fun InsFTOSW(i: IRInstruction) {
        registers.setSW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toShort())
        pc++
    }

    private fun InsFPOW(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg1!!)
        val exponent = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, value.pow(exponent))
        pc++
    }

    private fun InsFSIN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, sin(angle))
        pc++
    }

    private fun InsFCOS(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, cos(angle))
        pc++
    }

    private fun InsFTAN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, tan(angle))
        pc++
    }

    private fun InsFATAN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, atan(angle))
        pc++
    }

    private fun InsFABS(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, abs(value))
        pc++
    }

    private fun InsFLN(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ln(value))
        pc++
    }

    private fun InsFLOG(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, log2(value))
        pc++
    }

    private fun InsFROUND(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, round(value))
        pc++
    }

    private fun InsFFLOOR(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, floor(value))
        pc++
    }

    private fun InsFCEIL(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ceil(value))
        pc++
    }

    private fun InsFCOMP(i: IRInstruction) {
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

    private fun getBranchOperands(i: IRInstruction): Pair<Int, Int> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
            IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getBranchOperandsU(i: IRInstruction): Pair<UInt, UInt> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
            IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getLogicalOperandsU(i: IRInstruction): Pair<UInt, UInt> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
            IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun getLogicalOperandsS(i: IRInstruction): Pair<Int, Int> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
            IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for logical instruction")
        }
    }

    private fun getSetOnConditionOperands(ins: IRInstruction): Pair<Int, Int> {
        return when(ins.type) {
            IRDataType.BYTE -> Pair(registers.getSB(ins.reg1!!).toInt(), registers.getSB(ins.reg2!!).toInt())
            IRDataType.WORD -> Pair(registers.getSW(ins.reg1!!).toInt(), registers.getSW(ins.reg2!!).toInt())
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't use float here")
            }
            null -> throw IllegalArgumentException("need type for branch instruction")
        }
    }

    private fun getSetOnConditionOperandsU(ins: IRInstruction): Pair<UInt, UInt> {
        return when(ins.type) {
            IRDataType.BYTE -> Pair(registers.getUB(ins.reg1!!).toUInt(), registers.getUB(ins.reg2!!).toUInt())
            IRDataType.WORD -> Pair(registers.getUW(ins.reg1!!).toUInt(), registers.getUW(ins.reg2!!).toUInt())
            IRDataType.FLOAT -> {
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

    fun randomSeed(seed1: UShort, seed2: UShort) {
        randomGenerator = Random(((seed1.toUInt() shl 16) or seed2.toUInt()).toInt())
    }

    fun randomSeedFloat(seed1: UByte, seed2: UByte, seed3: UByte) {
        val seed = (seed1.toUInt() shl 24) or (seed2.toUInt() shl 16) or (seed3.toUInt())
        randomGeneratorFloats = Random(seed.toInt())
    }
}

// probably called via reflection
class VmRunner: IVirtualMachineRunner {
    override fun runProgram(irSource: CharSequence) {
        runAndTestProgram(irSource) { /* no tests */ }
    }

    fun runAndTestProgram(irSource: CharSequence, test: (VirtualMachine) -> Unit) {
        val irProgram = IRFileReader().read(irSource)
        val vm = VirtualMachine(irProgram)
        vm.run()
        test(vm)
    }
}