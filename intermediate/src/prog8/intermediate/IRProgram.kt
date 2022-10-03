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
    val globalInits = mutableListOf<IRCodeLine>()
    val blocks = mutableListOf<IRBlock>()

    fun addGlobalInits(chunk: IRCodeChunk) = globalInits.addAll(chunk.lines)
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
                require(chunk.lines.isEmpty())
            }
            it.subroutines.forEach { sub ->
                sub.chunks.forEach { chunk ->
                    if (chunk is IRInlineAsmChunk) { require(chunk.lines.isEmpty()) }
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

        globalInits.forEach {
            if(it is IRInstruction)
                it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
        }
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

    operator fun plusAssign(chunk: IRCodeChunkBase) { chunks+= chunk }
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

sealed class IRCodeLine

class IRCodeLabel(val name: String): IRCodeLine()

abstract class IRCodeChunkBase(val position: Position) {
    val lines = mutableListOf<IRCodeLine>()

    abstract fun isEmpty(): Boolean
    abstract fun isNotEmpty(): Boolean
    abstract fun usedRegisters(): RegistersUsed
}

class IRCodeChunk(position: Position): IRCodeChunkBase(position) {

    override fun isEmpty() = lines.isEmpty()
    override fun isNotEmpty() = lines.isNotEmpty()
    override fun usedRegisters(): RegistersUsed {
        val inputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val inputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        val outputFpRegs = mutableMapOf<Int, Int>().withDefault { 0 }
        lines.forEach {
            if(it is IRInstruction)
                it.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
        }
        return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
    }

    operator fun plusAssign(line: IRCodeLine) {
        lines.add(line)
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        lines.addAll(chunk.lines)
    }
}

class IRInlineAsmChunk(val assembly: String, val isIR: Boolean, position: Position): IRCodeChunkBase(position) {
    // note: no lines, asm is in the property
    override fun isEmpty() = assembly.isBlank()
    override fun isNotEmpty() = assembly.isNotBlank()
    private val registersUsed by lazy { registersUsedInAssembly(isIR, assembly) }

    init {
        require(!assembly.startsWith('\n') && !assembly.startsWith('\r')) { "inline assembly should be trimmed" }
        require(!assembly.endsWith('\n') && !assembly.endsWith('\r')) { "inline assembly should be trimmed" }
    }

    override fun usedRegisters() = registersUsed
}

class IRInlineBinaryChunk(val data: Collection<UByte>, position: Position): IRCodeChunkBase(position) {
    override fun isEmpty() = data.isEmpty()
    override fun isNotEmpty() = data.isNotEmpty()
    override fun usedRegisters() = RegistersUsed(emptyMap(), emptyMap(), emptyMap(), emptyMap())
}

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
            val code = parseIRCodeLine(line.trim(), 0, mutableMapOf())
            if(code is IRInstruction)
                code.addUsedRegistersCounts(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
        }
    }

    return RegistersUsed(inputRegs, outputRegs, inputFpRegs, outputFpRegs)
}
