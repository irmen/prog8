package prog8.codegen.intermediate

import prog8.intermediate.*


object RegisterPacker {

    data class Interval(val register: Int, val start: Int, val end: Int, val type: IRDataType)

    fun pack(irProg: IRProgram) {
        val allRegTypes = mutableMapOf<Int, IRDataType>()
        irProg.foreachCodeChunk { chunk ->
            for (instr in chunk.instructions) {
                val (written, read) = getRegisterAccess(instr)
                for (r in written + read) {
                    val dt = getRegisterType(instr, r)
                    allRegTypes.putIfAbsent(r, dt)
                }
            }
        }
        val beforeCount = allRegTypes.size

        // Start packing slots after the highest original register number to avoid
        // collisions between packed slot numbers and original (non-packed) register numbers.
        val maxReg = allRegTypes.keys.maxOrNull() ?: 0
        val startSlot = maxReg + 1
        val globalSlotTypes = mutableMapOf<Int, IRDataType>()
        irProg.foreachSub { sub -> packSubroutine(sub, allRegTypes, globalSlotTypes, startSlot) }

        val afterTypes = rebuildTypeMap(irProg)
        val afterCount = afterTypes.size
        if (!irProg.options.quiet)
            println("Register packing: $beforeCount -> $afterCount registers")
    }

    // Rebuild the register type map after packing, using the same type determination as the packer.
    // This avoids the strict per-instruction validation in usedRegisters() that POINTER↔LONG↔WORD
    // cross-type packing can trigger.
    fun rebuildTypeMap(irProg: IRProgram): Map<RegisterNum, IRDataType> {
        val newTypes = mutableMapOf<RegisterNum, IRDataType>()
        irProg.foreachCodeChunk { chunk ->
            for (instr in chunk.instructions) {
                for ((reg, regNum) in listOf(instr.reg1 to 1, instr.reg2 to 2, instr.reg3 to 3)) {
                    if (reg != null)
                        newTypes.putIfAbsent(RegisterNum(reg), determineIntRegType(instr, regNum))
                }
                instr.fpReg1?.let { fpr -> newTypes.putIfAbsent(RegisterNum(fpr.value), IRDataType.FLOAT) }
                instr.fpReg2?.let { fpr -> newTypes.putIfAbsent(RegisterNum(fpr.value), IRDataType.FLOAT) }
                instr.fcallArgs?.let { fc ->
                    for (a in fc.arguments)
                        newTypes.putIfAbsent(RegisterNum(a.reg.registerNum.value), a.reg.dt)
                    for (r in fc.returns)
                        newTypes.putIfAbsent(RegisterNum(r.registerNum.value), IRDataType.BYTE)
                }
            }
        }
        return newTypes
    }

    private fun packSubroutine(sub: IRSubroutine, allRegTypes: Map<Int, IRDataType>, globalSlotTypes: MutableMap<Int, IRDataType>, startSlot: Int) {
        if (sub.chunks.isEmpty())
            return

        val successors = buildCFG(sub)
        val (_, liveOut) = computeLiveness(sub, successors)

        val codeChunks = sub.chunks.filterIsInstance<IRCodeChunk>()
        if (codeChunks.isEmpty())
            return

        val chunkRanges = mutableMapOf<IRCodeChunk, IntRange>()
        var globalIdx = 0
        for (chunk in codeChunks) {
            val start = globalIdx
            val end = globalIdx + chunk.instructions.size - 1
            chunkRanges[chunk] = start..end
            globalIdx += chunk.instructions.size
        }

        val registerIntervals = mutableMapOf<Int, MutableList<Interval>>()
        val registerTypes = mutableMapOf<Int, IRDataType>()

        for (chunk in codeChunks) {
            val range = chunkRanges[chunk]!!
            val chunkStart = range.first
            if (chunk.instructions.isEmpty())
                continue

            var liveSet = liveOut[chunk]?.toMutableSet() ?: mutableSetOf()
            val lastUse = mutableMapOf<Int, Int>()

            // Scan backward through instructions
            for (i in chunk.instructions.indices.reversed()) {
                val instr = chunk.instructions[i]
                val globalI = chunkStart + i
                val (written, read) = getRegisterAccess(instr)

                // Process READ first so types are set before WRITE processing
                for (r in read) {
                    val dt = getRegisterType(instr, r)
                    registerTypes.putIfAbsent(r, dt)
                    if (r !in liveSet) {
                        lastUse[r] = globalI
                        liveSet.add(r)
                    }
                }

                // Process WRITTEN registers: end their live range (start of interval going forward)
                for (r in written) {
                    if (r in liveSet) {
                        val end = lastUse.getOrElse(r) { globalI }
                        val actualType = getRegisterType(instr, r)
                        registerIntervals.getOrPut(r) { mutableListOf() }
                            .add(Interval(r, globalI, end, actualType))
                        liveSet.remove(r)
                        lastUse.remove(r)
                    }
                }
            }

            // Registers still live at start of chunk are live-in
            for (r in liveSet) {
                val end = lastUse.getOrElse(r) { chunkStart }
                registerIntervals.getOrPut(r) { mutableListOf() }
                    .add(Interval(r, chunkStart, end, registerTypes.getOrElse(r) { IRDataType.BYTE }))
            }
        }

        if (registerIntervals.isEmpty())
            return

        // Merge adjacent/overlapping intervals for each register.
        // Also merge non-overlapping intervals of the same register into a single
        // contiguous range, because the value in the slot must persist across the
        // gap to the next use (otherwise another register packed to the same slot
        // would clobber it between intervals).
        val mergedIntervals = mutableListOf<Interval>()
        val skipRegs = mutableSetOf<Int>()
        for ((reg, intervals) in registerIntervals) {
            val sorted = intervals.sortedBy { it.start }
            var current = sorted.first()
            for (next in sorted.drop(1)) {
                if (current.type != next.type && !typesCompatible(current.type, next.type)) {
                    skipRegs.add(reg)
                }
                // Always merge: same register's intervals are merged into a single
                // contiguous range to preserve the value across gaps.
                current = Interval(reg, current.start, maxOf(current.end, next.end), current.type)
            }
            mergedIntervals.add(current)
        }
        if (skipRegs.isNotEmpty()) {
            mergedIntervals.removeAll { it.register in skipRegs }
        }

        val conflictGraph = buildConflictGraph(sub)
        val packing = greedyColor(mergedIntervals, conflictGraph, allRegTypes, globalSlotTypes, startSlot)

        if (packing.isNotEmpty())
            rewrite(sub, packing)
    }

    private fun buildConflictGraph(sub: IRSubroutine): Map<Int, Set<Int>> {
        val conflicts = mutableMapOf<Int, MutableSet<Int>>()
        for (chunk in sub.chunks.filterIsInstance<IRCodeChunk>()) {
            val typeRegs = mutableMapOf<IRDataType, MutableSet<Int>>()
            for (instr in chunk.instructions) {
                for ((reg, regNum) in listOf(instr.reg1 to 1, instr.reg2 to 2, instr.reg3 to 3)) {
                    if (reg != null) {
                        val dt = determineIntRegType(instr, regNum)
                        typeRegs.getOrPut(dt) { mutableSetOf() }.add(reg)
                    }
                }
                instr.fpReg1?.let { fpr -> typeRegs.getOrPut(IRDataType.FLOAT) { mutableSetOf() }.add(fpr.value) }
                instr.fpReg2?.let { fpr -> typeRegs.getOrPut(IRDataType.FLOAT) { mutableSetOf() }.add(fpr.value) }

                instr.fcallArgs?.let { fc ->
                    for (arg in fc.arguments) {
                        val r = arg.reg.registerNum.value
                        typeRegs.getOrPut(arg.reg.dt) { mutableSetOf() }.add(r)
                    }
                    for (ret in fc.returns) {
                        val r = ret.registerNum.value
                        typeRegs.getOrPut(ret.dt) { mutableSetOf() }.add(r)
                    }
                }

                val r1 = instr.reg1
                val r2 = instr.reg2
                if (r1 != null && r2 != null) {
                    conflicts.getOrPut(r1) { mutableSetOf() }.add(r2)
                    conflicts.getOrPut(r2) { mutableSetOf() }.add(r1)
                }
            }
            // Add conflicts between registers with incompatible types in the same chunk
            val typeList = typeRegs.entries.toList()
            for (i in typeList.indices) {
                for (j in i + 1 until typeList.size) {
                    val (t1, regs1) = typeList[i]
                    val (t2, regs2) = typeList[j]
                    if (t1 != t2 && !typesCompatible(t1, t2)) {
                        for (r1 in regs1) {
                            for (r2 in regs2) {
                                if (r1 != r2) {
                                    conflicts.getOrPut(r1) { mutableSetOf() }.add(r2)
                                    conflicts.getOrPut(r2) { mutableSetOf() }.add(r1)
                                }
                            }
                        }
                    }
                }
            }
        }
        return conflicts
    }

    private fun buildCFG(sub: IRSubroutine): Map<IRCodeChunkBase, List<IRCodeChunkBase>> {
        val successors = mutableMapOf<IRCodeChunkBase, MutableList<IRCodeChunkBase>>()

        val conditionals = setOf(
            Opcode.BSTCC, Opcode.BSTCS, Opcode.BSTEQ, Opcode.BSTNE,
            Opcode.BSTNEG, Opcode.BSTPOS, Opcode.BSTVC, Opcode.BSTVS,
            Opcode.BGTR, Opcode.BGT, Opcode.BLT,
            Opcode.BGTSR, Opcode.BGTS, Opcode.BLTS,
            Opcode.BGER, Opcode.BGE, Opcode.BLE,
            Opcode.BGESR, Opcode.BGES, Opcode.BLES
        )

        for (chunk in sub.chunks) {
            val succ = mutableListOf<IRCodeChunkBase>()

            if (chunk is IRCodeChunk) {
                val lastInstr = chunk.instructions.lastOrNull()
                val endsWithUnconditional = lastInstr != null && lastInstr.opcode in OpcodesThatBranchUnconditionally
                val endsWithConditional = lastInstr != null && lastInstr.opcode in conditionals

                if (!endsWithUnconditional) {
                    chunk.next?.let { succ.add(it) }
                }
                if (endsWithConditional) {
                    lastInstr.branchTarget?.let { target ->
                        if (!succ.contains(target))
                            succ.add(target)
                    }
                } else if (lastInstr?.opcode == Opcode.JUMP) {
                    succ.clear()
                    lastInstr.branchTarget?.let { succ.add(it) }
                }
            } else {
                chunk.next?.let { succ.add(it) }
            }

            successors[chunk] = succ
        }

        return successors
    }

    private fun computeLiveness(
        sub: IRSubroutine,
        successors: Map<IRCodeChunkBase, List<IRCodeChunkBase>>
    ): Pair<Map<IRCodeChunk, Set<Int>>, Map<IRCodeChunk, Set<Int>>> {

        val liveIn = mutableMapOf<IRCodeChunk, MutableSet<Int>>()
        val liveOut = mutableMapOf<IRCodeChunk, MutableSet<Int>>()
        val gen = mutableMapOf<IRCodeChunk, MutableSet<Int>>()
        val kill = mutableMapOf<IRCodeChunk, MutableSet<Int>>()

        for (chunk in sub.chunks) {
            if (chunk !is IRCodeChunk)
                continue

            val genSet = mutableSetOf<Int>()
            val killSet = mutableSetOf<Int>()

            for (instr in chunk.instructions) {
                val (written, read) = getRegisterAccess(instr)
                for (r in read) {
                    if (r !in killSet)
                        genSet.add(r)
                }
                for (r in written) {
                    killSet.add(r)
                }
            }

            gen[chunk] = genSet
            kill[chunk] = killSet
            liveIn[chunk] = mutableSetOf()
            liveOut[chunk] = mutableSetOf()
        }

        var changed = true

        while (changed) {
            changed = false

            for (chunk in sub.chunks.reversed()) {
                if (chunk !is IRCodeChunk)
                    continue

                val newLiveOut = mutableSetOf<Int>()
                for (succ in successors[chunk].orEmpty()) {
                    if (succ is IRCodeChunk)
                        newLiveOut.addAll(liveIn[succ].orEmpty())
                }

                if (newLiveOut != liveOut[chunk]) {
                    liveOut[chunk] = newLiveOut
                    changed = true
                }

                val newLiveIn = mutableSetOf<Int>()
                newLiveIn.addAll(gen[chunk].orEmpty())
                newLiveIn.addAll(liveOut[chunk].orEmpty() - kill[chunk].orEmpty())

                if (newLiveIn != liveIn[chunk]) {
                    liveIn[chunk] = newLiveIn
                    changed = true
                }
            }
        }

        return liveIn to liveOut
    }

    private fun getRegisterAccess(instr: IRInstruction): Pair<Set<Int>, Set<Int>> {
        val written = mutableSetOf<Int>()
        val read = mutableSetOf<Int>()

        for ((reg, dir) in listOf(
            instr.reg1 to instr.reg1direction,
            instr.reg2 to instr.reg2direction,
            instr.reg3 to instr.reg3direction
        )) {
            when (dir) {
                OperandDirection.READ -> reg?.let { read.add(it) }
                OperandDirection.WRITE -> reg?.let { written.add(it) }
                OperandDirection.READWRITE -> { reg?.let { read.add(it); written.add(it) } }
                OperandDirection.UNUSED -> {}
            }
        }

        for ((fpReg, dir) in listOf(
            instr.fpReg1 to instr.fpReg1direction,
            instr.fpReg2 to instr.fpReg2direction
        )) {
            when (dir) {
                OperandDirection.READ -> fpReg?.let { read.add(it.value) }
                OperandDirection.WRITE -> fpReg?.let { written.add(it.value) }
                OperandDirection.READWRITE -> { fpReg?.let { read.add(it.value); written.add(it.value) } }
                OperandDirection.UNUSED -> {}
            }
        }

        val fcallArgs = instr.fcallArgs
        if (fcallArgs != null) {
            for (arg in fcallArgs.arguments)
                read.add(arg.reg.registerNum.value)
            for (ret in fcallArgs.returns)
                written.add(ret.registerNum.value)
        }

        return written to read
    }

    private fun getRegisterType(instr: IRInstruction, register: Int): IRDataType {
        if (instr.fpReg1?.value == register || instr.fpReg2?.value == register)
            return IRDataType.FLOAT

        return when {
            instr.reg1 == register -> determineIntRegType(instr, 1)
            instr.reg2 == register -> determineIntRegType(instr, 2)
            instr.reg3 == register -> determineIntRegType(instr, 3)
            else -> {
                // fcallArgs registers use their own type from the RegSpec, not instr.type
                val fcallArgs = instr.fcallArgs
                if (fcallArgs != null) {
                    for (arg in fcallArgs.arguments)
                        if (arg.reg.registerNum.value == register)
                            return arg.reg.dt
                    for (ret in fcallArgs.returns)
                        if (ret.registerNum.value == register)
                            return ret.dt
                }
                instr.type ?: IRDataType.BYTE
            }
        }
    }

    private fun determineIntRegType(instr: IRInstruction, regNum: Int): IRDataType {
        val opcode = instr.opcode
        val type = instr.type

        if (type == IRDataType.FLOAT) {
            return when (opcode) {
                Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FTOUB, Opcode.FTOSB,
                Opcode.FCOMP, Opcode.SGN -> IRDataType.BYTE
                Opcode.LOADX, Opcode.STOREX, Opcode.STOREZX -> IRDataType.BYTE
                Opcode.FFROMSL, Opcode.FTOSL -> IRDataType.LONG
                Opcode.LOADI, Opcode.STOREI -> IRDataType.POINTER
                else -> IRDataType.WORD
            }
        }

        if (type == IRDataType.WORD && regNum == 1) {
            return when (opcode) {
                Opcode.SGN, Opcode.STOREZX, Opcode.SQRT -> IRDataType.BYTE
                Opcode.EXT, Opcode.EXTS, Opcode.CONCAT -> IRDataType.LONG
                else -> type
            }
        }

        if (type == IRDataType.LONG && regNum == 1) {
            return when (opcode) {
                Opcode.SGN -> IRDataType.BYTE
                Opcode.SQRT -> IRDataType.WORD
                else -> type
            }
        }

        if (regNum == 2 || regNum == 3) {
            return when (opcode) {
                Opcode.LOADX, Opcode.STOREX -> IRDataType.BYTE
                Opcode.LOADI, Opcode.STOREI -> IRDataType.POINTER
                Opcode.ASRN, Opcode.LSRN, Opcode.LSLN -> IRDataType.BYTE
                else -> type ?: IRDataType.BYTE
            }
        }

        if (regNum == 1) {
            if (opcode in setOf(Opcode.JUMPI, Opcode.CALLI, Opcode.STOREZI))
                return IRDataType.POINTER
            if (opcode in setOf(Opcode.LSIGW, Opcode.MSIGW))
                return IRDataType.WORD
            if (opcode in setOf(Opcode.ASRNM, Opcode.LSRNM, Opcode.LSLNM, Opcode.SQRT, Opcode.LSIGB, Opcode.MSIGB, Opcode.BSIGB, Opcode.MIDB))
                return IRDataType.BYTE
            if (type == IRDataType.BYTE && opcode in setOf(Opcode.EXT, Opcode.EXTS, Opcode.CONCAT))
                return IRDataType.WORD
        }
        return type ?: IRDataType.BYTE
    }

    private fun greedyColor(
        intervals: List<Interval>,
        conflictGraph: Map<Int, Set<Int>>,
        allRegTypes: Map<Int, IRDataType>,
        slotTypes: MutableMap<Int, IRDataType>,
        startSlot: Int
    ): Map<Int, Int> {
        if (intervals.isEmpty())
            return emptyMap()

        val packing = mutableMapOf<Int, Int>()
        // Reserve slots for non-packed registers
        val packedRegNums = intervals.map { it.register }.toSet()
        for ((regNum, type) in allRegTypes) {
            if (regNum !in packedRegNums)
                slotTypes.putIfAbsent(regNum, type)
        }

        val sorted = intervals.sortedWith(compareBy<Interval> { it.type.ordinal }.thenBy { it.start })
        val activeSlots = mutableMapOf<Int, Pair<Int, Int>>()

        for (interval in sorted) {
            activeSlots.entries.removeAll { it.value.second < interval.start }

            var slot = startSlot
            while (true) {
                if (slot !in activeSlots) {
                    val existingType = slotTypes[slot]
                    if (existingType == null || existingType == interval.type || typesCompatible(existingType, interval.type)) {
                        if (!conflictsWithRegister(slot, interval.register, conflictGraph, packing))
                            break
                    }
                }
                slot++
            }

            packing[interval.register] = slot
            slotTypes.putIfAbsent(slot, interval.type)  // keep the first-assigned type to prevent type narrowing via typesCompatible chain
            activeSlots[slot] = Pair(interval.register, interval.end)
        }
        return packing
    }

    private fun typesCompatible(t1: IRDataType, t2: IRDataType): Boolean {
        if (t1 == t2) return true
        return (t1 == IRDataType.POINTER && t2 in setOf(IRDataType.WORD, IRDataType.LONG)) ||
                (t2 == IRDataType.POINTER && t1 in setOf(IRDataType.WORD, IRDataType.LONG))
    }

    private fun conflictsWithRegister(
        slot: Int,
        regNum: Int,
        conflictGraph: Map<Int, Set<Int>>,
        packing: Map<Int, Int>
    ): Boolean {
        val conflictingRegs = conflictGraph[regNum] ?: return false
        for (cr in conflictingRegs) {
            val crSlot = packing[cr]
            if (crSlot != null && crSlot == slot)
                return true
            if (cr !in packing && cr == slot)
                return true
        }
        return false
    }

    private fun rewrite(sub: IRSubroutine, packing: Map<Int, Int>) {
        for (chunk in sub.chunks) {
            if (chunk !is IRCodeChunk)
                continue

            for (i in chunk.instructions.indices) {
                val instr = chunk.instructions[i]

                val newReg1 = if (instr.reg1 != null) (packing[instr.reg1] ?: instr.reg1) else null
                val newReg2 = if (instr.reg2 != null) (packing[instr.reg2] ?: instr.reg2) else null
                val newReg3 = if (instr.reg3 != null) (packing[instr.reg3] ?: instr.reg3) else null
                val oldFpReg1 = instr.fpReg1
                val newFpReg1 = if (oldFpReg1 != null && oldFpReg1.value in packing)
                    RegisterNum(packing[oldFpReg1.value]!!) else oldFpReg1
                val oldFpReg2 = instr.fpReg2
                val newFpReg2 = if (oldFpReg2 != null && oldFpReg2.value in packing)
                    RegisterNum(packing[oldFpReg2.value]!!) else oldFpReg2
                val oldFcallArgs = instr.fcallArgs
                val newFcallArgs = if (oldFcallArgs != null)
                    remapFcallArgs(oldFcallArgs, packing) else oldFcallArgs

                val anyChange = newReg1 != instr.reg1 || newReg2 != instr.reg2 || newReg3 != instr.reg3
                        || newFpReg1 != instr.fpReg1 || newFpReg2 != instr.fpReg2
                        || newFcallArgs != instr.fcallArgs

                if (anyChange) {
                    chunk.instructions[i] = instr.copy(
                        reg1 = newReg1,
                        reg2 = newReg2,
                        reg3 = newReg3,
                        fpReg1 = newFpReg1,
                        fpReg2 = newFpReg2,
                        fcallArgs = newFcallArgs
                    )
                }
            }
        }
    }

    private fun remapFcallArgs(args: FunctionCallArgs, packing: Map<Int, Int>): FunctionCallArgs {
        var changed = false

        val newArgs = args.arguments.map { arg ->
            val newRegNum = packing[arg.reg.registerNum.value]
            if (newRegNum != null && newRegNum != arg.reg.registerNum.value) {
                changed = true
                FunctionCallArgs.ArgumentSpec(
                    arg.name, arg.address,
                    FunctionCallArgs.RegSpec(arg.reg.dt, RegisterNum(newRegNum), arg.reg.callingConventionSlot, arg.reg.statusflag)
                )
            } else arg
        }

        val newReturns = args.returns.map { ret ->
            val newRegNum = packing[ret.registerNum.value]
            if (newRegNum != null && newRegNum != ret.registerNum.value) {
                changed = true
                FunctionCallArgs.RegSpec(ret.dt, RegisterNum(newRegNum), ret.callingConventionSlot, ret.statusflag)
            } else ret
        }

        return if (changed)
            FunctionCallArgs(newArgs, newReturns)
        else
            args
    }
}
