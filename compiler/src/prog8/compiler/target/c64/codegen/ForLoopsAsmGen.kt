package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Register
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.ForLoop
import prog8.compiler.target.c64.MachineDefinition
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
                    if (range.isEmpty())
                        throw AssemblyError("empty range")
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
        val counterLabel = asmgen.makeLabel("for_counter")
        asmgen.loopEndLabels.push(endLabel)
        asmgen.loopContinueLabels.push(continueLabel)
        val stepsize=range.step.constValue(program)?.number
        when (stepsize) {
            1 -> {
                when(iterableDt) {
                    DataType.ARRAY_B, DataType.ARRAY_UB -> {
                        if (stmt.loopRegister != null) {
                            // loop register over range
                            if(stmt.loopRegister!= Register.A)
                                throw AssemblyError("can only use A")
                            asmgen.translateExpression(range.to)
                            asmgen.translateExpression(range.from)
                            asmgen.out("""
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX},x
                sta  $loopLabel+1
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX},x
                sec
                sbc  $loopLabel+1
                adc  #0
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
                        } else {
                            // loop over byte range via loopvar
                            val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                            asmgen.translateExpression(range.to)
                            asmgen.translateExpression(range.from)
                            asmgen.out("""
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX},x
                sta  $varname
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX},x
                sec
                sbc  $varname
                adc  #0
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
                    }
                    DataType.ARRAY_UW, DataType.ARRAY_W -> {
                        asmgen.translateExpression(range.to)
                        asmgen.out("  inc  ${MachineDefinition.ESTACK_LO_HEX}+1,x |  bne  + |  inc  ${MachineDefinition.ESTACK_HI_HEX}+1,x |+  ")
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        val assignLoopvar = Assignment(AssignTarget(null, stmt.loopVar, null, null, stmt.loopVar!!.position),
                                null, range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out(loopLabel)
                        asmgen.translate(stmt.body)
                        asmgen.out("""
                inc  $varname
                bne  +
                inc  $varname+1            
+               lda  ${MachineDefinition.ESTACK_HI_HEX}+1,x
                cmp  $varname+1
                bne  +
                lda  ${MachineDefinition.ESTACK_LO_HEX}+1,x
                cmp  $varname
                beq  $endLabel
+               jmp  $loopLabel
$endLabel       inx""")
                    }
                    else -> throw AssemblyError("range expression can only be byte or word")
                }
            }
            -1 -> {
                when(iterableDt){
                    DataType.ARRAY_B, DataType.ARRAY_UB -> {
                        if (stmt.loopRegister != null) {
                            // loop register over range
                            if(stmt.loopRegister!= Register.A)
                                throw AssemblyError("can only use A")
                            asmgen.translateExpression(range.from)
                            asmgen.translateExpression(range.to)
                            asmgen.out("""
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX}+1,x
                sta  $loopLabel+1
                sec
                sbc  ${MachineDefinition.ESTACK_LO_HEX},x
                adc  #0
                sta  $counterLabel
                inx
$loopLabel      lda  #0                 ; modified""")
                            asmgen.translate(stmt.body)
                            asmgen.out("""
$continueLabel  dec  $counterLabel
                beq  $endLabel
                dec  $loopLabel+1
                jmp  $loopLabel
$counterLabel   .byte  0                
$endLabel""")
                        } else {
                            // loop over byte range via loopvar
                            val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                            asmgen.translateExpression(range.from)
                            asmgen.translateExpression(range.to)
                            asmgen.out("""
                inx
                lda  ${MachineDefinition.ESTACK_LO_HEX}+1,x
                sta  $varname
                sec
                sbc  ${MachineDefinition.ESTACK_LO_HEX},x
                adc  #0
                sta  $counterLabel
                inx
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
                    }
                    DataType.ARRAY_UW, DataType.ARRAY_W -> {
                        asmgen.translateExpression(range.to)
                        asmgen.out("""
                lda  ${MachineDefinition.ESTACK_LO_HEX}+1,x
                bne  +
                dec  ${MachineDefinition.ESTACK_HI_HEX}+1,x
+               dec  ${MachineDefinition.ESTACK_LO_HEX}+1,x                            
                        """)
                        val varname = asmgen.asmIdentifierName(stmt.loopVar!!)
                        val assignLoopvar = Assignment(AssignTarget(null, stmt.loopVar, null, null, stmt.loopVar!!.position),
                                null, range.from, range.position)
                        assignLoopvar.linkParents(stmt)
                        asmgen.translate(assignLoopvar)
                        asmgen.out(loopLabel)
                        asmgen.translate(stmt.body)
                        asmgen.out("""
                lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname                
                lda  ${MachineDefinition.ESTACK_HI_HEX}+1,x
                cmp  $varname+1
                bne  +
                lda  ${MachineDefinition.ESTACK_LO_HEX}+1,x
                cmp  $varname
                beq  $endLabel
+               jmp  $loopLabel
$endLabel       inx""")
                    }
                    else -> throw AssemblyError("range expression can only be byte or word")
                }
            }
            else -> when (iterableDt) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> TODO("non-const forloop bytes, step >1: $stepsize")
                DataType.ARRAY_UW, DataType.ARRAY_W -> TODO("non-const forloop words, step >1: $stepsize")
                else -> throw AssemblyError("range expression can only be byte or word")
            }
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
                        TODO("for, word, step>=2")
                    }
                    else -> {
                        // step <= -2
                        TODO("for, word, step<=-2")
                    }
                }
            }
            else -> throw AssemblyError("range expression can only be byte or word")
        }
        asmgen.loopEndLabels.pop()
        asmgen.loopContinueLabels.pop()
    }

}
