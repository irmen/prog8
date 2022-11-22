package prog8.intermediate

import prog8.code.core.*
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


class IRFileWriter(private val irProgram: IRProgram, outfileOverride: Path?) {
    private val outfile = outfileOverride ?: (irProgram.options.outputDir / ("${irProgram.name}.p8ir"))
    private val out = outfile.bufferedWriter(charset=Charsets.UTF_8)
    private var numChunks = 0
    private var numInstr = 0


    fun write(): Path {
        println("Writing intermediate representation to $outfile")
        out.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        out.write("<PROGRAM NAME=\"${irProgram.name}\">\n")
        writeOptions()
        writeAsmSymbols()
        writeVariableAllocations()

        out.write("\n<INITGLOBALS>\n")
        if(!irProgram.options.dontReinitGlobals) {
            // note: this a block of code that loads values and stores them into the global variables to reset their values.
            writeCodeChunk(irProgram.globalInits)
        }
        out.write("</INITGLOBALS>\n")
        writeBlocks()
        out.write("</PROGRAM>\n")
        out.close()

        val used = irProgram.registersUsed()
        val numberUsed = (used.inputRegs.keys + used.outputRegs.keys).size + (used.inputFpRegs.keys + used.outputFpRegs.keys).size
        println("($numInstr instructions in $numChunks chunks, $numberUsed registers)")
        return outfile
    }

    private fun writeAsmSymbols() {
        out.write("<ASMSYMBOLS>\n")
        irProgram.asmSymbols.forEach { (name, value) -> out.write("$name=$value\n" )}
        out.write("</ASMSYMBOLS>\n")
    }

    private fun writeBlocks() {
        irProgram.blocks.forEach { block ->
            out.write("\n<BLOCK NAME=\"${block.name}\" ADDRESS=\"${block.address?.toHex()}\" ALIGN=\"${block.alignment}\" POS=\"${block.position}\">\n")
            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> {
                        val clobbers = child.clobbers.joinToString(",")
                        val returns = child.returns.map { ret ->
                            if(ret.reg.registerOrPair!=null) "${ret.reg.registerOrPair}:${ret.dt.toString().lowercase()}"
                            else "${ret.reg.statusflag}:${ret.dt.toString().lowercase()}"
                        }.joinToString(",")
                        out.write("<ASMSUB NAME=\"${child.label}\" ADDRESS=\"${child.address?.toHex()}\" CLOBBERS=\"$clobbers\" RETURNS=\"$returns\" POS=\"${child.position}\">\n")
                        out.write("<ASMPARAMS>\n")
                        child.parameters.forEach { ret ->
                            val reg = if(ret.reg.registerOrPair!=null) ret.reg.registerOrPair.toString()
                            else ret.reg.statusflag.toString()
                            out.write("${ret.dt.toString().lowercase()} $reg\n")
                        }
                        out.write("</ASMPARAMS>\n")
                        writeInlineAsm(child.asmChunk)
                        out.write("</ASMSUB>\n")
                    }
                    is IRCodeChunk -> writeCodeChunk(child)
                    is IRInlineAsmChunk -> writeInlineAsm(child)
                    is IRInlineBinaryChunk -> writeInlineBytes(child)
                    is IRSubroutine -> {
                        out.write("<SUB NAME=\"${child.label}\" RETURNTYPE=\"${child.returnType.toString().lowercase()}\" POS=\"${child.position}\">\n")
                        out.write("<PARAMS>\n")
                        child.parameters.forEach { param -> out.write("${getTypeString(param.dt)} ${param.name}\n") }
                        out.write("</PARAMS>\n")
                        child.chunks.forEach { chunk ->
                            numChunks++
                            when (chunk) {
                                is IRInlineAsmChunk -> writeInlineAsm(chunk)
                                is IRInlineBinaryChunk -> writeInlineBytes(chunk)
                                is IRCodeChunk -> writeCodeChunk(chunk)
                                else -> throw InternalCompilerException("invalid chunk")
                            }
                        }
                        out.write("</SUB>\n")
                    }
                }
            }
            out.write("</BLOCK>\n")
        }
    }

    private fun writeCodeChunk(chunk: IRCodeChunk) {
        if(chunk.label!=null)
            out.write("<CODE LABEL=\"${chunk.label}\">\n")
        else
            out.write("<CODE>\n")
        chunk.instructions.forEach { instr ->
            numInstr++
            out.write(instr.toString())
            out.write("\n")
        }
        out.write("</CODE>\n")
    }

    private fun writeInlineBytes(chunk: IRInlineBinaryChunk) {
        out.write("<BYTES LABEL=\"${chunk.label ?: ""}\">\n")
        chunk.data.withIndex().forEach {(index, byte) ->
            out.write(byte.toString(16).padStart(2,'0'))
            if(index and 63 == 63 && index < chunk.data.size-1)
                out.write("\n")
        }
        out.write("\n</BYTES>\n")
    }

    private fun writeInlineAsm(chunk: IRInlineAsmChunk) {
        out.write("<INLINEASM LABEL=\"${chunk.label ?: ""}\" IR=\"${chunk.isIR}\">\n")
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
        out.write("loadAddress=${irProgram.options.loadAddress.toHex()}\n")
        out.write("optimize=${irProgram.options.optimize}\n")
        out.write("dontReinitGlobals=${irProgram.options.dontReinitGlobals}\n")
        out.write("evalStackBaseAddress=${irProgram.options.evalStackBaseAddress?.toHex()}\n")
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
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue?.toInt()?.toHex() ?: "").toString()
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
                                it.number!!.toInt().toHex()
                            else
                                "@${it.addressOf!!.joinToString(".")}"
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
            out.write("@$typeStr ${variable.name}=${variable.address.toHex()}\n")
        }
        out.write("</MEMORYMAPPEDVARIABLES>\n")

        out.write("\n<MEMORYSLABS>\n")
        irProgram.st.allMemorySlabs().forEach{ slab -> out.write("SLAB ${slab.name} ${slab.size} ${slab.align}\n") }
        out.write("</MEMORYSLABS>\n")
    }
}