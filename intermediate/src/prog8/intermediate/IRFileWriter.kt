package prog8.intermediate

import prog8.code.core.*
import prog8.code.source.ImportFileSystem
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import kotlin.io.path.absolute
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


class IRFileWriter(private val irProgram: IRProgram, outfileOverride: Path?) {
    private val outfile = outfileOverride ?: (irProgram.options.outputDir / ("${irProgram.name}.p8ir"))
    private val out = outfile.bufferedWriter(charset=Charsets.UTF_8)
    private val xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out)
    private var numChunks = 0
    private var numInstr = 0

    fun write(): Path {
        if(!irProgram.options.quiet)
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
        if(!irProgram.options.quiet)
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
            if(block.options.forceOutput) xml.writeAttribute("FORCEOUTPUT", "true")
            if(block.options.noSymbolPrefixing) xml.writeAttribute("NOPREFIXING", "true")
            if(block.options.veraFxMuls) xml.writeAttribute("VERAFXMULS", "true")
            if(block.options.ignoreUnused) xml.writeAttribute("IGNOREUNUSED", "true")
            xml.writeAttribute("LIBRARY", block.library.toString())
            xml.writeAttribute("POS", block.position.toString())
            xml.writeCharacters("\n")
            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> {
                        val clobbers = child.clobbers.joinToString(",")
                        val returns = child.returns.joinToString(",") { ret ->
                            if (ret.reg.registerOrPair != null) "${ret.reg.registerOrPair}:${ret.dt.toString().lowercase()}"
                            else "${ret.reg.statusflag}:${ret.dt.toString().lowercase()}"
                        }
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
                        xml.writeAttribute("RETURNS", child.returns.joinToString(",") { it.irTypeString(null).lowercase() })
                        xml.writeAttribute("POS", child.position.toString())
                        xml.writeCharacters("\n")
                        xml.writeStartElement("PARAMS")
                        xml.writeCharacters("\n")
                        child.parameters.forEach { param -> xml.writeCharacters("${param.dt.irTypeString(null)} ${param.name}\n") }
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
        val usedRegs = chunk.usedRegisters()
        val regs = StringBuilder()
        if(usedRegs.readRegs.any() || usedRegs.writeRegs.any()) {
            regs.append("\nINT REGS:\n")
            if (usedRegs.readRegs.any())
                regs.append(" read: ${usedRegs.readRegs.toSortedMap().map { (reg, amount) -> "r$reg=${amount}" }}\n")
            if (usedRegs.writeRegs.any())
                regs.append(" write: ${usedRegs.writeRegs.toSortedMap().map { (reg, amount) -> "r$reg=${amount}" }}\n")
            regs.append(" types:\n")
            for ((regnum, type) in usedRegs.regsTypes.toSortedMap()) {
                regs.append("  r$regnum -> $type\n")
            }
        }
        if(usedRegs.readFpRegs.any() || usedRegs.writeFpRegs.any()) {
            regs.append("\nFP REGS:\n")
            if(usedRegs.readFpRegs.any())
                regs.append(" read: ${usedRegs.readFpRegs.toSortedMap().map { (reg, amount) -> "fr$reg=${amount}" }}\n")
            if(usedRegs.writeFpRegs.any())
                regs.append(" write: ${usedRegs.writeFpRegs.toSortedMap().map { (reg, amount) -> "fr$reg=${amount}" }}\n")
        }

        xml.writeStartElement("CODE")
        chunk.label?.let { xml.writeAttribute("LABEL", chunk.label) }

        // xml.writeAttribute("used-registers", chunk.usedRegisters().toString())
        xml.writeStartElement("REGS")
        xml.writeCData(regs.toString())
        xml.writeEndElement()

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
                code.sourceLinesPositions
                    .filter{ pos -> pos.line > 0 }
                    .sortedBy { it.line }
                    .forEach { pos ->
                        val line = ImportFileSystem.retrieveSourceLine(pos)
                        sourceTxt.append("$pos  $line\n")
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
        xml.writeCharacters("memtop=${irProgram.options.memtopAddress.toHex()}\n")
        xml.writeCharacters("optimize=${irProgram.options.optimize}\n")
        xml.writeCharacters("romable=${irProgram.options.romable}\n")
        xml.writeCharacters("outputDir=${irProgram.options.outputDir.absolute()}\n")
        // other options not yet useful here?
        xml.writeEndElement()
        xml.writeCharacters("\n\n")
    }

    private fun initArrayInXml(array: IRStArray, floats: Boolean): String = array.joinToString(",") {
        if(it.bool==true)
            "1"
        else if(it.bool==false)
            "0"
        else if(it.number!=null) {
            if(floats) it.number.toString()
            else it.number.toInt().toHex()
        }
        else
            "@${it.addressOfSymbol}"
    }

    private fun writeVariables() {
        fun writeNoInitVar(variable: IRStStaticVariable) {
            if(variable.dt.isSplitWordArray) {
                // split into 2 ubyte arrays lsb+msb
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_lsb zp=${variable.zpwish} split=true")
                if(variable.align!=0u)
                    xml.writeCharacters(" align=${variable.align}")
                xml.writeCharacters("\nubyte[${variable.length}] ${variable.name}_msb zp=${variable.zpwish} split=true\n")
            } else {
                xml.writeCharacters("${variable.typeString} ${variable.name} zp=${variable.zpwish}")
                if(variable.align!=0u)
                    xml.writeCharacters(" align=${variable.align}")
                xml.writeCharacters("\n")
            }
        }

        fun writeConstant(constant: IRStConstant) {
            val dt = constant.dt
            val value: String = when {
                dt.isBool -> constant.value.toInt().toString()
                dt.isFloat -> constant.value.toString()
                dt.isInteger -> constant.value.toInt().toHex()
                else -> throw InternalCompilerException("weird dt")
            }
            xml.writeCharacters("${constant.typeString} ${constant.name}=$value\n")
        }

        fun writeVarWithInit(variable: IRStStaticVariable) {
            if(variable.dt.isSplitWordArray) {
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
                xml.writeCharacters("ubyte[${variable.length}] ${variable.name}_lsb=$lsbValue zp=${variable.zpwish} split=true")
                if(variable.align!=0u)
                    xml.writeCharacters(" align=${variable.align}")
                xml.writeCharacters("\nubyte[${variable.length}] ${variable.name}_msb=$msbValue zp=${variable.zpwish} split=true\n")
            } else {
                val dt = variable.dt
                val value: String = when {
                    dt.isBool -> variable.onetimeInitializationNumericValue?.toInt()?.toString() ?: ""
                    dt.isFloat -> (variable.onetimeInitializationNumericValue ?: "").toString()
                    dt.isInteger -> variable.onetimeInitializationNumericValue?.toInt()?.toHex() ?: ""
                    dt.isString -> {
                        val encoded = irProgram.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue.second) + listOf(0u)
                        encoded.joinToString(",") { it.toInt().toString() }
                    }
                    dt.isFloatArray -> {
                        if(variable.onetimeInitializationArrayValue!=null) {
                            initArrayInXml(variable.onetimeInitializationArrayValue, true)
                        } else {
                            ""     // array will be zero'd out at program start
                        }
                    }
                    dt.isArray -> {
                        if(variable.onetimeInitializationArrayValue!==null) {
                            initArrayInXml(variable.onetimeInitializationArrayValue, false)
                        } else {
                            ""     // array will be zero'd out at program start
                        }
                    }
                    else -> throw InternalCompilerException("weird dt")
                }
                xml.writeCharacters("${variable.typeString} ${variable.name}=$value zp=${variable.zpwish}")
                if(variable.align!=0u)
                    xml.writeCharacters(" align=${variable.align}")
                xml.writeCharacters("\n")
            }
        }

        fun writeNoInitVars(segmentname: String, variables: List<IRStStaticVariable>) {
            xml.writeStartElement(segmentname)
            xml.writeCharacters("\n")
            val (noinitNotAligned, noinitAligned) = variables.partition { it.align==0u || it.align==1u }
            for (variable in noinitNotAligned) {
                writeNoInitVar(variable)
            }
            for (variable in noinitAligned.sortedBy { it.align }) {
                writeNoInitVar(variable)
            }
            xml.writeEndElement()
            xml.writeCharacters("\n")
        }

        val (variablesNoInit, variablesWithInit) = irProgram.st.allVariables().partition { it.uninitialized }

        val (dirtyvars, cleanvars) = variablesNoInit.partition { it.dirty }
        writeNoInitVars("VARIABLESNOINIT", cleanvars)
        writeNoInitVars("VARIABLESNOINITDIRTY", dirtyvars)

        xml.writeStartElement("VARIABLESWITHINIT")
        xml.writeCharacters("\n")
        val (initNotAligned, initAligned) = variablesWithInit.partition { it.align==0u || it.align==1u }
        for (variable in initNotAligned) {
            writeVarWithInit(variable)
        }
        for (variable in initAligned.sortedBy { it.align }) {
            writeVarWithInit(variable)
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")


        val (instancesNoInit, instances) = irProgram.st.allStructInstances().partition { it.values.isEmpty() }
        xml.writeStartElement("STRUCTINSTANCESNOINIT")
        xml.writeCharacters("\n")
        for (instance in instancesNoInit) {
            val struct = irProgram.st.lookup(instance.structName) as IRStStructDef
            require(struct.size == instance.size)
            xml.writeCharacters("${instance.structName} ${instance.name} size=${instance.size}\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
        xml.writeStartElement("STRUCTINSTANCES")
        xml.writeCharacters("\n")
        for (instance in instances) {
            val struct = irProgram.st.lookup(instance.structName) as IRStStructDef
            require(struct.size == instance.size)
            require(struct.fields.size == instance.values.size)
            xml.writeCharacters("${instance.structName} ${instance.name} size=${instance.size} values=")
            val values = struct.fields.zip(instance.values).map {(field, value) ->
                val valuestr = when {
                    value.dt == BaseDataType.BOOL -> {
                        if(value.value.bool==true) "1" else "0"
                    }
                    value.dt.isInteger || value.dt.isPointer -> {
                        if(value.value.number!=null)
                            value.value.number.toInt().toHex()
                        else
                            "@${value.value.addressOfSymbol}"
                    }
                    value.dt == BaseDataType.FLOAT -> {
                        value.value.number.toString()
                    }
                    else -> throw InternalCompilerException("weird dt")
                }
                field.first to valuestr
            }
            xml.writeCharacters(values.joinToString(",") { "${it.first}:${it.second}" })
            xml.writeCharacters("\n")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")


        xml.writeStartElement("CONSTANTS")
        xml.writeCharacters("\n")
        for (constant in irProgram.st.allConstants()) {
            writeConstant(constant)
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