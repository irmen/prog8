package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.RangeExpr
import prog8.ast.statements.ForLoop
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.codegen.assignment.AsmAssignSource
import prog8.compiler.target.c64.codegen.assignment.AsmAssignTarget
import prog8.compiler.target.c64.codegen.assignment.AsmAssignment
import prog8.compiler.target.c64.codegen.assignment.TargetStorageKind
import prog8.compiler.toHex
import kotlin.math.absoluteValue

internal class ForLoopsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translate(stmt: ForLoop) {
        val iterableDt = stmt.iterable.inferType(program)
        if(!iterableDt.isKnown)
            throw AssemblyError("unknown dt")
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
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        val modifiedLabel = asmgen.makeLabel("for_modified")
        val modifiedLabel2 = asmgen.makeLabel("for_modifiedb")
        asmgen.loopEndLabels.push(endLabel)
        val stepsize=range.step.constValue(program)!!.number.toInt()
        when(iterableDt) {
            DataType.ARRAY_B, DataType.ARRAY_UB -> {
                if (stepsize==1 || stepsize==-1) {

                    // bytes array, step 1 or -1

                    val incdec = if(stepsize==1) "inc" else "dec"
                    // loop over byte range via loopvar
                    val varname = asmgen.asmVariableName(stmt.loopVar)
                    asmgen.translateExpression(range.to)
                    asmgen.translateExpression(range.from)
                    asmgen.out("""
                inx
                lda  P8ESTACK_LO,x
                sta  $varname
                lda  P8ESTACK_LO+1,x
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
                    val varname = asmgen.asmVariableName(stmt.loopVar)
                    asmgen.translateExpression(range.to)
                    asmgen.translateExpression(range.from)
                    asmgen.out("""
                inx
                lda  P8ESTACK_LO,x
                sta  $varname
                lda  P8ESTACK_LO+1,x
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
                        assignLoopvar(stmt, range)
                        val varname = asmgen.asmVariableName(stmt.loopVar)
                        asmgen.out("""
                            lda  P8ESTACK_HI+1,x
                            sta  $modifiedLabel+1
                            lda  P8ESTACK_LO+1,x
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
                inc  $varname+1
                jmp  $loopLabel
                            """)
                        } else {
                            asmgen.out("""
+               lda  $varname
                bne  +
                dec  $varname+1
+               dec  $varname
                jmp  $loopLabel""")
                        }
                        asmgen.out(endLabel)
                        asmgen.out(" inx")
                    }
                    stepsize > 0 -> {

                        // (u)words, step >= 2
                        asmgen.translateExpression(range.to)
                        asmgen.out("""
                            lda  P8ESTACK_HI+1,x
                            sta  $modifiedLabel+1
                            lda  P8ESTACK_LO+1,x
                            sta  $modifiedLabel2+1
                        """)
                        assignLoopvar(stmt, range)
                        val varname = asmgen.asmVariableName(stmt.loopVar)
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
$modifiedLabel  cmp  #0     ; modified
                bcc  $loopLabel
                bne  $endLabel
$modifiedLabel2 lda  #0     ; modified
                cmp  $varname
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
$modifiedLabel2 lda  #0   ; modified
                cmp  $varname
$modifiedLabel  lda  #0   ; modified
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
                        asmgen.out("""
                            lda  P8ESTACK_HI+1,x
                            sta  $modifiedLabel+1
                            lda  P8ESTACK_LO+1,x
                            sta  $modifiedLabel2+1
                        """)
                        assignLoopvar(stmt, range)
                        val varname = asmgen.asmVariableName(stmt.loopVar)
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
$modifiedLabel  cmp  #0    ; modified                
                bcc  $endLabel
                bne  $loopLabel
                lda  $varname
$modifiedLabel2 cmp  #0    ; modified 
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
$modifiedLabel2 cmp  #0    ; modified 
                lda  $varname+1
$modifiedLabel  sbc  #0    ; modified
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
        val loopLabel = asmgen.makeLabel("for_loop")
        val endLabel = asmgen.makeLabel("for_end")
        asmgen.loopEndLabels.push(endLabel)
        val iterableName = asmgen.asmVariableName(ident)
        val decl = ident.targetVarDecl(program.namespace)!!
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
                if(length>=16 && asmgen.zeropage.available() > 0) {
                    // allocate index var on ZP
                    val zpAddr = asmgen.zeropage.allocate(indexVar, DataType.UBYTE, stmt.position, asmgen.errors)
                    asmgen.out("""$indexVar = $zpAddr  ; auto zp UBYTE""")
                } else {
                    asmgen.out("""
$indexVar           .byte  0""")
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
                if(length>=16 && asmgen.zeropage.available() > 0) {
                    // allocate index var on ZP
                    val zpAddr = asmgen.zeropage.allocate(indexVar, DataType.UBYTE, stmt.position, asmgen.errors)
                    asmgen.out("""$indexVar = $zpAddr  ; auto zp UBYTE""")
                } else {
                    asmgen.out("""
$indexVar           .byte  0""")
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

    private fun assignLoopvar(stmt: ForLoop, range: RangeExpr) {
        val target = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, stmt.loopVarDt(program).typeOrElse(DataType.STRUCT), stmt.definingSubroutine(), variableAsmName=asmgen.asmVariableName(stmt.loopVar))
        val src = AsmAssignSource.fromAstSource(range.from, program, asmgen).adjustSignedUnsigned(target)
        val assign = AsmAssignment(src, target, false, range.position)
        asmgen.translateNormalAssignment(assign)
    }
}
