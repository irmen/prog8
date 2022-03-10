package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.ast.Program
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.RangeExpression
import prog8.ast.statements.ForLoop
import prog8.code.core.ArrayToElementTypes
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair
import prog8.code.core.toHex
import prog8.compilerinterface.AssemblyError
import prog8.compilerinterface.Zeropage
import kotlin.math.absoluteValue

internal class ForLoopsAsmGen(private val program: Program, private val asmgen: AsmGen, private val zeropage: Zeropage) {

    internal fun translate(stmt: ForLoop) {
        val iterableDt = stmt.iterable.inferType(program)
        if(!iterableDt.isKnown)
            throw AssemblyError("unknown dt")
        when(stmt.iterable) {
            is RangeExpression -> {
                val range = (stmt.iterable as RangeExpression).toConstantIntegerRange()
                if(range==null) {
                    translateForOverNonconstRange(stmt, iterableDt.getOrElse { throw AssemblyError("unknown dt") }, stmt.iterable as RangeExpression)
                } else {
                    translateForOverConstRange(stmt, iterableDt.getOrElse { throw AssemblyError("unknown dt") }, range)
                }
            }
            is IdentifierReference -> {
                translateForOverIterableVar(stmt, iterableDt.getOrElse { throw AssemblyError("unknown dt") }, stmt.iterable as IdentifierReference)
            }
            else -> throw AssemblyError("can't iterate over ${stmt.iterable.javaClass} - should have been replaced by a variable")
        }
    }

    private fun translateForOverNonconstRange(stmt: ForLoop, iterableDt: DataType, range: RangeExpression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val modifiedLabel = asmgen.makeLabel("for_modified")
        val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
        asmgen.loopEndLabels.push(endLabel)
        val stepsize=range.step.constValue(program)!!.number.toInt()

        if(stepsize < -1) {
            val limit = range.to.constValue(program)?.number
            if(limit==0.0)
                throw AssemblyError("for unsigned loop variable it's not possible to count down with step != -1 from a non-const value to exactly zero due to value wrapping")
        }

        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                if (stepsize==1 || stepsize==-1) {

                    // bytes array, step 1 or -1

                    val incdec = if(stepsize==1) "inc" else "dec"
                    // loop over byte range via loopvar
                    val varname = asmgen.asmVariableName(stmt.loopVar)
                    asmgen.assignExpressionToVariable(range.from, varname, ArrayToElementTypes.getValue(iterableDt), null)
                    asmgen.assignExpressionToVariable(range.to, "$modifiedLabel+1", ArrayToElementTypes.getValue(iterableDt), null)
                    asmgen.out(loopLabel)
                    asmgen.translate(stmt.body)
                    asmgen.out("""
                        lda  $varname
$modifiedLabel          cmp  #0         ; modified 
                        beq  $endLabel
                        $incdec  $varname""")
                    asmgen.jmp(loopLabel)
                    asmgen.out(endLabel)

                } else {

                    // bytes, step >= 2 or <= -2

                    // loop over byte range via loopvar
                    val varname = asmgen.asmVariableName(stmt.loopVar)
                    asmgen.assignExpressionToVariable(range.from, varname, ArrayToElementTypes.getValue(iterableDt), null)
                    asmgen.assignExpressionToVariable(range.to, "$modifiedLabel+1", ArrayToElementTypes.getValue(iterableDt), null)
                    asmgen.out(loopLabel)
                    asmgen.translate(stmt.body)
                    if(stepsize>0) {
                        asmgen.out("""
                            lda  $varname
                            clc
                            adc  #$stepsize
                            sta  $varname
$modifiedLabel              cmp  #0    ; modified
                            bmi  $loopLabel
                            beq  $loopLabel""")
                    } else {
                        asmgen.out("""
                            lda  $varname
                            sec
                            sbc  #${stepsize.absoluteValue}
                            sta  $varname
$modifiedLabel              cmp  #0     ; modified
                            bpl  $loopLabel""")
                    }
                    asmgen.out(endLabel)
                }
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                when {

                    // words, step 1 or -1

                    stepsize == 1 || stepsize == -1 -> {
                        val varname = asmgen.asmVariableName(stmt.loopVar)
                        assignLoopvar(stmt, range)
                        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
                        asmgen.out("""
                            sty  $modifiedLabel+1
                            sta  $modifiedLabel2+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
                lda  $varname+1
$modifiedLabel  cmp  #0    ; modified 
                bne  +
                lda  $varname
$modifiedLabel2 cmp  #0    ; modified 
                beq  $endLabel""")
                        if(stepsize==1) {
                            asmgen.out("""
+               inc  $varname
                bne  $loopLabel
                inc  $varname+1""")
                            asmgen.jmp(loopLabel)
                        } else {
                            asmgen.out("""
+               lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname""")
                            asmgen.jmp(loopLabel)
                        }
                        asmgen.out(endLabel)
                    }
                    stepsize > 0 -> {

                        // (u)words, step >= 2
                        val varname = asmgen.asmVariableName(stmt.loopVar)
                        assignLoopvar(stmt, range)
                        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
                        asmgen.out("""
                            sty  $modifiedLabel+1
                            sta  $modifiedLabel2+1
$loopLabel""")
                        asmgen.translate(stmt.body)

                        if (iterableDt == DataType.ARRAY_UW) {
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
                    else -> {

                        // (u)words, step <= -2
                        val varname = asmgen.asmVariableName(stmt.loopVar)
                        assignLoopvar(stmt, range)
                        asmgen.assignExpressionToRegister(range.to, RegisterOrPair.AY)
                        asmgen.out("""
                            sty  $modifiedLabel+1
                            sta  $modifiedLabel2+1
$loopLabel""")
                        asmgen.translate(stmt.body)

                        if(iterableDt==DataType.ARRAY_UW) {
                            asmgen.out("""
                lda  $varname
                sec
                sbc  #<${stepsize.absoluteValue}
                sta  $varname
                lda  $varname+1
                sbc  #>${stepsize.absoluteValue}
                sta  $varname+1
$modifiedLabel  cmp  #0    ; modified                
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
$modifiedLabel2 cmp  #0    ; modified 
                bcs  $loopLabel
$endLabel""")
                        } else {
                            asmgen.out("""
                lda  $varname
                sec
                sbc  #<${stepsize.absoluteValue}
                sta  $varname
                pha
                lda  $varname+1
                sbc  #>${stepsize.absoluteValue}
                sta  $varname+1
                pla
$modifiedLabel2 cmp  #0    ; modified 
                lda  $varname+1
$modifiedLabel  sbc  #0    ; modified
                bvc  +
                eor  #$80
+               bpl  $loopLabel                
$endLabel""")
                        }
                    }
                }
            }
            else -> throw AssemblyError("range expression can only be byte or word")
        }

        asmgen.loopEndLabels.pop()
    }

    private fun translateForOverIterableVar(stmt: ForLoop, iterableDt: DataType, ident: IdentifierReference) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val iterableName = asmgen.asmVariableName(ident)
        val decl = ident.targetVarDecl(program)!!
        when(iterableDt) {
            DataType.STR -> {
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $loopLabel+1
                    sty  $loopLabel+2
$loopLabel          lda  ${65535.toHex()}       ; modified
                    beq  $endLabel
                    sta  ${asmgen.asmVariableName(stmt.loopVar)}""")
                asmgen.translate(stmt.body)
                asmgen.out("""
                    inc  $loopLabel+1
                    bne  $loopLabel
                    inc  $loopLabel+2
                    bne  $loopLabel
$endLabel""")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                val length = decl.arraysize!!.constIndex()!!
                val indexVar = asmgen.makeLabel("for_index")
                asmgen.out("""
                    ldy  #0
$loopLabel          sty  $indexVar
                    lda  $iterableName,y
                    sta  ${asmgen.asmVariableName(stmt.loopVar)}""")
                asmgen.translate(stmt.body)
                if(length<=255) {
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        cpy  #$length
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
                if(length>=16) {
                    // allocate index var on ZP if possible
                    val result = zeropage.allocate(listOf(indexVar), DataType.UBYTE, null, stmt.position, asmgen.errors)
                    result.fold(
                        success = { (address,_)-> asmgen.out("""$indexVar = $address  ; auto zp UBYTE""") },
                        failure = { asmgen.out("$indexVar    .byte  0") }
                    )
                } else {
                    asmgen.out("$indexVar    .byte  0")
                }
                asmgen.out(endLabel)
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                val length = decl.arraysize!!.constIndex()!! * 2
                val indexVar = asmgen.makeLabel("for_index")
                val loopvarName = asmgen.asmVariableName(stmt.loopVar)
                asmgen.out("""
                    ldy  #0
$loopLabel          sty  $indexVar
                    lda  $iterableName,y
                    sta  $loopvarName
                    lda  $iterableName+1,y
                    sta  $loopvarName+1""")
                asmgen.translate(stmt.body)
                if(length<=127) {
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        iny
                        cpy  #$length
                        beq  $endLabel
                        bne  $loopLabel""")
                } else {
                    // length is 128 words, 256 bytes
                    asmgen.out("""
                        ldy  $indexVar
                        iny
                        iny
                        bne  $loopLabel
                        beq  $endLabel""")
                }
                if(length>=16) {
                    // allocate index var on ZP if possible
                    val result = zeropage.allocate(listOf(indexVar), DataType.UBYTE, null, stmt.position, asmgen.errors)
                    result.fold(
                        success = { (address,_)-> asmgen.out("""$indexVar = $address  ; auto zp UBYTE""") },
                        failure = { asmgen.out("$indexVar    .byte  0") }
                    )
                } else {
                    asmgen.out("$indexVar    .byte  0")
                }
                asmgen.out(endLabel)
            }
            DataType.ARRAY_F -> {
                throw AssemblyError("for loop with floating point variables is not supported")
            }
            else -> throw AssemblyError("can't iterate over $iterableDt")
        }
        asmgen.loopEndLabels.pop()
    }

    private fun translateForOverConstRange(stmt: ForLoop, iterableDt: DataType, range: IntProgression) {
        if (range.isEmpty() || range.step==0)
            throw AssemblyError("empty range or step 0")
        if(iterableDt==DataType.ARRAY_B || iterableDt==DataType.ARRAY_UB) {
            if(range.step==1 && range.last>range.first) return translateForSimpleByteRangeAsc(stmt, range)
            if(range.step==-1 && range.last<range.first) return translateForSimpleByteRangeDesc(stmt, range)
        }
        else if(iterableDt==DataType.ARRAY_W || iterableDt==DataType.ARRAY_UW) {
            if(range.step==1 && range.last>range.first) return translateForSimpleWordRangeAsc(stmt, range)
            if(range.step==-1 && range.last<range.first) return translateForSimpleWordRangeDesc(stmt, range)
        }

        // not one of the easy cases, generate more complex code...
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                // loop over byte range via loopvar, step >= 2 or <= -2
                val varname = asmgen.asmVariableName(stmt.loopVar)
                asmgen.out("""
                            lda  #${range.first}
                            sta  $varname
$loopLabel""")
                asmgen.translate(stmt.body)
                when (range.step) {
                    0, 1, -1 -> {
                        throw AssemblyError("step 0, 1 and -1 should have been handled specifically  $stmt")
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
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                // loop over word range via loopvar, step >= 2 or <= -2
                val varname = asmgen.asmVariableName(stmt.loopVar)
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
                        asmgen.translate(stmt.body)
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
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleByteRangeAsc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmVariableName(stmt.loopVar)
        asmgen.out("""
                lda  #${range.first}
                sta  $varname
$loopLabel""")
        asmgen.translate(stmt.body)
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
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleByteRangeDesc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmVariableName(stmt.loopVar)
        asmgen.out("""
                    lda  #${range.first}
                    sta  $varname
$loopLabel""")
        asmgen.translate(stmt.body)
        when (range.last) {
            0 -> {
                asmgen.out("""
                    lda  $varname
                    beq  $endLabel
                    dec  $varname""")
                asmgen.jmp(loopLabel)
                asmgen.out(endLabel)
            }
            1 -> {
                asmgen.out("""
                    dec  $varname
                    bne  $loopLabel
$endLabel""")
            }
            else -> {
                asmgen.out("""
                    dec  $varname
                    lda  $varname
                    cmp  #${range.last-1}
                    bne  $loopLabel
$endLabel""")
            }
        }
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleWordRangeAsc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmVariableName(stmt.loopVar)
        asmgen.out("""
            lda  #<${range.first}
            ldy  #>${range.first}
            sta  $varname
            sty  $varname+1
$loopLabel""")
        asmgen.translate(stmt.body)
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
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleWordRangeDesc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmVariableName(stmt.loopVar)
        asmgen.out("""
            lda  #<${range.first}
            ldy  #>${range.first}
            sta  $varname
            sty  $varname+1
$loopLabel""")
        asmgen.translate(stmt.body)
        asmgen.out("""
            lda  $varname
            cmp  #<${range.last}
            bne  +
            lda  $varname+1
            cmp  #>${range.last}
            beq  $endLabel
+           lda  $varname
            bne  +
            dec  $varname+1
+           dec  $varname""")
        asmgen.jmp(loopLabel)
        asmgen.out(endLabel)
        asmgen.loopEndLabels.pop()
    }

    private fun assignLoopvar(stmt: ForLoop, range: RangeExpression) =
        asmgen.assignExpressionToVariable(
            range.from,
            asmgen.asmVariableName(stmt.loopVar),
            stmt.loopVarDt(program).getOrElse { throw AssemblyError("unknown dt") },
            stmt.definingSubroutine)
}
