package prog8.intermediate

import prog8.code.core.*
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


class IRFileWriter(private val irProgram: IRProgram, outfileOverride: Path?) {
    private val outfile = outfileOverride ?: (irProgram.options.outputDir / ("${irProgram.name}.p8ir"))
    private val out = outfile.bufferedWriter()
    private var numChunks = 0
    private var numLines = 0


    fun write(): Path {
        println("Writing intermediate representation to $outfile")
        out.write("<PROGRAM NAME=${irProgram.name}>\n")
        writeOptions()
        writeAsmSymbols()
        writeVariableAllocations()

        out.write("\n<INITGLOBALS>\n")
        if(!irProgram.options.dontReinitGlobals) {
            out.write("<C>\n")
            // note: this a block of code that loads values and stores them into the global variables to reset their values.
            irProgram.globalInits.forEach { out.writeLine(it) }
            out.write("</C>\n")
        }
        out.write("</INITGLOBALS>\n")
        writeBlocks()
        out.write("</PROGRAM>\n")
        out.close()

        val used = irProgram.registersUsed()
        val numberUsed = (used.inputRegs.keys + used.outputRegs.keys).size + (used.inputFpRegs.keys + used.outputFpRegs.keys).size
        println("($numLines lines in $numChunks code chunks, $numberUsed registers)")
        return outfile
    }

    private fun writeAsmSymbols() {
        out.write("<ASMSYMBOLS>\n")
        irProgram.asmSymbols.forEach { (name, value) -> out.write("$name=$value\n" )}
        out.write("</ASMSYMBOLS>\n")
    }

    private fun writeBlocks() {
        irProgram.blocks.forEach { block ->
            out.write("\n<BLOCK NAME=${block.name} ADDRESS=${block.address} ALIGN=${block.alignment} POS=${block.position}>\n")
            block.inlineAssembly.forEach {
                writeInlineAsm(it)
            }
            block.subroutines.forEach {
                out.write("<SUB NAME=${it.name} RETURNTYPE=${it.returnType.toString().lowercase()} POS=${it.position}>\n")
                out.write("<PARAMS>\n")
                it.parameters.forEach { param -> out.write("${getTypeString(param.dt)} ${param.name}\n") }
                out.write("</PARAMS>\n")
                it.chunks.forEach { chunk ->
                    numChunks++
                    if(chunk is IRInlineAsmChunk) {
                        writeInlineAsm(chunk)
                    } else {
                        out.write("<C>\n")
                        if (chunk.lines.isEmpty())
                            throw InternalCompilerException("empty code chunk in ${it.name} ${it.position}")
                        chunk.lines.forEach { line ->
                            numLines++
                            out.writeLine(line)
                        }
                        out.write("</C>\n")
                    }
                }
                out.write("</SUB>\n")
            }
            block.asmSubroutines.forEach {
                val clobbers = it.clobbers.joinToString(",")
                val returns = it.returns.map { (dt, reg) ->
                    if(reg.registerOrPair!=null) "${reg.registerOrPair}:${dt.toString().lowercase()}"
                    else "${reg.statusflag}:${dt.toString().lowercase()}"
                }.joinToString(",")
                out.write("<ASMSUB NAME=${it.name} ADDRESS=${it.address} CLOBBERS=$clobbers RETURNS=$returns POS=${it.position}>\n")
                out.write("<PARAMS>\n")
                it.parameters.forEach { (dt, regOrSf) ->
                    val reg = if(regOrSf.registerOrPair!=null) regOrSf.registerOrPair.toString()
                    else regOrSf.statusflag.toString()
                    out.write("${dt.toString().lowercase()} $reg\n")
                }
                out.write("</PARAMS>\n")
                out.write("<INLINEASM IR=${it.isIR} POS=${it.position}>\n")
                out.write(it.assembly)
                out.write("\n</INLINEASM>\n</ASMSUB>\n")
            }
            out.write("</BLOCK>\n")
        }
    }

    private fun writeInlineAsm(chunk: IRInlineAsmChunk) {
        out.write("<INLINEASM IR=${chunk.isIR} POS=${chunk.position}>\n")
        out.write(chunk.assembly)
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
        out.write("optimize=${irProgram.options.optimize}\n")
        out.write("dontReinitGlobals=${irProgram.options.dontReinitGlobals}\n")
        out.write("evalStackBaseAddress=${irProgram.options.evalStackBaseAddress}\n")
        out.write("outputDir=${irProgram.options.outputDir.toAbsolutePath()}\n")
        // other options not yet useful here?
        out.write("</OPTIONS>\n")
    }

    private fun writeVariableAllocations() {

        out.write("\n<VARIABLES>\n")
        for (variable in irProgram.st.allVariables()) {
            val typeStr = getTypeString(variable)
            val value: String = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: "").toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue?.toInt() ?: "").toString()
                DataType.STR -> {
                    val encoded = irProgram.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
                    encoded.joinToString(",") { it.toInt().toString() }
                }
                DataType.ARRAY_F -> {
                    if(variable.onetimeInitializationArrayValue!=null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toString() }
                    } else {
                        ""     // array will be zero'd out at program start
                    }
                }
                in ArrayDatatypes -> {
                    if(variable.onetimeInitializationArrayValue!==null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") {
                            if(it.number!=null)
                                it.number!!.toInt().toString()
                            else
                                "&${it.addressOf!!.joinToString(".")}"
                        }
                    } else {
                        ""     // array will be zero'd out at program start
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            out.write("$typeStr ${variable.name}=$value zp=${variable.zpwish}\n")
        }
        out.write("</VARIABLES>\n")

        out.write("\n<MEMORYMAPPEDVARIABLES>\n")
        for (variable in irProgram.st.allMemMappedVariables()) {
            val typeStr = getTypeString(variable)
            out.write("&$typeStr ${variable.name}=${variable.address}\n")
        }
        out.write("</MEMORYMAPPEDVARIABLES>\n")

        out.write("\n<MEMORYSLABS>\n")
        irProgram.st.allMemorySlabs().forEach{ slab -> out.write("SLAB ${slab.name} ${slab.size} ${slab.align}\n") }
        out.write("</MEMORYSLABS>\n")
    }

    private fun BufferedWriter.writeLine(line: IRCodeLine) {
        when(line) {
            is IRCodeComment -> write("; ${line.comment}\n")
            is IRInstruction -> write(line.toString() + "\n")
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