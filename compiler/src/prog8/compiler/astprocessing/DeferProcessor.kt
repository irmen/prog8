package prog8.compiler.astprocessing

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.source.SourceCode

/**
 * Processes `defer` statements in the simplified AST.
 *
 * Converts defer statements into runtime mask-based execution:
 * - Each subroutine with defers gets a mask variable (UBYTE on 6502/virtual, UWORD on m68k)
 * - Defer statements are replaced with bit-set operations
 * - Exit points (return, jump, sys.exit) are augmented to call defer handler
 * - A defer handler routine is generated that calls enabled defers in reverse order
 *
 * Program-wide extension:
 * - A global defer stack tracks active frames (handler ID) so that a `sys.exit()`
 *   from anywhere unwinds all callers. When the program contains no defer,
 *   no global state is emitted (zero overhead).
 *
 * Limitations:
 * - Maximum 8 defers per subroutine on 6502/virtual, 16 on m68k (due to UBYTE/UWORD mask)
 * - Only simple return values can be deferred (complex expressions need push/pop)
 */
internal object DeferProcessor {

    private const val maskVarName = "prog8_defers_mask"
    private const val invokeDefersRoutineName = "prog8_invoke_defers"
    private const val deferBlockName = "prog8_defer"
    private const val deferSpName = "defer_sp"
    private const val deferGuardName = "defer_guard"
    private const val deferHandlerStackName = "defer_handler_stack"
    private const val deferUnwindName = "defer_unwind_all"
    /**
     * Process all defer statements in the program.
     */
    fun process(program: PtProgram, st: SymbolTable, target: ICompilationTarget, errors: IErrorReporter) {
        val maskType = if (target.cpu == CpuType.M68000 || target.cpu == CpuType.M68020) DataType.UWORD else DataType.UBYTE
        val defers = setDeferMasks(program, maskType, errors)
        if(!errors.noErrors())
            return
        if(defers.isEmpty())
            return  // zero overhead: no globals, no handlers beyond what setDeferMasks already did (none)
        // setDeferMasks already created mask variables and replaced defer statements with |=
        // Now build global stack and program-wide integration
        val handlerIds = assignHandlerIds(defers)
        createGlobalDeferState(program, handlerIds, maskType, target, errors)
        if(!errors.noErrors())
            return
        integrateDefersProgramWide(defers, program, st, maskType, handlerIds, target)
    }

    private fun assignHandlerIds(defers: Map<PtSub, List<PtDefer>>): Map<PtSub, Int> {
        val sorted = defers.keys.sortedBy { it.scopedName }
        val map = mutableMapOf<PtSub, Int>()
        var id = 1
        for (sub in sorted) {
            map[sub] = id++
        }
        return map
    }

    private fun createGlobalDeferState(program: PtProgram, handlerIds: Map<PtSub, Int>, maskType: DataType, target: ICompilationTarget, errors: IErrorReporter) {
        val pos = Position.DUMMY
        val deferBlock = PtBlock(deferBlockName, false, SourceCode.Generated(deferBlockName), PtBlock.Options(), pos)
        program.add(deferBlock)

        val isM68k = target.cpu == CpuType.M68000 || target.cpu == CpuType.M68020
        val maxDeferFrames = if (isM68k) 256 else 32
        val spType = if (isM68k) DataType.UWORD else DataType.UBYTE
        val spBase = if (isM68k) BaseDataType.UWORD else BaseDataType.UBYTE
        val idDataType = if (isM68k) DataType.UWORD else DataType.UBYTE
        val arrayType = DataType.UBYTE.elementToArray(target)
        val spVar = PtVariable(deferSpName, spType, false, ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, null, null, pos)
        val guardVar = PtVariable(deferGuardName, DataType.UBYTE, false, ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, null, null, pos)
        val stackVar = PtVariable(deferHandlerStackName, arrayType, false, ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, null, maxDeferFrames.toUInt(), pos)

        deferBlock.add(spVar)
        deferBlock.add(guardVar)
        deferBlock.add(stackVar)

        // create unwind sub
        val unwindSub = PtSub(deferUnwindName, emptyList(), emptyList(), pos)
        deferBlock.add(unwindSub)

        // guard check: if defer_guard !=0 return
        val guardCheckCond = PtBinaryExpression("!=", DataType.BOOL, pos)
        guardCheckCond.add(PtIdentifier(deferBlock.scopedName + "." + deferGuardName, DataType.UBYTE, pos))
        guardCheckCond.add(PtNumber(BaseDataType.UBYTE, 0.0, pos))
        val guardIf = PtIfElse(pos)
        guardIf.add(guardCheckCond)
        val guardTrue = PtNodeGroup()
        guardTrue.add(PtReturn(pos))
        guardIf.add(guardTrue)
        guardIf.add(PtNodeGroup())
        unwindSub.add(guardIf)

        // guard =1
        val setGuard = PtAssignment(pos)
        setGuard.add(PtAssignTarget(false, pos).also { it.add(PtIdentifier(deferBlock.scopedName + "." + deferGuardName, DataType.UBYTE, pos)) })
        setGuard.add(PtNumber(BaseDataType.UBYTE, 1.0, pos))
        unwindSub.add(setGuard)

        // loop
        val loopLabel = PtLabel("defer_unwind_loop", pos)
        val doneLabel = PtLabel("defer_unwind_done", pos)
        unwindSub.add(loopLabel)

        // if sp==0 goto done
        val spZeroCond = PtBinaryExpression("==", DataType.BOOL, pos)
        spZeroCond.add(PtIdentifier(deferBlock.scopedName + "." + deferSpName, spType, pos))
        spZeroCond.add(PtNumber(spBase, 0.0, pos))
        val spZeroIf = PtIfElse(pos)
        spZeroIf.add(spZeroCond)
        val spZeroTrue = PtNodeGroup()
        val jumpToDone = PtJump(pos)
        jumpToDone.add(PtIdentifier(unwindSub.scopedName + "." + doneLabel.name, DataType.UBYTE, pos))
        spZeroTrue.add(jumpToDone)
        spZeroIf.add(spZeroTrue)
        spZeroIf.add(PtNodeGroup())
        unwindSub.add(spZeroIf)

        // sp--
        val decSp = PtAugmentedAssign("-=", pos)
        decSp.add(PtAssignTarget(false, pos).also { it.add(PtIdentifier(deferBlock.scopedName + "." + deferSpName, spType, pos)) })
        decSp.add(PtNumber(spBase, 1.0, pos))
        unwindSub.add(decSp)

        // id = handler_stack[sp]
        // create temporary variable id inside unwind sub
        val idVar = PtVariable("unwind_id", idDataType, false, ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, null, null, pos)
        unwindSub.add(idVar)
        val idAssign = PtAssignment(pos)
        val idTarget = PtAssignTarget(false, pos)
        idTarget.add(PtIdentifier(unwindSub.scopedName + "." + idVar.name, idDataType, pos))
        idAssign.add(idTarget)
        val arrayRead = PtArrayIndexer(DataType.UBYTE, false, pos)
        arrayRead.add(PtIdentifier(deferBlock.scopedName + "." + deferHandlerStackName, arrayType, pos))
        arrayRead.add(PtIdentifier(deferBlock.scopedName + "." + deferSpName, spType, pos))
        idAssign.add(arrayRead)
        unwindSub.add(idAssign)

        if (!errors.noErrors())
            return

        // when id { 1-> call handler1 ... }
        if(handlerIds.isNotEmpty()) {
            val whenNode = PtWhen(pos)
            whenNode.add(PtIdentifier(unwindSub.scopedName + "." + idVar.name, idDataType, pos))
            val choices = PtNodeGroup()
            for ((sub, id) in handlerIds.entries.sortedBy { it.value }) {
                if (isM68k && id > 255) {
                    errors.err("defer handler ID exceeds 255; too many defer-owning subroutines for current runtime", sub.position)
                    return
                }
                val choice = PtWhenChoice(false, pos)
                val values = PtNodeGroup()
                values.add(PtNumber(BaseDataType.UWORD, id.toDouble(), pos))
                val stmts = PtNodeGroup()
                val handlerCall = PtFunctionCall(sub.scopedName + "." + invokeDefersRoutineName, false, false, emptyArray(), pos)
                stmts.add(handlerCall)
                choice.add(values)
                choice.add(stmts)
                choices.add(choice)
            }
            // else choice empty (no call)
            val elseChoice = PtWhenChoice(true, pos)
            elseChoice.add(PtNodeGroup())
            elseChoice.add(PtNodeGroup())
            choices.add(elseChoice)
            whenNode.add(choices)
            unwindSub.add(whenNode)
        }

        // goto loop
        val jumpToLoop = PtJump(pos)
        jumpToLoop.add(PtIdentifier(unwindSub.scopedName + "." + loopLabel.name, DataType.UBYTE, pos))
        unwindSub.add(jumpToLoop)

        unwindSub.add(doneLabel)

        // guard =0
        val clearGuard = PtAssignment(pos)
        clearGuard.add(PtAssignTarget(false, pos).also { it.add(PtIdentifier(deferBlock.scopedName + "." + deferGuardName, DataType.UBYTE, pos)) })
        clearGuard.add(PtNumber(BaseDataType.UBYTE, 0.0, pos))
        unwindSub.add(clearGuard)

        unwindSub.add(PtReturn(pos))
    }

    /**
     * First pass: collect defers per subroutine and create mask variables.
     */
    private fun setDeferMasks(program: PtProgram, maskType: DataType, errors: IErrorReporter): Map<PtSub, List<PtDefer>> {
        val defersPerSub = mutableMapOf<PtSub, MutableList<PtDefer>>().withDefault { mutableListOf() }

        walkAst(program) { node, _ ->
            if(node is PtDefer) {
                val scope = node.definingSub()!!
                val defers = defersPerSub.getValue(scope)
                defers.add(node)
                defersPerSub[scope] = defers
            }
            true  // Continue traversal
        }

        for((sub, defers) in defersPerSub) {

            if(defers.isEmpty())
                continue
            val maxPerSub = if (maskType == DataType.UWORD) 16 else 8
            if (defers.size > maxPerSub) {
                errors.err("can have no more than $maxPerSub defers per subroutine", sub.position)
                return emptyMap()
            }

            // define the bitmask variable and set it to zero
            val deferVariable = PtVariable(
                maskVarName,
                maskType,
                false,
                ZeropageWish.NOT_IN_ZEROPAGE,
                0u,
                true,
                null,
                null,
                sub.position
            )
            val assignZero = PtAssignment(sub.position)
            assignZero.add(PtAssignTarget(false, sub.position).also {
                it.add(PtIdentifier(sub.scopedName+"."+maskVarName, maskType, sub.position))
            })
            val baseType = if (maskType == DataType.UWORD) BaseDataType.UWORD else BaseDataType.UBYTE
            assignZero.add(PtNumber(baseType, 0.0, sub.position))
            val firstIndex = sub.children.indexOfFirst { it !is PtSubSignature }   // first child node is the sub's signature so add below that one
            sub.add(firstIndex, assignZero)
            sub.add(firstIndex, deferVariable)

            for((deferIndex, defer) in defers.withIndex()) {
                // replace the defer statement with one that enables the bit in the mask for this defer
                val scope = defer.parent
                val idx = scope.children.indexOf(defer)
                val enableDefer = PtAugmentedAssign("|=", defer.position)
                val target = PtAssignTarget(false, defer.position)
                target.add(PtIdentifier(sub.scopedName+"."+maskVarName, maskType, defer.position))
                enableDefer.add(target)
                // enable the bit for this defer (beginning with high bits so the handler can simply shift right to check them in reverse order)
                val baseType = if (maskType == DataType.UWORD) BaseDataType.UWORD else BaseDataType.UBYTE
                enableDefer.add(PtNumber(baseType, (1 shl (defers.size-1 - deferIndex)).toDouble(), defer.position))
                scope.setChild(idx, enableDefer)
            }
        }

        return defersPerSub
    }

    /**
     * Second pass: integrate defer calls at exit points and generate handler routines.
     * Program-wide version: pushes handler ID on entry, pops before local unwind,
     * and routes sys.exit through global unwind.
     */
    private fun integrateDefersProgramWide(subdefers: Map<PtSub, List<PtDefer>>, program: PtProgram, st: SymbolTable, maskType: DataType, handlerIds: Map<PtSub, Int>, target: ICompilationTarget) {
        val deferBlockNameScoped = deferBlockName
        val spName = "$deferBlockNameScoped.$deferSpName"
        val stackName = "$deferBlockNameScoped.$deferHandlerStackName"
        val unwindName = "$deferBlockNameScoped.$deferUnwindName"
        val isM68k = target.cpu == CpuType.M68000 || target.cpu == CpuType.M68020
        val spType = if (isM68k) DataType.UWORD else DataType.UBYTE
        val spBase = if (isM68k) BaseDataType.UWORD else BaseDataType.UBYTE

        // Insert push at entry of each sub with defers
        val maxDeferFrames = if (isM68k) 256 else 32
        for ((sub, _) in subdefers) {
            val id = handlerIds[sub]!!
            val firstIndex = sub.children.indexOfFirst { it !is PtSubSignature }
            // find index after mask var and init (they are at firstIndex and firstIndex+1 after setDeferMasks, but we need to insert after them)
            // mask variable and assignZero are at firstIndex and firstIndex+1, so push after them
            var pushIdx = firstIndex
            // count mask var and assign zero if present
            // they occupy 2 slots after signature
            pushIdx += 2
            // overflow check: if sp == max -> fatal sys.exit (defer stack overflow)
            val overflowCond = PtBinaryExpression("==", DataType.BOOL, sub.position)
            overflowCond.add(PtIdentifier(spName, spType, sub.position))
            overflowCond.add(PtNumber(spBase, maxDeferFrames.toDouble(), sub.position))
            val overflowIf = PtIfElse(sub.position)
            overflowIf.add(overflowCond)
            val overflowTrue = PtNodeGroup()
            val overflowExit = PtFunctionCall("sys.exit", false, false, emptyArray(), sub.position)
            overflowExit.add(PtNumber(BaseDataType.UBYTE, 1.0, sub.position))
            overflowTrue.add(overflowExit)
            overflowIf.add(overflowTrue)
            overflowIf.add(PtNodeGroup())
            sub.add(pushIdx, overflowIf)
            pushIdx += 1
            // push: handler_stack[sp] = id
            val arrayTypeLocal = DataType.UBYTE.elementToArray(target)
            val pushAssign = PtAssignment(sub.position)
            val pushTarget = PtAssignTarget(false, sub.position)
            val arrIdx = PtArrayIndexer(DataType.UBYTE, false, sub.position)
            arrIdx.add(PtIdentifier(stackName, arrayTypeLocal, sub.position))
            arrIdx.add(PtIdentifier(spName, spType, sub.position))
            pushTarget.add(arrIdx)
            pushAssign.add(pushTarget)
            pushAssign.add(PtNumber(BaseDataType.UBYTE, id.toDouble(), sub.position))
            sub.add(pushIdx, pushAssign)
            // sp++
            val incSp = PtAugmentedAssign("+=", sub.position)
            incSp.add(PtAssignTarget(false, sub.position).also { it.add(PtIdentifier(spName, spType, sub.position)) })
            incSp.add(PtNumber(spBase, 1.0, sub.position))
            sub.add(pushIdx+1, incSp)
        }

        // Collect exit points
        val returnsToAugment = mutableListOf<PtReturn>()
        val jumpsToAugment = mutableListOf<PtJump>()
        val subEndsToAugment = mutableListOf<PtSub>()
        val sysExits = mutableListOf<PtFunctionCall>()

        walkAst(program) { node, _ ->
            when(node) {
                is PtReturn -> {
                    val defSub = node.definingSub()
                    if(defSub != null && defSub in subdefers) {
                        returnsToAugment.add(node)
                    }
                }
                is PtFunctionCall -> {
                    if(node.name.startsWith("sys.exit")) {
                        sysExits.add(node)
                    }
                }
                is PtJump -> {
                    val defSub = node.definingSub()
                    if(defSub != null && defSub in subdefers) {
                        val identifier = node.target as? PtIdentifier
                        if(identifier != null) {
                            val stNode = st.lookup(identifier.name)
                            if(stNode != null) {
                                val targetSub = stNode.astNode?.definingSub()
                                if(targetSub != null && targetSub != defSub)
                                    jumpsToAugment.add(node)
                            }
                        }
                    }
                }
                is PtSub -> {
                    if(node in subdefers) {
                        val lastStmt = node.children.lastOrNull { it !is PtDefer }
                        if (lastStmt != null && lastStmt !is PtReturn && lastStmt !is PtJump)
                            subEndsToAugment.add(node)
                    }
                }
                else -> {}
            }
            true
        }

        // augment returns: pop + handler before return (handle complex returns)
        for(ret in returnsToAugment) {
            val sub = ret.definingSub()!!
            if(ret.children.isEmpty() || ret.children.all { isSimple(it as PtExpression) }) {
                insertPopAndHandlerBefore(ret, sub, spName, unwindName, handlerIds, spType, spBase)
                if(sub.scopedName=="main.start") {
                    val idx = ret.parent.children.indexOf(ret)
                    val unwindCall = PtFunctionCall(unwindName, false, false, emptyArray(), ret.position)
                    ret.parent.add(idx, unwindCall)
                }
                continue
            }
            // complex return
            val pushAndPopCalls = ret.children.map {
                val expr = it as PtExpression
                if (expr.type == DataType.UNDEFINED)
                    Pair(expr, null)
                else
                    makePushPopFunctionCalls(expr, target)
            }
            val pushCalls = pushAndPopCalls.map { it.first }.reversed()
            val popCalls = pushAndPopCalls.mapNotNull { it.second }
            val newRet = PtReturn(ret.position)
            val group = PtNodeGroup()
            pushCalls.forEach { group.add(it) }
            popCalls.forEach { newRet.add(it) }
            // insert pop+handler before newRet inside group
            val popNode = makeDecSpNode(ret.position, spName, spType, spBase)
            val handlerCall = PtFunctionCall(sub.scopedName+"."+invokeDefersRoutineName, false, false,emptyArray(), ret.position)
            group.add(popNode)
            group.add(handlerCall)
            if(sub.scopedName=="main.start") {
                val unwindCall = PtFunctionCall(unwindName, false, false, emptyArray(), ret.position)
                group.add(unwindCall)
            }
            group.add(newRet)
            replaceNode(ret, group)
        }

        // jumps out
        for(jmp in jumpsToAugment) {
            val sub = jmp.definingSub()!!
            insertPopAndHandlerBefore(jmp, sub, spName, unwindName, handlerIds, spType, spBase)
        }

        // sub ends
        for(sub in subEndsToAugment) {
            val idx = sub.children.indexOfLast { it !is PtDefer }
            // insert pop+handler before implicit return
            val decSp = makeDecSpNode(sub.position, spName, spType, spBase)
            sub.add(idx+1, decSp)
            val handlerCall = PtFunctionCall(sub.scopedName+"."+invokeDefersRoutineName, false, false,emptyArray(), sub.position)
            sub.add(idx+2, handlerCall)
            var nextIdx = idx+3
            // for main.start falling off the end, also unwind all remaining frames before system cleanup
            if(sub.scopedName=="main.start") {
                val unwindCall = PtFunctionCall(unwindName, false, false, emptyArray(), sub.position)
                sub.add(nextIdx, unwindCall)
                nextIdx += 1
            }
            val ret = PtReturn(sub.position)
            sub.add(nextIdx, ret)
        }

        // sys.exit: route through global unwind
        for(exitCall in sysExits) {
            val idx = exitCall.parent.children.indexOf(exitCall)
            val unwindCall = PtFunctionCall(unwindName, false, false, emptyArray(), exitCall.position)
            exitCall.parent.add(idx, unwindCall)
        }

        // generate per-sub handlers (same as before)
        for( (sub, defers) in subdefers) {
            val defersRoutine = PtSub(invokeDefersRoutineName, emptyList(), emptyList(), sub.position)
            defersRoutine.parent=sub
            for((idx, defer) in defers.reversed().withIndex()) {
                val shift = PtAugmentedAssign(">>=", defer.position)
                shift.add(PtAssignTarget(false, defer.position).also {
                    it.add(PtIdentifier(sub.scopedName+"."+maskVarName, maskType, defer.position))
                })
                val baseType = if (maskType == DataType.UWORD) BaseDataType.UWORD else BaseDataType.UBYTE
                shift.add(PtNumber(baseType, 1.0, defer.position))
                defersRoutine.add(shift)
                val skiplabel = "prog8_defer_skip_${idx+1}"
                val branchcc = PtConditionalBranch(BranchCondition.CC, defer.position)
                branchcc.add(PtNodeGroup().also {
                    val jump = PtJump(defer.position)
                    jump.add(PtIdentifier(defersRoutine.scopedName+"."+skiplabel, DataType.UBYTE, defer.position))
                    it.add(jump)
                })
                branchcc.add(PtNodeGroup())
                defersRoutine.add(branchcc)
                transferChildren(defer, defersRoutine, true)
                defersRoutine.add(PtLabel(skiplabel, defer.position))
            }
            defersRoutine.add(PtReturn(sub.position))
            sub.add(defersRoutine)
        }
    }

    private fun makeDecSpNode(pos: Position, spName: String, spType: DataType, spBase: BaseDataType): PtAugmentedAssign {
        val decSp = PtAugmentedAssign("-=", pos)
        decSp.add(PtAssignTarget(false, pos).also { it.add(PtIdentifier(spName, spType, pos)) })
        decSp.add(PtNumber(spBase, 1.0, pos))
        return decSp
    }

    private fun insertPopAndHandlerBefore(node: PtNode, sub: PtSub, spName: String, unwindName: String, handlerIds: Map<PtSub, Int>, spType: DataType, spBase: BaseDataType) {
        val idx = node.parent.children.indexOf(node)
        val handlerCall = PtFunctionCall(sub.scopedName+"."+invokeDefersRoutineName, false, false,emptyArray(), node.position)
        node.parent.add(idx, handlerCall)
        val decSp = makeDecSpNode(node.position, spName, spType, spBase)
        node.parent.add(idx, decSp)
    }

    /**
     * Check if a return value expression is simple (can be evaluated before defer handler).
     * Simple expressions don't depend on variables that might be modified by defers.
     */
    private fun isSimple(value: PtExpression): Boolean = when(value) {
        is PtAddressOf -> value.arrayIndexExpr == null || isSimple(value.arrayIndexExpr!!)
        is PtFunctionCall -> value.builtin && value.isSimple()
        is PtMemoryByte -> value.address is PtNumber
        is PtPrefix -> isSimple(value.value)
        is PtTypeCast -> isSimple(value.value)
        is PtArray,
        is PtIrRegister,
        is PtBool,
        is PtNumber,
        is PtRange,
        is PtString -> true
        // note that  PtIdentifier als is  "complex" this time (it's a variable that might change)
        else -> false
    }
}
