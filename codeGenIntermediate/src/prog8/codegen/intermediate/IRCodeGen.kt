package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*
import kotlin.io.path.readBytes


class IRCodeGen(
    internal val program: PtProgram,
    internal val symbolTable: SymbolTable,
    internal val options: CompilationOptions,
    internal val errors: IErrorReporter
) {

    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    private val assignmentGen = AssignmentGen(this, expressionEval)
    internal val registers = RegisterPool()

    fun generate(): IRProgram {
        makeAllNodenamesScoped(program)
        moveAllNestedSubroutinesToBlockScope(program)
        verifyNameScoping(program, symbolTable)
        changeGlobalVarInits(symbolTable)

        val irSymbolTable = IRSymbolTable.fromAstSymboltable(symbolTable)
        val irProg = IRProgram(program.name, irSymbolTable, options, program.encoding)

        // collect global variables initializers
        program.allBlocks().forEach {
            val result = mutableListOf<IRCodeChunkBase>()
            it.children.filterIsInstance<PtAssignment>().forEach { assign -> result += assignmentGen.translate(assign) }
            result.forEach { chunk ->
                if (chunk is IRCodeChunk) irProg.addGlobalInits(chunk)
                else throw AssemblyError("only expect code chunk for global inits")
            }
        }

        irProg.addAsmSymbols(options.symbolDefs)

        for (block in program.allBlocks())
            irProg.addBlock(translate(block))

        replaceMemoryMappedVars(irProg)
        ensureFirstChunkLabels(irProg)
        irProg.linkChunks()
        irProg.convertAsmChunks()

        val optimizer = IRPeepholeOptimizer(irProg)
        optimizer.optimize(options.optimize, errors)
        irProg.validate()

        return irProg
    }

    private fun changeGlobalVarInits(symbolTable: SymbolTable) {
        // Normally, block level (global) variables that have a numeric initialization value
        // are initialized via an assignment statement.
        val initsToRemove = mutableListOf<Pair<PtBlock, PtAssignment>>()

        symbolTable.allVariables.forEach { variable ->
            if(variable.uninitialized && variable.parent.type==StNodeType.BLOCK) {
                val block = variable.parent.astNode as PtBlock
                val initialization = (block.children.firstOrNull {
                    it is PtAssignment && it.target.identifier?.name==variable.scopedName
                } as PtAssignment?)
                val initValue = initialization?.value
                when(initValue){
                    is PtBool -> {
                        variable.setOnetimeInitNumeric(initValue.asInt().toDouble())
                        initsToRemove += block to initialization
                    }
                    is PtNumber -> {
                        variable.setOnetimeInitNumeric(initValue.number)
                        initsToRemove += block to initialization
                    }
                    is PtArray, is PtString -> throw AssemblyError("array or string initialization values should already be part of the vardecl, not a separate assignment")
                    else -> {}
                }
            }
        }

        for((block, assign) in initsToRemove) {
            block.children.remove(assign)
        }
    }

    private fun verifyNameScoping(program: PtProgram, symbolTable: SymbolTable) {
        fun verifyPtNode(node: PtNode) {
            when (node) {
                is PtBuiltinFunctionCall -> require('.' !in node.name) { "builtin function call name should not be scoped: ${node.name}" }
                is PtFunctionCall -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtAsmSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtBlock -> require('.' !in node.name) { "block name should not be scoped: ${node.name}" }
                is PtConstant -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtLabel -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtMemMapped -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtVariable -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtProgram -> require('.' !in node.name) { "program name should not be scoped: ${node.name}" }
                is PtSubroutineParameter -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                else -> { /* node has no name or is ok to have no dots in the name */ }
            }
            node.children.forEach { verifyPtNode(it) }
        }

        fun verifyStNode(node: StNode) {
            require('.' !in node.name) { "st node name should not be scoped: ${node.name}"}
            node.children.forEach {
                require(it.key==it.value.name)
                verifyStNode(it.value)
            }
        }

        verifyPtNode(program)
        verifyStNode(symbolTable)
    }

    private fun ensureFirstChunkLabels(irProg: IRProgram) {
        // make sure that first chunks in Blocks and Subroutines share the name of the block/sub as label.

        irProg.blocks.forEach { block ->
            if(block.isNotEmpty()) {
                val firstAsm = block.children[0] as? IRInlineAsmChunk
                if(firstAsm!=null) {
                    if(firstAsm.label==null) {
                        val replacement = IRInlineAsmChunk(block.label, firstAsm.assembly, firstAsm.isIR, firstAsm.next)
                        block.children.removeAt(0)
                        block.children.add(0, replacement)
                    } else if(firstAsm.label != block.label) {
                        throw AssemblyError("first chunk in block has label that differs from block name")
                    }
                }
            }

            block.children.filterIsInstance<IRSubroutine>().forEach { sub ->
                if(sub.chunks.isNotEmpty()) {
                    val first = sub.chunks.first()
                    if(first.label==null) {
                        val replacement = when(first) {
                            is IRCodeChunk -> {
                                val replacement = IRCodeChunk(sub.label, first.next)
                                replacement.instructions += first.instructions
                                replacement
                            }
                            is IRInlineAsmChunk -> IRInlineAsmChunk(sub.label, first.assembly, first.isIR, first.next)
                            is IRInlineBinaryChunk -> IRInlineBinaryChunk(sub.label, first.data, first.next)
                        }
                        sub.chunks.removeAt(0)
                        sub.chunks.add(0, replacement)
                    } else if(first.label != sub.label) {
                        val next = first as? IRCodeChunk
                        sub.chunks.add(0, IRCodeChunk(sub.label, next))
                    }
                }
            }
        }
    }

    private fun replaceMemoryMappedVars(irProg: IRProgram) {
        // replace memory mapped variable symbols with the memory address directly.
        // note: we do still export the memory mapped symbols so a code generator can use those
        //       for instance when a piece of inlined assembly references them.
        val replacements = mutableListOf<Triple<IRCodeChunkBase, Int, UInt>>()
        irProg.foreachCodeChunk { chunk ->
            chunk.instructions.withIndex().forEach {
                (idx, instr) ->
                    val symbolExpr = instr.labelSymbol
                    if(symbolExpr!=null) {
                        val index = instr.labelSymbolOffset ?: 0
                        val target = symbolTable.flat[symbolExpr]
                        if (target is StMemVar) {
                            replacements.add(Triple(chunk, idx, target.address+index.toUInt()))
                        }
                    }
                }
        }

        replacements.forEach {
            val old = it.first.instructions[it.second]
            val formats = instructionFormats.getValue(old.opcode)
            val format = formats.getOrElse(old.type) { throw IllegalArgumentException("type ${old.type} invalid for ${old.opcode}") }
            val immediateValue = if(format.immediate) it.third.toInt() else null
            val addressValue = if(format.immediate) null else it.third.toInt()

            it.first.instructions[it.second] = IRInstruction(
                old.opcode,
                old.type,
                old.reg1,
                old.reg2,
                old.reg3,
                old.fpReg1,
                old.fpReg2,
                immediate = immediateValue,
                null,
                address = addressValue,
                null,
                null
            )
        }
    }

    internal fun translateNode(node: PtNode): IRCodeChunks {
        val chunks = when(node) {
            is PtVariable -> emptyList() // var should be looked up via symbol table
            is PtMemMapped -> emptyList() // memmapped var should be looked up via symbol table
            is PtConstant -> emptyList() // constants have all been folded into the code
            is PtAssignment -> assignmentGen.translate(node)
            is PtAugmentedAssign -> assignmentGen.translate(node)
            is PtNodeGroup -> translateGroup(node.children)
            is PtBuiltinFunctionCall -> {
                val result = translateBuiltinFunc(node)
                result.chunks       // it's not an expression so no result value.
            }
            is PtFunctionCall -> {
                val result = expressionEval.translate(node)
                result.chunks       // it's not an expression so no result value
            }
            is PtNop -> emptyList()
            is PtReturn -> translate(node)
            is PtJump -> translate(node)
            is PtWhen -> translate(node)
            is PtForLoop -> translate(node)
            is PtIfElse -> translate(node)
            is PtRepeatLoop -> translate(node)
            is PtLabel -> listOf(IRCodeChunk(node.name, null))
            is PtBreakpoint -> {
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstruction(Opcode.BREAKPOINT)
                listOf(chunk)
            }
            is PtAlign -> {
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstruction(Opcode.ALIGN, immediate = node.align.toInt())
                listOf(chunk)
            }
            is PtConditionalBranch -> translate(node)
            is PtInlineAssembly -> listOf(IRInlineAsmChunk(null, node.assembly, node.isIR, null))
            is PtIncludeBinary -> listOf(IRInlineBinaryChunk(null, readBinaryData(node), null))
            is PtAddressOf,
            is PtContainmentCheck,
            is PtMemoryByte,
            is PtProgram,
            is PtArrayIndexer,
            is PtBinaryExpression,
            is PtIdentifier,
            is PtWhenChoice,
            is PtPrefix,
            is PtRange,
            is PtAssignTarget,
            is PtTypeCast,
            is PtSubroutineParameter,
            is PtNumber,
            is PtBool,
            is PtArray,
            is PtBlock,
            is PtDefer -> throw AssemblyError("should have been transformed")
            is PtString -> throw AssemblyError("should not occur as separate statement node ${node.position}")
            is PtSub -> throw AssemblyError("nested subroutines should have been flattened ${node.position}")
            else -> TODO("missing codegen for $node")
        }

        val nonEmptyChunks = chunks.filter { it.isNotEmpty() || it.label != null }
        nonEmptyChunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(node.position)

        return nonEmptyChunks
    }

    private fun readBinaryData(node: PtIncludeBinary): Collection<UByte> {
        return node.file.readBytes()
            .drop(node.offset?.toInt() ?: 0)
            .take(node.length?.toInt() ?: Int.MAX_VALUE)
            .map { it.toUByte() }
    }

    private fun translate(branch: PtConditionalBranch): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()

        val goto = branch.trueScope.children.firstOrNull() as? PtJump
        if (goto is PtJump) {
            // special case the form:   if_cc  goto <place>   (with optional else)
            val address = goto.target.asConstInteger()
            val label = (goto.target as? PtIdentifier)?.name
            if(address!=null) {
                val branchIns = IRBranchInstr(branch.condition, address=address)
                addInstr(result, branchIns, null)
            } else if(label!=null && !isIndirectJump(goto)) {
                val branchIns = IRBranchInstr(branch.condition, label = label)
                addInstr(result, branchIns, null)
            } else {
                val skipJumpLabel = createLabelName()
                // note that the branch opcode used is the opposite as the branch condition, because it needs to skip the indirect jump
                val branchIns = IRInvertedBranchInstr(branch.condition, label = skipJumpLabel)
                // evaluate jump address expression into a register and jump indirectly to it
                addInstr(result, branchIns, null)
                val tr = expressionEval.translateExpression(goto.target)
                result += tr.chunks
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.JUMPI, reg1=tr.resultReg)
                }
                result += IRCodeChunk(skipJumpLabel, null)
            }
            if(branch.falseScope.children.isNotEmpty())
                result += translateNode(branch.falseScope)
            return result
        }

        val elseLabel = createLabelName()
        // note that the branch opcode used is the opposite as the branch condition, because the generated code jumps to the 'else' part
        val branchIns = IRInvertedBranchInstr(branch.condition, label = elseLabel)
        addInstr(result, branchIns, null)
        result += translateNode(branch.trueScope)
        if(branch.falseScope.children.isNotEmpty()) {
            val endLabel = createLabelName()
            addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
            val chunks = translateNode(branch.falseScope)
            result += labelFirstChunk(chunks, elseLabel)
            result += IRCodeChunk(endLabel, null)
        } else {
            result += IRCodeChunk(elseLabel, null)
        }
        return result
    }

    private fun IRBranchInstr(condition: BranchCondition, label: String?=null, address: Int?=null): IRInstruction {
        if(label!=null)
            return when(condition) {
                BranchCondition.CS -> IRInstruction(Opcode.BSTCS, labelSymbol = label)
                BranchCondition.CC -> IRInstruction(Opcode.BSTCC, labelSymbol = label)
                BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTEQ, labelSymbol = label)
                BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTNE, labelSymbol = label)
                BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTNEG, labelSymbol = label)
                BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTPOS, labelSymbol = label)
                BranchCondition.VC -> IRInstruction(Opcode.BSTVC, labelSymbol = label)
                BranchCondition.VS -> IRInstruction(Opcode.BSTVS, labelSymbol = label)
            }
        else if(address!=null) {
            return when(condition) {
                BranchCondition.CS -> IRInstruction(Opcode.BSTCS, address = address)
                BranchCondition.CC -> IRInstruction(Opcode.BSTCC, address = address)
                BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTEQ, address = address)
                BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTNE, address = address)
                BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTNEG, address = address)
                BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTPOS, address = address)
                BranchCondition.VC -> IRInstruction(Opcode.BSTVC, address = address)
                BranchCondition.VS -> IRInstruction(Opcode.BSTVS, address = address)
            }
        }
        else throw AssemblyError("need label or address for branch")
    }

    private fun IRInvertedBranchInstr(condition: BranchCondition, label: String?=null, address: Int?=null): IRInstruction {
        if(label!=null)
            return when(condition) {
                BranchCondition.CS -> IRInstruction(Opcode.BSTCC, labelSymbol = label)
                BranchCondition.CC -> IRInstruction(Opcode.BSTCS, labelSymbol = label)
                BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTNE, labelSymbol = label)
                BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTEQ, labelSymbol = label)
                BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTPOS, labelSymbol = label)
                BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTNEG, labelSymbol = label)
                BranchCondition.VC -> IRInstruction(Opcode.BSTVS, labelSymbol = label)
                BranchCondition.VS -> IRInstruction(Opcode.BSTVC, labelSymbol = label)
            }
        else if(address!=null) {
            return when(condition) {
                BranchCondition.CS -> IRInstruction(Opcode.BSTCC, address = address)
                BranchCondition.CC -> IRInstruction(Opcode.BSTCS, address = address)
                BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTNE, address = address)
                BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTEQ, address = address)
                BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTPOS, address = address)
                BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTNEG, address = address)
                BranchCondition.VC -> IRInstruction(Opcode.BSTVS, address = address)
                BranchCondition.VS -> IRInstruction(Opcode.BSTVC, address = address)
            }
        }
        else throw AssemblyError("need label or address for branch")
    }

    private fun labelFirstChunk(chunks: IRCodeChunks, label: String): IRCodeChunks {
        if(chunks.isEmpty()) {
            return listOf(
                IRCodeChunk(label, null)
            )
        }

        require(chunks.isNotEmpty() && label.isNotBlank())
        val first = chunks[0]
        if(first.label!=null) {
            if(first.label==label)
                return chunks
            val newFirst = IRCodeChunk(label, first)
            return listOf(newFirst) + chunks
        }
        val labeledFirstChunk = when(first) {
            is IRCodeChunk -> {
                val newChunk = IRCodeChunk(label, first.next)
                newChunk.instructions += first.instructions
                newChunk
            }
            is IRInlineAsmChunk -> {
                IRInlineAsmChunk(label, first.assembly, first.isIR, first.next)
            }
            is IRInlineBinaryChunk -> {
                IRInlineBinaryChunk(label, first.data, first.next)
            }
        }
        return listOf(labeledFirstChunk) + chunks.drop(1)
    }

    private fun translate(whenStmt: PtWhen): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueDt = irType(whenStmt.value.type)
        val valueTr = expressionEval.translateExpression(whenStmt.value)
        addToResult(result, valueTr, valueTr.resultReg, -1)

        val choices = mutableListOf<Pair<String, PtWhenChoice>>()
        val endLabel = createLabelName()
        whenStmt.choices.children.forEach {
            val choice = it as PtWhenChoice
            if(choice.isElse) {
                result += translateNode(choice.statements)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
            } else {
                if(choice.statements.children.isEmpty()) {
                    // no statements for this choice value, jump to the end immediately
                    choice.values.children.map { it as PtNumber }.sortedBy { it.number }.forEach { value ->
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, valueDt, reg1=valueTr.resultReg, immediate = value.number.toInt())
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = endLabel)
                        }
                    }
                } else {
                    val choiceLabel = createLabelName()
                    choices.add(choiceLabel to choice)
                    choice.values.children.map { it as PtNumber }.sortedBy { it.number }.forEach { value ->
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, valueDt, reg1=valueTr.resultReg, immediate = value.number.toInt())
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = choiceLabel)
                        }
                    }
                }
            }
        }
        addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)

        choices.forEach { (label, choice) ->
            result += labelFirstChunk(translateNode(choice.statements), label)
            val lastStatement = choice.statements.children.last()
            if(lastStatement !is PtReturn && lastStatement !is PtJump)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
        }

        result += IRCodeChunk(endLabel, null)
        return result
    }

    private fun translate(forLoop: PtForLoop): IRCodeChunks {
        val loopvar = symbolTable.lookup(forLoop.variable.name)!!
        val iterable = forLoop.iterable
        val result = mutableListOf<IRCodeChunkBase>()
        when(iterable) {
            is PtRange -> {
                result += if(iterable.from is PtNumber && iterable.to is PtNumber)
                    translateForInConstantRange(forLoop, loopvar)
                else
                    translateForInNonConstantRange(forLoop, loopvar)
            }
            is PtIdentifier -> {
                require(forLoop.variable.name == loopvar.scopedName)
                val iterableLength = symbolTable.getLength(iterable.name)
                val loopvarSymbol = forLoop.variable.name
                val indexReg = registers.nextFree()
                val tmpReg = registers.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                when {
                    iterable.type.isString -> {
                        // iterate over a zero-terminated string
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = tmpReg, reg2 = indexReg, labelSymbol = iterable.name)
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = endLabel)
                            it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = tmpReg, labelSymbol = loopvarSymbol)
                        }
                        result += translateNode(forLoop.statements)
                        val jumpChunk = IRCodeChunk(null, null)
                        jumpChunk += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1 = indexReg)
                        jumpChunk += IRInstruction(Opcode.JUMP, labelSymbol = loopLabel)
                        result += jumpChunk
                        result += IRCodeChunk(endLabel, null)
                    }
                    iterable.type.isSplitWordArray -> {
                        // iterate over lsb/msb split word array
                        val elementDt = iterable.type.elementType()
                        if(!elementDt.isWord)
                            throw AssemblyError("weird dt")
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            val tmpRegLsb = registers.nextFree()
                            val tmpRegMsb = registers.nextFree()
                            val concatReg = registers.nextFree()
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegMsb, reg2=indexReg, labelSymbol=iterable.name+"_msb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegLsb, reg2=indexReg, labelSymbol=iterable.name+"_lsb")
                            it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=concatReg, reg2=tmpRegMsb, reg3=tmpRegLsb)
                            it += IRInstruction(Opcode.STOREM, irType(elementDt), reg1=concatReg, labelSymbol = loopvarSymbol)
                        }
                        result += translateNode(forLoop.statements)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexReg)
                            if(iterableLength!=256) {
                                // for length 256, the compare is actually against 0, which doesn't require a separate CMP instruction
                                it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=indexReg, immediate = iterableLength)
                            }
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
                        }
                    }
                    else -> {
                        // iterate over regular array
                        val elementDt = iterable.type.sub!!
                        val elementSize = program.memsizer.memorySize(elementDt)
                        val lengthBytes = iterableLength!! * elementSize
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            it += IRInstruction(Opcode.LOADX, irType(DataType.forDt(elementDt)), reg1=tmpReg, reg2=indexReg, labelSymbol=iterable.name)
                            it += IRInstruction(Opcode.STOREM, irType(DataType.forDt(elementDt)), reg1=tmpReg, labelSymbol = loopvarSymbol)
                        }
                        result += translateNode(forLoop.statements)
                        result += addConstIntToReg(IRDataType.BYTE, indexReg, elementSize)
                        result += IRCodeChunk(null, null).also {
                            if(lengthBytes!=256) {
                                // for length 256, the compare is actually against 0, which doesn't require a separate CMP instruction
                                it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=indexReg, immediate = lengthBytes)
                            }
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
                        }
                    }
                }
            }
            else -> throw AssemblyError("weird for iterable")
        }
        return result
    }

    private fun translateForInNonConstantRange(forLoop: PtForLoop, loopvar: StNode): IRCodeChunks {
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        if (step==0)
            throw AssemblyError("step 0")
        require(forLoop.variable.name == loopvar.scopedName)
        val loopvarSymbol = forLoop.variable.name
        val loopvarDt = when(loopvar) {
            is StMemVar -> loopvar.dt
            is StStaticVariable -> loopvar.dt
            else -> throw AssemblyError("invalid loopvar node type")
        }
        val loopvarDtIr = irType(loopvarDt)
        val loopLabel = createLabelName()
        val result = mutableListOf<IRCodeChunkBase>()

        if(loopvarDtIr==IRDataType.BYTE && step==-1 && iterable.to.asConstInteger()==0) {
            // downto 0 optimization (byte)
            val fromTr = expressionEval.translateExpression(iterable.from)
            addToResult(result, fromTr, fromTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=fromTr.resultReg, labelSymbol=loopvarSymbol), null)
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            result += addConstMem(loopvarDtIr, null, loopvarSymbol, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = fromTr.resultReg, labelSymbol = loopvarSymbol)
                it += IRInstruction(Opcode.CMPI, loopvarDtIr, reg1 = fromTr.resultReg, immediate = 255)
                it += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
            }
        }
        else if(step==-1 && iterable.to.asConstInteger()==1) {
            // downto 1 optimization (byte and word)
            val fromTr = expressionEval.translateExpression(iterable.from)
            addToResult(result, fromTr, fromTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=fromTr.resultReg, labelSymbol=loopvarSymbol), null)
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            result += addConstMem(loopvarDtIr, null, loopvarSymbol, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = fromTr.resultReg, labelSymbol = loopvarSymbol)
                it += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
            }
        }
        else {
            val toTr = expressionEval.translateExpression(iterable.to)
            addToResult(result, toTr, toTr.resultReg, -1)
            val fromTr = expressionEval.translateExpression(iterable.from)
            addToResult(result, fromTr, fromTr.resultReg, -1)

            val labelAfterFor = createLabelName()
            val precheckInstruction = if(loopvarDt.isSigned) {
                if(step>0)
                    IRInstruction(Opcode.BGTSR, loopvarDtIr, fromTr.resultReg, toTr.resultReg, labelSymbol=labelAfterFor)
                else
                    IRInstruction(Opcode.BGTSR, loopvarDtIr, toTr.resultReg, fromTr.resultReg, labelSymbol=labelAfterFor)
            } else {
                if(step>0)
                    IRInstruction(Opcode.BGTR, loopvarDtIr, fromTr.resultReg, toTr.resultReg, labelSymbol=labelAfterFor)
                else
                    IRInstruction(Opcode.BGTR, loopvarDtIr, toTr.resultReg, fromTr.resultReg, labelSymbol=labelAfterFor)
            }
            addInstr(result, precheckInstruction, null)

            addInstr(result, IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=fromTr.resultReg, labelSymbol=loopvarSymbol), null)
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            if(step==1 || step==-1) {
                // if endvalue == loopvar, stop loop, else iterate
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = fromTr.resultReg, labelSymbol = loopvarSymbol)
                    it += IRInstruction(Opcode.CMP, loopvarDtIr, reg1=toTr.resultReg, reg2=fromTr.resultReg)
                    it += IRInstruction(Opcode.BSTEQ, labelSymbol = labelAfterFor)
                }
                result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = loopLabel), null)
            } else {
                // ind/dec index, then:
                // ascending: if endvalue >= loopvar, iterate
                // descending: if loopvar >= endvalue, iterate
                result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
                addInstr(result, IRInstruction(Opcode.LOADM, loopvarDtIr, reg1=fromTr.resultReg, labelSymbol = loopvarSymbol), null)
                if(step > 0)
                    addInstr(result, IRInstruction(Opcode.CMP, loopvarDtIr, reg1 = toTr.resultReg, fromTr.resultReg), null)
                else
                    addInstr(result, IRInstruction(Opcode.CMP, loopvarDtIr, reg1 = fromTr.resultReg, toTr.resultReg), null)
                addInstr(result, IRInstruction(Opcode.BSTPOS, labelSymbol = loopLabel), null)
            }
            result += IRCodeChunk(labelAfterFor, null)
        }
        return result
    }

    private fun translateForInConstantRange(forLoop: PtForLoop, loopvar: StNode): IRCodeChunks {
        val loopLabel = createLabelName()
        require(forLoop.variable.name == loopvar.scopedName)
        val loopvarSymbol = forLoop.variable.name
        val indexReg = registers.nextFree()
        val loopvarDt = when(loopvar) {
            is StMemVar -> loopvar.dt
            is StStaticVariable -> loopvar.dt
            else -> throw AssemblyError("invalid loopvar node type")
        }
        val loopvarDtIr = irType(loopvarDt)
        val iterable = (forLoop.iterable as PtRange).toConstantIntegerRange()!!
        if(iterable.isEmpty())
            throw AssemblyError("empty range")
        if(iterable.step==0)
            throw AssemblyError("step 0")
        val rangeEndExclusiveUntyped = iterable.last + iterable.step
        val rangeEndExclusiveWrapped = if(loopvarDtIr==IRDataType.BYTE) rangeEndExclusiveUntyped and 255 else rangeEndExclusiveUntyped and 65535
        val result = mutableListOf<IRCodeChunkBase>()
        val chunk = IRCodeChunk(null, null)
        chunk += IRInstruction(Opcode.LOAD, loopvarDtIr, reg1=indexReg, immediate = iterable.first)
        chunk += IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=indexReg, labelSymbol=loopvarSymbol)
        result += chunk
        result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
        val chunk2 = addConstMem(loopvarDtIr, null, loopvarSymbol, iterable.step)
        if(loopvarDtIr==IRDataType.BYTE && iterable.step==-1 && iterable.last==0) {
            // downto 0 optimization (byte)
            if(loopvarDt.isSignedByte || iterable.first<=127) {
                chunk2 += IRInstruction(Opcode.BSTPOS, labelSymbol = loopLabel)
            } else {
                chunk2 += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = indexReg, labelSymbol = loopvarSymbol)
                chunk2 += IRInstruction(Opcode.CMPI, loopvarDtIr, reg1 = indexReg, immediate = rangeEndExclusiveWrapped)
                chunk2 += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
            }
        }
        else if(iterable.step==-1 && iterable.last==1) {
            // downto 1 optimization (byte and word)
            chunk2 += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
        } else {
            // downto some other value
            chunk2 += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = indexReg, labelSymbol = loopvarSymbol)
            chunk2 += IRInstruction(Opcode.CMPI, loopvarDtIr, reg1 = indexReg, immediate = rangeEndExclusiveWrapped)
            chunk2 += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
        }
        result += chunk2
        return result
    }

    private fun addConstIntToReg(dt: IRDataType, reg: Int, value: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += IRInstruction(Opcode.INC, dt, reg1=reg)
            }
            2 -> {
                code += IRInstruction(Opcode.INC, dt, reg1=reg)
                code += IRInstruction(Opcode.INC, dt, reg1=reg)
            }
            -1 -> {
                code += IRInstruction(Opcode.DEC, dt, reg1=reg)
            }
            -2 -> {
                code += IRInstruction(Opcode.DEC, dt, reg1=reg)
                code += IRInstruction(Opcode.DEC, dt, reg1=reg)
            }
            else -> {
                code += if(value>0) {
                    IRInstruction(Opcode.ADD, dt, reg1 = reg, immediate = value)
                } else {
                    IRInstruction(Opcode.SUB, dt, reg1 = reg, immediate = -value)
                }
            }
        }
        return code
    }

    private fun addConstMem(dt: IRDataType, knownAddress: UInt?, symbol: String?, value: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.INCM, dt, address = knownAddress.toInt())
                else
                    IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
            }
            2 -> {
                if(knownAddress!=null) {
                    code += IRInstruction(Opcode.INCM, dt, address = knownAddress.toInt())
                    code += IRInstruction(Opcode.INCM, dt, address = knownAddress.toInt())
                } else {
                    code += IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
                    code += IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
                }
            }
            -1 -> {
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, dt, address = knownAddress.toInt())
                else
                    IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
            }
            -2 -> {
                if(knownAddress!=null) {
                    code += IRInstruction(Opcode.DECM, dt, address = knownAddress.toInt())
                    code += IRInstruction(Opcode.DECM, dt, address = knownAddress.toInt())
                } else {
                    code += IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
                    code += IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
                }
            }
            else -> {
                val valueReg = registers.nextFree()
                if(value>0) {
                    code += IRInstruction(Opcode.LOAD, dt, reg1=valueReg, immediate = value)
                    code += if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, dt, reg1=valueReg, address = knownAddress.toInt())
                    else
                        IRInstruction(Opcode.ADDM, dt, reg1=valueReg, labelSymbol = symbol)
                }
                else {
                    code += IRInstruction(Opcode.LOAD, dt, reg1=valueReg, immediate = -value)
                    code += if(knownAddress!=null)
                        IRInstruction(Opcode.SUBM, dt, reg1=valueReg, address = knownAddress.toInt())
                    else
                        IRInstruction(Opcode.SUBM, dt, reg1=valueReg, labelSymbol = symbol)
                }
            }
        }
        return code
    }

    internal fun multiplyByConstFloat(fpReg: Int, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        code += if(factor==0.0) {
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = 0.0)
        } else {
            IRInstruction(Opcode.MUL, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        if(factor==0.0) {
            code += if(knownAddress!=null)
                IRInstruction(Opcode.STOREZM, IRDataType.FLOAT, address = knownAddress)
            else
                IRInstruction(Opcode.STOREZM, IRDataType.FLOAT, labelSymbol = symbol)
        } else {
            val factorReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1=factorReg, immediateFp = factor)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.MULM, IRDataType.FLOAT, fpReg1 = factorReg, address = knownAddress)
            else
                IRInstruction(Opcode.MULM, IRDataType.FLOAT, fpReg1 = factorReg, labelSymbol = symbol)
        }
        return code
    }

    internal fun multiplyByConst(dt: IRDataType, reg: Int, factor: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += IRInstruction(Opcode.LSL, dt, reg1=reg)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
            code += IRInstruction(Opcode.LSLN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRInstruction(Opcode.LOAD, dt, reg1=reg, immediate = 0)
            } else {
                IRInstruction(Opcode.MUL, dt, reg1=reg, immediate = factor)
            }
        }
        return code
    }

    internal fun multiplyByConstInplace(dt: IRDataType, knownAddress: Int?, symbol: String?, factor: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += if(knownAddress!=null)
                IRInstruction(Opcode.LSLM, dt, address = knownAddress)
            else
                IRInstruction(Opcode.LSLM, dt, labelSymbol = symbol)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.LSLNM, dt, reg1=pow2reg, address = knownAddress)
            else
                IRInstruction(Opcode.LSLNM, dt, reg1=pow2reg, labelSymbol = symbol)
        } else {
            if (factor == 0) {
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.STOREZM, dt, address = knownAddress)
                else
                    IRInstruction(Opcode.STOREZM, dt, labelSymbol = symbol)
            }
            else {
                val factorReg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=factorReg, immediate = factor)
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, dt, reg1=factorReg, address = knownAddress)
                else
                    IRInstruction(Opcode.MULM, dt, reg1=factorReg, labelSymbol = symbol)
            }
        }
        return code
    }

    internal fun divideByConstFloat(fpReg: Int, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        code += if(factor==0.0) {
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = Double.MAX_VALUE)
        } else {
            IRInstruction(Opcode.DIVS, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        if(factor==0.0) {
            val maxvalueReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = maxvalueReg, immediateFp = Double.MAX_VALUE)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = maxvalueReg, address = knownAddress)
            else
                IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = maxvalueReg, labelSymbol = symbol)
        } else {
            val factorReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1=factorReg, immediateFp = factor)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.DIVSM, IRDataType.FLOAT, fpReg1 = factorReg, address = knownAddress)
            else
                IRInstruction(Opcode.DIVSM, IRDataType.FLOAT, fpReg1 = factorReg, labelSymbol = symbol)
        }
        return code
    }

    internal fun divideByConst(dt: IRDataType, reg: Int, factor: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2>=0) {
            if(signed) {
                if(pow2==1) {
                    // simple single bit shift (signed)
                    code += IRInstruction(Opcode.ASR, dt, reg1=reg)
                } else {
                    // just shift multiple bits (signed)
                    val pow2reg = registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
                    code += IRInstruction(Opcode.ASRN, dt, reg1=reg, reg2=pow2reg)
                }
            } else {
                if(pow2==1) {
                    // simple single bit shift (unsigned)
                    code += IRInstruction(Opcode.LSR, dt, reg1=reg)
                } else {
                    // just shift multiple bits (unsigned)
                    val pow2reg = registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, dt, reg1 = pow2reg, immediate = pow2)
                    code += IRInstruction(Opcode.LSRN, dt, reg1 = reg, reg2 = pow2reg)
                }
            }
            return code
        } else {
            // regular div
            code += if (factor == 0) {
                IRInstruction(Opcode.LOAD, dt, reg1=reg, immediate = 0xffff)
            } else {
                if(signed)
                    IRInstruction(Opcode.DIVS, dt, reg1=reg, immediate = factor)
                else
                    IRInstruction(Opcode.DIV, dt, reg1=reg, immediate = factor)
            }
            return code
        }
    }

    internal fun divideByConstInplace(dt: IRDataType, knownAddress: Int?, symbol: String?, factor: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2>=0) {
            // can do bit shift instead of division
            if(signed) {
                if(pow2==1) {
                    // just simple bit shift (signed)
                    code += if (knownAddress != null)
                        IRInstruction(Opcode.ASRM, dt, address = knownAddress)
                    else
                        IRInstruction(Opcode.ASRM, dt, labelSymbol = symbol)
                } else {
                    // just shift multiple bits (signed)
                    val pow2reg = registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, dt, reg1 = pow2reg, immediate = pow2)
                    code += if (knownAddress != null)
                                IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, address = knownAddress)
                            else
                                IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
                }
            } else {
                if(pow2==1) {
                    // just simple bit shift (unsigned)
                    code += if(knownAddress!=null)
                        IRInstruction(Opcode.LSRM, dt, address = knownAddress)
                    else
                        IRInstruction(Opcode.LSRM, dt, labelSymbol = symbol)
                }
                else {
                    // just shift multiple bits (unsigned)
                    val pow2reg = registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
                    code += if(knownAddress!=null)
                                IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, address = knownAddress)
                            else
                                IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
                }
            }
            return code
        }
        else
        {
            // regular div
            if (factor == 0) {
                val reg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=reg, immediate = 0xffff)
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.STOREM, dt, reg1=reg, address = knownAddress)
                else
                    IRInstruction(Opcode.STOREM, dt, reg1=reg, labelSymbol = symbol)
            }
            else {
                val factorReg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=factorReg, immediate = factor)
                code += if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, dt, reg1 = factorReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, dt, reg1 = factorReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, dt, reg1 = factorReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, dt, reg1 = factorReg, labelSymbol = symbol)
                }
            }
            return code
        }
    }

    private fun translate(ifElse: PtIfElse): IRCodeChunks {
        val goto = ifElse.ifScope.children.firstOrNull() as? PtJump
        return if(goto!=null && ifElse.elseScope.children.isEmpty()) {
            translateIfFollowedByJustGoto(ifElse, goto)
        } else {
            translateIfElse(ifElse)
        }
    }

    private fun translateIfFollowedByJustGoto(ifElse: PtIfElse, goto: PtJump): MutableList<IRCodeChunkBase> {
        val condition = ifElse.condition as? PtBinaryExpression
        if(condition==null || !condition.left.type.isFloat) {
            return if(isIndirectJump(goto))
                ifWithOnlyIndirectJump_IntegerCond(ifElse, goto)
            else
                ifWithOnlyNormalJump_IntegerCond(ifElse, goto)
        }

        // floating-point condition only from here!
        // we assume only a binary expression can contain a floating point.
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = expressionEval.translateExpression(condition.left)
        addToResult(result, leftTr, -1, leftTr.resultFpReg)
        val rightTr = expressionEval.translateExpression(condition.right)
        addToResult(result, rightTr, -1, rightTr.resultFpReg)
        var afterIfLabel = ""
        result += IRCodeChunk(null, null).also {
            val compResultReg = registers.nextFree()
            it += IRInstruction(
                Opcode.FCOMP,
                IRDataType.FLOAT,
                reg1 = compResultReg,
                fpReg1 = leftTr.resultFpReg,
                fpReg2 = rightTr.resultFpReg
            )

            if(isIndirectJump(goto)) {
                // indirect jump to target so the if has to jump past it instead
                afterIfLabel = createLabelName()
                when(condition.operator) {
                    "==" -> {
                        it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                        it += IRInstruction(Opcode.BSTNE, labelSymbol = afterIfLabel)
                    }
                    "!=" -> {
                        it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                        it += IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel)
                    }
                    else -> {
                        val gotoOpcode = when (condition.operator) {
                            "<" -> Opcode.BGES
                            ">" -> Opcode.BLES
                            "<=" -> Opcode.BGTS
                            ">=" -> Opcode.BLTS
                            else -> throw AssemblyError("weird operator")
                        }
                        it += IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = afterIfLabel)
                    }
                }
                // evaluate jump address expression into a register and jump indirectly to it
                val tr = expressionEval.translateExpression(goto.target)
                for(i in tr.chunks.flatMap { it.instructions }) {
                    it += i
                }
                it += IRInstruction(Opcode.JUMPI, reg1 = tr.resultReg)
            } else {
                // normal jump, directly to target with branch opcode
                when(condition.operator) {
                    "==" -> {
                        it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                        it += branchInstr(goto, Opcode.BSTEQ)
                    }
                    "!=" -> {
                        it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                        it += branchInstr(goto, Opcode.BSTNE)
                    }
                    else -> {
                        val gotoOpcode = when (condition.operator) {
                            "<" -> Opcode.BLTS
                            ">" -> Opcode.BGTS
                            "<=" -> Opcode.BLES
                            ">=" -> Opcode.BGES
                            else -> throw AssemblyError("weird operator")
                        }
                        it += if (goto.target.asConstInteger() != null)
                            IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, address = goto.target.asConstInteger())
                        else if(goto.target is PtIdentifier && !isIndirectJump(goto))
                            IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = (goto.target as PtIdentifier).name)
                        else
                            throw AssemblyError("non-indirect jump shouldn't have an expression as target")
                    }
                }
            }
        }
        if(afterIfLabel.isNotEmpty())
            result += IRCodeChunk(afterIfLabel, null)
        return result
    }

    private fun branchInstr(goto: PtJump, branchOpcode: Opcode): IRInstruction {
        return if (goto.target.asConstInteger() != null)
            IRInstruction(branchOpcode, address = goto.target.asConstInteger())
        else {
            require(!isIndirectJump(goto)) { "indirect jumps cannot be expressed using a branch opcode"}
            val identifier = goto.target as? PtIdentifier
            if(identifier!=null && !isIndirectJump(goto))
                IRInstruction(branchOpcode, labelSymbol = identifier.name)
            else
                TODO("JUMP to expression address ${goto.target}")
        }
    }

    private fun ifWithOnlyIndirectJump_IntegerCond(ifElse: PtIfElse, goto: PtJump): MutableList<IRCodeChunkBase> {
        // indirect jump to target so the if has to jump past it instead
        val result = mutableListOf<IRCodeChunkBase>()
        val afterIfLabel = createLabelName()

        fun ifNonZeroIntThenJump_BinExpr(condition: PtBinaryExpression) {
            if(condition.operator in LogicalOperators) {
                val trCond = expressionEval.translateExpression(condition)
                result += trCond.chunks
                addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel), null)
                return
            }

            val leftTr = expressionEval.translateExpression(condition.left)
            val irDt = leftTr.dt
            val signed = condition.left.type.isSigned
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val number = (condition.right as? PtNumber)?.number?.toInt()
            if(number!=null) {
                val firstReg = leftTr.resultReg
                when(condition.operator) {
                    "==" -> {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, irDt, reg1 = firstReg, immediate = number)
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = afterIfLabel)
                        }
                    }
                    "!=" -> {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, irDt, reg1 = firstReg, immediate = number)
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel)
                        }
                    }
                    else -> {
                        val opcode = when (condition.operator) {
                            "<" -> if(signed) Opcode.BGES else Opcode.BGE
                            ">" -> if(signed) Opcode.BLES else Opcode.BLE
                            "<=" -> if(signed) Opcode.BGTS else Opcode.BGT
                            ">=" -> if(signed) Opcode.BLTS else Opcode.BLT
                            else -> throw AssemblyError("invalid comparison operator")
                        }
                        addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, immediate = number, labelSymbol = afterIfLabel), null)
                    }
                }
            } else {
                val rightTr = expressionEval.translateExpression(condition.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val firstReg: Int
                val secondReg: Int
                val opcode: Opcode
                var useCmp = false
                when (condition.operator) {
                    "==" -> {
                        useCmp = true
                        opcode = Opcode.BSTNE
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "!=" -> {
                        useCmp = true
                        opcode = Opcode.BSTEQ
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "<" -> {
                        opcode = if (signed) Opcode.BGESR else Opcode.BGER
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    ">" -> {
                        // swapped operands
                        opcode = if (signed) Opcode.BGESR else Opcode.BGER
                        firstReg = rightTr.resultReg
                        secondReg = leftTr.resultReg
                    }
                    "<=" -> {
                        opcode = if (signed) Opcode.BGTSR else Opcode.BGTR
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    ">=" -> {
                        // swapped operands
                        opcode = if (signed) Opcode.BGTSR else Opcode.BGTR
                        firstReg = rightTr.resultReg
                        secondReg = leftTr.resultReg
                    }
                    else -> throw AssemblyError("invalid comparison operator")
                }

                if(useCmp) {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.CMP, irDt, reg1 = firstReg, reg2 = secondReg)
                        it += IRInstruction(opcode, labelSymbol = afterIfLabel)
                    }
                } else {
                    addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, reg2 = secondReg, labelSymbol = afterIfLabel), null)
                }
            }
        }

        when(val cond = ifElse.condition) {
            is PtTypeCast -> {
                require(cond.type.isBool && cond.value.type.isNumeric)
                val tr = expressionEval.translateExpression(cond)
                result += tr.chunks
                addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel), null)
            }
            is PtIdentifier, is PtArrayIndexer, is PtBuiltinFunctionCall, is PtFunctionCall, is PtContainmentCheck -> {
                val tr = expressionEval.translateExpression(cond)
                result += tr.chunks
                addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel), null)
            }
            is PtPrefix -> {
                require(cond.operator=="not")
                val tr = expressionEval.translateExpression(cond.value)
                result += tr.chunks
                addInstr(result, IRInstruction(Opcode.BSTNE, labelSymbol = afterIfLabel), null)
            }
            is PtBinaryExpression -> {
                ifNonZeroIntThenJump_BinExpr(cond)
            }
            else -> throw AssemblyError("weird if condition ${ifElse.condition}")
        }

        // indirect jump to some computed address
        val tr = expressionEval.translateExpression(goto.target)
        result += tr.chunks
        addInstr(result, IRInstruction(Opcode.JUMPI, reg1 = tr.resultReg), null)
        result += IRCodeChunk(afterIfLabel, null)
        return result
    }

    private fun ifWithOnlyNormalJump_IntegerCond(ifElse: PtIfElse, goto: PtJump): MutableList<IRCodeChunkBase> {
        // normal goto after if, using branch instructions

        val result = mutableListOf<IRCodeChunkBase>()

        fun ifNonZeroIntThenJump_BinExpr(condition: PtBinaryExpression) {
            if(condition.operator in LogicalOperators) {
                val trCond = expressionEval.translateExpression(condition)
                result += trCond.chunks
                addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
                return
            }

            val leftTr = expressionEval.translateExpression(condition.left)
            val irDt = leftTr.dt
            val signed = condition.left.type.isSigned
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val number = (condition.right as? PtNumber)?.number?.toInt()
            if(number!=null) {
                val firstReg = leftTr.resultReg
                when(condition.operator) {
                    "==" -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, irDt, reg1 = firstReg, immediate = number), null)
                        addInstr(result, branchInstr(goto, Opcode.BSTEQ), null)
                    }
                    "!=" -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, irDt, reg1 = firstReg, immediate = number), null)
                        addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
                    }
                    else -> {
                        val opcode = when (condition.operator) {
                            "<" -> if(signed) Opcode.BLTS else Opcode.BLT
                            ">" -> if(signed) Opcode.BGTS else Opcode.BGT
                            "<=" -> if(signed) Opcode.BLES else Opcode.BLE
                            ">=" -> if(signed) Opcode.BGES else Opcode.BGE
                            else -> throw AssemblyError("invalid comparison operator")
                        }
                        if (goto.target.asConstInteger() != null)
                            addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, immediate = number, address = goto.target.asConstInteger()), null)
                        else if(goto.target is PtIdentifier && !isIndirectJump(goto))
                            addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, immediate = number, labelSymbol = (goto.target as PtIdentifier).name), null)
                        else
                            throw AssemblyError("non-indirect jump shouldn't have an expression as target")
                    }
                }
            } else {
                val rightTr = expressionEval.translateExpression(condition.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val firstReg: Int
                val secondReg: Int
                val opcode: Opcode
                var useCmp = false
                when (condition.operator) {
                    "==" -> {
                        useCmp = true
                        opcode = Opcode.BSTEQ
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "!=" -> {
                        useCmp = true
                        opcode = Opcode.BSTNE
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "<" -> {
                        // swapped '>'
                        opcode = if (signed) Opcode.BGTSR else Opcode.BGTR
                        firstReg = rightTr.resultReg
                        secondReg = leftTr.resultReg
                    }
                    ">" -> {
                        opcode = if (signed) Opcode.BGTSR else Opcode.BGTR
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "<=" -> {
                        // swapped '>='
                        opcode = if (signed) Opcode.BGESR else Opcode.BGER
                        firstReg = rightTr.resultReg
                        secondReg = leftTr.resultReg
                    }
                    ">=" -> {
                        opcode = if (signed) Opcode.BGESR else Opcode.BGER
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    else -> throw AssemblyError("invalid comparison operator")
                }

                if(useCmp) {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.CMP, irDt, reg1 = firstReg, reg2 = secondReg)
                        it += branchInstr(goto, opcode)
                    }
                } else {
                    if (goto.target.asConstInteger() != null)
                        addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, reg2 = secondReg, address = goto.target.asConstInteger()), null)
                    else if(goto.target is PtIdentifier && !isIndirectJump(goto))
                        addInstr(result, IRInstruction(opcode, irDt, reg1 = firstReg, reg2 = secondReg, labelSymbol = (goto.target as PtIdentifier).name), null)
                    else
                        throw AssemblyError("non-indirect jump shouldn't have an expression as target")
                }
            }
        }

        when(val cond = ifElse.condition) {
            is PtTypeCast -> {
                require(cond.type.isBool && cond.value.type.isNumeric)
                val tr = expressionEval.translateExpression(cond)
                result += tr.chunks
                addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
            }
            is PtIdentifier, is PtArrayIndexer, is PtBuiltinFunctionCall, is PtFunctionCall, is PtContainmentCheck -> {
                val tr = expressionEval.translateExpression(cond)
                result += tr.chunks
                addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
            }
            is PtPrefix -> {
                require(cond.operator=="not")
                val tr = expressionEval.translateExpression(cond.value)
                result += tr.chunks
                addInstr(result, branchInstr(goto, Opcode.BSTEQ), null)
            }
            is PtBinaryExpression -> ifNonZeroIntThenJump_BinExpr(cond)
            else -> throw AssemblyError("weird if condition ${ifElse.condition}")
        }
        return result
    }

    private fun translateIfElse(ifElse: PtIfElse): IRCodeChunks {
        if((ifElse.condition as? PtPrefix)?.operator=="not" && ifElse.hasElse())
            throw AssemblyError("not prefix in ifelse should have been replaced by swapped if-else blocks")

        val condition = ifElse.condition as? PtBinaryExpression
        if(condition==null || !condition.left.type.isFloat) {
            return ifElse_IntegerCond(ifElse)
        }

        // we assume only a binary expression can contain a floating point.
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = expressionEval.translateExpression(condition.left)
        addToResult(result, leftTr, -1, leftTr.resultFpReg)
        val rightTr = expressionEval.translateExpression(condition.right)
        addToResult(result, rightTr, -1, rightTr.resultFpReg)
        val compResultReg = registers.nextFree()
        addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1 = compResultReg, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
        val elseBranch: Opcode
        var useCmpi = false     // for the branch opcodes that have been converted to CMPI + BSTxx form already
        when (condition.operator) {
            "==" -> {
                elseBranch = Opcode.BSTNE
                useCmpi = true
            }
            "!=" -> {
                elseBranch = Opcode.BSTEQ
                useCmpi = true
            }
            "<" -> elseBranch = Opcode.BGES
            ">" -> elseBranch = Opcode.BLES
            "<=" -> elseBranch = Opcode.BGTS
            ">=" -> elseBranch = Opcode.BLTS
            else -> throw AssemblyError("weird operator")
        }

        if (ifElse.hasElse()) {
            // if and else parts
            val elseLabel = createLabelName()
            val afterIfLabel = createLabelName()
            if(useCmpi) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                    it += IRInstruction(elseBranch, labelSymbol = elseLabel)
                }
            } else
                addInstr(result, IRInstruction(elseBranch, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = elseLabel), null)
            result += translateNode(ifElse.ifScope)
            addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
            result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
            result += IRCodeChunk(afterIfLabel, null)
        } else {
            // only if part
            val afterIfLabel = createLabelName()
            if(useCmpi) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                    it += IRInstruction(elseBranch, labelSymbol = afterIfLabel)
                }
            } else
                addInstr(result, IRInstruction(elseBranch, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = afterIfLabel), null)
            result += translateNode(ifElse.ifScope)
            result += IRCodeChunk(afterIfLabel, null)
        }
        return result
    }

    private fun ifElse_IntegerCond(ifElse: PtIfElse): List<IRCodeChunkBase> {
        val result = mutableListOf<IRCodeChunkBase>()

        fun translateSimple(condition: PtExpression, jumpFalseOpcode: Opcode, addCmpiZero: Boolean) {

            fun ifElseUsingBIT(testBitSet: Boolean, value: PtIdentifier, bitnumber: Int) {
                addInstr(result, IRInstruction(Opcode.BIT, IRDataType.BYTE, labelSymbol = value.name), null)
                val bitBranchOpcode = when(testBitSet) {
                    true -> when(bitnumber) {
                        6 -> Opcode.BSTVC
                        7 -> Opcode.BSTPOS
                        else -> throw AssemblyError("need bit 6 or 7")
                    }
                    false -> when(bitnumber) {
                        6 -> Opcode.BSTVS
                        7 -> Opcode.BSTNEG
                        else -> throw AssemblyError("need bit 6 or 7")
                    }
                }

                if(ifElse.hasElse()) {
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    addInstr(result, IRInstruction(bitBranchOpcode, labelSymbol = elseLabel), null)
                    result += translateNode(ifElse.ifScope)
                    addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                    result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                    result += IRCodeChunk(afterIfLabel, null)
                } else {
                    val afterIfLabel = createLabelName()
                    addInstr(result, IRInstruction(bitBranchOpcode, labelSymbol = afterIfLabel), null)
                    result += translateNode(ifElse.ifScope)
                    result += IRCodeChunk(afterIfLabel, null)
                }
            }

            if(condition is PtBuiltinFunctionCall && condition.name.startsWith("prog8_ifelse_bittest_")) {
                // use a BIT instruction to test for bit 8 or 7
                when(condition.name) {
                    "prog8_ifelse_bittest_set" -> {
                        ifElseUsingBIT(true, condition.args[0] as PtIdentifier, condition.args[1].asConstInteger()!!)
                        return
                    }
                    "prog8_ifelse_bittest_notset" -> {
                        ifElseUsingBIT(false, condition.args[0] as PtIdentifier, condition.args[1].asConstInteger()!!)
                        return
                    }
                    else -> throw AssemblyError("weird bittest")
                }
            }

            val tr = expressionEval.translateExpression(condition)
            if(addCmpiZero)
                tr.chunks.last().instructions.add(IRInstruction(Opcode.CMPI, tr.dt, reg1 = tr.resultReg, immediate = 0))
            result += tr.chunks
            if(ifElse.hasElse()) {
                val elseLabel = createLabelName()
                val afterIfLabel = createLabelName()
                addInstr(result, IRInstruction(jumpFalseOpcode, labelSymbol = elseLabel), null)
                result += translateNode(ifElse.ifScope)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                result += IRCodeChunk(afterIfLabel, null)
            } else {
                val afterIfLabel = createLabelName()
                addInstr(result, IRInstruction(jumpFalseOpcode, labelSymbol = afterIfLabel), null)
                result += translateNode(ifElse.ifScope)
                result += IRCodeChunk(afterIfLabel, null)
            }
        }

        fun translateBinExpr(condition: PtBinaryExpression) {
            if(condition.operator in LogicalOperators)
                return translateSimple(condition, Opcode.BSTEQ, false)

            val signed = condition.left.type.isSigned
            val elseBranchFirstReg: Int
            val elseBranchSecondReg: Int
            val number = (condition.right as? PtNumber)?.number?.toInt()

            val leftTr = expressionEval.translateExpression(condition.left)
            val branchDt = leftTr.dt
            addToResult(result, leftTr, leftTr.resultReg, -1)
            if (number!=null) {
                val elseBranch: Opcode
                var useCmpi = false     // for the branch opcodes that have been converted to CMPI + BSTxx form already
                when (condition.operator) {
                    "==" -> {
                        elseBranch = Opcode.BSTNE
                        useCmpi = true
                    }
                    "!=" -> {
                        elseBranch = Opcode.BSTEQ
                        useCmpi = true
                    }
                    "<" -> elseBranch = if(signed) Opcode.BGES else Opcode.BGE
                    ">" -> elseBranch = if(signed) Opcode.BLES else Opcode.BLE
                    "<=" -> elseBranch = if(signed) Opcode.BGTS else Opcode.BGT
                    ">=" -> elseBranch = if(signed) Opcode.BLTS else Opcode.BLT
                    else -> throw AssemblyError("invalid comparison operator")
                }

                if (ifElse.hasElse()) {
                    // if and else parts
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    if(useCmpi) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, branchDt, reg1 = leftTr.resultReg, immediate = number)
                            it += IRInstruction(elseBranch, labelSymbol = elseLabel)
                        }
                    } else {
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = leftTr.resultReg, immediate = number, labelSymbol = elseLabel), null)
                    }
                    result += translateNode(ifElse.ifScope)
                    addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                    result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                    result += IRCodeChunk(afterIfLabel, null)
                } else {
                    // only if part
                    val afterIfLabel = createLabelName()
                    if(useCmpi) {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, branchDt, reg1 = leftTr.resultReg, immediate = number)
                            it += IRInstruction(elseBranch, labelSymbol = afterIfLabel)
                        }
                    } else
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = leftTr.resultReg, immediate = number, labelSymbol = afterIfLabel), null)
                    result += translateNode(ifElse.ifScope)
                    result += IRCodeChunk(afterIfLabel, null)
                }
            } else {
                val rightTr = expressionEval.translateExpression(condition.right)
                val elseBranch: Opcode
                var useCmp = false
                addToResult(result, rightTr, rightTr.resultReg, -1)
                when (condition.operator) {
                    "==" -> {
                        useCmp = true
                        elseBranch = Opcode.BSTNE
                        elseBranchFirstReg = leftTr.resultReg
                        elseBranchSecondReg = rightTr.resultReg
                    }
                    "!=" -> {
                        useCmp = true
                        elseBranch = Opcode.BSTEQ
                        elseBranchFirstReg = leftTr.resultReg
                        elseBranchSecondReg = rightTr.resultReg
                    }
                    "<" -> {
                        // else part when left >= right
                        elseBranch = if (signed) Opcode.BGESR else Opcode.BGER
                        elseBranchFirstReg = leftTr.resultReg
                        elseBranchSecondReg = rightTr.resultReg
                    }
                    ">" -> {
                        // else part when left <= right --> right >= left
                        elseBranch = if (signed) Opcode.BGESR else Opcode.BGER
                        elseBranchFirstReg = rightTr.resultReg
                        elseBranchSecondReg = leftTr.resultReg
                    }
                    "<=" -> {
                        // else part when left > right
                        elseBranch = if (signed) Opcode.BGTSR else Opcode.BGTR
                        elseBranchFirstReg = leftTr.resultReg
                        elseBranchSecondReg = rightTr.resultReg
                    }
                    ">=" -> {
                        // else part when left < right --> right > left
                        elseBranch = if (signed) Opcode.BGTSR else Opcode.BGTR
                        elseBranchFirstReg = rightTr.resultReg
                        elseBranchSecondReg = leftTr.resultReg
                    }
                    else -> throw AssemblyError("invalid comparison operator")
                }

                if (ifElse.hasElse()) {
                    // if and else parts
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    if(useCmp) {
                        result += IRCodeChunk(null,null).also {
                            it += IRInstruction(Opcode.CMP, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg)
                            it += IRInstruction(elseBranch, labelSymbol = elseLabel)
                        }
                    } else {
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg, labelSymbol = elseLabel), null)
                    }
                    result += translateNode(ifElse.ifScope)
                    addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                    result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                    result += IRCodeChunk(afterIfLabel, null)
                } else {
                    // only if part
                    val afterIfLabel = createLabelName()
                    if(useCmp) {
                        result += IRCodeChunk(null,null).also {
                            it += IRInstruction(Opcode.CMP, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg)
                            it += IRInstruction(elseBranch, labelSymbol = afterIfLabel)
                        }
                    } else {
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg, labelSymbol = afterIfLabel), null)
                    }
                    result += translateNode(ifElse.ifScope)
                    result += IRCodeChunk(afterIfLabel, null)
                }
            }
        }

        when(val cond=ifElse.condition) {
            // TODO investigate; maybe do all conditions require a CMPI at the end? Some here still have false, but do they work correctly in all cases?
            is PtBool -> {
                // normally this will be optimized away, but not with -noopt
                translateSimple(cond, Opcode.BSTEQ, false)
            }
            is PtTypeCast -> {
                require(cond.type.isBool && cond.value.type.isNumeric)
                translateSimple(cond, Opcode.BSTEQ, false)
            }
            is PtIdentifier, is PtArrayIndexer, is PtContainmentCheck -> {
                translateSimple(cond, Opcode.BSTEQ, false)
            }
            is PtBuiltinFunctionCall, is PtFunctionCall -> {
                translateSimple(cond, Opcode.BSTEQ, true)
            }
            is PtPrefix -> {
                require(cond.operator=="not")
                translateSimple(cond.value, Opcode.BSTNE, true)
            }
            is PtBinaryExpression -> {
                translateBinExpr(cond)
            }
            else -> throw AssemblyError("weird if condition ${ifElse.condition}")
        }
        return result
    }

    private fun translate(repeat: PtRepeatLoop): IRCodeChunks {
        when (repeat.count.asConstInteger()) {
            0 -> return emptyList()
            1 -> return translateGroup(repeat.children)
            256 -> {
                // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
                repeat.children[0] = PtNumber(BaseDataType.UBYTE, 0.0, repeat.count.position)
            }
        }

        val repeatLabel = createLabelName()
        val skipRepeatLabel = createLabelName()
        val constRepeats = repeat.count.asConstInteger()
        val result = mutableListOf<IRCodeChunkBase>()
        if(constRepeats==65536) {
            // make use of the word wrap around to count to 65536
            val resultRegister = registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=resultRegister, immediate = 0), null)
            result += labelFirstChunk(translateNode(repeat.statements), repeatLabel)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.DEC, IRDataType.WORD, reg1 = resultRegister)  // sets status bits
                it += IRInstruction(Opcode.BSTNE, labelSymbol = repeatLabel)
            }
        } else {
            val irDt = irType(repeat.count.type)
            val countTr = expressionEval.translateExpression(repeat.count)
            addToResult(result, countTr, countTr.resultReg, -1)
            if (repeat.count.asConstValue() == null) {
                // check if the counter is already zero
                addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = skipRepeatLabel), null)
            }
            result += labelFirstChunk(translateNode(repeat.statements), repeatLabel)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.DEC, irDt, reg1 = countTr.resultReg)  // sets status bits
                it += IRInstruction(Opcode.BSTNE, labelSymbol = repeatLabel)
            }
        }
        result += IRCodeChunk(skipRepeatLabel, null)
        return result
    }

    private fun translate(jump: PtJump): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val chunk = IRCodeChunk(null, null)
        if(jump.target.asConstInteger()!=null) {
            chunk += IRInstruction(Opcode.JUMP, address = jump.target.asConstInteger())
            result += chunk
            return result
        } else {
            val identifier = jump.target as? PtIdentifier
            if (identifier != null && !isIndirectJump(jump)) {
                // jump to label
                chunk += IRInstruction(Opcode.JUMP, labelSymbol = identifier.name)
                result += chunk
                return result
            }
            // evaluate jump address expression into a register and jump indirectly to it
            val tr = expressionEval.translateExpression(jump.target)
            result += tr.chunks
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.JUMPI, reg1=tr.resultReg)
            }
            return result
        }
    }

    private fun isIndirectJump(jump: PtJump): Boolean {
        if(jump.target.asConstInteger()!=null)
            return false
        val identifier = jump.target as? PtIdentifier
        if(identifier==null)
            return true
        val symbol = symbolTable.lookup(identifier.name)
        return symbol?.type==StNodeType.MEMVAR || symbol?.type==StNodeType.STATICVAR
    }

    private fun translateGroup(group: List<PtNode>): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        group.forEach { result += translateNode(it) }
        return result
    }

    private fun translate(ret: PtReturn): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val value = ret.value
        if(value==null) {
            addInstr(result, IRInstruction(Opcode.RETURN), null)
        } else {
            if(value.type.isFloat) {
                if(value is PtNumber) {
                    addInstr(result, IRInstruction(Opcode.RETURNI, IRDataType.FLOAT, immediateFp = value.number), null)
                } else {
                    val tr = expressionEval.translateExpression(value)
                    addToResult(result, tr, -1, tr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.RETURNR, IRDataType.FLOAT, fpReg1 = tr.resultFpReg), null)
                }
            }
            else {
                if(value.asConstInteger()!=null) {
                    addInstr(result, IRInstruction(Opcode.RETURNI, irType(value.type), immediate = value.asConstInteger()), null)
                } else {
                    val tr = expressionEval.translateExpression(value)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.RETURNR, irType(value.type), reg1 = tr.resultReg), null)
                }
            }
        }
        return result
    }

    private fun translate(block: PtBlock): IRBlock {
        val irBlock = IRBlock(block.name, block.library,
            IRBlock.Options(
                block.options.address,
                block.options.forceOutput,
                block.options.noSymbolPrefixing,
                block.options.veraFxMuls,
                block.options.ignoreUnused
            ), block.position)
        for (child in block.children) {
            when(child) {
                is PtNop -> { /* nothing */ }
                is PtAssignment, is PtAugmentedAssign -> { /* global variable initialization is done elsewhere */ }
                is PtVariable, is PtConstant, is PtMemMapped -> { /* vars should be looked up via symbol table */ }
                is PtAlign -> TODO("ir support for inline %align")
                is PtSub -> {
                    val sub = IRSubroutine(child.name, translate(child.parameters), child.returntype, child.position)
                    for (subchild in child.children) {
                        translateNode(subchild).forEach { sub += it }
                    }
                    irBlock += sub
                }
                is PtAsmSub -> {
                    if(child.address!=null) {
                        // extmsub. No codegen needed: calls to this are jumping straight to the address.
                        require(child.children.isEmpty()) {
                            "extsub should be empty at ${child.position}"
                        }
                    } else {
                        // regular asmsub
                        if(child.children.map { (it as PtInlineAssembly).isIR }.toSet().size>1)
                            errors.err("asmsub mixes IR and non-IR assembly code (could be compiler-generated)", child.position)
                        val asmblocks = child.children.map { (it as PtInlineAssembly).assembly.trimEnd() }
                        val assembly = asmblocks.joinToString("\n")
                        val asmChunk = IRInlineAsmChunk(
                            child.name, assembly, (child.children[0] as PtInlineAssembly).isIR , null
                        )
                        irBlock += IRAsmSubroutine(
                            child.name,
                            null,
                            child.clobbers,
                            child.parameters.map { IRAsmSubroutine.IRAsmParam(it.first, it.second.type) },        // note: the name of the asmsub param is not used here anymore
                            child.returns.map { IRAsmSubroutine.IRAsmParam(it.first, it.second)},
                            asmChunk,
                            child.position
                        )
                    }
                }
                is PtInlineAssembly -> {
                    irBlock += IRInlineAsmChunk(null, child.assembly, child.isIR, null)
                }
                is PtIncludeBinary -> {
                    irBlock += IRInlineBinaryChunk(null, readBinaryData(child), null)
                }
                is PtLabel -> {
                    irBlock += IRCodeChunk(child.name, null)
                }
                else -> TODO("weird block child node $child")
            }
        }
        return irBlock
    }

    private fun translate(parameters: List<PtSubroutineParameter>): List<IRSubroutine.IRParam> {
        val result = mutableListOf<IRSubroutine.IRParam>()
        parameters.forEach {
            if(it.register==null) {
                val flattenedName = it.definingISub()!!.name + "." + it.name
                if (symbolTable.lookup(flattenedName) == null)
                    TODO("fix missing lookup for: $flattenedName   parameter")
                val orig = symbolTable.lookup(flattenedName) as StStaticVariable
                result += IRSubroutine.IRParam(flattenedName, orig.dt)
            } else {
                val reg = it.register
                require(reg in Cx16VirtualRegisters) { "can only use R0-R15 'registers' here" }
                val regname = it.register!!.asScopedNameVirtualReg(it.type).joinToString(".")
                val targetVar = symbolTable.lookup(regname) as StMemVar
                result += IRSubroutine.IRParam(regname, targetVar.dt)
            }
        }
        return result
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): String {
        labelSequenceNumber++
        return "${PtLabel.GENERATED_LABEL_PREFIX}$labelSequenceNumber"
    }

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall): ExpressionCodeResult
        = builtinFuncGen.translate(call)

    internal fun isZero(expression: PtExpression): Boolean = (expression as? PtNumber)?.number==0.0 || (expression as? PtBool)?.value==false

    internal fun isOne(expression: PtExpression): Boolean = (expression as? PtNumber)?.number==1.0 || (expression as? PtBool)?.value==true

    fun makeSyscall(syscall: IMSyscall, params: List<Pair<IRDataType, Int>>, returns: Pair<IRDataType, Int>?, label: String?=null): IRCodeChunk {
        return IRCodeChunk(label, null).also {
            val args = params.map { (dt, reg)->
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(dt, reg, null))
            }
            // for now, syscalls have 0 or 1 return value
            val returnSpec = if(returns==null) emptyList() else listOf(FunctionCallArgs.RegSpec(returns.first, returns.second, null))
            it += IRInstruction(Opcode.SYSCALL, immediate = syscall.number, fcallArgs = FunctionCallArgs(args, returnSpec))
        }
    }
}
