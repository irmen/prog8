package il65.compiler

import il65.ast.INameScope
import il65.ast.Module
import java.nio.ByteBuffer
import java.nio.ByteOrder
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


fun Double.frexp(): Pair<Double, Int> {
    var exponent: Int = Math.getExponent(this)
    val mantissa: Double

    when (exponent) {
        1024
        -> {
            // Inf or NaN
            mantissa = this
            exponent = 0
        }

        -1023 -> if (this == 0.0) {
            // -0.0 or 0.0
            mantissa = this
            exponent = 0
        } else {
            exponent = Math.getExponent(this * 0x10000000000000) - 51     // that is 0x1p52 == 2**52
            mantissa = Math.scalb(this, -exponent)
        }

        else -> {
            exponent++
            mantissa = Math.scalb(this, -exponent)
        }
    }
    return Pair(mantissa, exponent)
}

fun Number.toMflpt5(): ShortArray {
    // algorithm here https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a

    // @todo fix this

    var flt = this.toDouble()
    if(flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
        throw CompilerException("floating point number out of 5-byte mflpt range: $this")
    if(flt==0.0)
        return shortArrayOf(0, 0, 0, 0, 0)
    val sign: Long =
            when {
                flt < 0.0 -> {
                    flt = -flt
                    0x80000000L
                }
                else -> 0x00000000L
            }

    var (mant, exp) = flt.frexp()
    exp += 128
    if(exp < 1) {
        // underflow, use zero instead
        return shortArrayOf(0, 0, 0 ,0 ,0)
    }
    if(exp > 255) {
        throw CompilerException("floating point number out of 5-byte mflpt range: $this")
    }
    val mflpt = sign.or((mant * 0x100000000L).toLong()).and(0x7fffffffL)


    val result = ShortArray(5)
    result[0] = exp.toShort()
    result[1] = (mflpt and -0x1000000 shr 24).toShort()
    result[2] = (mflpt and 0x00FF0000 shr 16).toShort()
    result[3] = (mflpt and 0x0000FF00 shr 8).toShort()
    result[4] = (mflpt and 0x000000FF shr 0).toShort()
    return result
    /*

    mant, exp = math.frexp(number)
    exp += 128
    if exp < 1:
        # underflow, use zero instead
        return bytearray([0, 0, 0, 0, 0])
    if exp > 255:
        raise OverflowError("floating point number out of 5-byte mflpt range", number)
    mant = sign | int(mant * 0x100000000) & 0x7fffffff
    return bytearray([exp]) + int.to_bytes(mant, 4, "big")

     */
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

    fun genereateBreakpointList(): String {
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