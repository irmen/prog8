package prog8.intermediate

import prog8.code.core.*
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


class IRFileWriter(private val irProgram: IRProgram, outfileOverride: Path?) {
    private val outfile = outfileOverride ?: (irProgram.options.outputDir / ("${irProgram.name}.p8ir"))
    private val out = outfile.bufferedWriter(charset=Charsets.UTF_8)
    private val xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out)
    private var numChunks = 0
    private var numInstr = 0


    fun write(): Path {

        println("Writing intermediate representation to $outfile")
        xml.writeStartDocument("utf-8", "1.0")
        xml.writeEndDocument()
        xml.writeCharacters("\n")
        xml.writeStartElement("PROGRAM")
        xml.writeAttribute("NAME", irProgram.name)
        xml.writeCharacters("\n")
        writeOptions()
        writeAsmSymbols()
        writeVariables()
        xml.writeStartElement("INITGLOBALS")
        xml.writeCharacters("\n")
        writeCodeChunk(irProgram.globalInits)
        xml.writeEndElement()
        xml.writeCharacters("\n\n")
        writeBlocks()
        xml.writeEndElement()
        xml.writeCharacters("\n")
        xml.close()
        out.close()

        val used = irProgram.registersUsed()
        val numberUsed = (used.readRegs.keys + used.writeRegs.keys).size + (used.readFpRegs.keys + used.writeFpRegs.keys).size
        println("($numInstr instructions in $numChunks chunks, $numberUsed registers)")
        return outfile
    }

    private fun writeAsmSymbols() {
        xml.writeStartElement("ASMSYMBOLS")
        xml.writeCharacters("\n")
        irProgram.asmSymbols.forEach { (name, value) -> xml.writeCharacters("$name=$value\n" )}
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeBlocks() {
        irProgram.blocks.forEach { block ->
            xml.writeStartElement("BLOCK")
            xml.writeAttribute("NAME", block.label)
            xml.writeAttribute("ADDRESS", block.address?.toHex() ?: "")
            xml.writeAttribute("LIBRARY", block.library.toString())
            xml.writeAttribute("FORCEOUTPUT", block.forceOutput.toString())
            xml.writeAttribute("ALIGN", block.alignment.toString())
            xml.writeAttribute("POS", block.position.toString())
            xml.writeCharacters("\n")
            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> {
                        val clobbers = child.clobbers.joinToString(",")
                        val returns = child.returns.map { ret ->
                            if(ret.reg.registerOrPair!=null) "${ret.reg.registerOrPair}:${ret.dt.toString().lowercase()}"
                            else "${ret.reg.statusflag}:${ret.dt.toString().lowercase()}"
                        }.joinToString(",")
                        xml.writeStartElement("ASMSUB")
                        xml.writeAttribute("NAME", child.label)
                        xml.writeAttribute("ADDRESS", child.address?.toHex() ?: "")
                        xml.writeAttribute("CLOBBERS", clobbers)
                        xml.writeAttribute("RETURNS", returns)
                        xml.writeAttribute("POS", child.position.toString())
                        xml.writeCharacters("\n")
                        xml.writeStartElement("ASMPARAMS")
                        xml.writeCharacters("\n")
                        child.parameters.forEach { ret ->
                            val reg = if(ret.reg.registerOrPair!=null) ret.reg.registerOrPair.toString()
                            else ret.reg.statusflag.toString()
                            xml.writeCharacters("${ret.dt.toString().lowercase()} $reg\n")
                        }
                        xml.writeEndElement()
                        xml.writeCharacters("\n")
                        writeInlineAsm(child.asmChunk)
                        xml.writeEndElement()
                        xml.writeCharacters("\n\n")
                    }
                    is IRCodeChunk -> writeCodeChunk(child)
                    is IRInlineAsmChunk -> writeInlineAsm(child)
                    is IRInlineBinaryChunk -> writeInlineBytes(child)
                    is IRSubroutine -> {
                        xml.writeStartElement("SUB")
                        xml.writeAttribute("NAME", child.label)
                        xml.writeAttribute("RETURNTYPE", child.returnType?.toString()?.lowercase() ?: "")
                        xml.writeAttribute("POS", child.position.toString())
                        xml.writeCharacters("\n")
                        xml.writeStartElement("PARAMS")
                        xml.writeCharacters("\n")
                        child.parameters.forEach { param -> xml.writeCharacters("${getTypeString(param.dt)} ${param.name}\n") }
                        xml.writeEndElement()
                        xml.writeCharacters("\n")
                        child.chunks.forEach { chunk ->
                            numChunks++
                            when (chunk) {
                                is IRInlineAsmChunk -> writeInlineAsm(chunk)
                                is IRInlineBinaryChunk -> writeInlineBytes(chunk)
                                is IRCodeChunk -> writeCodeChunk(chunk)
                                else -> throw InternalCompilerException("invalid chunk")
                            }
                        }
                        xml.writeEndElement()
                        xml.writeCharacters("\n\n")
                    }
                }
            }
            xml.writeEndElement()
            xml.writeCharacters("\n\n")
        }
    }

    private fun writeCodeChunk(chunk: IRCodeChunk) {
        xml.writeStartElement("CODE")
        chunk.label?.let { xml.writeAttribute("LABEL", chunk.label) }
        xml.writeAttribute("used-registers", chunk.usedRegisters().toString())
        xml.writeCharacters("\n")
        chunk.instructions.forEach { instr ->
            numInstr++
            xml.writeCharacters(instr.toString())
            xml.writeCharacters("\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeInlineBytes(chunk: IRInlineBinaryChunk) {
        xml.writeStartElement("BYTES")
        chunk.label?.let { xml.writeAttribute("LABEL", chunk.label) }
        chunk.data.withIndex().forEach {(index, byte) ->
            xml.writeCharacters(byte.toString(16).padStart(2,'0'))
            if(index and 63 == 63 && index < chunk.data.size-1)
                xml.writeCharacters("\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeInlineAsm(chunk: IRInlineAsmChunk) {
        xml.writeStartElement("INLINEASM")
        xml.writeAttribute("LABEL", chunk.label ?: "")
        xml.writeAttribute("IR", chunk.isIR.toString())
        xml.writeCharacters("\n")
        xml.writeCharacters(chunk.assembly)
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeOptions() {
        xml.writeStartElement("OPTIONS")
        xml.writeCharacters("\n")
        xml.writeCharacters("compTarget=${irProgram.options.compTarget.name}\n")
        xml.writeCharacters("output=${irProgram.options.output}\n")
        xml.writeCharacters("launcher=${irProgram.options.launcher}\n")
        xml.writeCharacters("zeropage=${irProgram.options.zeropage}\n")
        for(range in irProgram.options.zpReserved) {
            xml.writeCharacters("zpReserved=${range.first},${range.last}\n")
        }
        xml.writeCharacters("loadAddress=${irProgram.options.loadAddress.toHex()}\n")
        xml.writeCharacters("optimize=${irProgram.options.optimize}\n")
        xml.writeCharacters("evalStackBaseAddress=${irProgram.options.evalStackBaseAddress?.toHex() ?: ""}\n")
        xml.writeCharacters("outputDir=${irProgram.options.outputDir.toAbsolutePath()}\n")
        // other options not yet useful here?
        xml.writeEndElement()
        xml.writeCharacters("\n\n")
    }

    private fun writeVariables() {

        val (variablesNoInit, variablesWithInit) = irProgram.st.allVariables().partition { it.uninitialized }

        xml.writeStartElement("VARIABLESNOINIT")
        xml.writeCharacters("\n")
        for (variable in variablesNoInit) {
            val typeStr = getTypeString(variable)
            xml.writeCharacters("$typeStr ${variable.name} zp=${variable.zpwish}\n")
        }

        xml.writeEndElement()
        xml.writeCharacters("\n")
        xml.writeStartElement("VARIABLESWITHINIT")
        xml.writeCharacters("\n")

        for (variable in variablesWithInit) {
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
                                "@${it.addressOfSymbol}"
                        }
                    } else {
                        ""     // array will be zero'd out at program start
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            xml.writeCharacters("$typeStr ${variable.name}=$value zp=${variable.zpwish}\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")

        xml.writeStartElement("MEMORYMAPPEDVARIABLES")
        xml.writeCharacters("\n")
        for (variable in irProgram.st.allMemMappedVariables()) {
            val typeStr = getTypeString(variable)
            xml.writeCharacters("@$typeStr ${variable.name}=${variable.address.toHex()}\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")

        xml.writeStartElement("MEMORYSLABS")
        xml.writeCharacters("\n")
        irProgram.st.allMemorySlabs().forEach{ slab -> xml.writeCharacters("${slab.name} ${slab.size} ${slab.align}\n") }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }
}