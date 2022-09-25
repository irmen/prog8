package prog8.codegen.virtual

import prog8.code.core.AssemblyError
import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import prog8.intermediate.*
import prog8.vm.Syscall
import java.io.BufferedWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

internal class VmAssemblyProgram(override val name: String, private val irProgram: IRProgram): IAssemblyProgram {

    override fun assemble(dummyOptions: CompilationOptions): Boolean {
        val outfile = irProgram.options.outputDir / ("$name.p8virt")
        println("write code to $outfile")

        // at last, allocate the variables in memory.
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)

        outfile.bufferedWriter().use { out ->
            allocations.asVmMemory().forEach { (name, alloc) ->
                out.write("var ${name} $alloc\n")
            }

            out.write("------PROGRAM------\n")

            if(!irProgram.options.dontReinitGlobals) {
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
                    out.write(asm.assembly)
                    out.write("\n")
                }
                block.subroutines.forEach { sub ->
                    out.write("; SUB ${sub.name} ${sub.position}\n")
                    out.write("_${sub.name}:\n")
                    sub.chunks.forEach { chunk ->
                        if(chunk is IRInlineAsmChunk) {
                            out.write("; ASM ${chunk.position}\n")
                            out.write(processInlinedAsm(chunk.assembly, allocations))
                            out.write("\n")
                        } else {
                            chunk.lines.forEach { out.writeLine(it) }
                        }
                    }
                    out.write("; END SUB ${sub.name}\n")
                }
                block.asmSubroutines.forEach { sub ->
                    out.write("; ASMSUB ${sub.name} ${sub.position}\n")
                    out.write("_${sub.name}:\n")
                    out.write(processInlinedAsm(sub.assembly, allocations))
                    out.write("\n; END ASMSUB ${sub.name}\n")
                }
                out.write("; END BLOCK ${block.name}\n")
            }
        }
        return true
    }

    private fun processInlinedAsm(asm: String, allocations: VmVariableAllocator): String {
        // TODO do we have to replace variable names by their allocated address???
        return asm
    }
}

private fun BufferedWriter.writeLine(line: IRCodeLine) {
    when(line) {
        is IRCodeComment -> {
            write("; ${line.comment}\n")
        }
        is IRCodeInstruction -> {
            if(line.ins.opcode==Opcode.SYSCALL) {
                // convert IM Syscall to VM Syscall
                val vmSyscall = when(line.ins.value!!) {
                    IMSyscall.SORT_UBYTE.ordinal -> Syscall.SORT_UBYTE
                    IMSyscall.SORT_BYTE.ordinal -> Syscall.SORT_BYTE
                    IMSyscall.SORT_UWORD.ordinal -> Syscall.SORT_UWORD
                    IMSyscall.SORT_WORD.ordinal -> Syscall.SORT_WORD
                    IMSyscall.ANY_BYTE.ordinal -> Syscall.ANY_BYTE
                    IMSyscall.ANY_WORD.ordinal -> Syscall.ANY_WORD
                    IMSyscall.ANY_FLOAT.ordinal -> Syscall.ANY_FLOAT
                    IMSyscall.ALL_BYTE.ordinal -> Syscall.ALL_BYTE
                    IMSyscall.ALL_WORD.ordinal -> Syscall.ALL_WORD
                    IMSyscall.ALL_FLOAT.ordinal -> Syscall.ALL_FLOAT
                    IMSyscall.REVERSE_BYTES.ordinal -> Syscall.REVERSE_BYTES
                    IMSyscall.REVERSE_WORDS.ordinal -> Syscall.REVERSE_WORDS
                    IMSyscall.REVERSE_FLOATS.ordinal -> Syscall.REVERSE_FLOATS
                    else -> throw IllegalArgumentException("invalid IM syscall number ${line.ins.value}")
                }
                val newIns = line.ins.copy(value = vmSyscall.ordinal)
                write(newIns.toString() + "\n")
            } else
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
