package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Register
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.ForLoop
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.toHex
import kotlin.math.absoluteValue

// todo choose more efficient comparisons to avoid needless lda's
// todo optimize common case step == 2 / -2


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
            else -> throw AssemblyError("can't iterate over ${stmt.iterable}")
        }
    }

    private fun translateForOverNonconstRange(stmt: ForLoop, iterableDt: DataType, range: RangeExpr) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val continueLabel = asmgen.makeLabel("for_continue")
        asmgen.loopEndLabels.push(endLabel)
        asmgen.loopContinueLabels.push(continueLabel)
        val stepsize=range.step.constValue(program)!!.number.toInt()
        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                if (stepsize==1 || stepsize==-1) {

                    // bytes, step 1 or -1

                    val incdec = if(stepsize==1) "inc" else "dec"
                    if (stmt.loopRegister != null) {
                        // loop register over range
                        if(stmt.loopRegister!= Register.A)
                            throw AssemblyError("can only use A")
                        asmgen.translateExpression(range.to)
                        asmgen.translateExpression(range.from)
                        asmgen.out("""
                inx
                lda  ${ESTACK_LO_HEX},x
                sta  $loopLabel+1
$loopLabel      lda  #0                 ; modified""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  lda  $loopLabel+1
                cmp  $ESTACK_LO_PLUS1_HEX,x
                beq  $endLabel
                $incdec  $loopLabel+1
                jmp  $loopLabel
$endLabel       inx""")
                    } else {
                        // loop over byte range via loopvar
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        asmgen.translateExpression(range.to)
                        asmgen.translateExpression(range.from)
                        asmgen.out("""
                inx
                lda  ${ESTACK_LO_HEX},x
                sta  $varname
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
                beq  $endLabel
                $incdec  $varname
                jmp  $loopLabel
$endLabel       inx""")
                    }
                }
                else {

                    // bytes, step >= 2 or <= -2

                    if (stmt.loopRegister != null) {
                        // loop register over range
                        if(stmt.loopRegister!= Register.A)
                            throw AssemblyError("can only use A")
                        asmgen.translateExpression(range.to)
                        asmgen.translateExpression(range.from)
                        asmgen.out("""
                inx
                lda  ${ESTACK_LO_HEX},x
                sta  $loopLabel+1
$loopLabel      lda  #0                 ; modified""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  lda  $loopLabel+1""")
                        if(stepsize>0) {
                            asmgen.out("""
                clc
                adc  #$stepsize
                sta  $loopLabel+1
                cmp  $ESTACK_LO_PLUS1_HEX,x
                bcc  $loopLabel
                beq  $loopLabel""")
                        } else {
                            asmgen.out("""
                sec
                sbc  #${stepsize.absoluteValue}
                sta  $loopLabel+1
                cmp  $ESTACK_LO_PLUS1_HEX,x
                bcs  $loopLabel""")
                        }
                        asmgen.out("""
$endLabel       inx""")
                    } else {
                        // loop over byte range via loopvar
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        asmgen.translateExpression(range.to)
                        asmgen.translateExpression(range.from)
                        asmgen.out("""
                inx
                lda  ${ESTACK_LO_HEX},x
                sta  $varname
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  lda  $varname""")
                        if(stepsize>0) {
                            asmgen.out("""
                clc
                adc  #$stepsize
                sta  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
                bcc  $loopLabel
                beq  $loopLabel""")
                        } else {
                            asmgen.out("""
                sec
                sbc  #${stepsize.absoluteValue}
                sta  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
                bcs  $loopLabel""")
                        }
                        asmgen.out("""
$endLabel       inx""")
                    }
                }
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                when {

                    // words, step 1 or -1

                    stepsize == 1 || stepsize == -1 -> {
                        asmgen.translateExpression(range.to)
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        val assignLoopvar = Assignment(AssignTarget(null, stmt.loopVar, null, null, stmt.loopVar!!.position),
                                null, range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out(loopLabel)
                        asmgen.translate(stmt.body)
                        asmgen.out("""
                lda  $varname+1
                cmp  $ESTACK_HI_PLUS1_HEX,x
                bne  +
                lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
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
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        val assignLoopvar = Assignment(AssignTarget(null, stmt.loopVar, null, null, stmt.loopVar!!.position),
                                null, range.from, range.position)
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
                lda  $ESTACK_HI_PLUS1_HEX,x
                cmp  $varname+1
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
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
                lda  $ESTACK_LO_PLUS1_HEX,x
                cmp  $varname
                lda  $ESTACK_HI_PLUS1_HEX,x
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
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        val assignLoopvar = Assignment(AssignTarget(null, stmt.loopVar, null, null, stmt.loopVar!!.position),
                                null, range.from, range.position)
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
                cmp  $ESTACK_HI_PLUS1_HEX,x
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
                cmp  $ESTACK_LO_PLUS1_HEX,x
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
                cmp  $ESTACK_LO_PLUS1_HEX,x
                lda  $varname+1
                sbc  $ESTACK_HI_PLUS1_HEX,x
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
        asmgen.loopContinueLabels.pop()
    }

    private fun translateForOverIterableVar(stmt: ForLoop, iterableDt: DataType, ident: IdentifierReference) {
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val continueLabel = asmgen.makeLabel("for_continue")
        asmgen.loopEndLabels.push(endLabel)
        asmgen.loopContinueLabels.push(continueLabel)
        val iterableName = asmgen.asmIdentifierName(ident)
        val decl = ident.targetVarDecl(program.namespace)!!
        when(iterableDt) {
            DataType.STR, DataType.STR_S -> {
                if(stmt.loopRegister!=null && stmt.loopRegister!= Register.A)
                    throw AssemblyError("can only use A")
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $loopLabel+1
                    sty  $loopLabel+2
$loopLabel          lda  ${65535.toHex()}       ; modified
                    beq  $endLabel""")
                if(stmt.loopVar!=null)
                    asmgen.out("  sta  ${asmgen.asmIdentifierName(stmt.loopVar!!)}")
                asmgen.translate(stmt.body)
                asmgen.out("""
$continueLabel      inc  $loopLabel+1
                    bne  $loopLabel
                    inc  $loopLabel+2
                    bne  $loopLabel
$endLabel""")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                // TODO: optimize loop code when the length of the array is < 256, don't need a separate counter in such cases
                val length = decl.arraysize!!.size()!!
                if(stmt.loopRegister!=null && stmt.loopRegister!= Register.A)
                    throw AssemblyError("can only use A")
                val counterLabel = asmgen.makeLabel("for_counter")
                val modifiedLabel = asmgen.makeLabel("for_modified")
                asmgen.out("""
                    lda  #<$iterableName
                    ldy  #>$iterableName
                    sta  $modifiedLabel+1
                    sty  $modifiedLabel+2
                    ldy  #0
$loopLabel          sty  $counterLabel
$modifiedLabel      lda  ${65535.toHex()},y       ; modified""")
                if(stmt.loopVar!=null)
                    asmgen.out("  sta  ${asmgen.asmIdentifierName(stmt.loopVar!!)}")
                asmgen.translate(stmt.body)
                asmgen.out("""
$continueLabel      ldy  $counterLabel
                    iny
                    cpy  #${length and 255}
                    beq  $endLabel
                    jmp  $loopLabel
$counterLabel       .byte  0
$endLabel""")
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                // TODO: optimize loop code when the length of the array is < 256, don't need a separate counter in such cases
                val length = decl.arraysize!!.size()!! * 2
                if(stmt.loopRegister!=null)
                    throw AssemblyError("can't use register to loop over words")
                val counterLabel = asmgen.makeLabel("for_counter")
                val modifiedLabel = asmgen.makeLabel("for_modified")
                val modifiedLabel2 = asmgen.makeLabel("for_modified2")
                val loopvarName = asmgen.asmIdentifierName(stmt.loopVar!!)
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
$continueLabel      ldy  $counterLabel
                    iny
                    iny
                    cpy  #${length and 255}
                    beq  $endLabel
                    jmp  $loopLabel
$counterLabel       .byte  0
$endLabel""")
            }
            DataType.ARRAY_F -> {
                throw AssemblyError("for loop with floating point variables is not supported")
            }
            else -> throw AssemblyError("can't iterate over $iterableDt")
        }
        asmgen.loopEndLabels.pop()
        asmgen.loopContinueLabels.pop()
    }

    private fun translateForOverConstRange(stmt: ForLoop, iterableDt: DataType, range: IntProgression) {
        // TODO: optimize loop code when the range is < 256 iterations, don't need a separate counter in such cases
        if (range.isEmpty())
            throw AssemblyError("empty range")
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val continueLabel = asmgen.makeLabel("for_continue")
        asmgen.loopEndLabels.push(endLabel)
        asmgen.loopContinueLabels.push(continueLabel)
        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                val counterLabel = asmgen.makeLabel("for_counter")
                if(stmt.loopRegister!=null) {

                    // loop register over range

                    if(stmt.loopRegister!= Register.A)
                        throw AssemblyError("can only use A")
                    when {
                        range.step==1 -> {
                            // step = 1
                            asmgen.out("""
                lda  #${range.first}
                sta  $loopLabel+1
                lda  #${range.last-range.first+1 and 255}
                sta  $counterLabel
$loopLabel      lda  #0                 ; modified""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                inc  $loopLabel+1
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        range.step==-1 -> {
                            // step = -1
                            asmgen.out("""
                lda  #${range.first}
                sta  $loopLabel+1
                lda  #${range.first-range.last+1 and 255}
                sta  $counterLabel
$loopLabel      lda  #0                 ; modified """)
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                dec  $loopLabel+1
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        range.step >= 2 -> {
                            // step >= 2
                            asmgen.out("""
                lda  #${(range.last-range.first) / range.step + 1}
                sta  $counterLabel
                lda  #${range.first}
$loopLabel      pha""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  pla
                dec  $counterLabel
                beq  $endLabel
                clc
                adc  #${range.step}
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        else -> {
                            // step <= -2
                            asmgen.out("""
                lda  #${(range.first-range.last) / range.step.absoluteValue + 1}
                sta  $counterLabel
                lda  #${range.first}
$loopLabel      pha""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  pla
                dec  $counterLabel
                beq  $endLabel
                sec
                sbc  #${range.step.absoluteValue}
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                    }

                } else {

                    // loop over byte range via loopvar
                    val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                    when {
                        range.step==1 -> {
                            // step = 1
                            asmgen.out("""
                lda  #${range.first}
                sta  $varname
                lda  #${range.last-range.first+1 and 255}
                sta  $counterLabel
$loopLabel""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                inc  $varname
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        range.step==-1 -> {
                            // step = -1
                            asmgen.out("""
                lda  #${range.first}
                sta  $varname
                lda  #${range.first-range.last+1 and 255}
                sta  $counterLabel
$loopLabel""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                dec  $varname
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        range.step >= 2 -> {
                            // step >= 2
                            asmgen.out("""
                lda  #${(range.last-range.first) / range.step + 1}
                sta  $counterLabel
                lda  #${range.first}
                sta  $varname
$loopLabel""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                lda  $varname
                clc
                adc  #${range.step}
                sta  $varname
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                        else -> {
                            // step <= -2
                            asmgen.out("""
                lda  #${(range.first-range.last) / range.step.absoluteValue + 1}
                sta  $counterLabel
                lda  #${range.first}
                sta  $varname
$loopLabel""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                lda  $varname
                sec
                sbc  #${range.step.absoluteValue}
                sta  $varname
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        }
                    }
                }
            }
            DataType.ARRAY_W, DataType.ARRAY_UW -> {
                // loop over word range via loopvar
                val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                when {
                    range.step == 1 -> {
                        // word, step = 1
                        val lastValue = range.last+1
                        asmgen.out("""
                lda  #<${range.first}
                ldy  #>${range.first}
                sta  $varname
                sty  $varname+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  inc  $varname
                bne  +
                inc  $varname+1
+               lda  $varname
                cmp  #<$lastValue
                bne  +
                lda  $varname+1
                cmp  #>$lastValue
                beq  $endLabel
+               jmp  $loopLabel
$endLabel""")
                    }
                    range.step == -1 -> {
                        // word, step = 1
                        val lastValue = range.last-1
                        asmgen.out("""
                lda  #<${range.first}
                ldy  #>${range.first}
                sta  $varname
                sty  $varname+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname
                lda  $varname
                cmp  #<$lastValue
                bne  +
                lda  $varname+1
                cmp  #>$lastValue
                beq  $endLabel
+               jmp  $loopLabel
$endLabel""")
                    }
                    range.step >= 2 -> {
                        // word, step >= 2
                        // note: range.last has already been adjusted by kotlin itself to actually be the last value of the sequence
                        val lastValue = range.last+range.step
                        asmgen.out("""
                lda  #<${range.first}
                ldy  #>${range.first}
                sta  $varname
                sty  $varname+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  clc
                lda  $varname
                adc  #<${range.step}
                sta  $varname
                lda  $varname+1
                adc  #>${range.step}
                sta  $varname+1
                lda  $varname
                cmp  #<$lastValue
                bne  +
                lda  $varname+1
                cmp  #>$lastValue
                beq  $endLabel
+               jmp  $loopLabel
$endLabel""")
                    }
                    else -> {
                        // step <= -2
                        // note: range.last has already been adjusted by kotlin itself to actually be the last value of the sequence
                        val lastValue = range.last+range.step
                        asmgen.out("""
                lda  #<${range.first}
                ldy  #>${range.first}
                sta  $varname
                sty  $varname+1
$loopLabel""")
                        asmgen.translate(stmt.body)
                        asmgen.out("""
$continueLabel  sec
                lda  $varname
                sbc  #<${range.step.absoluteValue}
                sta  $varname
                lda  $varname+1
                sbc  #>${range.step.absoluteValue}
                sta  $varname+1
                lda  $varname
                cmp  #<$lastValue
                bne  +
                lda  $varname+1
                cmp  #>$lastValue
                beq  $endLabel
+               jmp  $loopLabel
$endLabel""")
                    }
                }
            }
            else -> throw AssemblyError("range expression can only be byte or word")
        }
        asmgen.loopEndLabels.pop()
        asmgen.loopContinueLabels.pop()
    }

}
