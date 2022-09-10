package prog8.intermediate

import prog8.code.StMemVar
import prog8.code.StStaticVariable
import prog8.code.core.*
import java.io.BufferedWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


class IRFileWriter(private val irProgram: IRProgram) {
    private val outfile = irProgram.options.outputDir / ("${irProgram.name}.p8ir")
    private val out = outfile.bufferedWriter()

    fun writeFile() {
        println("Writing intermediate representation to $outfile")
        out.write("<PROGRAM NAME=${irProgram.name}>\n")
        writeOptions()
        writeVariableAllocations()

        out.write("\n<INITGLOBALS>\n")
        if(!irProgram.options.dontReinitGlobals) {
            out.write("<CODE>\n")
            // note: this a block of code that loads values and stores them into the global variables to reset their values.
            irProgram.globalInits.forEach { out.writeLine(it) }
            out.write("</CODE>\n")
        }
        out.write("</INITGLOBALS>\n")
        writeBlocks()
        out.write("</PROGRAM>\n")
        out.close()
    }

    private fun writeBlocks() {
        irProgram.blocks.forEach { block ->
            out.write("\n<BLOCK NAME=${block.name} ADDRESS=${block.address} ALIGN=${block.alignment} POS=${block.position}>\n")
            block.inlineAssembly.forEach {
                writeInlineAsm(it)
            }
            block.subroutines.forEach {
                out.write("<SUB NAME=${it.name} RETURNTYPE=${it.returnType.toString().lowercase()} POS=${it.position}>\n")
                // TODO rest of the signature
                it.chunks.forEach { chunk ->
                    if(chunk is IRInlineAsmChunk) {
                        writeInlineAsm(chunk)
                    } else {
                        out.write("<CODE>\n")
                        if (chunk.lines.isEmpty())
                            throw InternalCompilerException("empty code chunk in ${it.name} ${it.position}")
                        chunk.lines.forEach { line -> out.writeLine(line) }
                        out.write("</CODE>\n")
                    }
                }
                out.write("</SUB>\n")
            }
            block.asmSubroutines.forEach {
                out.write("<ASMSUB NAME=${it.name} ADDRESS=${it.address} POS=${it.position}>\n")
                // TODO rest of the signature
                out.write("<INLINEASM POS=${it.position}>\n")
                out.write(it.assembly.trimStart('\n').trimEnd(' ', '\n'))
                out.write("\n</INLINEASM>\n</ASMSUB>\n")
            }
            out.write("</BLOCK>\n")
        }
    }

    private fun writeInlineAsm(chunk: IRInlineAsmChunk) {
        out.write("<INLINEASM POS=${chunk.position}>\n")
        out.write(chunk.asm.trimStart('\n').trimEnd(' ', '\n'))
        out.write("\n</INLINEASM>\n")
    }

    private fun writeOptions() {
        out.write("<OPTIONS>\n")
        out.write("compTarget=${irProgram.options.compTarget.name}\n")
        out.write("output=${irProgram.options.output}\n")
        out.write("launcher=${irProgram.options.launcher}\n")
        out.write("zeropage=${irProgram.options.zeropage}\n")
        for(range in irProgram.options.zpReserved) {
            out.write("zpReserved=${range.first},${range.last}\n")
        }
        out.write("loadAddress=${irProgram.options.loadAddress}\n")
        out.write("dontReinitGlobals=${irProgram.options.dontReinitGlobals}\n")
        out.write("evalStackBaseAddress=${irProgram.options.evalStackBaseAddress}\n")
        // other options not yet useful here?
        out.write("</OPTIONS>\n")
    }

    private fun writeVariableAllocations() {
        out.write("\n<VARIABLES>\n")
        for (variable in irProgram.st.allVariables) {
            val typeStr = getTypeString(variable)
            val value: String = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue?.toInt()?.toString() ?: "0")
                DataType.STR -> {
                    val encoded = irProgram.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
                    encoded.joinToString(",") { it.toInt().toString() }
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
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toInt().toString() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            // TODO have uninitialized variables and arrays? (BSS SECTION)
            out.write("$typeStr ${variable.scopedName.joinToString(".")}=$value zp=${variable.zpwish}\n")
        }
        out.write("</VARIABLES>\n")

        out.write("\n<MEMORYMAPPEDVARIABLES>\n")
        for (variable in irProgram.st.allMemMappedVariables) {
            val typeStr = getTypeString(variable)
            out.write("&$typeStr ${variable.scopedName.joinToString(".")}=${variable.address}\n")
        }
        out.write("</MEMORYMAPPEDVARIABLES>\n")

        out.write("\n<MEMORYSLABS>\n")
        irProgram.st.allMemorySlabs.forEach{ slab -> out.write("SLAB _${slab.name} ${slab.size} ${slab.align}\n") }
        out.write("</MEMORYSLABS>\n")
    }

    private fun getTypeString(memvar: StMemVar): String {
        return when(memvar.dt) {
            DataType.UBYTE -> "ubyte"
            DataType.BYTE -> "byte"
            DataType.UWORD -> "uword"
            DataType.WORD -> "word"
            DataType.FLOAT -> "float"
            DataType.ARRAY_UB, DataType.STR -> "ubyte[${memvar.length}]"
            DataType.ARRAY_B -> "byte[${memvar.length}]"
            DataType.ARRAY_UW -> "uword[${memvar.length}]"
            DataType.ARRAY_W -> "word[${memvar.length}]"
            DataType.ARRAY_F -> "float[${memvar.length}]"
            else -> throw InternalCompilerException("weird dt")
        }
    }

    private fun getTypeString(variable : StStaticVariable): String {
        return when(variable.dt) {
            DataType.UBYTE -> "ubyte"
            DataType.BYTE -> "byte"
            DataType.UWORD -> "uword"
            DataType.WORD -> "word"
            DataType.FLOAT -> "float"
            DataType.ARRAY_UB, DataType.STR -> "ubyte[${variable.length}]"
            DataType.ARRAY_B -> "byte[${variable.length}]"
            DataType.ARRAY_UW -> "uword[${variable.length}]"
            DataType.ARRAY_W -> "word[${variable.length}]"
            DataType.ARRAY_F -> "float[${variable.length}]"
            else -> throw InternalCompilerException("weird dt")
        }
    }

    private fun BufferedWriter.writeLine(line: IRCodeLine) {
        when(line) {
            is IRCodeComment -> write("; ${line.comment}\n")
            is IRCodeInstruction -> {
                write(line.ins.toString() + "\n")
            }
            is IRCodeLabel -> write("_${line.name}:\n")
            is IRCodeInlineBinary -> {
                write("<BYTES>\n")
                line.data.withIndex().forEach {(index, byte) ->
                    write(byte.toString(16).padStart(2,'0'))
                    if(index and 63 == 63 && index < line.data.size-1)
                        write("\n")
                }
                write("\n</BYTES>\n")
            }
            else -> throw AssemblyError("invalid vm code line")
        }
    }
}