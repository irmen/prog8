package prog8.compiler.astprocessing

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*

/**
 * Processes `defer` statements in the simplified AST.
 *
 * Converts defer statements into runtime mask-based execution:
 * - Each subroutine with defers gets a mask variable (UBYTE, max 8 defers)
 * - Defer statements are replaced with bit-set operations
 * - Exit points (return, jump, sys.exit) are augmented to call defer handler
 * - A defer handler routine is generated that calls enabled defers in reverse order
 *
 * Limitations:
 * - Maximum 8 defers per subroutine (due to UBYTE mask)
 * - Only simple return values can be deferred (complex expressions need push/pop)
 */
internal object DeferProcessor {

    private const val maskVarName = "prog8_defers_mask"
    private const val invokeDefersRoutineName = "prog8_invoke_defers"

    /**
     * Process all defer statements in the program.
     */
    fun process(program: PtProgram, st: SymbolTable, errors: IErrorReporter) {
        val defers = setDeferMasks(program, errors)
        if(errors.noErrors())
            integrateDefers(defers, program, st, errors)
    }

    /**
     * First pass: collect defers per subroutine and create mask variables.
     */
    private fun setDeferMasks(program: PtProgram, errors: IErrorReporter): Map<PtSub, List<PtDefer>> {
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
                val target = PtAssignTarget(false, defer.position)
                target.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, defer.position))
                enableDefer.add(target)
                // enable the bit for this defer (beginning with high bits so the handler can simply shift right to check them in reverse order)
                enableDefer.add(PtNumber(BaseDataType.UBYTE, (1 shl (defers.size-1 - deferIndex)).toDouble(), defer.position))
                scope.setChild(idx, enableDefer)
            }
        }

        return defersPerSub
    }

    /**
     * Second pass: integrate defer calls at exit points and generate handler routines.
     */
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
            true  // Continue traversal
        }

        // jumps and calls (sys.exit) exits
        for(call in jumpsAndCallsToAugment) {
            invokedeferbefore(call)
        }

        // return exits
        for(ret in returnsToAugment) {
            if(ret.children.isEmpty() || ret.children.all { isSimple(it as PtExpression) }) {
                invokedeferbefore(ret)
                continue
            }

            // complex return value, need to store it before calling the defer block
            val pushAndPopCalls = ret.children.map { makePushPopFunctionCalls(it as PtExpression) }
            val pushCalls = pushAndPopCalls.map { it.first }.reversed()     // push in reverse order
            val popCalls = pushAndPopCalls.map { it.second }
            val newRet = PtReturn(ret.position)
            val group = PtNodeGroup()
            pushCalls.forEach { group.add(it) }
            popCalls.forEach { newRet.add(it) }
            group.add(PtFunctionCall(ret.definingSub()!!.scopedName+"."+invokeDefersRoutineName, false, false,emptyArray(), ret.position))
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
                val invokedefer = PtFunctionCall(sub.scopedName+"."+invokeDefersRoutineName, false, false, emptyArray(), sub.position)
                sub.add(idx+1, invokedefer)
            }
        }


        for( (sub, defers) in subdefers) {
            // create the routine that calls the enabled defers in reverse order
            val defersRoutine = PtSub(invokeDefersRoutineName, emptyList(), emptyList(), sub.position)
            defersRoutine.parent=sub
            for((idx, defer) in defers.reversed().withIndex()) {
                val shift = PtAugmentedAssign(">>=", defer.position)
                shift.add(PtAssignTarget(false, defer.position).also {
                    it.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, defer.position))
                })
                shift.add(PtNumber(BaseDataType.UBYTE, 1.0, defer.position))
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
//        val printMask = PtFunctionCall("txt.print_ubbin", true, DataType.UNDEFINED, sub.position)
//        printMask.add(PtIdentifier(sub.scopedName+"."+maskVarName, DataType.UBYTE, sub.position))
//        printMask.add(PtBool(true, sub.position))
//        defersRoutine.add(printMask)

            defersRoutine.add(PtReturn(sub.position))
            sub.add(defersRoutine)
        }
    }

    /**
     * Insert a call to the defer handler before the given node.
     */
    private fun invokedeferbefore(node: PtNode) {
        val idx = node.parent.children.indexOf(node)
        val invokedefer = PtFunctionCall(node.definingSub()!!.scopedName+"."+invokeDefersRoutineName, false, false, emptyArray(), node.position)
        node.parent.add(idx, invokedefer)
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
