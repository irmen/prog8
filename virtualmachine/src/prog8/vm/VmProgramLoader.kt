package prog8.vm

import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.intermediate.*

class VmProgramLoader {
    private val placeholders = mutableMapOf<Pair<IRCodeChunk, Int>, String>()      // program chunk+index to symbolname

    fun load(irProgram: IRProgram, memory: Memory): List<IRCodeChunk> {
        placeholders.clear()
        irProgram.validate()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val symbolAddresses = allocations.allocations.toMutableMap()
        val programChunks = mutableListOf<IRCodeChunk>()

        varsToMemory(irProgram, allocations, symbolAddresses, memory)

        if(!irProgram.options.dontReinitGlobals) {
            if(irProgram.globalInits.isNotEmpty())
                addToProgram(irProgram.globalInits, programChunks, symbolAddresses)
        }

        // make sure that if there is a "main.start" entrypoint, we jump to it
        irProgram.blocks.firstOrNull()?.let {
            if(it.subroutines.any { sub -> sub.name=="main.start" }) {
                val chunk = IRCodeChunk(null, Position.DUMMY, null)
                placeholders[Pair(chunk, 0)] = "main.start"
                chunk += IRInstruction(Opcode.JUMP, labelSymbol = "main.start")
                programChunks += chunk
            }
        }

        // TODO load rest of the program
        irProgram.blocks.forEach { block ->
            if(block.address!=null)
                throw IRParseException("blocks cannot have a load address for vm: ${block.name}")

            block.inlineAssembly.forEach { addAssemblyToProgram(it, programChunks, symbolAddresses) }
            block.subroutines.forEach {
                it.chunks.forEach { chunk ->
                    when (chunk) {
                        is IRInlineAsmChunk -> addAssemblyToProgram(chunk, programChunks, symbolAddresses)
                        is IRInlineBinaryChunk -> TODO("inline binary data not yet supported in the VM")
                        else -> addToProgram(chunk.instructions, programChunks, symbolAddresses)
                    }
                }
            }
            if(block.asmSubroutines.any())
                throw IRParseException("vm currently does not support asmsubs: ${block.asmSubroutines.first().name}")
        }

        pass2replaceLabelsByProgIndex(programChunks, symbolAddresses)

        programChunks.asSequence().flatMap { it.instructions }.forEach {
            if(it.opcode in OpcodesWithAddress && it.value==null) {
                throw IRParseException("instruction missing numeric value, label not replaced? $it")
            }
        }

        return programChunks
    }

    private fun pass2replaceLabelsByProgIndex(
        chunks: MutableList<IRCodeChunk>,
        symbolAddresses: MutableMap<String, Int>
    ) {
        for((ref, label) in placeholders) {
            val (chunk, line) = ref
            val replacement = symbolAddresses[label]
            if(replacement==null) {
                // it could be an address + index:   symbol+42
                if('+' in label) {
                    val (symbol, indexStr) = label.split('+')
                    val index = indexStr.toInt()
                    val address = symbolAddresses.getValue(symbol) + index
                    chunk.instructions[line] = chunk.instructions[line].copy(value = address)
                } else {
                    throw IRParseException("placeholder not found in labels: $label")
                }
            } else {
                chunk.instructions[line] = chunk.instructions[line].copy(value = replacement)
            }
        }
    }

    private fun addToProgram(
        instructions: Iterable<IRInstruction>,
        program: MutableList<IRCodeChunk>,
        symbolAddresses: MutableMap<String, Int>
    ) {
        val chunk = IRCodeChunk(null, Position.DUMMY, null)
        instructions.map {
            it.labelSymbol?.let { symbol -> placeholders[Pair(chunk, chunk.instructions.size)]=symbol }
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
                chunk += it.copy(value=vmSyscall.ordinal)
            } else {
                chunk += it
            }
        }
        program += chunk
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


    private fun addAssemblyToProgram(
        asmChunk: IRInlineAsmChunk,
        chunks: MutableList<IRCodeChunk>,
        symbolAddresses: MutableMap<String, Int>,
    ) {
        if(asmChunk.isIR) {
            val chunk = IRCodeChunk(asmChunk.label, asmChunk.position, null)
            asmChunk.assembly.lineSequence().forEach {
                val parsed = parseIRCodeLine(it.trim(), Pair(chunk, chunk.instructions.size), placeholders)
                parsed.fold(
                    ifLeft = { instruction -> chunk += instruction },
                    ifRight = { label -> symbolAddresses[label] = chunk.instructions.size }
                )
            }
            chunks += chunk
        } else {
            throw IRParseException("vm currently does not support real inlined assembly (only IR): ${asmChunk.position}")
        }
    }
}
