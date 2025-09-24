package prog8.vm

import prog8.Either
import prog8.code.core.*
import prog8.intermediate.*
import prog8.left
import prog8.right

class VmProgramLoader {
    private val placeholders = mutableMapOf<Pair<IRCodeChunk, Int>, String>()      // program chunk+index to symbolname
    private val subroutines = mutableMapOf<String, IRSubroutine>()                 // label to subroutine node
    private val artificialLabelAddresses = mutableMapOf<String, Int>()

    fun load(irProgram: IRProgram, memory: Memory): Pair<List<IRCodeChunk>, Map<String, Int>> {
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
                    is IRAsmSubroutine -> throw IRParseException("vm does not support asmsubs (use normal sub): ${child.label}")
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
                            }
                        }
                    }
                }
            }
        }

        pass2translateSyscalls(programChunks + irProgram.globalInits)
        pass2replaceLabelsByProgIndex(programChunks, variableAddresses, subroutines)
        phase2relinkReplacedChunks(chunkReplacements, programChunks)

        (programChunks + irProgram.globalInits).forEach {
            it.instructions.forEach { ins ->
                if (ins.labelSymbol != null && ins.opcode !in OpcodesThatBranch)
                    requireNotNull(ins.address) { "instruction with labelSymbol for a var should have value set to the memory address" }
            }
        }

        return programChunks to artificialLabelAddresses
    }

    private fun phase2relinkReplacedChunks(
        replacements: List<Pair<IRCodeChunkBase, IRCodeChunk>>,
        programChunks: List<IRCodeChunk>
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

    private fun pass2translateSyscalls(chunks: List<IRCodeChunk>) {
        chunks.forEach { chunk ->
            chunk.instructions.withIndex().forEach { (index, ins) ->
                if(ins.opcode == Opcode.SYSCALL) {
                    // convert IR Syscall to VM Syscall
                    val vmSyscall = when(ins.immediate!!) {
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
                        IMSyscall.CALLFAR2.number -> throw IRParseException("vm doesn't support the callfar2() syscall")
                        IMSyscall.MEMCOPY.number -> Syscall.MEMCOPY
                        else -> null
                    }

                    if(vmSyscall!=null)
                        chunk.instructions[index] = ins.copy(immediate = vmSyscall.ordinal)
                }

                val label = ins.labelSymbol
                if (label != null && (ins.opcode !in OpcodesThatBranch)) {
                    placeholders[Pair(chunk, index)] = label
                }
            }
        }
    }

    private fun pass2replaceLabelsByProgIndex(
        chunks: List<IRCodeChunk>,
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
                        throw IRParseException("label '$label' not found in variables nor labels. VM cannot reference other things such as blocks, and constants should have been replaced by their value")
                    else if(instr.opcode in OpcodesThatBranch)
                        chunk.instructions[line] = instr.copy(branchTarget = target, address = null)
                    else {
                        var address = artificialLabelAddresses[label]
                        if(address==null) {
                            // generate an artificial address
                            address = 0xa000 + artificialLabelAddresses.size
                            artificialLabelAddresses[label] = address
                        }
                        chunk.instructions[line] = instr.copy(address=address, branchTarget = target)
                    }
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

            // Zero out uninitialized both clean AND dirty variables.
            // Dirty vars actually are ALSO are zero'd out here at program startup,
            // but NOT at each entry of the subroutine they're declared in.
            // This saves the clear to zero overhead in the subroutine, while still having deterministic start state.
            if(variable.uninitialized) {
                val dt = variable.dt
                if(variable.dt.isArray) {
                    repeat(variable.length!!.toInt()) {
                        when {
                            dt.isPointerArray -> {
                                memory.setUW(addr, 0u)      // array of pointers is just array of word addresses
                                addr += 2
                            }
                            dt.isString || dt.isBoolArray || dt.isByteArray -> {
                                memory.setUB(addr, 0u)
                                addr++
                            }
                            dt.isSplitWordArray -> {
                                // lo bytes come after the hi bytes
                                memory.setUB(addr, 0u)
                                memory.setUB(addr+variable.length!!.toInt(), 0u)
                                addr++
                            }
                            dt.isWordArray -> {
                                memory.setUW(addr, 0u)
                                addr += 2
                            }
                            dt.isFloatArray -> {
                                memory.setFloat(addr, 0.0)
                                addr += program.options.compTarget.FLOAT_MEM_SIZE
                            }
                            else -> throw IRParseException("invalid dt")
                        }
                    }
                } else {
                    when {
                        dt.isUnsignedByte || dt.isBool -> memory.setUB(addr, 0u)
                        dt.isSignedByte -> memory.setSB(addr, 0)
                        dt.isUnsignedWord || dt.isPointer -> memory.setUW(addr, 0u)
                        dt.isSignedWord -> memory.setSW(addr, 0)
                        dt.isLong -> memory.setSL(addr, 0)
                        dt.isFloat -> memory.setFloat(addr, 0.0)
                        else -> throw IRParseException("invalid dt")
                    }
                }
            }

            variable.onetimeInitializationNumericValue?.let {
                when {
                    variable.dt.isUnsignedByte || variable.dt.isBool -> memory.setUB(addr, it.toInt().toUByte())
                    variable.dt.isSignedByte -> memory.setSB(addr, it.toInt().toByte())
                    variable.dt.isUnsignedWord -> memory.setUW(addr, it.toInt().toUShort())
                    variable.dt.isSignedWord -> memory.setSW(addr, it.toInt().toShort())
                    variable.dt.isLong -> memory.setSL(addr, it.toInt())
                    variable.dt.isFloat -> memory.setFloat(addr, it)
                    else -> throw IRParseException("invalid dt")
                }
            }
            variable.onetimeInitializationArrayValue?.let { iElts ->
                require(variable.length==iElts.size.toUInt())
                initializeWithValues(variable, iElts, addr, symbolAddresses, memory, program)
            }
            require(variable.onetimeInitializationStringValue==null) { "in vm/ir, strings should have been converted into bytearrays." }
        }

        program.st.allStructInstances().forEach { instance ->
            val address = allocations.allocations.getValue(instance.name)
            var a = address
            if(instance.values.isEmpty()) {
                // Zero out BSS for this uninitialized instance
                // TODO doesn't work yet????
                repeat(instance.size.toInt()) {
                    memory.setUB(a, 0u)
                    a++
                }
            } else {
                instance.values.forEach {
                    val value: Double = if(it.value.bool==true) 1.0
                        else if(it.value.bool==false) 0.0
                        else if(it.value.number!=null) it.value.number!!
                        else if(it.value.addressOfSymbol!=null) {
                            val name = it.value.addressOfSymbol!!
                            val symbolAddress = symbolAddresses[name]
                                ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                            symbolAddress.toDouble()
                        }
                        else throw IRParseException("invalid initializer value")

                    when {
                        it.dt.isByteOrBool -> {
                            memory.setUB(a, value.toInt().toUByte())
                            a++
                        }
                        it.dt.isWord || it.dt.isPointer -> {
                            memory.setUW(a, value.toInt().toUShort())
                            a += 2
                        }
                        it.dt == BaseDataType.LONG -> {
                            memory.setSL(a, value.toInt())
                            a += 4
                        }
                        it.dt == BaseDataType.FLOAT -> {
                            memory.setFloat(a, value)
                            a += program.options.compTarget.FLOAT_MEM_SIZE
                        }
                        else -> throw IRParseException("invalid dt")
                    }
                }
            }
            require(a-address == instance.size.toInt())  {
                "invalid struct init size, expected ${instance.size}, got ${a-address}"
            }
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
        when {
            variable.dt.isBoolArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { throw IRParseException("didn't expect float") },
                        { b -> memory.setUB(address, if(b) 1u else 0u) }
                    )
                    address++
                }
            }
            variable.dt.isString || variable.dt.isUnsignedByteArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setUB(address, it.toInt().toUByte()) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address++
                }
            }

            variable.dt.isSignedByteArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setSB(address, it.toInt().toByte()) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address++
                }
            }

            variable.dt.isSplitWordArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        {
                            val integer = it.toUInt()
                            memory.setUB(address, (integer and 255u).toUByte())
                            memory.setUB(address + variable.length!!.toInt(), (integer shr 8).toUByte())
                        },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address++
                }
            }

            variable.dt.isUnsignedWordArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setUW(address, it.toInt().toUShort()) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address += 2
                }
            }

            variable.dt.isSignedWordArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setSW(address, it.toInt().toShort()) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address += 2
                }
            }

            variable.dt.isLongArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setSL(address, it.toInt()) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address += 4
                }
            }

            variable.dt.isFloatArray -> {
                for (elt in iElts) {
                    val value = getInitializerValue(variable.dt, elt, symbolAddresses)
                    value.fold(
                        { memory.setFloat(address, it) },
                        { throw IRParseException("didn't expect bool") }
                    )
                    address += program.options.compTarget.FLOAT_MEM_SIZE
                }
            }

            else -> throw IRParseException("invalid dt")
        }
    }

    private fun getInitializerValue(arrayDt: DataType, elt: IRStArrayElement, symbolAddresses: MutableMap<String, Int>): Either<Double, Boolean> {
        if(elt.addressOfSymbol!=null) {
            when {
                arrayDt.isString || arrayDt.isByteArray || arrayDt.isBoolArray -> {
                    val name = elt.addressOfSymbol!!
                    val symbolAddress = if(name.startsWith('<')) {
                        symbolAddresses[name.drop(1)]?.and(255)
                            ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    } else if(name.startsWith('>')) {
                        symbolAddresses[name.drop(1)]?.shr(8)
                            ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    } else
                        throw IRParseException("for byte-array address-of, expected < or > (lsb/msb)")
                    return left(symbolAddress.toDouble())
                }
                else -> {
                    val name = elt.addressOfSymbol!!
                    val symbolAddress = symbolAddresses[name]
                        ?: throw IRParseException("vm cannot yet load a label address as a value: $name")
                    return left(symbolAddress.toDouble())
                }
            }
        } else if (elt.number!=null) {
            return left(elt.number!!)
        } else
            return right(elt.bool!!)
    }
}
