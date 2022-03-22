package prog8.codegen.virtual

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.DataType


class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    internal val allocations = VariableAllocator(symbolTable, program, errors)
    private val builtinFunctions = BuiltinFunctionsGen(this)
    private val expressionEval = ExpressionGen(this, builtinFunctions)
    private val instructions = mutableListOf<String>()

    init {
        if(options.dontReinitGlobals)
            TODO("support no globals re-init in vm")
    }

    override fun compileToAssembly(): IAssemblyProgram? {
        instructions.clear()
        val vmprog = AssemblyProgram(program.name, allocations)

        program.allBlocks().forEach {
            it.children
                .singleOrNull { node->node is PtScopeVarsInit }
                ?.let { inits ->
                    vmprog.addGlobalInits(translate(inits as PtScopeVarsInit))
                    it.children.remove(inits)
                }
        }

        for (block in program.allBlocks()) {
            vmprog.addBlock(translate(block))
        }

        return vmprog
    }

    private fun translate(assignment: PtAssignment): VmCodeChunk {
        val chunk = VmCodeChunk()
        val (expressionChunk, resultRegister) = expressionEval.translateExpression(assignment.value)
        chunk += expressionChunk
        val ident = assignment.target.identifier
        val memory = assignment.target.memory
        val array = assignment.target.array
        val vmDt = vmType(assignment.value.type)
        if(ident!=null) {
            val address = allocations.get(ident.targetName)
            val ins = Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=address)
            chunk += VmCodeInstruction(ins)
        }
        else if(array!=null) {
            TODO("assign to array")
        }
        else if(memory!=null) {
            val ins =
                if(memory.address is PtNumber) {
                    Instruction(Opcode.STOREM, vmDt, reg1=resultRegister, value=(memory.address as PtNumber).number.toInt())
                } else {
                    // TODO make sure the registers used in this eval don't overlap with the one above
                    val (addrExpressionChunk, addressRegister) = expressionEval.translateExpression(assignment.value)
                    chunk += addrExpressionChunk
                    Instruction(Opcode.STOREI, vmDt, reg1=resultRegister, reg2=addressRegister)
                }
            chunk += VmCodeInstruction(ins)
        }
        else
            throw AssemblyError("weird assigntarget")
        return chunk
    }

    private fun translateNode(node: PtNode): VmCodeChunk {
        return when(node) {
            is PtBlock -> translate(node)
            is PtSub -> translate(node)
            is PtScopeVarsDecls -> VmCodeChunk() // vars should be looked up via symbol table
            is PtVariable -> VmCodeChunk() // var should be looked up via symbol table
            is PtMemMapped -> VmCodeChunk() // memmapped var should be looked up via symbol table
            is PtConstant -> VmCodeChunk() // constants have all been folded into the code
            is PtAssignTarget -> TODO()
            is PtAssignment -> translate(node)
            is PtScopeVarsInit -> translate(node)
            is PtConditionalBranch -> TODO()
            is PtAddressOf -> TODO()
            is PtArrayIndexer -> TODO()
            is PtArrayLiteral -> TODO()
            is PtBinaryExpression -> TODO()
            is PtBuiltinFunctionCall -> builtinFunctions.translate(node)
            is PtContainmentCheck -> TODO()
            is PtFunctionCall -> translate(node)
            is PtIdentifier -> TODO()
            is PtMemoryByte -> TODO()
            is PtNumber -> TODO()
            is PtPipe -> TODO()
            is PtPrefix -> TODO()
            is PtRange -> TODO()
            is PtString -> TODO()
            is PtTypeCast -> TODO()
            is PtForLoop -> TODO()
            is PtGosub -> translate(node)
            is PtIfElse -> TODO()
            is PtJump -> TODO()
            is PtNodeGroup -> TODO()
            is PtNop -> VmCodeChunk()
            is PtPostIncrDecr -> TODO()
            is PtProgram -> TODO()
            is PtRepeatLoop -> TODO()
            is PtReturn -> translate(node)
            is PtSubroutineParameter -> TODO()
            is PtWhen -> TODO()
            is PtWhenChoice -> TODO()
            is PtLabel -> TODO()
            is PtBreakpoint -> TODO()
            is PtAsmSub -> throw AssemblyError("asmsub not supported on virtual machine target")
            is PtInlineAssembly -> throw AssemblyError("inline assembly not supported on virtual machine target")
            is PtIncludeBinary -> throw AssemblyError("inline binary data not supported on virtual machine target")
            else -> TODO("missing codegen for $node")
        }
    }

    private fun translate(fcall: PtFunctionCall): VmCodeChunk {
        val chunk = VmCodeChunk()
        // TODO evaluate function call arguments
        chunk += VmCodeOpcodeWithStringArg(Opcode.GOSUB, gosubArg(fcall.functionName))
        return chunk
    }

    private fun translate(gosub: PtGosub): VmCodeChunk {
        val chunk = VmCodeChunk()
        if(gosub.address!=null)
            throw AssemblyError("cannot gosub to a memory address in the vm target")
        else if(gosub.identifier!=null) {
            chunk += VmCodeOpcodeWithStringArg(Opcode.GOSUB, gosubArg(gosub.identifier!!.targetName))
        } else if(gosub.generatedLabel!=null) {
            chunk += VmCodeOpcodeWithStringArg(Opcode.GOSUB, gosubArg(listOf(gosub.generatedLabel!!)))
        }
        return chunk
    }

    private fun translate(ret: PtReturn): VmCodeChunk {
        val chunk = VmCodeChunk()
        val value = ret.value
        if(value!=null) {
            val (expressionChunk, resultRegister) = expressionEval.translateExpression(value)
            chunk += expressionChunk
            if(resultRegister!=0)
                chunk += VmCodeInstruction(Instruction(Opcode.LOADR, vmType(value.type), reg1=0, reg2=resultRegister))
        }
        chunk += VmCodeInstruction(Instruction(Opcode.RETURN))
        return chunk
    }

    internal fun vmType(type: prog8.code.core.DataType): DataType {
        return when(type) {
            prog8.code.core.DataType.UBYTE,
            prog8.code.core.DataType.BYTE -> DataType.BYTE
            prog8.code.core.DataType.UWORD,
            prog8.code.core.DataType.WORD -> DataType.WORD
            in PassByReferenceDatatypes -> DataType.WORD
            else -> throw AssemblyError("no vm datatype for $type")
        }
    }

    private fun translate(init: PtScopeVarsInit): VmCodeChunk {
        val chunk = VmCodeChunk()
        init.children.forEach { chunk += translateNode(it) }
        return chunk
    }

    private fun translate(sub: PtSub): VmCodeChunk {
        val chunk = VmCodeChunk()
        chunk += VmCodeComment("SUB: ${sub.scopedName} -> ${sub.returntype}")
        chunk += VmCodeLabel(sub.scopedName)
        sub.children
            .singleOrNull { it is PtScopeVarsInit }
            ?.let { inits ->
                sub.children.remove(inits)
                chunk += translateNode(inits)
            }

        for (child in sub.children) {
            chunk += translateNode(child)
        }
        chunk += VmCodeComment("SUB-END '${sub.name}'")
        return chunk
    }

    private fun translate(block: PtBlock): VmCodeChunk {
        val chunk = VmCodeChunk()
        chunk += VmCodeComment("BLOCK '${block.name}'  addr=${block.address}  lib=${block.library}")
        for (child in block.children)
            chunk += translateNode(child)
        chunk += VmCodeComment("BLOCK-END '${block.name}'")
        return chunk
    }


    internal fun gosubArg(targetName: List<String>) = "_${targetName.joinToString(".")}"
}
