package prog8.codegen.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.OpcodesWithAddress
import prog8.vm.VmDataType
import java.io.BufferedWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


internal class AssemblyProgram(override val name: String,
                               private val allocations: VariableAllocator
) : IAssemblyProgram {

    private val globalInits = mutableListOf<VmCodeLine>()
    private val blocks = mutableListOf<VmCodeChunk>()

    override fun assemble(options: CompilationOptions): Boolean {
        val outfile = options.outputDir / ("$name.p8virt")
        println("write code to $outfile")
        outfile.bufferedWriter().use { out ->
            allocations.asVmMemory().forEach { (name, alloc) ->
                out.write("; ${name.joinToString(".")}\n")
                out.write(alloc + "\n")
            }
            out.write("------PROGRAM------\n")

            if(!options.dontReinitGlobals) {
                out.write("; global var inits\n")
                globalInits.forEach { out.writeLine(it) }
            }

            out.write("; actual program code\n")
            blocks.asSequence().flatMap { it.lines }.forEach { line->out.writeLine(line) }
        }
        return true
    }

    private fun BufferedWriter.writeLine(line: VmCodeLine) {
        when(line) {
            is VmCodeComment -> write("; ${line.comment}\n")
            is VmCodeInstruction -> {
                write(line.ins.toString() + "\n")
            }
            is VmCodeLabel -> write("_" + line.name.joinToString(".") + ":\n")
        }
    }

    fun addGlobalInits(chunk: VmCodeChunk) = globalInits.addAll(chunk.lines)
    fun addBlock(block: VmCodeChunk) = blocks.add(block)
}

internal sealed class VmCodeLine

internal class VmCodeInstruction(
    opcode: Opcode,
    type: VmDataType?=null,
    reg1: Int?=null,        // 0-$ffff
    reg2: Int?=null,        // 0-$ffff
    reg3: Int?=null,        // 0-$ffff
    fpReg1: Int?=null,      // 0-$ffff
    fpReg2: Int?=null,      // 0-$ffff
    fpReg3: Int?=null,      // 0-$ffff
    value: Int?=null,       // 0-$ffff
    fpValue: Float?=null,
    symbol: List<String>?=null    // alternative to value
    ): VmCodeLine() {
        val ins = Instruction(opcode, type, reg1, reg2, reg3, fpReg1, fpReg2, fpReg3, value, fpValue, symbol)

        init {
            if(reg1!=null && (reg1<0 || reg1>65536))
                throw IllegalArgumentException("reg1 out of bounds")
            if(reg2!=null && (reg2<0 || reg2>65536))
                throw IllegalArgumentException("reg2 out of bounds")
            if(reg3!=null && (reg3<0 || reg3>65536))
                throw IllegalArgumentException("reg3 out of bounds")
            if(fpReg1!=null && (fpReg1<0 || fpReg1>65536))
                throw IllegalArgumentException("fpReg1 out of bounds")
            if(fpReg2!=null && (fpReg2<0 || fpReg2>65536))
                throw IllegalArgumentException("fpReg2 out of bounds")
            if(fpReg3!=null && (fpReg3<0 || fpReg3>65536))
                throw IllegalArgumentException("fpReg3 out of bounds")
            
            if(value!=null && opcode !in OpcodesWithAddress) {
                when (type) {
                    VmDataType.BYTE -> {
                        if (value < -128 || value > 255)
                            throw IllegalArgumentException("value out of range for byte: $value")
                    }
                    VmDataType.WORD -> {
                        if (value < -32768 || value > 65535)
                            throw IllegalArgumentException("value out of range for word: $value")
                    }
                    VmDataType.FLOAT, null -> {}
                }
            }
        }
    }

internal class VmCodeLabel(val name: List<String>): VmCodeLine()
internal class VmCodeComment(val comment: String): VmCodeLine()

internal class VmCodeChunk(initial: VmCodeLine? = null) {
    val lines = mutableListOf<VmCodeLine>()

    init {
        if(initial!=null)
            lines.add(initial)
    }

    operator fun plusAssign(line: VmCodeLine) {
        lines.add(line)
    }

    operator fun plusAssign(chunk: VmCodeChunk) {
        lines.addAll(chunk.lines)
    }
}