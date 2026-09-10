package prog8.vm

import prog8.code.SymbolNames
import prog8.code.core.toHex
import prog8.code.target.IVirtualMachineRunner
import prog8.code.target.VMTarget
import prog8.intermediate.*
import kotlin.math.*
import kotlin.random.Random

/*

Virtual machine specs:

Program to execute is not stored in the system memory, it's just a separate list of instructions.
100K virtual integer registers (32 bits wide, can also be used as 8 or 16 bits). r0-r99999
100K virtual floating point registers (64 bits double precision). fr0-fr99999
16 MB of memory. Memory pointers (addresses) are 32 bits.
Value stack, max 128 entries of 1 byte each.

Status flags: Carry, Zero, Negative, Overflow.

Status bit contract (see CpuType.statusBitsOnMultiByteOps for the rationale):
  - For the VIRTUAL target (statusBitsOnMultiByteOps = false) the contract is STRICT:
    status flags are only modified by CMP, CMPI, SEC, CLC, SGN and BITTST.
    All other instructions (LOAD, INC, DEC, NEG, AND, OR, XOR, ADD, SUB, MUL, DIV, ...)
    do NOT touch the status flags. The IR generator emits an explicit CMP/CMPI
    before any branch that depends on the result of such an instruction.
  - For targets where statusBitsOnMultiByteOps = true, the contract is more
    permissive: arithmetic and load operations on multi-byte values are
    expected to set Z and N correctly for the full value (e.g. M68000's
    `move.w #imm,d0` sets Z based on the 16-bit value).

This is the only VM-spec-level change. All other VM semantics (memory model,
calling convention, syscalls, etc.) are unchanged.

 */

class ProgramExitException(val status: Int): Exception()


class BreakpointException(val pcChunk: IRCodeChunk, val pcIndex: Int): Exception()


@Suppress("FunctionName")
class VirtualMachine(private val irProgram: IRProgram) {

    init {
        // The VM implements the STRICT status-bits contract: only CMP, CMPI, SEC, CLC,
        // SGN and BITTST modify the status flags. The many other places in the VM that
        // used to set Z/N as a side effect of arithmetic or load have been removed
        // because the IR generator now always emits an explicit CMPI before any branch
        // that depends on the result. This is consistent with the CpuType contract
        // (see CpuType.statusBitsOnMultiByteOps) - the VM target sets this to false.
        require(!irProgram.options.compTarget.cpu.statusBitsOnMultiByteOps) {
            "VirtualMachine only supports the strict status-bits contract " +
            "(statusBitsOnMultiByteOps=false). The IR program was compiled for a " +
            "target that honors the multi-byte status-bits contract, which the VM " +
            "does not implement. To run such a program in the VM you must change the " +
            "target's statusBitsOnMultiByteOps to false."
        }
    }

    class CallSiteContext(val returnChunk: IRCodeChunk, val returnIndex: Int, val callSite: CallSite)

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

    // m68k-style hardware registers used by the 32-bit VIRTUAL target calling convention.
    // NOTE: these are "for show" / interface compatibility only. The VM is NOT a true m68k:
    //   - Floating point is stored as Double (8 bytes), not Single (4 bytes) like m68k.
    //   - The machine is little-endian (x86 native), while m68k is big-endian.
    // Struct instances containing a `float` field therefore have size 14 on VM (4-byte pointer
    // + 8-byte float + 2-byte uword) but size 10 on m68k (4-byte pointer + 4-byte float + 2-byte uword),
    // and the `area` field offset is 12 on VM vs 8 on m68k. The IR code generator already produces
    // the correct per-target sizes and offsets.
    internal val hardwareRegisterD = Array(8) { 0u }      // D0-D7 (slots 10-17)
    internal val hardwareRegisterAddr = Array(7) { 0u }   // A0-A6 (slots 18-24)
    internal val hardwareRegisterFP = Array(8) { 0.0 }    // FP0-FP7 (slots 25-32)

    // X ABC 4-byte PRNG, same as 6502/m68k targets (math.asm:490 / shared_m68k_math.p8:165)
    internal var rnd_x1: UByte = 0x00u
    internal var rnd_c1: UByte = 0xC2u
    internal var rnd_a1: UByte = 0x11u
    internal var rnd_b1: UByte = 0x37u
    internal var randomGeneratorFloats = Random(0xc0d3dbad)
    internal var mul16LastUpper = 0u

    internal fun nextRandWord(): UShort {
        // X ABC algorithm, identical to 6502 math.asm randword and m68k shared_m68k_math rndw
        rnd_x1 = (rnd_x1 + 1u).toUByte()
        var a = rnd_x1.toInt() xor rnd_c1.toInt() xor rnd_a1.toInt()
        rnd_a1 = a.toUByte()
        var sum = a + rnd_b1.toInt() // clc
        var carry: Int
        a = sum and 0xFF
        rnd_b1 = a.toUByte()
        carry = a and 1 // lsr carry
        a = (a ushr 1) and 0xFF
        a = a xor rnd_a1.toInt()
        sum = a + rnd_c1.toInt() + carry
        a = sum and 0xFF
        rnd_c1 = a.toUByte()
        return ((rnd_b1.toInt() shl 8) or rnd_c1.toInt()).toUShort()
    }
    internal val wordArrayIndex = machine.POINTER_MEM_SIZE > 2u

    init {
        val (prg, labelAddr) = VmProgramLoader().load(irProgram, memory)
        program = prg
        artificialLabelAddresses = mutableMapOf()
        labelAddr.forEach { (labelname, artificialAddress) ->
            artificialLabelAddresses[artificialAddress] = program.single { SymbolNames.stripPrefixes(it.label ?: "")==labelname }
        }
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
        hardwareRegisterD.fill(0u)
        hardwareRegisterAddr.fill(0u)
        hardwareRegisterFP.fill(0.0)
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
                is IRLoopChunk -> {
                    // loops should have been expanded; enter its body
                    val firstBody = nextChunk.body.firstOrNull() as? IRCodeChunk
                        ?: throw IllegalArgumentException("VM cannot run empty loop $nextChunk")
                    pcChunk = firstBody
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
        // Branch/jump/call targets are resolved via the program's label linkage (filled in by
        // IRProgram.linkChunks(), which VmProgramLoader.load() calls after loop expansion), rather
        // than through a mutable per-instruction chunk reference (that field no longer exists).
        when (val target = i.codeTarget) {
            is CodeReference.Label -> {
                when (val chunk = irProgram.resolveCodeTarget(target)) {
                    is IRCodeChunk -> {
                        pcChunk = chunk
                        pcIndex = 0
                    }
                    is IRLoopChunk -> {
                        // Loops should have been expanded by VmProgramLoader; if still present, enter its first body chunk
                        val firstBody = chunk.body.firstOrNull() as? IRCodeChunk
                            ?: throw IllegalArgumentException("vm cannot branch to empty loop $chunk")
                        pcChunk = firstBody
                        pcIndex = 0
                    }
                    is IRInlineAsmChunk -> TODO("branch to inline asm chunk")
                    is IRInlineBinaryChunk -> throw IllegalArgumentException("can't branch to inline binary chunk")
                    null -> throw IllegalArgumentException("label '${target.name}' not found for $i")
                }
            }
            is CodeReference.Absolute ->
                throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${target.address.value.toInt().toHex()})")
            is CodeReference.Indirect ->
                throw IllegalArgumentException("vm program can't jump to system memory address (${i} = ${registers.getUW(target.pointer.registerNumber)})")
            null -> throw IllegalArgumentException("no branchtarget in $i")
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
            Opcode.LOADP_INC -> InsLOADP_INC(ins)
            Opcode.STOREP_INC -> InsSTOREP_INC(ins)
            Opcode.STOREM -> InsSTOREM(ins)
            Opcode.STOREX -> InsSTOREX(ins)
            Opcode.STOREI-> InsSTOREI(ins)
            Opcode.STOREZM -> InsSTOREZM(ins)
            Opcode.STOREZX -> InsSTOREZX(ins)
            Opcode.STOREZI -> InsSTOREZI(ins)
            Opcode.STOREIM -> InsSTOREIM(ins)
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
            Opcode.ADDIM -> InsADDIM(ins)
            Opcode.SUBR -> InsSUBR(ins)
            Opcode.SUB -> InsSUB(ins)
            Opcode.SUBM -> InsSUBM(ins)
            Opcode.SUBIM -> InsSUBIM(ins)
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
            Opcode.EXTL -> InsEXTL(ins)
            Opcode.EXTLS -> InsEXTLS(ins)
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
            Opcode.ASRI -> InsASRI(ins)
            Opcode.LSRI -> InsLSRI(ins)
            Opcode.LSLI -> InsLSLI(ins)
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
        // The VM implements the STRICT status-bits contract: setResultReg does NOT
        // modify Z or N. Only CMP/CMPI/SEC/CLC/SGN/BITTST and the shift/rotate
        // operations (for the carry-out) do. The init-block require() asserts that the
        // target has statusBitsOnMultiByteOps=false, which means the IR generator
        // always emits an explicit CMPI before any branch that depends on the result.
        // See CpuType.statusBitsOnMultiByteOps for the rationale.
        when(type) {
            IRDataType.BYTE -> {
                registers.setUB(reg, value.toUByte())
            }
            IRDataType.WORD -> {
                registers.setUW(reg, value.toUShort())
            }
            IRDataType.POINTER -> {
                registers.setUL(reg, value.toUInt())
            }
            IRDataType.LONG -> {
                registers.setSL(reg, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("attempt to set integer result register but float type")
        }
    }

    // Helpers to compute effective addresses from the structured memory operands.
    // VmProgramLoader resolves symbolic bases to absolute addresses before the VM runs,
    // so base+displacement (+index*scale) is all that is left to compute here.
    private fun MemoryReference.requireBaseAddress(): UInt =
        absoluteAddress?.value ?: throw IllegalArgumentException("vm requires a resolved absolute memory address: $this")

    private fun MemoryReference.Direct.resolvedAddress(): UInt = requireBaseAddress() + displacement.toUInt()

    private fun MemoryReference.Indexed.resolvedBaseAddress(): UInt = requireBaseAddress() + displacement.toUInt()

    // element index register value, sized according to the target's index register width
    private fun RegisterOperand.readIndexValue(): UInt =
        if(wordArrayIndex) registers.getUW(registerNumber).toUInt() else registers.getUB(registerNumber).toUInt()

    // pointer register value, sized according to the target's pointer width
    private fun RegisterOperand.readPointerValue(): UInt =
        if(wordArrayIndex) registers.getSL(registerNumber).toUInt() else registers.getUW(registerNumber).toUInt()

    private fun InsPUSH(i: IRInstruction) {
        if(valueStack.size >= VALUE_STACK_MAX)
            throw StackOverflowError("valuestack limit $VALUE_STACK_MAX exceeded")

        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(src)
                valueStack.add(value)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(src)
                valueStack.pushw(value)
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(src)
                valueStack.pushl(value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                valueStack.pushl(value)
            }
            IRDataType.FLOAT -> {
                val value = registers.getFloat(src)
                valueStack.pushf(value)
            }
        }
        nextPc()
    }

    private fun InsPOP(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> setResultReg(dest, valueStack.removeLast().toInt(), i.type!!)
            IRDataType.WORD -> setResultReg(dest, valueStack.popw().toInt(), i.type!!)
            IRDataType.POINTER -> setResultReg(dest, valueStack.popl(), i.type!!)
            IRDataType.LONG -> setResultReg(dest, valueStack.popl(), i.type!!)
            IRDataType.FLOAT -> registers.setFloat(dest, valueStack.popf())
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
                IRDataType.POINTER -> valueStack.pushl(value.value as Int)
                IRDataType.LONG -> valueStack.pushl(value.value as Int)
                IRDataType.FLOAT -> valueStack.pushf(value.value as Double)
            }
            value.dt=null
        }
        val site = i.requireCallSite()
        val syscallNumber = (site.target as? CallTarget.SystemCall)?.number
            ?: throw IllegalArgumentException("syscall must have a SystemCall target: $i")
        val call = Syscall.fromInt(syscallNumber)
        SysCalls.call(call, site, this)   // note: any result value(s) are pushed back on the value stack
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
        val dest = i.requireDest().registerNumber
        val imm = i.requireImmediate()
        if(i.type==IRDataType.FLOAT) {
            when(imm) {
                is ImmediateOperand.FloatValue -> registers.setFloat(dest, imm.value)
                is ImmediateOperand.Integer -> registers.setFloat(dest, imm.value.toDouble())
                is ImmediateOperand.SymbolAddress -> throw IllegalArgumentException("expected LOAD of a resolved immediate or address, got unresolved symbol '${imm.symbol}'")
            }
        }
        else {
            when(imm) {
                is ImmediateOperand.Integer -> setResultReg(dest, imm.value, i.type!!)
                is ImmediateOperand.SymbolAddress -> throw IllegalArgumentException("expected LOAD of a resolved address, got unresolved symbol '${imm.symbol}'")
                is ImmediateOperand.FloatValue -> throw IllegalArgumentException("unexpected float immediate for non-float LOAD")
            }
        }
        nextPc()
    }

    private fun InsLOADM(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = memory.getUB(address)
                registers.setUB(dest, value)
            }
            IRDataType.WORD -> {
                val value = memory.getUW(address)
                registers.setUW(dest, value)
            }
            IRDataType.POINTER -> {
                val value = memory.getSL(address)
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(address)
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> registers.setFloat(dest, memory.getFloat(address))
        }
        nextPc()
    }

    private fun InsLOADI(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val mem = i.requireMemory() as MemoryReference.Indirect
        val offset = mem.displacement
        require(offset in 0..65535)
        val baseAddr = mem.pointer.readPointerValue()
        when(i.type!!) {
            IRDataType.FLOAT -> {
                val value = memory.getFloat(baseAddr + offset.toUInt())
                registers.setFloat(dest, value)
            }
            IRDataType.BYTE -> registers.setUB(dest, memory.getUB(baseAddr + offset.toUInt()))
            IRDataType.WORD -> registers.setUW(dest, memory.getUW(baseAddr + offset.toUInt()))
            IRDataType.POINTER -> registers.setUL(dest, memory.getUL(baseAddr + offset.toUInt()))
            IRDataType.LONG -> registers.setSL(dest, memory.getSL(baseAddr + offset.toUInt()))
        }
        nextPc()
    }

    private fun InsLOADP_INC(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val ptrVarAddr = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val ptr = memory.getSL(ptrVarAddr).toUInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, memory.getUB(ptr))
            IRDataType.WORD -> registers.setUW(dest, memory.getUW(ptr))
            IRDataType.POINTER -> registers.setUL(dest, memory.getUL(ptr))
            IRDataType.LONG -> registers.setSL(dest, memory.getSL(ptr))
            IRDataType.FLOAT -> throw IllegalStateException("LOADP_INC float not supported")
        }
        val inc = when(i.type!!) {
            IRDataType.BYTE -> 1
            IRDataType.WORD, IRDataType.POINTER -> if(i.type==IRDataType.POINTER && wordArrayIndex) 4 else 2
            IRDataType.LONG -> 4
            else -> 0
        }
        // pointer variable is LONG (32-bit)
        memory.setSL(ptrVarAddr, (ptr + inc.toUInt()).toInt())
        nextPc()
    }

    private fun InsSTOREP_INC(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val ptrVarAddr = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val ptr = memory.getSL(ptrVarAddr).toUInt()
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(ptr, registers.getUB(src))
            IRDataType.WORD -> memory.setUW(ptr, registers.getUW(src))
            IRDataType.POINTER -> memory.setUL(ptr, registers.getUL(src))
            IRDataType.LONG -> memory.setSL(ptr, registers.getSL(src))
            IRDataType.FLOAT -> throw IllegalStateException("STOREP_INC float not supported")
        }
        val inc = when(i.type!!) {
            IRDataType.BYTE -> 1
            IRDataType.WORD, IRDataType.POINTER -> if(i.type==IRDataType.POINTER && wordArrayIndex) 4 else 2
            IRDataType.LONG -> 4
            else -> 0
        }
        memory.setSL(ptrVarAddr, (ptr + inc.toUInt()).toInt())
        nextPc()
    }

    private fun InsLOADX(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val mem = i.requireMemory() as MemoryReference.Indexed
        val scale = mem.scale.toUInt()
        val base = mem.resolvedBaseAddress()
        val index = mem.index.readIndexValue()
        val address = base + index * scale
        when (i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, memory.getUB(address))
            IRDataType.WORD -> registers.setUW(dest, memory.getUW(address))
            IRDataType.POINTER -> registers.setUL(dest, memory.getUL(address))
            IRDataType.LONG -> registers.setSL(dest, memory.getSL(address))
            IRDataType.FLOAT -> registers.setFloat(dest, memory.getFloat(address))
        }
        nextPc()
    }

    private fun InsLOADR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(src)
                registers.setUB(dest, value)
            }
            IRDataType.WORD -> {
                val value = registers.getUW(src)
                registers.setUW(dest, value)
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(src)
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> registers.setFloat(dest, registers.getFloat(src))
        }
        nextPc()
    }

    private fun InsSTOREM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, registers.getUB(src))
            IRDataType.WORD -> memory.setUW(address, registers.getUW(src))
            IRDataType.POINTER -> memory.setSL(address, registers.getSL(src))
            IRDataType.LONG -> memory.setSL(address, registers.getSL(src))
            IRDataType.FLOAT -> memory.setFloat(address, registers.getFloat(src))
        }
        nextPc()
    }

    private fun InsSTOREI(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val mem = i.requireMemory() as MemoryReference.Indirect
        val offset = mem.displacement
        require(offset in 0..65535)
        val baseAddr = mem.pointer.readPointerValue()
        when (i.type!!) {
            IRDataType.FLOAT -> memory.setFloat(baseAddr + offset.toUInt(), registers.getFloat(src))
            IRDataType.BYTE -> memory.setUB(baseAddr + offset.toUInt(), registers.getUB(src))
            IRDataType.WORD -> memory.setUW(baseAddr + offset.toUInt(), registers.getUW(src))
            IRDataType.POINTER -> memory.setUL(baseAddr + offset.toUInt(), registers.getUL(src))
            IRDataType.LONG -> memory.setSL(baseAddr + offset.toUInt(), registers.getSL(src))
        }
        nextPc()
    }

    private fun InsSTOREX(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val mem = i.requireMemory() as MemoryReference.Indexed
        val scale = mem.scale.toUInt()
        val base = mem.resolvedBaseAddress()
        val index = mem.index.readIndexValue()
        val address = base + index * scale
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, registers.getUB(src))
            IRDataType.WORD -> memory.setUW(address, registers.getUW(src))
            IRDataType.POINTER -> memory.setUL(address, registers.getUL(src))
            IRDataType.LONG -> memory.setSL(address, registers.getSL(src))
            IRDataType.FLOAT -> memory.setFloat(address, registers.getFloat(src))
        }
        nextPc()
    }

    private fun InsSTOREZM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, 0u)
            IRDataType.WORD -> memory.setUW(address, 0u)
            IRDataType.POINTER -> memory.setSL(address, 0)
            IRDataType.LONG -> memory.setSL(address, 0)
            IRDataType.FLOAT -> memory.setFloat(address, 0.0)
        }
        nextPc()
    }

    private fun InsSTOREIM(i: IRInstruction) {
        val addr = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val imm = i.requireImmediate()
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(addr, (i.requireImmediateInt() and 0xff).toUByte())
            IRDataType.WORD -> memory.setUW(addr, (i.requireImmediateInt() and 0xffff).toUShort())
            IRDataType.POINTER -> memory.setSL(addr, i.requireImmediateInt())
            IRDataType.LONG -> memory.setSL(addr, i.requireImmediateInt())
            IRDataType.FLOAT -> memory.setFloat(addr, (imm as ImmediateOperand.FloatValue).value)
        }
        nextPc()
    }

    private fun InsSTOREZI(i: IRInstruction) {
        val mem = i.requireMemory() as MemoryReference.Indirect
        val offset = mem.displacement
        require(offset in 0..65535)
        val baseAddr = mem.pointer.readPointerValue()
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(baseAddr + offset.toUInt(), 0u)
            IRDataType.WORD -> memory.setUW(baseAddr + offset.toUInt(), 0u)
            IRDataType.POINTER -> memory.setUL(baseAddr + offset.toUInt(), 0u)
            IRDataType.LONG -> memory.setSL(baseAddr + offset.toUInt(), 0)
            IRDataType.FLOAT -> memory.setFloat(baseAddr + offset.toUInt(), 0.0)
        }
        nextPc()
    }

    private fun InsSTOREZX(i: IRInstruction) {
        val mem = i.requireMemory() as MemoryReference.Indexed
        val index = mem.index.readIndexValue()
        val scale = mem.scale.toUInt()
        val base = mem.resolvedBaseAddress()
        val address = base + index * scale
        when (i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, 0u)
            IRDataType.WORD -> memory.setUW(address, 0u)
            IRDataType.POINTER -> memory.setUL(address, 0u)
            IRDataType.LONG -> memory.setSL(address, 0)
            IRDataType.FLOAT -> memory.setFloat(address, 0.0)
        }
        nextPc()
    }

    private fun InsJUMP(i: IRInstruction) {
        branchTo(i)
    }

    private fun InsJUMPI(i: IRInstruction) {
        val pointer = (i.requireTarget() as CodeReference.Indirect).pointer
        val artificialAddress: UInt = registers.getUL(pointer.registerNumber)
        if(!artificialLabelAddresses.contains(artificialAddress))
            throw IllegalArgumentException("vm program can't jump to system memory address (${i.opcode} ${artificialAddress.toHex()})")
        pcChunk = artificialLabelAddresses.getValue(artificialAddress)
        pcIndex = 0
    }

    private class SyscallParamValue(var dt: IRDataType?, var value: Comparable<*>?)
    private val syscallParams = Array(100) { SyscallParamValue(null, null) }

    private fun InsCALL(i: IRInstruction) {
        val site = i.requireCallSite()
        site.arguments.forEach { arg ->
            val location = arg.location as? CallLocation.ParameterMemory
                ?: throw IllegalArgumentException("argument variable should have been given its memory address as well")
            val address = requireNotNull(location.address) { "argument variable should have been given its memory address as well" }
            when(arg.source.type) {
                IRDataType.BYTE -> memory.setUB(address.value, registers.getUB(arg.source.registerNumber))
                IRDataType.WORD -> memory.setUW(address.value, registers.getUW(arg.source.registerNumber))
                IRDataType.POINTER -> memory.setSL(address.value, registers.getSL(arg.source.registerNumber))
                IRDataType.LONG -> memory.setSL(address.value, registers.getSL(arg.source.registerNumber))
                IRDataType.FLOAT -> memory.setFloat(address.value, registers.getFloat(arg.source.registerNumber))
            }
        }
        // store the call site and jump
        callStack.add(CallSiteContext(pcChunk, pcIndex+1, site))
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
            val returns = context.callSite.results
            when (i.type!!) {
                IRDataType.BYTE -> {
                    if(returns.isNotEmpty()) {
                        val value = i.requireImmediateInt().toUByte()
                        registers.setUB(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty()) {
                        val value = i.requireImmediateInt().toUShort()
                        registers.setUW(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.POINTER -> {
                    if(returns.isNotEmpty()) {
                        val value = i.requireImmediateInt()
                        registers.setSL(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.LONG -> {
                    if(returns.isNotEmpty()) {
                        val value = i.requireImmediateInt()
                        registers.setSL(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.FLOAT -> {
                    if(returns.isNotEmpty())
                        registers.setFloat(returns.single().destination!!.registerNumber, i.requireImmediateFloat())
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
            val returns = context.callSite.results
            val src = i.requireSrcA().registerNumber
            when (i.type!!) {
                IRDataType.BYTE -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getUB(src)
                        registers.setUB(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.WORD -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getUW(src)
                        registers.setUW(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.POINTER -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getSL(src)
                        registers.setSL(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.LONG -> {
                    if(returns.isNotEmpty()) {
                        val value = registers.getSL(src)
                        registers.setSL(returns.single().destination!!.registerNumber, value)
                    } else {
                        val callInstr = context.returnChunk.instructions[context.returnIndex-1]
                        if(callInstr.opcode!=Opcode.CALL)
                            throw IllegalArgumentException("missing return value reg")
                    }
                }
                IRDataType.FLOAT -> {
                    if(returns.isNotEmpty())
                        registers.setFloat(returns.single().destination!!.registerNumber, registers.getFloat(src))
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
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (registers.getUB(dest)+1u).toUByte()
                registers.setUB(dest, value)
            }
            IRDataType.WORD -> {
                val value = (registers.getUW(dest)+1u).toUShort()
                registers.setUW(dest, value)
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest)+1
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest)+1
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> registers.setFloat(dest, registers.getFloat(dest)+1f)
        }
        nextPc()
    }

    private fun InsINCM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (memory.getUB(address)+1u).toUByte()
                memory.setUB(address, value)
            }
            IRDataType.WORD -> {
                val value = (memory.getUW(address)+1u).toUShort()
                memory.setUW(address, value)
            }
            IRDataType.POINTER -> {
                val value = memory.getSL(address)+1
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(address)+1
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)+1f)
        }
        nextPc()
    }

    private fun InsDEC(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (registers.getUB(dest)-1u).toUByte()
                registers.setUB(dest, value)
            }
            IRDataType.WORD -> {
                val value = (registers.getUW(dest)-1u).toUShort()
                registers.setUW(dest, value)
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest)-1
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest)-1
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> registers.setFloat(dest, registers.getFloat(dest)-1f)
        }
        nextPc()
    }

    private fun InsDECM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = (memory.getUB(address)-1u).toUByte()
                memory.setUB(address, value)
            }
            IRDataType.WORD -> {
                val value = (memory.getUW(address)-1u).toUShort()
                memory.setUW(address, value)
            }
            IRDataType.POINTER -> {
                val value = memory.getSL(address)-1
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val value = memory.getSL(address)-1
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> memory.setFloat(address, memory.getFloat(address)-1f)
        }
        nextPc()
    }

    private fun InsNEG(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = -registers.getUB(dest).toInt()
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = -registers.getUW(dest).toInt()
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                val value = -registers.getSL(dest)
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = -registers.getSL(dest)
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> registers.setFloat(dest, -registers.getFloat(dest))
        }
        nextPc()
    }

    private fun InsNEGM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = -memory.getUB(address).toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = -memory.getUW(address).toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.POINTER -> {
                val value = -memory.getSL(address)
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val value = -memory.getSL(address)
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> memory.setFloat(address, -memory.getFloat(address))
        }
        nextPc()
    }

    private fun InsADDR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("+", dest, src)
            IRDataType.WORD -> plusMinusMultAnyWord("+", dest, src)
            IRDataType.POINTER -> plusMinusMultAnyLong("+", dest, src)
            IRDataType.LONG -> plusMinusMultAnyLong("+", dest, src)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "+", right)
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsADD(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("+", dest, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("+", dest, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> plusMinusMultConstLong("+", dest, i.requireImmediateInt())
            IRDataType.LONG -> plusMinusMultConstLong("+", dest, i.requireImmediateInt())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val result = arithFloat(left, "+", i.requireImmediateFloat())
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsADDM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("+", src, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("+", src, address)
            IRDataType.POINTER -> plusMinusMultAnyLongInplace("+", src, address)
            IRDataType.LONG -> plusMinusMultAnyLongInplace("+", src, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "+", right)
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsADDIM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByteInplace("+", i.requireImmediateInt().toUByte(), address)
            IRDataType.WORD -> plusMinusMultConstWordInplace("+", i.requireImmediateInt().toUShort(), address)
            IRDataType.POINTER -> plusMinusMultConstLongInplace("+", i.requireImmediateInt(), address)
            IRDataType.LONG -> plusMinusMultConstLongInplace("+", i.requireImmediateInt(), address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val result = arithFloat(left, "+", i.requireImmediateFloat())
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsSUBR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("-", dest, src)
            IRDataType.WORD -> plusMinusMultAnyWord("-", dest, src)
            IRDataType.POINTER -> plusMinusMultAnyLong("-", dest, src)
            IRDataType.LONG -> plusMinusMultAnyLong("-", dest, src)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "-", right)
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsSUB(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("-", dest, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("-", dest, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> plusMinusMultConstLong("-", dest, i.requireImmediateInt())
            IRDataType.LONG -> plusMinusMultConstLong("-", dest, i.requireImmediateInt())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val result = arithFloat(left, "-", i.requireImmediateFloat())
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsSUBM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("-", src, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("-", src, address)
            IRDataType.POINTER -> plusMinusMultAnyLongInplace("-", src, address)
            IRDataType.LONG -> plusMinusMultAnyLongInplace("-", src, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "-", right)
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsSUBIM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByteInplace("-", i.requireImmediateInt().toUByte(), address)
            IRDataType.WORD -> plusMinusMultConstWordInplace("-", i.requireImmediateInt().toUShort(), address)
            IRDataType.POINTER -> plusMinusMultConstLongInplace("-", i.requireImmediateInt(), address)
            IRDataType.LONG -> plusMinusMultConstLongInplace("-", i.requireImmediateInt(), address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val result = arithFloat(left, "-", i.requireImmediateFloat())
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }    

    private fun InsMULR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByte("*", dest, src)
            IRDataType.WORD -> plusMinusMultAnyWord("*", dest, src)
            IRDataType.POINTER -> plusMinusMultAnyLong("*", dest, src)
            IRDataType.LONG -> throw IllegalArgumentException("mulr unsigned long not supported")
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "*", right)
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsMUL(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultConstByte("*", dest, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> plusMinusMultConstWord("*", dest, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> plusMinusMultConstLong("*", dest, i.requireImmediateInt())
            IRDataType.LONG -> throw IllegalArgumentException("mul unsigned long not supported")
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val result = arithFloat(left, "*", i.requireImmediateFloat())
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsMULM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> plusMinusMultAnyByteInplace("*", src, address)
            IRDataType.WORD -> plusMinusMultAnyWordInplace("*", src, address)
            IRDataType.POINTER -> plusMinusMultAnyLongInplace("*", src, address)
            IRDataType.LONG -> throw IllegalArgumentException("mulm unsigned long not supported")
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "*", right)
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsMULSR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> multiplyAnyByteSigned(dest, src)
            IRDataType.WORD -> multiplyAnyWordSigned(dest, src)
            IRDataType.POINTER -> multiplyAnyLongSigned(dest, src)
            IRDataType.LONG -> multiplyAnyLongSigned(dest, src)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "*", right)
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsMULS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> multiplyConstByteSigned(dest, i.requireImmediateInt().toByte())
            IRDataType.WORD -> multiplyConstWordSigned(dest, i.requireImmediateInt().toShort())
            IRDataType.POINTER -> multiplyConstLongSigned(dest, i.requireImmediateInt())
            IRDataType.LONG -> multiplyConstLongSigned(dest, i.requireImmediateInt())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val result = arithFloat(left, "*", i.requireImmediateFloat())
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsMULSM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> multiplyAnyByteSignedInplace(src, address)
            IRDataType.WORD -> multiplyAnyWordSignedInplace(src, address)
            IRDataType.POINTER -> multiplyAnyLongSignedInplace(src, address)
            IRDataType.LONG -> multiplyAnyLongSignedInplace(src, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "*", right)
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsDIVR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divOrModByteUnsigned("/", dest, src)
            IRDataType.WORD -> divOrModWordUnsigned("/", dest, src)
            IRDataType.POINTER -> throw IllegalArgumentException("divr unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIV(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("/", dest, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("/", dest, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> throw IllegalArgumentException("div unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("div unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> divModByteUnsignedInplace("/", src, address)
            IRDataType.WORD -> divModWordUnsignedInplace("/", src, address)
            IRDataType.POINTER -> throw IllegalArgumentException("divm unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divm unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVSR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSigned("/", dest, src)
            IRDataType.WORD -> divModWordSigned("/", dest, src)
            IRDataType.POINTER -> divModLongSigned("/", dest, src)
            IRDataType.LONG -> divModLongSigned("/", dest, src)
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "/", right)
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsDIVS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteSigned("/", dest, i.requireImmediateInt().toByte())
            IRDataType.WORD -> divModConstWordSigned("/", dest, i.requireImmediateInt().toShort())
            IRDataType.POINTER -> divModConstLongSigned("/", dest, i.requireImmediateInt())
            IRDataType.LONG -> divModConstLongSigned("/", dest, i.requireImmediateInt())
            IRDataType.FLOAT -> {
                val left = registers.getFloat(dest)
                val result = arithFloat(left, "/", i.requireImmediateFloat())
                registers.setFloat(dest, result)
            }
        }
        nextPc()
    }

    private fun InsDIVSM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSignedInplace("/", src, address)
            IRDataType.WORD -> divModWordSignedInplace("/", src, address)
            IRDataType.POINTER -> divModLongSignedInplace("/", src, address)
            IRDataType.LONG -> divModLongSignedInplace("/", src, address)
            IRDataType.FLOAT -> {
                val left = memory.getFloat(address)
                val right = registers.getFloat(src)
                val result = arithFloat(left, "/", right)
                memory.setFloat(address, result)
            }
        }
        nextPc()
    }

    private fun InsMODR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divOrModByteUnsigned("%", dest, src)
            IRDataType.WORD -> divOrModWordUnsigned("%", dest, src)
            IRDataType.POINTER -> throw IllegalArgumentException("modr unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("modr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMOD(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divOrModConstByteUnsigned("%", dest, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> divOrModConstWordUnsigned("%", dest, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> throw IllegalArgumentException("mod unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("mod unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMODSR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divModByteSigned("%", dest, src)
            IRDataType.WORD -> divModWordSigned("%", dest, src)
            IRDataType.POINTER -> divModLongSigned("%", dest, src)
            IRDataType.LONG -> divModLongSigned("%", dest, src)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsMODS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divModConstByteSigned("%", dest, i.requireImmediateInt().toByte())
            IRDataType.WORD -> divModConstWordSigned("%", dest, i.requireImmediateInt().toShort())
            IRDataType.POINTER -> divModConstLongSigned("%", dest, i.requireImmediateInt())
            IRDataType.LONG -> divModConstLongSigned("%", dest, i.requireImmediateInt())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMODR(i: IRInstruction) {
        val quotient = i.requireDest().registerNumber
        val remainder = i.requireDestB().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divAndModUByte(quotient, remainder)
            IRDataType.WORD -> divAndModUWord(quotient, remainder)
            IRDataType.POINTER -> throw IllegalArgumentException("divmodr unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divmodr unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsDIVMOD(i: IRInstruction) {
        val quotient = i.requireDest().registerNumber
        val remainder = i.requireDestB().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstUByte(quotient, remainder, i.requireImmediateInt().toUByte())
            IRDataType.WORD -> divAndModConstUWord(quotient, remainder, i.requireImmediateInt().toUShort())
            IRDataType.POINTER -> throw IllegalArgumentException("divmod unsigned pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divmod unsigned long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSDIVMODR(i: IRInstruction) {
        val quotient = i.requireDest().registerNumber
        val remainder = i.requireDestB().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divAndModSByte(quotient, remainder)
            IRDataType.WORD -> divAndModSWord(quotient, remainder)
            IRDataType.POINTER -> throw IllegalArgumentException("divmodr signed pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divmodr signed long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSDIVMOD(i: IRInstruction) {
        val quotient = i.requireDest().registerNumber
        val remainder = i.requireDestB().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> divAndModConstSByte(quotient, remainder, i.requireImmediateInt().toByte())
            IRDataType.WORD -> divAndModConstSWord(quotient, remainder, i.requireImmediateInt().toShort())
            IRDataType.POINTER -> throw IllegalArgumentException("divmod signed pointer not supported")
            IRDataType.LONG -> throw IllegalArgumentException("divmod signed long not supported")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsSGN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val sign: Int = when (i.type!!) {
            IRDataType.BYTE -> registers.getSB(src).toInt().sign
            IRDataType.WORD -> registers.getSW(src).toInt().sign
            IRDataType.POINTER -> registers.getSL(src).sign
            IRDataType.LONG -> registers.getSL(src).sign
            IRDataType.FLOAT -> registers.getFloat(src).sign.toInt()
        }
        registers.setSB(dest, sign.toByte())
        statusbitsComparisonWithOverflow(sign, 0, IRDataType.BYTE)
        nextPc()
    }

    private fun InsSQRT(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, sqrt(registers.getUB(src).toDouble()).toInt().toUByte())
            IRDataType.WORD -> registers.setUB(dest, sqrt(registers.getUW(src).toDouble()).toInt().toUByte())
            IRDataType.POINTER -> {
                val value = registers.getUL(src)
                registers.setUL(dest, sqrt(value.toDouble()).toUInt())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                if(value<0)
                    throw IllegalArgumentException("sqrt of negative long $value reg=$src")
                registers.setSL(dest, sqrt(value.toDouble()).toInt())
            }
            IRDataType.FLOAT -> registers.setFloat(dest, sqrt(registers.getFloat(src)))
        }
        nextPc()
    }

    private fun InsSQUARE(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(src).toInt()
                registers.setUB(dest, (value*value).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(src).toInt()
                registers.setUW(dest, (value*value).toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(src)
                registers.setSL(dest, value*value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                registers.setSL(dest, value*value)
            }
            IRDataType.FLOAT -> {
                val value = registers.getFloat(src)
                registers.setFloat(dest, value*value)
            }
        }
        nextPc()
    }

    private fun InsCMP(i: IRInstruction) {
        val type = i.type!!
        val leftReg = i.requireSrcA().registerNumber
        val rightReg = i.requireSrcB().registerNumber
        val left = when(type) {
            IRDataType.BYTE -> registers.getUB(leftReg).toInt()
            IRDataType.WORD -> registers.getUW(leftReg).toInt()
            IRDataType.POINTER -> registers.getSL(leftReg)
            IRDataType.LONG -> registers.getSL(leftReg)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        val right = when(type) {
            IRDataType.BYTE -> registers.getUB(rightReg).toInt()
            IRDataType.WORD -> registers.getUW(rightReg).toInt()
            IRDataType.POINTER -> registers.getSL(rightReg)
            IRDataType.LONG -> registers.getSL(rightReg)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparisonWithOverflow(left, right, type)
        nextPc()
    }

    private fun InsCMPI(i: IRInstruction) {
        val type = i.type!!
        val leftReg = i.requireSrcA().registerNumber
        val left = when(type) {
            IRDataType.BYTE -> registers.getUB(leftReg).toInt()
            IRDataType.WORD -> registers.getUW(leftReg).toInt()
            IRDataType.POINTER -> registers.getSL(leftReg)
            IRDataType.LONG -> registers.getSL(leftReg)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        val right = when(type) {
            IRDataType.BYTE -> i.requireImmediateInt() and 0xff
            IRDataType.WORD -> i.requireImmediateInt() and 0xffff
            IRDataType.POINTER -> i.requireImmediateInt()
            IRDataType.LONG -> i.requireImmediateInt()
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusbitsComparisonWithOverflow(left, right, type)
        nextPc()
    }

    private fun InsLOADHFACZERO(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, hardwareRegisterFAC0)
        nextPc()
    }

    private fun InsLOADHFACONE(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, hardwareRegisterFAC1)
        nextPc()
    }

    private fun InsSTOREHFACZERO(i: IRInstruction) {
        hardwareRegisterFAC0 = registers.getFloat(i.requireFloatSourceA().registerNumber)
        nextPc()
    }

    private fun InsSTOREHFACONE(i: IRInstruction) {
        hardwareRegisterFAC1 = registers.getFloat(i.requireFloatSourceA().registerNumber)
        nextPc()
    }


    private fun InsBITTST(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val mask = 1 shl i.requireImmediateInt()
        val value: Int = when(i.type!!) {
            IRDataType.BYTE -> registers.getUB(src).toInt()
            IRDataType.WORD -> registers.getUW(src).toInt()
            IRDataType.POINTER -> registers.getSL(src)
            IRDataType.LONG -> registers.getSL(src)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        statusZero = value and mask == 0
        nextPc()
    }

    private fun InsBITSET(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val mask = 1 shl i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(dest).toInt() or mask
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(dest).toInt() or mask
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest) or mask
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest) or mask
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsBITCLR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val mask = 1 shl i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(dest).toInt() and mask.inv()
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(dest).toInt() and mask.inv()
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest) and mask.inv()
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest) and mask.inv()
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsBITTOG(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val mask = 1 shl i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(dest).toInt() xor mask
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(dest).toInt() xor mask
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest) xor mask
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest) xor mask
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsEXT(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!){
            IRDataType.BYTE -> registers.setUW(dest, registers.getUB(src).toUShort())
            IRDataType.WORD -> registers.setSL(dest, registers.getUW(src).toInt())
            IRDataType.POINTER -> throw IllegalArgumentException("ext.p makes no sense, 32 bits is already the widest you can get")
            IRDataType.LONG -> throw IllegalArgumentException("ext.l makes no sense, 32 bits is already the widest you can get")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsEXTS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!){
            IRDataType.BYTE -> registers.setSW(dest, registers.getSB(src).toShort())
            IRDataType.WORD -> registers.setSL(dest, registers.getSW(src).toInt())
            IRDataType.POINTER -> throw IllegalArgumentException("exts.p makes no sense, 32 bits is already the widest you can get")
            IRDataType.LONG -> throw IllegalArgumentException("exts.l makes no sense, 32 bits is already the widest you can get")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsEXTL(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        registers.setSL(dest, registers.getUB(src).toInt())
        nextPc()
    }

    private fun InsEXTLS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        registers.setSL(dest, registers.getSB(src).toInt())
        nextPc()
    }

    private fun InsANDR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left and right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, value.toUByte())
            IRDataType.WORD -> registers.setUW(dest, value.toUShort())
            IRDataType.POINTER -> registers.setSL(dest, value)
            IRDataType.LONG -> registers.setSL(dest, value)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsAND(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(dest).toInt() and i.requireImmediateInt()
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(dest).toInt() and i.requireImmediateInt()
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                value = registers.getSL(dest) and i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                value = registers.getSL(dest) and i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsANDM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(src)
                value = left.toInt() and right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(src)
                value = left.toInt() and right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.POINTER -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left and right
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left and right
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsORR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left or right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, value.toUByte())
            IRDataType.WORD -> registers.setUW(dest, value.toUShort())
            IRDataType.POINTER -> registers.setSL(dest, value)
            IRDataType.LONG -> registers.setSL(dest, value)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsOR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(dest).toInt() or i.requireImmediateInt()
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(dest).toInt() or i.requireImmediateInt()
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                value = registers.getSL(dest) or i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                value = registers.getSL(dest) or i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsORM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(src)
                value = left.toInt() or right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(src)
                value = left.toInt() or right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.POINTER -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left or right
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left or right
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsXORR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        val value = (left xor right).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, value.toUByte())
            IRDataType.WORD -> registers.setUW(dest, value.toUShort())
            IRDataType.POINTER -> registers.setSL(dest, value)
            IRDataType.LONG -> registers.setSL(dest, value)
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsXOR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                value = registers.getUB(dest).toInt() xor i.requireImmediateInt()
                registers.setUB(dest, value.toUByte())
            }
            IRDataType.WORD -> {
                value = registers.getUW(dest).toInt() xor i.requireImmediateInt()
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.POINTER -> {
                value = registers.getSL(dest) xor i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.LONG -> {
                value = registers.getSL(dest) xor i.requireImmediateInt()
                registers.setSL(dest, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsXORM(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val value: Int
        when(i.type!!) {
            IRDataType.BYTE -> {
                val left = memory.getUB(address)
                val right = registers.getUB(src)
                value = left.toInt() xor right.toInt()
                memory.setUB(address, value.toUByte())
            }
            IRDataType.WORD -> {
                val left = memory.getUW(address)
                val right = registers.getUW(src)
                value = left.toInt() xor right.toInt()
                memory.setUW(address, value.toUShort())
            }
            IRDataType.POINTER -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left xor right
                memory.setSL(address, value)
            }
            IRDataType.LONG -> {
                val left = memory.getSL(address)
                val right = registers.getSL(src)
                value = left xor right
                memory.setSL(address, value)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsINV(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, registers.getUB(dest).inv())
            IRDataType.WORD -> registers.setUW(dest, registers.getUW(dest).inv())
            IRDataType.POINTER -> registers.setSL(dest, registers.getSL(dest).inv())
            IRDataType.LONG -> registers.setSL(dest, registers.getSL(dest).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsINVM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        when(i.type!!) {
            IRDataType.BYTE -> memory.setUB(address, memory.getUB(address).inv())
            IRDataType.WORD -> memory.setUW(address, memory.getUW(address).inv())
            IRDataType.POINTER -> memory.setSL(address, memory.getSL(address).inv())
            IRDataType.LONG -> memory.setSL(address, memory.getSL(address).inv())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    // multi-bit shift helpers: the JVM shift operators mask the shift count by 31,
    // which gives wrong results for counts >= the value's bit size
    private fun shiftRightArithmetic(value: Int, count: Int, bits: Int): Int {
        if(count >= bits)
            return if(value < 0) -1 else 0
        return value shr count
    }

    private fun shiftRightLogical(value: UInt, count: Int, bits: Int): UInt {
        if(count >= bits)
            return 0u
        return value shr count
    }

    private fun shiftLeft(value: UInt, count: Int, bits: Int): UInt {
        if(count >= bits)
            return 0u
        return value shl count
    }

    private fun InsASRN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val (left: Int, right: Int) = getLogicalOperandsS(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(dest, shiftRightArithmetic(left, right, 8).toByte())
            IRDataType.WORD -> registers.setSW(dest, shiftRightArithmetic(left, right, 16).toShort())
            IRDataType.POINTER -> registers.setSL(dest, shiftRightArithmetic(left, right, 32))
            IRDataType.LONG -> registers.setSL(dest, shiftRightArithmetic(left, right, 32))
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRI(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val left = getLogicalOperandS(i)
        val right = i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setSB(dest, shiftRightArithmetic(left, right, 8).toByte())
            IRDataType.WORD -> registers.setSW(dest, shiftRightArithmetic(left, right, 16).toShort())
            IRDataType.POINTER -> registers.setSL(dest, shiftRightArithmetic(left, right, 32))
            IRDataType.LONG -> registers.setSL(dest, shiftRightArithmetic(left, right, 32))
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRNM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val operand = registers.getUB(i.requireSrcA().registerNumber).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getSB(address).toInt()
                memory.setSB(address, shiftRightArithmetic(memvalue, operand, 8).toByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getSW(address).toInt()
                memory.setSW(address, shiftRightArithmetic(memvalue, operand, 16).toShort())
            }
            IRDataType.POINTER -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftRightArithmetic(memvalue, operand, 32))
            }
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftRightArithmetic(memvalue, operand, 32))
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getSB(dest).toInt()
                statusCarry = (value and 1)!=0
                registers.setSB(dest, (value shr 1).toByte())
            }
            IRDataType.WORD -> {
                val value = registers.getSW(dest).toInt()
                statusCarry = (value and 1)!=0
                registers.setSW(dest, (value shr 1).toShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest)
                statusCarry = (value and 1)!=0
                registers.setSL(dest, value shr 1)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest)
                statusCarry = (value and 1)!=0
                registers.setSL(dest, value shr 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsASRM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
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
            IRDataType.POINTER -> {
                val value = memory.getSL(address)
                statusCarry = (value and 1)!=0
                memory.setSL(address, value shr 1)
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
        val dest = i.requireDest().registerNumber
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, shiftRightLogical(left, right.toInt(), 8).toUByte())
            IRDataType.WORD -> registers.setUW(dest, shiftRightLogical(left, right.toInt(), 16).toUShort())
            IRDataType.POINTER -> registers.setSL(dest, shiftRightLogical(left, right.toInt(), 32).toInt())
            IRDataType.LONG -> registers.setSL(dest, shiftRightLogical(left, right.toInt(), 32).toInt())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRI(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val left = getLogicalOperandU(i)
        val right = i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, shiftRightLogical(left, right, 8).toUByte())
            IRDataType.WORD -> registers.setUW(dest, shiftRightLogical(left, right, 16).toUShort())
            IRDataType.POINTER -> registers.setSL(dest, shiftRightLogical(left, right, 32).toInt())
            IRDataType.LONG -> registers.setSL(dest, shiftRightLogical(left, right, 32).toInt())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRNM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val operand = registers.getUB(i.requireSrcA().registerNumber).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                memory.setUB(address, shiftRightLogical(memvalue.toUInt(), operand, 8).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                memory.setUW(address, shiftRightLogical(memvalue.toUInt(), operand, 16).toUShort())
            }
            IRDataType.POINTER -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftRightLogical(memvalue.toUInt(), operand, 32).toInt())
            }
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftRightLogical(memvalue.toUInt(), operand, 32).toInt())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(dest).toInt()
                statusCarry = (value and 1)!=0
                registers.setUB(dest, (value shr 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(dest).toInt()
                statusCarry = (value and 1)!=0
                registers.setUW(dest, (value shr 1).toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest)
                statusCarry = (value and 1)!=0
                registers.setSL(dest, value ushr 1)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest)
                statusCarry = (value and 1)!=0
                registers.setSL(dest, value ushr 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSRM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
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
            IRDataType.POINTER -> {
                val value = memory.getSL(address)
                statusCarry = (value and 1)!=0
                memory.setSL(address, value ushr 1)
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
        val dest = i.requireDest().registerNumber
        val (left: UInt, right: UInt) = getLogicalOperandsU(i)
        when(i.type!!) {
            IRDataType.BYTE -> {
                registers.setUB(dest, shiftLeft(left, right.toInt(), 8).toUByte())
            }
            IRDataType.WORD -> {
                registers.setUW(dest, shiftLeft(left, right.toInt(), 16).toUShort())
            }
            IRDataType.POINTER -> {
                registers.setSL(dest, shiftLeft(left, right.toInt(), 32).toInt())
            }
            IRDataType.LONG -> {
                registers.setSL(dest, shiftLeft(left, right.toInt(), 32).toInt())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSLI(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val left = getLogicalOperandU(i)
        val right = i.requireImmediateInt()
        when(i.type!!) {
            IRDataType.BYTE -> registers.setUB(dest, shiftLeft(left, right, 8).toUByte())
            IRDataType.WORD -> registers.setUW(dest, shiftLeft(left, right, 16).toUShort())
            IRDataType.POINTER -> registers.setSL(dest, shiftLeft(left, right, 32).toInt())
            IRDataType.LONG -> registers.setSL(dest, shiftLeft(left, right, 32).toInt())
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSLNM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val operand = registers.getUB(i.requireSrcA().registerNumber).toInt()
        when(i.type!!) {
            IRDataType.BYTE -> {
                val memvalue = memory.getUB(address).toInt()
                memory.setUB(address, shiftLeft(memvalue.toUInt(), operand, 8).toUByte())
            }
            IRDataType.WORD -> {
                val memvalue = memory.getUW(address).toInt()
                memory.setUW(address, shiftLeft(memvalue.toUInt(), operand, 16).toUShort())
            }
            IRDataType.POINTER -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftLeft(memvalue.toUInt(), operand, 32).toInt())
            }
            IRDataType.LONG -> {
                val memvalue = memory.getSL(address)
                memory.setSL(address, shiftLeft(memvalue.toUInt(), operand, 32).toInt())
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSL(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val value = registers.getUB(dest).toInt()
                statusCarry = (value and 0x80)!=0
                registers.setUB(dest, (value shl 1).toUByte())
            }
            IRDataType.WORD -> {
                val value = registers.getUW(dest).toInt()
                statusCarry = (value and 0x8000)!=0
                registers.setUW(dest, (value shl 1).toUShort())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(dest)
                statusCarry = value<0
                registers.setSL(dest, value shl 1)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(dest)
                statusCarry = value<0
                registers.setSL(dest, value shl 1)
            }
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSLM(i: IRInstruction) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
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
            IRDataType.POINTER -> {
                val value = memory.getSL(address)
                statusCarry = value<0
                memory.setSL(address, value shl 1)
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
        val dest = i.requireDest().registerNumber
        val newStatusCarry: Boolean
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = registers.getUB(dest)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 0x80u else 0x00u
                    (orig.toUInt().rotateRight(1) or carry).toUByte()
                } else
                    orig.rotateRight(1)
                registers.setUB(dest, rotated)
            }
            IRDataType.WORD -> {
                val orig = registers.getUW(dest)
                newStatusCarry = (orig.toInt() and 1) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 0x8000u else 0x0000u
                    (orig.toUInt().rotateRight(1) or carry).toUShort()
                } else
                    orig.rotateRight(1)
                registers.setUW(dest, rotated)
            }
            IRDataType.POINTER -> {
                val orig = registers.getUL(dest)
                newStatusCarry = (orig and 1u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 0x80000000u else 0u
                    (orig.rotateRight(1) or carry)
                } else
                    orig.rotateRight(1)
                registers.setUL(dest, rotated)
            }
            IRDataType.LONG -> {
                val orig = registers.getSL(dest).toUInt()
                newStatusCarry = (orig and 1u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 0x80000000u else 0u
                    (orig.rotateRight(1) or carry)
                } else
                    orig.rotateRight(1)
                registers.setSL(dest, rotated.toInt())
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROR a float")
            }
        }
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsRORM(i: IRInstruction, useCarry: Boolean) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
        val newStatusCarry: Boolean
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
            IRDataType.POINTER -> {
                val orig = memory.getUL(address)
                newStatusCarry = (orig and 1u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 0x80000000u else 0u
                    (orig.rotateRight(1) or carry)
                } else
                    orig.rotateRight(1)
                memory.setUL(address, rotated)
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
        val dest = i.requireDest().registerNumber
        val newStatusCarry: Boolean
        when (i.type!!) {
            IRDataType.BYTE -> {
                val orig = registers.getUB(dest)
                newStatusCarry = (orig.toInt() and 0x80) != 0
                val rotated: UByte = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUByte()
                } else
                    orig.rotateLeft(1)
                registers.setUB(dest, rotated)
            }
            IRDataType.WORD -> {
                val orig = registers.getUW(dest)
                newStatusCarry = (orig.toInt() and 0x8000) != 0
                val rotated: UShort = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.toUInt().rotateLeft(1) or carry).toUShort()
                } else
                    orig.rotateLeft(1)
                registers.setUW(dest, rotated)
            }
            IRDataType.POINTER -> {
                val orig = registers.getUL(dest)
                newStatusCarry = (orig and 0x80000000u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.rotateLeft(1) or carry)
                } else
                    orig.rotateLeft(1)
                registers.setUL(dest, rotated)
            }
            IRDataType.LONG -> {
                val orig = registers.getSL(dest).toUInt()
                newStatusCarry = (orig and 0x80000000u) != 0u
                val rotated: UInt = (if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.rotateLeft(1) or carry)
                } else
                    orig.rotateLeft(1))
                registers.setSL(dest, rotated.toInt())
            }
            IRDataType.FLOAT -> {
                throw IllegalArgumentException("can't ROL a float")
            }
        }
        nextPc()
        statusCarry = newStatusCarry
    }

    private fun InsROLM(i: IRInstruction, useCarry: Boolean) {
        val address = (i.requireMemory() as MemoryReference.Direct).resolvedAddress()
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
            IRDataType.POINTER -> {
                val orig = memory.getUL(address)
                newStatusCarry = (orig and 0x80000000u) != 0u
                val rotated: UInt = if (useCarry) {
                    val carry = if (statusCarry) 1u else 0u
                    (orig.rotateLeft(1) or carry)
                } else
                    orig.rotateLeft(1)
                memory.setUL(address, rotated)
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
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.WORD -> {
                val value = registers.getUW(src)
                val byte = value.toUByte()
                registers.setUB(dest, byte)
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(src)
                val byte = value.toUByte()
                registers.setUB(dest, byte)
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                val byte = value.toUByte()
                registers.setUB(dest, byte)
            }
            else -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsLSIGW(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        if (i.type!! == IRDataType.LONG || i.type!! == IRDataType.POINTER) {
            val value = registers.getSL(src)
            val word = value.toUShort()
            registers.setUW(dest, word)
        }
        else throw IllegalArgumentException("invalid float type for this instruction $i")
        nextPc()
    }

    private fun InsMSIGB(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        when(i.type!!) {
            IRDataType.WORD -> {
                val value = registers.getUW(src)
                val newValue = value.toInt() ushr 8
                registers.setUB(dest, newValue.toUByte())
            }
            IRDataType.POINTER -> {
                val value = registers.getSL(src)
                val newValue = value ushr 24
                registers.setUB(dest, newValue.toUByte())
            }
            IRDataType.LONG -> {
                val value = registers.getSL(src)
                val newValue = value ushr 24
                registers.setUB(dest, newValue.toUByte())
            }
            else -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsBSIGB(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getSL(src)
        val newValue = value ushr 16 and 255
        registers.setUB(dest, newValue.toUByte())
        nextPc()
    }

    private fun InsMIDB(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getSL(src)
        val newValue = value ushr 8 and 255
        registers.setUB(dest, newValue.toUByte())
        nextPc()
    }

    private fun InsMSIGW(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        if (i.type!! == IRDataType.LONG || i.type!! == IRDataType.POINTER) {
            val value = registers.getSL(src)
            val newValue = value ushr 16
            registers.setUW(dest, newValue.toUShort())
        }
        else throw IllegalArgumentException("invalid float type for this instruction $i")
        nextPc()
    }

    private fun InsCONCAT(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val msbReg = i.requireSrcA().registerNumber
        val lsbReg = i.requireSrcB().registerNumber
        when(i.type!!) {
            IRDataType.BYTE -> {
                val msb = registers.getUB(msbReg)
                val lsb = registers.getUB(lsbReg)
                val value = ((msb.toInt() shl 8) or lsb.toInt())
                registers.setUW(dest, value.toUShort())
            }
            IRDataType.WORD -> {
                val msw = registers.getUW(msbReg)
                val lsw = registers.getUW(lsbReg)
                registers.setSL(dest, ((msw.toInt() shl 16) or lsw.toInt()))
            }
            IRDataType.POINTER -> throw IllegalArgumentException("concat.p makes no sense, 32 bits is already the widest")
            IRDataType.LONG -> throw IllegalArgumentException("concat.l makes no sense, 32 bits is already the widest")
            IRDataType.FLOAT -> throw IllegalArgumentException("invalid float type for this instruction $i")
        }
        nextPc()
    }

    private fun InsFFROMUB(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, registers.getUB(i.requireIntSourceA().registerNumber).toDouble())
        nextPc()
    }

    private fun InsFFROMSB(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, registers.getSB(i.requireIntSourceA().registerNumber).toDouble())
        nextPc()
    }

    private fun InsFFROMUW(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, registers.getUW(i.requireIntSourceA().registerNumber).toDouble())
        nextPc()
    }

    private fun InsFFROMSW(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, registers.getSW(i.requireIntSourceA().registerNumber).toDouble())
        nextPc()
    }

    private fun InsFFROMSL(i: IRInstruction) {
        registers.setFloat(i.requireFloatDest().registerNumber, registers.getSL(i.requireIntSourceA().registerNumber).toDouble())
        nextPc()
    }

    private fun InsFTOUB(i: IRInstruction) {
        registers.setUB(i.requireIntDest().registerNumber, registers.getFloat(i.requireFloatSourceA().registerNumber).toInt().toUByte())
        nextPc()
    }

    private fun InsFTOUW(i: IRInstruction) {
        registers.setUW(i.requireIntDest().registerNumber, registers.getFloat(i.requireFloatSourceA().registerNumber).toInt().toUShort())
        nextPc()
    }

    private fun InsFTOSB(i: IRInstruction) {
        registers.setSB(i.requireIntDest().registerNumber, registers.getFloat(i.requireFloatSourceA().registerNumber).toInt().toByte())
        nextPc()
    }

    private fun InsFTOSW(i: IRInstruction) {
        registers.setSW(i.requireIntDest().registerNumber, registers.getFloat(i.requireFloatSourceA().registerNumber).toInt().toShort())
        nextPc()
    }

    private fun InsFTOSL(i: IRInstruction) {
        registers.setSL(i.requireIntDest().registerNumber, registers.getFloat(i.requireFloatSourceA().registerNumber).toInt())
        nextPc()
    }

    private fun InsFPOW(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(dest)
        val exponent = registers.getFloat(src)
        registers.setFloat(dest, value.pow(exponent))
        nextPc()
    }

    private fun InsFSIN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val angle = registers.getFloat(src)
        registers.setFloat(dest, sin(angle))
        nextPc()
    }

    private fun InsFCOS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val angle = registers.getFloat(src)
        registers.setFloat(dest, cos(angle))
        nextPc()
    }

    private fun InsFTAN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val angle = registers.getFloat(src)
        registers.setFloat(dest, tan(angle))
        nextPc()
    }

    private fun InsFATAN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val angle = registers.getFloat(src)
        registers.setFloat(dest, atan(angle))
        nextPc()
    }

    private fun InsFABS(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, abs(value))
        nextPc()
    }

    private fun InsFLN(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, ln(value))
        nextPc()
    }

    private fun InsFLOG(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, log2(value))
        nextPc()
    }

    private fun InsFROUND(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, round(value))
        nextPc()
    }

    private fun InsFFLOOR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, floor(value))
        nextPc()
    }

    private fun InsFCEIL(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        val src = i.requireSrcA().registerNumber
        val value = registers.getFloat(src)
        registers.setFloat(dest, ceil(value))
        nextPc()
    }

    private fun InsFCOMP(i: IRInstruction) {
        val dest = i.requireIntDest().registerNumber
        val left = registers.getFloat(i.requireFloatSourceA().registerNumber)
        val right = registers.getFloat(i.requireSrcB().registerNumber)
        val result =
            if(left<right)
                255u        // -1
            else if(left>right)
                1u
            else
                0u
        registers.setUB(dest, result.toUByte())
        nextPc()
    }

    private fun InsLOADHR(i: IRInstruction) {
        val dest = i.requireDest().registerNumber
        when(val slot = i.requireHardwareSlot().slot.value) {
            0 -> registers.setUB(dest, hardwareRegisterA)
            1 -> registers.setUB(dest, hardwareRegisterX)
            2 -> registers.setUB(dest, hardwareRegisterY)
            3 -> registers.setUW(dest, ((hardwareRegisterX.toUInt() shl 8) + hardwareRegisterA).toUShort())
            4 -> registers.setUW(dest, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterA).toUShort())
            5 -> registers.setUW(dest, ((hardwareRegisterY.toUInt() shl 8) + hardwareRegisterX).toUShort())
            6 -> registers.setFloat(dest, hardwareRegisterFAC0)
            7 -> registers.setFloat(dest, hardwareRegisterFAC1)
            in 10..17 -> {
                val reg = slot - 10
                when(i.type) {
                    IRDataType.BYTE -> registers.setUB(dest, hardwareRegisterD[reg].toUByte())
                    IRDataType.WORD -> registers.setUW(dest, hardwareRegisterD[reg].toUShort())
                    IRDataType.LONG, IRDataType.POINTER -> registers.setUL(dest, hardwareRegisterD[reg])
                    else -> throw IllegalArgumentException("invalid type for D register LOADHR: ${i.type}")
                }
            }
            in 18..24 -> {
                val reg = slot - 18
                when(i.type) {
                    IRDataType.WORD -> registers.setUW(dest, hardwareRegisterAddr[reg].toUShort())
                    IRDataType.LONG, IRDataType.POINTER -> registers.setUL(dest, hardwareRegisterAddr[reg])
                    else -> throw IllegalArgumentException("invalid type for A register LOADHR: ${i.type}")
                }
            }
            in 25..32 -> {
                val reg = slot - 25
                if(i.type==IRDataType.FLOAT)
                    registers.setFloat(dest, hardwareRegisterFP[reg])
                else
                    throw IllegalArgumentException("invalid type for FP register LOADHR: ${i.type}")
            }
            else -> throw IllegalArgumentException("unknown hardware register slot: $slot")
        }
        nextPc()
    }

    private fun InsSTOREHR(i: IRInstruction) {
        val src = i.requireSrcA().registerNumber
        when(val slot = i.requireHardwareSlot().slot.value) {
            0 -> hardwareRegisterA = registers.getUB(src)
            1 -> hardwareRegisterX = registers.getUB(src)
            2 -> hardwareRegisterY = registers.getUB(src)
            3 -> {
                val word = registers.getUW(src).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterX = (word shr 8).toUByte()
            }
            4 -> {
                val word = registers.getUW(src).toUInt()
                hardwareRegisterA = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            5 -> {
                val word = registers.getUW(src).toUInt()
                hardwareRegisterX = (word and 255u).toUByte()
                hardwareRegisterY = (word shr 8).toUByte()
            }
            6 -> hardwareRegisterFAC0 = registers.getFloat(src)
            7 -> hardwareRegisterFAC1 = registers.getFloat(src)
            in 10..17 -> {
                val reg = slot - 10
                hardwareRegisterD[reg] = when(i.type) {
                    IRDataType.BYTE -> registers.getUB(src).toUInt()
                    IRDataType.WORD -> registers.getUW(src).toUInt()
                    IRDataType.LONG, IRDataType.POINTER -> registers.getUL(src)
                    else -> throw IllegalArgumentException("invalid type for D register STOREHR: ${i.type}")
                }
            }
            in 18..24 -> {
                val reg = slot - 18
                hardwareRegisterAddr[reg] = when(i.type) {
                    IRDataType.WORD -> registers.getUW(src).toUInt()
                    IRDataType.LONG, IRDataType.POINTER -> registers.getUL(src)
                    else -> throw IllegalArgumentException("invalid type for A register STOREHR: ${i.type}")
                }
            }
            in 25..32 -> {
                val reg = slot - 25
                if(i.type==IRDataType.FLOAT)
                    hardwareRegisterFP[reg] = registers.getFloat(src)
                else
                    throw IllegalArgumentException("invalid type for FP register STOREHR: ${i.type}")
            }
            else -> throw IllegalArgumentException("unknown hardware register slot: $slot")
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
