package prog8.vm

import prog8.code.core.toHex
import prog8.code.target.IVirtualMachineRunner
import prog8.code.target.VMTarget
import prog8.intermediate.*
import java.awt.Color
import java.awt.Toolkit
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.math.*
import kotlin.random.Random

/*

Virtual machine specs:

Program to execute is not stored in the system memory, it's just a separate list of instructions.
65536 virtual registers, 16 bits wide, can also be used as 8 bits. r0-r65535
65536 virtual floating point registers (32 bits single precision floats)  fr0-fr65535
65536 bytes of memory, thus memory pointers (addresses) are limited to 16 bits.
Value stack, max 128 entries of 1 byte each.
Status flags: Carry, Zero, Negative.   NOTE: status flags are only affected by the CMP instruction or explicit CLC/SEC!!!
                                             logical AND, OR, XOR also set the N and Z bits.
                                             arithmetic operations DO NOT AFFECT THE STATUS FLAGS UNLESS EXPLICITLY NOTED!

 */

class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pcChunk: IRCodeChunk, val pcIndex: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(irProgram: IRProgram) {
    class CallSiteContext(val returnChunk: IRCodeChunk, val returnIndex: Int, val fcallSpec: FunctionCallArgs)

    private var fileOutputStream: OutputStream? = null
    private var fileInputStream: InputStream? = null
    val memory = Memory()
    val machine = VMTarget()
    val program: List<IRCodeChunk>
    val artificialLabelAddresses: Map<Int, IRCodeChunk>
    val registers = Registers()
    val callStack = ArrayDeque<CallSiteContext>()
    val valueStack = ArrayDeque<UByte>()       // max 128 entries
    var breakpointHandler: ((pcChunk: IRCodeChunk, pcIndex: Int) -> Unit)? = null       // can set custom breakpoint handler
    var pcChunk = IRCodeChunk(null, null)
    var pcIndex = 0
    var stepCount = 0
    var statusCarry = false
    var statusZero = false
    var statusNegative = false
    var statusOverflow = false
    var hardwareRegisterA: UByte = 0u
    var hardwareRegisterX: UByte = 0u
    var hardwareRegisterY: UByte = 0u
    var hardwareRegisterFAC0: Double = 0.0
    var hardwareRegisterFAC1: Double = 0.0

    internal var randomGenerator = Random(0xa55a7653)
    internal var randomGeneratorFloats = Random(0xc0d3dbad)
    internal var mul16LastUpper = 0u

    init {
        val (prg, labelAddr) = VmProgramLoader().load(irProgram, memory)
        program = prg
        artificialLabelAddresses = mutableMapOf()
        labelAddr.forEach { (labelname, artificialAddress) ->
            artificialLabelAddresses[artificialAddress] = program.single { it.label==labelname }
        }
        require(irProgram.st.getAsmSymbols().isEmpty()) { "virtual machine can't yet process asmsymbols defined on command line" }
        reset(false)
    }

    fun run(quiet: Boolean) {
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
            if(!quiet)
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
            when (val nextChunk = pcChunk.next) {
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
        when (val target = i.branchTarget) {
            is IRCodeChunk -> {
                pcChunk = target
                pcIndex = 0
            }
            null -> {
                if(i.address!=null)
                    throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${i.address!!.toHex()})")
                else if(i.labelSymbol!=null)
                    throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${i.labelSymbol})")
                else if(i.reg1!=null)
                    throw IllegalArgumentException("vm program can't jump to system memory address (${i} = ${registers.getUW(i.reg1!!)})")
                else
                    throw IllegalArgumentException("no branchtarget in $i")
            }
            is IRInlineAsmChunk -> TODO("branch to inline asm chunk")
            is IRInlineBinaryChunk -> throw IllegalArgumentException("can't branch to inline binary chunk")
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
            Opcode.LOADHA -> InsLOADHA(ins)
            Opcode.LOADHX -> InsLOADHX(ins)
            Opcode.LOADHY -> InsLOADHY(ins)
            Opcode.LOADHAX -> InsLOADHAX(ins)
            Opcode.LOADHAY -> InsLOADHAY(ins)
            Opcode.LOADHXY -> InsLOADHXY(ins)
            Opcode.LOADHFACZERO -> InsLOADHFACZERO(ins)
            Opcode.LOADHFACONE -> InsLOADHFACONE(ins)
            Opcode.LOADFIELD -> InsLOADFIELD(ins)
            Opcode.STOREM -> InsSTOREM(ins)
            Opcode.STOREX -> InsSTOREX(ins)
            Opcode.STOREIX -> InsSTOREIX(ins)
            Opcode.STOREI -> InsSTOREI(ins)
            Opcode.STOREZM -> InsSTOREZM(ins)
            Opcode.STOREZX -> InsSTOREZX(ins)
            Opcode.STOREZI -> InsSTOREZI(ins)
            Opcode.STOREHA -> InsSTOREHA(ins)
            Opcode.STOREHX -> InsSTOREHX(ins)
            Opcode.STOREHY -> InsSTOREHY(ins)
            Opcode.STOREHAX -> InsSTOREHAX(ins)
            Opcode.STOREHAY -> InsSTOREHAY(ins)
            Opcode.STOREHXY -> InsSTOREHXY(ins)
            Opcode.STOREHFACZERO -> InsSTOREHFACZERO(ins)
            Opcode.STOREHFACONE-> InsSTOREHFACONE(ins)
            Opcode.STOREFIELD-> InsSTOREFIELD(ins)
            Opcode.JUMP -> InsJUMP(ins)
            Opcode.JUMPI -> InsJUMPI(ins)
            Opcode.CALLI -> throw IllegalArgumentException("VM cannot run code from memory bytes")
            Opcode.CALL -> InsCALL(ins)
            Opcode.CALLFAR, Opcode.CALLFARVB -> throw IllegalArgumentException("VM cannot run code from another ram/rombank")
            Opcode.SYSCALL -> InsSYSCALL(ins)
            Opcode.RETURN -> InsRETURN()
            Opcode.RETURNR -> InsRETURNR(ins)
            Opcode.RETURNI -> InsRETURNI(ins)
            Opcode.BSTCC -> InsBSTCC(ins)
            Opcode.BSTCS -> InsBSTCS(ins)
            Opcode.BSTEQ -> InsBSTEQ(ins)
            Opcode.BSTNE -> InsBSTNE(ins)
            Opcode.BSTNEG -> InsBSTNEG(ins)
            Opcode.BSTPOS -> InsBSTPOS(ins)
            Opcode.BSTVC -> InsBSTVC(ins)
            Opcode.BSTVS -> InsBSTVS(ins)
            Opcode.BGTR -> InsBGTR(ins)
            Opcode.BGTSR -> InsBGTSR(ins)
            Opcode.BGER -> InsBGER(ins)
            Opcode.BGESR -> InsBGESR(ins)
            Opcode.BGT -> InsBGT(ins)
            Opcode.BLT -> InsBLT(ins)
            Opcode.BGTS -> InsBGTS(ins)
            Opcode.BLTS -> InsBLTS(ins)
            Opcode.BGE -> InsBGE(ins)
            Opcode.BLE -> InsBLE(ins)
            Opcode.BGES -> InsBGES(ins)
            Opcode.BLES -> InsBLES(ins)
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
            Opcode.MULSR -> InsMULSR(ins)
            Opcode.MULS -> InsMULS(ins)
            Opcode.MULSM -> InsMULSM(ins)
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
            Opcode.CMPI -> InsCMPI(ins)
            Opcode.SQRT -> InsSQRT(ins)
            Opcode.SQUARE -> InsSQUARE(ins)
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
            Opcode.LSIG -> InsLSIG(ins)
            Opcode.MSIG -> InsMSIG(ins)
            Opcode.CONCAT -> InsCONCAT(ins)
            Opcode.PUSH -> InsPUSH(ins)
            Opcode.POP -> InsPOP(ins)
            Opcode.PUSHST -> InsPUSHST()
            Opcode.POPST -> InsPOPST()
            Opcode.BREAKPOINT -> InsBREAKPOINT()
            Opcode.CLC -> { statusCarry = false; nextPc() }
            Opcode.SEC -> { statusCarry = true; nextPc() }
            Opcode.CLI, Opcode.SEI -> throw IllegalArgumentException("VM doesn't support interrupt status bit")
            Opcode.BIT -> InsBIT(ins)

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
            Opcode.ALIGN -> nextPc()   // actual alignment ignored in the VM
        }
    }

    private inline fun setResultReg(reg: Int, value: Int, type: IRDataType) {
        when(type) {
            IRDataType.BYTE -> {
                registers.setUB(reg, value.toUByte())
                statusZero = value==0
                statusNegative = value>=0x80
            }
            IRDataType.WORD -> {
                registers.setUW(reg, value.toUShort())
                statusZero = value==0
                statusNegative = value>=0x8000
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("attempt to set integer result register but float type")
        }
    }

    private fun InsPUSH(i: IRInstruction) {
        if(valueStack.size>=128)
            throw StackOverflowError("valuestack limit 128 exceeded")

        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!)
                valueStack.add(value)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!)
                valueStack.pushw(value)
            }
            IRDataType.FLOAT -> {
                val value = registers.getFloat(i.fpReg1!!)
                valueStack.pushf(value)
            }
        }
        nextPc()
    }

    private fun InsPOP(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> setResultReg(i.reg1!!, valueStack.removeLast().toInt(), i.type!!)
            IRDataType.WORD -> setResultReg(i.reg1!!, valueStack.popw().toInt(), i.type!!)
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, valueStack.popf())
        }
        nextPc()
    }

    private fun InsPUSHST() {
        var status: UByte = 0u
        if(statusNegative)
            status = status or 0b10000000u
        if(statusZero)
            status = status or 0b00000010u
        if(statusCarry)
            status = status or 0b00000001u
        if(statusOverflow)
            status = status or 0b01000000u
        valueStack.add(status)
        nextPc()
    }

    private fun InsPOPST() {
        val status = valueStack.removeLast().toInt()
        statusNegative = status and 0b10000000 != 0
        statusZero = status and 0b00000010 != 0
        statusCarry = status and 0b00000001 != 0
        statusOverflow = status and 0b01000000 != 0
        nextPc()
    }

    private fun InsSYSCALL(i: IRInstruction) {
        // put the syscall's arguments that were prepared onto the stack
        for(value in syscallParams) {
            if(value.dt==null)
                break
            when(value.dt!!) {
                IRDataType.BYTE -> valueStack.add(value.value as UByte)
                IRDataType.WORD -> valueStack.pushw(value.value as UShort)
                IRDataType.FLOAT -> valueStack.pushf(value.value as Double)
            }
            value.dt=null
        }
        val call = Syscall.fromInt(i.immediate!!)
        SysCalls.call(call, i.fcallArgs!!, this)   // note: any result value(s) are pushed back on the value stack
        nextPc()
    }

    private fun InsBREAKPOINT() {
        nextPc()
        if(breakpointHandler!=null)
            breakpointHandler?.invoke(pcChunk, pcIndex)
        else
            throw BreakpointException(pcChunk, pcIndex)
    }

    private fun InsLOAD(i: IRInstruction) {
        if(i.type==IRDataType.FLOAT)
            registers.setFloat(i.fpReg1!!, i.immediateFp!!)
        else {
            if(i.immediate!=null) {
                setResultReg(i.reg1!!, i.immediate!!, i.type!!)
                statusbitsNZ(i.immediate!!, i.type!!)
            }
            else {
                if(i.labelSymbol==null)
                    throw IllegalArgumentException("expected LOAD of address of labelsymbol")
                setResultReg(i.reg1!!, i.address!!, i.type!!)
                statusbitsNZ(i.address!!, i.type!!)
            }
        }
        nextPc()
    }

    private fun InsLOADM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(i.address!!)
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(i.address!!)
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!!))
        }
        nextPc()
    }

    private fun InsLOADI(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(registers.getUW(i.reg2!!).toInt())
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(registers.getUW(i.reg2!!).toInt())
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toInt()))
        }
        nextPc()
    }

    private fun InsLOADFIELD(i: IRInstruction) {
        val offset = i.immediate!!
        require(offset in 0..255)
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(registers.getUW(i.reg2!!).toInt() + offset)
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(registers.getUW(i.reg2!!).toInt() + offset)
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> {
                registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toInt() + offset))
            }
        }
        nextPc()
    }

    private fun InsLOADX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(i.address!! + registers.getUB(i.reg2!!).toInt())
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(i.address!! + registers.getUB(i.reg2!!).toInt())
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!! + registers.getUB(i.reg1!!).toInt()))
        }
        nextPc()
    }

    private fun InsLOADIX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                val value = memory.getUB(pointer.toInt())
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val pointer = memory.getUW(i.address!!) + registers.getUB(i.reg2!!)
                val value = memory.getUW(pointer.toInt())
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
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
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg2!!)
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg2!!)
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
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

    private fun InsSTOREFIELD(i: IRInstruction) {
        val offset = i.immediate!!
        require(offset in 0..255)
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toInt() + offset, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toInt() + offset, registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt() + offset, registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!! + registers.getUB(i.reg2!!).toInt(), registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(i.address!! + registers.getUB(i.reg2!!).toInt(), registers.getUW(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(i.address!! + registers.getUB(i.reg1!!).toInt(), registers.getFloat(i.fpReg1!!))
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
            IRDataType.FLOAT -> memory.setFloat(i.address!!, 0.0)
        }
        nextPc()
    }

    private fun InsSTOREZI(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toInt(), 0u)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toInt(), 0.0)
        }
        nextPc()
    }

    private fun InsSTOREZX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!! + registers.getUB(i.reg1!!).toInt(), 0u)
            IRDataType.WORD -> memory.setUW(i.address!! + registers.getUB(i.reg1!!).toInt(), 0u)
            IRDataType.FLOAT -> memory.setFloat(i.address!! + registers.getUB(i.reg1!!).toInt(), 0.0)
        }
        nextPc()
    }

    private fun InsJUMP(i: IRInstruction) {
        branchTo(i)
    }

    private fun InsJUMPI(i: IRInstruction) {
        val artificialAddress = registers.getUW(i.reg1!!).toInt()
        if(!artificialLabelAddresses.contains(artificialAddress))
            throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${artificialAddress.toHex()})")
        pcChunk = artificialLabelAddresses.getValue(artificialAddress)
        pcIndex = 0
    }

    private class SyscallParamValue(var dt: IRDataType?, var value: Comparable<*>?)
    private val syscallParams = Array(100) { SyscallParamValue(null, null) }

    private fun InsCALL(i: IRInstruction) {
        i.fcallArgs!!.arguments.forEach { arg ->
            requireNotNull(arg.address) {"argument variable should have been given its memory address as well"}
            when(arg.reg.dt) {
                IRDataType.BYTE -> memory.setUB(arg.address!!, registers.getUB(arg.reg.registerNum))
                IRDataType.WORD -> memory.setUW(arg.address!!, registers.getUW(arg.reg.registerNum))
                IRDataType.FLOAT -> memory.setFloat(arg.address!!, registers.getFloat(arg.reg.registerNum))
            }
        }
        // store the call site and jump
        callStack.add(CallSiteContext(pcChunk, pcIndex+1, i.fcallArgs!!))
        branchTo(i)
    }

    private fun InsRETURN() {
        if(callStack.isEmpty())
            exit(0)
        else {
            val context = callStack.removeLast()
            pcChunk = context.returnChunk
            pcIndex = context.returnIndex
            // ignore any return values.
        }
    }

    private fun InsRETURNI(i: IRInstruction) {
        if(callStack.isEmpty())
            exit(0)
        else {
            val context = callStack.removeLast()
            val returns = context.fcallSpec.returns
            when (i.type!!) {
                IRDataType.BYTE -> {
                    if(returns.isNotEmpty())
                        registers.setUB(returns.single().registerNum, i.immediate!!.toUByte())
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty())
                        registers.setUW(returns.single().registerNum, i.immediate!!.toUShort())
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.FLOAT -> {
                    if(returns.isNotEmpty())
                        registers.setFloat(returns.single().registerNum, i.immediateFp!!)
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

    private fun InsRETURNR(i: IRInstruction) {
        if(callStack.isEmpty())
            exit(0)
        else {
            val context = callStack.removeLast()
            val returns = context.fcallSpec.returns
            when (i.type!!) {
                IRDataType.BYTE -> {
                    if(returns.isNotEmpty())
                        registers.setUB(returns.single().registerNum, registers.getUB(i.reg1!!))
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty())
                        registers.setUW(returns.single().registerNum, registers.getUW(i.reg1!!))
                    else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.FLOAT -> {
                    if(returns.isNotEmpty())
                        registers.setFloat(returns.single().registerNum, registers.getFloat(i.fpReg1!!))
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

    private fun InsBSTVS(i: IRInstruction) {
        if(statusOverflow)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBSTVC(i: IRInstruction) {
        if(!statusOverflow)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGTR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGT(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsImmU(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBLT(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsImmU(i)
        if(left<right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGTSR(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGTS(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperandsImm(i)
        if(left>right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBLTS(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperandsImm(i)
        if(left<right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGER(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsU(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGE(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsImmU(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBLE(i: IRInstruction) {
        val (left: UInt, right: UInt) = getBranchOperandsImmU(i)
        if(left<=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGESR(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperands(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBGES(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperandsImm(i)
        if(left>=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsBLES(i: IRInstruction) {
        val (left: Int, right: Int) = getBranchOperandsImm(i)
        if(left<=right)
            branchTo(i)
        else
            nextPc()
    }

    private fun InsINC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (registers.getUB(i.reg1!!)+1u).toUByte()
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = (registers.getUW(i.reg1!!)+1u).toUShort()
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)+1f)
        }
        nextPc()
    }

    private fun InsINCM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (memory.getUB(address)+1u).toUByte()
                memory.setUB(address, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = (memory.getUW(address)+1u).toUShort()
                memory.setUW(address, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)+1f)
        }
        nextPc()
    }

    private fun InsDEC(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (registers.getUB(i.reg1!!)-1u).toUByte()
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = (registers.getUW(i.reg1!!)-1u).toUShort()
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)-1f)
        }
        nextPc()
    }

    private fun InsDECM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (memory.getUB(i.address!!)-1u).toUByte()
                memory.setUB(i.address!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = (memory.getUW(i.address!!)-1u).toUShort()
                memory.setUW(i.address!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.FLOAT -> memory.setFloat(i.address!!, memory.getFloat(i.address!!)-1f)
        }
        nextPc()
    }

    private fun InsNEG(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = -registers.getUB(i.reg1!!).toInt()
                registers.setUB(i.reg1!!, value.toUByte())
                statusbitsNZ(value, IRDataType.BYTE)
            }
            IRDataType.WORD -> {
                val value = -registers.getUW(i.reg1!!).toInt()
                registers.setUW(i.reg1!!, value.toUShort())
                statusbitsNZ(value, IRDataType.WORD)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, -registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsNEGM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = -memory.getUB(address).toInt()
                memory.setUB(address, value.toUByte())
                statusbitsNZ(value, IRDataType.BYTE)
            }
            IRDataType.WORD -> {
                val value = -memory.getUW(address).toInt()
                memory.setUW(address, value.toUShort())
                statusbitsNZ(value, IRDataType.WORD)
            }
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

    private fun InsMULSR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteSigned("*", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> plusMinusMultAnyWordSigned("*", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val right = registers.getFloat(i.fpReg2!!)
                val result = arithFloat(left, "*", right)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsMULS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByteSigned("*", i.reg1!!, i.immediate!!.toByte())
            IRDataType.WORD -> plusMinusMultConstWordSigned("*", i.reg1!!, i.immediate!!.toShort())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsMULSM(i: IRInstruction) {
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteSignedInplace("*", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordSignedInplace("*", i.reg1!!, address)
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
            IRDataType.BYTE -> divAndModUByte(i.reg1!!, i.reg2!!)       // division+remainder results on value stack
            IRDataType.WORD -> divAndModUWord(i.reg1!!, i.reg2!!)       // division+remainder results on value stack
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstUByte(i.reg1!!, i.immediate!!.toUByte())    // division+remainder results on value stack
            IRDataType.WORD -> divAndModConstUWord(i.reg1!!, i.immediate!!.toUShort())   // division+remainder results on value stack
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSGN(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, registers.getSB(i.reg2!!).toInt().sign.toByte())
            IRDataType.WORD -> registers.setSB(i.reg1!!, registers.getSW(i.reg2!!).toInt().sign.toByte())
            IRDataType.FLOAT -> registers.setSB(i.reg1!!, registers.getFloat(i.fpReg1!!).sign.toInt().toByte())
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

    private fun InsSQUARE(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg2!!).toDouble().toInt()
                registers.setUB(i.reg1!!, (value*value).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg2!!).toDouble().toInt()
                registers.setUW(i.reg1!!, (value*value).toUShort())
            }
            IRDataType.FLOAT -> {
                val value = registers.getFloat(i.fpReg2!!)
                registers.setFloat(i.fpReg1!!, value*value)
            }
        }
        nextPc()
    }

    private fun InsCMP(i: IRInstruction) {
        val comparison = when(i.type!!) {
            IRDataType.BYTE -> {
                val reg1 = registers.getUB(i.reg1!!)
                val reg2 = registers.getUB(i.reg2!!)
                reg1.toInt() - reg2.toInt()
            }
            IRDataType.WORD -> {
                val reg1 = registers.getUW(i.reg1!!)
                val reg2 = registers.getUW(i.reg2!!)
                reg1.toInt() - reg2.toInt()
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparison(comparison, i.type!!)
        nextPc()
    }

    private fun InsCMPI(i: IRInstruction) {
        val comparison = when(i.type!!) {
            IRDataType.BYTE -> {
                val reg1 = registers.getUB(i.reg1!!)
                reg1.toInt() - (i.immediate!! and 255)
            }
            IRDataType.WORD -> {
                val reg1 = registers.getUW(i.reg1!!)
                reg1.toInt() - (i.immediate!! and 65535)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparison(comparison, i.type!!)
        nextPc()
    }

    private fun InsLOADHFACZERO(ins: IRInstruction) {
        registers.setFloat(ins.fpReg1!!, hardwareRegisterFAC0)
        nextPc()
    }

    private fun InsLOADHFACONE(ins: IRInstruction) {
        registers.setFloat(ins.fpReg1!!, hardwareRegisterFAC1)
        nextPc()
    }

    private fun InsSTOREHFACZERO(ins: IRInstruction) {
        hardwareRegisterFAC0 = registers.getFloat(ins.fpReg1!!)
        nextPc()
    }

    private fun InsSTOREHFACONE(ins: IRInstruction) {
        hardwareRegisterFAC1 = registers.getFloat(ins.fpReg1!!)
        nextPc()
    }


    private fun statusbitsNZ(value: Int, type: IRDataType) {
        statusZero = value==0
        when(type) {
            IRDataType.BYTE -> statusNegative = (value and 0x80)==0x80
            IRDataType.WORD -> statusNegative = (value and 0x8000)==0x8000
            IRDataType.FLOAT -> { /* floats don't change the status bits */ }
        }
    }

    private fun statusbitsComparison(comparison: Int, type: IRDataType) {
        if(comparison==0) {
            statusZero = true
            statusCarry = true
        } else if(comparison>0) {
            statusZero = false
            statusCarry = true
        } else {
            statusZero = false
            statusCarry = false
        }
        when(type) {
            IRDataType.BYTE -> statusNegative = (comparison and 0x80)!=0
            IRDataType.WORD -> statusNegative = (comparison and 0x8000)!=0
            IRDataType.FLOAT -> { /* floats don't change the status bits */ }
        }
        // TODO determine statusOverflow in comparison
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

    private fun plusMinusMultAnyByteSigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getSB(reg1)
        val right = registers.getSB(reg2)
        val result = when(operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setSB(reg1, result.toByte())
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

    private fun plusMinusMultConstByteSigned(operator: String, reg1: Int, value: Byte) {
        val left = registers.getSB(reg1)
        val result = when(operator) {
            "+" -> left + value
            "-" -> left - value
            "*" -> left * value
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        registers.setSB(reg1, result.toByte())
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

    private fun plusMinusMultAnyByteSignedInplace(operator: String, reg1: Int, address: Int) {
        val memvalue = memory.getSB(address)
        val operand = registers.getSB(reg1)
        val result = when(operator) {
            "+" -> memvalue + operand
            "-" -> memvalue - operand
            "*" -> memvalue * operand
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        memory.setSB(address, result.toByte())
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
                if(right==0.toUByte()) 0u
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
                if(value==0.toUByte()) 0u
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
        val remainder = if(right==0.toUByte()) 0u else left % right
        valueStack.add(division.toUByte())
        valueStack.add(remainder.toUByte())
    }

    private fun divAndModConstUByte(reg1: Int, value: UByte) {
        val left = registers.getUB(reg1)
        val division = if(value==0.toUByte()) 0xffu else left / value
        val remainder = if(value==0.toUByte()) 0u else left % value
        valueStack.add(division.toUByte())
        valueStack.add(remainder.toUByte())
    }

    private fun divAndModUWord(reg1: Int, reg2: Int) {
        val left = registers.getUW(reg1)
        val right = registers.getUW(reg2)
        val division = if(right==0.toUShort()) 0xffffu else left / right
        val remainder = if(right==0.toUShort()) 0u else left % right
        valueStack.pushw(division.toUShort())
        valueStack.pushw(remainder.toUShort())
    }

    private fun divAndModConstUWord(reg1: Int, value: UShort) {
        val left = registers.getUW(reg1)
        val division = if(value==0.toUShort()) 0xffffu else left / value
        val remainder = if(value==0.toUShort()) 0u else left % value
        valueStack.pushw(division.toUShort())
        valueStack.pushw(remainder.toUShort())
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
                if(right==0.toUByte()) 0u
                else left % right
            }
            else -> throw IllegalArgumentException("operator byte $operator")
        }
        memory.setUB(address, result.toUByte())
    }

    private fun plusMinusMultAnyWord(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getUW(reg1)
        val right = registers.getUW(reg2)
        val result: UInt
        when(operator) {
            "+" -> result = left + right
            "-" -> result = left - right
            "*" -> {
                result = left.toUInt() * right
                mul16LastUpper = result shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun plusMinusMultAnyWordSigned(operator: String, reg1: Int, reg2: Int) {
        val left = registers.getSW(reg1)
        val right = registers.getSW(reg2)
        val result: Int
        when(operator) {
            "+" -> result = left + right
            "-" -> result = left - right
            "*" -> {
                result = left.toInt() * right
                mul16LastUpper = result.toUInt() shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setSW(reg1, result.toShort())
    }

    private fun plusMinusMultConstWord(operator: String, reg1: Int, value: UShort) {
        val left = registers.getUW(reg1)
        val result: UInt
        when(operator) {
            "+" -> result = left + value
            "-" -> result = left - value
            "*" -> {
                result = left.toUInt() * value
                mul16LastUpper = result shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setUW(reg1, result.toUShort())
    }

    private fun plusMinusMultConstWordSigned(operator: String, reg1: Int, value: Short) {
        val left = registers.getSW(reg1)
        val result: Int
        when(operator) {
            "+" -> result = left + value
            "-" -> result = left - value
            "*" -> {
                result = left.toInt() * value
                mul16LastUpper = result.toUInt() shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        registers.setSW(reg1, result.toShort())
    }

    private fun plusMinusMultAnyWordInplace(operator: String, reg1: Int, address: Int) {
        val memvalue = memory.getUW(address)
        val operand = registers.getUW(reg1)
        val result: UInt
        when(operator) {
            "+" -> result = memvalue + operand
            "-" -> result = memvalue - operand
            "*" -> {
                result = memvalue.toUInt() * operand
                mul16LastUpper = result shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        memory.setUW(address, result.toUShort())
    }

    private fun plusMinusMultAnyWordSignedInplace(operator: String, reg1: Int, address: Int) {
        val memvalue = memory.getSW(address)
        val operand = registers.getSW(reg1)
        val result: Int
        when(operator) {
            "+" -> result = memvalue + operand
            "-" -> result = memvalue - operand
            "*" -> {
                result = memvalue.toInt() * operand
                mul16LastUpper = result.toUInt() shr 16
            }
            else -> throw IllegalArgumentException("operator word $operator")
        }
        memory.setSW(address, result.toShort())
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
                if(right==0.toUShort()) 0u
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
                if(value==0.toUShort()) 0u
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
                if(right==0.toUShort()) 0u
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

    private fun arithFloat(left: Double, operator: String, right: Double): Double = when(operator) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        "/" -> {
            if(right==0.0) Double.MAX_VALUE
            else left / right
        }
        "%" -> {
            if(right==0.0) Double.MAX_VALUE
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }

    private fun InsBIT(i: IRInstruction) {
        if (i.type!! == IRDataType.BYTE) {
            val value = memory.getUB(i.address!!)
            statusNegative = value.toInt() and 0x80 != 0
            statusOverflow = value.toInt() and 0x40 != 0
            // NOTE: the 'AND' part of the BIT instruction as it does on the 6502 CPU, is not utilized in prog8 so we don't implement it here
        }
        else throw IllegalArgumentException("bit needs byte")
        nextPc()
    }

    private fun InsEXT(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setUW(i.reg1!!, registers.getUB(i.reg2!!).toUShort())
            IRDataType.WORD -> throw IllegalArgumentException("ext.w not yet supported, requires 32 bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsEXTS(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg2!!).toShort())
            IRDataType.WORD -> throw IllegalArgumentException("exts.w not yet supported, requires 32 bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsANDR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left and right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, value.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, value.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsAND(i: IRInstruction) {
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(i.reg1!!).toInt() and i.immediate!!
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(i.reg1!!).toInt() and i.immediate!!
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsANDM(i: IRInstruction) {
        val value: Int
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                value = left.toInt() and right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                value = left.toInt() and right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left or right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, value.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, value.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsOR(i: IRInstruction) {
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(i.reg1!!).toInt() or i.immediate!!
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(i.reg1!!).toInt() or i.immediate!!
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsORM(i: IRInstruction) {
        val value: Int
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                value = left.toInt() or right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                value = left.toInt() or right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsXORR(i: IRInstruction) {
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left xor right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, value.toUByte())
            IRDataType.WORD -> registers.setUW(i.reg1!!, value.toUShort())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsXOR(i: IRInstruction) {
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(i.reg1!!).toInt() xor i.immediate!!
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(i.reg1!!).toInt() xor i.immediate!!
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsXORM(i: IRInstruction) {
        val value: Int
        val address = i.address!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(i.reg1!!)
                value = left.toInt() xor right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(i.reg1!!)
                value = left.toInt() xor right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
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
                memory.setSB(address, (memvalue shr operand).toByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getSW(address).toInt()
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
                memory.setUB(address, (memvalue shr operand).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
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
                registers.setUB(i.reg1!!, (left shl right.toInt()).toUByte())
            }
            IRDataType.WORD -> {
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
                memory.setUB(address, (memvalue shl operand).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
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

    private fun InsLSIG(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUW(i.reg2!!)
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> throw IllegalArgumentException("lsig.w not yet supported, requires 32-bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
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
                val msb = registers.getUB(i.reg2!!)
                val lsb = registers.getUB(i.reg3!!)
                registers.setUW(i.reg1!!, ((msb.toInt() shl 8) or lsb.toInt()).toUShort())
            }
            IRDataType.WORD -> throw IllegalArgumentException("concat.w not yet supported, requires 32-bits registers")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsFFROMUB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUB(i.reg1!!).toDouble())
        nextPc()
    }

    private fun InsFFROMSB(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSB(i.reg1!!).toDouble())
        nextPc()
    }

    private fun InsFFROMUW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getUW(i.reg1!!).toDouble())
        nextPc()
    }

    private fun InsFFROMSW(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSW(i.reg1!!).toDouble())
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

    private fun InsLOADHA(i: IRInstruction) {
        registers.setUB(i.reg1!!, hardwareRegisterA)
        nextPc()
    }

    private fun InsLOADHX(i: IRInstruction) {
        registers.setUB(i.reg1!!, hardwareRegisterX)
        nextPc()
    }

    private fun InsLOADHY(i: IRInstruction) {
        registers.setUB(i.reg1!!, hardwareRegisterY)
        nextPc()
    }

    private fun InsLOADHAX(i: IRInstruction) {
        registers.setUW(i.reg1!!, ((hardwareRegisterX.toUInt() shl 8) + hardwareRegisterA).toUShort())
        nextPc()
    }

    private fun InsLOADHAY(i: IRInstruction) {
        registers.setUW(i.reg1!!, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterA).toUShort())
        nextPc()
    }

    private fun InsLOADHXY(i: IRInstruction) {
        registers.setUW(i.reg1!!, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterX).toUShort())
        nextPc()
    }

    private fun InsSTOREHA(i: IRInstruction) {
        hardwareRegisterA = registers.getUB(i.reg1!!)
        nextPc()
    }

    private fun InsSTOREHX(i: IRInstruction) {
        hardwareRegisterX = registers.getUB(i.reg1!!)
        nextPc()
    }

    private fun InsSTOREHY(i: IRInstruction) {
        hardwareRegisterY = registers.getUB(i.reg1!!)
        nextPc()
    }

    private fun InsSTOREHAX(i: IRInstruction) {
        val word = registers.getUW(i.reg1!!).toUInt()
        hardwareRegisterA = (word and 255u).toUByte()
        hardwareRegisterX = (word shr 8).toUByte()
        nextPc()
    }

    private fun InsSTOREHAY(i: IRInstruction) {
        val word = registers.getUW(i.reg1!!).toUInt()
        hardwareRegisterA = (word and 255u).toUByte()
        hardwareRegisterY = (word shr 8).toUByte()
        nextPc()
    }

    private fun InsSTOREHXY(i: IRInstruction) {
        val word = registers.getUW(i.reg1!!).toUInt()
        hardwareRegisterX = (word and 255u).toUByte()
        hardwareRegisterY = (word shr 8).toUByte()
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

    private fun getBranchOperandsImm(i: IRInstruction): Pair<Int, Int> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), i.immediate!!)
            IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), i.immediate!!)
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

    private fun getBranchOperandsImmU(i: IRInstruction): Pair<UInt, UInt> {
        return when(i.type) {
            IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), i.immediate!!.toUInt())
            IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), i.immediate!!.toUInt())
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

    private var window: GraphicsWindow? = null

    fun gfx_enable(mode: UByte) {
        window = when(mode.toInt()) {
            0 -> GraphicsWindow(320, 240, 3)
            1 -> GraphicsWindow(640, 480, 2)
            else -> throw IllegalArgumentException("invalid screen mode")
        }
        window!!.start()
    }

    fun gfx_clear(color: UByte) {
        window?.clear(color.toInt())
    }

    fun gfx_plot(x: UShort, y: UShort, color: UByte) {
        window?.plot(x.toInt(), y.toInt(), color.toInt())
    }

    fun gfx_getpixel(x: UShort, y: UShort): UByte {
        return if(window==null)
            0u
        else {
            val color = Color(window!!.getpixel(x.toInt(), y.toInt()))
            color.green.toUByte()
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

    fun randomSeedFloat(seed: Double) {
        randomGeneratorFloats = Random(seed.toBits())
    }

    fun open_file_read(name: String): Int {
        try {
            fileInputStream = Path(name).inputStream()
        } catch (_: IOException) {
            return 0
        }
        return 1
    }

    fun open_file_write(name: String): Int {
        try {
            fileOutputStream = Path(name).outputStream()
        } catch (_: IOException) {
            return 0
        }
        return 1
    }

    fun close_file_read() {
        fileInputStream?.close()
        fileInputStream=null
    }

    fun close_file_write() {
        fileOutputStream?.flush()
        fileOutputStream?.close()
        fileOutputStream=null
    }

    fun read_file_byte(): Pair<Boolean, UByte> {
        return if(fileInputStream==null)
            false to 0u
        else {
            try {
                val byte = fileInputStream!!.read()
                if(byte>=0)
                    true to byte.toUByte()
                else
                    false to 0u
            } catch(_: IOException) {
                false to 0u
            }
        }
    }

    fun write_file_byte(byte: UByte): Boolean {
        if(fileOutputStream==null)
            return false
        try {
            fileOutputStream!!.write(byte.toInt())
            return true
        } catch(_: IOException) {
            return false
        }
    }
}

internal fun ArrayDeque<UByte>.pushw(value: UShort) {
    add((value and 255u).toUByte())
    add((value.toInt() ushr 8).toUByte())
}

internal fun ArrayDeque<UByte>.pushf(value: Double) {
    // push float; lsb first, msb last
    var bits = value.toBits()
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
    bits = bits ushr 8
    add(bits.toUByte())
}

internal fun ArrayDeque<UByte>.popw(): UShort {
    val msb = removeLast()
    val lsb = removeLast()
    return ((msb.toInt() shl 8) + lsb.toInt()).toUShort()
}

internal fun ArrayDeque<UByte>.popf(): Double {
    // pop float; lsb is on bottom, msb on top
    val b0 = removeLast().toLong()
    val b1 = removeLast().toLong()
    val b2 = removeLast().toLong()
    val b3 = removeLast().toLong()
    val b4 = removeLast().toLong()
    val b5 = removeLast().toLong()
    val b6 = removeLast().toLong()
    val b7 = removeLast().toLong()
    val bits = b7 +
            (1L shl 8)*b6 +
            (1L shl 16)*b5 +
            (1L shl 24)*b4 +
            (1L shl 32)*b3 +
            (1L shl 40)*b2 +
            (1L shl 48)*b1 +
            (1L shl 56)*b0
    return Double.fromBits(bits)
}

// probably called via reflection
class VmRunner: IVirtualMachineRunner {
    override fun runProgram(irSource: String, quiet: Boolean) {
        runAndTestProgram(irSource, quiet) { /* no tests */ }
    }

    fun runAndTestProgram(irSource: String, quiet: Boolean = false, test: (VirtualMachine) -> Unit) {
        val irProgram = IRFileReader().read(irSource)
        val vm = VirtualMachine(irProgram)
//        vm.breakpointHandler = { pcChunk, pcIndex ->
//            println("UNHANDLED BREAKPOINT")
//            println("  IN CHUNK: $pcChunk(${pcChunk.label})  INDEX: $pcIndex = INSTR ${pcChunk.instructions[pcIndex]}")
//        }
        vm.run(quiet)
        test(vm)
    }
}