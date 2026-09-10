package prog8.intermediate

import prog8.code.core.Statusflag
import prog8.code.core.toHex

/*
The canonical text codec for IR instructions: it parses and prints the textual form of an
instruction, driven by the OpcodeSchema. The printed form always round-trips back to an
identical instruction.
*/


sealed interface IRToken {
    val text: String

    data class Register(override val text: String) : IRToken
    data class Immediate(override val text: String) : IRToken
    data class Memory(override val text: String) : IRToken
    data class CodeTarget(override val text: String) : IRToken
    data class HardwareSlot(override val text: String) : IRToken
    data class StatusFlag(override val text: String) : IRToken
}


object IRTextCodec {

    private val instructionPattern = Regex("""([a-zA-Z_]+)(\.b|\.w|\.l|\.f|\.p)?\s*(.*)""", RegexOption.IGNORE_CASE)
    private val typeSuffixPattern = Regex("""(.+)\.([bwlfp])""", RegexOption.IGNORE_CASE)
    private val registerPattern = Regex("""(f?)r(\d+)""", RegexOption.IGNORE_CASE)
    private val hardwareSlotPattern = Regex("""s(\d+)""", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- printing

    fun print(instruction: IRInstruction): String = buildString {
        append(instruction.opcode.name.lowercase())
        instruction.type?.let { append('.' + typeSuffix(it)) }
        val schema = instruction.schema
        val operands = schema.slots.mapNotNull { slot -> printSlot(instruction, slot) }
        if (operands.isNotEmpty()) {
            append(' ')
            append(operands.joinToString(","))
        }
    }

    private fun printSlot(instruction: IRInstruction, slot: SlotSchema): String? = when (slot) {
        is RegisterSlotSchema -> when (slot.slot) {
            InstructionSlot.DEST -> instruction.dest?.let { printRegister(it) }
            InstructionSlot.DEST_B -> instruction.destB?.let { printRegister(it) }
            InstructionSlot.SRC_A -> instruction.srcA?.let { printRegister(it) }
            InstructionSlot.SRC_B -> instruction.srcB?.let { printRegister(it) }
            else -> null
        }
        is ImmediateSlotSchema -> instruction.immediate?.let { printImmediate(it) }
        is MemorySlotSchema -> instruction.memory?.let { printMemory(it) }
        is HardwareSlotSchema -> instruction.hardwareSlot?.let { printHardwareSlot(it) }
        is TargetSlotSchema -> instruction.target?.let { printCodeReference(it) }
        is CallSiteSlotSchema -> instruction.callSite?.let { printCallSite(it) }
    }

    fun typeSuffix(type: IRDataType): String = when (type) {
        IRDataType.BYTE -> "b"
        IRDataType.WORD -> "w"
        IRDataType.LONG -> "l"
        IRDataType.FLOAT -> "f"
        IRDataType.POINTER -> "p"
    }

    fun printRegister(operand: RegisterOperand): String =
        "${operand.register}.${typeSuffix(operand.type)}"

    fun printImmediate(immediate: ImmediateOperand): String = when (immediate) {
        is ImmediateOperand.Integer -> "#${immediate.value.toHex()}.${typeSuffix(immediate.type)}"
        is ImmediateOperand.FloatValue -> "#${immediate.value}.f"
        is ImmediateOperand.SymbolAddress ->
            if (immediate.offset == 0) "#${immediate.symbol}" else "#${immediate.symbol}+${immediate.offset}"
    }

    fun printMemory(memory: MemoryReference): String = when (memory) {
        is MemoryReference.Direct -> buildString {
            append('[')
            append(printAddressBase(memory.base))
            if (memory.displacement != 0) append("+${memory.displacement}")
            append(']')
        }
        is MemoryReference.Indexed -> buildString {
            append('[')
            append(printAddressBase(memory.base))
            if (memory.displacement != 0) append("+${memory.displacement}")
            append('+')
            append(printRegister(memory.index))
            if (memory.scale != 1) append("*${memory.scale}")
            append(']')
        }
        is MemoryReference.Indirect -> buildString {
            append('[')
            append(printRegister(memory.pointer))
            if (memory.displacement != 0) append("+${memory.displacement}")
            append(']')
        }
    }

    private fun printAddressBase(base: AddressBase): String = when (base) {
        is AddressBase.Symbol -> base.name
        is AddressBase.Absolute -> base.address.toHex()
    }

    fun printHardwareSlot(operand: HardwareSlotOperand): String =
        "s${operand.slot.value}.${typeSuffix(operand.type)}"

    fun printCodeReference(reference: CodeReference): String = when (reference) {
        is CodeReference.Label -> if (reference.offset == 0) reference.name else "${reference.name}+${reference.offset}"
        is CodeReference.Absolute -> reference.address.toHex()
        is CodeReference.Indirect -> "(${printRegister(reference.pointer)})"
    }

    fun printCallSite(site: CallSite): String = buildString {
        when (val target = site.target) {
            is CallTarget.Direct -> {
                append(printCodeReference(target.reference))
                target.externalName?.let { append(",$it") }
            }
            is CallTarget.Banked -> {
                append("#${target.bank.toHex()},")
                append(printCodeReference(target.reference))
                target.externalName?.let { append(",$it") }
            }
            is CallTarget.BankedVariable -> {
                append(printRegister(target.bankRegister))
                append(',')
                append(printCodeReference(target.reference))
                target.externalName?.let { append(",$it") }
            }
            is CallTarget.AmigaLibrary -> {
                append("#${target.library.toHex()},")
                append(target.lvo.toString())
                target.name?.let { append(",$it") }
            }
            is CallTarget.SystemCall -> append(target.number.toHex())
        }
        append('(')
        append(site.arguments.joinToString(",") { printCallArgument(it) })
        append(')')
        if (site.results.isNotEmpty()) {
            append(':')
            append(site.results.joinToString(",") { printCallResult(it) })
        }
        if (site.effects != CallEffects.DEFAULT) {
            append(" !memory=")
            append(site.effects.memoryEffect.name.lowercase())
            append(",status=")
            append(site.effects.statusEffect.name.lowercase())
        }
    }

    private fun printCallLocationPrefix(location: CallLocation): String = when (location) {
        is CallLocation.ParameterMemory ->
            if (location.name.isNotBlank()) "${location.name}="
            else location.address?.let { "${it.toHex()}=" } ?: ""
        else -> ""
    }

    private fun printCallLocationSuffix(location: CallLocation): String = when (location) {
        is CallLocation.HardwareRegister -> "@s${location.slot.value}"
        is CallLocation.StatusFlag -> "@${location.flag}"
        else -> ""
    }

    private fun printCallArgument(argument: CallArgument): String =
        printCallLocationPrefix(argument.location) + printRegister(argument.source) + printCallLocationSuffix(argument.location)

    private fun printCallResult(result: CallResult): String {
        val destination = result.destination?.let { printRegister(it) } ?: ""
        val location = printCallLocationSuffix(result.location)
        return if (destination.isEmpty()) location else destination + location
    }

    // ---------------------------------------------------------------- parsing

    fun parse(line: String): IRInstruction = try {
        parseInternal(line)
    } catch (ex: IRParseException) {
        throw ex
    } catch (ex: IllegalArgumentException) {
        throw IRParseException(ex.message ?: "invalid IR instruction: $line")
    }

    private fun parseInternal(line: String): IRInstruction {
        val trimmed = line.trim()
        val match = instructionPattern.matchEntire(trimmed) ?: throw IRParseException("invalid IR instruction: $line")
        val (instr, typestr, rest) = match.destructured
        val opcode = try {
            Opcode.valueOf(instr.uppercase())
        } catch (_: IllegalArgumentException) {
            throw IRParseException("invalid IR instruction: $instr")
        }

        val availableTypes = OpcodeSchemas.typesFor(opcode)
        var type = convertIRType(typestr)
        if (type == null) {
            if (null !in availableTypes)
                type = if (IRDataType.BYTE in availableTypes) IRDataType.BYTE else availableTypes.first()
        }
        if (type !in availableTypes)
            throw IRParseException("invalid type $typestr for $opcode")
        val schema = OpcodeSchemas.get(opcode, type)

        if (schema.callSlot != null)
            return IRInstruction(opcode, type, callSite = parseCallSite(schema.callSlot.kind, rest, opcode))

        val tokens = tokenizeOperands(rest)
        if (tokens.size != schema.slots.size)
            throw IRParseException("$opcode expects ${schema.slots.size} operands but got ${tokens.size} in: $line")

        var dest: RegisterOperand? = null
        var destB: RegisterOperand? = null
        var srcA: RegisterOperand? = null
        var srcB: RegisterOperand? = null
        var immediate: ImmediateOperand? = null
        var hardwareSlot: HardwareSlotOperand? = null
        var memory: MemoryReference? = null
        var target: CodeReference? = null

        for ((slotSchema, token) in schema.slots.zip(tokens)) {
            when (slotSchema) {
                is RegisterSlotSchema -> {
                    val operand = parseRegisterOperand(token.text, slotSchema, schema)
                    when (slotSchema.slot) {
                        InstructionSlot.DEST -> dest = operand
                        InstructionSlot.DEST_B -> destB = operand
                        InstructionSlot.SRC_A -> srcA = operand
                        InstructionSlot.SRC_B -> srcB = operand
                        else -> throw IRParseException("invalid register slot ${slotSchema.slot}")
                    }
                }
                is ImmediateSlotSchema -> immediate = parseImmediate(token.text, slotSchema, schema)
                is MemorySlotSchema -> memory = parseMemory(token.text, slotSchema, schema)
                is HardwareSlotSchema -> hardwareSlot = parseHardwareSlot(token.text, schema)
                is TargetSlotSchema -> target = parseCodeReference(token.text, slotSchema.kind)
                is CallSiteSlotSchema -> throw IRParseException("unexpected call operand")
            }
        }

        return IRInstruction(opcode, type, dest, destB, srcA, srcB, immediate, hardwareSlot, memory, target)
    }

    /** Tokenize operands while preserving bracketed memory and parenthesized code targets. */
    private fun tokenizeOperands(rest: String): List<IRToken> {
        val trimmed = rest.trim()
        if (trimmed.isEmpty())
            return emptyList()
        return splitTopLevel(trimmed, ',').map { raw ->
            val text = raw.trim()
            if (text.isEmpty())
                throw IRParseException("empty IR operand in: $rest")
            val (withoutType, _) = stripTypeSuffix(text)
            when {
                text.startsWith('#') -> IRToken.Immediate(text)
                text.startsWith('[') && text.endsWith(']') -> IRToken.Memory(text)
                text.startsWith('(') && text.endsWith(')') -> IRToken.CodeTarget(text)
                hardwareSlotPattern.matchEntire(withoutType) != null -> IRToken.HardwareSlot(text)
                registerPattern.matchEntire(withoutType) != null -> IRToken.Register(text)
                else -> IRToken.CodeTarget(text)
            }
        }
    }

    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        val delimiters = ArrayDeque<Char>()
        val current = StringBuilder()
        for (char in text) {
            when (char) {
                '[', '(' -> {
                    delimiters.addLast(char)
                    current.append(char)
                }
                ']', ')' -> {
                    val expected = if (char == ']') '[' else '('
                    if (delimiters.removeLastOrNull() != expected)
                        throw IRParseException("unbalanced delimiters in: $text")
                    current.append(char)
                }
                separator -> if (delimiters.isEmpty()) {
                    result.add(current.toString())
                    current.clear()
                } else current.append(char)
                else -> current.append(char)
            }
        }
        if (delimiters.isNotEmpty())
            throw IRParseException("unbalanced delimiters in: $text")
        result.add(current.toString())
        return result
    }

    private fun stripTypeSuffix(text: String): Pair<String, IRDataType?> {
        val match = typeSuffixPattern.matchEntire(text) ?: return text to null
        return match.groupValues[1] to convertIRType("." + match.groupValues[2].lowercase())
    }

    private fun parseRegisterOperand(token: String, slotSchema: RegisterSlotSchema, schema: OpcodeSchema): RegisterOperand {
        val (regtext, explicitType) = stripTypeSuffix(token)
        val match = registerPattern.matchEntire(regtext)
            ?: throw IRParseException("${schema.opcode}: expected a register operand, got: $token")
        val isFloat = match.groupValues[1].isNotEmpty()
        val number = match.groupValues[2].toInt()
        when (slotSchema.registerFile) {
            RegisterFile.INTEGER -> if (isFloat) throw IRParseException("${schema.opcode}: expected an integer register, got: $token")
            RegisterFile.FLOAT -> if (!isFloat) throw IRParseException("${schema.opcode}: expected a float register, got: $token")
        }
        val type = explicitType
            ?: throw IRParseException("${schema.opcode}: register operand needs an explicit type: $token")
        val register = if (isFloat) VirtualRegister.float(number) else VirtualRegister.int(number)
        return RegisterOperand(register, type, slotSchema.role, slotSchema.direction)
    }

    private fun parseImmediate(token: String, slotSchema: ImmediateSlotSchema, schema: OpcodeSchema): ImmediateOperand {
        if (!token.startsWith('#'))
            throw IRParseException("${schema.opcode}: immediate operand must start with #: $token")
        val text = token.drop(1).trim()
        if (text.isEmpty())
            throw IRParseException("${schema.opcode}: empty immediate operand")
        if (text[0].isLetter() || text[0] == '_') {
            if (!slotSchema.allowSymbolAddress)
                throw IRParseException("${schema.opcode}: symbol address not allowed as immediate: $token")
            val plus = text.lastIndexOf('+')
            return if (plus > 0) {
                val offset = text.substring(plus + 1).toIntOrNull()
                    ?: throw IRParseException("${schema.opcode}: invalid symbol offset in $token")
                ImmediateOperand.SymbolAddress(text.substring(0, plus), offset)
            } else ImmediateOperand.SymbolAddress(text)
        }
        val (valueText, explicitType) = stripTypeSuffix(text)
        val type = explicitType
            ?: throw IRParseException("${schema.opcode}: immediate operand needs an explicit type: $token")
        if (type == IRDataType.FLOAT)
            return ImmediateOperand.FloatValue(parseIRValue(valueText))
        val value = parseInteger(valueText, "${schema.opcode}: invalid integer immediate")
        return ImmediateOperand.Integer(value, type)
    }

    private fun parseHardwareSlot(token: String, schema: OpcodeSchema): HardwareSlotOperand {
        val (slotText, explicitType) = stripTypeSuffix(token)
        val match = hardwareSlotPattern.matchEntire(slotText)
            ?: throw IRParseException("${schema.opcode}: expected a hardware slot operand (s0..s32), got: $token")
        val type = explicitType
            ?: throw IRParseException("${schema.opcode}: hardware slot needs an explicit type: $token")
        return HardwareSlotOperand(CallingConventionSlot(match.groupValues[1].toInt()), type)
    }

    private fun parseMemory(token: String, slotSchema: MemorySlotSchema, schema: OpcodeSchema): MemoryReference {
        if (!token.startsWith('[') || !token.endsWith(']'))
            throw IRParseException("${schema.opcode}: expected a memory reference in brackets, got: $token")
        val inner = token.substring(1, token.length - 1).trim()
        if (inner.isEmpty())
            throw IRParseException("${schema.opcode}: empty memory reference")
        val parts = splitTopLevel(inner, '+').map { it.trim() }
        if (parts.any { it.isEmpty() })
            throw IRParseException("${schema.opcode}: invalid memory reference: $token")

        fun parseIndexRegister(text: String): Pair<RegisterOperand, Int> {
            val (registerText, scaleText) = if ('*' in text) {
                val idx = text.indexOf('*')
                text.substring(0, idx).trim() to text.substring(idx + 1).trim()
            } else text to "1"
            val scale = scaleText.toIntOrNull() ?: throw IRParseException("${schema.opcode}: invalid index scale in $token")
            val (regtext, explicitType) = stripTypeSuffix(registerText)
            val match = registerPattern.matchEntire(regtext)
                ?: throw IRParseException("${schema.opcode}: invalid index register in $token")
            if (match.groupValues[1].isNotEmpty())
                throw IRParseException("${schema.opcode}: index register must be an integer register: $token")
            val type = explicitType ?: throw IRParseException("${schema.opcode}: index register needs an explicit type: $token")
            return IRMemory.indexOperand(match.groupValues[2].toInt(), type) to scale
        }

        fun isRegisterText(text: String): Boolean {
            val (regtext, _) = stripTypeSuffix(text)
            return registerPattern.matchEntire(regtext) != null
        }

        if (isRegisterText(parts[0])) {
            // indirect: [rX.p] or [rX.p+displacement]
            val (regtext, explicitType) = stripTypeSuffix(parts[0])
            val match = registerPattern.matchEntire(regtext)!!
            if (match.groupValues[1].isNotEmpty())
                throw IRParseException("${schema.opcode}: pointer register must be an integer register: $token")
            val displacement = if (parts.size > 1)
                parseNonNegativeInteger(parts[1], "${schema.opcode}: invalid displacement in $token")
            else 0
            if (parts.size > 2)
                throw IRParseException("${schema.opcode}: invalid memory reference: $token")
            val pointerType = explicitType
                ?: throw IRParseException("${schema.opcode}: pointer register needs an explicit type: $token")
            val reference = IRMemory.indirect(match.groupValues[2].toInt(), displacement, pointerType)
            requireKind(reference, slotSchema, schema, token)
            return reference
        }

        val base: AddressBase = if (parts[0].first().isLetter())
            AddressBase.Symbol(parts[0])
        else
            AddressBase.Absolute(parseAddress(parts[0], "${schema.opcode}: invalid memory address"))

        var displacement = 0
        var index: RegisterOperand? = null
        var scale = 1
        for (part in parts.drop(1)) {
            if (isRegisterText(part.substringBefore('*'))) {
                if (index != null) throw IRParseException("${schema.opcode}: more than one index register in $token")
                val (indexOperand, indexScale) = parseIndexRegister(part)
                index = indexOperand
                scale = indexScale
            } else {
                displacement += parseNonNegativeInteger(part, "${schema.opcode}: invalid displacement in $token")
            }
        }

        val reference = if (index == null)
            MemoryReference.Direct(base, displacement)
        else
            MemoryReference.Indexed(base, index, scale, displacement)
        requireKind(reference, slotSchema, schema, token)
        return reference
    }

    private fun requireKind(reference: MemoryReference, slotSchema: MemorySlotSchema, schema: OpcodeSchema, token: String) {
        val kind = when (reference) {
            is MemoryReference.Direct -> MemoryKind.DIRECT
            is MemoryReference.Indexed -> MemoryKind.INDEXED
            is MemoryReference.Indirect -> MemoryKind.INDIRECT
        }
        if (kind != slotSchema.kind)
            throw IRParseException("${schema.opcode}: needs a ${slotSchema.kind} memory reference, got: $token")
    }

    private fun parseInteger(text: String, description: String): Int {
        val value = parseIRValue(text)
        if (!value.isFinite() || value % 1.0 != 0.0 || value < Int.MIN_VALUE || value > UInt.MAX_VALUE.toDouble())
            throw IRParseException("$description: $text")
        return value.toLong().toInt()
    }

    private fun parseNonNegativeInteger(text: String, description: String): Int {
        val value = parseInteger(text, description)
        if (value < 0)
            throw IRParseException("$description: $text")
        return value
    }

    private fun parseAddress(text: String, description: String): MemoryAddress {
        val value = parseIRValue(text)
        if (!value.isFinite() || value % 1.0 != 0.0 || value < 0.0 || value > UInt.MAX_VALUE.toDouble())
            throw IRParseException("$description: $text")
        return MemoryAddress(value.toLong().toUInt())
    }

    private fun parseCodeReference(token: String, kind: TargetKind): CodeReference {
        val reference = parseCodeReference(token)
        when (kind) {
            TargetKind.STATIC -> if (reference is CodeReference.Indirect) throw IRParseException("expected a static code target: $token")
            TargetKind.INDIRECT -> if (reference !is CodeReference.Indirect) throw IRParseException("expected an indirect code target: $token")
        }
        return reference
    }

    private fun parseCodeReference(token: String): CodeReference {
        if (token.startsWith('(') && token.endsWith(')')) {
            val (regtext, explicitType) = stripTypeSuffix(token.substring(1, token.length - 1).trim())
            val match = registerPattern.matchEntire(regtext) ?: throw IRParseException("invalid indirect code target: $token")
            if (match.groupValues[1].isNotEmpty())
                throw IRParseException("indirect code target must use an integer register: $token")
            val pointerType = explicitType
                ?: throw IRParseException("indirect code target needs an explicit register type: $token")
            return codeIndirect(match.groupValues[2].toInt(), pointerType)
        }
        if (token.isEmpty())
            throw IRParseException("empty code target")
        if (token[0].isLetter()) {
            val plus = token.lastIndexOf('+')
            return if (plus > 0) {
                val offset = token.substring(plus + 1).toIntOrNull() ?: throw IRParseException("invalid code target offset: $token")
                CodeReference.Label(token.substring(0, plus), offset)
            } else CodeReference.Label(token)
        }
        return CodeReference.Absolute(parseAddress(token, "invalid code target"))
    }

    // ---- call parsing ----

    private fun parseCallSite(kind: CallKind, rest: String, opcode: Opcode): CallSite {
        val text = rest.trim()
        val closeParen = text.lastIndexOf(')')
        if (closeParen < 0)
            throw IRParseException("$opcode: invalid call, missing argument list: $rest")
        val suffix = text.substring(closeParen + 1).trim()
        val effectMarker = suffix.indexOf('!')
        val resultsText = suffix.substring(0, if (effectMarker >= 0) effectMarker else suffix.length).trim().let {
            if (it.isEmpty()) null
            else if (it.startsWith(':')) it.drop(1).trim()
            else throw IRParseException("$opcode: invalid call result spec: $it")
        }
        val effects = if (effectMarker >= 0)
            parseCallEffects(suffix.substring(effectMarker + 1).trim(), opcode)
        else
            CallEffects.DEFAULT
        // find the matching opening parenthesis of the argument list
        var depth = 0
        var openParen = -1
        for (index in closeParen downTo 0) {
            when (text[index]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) openParen = index
                }
            }
            if (openParen >= 0) break
        }
        if (openParen < 0)
            throw IRParseException("$opcode: invalid call, unbalanced parentheses: $rest")

        val targetText = text.substring(0, openParen).trim()
        val argumentsText = text.substring(openParen + 1, closeParen).trim()

        val target = parseCallTarget(kind, targetText, opcode)
        val arguments = if (argumentsText.isEmpty()) emptyList() else
            splitTopLevel(argumentsText, ',').map {
                if (it.isBlank()) throw IRParseException("$opcode: empty call argument")
                parseCallArgument(it.trim(), opcode)
            }
        val results = if (resultsText.isNullOrEmpty()) emptyList() else
            splitTopLevel(resultsText, ',').map {
                if (it.isBlank()) throw IRParseException("$opcode: empty call result")
                parseCallResult(it.trim(), opcode)
            }
        return CallSite(target, arguments, results, effects)
    }

    private fun parseCallEffects(text: String, opcode: Opcode): CallEffects {
        val specifications = text.split(',')
        val parts = specifications.associate { part ->
            val keyValue = part.split('=', limit = 2).map { it.trim() }
            if (keyValue.size != 2 || keyValue.any { it.isEmpty() })
                throw IRParseException("$opcode: invalid call effect: $part")
            keyValue[0] to keyValue[1]
        }
        if (parts.size != specifications.size || parts.keys != setOf("memory", "status"))
            throw IRParseException("$opcode: call effects must specify memory and status")
        fun <T : Enum<T>> parseEffect(value: String, values: Array<T>, name: String): T =
            values.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IRParseException("$opcode: invalid $name effect: $value")
        return CallEffects(
            parseEffect(parts.getValue("memory"), MemoryEffect.entries.toTypedArray(), "memory"),
            parseEffect(parts.getValue("status"), StatusEffect.entries.toTypedArray(), "status")
        )
    }

    private fun parseCallTarget(kind: CallKind, targetText: String, opcode: Opcode): CallTarget {
        if (targetText.isEmpty())
            throw IRParseException("$opcode: missing call target")
        return when (kind) {
            CallKind.NORMAL -> {
                val parts = splitTopLevel(targetText, ',').map { it.trim() }
                if (parts.size !in 1..2 || parts.any { it.isEmpty() })
                    throw IRParseException("$opcode: invalid direct call target: $targetText")
                CallTarget.Direct(parseCodeReference(parts[0]), parts.getOrNull(1))
            }
            CallKind.INDIRECT -> CallTarget.Direct(parseCodeReference(targetText))
            CallKind.SYSCALL -> CallTarget.SystemCall(parseInteger(targetText, "$opcode: invalid syscall number"))
            CallKind.FAR -> {
                val parts = splitTopLevel(targetText, ',').map { it.trim() }
                if (parts.size !in 2..3 || parts.any { it.isEmpty() })
                    throw IRParseException("$opcode: far call needs a bank and an address")
                if (!parts[0].startsWith('#'))
                    throw IRParseException("$opcode: far call bank must be an immediate: ${parts[0]}")
                val bank = parseInteger(parts[0].drop(1), "$opcode: invalid bank")
                val name = parts.getOrNull(2)
                val lvo = parts[1].toIntOrNull()
                if (lvo != null && lvo < 0)
                    CallTarget.AmigaLibrary(bank, lvo, name)
                else
                    CallTarget.Banked(bank, parseCodeReference(parts[1]), name)
            }
            CallKind.FAR_VARBANK -> {
                val parts = splitTopLevel(targetText, ',').map { it.trim() }
                if (parts.size !in 2..3 || parts.any { it.isEmpty() })
                    throw IRParseException("$opcode: far call needs a bank register and an address")
                val (regtext, explicitType) = stripTypeSuffix(parts[0])
                val match = registerPattern.matchEntire(regtext)
                    ?: throw IRParseException("$opcode: invalid bank register: ${parts[0]}")
                if (match.groupValues[1].isNotEmpty())
                    throw IRParseException("$opcode: bank register must be an integer register: ${parts[0]}")
                val bankRegister = RegisterOperand(
                    VirtualRegister.int(match.groupValues[2].toInt()),
                    explicitType ?: throw IRParseException("$opcode: bank register needs an explicit type: ${parts[0]}"),
                    OperandRole.VALUE, OperandDirection.USE
                )
                CallTarget.BankedVariable(bankRegister, parseCodeReference(parts[1]), parts.getOrNull(2))
            }
        }
    }

    private fun parseCallLocationSuffix(text: String, opcode: Opcode): Pair<String, CallLocation?> {
        val at = text.indexOf('@')
        if (at < 0)
            return text to null
        val locationText = text.substring(at + 1)
        val location = if (locationText.startsWith('s') && locationText.length > 1 && locationText[1].isDigit()) {
            val slot = locationText.drop(1).toIntOrNull() ?: throw IRParseException("$opcode: invalid slot $locationText")
            CallLocation.HardwareRegister(CallingConventionSlot(slot))
        } else {
            try {
                CallLocation.StatusFlag(Statusflag.valueOf(locationText))
            } catch (_: IllegalArgumentException) {
                throw IRParseException("$opcode: invalid status flag $locationText")
            }
        }
        return text.substring(0, at) to location
    }

    private fun parseCallRegister(text: String, role: OperandRole, direction: OperandDirection, opcode: Opcode): RegisterOperand {
        val (regtext, explicitType) = stripTypeSuffix(text)
        val match = registerPattern.matchEntire(regtext)
            ?: throw IRParseException("$opcode: invalid call register: $text")
        val type = explicitType ?: throw IRParseException("$opcode: call register needs a type: $text")
        val isFloat = match.groupValues[1].isNotEmpty()
        if (isFloat != (type == IRDataType.FLOAT))
            throw IRParseException("$opcode: float register/type mismatch: $text")
        val register = if (isFloat) VirtualRegister.float(match.groupValues[2].toInt()) else VirtualRegister.int(match.groupValues[2].toInt())
        return RegisterOperand(register, type, role, direction)
    }

    private fun parseCallArgument(text: String, opcode: Opcode): CallArgument {
        var remainder = text
        var parameterLocation: CallLocation? = null
        val equals = remainder.indexOf('=')
        if (equals >= 0) {
            val locationText = remainder.substring(0, equals)
            remainder = remainder.substring(equals + 1)
            parameterLocation = if (locationText.isEmpty()) null
            else if (locationText[0].isLetter()) CallLocation.ParameterMemory(locationText)
            else CallLocation.ParameterMemory("", parseAddress(locationText, "$opcode: invalid parameter address"))
        }
        val (registerText, hardwareLocation) = parseCallLocationSuffix(remainder, opcode)
        if (parameterLocation != null && hardwareLocation != null)
            throw IRParseException("$opcode: call argument cannot have both parameter and hardware locations: $text")
        val source = parseCallRegister(registerText, OperandRole.CALL_ARGUMENT, OperandDirection.USE, opcode)
        val location = hardwareLocation ?: parameterLocation ?: CallLocation.Default
        return CallArgument(source, location)
    }

    private fun parseCallResult(text: String, opcode: Opcode): CallResult {
        val (registerText, location) = parseCallLocationSuffix(text, opcode)
        if (registerText.isEmpty()) {
            if (location == null)
                throw IRParseException("$opcode: invalid call result: $text")
            return CallResult(null, location)
        }
        val destination = parseCallRegister(registerText, OperandRole.CALL_RESULT, OperandDirection.DEF, opcode)
        return CallResult(destination, location ?: CallLocation.Default)
    }
}
