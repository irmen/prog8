package prog8.codegen.m68k

// M68k assembly-level peephole optimizer.
//
// It operates on a MutableList<String> of assembly lines. Only instructions that a peephole
// actually matches are modified or removed; every other line (including all comments) is left
// completely untouched. This keeps the generated .asm readable and comments intact.
//
// To add a new peephole:
//   1. Write a private function `optimizeXxx(window: Sequence<List<TrimmedLine>>): List<Modification>`
//      that inspects a sliding window of consecutive instructions and returns the Modifications
//      it wants to apply.
//   2. Register it in optimizeAssembly() by adding a line:
//          byN = runPass(optimizeXxx(byN), N, byN)
// The optimizer re-runs all passes until a full pass makes no changes (so cascading optimizations
// are picked up too).

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
 * Remove `lea base,a0` instructions that recompute a base a0 is already known to hold.
 *
 * Indexed array access is emitted as `lea base,a0` followed by `(a0,d0.w*2)`. The lea itself is
 * required - absolute-indexed addressing (`base(a0,d0.w*2)`) does not exist on the m68k - but in
 * a sequence of accesses to the same array the base is reloaded several times over.
 *
 * A tracked value is dropped (a0 treated as unknown) whenever the scan sees:
 *  - a label: incoming control flow may not be the fallthrough path
 *  - any control transfer: a branch may reach elsewhere, and a call clobbers a0 (it is a
 *    scratch register in the m68k calling convention)
 *  - any instruction that names a0 outside a parenthesised address, i.e. writes it
 *
 * Anything else - including pure reads such as `move.w (a0,d0.w),d1` - leaves it untouched.
 */
private fun optimizeRedundantLea(allPretrimmed: List<String>): List<Modification> {
    // Unconditional control flow, calls and returns end the tracked value: control leaves via a
    // path where a0's contents are unknown (and a call clobbers a0, a scratch register in the
    // m68k calling convention). The whole DBcc family is included as loop control.
    // Plain condition-code branches (bne, bge, ...) are deliberately NOT in this set: they do not
    // modify a0, and their target is always a label, which resets the tracked value below. So the
    // fallthrough keeps the tracked value and the branch target is re-entered conservatively.
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
            // fallthrough into a label is fine, but the label may also be entered from
            // elsewhere, so the tracked value is no longer known to hold
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
            // If the line carries a label we must keep it (other code may jump to it);
            // otherwise the line is dropped entirely.
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

// Deliberately only recognizes move.b/w/l (data register moves), not movea or movem:
// movea always writes to an address register (rejected by isSafeMemoryOperand), and
// movem uses register-list operands (also rejected). So they can never participate in
// the patterns we optimize, and excluding them keeps moveOperands() simple.
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
 * (e.g. "p8_regfile+268", "sys.DOSBase", "$1234", "symbol"). Excludes data/address registers
 * and any indirect/indexed addressing ((a0), (a0)+, ($1234,a0), ...) whose effective address may
 * change between the two accesses.
 */
private fun String.isSafeMemoryOperand(): Boolean {
    if (isEmpty()) return false
    if (isRegister()) return false
    if ('(' in this || ')' in this) return false
    return true
}

// === peephole passes ===

private fun optimizeJmpToNextLabel(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  bra  LABEL
    //  LABEL:          ->  remove the branch (keep any label on the branch line itself)
    //
    // The branch is dead code because the target label immediately follows.
    // (also handles the older jmp form)
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

private fun optimizeTailCall(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  bsr  LABEL
    //  rts              ->  bra  LABEL
    //
    // When a subroutine call is immediately followed by a return, replace the call with a branch.
    // The called routine will return directly to the caller's caller (tail call optimization).
    // Only handles direct label targets, not indirect (bsr (a0) etc.).
    // (also handles the older jsr form, normalizing it to bra as well)
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
    //  move.S  SRC, DST
    //  tst.S   DST              ->  remove the tst
    //
    //  andi.S  #imm, DST
    //  tst.S   DST              ->  remove the tst
    //
    //  ori.S   #imm, DST
    //  tst.S   DST              ->  remove the tst
    //
    // On M68k, move/andi/ori/eori set N, Z based on the result. So a tst
    // immediately after to the same location is redundant.
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
 * Covers move, andi, ori, eori (the ones the codegen emits before tst).
 * Note: add/sub also set condition codes, but the codegen always inserts a move before branching,
 * so add/sub are never followed by a redundant tst.
 */
private fun String.flagSettingDest(): String? {
    val stripped = if (';' in this) substring(0, indexOf(';')).trim() else this
    return when {
        isMove() -> moveOperands()?.second
        startsWith("andi.") || startsWith("ori.") || startsWith("eori.") -> {
            // andi.S #imm, dst
            val sp = stripped.indexOf(' ')
            if (sp < 0) return null
            val ops = stripped.substring(sp + 1).split(',', limit = 2)
            if (ops.size != 2) return null
            ops[1].trim()
        }
        else -> null
    }
}

