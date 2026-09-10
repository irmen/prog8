package prog8.intermediate

import prog8.code.core.*

val IMemSizer.pointerIRType: IRDataType
    get() = if(POINTER_MEM_SIZE > 2u) IRDataType.LONG else IRDataType.WORD

val IMemSizer.indexRegType: IRDataType
    get() = if(POINTER_MEM_SIZE > 2u) IRDataType.WORD else IRDataType.BYTE

/*

Intermediate Represenation of the compiled program.

Note that even though it is called IR, it is not fully "intermediate".
This IR program is targeted to a single chosen target machine; some code in it
and all included library files are for this specific single chosen compilation target.
It will *not* be possible to create a binary program for a different target machine
from this IR than the one initially chosen!

Note: all symbol names are flattened so that they're a single string that is globally unique.


PROGRAM:
    OPTIONS                 (from CompilationOptions)
    ASMSYMBOLS              (from command line defined symbols)
    VARIABLES               (from Symboltable)
    CONSTANTS               (form Symboltable)
    MEMORYMAPPEDVARIABLES   (from Symboltable)
    MEMORYSLABS             (from Symboltable)
    INITGLOBALS
        C (CODE)
            CODE-LINE       (assignment to initialize a variable)
            ...
    BLOCK
        ASM
        ASM
        SUB
            PARAMS
            ASM
            ASM
            C (CODE)
                CODE-LINE  (label, instruction, comment, inlinebinary)
                CODE-LINE
                ...
            C (CODE)
            C (CODE)
            BYTES
            ...
        SUB
        SUB
        ASMSUB
            PARAMS
            ASM
        ASMSUB
            PARAMS
            ASM
        ...
    BLOCK
    BLOCK
    ...
*/

class IRProgram(val name: String,
                val st: IRSymbolTable,
                val options: CompilationOptions,
                val encoding: IStringEncoding) {

    val asmSymbols = mutableMapOf<String, String>()
    val globalInits = IRCodeChunk(null, null)
    val blocks = mutableListOf<IRBlock>()
    var wasPackingApplied = false

    fun allSubs(): Sequence<IRSubroutine> = blocks.asSequence().flatMap { it.children.filterIsInstance<IRSubroutine>() }
    fun allAsmSubs(): Sequence<IRAsmSubroutine> = blocks.asSequence().flatMap { it.children.filterIsInstance<IRAsmSubroutine>() }
    fun foreachSub(operation: (sub: IRSubroutine) -> Unit) = allSubs().forEach { operation(it) }

    /**
     * Visit every chunk in the program in a stable deterministic order,
     * recursing through nested [IRLoopChunk] bodies. Inline assembly and
     * binary chunks are visited as chunks even though they carry no IR instructions.
     */
    fun forEachChunk(action: (chunk: IRCodeChunkBase) -> Unit) {
        allSubs().flatMap { it.chunks }.forEach { it.forEachChunkRecursive(action) }
        allAsmSubs().forEach { it.asmChunk.forEachChunkRecursive(action) }
        // Top-level block chunks (not inside a subroutine) are rare but must not be missed.
        blocks.asSequence()
            .flatMap { it.children.asSequence() }
            .filterIsInstance<IRCodeChunkBase>()
            .forEach { it.forEachChunkRecursive(action) }
        globalInits.forEachChunkRecursive(action)
    }

    fun forEachInstruction(action: (IRInstruction) -> Unit) {
        forEachChunk { chunk -> chunk.instructions.forEach(action) }
    }

    fun foreachCodeChunk(operation: (chunk: IRCodeChunkBase) -> Unit) = forEachChunk(operation)

    fun countCodeElements(): Pair<Int, Int> {
        var numInstr = 0
        var numChunks = 0
        forEachChunk { numChunks++ }
        forEachInstruction { numInstr++ }
        return Pair(numInstr, numChunks)
    }

    fun countUsedRegisters(): Int {
        val used = registersUsed()
        return (used.readRegs.keys + used.writeRegs.keys).size
    }

    /** label -> code chunk, filled in by linkChunks() */
    var linkedCodeTargets: Map<String, IRCodeChunkBase> = emptyMap()
        private set

    /** the code chunk that a code reference points to (null if it is not a static label) */
    fun resolveCodeTarget(reference: CodeReference): IRCodeChunkBase? = when(reference) {
        is CodeReference.Label -> linkedCodeTargets[reference.name]
        is CodeReference.Absolute, is CodeReference.Indirect -> null
    }

    fun getChunkWithLabel(label: String): IRCodeChunkBase {
        var found: IRCodeChunkBase? = null
        forEachChunk { chunk ->
            if(found==null && chunk.label==label)
                found = chunk
        }
        return found ?: throw NoSuchElementException("no chunk with label '$label'")
    }

    fun addGlobalInits(chunk: IRCodeChunk) {
        globalInits += chunk
    }

    fun addBlock(block: IRBlock) {
        require(blocks.all { it.label != block.label}) { "duplicate block ${block.label} ${block.position}" }
        blocks.add(block)
    }

    fun addAsmSymbols(symbolDefs: Map<String, String>) {
        asmSymbols += symbolDefs
    }

    fun linkChunks() {
        fun addLabel(label: String?, chunk: IRCodeChunkBase, map: MutableMap<String, IRCodeChunkBase>) {
            if (label == null)
                return
            val previous = map.putIfAbsent(label, chunk)
            require(previous == null || previous === chunk) { "duplicate code chunk label: $label" }
        }

        fun collectLabels(chunk: IRCodeChunkBase, map: MutableMap<String, IRCodeChunkBase>) {
            chunk.forEachChunkRecursive { addLabel(it.label, it, map) }
        }

        fun getLabeledChunks(): Map<String, IRCodeChunkBase> {
            val result = mutableMapOf<String, IRCodeChunkBase>()
            collectLabels(globalInits, result)
            blocks.forEach { block ->
                block.children.forEach { child ->
                    when(child) {
                        is IRAsmSubroutine -> collectLabels(child.asmChunk, result)
                        is IRCodeChunk -> collectLabels(child, result)
                        is IRInlineAsmChunk -> collectLabels(child, result)
                        is IRInlineBinaryChunk -> collectLabels(child, result)
                        is IRLoopChunk -> collectLabels(child, result)
                        is IRSubroutine -> {
                            child.chunks.forEach { collectLabels(it, result) }
                            if (child.chunks.isNotEmpty()) {
                                addLabel(child.label, child.chunks.first(), result)
                            }
                        }
                    }
                }
            }
            return result
        }

        val labeledChunks = getLabeledChunks()
        linkedCodeTargets = labeledChunks

        fun verifyCodeTarget(instruction: IRInstruction) {
            val label = instruction.labelTarget ?: return
            if(resolveCodeTarget(instruction.codeTarget!!) == null)
                throw AssemblyError("Missing jump/call target: $label")
        }

        if(globalInits.isNotEmpty()) {
            if(globalInits.next==null) {
                // link globalinits to subsequent chunk
                val firstBlock = blocks.firstOrNull()
                if(firstBlock!=null && firstBlock.isNotEmpty()) {
                    firstBlock.children.first().let { child ->
                        when(child) {
                            is IRAsmSubroutine ->
                                throw AssemblyError("cannot link next to asmsub $child")
                            is IRCodeChunk -> globalInits.next = child
                            is IRInlineAsmChunk -> globalInits.next = child
                            is IRInlineBinaryChunk -> globalInits.next = child
                            is IRLoopChunk -> globalInits.next = child
                            is IRSubroutine -> {
                                if(child.chunks.isNotEmpty())
                                    globalInits.next = child.chunks.first()
                            }
                        }
                    }
                }
            }
        }

        fun linkCodeChunk(chunk: IRCodeChunk, next: IRCodeChunkBase?) {
            // link sequential chunks
            val lastInstr = chunk.instructions.lastOrNull()
            if (lastInstr == null || lastInstr.opcode !in OpcodesThatBranchUnconditionally) {
                // no jump at the end, so link to next chunk (if it exists)
                chunk.next = next
            } else {
                chunk.next = null
            }

            // verify that all jump and branching instructions have a resolvable target
            chunk.instructions.forEach { verifyCodeTarget(it) }
        }

        fun linkBaseChunk(chunk: IRCodeChunkBase, next: IRCodeChunkBase?) {
            when (chunk) {
                is IRCodeChunk -> linkCodeChunk(chunk, next)
                is IRLoopChunk -> {
                    chunk.next = next
                    // link body chunks sequentially (internal); last body chunk's next stays null (loop back-edge emitted by backend)
                    chunk.body.withIndex().forEach { (idx, bodyChunk) ->
                        val nextBody = if(idx < chunk.body.size - 1) chunk.body[idx+1] else null
                        linkBaseChunk(bodyChunk, nextBody)
                    }
                    // resolve branch targets for instructions inside body (including nested loops' bodies recursively)
                    fun resolveBody(bc: IRCodeChunkBase) {
                        if(bc is IRLoopChunk) {
                            bc.body.forEach { resolveBody(it) }
                        } else {
                            bc.instructions.forEach { verifyCodeTarget(it) }
                        }
                    }
                    chunk.body.forEach { resolveBody(it) }
                }
                is IRInlineAsmChunk -> {
                    val lastInstr = chunk.instructions.lastOrNull()
                    if (lastInstr == null || lastInstr.opcode !in OpcodesThatBranchUnconditionally)
                        chunk.next = next
                    else
                        chunk.next = null
                }
                is IRInlineBinaryChunk -> {
                    chunk.next = next
                }
            }
        }

        fun linkSubroutineChunks(sub: IRSubroutine) {
            sub.chunks.withIndex().forEach { (index, chunk) ->
                val next = if(index < sub.chunks.size - 1) sub.chunks[index + 1] else null
                linkBaseChunk(chunk, next)
            }
        }

        blocks.forEach { block ->
            block.children.forEachIndexed { index, child ->
                val next = if(index < block.children.lastIndex) block.children[index+1] as? IRCodeChunkBase else null
                when (child) {
                    is IRAsmSubroutine -> linkBaseChunk(child.asmChunk, next)
                    is IRCodeChunk -> linkBaseChunk(child, next)
                    is IRInlineAsmChunk -> linkBaseChunk(child, next)
                    is IRInlineBinaryChunk -> linkBaseChunk(child, next)
                    is IRLoopChunk -> linkBaseChunk(child, next)
                    is IRSubroutine -> linkSubroutineChunks(child)
                }
            }
        }
        linkBaseChunk(globalInits, globalInits.next)
    }

    fun validate() {
        fun validateCodeTarget(instr: IRInstruction) {
            val label = instr.labelTarget ?: return
            if(instr.opcode in OpcodesThatBranch)
                require(resolveCodeTarget(instr.codeTarget!!) != null) { "branching instruction to label $label should have a resolvable target chunk" }
        }

        fun validateChunk(chunk: IRCodeChunkBase, sub: IRSubroutine?, emptyChunkIsAllowed: Boolean) {
            if (chunk is IRLoopChunk) {
                require(chunk.label!=null) { "loop chunk needs label" }
                require(chunk.trip in 1..65536)
                chunk.body.forEach { validateChunk(it, null, false) }
                // loop's next is validated like normal chunk linking (outside)
                chunk.instructions.forEach { validateCodeTarget(it) }
                return
            }
            if (chunk is IRCodeChunk) {
                if(!emptyChunkIsAllowed)
                    require(chunk.instructions.isNotEmpty() || chunk.label != null)
                if(chunk.instructions.lastOrNull()?.opcode in OpcodesThatBranchUnconditionally)
                    require(chunk.next == null) { "chunk ending with a jump or return shouldn't be linked to next" }
                else if (sub!=null) {
                    // if chunk is NOT the last in the block, it needs to link to next.
                    val isLast = sub.chunks.last() === chunk
                    require(isLast || chunk.next != null) { "chunk needs to be linked to next" }
                }
            }
            else {
                require(chunk.instructions.isEmpty())
                if(chunk is IRInlineAsmChunk)
                    require(!chunk.isIR) { "inline IR-asm should have been converted into regular code chunk"}
            }
            chunk.instructions.forEach { validateCodeTarget(it) }
        }

        validateChunk(globalInits, null, true)
        blocks.forEach { block ->
            if(block.isNotEmpty()) {
                block.children.filterIsInstance<IRInlineAsmChunk>().forEach { chunk ->
                    require(chunk.instructions.isEmpty())
                    require(!chunk.isIR) { "inline IR-asm should have been converted into regular code chunk"}
                }
                block.children.filterIsInstance<IRSubroutine>().forEach { sub ->
                    if(sub.chunks.isNotEmpty()) {
                        require(sub.chunks.first().label == sub.label) { "first chunk in subroutine should have sub name (label) as its label" }
                    }
                    sub.chunks.forEach { validateChunk(it, sub, false) }
                }
                // also validate any top-level loop chunks in blocks (unlikely but handle)
                block.children.filterIsInstance<IRLoopChunk>().forEach { validateChunk(it, null, false) }
            }
        }
    }

    fun registersUsed(permissive: Boolean = wasPackingApplied): RegistersUsed {
        val readRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val writeRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val regsTypes = mutableMapOf<VirtualRegister, IRDataType>()

        fun addUsed(usedRegisters: RegistersUsed, child: IIRBlockElement) {
            usedRegisters.readRegs.forEach{ (reg, count) -> readRegsCounts[reg] = readRegsCounts.getValue(reg) + count }
            usedRegisters.writeRegs.forEach{ (reg, count) -> writeRegsCounts[reg] = writeRegsCounts.getValue(reg) + count }
        if(permissive) {
            // After register packing, the same slot number may be used with different types
            // in different subroutines (the packer's globalSlotTypes mechanism ensures type
            // consistency within each subroutine). Use putIfAbsent for cross-subroutine types.
            usedRegisters.regsTypes.forEach{ (reg, type) ->
                regsTypes.putIfAbsent(reg, type)
            }
        } else {
                usedRegisters.regsTypes.forEach{ (reg, type) ->
                    val existingType = regsTypes[reg]
                    if (existingType!=null) {
                        if (existingType != type) {
                            // POINTER is compatible with WORD or LONG (size depends on target)
                            val compatible = (existingType==IRDataType.POINTER && type in setOf(IRDataType.WORD, IRDataType.LONG)) ||
                                    (type==IRDataType.POINTER && existingType in setOf(IRDataType.WORD, IRDataType.LONG))
                            if(!compatible)
                                throw IllegalArgumentException("register $reg given multiple types! $existingType and $type  ${this.name}<--${child.label ?: child}")
                        }
                    } else
                        regsTypes[reg] = type
                }
            }
        }

        // Aggregate via uniform traversal. Loop containers themselves carry no
        // instructions; only leaf chunks contribute, so nested bodies are
        // counted exactly once. Inline-asm chunks contribute via their parsed
        // usedRegisters() because their instructions list is empty.
        forEachChunk { chunk ->
            if(chunk is IRLoopChunk)
                return@forEachChunk
            addUsed(chunk.usedRegisters(), chunk)
        }
        return RegistersUsed(readRegsCounts, writeRegsCounts, regsTypes)
    }

    fun convertAsmChunks() {
        fun convert(asmChunk: IRInlineAsmChunk): IRCodeChunks {
            val chunks = mutableListOf<IRCodeChunkBase>()
            var chunk = IRCodeChunk(asmChunk.label, null)
            asmChunk.assembly.lineSequence().filter{it.isNotBlank()}.forEach {
                val parsed = parseIRCodeLine(it.trim())
                when (parsed) {
                    is ParsedIRLine.Instruction -> chunk += parsed.value
                    is ParsedIRLine.Label -> {
                        val lastChunk = chunk
                        if(chunk.isNotEmpty() || chunk.label!=null)
                            chunks += chunk
                        chunk = IRCodeChunk(parsed.name, null)
                        val lastInstr = lastChunk.instructions.lastOrNull()
                        if(lastInstr==null || lastInstr.opcode !in OpcodesThatBranchUnconditionally)
                            lastChunk.next = chunk
                    }
                }
            }
            if(chunk.isNotEmpty() || chunk.label!=null)
                chunks += chunk
            chunks.lastOrNull()?.let {
                val lastInstr = it.instructions.lastOrNull()
                if(lastInstr==null || lastInstr.opcode !in OpcodesThatBranchUnconditionally)
                    it.next = asmChunk.next
            }
            return chunks
        }

        blocks.forEach { block ->
            val chunkReplacementsInBlock = mutableListOf<Pair<IRCodeChunkBase, IRCodeChunks>>()
            block.children.filterIsInstance<IRInlineAsmChunk>().forEach { asmchunk ->
                if(asmchunk.isIR) chunkReplacementsInBlock += asmchunk to convert(asmchunk)
                // non-IR asm cannot be converted
            }
            chunkReplacementsInBlock.reversed().forEach { (old, new) ->
                val index = block.children.indexOf(old)
                block.children.removeAt(index)
                new.reversed().forEach { block.children.add(index, it) }
            }
            chunkReplacementsInBlock.clear()

            block.children.filterIsInstance<IRSubroutine>().forEach { sub ->
                val chunkReplacementsInSub = mutableListOf<Pair<IRCodeChunkBase, IRCodeChunks>>()
                sub.chunks.filterIsInstance<IRInlineAsmChunk>().forEach { asmchunk ->
                    if(asmchunk.isIR) chunkReplacementsInSub += asmchunk to convert(asmchunk)
                    // non-IR asm cannot be converted
                }

                chunkReplacementsInSub.reversed().forEach { (old, new) ->
                    val index = sub.chunks.indexOf(old)
                    sub.chunks.removeAt(index)
                    new.reversed().forEach { sub.chunks.add(index, it) }
                }
                chunkReplacementsInSub.clear()
            }
            // also handle inline asm inside loop bodies
            fun convertLoopBody(loop: IRLoopChunk) {
                val replacements = mutableListOf<Pair<IRCodeChunkBase, IRCodeChunks>>()
                loop.body.filterIsInstance<IRInlineAsmChunk>().forEach { asmchunk ->
                    if(asmchunk.isIR) replacements += asmchunk to convert(asmchunk)
                }
                replacements.reversed().forEach { (old, new) ->
                    val index = loop.body.indexOf(old)
                    loop.body.removeAt(index)
                    new.reversed().forEach { loop.body.add(index, it) }
                }
                // recurse into nested loops
                loop.body.filterIsInstance<IRLoopChunk>().forEach { convertLoopBody(it) }
            }
            block.children.filterIsInstance<IRLoopChunk>().forEach { convertLoopBody(it) }
            block.children.filterIsInstance<IRSubroutine>().forEach { sub ->
                sub.chunks.filterIsInstance<IRLoopChunk>().forEach { convertLoopBody(it) }
            }
        }
    }

    fun splitSSAchunks() {

        class SplitInfo(val chunk: IRCodeChunkBase, val splitAt: Int, val blockParent: IRBlock?, val subParent: IRSubroutine?, val chunkIndex: Int)

        val tosplit = mutableListOf<SplitInfo>()

        fun split(chunk: IRCodeChunk, parent: IRBlock, chunkIndex: Int) {
            chunk.instructions.withIndex().forEach { (index, instr) ->
                if(instr.opcode in OpcodesThatEndSSAblock) {
                    if(instr !== chunk.instructions.last()) {
                        // to be a proper SSA basic block, this instruction has to be the last one in the block.
                        // split the current chunk and link both halves together using the next pointer
                        tosplit += SplitInfo(chunk, index, parent, null, chunkIndex)
                    }
                }
            }
        }

        fun split(chunk: IRCodeChunk, parent: IRSubroutine, chunkIndex: Int) {
            chunk.instructions.withIndex().forEach { (index, instr) ->
                if(instr.opcode in OpcodesThatEndSSAblock) {
                    if(instr !== chunk.instructions.last()) {
                        // to be a proper SSA basic block, this instruction has to be the last one in the block.
                        // split the current chunk and link both halves together using the next pointer
                        tosplit += SplitInfo(chunk, index, null, parent, chunkIndex)
                    }
                }
            }
        }

        this.blocks.forEach { block ->
            block.children.withIndex().forEach { (index, child) ->
                when(child) {
                    is IRCodeChunk -> split(child, block, index)
                    is IRSubroutine -> child.chunks.withIndex().forEach { (index2, chunk) ->
                        if(chunk is IRCodeChunk)
                            split(chunk, child, index2)
                    }
                    else -> {}
                }
            }
        }

        for(split in tosplit.reversed()) {
            val chunk = split.chunk
            if(split.blockParent!=null)
                require(chunk===split.blockParent.children[split.chunkIndex])
            else
                require(chunk===split.subParent!!.chunks[split.chunkIndex])
            val totalSize = chunk.instructions.size
            val splitPoint = split.splitAt + 1
            val secondChunk = IRCodeChunk(null, chunk.next)
            // Move instructions from splitPoint onward to secondChunk without copying
            secondChunk.instructions.addAll(chunk.instructions.subList(splitPoint, totalSize))
            chunk.instructions.subList(splitPoint, totalSize).clear()
            require(chunk.instructions.last().opcode in OpcodesThatEndSSAblock)
            require(chunk.instructions.size + secondChunk.instructions.size == totalSize)
            if(chunk.instructions.last().opcode !in OpcodesThatBranchUnconditionally) {
                chunk.next = secondChunk
                if(split.blockParent!=null) split.blockParent.children.add(split.chunkIndex+1, secondChunk)
                else split.subParent!!.chunks.add(split.chunkIndex+1, secondChunk)
            } else {
                chunk.next = null
            }
        }
    }


    /**
     * Verify each register has a consistent type across all uses.
     *
     * Cannot be used after register packing: the packer may assign the same slot to
     * differently-typed (but POINTER-compatible) registers in different subroutines,
     * which this strict check would reject. Use RegisterPacker.rebuildTypeMap() instead.
     */
    fun verifyRegisterTypes(registerTypes: Map<VirtualRegister, IRDataType>) {
        forEachChunk { chunk ->
            if(chunk is IRLoopChunk)
                return@forEachChunk
            chunk.usedRegisters().validate(registerTypes, chunk)
        }
    }
}

class IRBlock(
    val label: String,
    val library: Boolean,
    val options: Options,
    val position: Position
) {
    val children = mutableListOf<IIRBlockElement>()

    class Options(val address: UInt? = null,
                  val forceOutput: Boolean = false,
                  val noSymbolPrefixing: Boolean = false,
                  val veraFxMuls: Boolean = false,
                  val ignoreUnused: Boolean = false,
                  val amigaChipram: Boolean = false)

    operator fun plusAssign(sub: IRSubroutine) { children += sub }
    operator fun plusAssign(sub: IRAsmSubroutine) { children += sub }
    operator fun plusAssign(asm: IRInlineAsmChunk) { children += asm }
    operator fun plusAssign(binary: IRInlineBinaryChunk) { children += binary }
    operator fun plusAssign(irCodeChunk: IRCodeChunk) { children += irCodeChunk }
    operator fun plusAssign(loop: IRLoopChunk) { children += loop }

    fun isEmpty(): Boolean = children.isEmpty() || children.all { it.isEmpty() }
    fun isNotEmpty(): Boolean = !isEmpty()
}


sealed interface IIRBlockElement {
    val label: String?
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean
}


class IRSubroutine(
    override val label: String,
    val parameters: List<IRParam>,
    val returns: List<DataType>,
    val position: Position): IIRBlockElement {

    class IRParam(val name: String, val dt: DataType)

    val chunks = mutableListOf<IRCodeChunkBase>()

    init {
        require('.' in label) {"subroutine name is not scoped: $label"}
        require(!label.startsWith("main.main.")) {"subroutine name invalid main prefix: $label"}

        // params and return value should not be str
        require(parameters.all{ it.dt.isNumericOrBool || it.dt.isPointer }) {"parameter is not a bool, number or pointer"}
        require(returns.all { it.isNumericOrBool || it.isPointer}) {"returntype is not a bool, number or pointer"}
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        require(chunk.isNotEmpty() || chunk.label!=null) {
            "chunk should have instructions and/or a label"
        }
        chunks+= chunk
    }

    override fun isEmpty(): Boolean = chunks.isEmpty() || chunks.all { it.isEmpty() }
    override fun isNotEmpty(): Boolean  = !isEmpty()

    /**
     * Visit every chunk in the subroutine in stable deterministic depth-first
     * order, recursing through nested [IRLoopChunk] bodies. The loop chunk
     * itself is visited before its body. Inline assembly and binary chunks
     * are visited as chunks even though they carry no IR instructions.
     */
    fun forEachChunk(action: (IRCodeChunkBase) -> Unit) {
        chunks.forEach { it.forEachChunkRecursive(action) }
    }

    fun forEachInstruction(action: (IRInstruction) -> Unit) {
        forEachChunk { chunk -> chunk.instructions.forEach(action) }
    }
}


class IRAsmSubroutine(
    override val label: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<IRAsmParam>,
    val returns: List<IRAsmParam>,
    val asmChunk: IRInlineAsmChunk,
    val position: Position,
    val isInline: Boolean = false   // if true, the codegen MUST inline the assembly body at the call site (no jsr, no rts)
): IIRBlockElement {

    class IRAsmParam(val reg: RegisterOrStatusflag, val dt: DataType)

    init {
        require('.' in label) { "subroutine name is not scoped: $label" }
        require(!label.startsWith("main.main.")) { "subroutine name invalid main prefix: $label" }
        require(label==asmChunk.label)
    }

    private val registersUsed by lazy { registersUsedInAssembly(asmChunk.isIR, asmChunk.assembly) }

    fun usedRegisters() = registersUsed
    override fun isEmpty(): Boolean = if(address==null) asmChunk.isEmpty() else false
    override fun isNotEmpty(): Boolean = !isEmpty()
}


sealed class IRCodeChunkBase(override val label: String?, var next: IRCodeChunkBase?): IIRBlockElement {
    val instructions = mutableListOf<IRInstruction>()

    abstract override fun isEmpty(): Boolean
    abstract override fun isNotEmpty(): Boolean
    abstract fun usedRegisters(): RegistersUsed

    /**
     * Visit this chunk and, for [IRLoopChunk], all nested body chunks
     * depth-first. The receiver is visited first, then its body in order.
     */
    fun forEachChunkRecursive(action: (IRCodeChunkBase) -> Unit) {
        action(this)
        if(this is IRLoopChunk)
            body.forEach { it.forEachChunkRecursive(action) }
    }
}

class IRCodeChunk(label: String?, next: IRCodeChunkBase?): IRCodeChunkBase(label, next) {

    override fun isEmpty() = instructions.isEmpty()
    override fun isNotEmpty() = instructions.isNotEmpty()
    override fun usedRegisters(): RegistersUsed {
        val readRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val writeRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val regsTypes = mutableMapOf<VirtualRegister, IRDataType>()
        instructions.forEach { it.addUsedRegistersCounts(readRegsCounts, writeRegsCounts, regsTypes, this) }
        return RegistersUsed(readRegsCounts, writeRegsCounts, regsTypes)
    }

    operator fun plusAssign(ins: IRInstruction) {
        instructions.add(ins)
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        instructions.addAll(chunk.instructions)
    }

    fun appendSrcPosition(position: Position) {
        if(!sourceLinesPositions.contains(position))
            sourceLinesPositions.add(position)
    }

    fun appendSrcPositions(positions: Collection<Position>) {
        positions.asSequence().filter { it!==Position.DUMMY }.forEach { appendSrcPosition(it) }
    }

    val sourceLinesPositions = mutableListOf<Position>()

    override fun toString(): String = "IRCodeChunk(label=$label, firstpos=${sourceLinesPositions.firstOrNull()})"
}

class IRInlineAsmChunk(label: String?,
                       val assembly: String,
                       val isIR: Boolean,
                       next: IRCodeChunkBase?): IRCodeChunkBase(label, next) {
    // note: no instructions, asm is in the property
    override fun isEmpty() = assembly.isBlank()
    override fun isNotEmpty() = assembly.isNotBlank()
    private val registersUsed by lazy { registersUsedInAssembly(isIR, assembly) }

    init {
        require(!assembly.startsWith('\n') && !assembly.startsWith('\r')) { "inline assembly should be trimmed" }
        require(!assembly.endsWith('\n') && !assembly.endsWith('\r')) { "inline assembly should be trimmed" }
    }

    override fun usedRegisters() = registersUsed
}

class IRInlineBinaryChunk(label: String?,
                          val data: Collection<UByte>,
                          next: IRCodeChunkBase?): IRCodeChunkBase(label, next) {
    // note: no instructions, data is in the property
    override fun isEmpty() = data.isEmpty()
    override fun isNotEmpty() = data.isNotEmpty()
    override fun usedRegisters() = RegistersUsed(emptyMap(), emptyMap(), emptyMap())
}

class IRLoopChunk(label: String, val trip: Int, val body: MutableList<IRCodeChunkBase>, next: IRCodeChunkBase? = null): IRCodeChunkBase(label, next) {
    init {
        require(trip in 1..65536) { "IRLoopChunk trip out of range 1..65536: $trip" }
        require(label.isNotBlank()) { "IRLoopChunk requires a label" }
    }
    // IRLoopChunk itself carries no direct IRInstructions; its body holds them
    override fun isEmpty() = body.isEmpty() || body.all { it.isEmpty() }
    override fun isNotEmpty() = !isEmpty()
    override fun usedRegisters(): RegistersUsed {
        // NOTE: an alternative design is to report a synthetic target-specific loop
        // register here (m68k d7, new6502 Y) so the register allocator avoids using it
        // inside the body. The current backends instead save/restore the physical loop
        // register around body operations, because new6502 uses Y extensively as a
        // scratch register and m68k helper routines called by the backend clobber d7
        // regardless of IR state.
        val readRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val writeRegsCounts = mutableMapOf<VirtualRegister, Int>().withDefault { 0 }
        val regsTypes = mutableMapOf<VirtualRegister, IRDataType>()
        body.forEach { chunk ->
            val used = chunk.usedRegisters()
            used.readRegs.forEach { (reg, count) -> readRegsCounts[reg] = readRegsCounts.getValue(reg) + count }
            used.writeRegs.forEach { (reg, count) -> writeRegsCounts[reg] = writeRegsCounts.getValue(reg) + count }
            used.regsTypes.forEach { (reg, type) -> regsTypes.putIfAbsent(reg, type) }
        }
        return RegistersUsed(readRegsCounts, writeRegsCounts, regsTypes)
    }
}

typealias IRCodeChunks = List<IRCodeChunkBase>


internal class IRSubtypePlaceholder(override val scopedNameString: String, val size: Int = 999999999): ISubType {
    override fun memsize(sizer: IMemSizer) = size
    override fun sameas(other: ISubType): Boolean = false
    override fun getFieldType(name: String): DataType? = null
}


internal class IRStructSubtype(val def: IRStStructDef): ISubType {
    override val scopedNameString: String get() = def.name
    override fun memsize(sizer: IMemSizer) = def.size.toInt()
    override fun sameas(other: ISubType) = other is IRStructSubtype && other.def.name == def.name
    override fun getFieldType(name: String): DataType? = def.fields.firstOrNull { it.name == name }?.type
}


class RegistersUsed(
    // virtual register -> number of uses
    val readRegs: Map<VirtualRegister, Int>,
    val writeRegs: Map<VirtualRegister, Int>,
    val regsTypes: Map<VirtualRegister, IRDataType>
) {

    override fun toString(): String {
        return "read=$readRegs, write=$writeRegs, types=$regsTypes"
    }

    fun isEmpty() = readRegs.isEmpty() && writeRegs.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun used(register: VirtualRegister) = register in readRegs || register in writeRegs
    fun used(register: RegisterNum) = used(VirtualRegister.IntReg(register))
    fun usedFp(fpRegister: RegisterNum) = used(VirtualRegister.FloatReg(fpRegister))

    fun typeOf(register: VirtualRegister) = regsTypes[register]

    /** the integer registers that are read, by register number */
    val intRegsRead: Map<RegisterNum, Int> get() = readRegs.filterKeys { it is VirtualRegister.IntReg }.mapKeys { it.key.number }
    /** the integer registers that are written, by register number */
    val intRegsWritten: Map<RegisterNum, Int> get() = writeRegs.filterKeys { it is VirtualRegister.IntReg }.mapKeys { it.key.number }
    /** the float registers that are read, by register number */
    val floatRegsRead: Map<RegisterNum, Int> get() = readRegs.filterKeys { it is VirtualRegister.FloatReg }.mapKeys { it.key.number }
    /** the float registers that are written, by register number */
    val floatRegsWritten: Map<RegisterNum, Int> get() = writeRegs.filterKeys { it is VirtualRegister.FloatReg }.mapKeys { it.key.number }
    /** the data types of the integer registers that are used, by register number */
    val intRegsTypes: Map<RegisterNum, IRDataType> get() = regsTypes.filterKeys { it is VirtualRegister.IntReg }.mapKeys { it.key.number }

    fun validate(allowed: Map<VirtualRegister, IRDataType>, chunk: IRCodeChunkBase?) {
        for((reg, type) in regsTypes) {
            val allowedType = allowed[reg]
// can't do this check because %ir {{ .. }} segments may contain registers that the compiler doesn't know about yet.
//            if(allowedType==null)
//                throw IllegalArgumentException("Reg type mismatch for register $reg type $type: no type known.  CodeChunk=$chunk label ${chunk?.label}")
            if(allowedType!=null && allowedType!=type) {
                // POINTER is compatible with WORD or LONG (size depends on target)
                val compatible = (allowedType==IRDataType.POINTER && type in setOf(IRDataType.WORD, IRDataType.LONG)) ||
                        (type==IRDataType.POINTER && allowedType in setOf(IRDataType.WORD, IRDataType.LONG))
                if(!compatible)
                    throw IllegalArgumentException("Reg type mismatch for register $reg type $type: expected ${allowed[reg]}. CodeChunk=$chunk label ${chunk?.label}")
            }
        }
    }
}

private fun registersUsedInAssembly(isIR: Boolean, assembly: String): RegistersUsed {
    val readRegsCounts = mutableMapOf<VirtualRegister, Int>()
    val writeRegsCounts = mutableMapOf<VirtualRegister, Int>()
    val regsTypes = mutableMapOf<VirtualRegister, IRDataType>()

    if(isIR) {
        assembly.lineSequence().forEach { line ->
            val t = line.trim()
            if(t.isNotEmpty()) {
                when (val result = parseIRCodeLine(t)) {
                    is ParsedIRLine.Instruction -> result.value.addUsedRegistersCounts(readRegsCounts, writeRegsCounts, regsTypes, null)
                    is ParsedIRLine.Label -> { /* labels can be skipped */ }
                }
            }
        }
    }
    return RegistersUsed(readRegsCounts, writeRegsCounts, regsTypes)
}
