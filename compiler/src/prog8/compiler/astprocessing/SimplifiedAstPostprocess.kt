package prog8.compiler.astprocessing

import prog8.ast.FatalAstException
import prog8.code.StStruct
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*


internal fun postprocessSimplifiedAst(
    program: PtProgram,
    st: SymbolTable,
    option: CompilationOptions,
    errors: IErrorReporter
) {
    processDefers(program, st, errors)
    processSubtypesIntoStReferences(program, st)

    if(option.compTarget.cpu!=CpuType.VIRTUAL)
        checkForPointerTypesOn6502(program, errors)     // TODO remove this once the 6502 codegen can deal with pointer types and structs
}

private fun checkForPointerTypesOn6502(program: PtProgram, errors: IErrorReporter) {
    fun check(node: PtNode) {
        when(node) {
            //is PtAddressOf -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtAssignTarget -> if(!node.void && node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            //is PtBinaryExpression -> if(node.left.type.isPointer || node.right.type.isPointer) errors.err("cannot do pointer arithmetic yet on 6502 target  $node", node.position)
            is PtIdentifier -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtPointerDeref -> errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtPrefix -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtTypeCast -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtStructDecl -> errors.err("cannot use struct type yet on 6502 target  $node", node.position)
            is PtSubroutineParameter -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtVariable -> if(node.type.isPointer) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            is PtSubSignature -> if(node.returns.any{it.isPointer}) errors.err("cannot use pointer type yet on 6502 target  $node", node.position)
            else -> {}
        }
    }
    walkAst(program) { node, _ -> check(node) }
}


private fun processSubtypesIntoStReferences(program: PtProgram, st: SymbolTable) {

    fun getStStruct(subType: ISubType): StStruct {
        val stNode = st.lookup(subType.scopedNameString) as? StStruct
        if(stNode != null)
            return stNode
        else
            throw FatalAstException("cannot find in ST: ${subType.scopedNameString} $subType")
    }

    fun fixSubtype(type: DataType) {
        if(type.subType!=null && type.subType !is StStruct) {
            type.subType = getStStruct(type.subType!!)
        }
    }

    fun fixSubtypes(node: PtNode) {
        when(node) {
            is IPtVariable -> fixSubtype(node.type)
            is PtPointerDeref -> fixSubtype(node.type)
            is PtStructDecl -> node.fields.forEach { fixSubtype(it.first) }
            is PtAsmSub -> node.returns.forEach { fixSubtype(it.second) }
            is PtExpression -> fixSubtype(node.type)
            is PtSubSignature -> node.returns.forEach { fixSubtype(it) }
            is PtSubroutineParameter -> fixSubtype(node.type)
            else -> { /* has no datatype */ }
        }
    }

    walkAst(program) { node, _ -> fixSubtypes(node) }
}


private fun processDefers(program: PtProgram, st: SymbolTable, errors: IErrorReporter) {
    val defers = setDeferMasks(program, errors)
    if(errors.noErrors())
        integrateDefers(defers, program, st, errors)
}

private const val maskVarName = "prog8_defers_mask"
private const val invokeDefersRoutineName = "prog8_invoke_defers"

private fun setDeferMasks(program: PtProgram, errors: IErrorReporter): Map<PtSub, List<PtDefer>> {
    val defersPerSub = mutableMapOf<PtSub, MutableList<PtDefer>>().withDefault { mutableListOf() }

    walkAst(program) { node, _ ->
        if(node is PtDefer) {
            val scope = node.definingSub()!!
            val defers = defersPerSub.getValue(scope)
            defers.add(node)
            defersPerSub[scope] = defers
        }
    }

    for((sub, defers) in defersPerSub) {

        if(defers.isEmpty())
            continue
        if (defers.size > 8) {
            errors.err("can have no more than 8 defers per subroutine", sub.position)
            return emptyMap()
        }

        // define the bitmask variable and set it to zero
        val deferVariable = PtVariable(
            maskVarName,
            DataType.UBYTE,
            ZeropageWish.NOT_IN_ZEROPAGE,
            0u,
            true,
            null,
            null,
            sub.position
        )
        val assignZero = PtAssignment(sub.position)
        assignZero.add(PtAssignTarget(false, sub.position).also {
            it.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, sub.position))
        })
        assignZero.add(PtNumber(BaseDataType.UBYTE, 0.0, sub.position))
        val firstIndex = sub.children.indexOfFirst { it !is PtSubSignature }   // first child node is the sub's signature so add below that one
        sub.add(firstIndex, assignZero)
        sub.add(firstIndex, deferVariable)

        for((deferIndex, defer) in defers.withIndex()) {
            // replace the defer statement with one that enables the bit in the mask for this defer
            val scope = defer.parent
            val idx = scope.children.indexOf(defer)
            val enableDefer = PtAugmentedAssign("|=", defer.position)
            val target = PtAssignTarget(true, defer.position)
            target.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, defer.position))
            enableDefer.add(target)
            // enable the bit for this defer (beginning with high bits so the handler can simply shift right to check them in reverse order)
            enableDefer.add(PtNumber(BaseDataType.UBYTE, (1 shl (defers.size-1 - deferIndex)).toDouble(), defer.position))
            enableDefer.parent = scope
            scope.children[idx] = enableDefer
        }
    }

    return defersPerSub
}


private fun integrateDefers(subdefers: Map<PtSub, List<PtDefer>>, program: PtProgram, st: SymbolTable, errors: IErrorReporter) {
    val jumpsAndCallsToAugment = mutableListOf<PtNode>()
    val returnsToAugment = mutableListOf<PtReturn>()
    val subEndsToAugment = mutableListOf<PtSub>()

    walkAst(program) { node, _ ->
        if(node !is PtProgram && node.definingSub() in subdefers) {
            when (node) {
                is PtReturn -> returnsToAugment.add(node)
                is PtFunctionCall -> {
                    if (node.name.startsWith("sys.exit"))
                        jumpsAndCallsToAugment.add(node)
                }
                is PtJump -> {
                    val identifier = node.target as? PtIdentifier
                    if (identifier != null) {
                        val stNode = st.lookup(identifier.name)!!
                        val targetSub = stNode.astNode!!.definingSub()
                        if (targetSub != node.definingSub())
                            jumpsAndCallsToAugment.add(node)
                    }
                }
                is PtSub -> {
                    val lastStmt = node.children.lastOrNull { it !is PtDefer }
                    if (lastStmt != null && lastStmt !is PtReturn && lastStmt !is PtJump)
                        subEndsToAugment.add(node)
                }
                else -> {}
            }
        }
    }

    fun invokedeferbefore(node: PtNode) {
        val idx = node.parent.children.indexOf(node)
        val invokedefer = PtFunctionCall(node.definingSub()!!.scopedName+"."+invokeDefersRoutineName, true, DataType.UNDEFINED, node.position)
        node.parent.add(idx, invokedefer)
    }

    fun notComplex(value: PtExpression): Boolean = when(value) {
        is PtAddressOf -> value.arrayIndexExpr == null || notComplex(value.arrayIndexExpr!!)
        is PtBuiltinFunctionCall -> {
            when (value.name) {
                in arrayOf("msb", "lsb", "msw", "lsw", "mkword", "set_carry", "set_irqd", "clear_carry", "clear_irqd") -> value.args.all { notComplex(it) }
                else -> false
            }
        }
        is PtMemoryByte -> value.address is PtNumber
        is PtPrefix -> notComplex(value.value)
        is PtTypeCast -> notComplex(value.value)
        is PtArray,
        is PtIrRegister,
        is PtBool,
        is PtNumber,
        is PtRange,
        is PtString -> true
        // note that unlike most other times, PtIdentifier IS "complex" this time (it's a variable that might change)
        else -> false
    }

    // jumps and calls (sys.exit) exits
    for(call in jumpsAndCallsToAugment) {
        invokedeferbefore(call)
    }

    // return exits
    for(ret in returnsToAugment) {
        if(ret.children.isEmpty() || ret.children.all { notComplex(it as PtExpression) }) {
            invokedeferbefore(ret)
            continue
        }

        // complex return value, need to store it before calling the defer block
        errors.warn("using defer with nontrivial return value(s) incurs stack overhead", ret.children.first { !notComplex(it as PtExpression)}.position)
        val pushAndPopCalls = ret.children.map { makePushPopFunctionCalls(it as PtExpression) }
        val pushCalls = pushAndPopCalls.map { it.first }.reversed()     // push in reverse order
        val popCalls = pushAndPopCalls.map { it.second }
        val newRet = PtReturn(ret.position)
        val group = PtNodeGroup()
        pushCalls.forEach { group.add(it) }
        popCalls.forEach { newRet.add(it) }
        group.add(PtFunctionCall(ret.definingSub()!!.scopedName+"."+invokeDefersRoutineName, true,DataType.UNDEFINED, ret.position))
        group.add(newRet)
        replaceNode(ret, group)
    }

    // subroutine ends
    for(sub in subEndsToAugment) {
        val defer = sub.children.singleOrNull { it is PtDefer }
        if(defer != null) {
            val idx = sub.children.indexOfLast { it !is PtDefer }
            val ret = PtReturn(sub.position)
            sub.add(idx+1, ret)
            val invokedefer = PtFunctionCall(sub.scopedName+"."+invokeDefersRoutineName, true, DataType.UNDEFINED, sub.position)
            sub.add(idx+1, invokedefer)
        }
    }


    for( (sub, defers) in subdefers) {
        // create the routine that calls the enabled defers in reverse order
        val defersRoutine = PtSub(invokeDefersRoutineName, emptyList(), emptyList(), Position.DUMMY)
        defersRoutine.parent=sub
        for((idx, defer) in defers.reversed().withIndex()) {
            val shift = PtAugmentedAssign(">>=", Position.DUMMY)
            shift.add(PtAssignTarget(false, sub.position).also {
                it.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, sub.position))
            })
            shift.add(PtNumber(BaseDataType.UBYTE, 1.0, sub.position))
            defersRoutine.add(shift)
            val skiplabel = "prog8_defer_skip_${idx+1}"
            val branchcc = PtConditionalBranch(BranchCondition.CC, Position.DUMMY)
            branchcc.add(PtNodeGroup().also {
                val jump = PtJump(Position.DUMMY)
                jump.add(PtIdentifier(defersRoutine.scopedName+"."+skiplabel, DataType.UBYTE, Position.DUMMY))
                it.add(jump)
            })
            branchcc.add(PtNodeGroup())
            defersRoutine.add(branchcc)
            transferChildren(defer, defersRoutine, true)
            defersRoutine.add(PtLabel(skiplabel, Position.DUMMY))
        }
//        val printMask = PtFunctionCall("txt.print_ubbin", true, DataType.UNDEFINED, Position.DUMMY)
//        printMask.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, Position.DUMMY))
//        printMask.add(PtBool(true, Position.DUMMY))
//        defersRoutine.add(printMask)

        defersRoutine.add(PtReturn(Position.DUMMY))
        sub.add(defersRoutine)
    }
}


private fun transferChildren(source: PtNode, target: PtNode, keepExisting: Boolean = false) {
    if(!keepExisting)
        target.children.clear()
    for(c in source.children)
        target.add(c)
}


private fun replaceNode(oldNode: PtNode, newNode: PtNode) {
    newNode.parent = oldNode.parent
    val idx = oldNode.parent.children.indexOf(oldNode)
    oldNode.parent.children[idx] = newNode
}
