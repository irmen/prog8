package prog8.intermediate

import prog8.code.core.*

/*

note: all symbol names are flattened so that they're a single string that is globally unique.


PROGRAM:
    OPTIONS                 (from CompilationOptions)
    ASMSYMBOLS              (from command line defined symbols)
    VARIABLES               (from Symboltable)
    MEMORYMAPPEDVARIABLES   (from Symboltable)
    MEMORYSLABS             (from Symboltable)
    INITGLOBALS
        C (CODE)
            CODE-LINE       (assignment to initialize a variable)
            ...
    BLOCK
        INLINEASM
        INLINEASM
        SUB
            PARAMS
            INLINEASM
            INLINEASM
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
            INLINEASM
        ASMSUB
            PARAMS
            INLINEASM
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

    fun addGlobalInits(chunk: IRCodeChunk) {
        globalInits += chunk
    }

    fun addBlock(block: IRBlock) {
        require(blocks.all { it.name != block.name}) { "duplicate block ${block.name} ${block.position}" }
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
            return result
        }

        val labeledChunks = getLabeledChunks()

        if(globalInits.isNotEmpty()) {
            if(globalInits.next==null) {
                // link globalinits to subsequent chunk
                val firstBlock = blocks.firstOrNull()
                if(firstBlock!=null && firstBlock.isNotEmpty()) {
                    firstBlock.children.forEach { child ->
                        when(child) {
                            is IRAsmSubroutine -> throw AssemblyError("cannot link next to asmsub $child")
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

        fun linkSubroutineChunks(sub: IRSubroutine) {
            sub.chunks.withIndex().forEach { (index, chunk) ->

                fun nextChunk(): IRCodeChunkBase? = if(index<sub.chunks.size-1) sub.chunks[index + 1] else null

                when (chunk) {
                    is IRCodeChunk -> {
                        // link sequential chunks
                        val jump = chunk.instructions.lastOrNull()?.opcode
                        if (jump == null || jump !in OpcodesThatJump) {
                            // no jump at the end, so link to next chunk (if it exists)
                            val next = nextChunk()
                            if(next!=null) {
                                if (next is IRCodeChunk && chunk.instructions.lastOrNull()?.opcode !in OpcodesThatJump)
                                    chunk.next = next
                                else if(next is IRInlineAsmChunk)
                                    chunk.next = next
                                else if(next is IRInlineBinaryChunk)
                                    chunk.next =next
                                else
                                    throw AssemblyError("code chunk followed by invalid chunk type $next")
                            }
                        }

                        // link all jump and branching instructions to their target
                        chunk.instructions.forEach {
                            if(it.opcode in OpcodesThatBranch && it.opcode!=Opcode.RETURN && it.labelSymbol!=null)
                                it.branchTarget = labeledChunks.getValue(it.labelSymbol)
                            // note: branches with an address value cannot be linked to something...
                        }
                    }
                    is IRInlineAsmChunk -> {
                        val next = nextChunk()
                        if(next!=null) {
                            val lastInstr = chunk.instructions.lastOrNull()
                            if(lastInstr==null || lastInstr.opcode !in OpcodesThatJump)
                                chunk.next = next
                        }
                    }
                    is IRInlineBinaryChunk -> { }
                    else -> throw AssemblyError("invalid chunk")
                }
            }
        }

        blocks.forEach { block ->
            block.children.forEachIndexed { index, child ->
                val next = if(index<block.children.size-1) block.children[index+1] as? IRCodeChunkBase else null
                when (child) {
                    is IRAsmSubroutine -> child.asmChunk.next = next
                    is IRCodeChunk -> child.next = next
                    is IRInlineAsmChunk -> child.next = next
                    is IRInlineBinaryChunk -> child.next = next
                    is IRSubroutine -> linkSubroutineChunks(child)
                }
            }
        }
    }

    fun validate() {
        blocks.forEach { block ->
            if(block.isNotEmpty()) {
                block.children.filterIsInstance<IRInlineAsmChunk>().forEach { chunk -> require(chunk.instructions.isEmpty()) }
                block.children.filterIsInstance<IRSubroutine>().forEach { sub ->
                    if(sub.chunks.isNotEmpty()) {
                        require(sub.chunks.first().label == sub.label) { "first chunk in subroutine should have sub name (label) as its label" }
                    }
                    sub.chunks.forEach { chunk ->
                        if (chunk is IRCodeChunk) {
                            require(chunk.instructions.isNotEmpty() || chunk.label != null)
                            if(chunk.instructions.lastOrNull()?.opcode in OpcodesThatJump)
                                require(chunk.next == null) { "chunk ending with a jump shouldn't be linked to next" }
                            else {
                                // if chunk is NOT the last in the block, it needs to link to next.
                                val isLast = sub.chunks.last() === chunk
                                require(isLast || chunk.next != null) { "chunk needs to be linked to next" }
                            }
                        }
                        else
                            require(chunk.instructions.isEmpty())
                        chunk.instructions.forEach {
                            if(it.labelSymbol!=null && it.opcode in OpcodesThatBranch)
                                require(it.branchTarget != null) { "branching instruction to label should have branchTarget set" }
                        }
                    }
                }
            }
        }
    }

    fun registersUsed(): RegistersUsed {
        val readRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val readFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }

        fun addUsed(usedRegisters: RegistersUsed) {
            usedRegisters.readRegs.forEach{ (reg, count) -> readRegs[reg] = readRegs.getValue(reg) + count }
            usedRegisters.writeRegs.forEach{ (reg, count) -> writeRegs[reg] = writeRegs.getValue(reg) + count }
            usedRegisters.readFpRegs.forEach{ (reg, count) -> readFpRegs[reg] = readFpRegs.getValue(reg) + count }
            usedRegisters.writeFpRegs.forEach{ (reg, count) -> writeFpRegs[reg] = writeFpRegs.getValue(reg) + count }
        }

        globalInits.instructions.forEach { it.addUsedRegistersCounts(readRegs, writeRegs, readFpRegs, writeFpRegs) }
        blocks.forEach {block ->
            block.children.forEach { child ->
                when(child) {
                    is IRAsmSubroutine -> addUsed(child.usedRegisters())
                    is IRCodeChunk -> addUsed(child.usedRegisters())
                    is IRInlineAsmChunk -> addUsed(child.usedRegisters())
                    is IRInlineBinaryChunk -> addUsed(child.usedRegisters())
                    is IRSubroutine -> child.chunks.forEach { chunk -> addUsed(chunk.usedRegisters()) }
                }
            }
        }

        return RegistersUsed(readRegs, writeRegs, readFpRegs, writeFpRegs)
    }
}

class IRBlock(
    val name: String,
    val address: UInt?,
    val alignment: BlockAlignment,
    val position: Position
) {
    val children = mutableListOf<IIRBlockElement>()

    enum class BlockAlignment {
        NONE,
        WORD,
        PAGE
    }

    operator fun plusAssign(sub: IRSubroutine) { children += sub }
    operator fun plusAssign(sub: IRAsmSubroutine) { children += sub }
    operator fun plusAssign(asm: IRInlineAsmChunk) { children += asm }
    operator fun plusAssign(binary: IRInlineBinaryChunk) { children += binary }
    operator fun plusAssign(irCodeChunk: IRCodeChunk) {
        // this is for a separate label in the block scope. (random code statements are not allowed)
        require(irCodeChunk.isEmpty() && irCodeChunk.label!=null)
        children += irCodeChunk
    }

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
    val returnType: DataType?,
    val position: Position): IIRBlockElement {

    class IRParam(val name: String, val dt: DataType)

    val chunks = mutableListOf<IRCodeChunkBase>()

    init {
        require('.' in label) {"subroutine name is not scoped: $label"}
        require(!label.startsWith("main.main.")) {"subroutine name invalid main prefix: $label"}

        // params and return value should not be str
        require(parameters.all{ it.dt in NumericDatatypes }) {"non-numeric parameter"}
        require(returnType==null || returnType in NumericDatatypes) {"non-numeric returntype $returnType"}
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
        val readRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val readFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val writeFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        instructions.forEach { it.addUsedRegistersCounts(readRegs, writeRegs, readFpRegs, writeFpRegs) }
        return RegistersUsed(readRegs, writeRegs, readFpRegs, writeFpRegs)
    }

    operator fun plusAssign(ins: IRInstruction) {
        instructions.add(ins)
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        instructions.addAll(chunk.instructions)
    }
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
    override fun usedRegisters() = RegistersUsed(emptyMap(), emptyMap(), emptyMap(), emptyMap())
}

typealias IRCodeChunks = List<IRCodeChunkBase>

class RegistersUsed(
    // register num -> number of uses
    val readRegs: Map<Int, Int>,
    val writeRegs: Map<Int, Int>,
    val readFpRegs: Map<Int, Int>,
    val writeFpRegs: Map<Int, Int>,
) {
    override fun toString(): String {
        return "read=$readRegs, write=$writeRegs, readFp=$readFpRegs, writeFp=$writeFpRegs"
    }

    fun isEmpty() = readRegs.isEmpty() && writeRegs.isEmpty() && readFpRegs.isEmpty() && writeFpRegs.isEmpty()
    fun isNotEmpty() = !isEmpty()
}

private fun registersUsedInAssembly(isIR: Boolean, assembly: String): RegistersUsed {
    val readRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val readFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val writeRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val writeFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }

    if(isIR) {
        assembly.lineSequence().forEach { line ->
            val result = parseIRCodeLine(line.trim(), null, mutableMapOf())
            result.fold(
                ifLeft = { it.addUsedRegistersCounts(readRegs, writeRegs, readFpRegs, writeFpRegs) },
                ifRight = { /* labels can be skipped */ }
            )
        }
    }

    return RegistersUsed(readRegs, writeRegs, readFpRegs, writeFpRegs)
}
