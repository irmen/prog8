package prog8.intermediate

import prog8.code.core.*
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
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
            xml.writeAttribute("ADDRESS", block.options.address?.toHex() ?: "")
            xml.writeAttribute("LIBRARY", block.library.toString())
            xml.writeAttribute("FORCEOUTPUT", block.options.forceOutput.toString())
            xml.writeAttribute("NOPREFIXING", block.options.noSymbolPrefixing.toString())
            xml.writeAttribute("VERAFXMULS", block.options.veraFxMuls.toString())
            xml.writeAttribute("ALIGN", block.options.alignment.toString())
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
        // xml.writeAttribute("used-registers", chunk.usedRegisters().toString())
        writeSourcelines(xml, chunk)
        xml.writeCharacters("\n")
        chunk.instructions.forEach { instr ->
            numInstr++
            xml.writeCharacters(instr.toString())
            xml.writeCharacters("\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeSourcelines(xml: XMLStreamWriter, code: IRCodeChunk) {
        if(irProgram.options.includeSourcelines) {
            if(code.sourceLinesPositions.any {it !== Position.DUMMY}) {
                xml.writeStartElement("P8SRC")
                val sourceTxt = StringBuilder("\n")
                code.sourceLinesPositions.forEach { pos ->
                    val line = SourceLineCache.retrieveLine(pos)
                    if(line!=null) {
                        sourceTxt.append("$pos  $line\n")
                    }
                }
                xml.writeCData(sourceTxt.toString())
                xml.writeEndElement()
            }
        }
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
        xml.writeStartElement("ASM")
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
        for(range in irProgram.options.zpAllowed) {
            xml.writeCharacters("zpAllowed=${range.first},${range.last}\n")
        }
        xml.writeCharacters("loadAddress=${irProgram.options.loadAddress.toHex()}\n")
        xml.writeCharacters("optimize=${irProgram.options.optimize}\n")
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
            if(variable.dt in SplitWordArrayTypes) {
                // split into 2 ubyte arrays lsb+msb
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_lsb zp=${variable.zpwish}\n")
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_msb zp=${variable.zpwish}\n")
            } else {
                xml.writeCharacters("${variable.typeString} ${variable.name} zp=${variable.zpwish}\n")
            }
        }

        xml.writeEndElement()
        xml.writeCharacters("\n")
        xml.writeStartElement("VARIABLESWITHINIT")
        xml.writeCharacters("\n")

        for (variable in variablesWithInit) {
            if(variable.dt in SplitWordArrayTypes) {
                val lsbValue: String
                val msbValue: String
                if(variable.onetimeInitializationArrayValue==null) {
                    lsbValue = ""
                    msbValue = ""
                } else {
                    lsbValue = variable.onetimeInitializationArrayValue.joinToString(",") {
                        if(it.number!=null)
                            (it.number.toInt() and 255).toHex()
                        else
                            "@<${it.addressOfSymbol}"
                    }
                    msbValue = variable.onetimeInitializationArrayValue.joinToString(",") {
                        if(it.number!=null)
                            (it.number.toInt() shr 8).toHex()
                        else
                            "@>${it.addressOfSymbol}"
                    }
                }
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_lsb=$lsbValue zp=${variable.zpwish}\n")
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_msb=$msbValue zp=${variable.zpwish}\n")
            } else {
                val value: String = when(variable.dt) {
                    DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: "").toString()
                    in NumericDatatypes -> (variable.onetimeInitializationNumericValue?.toInt()?.toHex() ?: "").toString()
                    DataType.STR -> {
                        val encoded = irProgram.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue.second) + listOf(0u)
                        encoded.joinToString(",") { it.toInt().toString() }
                    }
                    DataType.ARRAY_F -> {
                        if(variable.onetimeInitializationArrayValue!=null) {
                            variable.onetimeInitializationArrayValue.joinToString(",") { it.number!!.toString() }
                        } else {
                            ""     // array will be zero'd out at program start
                        }
                    }
                    in ArrayDatatypes -> {
                        if(variable.onetimeInitializationArrayValue!==null) {
                            variable.onetimeInitializationArrayValue.joinToString(",") {
                                if(it.number!=null)
                                    it.number.toInt().toHex()
                                else
                                    "@${it.addressOfSymbol}"
                            }
                        } else {
                            ""     // array will be zero'd out at program start
                        }
                    }
                    else -> throw InternalCompilerException("weird dt")
                }
                xml.writeCharacters("${variable.typeString} ${variable.name}=$value zp=${variable.zpwish}\n")
            }
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")

        xml.writeStartElement("MEMORYMAPPEDVARIABLES")
        xml.writeCharacters("\n")
        for (variable in irProgram.st.allMemMappedVariables()) {
            xml.writeCharacters("@${variable.typeString} ${variable.name}=${variable.address.toHex()}\n")
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