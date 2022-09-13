package prog8.codegen.experimental

import prog8.code.StMemorySlab
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*
import kotlin.io.path.readBytes
import kotlin.math.pow


internal class VmRegisterPool {
    private var firstFree: Int=3        // integer registers 0,1,2 are reserved
    private var firstFreeFloat: Int=0

    fun peekNext() = firstFree
    fun peekNextFloat() = firstFreeFloat

    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        if(firstFree>65535)
            throw AssemblyError("out of virtual registers (int)")
        return result
    }

    fun nextFreeFloat(): Int {
        val result = firstFreeFloat
        firstFreeFloat++
        if(firstFreeFloat>65535)
            throw AssemblyError("out of virtual registers (fp)")
        return result
    }
}


class CodeGen(internal val program: PtProgram,
              internal val symbolTable: SymbolTable,
              internal val options: CompilationOptions,
              internal val errors: IErrorReporter
): IAssemblyGenerator {

    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    private val assignmentGen = AssignmentGen(this, expressionEval)
    internal val vmRegisters = VmRegisterPool()

    override fun compileToAssembly(): IAssemblyProgram? {
        flattenNestedSubroutines()

        val irProg = IRProgram(program.name, symbolTable, options, program.encoding)

        if(!options.dontReinitGlobals) {
            // collect global variables initializers
            program.allBlocks().forEach {
                val code = IRCodeChunk(it.position)
                it.children.filterIsInstance<PtAssignment>().forEach { assign -> code += assignmentGen.translate(assign) }
                irProg.addGlobalInits(code)
            }
        }

        if(options.symbolDefs.isNotEmpty())
            throw AssemblyError("virtual target doesn't support symbols defined on the commandline")
        if(options.evalStackBaseAddress!=null)
            throw AssemblyError("virtual target doesn't use eval-stack")

        for (block in program.allBlocks()) {
            irProg.addBlock(translate(block))
        }

        if(options.optimize) {
            val optimizer = IRPeepholeOptimizer(irProg)
            optimizer.optimize()
        }

        // create IR file on disk and read it back.
        // TODO: this makes sure those I/O routines are correct, but this step should be skipped eventually.
        IRFileWriter(irProg).writeFile()
        val irProgFromDisk = IRFileReader(options.outputDir, irProg.name).readFile()
        return VmAssemblyProgram(irProgFromDisk.name, irProgFromDisk)
    }

    private fun flattenNestedSubroutines() {
        // this moves all nested subroutines up to the block scope.
        // also changes the name to be the fully scoped one so it becomes unique at the top level.
        // also moves the start() entrypoint as first subroutine.
        val flattenedSubs = mutableListOf<Pair<PtBlock, PtSub>>()
        val flattenedAsmSubs = mutableListOf<Pair<PtBlock, PtAsmSub>>()
        val removalsSubs = mutableListOf<Pair<PtSub, PtSub>>()
        val removalsAsmSubs = mutableListOf<Pair<PtSub, PtAsmSub>>()
        val renameSubs = mutableListOf<Pair<PtBlock, PtSub>>()
        val renameAsmSubs = mutableListOf<Pair<PtBlock, PtAsmSub>>()
        val renameLabels = mutableListOf<Pair<PtNode, PtLabel>>()
        val entrypoint = program.entrypoint()

        fun flattenNestedAsm(block: PtBlock, parentSub: PtSub, asmsub: PtAsmSub) {
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

        fun flattenNested(block: PtBlock, parentSub: PtSub, sub: PtSub) {
            sub.children.filterIsInstance<PtSub>().forEach { subsub->flattenNested(block, sub, subsub) }
            sub.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub->flattenNestedAsm(block, sub, asmsubsub) }
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
                    it.children.filterIsInstance<PtSub>().forEach { subsub->flattenNested(block, it, subsub)}
                    it.children.filterIsInstance<PtAsmSub>().forEach { asmsubsub->flattenNestedAsm(block, it, asmsubsub)}
                    it.children.filterIsInstance<PtLabel>().forEach { label->renameLabels.add(Pair(it, label))}
                    renameSubs += Pair(block, it)
                }
                if(it is PtAsmSub)
                    renameAsmSubs += Pair(block, it)
                if(it is PtLabel)
                    renameLabels += Pair(block, it)
            }
        }

        renameLabels.forEach { (parent, label) ->
            val renamedLabel = PtLabel(label.scopedName.joinToString("."), label.position)
            val idx = parent.children.indexOf(label)
            parent.children.removeAt(idx)
            parent.children.add(idx, renamedLabel)
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
                val firstsub = parent.children.withIndex().first { it.value is PtSub || it.value is PtAsmSub }
                parent.add(firstsub.index, renamedSub)
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
            parent.children.remove(sub)
            parent.add(renamedSub)
        }
    }

    internal fun translateNode(node: PtNode): IRCodeChunkBase {
        val code = when(node) {
            is PtScopeVarsDecls -> IRCodeChunk(node.position) // vars should be looked up via symbol table
            is PtVariable -> IRCodeChunk(node.position) // var should be looked up via symbol table
            is PtMemMapped -> IRCodeChunk(node.position) // memmapped var should be looked up via symbol table
            is PtConstant -> IRCodeChunk(node.position) // constants have all been folded into the code
            is PtAssignment -> assignmentGen.translate(node)
            is PtNodeGroup -> translateGroup(node.children, node.position)
            is PtBuiltinFunctionCall -> translateBuiltinFunc(node, 0)
            is PtFunctionCall -> expressionEval.translate(node, 0, 0)
            is PtNop -> IRCodeChunk(node.position)
            is PtReturn -> translate(node)
            is PtJump -> translate(node)
            is PtWhen -> translate(node)
            is PtForLoop -> translate(node)
            is PtIfElse -> translate(node)
            is PtPostIncrDecr -> translate(node)
            is PtRepeatLoop -> translate(node)
            is PtLabel -> {
                val chunk = IRCodeChunk(node.position)
                chunk += IRCodeLabel(node.name)
                return chunk
            }
            is PtBreakpoint -> {
                val chunk = IRCodeChunk(node.position)
                chunk += IRCodeInstruction(Opcode.BREAKPOINT)
                return chunk
            }
            is PtConditionalBranch -> translate(node)
            is PtInlineAssembly -> IRInlineAsmChunk(node.assembly, node.position)
            is PtIncludeBinary -> {
                val chunk = IRCodeChunk(node.position)
                val data =  node.file.readBytes()
                    .drop(node.offset?.toInt() ?: 0)
                    .take(node.length?.toInt() ?: Int.MAX_VALUE)
                chunk += IRCodeInlineBinary(data.toByteArray())
                return chunk
            }
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
        return code
    }

    private fun translate(branch: PtConditionalBranch): IRCodeChunk {
        val code = IRCodeChunk(branch.position)
        val elseLabel = createLabelName()
        // note that the branch opcode used is the opposite as the branch condition, because the generated code jumps to the 'else' part
        code += when(branch.condition) {
            BranchCondition.CS -> IRCodeInstruction(Opcode.BSTCC, labelSymbol = elseLabel)
            BranchCondition.CC -> IRCodeInstruction(Opcode.BSTCS, labelSymbol = elseLabel)
            BranchCondition.EQ, BranchCondition.Z -> IRCodeInstruction(Opcode.BSTNE, labelSymbol = elseLabel)
            BranchCondition.NE, BranchCondition.NZ -> IRCodeInstruction(Opcode.BSTEQ, labelSymbol = elseLabel)
            BranchCondition.MI, BranchCondition.NEG -> IRCodeInstruction(Opcode.BSTPOS, labelSymbol = elseLabel)
            BranchCondition.PL, BranchCondition.POS -> IRCodeInstruction(Opcode.BSTNEG, labelSymbol = elseLabel)
            BranchCondition.VC,
            BranchCondition.VS -> throw AssemblyError("conditional branch ${branch.condition} not supported in vm target due to lack of cpu V flag ${branch.position}")
        }
        code += translateNode(branch.trueScope)
        if(branch.falseScope.children.isNotEmpty()) {
            val endLabel = createLabelName()
            code += IRCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
            code += IRCodeLabel(elseLabel)
            code += translateNode(branch.falseScope)
            code += IRCodeLabel(endLabel)
        } else {
            code += IRCodeLabel(elseLabel)
        }
        return code
    }

    private fun translate(whenStmt: PtWhen): IRCodeChunk {
        val code = IRCodeChunk(whenStmt.position)
        if(whenStmt.choices.children.isEmpty())
            return code
        val valueReg = vmRegisters.nextFree()
        val choiceReg = vmRegisters.nextFree()
        val valueDt = vmType(whenStmt.value.type)
        code += expressionEval.translateExpression(whenStmt.value, valueReg, -1)
        val choices = whenStmt.choices.children.map {it as PtWhenChoice }
        val endLabel = createLabelName()
        for (choice in choices) {
            if(choice.isElse) {
                code += translateNode(choice.statements)
            } else {
                val skipLabel = createLabelName()
                val values = choice.values.children.map {it as PtNumber}
                if(values.size==1) {
                    code += IRCodeInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=values[0].number.toInt())
                    code += IRCodeInstruction(Opcode.BNE, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = skipLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += IRCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
                } else {
                    val matchLabel = createLabelName()
                    for (value in values) {
                        code += IRCodeInstruction(Opcode.LOAD, valueDt, reg1=choiceReg, value=value.number.toInt())
                        code += IRCodeInstruction(Opcode.BEQ, valueDt, reg1=valueReg, reg2=choiceReg, labelSymbol = matchLabel)
                    }
                    code += IRCodeInstruction(Opcode.JUMP, labelSymbol = skipLabel)
                    code += IRCodeLabel(matchLabel)
                    code += translateNode(choice.statements)
                    if(choice.statements.children.last() !is PtReturn)
                        code += IRCodeInstruction(Opcode.JUMP, labelSymbol = endLabel)
                }
                code += IRCodeLabel(skipLabel)
            }
        }
        code += IRCodeLabel(endLabel)
        return code
    }

    private fun translate(forLoop: PtForLoop): IRCodeChunk {
        val loopvar = symbolTable.lookup(forLoop.variable.targetName) as StStaticVariable
        val iterable = forLoop.iterable
        val code = IRCodeChunk(forLoop.position)
        when(iterable) {
            is PtRange -> {
                if(iterable.from is PtNumber && iterable.to is PtNumber)
                    code += translateForInConstantRange(forLoop, loopvar)
                else
                    code += translateForInNonConstantRange(forLoop, loopvar)
            }
            is PtIdentifier -> {
                val arrayAddress = addressOf(iterable.targetName)
                val iterableVar = symbolTable.lookup(iterable.targetName) as StStaticVariable
                val loopvarAddress = addressOf(loopvar.scopedName)  // TODO name?
                val indexReg = vmRegisters.nextFree()
                val tmpReg = vmRegisters.nextFree()
                val loopLabel = createLabelName()
                val endLabel = createLabelName()
                if(iterableVar.dt==DataType.STR) {
                    // iterate over a zero-terminated string
                    code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                    code += IRCodeLabel(loopLabel)
                    code += IRCodeInstruction(Opcode.LOADX, VmDataType.BYTE, reg1=tmpReg, reg2=indexReg, value = arrayAddress)
                    code += IRCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=tmpReg, labelSymbol = endLabel)
                    code += IRCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1=tmpReg, value = loopvarAddress)
                    code += translateNode(forLoop.statements)
                    code += IRCodeInstruction(Opcode.INC, VmDataType.BYTE, reg1=indexReg)
                    code += IRCodeInstruction(Opcode.JUMP, labelSymbol = loopLabel)
                    code += IRCodeLabel(endLabel)
                } else {
                    // iterate over array
                    val elementDt = ArrayToElementTypes.getValue(iterable.type)
                    val elementSize = program.memsizer.memorySize(elementDt)
                    val lengthBytes = iterableVar.length!! * elementSize
                    if(lengthBytes<256) {
                        val lengthReg = vmRegisters.nextFree()
                        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=lengthReg, value=lengthBytes)
                        code += IRCodeLabel(loopLabel)
                        code += IRCodeInstruction(Opcode.LOADX, vmType(elementDt), reg1=tmpReg, reg2=indexReg, value=arrayAddress)
                        code += IRCodeInstruction(Opcode.STOREM, vmType(elementDt), reg1=tmpReg, value = loopvarAddress)
                        code += translateNode(forLoop.statements)
                        code += addConstReg(VmDataType.BYTE, indexReg, elementSize, iterable.position)
                        code += IRCodeInstruction(Opcode.BNE, VmDataType.BYTE, reg1=indexReg, reg2=lengthReg, labelSymbol = loopLabel)
                    } else if(lengthBytes==256) {
                        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=indexReg, value=0)
                        code += IRCodeLabel(loopLabel)
                        code += IRCodeInstruction(Opcode.LOADX, vmType(elementDt), reg1=tmpReg, reg2=indexReg, value=arrayAddress)
                        code += IRCodeInstruction(Opcode.STOREM, vmType(elementDt), reg1=tmpReg, value = loopvarAddress)
                        code += translateNode(forLoop.statements)
                        code += addConstReg(VmDataType.BYTE, indexReg, elementSize, iterable.position)
                        code += IRCodeInstruction(Opcode.BNZ, VmDataType.BYTE, reg1=indexReg, labelSymbol = loopLabel)
                    } else {
                        throw AssemblyError("iterator length should never exceed 256")
                    }
                }
            }
            else -> throw AssemblyError("weird for iterable")
        }
        return code
    }

    internal fun addressOf(targetName: List<String>): Int {
        println("TODO: IR SUPPORT FOR ADDRESS-OF $targetName")  // TODO address-of
        return 4242
    }

    private fun translateForInNonConstantRange(forLoop: PtForLoop, loopvar: StStaticVariable): IRCodeChunk {
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        if (step==0)
            throw AssemblyError("step 0")
        val indexReg = vmRegisters.nextFree()
        val endvalueReg = vmRegisters.nextFree()
        val loopvarAddress = addressOf(loopvar.scopedName)  // TODO name?
        val loopvarDt = vmType(loopvar.dt)
        val loopLabel = createLabelName()
        val code = IRCodeChunk(forLoop.position)

        code += expressionEval.translateExpression(iterable.to, endvalueReg, -1)
        code += expressionEval.translateExpression(iterable.from, indexReg, -1)
        code += IRCodeInstruction(Opcode.STOREM, loopvarDt, reg1=indexReg, value=loopvarAddress)
        code += IRCodeLabel(loopLabel)
        code += translateNode(forLoop.statements)
        code += addConstMem(loopvarDt, loopvarAddress.toUInt(), step, iterable.position)
        code += IRCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        val branchOpcode = if(loopvar.dt in SignedDatatypes) Opcode.BLES else Opcode.BLE
        code += IRCodeInstruction(branchOpcode, loopvarDt, reg1=indexReg, reg2=endvalueReg, labelSymbol=loopLabel)
        return code
    }

    private fun translateForInConstantRange(forLoop: PtForLoop, loopvar: StStaticVariable): IRCodeChunk {
        val loopLabel = createLabelName()
        val loopvarAddress = addressOf(loopvar.scopedName)      // TODO name?
        val indexReg = vmRegisters.nextFree()
        val loopvarDt = vmType(loopvar.dt)
        val iterable = forLoop.iterable as PtRange
        val step = iterable.step.number.toInt()
        val rangeStart = (iterable.from as PtNumber).number.toInt()
        val rangeEndUntyped = (iterable.to as PtNumber).number.toInt() + step
        if(step==0)
            throw AssemblyError("step 0")
        if(step>0 && rangeEndUntyped<rangeStart || step<0 && rangeEndUntyped>rangeStart)
            throw AssemblyError("empty range")
        val rangeEndWrapped = if(loopvarDt==VmDataType.BYTE) rangeEndUntyped and 255 else rangeEndUntyped and 65535
        val code = IRCodeChunk(forLoop.position)
        val endvalueReg: Int
        if(rangeEndWrapped!=0) {
            endvalueReg = vmRegisters.nextFree()
            code += IRCodeInstruction(Opcode.LOAD, loopvarDt, reg1 = endvalueReg, value = rangeEndWrapped)
        } else {
            endvalueReg = -1 // not used
        }
        code += IRCodeInstruction(Opcode.LOAD, loopvarDt, reg1=indexReg, value=rangeStart)
        code += IRCodeInstruction(Opcode.STOREM, loopvarDt, reg1=indexReg, value=loopvarAddress)
        code += IRCodeLabel(loopLabel)
        code += translateNode(forLoop.statements)
        code += addConstMem(loopvarDt, loopvarAddress.toUInt(), step, iterable.position)
        code += IRCodeInstruction(Opcode.LOADM, loopvarDt, reg1 = indexReg, value = loopvarAddress)
        code += if(rangeEndWrapped==0) {
            IRCodeInstruction(Opcode.BNZ, loopvarDt, reg1 = indexReg, labelSymbol = loopLabel)
        } else {
            IRCodeInstruction(Opcode.BNE, loopvarDt, reg1 = indexReg, reg2 = endvalueReg, labelSymbol = loopLabel)
        }
        return code
    }

    private fun addConstReg(dt: VmDataType, reg: Int, value: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += IRCodeInstruction(Opcode.INC, dt, reg1=reg)
            }
            2 -> {
                code += IRCodeInstruction(Opcode.INC, dt, reg1=reg)
                code += IRCodeInstruction(Opcode.INC, dt, reg1=reg)
            }
            -1 -> {
                code += IRCodeInstruction(Opcode.DEC, dt, reg1=reg)
            }
            -2 -> {
                code += IRCodeInstruction(Opcode.DEC, dt, reg1=reg)
                code += IRCodeInstruction(Opcode.DEC, dt, reg1=reg)
            }
            else -> {
                code += if(value>0) {
                    IRCodeInstruction(Opcode.ADD, dt, reg1 = reg, value=value)
                } else {
                    IRCodeInstruction(Opcode.SUB, dt, reg1 = reg, value=-value)
                }
            }
        }
        return code
    }

    private fun addConstMem(dt: VmDataType, address: UInt, value: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += IRCodeInstruction(Opcode.INCM, dt, value=address.toInt())
            }
            2 -> {
                code += IRCodeInstruction(Opcode.INCM, dt, value=address.toInt())
                code += IRCodeInstruction(Opcode.INCM, dt, value=address.toInt())
            }
            -1 -> {
                code += IRCodeInstruction(Opcode.DECM, dt, value=address.toInt())
            }
            -2 -> {
                code += IRCodeInstruction(Opcode.DECM, dt, value=address.toInt())
                code += IRCodeInstruction(Opcode.DECM, dt, value=address.toInt())
            }
            else -> {
                val valueReg = vmRegisters.nextFree()
                if(value>0) {
                    code += IRCodeInstruction(Opcode.LOAD, dt, reg1=valueReg, value=value)
                    code += IRCodeInstruction(Opcode.ADDM, dt, reg1=valueReg, value=address.toInt())
                }
                else {
                    code += IRCodeInstruction(Opcode.LOAD, dt, reg1=valueReg, value=-value)
                    code += IRCodeInstruction(Opcode.SUBM, dt, reg1=valueReg, value=address.toInt())
                }
            }
        }
        return code
    }

    internal fun multiplyByConstFloat(fpReg: Int, factor: Float, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1f)
            return code
        code += if(factor==0f) {
            IRCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = fpReg, fpValue = 0f)
        } else {
            IRCodeInstruction(Opcode.MUL, VmDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(address: Int, factor: Float, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1f)
            return code
        if(factor==0f) {
            code += IRCodeInstruction(Opcode.STOREZM, VmDataType.FLOAT, value = address)
        } else {
            val factorReg = vmRegisters.nextFreeFloat()
            code += IRCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += IRCodeInstruction(Opcode.MULM, VmDataType.FLOAT, fpReg1 = factorReg, value = address)
        }
        return code
    }

    internal val powersOfTwo = (0..16).map { 2.0.pow(it.toDouble()).toInt() }

    internal fun multiplyByConst(dt: VmDataType, reg: Int, factor: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += IRCodeInstruction(Opcode.LSL, dt, reg1=reg)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += IRCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += IRCodeInstruction(Opcode.LSLN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0)
            } else {
                IRCodeInstruction(Opcode.MUL, dt, reg1=reg, value=factor)
            }
        }
        return code
    }

    internal fun multiplyByConstInplace(dt: VmDataType, address: Int, factor: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += IRCodeInstruction(Opcode.LSLM, dt, value = address)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += IRCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += IRCodeInstruction(Opcode.LSLNM, dt, reg1=pow2reg, value=address)
        } else {
            if (factor == 0) {
                code += IRCodeInstruction(Opcode.STOREZM, dt, value=address)
            }
            else {
                val factorReg = vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value = factor)
                code += IRCodeInstruction(Opcode.MULM, dt, reg1=factorReg, value = address)
            }
        }
        return code
    }

    internal fun divideByConstFloat(fpReg: Int, factor: Float, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1f)
            return code
        code += if(factor==0f) {
            IRCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = fpReg, fpValue = Float.MAX_VALUE)
        } else {
            IRCodeInstruction(Opcode.DIVS, VmDataType.FLOAT, fpReg1 = fpReg, fpValue=factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(address: Int, factor: Float, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1f)
            return code
        if(factor==0f) {
            val maxvalueReg = vmRegisters.nextFreeFloat()
            code += IRCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1 = maxvalueReg, fpValue = Float.MAX_VALUE)
            code += IRCodeInstruction(Opcode.STOREM, VmDataType.FLOAT, fpReg1 = maxvalueReg, value=address)
        } else {
            val factorReg = vmRegisters.nextFreeFloat()
            code += IRCodeInstruction(Opcode.LOAD, VmDataType.FLOAT, fpReg1=factorReg, fpValue = factor)
            code += IRCodeInstruction(Opcode.DIVSM, VmDataType.FLOAT, fpReg1 = factorReg, value=address)
        }
        return code
    }

    internal fun divideByConst(dt: VmDataType, reg: Int, factor: Int, signed: Boolean, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1 && !signed) {
            code += IRCodeInstruction(Opcode.LSR, dt, reg1=reg)     // simple single bit shift
        }
        else if(pow2>=1 &&!signed) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += IRCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed)
                IRCodeInstruction(Opcode.ASRN, dt, reg1=reg, reg2=pow2reg)
            else
                IRCodeInstruction(Opcode.LSRN, dt, reg1=reg, reg2=pow2reg)
        } else {
            code += if (factor == 0) {
                IRCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
            } else {
                if(signed)
                    IRCodeInstruction(Opcode.DIVS, dt, reg1=reg, value=factor)
                else
                    IRCodeInstruction(Opcode.DIV, dt, reg1=reg, value=factor)
            }
        }
        return code
    }

    internal fun divideByConstInplace(dt: VmDataType, address: Int, factor: Int, signed: Boolean, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        if(factor==1)
            return code
        val pow2 = powersOfTwo.indexOf(factor)
        if(pow2==1 && !signed) {
            code += IRCodeInstruction(Opcode.LSRM, dt, value=address)       // just simple bit shift
        }
        else if(pow2>=1 && !signed) {
            // just shift multiple bits
            val pow2reg = vmRegisters.nextFree()
            code += IRCodeInstruction(Opcode.LOAD, dt, reg1=pow2reg, value=pow2)
            code += if(signed)
                IRCodeInstruction(Opcode.ASRNM, dt, reg1=pow2reg, value=address)
            else
                IRCodeInstruction(Opcode.LSRNM, dt, reg1=pow2reg, value=address)
        } else {
            if (factor == 0) {
                val reg = vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, dt, reg1=reg, value=0xffff)
                code += IRCodeInstruction(Opcode.STOREM, dt, reg1=reg, value=address)
            }
            else {
                val factorReg = vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, dt, reg1=factorReg, value= factor)
                code += if(signed)
                    IRCodeInstruction(Opcode.DIVSM, dt, reg1=factorReg, value=address)
                else
                    IRCodeInstruction(Opcode.DIVM, dt, reg1=factorReg, value=address)
            }
        }
        return code
    }

    private fun translate(ifElse: PtIfElse): IRCodeChunk {
        if(ifElse.condition.operator !in ComparisonOperators)
            throw AssemblyError("if condition should only be a binary comparison expression")

        val signed = ifElse.condition.left.type in arrayOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
        val vmDt = vmType(ifElse.condition.left.type)
        val code = IRCodeChunk(ifElse.position)

        fun translateNonZeroComparison(): IRCodeChunk {
            val elseBranch = when(ifElse.condition.operator) {
                "==" -> Opcode.BNE
                "!=" -> Opcode.BEQ
                "<" -> if(signed) Opcode.BGES else Opcode.BGE
                ">" -> if(signed) Opcode.BLES else Opcode.BLE
                "<=" -> if(signed) Opcode.BGTS else Opcode.BGT
                ">=" -> if(signed) Opcode.BLTS else Opcode.BLT
                else -> throw AssemblyError("invalid comparison operator")
            }

            val leftReg = vmRegisters.nextFree()
            val rightReg = vmRegisters.nextFree()
            code += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
            code += expressionEval.translateExpression(ifElse.condition.right, rightReg, -1)
            if(ifElse.elseScope.children.isNotEmpty()) {
                // if and else parts
                val elseLabel = createLabelName()
                val afterIfLabel = createLabelName()
                code += IRCodeInstruction(elseBranch, vmDt, reg1=leftReg, reg2=rightReg, labelSymbol = elseLabel)
                code += translateNode(ifElse.ifScope)
                code += IRCodeInstruction(Opcode.JUMP, labelSymbol = afterIfLabel)
                code += IRCodeLabel(elseLabel)
                code += translateNode(ifElse.elseScope)
                code += IRCodeLabel(afterIfLabel)
            } else {
                // only if part
                val afterIfLabel = createLabelName()
                code += IRCodeInstruction(elseBranch, vmDt, reg1=leftReg, reg2=rightReg, labelSymbol = afterIfLabel)
                code += translateNode(ifElse.ifScope)
                code += IRCodeLabel(afterIfLabel)
            }
            return code
        }

        fun translateZeroComparison(): IRCodeChunk {
            fun equalOrNotEqualZero(elseBranch: Opcode): IRCodeChunk {
                val leftReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(ifElse.condition.left, leftReg, -1)
                if(ifElse.elseScope.children.isNotEmpty()) {
                    // if and else parts
                    val elseLabel = createLabelName()
                    val afterIfLabel = createLabelName()
                    code += IRCodeInstruction(elseBranch, vmDt, reg1=leftReg, labelSymbol = elseLabel)
                    code += translateNode(ifElse.ifScope)
                    code += IRCodeInstruction(Opcode.JUMP, labelSymbol = afterIfLabel)
                    code += IRCodeLabel(elseLabel)
                    code += translateNode(ifElse.elseScope)
                    code += IRCodeLabel(afterIfLabel)
                } else {
                    // only if part
                    val afterIfLabel = createLabelName()
                    code += IRCodeInstruction(elseBranch, vmDt, reg1=leftReg, labelSymbol = afterIfLabel)
                    code += translateNode(ifElse.ifScope)
                    code += IRCodeLabel(afterIfLabel)
                }
                return code
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

    private fun translate(postIncrDecr: PtPostIncrDecr): IRCodeChunk {
        val code = IRCodeChunk(postIncrDecr.position)
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
        val vmDt = vmType(postIncrDecr.target.type)
        if(ident!=null) {
            val address = addressOf(ident.targetName)
            code += IRCodeInstruction(operationMem, vmDt, value = address)
        } else if(memory!=null) {
            if(memory.address is PtNumber) {
                val address = (memory.address as PtNumber).number.toInt()
                code += IRCodeInstruction(operationMem, vmDt, value = address)
            } else {
                val incReg = vmRegisters.nextFree()
                val addressReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(memory.address, addressReg, -1)
                code += IRCodeInstruction(Opcode.LOADI, vmDt, reg1 = incReg, reg2 = addressReg)
                code += IRCodeInstruction(operationRegister, vmDt, reg1 = incReg)
                code += IRCodeInstruction(Opcode.STOREI, vmDt, reg1 = incReg, reg2 = addressReg)
            }
        } else if (array!=null) {
            val variable = array.variable.targetName
            var variableAddr = addressOf(variable)
            val itemsize = program.memsizer.memorySize(array.type)
            val fixedIndex = constIntValue(array.index)
            if(fixedIndex!=null) {
                variableAddr += fixedIndex*itemsize
                code += IRCodeInstruction(operationMem, vmDt, value=variableAddr)
            } else {
                val incReg = vmRegisters.nextFree()
                val indexReg = vmRegisters.nextFree()
                code += expressionEval.translateExpression(array.index, indexReg, -1)
                code += IRCodeInstruction(Opcode.LOADX, vmDt, reg1=incReg, reg2=indexReg, value=variableAddr)
                code += IRCodeInstruction(operationRegister, vmDt, reg1=incReg)
                code += IRCodeInstruction(Opcode.STOREX, vmDt, reg1=incReg, reg2=indexReg, value=variableAddr)
            }
        } else
            throw AssemblyError("weird assigntarget")

        return code
    }

    private fun translate(repeat: PtRepeatLoop): IRCodeChunk {
        when (constIntValue(repeat.count)) {
            0 -> return IRCodeChunk(repeat.position)
            1 -> return translateGroup(repeat.children, repeat.position)
            256 -> {
                // 256 iterations can still be done with just a byte counter if you set it to zero as starting value.
                repeat.children[0] = PtNumber(DataType.UBYTE, 0.0, repeat.count.position)
            }
        }

        val code = IRCodeChunk(repeat.position)
        val counterReg = vmRegisters.nextFree()
        val vmDt = vmType(repeat.count.type)
        code += expressionEval.translateExpression(repeat.count, counterReg, -1)
        val repeatLabel = createLabelName()
        code += IRCodeLabel(repeatLabel)
        code += translateNode(repeat.statements)
        code += IRCodeInstruction(Opcode.DEC, vmDt, reg1=counterReg)
        code += IRCodeInstruction(Opcode.BNZ, vmDt, reg1=counterReg, labelSymbol = repeatLabel)
        return code
    }

    private fun translate(jump: PtJump): IRCodeChunk {
        val code = IRCodeChunk(jump.position)
        if(jump.address!=null)
            throw AssemblyError("cannot jump to memory location in the vm target")
        code += if(jump.generatedLabel!=null)
            IRCodeInstruction(Opcode.JUMP, labelSymbol = jump.generatedLabel!!)
        else if(jump.identifier!=null)
            IRCodeInstruction(Opcode.JUMP, labelSymbol = jump.identifier!!.targetName.joinToString("."))
        else
            throw AssemblyError("weird jump")
        return code
    }

    private fun translateGroup(group: List<PtNode>, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        group.forEach { code += translateNode(it) }
        return code
    }

    private fun translate(ret: PtReturn): IRCodeChunk {
        val code = IRCodeChunk(ret.position)
        val value = ret.value
        if(value!=null) {
            // Call Convention: return value is always returned in r0 (or fr0 if float)
            code += if(value.type==DataType.FLOAT)
                expressionEval.translateExpression(value, -1, 0)
            else
                expressionEval.translateExpression(value, 0, -1)
        }
        code += IRCodeInstruction(Opcode.RETURN)
        return code
    }

    private fun translate(block: PtBlock): IRBlock {
        val vmblock = IRBlock(block.name, block.address, translate(block.alignment), block.position)   // no use for other attributes yet?
        for (child in block.children) {
            when(child) {
                is PtNop -> { /* nothing */ }
                is PtAssignment -> { /* global variable initialization is done elsewhere */ }
                is PtScopeVarsDecls -> { /* vars should be looked up via symbol table */ }
                is PtSub -> {
                    val vmsub = IRSubroutine(child.name, translate(child.parameters), child.returntype, child.position)
                    for (subchild in child.children) {
                        val translated = translateNode(subchild)
                        if(translated.isNotEmpty())
                            vmsub += translated
                    }
                    vmblock += vmsub
                }
                is PtAsmSub -> {
                    val assembly = if(child.children.isEmpty()) "" else (child.children.single() as PtInlineAssembly).assembly
                    vmblock += IRAsmSubroutine(child.name, child.position, child.address,
                        child.clobbers,
                        child.parameters.map { Pair(it.first.type, it.second) },        // note: the name of the asmsub param is not used anymore.
                        child.returnTypes.zip(child.retvalRegisters),
                        assembly)
                }
                is PtInlineAssembly -> {
                    vmblock += IRInlineAsmChunk(child.assembly, child.position)
                }
                else -> TODO("BLOCK HAS WEIRD CHILD NODE $child")
            }
        }
        return vmblock
    }

    private fun translate(parameters: List<PtSubroutineParameter>) =
        parameters.map {
            val flattenedName = (it.definingSub()!!.scopedName + it.name)
            symbolTable.flat.getValue(flattenedName) as StStaticVariable
        }

    private fun translate(alignment: PtBlock.BlockAlignment): IRBlock.BlockAlignment {
        return when(alignment) {
            PtBlock.BlockAlignment.NONE -> IRBlock.BlockAlignment.NONE
            PtBlock.BlockAlignment.WORD -> IRBlock.BlockAlignment.WORD
            PtBlock.BlockAlignment.PAGE -> IRBlock.BlockAlignment.PAGE
        }
    }


    internal fun vmType(type: DataType): VmDataType {
        return when(type) {
            DataType.BOOL,
            DataType.UBYTE,
            DataType.BYTE -> VmDataType.BYTE
            DataType.UWORD,
            DataType.WORD -> VmDataType.WORD
            DataType.FLOAT -> VmDataType.FLOAT
            in PassByReferenceDatatypes -> VmDataType.WORD
            else -> throw AssemblyError("no vm datatype for $type")
        }
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): String {
        labelSequenceNumber++
        return "prog8_label_gen_$labelSequenceNumber"
    }

    internal fun translateBuiltinFunc(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk =
        builtinFuncGen.translate(call, resultRegister)

    internal fun isZero(expression: PtExpression): Boolean = expression is PtNumber && expression.number==0.0

    internal fun isOne(expression: PtExpression): Boolean = expression is PtNumber && expression.number==1.0

    fun addMemorySlab(name: String, size: UInt, align: UInt, position: Position): String {
        val scopedName = "prog8_memoryslab_$name"
        symbolTable.add(StMemorySlab(scopedName, size, align, null, position))
        return scopedName
    }
}
