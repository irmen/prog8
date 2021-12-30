package prog8.codegen.target.cx16

import prog8.ast.base.DataType
import prog8.codegen.target.cbm.Mflpt5
import prog8.codegen.target.cbm.viceMonListName
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path


class CX16MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU65c02

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val BASIC_LOAD_ADDRESS = 0x0801u
    override val RAW_LOAD_ADDRESS = 0x8000u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0x0400u        //  $0400-$04ff inclusive
    override val ESTACK_HI = 0x0500u        //  $0500-$05ff inclusive

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = Mflpt5.fromNumber(num)
    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == LauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        val emulatorName: String
        val extraArgs: List<String>

        when(selectedEmulator) {
            1 -> {
                emulatorName = "x16emu"
                extraArgs = emptyList()
            }
            2 -> {
                emulatorName = "box16"
                extraArgs = listOf("-sym", viceMonListName(programNameWithPath.toString()))
            }
            else -> {
                System.err.println("Cx16 target only supports x16emu and box16 emulators.")
                return
            }
        }

        for(emulator in listOf(emulatorName)) {
            println("\nStarting Commander X16 emulator $emulator...")
            val cmdline = listOf(emulator, "-scale", "2", "-run", "-prg", "${programNameWithPath}.prg") + extraArgs
            val processb = ProcessBuilder(cmdline).inheritIO()
            val process: Process
            try {
                process=processb.start()
            } catch(x: IOException) {
                continue  // try the next emulator executable
            }
            process.waitFor()
            break
        }
    }

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0x9f00u..0x9fffu
    override fun getPreallocatedZeropageVars(): Map<String, Pair<UInt, DataType>> {
        val vars = mutableMapOf<String, Pair<UInt, DataType>>()
        for(reg in 0..15) {
            vars["cx16.r${reg}"] = (2+reg*2).toUInt() to DataType.UWORD        // cx16.r0 .. cx16.r15
            vars["cx16.r${reg}s"] = (2+reg*2).toUInt() to DataType.WORD        // cx16.r0s .. cx16.r15s
            vars["cx16.r${reg}L"] = (2+reg*2).toUInt() to DataType.UBYTE       // cx16.r0L .. cx16.r15L
            vars["cx16.r${reg}H"] = (3+reg*2).toUInt() to DataType.UBYTE       // cx16.r0H .. cx16.r15H
            vars["cx16.r${reg}sL"] = (2+reg*2).toUInt() to DataType.BYTE       // cx16.r0sL .. cx16.r15sL
            vars["cx16.r${reg}sH"] = (3+reg*2).toUInt() to DataType.BYTE       // cx16.r0sH .. cx16.r15sH
        }
        return vars
    }

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = CX16Zeropage(compilerOptions)
    }

    // 6502 opcodes (including aliases and illegal opcodes), these cannot be used as variable or label names
    override val opcodeNames = setOf("adc", "and", "asl", "bcc", "bcs",
            "beq", "bge", "bit", "blt", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc",
            "cld", "cli", "clv", "cmp", "cpx", "cpy", "dec", "dex", "dey",
            "eor", "gcc", "gcs", "geq", "gge", "glt", "gmi", "gne", "gpl", "gvc", "gvs",
            "inc", "inx", "iny", "jmp", "jsr",
            "lda", "ldx", "ldy", "lsr", "nop", "ora", "pha", "php",
            "pla", "plp", "rol", "ror", "rti", "rts", "sbc",
            "sec", "sed", "sei",
            "sta", "stx", "sty", "tax", "tay", "tsx", "txa", "txs", "tya",
            "bra", "phx", "phy", "plx", "ply", "stz", "trb", "tsb", "bbr", "bbs",
            "rmb", "smb", "stp", "wai")


}
