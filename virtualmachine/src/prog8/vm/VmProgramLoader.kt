package prog8.vm

import prog8.code.core.DataType
import prog8.intermediate.*

/*
class VmProgramLoader {

    private val placeholders = mutableMapOf<Int, String>()      // program index to symbolname

    fun load(irProgram: IRProgram, memory: Memory): List<IRInstruction> {

        // at long last, allocate the variables in memory.
        placeholders.clear()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val symbolAddresses = allocations.allocations.toMutableMap()
        val programChunks = mutableListOf<IRCodeChunkBase>()

        varsToMemory(irProgram, allocations, symbolAddresses, memory)

        if(!irProgram.options.dontReinitGlobals)
            addToProgram(irProgram.globalInits, programChunks, symbolAddresses)

        // make sure that if there is a "main.start" entrypoint, we jump to it
        irProgram.blocks.firstOrNull()?.let {
            if(it.subroutines.any { sub -> sub.name=="main.start" }) {
                placeholders[program.size] = "main.start"
                program += IRInstruction(Opcode.JUMP, labelSymbol = "main.start")
            }
        }

        irProgram.blocks.forEach { block ->
            if(block.address!=null)
                throw IRParseException("blocks cannot have a load address for vm: ${block.name}")

            block.inlineAssembly.forEach { addAssemblyToProgram(it, program, symbolAddresses) }
            block.subroutines.forEach {
                symbolAddresses[it.name] = program.size
                it.chunks.forEach { chunk ->
                    when (chunk) {
                        is IRInlineAsmChunk -> addAssemblyToProgram(chunk, program, symbolAddresses)
                        is IRInlineBinaryChunk -> program += IRInstruction(Opcode.BINARYDATA, binaryData = chunk.data)
                        else -> addToProgram(chunk.instructions, program, symbolAddresses)
                    }
                }
            }
            if(block.asmSubroutines.any())
                throw IRParseException("vm currently does not support asmsubs: ${block.asmSubroutines.first().name}")
        }

        pass2replaceLabelsByProgIndex(program, symbolAddresses)

        program.forEach {
            if(it.opcode in OpcodesWithAddress && it.value==null) {
                throw IRParseException("instruction missing numeric value, label not replaced? $it")
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
                    else -> throw IRParseException("invalid dt")
                }
            }
            variable.onetimeInitializationArrayValue?.let {
                require(variable.length==it.size || it.size==1 || it.size==0)
                if(it.isEmpty() || it.size==1) {
                    val value = if(it.isEmpty()) {
                        require(variable.bss)
                        0.0
                    } else {
                        require(!variable.bss)
                        it[0].number!!
                    }
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
                        else -> throw IRParseException("invalid dt")
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
                        else -> throw IRParseException("invalid dt")
                    }
                }
            }
            require(variable.onetimeInitializationStringValue==null) { "in vm/ir, strings should have been converted into bytearrays." }
        }
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
                    throw IRParseException("placeholder not found in labels: $label")
                }
            } else {
                program[line] = program[line].copy(value = replacement)
            }
        }
    }

    private fun addToProgram(
        chunks: Iterable<IRCodeChunkBase>,
        program: MutableList<IRCodeChunkBase>,
        symbolAddresses: MutableMap<String, Int>
    ) {
        instructions.map {
            when(it) {
                is IRInstruction -> {
                    it.labelSymbol?.let { symbol -> placeholders[program.size]=symbol }
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
                            else -> throw IRParseException("invalid IM syscall number $it")
                        }
                        program += it.copy(value=vmSyscall.ordinal)
                    } else {
                        program += it
                    }
                }
                is IRCodeLabel -> { symbolAddresses[it.name] = program.size }
            }
        }
    }

    private fun addAssemblyToProgram(
        asmChunk: IRInlineAsmChunk,
        program: MutableList<IRInstruction>,
        symbolAddresses: MutableMap<String, Int>,
    ) {
        if(asmChunk.isIR) {
            asmChunk.assembly.lineSequence().forEach {
                val parsed = parseIRCodeLine(it.trim(), program.size, placeholders)
                if (parsed is IRInstruction)
                    program += parsed
                else if (parsed is IRCodeLabel)
                    symbolAddresses[parsed.name] = program.size
            }
        } else {
            throw IRParseException("vm currently does not support real inlined assembly (only IR): ${asmChunk.position}")
        }
    }
}
*/
