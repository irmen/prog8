package prog8.codegen.new6502

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cli = ArgParser("prog8-newgen", prefixStyle = ArgParser.OptionPrefixStyle.JVM)
    val inputFile by cli.argument(ArgType.String, fullName = "input", description = "path to .p8ir file")
    val asmListfile by cli.option(ArgType.Boolean, fullName = "list", shortName = "l", description = "produce assembler listing file (.list)")
    try {
        cli.parse(args)
    } catch (e: IllegalStateException) {
        println(e.message)
        exitProcess(1)
    }

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
        CpuType.CPU6502, CpuType.CPU65C02 -> CodeGenerator(program, target)
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
