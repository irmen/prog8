package prog8.codegen.m68k

// M68k assembly-level peephole optimizer over a MutableList<String> of assembly lines: only
// lines a peephole matches are modified or removed (comments stay intact), and all passes
// re-run until a full pass makes no changes. Add a pass by registering it in optimizeAssembly().

internal fun optimizeAssembly(lines: MutableList<String>) {
    val pretrimmed = lines.map { it.trimStart() }.toMutableList()

    while (true) {
        var modified = false

        /** Runs a single optimization pass: applies any modifications, then recomputes the line windows. */
        fun runPass(
            mods: List<Modification>,
            windowSize: Int,
            currentLines: Sequence<List<TrimmedLine>>
        ): Sequence<List<TrimmedLine>> {
            if (mods.isNotEmpty()) {
                applyModifications(mods, lines, pretrimmed)
                modified = true
                return getLinesBy(pretrimmed, lines, windowSize)
            }
            return currentLines
        }

        var by2 = getLinesBy(pretrimmed, lines, 2)
        by2 = runPass(optimizeJmpToNextLabel(by2), 2, by2)
        by2 = runPass(optimizeTailCall(by2), 2, by2)
        by2 = runPass(optimizeRedundantTst(by2), 2, by2)
        var by3 = getLinesBy(pretrimmed, lines, 3)
        by3 = runPass(optimizeConditionalOverJump(by3), 3, by3)

        // DBRA peephole for repeat loops is now obsolete: counted loops are emitted as IRLoopChunk and directly translated to dbra.
        // The old REGFILE-DEPENDENT pattern (move #N,p8_regfile+slot / label: body / subq #1,slot / bne label) no longer occurs.

        if (!modified)
            break
    }
    // drop a `lea base,a0` when a0 already holds that same base.
    // a0 is a real CPU register, so this pass is independent of the
    // p8_regfile layout and of the register allocator.
    val leaMods = optimizeRedundantLea(pretrimmed)
    if (leaMods.isNotEmpty()) {
        applyModifications(leaMods, lines, pretrimmed)
    }
}

/**
 * Remove `lea base,a0` instructions that recompute a base a0 is already known to hold. The lea
 * itself is required (the m68k has no absolute-indexed addressing), but a sequence of accesses
 * to the same array reloads the base several times over.
 */
private fun optimizeRedundantLea(allPretrimmed: List<String>): List<Modification> {
    // Control transfers end the tracked value (a call clobbers a0); cc branches are excluded, their label resets it.
    val controlTransfers = setOf(
        "bra", "bsr", "jmp", "jsr", "rts", "rtr", "rte", "trap",
        "dbcc", "dbcs", "dbhi", "dbhs", "dblo", "dbls", "dbne", "dbnz", "dbeq", "dbz",
        "dbmi", "dbpl", "dbge", "dblt", "dbgt", "dble", "dbvc", "dbvs", "dbtst", "dbset",
        "dbclr", "dbchg", "dbf", "dbra"
    )
    // A lea whose destination is a0, and the value it leaves there.
    fun leaBase(instr: String): String? {
        if (!instr.startsWith("lea ") && !instr.startsWith("lea\t"))
            return null
        val operands = instr.substringAfter(' ').substringBefore(';')
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2 || parts[1].trim() != SCRATCH_A0)
            return null
        return parts[0].trim()
    }

    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { TrimmedLine(it.value.trimStart(), it.value.trimStart(), it.index) }

    val mods = mutableListOf<Modification>()
    var a0Base: String? = null
    for (line in filtered) {
        val instr = line.instruction
        if (hasLabel(line.value)) {
            // fallthrough into a label is fine, but the label may also be entered from elsewhere
            a0Base = null
            continue
        }
        val mnemonic = instr.substringBefore(' ').substringBefore('\t').lowercase()
        if (mnemonic in controlTransfers) {
            a0Base = null
            continue
        }
        val base = leaBase(instr.lowercase())
        if (base != null) {
            if (base == a0Base)
                mods.add(Modification(line.index, remove = true, replacement = null))
            else
                a0Base = base
            continue
        }
        // Any non-parenthesised mention of a0 means it is written
        if (instr.replace(parenthesised, "").contains(SCRATCH_A0, ignoreCase = true))
            a0Base = null
    }
    return mods
}

/** Matches a parenthesised group, for stripping addressing modes. */
private val parenthesised = Regex("""\([^)]*\)""")

/** A single edit to apply: either remove a line (keeping its label if present) or replace it. */
private class Modification(val lineIndex: Int, val remove: Boolean, val replacement: String?)

/** Pre-trimmed assembly line that also knows its original text and source line index. */
private class TrimmedLine(val value: String, val trimmed: String, val index: Int) {
    val instruction: String = trimmed.instructionPart()
}

private fun getLinesBy(pretrimmed: MutableList<String>, originalLines: MutableList<String>, windowSize: Int): Sequence<List<TrimmedLine>> =
    pretrimmed.asSequence()
        .withIndex()
        .filter { it.value.isNotBlank() && !it.value.startsWith(';') }
        .map { TrimmedLine(originalLines[it.index], it.value, it.index) }
        .windowed(windowSize, partialWindows = false)

private fun applyModifications(modifications: List<Modification>, lines: MutableList<String>, pretrimmed: MutableList<String>) {
    for (modification in modifications.sortedBy { it.lineIndex }.reversed()) {
        val idx = modification.lineIndex
        if (modification.remove) {
            val pretrim = pretrimmed.getOrNull(idx)
            // Keep the line if it carries a label (other code may jump to it), else drop it entirely.
            if (pretrim.isNullOrBlank() || pretrim.startsWith(';')) {
                lines.removeAt(idx)
            } else if (hasLabel(pretrim)) {
                val label = keepLabel(pretrim)
                if (label.isNotEmpty())
                    lines[idx] = label
                else
                    lines.removeAt(idx)
            } else {
                lines.removeAt(idx)
            }
            pretrimmed.removeAt(idx)
        } else {
            lines[idx] = modification.replacement!!
            pretrimmed[idx] = modification.replacement.trimStart()
        }
    }
}

// === helpers ===

/** Strips a leading label (e.g. "mylabel:  move.l ..." -> "move.l ..."). */
private fun String.instructionPart(): String {
    val idx = indexOf(':')
    return if (idx >= 0) substring(idx + 1).trimStart() else this
}

private fun hasLabel(line: String): Boolean =
    line.length > 1 && !line.startsWith(';') && ':' in line

/** Returns just the "label:" part of a line, or "" if there is no label. */
private fun keepLabel(line: String): String {
    val idx = line.indexOf(':')
    return if (idx >= 0) line.substring(0, idx + 1) else ""
}

// Deliberately only recognizes move.b/w/l, not movea or movem: movea always writes to an address
// register and movem uses register-list operands, both rejected by isSafeMemoryOperand, so they
// never participate in the patterns we optimize.
private fun String.isMove(): Boolean =
    startsWith("move.b") || startsWith("move.w") || startsWith("move.l")

/** Size suffix of a move instruction: 'b', 'w' or 'l'. */
private fun String.moveSize(): Char = substringAfter('.').firstOrNull() ?: ' '

/** Splits a move instruction "move.S  src, dst" into (src, dst), or null if it isn't a move. */
private fun String.moveOperands(): Pair<String, String>? {
    val sp = indexOf(' ')
    if (sp < 0) return null
    val rest = substring(sp + 1)
    val commentIdx = rest.indexOf(';')
    val ops = (if (commentIdx >= 0) rest.substring(0, commentIdx) else rest).split(',', limit = 2)
    if (ops.size != 2) return null
    return ops[0].trim() to ops[1].trim()
}

private fun String.isRegister(): Boolean =
    this.matches(Regex("^(d[0-7]|a[0-7]|fp[0-9]+|sp|pc)$"))

/**
 * A plain memory operand that can be safely read twice and assumed to hit the same location
 * (e.g. "p8_regfile+268", "sys.DOSBase", "$1234", "symbol"); excludes registers and indirect
 * or indexed addressing whose effective address may change between the two accesses.
 */
private fun String.isSafeMemoryOperand(): Boolean {
    if (isEmpty()) return false
    if (isRegister()) return false
    if ('(' in this || ')' in this) return false
    return true
}

// === peephole passes ===

private fun optimizeJmpToNextLabel(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  bra  LABEL / LABEL:  ->  remove the branch; keep any label on the branch line (also the jmp form)
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val firstInstr = lines[0].instruction
        val secondLine = lines[1].trimmed
        val branchMnemonic = when {
            firstInstr.startsWith("bra ") -> "bra"
            firstInstr.startsWith("jmp ") -> "jmp"
            else -> null
        } ?: continue
        val target = firstInstr.substringAfter("$branchMnemonic ").trim()
        // Skip indirect jumps like jmp ([p8_regfile+12]) or jmp (a0)
        if (target.startsWith("("))
            continue
        if (hasLabel(secondLine)) {
            val label = keepLabel(secondLine).removeSuffix(":")
            if (target == label) {
                if (hasLabel(lines[0].trimmed)) {
                    // The branch line also carries a label - keep the label, drop the branch
                    mods.add(Modification(lines[0].index, false, keepLabel(lines[0].trimmed)))
                } else {
                    mods.add(Modification(lines[0].index, true, null))
                }
            }
        }
    }
    return mods
}

private fun optimizeConditionalOverJump(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  bCOND  THEN
    //  bra    JOIN
    // THEN:
    //      ...                 ->  bINVERTED_COND  JOIN
    //  JOIN:                       THEN:
    //                              ...
    //
    // The conditional branch and unconditional jump are adjacent, so inverting the condition
    // makes the old fallthrough target the taken path and removes the unconditional jump.
    val inverse = mapOf(
        "bcc" to "bcs", "bcs" to "bcc",
        "beq" to "bne", "bne" to "beq",
        "bmi" to "bpl", "bpl" to "bmi",
        "bvc" to "bvs", "bvs" to "bvc",
        "bhi" to "bls", "bls" to "bhi",
        "bhs" to "blo", "blo" to "bhs",
        "bgt" to "ble", "ble" to "bgt",
        "bge" to "blt", "blt" to "bge"
    )
    val mods = mutableListOf<Modification>()
    for (window in linesBy) {
        val first = window[0]
        val second = window[1]
        val third = window[2]
        if (hasLabel(second.trimmed) || !hasLabel(third.trimmed))
            continue

        val firstParts = first.instruction.substringBefore(';').trim().split(Regex("\\s+"), limit = 2)
        val secondParts = second.instruction.substringBefore(';').trim().split(Regex("\\s+"), limit = 2)
        if (firstParts.size != 2 || secondParts.size != 2)
            continue
        val inverted = inverse[firstParts[0]] ?: continue
        if (secondParts[0] !in setOf("bra", "jmp"))
            continue
        if (secondParts[1].startsWith("(") || firstParts[1].startsWith("("))
            continue

        val conditionalTarget = firstParts[1].trim()
        val targetLabel = keepLabel(third.trimmed).removeSuffix(":")
        if (conditionalTarget != targetLabel)
            continue

        val indent = first.value.takeWhile { it.isWhitespace() }
        val label = keepLabel(first.trimmed)
        val replacement = "$indent$label$inverted  ${secondParts[1]}"
        mods += Modification(first.index, false, replacement)
        mods += Modification(second.index, true, null)
    }
    return mods
}

private fun optimizeTailCall(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  bsr  LABEL / rts  ->  bra  LABEL (tail call); only direct targets, also handles the jsr form
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val firstInstr = lines[0].instruction
        val secondInstr = lines[1].instruction
        // Don't optimize if the rts line has a label (it's a jump target)
        if (hasLabel(lines[1].trimmed))
            continue
        val callMnemonic = when {
            firstInstr.startsWith("bsr ") -> "bsr"
            firstInstr.startsWith("jsr ") -> "jsr"
            else -> null
        } ?: continue
        if (secondInstr == "rts") {
            val target = firstInstr.substringAfter("$callMnemonic ").trim()
            // Skip indirect/library calls like bsr (a0), jsr (a0) or jsr dos.VPrintf(a6)
            if ('(' in target)
                continue
            val indent = lines[0].value.takeWhile { it.isWhitespace() }
            if (hasLabel(lines[0].trimmed)) {
                // Keep the label, replace call with branch and remove rts
                val label = keepLabel(lines[0].trimmed)
                mods.add(Modification(lines[0].index, false, "$label\n${indent}bra  $target"))
                mods.add(Modification(lines[1].index, true, null))
            } else {
                // Replace call with branch and remove rts
                mods.add(Modification(lines[0].index, false, "${indent}bra  $target"))
                mods.add(Modification(lines[1].index, true, null))
            }
        }
    }
    return mods
}

private fun optimizeRedundantTst(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  move.S / andi.S / ori.S  X, DST followed by  tst.S  DST  ->  remove the tst: they set N/Z.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        // Don't remove tst if it's a jump target
        if (hasLabel(lines[1].trimmed))
            continue
        if (!second.startsWith("tst."))
            continue
        val dst0 = first.flagSettingDest() ?: continue
        if (first.moveSize() != second.moveSize())
            continue
        val tstRaw = second.substringAfter('.').substringAfter(' ').trim()
        val tstOperand = if (';' in tstRaw) tstRaw.substringBefore(';').trim() else tstRaw
        if (dst0 == tstOperand && dst0.isSafeMemoryOperand()) {
            mods.add(Modification(lines[1].index, true, null))
        }
    }
    return mods
}

/**
 * If this instruction sets N/Z flags and writes to a single destination, return that destination.
 * Covers move, andi, ori, eori (the ones the codegen emits before tst); add/sub also set condition
 * codes but the codegen always inserts a move before branching, so they are never followed by a tst.
 */
private fun String.flagSettingDest(): String? {
    val stripped = if (';' in this) substring(0, indexOf(';')).trim() else this
    return when {
        isMove() -> moveOperands()?.second
        startsWith("andi.") || startsWith("ori.") || startsWith("eori.") -> {
            val sp = stripped.indexOf(' ')
            if (sp < 0) return null
            val ops = stripped.substring(sp + 1).split(',', limit = 2)
            if (ops.size != 2) return null
            ops[1].trim()
        }
        else -> null
    }
}

