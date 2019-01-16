package prog8.compiler.target.c64

fun optimizeAssembly(lines: MutableList<String>): Int {

    var numberOfOptimizations = 0

    var linesByTwo = getLinesBy(lines, 2)

    var removeLines = optimizeIncDec(linesByTwo)
    if(removeLines.isNotEmpty()) {
        for (i in removeLines.reversed())
            lines.removeAt(i)
        linesByTwo = getLinesBy(lines, 2)
        numberOfOptimizations++
    }

    removeLines = optimizeStoreLoadSame(linesByTwo)

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

    return numberOfOptimizations
}

fun optimizeSameAssignments(linesByFourteen: List<List<IndexedValue<String>>>): List<Int> {

    // optimize sequential assignments of the same value to various targets (bytes, words, floats)
    // the float one is the one that requires 2*7=14 lines of code to check...

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

        // @todo check float initializations.
    }
    return removeLines
}

private fun getLinesBy(lines: MutableList<String>, windowSize: Int) =
// all lines (that aren't empty or comments) in sliding pairs of 2
        lines.withIndex().filter { it.value.isNotBlank() && !it.value.trimStart().startsWith(';') }.windowed(windowSize, partialWindows = false)

private fun optimizeStoreLoadSame(linesByTwo: List<List<IndexedValue<String>>>): List<Int> {
    // sta X + lda X,  sty X + ldy X,   stx X + ldx X  -> the second instruction can be eliminated
    val removeLines = mutableListOf<Int>()
    for (pair in linesByTwo) {
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
