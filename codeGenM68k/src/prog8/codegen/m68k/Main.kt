package prog8.codegen.m68k

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val cli = M68kCli()
    cli.main(args)
    val inputFile = cli.inputFile

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

private class M68kCli : CliktCommand(name = "prog8-m68kgen") {
    val inputFile by argument(help = "path to .p8ir")

    override fun run() = Unit
}
