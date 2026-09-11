package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.intermediate.*
import kotlin.io.path.readBytes

// NOTE: All symbol names in the generated IR are fully scoped (entire dotted path)
// and carry type prefixes: p8b_ (block), p8s_ (sub), p8v_ (variable), p8c_ (const),
// p8l_ (label), p8t_ (struct), p8i_ (instance). Any backend consuming the IR
// can rely on these already being present and does not need to add them itself.


class IRCodeGen(
    internal val program: PtProgram,
    internal var symbolTable: SymbolTable,
    internal val options: CompilationOptions,
    internal val errors: IErrorReporter,
    internal val retainSSA: Boolean,
    preassignedCallSiteIds: Map<String, UByte> = emptyMap()
) {

    private val expressionEval = ExpressionGen(this)
    private val builtinFuncGen = BuiltinFuncGen(this, expressionEval)
    private val assignmentGen = AssignmentGen(this, expressionEval)
    internal val registers = RegisterPool()
    internal val extsubCallSiteIds: MutableMap<String, UByte> = preassignedCallSiteIds.toMutableMap()
    var wasPackingApplied: Boolean = false
        private set

    // on 32-bit targets, LOADX/STOREX/STOREZX use a word index register (0-32767) instead of a byte (0-255)
    internal val wordArrayIndex: Boolean = options.compTarget.indexRegType == IRDataType.WORD

    /**
     * The IR data type to use for a memory address on the current target.
     * On 32-bit-pointer targets (VM) this is LONG (32-bit signed); on 16-bit-pointer
     * targets (6502) this is WORD (16-bit unsigned). Use this everywhere the IR
     * generator emits a register or argument that holds a memory address.
     */
    internal val addressDt: IRDataType
        get() = addressDtFor(options.compTarget)

    fun generate(): IRProgram {
        // The pure "virtual" (VM) target doesn't need symbol prefixing because the VM
        // doesn't emit assembly and has no risk of symbol clashes.  Any other target
        // (that goes through assembly code generation) MUST prefix symbols to avoid
        // clashes with 64tass reserved names, BASIC tokens, and other toolchain symbols.
        if(options.compTarget.name!=VMTarget.NAME) 
            symbolTable = prefixSymbols(program, options, symbolTable)
        makeAllNodenamesScoped(program)
        moveAllNestedSubroutinesToBlockScope(program)
        verifyNameScoping(program, symbolTable)
        changeGlobalVarInits(symbolTable)

        val irSymbolTable = convertStToIRSt(symbolTable, options.romable)
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

        // in romable mode, generate global init code for declaration-initialized variables
        // and clear their init value so they go into the BSS section
        if(options.romable) {
            generateRomableInits(irProg)
        }

        // generate global init code for string/array-initialized variables
        // (the existing mechanisms only handle numeric inits; string/array data stays
        //  in the INIT section declarations but no runtime copy instructions are emitted)
        generateStringArrayInits(irProg)

        irProg.addAsmSymbols(options.symbolDefs)

        for (block in program.allBlocks())
            irProg.addBlock(translate(block))

        replaceMemoryMappedVars(irProg)
        ensureFirstChunkLabels(irProg)
        irProg.linkChunks()
        irProg.convertAsmChunks()

        if(retainSSA)
            irProg.splitSSAchunks()

        // the optimizer also does 1 essential step regardless of optimizations: joining adjacent chunks.
        val optimizer = IRPeepholeOptimizer(irProg, retainSSA)
        optimizer.optimize(options.optimize, errors)

        // Register packing: reduce distinct virtual registers by coalescing non-overlapping live ranges.
        // Subroutines at different call depths should get disjoint slot ranges to avoid caller/callee
        // clashes. Currently DISABLED - see register-packing.md for the plan and what needs fixing.
        // TODO: re-enable once the depth-range approach is implemented.
        //if(options.optimize && options.compTarget.name!=VMTarget.NAME) {
        //    RegisterPacker.pack(irProg)
        //    registers.resetTypes(RegisterPacker.rebuildTypeMap(irProg))
        //    irProg.wasPackingApplied = true
        //    wasPackingApplied = true
        //}

        irProg.validate()

        return irProg
    }

    fun registerTypes(): Map<RegisterNum, IRDataType> = registers.getTypes()

    private fun changeGlobalVarInits(symbolTable: SymbolTable) {
        // In romable mode, don't pull initializers into the symbol table;
        // they stay as assignments in the AST and become global init code naturally.
        if(options.romable)
            return

        // Normally, block level (global) variables that have a numeric initialization value
        // are initialized via an assignment statement.
        val initsToRemove = mutableListOf<Pair<PtBlock, PtAssignment>>()

        symbolTable.allVariables.forEach { variable ->
            if(variable.uninitialized && variable.parent.type==StNodeType.BLOCK) {
                val block = variable.parent.astNode as PtBlock
                val initialization = (block.children.firstOrNull {
                    it is PtAssignment && it.isVarInitializer && it.target.identifier?.name==variable.scopedNameString
                } as PtAssignment?)
                when(val initValue = initialization?.value){
                    is PtBool -> {
                        require(initValue.asInt()!=0 || variable.zpwish!=ZeropageWish.NOT_IN_ZEROPAGE) { "non-zp variable should not be initialized with 0, it will already be zeroed as part of BSS clear, initializer=$initialization" }
                        variable.setOnetimeInitNumeric(initValue.asInt().toDouble())
                        initsToRemove += block to initialization
                    }
                    is PtNumber -> {
                        require(initValue.number!=0.0 || variable.zpwish!=ZeropageWish.NOT_IN_ZEROPAGE) { "non-zp variable should not be initialized with 0, it will already be zeroed as part of BSS clear, initializer=$initialization" }
                        variable.setOnetimeInitNumeric(initValue.number)
                        initsToRemove += block to initialization
                    }
                    is PtArray, is PtString -> throw AssemblyError("array or string initialization values should already be part of the vardecl, not a separate assignment")
                    else -> {}
                }
            }
        }

        for((block, assign) in initsToRemove) {
            block.removeChild(assign)
        }
    }

    private fun generateRomableInits(irProg: IRProgram) {
        // For romable mode, declaration-initialized variables marked inBss need
        // runtime init code in the globalInits chunk, and their initializationValue
        // must be cleared so they appear in the NOINIT (BSS) section of the IR file.
        val replacements = mutableListOf<IRStStaticVariable>()
        for(variable in irProg.st.allVariables()) {
            if(variable.inBss && variable.initializationValue != null) {
                when(val initValue = variable.initializationValue) {
                    is IRVariableInitializer.Numeric -> {
                        val dt = irType(variable.dt)
                        val chunk = IRCodeChunk(null, null)
                        chunk += IRInstructions.storeImmediate(dt, initValue.value.toInt(), IRMemory.direct(variable.name))
                        irProg.addGlobalInits(chunk)
                        // replace with uninitialized version so it goes to NOINIT section
                        replacements += IRStStaticVariable(
                            variable.name, variable.dt, null, variable.length,
                            variable.zpwish, variable.align, variable.dirty,
                            variable.inBss, variable.readonly
                        )
                    }
                    is IRVariableInitializer.Str, is IRVariableInitializer.Array -> {
                        // Strings and arrays: keep inline data but now marked readonly.
                        // They can serve as ROM-based read-only data.
                    }
                    null -> {}  // uninitialized, skip
                }
            }
        }
        replacements.forEach { irProg.st.add(it) }
    }

    private fun generateStringArrayInits(irProg: IRProgram) {
        // ZP variables with string/array init values need runtime copy code.
        // The VM's varsToMemory already initializes them from the INIT section,
        // but a 6502 backend needs explicit init instructions.
        //
        // Create a non-ZP shadow variable holding the init bytes, clear the
        // original variable's init value so it becomes uninitialized/BSS, then
        // emit a single MEMCOPY from the shadow to the ZP variable at startup.
        val chunk = IRCodeChunk(null, null)
        for(variable in irProg.st.allVariables().toList()) {
            if(variable.initializationValue == null)
                continue
            if(variable.inBss)
                continue
            if(variable.zpwish == ZeropageWish.DONTCARE || variable.zpwish == ZeropageWish.NOT_IN_ZEROPAGE)
                continue
            if(variable.dt.isSplitWordArray(irProg.options.compTarget))
                continue
            if(variable.initializationValue is IRVariableInitializer.Numeric)
                continue

            val initBytes = when(val initVal = variable.initializationValue) {
                is IRVariableInitializer.Str -> {
                    irProg.encoding.encodeString(initVal.text, initVal.encoding) + 0u
                }
                is IRVariableInitializer.Array -> {
                    val elemDt = variable.dt.elementType()
                    val elemByteSize = when {
                        elemDt.isByte || elemDt.isBool -> 1
                        elemDt.isWord || elemDt.isPointer -> 2
                        else -> -1
                    }
                    if(elemByteSize < 0)
                        continue
                    val bytes = mutableListOf<UByte>()
                    for(element in initVal.elements) {
                        when(element) {
                            is IRStSymbolicReference.Numeric -> {
                                val value = element.value.toInt()
                                when(elemByteSize) {
                                    1 -> bytes += value.toUByte()
                                    2 -> {
                                        bytes += (value and 0xFF).toUByte()
                                        bytes += ((value shr 8) and 0xFF).toUByte()
                                    }
                                }
                            }
                            is IRStSymbolicReference.BoolValue -> {
                                bytes += (if(element.value) 1u else 0u).toUByte()
                            }
                            is IRStSymbolicReference.Symbol -> {
                                bytes.clear()
                                break
                            }
                        }
                    }
                    if(bytes.isEmpty())
                        continue
                    bytes
                }
                else -> continue
            }

            if(initBytes.isEmpty())
                continue

            val shadowName = variable.name + "_init_value"
            val shadowInit = IRVariableInitializer.Array(initBytes.map { IRStSymbolicReference.Numeric(it.toDouble()) })
            val shadowVar = IRStStaticVariable(
                shadowName,
                DataType.arrayFor(BaseDataType.UBYTE, irProg.options.compTarget),
                shadowInit,
                initBytes.size.toUInt(),
                ZeropageWish.NOT_IN_ZEROPAGE,
                0u,
                false,
                inBss = false,
                readonly = false
            )
            irProg.st.add(shadowVar)

            // Clear the original variable's init value so it becomes uninitialized (BSS/NOINIT).
            // The runtime copy from the shadow variable will provide the actual initial bytes.
            val clearedVar = IRStStaticVariable(
                variable.name,
                variable.dt,
                null,
                variable.length,
                variable.zpwish,
                variable.align,
                variable.dirty,
                variable.inBss,
                variable.readonly
            )
            irProg.st.add(clearedVar)

            val addressDt = addressDtFor(irProg.options.compTarget)
            val sourceReg = registers.next(addressDt)
            val destReg = registers.next(addressDt)
            val countReg = registers.next(IRDataType.WORD)
            chunk += IRInstructions.loadAddress(addressDt, sourceReg, shadowName)
            chunk += IRInstructions.loadAddress(addressDt, destReg, variable.name)
            chunk += IRInstructions.load(IRDataType.WORD, countReg, initBytes.size)
            val args = listOf(
                Calls.argument(sourceReg, addressDt),
                Calls.argument(destReg, addressDt),
                Calls.argument(countReg, IRDataType.WORD)
            )
            chunk += IRInstructions.syscall(IMSyscall.MEMCOPY.number, args, emptyList())
        }

        if(chunk.isNotEmpty())
            irProg.addGlobalInits(chunk)
    }

    private fun verifyNameScoping(program: PtProgram, symbolTable: SymbolTable) {
        fun verifyPtNode(node: PtNode) {
            when (node) {
                is PtFunctionCall -> {
                    if(node.builtin)
                        require('.' !in node.name) { "builtin function call name should not be scoped: ${node.name}" }
                    else
                        require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                }
                is PtAsmSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtBlock -> require('.' !in node.name) { "block name should not be scoped: ${node.name}" }
                is PtConstant -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtLabel -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtMemMapped -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtSub -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtVariable -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtProgram -> require('.' !in node.name) { "program name should not be scoped: ${node.name}" }
                is PtSubroutineParameter -> require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                is PtPointerDeref -> require('.' in node.startpointer.name) { "node $node name is not scoped: ${node.startpointer.name}" }
                is PtIdentifier -> {
                    if('.' !in node.name) {
                        // there are 2 cases where the identifier is not scoped:
                        // 1) it is the value field name after a pointer array indexing.
                        // 2) it is the operand of a PtAddressOf referring to a block name.
                        val expr = node.parent as? PtBinaryExpression
                        val isFieldOfPointerArrayElement = expr?.operator == "." && expr.right === node
                                && expr.left is PtArrayIndexer
                                && (expr.left.type.isPointer || expr.left.type.isStructInstance)
                        val isAddressOfBlock = node.parent is PtAddressOf
                                && symbolTable.lookup(node.name)?.type == StNodeType.BLOCK
                        if(!isFieldOfPointerArrayElement && !isAddressOfBlock)
                            require('.' in node.name) { "node $node name is not scoped: ${node.name}" }
                    }
                }
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
                        val replacement: IRCodeChunkBase = when(first) {
                            is IRCodeChunk -> {
                                val replacement = IRCodeChunk(sub.label, first.next)
                                replacement.instructions += first.instructions
                                replacement.appendSrcPositions(first.sourceLinesPositions)
                                replacement
                            }
                            is IRInlineAsmChunk -> IRInlineAsmChunk(sub.label, first.assembly, first.isIR, first.next)
                            is IRInlineBinaryChunk -> IRInlineBinaryChunk(sub.label, first.data, first.next)
                            is IRLoopChunk -> IRLoopChunk(sub.label, first.trip, first.body, first.next)
                        }
                        sub.chunks.removeAt(0)
                        sub.chunks.add(0, replacement)
                    } else if(first.label != sub.label) {
                        sub.chunks.add(0, IRCodeChunk(sub.label, first))
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
                    val symbolExpr = instr.memory?.symbolName ?: (instr.immediate as? ImmediateOperand.SymbolAddress)?.symbol
                    if(symbolExpr!=null) {
                        val index = when(val mem = instr.memory) {
                            is MemoryReference.Direct -> mem.displacement
                            is MemoryReference.Indexed -> mem.displacement
                            is MemoryReference.Indirect -> 0
                            null -> (instr.immediate as ImmediateOperand.SymbolAddress).offset
                        }
                        val target = symbolTable.flat[symbolExpr]
                        if (target is StMemVar) {
                            replacements.add(Triple(chunk, idx, target.address+index.toUInt()))
                        }
                    }
                }
        }

        replacements.forEach {
            val old = it.first.instructions[it.second]
            val address = it.third
            val newInstr = if(old.memory!=null) {
                // keep index register, scale and any other operands intact; only the symbolic base changes
                old.mapMemoryReferences { mem ->
                    when(mem) {
                        is MemoryReference.Direct -> MemoryReference.Direct(AddressBase.Absolute(address.toAddress()))
                        is MemoryReference.Indexed -> MemoryReference.Indexed(AddressBase.Absolute(address.toAddress()), mem.index, mem.scale)
                        is MemoryReference.Indirect -> mem
                    }
                }
            } else {
                val symbolType = (old.immediate as ImmediateOperand.SymbolAddress).type
                old.copy(immediate = ImmediateOperand.Integer(address.toInt(), symbolType))
            }
            it.first.instructions[it.second] = newInstr
        }
    }

    internal fun translateNode(node: PtNode): IRCodeChunks {
        val chunks = when(node) {
            is PtVariable -> emptyList() // var should be looked up via symbol table
            is PtMemMapped -> emptyList() // memmapped var should be looked up via symbol table
            is PtConstant -> emptyList() // constants have all been folded into the code
            is PtMemorySlabReservation -> emptyList() // handled via symbol table
            is PtAssignment -> assignmentGen.translate(node)
            is PtAugmentedAssign -> assignmentGen.translate(node)
            is PtNodeGroup -> translateGroup(node.children)
            is PtFunctionCall -> expressionEval.translate(node, false).chunks   // it's not an expression so no result value
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
                chunk += IRInstructions.simple(Opcode.BREAKPOINT)
                listOf(chunk)
            }
            is PtAlign -> {
                val chunk = IRCodeChunk(null, null)
                chunk += IRInstructions.align(node.align.toInt())
                listOf(chunk)
            }
            is PtConditionalBranch -> translate(node)
            is PtSwap -> translate(node)
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
            is PtDefer -> throw AssemblyError("defer should have been transformed")
            is PtString -> throw AssemblyError("string should not occur as separate statement node $node")
            is PtSub -> throw AssemblyError("nested subroutines should have been flattened $node")
            is PtStructDecl -> emptyList()
            is PtSubSignature -> emptyList()
            else -> TODO("missing codegen for $node  ${node.position}")
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
            val address: UInt? = goto.target.asConstInteger()?.toUInt()
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
                    it += IRInstructions.jumpIndirect(tr.resultReg)
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
            addInstr(result, IRInstructions.jump(codeLabel(endLabel)), null)
            val chunks = translateNode(branch.falseScope)
            result += labelFirstChunk(chunks, elseLabel)
            result += IRCodeChunk(endLabel, null)
        } else {
            result += IRCodeChunk(elseLabel, null)
        }
        return result
    }

    internal fun IRBranchInstr(condition: BranchCondition, label: String?=null, address: UInt?=null): IRInstruction {
        val target = branchTarget(label, address)
        val opcode = when(condition) {
            BranchCondition.CS -> Opcode.BSTCS
            BranchCondition.CC -> Opcode.BSTCC
            BranchCondition.EQ, BranchCondition.Z -> Opcode.BSTEQ
            BranchCondition.NE, BranchCondition.NZ -> Opcode.BSTNE
            BranchCondition.MI, BranchCondition.NEG -> Opcode.BSTNEG
            BranchCondition.PL, BranchCondition.POS -> Opcode.BSTPOS
            BranchCondition.VC -> Opcode.BSTVC
            BranchCondition.VS -> Opcode.BSTVS
        }
        return IRInstructions.branch(opcode, target)
    }

    private fun IRInvertedBranchInstr(condition: BranchCondition, label: String?=null, address: UInt?=null): IRInstruction {
        val target = branchTarget(label, address)
        val opcode = when(condition) {
            BranchCondition.CS -> Opcode.BSTCC
            BranchCondition.CC -> Opcode.BSTCS
            BranchCondition.EQ, BranchCondition.Z -> Opcode.BSTNE
            BranchCondition.NE, BranchCondition.NZ -> Opcode.BSTEQ
            BranchCondition.MI, BranchCondition.NEG -> Opcode.BSTPOS
            BranchCondition.PL, BranchCondition.POS -> Opcode.BSTNEG
            BranchCondition.VC -> Opcode.BSTVS
            BranchCondition.VS -> Opcode.BSTVC
        }
        return IRInstructions.branch(opcode, target)
    }

    private fun branchTarget(label: String?, address: UInt?): CodeReference = when {
        label!=null -> codeLabel(label)
        address!=null -> codeAddress(address)
        else -> throw AssemblyError("need label or address for branch")
    }

    private fun branchTarget(label: String?, address: MemoryAddress?): CodeReference = when {
        label!=null -> codeLabel(label)
        address!=null -> codeAddress(address)
        else -> throw AssemblyError("need label or address for branch")
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
        val labeledFirstChunk: IRCodeChunkBase = when(first) {
            is IRCodeChunk -> {
                val newChunk = IRCodeChunk(label, first.next)
                newChunk.instructions += first.instructions
                newChunk.appendSrcPositions(first.sourceLinesPositions)
                newChunk
            }
            is IRInlineAsmChunk -> {
                IRInlineAsmChunk(label, first.assembly, first.isIR, first.next)
            }
            is IRInlineBinaryChunk -> {
                IRInlineBinaryChunk(label, first.data, first.next)
            }
            is IRLoopChunk -> {
                // transplant label: create wrapper chunk that falls through to loop
                IRCodeChunk(label, first)
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
                require(choice.parent.children.last() === choice)
                result += translateNode(choice.statements)
                // is always the last node so can fall through
            } else {
                if(choice.statements.children.isEmpty()) {
                    // no statements for this choice value, jump to the end immediately
                    choice.values.children.map { v -> v as PtNumber }.sortedBy { v -> v.number }.forEach { value ->
                        result += IRCodeChunk(null, null).also { chunk ->
                            chunk += IRInstructions.compareImmediate(valueDt, valueTr.resultReg, value.number.toInt())
                            chunk += IRInstructions.branch(Opcode.BSTEQ, codeLabel(endLabel))
                        }
                    }
                } else {
                    val choiceLabel = createLabelName()
                    val onlyJumpLabel = ((choice.statements.children.singleOrNull() as? PtJump)?.target as? PtIdentifier)?.name
                    val branchLabel: String
                    if(onlyJumpLabel==null) {
                        choices.add(choiceLabel to choice)
                        branchLabel = choiceLabel
                    } else {
                        branchLabel = onlyJumpLabel
                    }
                    choice.values.children.map { v -> v as PtNumber }.sortedBy { v -> v.number }.forEach { value ->
                        result += IRCodeChunk(null, null).also { chunk ->
                            chunk += IRInstructions.compareImmediate(valueDt, valueTr.resultReg, value.number.toInt())
                            chunk += IRInstructions.branch(Opcode.BSTEQ, codeLabel(branchLabel))
                        }
                    }
                }
            }
        }

        if(choices.isNotEmpty())
            addInstr(result, IRInstructions.jump(codeLabel(endLabel)), null)

        choices.forEach { (label, choice) ->
            result += labelFirstChunk(translateNode(choice.statements), label)
            if(!choice.isOnlyGotoOrReturn())
                addInstr(result, IRInstructions.jump(codeLabel(endLabel)), null)
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
                result += if(iterable.from is PtNumber && iterable.to is PtNumber && iterable.step is PtNumber)
                    translateForInConstantRange(forLoop, loopvar)
                else
                    translateForInNonConstantRange(forLoop, loopvar)
            }
            is PtIdentifier -> {
                require(forLoop.variable.name == loopvar.scopedNameString)
                val elementDt = irType(iterable.type.elementType())
                val iterableLength = symbolTable.getLength(iterable.name)
                val loopvarSymbol = forLoop.variable.name
                val loopvarDt = irType((loopvar.astNode as IPtVariable).type)
                val needsWidening = loopvarDt > elementDt && elementDt != IRDataType.FLOAT
                val indexRegType = if(wordArrayIndex) IRDataType.WORD else IRDataType.BYTE
                val indexReg = registers.next(indexRegType)
                val tmpReg = registers.next(elementDt)
                val loopLabel = createLabelName()
                val endLabel = createLabelName()

                // EXT only extends one step: BYTE->WORD or WORD->LONG
                // for BYTE->LONG we use EXTL (single step)
                fun emitWidening(chunk: IRCodeChunk, srcReg: Int, srcDt: IRDataType): Pair<Int, IRDataType> {
                    if(!needsWidening) return Pair(srcReg, srcDt)
                    if(loopvarDt == IRDataType.LONG && srcDt == IRDataType.BYTE) {
                        val longReg = registers.next(IRDataType.LONG)
                        chunk += IRInstructions.binary(Opcode.EXTL, srcDt, longReg, srcReg)
                        return Pair(longReg, IRDataType.LONG)
                    }
                    val wordReg = registers.next(IRDataType.WORD)
                    chunk += IRInstructions.binary(Opcode.EXT, srcDt, wordReg, srcReg)
                    return Pair(wordReg, loopvarDt)
                }

                when {
                    iterable.type.isString -> {
                        addInstr(result, IRInstructions.load(indexRegType, indexReg, 0), null)
                        val loopChunk = IRCodeChunk(loopLabel, null)
                        loopChunk += IRInstructions.loadMemory(Opcode.LOADX, elementDt, tmpReg, IRMemory.indexed(iterable.name, indexReg, options.compTarget.indexRegType))
                        if(!options.compTarget.cpu.statusBitsOnMultiByteOps)
                            loopChunk += IRInstructions.compareImmediate(elementDt, tmpReg, 0)
                        loopChunk += IRInstructions.branch(Opcode.BSTEQ, codeLabel(endLabel))
                        val (storeReg, storeDt) = emitWidening(loopChunk, tmpReg, elementDt)
                        loopChunk += IRInstructions.storeMemory(Opcode.STOREM, storeDt, storeReg, IRMemory.direct(loopvarSymbol))
                        result += loopChunk
                        result += translateNode(forLoop.statements)
                        val jumpChunk = IRCodeChunk(null, null)
                        jumpChunk += IRInstructions.unary(Opcode.INC, indexRegType, indexReg)
                        jumpChunk += IRInstructions.jump(codeLabel(loopLabel))
                        result += jumpChunk
                        result += IRCodeChunk(endLabel, null)
                    }
                    iterable.type.isSplitWordArray(options.compTarget) -> {
                        if(elementDt!=IRDataType.WORD && elementDt!=IRDataType.POINTER)
                            throw AssemblyError("weird dt $elementDt")
                        addInstr(result, IRInstructions.load(indexRegType, indexReg, 0), null)
                        val loopChunk = IRCodeChunk(loopLabel, null)
                        val tmpRegLsb = registers.next(IRDataType.BYTE)
                        val tmpRegMsb = registers.next(IRDataType.BYTE)
                        val concatReg = registers.next(IRDataType.WORD)
                        loopChunk += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, tmpRegMsb, IRMemory.indexed(iterable.name+"_msb", indexReg, options.compTarget.indexRegType))
                        loopChunk += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, tmpRegLsb, IRMemory.indexed(iterable.name+"_lsb", indexReg, options.compTarget.indexRegType))
                        loopChunk += IRInstructions.concat(IRDataType.BYTE, concatReg, tmpRegMsb, tmpRegLsb)
                        val (storeReg, storeDt) = emitWidening(loopChunk, concatReg, IRDataType.WORD)
                        loopChunk += IRInstructions.storeMemory(Opcode.STOREM, storeDt, storeReg, IRMemory.direct(loopvarSymbol))
                        result += loopChunk
                        result += translateNode(forLoop.statements)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstructions.unary(Opcode.INC, indexRegType, indexReg)
                            if(iterableLength!=256 || indexRegType==IRDataType.WORD)
                                it += IRInstructions.compareImmediate(indexRegType, indexReg, iterableLength!!)
                            it += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
                        }
                    }
                    else -> {
                        val arrElementDt = iterable.type.elementType()
                        val elementSize = program.memsizer.memorySize(arrElementDt, null)
                        val arrElementIR = irType(arrElementDt)
                        addInstr(result, IRInstructions.load(indexRegType, indexReg, 0), null)
                        val loopChunk = IRCodeChunk(loopLabel, null)
                        loopChunk += IRInstructions.loadMemory(Opcode.LOADX, arrElementIR, tmpReg, IRMemory.indexed(iterable.name, indexReg, options.compTarget.indexRegType, scale=elementSize))
                        val (storeReg, storeDt) = emitWidening(loopChunk, tmpReg, arrElementIR)
                        loopChunk += IRInstructions.storeMemory(Opcode.STOREM, storeDt, storeReg, IRMemory.direct(loopvarSymbol))
                        result += loopChunk
                        result += translateNode(forLoop.statements)
                        result += addConstToReg(indexReg, 1, indexRegType)
                        result += IRCodeChunk(null, null).also {
                            if(iterableLength!=256 || indexRegType==IRDataType.WORD)
                                it += IRInstructions.compareImmediate(indexRegType, indexReg, iterableLength!!)
                            it += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
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
        val step = iterable.step.asConstInteger()
        if (step==0)
            throw AssemblyError("step 0")
        require(forLoop.variable.name == loopvar.scopedNameString)
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
            addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            result += addConstMem(loopvarDtIr, null, loopvarSymbol, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol))
                it += IRInstructions.compareImmediate(loopvarDtIr, fromTr.resultReg, 255)
                it += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
            }
        }
        else if(step==-1 && iterable.to.asConstInteger()==1) {
            // downto 1 optimization (byte and word)
            val fromTr = expressionEval.translateExpression(iterable.from)
            addToResult(result, fromTr, fromTr.resultReg, -1)
            addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            result += addConstMem(loopvarDtIr, null, loopvarSymbol, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol))
                // Emit explicit CMPI #0 before the BSTNE branch on 8-bit targets where
                // LOADM doesn't reliably set Z for multi-byte results. Skip on targets
                // that honor the contract (e.g. M68000). See CpuType.statusBitsOnMultiByteOps.
                if(!options.compTarget.cpu.statusBitsOnMultiByteOps) {
                    it += IRInstructions.compareImmediate(loopvarDtIr, fromTr.resultReg, 0)
                }
                it += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
            }
        }
        else {
            if(step==null)
                return translateForInVariableStepRange(forLoop, loopvar)

            val fromTr = expressionEval.translateExpression(iterable.from)
            addToResult(result, fromTr, fromTr.resultReg, -1)
            val toTr = expressionEval.translateExpression(iterable.to)
            addToResult(result, toTr, toTr.resultReg, -1)

            val labelAfterFor = createLabelName()

            val precheckInstruction = if(loopvarDt.isSigned) {
                if(step>0)
                    IRInstructions.branchRegister(Opcode.BGTSR, loopvarDtIr, fromTr.resultReg, toTr.resultReg, codeLabel(labelAfterFor))
                else
                    IRInstructions.branchRegister(Opcode.BGTSR, loopvarDtIr, toTr.resultReg, fromTr.resultReg, codeLabel(labelAfterFor))
            } else {
                if(step>0)
                    IRInstructions.branchRegister(Opcode.BGTR, loopvarDtIr, fromTr.resultReg, toTr.resultReg, codeLabel(labelAfterFor))
                else
                    IRInstructions.branchRegister(Opcode.BGTR, loopvarDtIr, toTr.resultReg, fromTr.resultReg, codeLabel(labelAfterFor))
            }
            addInstr(result, precheckInstruction, null)

            // For step +-1 on scalar integer types, transform to an increment-then-compare
            // against an end-exclusive value computed once in a typed register. This mirrors
            // the 6502 backend optimization and eliminates the trailing JUMP and the extra
            // LOADM per iteration. Pointer loop variables keep the original shape.
            val useEndExclusive = (step==1 || step==-1) && loopvarDtIr != IRDataType.POINTER
            if(useEndExclusive) {
                // Emit the end-bump and the initial store each in their own labeled chunk.
                // This prevents chunk joining from merging them, which would break M4's
                // single-instruction store pattern.
                val endBumpLabel = createLabelName()
                result += IRCodeChunk(endBumpLabel, null).also {
                    it += if(step==1)
                        IRInstructions.unary(Opcode.INC, loopvarDtIr, toTr.resultReg)
                    else
                        IRInstructions.unary(Opcode.DEC, loopvarDtIr, toTr.resultReg)
                }
                val storeLabel = createLabelName()
                result += IRCodeChunk(storeLabel, null).also {
                    it += IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol))
                }
            } else {
                addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
            }
            result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
            if(useEndExclusive) {
                // Keep the increment and compare in separate labeled/unlabeled chunks so M4 can
                // recognize the new tail shape after chunk joining.
                val incLabel = createLabelName()
                result += IRCodeChunk(incLabel, null).also {
                    it.instructions += addConstMem(loopvarDtIr, null, loopvarSymbol, step).instructions
                }
                val cmpLabel = createLabelName()
                result += IRCodeChunk(cmpLabel, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol))
                    it += IRInstructions.compare(loopvarDtIr, toTr.resultReg, fromTr.resultReg)
                    it += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
                }
            } else if(step==1 || step==-1) {
                // if endvalue == loopvar, stop loop, else iterate
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol))
                    it += IRInstructions.compare(loopvarDtIr, toTr.resultReg, fromTr.resultReg)
                    it += IRInstructions.branch(Opcode.BSTEQ, codeLabel(labelAfterFor))
                }
                result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
                addInstr(result, IRInstructions.jump(codeLabel(loopLabel)), null)
            } else {
                // ind/dec index, then:
                // ascending: if endvalue >= loopvar, iterate
                // descending: if loopvar >= endvalue, iterate
                val previousReg = registers.next(loopvarDtIr)
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, previousReg, IRMemory.direct(loopvarSymbol)), null)
                result += addConstMem(loopvarDtIr, null, loopvarSymbol, step)
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
                val compareOpcode = if(loopvarDt.isSigned) Opcode.BGTSR else Opcode.BGTR
                if(step > 0) {
                    addInstr(result, IRInstructions.branchRegister(compareOpcode, loopvarDtIr, previousReg, fromTr.resultReg, codeLabel(labelAfterFor)), null)
                    addInstr(result, IRInstructions.branchRegister(compareOpcode, loopvarDtIr, fromTr.resultReg, toTr.resultReg, codeLabel(labelAfterFor)), null)
                } else {
                    addInstr(result, IRInstructions.branchRegister(compareOpcode, loopvarDtIr, fromTr.resultReg, previousReg, codeLabel(labelAfterFor)), null)
                    addInstr(result, IRInstructions.branchRegister(compareOpcode, loopvarDtIr, toTr.resultReg, fromTr.resultReg, codeLabel(labelAfterFor)), null)
                }
                addInstr(result, IRInstructions.jump(codeLabel(loopLabel)), null)
            }
            result += IRCodeChunk(labelAfterFor, null)
        }
        return result
    }

    private fun translateForInVariableStepRange(forLoop: PtForLoop, loopvar: StNode): IRCodeChunks {
        val iterable = forLoop.iterable as PtRange
        require(forLoop.variable.name == loopvar.scopedNameString)
        val loopvarSymbol = forLoop.variable.name
        val loopvarDt = when(loopvar) {
            is StMemVar -> loopvar.dt
            is StStaticVariable -> loopvar.dt
            else -> throw AssemblyError("invalid loopvar node type")
        }
        val loopvarDtIr = irType(loopvarDt)
        val result = mutableListOf<IRCodeChunkBase>()

        val labelAfterFor = createLabelName()
        val loopLabel = createLabelName()
        val labelStoreNext = createLabelName()
        val signedStep = iterable.step.type.isSigned
        val labelDescending = if(signedStep) createLabelName() else null
        val labelDescendingTail = if(signedStep) createLabelName() else null

        // Evaluate bounds and step once, in source order. The step register stays live throughout
        // because the register pool is monotonic (never reuses registers).
        val fromTr = expressionEval.translateExpression(iterable.from)
        addToResult(result, fromTr, fromTr.resultReg, -1)
        val toTr = expressionEval.translateExpression(iterable.to)
        addToResult(result, toTr, toTr.resultReg, -1)
        val stepTr = expressionEval.translateExpression(iterable.step)
        addToResult(result, stepTr, stepTr.resultReg, -1)
        val stepReg = when {
            stepTr.dt == loopvarDtIr -> stepTr.resultReg
            stepTr.dt == IRDataType.WORD && loopvarDtIr == IRDataType.LONG && !iterable.step.type.isSigned -> {
                val widenedReg = registers.next(IRDataType.LONG)
                addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.WORD, widenedReg, stepTr.resultReg), null)
                widenedReg
            }
            else -> throw AssemblyError("unexpected normalized loop step ${stepTr.dt} for $loopvarDtIr")
        }

        // step == 0 => empty loop
        addInstr(result, IRInstructions.compareImmediate(loopvarDtIr, stepReg, 0), null)
        addInstr(result, IRInstructions.branch(Opcode.BSTEQ, codeLabel(labelAfterFor)), null)

        // Determine direction from step sign
        if(signedStep)
            addInstr(result, IRInstructions.branch(Opcode.BSTNEG, codeLabel(labelDescending!!)), null)

        val precheckOpcode = if(loopvarDt.isSigned) Opcode.BGTSR else Opcode.BGTR

        val directionReg = if(signedStep) registers.next(IRDataType.BYTE) else null
        val currentReg = registers.next(loopvarDtIr)
        val nextReg = registers.next(loopvarDtIr)

        if(signedStep)
            addInstr(result, IRInstructions.load(IRDataType.BYTE, directionReg!!, 0), null)
        addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, fromTr.resultReg, toTr.resultReg, codeLabel(labelAfterFor)), null)
        addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
        addInstr(result, IRInstructions.jump(codeLabel(loopLabel)), null)

        if(signedStep) {
            result += IRCodeChunk(labelDescending!!, null)
            addInstr(result, IRInstructions.load(IRDataType.BYTE, directionReg!!, 1), null)
            addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, toTr.resultReg, fromTr.resultReg, codeLabel(labelAfterFor)), null)
            addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, fromTr.resultReg, IRMemory.direct(loopvarSymbol)), null)
        }

        result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
        addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, currentReg, IRMemory.direct(loopvarSymbol)), null)
        addInstr(result, IRInstructions.move(loopvarDtIr, nextReg, currentReg), null)
        addInstr(result, IRInstructions.binary(Opcode.ADDR, loopvarDtIr, nextReg, stepReg), null)

        if(signedStep) {
            addInstr(result, IRInstructions.compareImmediate(IRDataType.BYTE, directionReg!!, 0), null)
            addInstr(result, IRInstructions.branch(Opcode.BSTNE, codeLabel(labelDescendingTail!!)), null)
        }

        // Ascending: wrapping makes next smaller than current; otherwise
        // next must not exceed the upper bound.
        addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, currentReg, nextReg, codeLabel(labelAfterFor)), null)
        addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, nextReg, toTr.resultReg, codeLabel(labelAfterFor)), null)
        addInstr(result, IRInstructions.jump(codeLabel(labelStoreNext)), null)

        if(signedStep) {
            result += IRCodeChunk(labelDescendingTail!!, null)
            // Descending: wrapping makes next larger than current; otherwise
            // next must not fall below the lower bound.
            addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, nextReg, currentReg, codeLabel(labelAfterFor)), null)
            addInstr(result, IRInstructions.branchRegister(precheckOpcode, loopvarDtIr, toTr.resultReg, nextReg, codeLabel(labelAfterFor)), null)
            addInstr(result, IRInstructions.jump(codeLabel(labelStoreNext)), null)
        }

        result += IRCodeChunk(labelStoreNext, null)
        addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, loopvarDtIr, nextReg, IRMemory.direct(loopvarSymbol)), null)
        addInstr(result, IRInstructions.jump(codeLabel(loopLabel)), null)

        result += IRCodeChunk(labelAfterFor, null)
        return result
    }

    private fun isLoopVarUsed(forLoop: PtForLoop, loopvarSymbol: String): Boolean {
        fun recurse(node: PtNode): Boolean {
            if(node is PtIdentifier && node.name == loopvarSymbol) return true
            for(child in node.children) if(recurse(child)) return true
            return false
        }
        return recurse(forLoop.statements)
    }

    private fun translateForInConstantRange(forLoop: PtForLoop, loopvar: StNode): IRCodeChunks {
        val loopLabel = createLabelName()
        require(forLoop.variable.name == loopvar.scopedNameString)
        val loopvarSymbol = forLoop.variable.name
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
        // Eligibility for counted IR loop: constant bounds, step +-1, loop var not read inside body, trip 1..65536 (spec 8.6)
        if(iterable.step==1 || iterable.step==-1) {
            val trip = iterable.count()
            if(trip in 1..65536 && !isLoopVarUsed(forLoop, loopvarSymbol)) {
                val bodyChunks = translateNode(forLoop.statements).toMutableList()
                return listOf(IRLoopChunk(loopLabel, trip, bodyChunks, null))
            }
        }
        val rangeEndExclusiveUntyped = iterable.last + iterable.step
        val rangeEndExclusiveWrapped =
            when (loopvarDtIr) {
                IRDataType.BYTE -> rangeEndExclusiveUntyped and 255
                IRDataType.WORD -> rangeEndExclusiveUntyped and 65535
                else -> rangeEndExclusiveUntyped
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val indexReg = registers.next(loopvarDtIr)
        val chunk = IRCodeChunk(null, null)
        chunk += IRInstructions.storeImmediate(loopvarDtIr, iterable.first, IRMemory.direct(loopvarSymbol))
        result += chunk
        result += labelFirstChunk(translateNode(forLoop.statements), loopLabel)
        val chunk2 = addConstMem(loopvarDtIr, null, loopvarSymbol, iterable.step)
        if(loopvarDtIr==IRDataType.BYTE && iterable.step==-1 && iterable.last==0) {
            // downto 0 optimization (byte)
            // Only rely on DECM setting status bits on targets where it actually does (6502 hardware,
            // or targets that honor the strict multi-byte status-bits contract). On the VM DECM
            // leaves flags untouched, so BSTPOS would read stale state and loop forever.
            if((loopvarDt.isSignedByte || iterable.first<=127) && (options.compTarget.cpu.is6502 || options.compTarget.cpu.statusBitsOnMultiByteOps)) {
                chunk2 += IRInstructions.branch(Opcode.BSTPOS, codeLabel(loopLabel))
            } else {
                chunk2 += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, indexReg, IRMemory.direct(loopvarSymbol))
                chunk2 += IRInstructions.compareImmediate(loopvarDtIr, indexReg, rangeEndExclusiveWrapped)
                chunk2 += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
            }
        }
        else if(iterable.step==-1 && iterable.last==1) {
            // downto 1 optimization (byte and word)
            // On 8-bit targets the preceding addConstMem emitted a DECM which doesn't
            // reliably set Z; we must explicitly test the loop var. Skip the explicit
            // CMPI on targets that honor the contract (e.g. M68000).
            // See CpuType.statusBitsOnMultiByteOps.
            chunk2 += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, indexReg, IRMemory.direct(loopvarSymbol))
            if(!options.compTarget.cpu.statusBitsOnMultiByteOps) {
                chunk2 += IRInstructions.compareImmediate(loopvarDtIr, indexReg, 0)
            }
            chunk2 += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
        } else {
            // downto some other value
            chunk2 += IRInstructions.loadMemory(Opcode.LOADM, loopvarDtIr, indexReg, IRMemory.direct(loopvarSymbol))
            chunk2 += IRInstructions.compareImmediate(loopvarDtIr, indexReg, rangeEndExclusiveWrapped)
            chunk2 += IRInstructions.branch(Opcode.BSTNE, codeLabel(loopLabel))
        }
        result += chunk2
        return result
    }

    private fun addConstToReg(reg: Int, value: Int, dt: IRDataType): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += IRInstructions.unary(Opcode.INC, dt, reg)
            }
            2 -> {
                code += IRInstructions.unary(Opcode.INC, dt, reg)
                code += IRInstructions.unary(Opcode.INC, dt, reg)
            }
            -1 -> {
                code += IRInstructions.unary(Opcode.DEC, dt, reg)
            }
            -2 -> {
                code += IRInstructions.unary(Opcode.DEC, dt, reg)
                code += IRInstructions.unary(Opcode.DEC, dt, reg)
            }
            else -> {
                code += if(value>0) {
                    IRInstructions.binaryImmediate(Opcode.ADD, dt, reg, value)
                } else {
                    IRInstructions.binaryImmediate(Opcode.SUB, dt, reg, -value)
                }
            }
        }
        return code
    }

    /** the memory reference for an in-place operation on either a known absolute address or a symbol */
    private fun mem(knownAddress: UInt?, symbol: String?): MemoryReference =
        if(knownAddress!=null) IRMemory.direct(knownAddress.toAddress()) else IRMemory.direct(symbol!!)

    private fun addConstMem(dt: IRDataType, knownAddress: UInt?, symbol: String?, value: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        val is6502 = options.compTarget.cpu.is6502
        fun mem() = mem(knownAddress, symbol)
        when(value) {
            0 -> { /* do nothing */ }
            1 -> {
                code += IRInstructions.memoryOp(Opcode.INCM, dt, mem())
            }
            2 -> {
                if(is6502) {
                    if(knownAddress!=null) {
                        code += IRInstructions.memoryOp(Opcode.INCM, dt, IRMemory.direct(knownAddress.toAddress()))
                        code += IRInstructions.memoryOp(Opcode.INCM, dt, IRMemory.direct(knownAddress.toAddress()))
                    } else {
                        val symbolName = symbol!!
                        code += IRInstructions.memoryOp(Opcode.INCM, dt, IRMemory.direct(symbolName))
                        code += IRInstructions.memoryOp(Opcode.INCM, dt, IRMemory.direct(symbolName))
                    }
                } else {
                    code += IRInstructions.memoryOpImmediate(Opcode.ADDIM, dt, mem(), 2)
                }
            }
            -1 -> {
                code += IRInstructions.memoryOp(Opcode.DECM, dt, mem())
            }
            -2 -> {
                if(is6502) {
                    if(knownAddress!=null) {
                        code += IRInstructions.memoryOp(Opcode.DECM, dt, IRMemory.direct(knownAddress.toAddress()))
                        code += IRInstructions.memoryOp(Opcode.DECM, dt, IRMemory.direct(knownAddress.toAddress()))
                    } else {
                        val symbolName = symbol!!
                        code += IRInstructions.memoryOp(Opcode.DECM, dt, IRMemory.direct(symbolName))
                        code += IRInstructions.memoryOp(Opcode.DECM, dt, IRMemory.direct(symbolName))
                    }
                } else {
                    code += IRInstructions.memoryOpImmediate(Opcode.SUBIM, dt, mem(), 2)
                }
            }
            else -> {
                if(dt==IRDataType.FLOAT) {
                    // float loop variables are not currently supported; keep the load+addm/subm form
                    val valueReg = registers.next(dt)
                    if(value>0) {
                        code += IRInstructions.load(dt, valueReg, value)
                        code += IRInstructions.memoryOp(Opcode.ADDM, dt, mem(), valueReg)
                    }
                    else {
                        code += IRInstructions.load(dt, valueReg, -value)
                        code += IRInstructions.memoryOp(Opcode.SUBM, dt, mem(), valueReg)
                    }
                } else {
                    if(value>0) {
                        code += IRInstructions.memoryOpImmediate(Opcode.ADDIM, dt, mem(), value)
                    } else {
                        code += IRInstructions.memoryOpImmediate(Opcode.SUBIM, dt, mem(), -value)
                    }
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
            IRInstructions.loadFloat(fpReg, 0.0)
        } else {
            IRInstructions.binaryImmediateFloat(Opcode.MULS, fpReg, factor)
        }
        return code
    }

    internal fun multiplyByConstFloatInplace(knownAddress: UInt?, symbol: String?, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        if(factor==0.0) {
            code += IRInstructions.storeZero(Opcode.STOREZM, IRDataType.FLOAT, mem(knownAddress, symbol))
        } else {
            val factorReg = registers.next(IRDataType.FLOAT)
            code += IRInstructions.loadFloat(factorReg, factor)
            code += IRInstructions.memoryOp(Opcode.MULSM, IRDataType.FLOAT, mem(knownAddress, symbol), factorReg)
        }
        return code
    }

    internal fun multiplyByConst(dt: DataType, reg: Int, factor: Int): IRCodeChunk {
        val irdt = irType(dt)
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += IRInstructions.unary(Opcode.LSL, irdt, reg)
        }
        else if(pow2>=1) {
            // just shift multiple bits
            code += IRInstructions.binaryImmediate(Opcode.LSLI, irdt, reg, pow2)
        } else {
            code += if (factor == 0) {
                IRInstructions.load(irdt, reg, 0)
            } else {
                val opcode = if(dt.isSigned) Opcode.MULS else Opcode.MUL
                IRInstructions.binaryImmediate(opcode, irdt, reg, factor)
            }
        }
        return code
    }

    internal fun multiplyByConstInplace(dt: IRDataType, signed: Boolean, knownAddress: UInt?, symbol: String?, factor: Int): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2==1) {
            // just shift 1 bit
            code += IRInstructions.memoryOp(Opcode.LSLM, dt, mem(knownAddress, symbol))
        }
        else if(pow2>=1) {
            // just shift multiple bits
            val pow2reg = registers.next(IRDataType.BYTE)
            code += IRInstructions.load(IRDataType.BYTE, pow2reg, pow2)
            code += IRInstructions.memoryOp(Opcode.LSLNM, dt, mem(knownAddress, symbol), pow2reg)
        } else {
            if (factor == 0) {
                code += IRInstructions.storeZero(Opcode.STOREZM, dt, mem(knownAddress, symbol))
            }
            else {
                val factorReg = registers.next(dt)
                code += IRInstructions.load(dt, factorReg, factor)
                val opcode = if(signed) Opcode.MULSM else Opcode.MULM
                code += IRInstructions.memoryOp(opcode, dt, mem(knownAddress, symbol), factorReg)
            }
        }
        return code
    }

    internal fun divideByConstFloat(fpReg: Int, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        code += if(factor==0.0) {
            IRInstructions.loadFloat(fpReg, Double.MAX_VALUE)
        } else {
            IRInstructions.binaryImmediateFloat(Opcode.DIVS, fpReg, factor)
        }
        return code
    }

    internal fun divideByConstFloatInplace(knownAddress: UInt?, symbol: String?, factor: Double): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1.0)
            return code
        if(factor==0.0) {
            val maxvalueReg = registers.next(IRDataType.FLOAT)
            code += IRInstructions.loadFloat(maxvalueReg, Double.MAX_VALUE)
            code += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.FLOAT, maxvalueReg, mem(knownAddress, symbol))
        } else {
            val factorReg = registers.next(IRDataType.FLOAT)
            code += IRInstructions.loadFloat(factorReg, factor)
            code += IRInstructions.memoryOp(Opcode.DIVSM, IRDataType.FLOAT, mem(knownAddress, symbol), factorReg)
        }
        return code
    }

    internal fun divideByConst(dt: IRDataType, reg: Int, factor: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2>=0 && !signed) {
            // unsigned division by a power of two: logical shift right (correct)
            if(pow2==1) {
                code += IRInstructions.unary(Opcode.LSR, dt, reg)
            } else if(dt == IRDataType.LONG && pow2 == 16) {
                // x / 65536 for unsigned long == x >> 16 == MSIGW(x)
                code += IRInstructions.binary(Opcode.MSIGW, dt, reg, reg)
            } else {
                code += IRInstructions.binaryImmediate(Opcode.LSRI, dt, reg, pow2)
            }
            return code
        }

// NOTE: bias-shift code not activated because it causes large code bloat
//        if(pow2>=0 && signed && options.compTarget.cpu.is6502) {
//            // signed division by a power of two: bias-corrected shift (much cheaper than a DIVS routine on the 6502)
//            emitSignedDivByPow2Shift(code, dt, reg, pow2)
//            return code
//        }

        // regular div (also used for signed division by a power of two on non-6502 targets: >> floors,
        // whereas / truncates toward zero for negative dividends, so a plain shift is wrong)
        code += if (factor == 0) {
            IRInstructions.load(dt, reg, 0xffff)
        } else {
            if(signed)
                IRInstructions.binaryImmediate(Opcode.DIVS, dt, reg, factor)
            else
                IRInstructions.binaryImmediate(Opcode.DIV, dt, reg, factor)
        }
        return code
    }

    internal fun divideByConstInplace(dt: IRDataType, knownAddress: UInt?, symbol: String?, factor: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(null, null)
        if(factor==1)
            return code
        val pow2 = powersOfTwoInt.indexOf(factor)
        if(pow2>=0 && !signed) {
            // unsigned division by a power of two: logical shift right (correct)
            if(pow2==1) {
                code += if(knownAddress!=null)
                    IRInstructions.memoryOp(Opcode.LSRM, dt, IRMemory.direct(knownAddress.toAddress()))
                else
                    IRInstructions.memoryOp(Opcode.LSRM, dt, IRMemory.direct(symbol!!))
            }
            else {
                val pow2reg = registers.next(IRDataType.BYTE)
                code += IRInstructions.load(IRDataType.BYTE, pow2reg, pow2)
                code += if(knownAddress!=null)
                            IRInstructions.memoryOp(Opcode.LSRNM, dt, IRMemory.direct(knownAddress.toAddress()), pow2reg)
                        else
                            IRInstructions.memoryOp(Opcode.LSRNM, dt, IRMemory.direct(symbol!!), pow2reg)
            }
            return code
        }
        // signed division by a power of two falls through to the real division below
        else
        {
// NOTE: bias-shift code not activated because it causes large code bloat
//            if(pow2>=0 && signed && options.compTarget.cpu.is6502) {
//                // signed division by a power of two: bias-corrected shift (much cheaper than a DIVS routine on the 6502)
//                val reg = registers.next(dt)
//                code += if(knownAddress!=null)
//                    IRInstructions.loadMemory(Opcode.LOADM, dt, reg, IRMemory.direct(knownAddress.toAddress()))
//                else
//                    IRInstructions.loadMemory(Opcode.LOADM, dt, reg, IRMemory.direct(symbol))
//                emitSignedDivByPow2Shift(code, dt, reg, pow2)
//                code += if(knownAddress!=null)
//                    IRInstructions.storeMemory(Opcode.STOREM, dt, reg, IRMemory.direct(knownAddress.toAddress()))
//                else
//                    IRInstructions.storeMemory(Opcode.STOREM, dt, reg, IRMemory.direct(symbol))
//                return code
//            }

            // regular div
            if (factor == 0) {
                val reg = registers.next(dt)
                code += IRInstructions.load(dt, reg, 0xffff)
                code += if(knownAddress!=null)
                    IRInstructions.storeMemory(Opcode.STOREM, dt, reg, IRMemory.direct(knownAddress.toAddress()))
                else
                    IRInstructions.storeMemory(Opcode.STOREM, dt, reg, IRMemory.direct(symbol!!))
            }
            else {
                val factorReg = registers.next(dt)
                code += IRInstructions.load(dt, factorReg, factor)
                code += if(signed) {
                    if(knownAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVSM, dt, IRMemory.direct(knownAddress.toAddress()), factorReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVSM, dt, IRMemory.direct(symbol!!), factorReg)
                }
                else {
                    if(knownAddress!=null)
                        IRInstructions.memoryOp(Opcode.DIVM, dt, IRMemory.direct(knownAddress.toAddress()), factorReg)
                    else
                        IRInstructions.memoryOp(Opcode.DIVM, dt, IRMemory.direct(symbol!!), factorReg)
                }
            }
            return code
        }
    }

/*    private fun emitSignedDivByPow2Shift(code: IRCodeChunk, dt: IRDataType, reg: Int, pow2: Int) {
        // Signed division by 2^pow2 via a bias-corrected arithmetic shift, which is far cheaper
        // than a DIVS routine on the 6502. Plain arithmetic shift floors toward -inf, whereas
        // integer division truncates toward zero, so for negative dividends we must add the
        // remainder before shifting. The sign-dependent correction is folded into the add:
        //   result = (x + ((x >> (W-1)) & (2^pow2 - 1))) >> pow2
        val wordSize = when(dt) {
            IRDataType.BYTE -> 8
            IRDataType.WORD -> 16
            IRDataType.LONG -> 32
            else -> throw IllegalArgumentException("division of unsupported datatype $dt")
        }
        val signReg = registers.next(dt)
        val wm1Reg = registers.next(IRDataType.BYTE)
        val nReg = registers.next(IRDataType.BYTE)
        val mask = (1 shl pow2) - 1
        code += IRInstructions.load(IRDataType.BYTE, wm1Reg, wordSize - 1)
        code += IRInstructions.load(IRDataType.BYTE, nReg, pow2)
        code += IRInstructions.move(dt, signReg, reg)       // signReg = x
        code += IRInstructions.binary(Opcode.ASRN, dt, signReg, wm1Reg)     // signReg = x >> (W-1)
        code += IRInstructions.binaryImmediate(Opcode.AND, dt, signReg, mask)   // signReg = correction
        code += IRInstructions.binary(Opcode.ADDR, dt, reg, signReg)        // reg = x + correction
        code += IRInstructions.binary(Opcode.ASRN, dt, reg, nReg)          // reg = result
    }*/

    private fun translate(ifElse: PtIfElse): IRCodeChunks {
        val goto = ifElse.ifScope.children.firstOrNull() as? PtJump
        return if(goto!=null && ifElse.elseScope.children.isEmpty()) {
            translateIfFollowedByJustGoto(ifElse, goto)
        } else {
            translateIfElse(ifElse)
        }
    }

    private fun translateIfFollowedByJustGoto(ifElse: PtIfElse, goto: PtJump): MutableList<IRCodeChunkBase> {
        val result = mutableListOf<IRCodeChunkBase>()
        if (isIndirectJump(goto)) {
            val afterIfLabel = createLabelName()
            translateCondition(ifElse.condition, null, afterIfLabel, result)
            val tr = expressionEval.translateExpression(goto.target)
            result += tr.chunks
            addInstr(result, IRInstructions.jumpIndirect(tr.resultReg), null)
            result += IRCodeChunk(afterIfLabel, null)
        } else {
            val address = goto.target.asConstInteger()?.toUInt()?.toAddress()
            val label = if (address == null) (goto.target as PtIdentifier).name else null
            translateCondition(ifElse.condition, onTrueLabel = label, onTrueAddress = address, onFalseLabel = null, result = result)
        }
        return result
    }

    private fun translateCondition(
        condition: PtExpression,
        onTrueLabel: String?,
        onFalseLabel: String?,
        result: MutableList<IRCodeChunkBase>,
        onTrueAddress: MemoryAddress? = null,
        onFalseAddress: MemoryAddress? = null
    ) {
        if (onTrueLabel == null && onFalseLabel == null && onTrueAddress == null && onFalseAddress == null) {
            val tr = expressionEval.translateExpression(condition)
            result += tr.chunks
            return
        }

        if (condition is PtBinaryExpression) {
            if (condition.operator == "or") {
                val bodyLabel = onTrueLabel ?: createLabelName()
                translateCondition(condition.left, bodyLabel, null, result)
                translateCondition(condition.right, onTrueLabel, onFalseLabel, result, onTrueAddress, onFalseAddress)
                if (onTrueLabel == null && onTrueAddress == null) result += IRCodeChunk(bodyLabel, null)
                return
            }
            if (condition.operator == "and") {
                val skipLabel = onFalseLabel ?: createLabelName()
                translateCondition(condition.left, null, skipLabel, result)
                translateCondition(condition.right, onTrueLabel, onFalseLabel, result, onTrueAddress, onFalseAddress)
                if (onFalseLabel == null && onFalseAddress == null) result += IRCodeChunk(skipLabel, null)
                return
            }
            if (condition.operator in ComparisonOperators) {
                if (condition.left.type.isFloat) {
                    translateFloatComparison(condition, onTrueLabel, onTrueAddress, onFalseLabel, onFalseAddress, result)
                } else {
                    translateIntegerComparison(condition, onTrueLabel, onTrueAddress, onFalseLabel, onFalseAddress, result)
                }
                return
            }
        }
        if (condition is PtPrefix && condition.operator == "not") {
            translateCondition(condition.value, onFalseLabel, onTrueLabel, result, onFalseAddress, onTrueAddress)
            return
        }

        // Fallback: materialize expression and branch on result
        val tr = expressionEval.translateExpression(condition)
        result += tr.chunks
        // Only skip the CMPI #0 if BOTH:
        //   1) The previous instruction is documented to set status bits (Z/N), AND
        //   2) The target CPU honors the "multi-byte ops set Z based on full value" contract
        //      (see CpuType.statusBitsOnMultiByteOps). For 8-bit targets this contract
        //      is false: e.g. a 16-bit DEC only sets Z from the low byte's dec, not the
        //      full 16-bit value, so we MUST emit an explicit CMPI here.
        val lastInstr = tr.chunks.lastOrNull()?.instructions?.lastOrNull()
        val targetHonorsContract = options.compTarget.cpu.statusBitsOnMultiByteOps
        val skipCmpi = targetHonorsContract
                && lastInstr != null
                && lastInstr.opcode in OpcodesThatSetZeroFlagOnM68k
        if (!skipCmpi) {
            addInstr(result, IRInstructions.compareImmediate(tr.dt, tr.resultReg, 0), null)
        }

        if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel != null || onFalseAddress != null)) {
            addInstr(result, IRInstructions.branch(Opcode.BSTNE, branchTarget(onTrueLabel, onTrueAddress)), null)
            addInstr(result, IRInstructions.jump(branchTarget(onFalseLabel, onFalseAddress)), null)
        } else if (onTrueLabel != null || onTrueAddress != null) {
            addInstr(result, IRInstructions.branch(Opcode.BSTNE, branchTarget(onTrueLabel, onTrueAddress)), null)
        } else if (onFalseLabel != null || onFalseAddress != null) {
            addInstr(result, IRInstructions.branch(Opcode.BSTEQ, branchTarget(onFalseLabel, onFalseAddress)), null)
        }
    }

    private fun translateIntegerComparison(condition: PtBinaryExpression, onTrueLabel: String?, onTrueAddress: MemoryAddress?, onFalseLabel: String?, onFalseAddress: MemoryAddress?, result: MutableList<IRCodeChunkBase>) {
        val useBIT = expressionEval.checkIfConditionCanUseBIT(condition)
        if (useBIT != null) {
            val (testBitSet, expr, bitmask) = useBIT
            val bitPos = Integer.numberOfTrailingZeros(bitmask)
            val leftTr = expressionEval.translateExpression(expr)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            addInstr(result, bitTest(leftTr.dt, leftTr.resultReg, bitPos), null)
            if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel == null && onFalseAddress == null)) {
                addInstr(result, IRInstructions.branch(getBitBranchOpcode(testBitSet), branchTarget(onTrueLabel, onTrueAddress)), null)
            } else if ((onFalseLabel != null || onFalseAddress != null) && (onTrueLabel == null && onTrueAddress == null)) {
                addInstr(result, IRInstructions.branch(getBitBranchOpcode(!testBitSet), branchTarget(onFalseLabel, onFalseAddress)), null)
            } else if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel != null || onFalseAddress != null)) {
                addInstr(result, IRInstructions.branch(getBitBranchOpcode(testBitSet), branchTarget(onTrueLabel, onTrueAddress)), null)
                addInstr(result, IRInstructions.jump(branchTarget(onFalseLabel, onFalseAddress)), null)
            }
            return
        }

        val signed = condition.left.type.isSigned
        val number = (condition.right as? PtNumber)?.number?.toInt()
        val leftTr = expressionEval.translateExpression(condition.left)
        val branchDt = leftTr.dt
        addToResult(result, leftTr, leftTr.resultReg, -1)

        if (number != null) {
            val isComparingWithZero = number == 0
            val lastInstr = leftTr.chunks.lastOrNull()?.instructions?.lastOrNull()
            // Skip the CMPI only if the target honors the "multi-byte ops set Z correctly"
            // contract. On 8-bit targets a 16/32-bit op like DEC only sets Z from the last
            // byte, so we cannot rely on the previous instruction's status bits - the
            // CMPI is required to test the full multi-byte value.
            // See CpuType.statusBitsOnMultiByteOps for details.
            val canSkipCmpi = isComparingWithZero
                    && options.compTarget.cpu.statusBitsOnMultiByteOps
                    && lastInstr != null
                    && lastInstr.opcode in OpcodesThatSetZeroFlagOnM68k

            if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel == null && onFalseAddress == null)) {
                var (opcode, useCmpi) = getIntegerComparisonBranch(condition.operator, false, signed)
                if (canSkipCmpi && (opcode == Opcode.BSTEQ || opcode == Opcode.BSTNE)) useCmpi = false
                emitIntegerComparisonBranch(result, opcode, useCmpi, branchDt, leftTr.resultReg, number, onTrueLabel, onTrueAddress)
            } else if ((onFalseLabel != null || onFalseAddress != null) && (onTrueLabel == null && onTrueAddress == null)) {
                var (opcode, useCmpi) = getIntegerComparisonBranch(condition.operator, true, signed)
                if (canSkipCmpi && (opcode == Opcode.BSTEQ || opcode == Opcode.BSTNE)) useCmpi = false
                emitIntegerComparisonBranch(result, opcode, useCmpi, branchDt, leftTr.resultReg, number, onFalseLabel, onFalseAddress)
            } else if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel != null || onFalseAddress != null)) {
                var (opcode, useCmpi) = getIntegerComparisonBranch(condition.operator, false, signed)
                if (canSkipCmpi && (opcode == Opcode.BSTEQ || opcode == Opcode.BSTNE)) useCmpi = false
                emitIntegerComparisonBranch(result, opcode, useCmpi, branchDt, leftTr.resultReg, number, onTrueLabel, onTrueAddress)
                addInstr(result, IRInstructions.jump(branchTarget(onFalseLabel, onFalseAddress)), null)
            }
        } else {
            val rightTr = expressionEval.translateExpression(condition.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel == null && onFalseAddress == null)) {
                val res = getIntegerComparisonRegBranch(condition.operator, false, signed, leftTr.resultReg, rightTr.resultReg)
                emitIntegerComparisonRegBranch(result, res.opcode, res.useCmp, branchDt, res.leftReg, res.rightReg, onTrueLabel, onTrueAddress)
            } else if ((onFalseLabel != null || onFalseAddress != null) && (onTrueLabel == null && onTrueAddress == null)) {
                val res = getIntegerComparisonRegBranch(condition.operator, true, signed, leftTr.resultReg, rightTr.resultReg)
                emitIntegerComparisonRegBranch(result, res.opcode, res.useCmp, branchDt, res.leftReg, res.rightReg, onFalseLabel, onFalseAddress)
            } else if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel != null || onFalseAddress != null)) {
                val res = getIntegerComparisonRegBranch(condition.operator, false, signed, leftTr.resultReg, rightTr.resultReg)
                emitIntegerComparisonRegBranch(result, res.opcode, res.useCmp, branchDt, res.leftReg, res.rightReg, onTrueLabel, onTrueAddress)
                addInstr(result, IRInstructions.jump(branchTarget(onFalseLabel, onFalseAddress)), null)
            }
        }
    }

    private fun translateFloatComparison(condition: PtBinaryExpression, onTrueLabel: String?, onTrueAddress: MemoryAddress?, onFalseLabel: String?, onFalseAddress: MemoryAddress?, result: MutableList<IRCodeChunkBase>) {
        val leftTr = expressionEval.translateExpression(condition.left)
        addToResult(result, leftTr, -1, leftTr.resultFpReg)
        val rightTr = expressionEval.translateExpression(condition.right)
        addToResult(result, rightTr, -1, rightTr.resultFpReg)
        val compResultReg = registers.next(IRDataType.BYTE)
        addInstr(result, IRInstructions.floatCompare(compResultReg, leftTr.resultFpReg, rightTr.resultFpReg), null)

        if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel == null && onFalseAddress == null)) {
            val (opcode, useCmpi) = getFloatComparisonBranch(condition.operator, false)
            emitIntegerComparisonBranch(result, opcode, useCmpi, IRDataType.BYTE, compResultReg, 0, onTrueLabel, onTrueAddress)
        } else if ((onFalseLabel != null || onFalseAddress != null) && (onTrueLabel == null && onTrueAddress == null)) {
            val (opcode, useCmpi) = getFloatComparisonBranch(condition.operator, true)
            emitIntegerComparisonBranch(result, opcode, useCmpi, IRDataType.BYTE, compResultReg, 0, onFalseLabel, onFalseAddress)
        } else if ((onTrueLabel != null || onTrueAddress != null) && (onFalseLabel != null || onFalseAddress != null)) {
            val (opcode, useCmpi) = getFloatComparisonBranch(condition.operator, false)
            emitIntegerComparisonBranch(result, opcode, useCmpi, IRDataType.BYTE, compResultReg, 0, onTrueLabel, onTrueAddress)
            addInstr(result, IRInstructions.jump(branchTarget(onFalseLabel, onFalseAddress)), null)
        }
    }

    private fun getBitBranchOpcode(wantSet: Boolean): Opcode {
        return if (wantSet) Opcode.BSTNE else Opcode.BSTEQ
    }

    private fun invertOperator(operator: String) = when (operator) {
        "==" -> "!="
        "!=" -> "=="
        "<" -> ">="
        ">" -> "<="
        "<=" -> ">"
        ">=" -> "<"
        else -> throw AssemblyError("invalid operator to invert")
    }

    private fun getIntegerComparisonBranch(operator: String, invert: Boolean, signed: Boolean): Pair<Opcode, Boolean> {
        val op = if (invert) invertOperator(operator) else operator
        return when (op) {
            "==" -> Opcode.BSTEQ to true
            "!=" -> Opcode.BSTNE to true
            "<" -> (if (signed) Opcode.BLTS else Opcode.BLT) to false
            ">" -> (if (signed) Opcode.BGTS else Opcode.BGT) to false
            "<=" -> (if (signed) Opcode.BLES else Opcode.BLE) to false
            ">=" -> (if (signed) Opcode.BGES else Opcode.BGE) to false
            else -> throw AssemblyError("invalid comparison operator")
        }
    }

    private data class RegBranchResult(val opcode: Opcode, val useCmp: Boolean, val leftReg: Int, val rightReg: Int)

    private fun getIntegerComparisonRegBranch(operator: String, invert: Boolean, signed: Boolean, leftReg: Int, rightReg: Int): RegBranchResult {
        val op = if (invert) invertOperator(operator) else operator
        return when (op) {
            "==" -> RegBranchResult(Opcode.BSTEQ, true, leftReg, rightReg)
            "!=" -> RegBranchResult(Opcode.BSTNE, true, leftReg, rightReg)
            "<" -> if (signed) RegBranchResult(Opcode.BGTSR, false, rightReg, leftReg) else RegBranchResult(Opcode.BGTR, false, rightReg, leftReg)
            ">" -> if (signed) RegBranchResult(Opcode.BGTSR, false, leftReg, rightReg) else RegBranchResult(Opcode.BGTR, false, leftReg, rightReg)
            "<=" -> if (signed) RegBranchResult(Opcode.BGESR, false, rightReg, leftReg) else RegBranchResult(Opcode.BGER, false, rightReg, leftReg)
            ">=" -> if (signed) RegBranchResult(Opcode.BGESR, false, leftReg, rightReg) else RegBranchResult(Opcode.BGER, false, leftReg, rightReg)
            else -> throw AssemblyError("invalid comparison operator")
        }
    }

    private fun getFloatComparisonBranch(operator: String, invert: Boolean): Pair<Opcode, Boolean> {
        val op = if (invert) invertOperator(operator) else operator
        return when (op) {
            "==" -> Opcode.BSTEQ to true
            "!=" -> Opcode.BSTNE to true
            "<" -> Opcode.BLTS to false
            ">" -> Opcode.BGTS to false
            "<=" -> Opcode.BLES to false
            ">=" -> Opcode.BGES to false
            else -> throw AssemblyError("weird operator")
        }
    }

    private fun emitIntegerComparisonBranch(result: MutableList<IRCodeChunkBase>, opcode: Opcode, useCmpi: Boolean, dt: IRDataType, reg: Int, immediate: Int, label: String?, address: MemoryAddress?) {
        if (useCmpi) {
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.compareImmediate(dt, reg, immediate)
                it += IRInstructions.branch(opcode, branchTarget(label, address))
            }
        } else if (opcode in setOf(Opcode.BSTEQ, Opcode.BSTNE, Opcode.BSTPOS, Opcode.BSTNEG, Opcode.BSTCS, Opcode.BSTCC, Opcode.BSTVS, Opcode.BSTVC)) {
            addInstr(result, IRInstructions.branch(opcode, branchTarget(label, address)), null)
        } else {
            addInstr(result, IRInstructions.branchImmediate(opcode, dt, reg, immediate, branchTarget(label, address)), null)
        }
    }

    private fun emitIntegerComparisonRegBranch(result: MutableList<IRCodeChunkBase>, opcode: Opcode, useCmp: Boolean, dt: IRDataType, leftReg: Int, rightReg: Int, label: String?, address: MemoryAddress?) {
        if (useCmp) {
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.compare(dt, leftReg, rightReg)
                it += IRInstructions.branch(opcode, branchTarget(label, address))
            }
        } else {
            addInstr(result, IRInstructions.branchRegister(opcode, dt, leftReg, rightReg, branchTarget(label, address)), null)
        }
    }


    private fun translateIfElse(ifElse: PtIfElse): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val afterIfLabel = createLabelName()
        if (ifElse.hasElse()) {
            val elseLabel = createLabelName()
            translateCondition(ifElse.condition, null, elseLabel, result)
            result += translateNode(ifElse.ifScope)
            addInstr(result, IRInstructions.jump(codeLabel(afterIfLabel)), null)
            result += labelFirstChunk(translateNode(ifElse.elseScope), elseLabel)
        } else {
            translateCondition(ifElse.condition, null, afterIfLabel, result)
            result += translateNode(ifElse.ifScope)
        }
        result += IRCodeChunk(afterIfLabel, null)
        return result
    }


    private fun translate(repeat: PtRepeatLoop): IRCodeChunks {
        val constRepeats = repeat.count.asConstInteger()
        if(constRepeats!=null) {
            when(constRepeats) {
                0 -> return emptyList()
                1 -> return translateGroup(repeat.children)
            }
            require(constRepeats in 1..65536) { "repeat count out of range 1..65536: $constRepeats" }
            val repeatLabel = createLabelName()
            val bodyChunks = translateNode(repeat.statements).toMutableList()
            // Preserve counted trip as IR loop; backend will emit optimal dbra/dey loop
            val loop = IRLoopChunk(repeatLabel, constRepeats, bodyChunks, null)
            return listOf(loop)
        }

        val repeatLabel = createLabelName()
        val skipRepeatLabel = createLabelName()
        val result = mutableListOf<IRCodeChunkBase>()
        val needsExplicitCmpi = !options.compTarget.cpu.statusBitsOnMultiByteOps
        val irDt = irType(repeat.count.type)
        val countTr = expressionEval.translateExpression(repeat.count)
        addToResult(result, countTr, countTr.resultReg, -1)
        if (repeat.count.asConstValue() == null) {
            if (needsExplicitCmpi) {
                addInstr(result, IRInstructions.compareImmediate(irDt, countTr.resultReg, 0), null)
            }
            addInstr(result, IRInstructions.branch(Opcode.BSTEQ, codeLabel(skipRepeatLabel)), null)
        }
        result += labelFirstChunk(translateNode(repeat.statements), repeatLabel)
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.unary(Opcode.DEC, irDt, countTr.resultReg)
            if (needsExplicitCmpi) {
                it += IRInstructions.compareImmediate(irDt, countTr.resultReg, 0)
            }
            it += IRInstructions.branch(Opcode.BSTNE, codeLabel(repeatLabel))
        }
        result += IRCodeChunk(skipRepeatLabel, null)
        return result
    }

    private fun translate(jump: PtJump): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val chunk = IRCodeChunk(null, null)
        if(jump.target.asConstInteger()!=null) {
            chunk += IRInstructions.jump(codeAddress(jump.target.asConstInteger()!!.toUInt().toAddress()))
            result += chunk
            return result
        } else {
            val identifier = jump.target as? PtIdentifier
            if (identifier != null && !isIndirectJump(jump)) {
                // jump to label
                chunk += IRInstructions.jump(codeLabel(identifier.name))
                result += chunk
                return result
            }
            // evaluate jump address expression into a register and jump indirectly to it
            val tr = expressionEval.translateExpression(jump.target)
            result += tr.chunks
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.jumpIndirect(tr.resultReg)
            }
            return result
        }
    }

    private fun isIndirectJump(jump: PtJump): Boolean {
        if(jump.target.asConstInteger()!=null)
            return false
        val identifier = jump.target as? PtIdentifier ?: return true
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
        if(ret.numReturnValues()>1) {
            // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
            // (this allows unencumbered use of many Rx registers if you don't return that many values)
            // a floating point value is passed via FAC   (just one fp value is possible)

            val returnRegs = ret.definingISub()!!.returnsWhatWhere(options.compTarget)

            if(ret.children.size < ret.numReturnValues()) {
                // Return values come from a multi-value function call (e.g., "return multi2(99)")
                // We need to call the function and then move its return values to our return registers
                val fcall = ret.children.single() as? PtFunctionCall
                    ?: throw AssemblyError("expected function call for multi-value return ${ret.position}")
                
                // Translate the function call (this generates the CALL instruction)
                val callResult = expressionEval.translate(fcall, false)
                result += callResult.chunks
                
                // Get the return register specs for the called function
                val calledSub = symbolTable.lookup(fcall.name)
                val calledReturnRegs = when(calledSub) {
                    is StSub -> (calledSub.astNode!! as IPtSubroutine).returnsWhatWhere(options.compTarget)
                    is StExtSub -> calledSub.returns.map { it.register to it.type }
                    else -> throw AssemblyError("unexpected subroutine type for multi-value return ${ret.position}")
                }
                
                // Move each return value from the called function's return registers to our return registers
                calledReturnRegs.zip(returnRegs).forEachIndexed { index, (fromRegTo, toRegTo) ->
                    val (fromReg, fromType) = fromRegTo
                    val (toReg, toType) = toRegTo
                    require(fromType == toType) { "return type mismatch at position $index" }

                    if(fromType.isFloat) {
                        // For float returns, use FAC1/FAC2
                        val tempFpReg = registers.next(IRDataType.FLOAT)
                        when(fromReg.registerOrPair) {
                            RegisterOrPair.FAC1 -> addInstr(result, IRInstructions.unary(Opcode.LOADHFACZERO, IRDataType.FLOAT, tempFpReg), null)
                            RegisterOrPair.FAC2 -> addInstr(result, IRInstructions.unary(Opcode.LOADHFACONE, IRDataType.FLOAT, tempFpReg), null)
                            else -> throw AssemblyError("unexpected FP return register ${fromReg}")
                        }
                        when(toReg.registerOrPair) {
                            RegisterOrPair.FAC1 -> addInstr(result, storeHwFac(Opcode.STOREHFACZERO, tempFpReg), null)
                            RegisterOrPair.FAC2 -> addInstr(result, storeHwFac(Opcode.STOREHFACONE, tempFpReg), null)
                            else -> throw AssemblyError("unexpected FP return register ${toReg}")
                        }
                    } else {
                        // For non-float returns, load from source to temp IR register, then store to destination
                        val tempReg = registers.next(irType(fromType))
                        result += loadFromCpuRegister(fromReg, fromType, tempReg)
                        result += setCpuRegister(toReg, irType(toType), tempReg, -1)
                    }
                }
                addInstr(result, IRInstructions.returnVoid(), null)
                return result
            }

            val values = ret.children.zip(returnRegs)
            // first all but the first return values
            for ((value, register) in values.drop(1)) {
                val tr = expressionEval.translateExpression(value as PtExpression)
                if(register.second.isFloat) {
                    addToResult(result, tr, -1, tr.resultFpReg)
                    result += setCpuRegister(register.first, IRDataType.FLOAT, -1, tr.resultFpReg)
                }
                else {
                    addToResult(result, tr, tr.resultReg, -1)
                    result += setCpuRegister(register.first, irType(register.second), tr.resultReg, -1)
                }
            }
            // finally do the first of the return values (this avoids clobbering of its value in AY)
            values.first().also { (value, register) ->
                val tr = expressionEval.translateExpression(value as PtExpression)
                if(register.second.isFloat) {
                    addToResult(result, tr, -1, tr.resultFpReg)
                    result += setCpuRegister(register.first, IRDataType.FLOAT, -1, tr.resultFpReg)
                }
                else {
                    addToResult(result, tr, tr.resultReg, -1)
                    result += setCpuRegister(register.first, irType(register.second), tr.resultReg, -1)
                }
            }
            addInstr(result, IRInstructions.returnVoid(), null)
            return result
        }

        val value = ret.children.singleOrNull()
        if(value==null) {
            addInstr(result, IRInstructions.returnVoid(), null)
        } else {
            value as PtExpression
            if(value.type.isFloat) {
                if(value is PtNumber) {
                    addInstr(result, IRInstructions.returnImmediateFloat(value.number), null)
                } else {
                    val tr = expressionEval.translateExpression(value)
                    addToResult(result, tr, -1, tr.resultFpReg)
                    addInstr(result, IRInstructions.returnRegister(IRDataType.FLOAT, tr.resultFpReg), null)
                }
            }
            else {
                if(value.asConstInteger()!=null) {
                    addInstr(result, IRInstructions.returnImmediate(irType(value.type), value.asConstInteger()!!), null)
                } else {
                    val tr = expressionEval.translateExpression(value)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstructions.returnRegister(irType(value.type), tr.resultReg), null)
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
                block.options.ignoreUnused,
                block.options.amigaChipram
            ), block.position)
        for (child in block.children) {
            when(child) {
                is PtNop -> { /* nothing */ }
                is PtAssignment, is PtAugmentedAssign -> { /* global variable initialization is done elsewhere */ }
                is PtVariable, is PtConstant, is PtMemMapped, is PtMemorySlabReservation -> { /* vars should be looked up via symbol table */ }
                is PtAlign -> {
                    val chunk = IRCodeChunk(null, null)
                    chunk += IRInstructions.align(child.align.toInt())
                    irBlock += chunk
                }
                is PtSub -> {
                    val sub = IRSubroutine(child.name, translateParameters(child.signature.children), child.signature.returns, child.position)
                    for (subchild in child.children) {
                        translateNode(subchild).forEach { sub += it }
                    }
                    irBlock += sub
                }
                is PtAsmSub -> {
                    val addr = child.address
                    if(addr!=null) {
                        // extsub: emit as inline assembly equate so that other tools (vm, 6502 codegen) can reference the symbol as an address label
                        require(child.children.isEmpty()) {
                            "extsub should be empty at ${child.position}"
                        }
                        val bank = if(addr.constbank!=null) " ; @bank ${addr.constbank}" else ""
                        val addressStr = if(addr.address > 0x7fffffffu) addr.address.toInt().toString() else addr.address.toHex()
                        val asm = "${child.name} = $addressStr$bank"
                        irBlock += IRInlineAsmChunk(null, asm, false, null)
                    } else {
                        // regular asmsub
                        if(child.children.mapTo(mutableSetOf()) { (it as PtInlineAssembly).isIR }.size>1)
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
                            child.parameters.map { IRAsmSubroutine.IRAsmParam(it.first, it.second.type) },
                            child.returns.map { IRAsmSubroutine.IRAsmParam(it.first, it.second)},
                            asmChunk,
                            child.position,
                            child.inline
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
                is PtJmpTable -> {
                    irBlock += IRCodeChunk(null, null).also {
                        for(addr in child.children) {
                            addr as PtIdentifier
                            it += IRInstructions.jump(codeLabel(addr.name))
                        }
                    }
                }
                is PtStructDecl -> { /* do nothing, should be found in the symbol table */ }
                else -> TODO("weird block child node $child  ${child.position}")
            }
        }
        return irBlock
    }

    private fun translate(swap: PtSwap): IRCodeChunks {
        require(swap.target1.type == swap.target2.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val target1: PtExpression = swap.target1.children.single() as PtExpression
        val target2: PtExpression = swap.target2.children.single() as PtExpression
        val t1 = expressionEval.translateExpression(target1)
        val t2 = expressionEval.translateExpression(target2)
        if(swap.target1.type.isFloat) {
            addToResult(result, t1, -1, t1.resultFpReg)
            addToResult(result, t2, -1, t2.resultFpReg)
            result += assignFpRegisterTo(target1, t2.resultFpReg)
            result += assignFpRegisterTo(target2, t1.resultFpReg)
        } else {
            addToResult(result, t1, t1.resultReg, -1)
            addToResult(result, t2, t2.resultReg, -1)
            result += assignRegisterTo(target1, t2.resultReg)
            result += assignRegisterTo(target2, t1.resultReg)
        }
        return result
    }

    private fun assignFpRegisterTo(target: PtExpression, fpRegister: Int): IRCodeChunks {
        require(target.type.isFloat)
        val assignment = PtAssignment(target.position)
        val assignTarget = PtAssignTarget(false, target.position)
        assignTarget.add(target)
        assignment.add(assignTarget)
        assignment.add(PtIrRegister(fpRegister, DataType.FLOAT, target.position))
        val result = mutableListOf<IRCodeChunkBase>()
        result += translateNode(assignment)
        return result
    }

    internal fun assignRegisterTo(target: PtExpression, register: Int): IRCodeChunks {
        val assignment = PtAssignment(target.position)
        val assignTarget = PtAssignTarget(false, target.position)
        assignTarget.add(target)
        assignment.add(assignTarget)
        assignment.add(PtIrRegister(register, target.type, target.position))
        val result = mutableListOf<IRCodeChunkBase>()
        result += translateNode(assignment)
        return result
    }

    private fun translateParameters(parameters: List<PtNode>): List<IRSubroutine.IRParam> {
        val result = mutableListOf<IRSubroutine.IRParam>()
        parameters.forEach {
            it as PtSubroutineParameter
            if(it.register==null) {
                require('.' in it.name) { "even parameter names should have been made fully scoped by now" }
                val orig = symbolTable.lookup(it.name) as? StStaticVariable
                    ?: TODO("fix missing lookup for: ${it.name}   parameter")
                result += IRSubroutine.IRParam(it.name, orig.dt)
            } else {
                val reg = it.register
                require(reg in Cx16VirtualRegisters || reg in CombinedLongRegisters || reg in M68kRegisters) { "can only use R0-R15, D0-D7, A0-A6, or FP0-FP7 'registers' here" }
                if (reg in Cx16VirtualRegisters || reg in CombinedLongRegisters) {
                    require('.' in it.name) { "even parameter names should have been made fully scoped by now" }
                    val targetVar = symbolTable.lookup(it.name) as StMemVar
                    result += IRSubroutine.IRParam(it.name, targetVar.dt)
                } else {
                    // M68k registers: pass as regular parameter
                    require('.' in it.name) { "even parameter names should have been made fully scoped by now" }
                    val orig = symbolTable.lookup(it.name) as? StStaticVariable
                        ?: TODO("fix missing lookup for: ${it.name}   parameter")
                    result += IRSubroutine.IRParam(it.name, orig.dt)
                }
            }
        }
        return result
    }

    private var labelSequenceNumber = 0
    internal fun createLabelName(): String {
        labelSequenceNumber++
        return "${GENERATED_LABEL_PREFIX}$labelSequenceNumber"
    }

    internal fun translateBuiltinFunc(call: PtFunctionCall): ExpressionCodeResult {
        return builtinFuncGen.translate(call)
    }

    internal fun isZero(expression: PtExpression): Boolean = (expression as? PtNumber)?.number==0.0 || (expression as? PtBool)?.value==false

    internal fun isOne(expression: PtExpression): Boolean = (expression as? PtNumber)?.number==1.0 || (expression as? PtBool)?.value==true

    internal fun makeSyscall(syscall: IMSyscall, params: List<Pair<IRDataType, Int>>, returns: Pair<IRDataType, Int>?, label: String?=null): IRCodeChunk {
        return IRCodeChunk(label, null).also {
            val args = params.map { (dt, reg)-> Calls.argument(reg, dt) }
            // for now, syscalls have 0 or 1 return value
            val results = if(returns==null) emptyList() else listOf(Calls.result(returns.second, returns.first))
            it += IRInstructions.syscall(syscall.number, args, results)
        }
    }

    internal fun setCpuRegister(registerOrFlag: RegisterOrStatusflag, paramDt: IRDataType, resultReg: Int, resultFpReg: Int): IRCodeChunk {
        val chunk = IRCodeChunk(null, null)
        val reg = registerOrFlag.registerOrPair
        val (slot, _) = if (reg != null && reg !in setOf(RegisterOrPair.FAC1, RegisterOrPair.FAC2))
            expressionEval.registerOrStatusflagToSlotAndFlag(RegisterOrStatusflag(reg, null))
        else null to null
        val m68kSlot = slot?.takeIf { it.value >= 10 }
        if (m68kSlot != null) {
            chunk += IRInstructions.hardwareStore(paramDt, resultReg, m68kSlot)
        } else when(registerOrFlag.registerOrPair) {
            RegisterOrPair.A -> chunk += IRInstructions.hardwareStore(IRDataType.BYTE, resultReg, CallingConventionSlot(0))
            RegisterOrPair.X -> chunk += IRInstructions.hardwareStore(IRDataType.BYTE, resultReg, CallingConventionSlot(1))
            RegisterOrPair.Y -> chunk += IRInstructions.hardwareStore(IRDataType.BYTE, resultReg, CallingConventionSlot(2))
            RegisterOrPair.AX -> chunk += IRInstructions.hardwareStore(IRDataType.WORD, resultReg, CallingConventionSlot(3))
            RegisterOrPair.AY -> chunk += IRInstructions.hardwareStore(IRDataType.WORD, resultReg, CallingConventionSlot(4))
            RegisterOrPair.XY -> chunk += IRInstructions.hardwareStore(IRDataType.WORD, resultReg, CallingConventionSlot(5))
            RegisterOrPair.FAC1 -> chunk += storeHwFac(Opcode.STOREHFACZERO, resultFpReg)
            RegisterOrPair.FAC2 -> chunk += storeHwFac(Opcode.STOREHFACONE, resultFpReg)
            in Cx16VirtualRegisters -> {
                chunk += IRInstructions.storeMemory(Opcode.STOREM, paramDt, resultReg, IRMemory.direct("cx16.${registerOrFlag.registerOrPair.toString().lowercase()}"))
            }
            in CombinedLongRegisters -> {
                require(paramDt==IRDataType.LONG)
                val startreg = registerOrFlag.registerOrPair!!.startregname()
                chunk += IRInstructions.storeMemory(Opcode.STOREM, paramDt, resultReg, IRMemory.direct("cx16.${startreg}"))
            }
            null -> when(registerOrFlag.statusflag) {
                // TODO: do the statusflag argument as last
                Statusflag.Pc -> chunk += IRInstructions.unary(Opcode.LSR, paramDt, resultReg)
                else -> throw AssemblyError("unsupported statusflag as param")
            }
            else -> throw AssemblyError("unsupported register arg $registerOrFlag")
        }
        return chunk
    }

    internal fun loadFromCpuRegister(registerOrFlag: RegisterOrStatusflag, fromType: DataType, tempReg: Int): IRCodeChunk {
        val chunk = IRCodeChunk(null, null)
        val irType = irType(fromType)
        val reg2 = registerOrFlag.registerOrPair
        val (slot2, _) = if (reg2 != null && reg2 !in setOf(RegisterOrPair.FAC1, RegisterOrPair.FAC2))
            expressionEval.registerOrStatusflagToSlotAndFlag(RegisterOrStatusflag(reg2, null))
        else null to null
        val m68kSlot2 = slot2?.takeIf { it.value >= 10 }
        if (m68kSlot2 != null) {
            chunk += IRInstructions.hardwareLoad(irType, tempReg, m68kSlot2)
        } else when(registerOrFlag.registerOrPair) {
            RegisterOrPair.A -> chunk += IRInstructions.hardwareLoad(IRDataType.BYTE, tempReg, CallingConventionSlot(0))
            RegisterOrPair.X -> chunk += IRInstructions.hardwareLoad(IRDataType.BYTE, tempReg, CallingConventionSlot(1))
            RegisterOrPair.Y -> chunk += IRInstructions.hardwareLoad(IRDataType.BYTE, tempReg, CallingConventionSlot(2))
            RegisterOrPair.AX -> chunk += IRInstructions.hardwareLoad(IRDataType.WORD, tempReg, CallingConventionSlot(3))
            RegisterOrPair.AY -> chunk += IRInstructions.hardwareLoad(IRDataType.WORD, tempReg, CallingConventionSlot(4))
            RegisterOrPair.XY -> chunk += IRInstructions.hardwareLoad(IRDataType.WORD, tempReg, CallingConventionSlot(5))
            RegisterOrPair.FAC1 -> chunk += IRInstructions.unary(Opcode.LOADHFACZERO, IRDataType.FLOAT, tempReg)
            RegisterOrPair.FAC2 -> chunk += IRInstructions.unary(Opcode.LOADHFACONE, IRDataType.FLOAT, tempReg)
            in Cx16VirtualRegisters -> {
                chunk += IRInstructions.loadMemory(Opcode.LOADM, irType, tempReg, IRMemory.direct("cx16.${registerOrFlag.registerOrPair.toString().lowercase()}"))
            }
            in CombinedLongRegisters -> {
                require(fromType.isLong)
                val startreg = registerOrFlag.registerOrPair!!.startregname()
                chunk += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.LONG, tempReg, IRMemory.direct("cx16.${startreg}"))
            }
            null -> when(registerOrFlag.statusflag) {
                Statusflag.Pc -> {
                    chunk += IRInstructions.load(IRDataType.BYTE, tempReg, 0)
                    chunk += IRInstructions.unary(Opcode.ROXL, IRDataType.BYTE, tempReg)
                }
                else -> throw AssemblyError("unsupported statusflag ${registerOrFlag.statusflag}")
            }
            else -> throw AssemblyError("weird CPU register ${registerOrFlag}")
        }
        return chunk
    }

    internal fun evaluatePointerAddressIntoReg(result: MutableList<IRCodeChunkBase>, deref: PtPointerDeref): Pair<Int, UByte> {
        // calculates the pointer address and returns the register it's in + remaining offset into the struct  (so that LOADI/STOREFIELD instructions can be used)
        val pointerTr = expressionEval.translateExpression(deref.startpointer)
        result += pointerTr.chunks
        val (instructions, offset) = expressionEval.traverseRestOfDerefChainToCalculateFinalAddress(deref, pointerTr.resultReg)
        result += instructions
        return pointerTr.resultReg to offset
    }

    internal fun storeValueAtPointersLocation(result: MutableList<IRCodeChunkBase>, addressReg: Int, offset: UByte, type: DataType, valueIsZero: Boolean, existingValueRegister: Int) {
        if(offset<=0u) {
            val irdt = irType(type)
            val instr = if(type.isFloat) {
                if (valueIsZero) IRInstructions.storeZero(Opcode.STOREZI, IRDataType.FLOAT, IRMemory.indirect(addressReg, 0))
                else IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, existingValueRegister, IRMemory.indirect(addressReg, 0))
            } else {
                if (valueIsZero) IRInstructions.storeZero(Opcode.STOREZI, irdt, IRMemory.indirect(addressReg, 0))
                else IRInstructions.storeMemory(Opcode.STOREI, irdt, existingValueRegister, IRMemory.indirect(addressReg, 0))
            }
            addInstr(result, instr, null)
            return
        }

        // store with field offset
        val valueRegister = existingValueRegister
        val irdt = irType(type)
        if(valueIsZero && valueRegister<0) {
            addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, irdt, IRMemory.indirect(addressReg, offset.toInt())), null)
        } else {
            val instr = if (type.isFloat)
                IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, valueRegister, IRMemory.indirect(addressReg, offset.toInt()))
            else
                IRInstructions.storeMemory(Opcode.STOREI, irdt, valueRegister, IRMemory.indirect(addressReg, offset.toInt()))
            addInstr(result, instr, null)
        }
    }

    internal fun loadIndexReg(index: PtExpression, itemsize: Int, wordIndex: Boolean, arrayIsSplitWords: Boolean): Pair<IRCodeChunks, Int> {
        // returns the code to load the Index into the register, which is also returned.
        // The returned register is always canonicalized to the expected index width
        // (WORD on 32-bit targets, BYTE on 8-bit) so that LOADX/STOREX/STOREZX never
        // need to guess the index type later (see IRInstructions.addUsedRegistersCounts).

        require(index !is PtNumber) { "index should not be a constant number here, calling code should handle that in a more efficient way" }

        val result = mutableListOf<IRCodeChunkBase>()

        if(wordIndex) {
            val tr = expressionEval.translateExpression(index)
            addToResult(result, tr, tr.resultReg, -1)
            var indexReg = tr.resultReg
            val indexDt = tr.dt
            if(indexDt != IRDataType.WORD) {
                val newReg = registers.next(IRDataType.WORD)
                when(indexDt) {
                    IRDataType.BYTE -> addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, newReg, tr.resultReg), null)
                    IRDataType.LONG -> addInstr(result, IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, newReg, tr.resultReg), null)
                    IRDataType.POINTER -> TODO("handle pointer-typed array index for word-indexed access at ${index.position}")
                    else -> throw IllegalArgumentException("unexpected index dt $indexDt for wordIndex")
                }
                indexReg = newReg
            }
            // no pre-scaling - scale is encoded in the LOADX/STOREX instruction
            return Pair(result, indexReg)
        }

        // regular byte size index value.
        val byteIndexTr = expressionEval.translateExpression(index)
        addToResult(result, byteIndexTr, byteIndexTr.resultReg, -1)

        // LOADX/STOREX use word-sized indices on targets with 32-bit pointers.
        val indexRegType = options.compTarget.indexRegType
        var indexReg = byteIndexTr.resultReg
        val indexDt = byteIndexTr.dt
        if(indexDt != indexRegType) {
            val newReg = registers.next(indexRegType)
            when {
                indexRegType == IRDataType.WORD && indexDt == IRDataType.BYTE ->
                    addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, newReg, byteIndexTr.resultReg), null)
                indexRegType == IRDataType.WORD && indexDt == IRDataType.LONG ->
                    addInstr(result, IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, newReg, byteIndexTr.resultReg), null)
                indexRegType == IRDataType.BYTE && indexDt == IRDataType.WORD ->
                    addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newReg, byteIndexTr.resultReg), null)
                indexRegType == IRDataType.BYTE && indexDt == IRDataType.LONG ->
                    addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, newReg, byteIndexTr.resultReg), null)
                else -> throw IllegalArgumentException("unexpected index conversion $indexDt -> $indexRegType")
            }
            indexReg = newReg
        }

        // no pre-scaling - scale is encoded in the LOADX/STOREX instruction
        return Pair(result, indexReg)
    }

    internal fun canonicalizeIndexReg(result: MutableList<IRCodeChunkBase>, indexTr: ExpressionCodeResult): Int {
        val indexRegType = options.compTarget.indexRegType
        if(indexTr.dt == indexRegType)
            return indexTr.resultReg
        val effectiveDt = if(indexTr.dt==IRDataType.POINTER) options.compTarget.pointerIRType else indexTr.dt
        if(effectiveDt == indexRegType)
            return indexTr.resultReg
        val newReg = registers.next(indexRegType)
        when {
            indexRegType==IRDataType.WORD && indexTr.dt==IRDataType.BYTE ->
                addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, newReg, indexTr.resultReg), null)
            indexRegType==IRDataType.WORD && indexTr.dt==IRDataType.LONG ->
                addInstr(result, IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, newReg, indexTr.resultReg), null)
            indexRegType==IRDataType.WORD && indexTr.dt==IRDataType.POINTER ->
                addInstr(result, IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, newReg, indexTr.resultReg), null)
            indexRegType==IRDataType.BYTE && indexTr.dt==IRDataType.WORD ->
                addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newReg, indexTr.resultReg), null)
            indexRegType==IRDataType.BYTE && indexTr.dt==IRDataType.LONG ->
                addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, newReg, indexTr.resultReg), null)
            indexRegType==IRDataType.BYTE && indexTr.dt==IRDataType.POINTER -> {
                val ptrType = options.compTarget.pointerIRType
                if(ptrType==IRDataType.WORD)
                    addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, newReg, indexTr.resultReg), null)
                else
                    addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, newReg, indexTr.resultReg), null)
            }
            else -> throw IllegalArgumentException("unexpected index conversion ${indexTr.dt} -> $indexRegType")
        }
        return newReg
    }

    internal fun irType(type: DataType): IRDataType {
        if(type.base.isPassByRef)
            return IRDataType.POINTER

        return when(type.base) {
            BaseDataType.BOOL,
            BaseDataType.UBYTE,
            BaseDataType.BYTE -> IRDataType.BYTE
            BaseDataType.UWORD, BaseDataType.WORD -> IRDataType.WORD
            BaseDataType.POINTER -> IRDataType.POINTER
            BaseDataType.LONG -> IRDataType.LONG
            BaseDataType.FLOAT -> IRDataType.FLOAT
            BaseDataType.STRUCT_INSTANCE -> throw AssemblyError("no support for struct instances yet so no IR datatype for $type")
            else -> throw AssemblyError("no IR datatype for $type")
        }
    }

}

/**
 * Returns the IR data type to use for a memory address on [target].
 * On 32-bit-pointer targets (VM, M68k) this is LONG (32-bit signed);
 * on 16-bit-pointer targets (6502) this is WORD (16-bit unsigned).
 * Use this everywhere the IR generator emits a register or argument that holds a memory address.
 */
internal fun addressDtFor(target: ICompilationTarget): IRDataType = target.pointerIRType
