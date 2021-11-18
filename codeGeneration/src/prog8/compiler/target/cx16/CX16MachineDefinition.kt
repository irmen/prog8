package prog8.compiler.target.cx16

import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cbm.viceMonListPostfix
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path


object CX16MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU65c02

    // 5-byte cbm MFLPT format limitations:
    override val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
    override val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255
    override val FLOAT_MEM_SIZE = 5
    override val POINTER_MEM_SIZE = 2
    override val BASIC_LOAD_ADDRESS = 0x0801
    override val RAW_LOAD_ADDRESS = 0x8000

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0x0400        //  $0400-$04ff inclusive
    override val ESTACK_HI = 0x0500        //  $0500-$05ff inclusive

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = C64MachineDefinition.Mflpt5.fromNumber(num)
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
                extraArgs = listOf("-sym", "${programNameWithPath}.$viceMonListPostfix")
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

    override fun isIOAddress(address: Int): Boolean = address==0 || address==1 || address in 0x9f00..0x9fff

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


    class CX16Zeropage(options: CompilationOptions) : Zeropage(options) {

        override val SCRATCH_B1 = 0x7a      // temp storage for a single byte
        override val SCRATCH_REG = 0x7b     // temp storage for a register, must be B1+1
        override val SCRATCH_W1 = 0x7c      // temp storage 1 for a word  $7c+$7d
        override val SCRATCH_W2 = 0x7e      // temp storage 2 for a word  $7e+$7f


        init {
            if (options.floats && options.zeropage !in arrayOf(ZeropageType.BASICSAFE, ZeropageType.DONTUSE ))
                throw InternalCompilerException("when floats are enabled, zero page type should be 'basicsafe' or 'dontuse'")

            // the addresses 0x02 to 0x21 (inclusive) are taken for sixteen virtual 16-bit api registers.

            when (options.zeropage) {
                ZeropageType.FULL -> {
                    free.addAll(0x22..0xff)
                }
                ZeropageType.KERNALSAFE -> {
                    free.addAll(0x22..0x7f)
                    free.addAll(0xa9..0xff)
                }
                ZeropageType.BASICSAFE -> {
                    free.addAll(0x22..0x7f)
                }
                ZeropageType.DONTUSE -> {
                    free.clear() // don't use zeropage at all
                }
                else -> throw InternalCompilerException("for this machine target, zero page type 'floatsafe' is not available. ${options.zeropage}")
            }

            removeReservedFromFreePool()
        }
    }
}
