package prog8.vm

import prog8.code.core.unescape


class Assembler {
    private val labels = mutableMapOf<String, Int>()
    private val placeholders = mutableMapOf<Int, String>()

    init {
        require(instructionFormats.size== Opcode.values().size) {
            "missing " + (Opcode.values().toSet() - instructionFormats.keys)
        }
    }

    fun initializeMemory(memsrc: String, memory: Memory) {
        val instrPattern = Regex("""(.+?)\s+([a-z]+)\s+(.+)""", RegexOption.IGNORE_CASE)
        for(line in memsrc.lines()) {
            if(line.isBlank() || line.startsWith(';'))
                continue
            val match = instrPattern.matchEntire(line.trim())
            if(match==null)
                throw IllegalArgumentException("invalid line $line")
            else {
                val (_, addr, what, values) = match.groupValues
                var address = parseValue(addr, 0).toInt()
                when(what) {
                    "str" -> {
                        val string = values.trim('"').unescape()
                        memory.setString(address, string, false)
                    }
                    "strz" -> {
                        val string = values.trim('"').unescape()
                        memory.setString(address, string, true)
                    }
                    "ubyte", "byte" -> {
                        val array = values.split(',').map { parseValue(it.trim(), 0).toInt() }
                        for (value in array) {
                            memory.setUB(address, value.toUByte())
                            address++
                        }
                    }
                    "uword", "word" -> {
                        val array = values.split(',').map { parseValue(it.trim(), 0).toInt() }
                        for (value in array) {
                            memory.setUW(address, value.toUShort())
                            address += 2
                        }
                    }
                    "float" -> {
                        val array = values.split(',').map { it.toFloat() }
                        for (value in array) {
                            memory.setFloat(address, value)
                            address += 4    // 32-bits floats
                        }
                    }
                    else -> throw IllegalArgumentException("mem instr $what")
                }
            }
        }
    }

    fun assembleProgram(source: CharSequence): List<Instruction> {
        labels.clear()
        placeholders.clear()
        val program = mutableListOf<Instruction>()
        val instructionPattern = Regex("""([a-z]+)(\.b|\.w|\.f)?(.*)""", RegexOption.IGNORE_CASE)
        val labelPattern = Regex("""_([a-zA-Z\d\._]+):""")
        for (line in source.lines()) {
            if(line.isBlank() || line.startsWith(';'))
                continue
            val match = instructionPattern.matchEntire(line.trim())
            if(match==null) {
                val labelmatch = labelPattern.matchEntire(line.trim())
                if(labelmatch==null)
                    throw IllegalArgumentException("invalid line $line at line ${program.size+1}")
                else {
                    val label = labelmatch.groupValues[1]
                    if(label in labels)
                        throw IllegalArgumentException("label redefined $label")
                    labels[label] = program.size
                }
            } else {
                val (_, instr, typestr, rest) = match.groupValues
                if(instr=="incbin") {
                    println("warning: ignoring incbin command: $rest")
                    continue
                }
                val opcode = try {
                    Opcode.valueOf(instr.uppercase())
                } catch (ax: IllegalArgumentException) {
                    throw IllegalArgumentException("invalid vmasm instruction: $instr", ax)
                }
                var type: VmDataType? = convertType(typestr)
                val formats = instructionFormats.getValue(opcode)
                val format: InstructionFormat
                if(type !in formats) {
                    type = VmDataType.BYTE
                    format = if(type !in formats)
                        formats.getValue(null)
                    else
                        formats.getValue(type)
                } else {
                    format = formats.getValue(type)
                }
                // parse the operands
                val operands = rest.lowercase().split(",").toMutableList()
                var reg1: Int? = null
                var reg2: Int? = null
                var reg3: Int? = null
                var fpReg1: Int? = null
                var fpReg2: Int? = null
                var fpReg3: Int? = null
                var value: Float? = null
                var operand: String?
                if(operands.isNotEmpty() && operands[0].isNotEmpty()) {
                    operand = operands.removeFirst().trim()
                    if(operand[0]=='r')
                        reg1 = operand.substring(1).toInt()
                    else if(operand[0]=='f' && operand[1]=='r')
                        fpReg1 = operand.substring(2).toInt()
                    else {
                        value = if(operand.startsWith('_')) {
                            // it's a label, keep the original case!
                            val labelname = rest.split(",").first().trim()
                            parseValue(labelname, program.size)
                        } else {
                            parseValue(operand, program.size)
                        }
                        operands.clear()
                    }
                    if(operands.isNotEmpty()) {
                        operand = operands.removeFirst().trim()
                        if(operand[0]=='r')
                            reg2 = operand.substring(1).toInt()
                        else if(operand[0]=='f' && operand[1]=='r')
                            fpReg2 = operand.substring(2).toInt()
                        else {
                            value = parseValue(operand, program.size)
                            operands.clear()
                        }
                        if(operands.isNotEmpty()) {
                            operand = operands.removeFirst().trim()
                            if(operand[0]=='r')
                                reg3 = operand.substring(1).toInt()
                            else if(operand[0]=='f' && operand[1]=='r')
                                fpReg3 = operand.substring(2).toInt()
                            else {
                                value = parseValue(operand, program.size)
                                operands.clear()
                            }
                            if(operands.isNotEmpty()) {
                                operand = operands.removeFirst().trim()
                                value = parseValue(operand, program.size)
                                operands.clear()
                            }
                        }
                    }
                }

                // shift the operands back into place
                while(reg1==null && reg2!=null) {
                    reg1 = reg2
                    reg2 = reg3
                    reg3 = null
                }
                while(fpReg1==null && fpReg2!=null) {
                    fpReg1 = fpReg2
                    fpReg2 = fpReg3
                    fpReg3 = null
                }
                if(reg3!=null)
                    throw IllegalArgumentException("too many reg arguments $line")
                if(fpReg3!=null)
                    throw IllegalArgumentException("too many fpreg arguments $line")

                if(type!=null && type !in formats)
                    throw IllegalArgumentException("invalid type code for $line")
                if(format.reg1 && reg1==null)
                    throw IllegalArgumentException("needs reg1 for $line")
                if(format.reg2 && reg2==null)
                    throw IllegalArgumentException("needs reg2 for $line")
                if(format.value && value==null)
                    throw IllegalArgumentException("needs value for $line")
                if(!format.reg1 && reg1!=null)
                    throw IllegalArgumentException("invalid reg1 for $line")
                if(!format.reg2 && reg2!=null)
                    throw IllegalArgumentException("invalid reg2 for $line")
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
                        VmDataType.FLOAT -> {}
                        null -> {}
                    }
                }
                var floatValue: Float? = null
                var intValue: Int? = null

                if(format.value)
                    intValue = value!!.toInt()
                if(format.fpValue)
                    floatValue = value!!

                program.add(Instruction(opcode, type, reg1, reg2, fpReg1, fpReg2, intValue, floatValue))
            }
        }

        pass2replaceLabels(program)
        return program
    }

    private fun pass2replaceLabels(program: MutableList<Instruction>) {
        for((line, label) in placeholders) {
            val replacement = labels.getValue(label)
            program[line] = program[line].copy(value = replacement)
        }
    }

    private fun parseValue(value: String, pc: Int): Float {
        if(value.startsWith("-")) {
            return -parseValue(value.substring(1), pc)
        }
        if(value.startsWith('$'))
            return value.substring(1).toInt(16).toFloat()
        if(value.startsWith('%'))
            return value.substring(1).toInt(2).toFloat()
        if(value.startsWith("0x"))
            return value.substring(2).toInt(16).toFloat()
        if(value.startsWith('_')) {
            placeholders[pc] = value.substring(1)
            return 0f
        }
        return value.toFloat()
    }

    private fun convertType(typestr: String): VmDataType? {
        return when(typestr.lowercase()) {
            "" -> null
            ".b" -> VmDataType.BYTE
            ".w" -> VmDataType.WORD
            ".f" -> VmDataType.FLOAT
            else -> throw IllegalArgumentException("invalid type $typestr")
        }
    }
}
