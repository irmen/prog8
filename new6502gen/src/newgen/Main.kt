package newgen

import codegen.CodeGenerator
import kotlinx.cli.*
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cli = ArgParser("prog8-newgen", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
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
    System.err.println("Loaded IR program: ${program.name}")
    System.err.println("Target system and CPU: ${target.name} / ${target.cpu}")
    
    val gen = when(target.cpu) {
        CpuType.CPU6502, CpuType.CPU65C02 -> CodeGenerator(program, target)
        CpuType.VIRTUAL -> {
            println("This code generator only works for 6502 and 65C02 CPUs.")
            exitProcess(1)
        }
    }
    
    val ok = gen.generate()
    if(!ok) {
        System.err.println("Assembly failed.")
        exitProcess(1)
    }
}
