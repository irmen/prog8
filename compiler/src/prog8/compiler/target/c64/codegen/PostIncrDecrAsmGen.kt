package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.PostIncrDecr
import prog8.compiler.AssemblyError
import prog8.compiler.toHex


internal class PostIncrDecrAsmGen(private val program: Program, private val asmgen: AsmGen) {
    internal fun translate(stmt: PostIncrDecr) {
        val incr = stmt.operator=="++"
        val targetIdent = stmt.target.identifier
        val targetMemory = stmt.target.memoryAddress
        val targetArrayIdx = stmt.target.arrayindexed
        when {
            targetIdent!=null -> {
                val what = asmgen.asmVariableName(targetIdent)
                when (stmt.target.inferType(program, stmt).typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> asmgen.out(if (incr) "  inc  $what" else "  dec  $what")
                    in WordDatatypes -> {
                        if(incr)
                            asmgen.out(" inc  $what |  bne  + |  inc  $what+1 |+")
                        else
                            asmgen.out("""
        lda  $what
        bne  +
        dec  $what+1
+       dec  $what 
""")
                    }
                    DataType.FLOAT -> {
                        asmgen.out("  lda  #<$what |  ldy  #>$what")
                        asmgen.out(if(incr) "  jsr  floats.inc_var_f" else "  jsr  floats.dec_var_f")
                    }
                    else -> throw AssemblyError("need numeric type")
                }
            }
            targetMemory!=null -> {
                when (val addressExpr = targetMemory.addressExpression) {
                    is NumericLiteralValue -> {
                        val what = addressExpr.number.toHex()
                        asmgen.out(if(incr) "  inc  $what" else "  dec  $what")
                    }
                    is IdentifierReference -> {
                        val what = asmgen.asmVariableName(addressExpr)
                        asmgen.out("  lda  $what |  sta  (+) +1 |  lda  $what+1 |  sta  (+) +2")
                        if(incr)
                            asmgen.out("+\tinc  ${'$'}ffff\t; modified")
                        else
                            asmgen.out("+\tdec  ${'$'}ffff\t; modified")
                    }
                    else -> {
                        asmgen.translateExpression(addressExpr)
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            sta  (+) + 1
                            lda  P8ESTACK_HI,x
                            sta  (+) + 2
                        """)
                        if(incr)
                            asmgen.out("+\tinc  ${'$'}ffff\t; modified")
                        else
                            asmgen.out("+\tdec  ${'$'}ffff\t; modified")
                    }
                }
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val asmArrayvarname = asmgen.asmVariableName(targetArrayIdx.identifier)
                val elementDt = targetArrayIdx.inferType(program).typeOrElse(DataType.STRUCT)
                when(index) {
                    is NumericLiteralValue -> {
                        val indexValue = index.number.toInt() * elementDt.memorySize()
                        when(elementDt) {
                            in ByteDatatypes -> asmgen.out(if (incr) "  inc  $asmArrayvarname+$indexValue" else "  dec  $asmArrayvarname+$indexValue")
                            in WordDatatypes -> {
                                if(incr)
                                    asmgen.out(" inc  $asmArrayvarname+$indexValue |  bne  + |  inc  $asmArrayvarname+$indexValue+1 |+")
                                else
                                    asmgen.out("""
        lda  $asmArrayvarname+$indexValue
        bne  +
        dec  $asmArrayvarname+$indexValue+1
+       dec  $asmArrayvarname+$indexValue 
""")
                            }
                            DataType.FLOAT -> {
                                asmgen.out("  lda  #<$asmArrayvarname+$indexValue |  ldy  #>$asmArrayvarname+$indexValue")
                                asmgen.out(if(incr) "  jsr  floats.inc_var_f" else "  jsr  floats.dec_var_f")
                            }
                            else -> throw AssemblyError("need numeric type")
                        }
                    }
                    else -> {
                        asmgen.loadScaledArrayIndexIntoRegister(targetArrayIdx, elementDt, CpuRegister.A)
                        asmgen.saveRegister(CpuRegister.X)
                        asmgen.out("  tax")
                        when(elementDt) {
                            in ByteDatatypes -> {
                                asmgen.out(if(incr) "  inc  $asmArrayvarname,x" else "  dec  $asmArrayvarname,x")
                            }
                            in WordDatatypes -> {
                                if(incr)
                                    asmgen.out(" inc  $asmArrayvarname,x |  bne  + |  inc  $asmArrayvarname+1,x |+")
                                else
                                    asmgen.out("""
        lda  $asmArrayvarname,x
        bne  +
        dec  $asmArrayvarname+1,x
+       dec  $asmArrayvarname 
""")
                            }
                            DataType.FLOAT -> {
                                asmgen.out("""
                        ldy  #>$asmArrayvarname
                        clc
                        adc  #<$asmArrayvarname
                        bcc  +
                        iny
+                       jsr  floats.inc_var_f""")
                            }
                            else -> throw AssemblyError("weird array elt dt")
                        }
                        asmgen.restoreRegister(CpuRegister.X)
                    }
                }
            }
            else -> throw AssemblyError("weird target type ${stmt.target}")
        }
    }
}
