package sim65.components


class InstructionError(msg: String) : RuntimeException(msg)

interface ICpu {
    fun disassemble(memory: Array<UByte>, baseAddress: Address, from: Address, to: Address): List<String>
    fun disassemble(component: MemMappedComponent, from: Address, to: Address) =
            disassemble(component.cloneMem(), component.startAddress, from, to)

    fun clock()
    fun reset()
    fun step()

    var tracing: Boolean
    val totalCycles: Int
}

// TODO: add additional cycles to certain instructions and addressing modes
// TODO: test all opcodes and fix bugs


class Cpu6502(private val illegalInstrsAllowed: Boolean) : BusComponent(), ICpu {
    override var tracing: Boolean = false
    override var totalCycles: Int = 0
        private set

    companion object {
        const val NMI_vector = 0xfffa
        const val RESET_vector = 0xfffc
        const val IRQ_vector = 0xfffe

        fun hexW(number: Address, allowSingleByte: Boolean = false): String {
            val msb = number ushr 8
            val lsb = number and 255
            return if(msb==0 && allowSingleByte)
                hexB(lsb)
            else
                hexB(msb) + hexB(lsb)
        }

        private const val hexdigits = "0123456789abcdef"

        fun hexB(number: Short): String = hexB(number.toInt())

        fun hexB(number: Int): String {
            val loNibble = number and 15
            val hiNibble = number ushr 4
            return hexdigits[hiNibble].toString() + hexdigits[loNibble]
        }
    }

    enum class AddrMode {
        Imp,
        Acc,
        Imm,
        Zp,
        ZpX,
        ZpY,
        Rel,
        Abs,
        AbsX,
        AbsY,
        Ind,
        IzX,
        IzY
    }

    class Instruction(val mnemonic: String, val mode: AddrMode, val cycles: Int, val official: Boolean, val execute: () -> Unit)

    class StatusRegister(
            var C: Boolean = false,
            var Z: Boolean = false,
            var I: Boolean = false,
            var D: Boolean = false,
            var B: Boolean = false,
            var V: Boolean = false,
            var N: Boolean = false
    ) {
        fun asByte(): UByte {
            return (0b00100000 or
                    (if (N) 0b10000000 else 0) or
                    (if (V) 0b01000000 else 0) or
                    (if (B) 0b00010000 else 0) or
                    (if (D) 0b00001000 else 0) or
                    (if (I) 0b00000100 else 0) or
                    (if (Z) 0b00000010 else 0) or
                    (if (C) 0b00000001 else 0)
                    ).toShort()
        }

        fun fromByte(byte: Int) {
            N = (byte and 0b10000000) != 0
            V = (byte and 0b01000000) != 0
            B = (byte and 0b00010000) != 0
            D = (byte and 0b00001000) != 0
            I = (byte and 0b00000100) != 0
            Z = (byte and 0b00000010) != 0
            C = (byte and 0b00000001) != 0
        }

        override fun toString(): String {
            return asByte().toString(2).padStart(8, '0')
        }

        override fun hashCode(): Int = asByte().toInt()

        override fun equals(other: Any?): Boolean {
            if(other !is StatusRegister)
                return false
            return asByte()==other.asByte()
        }
    }


    var instrCycles: Int = 0
    var A: Int = 0
    var X: Int = 0
    var Y: Int = 0
    var SP: Int = 0
    var PC: Address = 0
    val Status = StatusRegister()
    var currentOpcode: Int = 0
    var waiting: Boolean = false    // 65c02
    private lateinit var currentInstruction: Instruction

    // data byte from the instruction (only set when addr.mode is Accumulator, Immediate or Implied)
    private var fetchedData: Int = 0

    // all other addressing modes yield a fetched memory address
    private var fetchedAddress: Address = 0

    private val addressingModes = mapOf<AddrMode, () -> Unit>(
            AddrMode.Imp to ::amImp,
            AddrMode.Acc to ::amAcc,
            AddrMode.Imm to ::amImm,
            AddrMode.Zp to ::amZp,
            AddrMode.ZpX to ::amZpx,
            AddrMode.ZpY to ::amZpy,
            AddrMode.Rel to ::amRel,
            AddrMode.Abs to ::amAbs,
            AddrMode.AbsX to ::amAbsx,
            AddrMode.AbsY to ::amAbsy,
            AddrMode.Ind to ::amInd,
            AddrMode.IzX to ::amIzx,
            AddrMode.IzY to ::amIzy
    )

    override fun disassemble(memory: Array<UByte>, baseAddress: Address, from: Address, to: Address): List<String> {
        var address = from - baseAddress
        val spacing1 = "        "
        val spacing2 = "     "
        val spacing3 = "  "
        val result = mutableListOf<String>()

        while (address <= (to - baseAddress)) {
            val byte = memory[address]
            var line = "\$${hexW(address)}  ${hexB(byte)} "
            address++
            val opcode = opcodes[byte.toInt()]
            if (!opcode.official && !illegalInstrsAllowed) {
                line += "$spacing1 ???"
            } else {
                when (opcode.mode) {
                    AddrMode.Acc -> {
                        line += "$spacing1 ${opcode.mnemonic}  a"
                    }
                    AddrMode.Imp -> {
                        line += "$spacing1 ${opcode.mnemonic}"
                    }
                    AddrMode.Imm -> {
                        val value = memory[address++]
                        line += "${hexB(value)} $spacing2 ${opcode.mnemonic}  #\$${hexB(value)}"
                    }
                    AddrMode.Zp -> {
                        val zpAddr = memory[address++]
                        line += "${hexB(zpAddr)} $spacing2 ${opcode.mnemonic}  \$${hexB(zpAddr)}"
                    }
                    AddrMode.ZpX -> {
                        val zpAddr = memory[address++]
                        line += "${hexB(zpAddr)} $spacing2 ${opcode.mnemonic}  \$${hexB(zpAddr)},x"
                    }
                    AddrMode.ZpY -> {
                        val zpAddr = memory[address++]
                        line += "${hexB(zpAddr)} $spacing2 ${opcode.mnemonic}  \$${hexB(zpAddr)},y"
                    }
                    AddrMode.Rel -> {
                        val rel = memory[address++]
                        val target =
                                if (rel <= 0x7f)
                                    address + rel
                                else
                                    address - (256 - rel)
                        line += "${hexB(rel)} $spacing2 ${opcode.mnemonic}  \$${hexW(target, true)}"
                    }
                    AddrMode.Abs -> {
                        val lo = memory[address++]
                        val hi = memory[address++]
                        val absAddr = lo.toInt() or (hi.toInt() shl 8)
                        line += "${hexB(lo)} ${hexB(hi)} $spacing3 ${opcode.mnemonic}  \$${hexW(absAddr)}"
                    }
                    AddrMode.AbsX -> {
                        val lo = memory[address++]
                        val hi = memory[address++]
                        val absAddr = lo.toInt() or (hi.toInt() shl 8)
                        line += "${hexB(lo)} ${hexB(hi)} $spacing3 ${opcode.mnemonic}  \$${hexW(absAddr)},x"
                    }
                    AddrMode.AbsY -> {
                        val lo = memory[address++]
                        val hi = memory[address++]
                        val absAddr = lo.toInt() or (hi.toInt() shl 8)
                        line += "${hexB(lo)} ${hexB(hi)} $spacing3 ${opcode.mnemonic}  \$${hexW(absAddr)},y"
                    }
                    AddrMode.Ind -> {
                        val lo = memory[address++]
                        val hi = memory[address++]
                        val indirectAddr = lo.toInt() or (hi.toInt() shl 8)
                        line += "${hexB(lo)} ${hexB(hi)} $spacing3 ${opcode.mnemonic}  (\$${hexW(indirectAddr)})"
                    }
                    AddrMode.IzX -> {
                        val zpAddr = memory[address++]
                        line += "${hexB(zpAddr)} $spacing2 ${opcode.mnemonic}  (\$${hexB(zpAddr)},x)"
                    }
                    AddrMode.IzY -> {
                        val zpAddr = memory[address++]
                        line += "${hexB(zpAddr)} $spacing2 ${opcode.mnemonic}  (\$${hexB(zpAddr)}),y"
                    }
                }
            }
            result.add(line)
        }

        return result
    }

    override fun reset() {
        SP = 0xfd
        PC = readWord(RESET_vector)
        A = 0
        X = 0
        Y = 0
        Status.C = false
        Status.Z = false
        Status.I = false
        Status.D = false
        Status.B = false
        Status.V = false
        Status.N = false
        instrCycles = 8
        currentOpcode = 0
        currentInstruction = opcodes[0]
        waiting = false
    }

    override fun clock() {
        if (instrCycles == 0) {
            currentOpcode = read(PC++)
            currentInstruction = opcodes[currentOpcode]

            if (tracing) {
                printState()
            }

            if (!currentInstruction.official && !illegalInstrsAllowed) {
                throw InstructionError("illegal instructions not enabled")
            }

            instrCycles = currentInstruction.cycles
            addressingModes.getValue(currentInstruction.mode)()
            currentInstruction.execute()
        }

        instrCycles--
        totalCycles++
    }

    override fun step() {
        // step a whole instruction
        while(instrCycles>0) clock()        // instruction subcycles
        clock()   // the actual instruction execution cycle
    }

    fun printState() {
        println("cycle:$totalCycles - pc=${hexW(PC)} " +
                "A=${hexB(A)} " +
                "X=${hexB(X)} " +
                "Y=${hexB(Y)} " +
                "SP=${hexB(SP)} " +
                " n=" + (if (Status.N) "1" else "0") +
                " v=" + (if (Status.V) "1" else "0") +
                " b=" + (if (Status.B) "1" else "0") +
                " d=" + (if (Status.D) "1" else "0") +
                " i=" + (if (Status.I) "1" else "0") +
                " z=" + (if (Status.Z) "1" else "0") +
                " c=" + (if (Status.C) "1" else "0") +
                "  icycles=$instrCycles  instr=${hexB(currentOpcode)}:${currentInstruction.mnemonic}"
        )
    }

    private fun amImp() {
        fetchedData = A
    }

    private fun amAcc() {
        fetchedData = A
    }

    private fun amImm() {
        fetchedData = readPc()
    }

    private fun amZp() {
        fetchedAddress = readPc()
    }

    private fun amZpx() {
        // note: zeropage index will not leave Zp when page boundray is crossed
        fetchedAddress = (readPc() + X) and 255
    }

    private fun amZpy() {
        // note: zeropage index will not leave Zp when page boundray is crossed
        fetchedAddress = (readPc() + Y) and 255
    }

    private fun amRel() {
        val relative = readPc().toByte()
        fetchedAddress = if (relative >= 0x80)
            PC - (256 - relative)
        else
            PC + relative
    }

    private fun amAbs() {
        val lo = readPc()
        val hi = readPc()
        fetchedAddress = lo or (hi shl 8)
    }

    private fun amAbsx() {
        val lo = readPc()
        val hi = readPc()
        fetchedAddress = (X + (lo or (hi shl 8))) and 0xffff
    }

    private fun amAbsy() {
        val lo = readPc()
        val hi = readPc()
        fetchedAddress = (Y + (lo or (hi shl 8))) and 0xffff
    }

    private fun amInd() {
        // not able to fetch an adress which crosses the page boundary (6502, fixed in 65C02)
        var lo = readPc()
        var hi = readPc()
        fetchedAddress = lo or (hi shl 8)
        if (lo == 0xff) {
            // emulate bug
            lo = read(fetchedAddress)
            hi = read(fetchedAddress and 0xff00)
        } else {
            // normal behavior
            lo = read(fetchedAddress)
            hi = read(fetchedAddress + 1)
        }
        fetchedAddress = lo or (hi shl 8)
    }

    private fun amIzx() {
        // note: not able to fetch an adress which crosses the page boundary
        fetchedAddress = readPc() + X
        val lo = read(fetchedAddress)
        val hi = read((fetchedAddress + 1) and 255)
        fetchedAddress = lo or (hi shl 8)
    }

    private fun amIzy() {
        // note: not able to fetch an adress which crosses the page boundary
        fetchedAddress = readPc()
        val lo = read(fetchedAddress)
        val hi = read((fetchedAddress + 1) and 255)
        fetchedAddress = Y + (lo or (hi shl 8))
    }

    private fun readPc(): Int = bus.read(PC++).toInt()

    private fun pushStackAddr(address: Address) {
        val lo = address and 255
        val hi = (address ushr 8)
        pushStack(hi)
        pushStack(lo)
    }

    private fun pushStack(status: StatusRegister) {
        pushStack(status.asByte().toInt())
    }

    private fun pushStack(data: Int) {
        write(SP or 0x0100, data)
        SP = (SP - 1) and 0xff
    }

    private fun popStack(): Int {
        SP = (SP + 1) and 0xff
        return read(SP or 0x0100)
    }

    private fun popStackAddr(): Address {
        val lo = popStack()
        val hi = popStack()
        return lo + hi ushr 8
    }

    private fun read(address: Address): Int = bus.read(address).toInt()
    private fun readWord(address: Address): Int = bus.read(address).toInt() or (bus.read(address + 1).toInt() shl 8)
    private fun write(address: Address, data: Int) = bus.write(address, data.toShort())

    // opcodes table from  http://www.oxyron.de/html/opcodes02.html
    private val opcodes = listOf(
            /* 00 */  Instruction("brk", AddrMode.Imp, 7, true, ::iBrk),
            /* 01 */  Instruction("ora", AddrMode.IzX, 6, true, ::iOra),
            /* 02 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 03 */  Instruction("slo", AddrMode.IzX, 8, false, ::iSlo),
            /* 04 */  Instruction("nop", AddrMode.Zp, 3, false, ::iNop),
            /* 05 */  Instruction("ora", AddrMode.Zp, 3, true, ::iOra),
            /* 06 */  Instruction("asl", AddrMode.Zp, 5, true, ::iAsl),
            /* 07 */  Instruction("slo", AddrMode.Zp, 5, false, ::iSlo),
            /* 08 */  Instruction("php", AddrMode.Imp, 3, true, ::iPhp),
            /* 09 */  Instruction("ora", AddrMode.Imm, 2, true, ::iOra),
            /* 0a */  Instruction("asl", AddrMode.Acc, 2, true, ::iAsl),
            /* 0b */  Instruction("anc", AddrMode.Imm, 2, false, ::iAnc),
            /* 0c */  Instruction("nop", AddrMode.Abs, 4, false, ::iNop),
            /* 0d */  Instruction("ora", AddrMode.Abs, 4, true, ::iOra),
            /* 0e */  Instruction("asl", AddrMode.Abs, 6, true, ::iAsl),
            /* 0f */  Instruction("slo", AddrMode.Abs, 6, false, ::iSlo),
            /* 10 */  Instruction("bpl", AddrMode.Rel, 2, true, ::iBpl),
            /* 11 */  Instruction("ora", AddrMode.IzY, 5, true, ::iOra),
            /* 12 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 13 */  Instruction("slo", AddrMode.IzY, 6, false, ::iSlo),
            /* 14 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* 15 */  Instruction("ora", AddrMode.ZpX, 4, true, ::iOra),
            /* 16 */  Instruction("asl", AddrMode.ZpX, 6, true, ::iAsl),
            /* 17 */  Instruction("slo", AddrMode.ZpX, 6, false, ::iSlo),
            /* 18 */  Instruction("clc", AddrMode.Imp, 2, true, ::iClc),
            /* 19 */  Instruction("ora", AddrMode.AbsY, 4, true, ::iOra),
            /* 1a */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* 1b */  Instruction("slo", AddrMode.AbsY, 7, false, ::iSlo),
            /* 1c */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* 1d */  Instruction("ora", AddrMode.AbsX, 4, true, ::iOra),
            /* 1e */  Instruction("asl", AddrMode.AbsX, 7, true, ::iAsl),
            /* 1f */  Instruction("slo", AddrMode.AbsX, 7, false, ::iSlo),
            /* 20 */  Instruction("jsr", AddrMode.Abs, 6, true, ::iJsr),
            /* 21 */  Instruction("and", AddrMode.IzX, 6, true, ::iAnd),
            /* 22 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 23 */  Instruction("rla", AddrMode.IzX, 8, false, ::iRla),
            /* 24 */  Instruction("bit", AddrMode.Zp, 3, true, ::iBit),
            /* 25 */  Instruction("and", AddrMode.Zp, 3, true, ::iAnd),
            /* 26 */  Instruction("rol", AddrMode.Zp, 5, true, ::iRol),
            /* 27 */  Instruction("rla", AddrMode.Zp, 5, false, ::iRla),
            /* 28 */  Instruction("plp", AddrMode.Imp, 4, true, ::iPlp),
            /* 29 */  Instruction("and", AddrMode.Imm, 2, true, ::iAnd),
            /* 2a */  Instruction("rol", AddrMode.Acc, 2, true, ::iRol),
            /* 2b */  Instruction("anc", AddrMode.Imm, 2, false, ::iAnc),
            /* 2c */  Instruction("bit", AddrMode.Abs, 4, true, ::iBit),
            /* 2d */  Instruction("and", AddrMode.Abs, 4, true, ::iAnd),
            /* 2e */  Instruction("rol", AddrMode.Abs, 6, true, ::iRol),
            /* 2f */  Instruction("rla", AddrMode.Abs, 6, false, ::iRla),
            /* 30 */  Instruction("bmi", AddrMode.Rel, 2, true, ::iBmi),
            /* 31 */  Instruction("and", AddrMode.IzY, 5, true, ::iAnd),
            /* 32 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 33 */  Instruction("rla", AddrMode.IzY, 8, false, ::iRla),
            /* 34 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* 35 */  Instruction("and", AddrMode.ZpX, 4, true, ::iAnd),
            /* 36 */  Instruction("rol", AddrMode.ZpX, 6, true, ::iRol),
            /* 37 */  Instruction("rla", AddrMode.ZpX, 6, false, ::iRla),
            /* 38 */  Instruction("sec", AddrMode.Imp, 2, true, ::iSec),
            /* 39 */  Instruction("and", AddrMode.AbsY, 4, true, ::iAnd),
            /* 3a */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* 3b */  Instruction("rla", AddrMode.AbsY, 7, false, ::iRla),
            /* 3c */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* 3d */  Instruction("and", AddrMode.AbsX, 4, true, ::iAnd),
            /* 3e */  Instruction("rol", AddrMode.AbsX, 7, true, ::iRol),
            /* 3f */  Instruction("rla", AddrMode.AbsX, 7, false, ::iRla),
            /* 40 */  Instruction("rti", AddrMode.Imp, 6, true, ::iRti),
            /* 41 */  Instruction("eor", AddrMode.IzX, 6, true, ::iEor),
            /* 42 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 43 */  Instruction("sre", AddrMode.IzX, 8, false, ::iSre),
            /* 44 */  Instruction("nop", AddrMode.Zp, 3, false, ::iNop),
            /* 45 */  Instruction("eor", AddrMode.Zp, 3, true, ::iEor),
            /* 46 */  Instruction("lsr", AddrMode.Zp, 5, true, ::iLsr),
            /* 47 */  Instruction("sre", AddrMode.Zp, 5, false, ::iSre),
            /* 48 */  Instruction("pha", AddrMode.Imp, 3, true, ::iPha),
            /* 49 */  Instruction("eor", AddrMode.Imm, 2, true, ::iEor),
            /* 4a */  Instruction("lsr", AddrMode.Acc, 2, true, ::iLsr),
            /* 4b */  Instruction("alr", AddrMode.Imm, 2, false, ::iAlr),
            /* 4c */  Instruction("jmp", AddrMode.Abs, 3, true, ::iJmp),
            /* 4d */  Instruction("eor", AddrMode.Abs, 4, true, ::iEor),
            /* 4e */  Instruction("lsr", AddrMode.Abs, 6, true, ::iLsr),
            /* 4f */  Instruction("sre", AddrMode.Abs, 6, false, ::iSre),
            /* 50 */  Instruction("bvc", AddrMode.Rel, 2, true, ::iBvc),
            /* 51 */  Instruction("eor", AddrMode.IzY, 5, true, ::iEor),
            /* 52 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 53 */  Instruction("sre", AddrMode.IzY, 8, false, ::iSre),
            /* 54 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* 55 */  Instruction("eor", AddrMode.ZpX, 4, true, ::iEor),
            /* 56 */  Instruction("lsr", AddrMode.ZpX, 6, true, ::iLsr),
            /* 57 */  Instruction("sre", AddrMode.ZpX, 6, false, ::iSre),
            /* 58 */  Instruction("cli", AddrMode.Imp, 2, true, ::iCli),
            /* 59 */  Instruction("eor", AddrMode.AbsY, 4, true, ::iEor),
            /* 5a */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* 5b */  Instruction("sre", AddrMode.AbsY, 7, false, ::iSre),
            /* 5c */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* 5d */  Instruction("eor", AddrMode.AbsX, 4, true, ::iEor),
            /* 5e */  Instruction("lsr", AddrMode.AbsX, 7, true, ::iLsr),
            /* 5f */  Instruction("sre", AddrMode.AbsX, 7, false, ::iSre),
            /* 60 */  Instruction("rts", AddrMode.Imp, 6, true, ::iRts),
            /* 61 */  Instruction("adc", AddrMode.IzX, 6, true, ::iAdc),
            /* 62 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 63 */  Instruction("rra", AddrMode.IzX, 8, false, ::iRra),
            /* 64 */  Instruction("nop", AddrMode.Zp, 3, false, ::iNop),
            /* 65 */  Instruction("adc", AddrMode.Zp, 3, true, ::iAdc),
            /* 66 */  Instruction("ror", AddrMode.Zp, 5, true, ::iRor),
            /* 67 */  Instruction("rra", AddrMode.Zp, 5, false, ::iRra),
            /* 68 */  Instruction("pla", AddrMode.Imp, 4, true, ::iPla),
            /* 69 */  Instruction("adc", AddrMode.Imm, 2, true, ::iAdc),
            /* 6a */  Instruction("ror", AddrMode.Acc, 2, true, ::iRor),
            /* 6b */  Instruction("arr", AddrMode.Imm, 2, false, ::iArr),
            /* 6c */  Instruction("jmp", AddrMode.Ind, 5, true, ::iJmp),
            /* 6d */  Instruction("adc", AddrMode.Abs, 4, true, ::iAdc),
            /* 6e */  Instruction("ror", AddrMode.Abs, 6, true, ::iRor),
            /* 6f */  Instruction("rra", AddrMode.Abs, 6, false, ::iRra),
            /* 70 */  Instruction("bvs", AddrMode.Rel, 2, true, ::iBvs),
            /* 71 */  Instruction("adc", AddrMode.IzY, 5, true, ::iAdc),
            /* 72 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 73 */  Instruction("rra", AddrMode.IzY, 8, false, ::iRra),
            /* 74 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* 75 */  Instruction("adc", AddrMode.ZpX, 4, true, ::iAdc),
            /* 76 */  Instruction("ror", AddrMode.ZpX, 6, true, ::iRor),
            /* 77 */  Instruction("rra", AddrMode.ZpX, 6, false, ::iRra),
            /* 78 */  Instruction("sei", AddrMode.Imp, 2, true, ::iSei),
            /* 79 */  Instruction("adc", AddrMode.AbsY, 4, true, ::iAdc),
            /* 7a */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* 7b */  Instruction("rra", AddrMode.AbsY, 7, false, ::iRra),
            /* 7c */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* 7d */  Instruction("adc", AddrMode.AbsX, 4, true, ::iAdc),
            /* 7e */  Instruction("ror", AddrMode.AbsX, 7, true, ::iRor),
            /* 7f */  Instruction("rra", AddrMode.AbsX, 7, false, ::iRra),
            /* 80 */  Instruction("nop", AddrMode.Imm, 2, false, ::iNop),
            /* 81 */  Instruction("sta", AddrMode.IzX, 6, true, ::iSta),
            /* 82 */  Instruction("nop", AddrMode.Imm, 2, false, ::iNop),
            /* 83 */  Instruction("sax", AddrMode.IzX, 6, false, ::iSax),
            /* 84 */  Instruction("sty", AddrMode.Zp, 3, true, ::iSty),
            /* 85 */  Instruction("sta", AddrMode.Zp, 3, true, ::iSta),
            /* 86 */  Instruction("stx", AddrMode.Zp, 3, true, ::iStx),
            /* 87 */  Instruction("sax", AddrMode.Zp, 3, false, ::iSax),
            /* 88 */  Instruction("dey", AddrMode.Imp, 2, true, ::iDey),
            /* 89 */  Instruction("nop", AddrMode.Imm, 2, false, ::iNop),
            /* 8a */  Instruction("txa", AddrMode.Imp, 2, true, ::iTxa),
            /* 8b */  Instruction("xaa", AddrMode.Imm, 2, false, ::iXaa),
            /* 8c */  Instruction("sty", AddrMode.Abs, 4, true, ::iSty),
            /* 8d */  Instruction("sta", AddrMode.Abs, 4, true, ::iSta),
            /* 8e */  Instruction("stx", AddrMode.Abs, 4, true, ::iStx),
            /* 8f */  Instruction("sax", AddrMode.Abs, 4, true, ::iSax),
            /* 90 */  Instruction("bcc", AddrMode.Rel, 2, true, ::iBcc),
            /* 91 */  Instruction("sta", AddrMode.IzY, 6, true, ::iSta),
            /* 92 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* 93 */  Instruction("ahx", AddrMode.IzY, 6, false, ::iAhx),
            /* 94 */  Instruction("sty", AddrMode.ZpX, 4, true, ::iSty),
            /* 95 */  Instruction("sta", AddrMode.ZpX, 4, true, ::iSta),
            /* 96 */  Instruction("stx", AddrMode.ZpY, 4, true, ::iStx),
            /* 97 */  Instruction("sax", AddrMode.ZpY, 4, false, ::iSax),
            /* 98 */  Instruction("tya", AddrMode.Imp, 2, true, ::iTya),
            /* 99 */  Instruction("sta", AddrMode.AbsY, 5, true, ::iSta),
            /* 9a */  Instruction("txs", AddrMode.Imp, 2, true, ::iTxs),
            /* 9b */  Instruction("tas", AddrMode.AbsY, 5, false, ::iTas),
            /* 9c */  Instruction("shy", AddrMode.AbsX, 5, false, ::iShy),
            /* 9d */  Instruction("sta", AddrMode.AbsX, 5, true, ::iSta),
            /* 9e */  Instruction("shx", AddrMode.AbsY, 5, false, ::iShx),
            /* 9f */  Instruction("ahx", AddrMode.AbsY, 5, false, ::iAhx),
            /* a0 */  Instruction("ldy", AddrMode.Imm, 2, true, ::iLdy),
            /* a1 */  Instruction("lda", AddrMode.IzX, 6, true, ::iLda),
            /* a2 */  Instruction("ldx", AddrMode.Imm, 2, true, ::iLdx),
            /* a3 */  Instruction("lax", AddrMode.IzX, 6, false, ::iLax),
            /* a4 */  Instruction("ldy", AddrMode.Zp, 3, true, ::iLdy),
            /* a5 */  Instruction("lda", AddrMode.Zp, 3, true, ::iLda),
            /* a6 */  Instruction("ldx", AddrMode.Zp, 3, true, ::iLdx),
            /* a7 */  Instruction("lax", AddrMode.Zp, 3, false, ::iLax),
            /* a8 */  Instruction("tay", AddrMode.Imp, 2, true, ::iTay),
            /* a9 */  Instruction("lda", AddrMode.Imm, 2, true, ::iLda),
            /* aa */  Instruction("tax", AddrMode.Imp, 2, true, ::iTax),
            /* ab */  Instruction("lax", AddrMode.Imm, 2, false, ::iLax),
            /* ac */  Instruction("ldy", AddrMode.Abs, 4, true, ::iLdy),
            /* ad */  Instruction("lda", AddrMode.Abs, 4, true, ::iLda),
            /* ae */  Instruction("ldx", AddrMode.Abs, 4, true, ::iLdx),
            /* af */  Instruction("lax", AddrMode.Abs, 4, false, ::iLax),
            /* b0 */  Instruction("bcs", AddrMode.Rel, 2, true, ::iBcs),
            /* b1 */  Instruction("lda", AddrMode.IzY, 5, true, ::iLda),
            /* b2 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* b3 */  Instruction("lax", AddrMode.IzY, 5, false, ::iLax),
            /* b4 */  Instruction("ldy", AddrMode.ZpX, 4, true, ::iLdy),
            /* b5 */  Instruction("lda", AddrMode.ZpX, 4, true, ::iLda),
            /* b6 */  Instruction("ldx", AddrMode.ZpY, 4, true, ::iLdx),
            /* b7 */  Instruction("lax", AddrMode.ZpY, 4, false, ::iLax),
            /* b8 */  Instruction("clv", AddrMode.Imp, 2, true, ::iClv),
            /* b9 */  Instruction("lda", AddrMode.AbsY, 4, true, ::iLda),
            /* ba */  Instruction("tsx", AddrMode.Imp, 2, true, ::iTsx),
            /* bb */  Instruction("las", AddrMode.AbsY, 4, false, ::iLas),
            /* bc */  Instruction("ldy", AddrMode.AbsX, 4, true, ::iLdy),
            /* bd */  Instruction("lda", AddrMode.AbsX, 4, true, ::iLda),
            /* be */  Instruction("ldx", AddrMode.AbsY, 4, true, ::iLdx),
            /* bf */  Instruction("lax", AddrMode.AbsY, 4, false, ::iLax),
            /* c0 */  Instruction("cpy", AddrMode.Imm, 2, true, ::iCpy),
            /* c1 */  Instruction("cmp", AddrMode.IzX, 6, true, ::iCmp),
            /* c2 */  Instruction("nop", AddrMode.Imm, 2, false, ::iNop),
            /* c3 */  Instruction("dcp", AddrMode.IzX, 8, false, ::iDcp),
            /* c4 */  Instruction("cpy", AddrMode.Zp, 3, true, ::iCpy),
            /* c5 */  Instruction("cmp", AddrMode.Zp, 3, true, ::iCmp),
            /* c6 */  Instruction("dec", AddrMode.Zp, 5, true, ::iDec),
            /* c7 */  Instruction("dcp", AddrMode.Zp, 5, false, ::iDcp),
            /* c8 */  Instruction("iny", AddrMode.Imp, 2, true, ::iIny),
            /* c9 */  Instruction("cmp", AddrMode.Imm, 2, true, ::iCmp),
            /* ca */  Instruction("dex", AddrMode.Imp, 2, true, ::iDex),
            /* cb */  Instruction("axs", AddrMode.Imm, 2, false, ::iAxs),
            /* cc */  Instruction("cpy", AddrMode.Abs, 4, true, ::iCpy),
            /* cd */  Instruction("cmp", AddrMode.Abs, 4, true, ::iCmp),
            /* ce */  Instruction("dec", AddrMode.Abs, 6, true, ::iDec),
            /* cf */  Instruction("dcp", AddrMode.Abs, 6, false, ::iDcp),
            /* d0 */  Instruction("bne", AddrMode.Rel, 2, true, ::iBne),
            /* d1 */  Instruction("cmp", AddrMode.IzY, 5, true, ::iCmp),
            /* d2 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* d3 */  Instruction("dcp", AddrMode.IzY, 8, false, ::iDcp),
            /* d4 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* d5 */  Instruction("cmp", AddrMode.ZpX, 4, true, ::iCmp),
            /* d6 */  Instruction("dec", AddrMode.ZpX, 6, true, ::iDec),
            /* d7 */  Instruction("dcp", AddrMode.ZpX, 6, false, ::iDcp),
            /* d8 */  Instruction("cld", AddrMode.Imp, 2, true, ::iCld),
            /* d9 */  Instruction("cmp", AddrMode.AbsY, 4, true, ::iCmp),
            /* da */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* db */  Instruction("dcp", AddrMode.AbsY, 7, false, ::iDcp),
            /* dc */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* dd */  Instruction("cmp", AddrMode.AbsX, 4, true, ::iCmp),
            /* de */  Instruction("dec", AddrMode.AbsX, 7, true, ::iDec),
            /* df */  Instruction("dcp", AddrMode.AbsX, 7, false, ::iDcp),
            /* e0 */  Instruction("cpx", AddrMode.Imm, 2, true, ::iCpx),
            /* e1 */  Instruction("sbc", AddrMode.IzX, 6, true, ::iSbc),
            /* e2 */  Instruction("nop", AddrMode.Imm, 2, false, ::iNop),
            /* e3 */  Instruction("isc", AddrMode.IzX, 8, false, ::iIsc),
            /* e4 */  Instruction("cpx", AddrMode.Zp, 3, true, ::iCpx),
            /* e5 */  Instruction("sbc", AddrMode.Zp, 3, true, ::iSbc),
            /* e6 */  Instruction("inc", AddrMode.Zp, 5, true, ::iInc),
            /* e7 */  Instruction("isc", AddrMode.Zp, 5, false, ::iIsc),
            /* e8 */  Instruction("inx", AddrMode.Imp, 2, true, ::iInx),
            /* e9 */  Instruction("sbc", AddrMode.Imm, 2, true, ::iSbc),
            /* ea */  Instruction("nop", AddrMode.Imp, 2, true, ::iNop),
            /* eb */  Instruction("sbc", AddrMode.Imm, 2, false, ::iSbc),
            /* ec */  Instruction("cpx", AddrMode.Abs, 4, true, ::iCpx),
            /* ed */  Instruction("sbc", AddrMode.Abs, 4, true, ::iSbc),
            /* ee */  Instruction("inc", AddrMode.Abs, 6, true, ::iInc),
            /* ef */  Instruction("isc", AddrMode.Abs, 6, true, ::iIsc),
            /* f0 */  Instruction("beq", AddrMode.Rel, 2, true, ::iBeq),
            /* f1 */  Instruction("sbc", AddrMode.IzY, 5, true, ::iSbc),
            /* f2 */  Instruction("???", AddrMode.Imp, 0, false, ::iInvalid),
            /* f3 */  Instruction("isc", AddrMode.IzY, 8, false, ::iIsc),
            /* f4 */  Instruction("nop", AddrMode.ZpX, 4, false, ::iNop),
            /* f5 */  Instruction("sbc", AddrMode.ZpX, 4, true, ::iSbc),
            /* f6 */  Instruction("inc", AddrMode.ZpX, 6, true, ::iInc),
            /* f7 */  Instruction("isc", AddrMode.ZpX, 6, false, ::iIsc),
            /* f8 */  Instruction("sed", AddrMode.Imp, 2, true, ::iSed),
            /* f9 */  Instruction("sbc", AddrMode.AbsY, 4, true, ::iSbc),
            /* fa */  Instruction("nop", AddrMode.Imp, 2, false, ::iNop),
            /* fb */  Instruction("isc", AddrMode.AbsY, 7, false, ::iIsc),
            /* fc */  Instruction("nop", AddrMode.AbsX, 4, false, ::iNop),
            /* fd */  Instruction("sbc", AddrMode.AbsX, 4, true, ::iSbc),
            /* fe */  Instruction("inc", AddrMode.AbsX, 7, true, ::iInc),
            /* ff */  Instruction("isc", AddrMode.AbsX, 7, false, ::iIsc)
    ).toTypedArray()


    private fun iInvalid() {
        throw InstructionError("invalid instruction encountered: opcode=${hexB(currentOpcode)} instr=${currentInstruction.mnemonic}")
    }

    // official instructions

    private fun iAdc() {
        val operand = if (currentInstruction.mode == AddrMode.Imm) {
            fetchedData
        } else {
            read(fetchedAddress)
        }
        if (Status.D) {
            // BCD add
            var lo = (A and 0x0f) + (operand and 0x0f) + if (Status.C) 1 else 0
            if (lo and 0xff > 9) lo += 6
            var hi = (A shr 4) + (operand shr 4) + if (lo > 15) 1 else 0
            if (hi and 0xff > 9) hi += 6
            var result = lo and 0x0f or (hi shl 4)
            result = result and 0xff
            Status.C = hi > 15
            Status.Z = result == 0
            Status.V = false  // BCD never sets overflow flag
            Status.N = false  // BCD is never negative on NMOS 6502 (bug)
        } else {
            // normal add
            val result = A + operand + if (Status.C) 1 else 0
            Status.C = result > 255
            Status.V = (A xor operand).inv() and (A xor result) and 0x0080 != 0
            A = result and 255
            Status.N = (A and 0b10000000) != 0
            Status.Z = A == 0
        }
    }

    private fun iAnd() {
        A = A and fetchedData
        Status.Z = A == 0
        Status.N = (A and 0b10000000) != 0
    }

    private fun iAsl() {
        if (currentInstruction.mode == AddrMode.Acc) {
            Status.C = (A and 0b10000000) == 1
            A = (A shl 1) and 255
            Status.Z = A == 0
            Status.N = (A and 0b10000000) == 1
        } else {
            val data = read(fetchedAddress)
            Status.C = (data and 0b10000000) == 1
            val shifted = (data shl 1) and 255
            write(fetchedAddress, shifted)
            Status.Z = shifted == 0
            Status.N = (shifted and 0b10000000) == 1
        }
    }

    private fun iBcc() {
        if (!Status.C) PC = fetchedAddress
    }

    private fun iBcs() {
        if (Status.C) PC = fetchedAddress
    }

    private fun iBeq() {
        if (Status.Z) PC = fetchedAddress
    }

    private fun iBit() {
        Status.Z = (A and fetchedData) == 0
        Status.V = (fetchedData and 0b01000000) != 0
        Status.N = (fetchedData and 0b10000000) != 0
    }

    private fun iBmi() {
        if (Status.N) PC = fetchedAddress
    }

    private fun iBne() {
        if (!Status.Z) PC = fetchedAddress
    }

    private fun iBpl() {
        if (!Status.N) PC = fetchedAddress
    }

    private fun iBrk() {
        PC++
        Status.I = true
        Status.B = true
        pushStackAddr(PC)
        pushStack(Status)
        Status.B = false
        PC = readWord(IRQ_vector)
    }

    private fun iBvc() {
        if (!Status.V) PC = fetchedAddress
    }

    private fun iBvs() {
        if (Status.V) PC = fetchedAddress
    }

    private fun iClc() {
        Status.C = false
    }

    private fun iCld() {
        Status.D = false
    }

    private fun iCli() {
        Status.I = false
    }

    private fun iClv() {
        Status.V = false
    }

    private fun iCmp() {
        val fetched =
                if (currentInstruction.mode == AddrMode.Imm) {
                    fetchedData
                } else {
                    read(fetchedAddress)
                }
        Status.C = A >= fetched
        Status.Z = A == fetched
        Status.N = ((A - fetched) and 0b10000000) == 1
    }

    private fun iCpx() {
        val fetched =
                if (currentInstruction.mode == AddrMode.Imm) {
                    fetchedData
                } else {
                    read(fetchedAddress)
                }
        Status.C = X >= fetched
        Status.Z = X == fetched
        Status.N = ((X - fetched) and 0b10000000) == 1
    }

    private fun iCpy() {
        val fetched =
                if (currentInstruction.mode == AddrMode.Imm) {
                    fetchedData
                } else {
                    read(fetchedAddress)
                }
        Status.C = Y >= fetched
        Status.Z = Y == fetched
        Status.N = ((Y - fetched) and 0b10000000) == 1
    }

    private fun iDec() {
        val data = (read(fetchedAddress) - 1) and 255
        write(fetchedAddress, data)
        Status.Z = data == 0
        Status.N = (data and 0b10000000) == 1
    }

    private fun iDex() {
        X = (X - 1) and 255
        Status.Z = X == 0
        Status.N = (X and 0b10000000) == 1
    }

    private fun iDey() {
        Y = (Y - 1) and 255
        Status.Z = Y == 0
        Status.N = (Y and 0b10000000) == 1
    }

    private fun iEor() {
        A = if (currentInstruction.mode == AddrMode.Imm) {
            A xor fetchedData
        } else {
            A xor read(fetchedAddress)
        }
        Status.Z = A == 0
        Status.N = (A and 0b10000000) == 1
    }

    private fun iInc() {
        val data = (read(fetchedAddress) + 1) and 255
        write(fetchedAddress, data)
        Status.Z = data == 0
        Status.N = (data and 0b10000000) == 1
    }

    private fun iInx() {
        X = (X + 1) and 255
        Status.Z = X == 0
        Status.N = (X and 0b10000000) == 1
    }

    private fun iIny() {
        Y = (Y + 1) and 255
        Status.Z = Y == 0
        Status.N = (Y and 0b10000000) == 1
    }

    private fun iJmp() {
        PC = fetchedAddress
    }

    private fun iJsr() {
        pushStackAddr(PC)
        PC = fetchedAddress
    }

    private fun iLda() {
        A = if (currentInstruction.mode == AddrMode.Imm)
            fetchedData
        else
            read(fetchedAddress)
        Status.Z = A == 0
        Status.N = (A and 0b10000000) == 1
    }

    private fun iLdx() {
        X = if (currentInstruction.mode == AddrMode.Imm)
            fetchedData
        else
            read(fetchedAddress)
        Status.Z = X == 0
        Status.N = (X and 0b10000000) == 1
    }

    private fun iLdy() {
        Y = if (currentInstruction.mode == AddrMode.Imm)
            fetchedData
        else
            read(fetchedAddress)
        Status.Z = Y == 0
        Status.N = (Y and 0b10000000) == 1
    }

    private fun iLsr() {
        if (currentInstruction.mode == AddrMode.Acc) {
            Status.C = (A and 1) == 1
            A = A ushr 1
            Status.Z = A == 0
            Status.N = (A and 0b10000000) == 1
        } else {
            val data = read(fetchedAddress)
            Status.C = (data and 1) == 1
            val shifted = data ushr 1
            write(fetchedAddress, shifted)
            Status.Z = shifted == 0
            Status.N = (shifted and 0b10000000) == 1
        }
    }

    private fun iNop() {}

    private fun iOra() {
        A = if (currentInstruction.mode == AddrMode.Imm)
            A or fetchedData
        else
            A or read(fetchedAddress)
        Status.Z = A == 0
        Status.N = (A and 0b10000000) == 1
    }

    private fun iPha() {
        pushStack(A)
    }

    private fun iPhp() {
        pushStack(Status)
    }

    private fun iPla() {
        A = popStack()
        Status.Z = A == 0
        Status.N = (A and 0b10000000) == 1
    }

    private fun iPlp() {
        Status.fromByte(popStack())
    }

    private fun iRol() {
        val oldCarry = Status.C
        if (currentInstruction.mode == AddrMode.Acc) {
            Status.C = (A and 0b10000000) == 1
            A = (A shl 1) or (if (oldCarry) 1 else 0)
            Status.Z = A == 0
            Status.N = (A and 0b10000000) == 1
        } else {
            val data = read(fetchedAddress)
            Status.C = (data and 0b10000000) == 1
            val shifted = (data shl 1) or (if (oldCarry) 1 else 0) and 255
            write(fetchedAddress, shifted)
            Status.Z = shifted == 0
            Status.N = (shifted and 0b10000000) == 1
        }
    }

    private fun iRor() {
        val oldCarry = Status.C
        if (currentInstruction.mode == AddrMode.Acc) {
            Status.C = (A and 1) == 1
            A = (A ushr 1) or (if (oldCarry) 0b10000000 else 0)
            Status.Z = A == 0
            Status.N = (A and 0b10000000) == 1
        } else {
            val data = read(fetchedAddress)
            Status.C = (data and 1) == 1
            val shifted = (data ushr 1) or (if (oldCarry) 0b10000000 else 0)
            write(fetchedAddress, shifted)
            Status.Z = shifted == 0
            Status.N = (shifted and 0b10000000) == 1
        }
    }

    private fun iRti() {
        Status.fromByte(popStack())
        PC = popStackAddr()
    }

    private fun iRts() {
        PC = popStackAddr()
    }

    private fun iSbc() {
        val operand = if (currentInstruction.mode == AddrMode.Imm) {
            fetchedData
        } else {
            read(fetchedAddress)
        }
        if (Status.D) {
            var lo = (A and 0x0f) - (operand and 0x0f) - if (Status.C) 0 else 1
            if (lo and 0x10 != 0) lo -= 6
            var h = (A shr 4) - (operand shr 4) - if (lo and 0x10 != 0) 1 else 0
            if (h and 0x10 != 0) h -= 6
            val result = lo and 0x0f or (h shl 4 and 0xff)
            Status.C = h and 255 < 15
            Status.Z = result == 0
            Status.V = false // BCD never sets overflow flag
            Status.N = false // BCD is never negative on NMOS 6502 (bug)
            A = result and 255
        } else {
            // normal sub
            val invertedOperand = operand xor 255
            val result = A + invertedOperand + if (Status.C) 1 else 0
            Status.C = result > 255
            Status.V = (A xor invertedOperand) and (A xor result) and 0x0080 != 0
            A = result and 255
            Status.N = (A and 0b10000000) != 0
            Status.Z = A == 0
        }
    }

    private fun iSec() {
        Status.C = true
    }

    private fun iSed() {
        Status.D = true
    }

    private fun iSei() {
        Status.I = true
    }

    private fun iSta() {
        write(fetchedAddress, A)
    }

    private fun iStx() {
        write(fetchedAddress, X)
    }

    private fun iSty() {
        write(fetchedAddress, Y)
    }

    private fun iTax() {
        X = A
        Status.Z = X == 0
        Status.N = (X and 0b10000000) != 0
    }

    private fun iTay() {
        Y = A
        Status.Z = Y == 0
        Status.N = (Y and 0b10000000) != 0
    }

    private fun iTsx() {
        X = Status.asByte().toInt()
        Status.Z = X == 0
        Status.N = (X and 0b10000000) != 0
    }

    private fun iTxa() {
        A = X
        Status.Z = A == 0
        Status.N = (A and 0b10000000) != 0
    }

    private fun iTxs() {
        Status.fromByte(X)
    }

    private fun iTya() {
        A = Y
        Status.Z = A == 0
        Status.N = (A and 0b10000000) != 0
    }

    // unofficial/illegal instructions

    private fun iAhx() {
        TODO()
    }

    private fun iAlr() {
        TODO()
    }

    private fun iAnc() {
        TODO()
    }

    private fun iArr() {
        TODO()
    }

    private fun iAxs() {
        TODO()
    }

    private fun iDcp() {
        TODO()
    }

    private fun iIsc() {
        TODO()
    }

    private fun iLas() {
        TODO()
    }

    private fun iLax() {
        TODO()
    }

    private fun iRla() {
        TODO()
    }

    private fun iRra() {
        TODO()
    }

    private fun iSax() {
        TODO()
    }

    private fun iShx() {
        TODO()
    }

    private fun iShy() {
        TODO()
    }

    private fun iSlo() {
        TODO()
    }

    private fun iSre() {
        TODO()
    }

    private fun iTas() {
        TODO()
    }

    private fun iXaa() {
        TODO()
    }

}
