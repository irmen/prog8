package prog8.codegen.virtual

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Instruction
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    private val instructions = mutableListOf<String>()

    override fun compileToAssembly(): IAssemblyProgram? {
        instructions.clear()
        val allocations = VariableAllocator(symbolTable, program, errors)
        for (block in program.allBlocks())
            translateNode(block)
        return AssemblyProgram(program.name, allocations, instructions)
    }

    private fun out(ins: Instruction) {
        instructions.add(ins.toString())
    }

    private fun out(ins: String) {
        instructions.add(ins)
    }

    private fun translateNode(node: PtNode) {
        when(node) {
            is PtBlock -> translate(node)
            is PtSub -> translate(node)
            is PtVariable -> { /* var should be looked up via symbol table */ }
            is PtMemMapped -> { /* memmapped var should be looked up via symbol table */ }
            is PtConstant -> { /* constants have all been folded into the code */ }
            is PtAsmSub -> translate(node)
            is PtAssignTarget -> TODO()
            is PtAssignment -> translate(node)
            is PtBreakpoint -> TODO()
            is PtConditionalBranch -> TODO()
            is PtAddressOf -> TODO()
            is PtArrayIndexer -> TODO()
            is PtArrayLiteral -> TODO()
            is PtBinaryExpression -> TODO()
            is PtBuiltinFunctionCall -> TODO()
            is PtContainmentCheck -> TODO()
            is PtFunctionCall -> TODO()
            is PtIdentifier -> TODO()
            is PtMemoryByte -> TODO()
            is PtNumber -> TODO()
            is PtPipe -> TODO()
            is PtPrefix -> TODO()
            is PtRange -> TODO()
            is PtString -> TODO()
            is PtTypeCast -> TODO()
            is PtForLoop -> TODO()
            is PtGosub -> TODO()
            is PtIfElse -> TODO()
            is PtIncludeBinary -> TODO()
            is PtJump -> TODO()
            is PtNodeGroup -> TODO()
            is PtNop -> { }
            is PtPostIncrDecr -> TODO()
            is PtProgram -> TODO()
            is PtRepeatLoop -> TODO()
            is PtReturn -> TODO()
            is PtSubroutineParameter -> TODO()
            is PtWhen -> TODO()
            is PtWhenChoice -> TODO()
            is PtInlineAssembly -> throw AssemblyError("inline assembly not supported on virtual machine target")
            else -> TODO("missing codegen for $node")
        }
    }

    private fun translate(sub: PtSub) {
        out("; SUB: ${sub.scopedName} -> ${sub.returntype}")
    }

    private fun translate(asmsub: PtAsmSub) {
        out("; ASMSUB: ${asmsub.scopedName} = ${asmsub.address}  -> ${asmsub.retvalRegisters}")
    }

    private fun translate(assign: PtAssignment) {
        out("; ASSIGN: ${assign.target.identifier?.targetName} = ${assign.value}")
    }

    private fun translate(block: PtBlock) {
        out("\n; BLOCK '${block.name}'  addr=${block.address}  lib=${block.library}")
        for (child in block.children) {
            translateNode(child)
        }
        out("; BLOCK-END '${block.name}'\n")
    }
}
