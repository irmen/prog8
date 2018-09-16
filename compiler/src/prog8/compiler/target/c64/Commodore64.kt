package prog8.compiler.target.c64

import prog8.compiler.*
import prog8.stackvm.Program
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.system.exitProcess


// 5-byte cbm MFLPT format limitations:
const val FLOAT_MAX_POSITIVE = 1.7014118345e+38
const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38



class C64Zeropage(options: CompilationOptions) : Zeropage(options) {

    companion object {
        const val SCRATCH_B1 = 0x02
        const val SCRATCH_B2 = 0x03
        const val SCRATCH_W1 = 0xfb     // $fb/$fc
        const val SCRATCH_W2 = 0xfd     // $fd/$fe
    }

    init {
        if(options.zeropage== ZeropageType.FULL) {
            free.addAll(0x04 .. 0xfa)
            free.add(0xff)
            free.removeAll(listOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
        } else {
            if(options.zeropage== ZeropageType.KERNALSAFE) {
                // add the Zp addresses that are just used by BASIC routines to the free list
                free.addAll(listOf(0x09, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11,
                        0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21,
                        0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                        0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x53, 0x6f, 0x70))
            }
            // add the Zp addresses not even used by BASIC
            // these are valid for the C-64 (when no RS232 I/O is performed):
            // ($02, $03, $fb-$fc, $fd-$fe are reserved as scratch addresses for various routines)
            // KNOWN WORKING FREE: 0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa))
            free.addAll(listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0d, 0x0e,
                    0x12, 0x2a, 0x52, 0x94, 0x95, 0xa7, 0xa8, 0xa9, 0xaa,
                    0xb5, 0xb6, 0xf7, 0xf8, 0xf9, 0xfa))
        }
        assert(!free.contains(SCRATCH_B1))
        assert(!free.contains(SCRATCH_B2))
        assert(!free.contains(SCRATCH_W1))
        assert(!free.contains(SCRATCH_W2))
    }
}


data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short) {

    companion object {
        val zero = Mflpt5(0, 0,0,0,0)
        fun fromNumber(num: Number): Mflpt5 {
            // see https://en.wikipedia.org/wiki/Microsoft_Binary_Format
            // and https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
            // and https://en.wikipedia.org/wiki/IEEE_754-1985

            val flt = num.toDouble()
            if(flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
                throw CompilerException("floating point number out of 5-byte mflpt range: $this")
            if(flt==0.0)
                return zero

            val sign = if(flt<0.0) 0x80L else 0x00L
            var exponent = 128 + 32	// 128 is cbm's bias, 32 is this algo's bias
            var mantissa = flt.absoluteValue

            // if mantissa is too large, shift right and adjust exponent
            while(mantissa >= 0x100000000) {
                mantissa /= 2.0
                exponent ++
            }
            // if mantissa is too small, shift left and adjust exponent
            while(mantissa < 0x80000000) {
                mantissa *= 2.0
                exponent --
            }

            return when {
                exponent<0 -> zero  // underflow, use zero instead
                exponent>255 -> throw CompilerException("floating point overflow: $this")
                exponent==0 -> zero
                else -> {
                    val mantLong = mantissa.toLong()
                    Mflpt5(
                            exponent.toShort(),
                            (mantLong.and(0x7f000000L) ushr 24).or(sign).toShort(),
                            (mantLong.and(0x00ff0000L) ushr 16).toShort(),
                            (mantLong.and(0x0000ff00L) ushr 8).toShort(),
                            (mantLong.and(0x000000ffL)).toShort())
                }
            }
        }
    }

    fun toDouble(): Double {
        if(this == zero) return 0.0
        val exp = b0 - 128
        val sign = (b1.toInt() and 0x80) > 0
        val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
        val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
        return if(sign) -result else result
    }
}


fun compileToAssembly(program: Program): AssemblyResult {
    println("\nGenerating assembly code from stackvmProg code... ")
    // todo generate 6502 assembly
    return AssemblyResult(program.name)
}


class AssemblyResult(val name: String) {
    fun assemble(options: CompilationOptions, inputfilename: String, outputfilename: String) {
        println("\nGenerating machine code program...")

        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "-Wall", "-Wno-strict-bool",
                "--dump-labels", "--vice-labels", "-l", "$outputfilename.vice-mon-list",
                "--no-monitor", "--output", outputfilename, inputfilename)

        when(options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating C-64 prg.")
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary.")
            }
        }

        val proc = ProcessBuilder(command).inheritIO().start()
        val result = proc.waitFor()
        if(result!=0) {
            System.err.println("assembler failed with returncode $result")
            exitProcess(result)
        }
    }

    fun generateBreakpointList(): String {
        // todo build breakpoint list
/*
    def generate_breakpoint_list(self, program_filename: str) -> str:
        breakpoints = []
        vice_mon_file = program_filename + ".vice-mon-list"
        with open(vice_mon_file, "rU") as f:
            for line in f:
                match = re.fullmatch(r"al (?P<address>\w+) \S+_prog8_breakpoint_\d+.?", line, re.DOTALL)
                if match:
                    breakpoints.append("$" + match.group("address"))
        with open(vice_mon_file, "at") as f:
            print("; vice monitor breakpoint list now follows", file=f)
            print("; {:d} breakpoints have been defined here".format(len(breakpoints)), file=f)
            print("del", file=f)
            for b in breakpoints:
                print("break", b, file=f)
        return vice_mon_file
 */
        return "monitorfile.txt"
    }
}