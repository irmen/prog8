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
            currentLines: List<List<TrimmedLine>>
        ): List<List<TrimmedLine>> {
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
        by2 = runPass(optimizeRedundantMovePair(by2), 2, by2)
        by2 = runPass(optimizeStoreReloadToRegCopy(by2), 2, by2)
        by2 = runPass(optimizeRedundantExt(by2), 2, by2)
        // single-line immediate-form rewrites: provably safe, no context needed.
        // plus single-line true no-ops (movea/exg with identical operands)
        // and the lsl #1 -> add dN,dN strength reduction.
        val quickMods = optimizeMoveqLongForm(lines, pretrimmed) + optimizeAddSubQuick(lines, pretrimmed) +
            optimizeMoveSelfNoOps(pretrimmed) + optimizeLslOneToDouble(lines, pretrimmed) +
            optimizeCmpZeroToTst(lines, pretrimmed)
        if (quickMods.isNotEmpty()) {
            applyModifications(quickMods, lines, pretrimmed)
            modified = true
        }
        var by3 = getLinesBy(pretrimmed, lines, 3)
        by3 = runPass(optimizeConditionalOverJump(by3), 3, by3)
        // drop a `lea base,a0` when a0 already holds that same base.
        // a0 is a real CPU register, so this pass is independent of the
        // p8_regfile layout and of the register allocator. Runs inside the
        // loop: branch removals above can join straight-line runs and reveal
        // more, and lea removals can in turn enable further branch removals.
        val leaMods = optimizeRedundantLea(pretrimmed)
        if (leaMods.isNotEmpty()) {
            applyModifications(leaMods, lines, pretrimmed)
            modified = true
        }
        // fold move+address-arithmetic into lea; creates lea lines the pass above may trim next round.
        val addrMods = optimizeAddressArithmetic(lines, pretrimmed)
        if (addrMods.isNotEmpty()) {
            applyModifications(addrMods, lines, pretrimmed)
            modified = true
        }

        // DBRA peephole for repeat loops is now obsolete: counted loops are emitted as IRLoopChunk and directly translated to dbra.
        // The old REGFILE-DEPENDENT pattern (move #N,p8_regfile+slot / label: body / subq #1,slot / bne label) no longer occurs.

        // fold move-#N plus operate into the immediate form; creates addi/andi/ori
        // lines other passes (tst removal, moveq forms) consume next round.
        val constMods = optimizeConstOperateFold(lines, pretrimmed)
        if (constMods.isNotEmpty()) {
            applyModifications(constMods, lines, pretrimmed)
            modified = true
        }

        if (!modified)
            break
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

/** Parse an m68k `#` immediate (decimal or `$`/`0x` hex) to a signed Long, or null if not numeric. */
private fun parseImmediate(imm: String): Long? {
    val text = imm.trim()
    if (!text.startsWith("#"))
        return null
    val body = text.substring(1).trim()
    return try {
        when {
            body.startsWith("$") -> body.substring(1).toLong(16)
            body.startsWith("0x", ignoreCase = true) -> body.substring(2).toLong(16)
            else -> body.toLong()
        }
    } catch (_: NumberFormatException) {
        null
    }
}

/**
 * Rewrite `move.l #N,dK` to `moveq #N,dK` when N fits in -128..127 (normalizing
 * unsigned 32-bit forms like $FFFFFF80 to their signed value). Always safe with no
 * forward scan: both are full 32-bit writes with identical flags. Symbol immediates
 * (`#label`) fail parsing and are skipped.
 */
private fun optimizeMoveqLongForm(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (entry in filtered) {
        val instr = entry.value.instructionPart().lowercase()
        if (!instr.startsWith("move.l"))
            continue
        val operands = entry.value.instructionOperands() ?: continue
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2)
            continue
        val dst = parts[1].trim().lowercase()
        if (!dst.isDataReg())
            continue
        val value = parseImmediate(parts[0]) ?: continue
        val signed = if (value > 0x7fffffffL) value - 0x100000000L else value
        if (signed !in -128..127)
            continue
        val originalLine = lines.getOrNull(entry.index) ?: allPretrimmed[entry.index]
        val originalIndent = originalLine.takeWhile { it.isWhitespace() }
        val label = keepLabel(entry.value)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(entry.index, false, "$originalIndent$labelPrefix" + "moveq  #$signed,$dst"))
    }

    return mods
}

/**
 * Rewrite `add.S #N,dst` / `sub.S #N,dst` to `addq`/`subq` for N in 1..8, flipping
 * the operation for N in -8..-1 (`add #-3` -> `subq #3`). Identical flags, shorter
 * encoding. Skips ccr/sr destinations (quick forms cannot target them) and
 * non-numeric immediates. Validity of everything else is preserved: the rewrite keeps
 * the size, and addq/subq accept every destination the plain forms accept.
 */
private fun optimizeAddSubQuick(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (entry in filtered) {
        val instr = entry.value.instructionPart()
        val token = instr.substringBefore(' ').substringBefore('\t').lowercase()
        if (token !in setOf("add.b", "add.w", "add.l", "sub.b", "sub.w", "sub.l"))
            continue
        val size = token.substringAfter('.')
        val operands = entry.value.instructionOperands() ?: continue
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2)
            continue
        val dst = parts[1].trim()
        if (dst.lowercase() in setOf("ccr", "sr"))
            continue
        val value = parseImmediate(parts[0]) ?: continue
        val replacement = when {
            value in 1..8 -> "${token.substringBefore('.')}q.$size  #$value,$dst"
            value in -8..-1 -> {
                val flipped = if (token.startsWith("add")) "subq" else "addq"
                "$flipped.$size  #${-value},$dst"
            }
            else -> continue
        }
        val originalLine = lines.getOrNull(entry.index) ?: allPretrimmed[entry.index]
        val originalIndent = originalLine.takeWhile { it.isWhitespace() }
        val label = keepLabel(entry.value)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(entry.index, false, "$originalIndent$labelPrefix$replacement"))
    }

    return mods
}

/** Foldable lea base: only registers and `#` immediates, since those read no memory. */
private sealed interface LeaBase {
    /** address register: no memory read at all. */
    data class Register(val reg: String) : LeaBase
    /** numeric address: no memory read at all. */
    data class Absolute(val value: Long) : LeaBase
    /** immediate `#symbol[+off]`: an address constant, no memory read. */
    data class Symbol(val name: String, val off: Long) : LeaBase
}

private val addressArithOpPattern = Regex("^(addq|subq|adda|suba|add|sub)\\.([wl])$")
private val symbolBasePattern = Regex("^([^$():,\\s]+?)([+-]\\d+)?$")

/**
 * Displacement of an `(off,a5)` frame-slot operand in any emitted form
 * (`8(a5)`, `-2(a5)`, `(-8,a5)`, `(a5)`), or null. Frame slots are RAM by
 * construction, so access has no side effects.
 */
private fun parseFrameSlot(text: String): Long? {
    val t = text.trim()
    val lower = t.lowercase()
    val inner = when {
        lower.endsWith("(a5)") -> t.substring(0, t.length - 4)
        lower.endsWith(",a5)") && t.startsWith("(") -> t.substring(1, t.length - 4)
        else -> return null
    }
    if (inner.isEmpty())
        return 0L
    return inner.toLongOrNull()
}

/**
 * Split a `movea.l`/`move.l` source into a foldable lea base, or null when folding
 * is unsafe or impossible. Only registers and `#` immediates qualify: a memory
 * source loads a value, whereas `lea` would compute the address of that operand.
 * `$hex` sources are rejected (may be hardware registers whose read must not be
 * dropped); anything unparseable is rejected too.
 */
private fun parseLeaBase(src: String): LeaBase? {
    val text = src.trim()
    if (text.startsWith("#")) {
        val body = text.substring(1).trim()
        val num = try {
            when {
                body.startsWith("$") -> body.substring(1).toLong(16)
                body.startsWith("0x", ignoreCase = true) -> body.substring(2).toLong(16)
                else -> body.toLong()
            }
        } catch (_: NumberFormatException) {
            null
        }
        if (num != null)
            return LeaBase.Absolute(num)
        // `#symbol[+off]`: no memory read, fold symbolically.
        if (body.lowercase() in setOf("d0","d1","d2","d3","d4","d5","d6","d7","a0","a1","a2","a3","a4","a5","a6","a7","sp","pc","sr","ccr","usp"))
            return null
        val m = symbolBasePattern.matchEntire(body) ?: return null
        return LeaBase.Symbol(m.groupValues[1], m.groupValues[2].ifEmpty { "0" }.toLong())
    }
    if (text.isAddressReg())
        return LeaBase.Register(text.lowercase())
    // Anything else reads memory (frame slot, symbol, indexed operand), so its
    // value cannot be reproduced by a `lea`.
    return null
}

/**
 * Fold `movea.l`/`move.l X,aY` + address-size `add`/`sub #N,aY` into a single
 * `lea (N+X),aY`. Only address-register destinations (all such ops are flag-free
 * there); data-register destinations are never folded. `movea` sources fold freely
 * (`movea` sets no flags); `move.l` sources additionally need the flag scan, since
 * the move sets N/Z that the folded `lea` would not.
 */
private fun optimizeAddressArithmetic(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (i in 0 until filtered.size - 1) {
        val first = filtered[i].value
        val second = filtered[i + 1].value
        if (hasLabel(second))
            continue
        val moveToken = first.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        if (moveToken != "movea.l" && moveToken != "move.l")
            continue
        val moveOps = first.instructionOperands()?.let(::splitOperands) ?: continue
        if (moveOps.size != 2)
            continue
        val dst = moveOps[1].trim().lowercase()
        if (!dst.isAddressReg())
            continue
        val arithToken = second.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        val arith = addressArithOpPattern.matchEntire(arithToken) ?: continue
        val arithOps = second.instructionOperands()?.let(::splitOperands) ?: continue
        if (arithOps.size != 2 || arithOps[1].trim().lowercase() != dst)
            continue
        val imm = parseImmediate(arithOps[0]) ?: continue
        val delta = if (arith.groupValues[1].startsWith("add")) imm else -imm
        val base = parseLeaBase(moveOps[0]) ?: continue
        // `move.l` sets N/Z; only fold when no later reader can observe them.
        if (moveToken == "move.l") {
            var flagSafe = false
            scan@ for (j in i + 2 until filtered.size) {
                val text = filtered[j].value
                if (hasLabel(text))
                    break@scan
                when (text.zeroExtendFlagDecision()) {
                    FlagDecision.Overwritten -> {
                        flagSafe = true
                        break@scan
                    }
                    FlagDecision.Unsafe -> break@scan
                    FlagDecision.Continue -> {}
                }
            }
            if (!flagSafe)
                continue
        }
        val folded = when (base) {
            is LeaBase.Register -> {
                val total = delta
                if (total !in -32768..32767)
                    continue
                "($total,${base.reg})"
            }
            is LeaBase.Absolute -> {
                val total = base.value + delta
                if (total !in 0..0xffffffffL)
                    continue
                "$" + total.toString(16)
            }
            is LeaBase.Symbol -> {
                val total = base.off + delta
                base.name + when {
                    total > 0 -> "+$total"
                    total < 0 -> "$total"
                    else -> ""
                }
            }
        }
        val originalLine = lines.getOrNull(filtered[i].index) ?: allPretrimmed[filtered[i].index]
        val originalIndent = originalLine.takeWhile { it.isWhitespace() }
        val label = keepLabel(filtered[i].value)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(filtered[i].index, false, "$originalIndent$labelPrefix" + "lea  $folded,$dst"))
        mods.add(Modification(filtered[i + 1].index, true, null))
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
        if (!dst.isDataReg()) continue

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

/** Immediate-form counterpart of a two-operand op, or null when folding would be illegal or flag-unsound. */
private fun constFoldMnemonic(op: String, size: Char, dst: String): String? {
    val dstLower = dst.lowercase()
    if (dstLower == "sr" || dstLower == "ccr")
        return null
    if (dstLower.isAddressReg() && op != "move")
        return null
    return when (op) {
        "add" -> "addi.$size"
        "sub" -> "subi.$size"
        "and" -> "andi.$size"
        "or" -> "ori.$size"
        "eor" -> "eori.$size"
        "cmp" -> "cmpi.$size"
        "move" -> "move.$size"
        else -> null
    }
}

/**
 * Fold `move.S #N,dX` (or `moveq #N,dX`) + `op.S dX,dY` into `opi.S #N,dY`.
 * Sound: the pair's end state (dY, flags) is a pure function of (dY, N), which
 * the replacement recomputes identically; the dropped `move` only survives in
 * dX, so dX must be dead after the pair (forward scan below). `btst`-style ops
 * are excluded (they preserve incoming flags, so dropping the move would leak
 * its flag state); `addi`-family destinations exclude address registers (the
 * immediate form cannot target `An`, unlike the register form).
 */
private fun optimizeConstOperateFold(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val controlTransfers = setOf(
        "bra", "bsr", "jmp", "jsr", "rts", "rtr", "rte", "trap",
        "bcc", "bcs", "beq", "bne", "bmi", "bpl", "bvc", "bvs",
        "bhi", "bls", "bhs", "blo", "bgt", "ble", "bge", "blt",
        "dbcc", "dbcs", "dbhi", "dbhs", "dblo", "dbls", "dbne", "dbnz", "dbeq", "dbz",
        "dbmi", "dbpl", "dbge", "dblt", "dbgt", "dble", "dbvc", "dbvs", "dbtst", "dbset",
        "dbclr", "dbchg", "dbf", "dbra"
    )
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (i in 0 until filtered.size - 1) {
        val first = filtered[i].value
        val second = filtered[i + 1].value
        if (hasLabel(second))
            continue
        val firstLower = first.instructionPart().trim().lowercase()
        val secondLower = second.instructionPart().trim().lowercase()
        val firstMnemonic = firstLower.substringBefore(' ').substringBefore('\t')
        val opBase = secondLower.substringBefore(' ').substringBefore('\t').substringBefore('.')
        if (opBase !in setOf("add", "sub", "and", "or", "eor", "cmp", "move"))
            continue
        val secondSize = secondLower.substringBefore(' ').substringBefore('\t').substringAfter('.', "")
        if (secondSize.length != 1 || secondSize[0] !in "bwl")
            continue
        val m1 = first.instructionOperands()?.let(::splitOperands) ?: continue
        val m2 = second.instructionOperands()?.let(::splitOperands) ?: continue
        if (m1.size != 2 || m2.size != 2)
            continue
        val imm = m1[0].trim()
        val reg = m1[1].trim()
        if (!imm.startsWith("#") || !reg.isDataReg())
            continue
        if (firstMnemonic.startsWith("moveq")) {
            // full-long write: any second size reads the same low part.
        } else if (firstMnemonic == "move.$secondSize") {
            // same-size partial write: low part matches.
        } else {
            continue
        }
        if (!sameOperand(m2[0].trim(), reg))
            continue
        val dst = m2[1].trim()
        if (dst.startsWith("#"))
            continue
        val foldedMnemonic = constFoldMnemonic(opBase, secondSize[0], dst) ?: continue
        // dX must be dead after the pair: scan forward; safe only when the first
        // subsequent mention is an overwrite covering the written bytes. Labels and
        // control transfers end the scan as unsafe (another path may read dX).
        val writtenLong = firstMnemonic.startsWith("moveq") || firstMnemonic == "move.l"
        var dead = false
        scan@ for (j in i + 2 until filtered.size) {
            val text = filtered[j].value
            if (hasLabel(text))
                break@scan
            val low = text.instructionPart().trim().lowercase()
            val mn = low.substringBefore(' ').substringBefore('\t')
            if (mn in controlTransfers)
                break@scan
            if (!Regex("""\b${Regex.escape(reg.lowercase())}\b""").containsMatchIn(low))
                continue
            val ops = text.instructionOperands()?.let(::splitOperands) ?: break@scan
            val kill = when {
                mn.startsWith("moveq") && ops.size == 2 && sameOperand(ops[1].trim(), reg) -> true
                (mn == "move.l" || (!writtenLong && mn == "move.$secondSize")) && ops.size == 2 && sameOperand(ops[1].trim(), reg) -> true
                mn == "clr.$secondSize" && ops.size == 1 && sameOperand(ops[0].trim(), reg) -> true
                else -> false
            }
            if (kill) {
                dead = true
                break@scan
            }
            break@scan
        }
        if (!dead)
            continue
        val originalLine = lines.getOrNull(filtered[i + 1].index) ?: allPretrimmed[filtered[i + 1].index]
        val originalIndent = originalLine.takeWhile { it.isWhitespace() }
        mods.add(Modification(filtered[i].index, true, null))
        mods.add(Modification(filtered[i + 1].index, false, "$originalIndent$foldedMnemonic  $imm,$dst"))
    }

    return mods
}

/** A move operand with no read/write side effects: plain register, safe memory, or immediate. */
private fun String.isSwizzleSafeOperand(): Boolean {
    val t = trim()
    if (t.isEmpty()) return false
    val l = t.lowercase()
    if (l in setOf("ccr", "sr", "usp", "sp", "pc")) return false
    if (l.matches(Regex("^(d[0-7]|a[0-7]|fp[0-9]+)$"))) return true
    return t.isSafeMemoryOperand()
}

/** Same operand: exact match, or case-insensitive for registers (symbols stay case-sensitive). */
private fun sameOperand(a: String, b: String): Boolean {
    if (a == b) return true
    val al = a.lowercase()
    return al == b.lowercase() && al.matches(Regex("^(d[0-7]|a[0-7]|fp[0-9]+)$"))
}

private fun optimizeRedundantMovePair(linesBy: List<List<TrimmedLine>>): List<Modification> {
    //  Drops the second of two consecutive moves when it provably does nothing:
    //  - duplicate `move.S X,Y` twice: same value, identical flags.
    //  - mirrored `move.S X,Y` / `move.S Y,X` (both plain `move.`, or both
    //    `movea.`, same size): after the first both hold the same value, so the
    //    second writes back what is already there with identical flags.
    //    Generalizes swap-back beyond data registers: real output contains
    //    `move.l d0,a2` / `move.l a2,d0` with no use of a2 between.
    //  Both operands must be side-effect-free (rules out `(aN)+`/`-(aN)` whose
    //  read/write advances a pointer, and `$hex` MMIO), and `#` immediates are
    //  never valid destinations. Mixed move./movea. mirrors are excluded: movea
    //  sets no flags, so the second line's flag state would differ. Flag-setting
    //  self-moves (`move.S X,X`) are excluded too: value no-op but flag-setter,
    //  needs a liveness proof this pass does not attempt.
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        // Mnemonic checks on a lowered copy; operands keep their original case
        // (symbols are case-sensitive, unlike registers).
        val firstLower = lines[0].instruction.lowercase()
        val secondLower = lines[1].instruction.lowercase()
        if (hasLabel(lines[1].trimmed)) continue
        val bothMove = firstLower.isMove() && secondLower.isMove()
        val bothMovea = firstLower.startsWith("movea.") && secondLower.startsWith("movea.")
        if (!bothMove && !bothMovea) continue
        if (firstLower.moveSize() != secondLower.moveSize()) continue
        val m1 = lines[0].instruction.instructionOperands()?.let(::splitOperands) ?: continue
        val m2 = lines[1].instruction.instructionOperands()?.let(::splitOperands) ?: continue
        if (m1.size != 2 || m2.size != 2) continue
        val src1 = m1[0].trim()
        val dst1 = m1[1].trim()
        val src2 = m2[0].trim()
        val dst2 = m2[1].trim()
        if (dst1.startsWith("#") || dst2.startsWith("#")) continue
        if (!src1.isSwizzleSafeOperand() || !dst1.isSwizzleSafeOperand()) continue
        if (!src2.isSwizzleSafeOperand() || !dst2.isSwizzleSafeOperand()) continue
        if (sameOperand(src1, dst1)) continue
        val dup = sameOperand(src1, src2) && sameOperand(dst1, dst2)
        val mirror = sameOperand(src1, dst2) && sameOperand(dst1, src2)
        if (!dup && !mirror) continue
        mods.add(Modification(lines[1].index, true, null))
    }
    return mods
}

/** Single-line true no-ops: `movea.S aN,aN` (flag-free) and `exg r,r` (no flags). */
private fun optimizeMoveSelfNoOps(allPretrimmed: List<String>): List<Modification> {
    val mods = mutableListOf<Modification>()
    for ((index, line) in allPretrimmed.withIndex()) {
        if (line.isBlank() || line.trimStart().startsWith(';')) continue
        val instr = line.instructionPart().trim().lowercase()
        if (instr.startsWith("movea.")) {
            val ops = instr.instructionOperands()?.let(::splitOperands) ?: continue
            if (ops.size != 2) continue
            val src = ops[0].trim()
            val dst = ops[1].trim()
            if (src.matches(Regex("^a[0-7]$")) && src == dst)
                mods.add(Modification(index, true, null))
        } else if (instr.startsWith("exg")) {
            val ops = instr.instructionOperands()?.let(::splitOperands) ?: continue
            if (ops.size != 2) continue
            if (sameOperand(ops[0].trim(), ops[1].trim()))
                mods.add(Modification(index, true, null))
        }
    }
    return mods
}

private fun optimizeSwapBack(linesBy: List<List<TrimmedLine>>): List<Modification> {
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
        if (!src1.isDataReg() || !dst1.isDataReg()) continue
        if (src1.lowercase() == dst1.lowercase()) continue
        if (src2.lowercase() == dst1.lowercase() && dst2.lowercase() == src1.lowercase() && first.moveSize() == second.moveSize())
            mods.add(Modification(lines[1].index, true, null))
    }
    return mods
}

/** `(off,a5)` frame slot: RAM by construction, so the access has no read/write
 *  side effects and hits the same slot while a5 is untouched (consecutive lines). */
private fun String.isFrameSlotOperand(): Boolean = parseFrameSlot(this) != null

private fun optimizeStoreReloadToRegCopy(linesBy: List<List<TrimmedLine>>): List<Modification> {
    //  move.S  dN,M  /  move.S  M,dK  (K != N, same mem)  ->  keep store, replace reload with move.S dN,dK
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (hasLabel(lines[1].trimmed)) continue
        if (!first.isMove() || !second.isMove()) continue
        // paren-aware split: frame-slot operands embed a comma (`(-8,a5),d1`).
        val m1 = first.instructionOperands()?.let(::splitOperands) ?: continue
        val m2 = second.instructionOperands()?.let(::splitOperands) ?: continue
        if (m1.size != 2 || m2.size != 2) continue
        val (src1, dst1) = m1[0].trim() to m1[1].trim()
        val (src2, dst2) = m2[0].trim() to m2[1].trim()
        if (!src1.isDataReg()) continue
        if (dst1.isDataReg()) continue
        if (!dst2.isDataReg()) continue
        if (src2.lowercase() != dst1.lowercase()) continue
        if (dst2.lowercase() == src1.lowercase()) continue
        if (first.moveSize() != second.moveSize()) continue
        // memory safety: plain memory operands or (off,a5) frame slots; anything
        // else parenthesised (e.g. an (aX)+ increment) stays excluded since its
        // address or side effects would differ between read and no-read.
        if (!dst1.isSafeMemoryOperand() && !dst1.isFrameSlotOperand()) continue
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


/**
 * Rewrite `cmp.<size> #0,dN` and `cmpi.<size> #0,dN` to `tst.<size> dN`.
 * For a comparison against zero the flags are identical (N/Z reflect the
 * register, V and C are cleared), and `tst` is smaller than `cmpi`.
 */
private fun optimizeCmpZeroToTst(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val mods = mutableListOf<Modification>()
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    for (entry in filtered) {
        val line = entry.value
        val token = line.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        val base = token.substringBefore('.')
        if (base !in setOf("cmp", "cmpi"))
            continue
        val size = token.substringAfter('.', "")
        if (size !in setOf("b", "w", "l"))
            continue
        val ops = line.instructionOperands() ?: continue
        val parts = splitOperands(ops)
        if (parts.size != 2)
            continue
        if (!isImmediateZero(parts[0].trim()))
            continue
        val dst = parts[1].trim().lowercase()
        if (!dst.isDataReg())
            continue
        val originalLine = lines.getOrNull(entry.index) ?: allPretrimmed[entry.index]
        val indent = originalLine.takeWhile { it.isWhitespace() }
        val label = keepLabel(originalLine)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(entry.index, false, "${indent}${labelPrefix}tst.$size  $dst"))
    }
    return mods
}

private fun isImmediateZero(s: String): Boolean {
    val value = s.trim().lowercase()
    if (!value.startsWith('#'))
        return false
    val num = value.substring(1).trim()
    return num == "0" || num == "$0" || num == "0x0" || num == "00"
}

/** Matches a parenthesised group, for stripping addressing modes. */
private val parenthesised = Regex("""\([^)]*\)""")

/** A single edit to apply: either remove a line (keeping its label if present) or replace it. */
private class Modification(val lineIndex: Int, val remove: Boolean, val replacement: String?)

/** Pre-trimmed assembly line that also knows its original text and source line index. */
private class TrimmedLine(val value: String, val trimmed: String, val index: Int) {
    val instruction: String = trimmed.instructionPart()
}

/**
 * The instruction windows of the given size, one list per window.
 *
 * Materialized once per caller: the same sequence is consumed by every pass in a round, so a lazy
 * sequence would re-parse every line once per pass.
 */
private fun getLinesBy(pretrimmed: MutableList<String>, originalLines: MutableList<String>, windowSize: Int): List<List<TrimmedLine>> =
    pretrimmed.withIndex()
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

/** A data register d0-d7 (registers are case-insensitive, unlike symbols). */
private fun String.isDataReg(): Boolean =
    length == 2 && (this[0] == 'd' || this[0] == 'D') && this[1] in '0'..'7'

/** An address register a0-a7 (registers are case-insensitive, unlike symbols). */
private fun String.isAddressReg(): Boolean =
    length == 2 && (this[0] == 'a' || this[0] == 'A') && this[1] in '0'..'7'

/** Split an operand pair on commas at parenthesis depth 0 (m68k addressing modes embed commas, e.g. `(-8,a5),a2`). */
private fun splitOperands(operands: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in operands.indices) {
        when (operands[i]) {
            '(', '[' -> depth++
            ')', ']' -> if (depth > 0) depth--
            ',' -> if (depth == 0) {
                parts.add(operands.substring(start, i))
                start = i + 1
            }
        }
    }
    parts.add(operands.substring(start))
    return parts
}

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

private fun optimizeJmpToNextLabel(linesBy: List<List<TrimmedLine>>): List<Modification> {
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

private fun optimizeConditionalOverJump(linesBy: List<List<TrimmedLine>>): List<Modification> {
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

private fun optimizeTailCall(linesBy: List<List<TrimmedLine>>): List<Modification> {
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

private fun optimizeRedundantTst(linesBy: List<List<TrimmedLine>>): List<Modification> {
    //  An instruction below that sets N/Z exactly like tst (clearing V/C, preserving X),
    //  followed by  tst.S  DST on the same operand at the same size  ->  remove the tst.
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
        if (first.instrSize() != second.instrSize())
            continue
        val tstRaw = second.substringAfter('.').substringAfter(' ').trim()
        val tstOperand = if (';' in tstRaw) tstRaw.substringBefore(';').trim() else tstRaw
        val same = dst0 == tstOperand || (dst0.isDataReg() && dst0.equals(tstOperand, ignoreCase = true))
        if (same && (dst0.isSafeMemoryOperand() || dst0.isDataReg())) {
            mods.add(Modification(lines[1].index, true, null))
        }
    }
    return mods
}

/**
 * If this instruction sets N/Z exactly like a tst would (result in N/Z, V and C
 * cleared, X preserved) and writes to a single destination, return that destination.
 * Covers move, moveq, and, or, eor (all forms), clr, not, ext, extb, swap and tst
 * itself. Deliberately NOT covered: add/sub/cmp and friends set V/C/X from the
 * computation, so a following tst is only redundant when no V/C/X reader follows -
 * that needs liveness proof this pass does not attempt (and no such case occurs in
 * real output); movea/lea/adda set no flags at all.
 */
private fun String.flagSettingDest(): String? {
    val stripped = if (';' in this) substring(0, indexOf(';')).trim() else this
    val cut = stripped.indexOfFirst { it.isWhitespace() }
    if (cut < 0)
        return null
    val base = stripped.substring(0, cut).lowercase().substringBefore('.')
    val operands = stripped.substring(cut).trim()
    return when (base) {
        "move", "moveq", "and", "or", "eor", "andi", "ori", "eori" -> {
            val parts = splitOperands(operands)
            if (parts.size != 2)
                return null
            val dst = parts[1].trim()
            if (dst.lowercase() in setOf("ccr", "sr"))
                return null
            if (base == "moveq" && !dst.isDataReg())
                return null
            dst
        }
        "clr", "not", "ext", "extb", "swap", "tst" -> {
            val parts = splitOperands(operands)
            if (parts.size != 1)
                return null
            parts[0].trim()
        }
        else -> null
    }
}

/** Size letter of an instruction, mapping the sizeless flag-setting mnemonics to their width. */
private fun String.instrSize(): Char {
    val token = substringBefore(' ').substringBefore('\t').lowercase()
    val dot = token.indexOf('.')
    if (dot >= 0)
        return token.substring(dot + 1).firstOrNull() ?: ' '
    return when (token) {
        "swap", "moveq" -> 'l'
        else -> ' '
    }
}

/**
 * Rewrite `lsl.<size> #1,dN` to `add.<size> dN,dN` for data registers.
 * For a one-bit shift, N/Z/C/X are identical; V may differ (LSL clears V,
 * ADD sets it on overflow), but compiler-generated code never reads V after
 * a shift. Cheaper and smaller on 68000. Only immediate #1.
 */
private fun optimizeLslOneToDouble(lines: List<String>, allPretrimmed: List<String>): List<Modification> {
    val filtered = allPretrimmed.withIndex()
        .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }
        .map { IndexedValue(it.index, it.value.trimStart()) }
        .toList()

    val mods = mutableListOf<Modification>()

    for (entry in filtered) {
        val instrToken = entry.value.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        if (!instrToken.startsWith("lsl."))
            continue
        val size = instrToken.substringAfter("lsl.")
        if (size !in setOf("b", "w", "l"))
            continue
        val operands = entry.value.instructionOperands() ?: continue
        val parts = operands.split(',', limit = 2)
        if (parts.size != 2)
            continue
        if (parts[0].trim() != "#1")
            continue
        val dst = parts[1].trim()
        if (!dst.isDataReg())
            continue
        val originalLine = lines.getOrNull(entry.index) ?: allPretrimmed[entry.index]
        val originalIndent = originalLine.takeWhile { it.isWhitespace() }
        val label = keepLabel(entry.value)
        val labelPrefix = if (label.isNotEmpty()) "$label  " else ""
        mods.add(Modification(entry.index, false, "$originalIndent$labelPrefix" + "add.$size  $dst,$dst"))
    }
    return mods
}

/**
 * Remove the second of two identical consecutive `ext.w` or `ext.l`
 * instructions into the same data register (e.g. `ext.w d0 ; ext.w d0`).
 * Both instructions set identical N/Z/V/C flags and leave X untouched,
 * so the second one has no effect.
 *
 * `move.w X,dN` + `ext.l dN` and `ext.w dN` + `ext.l dN` are intentionally
 * not folded: existing tests/codegen rely on `ext.l` being emitted explicitly.
 */
private fun optimizeRedundantExt(linesBy: List<List<TrimmedLine>>): List<Modification> {
    val mods = mutableListOf<Modification>()
    for (lines in linesBy) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        if (hasLabel(lines[1].trimmed)) continue
        val firstToken = first.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        val secondToken = second.instructionPart().substringBefore(' ').substringBefore('\t').lowercase()
        val secondOps = second.instructionOperands() ?: continue
        val secondParts = secondOps.split(',', limit = 2)
        if (secondParts.size != 1) continue
        val secondDst = secondParts[0].trim().lowercase()
        if (!secondDst.isDataReg()) continue

        // duplicate extension
        if ((firstToken == "ext.w" || firstToken == "ext.l") && firstToken == secondToken) {
            val firstOps = first.instructionOperands() ?: continue
            if (firstOps.trim().lowercase() == secondDst) {
                mods.add(Modification(lines[1].index, true, null))
                continue
            }
        }
    }
    return mods
}
