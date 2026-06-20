package prog8.vm

import prog8.code.core.toHex
import prog8.code.target.IVirtualMachineRunner
import prog8.code.target.VMTarget
import prog8.intermediate.*
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

    // Constants for performance and maintainability
    private companion object {
        private const val VALUE_STACK_MAX = 128
        private const val MIPS_COUNTER_MASK = 0xffffff
    }

    internal var fileOutputStream: java.io.RandomAccessFile? = null
    internal var fileInputStream: java.io.RandomAccessFile? = null
    val memory = Memory()
    val machine = VMTarget()
    val program: List<IRCodeChunk>
    val artificialLabelAddresses: Map<UInt, IRCodeChunk>
    val registers = Registers()
    val callStack = ArrayDeque<CallSiteContext>()
    val valueStack = ArrayDeque<UByte>()       // max VALUE_STACK_MAX entries
    var breakpointHandler: ((pcChunk: IRCodeChunk, pcIndex: Int) -> Unit)? = null       // can set custom breakpoint handler
    var traceEnabled: Boolean = false        // enable instruction tracing
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

                if(stepCount and MIPS_COUNTER_MASK == 0) {
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
        pcIndex++
        if(pcIndex >= pcChunk.instructions.size)
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
                    throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${i.address!!.value.toInt().toHex()})")
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
        if (traceEnabled) {
            val chunkLabel = pcChunk.label ?: "?"
            println("[$chunkLabel:$pcIndex] $ins")
        }
        when(ins.opcode) {
            Opcode.NOP -> nextPc()
            Opcode.LOAD -> InsLOAD(ins)
            Opcode.LOADM -> InsLOADM(ins)
            Opcode.LOADX -> InsLOADX(ins)
            Opcode.LOADR -> InsLOADR(ins)
            Opcode.LOADHR -> InsLOADHR(ins)
            Opcode.LOADHFACZERO -> InsLOADHFACZERO(ins)
            Opcode.LOADHFACONE -> InsLOADHFACONE(ins)
            Opcode.LOADI -> InsLOADI(ins)
            Opcode.STOREM -> InsSTOREM(ins)
            Opcode.STOREX -> InsSTOREX(ins)
            Opcode.STOREI-> InsSTOREI(ins)
            Opcode.STOREZM -> InsSTOREZM(ins)
            Opcode.STOREZX -> InsSTOREZX(ins)
            Opcode.STOREZI -> InsSTOREZI(ins)
            Opcode.STOREHR -> InsSTOREHR(ins)
            Opcode.STOREHFACZERO -> InsSTOREHFACZERO(ins)
            Opcode.STOREHFACONE-> InsSTOREHFACONE(ins)
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
            Opcode.MODSR -> InsMODSR(ins)
            Opcode.MODS -> InsMODS(ins)
            Opcode.DIVMODR -> InsDIVMODR(ins)
            Opcode.DIVMOD -> InsDIVMOD(ins)
            Opcode.SDIVMODR -> InsSDIVMODR(ins)
            Opcode.SDIVMOD -> InsSDIVMOD(ins)
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
            Opcode.LSIGB -> InsLSIGB(ins)
            Opcode.LSIGW -> InsLSIGW(ins)
            Opcode.MSIGB -> InsMSIGB(ins)
            Opcode.MSIGW -> InsMSIGW(ins)
            Opcode.BSIGB -> InsBSIGB(ins)
            Opcode.MIDB -> InsMIDB(ins)
            Opcode.CONCAT -> InsCONCAT(ins)
            Opcode.PUSH -> InsPUSH(ins)
            Opcode.POP -> InsPOP(ins)
            Opcode.PUSHST -> InsPUSHST()
            Opcode.POPST -> InsPOPST()
            Opcode.BREAKPOINT -> InsBREAKPOINT()
            Opcode.CLC -> { statusCarry = false; nextPc() }
            Opcode.SEC -> { statusCarry = true; nextPc() }
            Opcode.CLI, Opcode.SEI -> throw IllegalArgumentException("VM doesn't support interrupt status bit")
            Opcode.BITTST -> InsBITTST(ins)
            Opcode.BITSET -> InsBITSET(ins)
            Opcode.BITCLR -> InsBITCLR(ins)
            Opcode.BITTOG -> InsBITTOG(ins)

            Opcode.FFROMUB -> InsFFROMUB(ins)
            Opcode.FFROMSB -> InsFFROMSB(ins)
            Opcode.FFROMUW -> InsFFROMUW(ins)
            Opcode.FFROMSW -> InsFFROMSW(ins)
            Opcode.FFROMSL -> InsFFROMSL(ins)
            Opcode.FTOUB -> InsFTOUB(ins)
            Opcode.FTOSB -> InsFTOSB(ins)
            Opcode.FTOUW -> InsFTOUW(ins)
            Opcode.FTOSW -> InsFTOSW(ins)
            Opcode.FTOSL -> InsFTOSL(ins)
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

    private fun setResultReg(reg: Int, value: Int, type: IRDataType) {
        when(type) {
            IRDataType.BYTE -> {
                registers.setUB(reg, value.toUByte())
                statusZero = value == 0
                statusNegative = value !in 0..<0x80
            }
            IRDataType.WORD -> {
                registers.setUW(reg, value.toUShort())
                statusZero = value == 0
                statusNegative = value !in 0..<0x8000
            }
            IRDataType.LONG -> {
                registers.setSL(reg, value)
                statusZero = value == 0
                statusNegative = value < 0
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("attempt to set integer result register but float type")
        }
    }

    private fun InsPUSH(i: IRInstruction) {
        if(valueStack.size >= VALUE_STACK_MAX)
            throw StackOverflowError("valuestack limit $VALUE_STACK_MAX exceeded")

        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!)
                valueStack.add(value)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!)
                valueStack.pushw(value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)
                valueStack.pushl(value)
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
            IRDataType.LONG -> setResultReg(i.reg1!!, valueStack.popl(), i.type!!)
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
                IRDataType.LONG -> valueStack.pushl(value.value as Int)
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
        if(i.type==IRDataType.FLOAT) {
            if (i.immediateFp != null)
                registers.setFloat(i.fpReg1!!, i.immediateFp!!)
            else {
                if (i.labelSymbol == null)
                    throw IllegalArgumentException("expected LOAD of immediate or labelsymbol")
                registers.setFloat(i.fpReg1!!, i.address!!.value.toDouble())
            }
        }
        else {
            if(i.immediate!=null) {
                setResultReg(i.reg1!!, i.immediate!!, i.type!!)
                statusbitsNZ(i.immediate!!, i.type!!)
            }
            else {
                if(i.labelSymbol==null)
                    throw IllegalArgumentException("expected LOAD of address of labelsymbol")
                setResultReg(i.reg1!!, i.address!!.value.toInt(), i.type!!)
                statusbitsNZ(i.address!!.value.toInt(), i.type!!)
            }
        }
        nextPc()
    }

    private fun InsLOADM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(i.address!!.value)
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(i.address!!.value)
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(i.address!!.value)
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!!.value))
        }
        nextPc()
    }

    private fun InsLOADI(i: IRInstruction) {
        val offset = i.immediate!!
        require(offset in 0..65535)
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(registers.getUW(i.reg2!!).toUInt() + offset.toUInt())
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(registers.getUW(i.reg2!!).toUInt() + offset.toUInt())
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(registers.getUW(i.reg2!!).toUInt() + offset.toUInt())
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> {
                registers.setFloat(i.fpReg1!!, memory.getFloat(registers.getUW(i.reg1!!).toUInt() + offset.toUInt()))
            }
        }
        nextPc()
    }

    private fun InsLOADX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(i.address!!.value + registers.getUB(i.reg2!!).toUInt())
                registers.setUB(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(i.address!!.value + registers.getUB(i.reg2!!).toUInt())
                registers.setUW(i.reg1!!, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(i.address!!.value + registers.getUB(i.reg2!!).toUInt())
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, memory.getFloat(i.address!!.value + registers.getUB(i.reg1!!).toUInt()))
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg2!!)
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg2!!))
        }
        nextPc()
    }

    private fun InsSTOREM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!.value, registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(i.address!!.value, registers.getUW(i.reg1!!))
            IRDataType.LONG -> memory.setSL(i.address!!.value, registers.getSL(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(i.address!!.value, registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREI(i: IRInstruction) {
        val offset = i.immediate!!
        require(offset in 0..65535)
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg2!!).toUInt() + offset.toUInt(), registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg2!!).toUInt() + offset.toUInt(), registers.getUW(i.reg1!!))
            IRDataType.LONG -> memory.setSL(registers.getUW(i.reg2!!).toUInt() + offset.toUInt(), registers.getSL(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toUInt() + offset.toUInt(), registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!.value + registers.getUB(i.reg2!!).toUInt(), registers.getUB(i.reg1!!))
            IRDataType.WORD -> memory.setUW(i.address!!.value + registers.getUB(i.reg2!!).toUInt(), registers.getUW(i.reg1!!))
            IRDataType.LONG -> memory.setSL(i.address!!.value + registers.getUB(i.reg2!!).toUInt(), registers.getSL(i.reg1!!))
            IRDataType.FLOAT -> memory.setFloat(i.address!!.value + registers.getUB(i.reg1!!).toUInt(), registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsSTOREZM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!.value, 0u)
            IRDataType.WORD -> memory.setUW(i.address!!.value, 0u)
            IRDataType.LONG -> memory.setSL(i.address!!.value, 0)
            IRDataType.FLOAT -> memory.setFloat(i.address!!.value, 0.0)
        }
        nextPc()
    }

    private fun InsSTOREZI(i: IRInstruction) {
        val offset = i.immediate!!
        require(offset in 0..65535)
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(registers.getUW(i.reg1!!).toUInt() + offset.toUInt(), 0u)
            IRDataType.WORD -> memory.setUW(registers.getUW(i.reg1!!).toUInt() + offset.toUInt(), 0u)
            IRDataType.LONG -> memory.setSL(registers.getUW(i.reg1!!).toUInt() + offset.toUInt(), 0)
            IRDataType.FLOAT -> memory.setFloat(registers.getUW(i.reg1!!).toUInt() + offset.toUInt(), 0.0)
        }
        nextPc()
    }

    private fun InsSTOREZX(i: IRInstruction) {
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(i.address!!.value + registers.getUB(i.reg1!!).toUInt(), 0u)
            IRDataType.WORD -> memory.setUW(i.address!!.value + registers.getUB(i.reg1!!).toUInt(), 0u)
            IRDataType.LONG -> memory.setSL(i.address!!.value + registers.getUB(i.reg1!!).toUInt(), 0)
            IRDataType.FLOAT -> memory.setFloat(i.address!!.value + registers.getUB(i.reg1!!).toUInt(), 0.0)
        }
        nextPc()
    }

    private fun InsJUMP(i: IRInstruction) {
        branchTo(i)
    }

    private fun InsJUMPI(i: IRInstruction) {
        val artificialAddress: UInt = registers.getUW(i.reg1!!).toUInt()
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
                IRDataType.LONG -> memory.setSL(arg.address!!, registers.getSL(arg.reg.registerNum))
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
                    if(returns.isNotEmpty()) {
                        val value = i.immediate!!.toUByte()
                        registers.setUB(returns.single().registerNum, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty()) {
                        val value = i.immediate!!.toUShort()
                        registers.setUW(returns.single().registerNum, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.LONG -> {
                    if(returns.isNotEmpty()) {
                        val value = i.immediate!!
                        registers.setSL(returns.single().registerNum, value)
                    } else {
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
                    if(returns.isNotEmpty()) {
                        val value = registers.getUB(i.reg1!!)
                        registers.setUB(returns.single().registerNum, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getUW(i.reg1!!)
                        registers.setUW(returns.single().registerNum, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.LONG -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getSL(i.reg1!!)
                        registers.setSL(returns.single().registerNum, value)
                    } else {
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)+1
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)+1f)
        }
        nextPc()
    }

    private fun InsINCM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val value = memory.getSL(address)+1
                memory.setSL(address, value)
                statusbitsNZ(value, i.type!!)
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)-1
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, registers.getFloat(i.fpReg1!!)-1f)
        }
        nextPc()
    }

    private fun InsDECM(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (memory.getUB(i.address!!.value)-1u).toUByte()
                memory.setUB(i.address!!.value, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.WORD -> {
                val value = (memory.getUW(i.address!!.value)-1u).toUShort()
                memory.setUW(i.address!!.value, value)
                statusbitsNZ(value.toInt(), i.type!!)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(i.address!!.value)-1
                memory.setSL(i.address!!.value, value)
                statusbitsNZ(value, i.type!!)
            }
            IRDataType.FLOAT -> memory.setFloat(i.address!!.value, memory.getFloat(i.address!!.value)-1f)
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
            IRDataType.LONG -> {
                val value = -registers.getSL(i.reg1!!)
                registers.setSL(i.reg1!!, value)
                statusbitsNZ(value, IRDataType.LONG)
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, -registers.getFloat(i.fpReg1!!))
        }
        nextPc()
    }

    private fun InsNEGM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val value = -memory.getSL(address)
                memory.setSL(address, value)
                statusbitsNZ(value, IRDataType.LONG)
            }
            IRDataType.FLOAT -> memory.setFloat(address, -memory.getFloat(address))
        }
        nextPc()
    }

    private fun InsADDR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("+", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> plusMinusMultAnyWord("+", i.reg1!!, i.reg2!!)
            IRDataType.LONG -> plusMinusMultAnyLong("+", i.reg1!!, i.reg2!!)
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
            IRDataType.LONG -> plusMinusMultConstLong("+", i.reg1!!, i.immediate!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "+", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsADDM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("+", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("+", i.reg1!!, address)
            IRDataType.LONG -> plusMinusMultAnyLongInplace("+", i.reg1!!, address)
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
            IRDataType.LONG -> plusMinusMultAnyLong("-", i.reg1!!, i.reg2!!)
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
            IRDataType.LONG -> plusMinusMultConstLong("-", i.reg1!!, i.immediate!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "-", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsSUBM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("-", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("-", i.reg1!!, address)
            IRDataType.LONG -> plusMinusMultAnyLongInplace("-", i.reg1!!, address)
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
            IRDataType.LONG -> throw IllegalArgumentException("mulr unsigned long not supported")
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
            IRDataType.LONG -> throw IllegalArgumentException("mul unsigned long not supported")
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsMULM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("*", i.reg1!!, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("*", i.reg1!!, address)
            IRDataType.LONG -> throw IllegalArgumentException("mulm unsigned long not supported")
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
            IRDataType.BYTE -> multiplyAnyByteSigned(i.reg1!!, i.reg2!!)
            IRDataType.WORD -> multiplyAnyWordSigned(i.reg1!!, i.reg2!!)
            IRDataType.LONG -> multiplyAnyLongSigned(i.reg1!!, i.reg2!!)
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
            IRDataType.BYTE -> multiplyConstByteSigned(i.reg1!!, i.immediate!!.toByte())
            IRDataType.WORD -> multiplyConstWordSigned(i.reg1!!, i.immediate!!.toShort())
            IRDataType.LONG -> multiplyConstLongSigned(i.reg1!!, i.immediate!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "*", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsMULSM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> multiplyAnyByteSignedInplace(i.reg1!!, address)
            IRDataType.WORD -> multiplyAnyWordSignedInplace(i.reg1!!, address)
            IRDataType.LONG -> multiplyAnyLongSignedInplace(i.reg1!!, address)
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
            IRDataType.LONG -> throw IllegalArgumentException("divr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIV(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("/", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("/", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.LONG -> throw IllegalArgumentException("div unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsignedInplace("/", i.reg1!!, address)
            IRDataType.WORD -> divModWordUnsignedInplace("/", i.reg1!!, address)
            IRDataType.LONG -> throw IllegalArgumentException("divm unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVSR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSigned("/", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divModWordSigned("/", i.reg1!!, i.reg2!!)
            IRDataType.LONG -> divModLongSigned("/", i.reg1!!, i.reg2!!)
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
            IRDataType.LONG -> divModConstLongSigned("/", i.reg1!!, i.immediate!!)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(i.fpReg1!!)
                val result = arithFloat(left, "/", i.immediateFp!!)
                registers.setFloat(i.fpReg1!!, result)
            }
        }
        nextPc()
    }

    private fun InsDIVSM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSignedInplace("/", i.reg1!!, address)
            IRDataType.WORD -> divModWordSignedInplace("/", i.reg1!!, address)
            IRDataType.LONG -> divModLongSignedInplace("/", i.reg1!!, address)
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
            IRDataType.LONG -> throw IllegalArgumentException("modr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("%", i.reg1!!, i.immediate!!.toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("%", i.reg1!!, i.immediate!!.toUShort())
            IRDataType.LONG -> throw IllegalArgumentException("mod unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMODSR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSigned("%", i.reg1!!, i.reg2!!)
            IRDataType.WORD -> divModWordSigned("%", i.reg1!!, i.reg2!!)
            IRDataType.LONG -> divModLongSigned("%", i.reg1!!, i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMODS(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteSigned("%", i.reg1!!, i.immediate!!.toByte())
            IRDataType.WORD -> divModConstWordSigned("%", i.reg1!!, i.immediate!!.toShort())
            IRDataType.LONG -> divModConstLongSigned("%", i.reg1!!, i.immediate!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMODR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModUByte(i.reg1!!, i.reg2!!)       // division+remainder results on value stack
            IRDataType.WORD -> divAndModUWord(i.reg1!!, i.reg2!!)       // division+remainder results on value stack
            IRDataType.LONG -> throw IllegalArgumentException("divmodr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstUByte(i.reg1!!, i.immediate!!.toUByte())    // division+remainder results on value stack
            IRDataType.WORD -> divAndModConstUWord(i.reg1!!, i.immediate!!.toUShort())   // division+remainder results on value stack
            IRDataType.LONG -> throw IllegalArgumentException("divmod unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSDIVMODR(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModSByte(i.reg1!!, i.reg2!!)       // signed division+remainder results on value stack
            IRDataType.WORD -> divAndModSWord(i.reg1!!, i.reg2!!)       // signed division+remainder results on value stack
            IRDataType.LONG -> throw IllegalArgumentException("divmodr signed long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSDIVMOD(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstSByte(i.reg1!!, i.immediate!!.toByte())    // signed division+remainder results on value stack
            IRDataType.WORD -> divAndModConstSWord(i.reg1!!, i.immediate!!.toShort())   // signed division+remainder results on value stack
            IRDataType.LONG -> throw IllegalArgumentException("divmod signed long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSGN(i: IRInstruction) {
        val sign: Int = when (i.type!!) {
            IRDataType.BYTE -> registers.getSB(i.reg2!!).toInt().sign
            IRDataType.WORD -> registers.getSW(i.reg2!!).toInt().sign
            IRDataType.LONG -> registers.getSL(i.reg2!!).sign
            IRDataType.FLOAT -> registers.getFloat(i.fpReg1!!).sign.toInt()
        }
        registers.setSB(i.reg1!!, sign.toByte())
        statusbitsComparisonWithOverflow(sign, 0, IRDataType.BYTE)
        nextPc()
    }

    private fun InsSQRT(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(i.reg1!!, sqrt(registers.getUB(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.WORD -> registers.setUB(i.reg1!!, sqrt(registers.getUW(i.reg2!!).toDouble()).toInt().toUByte())
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg2!!)
                if(value<0)
                    throw IllegalArgumentException("sqrt of negative long $value reg=${i.reg2}")
                registers.setSL(i.reg1!!, sqrt(value.toDouble()).toInt())
            }
            IRDataType.FLOAT -> registers.setFloat(i.fpReg1!!, sqrt(registers.getFloat(i.fpReg2!!)))
        }
        nextPc()
    }

    private fun InsSQUARE(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg2!!).toInt()
                registers.setUB(i.reg1!!, (value*value).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg2!!).toInt()
                registers.setUW(i.reg1!!, (value*value).toUShort())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg2!!)
                registers.setSL(i.reg1!!, value*value)
            }
            IRDataType.FLOAT -> {
                val value = registers.getFloat(i.fpReg2!!)
                registers.setFloat(i.fpReg1!!, value*value)
            }
        }
        nextPc()
    }

    private fun InsCMP(i: IRInstruction) {
        val type = i.type!!
        val left = when(type) {
            IRDataType.BYTE -> registers.getUB(i.reg1!!).toInt()
            IRDataType.WORD -> registers.getUW(i.reg1!!).toInt()
            IRDataType.LONG -> registers.getSL(i.reg1!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        val right = when(type) {
            IRDataType.BYTE -> registers.getUB(i.reg2!!).toInt()
            IRDataType.WORD -> registers.getUW(i.reg2!!).toInt()
            IRDataType.LONG -> registers.getSL(i.reg2!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparisonWithOverflow(left, right, type)
        nextPc()
    }

    private fun InsCMPI(i: IRInstruction) {
        val type = i.type!!
        val left = when(type) {
            IRDataType.BYTE -> registers.getUB(i.reg1!!).toInt()
            IRDataType.WORD -> registers.getUW(i.reg1!!).toInt()
            IRDataType.LONG -> registers.getSL(i.reg1!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        val right = when(type) {
            IRDataType.BYTE -> i.immediate!! and 0xff
            IRDataType.WORD -> i.immediate!! and 0xffff
            IRDataType.LONG -> i.immediate!!
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparisonWithOverflow(left, right, type)
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


    private fun InsBITTST(i: IRInstruction) {
        if (i.reg1 == null) throw IllegalArgumentException("bittst needs a register")
        val mask = 1 shl i.immediate!!
        val value: Int = when(i.type!!) {
            IRDataType.BYTE -> registers.getUB(i.reg1!!).toInt()
            IRDataType.WORD -> registers.getUW(i.reg1!!).toInt()
            IRDataType.LONG -> registers.getSL(i.reg1!!)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusZero = value and mask == 0
        nextPc()
    }

    private fun InsBITSET(i: IRInstruction) {
        val mask = 1 shl i.immediate!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt() or mask
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt() or mask
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!) or mask
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(registers.getSL(i.reg1!!), i.type!!)
        nextPc()
    }

    private fun InsBITCLR(i: IRInstruction) {
        val mask = 1 shl i.immediate!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt() and mask.inv()
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt() and mask.inv()
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!) and mask.inv()
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(registers.getSL(i.reg1!!), i.type!!)
        nextPc()
    }

    private fun InsBITTOG(i: IRInstruction) {
        val mask = 1 shl i.immediate!!
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(i.reg1!!).toInt() xor mask
                registers.setUB(i.reg1!!, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg1!!).toInt() xor mask
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!) xor mask
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(registers.getSL(i.reg1!!), i.type!!)
        nextPc()
    }

    private fun InsEXT(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setUW(i.reg1!!, registers.getUB(i.reg2!!).toUShort())
            IRDataType.WORD -> registers.setSL(i.reg1!!, registers.getUW(i.reg2!!).toInt())
            IRDataType.LONG -> throw IllegalArgumentException("ext.l makes no sense, 32 bits is already the widest you can get")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsEXTS(i: IRInstruction) {
        when(i.type!!){
            IRDataType.BYTE -> registers.setSW(i.reg1!!, registers.getSB(i.reg2!!).toShort())
            IRDataType.WORD -> registers.setSL(i.reg1!!, registers.getSW(i.reg2!!).toInt())
            IRDataType.LONG -> throw IllegalArgumentException("exts.l makes no sense, 32 bits is already the widest you can get")
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
            IRDataType.LONG -> registers.setSL(i.reg1!!, value)
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
            IRDataType.LONG -> {
                value = registers.getSL(i.reg1!!) and i.immediate!!
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsANDM(i: IRInstruction) {
        val value: Int
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(i.reg1!!)
                value = left and right
                memory.setSL(address, value)
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
            IRDataType.LONG -> registers.setSL(i.reg1!!, value)
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
            IRDataType.LONG -> {
                value = registers.getSL(i.reg1!!) or i.immediate!!
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsORM(i: IRInstruction) {
        val value: Int
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(i.reg1!!)
                value = left or right
                memory.setSL(address, value)
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
            IRDataType.LONG -> registers.setSL(i.reg1!!, value)
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
            IRDataType.LONG -> {
                value = registers.getSL(i.reg1!!) xor i.immediate!!
                registers.setSL(i.reg1!!, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsNZ(value, i.type!!)
        nextPc()
    }

    private fun InsXORM(i: IRInstruction) {
        val value: Int
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(i.reg1!!)
                value = left xor right
                memory.setSL(address, value)
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
            IRDataType.LONG -> registers.setSL(i.reg1!!, registers.getSL(i.reg1!!).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsINVM(i: IRInstruction) {
        val address = i.address!!.value
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, memory.getUB(address).inv())
            IRDataType.WORD -> memory.setUW(address, memory.getUW(address).inv())
            IRDataType.LONG -> memory.setSL(address, memory.getSL(address).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRN(i: IRInstruction) {
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(i.reg1!!, (left shr right).toByte())
            IRDataType.WORD -> registers.setSW(i.reg1!!, (left shr right).toShort())
            IRDataType.LONG -> registers.setSL(i.reg1!!, left shr right)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRNM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, memvalue shr operand)
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)
                statusCarry = (value and 1)!=0
                registers.setSL(i.reg1!!, value shr 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val value = memory.getSL(address)
                statusCarry = (value and 1)!=0
                memory.setSL(address, value shr 1)
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
            IRDataType.LONG -> registers.setSL(i.reg1!!, (left shr right.toInt()).toInt())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRNM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, memvalue ushr operand)
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)
                statusCarry = (value and 1)!=0
                registers.setSL(i.reg1!!, value ushr 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val value = memory.getSL(address)
                statusCarry = (value and 1)!=0
                memory.setSL(address, value ushr 1)
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
            IRDataType.LONG -> {
                registers.setSL(i.reg1!!, (left shl right.toInt()).toInt())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSLNM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, memvalue shl operand)
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
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg1!!)
                statusCarry = value<0
                registers.setSL(i.reg1!!, value shl 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSLM(i: IRInstruction) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val value = memory.getSL(address)
                statusCarry = value<0
                memory.setSL(address, value shl 1)
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
            IRDataType.LONG -> {
                val orig = registers.getSL(i.reg1!!).toUInt()
                newStatusCarry = (orig and 1u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 0x80000000u else 0u
                    (orig.rotateRight(1) or carry)
                } else
                    orig.rotateRight(1)
                registers.setSL(i.reg1!!, rotated.toInt())
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
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val orig = memory.getSL(address).toUInt()
                newStatusCarry = (orig and 1u) != 0u
                val rotated: UInt = (if (useCarry) {
                    val carry = if (statusCarry) 0x80000000u else 0u
                    (orig.rotateRight(1) or carry)
                } else
                    orig.rotateRight(1))
                memory.setSL(address, rotated.toInt())
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
            IRDataType.LONG -> {
                val orig = registers.getSL(i.reg1!!).toUInt()
                newStatusCarry = (orig and 0x80000000u) != 0u
                val rotated: UInt = (if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.rotateLeft(1) or carry)
                } else
                    orig.rotateLeft(1))
                registers.setSL(i.reg1!!, rotated.toInt())
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsROLM(i: IRInstruction, useCarry: Boolean) {
        val address = i.address!!.value
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
            IRDataType.LONG -> {
                val orig = memory.getSL(address).toUInt()
                newStatusCarry = (orig and 0x80000000u) != 0u
                val rotated: UInt = (if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.rotateLeft(1) or carry)
                } else
                    orig.rotateLeft(1))
                memory.setSL(address, rotated.toInt())
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsLSIGB(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg2!!)
                val byte = value.toUByte()
                registers.setUB(i.reg1!!, byte)
                statusbitsNZ(byte.toInt(), i.type!!)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg2!!)
                val byte = value.toUByte()
                registers.setUB(i.reg1!!, byte)
                statusbitsNZ(byte.toInt(), i.type!!)
            }
            else -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSIGW(i: IRInstruction) {
        if (i.type!! == IRDataType.LONG) {
            val value = registers.getSL(i.reg2!!)
            val word = value.toUShort()
            registers.setUW(i.reg1!!, word)
            statusbitsNZ(word.toInt(), i.type!!)
        }
        else throw IllegalArgumentException("invalid float type for this instruction $i")
        nextPc()
    }

    private fun InsMSIGB(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.WORD -> {
                val value = registers.getUW(i.reg2!!)
                val newValue = value.toInt() ushr 8
                statusbitsNZ(newValue, i.type!!)
                registers.setUB(i.reg1!!, newValue.toUByte())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(i.reg2!!)
                val newValue = value ushr 24
                statusbitsNZ(newValue, i.type!!)
                registers.setUB(i.reg1!!, newValue.toUByte())
            }
            else -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsBSIGB(i: IRInstruction) {
        val value = registers.getSL(i.reg2!!)
        val newValue = value ushr 16 and 255
        statusbitsNZ(newValue, i.type!!)
        registers.setUB(i.reg1!!, newValue.toUByte())
        nextPc()
    }

    private fun InsMIDB(i: IRInstruction) {
        val value = registers.getSL(i.reg2!!)
        val newValue = value ushr 8 and 255
        statusbitsNZ(newValue, i.type!!)
        registers.setUB(i.reg1!!, newValue.toUByte())
        nextPc()
    }

    private fun InsMSIGW(i: IRInstruction) {
        if (i.type!! == IRDataType.LONG) {
            val value = registers.getSL(i.reg2!!)
            val newValue = value ushr 16
            statusbitsNZ(newValue, i.type!!)
            registers.setUW(i.reg1!!, newValue.toUShort())
        }
        else throw IllegalArgumentException("invalid float type for this instruction $i")
        nextPc()
    }

    private fun InsCONCAT(i: IRInstruction) {
        when(i.type!!) {
            IRDataType.BYTE -> {
                val msb = registers.getUB(i.reg2!!)
                val lsb = registers.getUB(i.reg3!!)
                val value = ((msb.toInt() shl 8) or lsb.toInt())
                registers.setUW(i.reg1!!, value.toUShort())
            }
            IRDataType.WORD -> {
                val msw = registers.getUW(i.reg2!!)
                val lsw = registers.getUW(i.reg3!!)
                registers.setSL(i.reg1!!, ((msw.toInt() shl 16) or lsw.toInt()))
            }
            IRDataType.LONG -> throw IllegalArgumentException("concat.l makes no sense, 32 bits is already the widest")
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

    private fun InsFFROMSL(i: IRInstruction) {
        registers.setFloat(i.fpReg1!!, registers.getSL(i.reg1!!).toDouble())
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

    private fun InsFTOSL(i: IRInstruction) {
        registers.setSL(i.reg1!!, registers.getFloat(i.fpReg1!!).toInt())
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

    private fun InsLOADHR(i: IRInstruction) {
        when(i.immediate) {
            0 -> registers.setUB(i.reg1!!, hardwareRegisterA)
            1 -> registers.setUB(i.reg1!!, hardwareRegisterX)
            2 -> registers.setUB(i.reg1!!, hardwareRegisterY)
            3 -> registers.setUW(i.reg1!!, ((hardwareRegisterX.toUInt() shl 8) + hardwareRegisterA).toUShort())
            4 -> registers.setUW(i.reg1!!, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterA).toUShort())
            5 -> registers.setUW(i.reg1!!, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterX).toUShort())
            else -> throw IllegalArgumentException("unknown hardware register slot: ${i.immediate}")
        }
        nextPc()
    }

    private fun InsSTOREHR(i: IRInstruction) {
        when(i.immediate) {
            0 -> hardwareRegisterA = registers.getUB(i.reg1!!)
            1 -> hardwareRegisterX = registers.getUB(i.reg1!!)
            2 -> hardwareRegisterY = registers.getUB(i.reg1!!)
            3 -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterX = (word shr 8).toUByte()
            }
            4 -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            5 -> {
                val word = registers.getUW(i.reg1!!).toUInt()
                hardwareRegisterX = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            else -> throw IllegalArgumentException("unknown hardware register slot: ${i.immediate}")
        }
        nextPc()
    }

    internal var window: GraphicsWindow? = null

}

// probably called via reflection
class VmRunner: IVirtualMachineRunner {
    override fun runProgram(irSource: String, quiet: Boolean, traceEnabled: Boolean) {
        runAndTestProgram(irSource, quiet, traceEnabled) { /* no tests */ }
    }

    fun runAndTestProgram(irSource: String, quiet: Boolean = false, traceEnabled: Boolean = false, test: (VirtualMachine) -> Unit = {}) {
        val irProgram = IRFileReader().read(irSource)
        val vm = VirtualMachine(irProgram)
        vm.traceEnabled = traceEnabled
//        vm.breakpointHandler = { pcChunk, pcIndex ->
//            println("UNHANDLED BREAKPOINT")
//            println("  IN CHUNK: $pcChunk(${pcChunk.label})  INDEX: $pcIndex = INSTR ${pcChunk.instructions[pcIndex]}")
//        }
        vm.run(quiet)
        test(vm)
    }
}
