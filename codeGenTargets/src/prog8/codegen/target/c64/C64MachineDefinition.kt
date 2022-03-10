package prog8.codegen.target.c64

import prog8.code.core.CbmPrgLauncherType
import prog8.code.core.OutputType
import prog8.code.core.viceMonListName
import prog8.codegen.target.cbm.Mflpt5
import prog8.compilerinterface.*
import java.io.IOException
import java.nio.file.Path


class C64MachineDefinition: IMachineDefinition {

    override val cpu = CpuType.CPU6502

    override val FLOAT_MAX_POSITIVE = Mflpt5.FLOAT_MAX_POSITIVE
    override val FLOAT_MAX_NEGATIVE = Mflpt5.FLOAT_MAX_NEGATIVE
    override val FLOAT_MEM_SIZE = Mflpt5.FLOAT_MEM_SIZE
    override val PROGRAM_LOAD_ADDRESS = 0x0801u

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    override val ESTACK_LO = 0xce00u     //  $ce00-$ceff inclusive
    override val ESTACK_HI = 0xcf00u     //  $ce00-$ceff inclusive

    override lateinit var zeropage: Zeropage

    override fun getFloat(num: Number) = Mflpt5.fromNumber(num)

    override fun importLibs(compilerOptions: CompilationOptions, compilationTargetName: String): List<String> {
        return if (compilerOptions.launcher == CbmPrgLauncherType.BASIC || compilerOptions.output == OutputType.PRG)
            listOf("syslib")
        else
            emptyList()
    }

    override fun launchEmulator(selectedEmulator: Int, programNameWithPath: Path) {
        if(selectedEmulator!=1) {
            System.err.println("The c64 target only supports the main emulator (Vice).")
            return
        }

        for(emulator in listOf("x64sc", "x64")) {
            println("\nStarting C-64 emulator $emulator...")
            val viceMonlist = viceMonListName(programNameWithPath.toString())
            val cmdline = listOf(emulator, "-silent", "-moncommands", viceMonlist,
                    "-autostartprgmode", "1", "-autostart-warp", "-autostart", "${programNameWithPath}.prg")
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

    override fun isIOAddress(address: UInt): Boolean = address==0u || address==1u || address in 0xd000u..0xdfffu

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = C64Zeropage(compilerOptions)
    }

    override val opcodeNames = normal6502instructions
}


// 6502 opcodes (including aliases and illegal opcodes), these cannot be used as variable or label names
internal val normal6502instructions = setOf(
    "adc", "ahx", "alr", "anc", "and", "ane", "arr", "asl", "asr", "axs", "bcc", "bcs",
    "beq", "bge", "bit", "blt", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc",
    "cld", "cli", "clv", "cmp", "cpx", "cpy", "dcm", "dcp", "dec", "dex", "dey",
    "eor", "gcc", "gcs", "geq", "gge", "glt", "gmi", "gne", "gpl", "gvc", "gvs",
    "inc", "ins", "inx", "iny", "isb", "isc", "jam", "jmp", "jsr", "lae", "las",
    "lax", "lda", "lds", "ldx", "ldy", "lsr", "lxa", "nop", "ora", "pha", "php",
    "pla", "plp", "rla", "rol", "ror", "rra", "rti", "rts", "sax", "sbc", "sbx",
    "sec", "sed", "sei", "sha", "shl", "shr", "shs", "shx", "shy", "slo", "sre",
    "sta", "stx", "sty", "tas", "tax", "tay", "tsx", "txa", "txs", "tya", "xaa")