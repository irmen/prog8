package prog8.compiler.target.c64

import prog8.compiler.toHex

fun optimizeAssembly(lines: MutableList<String>): Int {

    var numberOfOptimizations = 0

    var linesByFour = getLinesBy(lines, 4)

    var removeLines = optimizeUselessStackByteWrites(linesByFour)
    if(removeLines.isNotEmpty()) {
        for (i in removeLines.reversed())
            lines.removeAt(i)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    removeLines = optimizeIncDec(linesByFour)
    if(removeLines.isNotEmpty()) {
        for (i in removeLines.reversed())
            lines.removeAt(i)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    removeLines = optimizeStoreLoadSame(linesByFour)
    if(removeLines.isNotEmpty()) {
        for (i in removeLines.reversed())
            lines.removeAt(i)
        numberOfOptimizations++
    }

    var linesByFourteen = getLinesBy(lines, 14)
    removeLines = optimizeSameAssignments(linesByFourteen)
    if(removeLines.isNotEmpty()) {
        for (i in removeLines.reversed())
            lines.removeAt(i)
        numberOfOptimizations++
    }

    // TODO more assembly optimizations?

    return numberOfOptimizations
}

fun optimizeUselessStackByteWrites(linesByFour: List<List<IndexedValue<String>>>): List<Int> {
    // sta on stack, dex, inx, lda from stack -> eliminate this useless stack byte write
    // this is a lot harder for word values because the instruction sequence varies.
    val removeLines = mutableListOf<Int>()
    for(lines in linesByFour) {
        if(lines[0].value.trim()=="sta  ${ESTACK_LO.toHex()},x" &&
                lines[1].value.trim()=="dex" &&
                lines[2].value.trim()=="inx" &&
                lines[3].value.trim()=="lda  ${ESTACK_LO.toHex()},x") {
            removeLines.add(lines[0].index)
            removeLines.add(lines[1].index)
            removeLines.add(lines[2].index)
            removeLines.add(lines[3].index)
        }
    }
    return removeLines
}

fun optimizeSameAssignments(linesByFourteen: List<List<IndexedValue<String>>>): List<Int> {

    // optimize sequential assignments of the same value to various targets (bytes, words, floats)
    // the float one is the one that requires 2*7=14 lines of code to check...
    // @todo a better place to do this is in the Compiler instead and work on opcodes, and never even create the inefficient asm...

    val removeLines = mutableListOf<Int>()
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
                // lda/ldy   sta/sty   twice the same word -->  remove second lda/ldy pair (fifth and sixth lines)
                removeLines.add(pair[4].index)
                removeLines.add(pair[5].index)
            }
        }

        if(first.startsWith("lda") && second.startsWith("sta") && third.startsWith("lda") && fourth.startsWith("sta")) {
            val firstvalue = first.substring(4)
            val secondvalue = third.substring(4)
            if(firstvalue==secondvalue) {
                // lda value / sta ? / lda same-value / sta ?  -> remove second lda (third line)
                removeLines.add(pair[2].index)
            }
        }

        if(first.startsWith("lda") && second.startsWith("ldy") && third.startsWith("sta") && fourth.startsWith("sty") &&
                fifth.startsWith("lda") && sixth.startsWith("ldy") && seventh.startsWith("jsr  c64flt.copy_float")) {

            val nineth = pair[8].value.trimStart()
            val tenth = pair[9].value.trimStart()
            val eleventh = pair[10].value.trimStart()
            val twelveth = pair[11].value.trimStart()
            val thirteenth = pair[12].value.trimStart()
            val fourteenth = pair[13].value.trimStart()

            if(eighth.startsWith("lda") && nineth.startsWith("ldy") && tenth.startsWith("sta") && eleventh.startsWith("sty") &&
                    twelveth.startsWith("lda") && thirteenth.startsWith("ldy") && fourteenth.startsWith("jsr  c64flt.copy_float")) {

                if(first.substring(4) == eighth.substring(4) && second.substring(4)==nineth.substring(4)) {
                    // identical float init
                    removeLines.add(pair[7].index)
                    removeLines.add(pair[8].index)
                    removeLines.add(pair[9].index)
                    removeLines.add(pair[10].index)
                }
            }
        }
    }
    return removeLines
}

private fun getLinesBy(lines: MutableList<String>, windowSize: Int) =
// all lines (that aren't empty or comments) in sliding pairs of 2
        lines.withIndex().filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }.windowed(windowSize, partialWindows = false)

private fun optimizeStoreLoadSame(linesByFour: List<List<IndexedValue<String>>>): List<Int> {
    // sta X + lda X,  sty X + ldy X,   stx X + ldx X  -> the second instruction can be eliminated
    val removeLines = mutableListOf<Int>()
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
            val firstLoc = first.substring(4)
            val secondLoc = second.substring(4)
            if (firstLoc == secondLoc) {
                removeLines.add(pair[1].index)
            }
        }
    }
    return removeLines
}

private fun optimizeIncDec(linesByTwo: List<List<IndexedValue<String>>>): List<Int> {
    // sometimes, iny+dey / inx+dex / dey+iny / dex+inx sequences are generated, these can be eliminated.
    val removeLines = mutableListOf<Int>()
    for (pair in linesByTwo) {
        val first = pair[0].value
        val second = pair[1].value
        if ((" iny" in first || "\tiny" in first) && (" dey" in second || "\tdey" in second)
                || (" inx" in first || "\tinx" in first) && (" dex" in second || "\tdex" in second)
                || (" dey" in first || "\tdey" in first) && (" iny" in second || "\tiny" in second)
                || (" dex" in first || "\tdex" in first) && (" inx" in second || "\tinx" in second)) {
            removeLines.add(pair[0].index)
            removeLines.add(pair[1].index)
        }
    }
    return removeLines
}
