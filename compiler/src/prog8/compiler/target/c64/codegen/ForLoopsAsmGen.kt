package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.ForLoop
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex
import kotlin.math.absoluteValue

internal class ForLoopsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translate(stmt: ForLoop) {
        val iterableDt = stmt.iterable.inferType(program)
        if(!iterableDt.isKnown)
            throw AssemblyError("can't determine iterable dt")
        when(stmt.iterable) {
            is RangeExpr -> {
                val range = (stmt.iterable as RangeExpr).toConstantIntegerRange()
                if(range==null) {
                    translateForOverNonconstRange(stmt, iterableDt.typeOrElse(DataType.STRUCT), stmt.iterable as RangeExpr)
                } else {
                    translateForOverConstRange(stmt, iterableDt.typeOrElse(DataType.STRUCT), range)
                }
            }
            is IdentifierReference -> {
                translateForOverIterableVar(stmt, iterableDt.typeOrElse(DataType.STRUCT), stmt.iterable as IdentifierReference)
            }
            else -> throw AssemblyError("can't iterate over ${stmt.iterable.javaClass} - should have been replaced by a variable")
        }
    }

    private fun translateForOverNonconstRange(stmt: ForLoop, iterableDt: DataType, range: RangeExpr) {
        // TODO get rid of all cmp stack,x  by using modifying code
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val modifiedLabel = asmgen.makeLabel("for_modified")
        val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
        asmgen.loopEndLabels.push(endLabel)
        val stepsize=range.step.constValue(program)!!.number.toInt()
        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                if (stepsize==1 || stepsize==-1) {

                    // bytes, step 1 or -1

                    val incdec = if(stepsize==1) "inc" else "dec"
                    // loop over byte range via loopvar
                    val varname = asmgen.asmIdentifierName(stmt.loopVar)
                    asmgen.translateExpression(range.to)
                    asmgen.translateExpression(range.from)
                    asmgen.out("""
                inx
                lda  $ESTACK_LO_HEX,x
                sta  $varname
                lda  $ESTACK_LO_PLUS1_HEX,x
                sta  $modifiedLabel+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
                lda  $varname
$modifiedLabel  cmp  #0         ; modified 
                beq  $endLabel
                $incdec  $varname
                jmp  $loopLabel
$endLabel       inx""")

                } else {

                    // bytes, step >= 2 or <= -2

                    // loop over byte range via loopvar
                    val varname = asmgen.asmIdentifierName(stmt.loopVar)
                    asmgen.translateExpression(range.to)
                    asmgen.translateExpression(range.from)
                    asmgen.out("""
                inx
                lda  $ESTACK_LO_HEX,x
                sta  $varname
                lda  $ESTACK_LO_PLUS1_HEX,x
                sta  $modifiedLabel+1
$loopLabel""")
                    asmgen.translate(stmt.body)
                    asmgen.out("""
                lda  $varname""")
                    if(stepsize>0) {
                        asmgen.out("""
                clc
                adc  #$stepsize
                sta  $varname
$modifiedLabel  cmp  #0    ; modified
                bcc  $loopLabel
                beq  $loopLabel""")
                    } else {
                        asmgen.out("""
                sec
                sbc  #${stepsize.absoluteValue}
                sta  $varname
$modifiedLabel  cmp  #0     ; modified 
                bcs  $loopLabel""")
                    }
                    asmgen.out("""
$endLabel       inx""")
                }
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                when {

                    // words, step 1 or -1

                    stepsize == 1 || stepsize == -1 -> {
                        asmgen.translateExpression(range.to)
                        val varname = asmgen.asmIdentifierName(stmt.loopVar)
                        val assignLoopvar = Assignment(AssignTarget(stmt.loopVar, null, null, stmt.loopVar.position), range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out("""
                            lda  $ESTACK_HI_PLUS1_HEX,x
                            sta  $modifiedLabel+1
                            lda  $ESTACK_LO_PLUS1_HEX,x
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
                bne  +
                inc  $varname+1            
                            """)
                        } else {
                            asmgen.out("""
+               lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname""")
                        }
                        asmgen.out("""
+               jmp  $loopLabel
$endLabel       inx""")
                    }
                    stepsize > 0 -> {

                        // (u)words, step >= 2

                        asmgen.translateExpression(range.to)
                        val varname = asmgen.asmIdentifierName(stmt.loopVar)
                        val assignLoopvar = Assignment(AssignTarget(stmt.loopVar, null, null, stmt.loopVar.position), range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out(loopLabel)
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
                lda  $ESTACK_HI_PLUS1_HEX,x    TODO modifying code1
                cmp  $varname+1
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x       TODO modifying code1
                bcc  $endLabel
                bcs  $loopLabel
$endLabel       inx""")
                        } else {
                            asmgen.out("""
                lda  $varname
                clc
                adc  #<$stepsize
                sta  $varname
                lda  $varname+1
                adc  #>$stepsize
                sta  $varname+1
                lda  $ESTACK_LO_PLUS1_HEX,x   TODO modifying code2
                cmp  $varname
                lda  $ESTACK_HI_PLUS1_HEX,x   TODO modifying code2 
                sbc  $varname+1
                bvc  +
                eor  #$80
+               bpl  $loopLabel                
$endLabel       inx""")
                        }
                    }
                    else -> {

                        // (u)words, step <= -2
                        asmgen.translateExpression(range.to)
                        val varname = asmgen.asmIdentifierName(stmt.loopVar)
                        val assignLoopvar = Assignment(AssignTarget(stmt.loopVar, null, null, stmt.loopVar.position), range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out(loopLabel)
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
                cmp  $ESTACK_HI_PLUS1_HEX,x    TODO modifying code3 
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x    TODO modifying code3 
                bcs  $loopLabel
$endLabel       inx""")
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
                cmp  $ESTACK_LO_PLUS1_HEX,x    TODO modifying code4 
                lda  $varname+1
                sbc  $ESTACK_HI_PLUS1_HEX,x    TODO modifying code4
                bvc  +
                eor  #$80
+               bpl  $loopLabel                
$endLabel       inx""")
                        }
                    }
                }
            }
            else -> throw AssemblyError("range expression can only be byte or word")
        }

        asmgen.loopEndLabels.pop()
    }

    private fun translateForOverIterableVar(stmt: ForLoop, iterableDt: DataType, ident: IdentifierReference) {
        // TODO optimize this more
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val iterableName = asmgen.asmIdentifierName(ident)
        val decl = ident.targetVarDecl(program.namespace)!!
        when(iterableDt) {
            DataType.STR -> {
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $loopLabel+1
                    sty  $loopLabel+2
$loopLabel          lda  ${65535.toHex()}       ; modified
                    beq  $endLabel""")
                asmgen.out("  sta  ${asmgen.asmIdentifierName(stmt.loopVar)}")
                asmgen.translate(stmt.body)
                asmgen.out("""
                    inc  $loopLabel+1
                    bne  $loopLabel
                    inc  $loopLabel+2
                    bne  $loopLabel
$endLabel""")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                // TODO: optimize loop code when the length of the array is < 256   (i.e. always)
                val length = decl.arraysize!!.size()!!
                val counterLabel = asmgen.makeLabel("for_counter")      // todo allocate dynamically, zero page preferred if iterations >= 8
                val modifiedLabel = asmgen.makeLabel("for_modified")
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $modifiedLabel+1
                    sty  $modifiedLabel+2
                    ldy  #0
$loopLabel          sty  $counterLabel
$modifiedLabel      lda  ${65535.toHex()},y       ; modified""")
                asmgen.out("  sta  ${asmgen.asmIdentifierName(stmt.loopVar)}")
                asmgen.translate(stmt.body)
                asmgen.out("""
                    ldy  $counterLabel
                    iny
                    cpy  #${length and 255}
                    beq  $endLabel
                    bne  $loopLabel
$counterLabel       .byte  0
$endLabel""")
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                // TODO: optimize loop code when the length of the array is < 256  (i.e. always)
                val length = decl.arraysize!!.size()!! * 2
                val counterLabel = asmgen.makeLabel("for_counter")    // todo allocate dynamically, zero page preferred if iterations >= 8
                val modifiedLabel = asmgen.makeLabel("for_modified")
                val modifiedLabel2 = asmgen.makeLabel("for_modified2")
                val loopvarName = asmgen.asmIdentifierName(stmt.loopVar)
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $modifiedLabel+1
                    sty  $modifiedLabel+2
                    lda  #<$iterableName+1
                    ldy  #>$iterableName+1
                    sta  $modifiedLabel2+1
                    sty  $modifiedLabel2+2
                    ldy  #0
$loopLabel          sty  $counterLabel
$modifiedLabel      lda  ${65535.toHex()},y       ; modified
                    sta  $loopvarName
$modifiedLabel2     lda  ${65535.toHex()},y       ; modified
                    sta  $loopvarName+1""")
                asmgen.translate(stmt.body)
                asmgen.out("""
                    ldy  $counterLabel
                    iny
                    iny
                    cpy  #${length and 255}
                    beq  $endLabel
                    bne  $loopLabel
$counterLabel       .byte  0
$endLabel""")
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
                val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
                        if(range.last==255) {
                            asmgen.out("""
                                inc  $varname
                                beq  $endLabel
                                inc  $varname
                                bne  $loopLabel""")
                        } else {
                            asmgen.out("""
                                lda  $varname
                                cmp  #${range.last}
                                beq  $endLabel
                                inc  $varname
                                inc  $varname
                                jmp  $loopLabel""")
                        }
                    }
                    -2 -> {
                        when (range.last) {
                            0 -> asmgen.out("""
                                lda  $varname
                                beq  $endLabel
                                dec  $varname
                                dec  $varname
                                jmp  $loopLabel""")
                            1 -> asmgen.out("""
                                dec  $varname
                                beq  $endLabel
                                dec  $varname
                                bne  $loopLabel""")
                            else -> asmgen.out("""
                                lda  $varname
                                cmp  #${range.last}
                                beq  $endLabel
                                dec  $varname
                                dec  $varname
                                jmp  $loopLabel""")
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
                            sta  $varname
                            jmp  $loopLabel""")
                    }
                }
                asmgen.out(endLabel)
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                // loop over word range via loopvar, step >= 2 or <= -2
                val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
                            sta  $varname+1
                            jmp  $loopLabel
$endLabel""")
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
        val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
                lda  $varname
                cmp  #${range.last}
                beq  $endLabel
                inc  $varname
                jmp  $loopLabel
$endLabel""")
        }
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleByteRangeDesc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
                    dec  $varname
                    jmp  $loopLabel
$endLabel""")
            }
            1 -> {
                asmgen.out("""
                    dec  $varname
                    jmp  $loopLabel
$endLabel""")
            }
            else -> {
                asmgen.out("""
                    lda  $varname
                    cmp  #${range.last}
                    beq  $endLabel
                    dec  $varname
                    jmp  $loopLabel
$endLabel""")
            }
        }
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleWordRangeAsc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
+           inc  $varname
            bne  $loopLabel
            inc  $varname+1
            jmp  $loopLabel
$endLabel""")
        asmgen.loopEndLabels.pop()
    }

    private fun translateForSimpleWordRangeDesc(stmt: ForLoop, range: IntProgression) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val varname = asmgen.asmIdentifierName(stmt.loopVar)
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
+           lda  $varname
            bne  +
            dec  $varname+1
+           dec  $varname
            jmp  $loopLabel
$endLabel""")
        asmgen.loopEndLabels.pop()
    }
}
