package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Instruction
import prog8.vm.Opcode

class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    private val instructions = mutableListOf<String>()

    init {
        if(options.dontReinitGlobals)
            TODO("support no globals re-init in vm")
    }

    override fun compileToAssembly(): IAssemblyProgram? {
        instructions.clear()
        val allocations = VariableAllocator(symbolTable, program, errors)

        outComment("GLOBAL VARS INITS")
        program.allBlocks().forEach {
            it.children
                .singleOrNull { node->node is PtScopeVarsInit }
                ?.let { inits->
                    translateNode(inits)
                    it.children.remove(inits)
                }
        }


        outComment("PROGRAM CODE")
        for (block in program.allBlocks()) {
            translateNode(block)
        }
        return AssemblyProgram(program.name, allocations, instructions)
    }

    private fun out(ins: Instruction) {
        instructions.add(ins.toString())
    }

    private fun outComment(ins: String) {
        instructions.add("; $ins")
    }

    private fun translateNode(node: PtNode) {
        when(node) {
            is PtBlock -> translate(node)
            is PtSub -> translate(node)
            is PtScopeVarsDecls -> { /* vars should be looked up via symbol table */ }
            is PtVariable -> { /* var should be looked up via symbol table */ }
            is PtMemMapped -> { /* memmapped var should be looked up via symbol table */ }
            is PtConstant -> { /* constants have all been folded into the code */ }
            is PtAssignTarget -> TODO()
            is PtAssignment -> translate(node)
            is PtScopeVarsInit -> translate(node)
            is PtBreakpoint -> translate(node)
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
            is PtAsmSub -> throw AssemblyError("asmsub not supported on virtual machine target")
            is PtInlineAssembly -> throw AssemblyError("inline assembly not supported on virtual machine target")
            else -> TODO("missing codegen for $node")
        }
    }

    private fun translate(breakpoint: PtBreakpoint) {
        out(Instruction(Opcode.BREAKPOINT))
    }

    private fun translate(init: PtScopeVarsInit) {
        init.children.forEach { translateNode(it) }
    }

    private fun translate(sub: PtSub) {
        outComment("SUB: ${sub.scopedName} -> ${sub.returntype}")
        sub.children
            .singleOrNull { it is PtScopeVarsInit }
            ?.let { inits ->
                sub.children.remove(inits)
                translateNode(inits)
            }

        // TODO rest
        outComment("SUB-END ${sub.scopedName}\n")
    }

    private fun translate(assign: PtAssignment) {
        outComment("ASSIGN: ${assign.target.identifier?.targetName} = ${assign.value}")
    }

    private fun translate(block: PtBlock) {
        outComment("BLOCK '${block.name}'  addr=${block.address}  lib=${block.library}")
        for (child in block.children) {
            translateNode(child)
        }
        outComment("BLOCK-END '${block.name}'\n")
    }
}
