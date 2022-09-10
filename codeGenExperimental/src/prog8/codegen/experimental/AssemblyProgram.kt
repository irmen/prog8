package prog8.codegen.experimental

import prog8.code.core.AssemblyError
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import prog8.intermediate.*
import java.io.BufferedWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

class AssemblyProgram(override val name: String, val irProgram: IRProgram): IAssemblyProgram {

    // TODO once this is working, replace the codeGenVirtual by codeGenExperimental
    // after that,

    override fun assemble(options: CompilationOptions): Boolean {
        val outfile = options.outputDir / ("$name.p8virt")
        println("write code to $outfile")

        // at last, allocate the variables in memory.
        val allocations = VariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)

        outfile.bufferedWriter().use { out ->
            allocations.asVmMemory().forEach { (name, alloc) ->
                out.write("; ${name.joinToString(".")}\n")
                out.write(alloc + "\n")
            }
//            with(irProgram.st) {
//                allVariables.forEach {
//                    //TODO("var $it")
//                }
//                allMemMappedVariables.forEach {
//                    println("MAPPED ${it.name} ${it.address}")
//                    // TODO implement proper memory mapped variable in VM - for now put them as regular variable to get it to compile
//
//                }
//                allMemorySlabs.forEach {
//                    TODO("implement proper memory slab allocation in VM")
//                }
//            }

            out.write("------PROGRAM------\n")

            if(!options.dontReinitGlobals) {
                out.write("; global var inits\n")
                irProgram.globalInits.forEach { out.writeLine(it) }
            }
            irProgram.blocks.firstOrNull()?.let {
                if(it.subroutines.any { it.name=="main.start" }) {
                    // there is a "main.start" entrypoint, jump to it
                    out.writeLine(IRCodeInstruction(Opcode.JUMP, labelSymbol = "main.start"))
                }
            }

            out.write("; actual program code\n")

            irProgram.blocks.forEach { block ->
                if(block.address!=null)
                    TODO("blocks can't have a load address for vm")
                out.write("; BLOCK ${block.name} ${block.position}\n")
                block.inlineAssembly.forEach { asm ->
                    out.write("; ASM ${asm.position}\n")
                    out.write(asm.asm)
                    out.write("\n")
                }
                block.subroutines.forEach { sub ->
                    out.write("; SUB ${sub.name} ${sub.position}\n")
                    out.write("_${sub.name}:\n")
                    sub.chunks.forEach { chunk ->
                        if(chunk is IRInlineAsmChunk) {
                            out.write("; ASM ${chunk.position}\n${chunk.asm}\n")
                        } else {
                            chunk.lines.forEach { out.writeLine(it) }
                        }
                    }
                    out.write("; END SUB ${sub.name}\n")
                }
                block.asmSubroutines.forEach { sub ->
                    out.write("; ASMSUB ${sub.name} ${sub.position}\n")
                    out.write("_${sub.name}:\n")
                    out.write(sub.assembly)
                    out.write("\n; END ASMSUB ${sub.name}\n")
                }
                out.write("; END BLOCK ${block.name}\n")
            }
        }
        return true
    }
}

private fun BufferedWriter.writeLine(line: IRCodeLine) {
    when(line) {
        is IRCodeComment -> {
            write("; ${line.comment}\n")
        }
        is IRCodeInstruction -> {
            write(line.ins.toString() + "\n")
        }
        is IRCodeInlineBinary -> {
            write("!binary ")
            line.data.withIndex().forEach {(index, byte) ->
                write(byte.toString(16).padStart(2,'0'))
                if(index and 63 == 63 && index<line.data.size-1)
                    write("\n!binary ")
            }
            write("\n")
        }
        is IRCodeLabel -> {
            write("_${line.name}:\n")
        }
        else -> throw AssemblyError("invalid IR code line")
    }
}
