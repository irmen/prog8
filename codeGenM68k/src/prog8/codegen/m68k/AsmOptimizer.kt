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
        by2 = runPass(optimizeMsigbSpill(by2, pretrimmed), 2, by2)

        // DBRA peephole for repeat loops: move #N,p8_regfile+slot / label: body / subq #1,slot / bne label  ->  move #N-1,d7 / label: body / dbra d7,label
        val dbraMods = optimizeDbraRepeatLoops(pretrimmed, lines)
        if (dbraMods.isNotEmpty()) {
            applyModifications(dbraMods, lines, pretrimmed)
            modified = true
        }

        if (!modified)
            break
    }
    // M3 is a full-file scan (not a 2-window peephole) and is idempotent; run once after the fixed-point loop
    val clampMods = optimizeClampImmediate(pretrimmed, lines)
    if (clampMods.isNotEmpty()) {
        applyModifications(clampMods, lines, pretrimmed)
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

private fun optimizeMsigbSpill(linesBy: Sequence<List<TrimmedLine>>, allPretrimmed: List<String>): List<Modification> {
    //  move.w/l  SYMBOL, p8_regfile+N   ; spill word/long to slot
    //  move.b    p8_regfile+N, d0       ; read MSB
    //  ->  move.b  SYMBOL, d0           ; big-endian: MSB is at SYMBOL+0, no spill needed
    // Keep the spill only if the slot is read again later in the subroutine.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        if (hasLabel(lines[0].trimmed) || hasLabel(lines[1].trimmed)) continue
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (!first.isMove() || !second.isMove()) continue
        if (second.moveSize() != 'b') continue
        val firstSize = first.moveSize()
        if (firstSize != 'w' && firstSize != 'l') continue
        val (src0, dst0) = first.moveOperands() ?: continue
        val (src1, dst1) = second.moveOperands() ?: continue
        if (dst1 != "d0") continue
        if (src1 != dst0) continue
        if (!dst0.isRegfileSlot()) continue
        if (src0.isRegfileSlot() || src0.isRegister()) continue
        if ('(' in src0 || ')' in src0) continue
        // src0 must be a plain memory operand (symbol)
        if (!src0.isSafeMemoryOperand()) continue
        // If the slot is read again later, keep the spill; otherwise we can remove it
        val indent2 = lines[1].value.takeWhile { it.isWhitespace() }
        if (isSlotReadAfter(dst0, lines[1].index + 1, allPretrimmed)) {
            // keep spill, just rewrite the byte load to read directly from the symbol
            mods.add(Modification(lines[1].index, false, "${indent2}move.b  $src0,d0"))
        } else {
            mods.add(Modification(lines[0].index, true, null))
            mods.add(Modification(lines[1].index, false, "${indent2}move.b  $src0,d0"))
        }
    }
    return mods
}

private fun optimizeDbraRepeatLoops(pretrimmed: List<String>, lines: MutableList<String>): List<Modification> {
    // repeat N { body } -> move.w #N-1,d7 / label: body / dbra d7,label
    // Only for hidden p8_regfile slots (repeat counters), not user variables.
    // Requires: body has no d7 dest, no bsr/jsr (calls clobber d7), and does not reference the counter slot.
    val filtered = pretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { TrimmedLine(lines[it.index], it.value.trimStart(), it.index) }

    val mods = mutableListOf<Modification>()
    val modified = mutableSetOf<Int>()

    for (i in filtered.indices) {
        if (filtered[i].index in modified) continue
        val labelLine = filtered[i]
        if (!labelLine.trimmed.endsWith(":")) continue
        val label = labelLine.trimmed.removeSuffix(":").trim()
        if (!label.startsWith("p8_label_gen_")) continue
        if (i == 0) continue
        val initLine = filtered[i - 1]
        if (initLine.index in modified) continue
        val initInstr = initLine.instruction
        if (!initInstr.isMove()) continue
        val (initSrc, initDst) = initInstr.moveOperands() ?: continue
        if (!initSrc.startsWith("#")) continue
        if (!initDst.isRegfileSlot()) continue
        val initSize = initInstr.moveSize()
        if (initSize != 'b' && initSize != 'w') continue
        val initImmStr = initSrc.removePrefix("#")
        val initImm = initImmStr.toIntOrNull() ?: continue

        // find bne that targets this label within next 11 filtered lines
        var branchIdx = -1
        var branchLine: TrimmedLine? = null
        for (j in i + 1 until minOf(filtered.size, i + 12)) {
            val cand = filtered[j]
            val instr = cand.instruction
            if (instr.startsWith("bne ")) {
                val tgt = instr.substringAfter("bne ").trim().substringBefore(';').trim()
                if (tgt == label) {
                    branchIdx = j
                    branchLine = cand
                    break
                }
            }
        }
        if (branchIdx == -1 || branchLine == null) continue
        if (branchLine.index in modified) continue
        if (branchIdx - 1 < 0) continue
        val subqLine = filtered[branchIdx - 1]
        if (subqLine.index in modified) continue
        val subqInstr = subqLine.instruction
        if (!subqInstr.startsWith("subq.")) continue
        val subqSize = subqInstr.substringAfter("subq.").firstOrNull() ?: continue
        // require same size (b->b, w->w); promotion from b init to w dbra is handled via new move.w
        if (subqSize != initSize) continue
        val subqParts = subqInstr.substringAfter(' ').trim().split(',', limit = 2).map { it.trim().substringBefore(';').trim() }
        if (subqParts.size != 2) continue
        if (subqParts[0] != "#1") continue
        if (subqParts[1] != initDst) continue

        // body between label+1 and subq-1 must not use d7, must not contain bsr/jsr,
        // and must not read or write the loop-counter slot (the slot is no longer updated).
        var bodyOk = true
        for (k in i + 1 until branchIdx - 1) {
            val bodyLine = filtered[k].trimmed
            val bodyInstr = filtered[k].instruction
            if (bodyInstr.contains("d7")) { bodyOk = false; break }
            if (bodyInstr.startsWith("bsr ") || bodyInstr.startsWith("jsr ") || bodyInstr.contains(" bsr ") || bodyInstr.contains(" jsr ")) { bodyOk = false; break }
            if (isSlotReferencedInLine(initDst, bodyLine)) { bodyOk = false; break }
        }
        if (!bodyOk) continue

        val newImm = if (initImm == 0) {
            if (initSize == 'b') 255 else 65535
        } else initImm - 1

        val initIndent = lines[initLine.index].takeWhile { it.isWhitespace() }
        val initLabel = if (hasLabel(initLine.trimmed)) keepLabel(initLine.trimmed) + "\n" else ""
        mods.add(Modification(initLine.index, false, "$initLabel${initIndent}move.w  #$newImm,d7"))
        modified.add(initLine.index)

        mods.add(Modification(subqLine.index, true, null))
        modified.add(subqLine.index)

        val branchIndent = lines[branchLine.index].takeWhile { it.isWhitespace() }
        val branchLabel = if (hasLabel(branchLine.trimmed)) keepLabel(branchLine.trimmed) + "\n" else ""
        mods.add(Modification(branchLine.index, false, "$branchLabel${branchIndent}dbra  d7,$label"))
        modified.add(branchLine.index)
    }
    return mods
}

private fun optimizeClampImmediate(allPretrimmed: List<String>, lines: MutableList<String>): List<Modification> {
    // M3: fold clamp bounds that were materialized as move #imm,SLOT.
    // Only fold when we can identify the full clamp block, to avoid touching unrelated compares.
    // Pattern (filtered lines, b/w/l size must match):
    //   cmp.S  SLOT_MIN,d0
    //   bge/bhs  .clamp_max
    //   move.S SLOT_MIN,d0
    //   bra  .clamp_done
    //   .clamp_max:
    //   cmp.S  SLOT_MAX,d0
    //   ble/bls  .clamp_done
    //   move.S SLOT_MAX,d0
    //   .clamp_done:
    // where SLOT_MIN/MAX were defined as move.S #imm,SLOT within a few lines before the block.
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { TrimmedLine(lines[it.index], it.value.trimStart(), it.index) }

    fun findDefine(slot: String, size: Char, useIdx: Int, limit: Int = 6): Pair<Int,String>? {
        var count = 0
        for (k in useIdx-1 downTo 0) {
            if (count >= limit) break
            count++
            val cand = filtered[k]
            val instr = cand.instruction
            if (!instr.isMove()) continue
            if (instr.moveSize() != size) continue
            val (src, dst) = instr.moveOperands() ?: continue
            if (dst != slot) continue
            if (!src.startsWith("#")) continue
            var overwritten = false
            for (t in k+1 until useIdx) {
                val mid = filtered[t]
                if (mid.instruction.isMove()) {
                    val ops = mid.instruction.moveOperands()
                    if (ops != null && ops.second == slot) { overwritten = true; break }
                }
            }
            if (!overwritten) return cand.index to src
        }
        return null
    }

    val mods = mutableListOf<Modification>()
    // Scan for clamp blocks
    for (idx in filtered.indices) {
        val instr = filtered[idx].instruction
        if (!instr.startsWith("cmp.")) continue
        // need at least 8 more filtered lines for a full block
        if (idx + 8 >= filtered.size) continue
        val sizeMin = instr[4]
        if (sizeMin != 'b' && sizeMin != 'w' && sizeMin != 'l') continue
        val cmpMinParts = instr.substringAfter(' ').trim().split(',', limit = 2).map { it.trim().substringBefore(';').trim() }
        if (cmpMinParts.size != 2 || cmpMinParts[1] != "d0" || !cmpMinParts[0].isRegfileSlot()) continue
        val slotMin = cmpMinParts[0]

        val bgeInstr = filtered[idx+1].instruction
        if (!bgeInstr.startsWith("bge ") && !bgeInstr.startsWith("bhs ")) continue
        // bge target should be the next label
        val labelMax = bgeInstr.substringAfter(' ').trim().substringBefore(';').trim()

        val moveMinInstr = filtered[idx+2].instruction
        if (!moveMinInstr.startsWith("move.")) continue
        if (moveMinInstr.moveSize() != sizeMin) continue
        val moveMinOps = moveMinInstr.moveOperands() ?: continue
        if (moveMinOps.first != slotMin || moveMinOps.second != "d0") continue

        val braInstr = filtered[idx+3].instruction
        if (!braInstr.startsWith("bra ")) continue
        val labelDone = braInstr.substringAfter(' ').trim().substringBefore(';').trim()

        val labelMaxLine = filtered[idx+4].trimmed
        if (!labelMaxLine.startsWith("$labelMax:")) continue

        val cmpMaxInstr = filtered[idx+5].instruction
        if (!cmpMaxInstr.startsWith("cmp.")) continue
        if (cmpMaxInstr[4] != sizeMin) continue
        val cmpMaxParts = cmpMaxInstr.substringAfter(' ').trim().split(',', limit = 2).map { it.trim().substringBefore(';').trim() }
        if (cmpMaxParts.size != 2 || cmpMaxParts[1] != "d0" || !cmpMaxParts[0].isRegfileSlot()) continue
        val slotMax = cmpMaxParts[0]

        val bleInstr = filtered[idx+6].instruction
        if (!bleInstr.startsWith("ble ") && !bleInstr.startsWith("bls ")) continue
        val bleTarget = bleInstr.substringAfter(' ').trim().substringBefore(';').trim()
        if (bleTarget != labelDone) continue

        val moveMaxInstr = filtered[idx+7].instruction
        if (!moveMaxInstr.startsWith("move.")) continue
        if (moveMaxInstr.moveSize() != sizeMin) continue
        val moveMaxOps = moveMaxInstr.moveOperands() ?: continue
        if (moveMaxOps.first != slotMax || moveMaxOps.second != "d0") continue

        val labelDoneLine = filtered[idx+8].trimmed
        if (!labelDoneLine.startsWith("$labelDone:")) continue

        // Found a clamp block; try to fold each bound if its slot has an immediate define
        val defineMin = findDefine(slotMin, sizeMin, idx)
        if (defineMin != null) {
            val immMin = defineMin.second
            val indentCmp = filtered[idx].value.takeWhile { it.isWhitespace() }
            val indentMove = filtered[idx+2].value.takeWhile { it.isWhitespace() }
            mods.add(Modification(filtered[idx].index, false, "${indentCmp}cmpi.$sizeMin  $immMin,d0"))
            mods.add(Modification(filtered[idx+2].index, false, "${indentMove}move.$sizeMin  $immMin,d0"))
            // If the slot is not read after the clamp block, the defining move is dead
            val defineIdx = defineMin.first
            if (!isSlotReadAfter(slotMin, filtered[idx+8].index + 1, allPretrimmed)) {
                mods.add(Modification(defineIdx, true, null))
            }
        }
        val defineMax = findDefine(slotMax, sizeMin, idx+5)
        if (defineMax != null) {
            val immMax = defineMax.second
            val indentCmp2 = filtered[idx+5].value.takeWhile { it.isWhitespace() }
            val indentMove2 = filtered[idx+7].value.takeWhile { it.isWhitespace() }
            mods.add(Modification(filtered[idx+5].index, false, "${indentCmp2}cmpi.$sizeMin  $immMax,d0"))
            mods.add(Modification(filtered[idx+7].index, false, "${indentMove2}move.$sizeMin  $immMax,d0"))
            val defineIdxMax = defineMax.first
            if (!isSlotReadAfter(slotMax, filtered[idx+8].index + 1, allPretrimmed)) {
                mods.add(Modification(defineIdxMax, true, null))
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
