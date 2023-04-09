package prog8.intermediate

import prog8.code.*
import prog8.code.core.DataType
import prog8.code.core.InternalCompilerException


fun getTypeString(dt : DataType): String = when(dt) {
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

fun getTypeString(memvar: StMemVar): String = when(memvar.dt) {
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

fun getTypeString(variable : StStaticVariable): String = when(variable.dt) {
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
    val format: InstructionFormat =
        if(type !in formats) {
            type = IRDataType.BYTE
            if(type !in formats)
                formats.getValue(null)
            else
                formats.getValue(type)
        } else {
            formats.getValue(type)
        }

    // parse the operands
    val operands = if(rest.isBlank()) emptyList() else rest.split(",").map{ it.trim() }.toMutableList()
    var reg1: Int? = null
    var reg2: Int? = null
    var fpReg1: Int? = null
    var fpReg2: Int? = null
    var immediateInt: Int? = null
    var immediateFp: Float? = null
    var address: Int? = null
    var labelSymbol: String? = null

    fun parseValueOrPlaceholder(operand: String, location: Pair<IRCodeChunk, Int>?): Float? {
        return if(operand[0].isLetter()) {
            if(location!=null)
                placeholders[location] = operand
            null
        } else {
            parseIRValue(operand)
        }
    }

    operands.forEach { oper ->
        if(oper[0] == '&')
            throw IRParseException("address-of should be done with normal LOAD <symbol>")
        else if(oper[0] in "rR") {
            if(reg1==null) reg1 = oper.substring(1).toInt()
            else if(reg2==null) reg2 = oper.substring(1).toInt()
            else throw IRParseException("too many register operands")
        } else if (oper[0] in "fF" && oper[1] in "rR") {
            if(fpReg1==null) fpReg1 = oper.substring(2).toInt()
            else if(fpReg2==null) fpReg2 = oper.substring(2).toInt()
            else throw IRParseException("too many fp register operands")
        } else if (oper[0].isDigit() || oper[0] == '$' || oper[0]=='%' || oper[0]=='-' || oper.startsWith("0x")) {
            val value = parseIRValue(oper)
            if(format.immediate) {
                if(immediateInt==null && immediateFp==null) {
                    if (type == IRDataType.FLOAT)
                        immediateFp = value
                    else
                        immediateInt = value.toInt()
                } else {
                    address = value.toInt()
                }
            } else {
                address = value.toInt()
            }
        } else {
            if(!oper[0].isLetter())
                throw IRParseException("expected symbol name: $oper")
            labelSymbol = oper
            val value = parseValueOrPlaceholder(oper, location)
            if(value!=null)
                address = value.toInt()
        }
    }

    if(type!=null && type !in formats)
        throw IRParseException("invalid type code for $line")
    if(format.reg1!=OperandDirection.UNUSED && reg1==null)
        throw IRParseException("needs reg1 for $line")
    if(format.reg2!=OperandDirection.UNUSED && reg2==null)
        throw IRParseException("needs reg2 for $line")
    if(format.fpReg1!=OperandDirection.UNUSED && fpReg1==null)
        throw IRParseException("needs fpReg1 for $line")
    if(format.fpReg2!=OperandDirection.UNUSED && fpReg2==null)
        throw IRParseException("needs fpReg2 for $line")
    if(format.address!=OperandDirection.UNUSED && address==null && labelSymbol==null)
        throw IRParseException("needs address or symbol for $line")
    if(format.reg1==OperandDirection.UNUSED && reg1!=null)
        throw IRParseException("invalid reg1 for $line")
    if(format.reg2==OperandDirection.UNUSED && reg2!=null)
        throw IRParseException("invalid reg2 for $line")
    if(format.fpReg1==OperandDirection.UNUSED && fpReg1!=null)
        throw IRParseException("invalid fpReg1 for $line")
    if(format.fpReg2==OperandDirection.UNUSED && fpReg2!=null)
        throw IRParseException("invalid fpReg2 for $line")
    if(format.immediate) {
        if(immediateInt==null && immediateFp==null && labelSymbol==null)
            throw IRParseException("needs value or symbol for $line")
        when (type) {
            IRDataType.BYTE -> {
                if (immediateInt!=null && (immediateInt!! < -128 || immediateInt!! > 255))
                    throw IRParseException("immediate value out of range for byte: $immediateInt")
            }
            IRDataType.WORD -> {
                if (immediateInt!=null && (immediateInt!! < -32768 || immediateInt!! > 65535))
                    throw IRParseException("immediate value out of range for word: $immediateInt")
            }
            IRDataType.FLOAT -> {}
            null -> {}
        }
    }

    if(format.address!=OperandDirection.UNUSED && address==null && labelSymbol==null)
        throw IRParseException("requires address or symbol for $line")

    if(labelSymbol!=null) {
        if (labelSymbol!![0] == 'r' && labelSymbol!![1].isDigit())
            throw IRParseException("labelsymbol confused with register?: $labelSymbol")
    }

    if(opcode in OpcodesForCpuRegisters) {
        val reg = operands.last().lowercase()
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

    return left(IRInstruction(opcode, type, reg1, reg2, fpReg1, fpReg2, immediateInt, immediateFp, address, labelSymbol = labelSymbol))
}
