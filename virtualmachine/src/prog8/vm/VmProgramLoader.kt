package prog8.vm

import prog8.code.core.ArrayDatatypes
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.SplitWordArrayTypes
import prog8.intermediate.*

class VmProgramLoader {
    private val placeholders = mutableMapOf<Pair<IRCodeChunk, Int>, String>()      // program chunk+index to symbolname
    private val subroutines = mutableMapOf<String, IRSubroutine>()                 // label to subroutine node

    fun load(irProgram: IRProgram, memory: Memory): List<IRCodeChunk> {
        irProgram.validate()
        placeholders.clear()
        subroutines.clear()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget)
        val variableAddresses = allocations.allocations.toMutableMap()
        val programChunks = mutableListOf<IRCodeChunk>()

        varsToMemory(irProgram, allocations, variableAddresses, memory)

        if(irProgram.globalInits.isNotEmpty())
            programChunks += irProgram.globalInits

        // make sure that if there is a "main.start" entrypoint, we jump to it
        irProgram.blocks.firstOrNull()?.let {
            if(it.children.any { sub -> sub is IRSubroutine && sub.label=="main.start" }) {
                val previous = programChunks.lastOrNull()
                val chunk = IRCodeChunk(null, previous)
                placeholders[Pair(chunk, 0)] = "main.start"
                chunk += IRInstruction(Opcode.JUMP, labelSymbol = "main.start")
                previous?.let { p -> p.next = chunk }
                programChunks += chunk
            }
        }

        // load rest of the program into the list
        val chunkReplacements = mutableListOf<Pair<IRCodeChunkBase, IRCodeChunk>>()
        irProgram.blocks.forEach { block ->
            if(block.options.address!=null)
                throw IRParseException("blocks cannot have a load address for vm: ${block.label}")

            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> throw IRParseException("vm does not support non-inlined asmsubs (use normal sub): ${child.label}")
                    is IRCodeChunk -> programChunks += child
                    is IRInlineAsmChunk -> throw IRParseException("encountered unconverted inline assembly chunk")
                    is IRInlineBinaryChunk -> throw IRParseException("inline binary data not yet supported in the VM")
                    is IRSubroutine -> {
                        subroutines[child.label] = child
                        child.chunks.forEach { chunk ->
                            when (chunk) {
                                is IRInlineAsmChunk -> throw IRParseException("encountered unconverted inline assembly chunk")
                                is IRInlineBinaryChunk -> throw IRParseException("inline binary data not yet supported in the VM")
                                is IRCodeChunk -> programChunks += chunk
                                else -> throw AssemblyError("weird chunk type")
                            }
                        }
                    }
                }
            }
        }

        pass2translateSyscalls(programChunks)
        pass2replaceLabelsByProgIndex(programChunks, variableAddresses, subroutines)
        phase2relinkReplacedChunks(chunkReplacements, programChunks)

        programChunks.forEach {
            it.instructions.forEach { ins ->
                if (ins.labelSymbol != null && ins.opcode !in OpcodesThatBranch)
                    require(ins.address != null) { "instruction with labelSymbol for a var should have value set to the memory address" }
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
                    val vmSyscall = when(ins.immediate!!) {
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
                        IMSyscall.COMPARE_STRINGS.number -> Syscall.COMPARE_STRINGS
                        IMSyscall.STRING_CONTAINS.number -> Syscall.STRING_CONTAINS
                        IMSyscall.BYTEARRAY_CONTAINS.number -> Syscall.BYTEARRAY_CONTAINS
                        IMSyscall.WORDARRAY_CONTAINS.number -> Syscall.WORDARRAY_CONTAINS
                        IMSyscall.FLOATARRAY_CONTAINS.number -> Syscall.FLOATARRAY_CONTAINS
                        IMSyscall.CLAMP_BYTE.number -> Syscall.CLAMP_BYTE
                        IMSyscall.CLAMP_UBYTE.number -> Syscall.CLAMP_UBYTE
                        IMSyscall.CLAMP_WORD.number -> Syscall.CLAMP_WORD
                        IMSyscall.CLAMP_UWORD.number -> Syscall.CLAMP_UWORD
                        IMSyscall.CLAMP_FLOAT.number -> Syscall.CLAMP_FLOAT
                        IMSyscall.CALLFAR.number -> throw IRParseException("vm doesn't support the callfar() syscall")
                        else -> null
                    }

                    if(vmSyscall!=null)
                        chunk.instructions[index] = ins.copy(immediate = vmSyscall.ordinal)
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
        variableAddresses: MutableMap<String, Int>,
        subroutines: MutableMap<String, IRSubroutine>
    ) {
        for((ref, label) in placeholders) {
            val (chunk, line) = ref
            val replacement = variableAddresses[label]
            val instr = chunk.instructions[line]
            val offset = instr.labelSymbolOffset ?: 0
            if(replacement==null) {
                // it could be an address + index:   symbol+42
                if(offset>0) {
                    val address = variableAddresses.getValue(label) + offset
                    chunk.instructions[line] = instr.copy(address = address)
                } else {
                    // placeholder is not a variable, so it must be a label of a code chunk instead
                    val target: IRCodeChunk? = chunks.firstOrNull { it.label==label }
                    if(target==null)
                        throw IRParseException("placeholder not found in variables nor labels: $label")
                    else if(instr.opcode in OpcodesThatBranch)
                        chunk.instructions[line] = instr.copy(branchTarget = target, address = null)
                    else
                        throw IRParseException("vm cannot yet load a label address as a value: ${instr}")
                }
            } else {
                chunk.instructions[line] = instr.copy(address = replacement + offset)
            }
        }

        subroutines.forEach {
            it.value.chunks.forEach { chunk ->
                chunk.instructions.withIndex().forEach { (_, ins) ->
                    if(ins.opcode==Opcode.CALL) {
                        val fcallspec = ins.fcallArgs!!
                        val argsWithAddresses = fcallspec.arguments.map { arg ->
                            if(arg.address!=null)
                                arg
                            else {
                                val address = ins.address ?: variableAddresses.getValue(ins.labelSymbol + "." + arg.name)
                                FunctionCallArgs.ArgumentSpec(arg.name, address, arg.reg)
                            }
                        }
                        fcallspec.arguments = argsWithAddresses
                    }
                }
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

            // zero out uninitialized ('bss') variables.
            if(variable.uninitialized) {
                if(variable.dt in ArrayDatatypes) {
                    repeat(variable.length!!) {
                        when(variable.dt) {
                            DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_BOOL -> {
                                memory.setUB(addr, 0u)
                                addr++
                            }
                            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                                memory.setUW(addr, 0u)
                                addr += 2
                            }
                            DataType.ARRAY_F -> {
                                memory.setFloat(addr, 0.0)
                                addr += program.options.compTarget.machine.FLOAT_MEM_SIZE
                            }
                            in SplitWordArrayTypes -> {
                                // lo bytes come after the hi bytes
                                memory.setUB(addr, 0u)
                                memory.setUB(addr+variable.length!!, 0u)
                                addr++
                            }
                            else -> throw IRParseException("invalid dt")
                        }
                    }
                }
            }

            variable.onetimeInitializationNumericValue?.let {
                when(variable.dt) {
                    DataType.UBYTE -> memory.setUB(addr, it.toInt().toUByte())
                    DataType.BYTE -> memory.setSB(addr, it.toInt().toByte())
                    DataType.UWORD -> memory.setUW(addr, it.toInt().toUShort())
                    DataType.WORD -> memory.setSW(addr, it.toInt().toShort())
                    DataType.FLOAT -> memory.setFloat(addr, it)
                    else -> throw IRParseException("invalid dt")
                }
            }
            variable.onetimeInitializationArrayValue?.let { iElts ->
                require(variable.length==iElts.size || iElts.size==1 || iElts.size==0)
                if(iElts.isEmpty() || iElts.size==1) {
                    val iElt = if(iElts.isEmpty()) {
                        require(variable.uninitialized)
                        IRStArrayElement(0.0, null)
                    } else {
                        require(!variable.uninitialized)
                        iElts[0]
                    }
                    initializeWithOneValue(variable, iElt, addr, symbolAddresses, memory, program)
                } else {
                    initializeWithValues(variable, iElts, addr, symbolAddresses, memory, program)
                }
            }
            require(variable.onetimeInitializationStringValue==null) { "in vm/ir, strings should have been converted into bytearrays." }
        }
    }

    private fun initializeWithValues(
        variable: IRStStaticVariable,
        iElts: IRStArray,
        startAddress: Int,
        symbolAddresses: MutableMap<String, Int>,
        memory: Memory,
        program: IRProgram
    ) {
        var address = startAddress
        when (variable.dt) {
            DataType.STR, DataType.ARRAY_UB -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toInt().toUByte()
                    memory.setUB(address, value)
                    address++
                }
            }

            DataType.ARRAY_B -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toInt().toByte()
                    memory.setSB(address, value)
                    address++
                }
            }

            DataType.ARRAY_UW -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toInt().toUShort()
                    memory.setUW(address, value)
                    address += 2
                }
            }

            DataType.ARRAY_W -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toInt().toShort()
                    memory.setSW(address, value)
                    address += 2
                }
            }

            in SplitWordArrayTypes -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toUInt()
                    memory.setUB(address, (value and 255u).toUByte())
                    memory.setUB(address + variable.length!!, (value shr 8).toUByte())
                    address++
                }
            }

            DataType.ARRAY_F -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses).toDouble()
                    memory.setFloat(address, value)
                    address += program.options.compTarget.machine.FLOAT_MEM_SIZE
                }
            }

            else -> throw IRParseException("invalid dt")
        }
    }

    private fun initializeWithOneValue(
        variable: IRStStaticVariable,
        iElt: IRStArrayElement,
        startAddress: Int,
        symbolAddresses: MutableMap<String, Int>,
        memory: Memory,
        program: IRProgram
    ) {
        var address = startAddress
        when (variable.dt) {
            DataType.STR, DataType.ARRAY_UB -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toInt().toUByte()
                repeat(variable.length!!) {
                    memory.setUB(address, value)
                    address++
                }
            }

            DataType.ARRAY_B -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toInt().toByte()
                repeat(variable.length!!) {
                    memory.setSB(address, value)
                    address++
                }
            }

            DataType.ARRAY_UW -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toInt().toUShort()
                repeat(variable.length!!) {
                    memory.setUW(address, value)
                    address += 2
                }
            }

            DataType.ARRAY_W -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toInt().toShort()
                repeat(variable.length!!) {
                    memory.setSW(address, value)
                    address += 2
                }
            }

            in SplitWordArrayTypes -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toUInt()
                val lsb = (value and 255u).toUByte()
                val msb = (value shr 8).toUByte()
                repeat(variable.length!!) {
                    memory.setUB(address, lsb)
                    memory.setUB(address + variable.length!!, msb)
                    address++
                }
            }

            DataType.ARRAY_F -> {
                val value = getInitializerValue(variable.dt, iElt, symbolAddresses).toDouble()
                repeat(variable.length!!) {
                    memory.setFloat(address, value)
                    address += program.options.compTarget.machine.FLOAT_MEM_SIZE
                }
            }

            else -> throw IRParseException("invalid dt")
        }
    }

    private fun getInitializerValue(arrayDt: DataType, elt: IRStArrayElement, symbolAddresses: MutableMap<String, Int>): Double {
        if(elt.addressOfSymbol!=null) {
            when(arrayDt) {
                DataType.ARRAY_UB, DataType.STR, DataType.ARRAY_B, DataType.ARRAY_BOOL -> {
                    val name = elt.addressOfSymbol!!
                    val symbolAddress = if(name.startsWith('<')) {
                        symbolAddresses[name.drop(1)]?.and(255)
                            ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    } else if(name.startsWith('>')) {
                        symbolAddresses[name.drop(1)]?.shr(8)
                            ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    } else
                        throw IRParseException("for byte-array address-of, expected < or > (lsb/msb)")
                    return symbolAddress.toDouble()
                }
                else -> {
                    val name = elt.addressOfSymbol!!
                    val symbolAddress = symbolAddresses[name]
                        ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    return symbolAddress.toDouble()
                }
            }
        } else {
            return elt.number!!
        }
    }
}
