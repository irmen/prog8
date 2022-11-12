package prog8.codegen.intermediate

import prog8.code.StMemVar
import prog8.code.StNode
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
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
    internal val registers = RegisterPool()

    fun generate(): IRProgram {
        flattenLabelNames()
        flattenNestedSubroutines()

        val irProg = IRProgram(program.name, IRSymbolTable(symbolTable), options, program.encoding)

        if(options.evalStackBaseAddress!=null)
            throw AssemblyError("IR doesn't use eval-stack")

        if(!options.dontReinitGlobals) {
            // collect global variables initializers
            program.allBlocks().forEach {
                val result = mutableListOf<IRCodeChunkBase>()
                it.children.filterIsInstance<PtAssignment>().forEach { assign -> result += assignmentGen.translate(assign) }
                result.forEach { chunk ->
                    if (chunk is IRCodeChunk) irProg.addGlobalInits(chunk)
                    else throw AssemblyError("only expect code chunk for global inits")
                }
            }
        }

        irProg.addAsmSymbols(options.symbolDefs)

        for (block in program.allBlocks())
            irProg.addBlock(translate(block))

        replaceMemoryMappedVars(irProg)
        ensureFirstChunkLabels(irProg)
        irProg.linkChunks()

        if(options.optimize) {
            val optimizer = IRPeepholeOptimizer(irProg)
            optimizer.optimize()

            val remover = IRUnusedCodeRemover(irProg, errors)
            do {
                val numRemoved = remover.optimize()
            } while(numRemoved>0 && errors.noErrors())

            errors.report()

            irProg.linkChunks()  // re-link
        }

        irProg.validate()
        return irProg
    }

    private fun ensureFirstChunkLabels(irProg: IRProgram) {
        // make sure that first chunks in Blocks and Subroutines share the name of the block/sub as label.

        irProg.blocks.forEach { block ->
            if(block.inlineAssembly.isNotEmpty()) {
                val first = block.inlineAssembly.first()
                if(first.label==null) {
                    val replacement = IRInlineAsmChunk(block.name, first.assembly, first.isIR, first.next)
                    block.inlineAssembly.removeAt(0)
                    block.inlineAssembly.add(0, replacement)
                } else if(first.label != block.name) {
                    throw AssemblyError("first chunk in block has label that differs from block name")
                }
            }

            block.subroutines.forEach { sub ->
                if(sub.chunks.isNotEmpty()) {
                    val first = sub.chunks.first()
                    if(first.label==null) {
                        val replacement = when(first) {
                            is IRCodeChunk -> {
                                val replacement = IRCodeChunk(sub.name, first.next)
                                replacement.instructions += first.instructions
                                replacement
                            }
                            is IRInlineAsmChunk -> IRInlineAsmChunk(sub.name, first.assembly, first.isIR, first.next)
                            is IRInlineBinaryChunk -> IRInlineBinaryChunk(sub.name, first.data, first.next)
                            else -> throw AssemblyError("invalid chunk")
                        }
                        sub.chunks.removeAt(0)
                        sub.chunks.add(0, replacement)
                    } else if(first.label != sub.name) {
                        val next = if(first is IRCodeChunk) first else null
                        sub.chunks.add(0, IRCodeChunk(sub.name, next))
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
        irProg.blocks.asSequence().flatMap { it.subroutines }.flatMap { it.chunks }.forEach { chunk ->
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
                        val target = symbolTable.flat[symbol.split('.')]
                        if (target is StMemVar) {
                            replacements.add(Triple(chunk, idx, target.address+index))
                        }
                    }
                }
        }

        replacements.forEach {
            val old = it.first.instructions[it.second]
            it.first.instructions[it.second] = IRInstruction(
                old.opcode,
                old.type,
                old.reg1,
                old.reg2,
                old.fpReg1,
                old.fpReg2,
                it.third.toInt(),
                null,
                null
            )
        }
    }

    private fun flattenLabelNames() {
        val renameLabels = mutableListOf<Pair<PtNode, PtLabel>>()

        fun flattenRecurse(node: PtNode) {
            node.children.forEach {
                if (it is PtLabel)
                    renameLabels += Pair(it.parent, it)
                else
                    flattenRecurse(it)
            }
        }

        flattenRecurse(program)

        renameLabels.forEach { (parent, label) ->
            val renamedLabel = PtLabel(label.scopedName.joinToString("."), label.position)
            val idx = parent.children.indexOf(label)
            parent.children.removeAt(idx)
            parent.children.add(idx, renamedLabel)
        }
    }

    private fun flattenNestedSubroutines() {
        // this moves all nested subroutines up to the block scope.
        // also changes the name to be the fully scoped one, so it becomes unique at the top level.
        // also moves the start() entrypoint as first subroutine.
        val flattenedSubs = mutableListOf<Pair<PtBlock, PtSub>>()
        val flattenedAsmSubs = mutableListOf<Pair<PtBlock, PtAsmSub>>()
        val removalsSubs = mutableListOf<Pair<PtSub, PtSub>>()
        val removalsAsmSubs = mutableListOf<Pair<PtSub, PtAsmSub>>()
        val renameSubs = mutableListOf<Pair<PtBlock, PtSub>>()
        val renameAsmSubs = mutableListOf<Pair<PtBlock, PtAsmSub>>()
        val entrypoint = program.entrypoint()

        fun flattenNestedAsmSub(block: PtBlock, parentSub: PtSub, asmsub: PtAsmSub) {
            val flattened = PtAsmSub(asmsub.scopedName.joinToString("."),
                asmsub.address,
                asmsub.clobbers,
                asmsub.parameters,
                asmsub.returnTypes,
                asmsub.retvalRegisters,
                asmsub.inline,
                asmsub.position)
            asmsub.children.forEach { flattened.add(it) }
            flattenedAsmSubs += Pair(block, flattened)
            removalsAsmSubs += Pair(parentSub, asmsub)
        }

        fun flattenNestedSub(block: PtBlock, parentSub: PtSub, sub: PtSub) {
            sub.children.filterIsInstance<PtSub>().forEach { subsub->flattenNestedSub(block, sub, subsub) }
            sub.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub->flattenNestedAsmSub(block, sub, asmsubsub) }
            val flattened = PtSub(sub.scopedName.joinToString("."),
                sub.parameters,
                sub.returntype,
                sub.inline,
                sub.position)
            sub.children.forEach { if(it !is PtSub) flattened.add(it) }
            flattenedSubs += Pair(block, flattened)
            removalsSubs += Pair(parentSub, sub)
        }

        program.allBlocks().forEach { block ->
            block.children.forEach {
                if(it is PtSub) {
                    // Only regular subroutines can have nested subroutines.
                    it.children.filterIsInstance<PtSub>().forEach { subsub->flattenNestedSub(block, it, subsub)}
                    it.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub->flattenNestedAsmSub(block, it, asmsubsub)}
                    renameSubs += Pair(block, it)
                }
                if(it is PtAsmSub)
                    renameAsmSubs += Pair(block, it)
            }
        }

        removalsSubs.forEach { (parent, sub) -> parent.children.remove(sub) }
        removalsAsmSubs.forEach { (parent, asmsub) -> parent.children.remove(asmsub) }
        flattenedSubs.forEach { (block, sub) -> block.add(sub) }
        flattenedAsmSubs.forEach { (block, asmsub) -> block.add(asmsub) }
        renameSubs.forEach { (parent, sub) ->
            val renamedSub = PtSub(sub.scopedName.joinToString("."), sub.parameters, sub.returntype, sub.inline, sub.position)
            sub.children.forEach { renamedSub.add(it) }
            parent.children.remove(sub)
            if (sub === entrypoint) {
                // entrypoint sub must be first sub
                val firstsub = parent.children.withIndex().firstOrNull() { it.value is PtSub || it.value is PtAsmSub }
                if(firstsub!=null)
                    parent.add(firstsub.index, renamedSub)
                else
                    parent.add(renamedSub)
            } else {
                parent.add(renamedSub)
            }
        }
        renameAsmSubs.forEach { (parent, sub) ->
            val renamedSub = PtAsmSub(sub.scopedName.joinToString("."),
                sub.address,
                sub.clobbers,
                sub.parameters,
                sub.returnTypes,
                sub.retvalRegisters,
                sub.inline,
                sub.position)

            if(sub.children.isNotEmpty())
                renamedSub.add(sub.children.single())
            parent.children.remove(sub)
            parent.add(renamedSub)
        }
    }

    internal fun translateNode(node: PtNode): IRCodeChunks {
        val chunks = when(node) {
            is PtScopeVarsDecls -> emptyList() // vars should be looked up via symbol table
            is PtVariable -> emptyList() // var should be looked up via symbol table
            is PtMemMapped -> emptyList() // memmapped var should be looked up via symbol table
            is PtConstant -> emptyList() // constants have all been folded into the code
            is PtAssignment -> assignmentGen.translate(node)
            is PtNodeGroup -> translateGroup(node.children)
            is PtBuiltinFunctionCall -> translateBuiltinFunc(node, 0)
            is PtFunctionCall -> expressionEval.translate(node, 0, 0)
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
        val elseLabel = createLabelName()
        // note that the branch opcode used is the opposite as the branch condition, because the generated code jumps to the 'else' part
        val branchIns = when(branch.condition) {
            BranchCondition.CS -> IRInstruction(Opcode.BSTCC, labelSymbol = elseLabel)
            BranchCondition.CC -> IRInstruction(Opcode.BSTCS, labelSymbol = elseLabel)
            BranchCondition.EQ, BranchCondition.Z -> IRInstruction(Opcode.BSTNE, labelSymbol = elseLabel)
            BranchCondition.NE, BranchCondition.NZ -> IRInstruction(Opcode.BSTEQ, labelSymbol = elseLabel)
            BranchCondition.MI, BranchCondition.NEG -> IRInstruction(Opcode.BSTPOS, labelSymbol = elseLabel)
            BranchCondition.PL, BranchCondition.POS -> IRInstruction(Opcode.BSTNEG, labelSymbol = elseLabel)
            BranchCondition.VC -> IRInstruction(Opcode.BSTVC, labelSymbol = elseLabel)
            BranchCondition.VS -> IRInstruction(Opcode.BSTVS, labelSymbol = elseLabel)
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
        if(whenStmt.choices.children.isEmpty())
            return emptyList()
        val result = mutableListOf<IRCodeChunkBase>()
        val valueReg = registers.nextFree()
        val choiceReg = registers.nextFree()
        val valueDt = irType(whenStmt.value.type)
        result += expressionEval.translateExpression(whenStmt.value, valueReg, -1)
        val choices = whenStmt.choices.children.map {it as PtWhenChoice }
        val endLabel = createLabelName()
        for (choice in choices) {
            if(choice.isElse) {
                result += translateNode(choice.statements)
            } else {
                val skipLabel = createLabelName()
                val values = choice.values.children.map {it as PtNumber}
                if(values.size==1) {
                    val chunk = IRCodeChunk(null, null)
                    chunk += IRInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=values[0].number.toInt())
                    chunk += IRInstruction(Opcode.BNE, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = skipLabel)
                    result += chunk
                    result += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
                } else {
                    val matchLabel = createLabelName()
                    val chunk = IRCodeChunk(null, null)
                    for (value in values) {
                        chunk += IRInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=value.number.toInt())
                        chunk += IRInstruction(Opcode.BEQ, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = matchLabel)
                    }
                    chunk += IRInstruction(Opcode.JUMP, labelSymbol = skipLabel)
                    result += chunk
                    result += labelFirstChunk(translateNode(choice.statements), matchLabel)
                    if(choice.statements.children.last() !is PtReturn)
                        addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
                }
                result += IRCodeChunk(skipLabel, null)
            }
        }
        result += IRCodeChunk(endLabel, null)
        return result
    }

    private fun translate(forLoop: PtForLoop): IRCodeChunks {
        val loopvar = symbolTable.lookup(forLoop.variable.targetName)!!
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
                val symbol = iterable.targetName.joinToString(".")
                val iterableVar = symbolTable.lookup(iterable.targetName) as StStaticVariable
                val loopvarSymbol = loopvar.scopedName.joinToString(".")
                val indexReg = registers.nextFree()
                val tmpReg = registers.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                if(iterableVar.dt==DataType.STR) {
                    // iterate over a zero-terminated string
                    addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, value=0), null)
                    val chunk = IRCodeChunk(loopLabel, null)
                    chunk += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpReg, reg2=indexReg, labelSymbol = symbol)
                    chunk += IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=tmpReg, labelSymbol = endLabel)
                    chunk += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=tmpReg, labelSymbol = loopvarSymbol)
                    result += chunk
                    result += translateNode(forLoop.statements)
                    val jumpChunk = IRCodeChunk(null, null)
                    jumpChunk += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexReg)
                    jumpChunk += IRInstruction(Opcode.JUMP, labelSymbol = loopLabel)
                    result += jumpChunk
                    result += IRCodeChunk(endLabel, null)
                } else {
                    // iterate over array
                    val elementDt = ArrayToElementTypes.getValue(iterable.type)
                    val elementSize = program.memsizer.memorySize(elementDt)
                    val lengthBytes = iterableVar.length!! * elementSize
                    if(lengthBytes<256) {
                        val lengthReg = registers.nextFree()
                        val chunk = IRCodeChunk(null, null)
                        chunk += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, value=0)
                        chunk += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, value=lengthBytes)
                        result += chunk
                        val chunk2 = IRCodeChunk(loopLabel, null)
                        chunk2 += IRInstruction(Opcode.LOADX, irType(elementDt), reg1=tmpReg, reg2=indexReg, labelSymbol=symbol)
                        chunk2 += IRInstruction(Opcode.STOREM, irType(elementDt), reg1=tmpReg, labelSymbol = loopvarSymbol)
                        result += chunk2
                        result += translateNode(forLoop.statements)
                        result += addConstReg(IRDataType.BYTE, indexReg, elementSize)
                        addInstr(result, IRInstruction(Opcode.BNE, IRDataType.BYTE, reg1=indexReg, reg2=lengthReg, labelSymbol = loopLabel), null)
                    } else if(lengthBytes==256) {
                        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=indexReg, value=0), null)
                        val chunk = IRCodeChunk(loopLabel, null)
                        chunk += IRInstruction(Opcode.LOADX, irType(elementDt), reg1=tmpReg, reg2=indexReg, labelSymbol=symbol)
                        chunk += IRInstruction(Opcode.STOREM, irType(elementDt), reg1=tmpReg, labelSymbol = loopvarSymbol)
                        result += chunk
                        result += translateNode(forLoop.statements)
                        result += addConstReg(IRDataType.BYTE, indexReg, elementSize)
                        addInstr(result, IRInstruction(Opcode.BNZ, IRDataType.BYTE, reg1=indexReg, labelSymbol = loopLabel), null)
                    } else {
                        throw AssemblyError("iterator length should never exceed 256")
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
        val indexReg = registers.nextFree()
        val endvalueReg = registers.nextFree()
        val loopvarSymbol = loopvar.scopedName.joinToString(".")
        val loopvarDt = when(loopvar) {
            is StMemVar -> loopvar.dt
            is StStaticVariable -> loopvar.dt
            else -> throw AssemblyError("invalid loopvar node type")
        }
        val loopvarDtIr = irType(loopvarDt)
        val loopLabel = createLabelName()
        val result = mutableListOf<IRCodeChunkBase>()

        result += expressionEval.translateExpression(iterable.to, endvalueReg, -1)
        result += expressionEval.translateExpression(iterable.from, indexReg, -1)
        addInstr(result, IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=indexReg, labelSymbol=loopvarSymbol), null)
        result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
        result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
        addInstr(result, IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = indexReg, labelSymbol = loopvarSymbol), null)
        val branchOpcode = if(loopvarDt in SignedDatatypes) Opcode.BLES else Opcode.BLE
        addInstr(result, IRInstruction(branchOpcode, loopvarDtIr, reg1=indexReg, reg2=endvalueReg, labelSymbol=loopLabel), null)
        return result
    }

    private fun translateForInConstantRange(forLoop: PtForLoop, loopvar: StNode): IRCodeChunks {
        val loopLabel = createLabelName()
        val loopvarSymbol = loopvar.scopedName.joinToString(".")
        val indexReg = registers.nextFree()
        val loopvarDt = when(loopvar) {
            is StMemVar -> loopvar.dt
            is StStaticVariable -> loopvar.dt
            else -> throw AssemblyError("invalid loopvar node type")
        }
        val loopvarDtIr = irType(loopvarDt)
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        val rangeStart = (iterable.from as PtNumber).number.toInt()
        val rangeEndUntyped = (iterable.to as PtNumber).number.toInt() + step
        if(step==0)
            throw AssemblyError("step 0")
        if(step>0 && rangeEndUntyped<rangeStart || step<0 && rangeEndUntyped>rangeStart)
            throw AssemblyError("empty range")
        val rangeEndWrapped = if(loopvarDtIr==IRDataType.BYTE) rangeEndUntyped and 255 else rangeEndUntyped and 65535
        val endvalueReg: Int
        val result = mutableListOf<IRCodeChunkBase>()
        val chunk = IRCodeChunk(null, null)
        if(rangeEndWrapped!=0) {
            endvalueReg = registers.nextFree()
            chunk += IRInstruction(Opcode.LOAD, loopvarDtIr, reg1 = endvalueReg, value = rangeEndWrapped)
        } else {
            endvalueReg = -1 // not used
        }
        chunk += IRInstruction(Opcode.LOAD, loopvarDtIr, reg1=indexReg, value=rangeStart)
        chunk += IRInstruction(Opcode.STOREM, loopvarDtIr, reg1=indexReg, labelSymbol=loopvarSymbol)
        result += chunk
        result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
        result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
        val chunk2 = IRCodeChunk(null, null)
        chunk2 += IRInstruction(Opcode.LOADM, loopvarDtIr, reg1 = indexReg, labelSymbol = loopvarSymbol)
        chunk2 += if(rangeEndWrapped==0) {
            IRInstruction(Opcode.BNZ, loopvarDtIr, reg1 = indexReg, labelSymbol = loopLabel)
        } else {
            IRInstruction(Opcode.BNE, loopvarDtIr, reg1 = indexReg, reg2 = endvalueReg, labelSymbol = loopLabel)
        }
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
                    IRInstruction(Opcode.ADD, dt, reg1 = reg, value=value)
                } else {
                    IRInstruction(Opcode.SUB, dt, reg1 = reg, value=-value)
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
                    IRInstruction(Opcode.INCM, dt, value=knownAddress.toInt())
                else
                    IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
            }
            2 -> {
                if(knownAddress!=null) {
                    code += IRInstruction(Opcode.INCM, dt, value = knownAddress.toInt())
                    code += IRInstruction(Opcode.INCM, dt, value = knownAddress.toInt())
                } else {
                    code += IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
                    code += IRInstruction(Opcode.INCM, dt, labelSymbol = symbol)
                }
            }
            -1 -> {
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, dt, value=knownAddress.toInt())
                else
                    IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
            }
            -2 -> {
                if(knownAddress!=null) {
                    code += IRInstruction(Opcode.DECM, dt, value = knownAddress.toInt())
                    code += IRInstruction(Opcode.DECM, dt, value = knownAddress.toInt())
                } else {
                    code += IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
                    code += IRInstruction(Opcode.DECM, dt, labelSymbol = symbol)
                }
            }
            else -> {
                val valueReg = registers.nextFree()
                if(value>0) {
                    code += IRInstruction(Opcode.LOAD, dt, reg1=valueReg, value=value)
                    code += if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, dt, reg1=valueReg, value=knownAddress.toInt())
                    else
                        IRInstruction(Opcode.ADDM, dt, reg1=valueReg, labelSymbol = symbol)
                }
                else {
                    code += IRInstruction(Opcode.LOAD, dt, reg1=valueReg, value=-value)
                    code += if(knownAddress!=null)
                        IRInstruction(Opcode.SUBM, dt, reg1=valueReg, value=knownAddress.toInt())
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
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, fpValue = 0f)
        } else {
            IRInstruction(Opcode.MUL, IRDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        if(factor==0f) {
            code += if(knownAddress!=null)
                IRInstruction(Opcode.STOREZM, IRDataType.FLOAT, value = knownAddress)
            else
                IRInstruction(Opcode.STOREZM, IRDataType.FLOAT, labelSymbol = symbol)
        } else {
            val factorReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.MULM, IRDataType.FLOAT, fpReg1 = factorReg, value = knownAddress)
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
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += IRInstruction(Opcode.LSLN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRInstruction(Opcode.LOAD, dt, reg1=reg, value=0)
            } else {
                IRInstruction(Opcode.MUL, dt, reg1=reg, value=factor)
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
                IRInstruction(Opcode.LSLM, dt, value = knownAddress)
            else
                IRInstruction(Opcode.LSLM, dt, labelSymbol = symbol)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.LSLNM, dt, reg1=pow2reg, value=knownAddress)
            else
                IRInstruction(Opcode.LSLNM, dt, reg1=pow2reg, labelSymbol = symbol)
        } else {
            if (factor == 0) {
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.STOREZM, dt, value=knownAddress)
                else
                    IRInstruction(Opcode.STOREZM, dt, labelSymbol = symbol)
            }
            else {
                val factorReg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=factorReg, value = factor)
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, dt, reg1=factorReg, value = knownAddress)
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
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = fpReg, fpValue = Float.MAX_VALUE)
        } else {
            IRInstruction(Opcode.DIVS, IRDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(knownAddress: Int?, symbol: String?, factor: Float): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1f)
            return code
        if(factor==0f) {
            val maxvalueReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = maxvalueReg, fpValue = Float.MAX_VALUE)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = maxvalueReg, value=knownAddress)
            else
                IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = maxvalueReg, labelSymbol = symbol)
        } else {
            val factorReg = registers.nextFreeFloat()
            code += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += if(knownAddress!=null)
                IRInstruction(Opcode.DIVSM, IRDataType.FLOAT, fpReg1 = factorReg, value=knownAddress)
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
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed)
                IRInstruction(Opcode.ASRN, dt, reg1=reg, reg2=pow2reg)
            else
                IRInstruction(Opcode.LSRN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
            } else {
                if(signed)
                    IRInstruction(Opcode.DIVS, dt, reg1=reg, value=factor)
                else
                    IRInstruction(Opcode.DIV, dt, reg1=reg, value=factor)
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
                IRInstruction(Opcode.LSRM, dt, value=knownAddress)
            else
                IRInstruction(Opcode.LSRM, dt, labelSymbol = symbol)
        }
        else if(pow2>=1 && !signed) {
            // just shift multiple bits
            val pow2reg = registers.nextFree()
            code += IRInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed) {
                if(knownAddress!=null)
                    IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, value = knownAddress)
                else
                    IRInstruction(Opcode.ASRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
            }
            else {
                if(knownAddress!=null)
                    IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, value = knownAddress)
                else
                    IRInstruction(Opcode.LSRNM, dt, reg1 = pow2reg, labelSymbol = symbol)
            }
        } else {
            if (factor == 0) {
                val reg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
                code += if(knownAddress!=null)
                    IRInstruction(Opcode.STOREM, dt, reg1=reg, value=knownAddress)
                else
                    IRInstruction(Opcode.STOREM, dt, reg1=reg, labelSymbol = symbol)
            }
            else {
                val factorReg = registers.nextFree()
                code += IRInstruction(Opcode.LOAD, dt, reg1=factorReg, value= factor)
                code += if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, dt, reg1 = factorReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, dt, reg1 = factorReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, dt, reg1 = factorReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, dt, reg1 = factorReg, labelSymbol = symbol)
                }
            }
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): IRCodeChunks {
        if(ifElse.condition.operator !in ComparisonOperators)
            throw AssemblyError("if condition should only be a binary comparison expression")

        val signed = ifElse.condition.left.type in arrayOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
        val irDt = irType(ifElse.condition.left.type)

        fun translateNonZeroComparison(): IRCodeChunks {
            val result = mutableListOf<IRCodeChunkBase>()
            val elseBranch = when(ifElse.condition.operator) {
                "==" -> Opcode.BNE
                "!=" -> Opcode.BEQ
                "<" -> if(signed) Opcode.BGES else Opcode.BGE
                ">" -> if(signed) Opcode.BLES else Opcode.BLE
                "<=" -> if(signed) Opcode.BGTS else Opcode.BGT
                ">=" -> if(signed) Opcode.BLTS else Opcode.BLT
                else -> throw AssemblyError("invalid comparison operator")
            }

            val leftReg = registers.nextFree()
            val rightReg = registers.nextFree()
            result += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
            result += expressionEval.translateExpression(ifElse.condition.right, rightReg, -1)
            if(ifElse.elseScope.children.isNotEmpty()) {
                // if and else parts
                val elseLabel = createLabelName()
                val afterIfLabel = createLabelName()
                addInstr(result, IRInstruction(elseBranch, irDt, reg1=leftReg, reg2=rightReg, labelSymbol = elseLabel), null)
                result += translateNode(ifElse.ifScope)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                result += IRCodeChunk(afterIfLabel, null)
            } else {
                // only if part
                val afterIfLabel = createLabelName()
                addInstr(result, IRInstruction(elseBranch, irDt, reg1=leftReg, reg2=rightReg, labelSymbol = afterIfLabel), null)
                result += translateNode(ifElse.ifScope)
                result += IRCodeChunk(afterIfLabel, null)
            }
            return result
        }

        fun translateZeroComparison(): IRCodeChunks {
            fun equalOrNotEqualZero(elseBranch: Opcode): IRCodeChunks {
                val result = mutableListOf<IRCodeChunkBase>()
                val leftReg = registers.nextFree()
                result += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
                if(ifElse.elseScope.children.isNotEmpty()) {
                    // if and else parts
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    addInstr(result, IRInstruction(elseBranch, irDt, reg1=leftReg, labelSymbol = elseLabel), null)
                    result += translateNode(ifElse.ifScope)
                    addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = afterIfLabel), null)
                    result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
                    result += IRCodeChunk(afterIfLabel, null)
                } else {
                    // only if part
                    val afterIfLabel = createLabelName()
                    addInstr(result, IRInstruction(elseBranch, irDt, reg1=leftReg, labelSymbol = afterIfLabel), null)
                    result += translateNode(ifElse.ifScope)
                    result += IRCodeChunk(afterIfLabel, null)
                }
                return result
            }

            return when (ifElse.condition.operator) {
                "==" -> {
                    // if X==0 ...   so we just branch on left expr is Not-zero.
                    equalOrNotEqualZero(Opcode.BNZ)
                }
                "!=" -> {
                    // if X!=0 ...   so we just branch on left expr is Zero.
                    equalOrNotEqualZero(Opcode.BZ)
                }
                else -> {
                    // another comparison against 0, just use regular codegen for this.
                    translateNonZeroComparison()
                }
            }
        }

        return if(constValue(ifElse.condition.right)==0.0)
            translateZeroComparison()
        else
            translateNonZeroComparison()
    }

    private fun translate(postIncrDecr: PtPostIncrDecr): IRCodeChunks {
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
        val ident = postIncrDecr.target.identifier
        val memory = postIncrDecr.target.memory
        val array = postIncrDecr.target.array
        val irDt = irType(postIncrDecr.target.type)
        val result = mutableListOf<IRCodeChunkBase>()
        if(ident!=null) {
            addInstr(result, IRInstruction(operationMem, irDt, labelSymbol = ident.targetName.joinToString(".")), null)
        } else if(memory!=null) {
            if(memory.address is PtNumber) {
                val address = (memory.address as PtNumber).number.toInt()
                addInstr(result, IRInstruction(operationMem, irDt, value = address), null)
            } else {
                val incReg = registers.nextFree()
                val addressReg = registers.nextFree()
                result += expressionEval.translateExpression(memory.address, addressReg, -1)
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstruction(Opcode.LOADI, irDt, reg1 = incReg, reg2 = addressReg)
                chunk += IRInstruction(operationRegister, irDt, reg1 = incReg)
                chunk += IRInstruction(Opcode.STOREI, irDt, reg1 = incReg, reg2 = addressReg)
                result += chunk
            }
        } else if (array!=null) {
            val variable = array.variable.targetName.joinToString(".")
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = constIntValue(array.index)
            if(fixedIndex!=null) {
                val offset = fixedIndex*itemsize
                addInstr(result, IRInstruction(operationMem, irDt, labelSymbol="$variable+$offset"), null)
            } else {
                val incReg = registers.nextFree()
                val indexReg = registers.nextFree()
                result += expressionEval.translateExpression(array.index, indexReg, -1)
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstruction(Opcode.LOADX, irDt, reg1=incReg, reg2=indexReg, labelSymbol=variable)
                chunk += IRInstruction(operationRegister, irDt, reg1=incReg)
                chunk += IRInstruction(Opcode.STOREX, irDt, reg1=incReg, reg2=indexReg, labelSymbol=variable)
                result += chunk
            }
        } else
            throw AssemblyError("weird assigntarget")

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
        val counterReg = registers.nextFree()
        val irDt = irType(repeat.count.type)
        val result = mutableListOf<IRCodeChunkBase>()
        result += expressionEval.translateExpression(repeat.count, counterReg, -1)
        addInstr(result, IRInstruction(Opcode.BZ, irDt, reg1=counterReg, labelSymbol = skipRepeatLabel), null)
        result += labelFirstChunk(translateNode(repeat.statements), repeatLabel)
        val chunk = IRCodeChunk(null, null)
        chunk += IRInstruction(Opcode.DEC, irDt, reg1=counterReg)
        chunk += IRInstruction(Opcode.BNZ, irDt, reg1=counterReg, labelSymbol = repeatLabel)
        result += chunk
        result += IRCodeChunk(skipRepeatLabel, null)
        return result
    }

    private fun translate(jump: PtJump): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val instr = if(jump.address!=null) {
            IRInstruction(Opcode.JUMPA, value = jump.address!!.toInt())
        } else {
            if (jump.generatedLabel != null)
                IRInstruction(Opcode.JUMP, labelSymbol = jump.generatedLabel!!)
            else if (jump.identifier != null)
                IRInstruction(Opcode.JUMP, labelSymbol = jump.identifier!!.targetName.joinToString("."))
            else
                throw AssemblyError("weird jump")
        }
        addInstr(result, instr, null)
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
        if(value!=null) {
            // Call Convention: return value is always returned in r0 (or fr0 if float)
            result += if(value.type==DataType.FLOAT)
                expressionEval.translateExpression(value, -1, 0)
            else
                expressionEval.translateExpression(value, 0, -1)
        }
        addInstr(result, IRInstruction(Opcode.RETURN), null)
        return result
    }

    private fun translate(block: PtBlock): IRBlock {
        val irBlock = IRBlock(block.name, block.address, translate(block.alignment), block.position)   // no use for other attributes yet?
        for (child in block.children) {
            when(child) {
                is PtNop -> { /* nothing */ }
                is PtAssignment -> { /* global variable initialization is done elsewhere */ }
                is PtScopeVarsDecls -> { /* vars should be looked up via symbol table */ }
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
                        require(child.children.isEmpty())
                    } else {
                        // regular asmsub
                        val assemblyChild = child.children.single() as PtInlineAssembly
                        val asmChunk = IRInlineAsmChunk(
                            child.name, assemblyChild.assembly, assemblyChild.isIR, null
                        )
                        irBlock += IRAsmSubroutine(
                            child.name,
                            child.address,
                            child.clobbers,
                            child.parameters.map { IRAsmSubroutine.IRAsmParam(it.second, it.first.type) },        // note: the name of the asmsub param is not used anymore.
                            child.returnTypes.zip(child.retvalRegisters).map { IRAsmSubroutine.IRAsmParam(it.second, it.first) },
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
                else -> TODO("weird child node $child")
            }
        }
        return irBlock
    }

    private fun translate(parameters: List<PtSubroutineParameter>) =
        parameters.map {
            val flattenedName = (it.definingSub()!!.scopedName + it.name)
            val orig = symbolTable.flat.getValue(flattenedName) as StStaticVariable
            IRSubroutine.IRParam(flattenedName.joinToString("."), orig.dt)
        }

    private fun translate(alignment: PtBlock.BlockAlignment): IRBlock.BlockAlignment {
        return when(alignment) {
            PtBlock.BlockAlignment.NONE -> IRBlock.BlockAlignment.NONE
            PtBlock.BlockAlignment.WORD -> IRBlock.BlockAlignment.WORD
            PtBlock.BlockAlignment.PAGE -> IRBlock.BlockAlignment.PAGE
        }
    }


    internal fun irType(type: DataType): IRDataType {
        return when(type) {
            DataType.BOOL,
            DataType.UBYTE,
            DataType.BYTE -> IRDataType.BYTE
            DataType.UWORD,
            DataType.WORD -> IRDataType.WORD
            DataType.FLOAT -> IRDataType.FLOAT
            in PassByReferenceDatatypes -> IRDataType.WORD
            else -> throw AssemblyError("no IR datatype for $type")
        }
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): String {
        labelSequenceNumber++
        return "prog8_label_gen_$labelSequenceNumber"
    }

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks =
        builtinFuncGen.translate(call, resultRegister)

    internal fun isZero(expression: PtExpression): Boolean = expression is PtNumber && expression.number==0.0

    internal fun isOne(expression: PtExpression): Boolean = expression is PtNumber && expression.number==1.0
}
