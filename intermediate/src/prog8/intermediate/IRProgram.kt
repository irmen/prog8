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
            return blocks.flatMap { it.subroutines }.flatMap { it.chunks }.associateBy { it.label } +
                   blocks.flatMap { it.asmSubroutines }.map { it.asmChunk }.associateBy { it.label }
        }

        val labeledChunks = getLabeledChunks()

        if(globalInits.isNotEmpty()) {
            if(globalInits.next==null) {
                val firstBlock = blocks.firstOrNull()
                if(firstBlock!=null) {
                    if(firstBlock.inlineAssembly.isNotEmpty()) {
                        globalInits.next = firstBlock.inlineAssembly.first()
                    } else if(firstBlock.subroutines.isNotEmpty()) {
                        val firstSub = firstBlock.subroutines.first()
                        if(firstSub.chunks.isNotEmpty())
                            globalInits.next = firstSub.chunks.first()
                    }
                }
            }
        }

        blocks.asSequence().flatMap { it.subroutines }.forEach { sub ->

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
                                else
                                    throw AssemblyError("code chunk flows into following non-executable chunk")
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
    }

    fun validate() {
        blocks.forEach { block ->
            if(block.inlineAssembly.isNotEmpty()) {
                require(block.inlineAssembly.first().label == block.name) { "first block chunk should have block name as its label" }
            }
            block.inlineAssembly.forEach { chunk ->
                require(chunk.instructions.isEmpty())
            }
            block.subroutines.forEach { sub ->
                if(sub.chunks.isNotEmpty()) {
                    require(sub.chunks.first().label == sub.name) { "first chunk in subroutine should have sub name as its label" }
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

    fun registersUsed(): RegistersUsed {
        val inputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val inputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }

        fun addUsed(usedRegisters: RegistersUsed) {
            usedRegisters.inputRegs.forEach{ (reg, count) -> inputRegs[reg] = inputRegs.getValue(reg) + count }
            usedRegisters.outputRegs.forEach{ (reg, count) -> outputRegs[reg] = outputRegs.getValue(reg) + count }
            usedRegisters.inputFpRegs.forEach{ (reg, count) -> inputFpRegs[reg] = inputFpRegs.getValue(reg) + count }
            usedRegisters.outputFpRegs.forEach{ (reg, count) -> outputFpRegs[reg] = outputFpRegs.getValue(reg) + count }
        }

        globalInits.instructions.forEach { it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs) }
        blocks.forEach {
            it.inlineAssembly.forEach { chunk -> addUsed(chunk.usedRegisters()) }
            it.subroutines.flatMap { sub->sub.chunks }.forEach { chunk -> addUsed(chunk.usedRegisters()) }
            it.asmSubroutines.forEach { asmsub -> addUsed(asmsub.usedRegisters()) }
        }

        return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
    }
}

class IRBlock(
    val name: String,
    val address: UInt?,
    val alignment: BlockAlignment,
    val position: Position
) {
    // TODO not separate lists but just a single list of chunks, like IRSubroutine?
    val inlineAssembly = mutableListOf<IRInlineAsmChunk>()
    val subroutines = mutableListOf<IRSubroutine>()
    val asmSubroutines = mutableListOf<IRAsmSubroutine>()

    enum class BlockAlignment {
        NONE,
        WORD,
        PAGE
    }

    operator fun plusAssign(sub: IRSubroutine) {
        subroutines += sub
    }
    operator fun plusAssign(sub: IRAsmSubroutine) { asmSubroutines += sub }
    operator fun plusAssign(asm: IRInlineAsmChunk) { inlineAssembly += asm }
    operator fun plusAssign(binary: IRInlineBinaryChunk) { TODO("IR BLOCK can't contain inline binary data yet") }

    fun isEmpty(): Boolean {
        val noAsm = inlineAssembly.isEmpty() || inlineAssembly.all { it.isEmpty() }
        val noSubs = subroutines.isEmpty() || subroutines.all { it.isEmpty() }
        val noAsmSubs = asmSubroutines.isEmpty() || asmSubroutines.all { it.isEmpty() }
        return noAsm && noSubs && noAsmSubs
    }
}

class IRSubroutine(val name: String,
                   val parameters: List<IRParam>,
                   val returnType: DataType?,
                   val position: Position) {

    class IRParam(val name: String, val dt: DataType)

    val chunks = mutableListOf<IRCodeChunkBase>()

    init {
        require('.' in name) {"subroutine name is not scoped: $name"}
        require(!name.startsWith("main.main.")) {"subroutine name invalid main prefix: $name"}

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

    fun isEmpty(): Boolean = chunks.isEmpty() || chunks.all { it.isEmpty() }
}

class IRAsmSubroutine(
    val name: String,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<IRAsmParam>,
    val returns: List<IRAsmParam>,
    val asmChunk: IRInlineAsmChunk,
    val position: Position
) {

    class IRAsmParam(val reg: RegisterOrStatusflag, val dt: DataType)

    init {
        require('.' in name) { "subroutine name is not scoped: $name" }
        require(!name.startsWith("main.main.")) { "subroutine name invalid main prefix: $name" }
    }

    private val registersUsed by lazy { registersUsedInAssembly(asmChunk.isIR, asmChunk.assembly) }

    fun usedRegisters() = registersUsed
    fun isEmpty(): Boolean = if(address==null) asmChunk.isEmpty() else false
}

sealed class IRCodeChunkBase(val label: String?, var next: IRCodeChunkBase?) {
    val instructions = mutableListOf<IRInstruction>()

    abstract fun isEmpty(): Boolean
    abstract fun isNotEmpty(): Boolean
    abstract fun usedRegisters(): RegistersUsed
}

class IRCodeChunk(label: String?, next: IRCodeChunkBase?): IRCodeChunkBase(label, next) {

    override fun isEmpty() = instructions.isEmpty()
    override fun isNotEmpty() = instructions.isNotEmpty()
    override fun usedRegisters(): RegistersUsed {
        val inputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val inputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        instructions.forEach { it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs) }
        return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
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
    val inputRegs: Map<Int, Int>,
    val outputRegs: Map<Int, Int>,
    val inputFpRegs: Map<Int, Int>,
    val outputFpRegs: Map<Int, Int>,
) {
    override fun toString(): String {
        return "input=$inputRegs, output=$outputRegs, inputFp=$inputFpRegs, outputFp=$outputFpRegs"
    }

    fun isEmpty() = inputRegs.isEmpty() && outputRegs.isEmpty() && inputFpRegs.isEmpty() && outputFpRegs.isEmpty()
    fun isNotEmpty() = !isEmpty()
}

private fun registersUsedInAssembly(isIR: Boolean, assembly: String): RegistersUsed {
    val inputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val inputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val outputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
    val outputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }

    if(isIR) {
        assembly.lineSequence().forEach { line ->
            val result = parseIRCodeLine(line.trim(), null, mutableMapOf())
            result.fold(
                ifLeft = { it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs) },
                ifRight = { /* labels can be skipped */ }
            )
        }
    }

    return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
}
