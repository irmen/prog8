package prog8.intermediate

import prog8.code.core.*

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

    fun allSubs(): Sequence<IRSubroutine> = blocks.asSequence().flatMap { it.children.filterIsInstance<IRSubroutine>() }
    fun allAsmSubs(): Sequence<IRAsmSubroutine> = blocks.asSequence().flatMap { it.children.filterIsInstance<IRAsmSubroutine>() }
    fun foreachSub(operation: (sub: IRSubroutine) -> Unit) = allSubs().forEach { operation(it) }
    fun foreachCodeChunk(operation: (chunk: IRCodeChunkBase) -> Unit) {
        allSubs().flatMap { it.chunks }.forEach { operation(it) }
        allAsmSubs().forEach { operation(it.asmChunk) }
        operation(globalInits)
    }
    fun getChunkWithLabel(label: String): IRCodeChunkBase {
        for(sub in allSubs()) {
            for(chunk in sub.chunks) {
                if(chunk.label==label)
                    return chunk
            }
        }
        for(sub in allAsmSubs()) {
            if(sub.asmChunk.label==label)
                return sub.asmChunk
        }
        throw NoSuchElementException("no chunk with label '$label'")
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
        fun getLabeledChunks(): Map<String?, IRCodeChunkBase> {
            val result = mutableMapOf<String?, IRCodeChunkBase>()
            blocks.forEach { block ->
                block.children.forEach { child ->
                    when(child) {
                        is IRAsmSubroutine -> result[child.asmChunk.label] = child.asmChunk
                        is IRCodeChunk -> result[child.label] = child
                        is IRInlineAsmChunk -> result[child.label] = child
                        is IRInlineBinaryChunk -> result[child.label] = child
                        is IRSubroutine -> result.putAll(child.chunks.associateBy { it.label })
                    }
                }
            }
            result.remove(null)
            return result
        }

        val labeledChunks = getLabeledChunks()

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
            val jump = chunk.instructions.lastOrNull()?.opcode
            if (jump == null || jump !in OpcodesThatBranchUnconditionally) {
                // no jump at the end, so link to next chunk (if it exists)
                if(next!=null) {
                    when (next) {
                        is IRCodeChunk if chunk.instructions.lastOrNull()?.opcode !in OpcodesThatBranchUnconditionally -> chunk.next = next
                        is IRInlineAsmChunk -> chunk.next = next
                        is IRInlineBinaryChunk -> chunk.next =next
                        else -> throw AssemblyError("code chunk followed by invalid chunk type $next")
                    }
                }
            }

            // link all jump and branching instructions to their target
            chunk.instructions.forEach {
                if(it.opcode in OpcodesThatBranch && it.opcode!=Opcode.JUMPI && it.opcode!=Opcode.RETURN && it.opcode!=Opcode.RETURNR && it.opcode!=Opcode.RETURNI && it.labelSymbol!=null) {
                    if(it.labelSymbol.startsWith('$') || it.labelSymbol.first().isDigit()) {
                        // it's a call to an address (extsub most likely)
                        requireNotNull(it.address)
                    } else {
                        it.branchTarget = labeledChunks.getValue(it.labelSymbol)
                    }
                }
            }
        }

        fun linkSubroutineChunks(sub: IRSubroutine) {
            sub.chunks.withIndex().forEach { (index, chunk) ->

                val next = if(index<sub.chunks.size-1) sub.chunks[index + 1] else null

                when (chunk) {
                    is IRCodeChunk -> {
                        linkCodeChunk(chunk, next)
                    }
                    is IRInlineAsmChunk -> {
                        if(next!=null) {
                            val lastInstr = chunk.instructions.lastOrNull()
                            if(lastInstr==null || lastInstr.opcode !in OpcodesThatBranchUnconditionally)
                                chunk.next = next
                        }
                    }
                    is IRInlineBinaryChunk -> { }
                }
            }
        }

        blocks.forEach { block ->
            block.children.forEachIndexed { index, child ->
                val next = if(index<block.children.lastIndex) block.children[index+1] as? IRCodeChunkBase else null
                when (child) {
                    is IRAsmSubroutine -> child.asmChunk.next = next
                    is IRCodeChunk -> child.next = next
                    is IRInlineAsmChunk -> child.next = next
                    is IRInlineBinaryChunk -> child.next = next
                    is IRSubroutine -> linkSubroutineChunks(child)
                }
            }
        }
        linkCodeChunk(globalInits, globalInits.next)
    }

    fun validate() {
        fun validateChunk(chunk: IRCodeChunkBase, sub: IRSubroutine?, emptyChunkIsAllowed: Boolean) {
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
            chunk.instructions.forEach { instr ->
                if(instr.labelSymbol!=null && instr.opcode in OpcodesThatBranch) {
                    if(instr.opcode==Opcode.JUMPI) {
                        when(val pointervar = st.lookup(instr.labelSymbol)!!) {
                            is IRStStaticVariable -> require(pointervar.dt.isUnsignedWord)
                            is IRStMemVar -> require(pointervar.dt.isUnsignedWord)
                            else -> throw AssemblyError("weird pointervar type")
                        }
                    }
                    else if(!instr.labelSymbol.startsWith('$') && !instr.labelSymbol.first().isDigit())
                        require(instr.branchTarget != null) { "branching instruction to label should have branchTarget set" }
                }
            }
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
            }
        }
    }

    fun registersUsed(): RegistersUsed {
        val readRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val regsTypes = mutableMapOf<Int, IRDataType>()
        val readFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }

        fun addUsed(usedRegisters: RegistersUsed, child: IIRBlockElement) {
            usedRegisters.readRegs.forEach{ (reg, count) -> readRegsCounts[reg] = readRegsCounts.getValue(reg) + count }
            usedRegisters.writeRegs.forEach{ (reg, count) -> writeRegsCounts[reg] = writeRegsCounts.getValue(reg) + count }
            usedRegisters.readFpRegs.forEach{ (reg, count) -> readFpRegsCounts[reg] = readFpRegsCounts.getValue(reg) + count }
            usedRegisters.writeFpRegs.forEach{ (reg, count) -> writeFpRegsCounts[reg] = writeFpRegsCounts.getValue(reg) + count }
            usedRegisters.regsTypes.forEach{ (reg, type) ->
                val existingType = regsTypes[reg]
                if (existingType!=null) {
                    if (existingType != type)
                        throw IllegalArgumentException("register $reg given multiple types! $existingType and $type  ${this.name}<--${child.label ?: child}")
                } else
                    regsTypes[reg] = type
            }
        }

        globalInits.instructions.forEach {
            it.addUsedRegistersCounts(
                readRegsCounts,
                writeRegsCounts,
                readFpRegsCounts,
                writeFpRegsCounts,
                regsTypes,
                globalInits
            )
        }

        blocks.forEach {block ->
            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> addUsed(child.usedRegisters(), child)
                    is IRCodeChunk -> addUsed(child.usedRegisters(), child)
                    is IRInlineAsmChunk -> addUsed(child.usedRegisters(), child)
                    is IRInlineBinaryChunk -> addUsed(child.usedRegisters(), child)
                    is IRSubroutine -> child.chunks.forEach { chunk -> addUsed(chunk.usedRegisters(), child) }
                }
            }
        }
        return RegistersUsed(readRegsCounts, writeRegsCounts, readFpRegsCounts, writeFpRegsCounts, regsTypes)
    }

    fun convertAsmChunks() {
        fun convert(asmChunk: IRInlineAsmChunk): IRCodeChunks {
            val chunks = mutableListOf<IRCodeChunkBase>()
            var chunk = IRCodeChunk(asmChunk.label, null)
            asmChunk.assembly.lineSequence().forEach {
                val parsed = parseIRCodeLine(it.trim())
                parsed.fold(
                    ifLeft = { instruction -> chunk += instruction },
                    ifRight = { label ->
                        val lastChunk = chunk
                        if(chunk.isNotEmpty() || chunk.label!=null)
                            chunks += chunk
                        chunk = IRCodeChunk(label, null)
                        val lastInstr = lastChunk.instructions.lastOrNull()
                        if(lastInstr==null || lastInstr.opcode !in OpcodesThatBranchUnconditionally)
                            lastChunk.next = chunk
                    }
                )
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
            val first = chunk.instructions.dropLast(totalSize-split.splitAt-1)
            val second = chunk.instructions.drop(split.splitAt+1)
            chunk.instructions.clear()
            chunk.instructions.addAll(first)
            val secondChunk = IRCodeChunk(null, chunk.next)
            secondChunk.instructions.addAll(second)
            require(chunk.instructions.last().opcode in OpcodesThatEndSSAblock)
            require(chunk.instructions.size + secondChunk.instructions.size == totalSize)
            if(chunk.instructions.last().opcode !in OpcodesThatBranchUnconditionally) {
                chunk.next = secondChunk
                if(split.blockParent!=null) split.blockParent.children.add(split.chunkIndex+1, secondChunk)
                else split.subParent!!.chunks.add(split.chunkIndex+1, secondChunk)
                // println("split chunk ${chunk.label} at ${split.splitAt}:   ${chunk.instructions[split.splitAt]}   ${totalSize} = ${chunk.instructions.size}+${secondChunk.instructions.size}")
            } else {
                // shouldn't occur , unreachable code in second chunk?
                chunk.next = null
                // println("REMOVED UNREACHABLE CODE")
            }
        }
    }


    fun verifyRegisterTypes(registerTypes: Map<Int, IRDataType>) {
        for(block in blocks) {
            for(bc in block.children) {
                when(bc) {
                    is IRAsmSubroutine -> bc.usedRegisters().validate(registerTypes, null)
                    is IRCodeChunk -> bc.usedRegisters().validate(registerTypes, bc)
                    is IRInlineAsmChunk -> bc.usedRegisters().validate(registerTypes, bc)
                    is IRInlineBinaryChunk -> bc.usedRegisters().validate(registerTypes, bc)
                    is IRSubroutine -> {
                        for(sc in bc.chunks) {
                            sc.usedRegisters().validate(registerTypes, sc)
                        }
                    }
                }
            }
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
                  val ignoreUnused: Boolean = false)

    operator fun plusAssign(sub: IRSubroutine) { children += sub }
    operator fun plusAssign(sub: IRAsmSubroutine) { children += sub }
    operator fun plusAssign(asm: IRInlineAsmChunk) { children += asm }
    operator fun plusAssign(binary: IRInlineBinaryChunk) { children += binary }
    operator fun plusAssign(irCodeChunk: IRCodeChunk) { children += irCodeChunk }

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
}


class IRAsmSubroutine(
    override val label: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<IRAsmParam>,
    val returns: List<IRAsmParam>,
    val asmChunk: IRInlineAsmChunk,
    val position: Position
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
}

class IRCodeChunk(label: String?, next: IRCodeChunkBase?): IRCodeChunkBase(label, next) {

    override fun isEmpty() = instructions.isEmpty()
    override fun isNotEmpty() = instructions.isNotEmpty()
    override fun usedRegisters(): RegistersUsed {
        val readRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val regsTypes = mutableMapOf<Int, IRDataType>()
        val readFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
        instructions.forEach { it.addUsedRegistersCounts(readRegsCounts, writeRegsCounts, readFpRegsCounts, writeFpRegsCounts, regsTypes, this) }
        return RegistersUsed(readRegsCounts, writeRegsCounts, readFpRegsCounts, writeFpRegsCounts, regsTypes)
    }

    operator fun plusAssign(ins: IRInstruction) {
        instructions.add(ins)
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        instructions.addAll(chunk.instructions)
    }

    fun appendSrcPosition(position: Position) {
        if(sourceLinesPositions.lastOrNull()!=position)
            sourceLinesPositions.add(position)
    }

    fun appendSrcPositions(positions: Collection<Position>) {
        positions.forEach { appendSrcPosition(it) }
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
    override fun usedRegisters() = RegistersUsed(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
}

typealias IRCodeChunks = List<IRCodeChunkBase>


internal class IRSubtypePlaceholder(override val scopedNameString: String, val size: Int = 999999999): ISubType {
    override fun memsize(sizer: IMemSizer) = size
    override fun sameas(other: ISubType): Boolean = false
    override fun getFieldType(name: String): DataType? = null
}


class RegistersUsed(
    // register num -> number of uses
    val readRegs: Map<Int, Int>,
    val writeRegs: Map<Int, Int>,
    val readFpRegs: Map<Int, Int>,
    val writeFpRegs: Map<Int, Int>,
    val regsTypes: Map<Int, IRDataType>
) {

    override fun toString(): String {
        return "read=$readRegs, write=$writeRegs, readFp=$readFpRegs, writeFp=$writeFpRegs, types=$regsTypes"
    }

    fun isEmpty() = readRegs.isEmpty() && writeRegs.isEmpty() && readFpRegs.isEmpty() && writeFpRegs.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun used(register: Int) = register in readRegs || register in writeRegs
    fun usedFp(fpRegister: Int) = fpRegister in readFpRegs || fpRegister in writeFpRegs

    fun validate(allowed: Map<Int, IRDataType>, chunk: IRCodeChunkBase?) {
        for((reg, type) in regsTypes) {
            if(allowed[reg]==null)
                throw IllegalArgumentException("Reg type mismatch for register $reg type $type: no type known.  CodeChunk=$chunk label ${chunk?.label}")
            if(allowed[reg]!=type)
                throw IllegalArgumentException("Reg type mismatch for register $reg type $type: expected ${allowed[reg]}. CodeChunk=$chunk label ${chunk?.label}")
        }
    }
}

private fun registersUsedInAssembly(isIR: Boolean, assembly: String): RegistersUsed {
    val readRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
    val regsTypes = mutableMapOf<Int, IRDataType>()
    val readFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
    val writeRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }
    val writeFpRegsCounts = mutableMapOf<Int, Int>().withDefault { 0 }

    if(isIR) {
        assembly.lineSequence().forEach { line ->
            val t = line.trim()
            if(t.isNotEmpty()) {
                val result = parseIRCodeLine(t)
                result.fold(
                    ifLeft = { it.addUsedRegistersCounts(
                        readRegsCounts,
                        writeRegsCounts,
                        readFpRegsCounts,
                        writeFpRegsCounts,
                        regsTypes,
                        null
                    ) },
                    ifRight = { /* labels can be skipped */ }
                )
            }
        }
    }
    return RegistersUsed(readRegsCounts, writeRegsCounts, readFpRegsCounts, writeFpRegsCounts, regsTypes)
}
