package prog8.intermediate

import prog8.code.*
import prog8.code.core.DataType
import prog8.code.core.InternalCompilerException


fun getTypeString(dt : DataType): String {
    return when(dt) {
        DataType.UBYTE -> "ubyte"
        DataType.BYTE -> "byte"
        DataType.UWORD -> "uword"
        DataType.WORD -> "word"
        DataType.FLOAT -> "float"
        DataType.ARRAY_UB, DataType.STR -> "ubyte[]"
        DataType.ARRAY_B -> "byte[]"
        DataType.ARRAY_UW -> "uword[]"
        DataType.ARRAY_W -> "word[]"
        DataType.ARRAY_F -> "float[]"
        else -> throw InternalCompilerException("weird dt")
    }
}

fun getTypeString(memvar: StMemVar): String {
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

fun getTypeString(variable : StStaticVariable): String {
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

fun convertIRType(typestr: String): IRDataType? {
    return when(typestr.lowercase()) {
        "" -> null
        ".b" -> IRDataType.BYTE
        ".w" -> IRDataType.WORD
        ".f" -> IRDataType.FLOAT
        else -> throw IRParseException("invalid type $typestr")
    }
}

fun parseIRValue(value: String): Float {
    return if(value.startsWith("-"))
        -parseIRValue(value.substring(1))
    else if(value.startsWith('$'))
        value.substring(1).toInt(16).toFloat()
    else if(value.startsWith('%'))
        value.substring(1).toInt(2).toFloat()
    else if(value.startsWith("0x"))
        value.substring(2).toInt(16).toFloat()
    else if(value.startsWith('_'))
        throw IRParseException("attempt to parse a label as numeric value")
    else if(value.startsWith('&'))
        throw IRParseException("address-of should be done with normal LOAD <symbol>")
    else
        return value.toFloat()
}


private val instructionPattern = Regex("""([a-z]+)(\.b|\.w|\.f)?(.*)""", RegexOption.IGNORE_CASE)
private val labelPattern = Regex("""_([a-zA-Z\d\._]+):""")

fun parseIRCodeLine(line: String, location: Pair<IRCodeChunk, Int>?, placeholders: MutableMap<Pair<IRCodeChunk, Int>, String>): Either<IRInstruction, String> {
    // Note: this function is used from multiple places:
    // the IR File Reader but also the VirtualMachine itself to make sense of any inline vmasm blocks.
    val labelmatch = labelPattern.matchEntire(line.trim())
    if(labelmatch!=null)
        return right(labelmatch.groupValues[1])     // it's a label.

    val match = instructionPattern.matchEntire(line)
        ?: throw IRParseException("invalid IR instruction: $line")
    val (instr, typestr, rest) = match.destructured
    val opcode = try {
        Opcode.valueOf(instr.uppercase())
    } catch (ax: IllegalArgumentException) {
        throw IRParseException("invalid vmasm instruction: $instr")
    }
    var type: IRDataType? = convertIRType(typestr)
    val formats = instructionFormats.getValue(opcode)
    val format: InstructionFormat
    if(type !in formats) {
        type = IRDataType.BYTE
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
    var labelSymbol: String? = null

    fun parseValueOrPlaceholder(operand: String, location: Pair<IRCodeChunk, Int>?, rest: String, restIndex: Int): Float? {
        return if(operand.startsWith('_')) {
            labelSymbol = rest.split(",")[restIndex].trim().drop(1)
            if(location!=null)
                placeholders[location] = labelSymbol!!
            null
        } else if(operand[0].isLetter()) {
            labelSymbol = rest.split(",")[restIndex].trim()
            if(location!=null)
                placeholders[location] = labelSymbol!!
            null
        } else {
            parseIRValue(operand)
        }
    }

    if(operands.isNotEmpty() && operands[0].isNotEmpty()) {
        operand = operands.removeFirst().trim()
        if(operand[0]=='r')
            reg1 = operand.substring(1).toInt()
        else if(operand[0]=='f' && operand[1]=='r')
            fpReg1 = operand.substring(2).toInt()
        else {
            value = parseValueOrPlaceholder(operand, location, rest, 0)
            operands.clear()
        }
        if(operands.isNotEmpty()) {
            operand = operands.removeFirst().trim()
            if(operand[0]=='r')
                reg2 = operand.substring(1).toInt()
            else if(operand[0]=='f' && operand[1]=='r')
                fpReg2 = operand.substring(2).toInt()
            else {
                value = parseValueOrPlaceholder(operand, location, rest, 1)
                operands.clear()
            }
            if(operands.isNotEmpty()) {
                operand = operands.removeFirst().trim()
                if(operand[0]=='r')
                    reg3 = operand.substring(1).toInt()
                else if(operand[0]=='f' && operand[1]=='r')
                    fpReg3 = operand.substring(2).toInt()
                else {
                    value = parseValueOrPlaceholder(operand, location, rest, 2)
                    operands.clear()
                }
                if(operands.isNotEmpty()) {
                    TODO("placeholder symbol? $operands  rest=$rest'")
                    // operands.clear()
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
        throw IRParseException("too many reg arguments $line")
    if(fpReg3!=null)
        throw IRParseException("too many fpreg arguments $line")

    if(type!=null && type !in formats)
        throw IRParseException("invalid type code for $line")
    if(format.reg1!=OperandDirection.UNUSED && reg1==null)
        throw IRParseException("needs reg1 for $line")
    if(format.reg2!=OperandDirection.UNUSED && reg2==null)
        throw IRParseException("needs reg2 for $line")
    if(format.valueIn && value==null && labelSymbol==null)
        throw IRParseException("needs value or symbol for $line")
    if(format.reg1==OperandDirection.UNUSED && reg1!=null)
        throw IRParseException("invalid reg1 for $line")
    if(format.reg2==OperandDirection.UNUSED && reg2!=null)
        throw IRParseException("invalid reg2 for $line")
    if(value!=null && opcode !in OpcodesWithAddress) {
        when (type) {
            IRDataType.BYTE -> {
                if (value < -128 || value > 255)
                    throw IRParseException("value out of range for byte: $value")
            }
            IRDataType.WORD -> {
                if (value < -32768 || value > 65535)
                    throw IRParseException("value out of range for word: $value")
            }
            IRDataType.FLOAT -> {}
            null -> {}
        }
    }
    var floatValue: Float? = null
    var intValue: Int? = null

    if(format.valueIn && value!=null)
        intValue = value.toInt()
    if(format.fpValueIn && value!=null)
        floatValue = value

    if(opcode in OpcodesForCpuRegisters) {
        val regStr = rest.split(',').last().lowercase().trim()
        val reg = if(regStr.startsWith('_')) regStr.substring(1) else regStr
        if(reg !in setOf(
                "a", "x", "y",
                "ax", "ay", "xy",
                "r0", "r1", "r2", "r3",
                "r4", "r5", "r6", "r7",
                "r8", "r9", "r10","r11",
                "r12", "r13", "r14", "r15",
                "pc", "pz", "pv","pn"))
            throw IRParseException("invalid cpu reg: $reg")

        return left(IRInstruction(opcode, type, reg1, labelSymbol = reg))
    }

    return left(IRInstruction(opcode, type, reg1, reg2, fpReg1, fpReg2, intValue, floatValue, labelSymbol = labelSymbol))
}
