package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*
import kotlin.io.path.readBytes
import kotlin.math.pow


class IRCodeGen(
    internal val program: PtProgram,
    internal val symbolTable: SymbolTable,
    internal val options: CompilationOptions,
    internal val errors: IErrorReporter
) {

    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    private val assignmentGen = AssignmentGen(this, expressionEval)
    private var irSymbolTable: IRSymbolTable = IRSymbolTable(null)
    internal val registers = RegisterPool()

    fun generate(): IRProgram {
        makeAllNodenamesScoped()
        moveAllNestedSubroutinesToBlockScope()
        verifyNameScoping(program, symbolTable)

        irSymbolTable = IRSymbolTable(symbolTable)
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

        val regOptimizer = IRRegisterOptimizer(irProg)
        regOptimizer.optimize()

        return irProg
    }

    private fun verifyNameScoping(program: PtProgram, symbolTable: SymbolTable) {
        fun verifyPtNode(node: PtNode) {
            when (node) {
                is PtBuiltinFunctionCall -> require('.' !in node.name) { "builtin function call name should not be scoped: ${node.name}" }
                is PtFunctionCall -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtIdentifier -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtAsmSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtBlock -> require('.' !in node.name) { "block name should not be scoped: ${node.name}" }
                is PtConstant -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtLabel -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtMemMapped -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtVariable -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtProgram -> require('.' !in node.name) { "program name should not be scoped: ${node.name}" }
                is PtSubroutineParameter -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                else -> { /* node has no name */
                }
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
                            else -> throw AssemblyError("invalid chunk")
                        }
                        sub.chunks.removeAt(0)
                        sub.chunks.add(0, replacement)
                    } else if(first.label != sub.label) {
                        val next = if(first is IRCodeChunk) first else null
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
                        val symbol: String
                        val index: UInt
                        if('+' in symbolExpr) {
                            val operands = symbolExpr.split('+', )
                            symbol = operands[0]
                            index = operands[1].toUInt()
                        } else {
                            symbol = symbolExpr
                            index = 0u
                        }
                        val target = symbolTable.flat[symbol]
                        if (target is StMemVar) {
                            replacements.add(Triple(chunk, idx, target.address+index))
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

    private fun makeAllNodenamesScoped() {
        val renames = mutableListOf<Pair<PtNamedNode, String>>()
        fun recurse(node: PtNode) {
            node.children.forEach {
                if(it is PtNamedNode)
                    renames.add(it to it.scopedName)
                recurse(it)
            }
        }
        recurse(program)
        renames.forEach { it.first.name = it.second }
    }

    private fun moveAllNestedSubroutinesToBlockScope() {
        val movedSubs = mutableListOf<Pair<PtBlock, PtSub>>()
        val removedSubs = mutableListOf<Pair<PtSub, PtSub>>()

        fun moveToBlock(block: PtBlock, parent: PtSub, asmsub: PtAsmSub) {
            block.add(asmsub)
            parent.children.remove(asmsub)
        }

        fun moveToBlock(block: PtBlock, parent: PtSub, sub: PtSub) {
            sub.children.filterIsInstance<PtSub>().forEach { subsub -> moveToBlock(block, sub, subsub) }
            sub.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub -> moveToBlock(block, sub, asmsubsub) }
            movedSubs += Pair(block, sub)
            removedSubs += Pair(parent, sub)
        }

        program.allBlocks().forEach { block ->
            block.children.toList().forEach {
                if (it is PtSub) {
                    // Only regular subroutines can have nested subroutines.
                    it.children.filterIsInstance<PtSub>().forEach { subsub -> moveToBlock(block, it, subsub) }
                    it.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub -> moveToBlock(block, it, asmsubsub) }
                }
            }
        }

        removedSubs.forEach { (parent, sub) -> parent.children.remove(sub) }
        movedSubs.forEach { (block, sub) -> block.add(sub) }
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
            is PtPostIncrDecr -> translate(node)
            is PtRepeatLoop -> translate(node)
            is PtLabel -> listOf(IRCodeChunk(node.name, null))
            is PtBreakpoint -> {
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstruction(Opcode.BREAKPOINT)
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
            is PtArray,
            is PtBlock,
            is PtString -> throw AssemblyError("should not occur as separate statement node ${node.position}")
            is PtSub -> throw AssemblyError("nested subroutines should have been flattened ${node.position}")
            else -> TODO("missing codegen for $node")
        }

        chunks.forEach { chunk ->
            require(chunk.isNotEmpty() || chunk.label != null) {
                "chunk should have instructions and/or a label"
            }
        }

        chunks.filterIsInstance<IRCodeChunk>().firstOrNull()?.appendSrcPosition(node.position)

        return chunks
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
        if(goto is PtJump && branch.falseScope.children.isEmpty()) {
            // special case the form:   if_cc <condition> goto <place>
            val address = goto.address?.toInt()
            if(address!=null) {
                val branchIns = when(branch.condition) {
                    BranchCondition.CS -> IRInstruction(Opcode.BSTCS, address = address)
                    BranchCondition.CC -> IRInstruction(Opcode.BSTCC, address = address)
                    BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTEQ, address = address)
                    BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTNE, address = address)
                    BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTNEG, address = address)
                    BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTPOS, address = address)
                    BranchCondition.VC -> IRInstruction(Opcode.BSTVC, address = address)
                    BranchCondition.VS -> IRInstruction(Opcode.BSTVS, address = address)
                }
                addInstr(result, branchIns, null)
            } else {
                val label = if(goto.generatedLabel!=null) goto.generatedLabel else goto.identifier!!.name
                val branchIns = when(branch.condition) {
                    BranchCondition.CS -> IRInstruction(Opcode.BSTCS, labelSymbol = label)
                    BranchCondition.CC -> IRInstruction(Opcode.BSTCC, labelSymbol = label)
                    BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTEQ, labelSymbol = label)
                    BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTNE, labelSymbol = label)
                    BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTNEG, labelSymbol = label)
                    BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTPOS, labelSymbol = label)
                    BranchCondition.VC -> IRInstruction(Opcode.BSTVC, labelSymbol = label)
                    BranchCondition.VS -> IRInstruction(Opcode.BSTVS, labelSymbol = label)
                }
                addInstr(result, branchIns, null)
            }
            return result
        }


        val elseLabel = createLabelName()
        // note that the branch opcode used is the opposite as the branch condition, because the generated code jumps to the 'else' part
        val branchIns = when(branch.condition) {
            BranchCondition.CS -> IRInstruction(Opcode.BSTCC, labelSymbol = elseLabel)
            BranchCondition.CC -> IRInstruction(Opcode.BSTCS, labelSymbol = elseLabel)
            BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTNE, labelSymbol = elseLabel)
            BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTEQ, labelSymbol = elseLabel)
            BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTPOS, labelSymbol = elseLabel)
            BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTNEG, labelSymbol = elseLabel)
            BranchCondition.VC -> IRInstruction(Opcode.BSTVS, labelSymbol = elseLabel)
            BranchCondition.VS -> IRInstruction(Opcode.BSTVC, labelSymbol = elseLabel)
        }
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
            else -> {
                throw AssemblyError("invalid chunk")
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
                if(iterable.from is PtNumber && iterable.to is PtNumber)
                    result += translateForInConstantRange(forLoop, loopvar)
                else
                    result += translateForInNonConstantRange(forLoop, loopvar)
            }
            is PtIdentifier -> {
                require(forLoop.variable.name == loopvar.scopedName)
                val iterableLength = symbolTable.getLength(iterable.name)
                val loopvarSymbol = forLoop.variable.name
                val indexReg = registers.nextFree()
                val tmpReg = registers.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                when (iterable.type) {
                    DataType.STR -> {
                        // iterate over a zero-terminated string
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1 = tmpReg, reg2 = indexReg, labelSymbol = iterable.name)
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = tmpReg, immediate = 0)
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
                    in SplitWordArrayTypes -> {
                        // iterate over lsb/msb split word array
                        val elementDt = ArrayToElementTypes.getValue(iterable.type)
                        if(elementDt !in WordDatatypes)
                            throw AssemblyError("weird dt")
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            val tmpRegLsb = registers.nextFree()
                            val tmpRegMsb = registers.nextFree()
                            val concatReg = registers.nextFree()
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegLsb, reg2=indexReg, immediate = iterableLength, labelSymbol=iterable.name+"_lsb")
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegMsb, reg2=indexReg, immediate = iterableLength, labelSymbol=iterable.name+"_msb")
                            it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=concatReg, reg2=tmpRegLsb, reg3=tmpRegMsb)
                            it += IRInstruction(Opcode.STOREM, irType(elementDt), reg1=concatReg, labelSymbol = loopvarSymbol)
                        }
                        result += translateNode(forLoop.statements)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexReg)
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=indexReg, immediate = if(iterableLength==256) 0 else iterableLength)
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
                        }
                    }
                    else -> {
                        // iterate over regular array
                        val elementDt = ArrayToElementTypes.getValue(iterable.type)
                        val elementSize = program.memsizer.memorySize(elementDt)
                        val lengthBytes = iterableLength!! * elementSize
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, immediate = 0), null)
                        result += IRCodeChunk(loopLabel, null).also {
                            it += IRInstruction(Opcode.LOADX, irType(elementDt), reg1=tmpReg, reg2=indexReg, labelSymbol=iterable.name)
                            it += IRInstruction(Opcode.STOREM, irType(elementDt), reg1=tmpReg, labelSymbol = loopvarSymbol)
                        }
                        result += translateNode(forLoop.statements)
                        result += addConstReg(IRDataType.BYTE, indexReg, elementSize)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=indexReg, immediate = if(lengthBytes==256) 0 else lengthBytes)
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

        val toTr = expressionEval.translateExpression(iterable.to)
        addToResult(result, toTr, toTr.resultReg, -1)
        val fromTr = expressionEval.translateExpression(iterable.from)
        addToResult(result, fromTr, fromTr.resultReg, -1)

        val labelAfterFor = createLabelName()
        val precheckInstruction = if(loopvarDt in SignedDatatypes) {
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
            addInstr(result, IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = fromTr.resultReg, labelSymbol = loopvarSymbol), null)
            addInstr(result, IRInstruction(Opcode.BEQR, loopvarDtIr, reg1=toTr.resultReg, reg2=fromTr.resultReg, labelSymbol = labelAfterFor), null)
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
        chunk2 += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = indexReg, labelSymbol = loopvarSymbol)
        chunk2 += IRInstruction(Opcode.CMPI, loopvarDtIr, reg1 = indexReg, immediate = rangeEndExclusiveWrapped)
        chunk2 += IRInstruction(Opcode.BSTNE, labelSymbol = loopLabel)
        result += chunk2
        return result
    }

    private fun addConstReg(dt: IRDataType, reg: Int, value: Int): IRCodeChunk {
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

    internal fun multiplyByConstFloat(fpReg: Int, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        code += if(factor==0f) {
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = 0f)
        } else {
            IRInstruction(Opcode.MUL, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        if(factor==0f) {
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

    internal val powersOfTwo = (0..16).map { 2.0.pow(it.toDouble()).toInt() }

    internal fun multiplyByConst(dt: IRDataType, reg: Int, factor: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
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
        val pow2 = powersOfTwo.indexOf(factor)
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

    internal fun divideByConstFloat(fpReg: Int, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        code += if(factor==0f) {
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = Float.MAX_VALUE)
        } else {
            IRInstruction(Opcode.DIVS, IRDataType.FLOAT, fpReg1 = fpReg, immediateFp = factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        if(factor==0f) {
            val maxvalueReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = maxvalueReg, immediateFp = Float.MAX_VALUE)
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
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1 && !signed) {
            code += IRInstruction(Opcode.LSR, dt, reg1=reg)     // simple single bit shift
        }
        else if(pow2>=1 &&!signed) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
            code += if(signed)
                IRInstruction(Opcode.ASRN, dt, reg1=reg, reg2=pow2reg)
            else
                IRInstruction(Opcode.LSRN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRInstruction(Opcode.LOAD, dt, reg1=reg, immediate = 0xffff)
            } else {
                if(signed)
                    IRInstruction(Opcode.DIVS, dt, reg1=reg, immediate = factor)
                else
                    IRInstruction(Opcode.DIV, dt, reg1=reg, immediate = factor)
            }
        }
        return code
    }

    internal fun divideByConstInplace(dt: IRDataType, knownAddress: Int?, symbol: String?, factor: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1 && !signed) {
            // just simple bit shift
            code += if(knownAddress!=null)
                IRInstruction(Opcode.LSRM, dt, address = knownAddress)
            else
                IRInstruction(Opcode.LSRM, dt, labelSymbol = symbol)
        }
        else if(pow2>=1 && !signed) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, immediate = pow2)
            code += if(signed) {
                if(knownAddress!=null)
                    IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, address = knownAddress)
                else
                    IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
            }
            else {
                if(knownAddress!=null)
                    IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, address = knownAddress)
                else
                    IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
            }
        } else {
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
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): IRCodeChunks {
        val condition = ifElse.condition
        val goto = ifElse.ifScope.children.firstOrNull() as? PtJump
        when (condition) {
            is PtBinaryExpression -> {
                if(condition.operator !in ComparisonOperators)
                    throw AssemblyError("if condition should only be a binary comparison expression")

                val signed = condition.left.type in SignedDatatypes
                val irDtLeft = irType(condition.left.type)
                return when {
                    goto!=null && ifElse.elseScope.children.isEmpty() -> translateIfFollowedByJustGoto(ifElse, goto, irDtLeft, signed)
                    constValue(condition.right) == 0.0 -> translateIfElseZeroComparison(ifElse, irDtLeft, signed)
                    else -> translateIfElseNonZeroComparison(ifElse, irDtLeft, signed)
                }
            }
            else -> {
                // if X   --> meaning:  if X!=0
                val irDt = irType(condition.type)
                val signed = condition.type in SignedDatatypes
                return if(goto!=null && ifElse.elseScope.children.isEmpty()) {
                    translateIfFollowedByJustGoto(ifElse, goto, irDt, signed)
                } else {
                    translateIfElseNonZeroComparison(ifElse, irDt, signed)
                }
            }
        }
    }


    private fun translateIfFollowedByJustGoto(ifElse: PtIfElse, goto: PtJump, irDtLeft: IRDataType, signed: Boolean): MutableList<IRCodeChunkBase> {
        val condition = ifElse.condition as? PtBinaryExpression
        val result = mutableListOf<IRCodeChunkBase>()
        if(condition==null) {
            if(irDtLeft==IRDataType.FLOAT)
                throw AssemblyError("condition value should not be float")
            ifNonZeroIntThenJump(result, ifElse, signed, irDtLeft, goto)
            return result
        } else {
            if (irDtLeft == IRDataType.FLOAT) {
                val leftTr = expressionEval.translateExpression(condition.left)
                addToResult(result, leftTr, -1, leftTr.resultFpReg)
                val rightTr = expressionEval.translateExpression(condition.right)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                result += IRCodeChunk(null, null).also {
                    val compResultReg = registers.nextFree()
                    it += IRInstruction(
                        Opcode.FCOMP,
                        IRDataType.FLOAT,
                        reg1 = compResultReg,
                        fpReg1 = leftTr.resultFpReg,
                        fpReg2 = rightTr.resultFpReg
                    )
                    when(condition.operator) {
                        // TODO: the converted list of operators
                        "==" -> {
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                            it += branchInstr(goto, Opcode.BSTEQ)
                        }
                        "!=" -> {
                            it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1 = compResultReg, immediate = 0)
                            it += branchInstr(goto, Opcode.BSTNE)
                        }
                        else -> {
                            // TODO: the old list of operators, still to be converted
                            val gotoOpcode = when (condition.operator) {
                                "<" -> Opcode.BLTS
                                ">" -> Opcode.BGTS
                                "<=" -> Opcode.BLES
                                ">=" -> Opcode.BGES
                                else -> throw AssemblyError("weird operator")
                            }
                            it += if (goto.address != null)
                                IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, address = goto.address?.toInt())
                            else if (goto.generatedLabel != null)
                                IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = goto.generatedLabel)
                            else
                                IRInstruction(gotoOpcode, IRDataType.BYTE, reg1 = compResultReg, immediate = 0, labelSymbol = goto.identifier!!.name)
                        }
                    }
                }
                return result
            } else {
                val rightConst = condition.right.asConstInteger()
                if (rightConst == 0)
                    ifZeroIntThenJump(result, ifElse, signed, irDtLeft, goto)
                else {
                    ifNonZeroIntThenJump(result, ifElse, signed, irDtLeft, goto)
                }
                return result
            }
        }
    }

    // TODO use this everywhere
    private fun branchInstr(goto: PtJump, branchOpcode: Opcode) = if (goto.address != null)
        IRInstruction(branchOpcode, address = goto.address?.toInt())
    else if (goto.generatedLabel != null)
        IRInstruction(branchOpcode, labelSymbol = goto.generatedLabel)
    else
        IRInstruction(branchOpcode, labelSymbol = goto.identifier!!.name)

    private fun ifZeroIntThenJump(
        result: MutableList<IRCodeChunkBase>,
        ifElse: PtIfElse,
        signed: Boolean,
        irDtLeft: IRDataType,
        goto: PtJump
    ) {
        val condition = ifElse.condition as PtBinaryExpression
        val leftTr = expressionEval.translateExpression(condition.left)
        addToResult(result, leftTr, leftTr.resultReg, -1)
        when(condition.operator) {
            // TODO: converted list of operators
            "==" -> {
                addInstr(result, IRInstruction(Opcode.CMPI, irDtLeft, reg1 = leftTr.resultReg, immediate = 0), null)
                addInstr(result, branchInstr(goto, Opcode.BSTEQ), null)
            }
            "!=" -> {
                addInstr(result, IRInstruction(Opcode.CMPI, irDtLeft, reg1 = leftTr.resultReg, immediate = 0), null)
                addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
            }
            else -> {
                // TODO: to-be converted operators
                val opcode = when (condition.operator) {
                    "<" -> if (signed) Opcode.BLTS else Opcode.BLT
                    ">" -> if (signed) Opcode.BGTS else Opcode.BGT
                    "<=" -> if (signed) Opcode.BLES else Opcode.BLE
                    ">=" -> if (signed) Opcode.BGES else Opcode.BGE
                    else -> throw AssemblyError("invalid comparison operator")
                }
                if (goto.address != null)
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = leftTr.resultReg, immediate = 0, address = goto.address?.toInt()), null)
                else if (goto.generatedLabel != null)
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = leftTr.resultReg, immediate = 0, labelSymbol = goto.generatedLabel), null)
                else
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = leftTr.resultReg, immediate = 0, labelSymbol = goto.identifier!!.name), null)
            }
        }
    }

    private fun ifNonZeroIntThenJump(
        result: MutableList<IRCodeChunkBase>,
        ifElse: PtIfElse,
        signed: Boolean,
        irDtLeft: IRDataType,
        goto: PtJump
    ) {
        val condition = ifElse.condition as? PtBinaryExpression
        if(condition==null) {
            val tr = expressionEval.translateExpression(ifElse.condition)
            result += tr.chunks
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.CMPI, irDtLeft, reg1 = tr.resultReg, immediate = 0)
                it += branchInstr(goto, Opcode.BSTNE)
            }
        } else {
            val leftTr = expressionEval.translateExpression(condition.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val number = (condition.right as? PtNumber)?.number?.toInt()
            if(number!=null) {
                val firstReg = leftTr.resultReg
                when(condition.operator) {
                    // TODO: the converted operators
                    "==" -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, irDtLeft, reg1 = firstReg, immediate = number), null)
                        addInstr(result, branchInstr(goto, Opcode.BSTEQ), null)
                    }
                    "!=" -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, irDtLeft, reg1 = firstReg, immediate = number), null)
                        addInstr(result, branchInstr(goto, Opcode.BSTNE), null)
                    }
                    else -> {
                        // TODO: to-be converted operators
                        val opcode = when (condition.operator) {
                            "<" -> if(signed) Opcode.BLTS else Opcode.BLT
                            ">" -> if(signed) Opcode.BGTS else Opcode.BGT
                            "<=" -> if(signed) Opcode.BLES else Opcode.BLE
                            ">=" -> if(signed) Opcode.BGES else Opcode.BGE
                            else -> throw AssemblyError("invalid comparison operator")
                        }
                        if (goto.address != null)
                            addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, immediate = number, address = goto.address?.toInt()), null)
                        else if (goto.generatedLabel != null)
                            addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, immediate = number, labelSymbol = goto.generatedLabel), null)
                        else
                            addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, immediate = number, labelSymbol = goto.identifier!!.name), null)
                    }
                }
            } else {
                val rightTr = expressionEval.translateExpression(condition.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val firstReg: Int
                val secondReg: Int
                val opcode: Opcode
                when (condition.operator) {
                    "==" -> {
                        opcode = Opcode.BEQR
                        firstReg = leftTr.resultReg
                        secondReg = rightTr.resultReg
                    }
                    "!=" -> {
                        opcode = Opcode.BNER
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
                if (goto.address != null)
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, reg2 = secondReg, address = goto.address?.toInt()), null)
                else if (goto.generatedLabel != null)
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, reg2 = secondReg, labelSymbol = goto.generatedLabel), null)
                else
                    addInstr(result, IRInstruction(opcode, irDtLeft, reg1 = firstReg, reg2 = secondReg, labelSymbol = goto.identifier!!.name), null)
            }
        }
    }

    private fun translateIfElseZeroComparison(ifElse: PtIfElse, irDtLeft: IRDataType, signed: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val elseBranch: Opcode
        var useCmpi = false     // for the branch opcodes that have been converted to CMPI + BSTxx form already
        val compResultReg: Int
        val branchDt: IRDataType
        val condition = ifElse.condition as PtBinaryExpression
        if(irDtLeft==IRDataType.FLOAT) {
            branchDt = IRDataType.BYTE
            compResultReg = registers.nextFree()
            val leftTr = expressionEval.translateExpression(condition.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            result += IRCodeChunk(null, null).also {
                val rightFpReg = registers.nextFreeFloat()
                it += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = rightFpReg, immediateFp = 0f)
                it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=compResultReg, fpReg1 = leftTr.resultFpReg, fpReg2 = rightFpReg)
            }
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
        } else {
            // integer comparisons
            branchDt = irDtLeft
            val tr = expressionEval.translateExpression(condition.left)
            compResultReg = tr.resultReg
            addToResult(result, tr, tr.resultReg, -1)
            when (condition.operator) {
                "==" -> {
                    elseBranch = Opcode.BSTNE
                    useCmpi = true
                }
                "!=" -> {
                    elseBranch = Opcode.BSTEQ
                    useCmpi = true
                }
                "<" -> elseBranch = if (signed) Opcode.BGES else Opcode.BGE
                ">" -> elseBranch = if (signed) Opcode.BLES else Opcode.BLE
                "<=" -> elseBranch = if (signed) Opcode.BGTS else Opcode.BGT
                ">=" -> elseBranch = if (signed) Opcode.BLTS else Opcode.BLT
                else -> throw AssemblyError("weird operator")
            }
        }

        if(ifElse.elseScope.children.isEmpty()) {
            // just if
            val afterIfLabel = createLabelName()
            if(useCmpi) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.CMPI, branchDt, reg1=compResultReg, immediate = 0)
                    it += IRInstruction(elseBranch, labelSymbol = afterIfLabel)
                }
            } else
                addInstr(result, IRInstruction(elseBranch, branchDt, reg1=compResultReg, immediate = 0, labelSymbol = afterIfLabel), null)
            result += translateNode(ifElse.ifScope)
            result += IRCodeChunk(afterIfLabel, null)
        } else {
            // if and else
            val elseLabel = createLabelName()
            val afterIfLabel = createLabelName()
            if(useCmpi) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.CMPI, branchDt, reg1=compResultReg, immediate = 0)
                    it += IRInstruction(elseBranch, labelSymbol = elseLabel)
                }
            } else
                addInstr(result, IRInstruction(elseBranch, branchDt, reg1=compResultReg, immediate = 0, labelSymbol = elseLabel), null)
            result += translateNode(ifElse.ifScope)
            addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
            result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
            result += IRCodeChunk(afterIfLabel, null)
        }
        return result
    }

    private fun translateIfElseNonZeroComparison(ifElse: PtIfElse, irDtLeft: IRDataType, signed: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val elseBranchFirstReg: Int
        val elseBranchSecondReg: Int
        val branchDt: IRDataType
        val condition = ifElse.condition as? PtBinaryExpression
        if(condition==null) {
            throw AssemblyError("if-else condition is not a binaryexpression, should have been converted?")
//            if(irDtLeft==IRDataType.FLOAT)
//                throw AssemblyError("condition value should not be float")
//            val tr = expressionEval.translateExpression(ifElse.condition)
//            result += tr.chunks
//            if(ifElse.elseScope.children.isNotEmpty()) {
//                val elseLabel = createLabelName()
//                result += IRCodeChunk(null, null).also {
//                    it += IRInstruction(Opcode.CMPI, irDtLeft, reg1=tr.resultReg, immediate = 0)
//                    it += IRInstruction(Opcode.BSTEQ, labelSymbol = elseLabel)
//                    TODO("test this")
//                }
//                result += translateNode(ifElse.ifScope)
//                val afterIfLabel = createLabelName()
//                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
//                result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
//                result += IRCodeChunk(afterIfLabel, null)
//            } else {
//                val afterIfLabel = createLabelName()
//                result += IRCodeChunk(null, null).also {
//                    it += IRInstruction(Opcode.CMPI, irDtLeft, reg1=tr.resultReg, immediate = 0)
//                    it += IRInstruction(Opcode.BSTEQ, labelSymbol = afterIfLabel)
//                    TODO("test this")
//                }
//                result += translateNode(ifElse.ifScope)
//                result += IRCodeChunk(afterIfLabel, null)
//            }
//            return result
        } else {
            if (irDtLeft == IRDataType.FLOAT) {
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
                if (ifElse.elseScope.children.isNotEmpty()) {
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
            } else {
                // integer comparisons
                branchDt = irDtLeft
                val leftTr = expressionEval.translateExpression(condition.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val number = (condition.right as? PtNumber)?.number?.toInt()
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
                    if (ifElse.elseScope.children.isNotEmpty()) {
                        // if and else parts
                        val elseLabel = createLabelName()
                        val afterIfLabel = createLabelName()
                        if(useCmpi) {
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.CMPI, branchDt, reg1 = leftTr.resultReg, immediate = number)
                                it += IRInstruction(elseBranch, labelSymbol = elseLabel)
                            }
                        } else
                            addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = leftTr.resultReg, immediate = number, labelSymbol = elseLabel), null)
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
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    when (condition.operator) {
                        "==" -> {
                            elseBranch = Opcode.BNER
                            elseBranchFirstReg = leftTr.resultReg
                            elseBranchSecondReg = rightTr.resultReg
                        }
                        "!=" -> {
                            elseBranch = Opcode.BEQR
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
                    if (ifElse.elseScope.children.isNotEmpty()) {
                        // if and else parts
                        val elseLabel = createLabelName()
                        val afterIfLabel = createLabelName()
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg, labelSymbol = elseLabel), null)
                        result += translateNode(ifElse.ifScope)
                        addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                        result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                        result += IRCodeChunk(afterIfLabel, null)
                    } else {
                        // only if part
                        val afterIfLabel = createLabelName()
                        addInstr(result, IRInstruction(elseBranch, branchDt, reg1 = elseBranchFirstReg, reg2 = elseBranchSecondReg, labelSymbol = afterIfLabel), null)
                        result += translateNode(ifElse.ifScope)
                        result += IRCodeChunk(afterIfLabel, null)
                    }
                }
            }
            return result
        }
    }

    private fun translate(postIncrDecr: PtPostIncrDecr): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val array = postIncrDecr.target.array
        if(array?.splitWords==true) {
            val variable = array.variable.name
            val fixedIndex = constIntValue(array.index)
            if(fixedIndex!=null) {
                val skipLabel = createLabelName()
                when(postIncrDecr.operator) {
                    "++" -> {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = "${variable}_lsb+$fixedIndex")
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = skipLabel)
                            it += IRInstruction(Opcode.INCM, IRDataType.BYTE, labelSymbol = "${variable}_msb+$fixedIndex")
                        }
                        result += IRCodeChunk(skipLabel, null)
                    }
                    "--" -> {
                        val valueReg=registers.nextFree()
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=valueReg, labelSymbol = "${variable}_lsb+$fixedIndex")
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = skipLabel)
                            it += IRInstruction(Opcode.DECM, IRDataType.BYTE, labelSymbol = "${variable}_msb+$fixedIndex")
                        }
                        result += IRCodeChunk(skipLabel, null).also {
                            it += IRInstruction(Opcode.DECM, IRDataType.BYTE, labelSymbol = "${variable}_lsb+$fixedIndex")
                        }
                    }
                    else -> throw AssemblyError("weird operator")
                }
            } else {
                val arrayLength = symbolTable.getLength(array.variable.name)
                val indexTr = expressionEval.translateExpression(array.index)
                addToResult(result, indexTr, indexTr.resultReg, -1)
                val incReg = registers.nextFree()
                val skipLabel = createLabelName()
                when(postIncrDecr.operator) {
                    "++" -> {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                            it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=incReg)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = skipLabel)
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                            it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=incReg)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                        }
                        result += IRCodeChunk(skipLabel, null)
                    }
                    "--" -> {
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                            it += IRInstruction(Opcode.BSTNE, labelSymbol = skipLabel)
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                            it += IRInstruction(Opcode.DEC, IRDataType.BYTE, reg1=incReg)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_msb")
                        }
                        result += IRCodeChunk(skipLabel, null).also {
                            it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                            it += IRInstruction(Opcode.DEC, IRDataType.BYTE, reg1=incReg)
                            it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=incReg, reg2=indexTr.resultReg, immediate = arrayLength, labelSymbol = "${variable}_lsb")
                        }
                    }
                    else -> throw AssemblyError("weird operator")
                }
            }
        } else {
            val ident = postIncrDecr.target.identifier
            val memory = postIncrDecr.target.memory
            val irDt = irType(postIncrDecr.target.type)
            val operationMem: Opcode
            val operationRegister: Opcode
            when(postIncrDecr.operator) {
                "++" -> {
                    operationMem = Opcode.INCM
                    operationRegister = Opcode.INC
                }
                "--" -> {
                    operationMem = Opcode.DECM
                    operationRegister = Opcode.DEC
                }
                else -> throw AssemblyError("weird operator")
            }
            if(ident!=null) {
                addInstr(result, IRInstruction(operationMem, irDt, labelSymbol = ident.name), null)
            } else if(memory!=null) {
                if(memory.address is PtNumber) {
                    val address = (memory.address as PtNumber).number.toInt()
                    addInstr(result, IRInstruction(operationMem, irDt, address = address), null)
                } else {
                    val tr = expressionEval.translateExpression(memory.address)
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        val incReg = registers.nextFree()
                        it += IRInstruction(Opcode.LOADI, irDt, reg1 = incReg, reg2 = tr.resultReg)
                        it += IRInstruction(operationRegister, irDt, reg1 = incReg)
                        it += IRInstruction(Opcode.STOREI, irDt, reg1 = incReg, reg2 = tr.resultReg)
                    }
                }
            } else if (array!=null) {
                val variable = array.variable.name
                val itemsize = program.memsizer.memorySize(array.type)
                val fixedIndex = constIntValue(array.index)
                if(fixedIndex!=null) {
                    val offset = fixedIndex*itemsize
                    val indexReg = registers.nextFree()
                    val dataReg = registers.nextFree()
                    if(array.usesPointerVariable) {
                        // we don't have an indirect dec/inc so do it via an intermediate register
                        result += IRCodeChunk(null,null).also {
                            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = indexReg, immediate = offset)
                            it += IRInstruction(Opcode.LOADIX, irDt, reg1 = dataReg, reg2 = indexReg, labelSymbol = variable)
                            it += IRInstruction(operationRegister, irDt, reg1=dataReg)
                            it += IRInstruction(Opcode.STOREIX, irDt, reg1 = dataReg, reg2 = indexReg, labelSymbol = variable)
                        }
                    } else {
                        addInstr(result, IRInstruction(operationMem, irDt, labelSymbol = "$variable+$offset"), null)
                    }
                } else {
                    val indexTr = expressionEval.translateExpression(array.index)
                    addToResult(result, indexTr, indexTr.resultReg, -1)
                    if(itemsize>1)
                        result += multiplyByConst(IRDataType.BYTE, indexTr.resultReg, itemsize)
                    result += IRCodeChunk(null, null).also {
                        val incReg = registers.nextFree()
                        if(array.usesPointerVariable) {
                            it += IRInstruction(Opcode.LOADIX, irDt, reg1=incReg, reg2=indexTr.resultReg, labelSymbol=variable)
                            it += IRInstruction(operationRegister, irDt, reg1=incReg)
                            it += IRInstruction(Opcode.STOREIX, irDt, reg1=incReg, reg2=indexTr.resultReg, labelSymbol=variable)
                        } else {
                            it += IRInstruction(Opcode.LOADX, irDt, reg1=incReg, reg2=indexTr.resultReg, labelSymbol=variable)
                            it += IRInstruction(operationRegister, irDt, reg1=incReg)
                            it += IRInstruction(Opcode.STOREX, irDt, reg1=incReg, reg2=indexTr.resultReg, labelSymbol=variable)
                        }
                    }
                }
            } else
                throw AssemblyError("weird assigntarget")
        }

        return result
    }

    private fun translate(repeat: PtRepeatLoop): IRCodeChunks {
        when (constIntValue(repeat.count)) {
            0 -> return emptyList()
            1 -> return translateGroup(repeat.children)
            256 -> {
                // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
                repeat.children[0] = PtNumber(DataType.UBYTE, 0.0, repeat.count.position)
            }
        }

        val repeatLabel = createLabelName()
        val skipRepeatLabel = createLabelName()
        val irDt = irType(repeat.count.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val countTr = expressionEval.translateExpression(repeat.count)
        addToResult(result, countTr, countTr.resultReg, -1)
        if(constIntValue(repeat.count)==null) {
            // check if the counter is already zero
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.CMPI, irDt, reg1=countTr.resultReg, immediate = 0)
                it += IRInstruction(Opcode.BSTEQ, labelSymbol = skipRepeatLabel)
            }
        }
        result += labelFirstChunk(translateNode(repeat.statements), repeatLabel)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.DEC, irDt, reg1 = countTr.resultReg)  // sets status bits
            it += IRInstruction(Opcode.BSTNE, labelSymbol = repeatLabel)
        }
        result += IRCodeChunk(skipRepeatLabel, null)
        return result
    }

    private fun translate(jump: PtJump): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val chunk = IRCodeChunk(null, null)
        if(jump.address!=null) {
            chunk += IRInstruction(Opcode.JUMP, address = jump.address!!.toInt())
        } else {
            if (jump.generatedLabel != null)
                chunk += IRInstruction(Opcode.JUMP, labelSymbol = jump.generatedLabel!!)
            else if (jump.identifier != null) {
                val symbol = symbolTable.lookup(jump.identifier!!.name)
                if(symbol?.type==StNodeType.MEMVAR || symbol?.type==StNodeType.STATICVAR) {
                    val jumpReg = registers.nextFree()
                    chunk += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = jumpReg, labelSymbol = jump.identifier!!.name)
                    chunk += IRInstruction(Opcode.JUMPI, reg1 = jumpReg)
                } else {
                    chunk += IRInstruction(Opcode.JUMP, labelSymbol = jump.identifier!!.name)
                }
            }
            else
                throw AssemblyError("weird jump")
        }
        result += chunk
        return result
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
            if(value.type==DataType.FLOAT) {
                val tr = expressionEval.translateExpression(value)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.RETURNR, IRDataType.FLOAT, fpReg1 = tr.resultFpReg), null)
            }
            else {
                val tr = expressionEval.translateExpression(value)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.RETURNR, irType(value.type) , reg1=tr.resultReg), null)
            }
        }
        return result
    }

    private fun translate(block: PtBlock): IRBlock {
        val irBlock = IRBlock(block.name, block.address, block.library, block.forceOutput, translate(block.alignment), block.position)   // no use for other attributes yet?
        for (child in block.children) {
            when(child) {
                is PtNop -> { /* nothing */ }
                is PtAssignment, is PtAugmentedAssign -> { /* global variable initialization is done elsewhere */ }
                is PtVariable, is PtConstant, is PtMemMapped -> { /* vars should be looked up via symbol table */ }
                is PtSub -> {
                    val sub = IRSubroutine(child.name, translate(child.parameters), child.returntype, child.position)
                    for (subchild in child.children) {
                        translateNode(subchild).forEach { sub += it }
                    }
                    irBlock += sub
                }
                is PtAsmSub -> {
                    if(child.address!=null) {
                        // romsub. No codegen needed: calls to this are jumping straight to the address.
                        require(child.children.isEmpty()) {
                            "romsub should be empty at ${child.position}"
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
                            child.address,
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
                else -> TODO("weird child node $child")
            }
        }
        return irBlock
    }

    private fun translate(parameters: List<PtSubroutineParameter>) =
        parameters.map {
            val flattenedName = it.definingSub()!!.name + "." + it.name
            val orig = symbolTable.lookup(flattenedName) as StStaticVariable
            IRSubroutine.IRParam(flattenedName, orig.dt)
        }

    private fun translate(alignment: PtBlock.BlockAlignment): IRBlock.BlockAlignment {
        return when(alignment) {
            PtBlock.BlockAlignment.NONE -> IRBlock.BlockAlignment.NONE
            PtBlock.BlockAlignment.WORD -> IRBlock.BlockAlignment.WORD
            PtBlock.BlockAlignment.PAGE -> IRBlock.BlockAlignment.PAGE
        }
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): String {
        labelSequenceNumber++
        return "prog8_label_gen_$labelSequenceNumber"
    }

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall): ExpressionCodeResult
        = builtinFuncGen.translate(call)

    internal fun isZero(expression: PtExpression): Boolean = expression is PtNumber && expression.number==0.0

    internal fun isOne(expression: PtExpression): Boolean = expression is PtNumber && expression.number==1.0

    fun makeSyscall(syscall: IMSyscall, params: List<Pair<IRDataType, Int>>, returns: Pair<IRDataType, Int>?, label: String?=null): IRCodeChunk {
        return IRCodeChunk(label, null).also {
            val args = params.map { (dt, reg)->
                FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(dt, reg, null))
            }
            val returnSpec = if(returns==null) null else FunctionCallArgs.RegSpec(returns.first, returns.second, null)
            it += IRInstruction(Opcode.SYSCALL, immediate = syscall.number, fcallArgs = FunctionCallArgs(args, returnSpec))
        }
    }
}
