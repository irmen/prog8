package prog8.intermediate

import prog8.Either
import prog8.code.core.*
import prog8.left
import prog8.right


fun DataType.irTypeString(length: UInt?): String {
    val lengthStr = if(length==0u) "" else length.toString()
    return when (this.base) {
        BaseDataType.BOOL -> "bool"
        BaseDataType.UBYTE -> "ubyte"
        BaseDataType.BYTE -> "byte"
        BaseDataType.UWORD -> "uword"
        BaseDataType.WORD -> "word"
        BaseDataType.LONG -> "long"
        BaseDataType.FLOAT -> "float"
        BaseDataType.STR -> "ubyte[$lengthStr]"             // here string doesn't exist as a seperate datatype anymore
        BaseDataType.POINTER -> {
            if(sub!=null)
                "^${sub!!.name.lowercase()}"
            else
                "^${subType!!.scopedNameString}"
        }
        BaseDataType.STRUCT_INSTANCE -> {
            if(sub!=null)
                sub!!.name.lowercase()
            else
                subType!!.scopedNameString
        }
        BaseDataType.ARRAY_POINTER -> {
            if(sub!=null)
                "^${sub!!.name.lowercase()}[$lengthStr]"
            else
                "^${subType!!.scopedNameString}[$lengthStr]"
        }
        BaseDataType.ARRAY -> {
            when(this.sub) {
                BaseDataType.UBYTE -> "ubyte[$lengthStr]"
                BaseDataType.UWORD -> "uword[$lengthStr]"
                BaseDataType.BYTE -> "byte[$lengthStr]"
                BaseDataType.WORD -> "word[$lengthStr]"
                BaseDataType.BOOL -> "bool[$lengthStr]"
                BaseDataType.FLOAT -> "float[$lengthStr]"
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.ARRAY_SPLITW -> {
            when(this.sub) {
                BaseDataType.UWORD -> "uword[$lengthStr]"       // should be 2 separate byte arrays by now really?
                BaseDataType.WORD -> "word[$lengthStr]"          // should be 2 separate byte arrays by now really?
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        BaseDataType.UNDEFINED -> throw IllegalArgumentException("wrong dt")
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

fun parseIRValue(value: String): Double {
    return if(value.startsWith("-"))
        -parseIRValue(value.substring(1))
    else if(value.startsWith('$'))
        value.substring(1).toInt(16).toDouble()
    else if(value.startsWith('%'))
        value.substring(1).toInt(2).toDouble()
    else if(value.startsWith("0x"))
        value.substring(2).toInt(16).toDouble()
    else if(value.startsWith('_'))
        throw IRParseException("attempt to parse a label as numeric value")
    else if(value.startsWith('&'))
        throw IRParseException("address-of should be done with normal LOAD <symbol>")
    else if(value.startsWith('@'))
        throw IRParseException("address-of @ should have been handled earlier")
    else
        return value.toDouble()
}


private val instructionPattern = Regex("""([a-z]+)(\.b|\.w|\.f)?(.*)""", RegexOption.IGNORE_CASE)
private val labelPattern = Regex("""_([a-zA-Z\d\._]+):""")

fun parseIRCodeLine(line: String): Either<IRInstruction, String> {
    val labelmatch = labelPattern.matchEntire(line.trim())
    if(labelmatch!=null)
        return right(labelmatch.groupValues[1])     // it's a label.

    val match = instructionPattern.matchEntire(line)
        ?: throw IRParseException("invalid IR instruction: $line")
    val (instr, typestr, rest) = match.destructured
    val opcode = try {
        Opcode.valueOf(instr.uppercase())
    } catch (_: IllegalArgumentException) {
        throw IRParseException("invalid IR instruction: $instr")
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
    var reg3: Int? = null
    var fpReg1: Int? = null
    var fpReg2: Int? = null
    var immediateInt: Int? = null
    var immediateFp: Double? = null
    var address: Int? = null
    var labelSymbol: String? = null

    fun parseValueOrPlaceholder(operand: String): Double? {
        return if(operand[0].isLetter()) {
            null
        } else {
            parseIRValue(operand)
        }
    }
    if(format.sysCall) {
        val call = parseCall(rest)
        val syscallNum = call.address ?: parseIRValue(call.target ?: "").toInt()
        return left(IRInstruction(Opcode.SYSCALL, immediate = syscallNum, fcallArgs = FunctionCallArgs(call.args, call.returns)))
    } else if (format.funcCall) {
        val call = parseCall(rest)
        return left(IRInstruction(Opcode.CALL, address = call.address, labelSymbol = call.target, fcallArgs = FunctionCallArgs(call.args, call.returns)))
    } else {
        operands.forEach { oper ->
            if (oper[0] == '&')
                throw IRParseException("address-of should be done with normal LOAD <symbol>")
            else if (isRegisterName(oper)) {
                if (reg1 == null) reg1 = oper.substring(1).toInt()
                else if (reg2 == null) reg2 = oper.substring(1).toInt()
                else if (reg3 == null) reg3 = oper.substring(1).toInt()
                else throw IRParseException("too many register operands")
            } else if (isFloatRegisterName(oper)) {
                if (fpReg1 == null) fpReg1 = oper.substring(2).toInt()
                else if (fpReg2 == null) fpReg2 = oper.substring(2).toInt()
                else throw IRParseException("too many fp register operands")
            } else if (oper[0] in "0123456789$%-#" || oper.startsWith("0x")) {
                val value = if(oper[0]=='#') parseIRValue(oper.drop(1)) else parseIRValue(oper)
                if (format.immediate) {
                    if (immediateInt == null && immediateFp == null) {
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
                if (!oper[0].isLetter())
                    throw IRParseException("expected symbol name: $oper")
                labelSymbol = oper
                val value = parseValueOrPlaceholder(oper)
                if (value != null)
                    address = value.toInt()
            }
        }
    }

    if(type!=null && type !in formats)
        throw IRParseException("invalid type code for $line")
    if(format.reg1!=OperandDirection.UNUSED && reg1==null)
        throw IRParseException("needs reg1 for $line")
    if(format.reg2!=OperandDirection.UNUSED && reg2==null)
        throw IRParseException("needs reg2 for $line")
    if(format.reg3!=OperandDirection.UNUSED && reg3==null)
        throw IRParseException("needs reg3 for $line")
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
    if(format.reg3==OperandDirection.UNUSED && reg3!=null)
        throw IRParseException("invalid reg3 for $line")
    if(format.fpReg1==OperandDirection.UNUSED && fpReg1!=null)
        throw IRParseException("invalid fpReg1 for $line")
    if(format.fpReg2==OperandDirection.UNUSED && fpReg2!=null)
        throw IRParseException("invalid fpReg2 for $line")
    if(format.immediate && opcode!=Opcode.SYSCALL) {
        if(immediateInt==null && immediateFp==null && labelSymbol==null)
            throw IRParseException("needs value or symbol for $line")
        when (type) {
            IRDataType.BYTE -> {
                if (immediateInt!=null && (immediateInt < -128 || immediateInt > 255))
                    throw IRParseException("immediate value out of range for byte: $immediateInt")
            }
            IRDataType.WORD -> {
                if (immediateInt!=null && (immediateInt < -32768 || immediateInt > 65535))
                    throw IRParseException("immediate value out of range for word: $immediateInt")
            }
            IRDataType.FLOAT -> {}
            null -> {}
        }
    }

    if(format.address!=OperandDirection.UNUSED && address==null && labelSymbol==null)
        throw IRParseException("requires address or symbol for $line")

    var offset: Int? = null
    if(labelSymbol!=null) {
        if (labelSymbol[0] == 'r' && labelSymbol[1].isDigit())
            throw IRParseException("labelsymbol confused with register?: $labelSymbol")
        if('+' in labelSymbol) {
            val offsetStr = labelSymbol.substringAfterLast('+')
            if (offsetStr.isNotEmpty()) {
                offset = offsetStr.toInt()
                labelSymbol = labelSymbol.substringBeforeLast('+')
            }
        }
    }

    return left(IRInstruction(opcode, type, reg1, reg2, reg3, fpReg1, fpReg2, immediateInt, immediateFp, address, labelSymbol = labelSymbol, symbolOffset = offset))
}

private fun isRegisterName(oper: String): Boolean {
    if(oper[0] in "rR") {
        try {
            oper.substring(1).toInt()
            return true
        } catch(_: NumberFormatException) {
            return false
        }
    }
    return false
}

private fun isFloatRegisterName(oper: String): Boolean {
    if(oper[0] in "fF" && oper[1] in "rR") {
        try {
            oper.substring(2).toInt()
            return true
        } catch(_: NumberFormatException) {
            return false
        }
    }
    return false
}

private class ParsedCall(
    val target: String?,
    val address: Int?,
    val args: List<FunctionCallArgs.ArgumentSpec>,
    val returns: List<FunctionCallArgs.RegSpec>
)

private fun parseCall(rest: String): ParsedCall {

    fun parseRegspec(reg: String): FunctionCallArgs.RegSpec {
        val pattern = Regex("f?r([0-9]+)\\.(.)(@.{1,4})?$")
        val match = pattern.matchEntire(reg) ?: throw IRParseException("invalid regspec $reg")
        val num =  match.groups[1]!!.value.toInt()
        val type = when(match.groups[2]!!.value) {
            "b" -> IRDataType.BYTE
            "w" -> IRDataType.WORD
            "f" -> IRDataType.FLOAT
            else -> throw IRParseException("invalid type spec in $reg")
        }
        val cpuRegister: RegisterOrStatusflag? =
            if(match.groups[3]!=null) {
                val cpuRegStr = match.groups[3]!!.value.drop(1)
                parseRegisterOrStatusflag(cpuRegStr)
            } else null
        return FunctionCallArgs.RegSpec(type, num, cpuRegister)
    }

    fun parseReturnRegspec(regs: String?): List<FunctionCallArgs.RegSpec> {
        if(regs==null)
            return emptyList()
        return regs.split(',').map { reg->
            if (reg.startsWith('@')) {
                FunctionCallArgs.RegSpec(IRDataType.BYTE, -1, parseRegisterOrStatusflag(reg.drop(1)))
            } else {
                parseRegspec(reg)
            }
        }
    }

    fun parseArgs(args: String): List<FunctionCallArgs.ArgumentSpec> {
        if(args.isBlank())
            return emptyList()
        return args.split(',').map {
            if(it.contains('=')) {
                val (argVar, argReg) = it.split('=')
                FunctionCallArgs.ArgumentSpec(argVar, null, parseRegspec(argReg))   // address will be set later
            } else {
                FunctionCallArgs.ArgumentSpec("", null, parseRegspec(it))   // address will be set later
            }
        }
    }

    val pattern = Regex("(?<target>.+?)\\((?<arglist>.*?)\\)(:(?<returns>.+?))?")
    val match = pattern.matchEntire(rest.replace(" ","")) ?: throw IRParseException("invalid call spec $rest")
    val target = match.groups["target"]!!.value
    val args = match.groups["arglist"]!!.value
    val arguments = parseArgs(args)
    val returns = match.groups["returns"]?.value
    var address: Int? = null
    var actualTarget: String? = target

    if(target.startsWith('$') || target[0].isDigit()) {
        address = parseIRValue(target).toInt()
        actualTarget = null
    }

    return ParsedCall(
        actualTarget,
        address,
        arguments,
        parseReturnRegspec(returns)
    )
}


internal fun parseRegisterOrStatusflag(sourceregs: String): RegisterOrStatusflag {
    var reg: RegisterOrPair? = null
    var sf: Statusflag? = null

    val regs = if(sourceregs.endsWith(".b") || sourceregs.endsWith(".w") || sourceregs.endsWith(".f"))
        sourceregs.dropLast(2) else sourceregs

    try {
        reg = RegisterOrPair.valueOf(regs)
    } catch (_: IllegalArgumentException) {
        try {
            sf = Statusflag.valueOf(regs)
        } catch(_: IllegalArgumentException) {
            throw IRParseException("invalid IR register or statusflag: $regs")
        }
    }
    return RegisterOrStatusflag(reg, sf)
}


fun irType(type: DataType): IRDataType {
    if(type.base.isPassByRef)
        return IRDataType.WORD

    return when(type.base) {
        BaseDataType.BOOL,
        BaseDataType.UBYTE,
        BaseDataType.BYTE -> IRDataType.BYTE
        BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.POINTER -> IRDataType.WORD
        BaseDataType.FLOAT -> IRDataType.FLOAT
        BaseDataType.STRUCT_INSTANCE -> TODO("IR datatype for struct instances")
        else -> throw AssemblyError("no IR datatype for $type")
    }
}
