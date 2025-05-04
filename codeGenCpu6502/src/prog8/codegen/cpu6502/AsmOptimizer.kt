package prog8.codegen.cpu6502

import prog8.code.GENERATED_LABEL_PREFIX
import prog8.code.StConstant
import prog8.code.StMemVar
import prog8.code.SymbolTable
import prog8.code.core.ICompilationTarget


// note: see https://wiki.nesdev.org/w/index.php/6502_assembly_optimisations


internal fun optimizeAssembly(lines: MutableList<String>, machine: ICompilationTarget, symbolTable: SymbolTable): Int {

    var numberOfOptimizations = 0

    var linesByFour = getLinesBy(lines, 4)

    var mods = optimizeIncDec(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeStoreLoadSame(linesByFour, machine, symbolTable)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeJsrRtsAndOtherCombinations(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeUselessPushPopStack(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeUnneededTempvarInAdd(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeTSBtoRegularOr(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    var linesByFourteen = getLinesBy(lines, 14)
    mods = optimizeSameAssignments(linesByFourteen, machine, symbolTable)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFourteen = getLinesBy(lines, 14)
        numberOfOptimizations++
    }

    mods = optimizeSamePointerIndexingAndUselessBeq(linesByFourteen)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFourteen = getLinesBy(lines, 14)
        numberOfOptimizations++
    }

    return numberOfOptimizations
}

private fun String.isBranch() = this.startsWith("b")
private fun String.isStoreReg() = this.startsWith("sta") || this.startsWith("sty") || this.startsWith("stx")
private fun String.isStoreRegOrZero() = this.isStoreReg() || this.startsWith("stz")
private fun String.isLoadReg() = this.startsWith("lda") || this.startsWith("ldy") || this.startsWith("ldx")

private class Modification(val lineIndex: Int, val remove: Boolean, val replacement: String?, val removeLabel: Boolean=false)

private fun apply(modifications: List<Modification>, lines: MutableList<String>) {
    for (modification in modifications.sortedBy { it.lineIndex }.reversed()) {
        if(modification.remove) {
            if(modification.removeLabel)
                lines.removeAt(modification.lineIndex)
            else {
                val line = lines[modification.lineIndex]
                if (line.length < 2 || line[0] == ';' || line.trimStart()[0] == ';')
                    lines.removeAt(modification.lineIndex)
                else if (haslabel(line)) {
                    val label = keeplabel(line)
                    if (label.isNotEmpty())
                        lines[modification.lineIndex] = label
                    else
                        lines.removeAt(modification.lineIndex)
                } else lines.removeAt(modification.lineIndex)
            }
        }
        else
            lines[modification.lineIndex] = modification.replacement!!
    }
}

private fun haslabel(line: String): Boolean {
    return line.length>1 && line[0]!=';' && (!line[0].isWhitespace() || ':' in line)
}

private fun keeplabel(line: String): String {
    if(':' in line)
        return line.substringBefore(':') + ':'
    val splits = line.split('\t', ' ', limit=2)
    return if(splits.size>1) splits[0] + ':' else ""
}

private fun getLinesBy(lines: MutableList<String>, windowSize: Int) =
// all lines (that aren't empty or comments) in sliding windows of certain size
        lines.asSequence().withIndex().filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }.windowed(windowSize, partialWindows = false)

private fun optimizeSameAssignments(
    linesByFourteen: Sequence<List<IndexedValue<String>>>,
    machine: ICompilationTarget,
    symbolTable: SymbolTable
): List<Modification> {

    // Optimize sequential assignments of the same value to various targets (bytes, words, floats)
    // the float one is the one that requires 2*7=14 lines of code to check...
    // The better place to do this is in the Compiler instead and never create these types of assembly, but hey

    val mods = mutableListOf<Modification>()
    for (lines in linesByFourteen) {
        val first = lines[0].value.trimStart()
        val second = lines[1].value.trimStart()
        val third = lines[2].value.trimStart()
        val fourth = lines[3].value.trimStart()
        val fifth = lines[4].value.trimStart()
        val sixth = lines[5].value.trimStart()
        val seventh = lines[6].value.trimStart()
        val eighth = lines[7].value.trimStart()

        if(first.startsWith("lda") && second.startsWith("ldy") && third.startsWith("sta") && fourth.startsWith("sty") &&
                fifth.startsWith("lda") && sixth.startsWith("ldy") && seventh.startsWith("sta") && eighth.startsWith("sty")) {
            val firstvalue = first.substring(4)
            val secondvalue = second.substring(4)
            val thirdvalue = fifth.substring(4)
            val fourthvalue = sixth.substring(4)
            if(firstvalue==thirdvalue && secondvalue==fourthvalue) {
                // lda/ldy   sta/sty   twice the same word -->  remove second lda/ldy pair (fifth and sixth lines)
                val address1 = getAddressArg(first, symbolTable)
                val address2 = getAddressArg(second, symbolTable)
                if(address1==null || address2==null || (!machine.isIOAddress(address1) && !machine.isIOAddress(address2))) {
                    mods.add(Modification(lines[4].index, true, null))
                    mods.add(Modification(lines[5].index, true, null))
                }
            }
        }

        if(first.startsWith("lda") && second.startsWith("sta") && third.startsWith("lda") && fourth.startsWith("sta")) {
            val firstvalue = first.substring(4)
            val secondvalue = third.substring(4)
            if(firstvalue==secondvalue) {
                // lda value / sta ? / lda same-value / sta ?  -> remove second lda (third line)
                val address = getAddressArg(first, symbolTable)
                if(address==null || !machine.isIOAddress(address))
                    mods.add(Modification(lines[2].index, true, null))
            }
        }

        if(first.startsWith("lda") && second.startsWith("ldy") && third.startsWith("sta") && fourth.startsWith("sty") &&
                fifth.startsWith("lda") && sixth.startsWith("ldy") &&
                (seventh.startsWith("jsr  floats.copy_float") || seventh.startsWith("jsr  cx16flt.copy_float"))) {

            val nineth = lines[8].value.trimStart()
            val tenth = lines[9].value.trimStart()
            val eleventh = lines[10].value.trimStart()
            val twelveth = lines[11].value.trimStart()
            val thirteenth = lines[12].value.trimStart()
            val fourteenth = lines[13].value.trimStart()

            if(eighth.startsWith("lda") && nineth.startsWith("ldy") && tenth.startsWith("sta") && eleventh.startsWith("sty") &&
                    twelveth.startsWith("lda") && thirteenth.startsWith("ldy") &&
                    (fourteenth.startsWith("jsr  floats.copy_float") || fourteenth.startsWith("jsr  cx16flt.copy_float"))) {

                if(first.substring(4) == eighth.substring(4) && second.substring(4)==nineth.substring(4)) {
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
        if(first.isStoreReg() && second.isStoreReg()
            && third.isLoadReg() && fourth.isLoadReg()
            && fifth.isStoreReg() && sixth.isStoreReg()) {
            val reg1 = first[2]
            val reg2 = second[2]
            val reg3 = third[2]
            val reg4 = fourth[2]
            val reg5 = fifth[2]
            val reg6 = sixth[2]
            if (reg1 == reg3 && reg1 == reg5 && reg2 == reg4 && reg2 == reg6) {
                val firstvalue = first.substring(4)
                val secondvalue = second.substring(4)
                val thirdvalue = third.substring(4)
                val fourthvalue = fourth.substring(4)
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
        if(!overlappingMods && first.isStoreReg() && second.isStoreReg()
            && third.isLoadReg() && fourth.isLoadReg()) {
            val reg1 = first[2]
            val reg2 = second[2]
            val reg3 = third[2]
            val reg4 = fourth[2]
            if(reg1==reg3 && reg2==reg4) {
                val firstvalue = first.substring(4)
                val secondvalue = second.substring(4)
                val thirdvalue = third.substring(4)
                val fourthvalue = fourth.substring(4)
                if(firstvalue==thirdvalue && secondvalue == fourthvalue) {
                    val address = getAddressArg(first, symbolTable)
                    if(address==null || !machine.isIOAddress(address)) {
                        overlappingMods = true
                        mods.add(Modification(lines[2].index, true, null))
                        if (!fifth.startsWith('b'))
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
        if(!overlappingMods && first.isStoreReg() && second.isStoreRegOrZero()
            && third.isLoadReg() && !fourth.isBranch()) {
            val reg1 = first[2]
            val reg3 = third[2]
            if(reg1==reg3) {
                val firstvalue = first.substring(4)
                val thirdvalue = third.substring(4)
                if(firstvalue==thirdvalue) {
                    val address = getAddressArg(first, symbolTable)
                    if(address==null || !machine.isIOAddress(address)) {
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
        if(!overlappingMods && first.startsWith("sta") && second.isLoadReg()
            && third.startsWith("sta") && second.length>4) {
            val firstvalue = first.substring(4)
            val secondvalue = second.substring(4)
            val thirdvalue = third.substring(4)
            if(firstvalue==secondvalue && firstvalue==thirdvalue) {
                val address = getAddressArg(first, symbolTable)
                if(address==null || !machine.isIOAddress(address)) {
                    overlappingMods = true
                    val reg2 = second[2]
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

private fun optimizeSamePointerIndexingAndUselessBeq(linesByFourteen: Sequence<List<IndexedValue<String>>>): List<Modification> {

    // Optimize same pointer indexing where for instance we load and store to the same ptr index in Y
    // if Y isn't modified in between we can omit the second LDY:
    //    ldy  #0
    //    lda  (ptr),y
    //    ora  #3       ; <-- instruction(s) that don't modify Y
    //    ldy  #0       ; <-- can be removed
    //    sta  (ptr),y

    val mods = mutableListOf<Modification>()
    for (lines in linesByFourteen) {
        val first = lines[0].value.trimStart()
        val second = lines[1].value.trimStart()
        val third = lines[2].value.trimStart()
        val fourth = lines[3].value.trimStart()
        val fifth = lines[4].value.trimStart()
        val sixth = lines[5].value.trimStart()

        if(first.startsWith("ldy") && second.startsWith("lda") && fourth.startsWith("ldy") && fifth.startsWith("sta")) {
            val firstvalue = first.substring(4)
            val secondvalue = second.substring(4)
            val fourthvalue = fourth.substring(4)
            val fifthvalue = fifth.substring(4)
            if("y" !in third && firstvalue==fourthvalue && secondvalue==fifthvalue && secondvalue.endsWith(",y") && fifthvalue.endsWith(",y")) {
                mods.add(Modification(lines[3].index, true, null))
            }
        }
        if(first.startsWith("ldy") && second.startsWith("lda") && fifth.startsWith("ldy") && sixth.startsWith("sta")) {
            val firstvalue = first.substring(4)
            val secondvalue = second.substring(4)
            val fifthvalue = fifth.substring(4)
            val sixthvalue = sixth.substring(4)
            if("y" !in third && "y" !in fourth && firstvalue==fifthvalue && secondvalue==sixthvalue && secondvalue.endsWith(",y") && sixthvalue.endsWith(",y")) {
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
        if(first=="beq  +" && second=="lda  #1" && third=="+") {
            if((fourth.startsWith("beq  $autoLabelPrefix") || fourth.startsWith("bne  $autoLabelPrefix")) &&
                (fourth.endsWith("_shortcut") || fourth.endsWith("_afterif") || fourth.endsWith("_shortcut:") || fourth.endsWith("_afterif:"))) {
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, true, null))
                mods.add(Modification(lines[2].index, true, null))
            }
            else if(fourth.startsWith(autoLabelPrefix) && (fourth.endsWith("_shortcut") || fourth.endsWith("_shortcut:"))) {
                if((fifth.startsWith("beq  $autoLabelPrefix") || fifth.startsWith("bne  $autoLabelPrefix")) &&
                    (fifth.endsWith("_shortcut") || fifth.endsWith("_afterif") || fifth.endsWith("_shortcut:") || fifth.endsWith("_afterif:"))) {
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
    linesByFour: Sequence<List<IndexedValue<String>>>,
    machine: ICompilationTarget,
    symbolTable: SymbolTable
): List<Modification> {
    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val first = lines[1].value.trimStart()
        val second = lines[2].value.trimStart()
        val third = lines[3].value.trimStart()

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
                    val loadinstruction = second.substring(0, 3)
                    lines[0].value.trimStart().startsWith(loadinstruction)
                }
                else {
                    // no branch instruction follows, we can remove the load instruction
                    val address = getAddressArg(lines[2].value, symbolTable)
                    address==null || !machine.isIOAddress(address)
                }

            if(attemptRemove) {
                val firstLoc = first.substring(4).trimStart()
                val secondLoc = second.substring(4).trimStart()
                if (firstLoc == secondLoc)
                    mods.add(Modification(lines[2].index, true, null))
            }
        }
        else if(first=="pha" && second=="pla" ||
            first=="phx" && second=="plx" ||
            first=="phy" && second=="ply" ||
            first=="php" && second=="plp") {
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
        if ((first.startsWith("lda ") && second.startsWith("sta ")) ||
            (first.startsWith("ldx ") && second.startsWith("stx ")) ||
            (first.startsWith("ldy ") && second.startsWith("sty "))
        ) {
            val firstLoc = first.substring(4).trimStart()
            val secondLoc = second.substring(4).trimStart()
            if (firstLoc == secondLoc)
                mods.add(Modification(lines[2].index, true, null))
        }
    }
    return mods
}

private val identifierRegex = Regex("""^([a-zA-Z_$][a-zA-Z\d_.$]*)""")

private fun getAddressArg(line: String, symbolTable: SymbolTable): UInt? {
    // try to get the constant value address, could return null if it's a symbol instead
    val loadArg = line.trimStart().substring(3).trim()
    return when {
        loadArg.startsWith('$') -> loadArg.substring(1).toUIntOrNull(16)
        loadArg.startsWith('%') -> loadArg.substring(1).toUIntOrNull(2)
        loadArg.startsWith('#') -> null
        loadArg.startsWith('(') -> null
        loadArg[0].isLetter() -> {
            val identMatch = identifierRegex.find(loadArg)
            if(identMatch!=null) {
                val identifier = identMatch.value
                when (val symbol = symbolTable.flat[identifier]) {
                    is StConstant -> symbol.value.toUInt()
                    is StMemVar -> symbol.address.toUInt()
                    else -> null
                }
            } else null
        }
        else -> loadArg.substring(1).toUIntOrNull()
    }
}

private fun optimizeIncDec(linesByFour: Sequence<List<IndexedValue<String>>>): List<Modification> {
    // sometimes, iny+dey / inx+dex / dey+iny / dex+inx sequences are generated, these can be eliminated.
    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val first = lines[0].value
        val second = lines[1].value
        if ((" iny" in first || "\tiny" in first) && (" dey" in second || "\tdey" in second)
                || (" inx" in first || "\tinx" in first) && (" dex" in second || "\tdex" in second)
                || (" ina" in first || "\tina" in first) && (" dea" in second || "\tdea" in second)
                || (" inc  a" in first || "\tinc  a" in first) && (" dec  a" in second || "\tdec  a" in second)
                || (" dey" in first || "\tdey" in first) && (" iny" in second || "\tiny" in second)
                || (" dex" in first || "\tdex" in first) && (" inx" in second || "\tinx" in second)
                || (" dea" in first || "\tdea" in first) && (" ina" in second || "\tina" in second)
                || (" dec  a" in first || "\tdec  a" in first) && (" inc  a" in second || "\tinc  a" in second)) {
            mods.add(Modification(lines[0].index, true, null))
            mods.add(Modification(lines[1].index, true, null))
        }

    }
    return mods
}

private fun optimizeJsrRtsAndOtherCombinations(linesByFour: Sequence<List<IndexedValue<String>>>): List<Modification> {
    // jsr Sub + rts -> jmp Sub
    // jmp Sub + rts -> jmp Sub
    // rts + jmp -> remove jmp
    // rts + bxx -> remove bxx
    // lda  + cmp #0 -> remove cmp,  same for cpy and cpx.
    // and some other optimizations.

    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val first = lines[0].value
        val second = lines[1].value
        val third = lines[2].value

        if(!haslabel(second)) {
            if ((" jmp" in first || "\tjmp" in first ) && (" rts" in second || "\trts" in second)) {
                mods += Modification(lines[1].index, true, null)
            }
            else if ((" jsr" in first || "\tjsr" in first ) && (" rts" in second || "\trts" in second)) {
                if("floats.pushFAC" !in first && "floats.popFAC" !in first) {       // these 2 routines depend on being called with JSR!!
                    mods += Modification(lines[0].index, false, lines[0].value.replace("jsr", "jmp"))
                    mods += Modification(lines[1].index, true, null)
                }
            }
            else if (" rts" in first || "\trts" in first) {
                if (" jmp" in second || "\tjmp" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bra" in second || "\tbra" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bcc" in second || "\tbcc" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bcs" in second || "\tbcs" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" beq" in second || "\tbeq" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bne" in second || "\tbne" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bmi" in second || "\tbmi" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bpl" in second || "\tbpl" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bvs" in second || "\tbvs" in second)
                    mods += Modification(lines[1].index, true, null)
                else if (" bvc" in second || "\tbvc" in second)
                    mods += Modification(lines[1].index, true, null)
            }

            if ((" lda" in first || "\tlda" in first) && (" cmp  #0" in second || "\tcmp  #0" in second) ||
                (" ldx" in first || "\tldx" in first) && (" cpx  #0" in second || "\tcpx  #0" in second) ||
                (" ldy" in first || "\tldy" in first) && (" cpy  #0" in second || "\tcpy  #0" in second)
            ) {
                mods.add(Modification(lines[1].index, true, null))
            }
            else if(" cmp  #0" in second || "\tcmp  #0" in second) {
                // there are many instructions that modify A and set the bits...
                for(instr in arrayOf("lda", "ora", "and", "eor", "adc", "sbc", "asl", "cmp", "inc  a", "lsr", "pla", "rol", "ror", "txa", "tya")) {
                    if(" $instr" in first || "\t$instr" in first) {
                        mods.add(Modification(lines[1].index, true, null))
                    }
                }
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
        val tfirst = first.trimStart()
        val tsecond = second.trimStart()
        val tthird = lines[2].value.trimStart()
        val tfourth = lines[3].value.trimStart()
        if(tfirst.startsWith("lda") && tsecond.startsWith("cmp") && tthird.startsWith("bcc") && tfourth.startsWith("beq")) {
            val label = tthird.substring(4)
            if(label==tfourth.substring(4)) {
                mods += Modification(lines[0].index, false, "  lda  ${tsecond.substring(4)}")
                mods += Modification(lines[1].index, false, "  cmp  ${tfirst.substring(4)}")
                mods += Modification(lines[2].index, false, "  bcs  $label")
                mods += Modification(lines[3].index, true, null)
            }
        }


        fun sameLabel(branchInstr: String, jumpInstr: String, labelInstr: String): Boolean {
            if('(' in jumpInstr) return false       // indirect jump cannot be replaced
            val label = labelInstr.trimEnd().substringBefore(':').substringBefore(' ').substringBefore('\t')
            val branchLabel = branchInstr.trimStart().substring(3).trim()
            return label==branchLabel
        }

        // beq Label + jmp Addr + Label  -> bne Addr
        if((" jmp" in second || "\tjmp " in second) && haslabel(third)) {
            if((" beq " in first || "\tbeq " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bne")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bne " in first || "\tbne " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "beq")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bcc " in first || "\tbcc " in first) && sameLabel(first, second, third)){
                val branch = second.replace("jmp", "bcs")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bcs " in first || "\tbcs " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bcc")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bpl " in first || "\tbpl " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bmi")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bmi " in first || "\tbmi " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bpl")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bvc " in first || "\tbvc " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bvs")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
            else if((" bvs " in first || "\tbvs " in first) && sameLabel(first, second, third)) {
                val branch = second.replace("jmp", "bvc")
                mods.add(Modification(lines[0].index, true, null))
                mods.add(Modification(lines[1].index, false, branch))
            }
        }
    }
    return mods
}

private fun optimizeUselessPushPopStack(linesByFour: Sequence<List<IndexedValue<String>>>): List<Modification> {
    val mods = mutableListOf<Modification>()

    fun optimize(register: Char, lines: List<IndexedValue<String>>) {
        if(lines[0].value.trimStart().startsWith("ph$register")) {
            if(lines[2].value.trimStart().startsWith("pl$register")) {
                val second = lines[1].value.trimStart().take(6).lowercase()
                if(register!in second
                    && !second.startsWith("jsr")
                    && !second.startsWith("pl")
                    && !second.startsWith("ph")) {
                    mods.add(Modification(lines[0].index, true, null))
                    mods.add(Modification(lines[2].index, true, null))
                }
            }
            else if (lines[3].value.trimStart().startsWith("pl$register")) {
                val second = lines[1].value.trimStart().take(6).lowercase()
                val third = lines[2].value.trimStart().take(6).lowercase()
                if(register !in second && register !in third
                    && !second.startsWith("jsr") && !third.startsWith("jsr")
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

        val first = lines[1].value.trimStart()
        val second = lines[2].value.trimStart()
        val third = lines[3].value.trimStart()

        // phy + ldy + pla -> tya + ldy
        // phx + ldx + pla -> txa + ldx
        // pha + lda + pla -> nop
        when (first) {
            "phy" if second.startsWith("ldy ") && third=="pla" -> {
                mods.add(Modification(lines[3].index, true, null))
                mods.add(Modification(lines[1].index, false, "  tya"))
            }
            "phx" if second.startsWith("ldx ") && third=="pla" -> {
                mods.add(Modification(lines[3].index, true, null))
                mods.add(Modification(lines[1].index, false, "  txa"))
            }
            "pha" if second.startsWith("lda ") && third=="pla" -> {
                mods.add(Modification(lines[1].index, true, null))
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[3].index, true, null))
            }
        }
    }


    return mods
}


private fun optimizeTSBtoRegularOr(linesByFour: Sequence<List<IndexedValue<String>>>): List<Modification> {
    // Asm peephole:   lda var2 / tsb var1 / lda var1  Replace this with this to save 1 cycle:   lda var1 / ora var2 / sta var1
    val mods = mutableListOf<Modification>()

    for(lines in linesByFour) {
        val first = lines[0].value.trimStart()
        val second = lines[1].value.trimStart()
        val third = lines[2].value.trimStart()
        if(first.startsWith("lda") && second.startsWith("tsb") && third.startsWith("lda")) {
            val operand1 = first.substring(3)
            val operand2 = second.substring(3)
            val operand3 = third.substring(3)
            if(operand1!=operand2 && operand2==operand3) {
                mods.add(Modification(lines[0].index, false, "  lda  $operand2"))
                mods.add(Modification(lines[1].index, false, "  ora  $operand1"))
                mods.add(Modification(lines[2].index, false, "  sta  $operand2"))
            }
        }
    }
    return mods
}

private fun optimizeUnneededTempvarInAdd(linesByFour: Sequence<List<IndexedValue<String>>>): List<Modification> {
    // sequence:  sta  P8ZP_SCRATCH_XX  / lda  something / clc / adc  P8ZP_SCRATCH_XX
    // this can be performed without the scratch variable:  clc  /  adc  something
    val mods = mutableListOf<Modification>()

    for(lines in linesByFour) {
        val first = lines[0].value.trimStart()
        val second = lines[1].value.trimStart()
        val third = lines[2].value.trimStart()
        val fourth = lines[3].value.trimStart()
        if(first.startsWith("sta  P8ZP_SCRATCH_") && second.startsWith("lda") && third.startsWith("clc") && fourth.startsWith("adc  P8ZP_SCRATCH_") ) {
            if(fourth.substring(4)==first.substring(4)) {
                mods.add(Modification(lines[0].index, false, "  clc"))
                mods.add(Modification(lines[1].index, false, "  adc  ${second.substring(3).trimStart()}"))
                mods.add(Modification(lines[2].index, true, null))
                mods.add(Modification(lines[3].index, true, null))
            }
        }
    }

    return mods
}
