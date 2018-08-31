package il65.compiler

import il65.ast.INameScope
import il65.ast.Module
import kotlin.experimental.and
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.system.exitProcess


class CompilerException(message: String?) : Exception(message)

// 5-byte cbm MFLPT format limitations:
const val FLOAT_MAX_POSITIVE = 1.7014118345e+38
const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38


fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    val integer = this.toInt()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
}


data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short){

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
                    val mant_long = mantissa.toLong()
                    Mflpt5(
                            exponent.toShort(),
                            (mant_long.and(0x7f000000L) ushr 24).or(sign).toShort(),
                            (mant_long.and(0x00ff0000L) ushr 16).toShort(),
                            (mant_long.and(0x0000ff00L) ushr 8).toShort(),
                            (mant_long.and(0x000000ffL)).toShort())
                }
            }
        }
    }

    fun toDouble(): Double {
        if(this == zero) return 0.0
        val exp = b0 - 128
        val sign = (b1.and(0x80)) > 0
        val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
        val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
        return if(sign) -result else result
    }
}


class Compiler(val options: CompilationOptions, val namespace: INameScope) {
    init {
        val zeropage = Zeropage(options)
    }

    fun compile(module: Module) : IntermediateForm {
        println("......@TODO compile Ast into Intermediate result......")       // todo
        return IntermediateForm(module.name)
    }
}


class IntermediateForm(val name: String) {
    fun optimize() {
        println("......@TODO optimize intermediate result......")       // todo
    }

    fun compileToAssembly(): AssemblyResult {
        println("......@TODO compile intermediate result to assembly code......")       // todo
        return AssemblyResult(name)
    }

}

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    COMPATIBLE,
    FULL,
    FULL_RESTORE
}


data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val floats: Boolean)


class AssemblyResult(val name: String) {
    fun assemble(options: CompilationOptions, inputfilename: String, outputfilename: String) {
        println("......@TODO assemble with 64tass......")       // todo

        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "-Wall", "-Wno-strict-bool",
            "--dump-labels", "--vice-labels", "-l", outputfilename+".vice-mon-list",
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
        // todo build breakpoint list!
/*
    def generate_breakpoint_list(self, program_filename: str) -> str:
        breakpoints = []
        vice_mon_file = program_filename + ".vice-mon-list"
        with open(vice_mon_file, "rU") as f:
            for line in f:
                match = re.fullmatch(r"al (?P<address>\w+) \S+_il65_breakpoint_\d+.?", line, re.DOTALL)
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