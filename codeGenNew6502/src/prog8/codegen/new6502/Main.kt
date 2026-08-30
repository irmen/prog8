package prog8.codegen.new6502

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cli = New6502Cli()
    cli.main(args)
    val inputFile = cli.inputFile
    val asmListfile = cli.asmListfile

    val reader = IRFileReader()
    val source = Path(inputFile).readText()
    val program = reader.read(source)
    if(asmListfile == true)
        program.options.asmListfile = true
    val target = program.options.compTarget

    // compute metrics (matching IRFileWriter counting)
    val used = program.registersUsed()
    val numRegs = (used.readRegs.keys + used.writeRegs.keys).size + (used.readFpRegs.keys + used.writeFpRegs.keys).size
    var numChunks = 0
    var numInstr = 0
    for (block in program.blocks) {
        for (child in block.children) {
            when (child) {
                is prog8.intermediate.IRSubroutine -> {
                    for (chunk in child.chunks) {
                        numChunks++
                        if (chunk is prog8.intermediate.IRCodeChunk)
                            numInstr += chunk.instructions.size
                    }
                }
                is prog8.intermediate.IRCodeChunk -> {
                    // counted in numInstr but NOT in numChunks (matches IRFileWriter)
                    numInstr += child.instructions.size
                }
                else -> {}
            }
        }
    }
    // globalInits: counted in numInstr but NOT in numChunks (matches IRFileWriter)
    numInstr += program.globalInits.instructions.size
    System.err.println("Loaded IR program: ${program.name}")
    System.err.println("Target system and CPU: ${target.name} / ${target.cpu}")
    System.err.println("($numInstr instructions in $numChunks chunks, $numRegs registers)")

    val gen = when(target.cpu) {
        CpuType.CPU6502, CpuType.CPU65C02 -> AsmGen(program, target, StderrErrorReporter())
        else -> {
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

private class New6502Cli : CliktCommand(name = "prog8-newgen") {
    val inputFile by argument(help = "path to .p8ir")
    val asmListfile by option("-l", "-list", "--list", help = "produce assembler listing file (.list)").flag()

    override fun run() = Unit
}
