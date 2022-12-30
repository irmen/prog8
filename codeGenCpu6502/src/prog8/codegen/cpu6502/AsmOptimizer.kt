package prog8.codegen.cpu6502

import prog8.ast.Program
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.code.core.IMachineDefinition


// note: see https://wiki.nesdev.org/w/index.php/6502_assembly_optimisations


internal fun optimizeAssembly(lines: MutableList<String>, machine: IMachineDefinition, program: Program): Int {

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

    mods = optimizeStoreLoadSame(linesByFour, machine, program)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    mods= optimizeJsrRtsAndOtherCombinations(linesByFour)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFour = getLinesBy(lines, 4)
        numberOfOptimizations++
    }

    var linesByFourteen = getLinesBy(lines, 14)
    mods = optimizeSameAssignments(linesByFourteen, machine, program)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFourteen = getLinesBy(lines, 14)
        numberOfOptimizations++
    }

    mods = optimizeSamePointerIndexing(linesByFourteen, machine, program)
    if(mods.isNotEmpty()) {
        apply(mods, lines)
        linesByFourteen = getLinesBy(lines, 14)
        numberOfOptimizations++
    }

    // TODO more assembly peephole optimizations

    return numberOfOptimizations
}

private fun String.isBranch() = this.startsWith("b")
private fun String.isStoreReg() = this.startsWith("sta") || this.startsWith("sty") || this.startsWith("stx")
private fun String.isStoreRegOrZero() = this.isStoreReg() || this.startsWith("stz")
private fun String.isLoadReg() = this.startsWith("lda") || this.startsWith("ldy") || this.startsWith("ldx")

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
    // when statement (on bytes) generates a sequence of:
    //	 lda  $ce01,x
    //	 cmp  #$20
    //	 beq  check_prog8_s72choice_32
    //	 lda  $ce01,x
    //	 cmp  #$21
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

private fun optimizeSameAssignments(linesByFourteen: List<List<IndexedValue<String>>>, machine: IMachineDefinition, program: Program): List<Modification> {

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
                val address1 = getAddressArg(first, program)
                val address2 = getAddressArg(second, program)
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
                val address = getAddressArg(first, program)
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
                    val address = getAddressArg(first, program)
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
                    val address = getAddressArg(first, program)
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
                val address = getAddressArg(first, program)
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
         */
        if(!overlappingMods && first.isStoreRegOrZero() && second.isStoreRegOrZero()) {
            if(first[2]==second[2]) {
                val firstvalue = first.substring(4)
                val secondvalue = second.substring(4)
                if(firstvalue==secondvalue) {
                    val address = getAddressArg(first, program)
                    if(address==null || !machine.isIOAddress(address)) {
                        overlappingMods = true
                        mods.add(Modification(lines[0].index, true, null))
                    }
                }
            }
        }
    }

    return mods
}

private fun optimizeSamePointerIndexing(linesByFourteen: List<List<IndexedValue<String>>>, machine: IMachineDefinition, program: Program): List<Modification> {

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
    }

    return mods
}

private fun optimizeStoreLoadSame(linesByFour: List<List<IndexedValue<String>>>, machine: IMachineDefinition, program: Program): List<Modification> {
    // sta X + lda X,  sty X + ldy X,   stx X + ldx X  -> the second instruction can OFTEN be eliminated
    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val first = lines[1].value.trimStart()
        val second = lines[2].value.trimStart()

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
            val third = lines[3].value.trimStart()
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
                    val address = getAddressArg(lines[2].value, program)
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
        } else if(first=="phx" && second=="ply") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  txy"))
        } else if(first=="phy" && second=="pla") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  tya"))
        } else if(first=="phy" && second=="plx") {
            mods.add(Modification(lines[1].index, true, null))
            mods.add(Modification(lines[2].index, false, "  tyx"))
        }
    }
    return mods
}

private val identifierRegex = Regex("""^([a-zA-Z_$][a-zA-Z\d_\.$]*)""")

private fun getAddressArg(line: String, program: Program): UInt? {
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
                val decl = program.toplevelModule.lookup(identifier.split('.')) as? VarDecl
                if(decl!=null) {
                    when(decl.type){
                        VarDeclType.VAR -> null
                        VarDeclType.CONST,
                        VarDeclType.MEMORY -> (decl.value as NumericLiteral).number.toUInt()
                    }
                }
                else null
            } else null
        }
        else -> loadArg.substring(1).toUIntOrNull()
    }
}

private fun optimizeIncDec(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
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

private fun optimizeJsrRtsAndOtherCombinations(linesByFour: List<List<IndexedValue<String>>>): List<Modification> {
    // jsr Sub + rts -> jmp Sub
    // rts + jmp -> remove jmp
    // rts + bxx -> remove bxx

    val mods = mutableListOf<Modification>()
    for (lines in linesByFour) {
        val first = lines[0].value
        val second = lines[1].value
        if ((" jsr" in first || "\tjsr" in first ) && (" rts" in second || "\trts" in second)) {
            mods += Modification(lines[0].index, false, lines[0].value.replace("jsr", "jmp"))
            mods += Modification(lines[1].index, true, null)
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
    }
    return mods
}
