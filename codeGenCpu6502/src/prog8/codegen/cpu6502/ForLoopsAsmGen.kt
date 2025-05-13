package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.code.StMemVar
import prog8.code.StStaticVariable
import prog8.code.ast.PtForLoop
import prog8.code.ast.PtIdentifier
import prog8.code.ast.PtRange
import prog8.code.core.*
import kotlin.math.absoluteValue

internal class ForLoopsAsmGen(
    private val asmgen: AsmGen6502Internal,
    private val zeropage: Zeropage
) {

    internal fun translate(stmt: PtForLoop) {
        val iterableDt = stmt.iterable.type
        when(stmt.iterable) {
            is PtRange -> {
                val range = (stmt.iterable as PtRange).toConstantIntegerRange()
                if(range==null) {
                    translateForOverNonconstRange(stmt, iterableDt, stmt.iterable as PtRange)
                } else {
                    translateForOverConstRange(stmt, iterableDt, range)
                }
            }
            is PtIdentifier -> {
                translateForOverIterableVar(stmt, iterableDt, stmt.iterable as PtIdentifier)
            }
            else -> throw AssemblyError("can't iterate over ${stmt.iterable.javaClass} - should have been replaced by a variable")
        }
    }

    private fun translateForOverNonconstRange(stmt: PtForLoop, iterableDt: DataType, range: PtRange) {
        if(range.step.asConstInteger()!! < -1) {
            val limit = range.to.asConstInteger()
            if(limit==0)
                throw AssemblyError("for unsigned loop variable it's not possible to count down with step != -1 from a non-const value to exactly zero due to value wrapping")
        }

        when {
            iterableDt.isByteArray -> forOverNonconstByteRange(stmt, iterableDt, range)
            iterableDt.isWordArray && !iterableDt.isSplitWordArray -> forOverNonconstWordRange(stmt, iterableDt, range)
            else -> throw AssemblyError("range expression can only be byte or word")
        }

        asmgen.loopEndLabels.removeLast()
    }

    private fun forOverNonconstByteRange(stmt: PtForLoop, iterableDt: DataType, range: PtRange) {
        val stepsize = range.step.asConstInteger()!!
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val varname = asmgen.asmVariableName(stmt.variable)

        asmgen.assignExpressionToVariable(range.from, varname, iterableDt.elementType())
        when (stepsize) {
            -1 if range.to.asConstInteger()==0 -> {
                // simple loop downto 0 step -1
                asmgen.out(loopLabel)
                asmgen.translate(stmt.statements)
                asmgen.out("""
                    dec  $varname
                    lda  $varname
                    cmp  #255
                    bne  $loopLabel""")
            }
            -1 if range.to.asConstInteger()==1 -> {
                // simple loop downto 1 step -1
                asmgen.out(loopLabel)
                asmgen.translate(stmt.statements)
                asmgen.out("""
                    dec  $varname
                    bne  $loopLabel""")
            }
            1, -1 -> forOverBytesRangeStepOne(range, varname, iterableDt, loopLabel, endLabel, stmt)
            else -> forOverBytesRangeStepGreaterOne(range, varname, iterableDt, loopLabel, endLabel, stmt)
        }
    }

    private fun forOverBytesRangeStepOne(range: PtRange, varname: String, iterableDt: DataType, loopLabel: String, endLabel: String, forloop: PtForLoop) {
        // bytes range, step 1 or -1

        val stepsize = range.step.asConstInteger()!!
        val incdec = if(stepsize==1) "inc" else "dec"

        if(asmgen.options.romable) {
            // cannot use self-modifying code, cannot use cpu stack (because loop can be interrupted halfway)
            // so we need to store the loop end value in a newly allocated temporary variable
            val toValueVar = asmgen.createTempVarReused(iterableDt.elementType().base, false, range)
            asmgen.assignExpressionToRegister(range.to, RegisterOrPair.A)
            asmgen.out("  sta  $toValueVar")
            // pre-check for end already reached
            if(iterableDt.isSignedByteArray) {
                if(stepsize<0) {
                    asmgen.out("""
                        clc
                        sbc  $varname
                        bvc  +
                        eor  #$80
+                       bpl  $endLabel""")
                }
                else {
                    asmgen.out("""
                        sec
                        sbc  $varname
                        bvc  +
                        eor  #$80
+                       bmi  $endLabel""")
                }
            } else {
                if(stepsize<0) {
                    asmgen.out("""
                        cmp  $varname
                        beq  +
                        bcs  $endLabel
+""")
                }
                else {
                    asmgen.out("  cmp  $varname  |  bcc  $endLabel")
                }
            }
            asmgen.out(loopLabel)
            asmgen.translate(forloop.statements)
            asmgen.out("""
                lda  $varname
                cmp  $toValueVar
                beq  $endLabel
                $incdec  $varname""")
            asmgen.jmp(loopLabel)
            asmgen.out(endLabel)

        } else {

            // use self-modifying code to store the loop end comparison value
            val modifiedLabel = asmgen.makeLabel("for_modified")
            asmgen.assignExpressionToRegister(range.to, RegisterOrPair.A)
            // pre-check for end already reached
            if(iterableDt.isSignedByteArray) {
                asmgen.out("  sta  $modifiedLabel+1")
                if(stepsize<0) {
                    asmgen.out("""
                        clc
                        sbc  $varname
                        bvc  +
                        eor  #$80
+                       bpl  $endLabel""")
                }
                else
                    asmgen.out("""
                        sec
                        sbc  $varname
                        bvc  +
                        eor  #$80
+                       bmi  $endLabel""")
            } else {
                if(stepsize<0) {
                    asmgen.out("""
                        cmp  $varname
                        beq  +
                        bcs  $endLabel
+""")
                }
                else {
                    asmgen.out("  cmp  $varname  |  bcc  $endLabel")
                }
                asmgen.out("  sta  $modifiedLabel+1")
            }
            asmgen.out(loopLabel)
            asmgen.translate(forloop.statements)
            asmgen.out("""
                lda  $varname
$modifiedLabel  cmp  #0         ; modified 
                beq  $endLabel
                $incdec  $varname""")
            asmgen.jmp(loopLabel)
            asmgen.out(endLabel)
        }
    }

    private fun forOverBytesRangeStepGreaterOne(range: PtRange, varname: String, iterableDt: DataType, loopLabel: String, endLabel: String, forloop: PtForLoop) {
        // bytes range, step >= 2 or <= -2

        val stepsize = range.step.asConstInteger()!!

        val modifiedLabel = asmgen.makeLabel("for_modified")
        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.A)
        // pre-check for end already reached
        if(iterableDt.isSignedByteArray) {
            asmgen.out("  sta  $modifiedLabel+1")
            if(stepsize<0)
                asmgen.out("""
                    clc
                    sbc  $varname
                    bvc  +
                    eor  #$80
+                   bpl  $endLabel""")
            else
                asmgen.out("""
                    sec
                    sbc  $varname
                    bvc  +
                    eor  #$80
+                   bmi  $endLabel""")
        } else {
            if(stepsize<0)
                asmgen.out("""
                    cmp  $varname
                    beq  +
                    bcs  $endLabel
+""")
            else {
                asmgen.out("  cmp  $varname  |  bcc  $endLabel")
            }
            asmgen.out("  sta  $modifiedLabel+1")
        }
        asmgen.out(loopLabel)
        asmgen.translate(forloop.statements)

        asmgen.romableError("self-modifying code (forloop over bytes range)", forloop.position)  // TODO fix romable; there is self-modifying code below
        if(stepsize>0) {
            asmgen.out("""
                lda  $varname
                clc
                adc  #$stepsize
                sta  $varname
$modifiedLabel  cmp  #0    ; modified
                bmi  $loopLabel
                beq  $loopLabel""")
        } else {
            asmgen.out("""
                lda  $varname
                sec
                sbc  #${stepsize.absoluteValue}
                sta  $varname
$modifiedLabel  cmp  #0     ; modified
                bpl  $loopLabel""")
        }
        asmgen.out(endLabel)
    }

    private fun forOverNonconstWordRange(stmt: PtForLoop, iterableDt: DataType, range: PtRange) {
        val stepsize = range.step.asConstInteger()!!
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val varname = asmgen.asmVariableName(stmt.variable)
        assignLoopvarWord(stmt, range)
        if(stepsize==-1 && range.to.asConstInteger()==0) {
            // simple loop downto 0 step -1 (words)
            asmgen.out(loopLabel)
            asmgen.translate(stmt.statements)
            asmgen.out("""
                lda  $varname
                bne  ++
                lda  $varname+1
                beq  $endLabel
+               lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname""")
            asmgen.jmp(loopLabel)
            asmgen.out(endLabel)
        }
        else if (stepsize==-1 && range.to.asConstInteger()==1) {
            // simple loop downto 1 step -1 (words)
            asmgen.out(loopLabel)
            asmgen.translate(stmt.statements)
            asmgen.out("""
                lda  $varname
                cmp  #1
                bne  +
                lda  $varname+1
                beq  $endLabel
+               lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname""")
            asmgen.jmp(loopLabel)
            asmgen.out(endLabel)
        }
        else if (stepsize == 1 || stepsize == -1)
            forOverWordsRangeStepOne(range, varname, iterableDt, loopLabel, endLabel, stmt)
        else if (stepsize > 0)
            forOverWordsRangeStepGreaterOne(range, varname, iterableDt, loopLabel, endLabel, stmt)
        else
            forOverWordsRangeStepGreaterOneDescending(range, varname, iterableDt, loopLabel, endLabel, stmt)
    }

    private fun forOverWordsRangeStepOne(range: PtRange, varname: String, iterableDt: DataType, loopLabel: String, endLabel: String, forloop: PtForLoop) {
        // words range, step 1 or -1
        val stepsize = range.step.asConstInteger()!!

        if(asmgen.options.romable) {
            // cannot use self-modifying code, cannot use cpu stack (because loop can be interrupted halfway)
            // so we need to store the loop end value in a newly allocated temporary variable
            val toValueVar = asmgen.createTempVarReused(iterableDt.elementType().base, false, range)
            asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
            precheckFromToWord(iterableDt, stepsize, varname, endLabel)
            asmgen.out("  sta  $toValueVar")
            asmgen.out("  sty  $toValueVar+1")
            asmgen.out(loopLabel)
            asmgen.translate(forloop.statements)
            asmgen.out("""
                lda  $varname+1
                cmp  $toValueVar+1
                bne  +
                lda  $varname
                cmp  $toValueVar
                beq  $endLabel""")
            if(stepsize==1) {
                asmgen.out("""
+                   inc  $varname
                    bne  $loopLabel
                    inc  $varname+1""")
                asmgen.jmp(loopLabel)
            } else {
                asmgen.out("""
+                   lda  $varname
                    bne  +
                    dec  $varname+1
+                   dec  $varname""")
                asmgen.jmp(loopLabel)
            }
            asmgen.out(endLabel)

        } else {

            val modifiedLabel = asmgen.makeLabel("for_modified")
            val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
            asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
            precheckFromToWord(iterableDt, stepsize, varname, endLabel)
            asmgen.out("""
                sty  $modifiedLabel+1
                sta  $modifiedLabel2+1
$loopLabel""")
            asmgen.translate(forloop.statements)
            asmgen.out("""
                lda  $varname+1
$modifiedLabel  cmp  #0    ; modified 
                bne  +
                lda  $varname
$modifiedLabel2 cmp  #0    ; modified 
                beq  $endLabel""")
            if(stepsize==1) {
                asmgen.out("""
+                   inc  $varname
                    bne  $loopLabel
                    inc  $varname+1""")
                asmgen.jmp(loopLabel)
            } else {
                asmgen.out("""
+                   lda  $varname
                    bne  +
                    dec  $varname+1
+                   dec  $varname""")
                asmgen.jmp(loopLabel)
            }
            asmgen.out(endLabel)
        }
    }

    private fun forOverWordsRangeStepGreaterOne(range: PtRange, varname: String, iterableDt: DataType, loopLabel: String, endLabel: String, stmt: PtForLoop) {
        // (u)words, step >= 2
        val stepsize = range.step.asConstInteger()!!
        val modifiedLabel = asmgen.makeLabel("for_modified")
        val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
        precheckFromToWord(iterableDt, stepsize, varname, endLabel)
        asmgen.out("""
                sty  $modifiedLabel+1
                sta  $modifiedLabel2+1
$loopLabel""")
        asmgen.translate(stmt.statements)

        asmgen.romableError("self-modifying code (forloop over word range)", stmt.position)  // TODO fix romable; there is self-modifying code below
        if (iterableDt.isUnsignedWordArray) {
            asmgen.out("""
                lda  $varname
                clc
                adc  #<$stepsize
                sta  $varname
                lda  $varname+1
                adc  #>$stepsize
                sta  $varname+1
$modifiedLabel  cmp  #0     ; modified
                bcc  $loopLabel
                bne  $endLabel
$modifiedLabel2 lda  #0     ; modified
                cmp  $varname
                bcc  $endLabel
                bcs  $loopLabel
$endLabel""")
        } else {
            asmgen.out("""
                lda  $varname
                clc
                adc  #<$stepsize
                sta  $varname
                lda  $varname+1
                adc  #>$stepsize
                sta  $varname+1
$modifiedLabel2 lda  #0   ; modified
                cmp  $varname
$modifiedLabel  lda  #0   ; modified
                sbc  $varname+1
                bvc  +
                eor  #$80
+               bpl  $loopLabel                
$endLabel""")
        }
    }

    private fun forOverWordsRangeStepGreaterOneDescending(range: PtRange, varname: String, iterableDt: DataType, loopLabel: String, endLabel: String, stmt: PtForLoop) {
        // (u)words, step <= -2
        val stepsize = range.step.asConstInteger()!!
        val modifiedLabel = asmgen.makeLabel("for_modified")
        val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
        precheckFromToWord(iterableDt, stepsize, varname, endLabel)
        asmgen.out("""
                sty  $modifiedLabel+1
                sta  $modifiedLabel2+1
$loopLabel""")
        asmgen.translate(stmt.statements)
        asmgen.out("""
                lda  $varname
                sec
                sbc  #<${stepsize.absoluteValue}
                sta  $varname
                tax
                lda  $varname+1
                sbc  #>${stepsize.absoluteValue}
                sta  $varname+1
                txa
$modifiedLabel2 cmp  #0    ; modified 
                lda  $varname+1
$modifiedLabel  sbc  #0    ; modified
                bvc  +
                eor  #$80
+               bpl  $loopLabel                
$endLabel""")
        asmgen.romableError("self-modifying code (forloop over words range)", stmt.position)  // TODO fix romable
    }

    private fun precheckFromToWord(iterableDt: DataType, stepsize: Int, fromVar: String, endLabel: String) {
        // pre-check for end already reached.
        // 'to' is in AY, do NOT clobber this!
        if(iterableDt.isSignedWordArray) {
            if(stepsize<0)
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W2        ; to
                    sty  P8ZP_SCRATCH_W2+1      ; to
                    lda  $fromVar
                    cmp  P8ZP_SCRATCH_W2
                    lda  $fromVar+1
                    sbc  P8ZP_SCRATCH_W2+1
                    bvc  +
                    eor  #$80
+                   bmi  $endLabel
                    lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1""")
            else
                asmgen.out("""
                    sta  P8ZP_SCRATCH_REG
                    cmp  $fromVar
                    tya
                    sbc  $fromVar+1
                    bvc  +
                    eor  #$80
+                   bmi  $endLabel
                    lda  P8ZP_SCRATCH_REG""")
        } else {
            if(stepsize<0)
                asmgen.out("""
                    cpy  $fromVar+1
                    beq  +
                    bcc  ++
                    bcs  $endLabel
+                   cmp  $fromVar
                    bcc  +
                    beq  +
                    bne  $endLabel
+""")
            else
                asmgen.out("""
                    cpy  $fromVar+1
                    bcc  $endLabel
                    bne  +
                    cmp  $fromVar
                    bcc  $endLabel
+""")
        }
    }

    private fun translateForOverIterableVar(stmt: PtForLoop, iterableDt: DataType, ident: PtIdentifier) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val iterableName = asmgen.asmVariableName(ident)
        val numElements = when(val symbol = asmgen.symbolTable.lookup(ident.name)) {
            is StStaticVariable -> symbol.length!!
            is StMemVar -> symbol.length!!
            else -> 0
        }
        when {
            iterableDt.isString -> {
                if(asmgen.options.romable) {
                    val indexVar = asmgen.createTempVarReused(BaseDataType.UBYTE, false, stmt)
                    asmgen.out("""
                        ldy  #0
                        sty  $indexVar
$loopLabel              lda  $iterableName,y
                        beq  $endLabel
                        sta  ${asmgen.asmVariableName(stmt.variable)}""")
                    asmgen.translate(stmt.statements)
                    asmgen.out("""
                        inc  $indexVar
                        ldy  $indexVar
                        bne  $loopLabel
$endLabel""")
                } else {
                    val indexVar = asmgen.makeLabel("for_index")
                    asmgen.out("""
                        ldy  #0
                        sty  $indexVar
$loopLabel              lda  $iterableName,y
                        beq  $endLabel
                        sta  ${asmgen.asmVariableName(stmt.variable)}""")
                    asmgen.translate(stmt.statements)
                    asmgen.out("""
                        inc  $indexVar
                        ldy  $indexVar
                        bne  $loopLabel
$indexVar   .byte  0                        
$endLabel""")
                }
            }
            iterableDt.isByteArray || iterableDt.isBoolArray -> {
                val indexVar = if(asmgen.options.romable)
                    asmgen.createTempVarReused(iterableDt.elementType().base, false, stmt)
                else
                    asmgen.makeLabel("for_index")
                asmgen.out("""
                    ldy  #0
$loopLabel          sty  $indexVar
                    lda  $iterableName,y
                    sta  ${asmgen.asmVariableName(stmt.variable)}""")
                asmgen.translate(stmt.statements)
                if(numElements<=255) {
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        cpy  #$numElements
                        beq  $endLabel
                        bne  $loopLabel""")
                } else {
                    // length is 256
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        bne  $loopLabel
                        beq  $endLabel""")
                }
                if(!asmgen.options.romable) {
                    if(numElements>=16) {
                        // allocate index var on ZP if possible, otherwise inline
                        val result = zeropage.allocate(indexVar, DataType.UBYTE, null, stmt.position, asmgen.errors)
                        result.fold(
                            success = { (address, _, _)-> asmgen.out("""$indexVar = $address  ; auto zp UBYTE""") },
                            failure = { asmgen.out("$indexVar    .byte  0") }
                        )
                    } else {
                        asmgen.out("$indexVar    .byte  0")
                    }
                }
                asmgen.out(endLabel)
            }
            iterableDt.isSplitWordArray -> {
                val indexVar = if(asmgen.options.romable)
                    asmgen.createTempVarReused(BaseDataType.UBYTE, false, stmt)
                else
                    asmgen.makeLabel("for_index")
                val loopvarName = asmgen.asmVariableName(stmt.variable)
                asmgen.out("""
                    ldy  #0
$loopLabel          sty  $indexVar
                    lda  ${iterableName}_lsb,y
                    sta  $loopvarName
                    lda  ${iterableName}_msb,y
                    sta  $loopvarName+1""")
                asmgen.translate(stmt.statements)
                if(numElements<=255) {
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        cpy  #$numElements
                        beq  $endLabel
                        bne  $loopLabel""")
                } else {
                    // length is 256
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        bne  $loopLabel
                        beq  $endLabel""")
                }
                if(!asmgen.options.romable) {
                    if(numElements>=16) {
                        // allocate index var on ZP if possible, otherwise inline
                        val result = zeropage.allocate(indexVar, DataType.UBYTE, null, stmt.position, asmgen.errors)
                        result.fold(
                            success = { (address, _, _)-> asmgen.out("""$indexVar = $address  ; auto zp UBYTE""") },
                            failure = { asmgen.out("$indexVar    .byte  0") }
                        )
                    } else {
                        asmgen.out("$indexVar    .byte  0")
                    }
                }
                asmgen.out(endLabel)
            }
            iterableDt.isWordArray -> {
                val indexVar = if(asmgen.options.romable)
                    asmgen.createTempVarReused(BaseDataType.UBYTE, false, stmt)
                else
                    asmgen.makeLabel("for_index")
                val loopvarName = asmgen.asmVariableName(stmt.variable)
                asmgen.out("""
                    ldy  #0
$loopLabel          sty  $indexVar
                    lda  $iterableName,y
                    sta  $loopvarName
                    lda  $iterableName+1,y
                    sta  $loopvarName+1""")
                asmgen.translate(stmt.statements)
                if(numElements<=127) {
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        iny
                        cpy  #${numElements*2}
                        beq  $endLabel
                        bne  $loopLabel""")
                } else {
                    // array size is 128 words, 256 bytes
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        iny
                        bne  $loopLabel
                        beq  $endLabel""")
                }
                if(!asmgen.options.romable) {
                    if(numElements>=16) {
                        // allocate index var on ZP if possible, otherwise inline
                        val result = zeropage.allocate(indexVar, DataType.UBYTE, null, stmt.position, asmgen.errors)
                        result.fold(
                            success = { (address, _, _)-> asmgen.out("""$indexVar = $address  ; auto zp UBYTE""") },
                            failure = { asmgen.out("$indexVar    .byte  0") }
                        )
                    } else {
                        asmgen.out("$indexVar    .byte  0")
                    }
                }
                asmgen.out(endLabel)
            }
            iterableDt.isFloatArray -> {
                throw AssemblyError("for loop with floating point variables is not supported")
            }
            else -> throw AssemblyError("can't iterate over $iterableDt")
        }
        asmgen.loopEndLabels.removeLast()
    }

    private fun translateForOverConstRange(stmt: PtForLoop, iterableDt: DataType, range: IntProgression) {
        if (range.isEmpty() || range.step==0)
            throw AssemblyError("empty range or step 0")
        if(iterableDt.isByteArray) {
            if(range.last==range.first) return translateForSimpleByteRangeAsc(stmt, range)
            if(range.step==1 && range.last>range.first) return translateForSimpleByteRangeAsc(stmt, range)
            if(range.step==-1 && range.last<range.first) return translateForSimpleByteRangeDesc(stmt, range, iterableDt.isUnsignedByteArray)
        }
        else if(iterableDt.isWordArray) {
            if(range.last==range.first) return translateForSimpleWordRangeAsc(stmt, range)
            if(range.step==1 && range.last>range.first) return translateForSimpleWordRangeAsc(stmt, range)
            if(range.step==-1 && range.last<range.first) return translateForSimpleWordRangeDesc(stmt, range)
        }

        // not one of the easy cases, generate more complex code...
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        when {
            iterableDt.isByteArray -> {
                // loop over byte range via loopvar, step >= 2 or <= -2
                val varname = asmgen.asmVariableName(stmt.variable)
                asmgen.out("""
                            lda  #${range.first}
                            sta  $varname
$loopLabel""")
                asmgen.translate(stmt.statements)
                when (range.step) {
                    0, 1, -1 -> {
                        throw AssemblyError("step 0, 1 and -1 should have been handled specifically  $range ${stmt.position}")
                    }
                    2 -> {
                        if(range.last==255 || range.last==254) {
                            asmgen.out("""
                                inc  $varname
                                beq  $endLabel
                                inc  $varname
                                bne  $loopLabel""")
                        } else {
                            asmgen.out("""
                                inc  $varname
                                inc  $varname
                                lda  $varname
                                cmp  #${range.last+2}
                                bne  $loopLabel""")
                        }
                    }
                    -2 -> {
                        when (range.last) {
                            0 -> {
                                asmgen.out("""
                                    lda  $varname
                                    beq  $endLabel
                                    dec  $varname
                                    dec  $varname""")
                                asmgen.jmp(loopLabel)
                            }
                            1 -> asmgen.out("""
                                    dec  $varname
                                    beq  $endLabel
                                    dec  $varname
                                    bne  $loopLabel""")
                            else -> asmgen.out("""
                                    dec  $varname
                                    dec  $varname
                                    lda  $varname
                                    cmp  #${range.last-2}
                                    bne  $loopLabel""")
                        }
                    }
                    else -> {
                        // step <= -3 or >= 3
                        asmgen.out("""
                            lda  $varname
                            cmp  #${range.last}
                            beq  $endLabel
                            clc
                            adc  #${range.step}
                            sta  $varname""")
                        asmgen.jmp(loopLabel)
                    }
                }
                asmgen.out(endLabel)
            }
            iterableDt.isWordArray && !iterableDt.isSplitWordArray -> {
                // loop over word range via loopvar, step >= 2 or <= -2
                val varname = asmgen.asmVariableName(stmt.variable)
                when (range.step) {
                    0, 1, -1 -> {
                        throw AssemblyError("step 0, 1 and -1 should have been handled specifically  $stmt")
                    }
                    else -> {
                        // word, step >= 2 or <= -2
                        // note: range.last has already been adjusted by kotlin itself to actually be the last value of the sequence
                        asmgen.out("""
                            lda  #<${range.first}
                            ldy  #>${range.first}
                            sta  $varname
                            sty  $varname+1
$loopLabel""")
                        asmgen.translate(stmt.statements)
                        asmgen.out("""
                            lda  $varname
                            cmp  #<${range.last}
                            bne  +
                            lda  $varname+1
                            cmp  #>${range.last}
                            bne  +
                            beq  $endLabel
+                           lda  $varname
                            clc
                            adc  #<${range.step}
                            sta  $varname
                            lda  $varname+1
                            adc  #>${range.step}
                            sta  $varname+1""")
                        asmgen.jmp(loopLabel)
                        asmgen.out(endLabel)
                    }
                }
            }
            else -> throw AssemblyError("range expression can only be byte or word")
        }
        asmgen.loopEndLabels.removeLast()
    }

    private fun translateForSimpleByteRangeAsc(stmt: PtForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val varname = asmgen.asmVariableName(stmt.variable)
        asmgen.out("""
                lda  #${range.first}
                sta  $varname
$loopLabel""")
        asmgen.translate(stmt.statements)
        if (range.last == 255) {
            asmgen.out("""
                inc  $varname
                bne  $loopLabel
$endLabel""")
        } else {
            asmgen.out("""
                inc  $varname
                lda  $varname
                cmp  #${range.last+1}
                bne  $loopLabel
$endLabel""")
        }
        asmgen.loopEndLabels.removeLast()
    }

    private fun translateForSimpleByteRangeDesc(stmt: PtForLoop, range: IntProgression, unsigned: Boolean) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val varname = asmgen.asmVariableName(stmt.variable)
        asmgen.out("""
            lda  #${range.first}
            sta  $varname
$loopLabel""")
        asmgen.translate(stmt.statements)
        when (range.last) {
            0 -> {
                if(!unsigned || range.first<=127) {
                    asmgen.out("""
                        dec  $varname
                        bpl  $loopLabel""")
                } else {
                    asmgen.out("""
                        dec  $varname
                        lda  $varname
                        cmp  #255
                        bne  $loopLabel""")
                }
            }
            1 -> {
                asmgen.out("""
                    dec  $varname
                    bne  $loopLabel""")
            }
            else -> {
                asmgen.out("""
                    dec  $varname
                    lda  $varname
                    cmp  #${range.last-1}
                    bne  $loopLabel""")
            }
        }
    }

    private fun translateForSimpleWordRangeAsc(stmt: PtForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val varname = asmgen.asmVariableName(stmt.variable)
        asmgen.out("""
            lda  #<${range.first}
            ldy  #>${range.first}
            sta  $varname
            sty  $varname+1
$loopLabel""")
        asmgen.translate(stmt.statements)
        asmgen.out("""
            lda  $varname
            cmp  #<${range.last}
            bne  +
            lda  $varname+1
            cmp  #>${range.last}
            beq  $endLabel
+           inc  $varname
            bne  $loopLabel
            inc  $varname+1""")
        asmgen.jmp(loopLabel)
        asmgen.out(endLabel)
        asmgen.loopEndLabels.removeLast()
    }

    private fun translateForSimpleWordRangeDesc(stmt: PtForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.add(endLabel)
        val varname = asmgen.asmVariableName(stmt.variable)
        asmgen.out("""
            lda  #<${range.first}
            ldy  #>${range.first}
            sta  $varname
            sty  $varname+1
$loopLabel""")
        asmgen.translate(stmt.statements)
        if(range.last==0) {
            asmgen.out("""
                lda  $varname
                bne  ++
                lda  $varname+1
                beq  $endLabel""")
        } else {
            asmgen.out("""
                lda  $varname
                cmp  #<${range.last}
                bne  +
                lda  $varname+1
                cmp  #>${range.last}
                beq  $endLabel""")
        }
        asmgen.out("""
+           lda  $varname
            bne  +
            dec  $varname+1
+           dec  $varname""")
        asmgen.jmp(loopLabel)
        asmgen.out(endLabel)
        asmgen.loopEndLabels.removeLast()
    }

    private fun assignLoopvarWord(stmt: PtForLoop, range: PtRange) =
        asmgen.assignExpressionToVariable(
            range.from,
            asmgen.asmVariableName(stmt.variable),
            stmt.variable.type)
}
