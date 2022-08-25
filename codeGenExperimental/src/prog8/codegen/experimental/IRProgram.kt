package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.ast.PtBlock
import prog8.code.core.*
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.OpcodesWithAddress
import prog8.vm.VmDataType
import java.io.BufferedWriter
import java.io.Writer
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

// TODO: move this Intermedate Representation into the actual compiler core, code gen modules can receive it as input rather than an Ast.

class IRProgram(val name: String,
                private val options: CompilationOptions,
                private val encoding: IStringEncoding,
                private val st: SymbolTable) {

    val globalInits = mutableListOf<VmCodeLine>()
    val blocks = mutableListOf<VmBlock>()

    fun writeFile() {
        val outfile = options.outputDir / ("$name.p8ir")
        println("Writing intermediate representation to $outfile")
        outfile.bufferedWriter().use { out ->

            out.write("; PROGRAM '$name'\n")
            writeOptions(out)
            writeVariableAllocations(out)

            if(!options.dontReinitGlobals) {
                // note: this a block of code that loads values and stores them into the global variables to reset their values.
                out.write("\n<INITGLOBALS>\n")
                globalInits.forEach { out.writeLine(it) }
                out.write("</INITGLOBALS>\n")
            }

            out.write("\n<PROGRAM>\n")
            writeBlocks(out)
            out.write("</PROGRAM>\n")
        }
    }

    private fun writeBlocks(out: BufferedWriter) {
        blocks.forEach { block ->
            out.write("\n<BLOCK NAME=${block.name} ADDRESS=${block.address} ALIGN=${block.alignment} POS=${block.position}>\n")
            block.children.forEach {
                when(it) {
                    is VmSubroutine -> {
                        out.write("<SUB ${it.scopedName.joinToString(".")} returntype=${it.returnType} POS=${it.position}>\n")
                        it.lines.forEach { line -> out.writeLine(line) }
                        out.write("</SUB>\n")
                    }
                    is VmAsmSubroutine -> {
                        out.write("<ASMSUB ${it.scopedName.joinToString(".")} POS=${it.position}>\n")
                        it.lines.forEach { line -> out.writeLine(line) }
                        out.write("</ASMSUB>\n")
                    }
                    is VmInlineAsmChunk -> {
                        out.write("<INLINEASM POS=${it.position}>\n")
                        it.lines.forEach { line -> out.writeLine(line) }
                        out.write("</INLINEASM>\n")
                    }
                    is VmCodeChunk -> {
                        println("GENERIC CHUNK IN BLOCK $it ${it.position}")   // TODO must all be VmSubroutine
                        it.lines.forEach { line -> out.writeLine(line) }
                    }
                    else -> {
                        TODO("BLOCK CHILD $it")
                    }
                }
            }
            out.write("</BLOCK>\n")
        }
    }

    private fun writeOptions(out: BufferedWriter) {
        out.write("<OPTIONS>\n")
        out.write("compTarget = ${options.compTarget.name}\n")
        out.write("output = ${options.output}\n")
        out.write("launcher = ${options.launcher}\n")
        out.write("zeropage = ${options.zeropage}\n")
        out.write("zpReserved = ${options.zpReserved}\n")
        out.write("loadAddress = ${options.loadAddress}\n")
        out.write("dontReinitGlobals = ${options.dontReinitGlobals}\n")
        out.write("evalStackBaseAddress = ${options.evalStackBaseAddress}\n")
        // other options not yet useful here?
        out.write("</OPTIONS>\n")
    }

    private fun writeVariableAllocations(out: Writer) {
        out.write("\n<VARIABLES>\n")
        for (variable in st.allVariables) {
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            val value = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue ?: 0).toHex()
                DataType.STR -> {
                    val encoded = encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
                    encoded.joinToString(",") { it.toInt().toHex() }
                }
                DataType.ARRAY_F -> {
                    if(variable.onetimeInitializationArrayValue!=null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toString() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                in ArrayDatatypes -> {
                    if(variable.onetimeInitializationArrayValue!==null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toHex() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            // TODO have uninitialized variables? (BSS SECTION)
            out.write("VAR ${variable.scopedName.joinToString(".")} $typeStr = $value\n")
        }
        out.write("</VARIABLES>\n")

        out.write("\n<MEMORYMAPPEDVARIABLES>\n")
        for (variable in st.allMemMappedVariables) {
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            out.write("MAP ${variable.scopedName.joinToString(".")} $typeStr ${variable.address}\n")
        }
        out.write("</MEMORYMAPPEDVARIABLES>\n")

        out.write("\n<MEMORYSLABS>\n")
        st.allMemorySlabs.forEach{ slab -> out.write("SLAB _${slab.name} ${slab.size} ${slab.align}\n") }
        out.write("</MEMORYSLABS>\n")
    }

    private fun BufferedWriter.writeLine(line: VmCodeLine) {
        when(line) {
            is VmCodeComment -> write("; ${line.comment}\n")
            is VmCodeInstruction -> {
                write(line.ins.toString() + "\n")
            }
            is VmCodeLabel -> write("_" + line.name.joinToString(".") + ":\n")
            is VmInlineAsm -> {
                // TODO FIXUP ASM SYMBOLS???
                write(line.assembly+"\n")
            }
            is VmCodeInlineBinary -> {
                write("incbin \"${line.file}\"")
                if(line.offset!=null)
                    write(",${line.offset}")
                if(line.length!=null)
                    write(",${line.length}")
                write("\n")
            }
            else -> throw AssemblyError("invalid vm code line")
        }
    }

    fun addGlobalInits(chunk: VmCodeChunk) = globalInits.addAll(chunk.lines)
    fun addBlock(block: VmBlock) = blocks.add(block)
}

sealed class VmCodeLine

class VmCodeInstruction(
    opcode: Opcode,
    type: VmDataType?=null,
    reg1: Int?=null,        // 0-$ffff
    reg2: Int?=null,        // 0-$ffff
    fpReg1: Int?=null,      // 0-$ffff
    fpReg2: Int?=null,      // 0-$ffff
    value: Int?=null,       // 0-$ffff
    fpValue: Float?=null,
    labelSymbol: List<String>?=null    // alternative to value for branch/call/jump labels
): VmCodeLine() {
    val ins = Instruction(opcode, type, reg1, reg2, fpReg1, fpReg2, value, fpValue, labelSymbol)

    init {
        if(reg1!=null && (reg1<0 || reg1>65536))
            throw IllegalArgumentException("reg1 out of bounds")
        if(reg2!=null && (reg2<0 || reg2>65536))
            throw IllegalArgumentException("reg2 out of bounds")
        if(fpReg1!=null && (fpReg1<0 || fpReg1>65536))
            throw IllegalArgumentException("fpReg1 out of bounds")
        if(fpReg2!=null && (fpReg2<0 || fpReg2>65536))
            throw IllegalArgumentException("fpReg2 out of bounds")

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

class VmCodeLabel(val name: List<String>): VmCodeLine()
class VmCodeComment(val comment: String): VmCodeLine()


class VmBlock(
    val name: String,
    val address: UInt?,
    val alignment: PtBlock.BlockAlignment,
    val position: Position
) {
    val children = mutableListOf<VmCodeChunk>()

    operator fun plusAssign(child: VmCodeChunk) {
        children += child
    }
}

class VmSubroutine(val scopedName: List<String>,
                   val returnType: DataType?,
                   position: Position,
                   initial: VmCodeLine? = null): VmCodeChunk(position, initial)

class VmAsmSubroutine(val scopedName: List<String>,
                      position: Position,
                      initial: VmCodeLine? = null): VmCodeChunk(position, initial)

open class VmCodeChunk(val position: Position, initial: VmCodeLine? = null) {
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

class VmInlineAsmChunk(asm: String, position: Position): VmCodeChunk(position, VmInlineAsm(asm))


class VmInlineAsm(asm: String): VmCodeLine() {
    // TODO INLINE ASSEMBLY IN IL CODE
    val assembly: String = "; TODO INLINE ASSEMBLY IN IL CODE" // was:  asm.trimIndent()
}

class VmCodeInlineBinary(val file: Path, val offset: UInt?, val length: UInt?): VmCodeLine()
