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
    val globalInits = mutableListOf<IRInstruction>()
    val blocks = mutableListOf<IRBlock>()

    fun addGlobalInits(chunk: IRCodeChunk) = globalInits.addAll(chunk.instructions)
    fun addBlock(block: IRBlock) {
        require(blocks.all { it.name != block.name}) { "duplicate block ${block.name} ${block.position}" }
        blocks.add(block)
    }

    fun addAsmSymbols(symbolDefs: Map<String, String>) {
        asmSymbols += symbolDefs
    }

    fun validate() {
        blocks.forEach {
            it.inlineAssembly.forEach { chunk ->
                require(chunk.instructions.isEmpty())
            }
            it.subroutines.forEach { sub ->
                sub.chunks.forEach { chunk ->
                    if (chunk is IRCodeChunk) require(chunk.instructions.isNotEmpty() || chunk.label!=null)
                    else require(chunk.instructions.isEmpty())
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

        globalInits.forEach { it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs) }
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
    val subroutines = mutableListOf<IRSubroutine>()
    val asmSubroutines = mutableListOf<IRAsmSubroutine>()
    val inlineAssembly = mutableListOf<IRInlineAsmChunk>()

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
}

class IRAsmSubroutine(
    val name: String,
    val position: Position,
    val address: UInt?,
    val clobbers: Set<CpuRegister>,
    val parameters: List<Pair<DataType, RegisterOrStatusflag>>,
    val returns: List<Pair<DataType, RegisterOrStatusflag>>,
    val isIR: Boolean,
    val assembly: String
) {
    init {
        require('.' in name) { "subroutine name is not scoped: $name" }
        require(!name.startsWith("main.main.")) { "subroutine name invalid main prefix: $name" }
        require(!assembly.startsWith('\n') && !assembly.startsWith('\r')) { "inline assembly should be trimmed" }
        require(!assembly.endsWith('\n') && !assembly.endsWith('\r')) { "inline assembly should be trimmed" }
    }

    private val registersUsed by lazy { registersUsedInAssembly(isIR, assembly) }

    fun usedRegisters() = registersUsed
}

abstract class IRCodeChunkBase(val label: String?, val position: Position) {
    val instructions = mutableListOf<IRInstruction>()

    abstract fun isEmpty(): Boolean
    abstract fun isNotEmpty(): Boolean
    abstract fun usedRegisters(): RegistersUsed
}

class IRCodeChunk(label: String?, position: Position): IRCodeChunkBase(label, position) {

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

class IRInlineAsmChunk(label: String?, val assembly: String, val isIR: Boolean, position: Position): IRCodeChunkBase(label, position) {
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

class IRInlineBinaryChunk(label: String?, val data: Collection<UByte>, position: Position): IRCodeChunkBase(label, position) {
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
            val result = parseIRCodeLine(line.trim(), 0, mutableMapOf())
            result.fold(
                ifLeft = { it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs) },
                ifRight = { /* labels can be skipped */ }
            )
        }
    }

    return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
}
