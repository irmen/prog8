package prog8.codegen.cpu6502

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.StConstant
import prog8.code.StMemVar
import prog8.code.SymbolTable
import prog8.code.core.ICompilationTarget

// ============================================================================
// KNOWN BUGS / TODOs
// ============================================================================
//
// 1. Float copy optimization (optimizeSameAssignments, ~line 243):
//    The "identical float init" pattern removes 4 lines (lda/ldy/sta/sty)
//    based only on matching *load* operands. It never verifies that the two
//    sta/sty store to the *same* destination. If two different float variables
//    are initialized from the same source, the second initialization gets
//    silently deleted.
//    NOTE: Cannot be reproduced from Prog8 source - the codegen doesn't
//    produce the exact 14-line pattern that triggers this.
//
// 2. Crude "y" substring check for Y register modification (~line 387-410):
//    optimizeSamePointerIndexing checks "y" !in third to detect if Y is
//    modified between ldy pairs. This produces false positives (variable
//    "myvar" contains 'y') and false negatives (tay, phy, ply would be
//    missed). Should parse actual instruction mnemonics instead.
//
// 3. Inconsistent line property usage (~line 400):
//    The pointer indexing patterns mix lines[2].trimmed with
//    lines[3].instruction (e.g., checks "y" !in f4 where f4 is .instruction,
//    while third is .trimmed). Should consistently use .instruction for all.
//
// ============================================================================


// note: see https://wiki.nesdev.org/w/index.php/6502_assembly_optimisations

// PERFORMANCE: All lines are pre-trimmed once in getLinesBy() into TrimmedLine objects
// to avoid repeated trimStart()/instructionPart() calls across overlapping sliding windows.
// TrimmedLine has .value (original), .trimmed (trimStart), and .instruction (instructionPart).


internal fun optimizeAssembly(lines: MutableList<String>, machine: ICompilationTarget, symbolTable: SymbolTable): Int {
    var numberOfOptimizations = 0

    val pretrimmed = lines.map { it.trimStart() }.toMutableList()

    /** Runs an optimization pass, applies modifications if any, and recomputes line windows. */
    fun runPass(
        mods: List<Modification>,
        windowSize: Int,
        currentLines: Sequence<List<TrimmedLine>>
    ): Sequence<List<TrimmedLine>> {
        if (mods.isNotEmpty()) {
            apply(mods, lines, pretrimmed)
            numberOfOptimizations++
            return getLinesBy(pretrimmed, lines, windowSize)
        }
        return currentLines
    }

    var linesByFour = getLinesBy(pretrimmed, lines, 4)
    linesByFour = runPass(optimizeIncDec(linesByFour), 4, linesByFour)
    linesByFour = runPass(optimizeStoreLoadSame(linesByFour, machine, symbolTable), 4, linesByFour)
    linesByFour = runPass(optimizeJsrRtsAndOtherCombinations(linesByFour), 4, linesByFour)
    linesByFour = runPass(optimizeUselessPushPopStack(linesByFour), 4, linesByFour)
    linesByFour = runPass(optimizeUnneededTempvarInAdd(linesByFour), 4, linesByFour)
    linesByFour = runPass(optimizeTSBtoRegularOr(linesByFour), 4, linesByFour)

    var linesByFourteen = getLinesBy(pretrimmed, lines, 14)
    linesByFourteen = runPass(optimizeSameAssignments(linesByFourteen, machine, symbolTable), 14, linesByFourteen)
    linesByFourteen = runPass(optimizeSamePointerIndexingAndUselessBeq(linesByFourteen), 14, linesByFourteen)
    linesByFourteen = runPass(optimizeAddWordToSameVariableOrExtraRegisterLoadInWordStore(linesByFourteen), 14, linesByFourteen)

    return numberOfOptimizations
}

internal fun String.isBranch() = this.startsWith("b")
internal fun String.isStoreReg() = this.startsWith("sta ") || this.startsWith("sty ") || this.startsWith("stx ")
internal fun String.isStoreRegOrZero() = this.isStoreReg() || this.startsWith("stz ")
internal fun String.isLoadReg() = this.startsWith("lda ") || this.startsWith("ldy ") || this.startsWith("ldx ")

private class Modification(val lineIndex: Int, val remove: Boolean, val replacement: String?, val removeLabel: Boolean=false)

/** Pre-trimmed assembly line. Has .index and .value properties matching IndexedValue<String> for compatibility.
 *  Use `.trimmed` for pattern matching (preserves leading whitespace, just removes indentation).
 *  Use `.instruction` when you need the instruction mnemonic without any label prefix.
 *  Use `.value` for the original raw line (needed for replacements). */
private class TrimmedLine(val value: String, val trimmed: String, val index: Int) {
    val instruction: String = trimmed.instructionPart()
}

private fun getLinesBy(pretrimmed: MutableList<String>, originalLines: MutableList<String>, windowSize: Int): Sequence<List<TrimmedLine>> =
    pretrimmed.asSequence()
        .withIndex()
        .filter { it.value.isNotBlank() && !it.value.startsWith(';') }
        .map { TrimmedLine(originalLines[it.index], it.value, it.index) }
        .windowed(windowSize, partialWindows = false)

private fun apply(modifications: List<Modification>, lines: MutableList<String>, pretrimmed: MutableList<String>) {
    for (modification in modifications.sortedBy { it.lineIndex }.reversed()) {
        if(modification.remove) {
            if(modification.removeLabel)
                lines.removeAt(modification.lineIndex)
            else {
                val pretrim = pretrimmed.getOrNull(modification.lineIndex)
                if (pretrim == null || pretrim.isBlank() || pretrim[0] == ';')
                    lines.removeAt(modification.lineIndex)
                else if (haslabelPretrimmed(pretrim)) {
                    val label = keeplabelPretrimmed(pretrim)
                    if (label.isNotEmpty())
                        lines[modification.lineIndex] = label
                    else
                        lines.removeAt(modification.lineIndex)
                } else lines.removeAt(modification.lineIndex)
            }
            pretrimmed.removeAt(modification.lineIndex)
        }
        else {
            lines[modification.lineIndex] = modification.replacement!!
            pretrimmed[modification.lineIndex] = modification.replacement.trimStart()
        }
    }
}

internal fun haslabel(line: String): Boolean {
    return line.length>1 && line[0]!=';' && (!line[0].isWhitespace() || ':' in line)
}

internal fun haslabelPretrimmed(line: String): Boolean {
    return line.length>1 && line[0]!=';' && ':' in line
}

internal fun keeplabel(line: String): String {
    if(':' in line)
        return line.substringBefore(':') + ':'
    val splits = line.split('\t', ' ', limit=2)
    return if(splits.size>1) splits[0] + ':' else ""
}

internal fun keeplabelPretrimmed(line: String): String {
    if(':' in line)
        return line.substringBefore(':') + ':'
    val idx = line.indexOf(' ')
    val idxTab = line.indexOf('\t')
    val splitIdx = if(idx==-1) idxTab else if(idxTab==-1) idx else minOf(idx, idxTab)
    return if(splitIdx>0) line.substring(0, splitIdx) + ':' else ""
}

/** Extracts operand from an already-processed instruction (label already stripped).
 *  More efficient than extractOperand() when you already have the instruction part. */
private fun String.extractOperandFromInstruction(): String {
    val spaceIdx = this.indexOf(' ')
    return if (spaceIdx >= 0) this.substring(spaceIdx) else ""
}

/** Extracts operand from instruction and trims it in one pass.
 *  Efficient when you just need the operand value without leading whitespace. */
private fun String.extractOperandTrimmed(): String {
    val spaceIdx = this.indexOf(' ')
    return if (spaceIdx >= 0) this.substring(spaceIdx).trimStart() else ""
}

/** Checks if a trimmed assembly line contains the given instruction mnemonic.
 *  Handles lines with or without labels. Named labels must end with ':'.
 *  Anonymous labels (+, ++, -, --) may or may not have a colon.
 *  Does NOT match mnemonics in comments or operands. */
private val namedLabelPattern = Regex("^[a-zA-Z_][a-zA-Z0-9_.]*:\\s+(.*)$")
private val anonLabelPattern = Regex("^[-+]+:?\\s+(.*)$")

internal fun String.hasInstr(mnemonic: String): Boolean {
    if (this == mnemonic || startsWith("$mnemonic ")) return true
    // Check for named label with colon
    val namedMatch = namedLabelPattern.matchEntire(this)
    if (namedMatch != null) {
        val afterLabel = namedMatch.groupValues[1]
        return afterLabel.startsWith("$mnemonic ") || afterLabel == mnemonic
    }
    // Check for anonymous label
    val anonMatch = anonLabelPattern.matchEntire(this)
    if (anonMatch != null) {
        val afterLabel = anonMatch.groupValues[1]
        return afterLabel.startsWith("$mnemonic ") || afterLabel == mnemonic
    }
    return false
}

/** Extracts the instruction part of a trimmed assembly line, skipping any label prefix.
 *  For "mylabel: lda  #1" returns "lda  #1". For "lda  #1" returns "lda  #1". */
private fun String.instructionPart(): String {
    val namedMatch = namedLabelPattern.matchEntire(this)
    if (namedMatch != null) return namedMatch.groupValues[1]
    val anonMatch = anonLabelPattern.matchEntire(this)
    if (anonMatch != null) return anonMatch.groupValues[1]
    return this
}

private fun optimizeSameAssignments(
    linesByFourteen: Sequence<List<TrimmedLine>>,
    machine: ICompilationTarget,
    symbolTable: SymbolTable
): List<Modification> {

    // Optimize sequential assignments of the same value to various targets (bytes, words, floats)
    // the float one is the one that requires 2*7=14 lines of code to check...
    // The better place to do this is in the Compiler instead and never create these types of assembly, but hey

    val mods = mutableListOf<Modification>()
    for (lines in linesByFourteen) {
        val f1 = lines[0].instruction
        val f2 = lines[1].instruction
        val f3 = lines[2].instruction
        val f4 = lines[3].instruction
        val f5 = lines[4].instruction
        val f6 = lines[5].instruction
        val f7 = lines[6].instruction
        val f8 = lines[7].instruction

        if(f1.startsWith("lda ") && f2.startsWith("ldy ") && f3.startsWith("sta ") && f4.startsWith("sty ") &&
                f5.startsWith("lda ") && f6.startsWith("ldy ") && f7.startsWith("sta ") && f8.startsWith("sty ")) {
            val firstvalue = f1.extractOperandTrimmed()
            val secondvalue = f2.extractOperandTrimmed()
            val thirdvalue = f5.extractOperandTrimmed()
            val fourthvalue = f6.extractOperandTrimmed()
            if(firstvalue==thirdvalue && secondvalue==fourthvalue) {
                // lda/ldy   sta/sty   twice the same word -->  remove second lda/ldy pair (fifth and sixth lines)
                // Immediate values (#) are always safe to optimize; memory addresses must not be IO
                val isImmediate1 = firstvalue.startsWith('#')
                val isImmediate2 = secondvalue.startsWith('#')
                val safe1 = isImmediate1 || run {
                    val addr = getAddressArg(f1, symbolTable)
                    addr == null && !looksLikeIOAddress(firstvalue, machine) ||
                    addr != null && !machine.isIOAddress(addr)
                }
                val safe2 = isImmediate2 || run {
                    val addr = getAddressArg(f2, symbolTable)
                    addr == null && !looksLikeIOAddress(secondvalue, machine) ||
                    addr != null && !machine.isIOAddress(addr)
                }
                if(safe1 && safe2) {
                    mods.add(Modification(lines[4].index, true, null))
                    mods.add(Modification(lines[5].index, true, null))
                }
            }
        }

        if(f1.startsWith("lda ") && f2.startsWith("sta ") && f3.startsWith("lda ") && f4.startsWith("sta ")) {
            val firstvalue = f1.extractOperandTrimmed()
            val secondvalue = f3.extractOperandTrimmed()
            if(firstvalue==secondvalue) {
                // lda value / sta ? / lda same-value / sta ?  -> remove second lda (third line)
                // Immediate values (#) are always safe; memory addresses must not be IO
                val isImmediate = firstvalue.startsWith('#')
                val safe = isImmediate || run {
                    val addr = getAddressArg(f1, symbolTable)
                    addr == null && !looksLikeIOAddress(firstvalue, machine) ||
                    addr != null && !machine.isIOAddress(addr)
                }
                if(safe)
                    mods.add(Modification(lines[2].index, true, null))
            }
        }

        if(f1.startsWith("lda ") && f2.startsWith("ldy ") && f3.startsWith("sta ") && f4.startsWith("sty ") &&
                f5.startsWith("lda ") && f6.startsWith("ldy ") &&
                (f7.startsWith("jsr  floats.copy_float") || f7.startsWith("jsr  cx16flt.copy_float"))) {

            val nineth = lines[8].instruction
            val tenth = lines[9].instruction
            val eleventh = lines[10].instruction
            val twelveth = lines[11].instruction
            val thirteenth = lines[12].instruction
            val fourteenth = lines[13].instruction

            if(f8.startsWith("lda ") && nineth.startsWith("ldy ") && tenth.startsWith("sta ") && eleventh.startsWith("sty ") &&
                    twelveth.startsWith("lda ") && thirteenth.startsWith("ldy ") &&
                    (fourteenth.startsWith("jsr  floats.copy_float") || fourteenth.startsWith("jsr  cx16flt.copy_float"))) {

                if(f1.extractOperandTrimmed() == f8.extractOperandTrimmed() && f2.extractOperandTrimmed()==nineth.extractOperandTrimmed()) {
                    // identical float init
                    mods.add(Modification(lines[7].index, true, null))
                    mods.add(Modification(lines[8].index, true, null))
                    mods.add(Modification(lines[9].index, true, null))
                    mods.add(Modification(lines[10].index, true, null))
                }
            }
        }

        var overlappingMods = false
        /*
        sta  prog8_lib.retval_intermX      ; remove
        sty  prog8_lib.retval_intermY      ; remove
        lda  prog8_lib.retval_intermX      ; remove
        ldy  prog8_lib.retval_intermY      ; remove
        sta  A1
        sty  A2
         */
        if(f1.isStoreReg() && f2.isStoreReg()
            && f3.isLoadReg() && f4.isLoadReg()
            && f5.isStoreReg() && f6.isStoreReg()) {
            val reg1 = f1[2]
            val reg2 = f2[2]
            val reg3 = f3[2]
            val reg4 = f4[2]
            val reg5 = f5[2]
            val reg6 = f6[2]
            if (reg1 == reg3 && reg1 == reg5 && reg2 == reg4 && reg2 == reg6) {
                val firstvalue = f1.extractOperandTrimmed()
                val secondvalue = f2.extractOperandTrimmed()
                val thirdvalue = f3.extractOperandTrimmed()
                val fourthvalue = f4.extractOperandTrimmed()
                if(firstvalue.contains("prog8_lib.retval_interm") && secondvalue.contains("prog8_lib.retval_interm")
                    && firstvalue==thirdvalue && secondvalue==fourthvalue) {
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[1].index, true, null))
                    mods.add(Modification(lines[2].index, true, null))
                    mods.add(Modification(lines[3].index, true, null))
                    overlappingMods = true
                }
            }
        }

        /*
        sta  A1
        sty  A2
        lda  A1     ; can be removed
        ldy  A2     ; can be removed if not followed by a branch instuction
         */
        if(!overlappingMods && f1.isStoreReg() && f2.isStoreReg()
            && f3.isLoadReg() && f4.isLoadReg()) {
            val reg1 = f1[2]
            val reg2 = f2[2]
            val reg3 = f3[2]
            val reg4 = f4[2]
            if(reg1==reg3 && reg2==reg4) {
                val firstvalue = f1.extractOperandTrimmed()
                val secondvalue = f2.extractOperandTrimmed()
                val thirdvalue = f3.extractOperandTrimmed()
                val fourthvalue = f4.extractOperandTrimmed()
                if(firstvalue==thirdvalue && secondvalue == fourthvalue) {
                    val address = getAddressArg(f1, symbolTable)
                    val isIO = address != null && machine.isIOAddress(address) ||
                               address == null && looksLikeIOAddress(firstvalue, machine)
                    if(!isIO) {
                        overlappingMods = true
                        mods.add(Modification(lines[2].index, true, null))
                        if (!lines[4].instruction.startsWith('b'))
                            mods.add(Modification(lines[3].index, true, null))
                    }
                }
            }
        }

        /*
        sta  A1
        sty  A2     ; ... or stz
        lda  A1     ; can be removed if not followed by a branch instruction
         */
        if(!overlappingMods && f1.isStoreReg() && f2.isStoreRegOrZero()
            && f3.isLoadReg() && !f4.isBranch()) {
            val reg1 = f1[2]
            val reg3 = f3[2]
            if(reg1==reg3) {
                val firstvalue = f1.extractOperandTrimmed()
                val thirdvalue = f3.extractOperandTrimmed()
                if(firstvalue==thirdvalue) {
                    val address = getAddressArg(f1, symbolTable)
                    val isIO = address != null && machine.isIOAddress(address) ||
                               address == null && looksLikeIOAddress(firstvalue, machine)
                    if(!isIO) {
                        overlappingMods = true
                        mods.add(Modification(lines[2].index, true, null))
                    }
                }
            }
        }

        /*
        sta  A1
        ldy  A1     ; make tay
        sta  A1     ; remove
         */
        if(!overlappingMods && f1.startsWith("sta ") && f2.isLoadReg()
            && f3.startsWith("sta ") && f2.length>4) {
            val firstvalue = f1.extractOperandTrimmed()
            val secondvalue = f2.extractOperandTrimmed()
            val thirdvalue = f3.extractOperandTrimmed()
            if(firstvalue==secondvalue && firstvalue==thirdvalue) {
                val address = getAddressArg(f1, symbolTable)
                val isIO = address != null && machine.isIOAddress(address) ||
                           address == null && looksLikeIOAddress(firstvalue, machine)
                if(!isIO) {
                    overlappingMods = true
                    val reg2 = f2[2]
                    mods.add(Modification(lines[1].index, false, "  ta$reg2"))
                    mods.add(Modification(lines[2].index, true, null))
                }
            }
        }

        /*
        sta  A   ; or stz     double store, remove this first one
        sta  A   ; or stz
        However, this cannot be done relyably because 'A' could be a constant symbol referring to an I/O address.
        We can't see that here and would otherwise delete valid double stores.
        */
    }

    return mods
}

private fun optimizeSamePointerIndexingAndUselessBeq(linesByFourteen: Sequence<List<TrimmedLine>>): List<Modification> {

    // Optimize same pointer indexing where for instance we load and store to the same ptr index in Y
    // if Y isn't modified in between we can omit the second LDY:
    //    ldy  #0
    //    lda  (ptr),y
    //    ora  #3       ; <-- instruction(s) that don't modify Y
    //    ldy  #0       ; <-- can be removed
    //    sta  (ptr),y

    val mods = mutableListOf<Modification>()
    for (lines in linesByFourteen) {
        val f1 = lines[0].instruction
        val f2 = lines[1].instruction
        val third = lines[2].trimmed
        val f4 = lines[3].instruction
        val f5 = lines[4].instruction
        val f6 = lines[5].instruction

        if(f1.startsWith("ldy ") && f2.startsWith("lda ") && f4.startsWith("ldy ") && f5.startsWith("sta ")) {
            val firstvalue = f1.extractOperandTrimmed()
            val secondvalue = f2.extractOperandTrimmed()
            val fourthvalue = f4.extractOperandTrimmed()
            val fifthvalue = f5.extractOperandTrimmed()
            if("y" !in third && firstvalue==fourthvalue && secondvalue==fifthvalue && secondvalue.endsWith(",y") && fifthvalue.endsWith(",y")) {
                mods.add(Modification(lines[3].index, true, null))
            }
        }
        if(f1.startsWith("ldy ") && f2.startsWith("lda ") && f5.startsWith("ldy ") && f6.startsWith("sta ")) {
            val firstvalue = f1.extractOperandTrimmed()
            val secondvalue = f2.extractOperandTrimmed()
            val fifthvalue = f5.extractOperandTrimmed()
            val sixthvalue = f6.extractOperandTrimmed()
            if("y" !in third && "y" !in f4 && firstvalue==fifthvalue && secondvalue==sixthvalue && secondvalue.endsWith(",y") && sixthvalue.endsWith(",y")) {
                mods.add(Modification(lines[4].index, true, null))
            }
        }


        /*
    beq  +
    lda  #1
+
[ optional:  label_xxxx_shortcut   line here]
    beq  label_xxxx_shortcut   /  bne label_xxxx_shortcut
or *_afterif labels.

This gets generated after certain if conditions, and only the branch instruction is needed in these cases.
         */

        val autoLabelPrefix = GENERATED_LABEL_PREFIX
        if(f1=="beq  +" && f2=="lda  #1" && lines[2].trimmed=="+") {
            if((f4.startsWith("beq  $autoLabelPrefix") || f4.startsWith("bne  $autoLabelPrefix")) &&
                (f4.endsWith("_shortcut") || f4.endsWith("_afterif") || f4.endsWith("_shortcut:") || f4.endsWith("_afterif:"))) {
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, true, null))
                mods.add(Modification(lines[2].index, true, null))
            }
            else if(f4.startsWith(autoLabelPrefix) && (f4.endsWith("_shortcut") || f4.endsWith("_shortcut:"))) {
                if((f5.startsWith("beq  $autoLabelPrefix") || f5.startsWith("bne  $autoLabelPrefix")) &&
                    (f5.endsWith("_shortcut") || f5.endsWith("_afterif") || f5.endsWith("_shortcut:") || f5.endsWith("_afterif:"))) {
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[1].index, true, null))
                    mods.add(Modification(lines[2].index, true, null))
                }
            }
        }
    }

    return mods
}

private fun optimizeStoreLoadSame(
    linesByFour: Sequence<List<TrimmedLine>>,
    machine: ICompilationTarget,
    symbolTable: SymbolTable
): List<Modification> {
    val mods = mutableListOf<Modification>()

    // Push/pop pairs that can be eliminated when consecutive
    val pushPopPairs = mapOf("pha" to "pla", "phx" to "plx", "phy" to "ply", "php" to "plp")

    for (lines in linesByFour) {
        val first = lines[1].trimmed
        val second = lines[2].trimmed
        val third = lines[3].trimmed

        // sta X + lda X,  sty X + ldy X,   stx X + ldx X  -> the second instruction can OFTEN be eliminated
        if ((first.startsWith("sta ") && second.startsWith("lda ")) ||
                (first.startsWith("stx ") && second.startsWith("ldx ")) ||
                (first.startsWith("sty ") && second.startsWith("ldy ")) ||
                (first.startsWith("lda ") && second.startsWith("lda ")) ||
                (first.startsWith("ldy ") && second.startsWith("ldy ")) ||
                (first.startsWith("ldx ") && second.startsWith("ldx "))
        ) {
            val attemptRemove =
                if(third.isBranch()) {
                    // a branch instruction follows, we can only remove the load instruction if
                    // another load instruction of the same register precedes the store instruction
                    // (otherwise wrong cpu flags are used)
                    val loadinstruction = second.take(3)
                    lines[0].trimmed.startsWith(loadinstruction)
                }
                else {
                    // no branch instruction follows, we can remove the load instruction
                    // if we can resolve the address and it's NOT IO, or if it doesn't look like IO
                    val instr = lines[2].instruction
                    val address = getAddressArg(instr, symbolTable)
                    val operand = instr.extractOperandTrimmed()
                    val isIO = address != null && machine.isIOAddress(address) ||
                               address == null && looksLikeIOAddress(operand, machine)
                    !isIO
                }

            if(attemptRemove) {
                val firstLoc = first.extractOperandTrimmed()
                val secondLoc = second.extractOperandTrimmed()
                if (firstLoc == secondLoc)
                    mods.add(Modification(lines[2].index, true, null))
            }
        }
        else if(pushPopPairs.entries.any { (push, pop) -> first == push && second == pop }) {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, true, null))
        } else if(first=="pha" && second=="plx") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  tax"))
        } else if(first=="pha" && second=="ply") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  tay"))
        } else if(first=="phx" && second=="pla") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  txa"))
        } else if(first=="phy" && second=="pla") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  tya"))
        }


        // lda X + sta X,  ldy X + sty X,   ldx X + stx X  -> the second instruction can be eliminated
        // ONLY if X is NOT an IO address (reading from IO may have side effects)
        if (first.startsWith("lda ") && second.startsWith("sta ") ||
            first.startsWith("ldx ") && second.startsWith("stx ") ||
            first.startsWith("ldy ") && second.startsWith("sty ")
        ) {
            val firstLoc = first.extractOperandTrimmed()
            val secondLoc = second.extractOperandTrimmed()
            if (firstLoc == secondLoc) {
                val address = getAddressArg(first, symbolTable)
                val isIO = address != null && machine.isIOAddress(address) ||
                           address == null && looksLikeIOAddress(firstLoc, machine)
                if(!isIO)
                    mods.add(Modification(lines[2].index, true, null))
            }
        }

        //  all 3 registers:  lda VALUE + sta SOMEWHERE + lda VALUE  -> last load can be eliminated IF NOT IO ADDRESS
        if (first.startsWith("lda ") && second.startsWith("sta ") && third.startsWith("lda ") ||
            first.startsWith("ldx ") && second.startsWith("stx ") && third.startsWith("ldx ") ||
            first.startsWith("ldy ") && second.startsWith("sty ") && third.startsWith("ldy ")
        ) {
            val firstVal = first.extractOperandTrimmed()
            val thirdVal = third.extractOperandTrimmed()
            if (firstVal == thirdVal) {
                val address = getAddressArg(third, symbolTable)
                val isIO = address != null && machine.isIOAddress(address) ||
                           address == null && looksLikeIOAddress(thirdVal, machine)
                if(!isIO) {
                    mods.add(Modification(lines[3].index, true, null))
                }
            }
        }
    }
    return mods
}

private val identifierRegex = Regex("""^([a-zA-Z_$][a-zA-Z\d_.$]*)""")

internal fun getAddressArg(instruction: String, symbolTable: SymbolTable): UInt? {
    // instruction should already be label-stripped (e.g., from TrimmedLine.instruction)
    val loadArg = instruction.substring(3).trim()
    return when {
        loadArg.isEmpty() -> null
        loadArg.startsWith('$') -> loadArg.substring(1).toUIntOrNull(16)
        loadArg.startsWith('%') -> loadArg.substring(1).toUIntOrNull(2)
        loadArg.startsWith('#') -> null
        loadArg.startsWith('(') -> null
        loadArg[0].isLetter() -> {
            val identMatch = identifierRegex.find(loadArg)
            if(identMatch!=null) {
                val identifier = identMatch.value
                when (val symbol = symbolTable.flat[identifier]) {
                    is StConstant -> {
                        if(symbol.value!=null)
                            symbol.value!!.toUInt()
                        else
                            TODO("get memory()? $symbol")
                    }
                    is StMemVar -> symbol.address
                    else -> null
                }
            } else null
        }
        else -> loadArg.substring(1).toUIntOrNull()
    }
}

/** Heuristic check: does this operand string look like an IO address?
 *  Used when symbol table lookup fails - catches cases like `cx16.VERA_DATA1`
 *  that should be IO but might not be in the symbol table at optimization time.
 *  Delegates to machine.isIOAddress() for any parseable address. */
private fun looksLikeIOAddress(operand: String, machine: ICompilationTarget): Boolean {
    // Known IO module prefixes in the standard library
    if(operand.startsWith("cx16.") || operand.startsWith("cbm.") ||
        operand.startsWith("c128.") || operand.startsWith("c64.") ||
        operand.startsWith("pet32.") || operand.startsWith("sys."))
        return true
    // Try to parse hex address and delegate to machine
    if(operand.startsWith('$')) {
        val hexVal = operand.substring(1).take(4).toUIntOrNull(16)
        if(hexVal != null)
            return machine.isIOAddress(hexVal)
    }
    // Try to parse decimal address (e.g. "53280" = $d000) and delegate to machine
    val decVal = operand.toUIntOrNull()
    if(decVal != null)
        return machine.isIOAddress(decVal)
    // For unresolved symbolic names we can't determine - assume NOT IO
    // (the symbol table lookup already failed, so we have no better info)
    return false
}

private fun optimizeIncDec(linesByFour: Sequence<List<TrimmedLine>>): List<Modification> {
    // sometimes, iny+dey / inx+dex / dey+iny / dex+inx sequences are generated, these can be eliminated.
    val mods = mutableListOf<Modification>()

    // Canceling increment/decrement pairs
    val cancelingPairs = setOf(
        "iny" to "dey", "dey" to "iny",
        "inx" to "dex", "dex" to "inx",
        "ina" to "dea", "dea" to "ina",
        "inc  a" to "dec  a", "dec  a" to "inc  a",
        "inc a" to "dec a", "dec a" to "inc a"
    )

    for (lines in linesByFour) {
        val first = lines[0].trimmed
        val second = lines[1].trimmed
        if (cancelingPairs.any { (inc, dec) -> first.hasInstr(inc) && second.hasInstr(dec) }) {
            mods.add(Modification(lines[0].index, true, null))
            mods.add(Modification(lines[1].index, true, null))
        }

    }
    return mods
}

private fun optimizeJsrRtsAndOtherCombinations(linesByFour: Sequence<List<TrimmedLine>>): List<Modification> {
    // jsr Sub + rts -> jmp Sub
    // jmp Sub + rts -> jmp Sub
    // rts + jmp -> remove jmp
    // rts + bxx -> remove bxx
    // lda  + cmp #0 -> remove cmp,  same for cpy and cpx.
    // bra/jmp + bra/jmp -> remove second bra/jmp   (bra bra / jmp jmp are not removed because this is likely a jump table!)
    // and some other optimizations.

    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val tfirst = lines[0].instruction
        val tsecond = lines[1].instruction
        val tthird = lines[2].instruction
        val tfourth = lines[3].instruction

        if(!haslabel(lines[1].value)) {
            if ((tfirst.hasInstr("jmp") || tfirst.hasInstr("bra")) && tsecond.hasInstr("rts")) {
                mods += Modification(lines[1].index, true, null)
            }
            else if (tfirst.hasInstr("jsr") && tsecond.hasInstr("rts")) {
                if(!tfirst.contains("floats.pushFAC") && !tfirst.contains("floats.popFAC")) {       // these 2 routines depend on being called with JSR!!
                    mods += Modification(lines[0].index, false, lines[0].value.replace("jsr", "jmp"))
                    mods += Modification(lines[1].index, true, null)
                }
            }
            else if (tfirst.hasInstr("rts")) {
                // After RTS, any branch or jump is unreachable - remove it
                val branchInstructions = setOf("jmp", "bra", "bcc", "bcs", "beq", "bne", "bmi", "bpl", "bvs", "bvc")
                if (branchInstructions.any { tsecond.hasInstr(it) })
                    mods += Modification(lines[1].index, true, null)
            }

            val loadComparePairs = mapOf("lda" to "cmp", "ldx" to "cpx", "ldy" to "cpy")
            if (loadComparePairs.any { (load, compare) ->
                tfirst.hasInstr(load) && (tsecond.startsWith("$compare  #0") || tsecond.contains(" $compare  #0"))
            }) {
                mods.add(Modification(lines[1].index, true, null))
            }
            else if(tsecond.startsWith("cmp  #0") || tsecond.contains(" cmp  #0")) {
                // there are many instructions that modify A and set the bits...
                for(instr in arrayOf("lda", "ora", "and", "eor", "adc", "sbc", "asl", "cmp", "inc  a", "inc a", "dec  a", "dec a", "lsr", "pla", "rol", "ror", "txa", "tya")) {
                    if(tfirst.hasInstr(instr)) {
                        mods.add(Modification(lines[1].index, true, null))
                    }
                }
            }

            // only remove bra followed by jmp or jmp followed by bra
            // bra bra or jmp jmp is likely part of a jump table, which should keep all entries!
            if(tfirst.hasInstr("bra") && tsecond.hasInstr("jmp")) {
                mods.add(Modification(lines[1].index, true, null))
            }
            if(tfirst.hasInstr("jmp") && tsecond.hasInstr("bra")) {
                mods.add(Modification(lines[1].index, true, null))
            }
        }

        /*
    LDA NUM1
    CMP NUM2
    BCC LABEL
    BEQ LABEL

(or something similar) which branches to LABEL when NUM1 <= NUM2. (In this case NUM1 and NUM2 are unsigned numbers.) However, consider the following sequence:

    LDA NUM2
    CMP NUM1
    BCS LABEL
         */
        if(tfirst.startsWith("lda ") && tsecond.startsWith("cmp ") && tthird.startsWith("bcc ") && tfourth.startsWith("beq ")) {
            val label = tthird.extractOperandFromInstruction()
            if(label==tfourth.extractOperandFromInstruction()) {
                mods += Modification(lines[0].index, false, "  lda  ${tsecond.extractOperandFromInstruction()}")
                mods += Modification(lines[1].index, false, "  cmp  ${tfirst.extractOperandFromInstruction()}")
                mods += Modification(lines[2].index, false, "  bcs  $label")
                mods += Modification(lines[3].index, true, null)
            }
        }


        fun sameLabel(branchInstr: String, jumpInstr: String, labelInstr: String): Boolean {
            if('(' in jumpInstr) return false       // indirect jump cannot be replaced
            val label = labelInstr.trimEnd().substringBefore(':').substringBefore(' ').substringBefore('\t')
            val branchLabel = branchInstr.extractOperandTrimmed()
            return label==branchLabel
        }

        // beq Label + jmp Addr + Label  -> bne Addr
        if(tsecond.hasInstr("jmp") && haslabel(lines[2].value)) {
            val branchInversions = mapOf(
                "beq" to "bne", "bne" to "beq",
                "bcc" to "bcs", "bcs" to "bcc",
                "bpl" to "bmi", "bmi" to "bpl",
                "bvc" to "bvs", "bvs" to "bvc"
            )
            val firstMnemonic = tfirst.take(3)
            branchInversions[firstMnemonic]?.let { invertedBranch ->
                if (sameLabel(tfirst, tsecond, tthird)) {
                    val branch = lines[1].value.replace("jmp", invertedBranch)
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[1].index, false, branch))
                }
            }
        }
    }
    return mods
}

private fun optimizeUselessPushPopStack(linesByFour: Sequence<List<TrimmedLine>>): List<Modification> {
    val mods = mutableListOf<Modification>()

    fun optimize(register: Char, lines: List<TrimmedLine>) {
        if(lines[0].instruction.startsWith("ph$register")) {
            if(lines[2].instruction.startsWith("pl$register")) {
                val second = lines[1].instruction.take(6).lowercase()
                if(register!in second
                    && !second.startsWith("jsr ")
                    && !second.startsWith("pl")
                    && !second.startsWith("ph")) {
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[2].index, true, null))
                }
            }
            else if (lines[3].instruction.startsWith("pl$register")) {
                val second = lines[1].instruction.take(6).lowercase()
                val third = lines[2].instruction.take(6).lowercase()
                if(register !in second && register !in third
                    && !second.startsWith("jsr ") && !third.startsWith("jsr ")
                    && !second.startsWith("pl") && !third.startsWith("pl")
                    && !second.startsWith("ph") && !third.startsWith("ph")) {
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[3].index, true, null))
                }
            }
        }
    }

    for (lines in linesByFour) {
        optimize('a', lines)
        optimize('x', lines)
        optimize('y', lines)

        val first = lines[0].instruction
        val second = lines[1].instruction
        val third = lines[2].instruction
        val fourth = lines[3].instruction

        // phy + ldy + pla -> tya + ldy
        // phx + ldx + pla -> txa + ldx
        // pha + lda + pla -> nop
        // pha + tya + tay + pla -> nop
        // pha + txa + tax + pla -> nop
        when (first) {
            "phy" if second.startsWith("ldy ") && third=="pla" -> {
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[0].index, false, "  tya"))
            }
            "phx" if second.startsWith("ldx ") && third=="pla" -> {
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[0].index, false, "  txa"))
            }
            "pha" if second.startsWith("lda ") && third=="pla" -> {
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, true, null))
                mods.add(Modification(lines[2].index, true, null))
            }
            "pha" if ((second=="tya" && third=="tay") || (second=="txa" && third=="tax")) && fourth=="pla" -> {
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, true, null))
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[3].index, true, null))
            }
        }
    }


    return mods
}

private fun optimizeTSBtoRegularOr(linesByFour: Sequence<List<TrimmedLine>>): List<Modification> {
    // Asm peephole:   lda var2 / tsb var1 / lda var1  Replace this with this to save 1 cycle:   lda var1 / ora var2 / sta var1
    val mods = mutableListOf<Modification>()

    for(lines in linesByFour) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        val third = lines[2].instruction
        if(first.startsWith("lda ") && second.startsWith("tsb ") && third.startsWith("lda ")) {
            val operand1 = first.extractOperandTrimmed()
            val operand2 = second.extractOperandTrimmed()
            val operand3 = third.extractOperandTrimmed()
            if(operand1!=operand2 && operand2==operand3) {
                mods.add(Modification(lines[0].index, false, "  lda  $operand2"))
                mods.add(Modification(lines[1].index, false, "  ora  $operand1"))
                mods.add(Modification(lines[2].index, false, "  sta  $operand2"))
            }
        }
    }
    return mods
}

private fun optimizeUnneededTempvarInAdd(linesByFour: Sequence<List<TrimmedLine>>): List<Modification> {
    // sequence:  sta  P8ZP_SCRATCH_XX  / lda  something / clc / adc  P8ZP_SCRATCH_XX
    // this can be performed without the scratch variable:  clc  /  adc  something
    val mods = mutableListOf<Modification>()

    for(lines in linesByFour) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        val third = lines[2].instruction
        val fourth = lines[3].instruction
        if(first.startsWith("sta  P8ZP_SCRATCH_") && second.startsWith("lda ") && third=="clc" && fourth.startsWith("adc  P8ZP_SCRATCH_") ) {
            if(fourth.extractOperandTrimmed()==first.extractOperandTrimmed()) {
                mods.add(Modification(lines[0].index, false, "  clc"))
                mods.add(Modification(lines[1].index, false, "  adc  ${second.extractOperandTrimmed()}"))
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[3].index, true, null))
            }
        }
    }

    return mods
}

private fun optimizeAddWordToSameVariableOrExtraRegisterLoadInWordStore(linesByFourteen: Sequence<List<TrimmedLine>>): List<Modification> {
    /*
        ; FIRST SEQUYENCE: P8ZP_SCRATCH_PTR += AY :
        clc
        adc  P8ZP_SCRATCH_PTR
        pha
        tya
        adc  P8ZP_SCRATCH_PTR+1
        tay
        pla
        sta  P8ZP_SCRATCH_PTR
        sty  P8ZP_SCRATCH_PTR+1

        ->

        clc
        adc  P8ZP_SCRATCH_PTR
        sta  P8ZP_SCRATCH_PTR
        tya
        adc  P8ZP_SCRATCH_PTR+1
        sta  P8ZP_SCRATCH_PTR+1


        also SECOND SEQUENCE:

        ldx  VALUE/  ldy  VALUE
        sta  SOMEWHERE_WITHOUT_,x_OR_,y
        txa /   tya
        ldy  #1
        sta  SOMEWHERE
        -->
        sta  SOMEWHERE_WITHOUT_,x_OR_,y
        lda  VALUE
        ldy  #1
        sta  SOMEWHERE


        also THIRD SEQUENCE:

        ldx  VALUE
        ldy  #0
        sta  SOMEWHERE_WITHOUT_,x
        txa
        iny
        sta  SOMEWHERE
         -->
        ldy  #0
        sta  SOMEWHERE_WITHOUT_,x
        lda  VALUE
        iny
        sta  SOMEWHERE
    */
    val mods = mutableListOf<Modification>()
    for (lines in linesByFourteen) {
        val first = lines[0].instruction
        val second = lines[1].instruction
        val third = lines[2].instruction
        val fourth = lines[3].instruction
        val fifth = lines[4].instruction
        val sixth = lines[5].instruction
        val seventh = lines[6].instruction
        val eight = lines[7].instruction
        val ninth = lines[8].instruction

        // FIRST SEQUENCE
        if(first=="clc" && second.startsWith("adc ") && third=="pha" && fourth=="tya" &&
            fifth.startsWith("adc ") && sixth=="tay" && seventh=="pla" && eight.startsWith("sta ") && ninth.startsWith("sty ")) {
            val var2 = second.extractOperandTrimmed()
            val var5 = fifth.extractOperandTrimmed().substringBefore('+')
            val var8 = eight.extractOperandTrimmed()
            val var9 = ninth.extractOperandTrimmed().substringBefore('+')
            if(var2==var5 && var2==var8 && var2==var9) {
                if(fifth.endsWith("$var5+1") && ninth.endsWith("$var9+1")) {
                    mods.add(Modification(lines[2].index, false, "  sta  $var2"))
                    mods.add(Modification(lines[5].index, false, "  sta  $var2+1"))
                    mods.add(Modification(lines[6].index, true, null))
                    mods.add(Modification(lines[7].index, true, null))
                    mods.add(Modification(lines[8].index, true, null))
                }
            }
        }

        // SECOND SEQUENCE: ldx/ldy + sta + txa/tya + ldy + sta  -> sta + lda + ldy + sta
        val transferInstructions = mapOf('x' to "txa", 'y' to "tya")
        for ((reg, transferInstr) in transferInstructions) {
            if(first.startsWith("ld$reg ") && second.startsWith("sta ") &&
                third==transferInstr && fourth.startsWith("ldy ") && fifth.startsWith("sta ")
            ) {
                if(",${reg}" !in second) {
                    val value = first.extractOperandTrimmed()
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[2].index, false, "  lda  $value"))
                }
            }
        }

        // THIRD SEQUENCE: ldx + ldy + sta + txa + iny + sta  -> ldy + sta + lda + iny + sta
        if(first.startsWith("ldx ") && second.startsWith("ldy ") && third.startsWith("sta ") &&
            fourth=="txa" && fifth=="iny" && sixth.startsWith("sta ")
        ) {
            if(",x" !in third) {
                val value = first.extractOperandTrimmed()
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[3].index, false, "  lda  $value"))
            }
        }

    }
    return mods
}
