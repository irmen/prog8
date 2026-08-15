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
        by2 = runPass(optimizeRedundantReload(by2), 2, by2)
        by2 = runPass(optimizeBounceToGlobal(by2, pretrimmed), 2, by2)
        by2 = runPass(optimizeJmpToNextLabel(by2), 2, by2)
        by2 = runPass(optimizeTailCall(by2), 2, by2)
        by2 = runPass(optimizeRedundantTst(by2), 2, by2)

        if (!modified)
            break
    }
}

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
            if (pretrim == null || pretrim.isBlank() || pretrim.startsWith(';')) {
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
    val ops = substring(sp + 1).split(',', limit = 2)
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

private fun optimizeRedundantReload(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  move.S  d0, MEM
    //  move.S  MEM, d0        ->  remove the second line
    //
    // The reload is redundant: d0 already holds the value (it was just stored from d0 and is
    // unchanged between the two instructions), so dropping the reload leaves d0 intact and the
    // store to MEM stays for any later readers.
    // MEM must be a plain memory operand (see isSafeMemoryOperand) so both accesses hit the
    // exact same location.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        // Never remove an instruction that is a jump target: arriving via the label, d0 is
        // not guaranteed to hold the stored value, so the reload would be needed there.
        if (hasLabel(lines[1].trimmed))
            continue
        if (first.isMove() && second.isMove() && first.moveSize() == second.moveSize()) {
            val (src0, dst0) = first.moveOperands() ?: continue
            val (src1, dst1) = second.moveOperands() ?: continue
            if (src0 == "d0" && dst1 == "d0" && dst0 == src1 && dst0.isSafeMemoryOperand()) {
                mods.add(Modification(lines[1].index, true, null))
            }
        }
    }
    return mods
}

private fun optimizeBounceToGlobal(linesBy: Sequence<List<TrimmedLine>>, allPretrimmed: List<String>): List<Modification> {
    //  move.S  d0, p8_regfile+OFF     ; spill result to regfile temp
    //  move.S  p8_regfile+OFF, GLOBAL ; copy temp -> global
    //  ->
    //  move.S  d0, GLOBAL             ; store directly, skip regfile bounce
    //
    // Only safe when p8_regfile+OFF is NOT read again in the remainder of the current
    // subroutine. We scan forward from the global store to the next subroutine boundary
    // to confirm this.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (hasLabel(lines[0].trimmed) || hasLabel(lines[1].trimmed))
            continue
        if (first.isMove() && second.isMove() && first.moveSize() == second.moveSize()) {
            val (src0, dst0) = first.moveOperands() ?: continue
            val (src1, dst1) = second.moveOperands() ?: continue
            if (src0 == "d0" && dst1 != "d0" && dst0 == src1
                && dst0.isRegfileSlot() && dst1.isSafeMemoryOperand()
                && !isSlotReadAfter(dst0, lines[1].index + 1, allPretrimmed)
            ) {
                val size = first.moveSize()
                val indent = lines[1].value.takeWhile { it.isWhitespace() }
                mods.add(Modification(lines[0].index, true, null))   // remove regfile spill
                mods.add(Modification(lines[1].index, false, "${indent}move.$size  d0, $dst1"))  // direct store
            }
        }
    }
    return mods
}

private fun optimizeJmpToNextLabel(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  jmp  LABEL
    //  LABEL:          ->  remove the jmp (keep any label on the jmp line itself)
    //
    // The jmp is dead code because the target label immediately follows.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val firstInstr = lines[0].instruction
        val secondLine = lines[1].trimmed
        if (firstInstr.startsWith("jmp ") && hasLabel(secondLine)) {
            val target = firstInstr.substringAfter("jmp ").trim()
            val label = keepLabel(secondLine).removeSuffix(":")
            if (target == label) {
                if (hasLabel(lines[0].trimmed)) {
                    // The jmp line also carries a label - keep the label, drop the jmp
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
    //  jsr  LABEL
    //  rts              ->  jmp  LABEL
    //
    // When a subroutine call is immediately followed by a return, replace the jsr with a jmp.
    // The called routine will return directly to the caller's caller (tail call optimization).
    // Only handles direct label targets, not indirect (jsr (a0) etc.).
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val firstInstr = lines[0].instruction
        val secondInstr = lines[1].instruction
        // Don't optimize if the rts line has a label (it's a jump target)
        if (hasLabel(lines[1].trimmed))
            continue
        if (firstInstr.startsWith("jsr ") && secondInstr == "rts") {
            val target = firstInstr.substringAfter("jsr ").trim()
            // Skip indirect calls like jsr (a0) or jsr (a6)
            if (target.startsWith("("))
                continue
            val indent = lines[0].value.takeWhile { it.isWhitespace() }
            if (hasLabel(lines[0].trimmed)) {
                // Keep the label, replace jsr with jmp and remove rts
                val label = keepLabel(lines[0].trimmed)
                mods.add(Modification(lines[0].index, false, "$label\n${indent}jmp  $target"))
                mods.add(Modification(lines[1].index, true, null))
            } else {
                // Replace jsr with jmp and remove rts
                mods.add(Modification(lines[0].index, false, "${indent}jmp  $target"))
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
    // On M68k, move sets N, Z (and clears V, C) based on the value moved. So a tst
    // immediately after a move to the same location is redundant - the flags are already set.
    // Only applies when both instructions use the same size suffix and the tst operand matches
    // the move destination.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        // Don't remove tst if it's a jump target
        if (hasLabel(lines[1].trimmed))
            continue
        if (first.isMove() && second.startsWith("tst.") && first.moveSize() == second.moveSize()) {
            val (_, dst0) = first.moveOperands() ?: continue
            val tstOperand = second.substringAfter('.').substringAfter(' ').trim()
            if (dst0 == tstOperand && dst0.isSafeMemoryOperand()) {
                mods.add(Modification(lines[1].index, true, null))
            }
        }
    }
    return mods
}

/** True if this operand is a prog8 register-file slot (e.g. "p8_regfile+268", "p8_fregfile+12"). */
private fun String.isRegfileSlot(): Boolean =
    startsWith("p8_regfile+") || startsWith("p8_fregfile+")

/**
 * Scans forward from [startIndex] to the next subroutine boundary (or end of file) and
 * returns true if [slot] is referenced in any instruction line in that range.
 * A "reference" means the slot string appears as a standalone operand (not as a prefix
 * of a longer offset like p8_regfile+640 when checking p8_regfile+64).
 */
private fun isSlotReadAfter(slot: String, startIndex: Int, allPretrimmed: List<String>): Boolean {
    for (i in startIndex until allPretrimmed.size) {
        val line = allPretrimmed[i]
        if (line.startsWith("; ---- Subroutine:") || line.startsWith("; End of subroutine:")
            || line.startsWith("prog8_program_end"))
            break
        if (line.isBlank() || line.startsWith(';')) continue
        if (isSlotReferencedInLine(slot, line)) return true
    }
    return false
}

private fun isSlotReferencedInLine(slot: String, line: String): Boolean {
    var from = 0
    while (true) {
        val idx = line.indexOf(slot, from)
        if (idx < 0) return false
        val after = idx + slot.length
        // Ensure it's not a prefix of a longer offset (e.g. p8_regfile+6 vs p8_regfile+64)
        if (after < line.length && line[after].isDigit()) {
            from = after
            continue
        }
        return true
    }
}
