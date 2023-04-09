package prog8.vm

import prog8.code.StMemVar
import prog8.code.core.toHex
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


class BreakpointException(val pcChunk: IRCodeChunk, val pcIndex: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(irProgram: IRProgram) {
    class CallSiteContext(val returnChunk: IRCodeChunk, val returnIndex: Int, val returnValueReg: Int?, val returnValueFpReg: Int?)
    val memory = Memory()
    val program: List<IRCodeChunk>
    val registers = Registers()
    val callStack = Stack<CallSiteContext>()
    val valueStack = Stack<UByte>()       // max 128 entries
    var breakpointHandler: ((pcChunk: IRCodeChunk, pcIndex: Int) -> Unit)? = null       // can set custom breakpoint handler
    var pcChunk = IRCodeChunk(null, null)
    var pcIndex = 0
    var stepCount = 0
    var statusCarry = false
    var statusZero = false
    var statusNegative = false
    internal var randomGenerator = Random(0xa55a7653)
    internal var randomGeneratorFloats = Random(0xc0d3dbad)
    private val cx16virtualregsBaseAddress: Int

    init {
        program = VmProgramLoader().load(irProgram, memory)
        require(irProgram.st.getAsmSymbols().isEmpty()) { "virtual machine can't yet process asmsymbols defined on command line" }
        cx16virtualregsBaseAddress = (irProgram.st.lookup("cx16.r0") as? StMemVar)?.address?.toInt() ?: 0xff02
        reset(false)
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

    fun reset(clearMemory: Boolean) {
        // "reset" the VM without erasing the currently loaded program
        // this allows you to re-run the program multiple times without having to reload it
        registers.reset()
        if(clearMemory)
            memory.reset()
        pcIndex = 0
        pcChunk = program.firstOrNull() ?: IRCodeChunk(null, null)
        stepCount = 0
        callStack.clear()
        valueStack.clear()
        statusCarry = false
        statusNegative = false
        statusZero = false
    }

    fun exit(statuscode: Int) {
        throw ProgramExitException(statuscode)
    }

    fun step(count: Int=1) {
        var left=count
        while(left>0) {
            if(pcIndex >= pcChunk.instructions.size) {
                stepNextChunk()
            }
            stepCount++
            dispatch(pcChunk.instructions[pcIndex])
            left--
        }
    }

    private fun stepNextChunk() {
        do {
            val nextChunk = pcChunk.next
            when (nextChunk) {
                is IRCodeChunk -> {
                    pcChunk = nextChunk
                    pcIndex = 0
                }

                null -> {
                    exit(0)   // end of program reached
                }

                else -> {
                    throw IllegalArgumentException("VM cannot run code from non-code chunk $nextChunk")
                }
            }
        } while (pcChunk.isEmpty())
    }

    private fun nextPc() {
        pcIndex ++
        if(pcIndex>=pcChunk.instructions.size)
            stepNextChunk()
    }

    private fun branchTo(i: IRInstruction) {
        val target = i.branchTarget
        when (target) {
            is IRCodeChunk -> {
                pcChunk = target
                pcIndex = 0
            }
            null -> {
                if(i.address!=null)
                    throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${i.address!!.toHex()})")
                else
                    throw IllegalArgumentException("no branchtarget in $i")
            }
            else -> throw IllegalArgumentException("VM can't execute code in a non-codechunk: $target")
        }
    }

    private fun dispatch(ins: IRInstruction) {
        when(ins.opcode) {
            Opcode.NOP -> nextPc()
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
            Opcode.CALLRVAL -> InsCALLRVAL(ins)
            Opcode.SYSCALL -> InsSYSCALL(ins)
            Opcode.RETURN -> InsRETURN()
            Opcode.RETURNREG -> InsRETURNREG(ins)
            Opcode.BSTCC -> InsBSTCC(ins)
            Opcode.BSTCS -> InsBSTCS(ins)
            Opcode.BSTEQ -> InsBSTEQ(ins)
            Opcode.BSTNE -> InsBSTNE(ins)
            Opcode.BSTNEG -> InsBSTNEG(ins)
            Opcode.BSTPOS -> InsBSTPOS(ins)
            Opcode.BSTVC, Opcode.BSTVS -> TODO("overflow status flag not yet supported in VM (BSTVC,BSTVS)")
            Opcode.BZ -> InsBZ(ins)
            Opcode.BNZ -> InsBNZ(ins)
            Opcode.BGZS -> InsBGZS(ins)
            Opcode.BGEZS -> InsBGEZS(ins)
            Opcode.BLZS -> InsBLZS(ins)
            Opcode.BLEZS -> InsBLEZS(ins)
            Opcode.BEQ -> InsBEQ(ins)
            Opcode.BNE -> InsBNE(ins)
            Opcode.BGT -> InsBGTU(ins)
            Opcode.BGTS -> InsBGTS(ins)
            Opcode.BGE -> InsBGEU(ins)
            Opcode.BGES -> InsBGES(ins)
            Opcode.SZ -> InsSZ(ins)
            Opcode.SNZ -> InsSNZ(ins)
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
            Opcode.DIVMODR -> InsDIVMODR(ins)
            Opcode.DIVMOD -> InsDIVMOD(ins)
            Opcode.SGN -> InsSGN(ins)
            Opcode.CMP -> InsCMP(ins)
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
            Opcode.CLC -> { statusCarry = false; nextPc() }
            Opcode.SEC -> { statusCarry = true; nextPc() }
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
        nextPc()
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
        nextPc()
    }

    private fun InsSYSCALL(i: IRInstruction) {
        val call = Syscall.values()[i.immediate!!]
        SysCalls.call(call, this)
        nextPc()
    }

    private fun InsBREAKPOINT() {
        nextPc()
        if(breakpointHandler!=null)
            breakpointHandler?.invoke(pcChunk, pcIndex)
        else
            throw BreakpointException(pcChunk, pcIndex)
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
        nextPc()
    }

    private fun InsSTORECPU(i: IRInstruction) {
        val value: UInt = when(i.type!!) {
            IRDataType.BYTE -> registers.getUB(i.reg1!!).toUInt()
            IRDataType.WORD -> registers.getUW(i.reg1!!).toUInt()
            IRDataType.FLOAT -> throw IllegalArgumentException("there are no float cpu registers")
        }
        StoreCPU(value, i.type!!, i.labelSymbol!!)
        nextPc()
    }

    private fun InsSTOREZCPU(i: IRInstruction) {
        StoreCPU(0u, i.type!!, i.labelSymbol!!)
        nextPc()
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
            registers.setFloat(i.fpReg1!!, i.immediateFp!!)
        else {
            if(i.immediate!=null)
                setResultReg(i.reg1!!, i.immediate!!, i.type!!)
            else {
                if(i.labelSymbol==null)
                    throw IllegalArgumentException("expected LOAD of address of labelsymbol")
                setResultReg(i.reg1!!, i.address!!, i.type!!)
            }
        }
        nextPc()
    }

    private fun InsLOADM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.address!!))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.address!!))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!!))
        }
        nextPc()
    }

    private fun InsLOADI(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(registers.getUW(i.reg2!!).toInt()))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(registers.getUW(i.reg2!!).toInt()))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toInt()))
        }
        nextPc()
    }

    private fun InsLOADX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, memory.getUB(i.address!! + registers.getUW(i.reg2!!).toInt()))
            IRDataType.WORD -> registers.setUW(i.reg1!!, memory.getUW(i.address!! + registers.getUW(i.reg2!!).toInt()))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!! + registers.getUW(i.reg1!!).toInt()))
        }
        nextPc()
    }

    private fun InsLOADIX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                registers.setUB(i.reg1!!, memory.getUB(pointer.toInt()))
            }
            IRDataType.WORD -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                registers.setUW(i.reg1!!, memory.getUW(pointer.toInt()))
            }
            IRDataType.FLOAT -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg1!!)
                registers.setFloat(i.fpReg1!!, memory.getFloat(pointer.toInt()))
            }
        }
        nextPc()
    }

    private fun InsLOADR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg2!!))
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg2!!))
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!))
        }
        nextPc()
    }

    private fun InsSTOREM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(i.address!!, registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(i.address!!, registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREI(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt(), registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt(), registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + i.address!!, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + i.address!!, registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.address!!, registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREIX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                memory.setUB(pointer.toInt(), registers.getUB(i.reg1!!))
            }
            IRDataType.WORD -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                memory.setUW(pointer.toInt(), registers.getUW(i.reg1!!))
            }
            IRDataType.FLOAT -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg1!!)
                memory.setFloat(pointer.toInt(), registers.getFloat(i.fpReg1!!))
            }
        }
        nextPc()
    }

    private fun InsSTOREZM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!, 0u)
            IRDataType.WORD -> memory.setUW(i.address!!, 0u)
            IRDataType.FLOAT -> memory.setFloat(i.address!!, 0f)
        }
        nextPc()
    }

    private fun InsSTOREZI(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), 0f)
        }
        nextPc()
    }

    private fun InsSTOREZX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt() + i.address!!, 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt() + i.address!!, 0u)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + i.address!!, 0f)
        }
        nextPc()
    }

    private fun InsJUMP(i: IRInstruction) {
        branchTo(i)
    }

    private fun InsCALL(i: IRInstruction) {
        callStack.push(CallSiteContext(pcChunk, pcIndex+1, null, null))
        branchTo(i)
    }

    private fun InsCALLRVAL(i: IRInstruction) {
        callStack.push(CallSiteContext(pcChunk, pcIndex+1, i.reg1, i.fpReg1))
        branchTo(i)
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit(0)
        else {
            val context = callStack.pop()
            pcChunk = context.returnChunk
            pcIndex = context.returnIndex
            // ignore any return values.
        }
    }

    private fun InsRETURNREG(i: IRInstruction) {
        if(callStack.isEmpty())
            exit(0)
        else {
            val context = callStack.pop()
            when (i.type!!) {
                IRDataType.BYTE -> {
                    if(context.returnValueReg!=null)
                        registers.setUB(context.returnValueReg, registers.getUB(i.reg1!!))
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(context.returnValueReg!=null)
                        registers.setUW(context.returnValueReg, registers.getUW(i.reg1!!))
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.FLOAT -> {
                    if(context.returnValueFpReg!=null)
                        registers.setFloat(context.returnValueFpReg, registers.getFloat(i.fpReg1!!))
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
            }
            pcChunk = context.returnChunk
            pcIndex = context.returnIndex
        }
    }

    private fun InsBSTCC(i: IRInstruction) {
        if(!statusCarry)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTCS(i: IRInstruction) {
        if(statusCarry)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTEQ(i: IRInstruction) {
        if(statusZero)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTNE(i: IRInstruction) {
        if(!statusZero)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTNEG(i: IRInstruction) {
        if(statusNegative)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTPOS(i: IRInstruction) {
        if(!statusNegative)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBZ(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)==0.toUByte())
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getUW(i.reg1!!)==0.toUShort())
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBNZ(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getUB(i.reg1!!)!=0.toUByte())
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getUW(i.reg1!!)!=0.toUShort())
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBGZS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getSB(i.reg1!!)>0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getSW(i.reg1!!)>0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBGEZS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getSB(i.reg1!!)>=0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getSW(i.reg1!!)>=0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBLZS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getSB(i.reg1!!)<0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getSW(i.reg1!!)<0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBLEZS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                if(registers.getSB(i.reg1!!)<=0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.WORD -> {
                if(registers.getSW(i.reg1!!)<=0)
                    branchTo(i)
                else
                    nextPc()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
    }

    private fun InsBEQ(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left==right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBNE(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left!=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGTU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()

    }

    private fun InsBGTS(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGEU(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()

    }

    private fun InsBGES(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsSZ(i: IRInstruction) {
        val (_: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(right==0) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSNZ(i: IRInstruction) {
        val (_: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(right!=0) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSEQ(i: IRInstruction) {
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left==right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSNE(i: IRInstruction) {
        val (left: Int, right: Int) = getSetOnConditionOperands(i)
        val value = if(left!=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSLT(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSLTS(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSGT(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSGTS(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSLE(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSLES(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left<=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()
    }

    private fun InsSGE(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperandsU(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()

    }

    private fun InsSGES(i: IRInstruction) {
        val (left, right) = getSetOnConditionOperands(i)
        val value = if(left>=right) 1 else 0
        setResultReg(i.reg1!!, value, i.type!!)
        nextPc()

    }

    private fun InsINC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1!!)+1u).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1!!)+1u).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)+1f)
        }
        nextPc()
    }

    private fun InsINCM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, (memory.getUB(address)+1u).toUByte())
            IRDataType.WORD -> memory.setUW(address, (memory.getUW(address)+1u).toUShort())
            IRDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)+1f)
        }
        nextPc()
    }

    private fun InsDEC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (registers.getUB(i.reg1!!)-1u).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (registers.getUW(i.reg1!!)-1u).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)-1f)
        }
        nextPc()
    }

    private fun InsDECM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!, (memory.getUB(i.address!!)-1u).toUByte())
            IRDataType.WORD -> memory.setUW(i.address!!, (memory.getUW(i.address!!)-1u).toUShort())
            IRDataType.FLOAT -> memory.setFloat(i.address!!, memory.getFloat(i.address!!)-1f)
        }
        nextPc()
    }

    private fun InsNEG(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (-registers.getUB(i.reg1!!).toInt()).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (-registers.getUW(i.reg1!!).toInt()).toUShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, -registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsNEGM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, (-memory.getUB(address).toInt()).toUByte())
            IRDataType.WORD -> memory.setUW(address, (-memory.getUW(address).toInt()).toUShort())
            IRDataType.FLOAT -> memory.setFloat(address, -memory.getFloat(address))
        }
        nextPc()
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
        nextPc()
    }

    private fun InsADD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("+", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("+", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsADDM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsSUB(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("-", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("-", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsSUBM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsMUL(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("*", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("*", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsMULM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsDIVR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModByteUnsigned("/", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divOrModWordUnsigned("/", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIV(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("/", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("/", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsignedInplace("/", i.reg1!!, address)
            IRDataType.WORD -> divModWordUnsignedInplace("/", i.reg1!!, address)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
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
        nextPc()
    }

    private fun InsDIVS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteSigned("/", i.reg1!!, i.immediate!!.toByte())
            IRDataType.WORD -> divModConstWordSigned("/", i.reg1!!, i.immediate!!.toShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsDIVSM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsMODR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModByteUnsigned("%", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divOrModWordUnsigned("%", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("%", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("%", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMODR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModUByte(i.reg1!!, i.reg2!!)        // output in r0+r1
            IRDataType.WORD -> divAndModUWord(i.reg1!!, i.reg2!!)        // output in r0+r1
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstUByte(i.reg1!!, i.immediate!!.toUByte())    // output in r0+r1
            IRDataType.WORD -> divAndModConstUWord(i.reg1!!, i.immediate!!.toUShort())   // output in r0+r1
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSGN(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, registers.getSB(i.reg2!!).toInt().sign.toByte())
            IRDataType.WORD -> registers.setSW(i.reg1!!, registers.getSW(i.reg2!!).toInt().sign.toShort())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!).sign)
        }
        nextPc()
    }

    private fun InsSQRT(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, sqrt(registers.getUB(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.WORD -> registers.setUB(i.reg1!!, sqrt(registers.getUW(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, sqrt(registers.getFloat(i.fpReg2!!)))
        }
        nextPc()
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
        nextPc()
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

    private fun divOrModByteUnsigned(operator: String, reg1: Int, reg2: Int) {
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

    private fun divOrModConstByteUnsigned(operator: String, reg1: Int, value: UByte) {
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

    private fun divAndModUByte(reg1: Int, reg2: Int) {
        val left = registers.getUB(reg1)
        val right = registers.getUB(reg2)
        val division = if(right==0.toUByte()) 0xffu else left / right
        val remainder = if(right==0.toUByte()) 0xffu else left % right
        registers.setUB(0, division.toUByte())
        registers.setUB(1, remainder.toUByte())
    }

    private fun divAndModConstUByte(reg1: Int, value: UByte) {
        val left = registers.getUB(reg1)
        val division = if(value==0.toUByte()) 0xffu else left / value
        val remainder = if(value==0.toUByte()) 0xffu else left % value
        registers.setUB(0, division.toUByte())
        registers.setUB(1, remainder.toUByte())
    }

    private fun divAndModUWord(reg1: Int, reg2: Int) {
        val left = registers.getUW(reg1)
        val right = registers.getUW(reg2)
        val division = if(right==0.toUShort()) 0xffffu else left / right
        val remainder = if(right==0.toUShort()) 0xffffu else left % right
        registers.setUW(0, division.toUShort())
        registers.setUW(1, remainder.toUShort())
    }

    private fun divAndModConstUWord(reg1: Int, value: UShort) {
        val left = registers.getUW(reg1)
        val division = if(value==0.toUShort()) 0xffffu else left / value
        val remainder = if(value==0.toUShort()) 0xffffu else left % value
        registers.setUW(0, division.toUShort())
        registers.setUW(1, remainder.toUShort())
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

    private fun divOrModWordUnsigned(operator: String, reg1: Int, reg2: Int) {
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

    private fun divOrModConstWordUnsigned(operator: String, reg1: Int, value: UShort) {
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
        nextPc()
    }

    private fun InsEXTS(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg1!!).toShort())
            IRDataType.WORD -> throw IllegalArgumentException("exts.w not yet supported, requires 32 bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsANDR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left and right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left and right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsAND(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) and i.immediate!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) and i.immediate!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsANDM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left or right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left or right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsOR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) or i.immediate!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) or i.immediate!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsORM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsXORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left xor right).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left xor right).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsXOR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!) xor i.immediate!!.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!) xor i.immediate!!.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsXORM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsINV(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, registers.getUB(i.reg1!!).inv())
            IRDataType.WORD -> registers.setUW(i.reg1!!, registers.getUW(i.reg1!!).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsINVM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, memory.getUB(address).inv())
            IRDataType.WORD -> memory.setUW(address, memory.getUW(address).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRN(i: IRInstruction) {
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        statusCarry = (left and 1)!=0
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, (left shr right).toByte())
            IRDataType.WORD -> registers.setSW(i.reg1!!, (left shr right).toShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRNM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsASRM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
    }

    private fun InsLSRN(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        statusCarry = (left and 1u)!=0u
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, (left shr right.toInt()).toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, (left shr right.toInt()).toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRNM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsLSRM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsLSLNM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
    }

    private fun InsLSLM(i: IRInstruction) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsRORM(i: IRInstruction, useCarry: Boolean) {
        val newStatusCarry: Boolean
        val address = i.address!!
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
        nextPc()
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
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsROLM(i: IRInstruction, useCarry: Boolean) {
        val address = i.address!!
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
        nextPc()
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
        nextPc()
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
        nextPc()
    }

    private fun InsFFROMUB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUB(i.reg1!!).toFloat())
        nextPc()
    }

    private fun InsFFROMSB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSB(i.reg1!!).toFloat())
        nextPc()
    }

    private fun InsFFROMUW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUW(i.reg1!!).toFloat())
        nextPc()
    }

    private fun InsFFROMSW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSW(i.reg1!!).toFloat())
        nextPc()
    }

    private fun InsFTOUB(i: IRInstruction) {
        registers.setUB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUByte())
        nextPc()
    }

    private fun InsFTOUW(i: IRInstruction) {
        registers.setUW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toUShort())
        nextPc()
    }

    private fun InsFTOSB(i: IRInstruction) {
        registers.setSB(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toByte())
        nextPc()
    }

    private fun InsFTOSW(i: IRInstruction) {
        registers.setSW(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt().toShort())
        nextPc()
    }

    private fun InsFPOW(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg1!!)
        val exponent = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, value.pow(exponent))
        nextPc()
    }

    private fun InsFSIN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, sin(angle))
        nextPc()
    }

    private fun InsFCOS(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, cos(angle))
        nextPc()
    }

    private fun InsFTAN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, tan(angle))
        nextPc()
    }

    private fun InsFATAN(i: IRInstruction) {
        val angle = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, atan(angle))
        nextPc()
    }

    private fun InsFABS(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, abs(value))
        nextPc()
    }

    private fun InsFLN(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ln(value))
        nextPc()
    }

    private fun InsFLOG(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, log2(value))
        nextPc()
    }

    private fun InsFROUND(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, round(value))
        nextPc()
    }

    private fun InsFFLOOR(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, floor(value))
        nextPc()
    }

    private fun InsFCEIL(i: IRInstruction) {
        val value = registers.getFloat(i.fpReg2!!)
        registers.setFloat(i.fpReg1!!, ceil(value))
        nextPc()
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
        nextPc()
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
        window = when(registers.getUB(SyscallRegisterBase).toInt()) {
            0 -> GraphicsWindow(320, 240, 3)
            1 -> GraphicsWindow(640, 480, 2)
            else -> throw IllegalArgumentException("invalid screen mode")
        }
        window!!.start()
    }

    fun gfx_clear() {
        window?.clear(registers.getUB(SyscallRegisterBase).toInt())
    }

    fun gfx_plot() {
        window?.plot(registers.getUW(SyscallRegisterBase).toInt(),
            registers.getUW(SyscallRegisterBase+1).toInt(),
            registers.getUB(SyscallRegisterBase+2).toInt())
    }

    fun gfx_getpixel() {
        if(window==null)
            registers.setUB(0, 0u)
        else {
            val color = Color(window!!.getpixel(registers.getUW(SyscallRegisterBase).toInt(),
                registers.getUW(SyscallRegisterBase+1).toInt()))
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

    fun randomSeedFloat(seed: Float) {
        randomGeneratorFloats = Random(seed.toBits())
    }
}

// probably called via reflection
class VmRunner: IVirtualMachineRunner {
    override fun runProgram(irSource: String) {
        runAndTestProgram(irSource) { /* no tests */ }
    }

    fun runAndTestProgram(irSource: String, test: (VirtualMachine) -> Unit) {
        val irProgram = IRFileReader().read(irSource)
        val vm = VirtualMachine(irProgram)
//        vm.breakpointHandler = { pcChunk, pcIndex ->
//            println("UNHANDLED BREAKPOINT")
//            println("  IN CHUNK: $pcChunk(${pcChunk.label})  INDEX: $pcIndex = INSTR ${pcChunk.instructions[pcIndex]}")
//        }
        vm.run()
        test(vm)
    }
}