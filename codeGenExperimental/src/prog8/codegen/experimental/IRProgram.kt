package prog8.codegen.experimental

import prog8.code.SymbolTable
import prog8.code.ast.PtBlock
import prog8.code.core.CompilationOptions
import prog8.code.core.DataType
import prog8.code.core.IStringEncoding
import prog8.code.core.Position
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.OpcodesWithAddress
import prog8.vm.VmDataType
import java.nio.file.Path

// TODO: move this Intermedate Representation into the actual compiler core, code gen modules can receive it as input rather than an Ast.

class IRProgram(val name: String,
                val st: SymbolTable,
                val options: CompilationOptions,
                val encoding: IStringEncoding) {

    val globalInits = mutableListOf<IRCodeLine>()
    val blocks = mutableListOf<IRBlock>()

    fun addGlobalInits(chunk: IRCodeChunk) = globalInits.addAll(chunk.lines)
    fun addBlock(block: IRBlock) = blocks.add(block)
}

class IRBlock(
    val name: String,
    val address: UInt?,
    val alignment: PtBlock.BlockAlignment,
    val position: Position
) {
    val subroutines = mutableListOf<IRSubroutine>()
    val asmSubroutines = mutableListOf<IRAsmSubroutine>()
    val inlineAssembly = mutableListOf<IRInlineAsmChunk>()

    operator fun plusAssign(sub: IRSubroutine) {
        subroutines += sub
    }
    operator fun plusAssign(sub: IRAsmSubroutine) {
        asmSubroutines += sub
    }
    operator fun plusAssign(asm: IRInlineAsmChunk) {
        inlineAssembly += asm
    }
}

class IRSubroutine(val scopedName: List<String>,
                   val returnType: DataType?,
                   val position: Position) {
    val lines = mutableListOf<IRCodeLine>()

    operator fun plusAssign(chunk: IRCodeChunk) {
        lines += chunk.lines
    }
}

class IRAsmSubroutine(val scopedName: List<String>,
                      val position: Position,
                      val address: UInt?,
                      val assembly: String) {
    val lines = mutableListOf<IRCodeLine>()

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
    labelSymbol: List<String>?=null    // alternative to value for branch/call/jump labels
): IRCodeLine() {
    val ins = Instruction(opcode, type, reg1, reg2, fpReg1, fpReg2, value, fpValue, labelSymbol)

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
    }
}

class IRCodeLabel(val name: List<String>): IRCodeLine()

class IRCodeComment(val comment: String): IRCodeLine()

class IRCodeInlineBinary(val file: Path, val offset: UInt?, val length: UInt?): IRCodeLine()

open class IRCodeChunk(val position: Position) {
    val lines = mutableListOf<IRCodeLine>()

    operator fun plusAssign(line: IRCodeLine) {
        lines.add(line)
    }

    operator fun plusAssign(chunk: IRCodeChunk) {
        lines.addAll(chunk.lines)
    }
}

class IRInlineAsmChunk(val asm: String, position: Position): IRCodeChunk(position)  // note: no lines, asm is in the property

