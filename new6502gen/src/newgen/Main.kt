package newgen

import codegen.CodeGenerator
import codegen.PythonCodeGenerator
import prog8.code.core.CpuType
import prog8.intermediate.IRFileReader
import kotlin.io.path.Path
import kotlin.io.path.readText

fun main() {

    val reader = IRFileReader()
    val source = Path("test.p8ir").readText()
    val program = reader.read(source)
    val target = program.options.compTarget
    println("Loaded IR proram: ${program.name}")
    println("Target system and CPU: ${target.name} / ${target.cpu}")
    
    val gen = when(target.cpu) {
        CpuType.CPU6502, CpuType.CPU65C02 -> CodeGenerator(program, target.cpu)
        CpuType.VIRTUAL -> {
//            println("This code generator only works for 6502 and 65C02 CPUs.")
//            exitProcess(1)
            PythonCodeGenerator(program)
        }
    }
    
    gen.generate()
}
