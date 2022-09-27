package prog8.intermediate

import prog8.code.StStaticVariable
import prog8.code.core.*

/*

note: all symbol names are flattened so that they're a single string that is globally unique.


PROGRAM:
    OPTIONS                 (from CompilationOptions)
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

    val globalInits = mutableListOf<IRCodeLine>()
    val blocks = mutableListOf<IRBlock>()

    fun addGlobalInits(chunk: IRCodeChunk) = globalInits.addAll(chunk.lines)
    fun addBlock(block: IRBlock) {
        require(blocks.all { it.name != block.name}) { "duplicate block ${block.name} ${block.position}" }
        blocks.add(block)
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

    class IRParam(val name: String, val dt: DataType, val orig: StStaticVariable)

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

class IRAsmSubroutine(val name: String,
                      val position: Position,
                      val address: UInt?,
                      val clobbers: Set<CpuRegister>,
                      val parameters: List<Pair<DataType, RegisterOrStatusflag>>,
                      val returns: List<Pair<DataType, RegisterOrStatusflag>>,
                      val assembly: String) {
    init {
        require('.' in name) { "subroutine name is not scoped: $name" }
        require(!name.startsWith("main.main.")) { "subroutine name invalid main prefix: $name" }
    }
}

sealed class IRCodeLine

class IRCodeLabel(val name: String): IRCodeLine()

class IRCodeComment(val comment: String): IRCodeLine()

class IRCodeInlineBinary(val data: Collection<UByte>): IRCodeLine()

abstract class IRCodeChunkBase(val position: Position) {
    val lines = mutableListOf<IRCodeLine>()

    abstract fun isEmpty(): Boolean
    abstract fun isNotEmpty(): Boolean
}

class IRCodeChunk(position: Position): IRCodeChunkBase(position) {

    override fun isEmpty() = lines.isEmpty()
    override fun isNotEmpty() = lines.isNotEmpty()

    operator fun plusAssign(line: IRCodeLine) {
        lines.add(line)
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) {
        lines.addAll(chunk.lines)
    }
}

class IRInlineAsmChunk(val assembly: String, position: Position): IRCodeChunkBase(position) {
    // note: no lines, asm is in the property
    override fun isEmpty() = assembly.isBlank()
    override fun isNotEmpty() = assembly.isNotBlank()
}

