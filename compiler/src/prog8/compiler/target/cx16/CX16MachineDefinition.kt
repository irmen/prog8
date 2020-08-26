package prog8.compiler.target.cx16

import prog8.ast.Program
import prog8.compiler.*
import prog8.compiler.target.IMachineDefinition
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.parser.ModuleImporter
import java.io.IOException

internal object CX16MachineDefinition: IMachineDefinition {

    override val cpu = "65c02"

    // 5-byte cbm MFLPT format limitations:
    override val FLOAT_MAX_POSITIVE = 1.7014118345e+38         // bytes: 255,127,255,255,255
    override val FLOAT_MAX_NEGATIVE = -1.7014118345e+38        // bytes: 255,255,255,255,255
    override val FLOAT_MEM_SIZE = 5
    override val POINTER_MEM_SIZE = 2
    override val BASIC_LOAD_ADDRESS = 0x0801
    override val RAW_LOAD_ADDRESS = 0x8000

    // the 2*256 byte evaluation stack (on which bytes, words, and even floats are stored during calculations)
    // and some heavily used string constants derived from the two values above
    override val ESTACK_LO = 0x0400        //  $0400-$04ff inclusive
    override val ESTACK_HI = 0x0500        //  $0500-$05ff inclusive

    override lateinit var zeropage: Zeropage
    override val initSystemProcname = "cx16.init_system"

    override fun getFloat(num: Number) = C64MachineDefinition.Mflpt5.fromNumber(num)

    override fun getFloatRomConst(number: Double): String? = null       // TODO Does Cx16 have ROM float locations?
    override fun importLibs(compilerOptions: CompilationOptions, importer: ModuleImporter, program: Program) {
        // if we're producing a PRG or BASIC program, include the cx16utils and cx16lib libraries
        if (compilerOptions.launcher == LauncherType.BASIC || compilerOptions.output == OutputType.PRG) {
            importer.importLibraryModule(program, "cx16lib")
            importer.importLibraryModule(program, "cx16utils")
        }
    }

    override fun launchEmulator(programName: String) {
        for(emulator in listOf("x16emu")) {
            println("\nStarting Commander X16 emulator $emulator...")
            val cmdline = listOf(emulator, "-rom", "/usr/share/x16-rom/rom.bin", "-scale", "2",
                    "-run", "-prg", programName + ".prg")
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

    override fun initializeZeropage(compilerOptions: CompilationOptions) {
        zeropage = CX16Zeropage(compilerOptions)
    }

    // 6502 opcodes (including aliases and illegal opcodes), these cannot be used as variable or label names
    // TODO add 65C02 opcodes
    override val opcodeNames = setOf("adc", "ahx", "alr", "anc", "and", "ane", "arr", "asl", "asr", "axs", "bcc", "bcs",
            "beq", "bge", "bit", "blt", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc",
            "cld", "cli", "clv", "cmp", "cpx", "cpy", "dcm", "dcp", "dec", "dex", "dey",
            "eor", "gcc", "gcs", "geq", "gge", "glt", "gmi", "gne", "gpl", "gvc", "gvs",
            "inc", "ins", "inx", "iny", "isb", "isc", "jam", "jmp", "jsr", "lae", "las",
            "lax", "lda", "lds", "ldx", "ldy", "lsr", "lxa", "nop", "ora", "pha", "php",
            "pla", "plp", "rla", "rol", "ror", "rra", "rti", "rts", "sax", "sbc", "sbx",
            "sec", "sed", "sei", "sha", "shl", "shr", "shs", "shx", "shy", "slo", "sre",
            "sta", "stx", "sty", "tas", "tax", "tay", "tsx", "txa", "txs", "tya", "xaa")


    internal class CX16Zeropage(options: CompilationOptions) : Zeropage(options) {

        override val SCRATCH_B1 = 0x79      // temp storage for a single byte
        override val SCRATCH_REG = 0x7a     // temp storage for a register
        override val SCRATCH_REG_X = 0x7b   // temp storage for register X (the evaluation stack pointer)
        override val SCRATCH_W1 = 0x7c      // temp storage 1 for a word  $7c+$7d
        override val SCRATCH_W2 = 0x7e      // temp storage 2 for a word  $7e+$7f


        override val exitProgramStrategy: ExitProgramStrategy = when (options.zeropage) {
            ZeropageType.BASICSAFE, ZeropageType.DONTUSE -> ExitProgramStrategy.CLEAN_EXIT
            ZeropageType.KERNALSAFE, ZeropageType.FULL -> ExitProgramStrategy.SYSTEM_RESET
            else -> ExitProgramStrategy.SYSTEM_RESET
        }


        init {
            if (options.floats && options.zeropage !in setOf(ZeropageType.BASICSAFE, ZeropageType.DONTUSE ))
                throw CompilerException("when floats are enabled, zero page type should be 'basicsafe' or 'dontuse'")

            // the addresses 0x02 to 0x21 (inclusive) are taken for sixteen virtual 16-bit api registers.

            when (options.zeropage) {
                ZeropageType.FULL -> {
                    free.addAll(0x22..0xff)
                    free.removeAll(listOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_REG_X, SCRATCH_W1, SCRATCH_W1 + 1, SCRATCH_W2, SCRATCH_W2 + 1))
                }
                ZeropageType.KERNALSAFE -> {
                    free.addAll(0x22..0x7f)
                    free.addAll(0xa9..0xff)
                    free.removeAll(listOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_REG_X, SCRATCH_W1, SCRATCH_W1 + 1, SCRATCH_W2, SCRATCH_W2 + 1))
                }
                ZeropageType.BASICSAFE -> {
                    free.addAll(0x22..0x7f)
                    free.removeAll(listOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_REG_X, SCRATCH_W1, SCRATCH_W1 + 1, SCRATCH_W2, SCRATCH_W2 + 1))
                }
                ZeropageType.DONTUSE -> {
                    free.clear() // don't use zeropage at all
                }
                else -> throw CompilerException("for this machine target, zero page type 'floatsafe' is not available. ${options.zeropage}")
            }

            require(SCRATCH_B1 !in free)
            require(SCRATCH_REG !in free)
            require(SCRATCH_REG_X !in free)
            require(SCRATCH_W1 !in free)
            require(SCRATCH_W2 !in free)

            for (reserved in options.zpReserved)
                reserve(reserved)
        }
    }
}
