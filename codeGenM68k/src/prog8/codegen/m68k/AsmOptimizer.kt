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
        by2 = runPass(optimizeSwapBack(by2), 2, by2)
        by2 = runPass(optimizeStoreReloadToRegCopy(by2), 2, by2)
        val a6Mods = optimizeByteZeroExtendSafe(lines, pretrimmed)
        if (a6Mods.isNotEmpty()) {
            applyModifications(a6Mods, lines, pretrimmed)
            modified = true
        }
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

    // A5 .w form: rewrite move.w #N,dK to moveq #N,dK when safe (no longword read of dK
    // before a full write or block boundary in the same basic block).
    val moveqMods = optimizeMoveqWordForm(lines, pretrimmed)
    if (moveqMods.isNotEmpty()) {
        applyModifications(moveqMods, lines, pretrimmed)
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

/**
 * A5 .w form: rewrite `move.w #N,dK` to `moveq #N,dK` when N fits in -128..127
 * (or $FF80..$FFFF unsigned), provided no subsequent longword read of dK occurs
 * before a full write or block boundary in the same basic block.
 */
private fun optimizeMoveqWordForm(lines: MutableList<String>, allPretrimmed: List<String>): List<Modification> {
    val controlTransfers = setOf(
        "bra", "bsr", "jmp", "jsr", "rts", "rtr", "rte", "trap",
        "bcc", "bcs", "beq", "bne", "bmi", "bpl", "bvc", "bvs",
        "bhi", "bls", "bhs", "blo", "bgt", "ble", "bge", "blt",
        "dbcc", "dbcs", "dbhi", "dbhs", "dblo", "dbls", "dbne", "dbnz", "dbeq", "dbz",
        "dbmi", "dbpl", "dbge", "dblt", "dbgt", "dble", "dbvc", "dbvs", "dbtst", "dbset",
        "dbclr", "dbchg", "dbf", "dbra"
    )

    // Filter to non-blank, non-comment lines
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (i in filtered.indices) {
        val line = filtered[i].value
        // Match move.w #N,dK (strip any label first, so "label: move.w ..." is also handled)
        val instr = line.instructionPart().lowercase()
        if (!instr.startsWith("move.w")) continue
        val operands = line.instructionPart().instructionOperands() ?: continue
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2) continue
        val src = parts[0].trim()
        val dst = parts[1].trim().lowercase()
        if (!src.startsWith("#")) continue
        if (!dst.matches(dataRegisterPattern)) continue

        // Parse immediate
        val immStr = src.substring(1).trim()
        val value = try {
            when {
                immStr.startsWith("$") -> immStr.substring(1).toInt(16)
                immStr.startsWith("0x", ignoreCase = true) -> immStr.substring(2).toInt(16)
                else -> immStr.toInt()
            }
        } catch (_: NumberFormatException) { continue }
        // Normalize unsigned .w range to signed
        val signedValue = if (value > 32767) value - 65536 else value
        if (signedValue !in -128..127) continue

        // Check safety: scan forward in same basic block
        var safe = true
        for (j in i + 1 until filtered.size) {
            val nextLine = filtered[j].value
            val nextInstr = nextLine.substringBefore(';').trim()

            // Block boundary?
            if (hasLabel(nextLine)) { break }
            val mnemonic = nextInstr.substringBefore(' ').substringBefore('\t').lowercase()
            if (mnemonic in controlTransfers) { break }
            if (nextLine.trimStart().startsWith(';')) { continue }

            // Does this instruction fully write dK? (kills liveness)
            // Full writes: move.l, moveq, clr.l, and.l/or.l/eor.l/add.l/sub.l/etc with dK as dest
            val fullWritePattern = Regex("""\b(move\.l|moveq|clr\.l|and\.l|or\.l|eor\.l|add\.l|sub\.l|mulu\.l|muls\.l|divu\.l|divs\.l)\s+.*,\s*${Regex.escape(dst)}\b""")
            if (fullWritePattern.containsMatchIn(nextInstr.lowercase())) { break }
            // Also move.w/move.b/moveq with dK as dest is NOT a full write (only low bits)
            // But move.l/moveq/clr.l are full writes (handled above)

            // Check for longword read of dK
            // A .l instruction that mentions dK (as source or in a read context)
            val nextInstrLower = nextInstr.lowercase()
            if (nextInstrLower.contains(".l") && nextInstrLower.contains(dst)) {
                // But exclude if dK is only in the destination position of a full write (already handled above)
                // Check if it's a read: dK appears not just as the destination of a full write
                val parts2 = nextInstrLower.split(',', limit = 2)
                val isDestOnly = parts2.size == 2 && parts2[1].trim() == dst && fullWritePattern.containsMatchIn(nextInstrLower)
                if (!isDestOnly) {
                    safe = false
                    break
                }
            }
        }

        if (safe) {
            val originalLine = lines.getOrNull(filtered[i].index) ?: allPretrimmed[filtered[i].index]
            val originalIndent = originalLine.takeWhile { it.isWhitespace() }
            val label = keepLabel(filtered[i].value)
            val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
            mods.add(Modification(filtered[i].index, false, "$originalIndent$labelPrefix" + "moveq  #${signedValue},$dst"))
        }
    }

    return mods
}

private fun optimizeSwapBack(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  move.S  dA,dB  /  move.S  dB,dA  ->  drop the second move (swap-back, register-only, always safe;
    //  both moves set identical flags since they move the same value at the same size)
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (hasLabel(lines[1].trimmed)) continue
        if (!first.isMove() || !second.isMove()) continue
        val m1 = first.moveOperands() ?: continue
        val m2 = second.moveOperands() ?: continue
        val (src1, dst1) = m1
        val (src2, dst2) = m2
        if (!src1.lowercase().matches(dataRegisterPattern) || !dst1.lowercase().matches(dataRegisterPattern)) continue
        if (src1.lowercase() == dst1.lowercase()) continue
        if (src2.lowercase() == dst1.lowercase() && dst2.lowercase() == src1.lowercase() && first.moveSize() == second.moveSize())
            mods.add(Modification(lines[1].index, true, null))
    }
    return mods
}

private fun optimizeStoreReloadToRegCopy(linesBy: Sequence<List<TrimmedLine>>): List<Modification> {
    //  move.S  dN,M  /  move.S  M,dK  (K != N, same mem)  ->  keep store, replace reload with move.S dN,dK
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (hasLabel(lines[1].trimmed)) continue
        if (!first.isMove() || !second.isMove()) continue
        val m1 = first.moveOperands() ?: continue
        val m2 = second.moveOperands() ?: continue
        val (src1, dst1) = m1
        val (src2, dst2) = m2
        if (!src1.lowercase().matches(dataRegisterPattern)) continue
        if (dst1.lowercase().matches(dataRegisterPattern)) continue
        if (!dst2.lowercase().matches(dataRegisterPattern)) continue
        if (src2.lowercase() != dst1.lowercase()) continue
        if (dst2.lowercase() == src1.lowercase()) continue
        if (first.moveSize() != second.moveSize()) continue
        // memory safety: only plain memory operands, no indirect/indexing whose address or
        // side effects (e.g. an (aX)+ increment) would differ between read and no-read
        if (!dst1.isSafeMemoryOperand()) continue
        val sz = first.moveSize()
        val indent = lines[1].value.takeWhile { it.isWhitespace() }
        val label = keepLabel(lines[1].trimmed)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(lines[1].index, false, "$indent$labelPrefix" + "move.$sz  ${src1},${dst2}"))
    }
    return mods
}

private enum class FlagDecision { Overwritten, Continue, Unsafe }

/** Instruction mnemonics that unconditionally write the N flag (and Z/C), so the earlier state is gone. */
private val nFlagOverwriters = setOf(
    "moveq", "clr", "tst", "cmp", "cmpa", "cmpi", "cmpm",
    "add", "sub", "addi", "subi", "addq", "subq", "addx", "subx",
    "and", "andi", "or", "ori", "eor", "eori",
    "asl", "asr", "lsl", "lsr", "rol", "ror", "roxl", "roxr",
    "neg", "negx", "not", "ext", "swap", "tas",
    "mulu", "muls", "mulsl", "divu", "divs", "divsl", "chk"
)

/** Instruction mnemonics that leave the N flag untouched and do not read it: the fall-through
 *  path still sees the rewritten flags (which for Z/C are identical to before, and FPU ops only
 *  touch FPSR), so scanning may continue past them. */
private val nFlagOpaque = setOf(
    "lea", "movea", "adda", "suba", "exg", "link", "unlk", "nop", "movem",
    "btst", "bset", "bclr", "bchg", "st", "sf",
    "scc", "scs", "seq", "sne", "svc", "svs", "shi", "sls", "shs", "slo",
    "fmove", "fadd", "fsub", "fmul", "fdiv", "ftst", "fcmp",
    "fsqrt", "fabs", "fneg", "fintrz", "fgetexp", "fgetman",
    "dc", "ds", "dcb", "even", "cnop", "align", "org", "section", "xdef", "xref", "public", "global", "offset"
)

/**
 * How the A6 rewrite interacts with one instruction on the fall-through path: Continue keeps
 * scanning, Overwritten means N has been replaced so the rewrite becomes safe, Unsafe makes the
 * rewrite unsafe (an N-sensitive reader, or any control transfer whose taken path may still
 * observe our flags, or any unclassified instruction).
 */
private fun String.zeroExtendFlagDecision(): FlagDecision {
    val instr = substringBefore(';').trim().instructionPart().trim()
    if (instr.isEmpty()) return FlagDecision.Continue
    val tokens = instr.split(Regex("\\s+"), limit = 2)
    val base = tokens[0].substringBefore('.').lowercase()
    val operands = if (tokens.size > 1) tokens[1] else ""

    return when {
        base == "move" -> {
            val pair = operands.split(',', limit = 2)
            if (pair.size != 2) return FlagDecision.Unsafe
            val src = pair[0].trim().lowercase()
            val dst = pair[1].trim().lowercase()
            when {
                dst == "sr" || dst == "ccr" -> FlagDecision.Overwritten
                src == "sr" -> FlagDecision.Unsafe
                dst.matches(Regex("a[0-7]")) -> FlagDecision.Continue
                else -> FlagDecision.Overwritten
            }
        }
        base in nFlagOverwriters -> FlagDecision.Overwritten
        base in nFlagOpaque -> FlagDecision.Continue
        else -> FlagDecision.Unsafe
    }
}

private fun optimizeByteZeroExtendSafe(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    //  move.b  M,dN  /  and.l  #$ff,dN  ->  moveq  #0,dN  /  move.b  M,dN
    //  Safety: M must not use dN as an address/index register, and must be a plain operand token.
    //  Flag safety: the rewrite changes the N flag for byte values >= 128 (move.b reflects the
    //  byte's sign bit, and.l #$ff always leaves N=0). Scan forward along the fall-through path:
    //  only rewrite when a flag setter replaces N before an N-sensitive reader can see it.
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (i in filtered.indices) {
        val line = filtered[i].value
        val lower = line.lowercase()
        if (!lower.startsWith("move.b")) continue
        val operands = line.instructionOperands() ?: continue
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2) continue
        val src1 = parts[0].trim()
        val dst1 = parts[1].trim()
        if (!dst1.lowercase().matches(dataRegisterPattern)) continue
        val reg = dst1.lowercase()

        // Next line must be the masking and.l/andi.l
        if (i + 1 >= filtered.size) continue
        val nextLine = filtered[i + 1].value
        val nextLower = nextLine.lowercase()
        if (!nextLower.startsWith("and.l") && !nextLower.startsWith("andi.l")) continue
        val andOps = nextLine.instructionOperands() ?: continue
        val andParts = andOps.split(',', limit = 2)
        if (andParts.size != 2) continue
        if (andParts[1].trim().lowercase() != reg) continue
        val imm = andParts[0].trim().lowercase()
        if (imm !in setOf("#\$ff", "#\$00ff", "#\$000000ff", "#0xff", "#0x00ff", "#0x000000ff")) continue

        // M must be a clean operand token that does not reference dN
        if (src1.any { it.isWhitespace() }) continue
        if (src1.contains(reg, ignoreCase = true)) continue

        // Flag safety: scan the fall-through path after the rewritten pair.
        var flagSafe = true
        scan@ for (j in i + 2 until filtered.size) {
            when (filtered[j].value.zeroExtendFlagDecision()) {
                FlagDecision.Overwritten -> break@scan
                FlagDecision.Unsafe -> {
                    flagSafe = false
                    break@scan
                }
                FlagDecision.Continue -> {}
            }
        }
        if (!flagSafe) continue

        val originalLine0 = lines.getOrNull(filtered[i].index) ?: allPretrimmed[filtered[i].index]
        val originalIndent0 = originalLine0.takeWhile { it.isWhitespace() }
        val originalLine1 = lines.getOrNull(filtered[i + 1].index) ?: allPretrimmed[filtered[i + 1].index]
        val originalIndent1 = originalLine1.takeWhile { it.isWhitespace() }

        mods.add(Modification(filtered[i].index, false, originalIndent0 + "moveq  #0,$reg"))
        mods.add(Modification(filtered[i + 1].index, false, originalIndent1 + "move.b  $src1,$reg"))
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

/** Strips a leading label (e.g. "mylabel:  move.l ..." -> "move.l ..."); inline comments never participate. */
private fun String.instructionPart(): String {
    val idx = substringBefore(';').indexOf(':')
    return if (idx >= 0) substring(idx + 1).trimStart() else this
}

private fun hasLabel(line: String): Boolean =
    line.substringBefore(';').trimStart().let { it.length > 1 && ':' in it }

/** Returns just the "label:" part of a line, or "" if there is no label. */
private fun keepLabel(line: String): String {
    val idx = line.substringBefore(';').indexOf(':')
    return if (idx >= 0) line.substring(0, idx + 1) else ""
}

/** Operand part of an instruction line (separated by spaces or tabs, comments stripped), or null. */
private fun String.instructionOperands(): String? {
    val trimmed = this.trimStart()
    val cut = trimmed.indexOfFirst { it.isWhitespace() }
    if (cut < 0) return null
    val ops = trimmed.substring(cut + 1).trim().substringBefore(';').trim()
    return ops.ifEmpty { null }
}

// Deliberately only recognizes move.b/w/l, not movea or movem: movea always writes to an address
// register and movem uses register-list operands, both rejected by isSafeMemoryOperand, so they
// never participate in the patterns we optimize.
private fun String.isMove(): Boolean =
    startsWith("move.b") || startsWith("move.w") || startsWith("move.l")

/** Size suffix of a move instruction: 'b', 'w' or 'l'. */
private fun String.moveSize(): Char = substringAfter('.').firstOrNull() ?: ' '

/** Matches exactly a data register d0-d7, in either case. */
private val dataRegisterPattern = Regex("^d[0-7]$")

/** Splits a move instruction "move.S  src, dst" into (src, dst), or null if it isn't a move. */
private fun String.moveOperands(): Pair<String, String>? {
    val ops = instructionOperands() ?: return null
    val parts = ops.split(',', limit = 2)
    if (parts.size != 2) return null
    return parts[0].trim() to parts[1].trim()
}

private fun String.isRegister(): Boolean =
    this.matches(Regex("^(d[0-7]|a[0-7]|fp[0-9]+|sp|pc)$"))

/**
 * A plain memory operand that can be safely read twice and assumed to hit the same location
 * (e.g. "p8_regfile+268", "sys.DOSBase", "symbol"); excludes registers, indirect or indexed
 * addressing, and absolute hardware addresses such as "$dff180" whose read may have side effects.
 */
private fun String.isSafeMemoryOperand(): Boolean {
    if (isEmpty()) return false
    if (isRegister()) return false
    if ('(' in this || ')' in this) return false
    // Reject absolute addresses: named symbols are safe, but raw numeric/hex addresses may be
    // hardware registers with read side effects (e.g., Amiga custom-chip registers).
    if (startsWith("$")) return false
    if (all { it.isDigit() }) return false
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

