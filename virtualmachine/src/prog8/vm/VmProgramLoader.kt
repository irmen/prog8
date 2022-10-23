package prog8.vm

import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.intermediate.*

class VmProgramLoader {
    private val placeholders = mutableMapOf<Pair<IRCodeChunk, Int>, String>()      // program chunk+index to symbolname

    fun load(irProgram: IRProgram, memory: Memory): List<IRCodeChunk> {
        placeholders.clear()
        irProgram.validate()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val variableAddresses = allocations.allocations.toMutableMap()
        val programChunks = mutableListOf<IRCodeChunk>()

        varsToMemory(irProgram, allocations, variableAddresses, memory)

        if(!irProgram.options.dontReinitGlobals) {
            if(irProgram.globalInits.isNotEmpty())
                programChunks += irProgram.globalInits
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

        // load rest of the program into the list
        val chunkReplacements = mutableListOf<Pair<IRCodeChunkBase, IRCodeChunk>>()
        irProgram.blocks.forEach { block ->
            if(block.address!=null)
                throw IRParseException("blocks cannot have a load address for vm: ${block.name}")

            block.inlineAssembly.forEach {
                val replacement = addAssemblyToProgram(it, programChunks, variableAddresses)
                if(replacement!=null)
                    chunkReplacements += replacement
            }
            block.subroutines.forEach {
                it.chunks.forEach { chunk ->
                    when (chunk) {
                        is IRInlineAsmChunk -> {
                            val replacement = addAssemblyToProgram(chunk, programChunks, variableAddresses)
                            if(replacement!=null)
                                chunkReplacements += replacement
                        }
                        is IRInlineBinaryChunk -> TODO("inline binary data not yet supported in the VM")
                        is IRCodeChunk -> programChunks += chunk
                        else -> throw AssemblyError("weird chunk type")
                    }
                }
            }
            if(block.asmSubroutines.any())
                throw IRParseException("vm currently does not support asmsubs: ${block.asmSubroutines.first().name}")
        }

        pass2translateSyscalls(programChunks)
        pass2replaceLabelsByProgIndex(programChunks, variableAddresses)
        phase2relinkReplacedChunks(chunkReplacements, programChunks)

        programChunks.forEach {
            it.instructions.forEach { ins ->
                if (ins.labelSymbol != null && ins.opcode !in OpcodesThatBranch)
                    require(ins.value != null) { "instruction with labelSymbol for a var should have value set to var's memory address" }
            }
        }

        return programChunks
    }

    private fun phase2relinkReplacedChunks(
        replacements: MutableList<Pair<IRCodeChunkBase, IRCodeChunk>>,
        programChunks: MutableList<IRCodeChunk>
    ) {
        replacements.forEach { (old, new) ->
            programChunks.forEach { chunk ->
                if(chunk.next === old) {
                    chunk.next = new
                }
                chunk.instructions.forEach { ins ->
                    if(ins.branchTarget === old) {
                        ins.branchTarget = new
                    } else if(ins.branchTarget==null && ins.labelSymbol==new.label) {
                        ins.branchTarget = new
                    }
                }
            }
        }
    }

    private fun pass2translateSyscalls(chunks: MutableList<IRCodeChunk>) {
        chunks.forEach { chunk ->
            chunk.instructions.withIndex().forEach { (index, ins) ->
                if(ins.opcode == Opcode.SYSCALL) {
                    // convert IR Syscall to VM Syscall
                    val vmSyscall = when(ins.value!!) {
                        IMSyscall.SORT_UBYTE.number -> Syscall.SORT_UBYTE
                        IMSyscall.SORT_BYTE.number -> Syscall.SORT_BYTE
                        IMSyscall.SORT_UWORD.number -> Syscall.SORT_UWORD
                        IMSyscall.SORT_WORD.number -> Syscall.SORT_WORD
                        IMSyscall.ANY_BYTE.number -> Syscall.ANY_BYTE
                        IMSyscall.ANY_WORD.number -> Syscall.ANY_WORD
                        IMSyscall.ANY_FLOAT.number -> Syscall.ANY_FLOAT
                        IMSyscall.ALL_BYTE.number -> Syscall.ALL_BYTE
                        IMSyscall.ALL_WORD.number -> Syscall.ALL_WORD
                        IMSyscall.ALL_FLOAT.number -> Syscall.ALL_FLOAT
                        IMSyscall.REVERSE_BYTES.number -> Syscall.REVERSE_BYTES
                        IMSyscall.REVERSE_WORDS.number -> Syscall.REVERSE_WORDS
                        IMSyscall.REVERSE_FLOATS.number -> Syscall.REVERSE_FLOATS
                        else -> null
                    }

                    if(vmSyscall!=null)
                        chunk.instructions[index] = ins.copy(value = vmSyscall.ordinal)
                }

                val label = ins.labelSymbol
                if (label != null && ins.opcode !in OpcodesThatBranch) {
                    placeholders[Pair(chunk, index)] = label
                }
            }
        }
    }

    private fun pass2replaceLabelsByProgIndex(
        chunks: MutableList<IRCodeChunk>,
        variableAddresses: MutableMap<String, Int>
    ) {
        for((ref, label) in placeholders) {
            val (chunk, line) = ref
            val replacement = variableAddresses[label]
            if(replacement==null) {
                // it could be an address + index:   symbol+42
                if('+' in label) {
                    val (symbol, indexStr) = label.split('+')
                    val index = indexStr.toInt()
                    val address = variableAddresses.getValue(symbol) + index
                    chunk.instructions[line] = chunk.instructions[line].copy(value = address)
                } else {
                    // placeholder is not a variable, so it must be a label of a code chunk instead
                    val target: IRCodeChunk? = chunks.firstOrNull { it.label==label }
                    if(target==null)
                        throw IRParseException("placeholder not found in variables nor labels: $label")
                    else {
                        require(chunk.instructions[line].opcode in OpcodesThatBranch)
                        chunk.instructions[line] = chunk.instructions[line].copy(branchTarget = target, value = null)
                    }
                }
            } else {
                chunk.instructions[line] = chunk.instructions[line].copy(value = replacement)
            }
        }
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
    ): Pair<IRCodeChunkBase, IRCodeChunk> {
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
            return Pair(asmChunk, chunk)
        } else {
            throw IRParseException("vm currently does not support real inlined assembly (only IR): ${asmChunk.position}")
        }
    }
}
