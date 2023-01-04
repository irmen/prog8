package prog8.codegen.cpu6502

import prog8.code.ast.PtIdentifier
import prog8.code.ast.PtNumber
import prog8.code.ast.PtPostIncrDecr
import prog8.code.ast.PtProgram
import prog8.code.core.*


internal class PostIncrDecrAsmGen(private val program: PtProgram, private val asmgen: AsmGen) {
    internal fun translate(stmt: PtPostIncrDecr) {
        val incr = stmt.operator=="++"
        val targetIdent = stmt.target.identifier
        val targetMemory = stmt.target.memory
        val targetArrayIdx = stmt.target.array
        val scope = stmt.definingSub()
        when {
            targetIdent!=null -> {
                val what = asmgen.asmVariableName(targetIdent)
                when (stmt.target.type) {
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
                when (val addressExpr = targetMemory.address) {
                    is PtNumber -> {
                        val what = addressExpr.number.toHex()
                        asmgen.out(if(incr) "  inc  $what" else "  dec  $what")
                    }
                    is PtIdentifier -> {
                        val what = asmgen.asmVariableName(addressExpr)
                        asmgen.out("  lda  $what |  sta  (+) +1 |  lda  $what+1 |  sta  (+) +2")
                        if(incr)
                            asmgen.out("+\tinc  ${'$'}ffff\t; modified")
                        else
                            asmgen.out("+\tdec  ${'$'}ffff\t; modified")
                    }
                    else -> {
                        asmgen.assignExpressionToRegister(addressExpr, RegisterOrPair.AY)
                        asmgen.out("  sta  (+) + 1 |  sty  (+) + 2")
                        if(incr)
                            asmgen.out("+\tinc  ${'$'}ffff\t; modified")
                        else
                            asmgen.out("+\tdec  ${'$'}ffff\t; modified")
                    }
                }
            }
            targetArrayIdx!=null -> {
                val asmArrayvarname = asmgen.asmVariableName(targetArrayIdx.variable)
                val elementDt = targetArrayIdx.type
                val constIndex = targetArrayIdx.index.asConstInteger()
                if(constIndex!=null) {
                    val indexValue = constIndex * program.memsizer.memorySize(elementDt)
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
                            asmgen.out("  lda  #<($asmArrayvarname+$indexValue) |  ldy  #>($asmArrayvarname+$indexValue)")
                            asmgen.out(if(incr) "  jsr  floats.inc_var_f" else "  jsr  floats.dec_var_f")
                        }
                        else -> throw AssemblyError("need numeric type")
                    }
                }
                else
                {
                    asmgen.loadScaledArrayIndexIntoRegister(targetArrayIdx, elementDt, CpuRegister.A)
                    asmgen.saveRegisterLocal(CpuRegister.X, scope!!)
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
+       dec  $asmArrayvarname,x 
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
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
            }
        }
    }
}
