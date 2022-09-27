package prog8.vm

import prog8.code.core.DataType
import prog8.intermediate.*
import kotlin.IllegalArgumentException

class VmProgramLoader {

    private val placeholders = mutableMapOf<Int, String>()      // program index to symbolname

    fun load(irProgram: IRProgram, memory: Memory): List<IRInstruction> {

        // at long last, allocate the variables in memory.
        placeholders.clear()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val symbolAddresses = allocations.allocations.toMutableMap()
        val program = mutableListOf<IRInstruction>()

        varsToMemory(irProgram, allocations, symbolAddresses, memory)

        if(!irProgram.options.dontReinitGlobals)
            addToProgram(irProgram.globalInits, program, symbolAddresses)

        // make sure that if there is a "main.start" entrypoint, we jump to it
        irProgram.blocks.firstOrNull()?.let {
            if(it.subroutines.any { sub -> sub.name=="main.start" }) {
                rememberPlaceholder("main.start", program.size)
                program += IRInstruction(Opcode.JUMP, labelSymbol = "main.start")
            }
        }

        irProgram.blocks.forEach { block ->
            if(block.address!=null)
                throw IllegalArgumentException("blocks cannot have a load address for vm: ${block.name}")

            block.inlineAssembly.forEach { addAssemblyToProgram(it, program) }
            block.subroutines.forEach {
                symbolAddresses[it.name] = program.size
                it.chunks.forEach { chunk ->
                    if(chunk is IRInlineAsmChunk)
                        addAssemblyToProgram(chunk, program)
                    else
                        addToProgram(chunk.lines, program, symbolAddresses)
                }
            }
            if(block.asmSubroutines.any())
                throw IllegalArgumentException("vm currently does not support asmsubs: ${block.asmSubroutines.first().name}")
        }

        pass2replaceLabelsByProgIndex(program, symbolAddresses)

        program.forEach {
            if(it.opcode in OpcodesWithAddress && it.value==null) {
                throw IllegalArgumentException("instruction missing numeric value, label not replaced? $it")
            }
        }

        return program
    }

    private fun varsToMemory(
        program: IRProgram,
        allocations: VmVariableAllocator,
        symbolAddresses: MutableMap<String, Int>,
        memory: Memory
    ) {
        program.st.allVariables().forEach { variable ->
            var addr = allocations.allocations.getValue(variable.name)
            variable.onetimeInitializationNumericValue?.let {
                when(variable.dt) {
                    DataType.UBYTE -> memory.setUB(addr, it.toInt().toUByte())
                    DataType.BYTE -> memory.setSB(addr, it.toInt().toByte())
                    DataType.UWORD -> memory.setUW(addr, it.toInt().toUShort())
                    DataType.WORD -> memory.setSW(addr, it.toInt().toShort())
                    DataType.FLOAT -> memory.setFloat(addr, it.toFloat())
                    else -> throw IllegalArgumentException("invalid dt")
                }
            }
            variable.onetimeInitializationArrayValue?.let {
                require(variable.length==it.size || it.size==1)
                if(it.size==1) {
                    val value = it[0].number!!
                    when(variable.dt) {
                        DataType.STR, DataType.ARRAY_UB -> {
                            repeat(variable.length!!) {
                                memory.setUB(addr, value.toInt().toUByte())
                                addr++
                            }
                        }
                        DataType.ARRAY_B -> {
                            repeat(variable.length!!) {
                                memory.setSB(addr, value.toInt().toByte())
                                addr++
                            }
                        }
                        DataType.ARRAY_UW -> {
                            repeat(variable.length!!) {
                                memory.setUW(addr, value.toInt().toUShort())
                                addr+=2
                            }
                        }
                        DataType.ARRAY_W -> {
                            repeat(variable.length!!) {
                                memory.setSW(addr, value.toInt().toShort())
                                addr+=2
                            }
                        }
                        DataType.ARRAY_F -> {
                            repeat(variable.length!!) {
                                memory.setFloat(addr, value.toFloat())
                                addr += program.options.compTarget.machine.FLOAT_MEM_SIZE
                            }
                        }
                        else -> throw IllegalArgumentException("invalid dt")
                    }
                } else {
                    when(variable.dt) {
                        DataType.STR, DataType.ARRAY_UB -> {
                            for(elt in it) {
                                memory.setUB(addr, elt.number!!.toInt().toUByte())
                                addr++
                            }
                        }
                        DataType.ARRAY_B -> {
                            for(elt in it) {
                                memory.setSB(addr, elt.number!!.toInt().toByte())
                                addr++
                            }
                        }
                        DataType.ARRAY_UW -> {
                            for(elt in it) {
                                if(elt.addressOf!=null) {
                                    val symbolAddress = symbolAddresses.getValue(elt.addressOf!!.joinToString("."))
                                    memory.setUW(addr, symbolAddress.toUShort())
                                } else {
                                    memory.setUW(addr, elt.number!!.toInt().toUShort())
                                }
                                addr+=2
                            }
                        }
                        DataType.ARRAY_W -> {
                            for(elt in it) {
                                memory.setSW(addr, elt.number!!.toInt().toShort())
                                addr+=2
                            }
                        }
                        DataType.ARRAY_F -> {
                            for(elt in it) {
                                memory.setSW(addr, elt.number!!.toInt().toShort())
                                addr+=program.options.compTarget.machine.FLOAT_MEM_SIZE
                            }
                        }
                        else -> throw IllegalArgumentException("invalid dt")
                    }
                }
            }
            require(variable.onetimeInitializationStringValue==null) { "in vm/ir, strings should have been converted into bytearrays." }
        }
    }

    private fun rememberPlaceholder(symbol: String, pc: Int) {
        placeholders[pc] = symbol
    }

    private fun pass2replaceLabelsByProgIndex(
        program: MutableList<IRInstruction>,
        symbolAddresses: MutableMap<String, Int>
    ) {
        for((line, label) in placeholders) {
            val replacement = symbolAddresses[label]
            if(replacement==null) {
                // it could be an address + index:   symbol+42
                if('+' in label) {
                    val (symbol, indexStr) = label.split('+')
                    val index = indexStr.toInt()
                    val address = symbolAddresses.getValue(symbol) + index
                    program[line] = program[line].copy(value = address)
                } else {
                    throw IllegalArgumentException("placeholder not found in labels: $label")
                }
            } else {
                program[line] = program[line].copy(value = replacement)
            }
        }
    }

    private fun addToProgram(
        lines: Iterable<IRCodeLine>,
        program: MutableList<IRInstruction>,
        symbolAddresses: MutableMap<String, Int>
    ) {
        lines.map {
            when(it) {
                is IRInstruction -> {
                    it.labelSymbol?.let { symbol -> rememberPlaceholder(symbol, program.size)}
                    if(it.opcode==Opcode.SYSCALL) {
                        // convert IR Syscall to VM Syscall
                        val vmSyscall = when(it.value!!) {
                            IMSyscall.SORT_UBYTE.ordinal -> Syscall.SORT_UBYTE
                            IMSyscall.SORT_BYTE.ordinal -> Syscall.SORT_BYTE
                            IMSyscall.SORT_UWORD.ordinal -> Syscall.SORT_UWORD
                            IMSyscall.SORT_WORD.ordinal -> Syscall.SORT_WORD
                            IMSyscall.ANY_BYTE.ordinal -> Syscall.ANY_BYTE
                            IMSyscall.ANY_WORD.ordinal -> Syscall.ANY_WORD
                            IMSyscall.ANY_FLOAT.ordinal -> Syscall.ANY_FLOAT
                            IMSyscall.ALL_BYTE.ordinal -> Syscall.ALL_BYTE
                            IMSyscall.ALL_WORD.ordinal -> Syscall.ALL_WORD
                            IMSyscall.ALL_FLOAT.ordinal -> Syscall.ALL_FLOAT
                            IMSyscall.REVERSE_BYTES.ordinal -> Syscall.REVERSE_BYTES
                            IMSyscall.REVERSE_WORDS.ordinal -> Syscall.REVERSE_WORDS
                            IMSyscall.REVERSE_FLOATS.ordinal -> Syscall.REVERSE_FLOATS
                            else -> throw IllegalArgumentException("invalid IM syscall number $it")
                        }
                        program += it.copy(value=vmSyscall.ordinal)
                    } else {
                        program += it
                    }
                }
                is IRCodeInlineBinary -> program += IRInstruction(Opcode.BINARYDATA, binaryData = it.data)
                is IRCodeComment -> { /* just ignore */ }
                is IRCodeLabel -> { symbolAddresses[it.name] = program.size }
            }
        }
    }

    private fun addAssemblyToProgram(
        asmChunk: IRInlineAsmChunk,
        program: MutableList<IRInstruction>,
    ) {

        // TODO use IRFileReader.parseCodeLine instead of duplicating everything here

        val instructionPattern = Regex("""([a-z]+)(\.b|\.w|\.f)?(.*)""", RegexOption.IGNORE_CASE)
        asmChunk.assembly.lineSequence().forEach {
            val line = it.trim()
            val match = instructionPattern.matchEntire(line)
                ?: throw IllegalArgumentException("invalid IR instruction: $line in ${asmChunk.position}")
            val (instr, typestr, rest) = match.destructured
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

            fun parseValueOrPlaceholder(operand: String, pc: Int, rest: String, restIndex: Int, opcode: Opcode): Float {
                return if(operand.startsWith('_')) {
                    rememberPlaceholder(rest.split(",")[restIndex].trim().drop(1), pc)
                    0f
                } else if(operand[0].isLetter()) {
                    rememberPlaceholder(rest.split(",")[restIndex].trim(), pc)
                    0f
                } else
                    parseValue(opcode, operand, pc)
            }

            if(operands.isNotEmpty() && operands[0].isNotEmpty()) {
                operand = operands.removeFirst().trim()
                if(operand[0]=='r')
                    reg1 = operand.substring(1).toInt()
                else if(operand[0]=='f' && operand[1]=='r')
                    fpReg1 = operand.substring(2).toInt()
                else {
                    value = parseValueOrPlaceholder(operand, program.size, rest, 0, opcode)
                    operands.clear()
                }
                if(operands.isNotEmpty()) {
                    operand = operands.removeFirst().trim()
                    if(operand[0]=='r')
                        reg2 = operand.substring(1).toInt()
                    else if(operand[0]=='f' && operand[1]=='r')
                        fpReg2 = operand.substring(2).toInt()
                    else {
                        value = parseValueOrPlaceholder(operand, program.size, rest, 1, opcode)
                        operands.clear()
                    }
                    if(operands.isNotEmpty()) {
                        operand = operands.removeFirst().trim()
                        if(operand[0]=='r')
                            reg3 = operand.substring(1).toInt()
                        else if(operand[0]=='f' && operand[1]=='r')
                            fpReg3 = operand.substring(2).toInt()
                        else {
                            value = parseValueOrPlaceholder(operand, program.size, rest, 2, opcode)
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
                    throw IllegalArgumentException("invalid cpu reg: $reg")
                program += IRInstruction(opcode, type, reg1, labelSymbol = reg)
            } else {
                program += IRInstruction(opcode, type, reg1, reg2, fpReg1, fpReg2, intValue, floatValue)
            }
        }
    }

    private fun parseValue(opcode: Opcode, value: String, pc: Int): Float {
        return if(value.startsWith("-"))
            -parseValue(opcode, value.substring(1), pc)
        else if(value.startsWith('$'))
            value.substring(1).toInt(16).toFloat()
        else if(value.startsWith('%'))
            value.substring(1).toInt(2).toFloat()
        else if(value.startsWith("0x"))
            value.substring(2).toInt(16).toFloat()
        else if(value.startsWith('_') || value[0].isLetter())
            throw IllegalArgumentException("attempt to parse non-numeric value $value")
        else
            value.toFloat()
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
