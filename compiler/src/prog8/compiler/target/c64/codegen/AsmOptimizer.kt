package prog8.compiler.target.c64.codegen


// note: see https://wiki.nesdev.com/w/index.php/6502_assembly_optimisations


fun optimizeAssembly(lines: MutableList<String>): Int {

    var numberOfOptimizations = 0

    var linesByFour = getLinesBy(lines, 4)

    var mods = optimizeUselessStackByteWrites(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeIncDec(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeCmpSequence(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods = optimizeStoreLoadSame(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods= optimizeJsrRts(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    var linesByFourteen = getLinesBy(lines, 14)
    mods = optimizeSameAssignments(linesByFourteen)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFourteen = getLinesBy(lines, 14)
        numberOfOptimizations++
    }

    // TODO more assembly optimizations

    return numberOfOptimizations
}

private class Modification(val lineIndex: Int, val remove: Boolean, val replacement: String?)

private fun apply(modifications: List<Modification>, lines: MutableList<String>) {
    for (modification in modifications.sortedBy { it.lineIndex }.reversed()) {
        if(modification.remove)
            lines.removeAt(modification.lineIndex)
        else
            lines[modification.lineIndex] = modification.replacement!!
    }
}

private fun getLinesBy(lines: MutableList<String>, windowSize: Int) =
// all lines (that aren't empty or comments) in sliding windows of certain size
        lines.withIndex().filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }.windowed(windowSize, partialWindows = false)

private fun optimizeCmpSequence(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // the when statement (on bytes) generates a sequence of:
    //	 lda $ce01,x
    //	 cmp #$20
    //	 beq  check_prog8_s72choice_32
    //	 lda $ce01,x
    //	 cmp #$21
    //	 beq  check_prog8_s73choice_33
    // the repeated lda can be removed
    val mods = mutableListOf<Modification>()
    for(lines in linesByFour) {
        if(lines[0].value.trim()=="lda  P8ESTACK_LO+1,x" &&
                lines[1].value.trim().startsWith("cmp ") &&
                lines[2].value.trim().startsWith("beq ") &&
                lines[3].value.trim()=="lda  P8ESTACK_LO+1,x") {
            mods.add(Modification(lines[3].index, true, null)) // remove the second lda
        }
    }
    return mods
}

private fun optimizeUselessStackByteWrites(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // sta on stack, dex, inx, lda from stack -> eliminate this useless stack byte write
    // this is a lot harder for word values because the instruction sequence varies.
    val mods = mutableListOf<Modification>()
    for(lines in linesByFour) {
        if(lines[0].value.trim()=="sta  P8ESTACK_LO,x" &&
                lines[1].value.trim()=="dex" &&
                lines[2].value.trim()=="inx" &&
                lines[3].value.trim()=="lda  P8ESTACK_LO,x") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, true, null))
            mods.add(Modification(lines[3].index, true, null))
        }
    }
    return mods
}

private fun optimizeSameAssignments(linesByFourteen: List<List<IndexedValue<String>>>): List<Modification> {

    // optimize sequential assignments of the isSameAs value to various targets (bytes, words, floats)
    // the float one is the one that requires 2*7=14 lines of code to check...
    // @todo a better place to do this is in the Compiler instead and transform the Ast, or the AsmGen, and never even create the inefficient asm in the first place...

    val mods = mutableListOf<Modification>()
    for (pair in linesByFourteen) {
        val first = pair[0].value.trimStart()
        val second = pair[1].value.trimStart()
        val third = pair[2].value.trimStart()
        val fourth = pair[3].value.trimStart()
        val fifth = pair[4].value.trimStart()
        val sixth = pair[5].value.trimStart()
        val seventh = pair[6].value.trimStart()
        val eighth = pair[7].value.trimStart()

        if(first.startsWith("lda") && second.startsWith("ldy") && third.startsWith("sta") && fourth.startsWith("sty") &&
                fifth.startsWith("lda") && sixth.startsWith("ldy") && seventh.startsWith("sta") && eighth.startsWith("sty")) {
            val firstvalue = first.substring(4)
            val secondvalue = second.substring(4)
            val thirdvalue = fifth.substring(4)
            val fourthvalue = sixth.substring(4)
            if(firstvalue==thirdvalue && secondvalue==fourthvalue) {
                // lda/ldy   sta/sty   twice the isSameAs word -->  remove second lda/ldy pair (fifth and sixth lines)
                mods.add(Modification(pair[4].index, true, null))
                mods.add(Modification(pair[5].index, true, null))
            }
        }

        if(first.startsWith("lda") && second.startsWith("sta") && third.startsWith("lda") && fourth.startsWith("sta")) {
            val firstvalue = first.substring(4)
            val secondvalue = third.substring(4)
            if(firstvalue==secondvalue) {
                // lda value / sta ? / lda isSameAs-value / sta ?  -> remove second lda (third line)
                mods.add(Modification(pair[2].index, true, null))
            }
        }

        if(first.startsWith("lda") && second.startsWith("ldy") && third.startsWith("sta") && fourth.startsWith("sty") &&
                fifth.startsWith("lda") && sixth.startsWith("ldy") &&
                (seventh.startsWith("jsr  floats.copy_float") || seventh.startsWith("jsr  cx16flt.copy_float"))) {

            val nineth = pair[8].value.trimStart()
            val tenth = pair[9].value.trimStart()
            val eleventh = pair[10].value.trimStart()
            val twelveth = pair[11].value.trimStart()
            val thirteenth = pair[12].value.trimStart()
            val fourteenth = pair[13].value.trimStart()

            if(eighth.startsWith("lda") && nineth.startsWith("ldy") && tenth.startsWith("sta") && eleventh.startsWith("sty") &&
                    twelveth.startsWith("lda") && thirteenth.startsWith("ldy") &&
                    (fourteenth.startsWith("jsr  floats.copy_float") || fourteenth.startsWith("jsr  cx16flt.copy_float"))) {

                if(first.substring(4) == eighth.substring(4) && second.substring(4)==nineth.substring(4)) {
                    // identical float init
                    mods.add(Modification(pair[7].index, true, null))
                    mods.add(Modification(pair[8].index, true, null))
                    mods.add(Modification(pair[9].index, true, null))
                    mods.add(Modification(pair[10].index, true, null))
                }
            }
        }
    }
    return mods
}

private fun optimizeStoreLoadSame(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // sta X + lda X,  sty X + ldy X,   stx X + ldx X  -> the second instruction can be eliminated
    // TODO this is not true if X is not a regular RAM memory address (but instead mapped I/O or ROM)
    val mods = mutableListOf<Modification>()
    for (pair in linesByFour) {
        val first = pair[0].value.trimStart()
        val second = pair[1].value.trimStart()

        if ((first.startsWith("sta ") && second.startsWith("lda ")) ||
                (first.startsWith("stx ") && second.startsWith("ldx ")) ||
                (first.startsWith("sty ") && second.startsWith("ldy ")) ||
                (first.startsWith("lda ") && second.startsWith("lda ")) ||
                (first.startsWith("ldy ") && second.startsWith("ldy ")) ||
                (first.startsWith("ldx ") && second.startsWith("ldx ")) ||
                (first.startsWith("sta ") && second.startsWith("lda ")) ||
                (first.startsWith("sty ") && second.startsWith("ldy ")) ||
                (first.startsWith("stx ") && second.startsWith("ldx "))
        ) {
            val firstLoc = first.substring(4).trimStart()
            val secondLoc = second.substring(4).trimStart()
            if (firstLoc == secondLoc) {
                mods.add(Modification(pair[1].index, true, null))
            }
        }
    }
    return mods
}

private fun optimizeIncDec(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // sometimes, iny+dey / inx+dex / dey+iny / dex+inx sequences are generated, these can be eliminated.
    val mods = mutableListOf<Modification>()
    for (pair in linesByFour) {
        val first = pair[0].value
        val second = pair[1].value
        if ((" iny" in first || "\tiny" in first) && (" dey" in second || "\tdey" in second)
                || (" inx" in first || "\tinx" in first) && (" dex" in second || "\tdex" in second)
                || (" dey" in first || "\tdey" in first) && (" iny" in second || "\tiny" in second)
                || (" dex" in first || "\tdex" in first) && (" inx" in second || "\tinx" in second)) {
            mods.add(Modification(pair[0].index, true, null))
            mods.add(Modification(pair[1].index, true, null))
        }
    }
    return mods
}

private fun optimizeJsrRts(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // jsr Sub + rts -> jmp Sub
    val mods = mutableListOf<Modification>()
    for (pair in linesByFour) {
        val first = pair[0].value
        val second = pair[1].value
        if ((" jsr" in first || "\tjsr" in first ) && (" rts" in second || "\trts" in second)) {
            mods += Modification(pair[0].index, false, pair[0].value.replace("jsr", "jmp"))
            mods += Modification(pair[1].index, true, null)
        }
    }
    return mods
}
