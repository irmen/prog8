package prog8.codegen.m68k

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cli = ArgParser("prog8-m68kgen", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val inputFile by cli.argument(ArgType.String, fullName = "input", description = "path to .p8ir file")
    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        println(e.message)
        exitProcess(1)
    }

    val reader = IRFileReader()
    val source = Path(inputFile).readText()
    val program = reader.read(source)
    val target = program.options.compTarget
    println("Loaded IR program: ${program.name}")
    println("Target system and CPU: ${target.name} / ${target.cpu}")

    when(target.cpu) {
        CpuType.M68000, CpuType.M68020 -> {
            val gen = AsmGen(program, target)
            gen.generate()
            println("Generated assembly: ${program.name}.asm")
        }
        else -> {
            error("This code generator only works for M68000 or M68020 CPU.")
        }
    }
}
