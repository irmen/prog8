package prog8.vm


class Assembler {
    private val labels = mutableMapOf<String, Int>()
    private val placeholders = mutableMapOf<Int, String>()

    init {
        require(instructionFormats.size== Opcode.values().size)
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
                var address = parseValue(addr, 0)
                when(what) {
                    "str" -> {
                        val string = unescape(values.trim('"'))
                        memory.setString(address, string, false)
                    }
                    "strz" -> {
                        val string = unescape(values.trim('"'))
                        memory.setString(address, string, true)
                    }
                    "ubyte", "byte" -> {
                        val array = values.split(',').map { parseValue(it.trim(), 0) }
                        for (value in array) {
                            memory.setB(address, value.toUByte())
                            address++
                        }
                    }
                    "uword", "word" -> {
                        val array = values.split(',').map { parseValue(it.trim(), 0) }
                        for (value in array) {
                            memory.setW(address, value.toUShort())
                            address++
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
        val instructionPattern = Regex("""([a-z]+)(\.b|\.w)?(.*)""", RegexOption.IGNORE_CASE)
        val labelPattern = Regex("""_([a-z0-9\._]+):""")
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
                val opcode = Opcode.valueOf(instr.uppercase())
                var type: VmDataType? = convertType(typestr)
                val operands = rest.lowercase().split(",").toMutableList()
                var reg1: Int? = null
                var reg2: Int? = null
                var reg3: Int? = null
                var value: Int? = null
                var operand: String?
                if(operands.isNotEmpty() && operands[0].isNotEmpty()) {
                    operand = operands.removeFirst().trim()
                    if(operand[0]=='r')
                        reg1 = operand.substring(1).toInt()
                    else {
                        value = parseValue(operand, program.size)
                        operands.clear()
                    }
                    if(operands.isNotEmpty()) {
                        operand = operands.removeFirst().trim()
                        if(operand[0]=='r')
                            reg2 = operand.substring(1).toInt()
                        else {
                            value = parseValue(operand, program.size)
                            operands.clear()
                        }
                        if(operands.isNotEmpty()) {
                            operand = operands.removeFirst().trim()
                            if(operand[0]=='r')
                                reg3 = operand.substring(1).toInt()
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
                val format = instructionFormats.getValue(opcode)
                if(type==null && format.datatypes.isNotEmpty())
                    type= VmDataType.BYTE
                if(type!=null && type !in format.datatypes)
                    throw IllegalArgumentException("invalid type code for $line")
                if(format.reg1 && reg1==null)
                    throw IllegalArgumentException("needs reg1 for $line")
                if(format.reg2 && reg2==null)
                    throw IllegalArgumentException("needs reg2 for $line")
                if(format.reg3 && reg3==null)
                    throw IllegalArgumentException("needs reg3 for $line")
                if(format.value && value==null)
                    throw IllegalArgumentException("needs value for $line")
                if(!format.reg1 && reg1!=null)
                    throw IllegalArgumentException("invalid reg1 for $line")
                if(!format.reg2 && reg2!=null)
                    throw IllegalArgumentException("invalid reg2 for $line")
                if(!format.reg3 && reg3!=null)
                    throw IllegalArgumentException("invalid reg3 for $line")
                if(!format.value && value!=null)
                    throw IllegalArgumentException("invalid value for $line")
                program.add(Instruction(opcode, type, reg1, reg2, reg3, value))
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

    private fun parseValue(value: String, pc: Int): Int {
        if(value.startsWith('$'))
            return value.substring(1).toInt(16)
        if(value.startsWith('%'))
            return value.substring(1).toInt(2)
        if(value.startsWith("0x"))
            return value.substring(2).toInt(16)
        if(value.startsWith('_')) {
            placeholders[pc] = value.substring(1)
            return 0
        }
        return value.toInt()
    }

    private fun convertType(typestr: String): VmDataType? {
        return when(typestr.lowercase()) {
            "" -> null
            ".b" -> VmDataType.BYTE
            ".w" -> VmDataType.WORD
            else -> throw IllegalArgumentException("invalid type $typestr")
        }
    }

    private fun unescape(str: String): String {
        val result = mutableListOf<Char>()
        val iter = str.iterator()
        while(iter.hasNext()) {
            val c = iter.nextChar()
            if(c=='\\') {
                val ec = iter.nextChar()
                result.add(when(ec) {
                    '\\' -> '\\'
                    'n' -> '\n'
                    'r' -> '\r'
                    '"' -> '"'
                    '\'' -> '\''
                    'u' -> {
                        try {
                            "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                        } catch (sb: StringIndexOutOfBoundsException) {
                            throw IllegalArgumentException("invalid \\u escape sequence")
                        } catch (nf: NumberFormatException) {
                            throw IllegalArgumentException("invalid \\u escape sequence")
                        }
                    }
                    'x' -> {
                        try {
                            val hex = ("" + iter.nextChar() + iter.nextChar()).toInt(16)
                            hex.toChar()
                        } catch (sb: StringIndexOutOfBoundsException) {
                            throw IllegalArgumentException("invalid \\x escape sequence")
                        } catch (nf: NumberFormatException) {
                            throw IllegalArgumentException("invalid \\x escape sequence")
                        }
                    }
                    else -> throw IllegalArgumentException("invalid escape char in string: \\$ec")
                })
            } else {
                result.add(c)
            }
        }
        return result.joinToString("")
    }
}
