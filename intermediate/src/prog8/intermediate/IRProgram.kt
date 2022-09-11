package prog8.intermediate

import prog8.code.StStaticVariable
import prog8.code.SymbolTable
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
            INLINEASM
        ASMSUB
            INLINEASM
        ...
    BLOCK
    BLOCK
    ...
*/

class IRProgram(val name: String,
                val st: SymbolTable,
                val options: CompilationOptions,
                val encoding: IStringEncoding) {

    val globalInits = mutableListOf<IRCodeLine>()
    val blocks = mutableListOf<IRBlock>()

    fun addGlobalInits(chunk: IRCodeChunk) = globalInits.addAll(chunk.lines)
    fun addBlock(block: IRBlock) {
        if(blocks.any { it.name==block.name })
            throw IllegalArgumentException("duplicate block ${block.name} ${block.position}")
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
                   val parameters: List<StStaticVariable>,  // NOTE: these are the same objects as their occurrences as variables in the symbol table
                   val returnType: DataType?,
                   val position: Position) {

    val chunks = mutableListOf<IRCodeChunkBase>()

    init {
        if(!name.contains('.'))
            throw IllegalArgumentException("subroutine name is not scoped: $name")
        if(name.startsWith("main.main."))
            throw IllegalArgumentException("subroutine name invalid main prefix: $name")
    }

    operator fun plusAssign(chunk: IRCodeChunkBase) { chunks+= chunk }
}

class IRAsmSubroutine(val name: String,
                      val position: Position,
                      val address: UInt?,
                      val clobbers: Set<CpuRegister>,
                      val parameters: List<IRAsmSubParam>,
                      val returns: List<Pair<DataType, RegisterOrStatusflag>>,
                      val assembly: String) {
    val lines = mutableListOf<IRCodeLine>()

    init {
        if(!name.contains('.'))
            throw IllegalArgumentException("subroutine name is not scoped: $name")
        if(name.startsWith("main.main."))
            throw IllegalArgumentException("subroutine name invalid main prefix: $name")
    }

    class IRAsmSubParam(val name: String, val dt: DataType, val reg: RegisterOrStatusflag)
}

sealed class IRCodeLine

class IRCodeInstruction(
    opcode: Opcode,
    type: VmDataType?=null,
    reg1: Int?=null,        // 0-$ffff
    reg2: Int?=null,        // 0-$ffff
    fpReg1: Int?=null,      // 0-$ffff
    fpReg2: Int?=null,      // 0-$ffff
    value: Int?=null,       // 0-$ffff
    fpValue: Float?=null,
    labelSymbol: String?=null    // alternative to value
): IRCodeLine() {
    val ins = Instruction(opcode, type, reg1, reg2, fpReg1, fpReg2, value, fpValue,  if(labelSymbol==null) null else listOf(labelSymbol))

    init {
        if(reg1!=null && (reg1<0 || reg1>65536))
            throw IllegalArgumentException("reg1 out of bounds")
        if(reg2!=null && (reg2<0 || reg2>65536))
            throw IllegalArgumentException("reg2 out of bounds")
        if(fpReg1!=null && (fpReg1<0 || fpReg1>65536))
            throw IllegalArgumentException("fpReg1 out of bounds")
        if(fpReg2!=null && (fpReg2<0 || fpReg2>65536))
            throw IllegalArgumentException("fpReg2 out of bounds")

        if(value!=null && opcode !in OpcodesWithAddress) {
            when (type) {
                VmDataType.BYTE -> {
                    if (value < -128 || value > 255)
                        throw IllegalArgumentException("value out of range for byte: $value")
                }
                VmDataType.WORD -> {
                    if (value < -32768 || value > 65535)
                        throw IllegalArgumentException("value out of range for word: $value")
                }
                VmDataType.FLOAT, null -> {}
            }
        }

        if(labelSymbol?.startsWith('_')==true) {
            throw IllegalArgumentException("label/symbol should not start with underscore $labelSymbol")
        }
    }
}

class IRCodeLabel(val name: String): IRCodeLine()

class IRCodeComment(val comment: String): IRCodeLine()

class IRCodeInlineBinary(val data: ByteArray): IRCodeLine()

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

class IRInlineAsmChunk(val asm: String, position: Position): IRCodeChunkBase(position) {
    // note: no lines, asm is in the property
    override fun isEmpty() = asm.isBlank()
    override fun isNotEmpty() = asm.isNotBlank()
}

