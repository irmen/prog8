package prog8.intermediate

import prog8.code.core.BaseDataType
import prog8.code.core.InternalCompilerException
import prog8.code.core.Position
import prog8.code.core.toHex
import prog8.code.source.ImportFileSystem
import java.nio.file.Path
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import kotlin.io.path.absolute
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

private const val StMemorySlabBlockName = "prog8_slabs"

/**
 * Converts [IRStSymbolicReference] instances to IR XML text representation.
 * Used for serializing array initializers and struct field values.
 */
private object IRStSymbolicReferenceXml {
    /**
     * Formats a symbolic reference for use in a generic array initializer.
     * @param floats if true, numeric values are formatted as floats; otherwise as hex integers
     */
    fun formatForArray(ref: IRStSymbolicReference, floats: Boolean = false): String = when(ref) {
        is IRStSymbolicReference.BoolValue -> if(ref.value) "1" else "0"
        is IRStSymbolicReference.Numeric -> if(floats) ref.value.toString() else ref.value.toInt().toHex()
        is IRStSymbolicReference.Symbol -> "@${ref.name}"
    }

    /**
     * Formats a symbolic reference for use in the LSB byte of a split word array.
     */
    fun formatLsb(ref: IRStSymbolicReference): String = when(ref) {
        is IRStSymbolicReference.Numeric -> (ref.value.toInt() and 255).toHex()
        is IRStSymbolicReference.Symbol -> "@<${ref.name}"
        is IRStSymbolicReference.BoolValue -> throw InternalCompilerException("bool in word array")
    }

    /**
     * Formats a symbolic reference for use in the MSB byte of a split word array.
     */
    fun formatMsb(ref: IRStSymbolicReference): String = when(ref) {
        is IRStSymbolicReference.Numeric -> (ref.value.toInt() shr 8).toHex()
        is IRStSymbolicReference.Symbol -> "@>${ref.name}"
        is IRStSymbolicReference.BoolValue -> throw InternalCompilerException("bool in word array")
    }

    /**
     * Formats a symbolic reference for use as a struct field value.
     */
    fun formatForStructField(ref: IRStSymbolicReference): String = when(ref) {
        is IRStSymbolicReference.BoolValue -> if(ref.value) "1" else "0"
        is IRStSymbolicReference.Numeric -> ref.value.toInt().toHex()
        is IRStSymbolicReference.Symbol -> "@${ref.name}"
    }
}


class IRFileWriter(private val irProgram: IRProgram, outfileOverride: Path?) {
    private val outfile = outfileOverride ?: (irProgram.options.outputDir / ("${irProgram.name}.p8ir"))
    private val out = outfile.bufferedWriter(charset=Charsets.UTF_8)
    private val xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out)
    private var numChunks = 0
    private var numInstr = 0

    private fun emitLine(text: String) {
        xml.writeCharacters(text)
        xml.writeCharacters("\n")
    }

    fun write(): Path {
        if(!irProgram.options.quiet)
            println("Writing intermediate representation to $outfile")

        xml.writeStartDocument("utf-8", "1.0")
        xml.writeEndDocument()
        xml.writeCharacters("\n")
        xml.writeStartElement("PROGRAM")
        xml.writeAttribute("NAME", irProgram.name)
        xml.writeAttribute("COMPILERVERSION", irProgram.options.compilerVersion)
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
        irProgram.asmSymbols.forEach { (name, value) -> emitLine("$name=$value")}
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
                            if (ret.reg.registerOrPair != null) "${ret.reg.registerOrPair}:${ret.dt.irTypeString(null)}"
                            else "${ret.reg.statusflag}:${ret.dt.irTypeString(null)}"
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
                            emitLine("${ret.dt.toString().lowercase()} $reg")
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
                        child.parameters.forEach { param -> emitLine("${param.dt.irTypeString(null)} ${param.name}") }
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

        xml.writeStartElement("CHUNK")
        chunk.label?.let { xml.writeAttribute("LABEL", chunk.label) }

        // xml.writeAttribute("used-registers", chunk.usedRegisters().toString())
        xml.writeStartElement("REGS")
        xml.writeCData(regs.toString())
        xml.writeEndElement()
        writeSourcelines(xml, chunk)
        xml.writeStartElement("CODE")
        xml.writeCharacters("\n")
        chunk.instructions.forEach { instr ->
            numInstr++
            xml.writeCharacters(instr.toString())
            xml.writeCharacters("\n")
        }
        xml.writeEndElement()
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeSourcelines(xml: XMLStreamWriter, code: IRCodeChunk) {
        if(irProgram.options.includeSourcelines) {
            if(code.sourceLinesPositions.any {it !== Position.DUMMY}) {
                xml.writeStartElement("P8SRC")
                val sourceTxt = StringBuilder("\n")
                code.sourceLinesPositions
                    .asSequence()
                    .filter { it !== Position.DUMMY }
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
        emitLine("compTarget=${irProgram.options.compTarget.name}")
        emitLine("output=${irProgram.options.output}")
        emitLine("launcher=${irProgram.options.launcher}")
        emitLine("zeropage=${irProgram.options.zeropage}")
        for(range in irProgram.options.zpReserved) {
            emitLine("zpReserved=${range.first},${range.last}")
        }
        for(range in irProgram.options.zpAllowed) {
            emitLine("zpAllowed=${range.first},${range.last}")
        }
        emitLine("loadAddress=${irProgram.options.loadAddress.toHex()}")
        emitLine("memtop=${irProgram.options.memtopAddress.toHex()}")
        emitLine("optimize=${irProgram.options.optimize}")
        emitLine("romable=${irProgram.options.romable}")
        emitLine("outputDir=${irProgram.options.outputDir.absolute()}")
        // other options not yet useful here?
        xml.writeEndElement()
        xml.writeCharacters("\n\n")
    }

    private fun initArrayInXml(array: IRStArray, floats: Boolean): String =
        array.joinToString(",") { IRStSymbolicReferenceXml.formatForArray(it, floats) }

    private fun writeVariables() {
        xml.writeStartElement("VARS")
        xml.writeCharacters("\n")
        writeNoInitVariables()
        writeInitializedVariables()
        writeStructInstancesNoInit()
        writeStructInstances()
        writeConstants()
        writeMemoryMappedVariables()
        writeMemorySlabs()
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeNoInitVar(variable: IRStStaticVariable) {
        if(variable.dt.isSplitWordArray) {
            emitLine(buildString {
                append("ubyte[${variable.length}] ${variable.name}_lsb zp=${variable.zpwish} split=true")
                if(variable.align!=0u) append(" align=${variable.align}")
            })
            emitLine("ubyte[${variable.length}] ${variable.name}_msb zp=${variable.zpwish} split=true")
        } else {
            emitLine(buildString {
                append("${variable.typeString} ${variable.name} zp=${variable.zpwish}")
                if(variable.align!=0u) append(" align=${variable.align}")
            })
        }
    }

    private fun writeNoInitVars(segmentname: String, variables: List<IRStStaticVariable>) {
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

    private fun writeNoInitVariables() {
        val (variablesNoInit, _) = irProgram.st.allVariables().partition { it.uninitialized }
        val (dirtyvars, cleanvars) = variablesNoInit.partition { it.dirty }
        writeNoInitVars("NOINITCLEAN", cleanvars)
        writeNoInitVars("NOINITDIRTY", dirtyvars)
    }

    private fun writeInitializedVariables() {
        xml.writeStartElement("INIT")
        xml.writeCharacters("\n")
        val (_, variablesWithInit) = irProgram.st.allVariables().partition { it.uninitialized }
        val (initNotAligned, initAligned) = variablesWithInit.partition { it.align==0u || it.align==1u }
        for (variable in initNotAligned) {
            writeVarWithInit(variable)
        }
        for (variable in initAligned.sortedBy { it.align }) {
            writeVarWithInit(variable)
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeVarWithInit(variable: IRStStaticVariable) {
        if(variable.dt.isSplitWordArray) {
            writeSplitWordArrayVariable(variable)
        } else {
            writeRegularVariableWithInit(variable)
        }
    }

    private fun writeSplitWordArrayVariable(variable: IRStStaticVariable) {
        val lsbValue: String
        val msbValue: String
        if(variable.initializationValue is IRVariableInitializer.Array) {
            lsbValue = variable.initializationValue.elements.joinToString(",") { elt ->
                IRStSymbolicReferenceXml.formatLsb(elt)
            }
            msbValue = variable.initializationValue.elements.joinToString(",") { elt ->
                IRStSymbolicReferenceXml.formatMsb(elt)
            }
        } else {
            lsbValue = ""
            msbValue = ""
        }
        emitLine(buildString {
            append("ubyte[${variable.length}] ${variable.name}_lsb=$lsbValue zp=${variable.zpwish} split=true")
            if(variable.align!=0u) append(" align=${variable.align}")
        })
        emitLine("ubyte[${variable.length}] ${variable.name}_msb=$msbValue zp=${variable.zpwish} split=true")
    }

    private fun writeRegularVariableWithInit(variable: IRStStaticVariable) {
        val dt = variable.dt
        val initValue = variable.initializationValue
        val value: String = when {
            dt.isBool -> (initValue as? IRVariableInitializer.Numeric)?.value?.toInt()?.toString() ?: ""
            dt.isFloat -> (initValue as? IRVariableInitializer.Numeric)?.value?.toString() ?: ""
            dt.isInteger || dt.isPointer -> {
                val num = (initValue as? IRVariableInitializer.Numeric)?.value
                if (num != null) {
                    if (dt.base == BaseDataType.LONG)
                        num.toLong().toHex()
                    else
                        num.toInt().toHex()
                } else {
                    ""
                }
            }
            dt.isString -> {
                val strInit = initValue as? IRVariableInitializer.Str
                    ?: error("String variable missing initialization value")
                val encoded = irProgram.encoding.encodeString(strInit.text, strInit.encoding) + listOf(0u)
                encoded.joinToString(",") { it.toInt().toString() }
            }
            dt.isFloatArray -> {
                if(initValue is IRVariableInitializer.Array) {
                    initArrayInXml(initValue.elements, true)
                } else {
                    ""     // array will be zero'd out at program start
                }
            }
            dt.isArray -> {
                if(initValue is IRVariableInitializer.Array) {
                    initArrayInXml(initValue.elements, false)
                } else {
                    ""     // array will be zero'd out at program start
                }
            }
            else -> throw InternalCompilerException("weird dt $dt")
        }
        emitLine(buildString {
            append("${variable.typeString} ${variable.name}=$value zp=${variable.zpwish}")
            if(variable.align!=0u) append(" align=${variable.align}")
        })
    }

    private fun writeStructInstancesNoInit() {
        val (instancesNoInit, _) = irProgram.st.allStructInstances().partition { it.values.isEmpty() }
        xml.writeStartElement("STRUCTINSTANCESNOINIT")
        xml.writeCharacters("\n")
        for (instance in instancesNoInit) {
            val struct = irProgram.st.lookup(instance.structName) as IRStStructDef
            require(struct.size == instance.size)
            emitLine("${instance.structName} ${instance.name} size=${instance.size}")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeStructInstances() {
        val (_, instances) = irProgram.st.allStructInstances().partition { it.values.isEmpty() }
        xml.writeStartElement("STRUCTINSTANCES")
        xml.writeCharacters("\n")
        for (instance in instances) {
            val struct = irProgram.st.lookup(instance.structName) as IRStStructDef
            require(struct.size == instance.size)
            require(struct.fields.size == instance.values.size)
            emitLine(buildString {
                append("${instance.structName} ${instance.name} size=${instance.size} values=")
                val values = struct.fields.zip(instance.values).map {(field, value) ->
                    val valuestr = IRStSymbolicReferenceXml.formatForStructField(value.value)
                    field.first to valuestr
                }
                append(values.joinToString(",") { "${it.first}:${it.second}" })
            })
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeConstants() {
        xml.writeStartElement("CONSTANTS")
        xml.writeCharacters("\n")
        for (constant in irProgram.st.allConstants()) {
            writeConstant(constant)
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeConstant(constant: IRStConstant) {
        val dt = constant.dt
        val value: String = when {
            dt.isBool -> constant.value?.toInt()?.toString() ?: ""
            dt.isFloat -> constant.value?.toString() ?: ""
            dt.isInteger || dt.isPointer -> {
                if (constant.value != null) {
                    if (dt.base == BaseDataType.LONG)
                        constant.value.toLong().toHex()
                    else
                        constant.value.toInt().toHex()
                } else if (constant.memorySlabName != null) {
                    "@$StMemorySlabBlockName.${constant.memorySlabName}"
                } else {
                    throw InternalCompilerException("constant without value or memory slab: $constant")
                }
            }
            else -> throw InternalCompilerException("weird dt $dt")
        }
        emitLine("${constant.typeString} ${constant.name}=$value")
    }

    private fun writeMemoryMappedVariables() {
        xml.writeStartElement("MEMORYMAPPED")
        xml.writeCharacters("\n")
        for (variable in irProgram.st.allMemMappedVariables()) {
            emitLine("@${variable.typeString} ${variable.name}=${variable.address.toHex()}")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }

    private fun writeMemorySlabs() {
        xml.writeStartElement("MEMORYSLABS")
        xml.writeCharacters("\n")
        irProgram.st.allMemorySlabs().forEach{ slab ->
            emitLine("${slab.name} ${slab.size} ${slab.align}")
        }
        xml.writeEndElement()
        xml.writeCharacters("\n")
    }
}
