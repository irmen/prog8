package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RegisterExpr
import prog8.ast.statements.PostIncrDecr
import prog8.compiler.target.c64.MachineDefinition
import prog8.compiler.toHex

internal class PostIncrDecrAsmGen(private val program: Program, private val asmgen: AsmGen) {
    internal fun translate(stmt: PostIncrDecr) {
        val incr = stmt.operator=="++"
        val targetIdent = stmt.target.identifier
        val targetMemory = stmt.target.memoryAddress
        val targetArrayIdx = stmt.target.arrayindexed
        val targetRegister = stmt.target.register
        when {
            targetRegister!=null -> {
                when(targetRegister) {
                    Register.A -> {
                        if(incr)
                            asmgen.out("  clc |  adc  #1 ")
                        else
                            asmgen.out("  sec |  sbc  #1 ")
                    }
                    Register.X -> {
                        if(incr) asmgen.out("  inx") else asmgen.out("  dex")
                    }
                    Register.Y -> {
                        if(incr) asmgen.out("  iny") else asmgen.out("  dey")
                    }
                }
            }
            targetIdent!=null -> {
                val what = asmgen.asmIdentifierName(targetIdent)
                val dt = stmt.target.inferType(program, stmt).typeOrElse(DataType.STRUCT)
                when (dt) {
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
                        asmgen.out(if(incr) "  jsr  c64flt.inc_var_f" else "  jsr  c64flt.dec_var_f")
                    }
                    else -> throw AssemblyError("need numeric type")
                }
            }
            targetMemory!=null -> {
                val addressExpr = targetMemory.addressExpression
                when (addressExpr) {
                    is NumericLiteralValue -> {
                        val what = addressExpr.number.toHex()
                        asmgen.out(if(incr) "  inc  $what" else "  dec  $what")
                    }
                    is IdentifierReference -> {
                        val what = asmgen.asmIdentifierName(addressExpr)
                        asmgen.out(if(incr) "  inc  $what" else "  dec  $what")
                    }
                    else -> throw AssemblyError("weird target type $targetMemory")
                }
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val what = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                val elementDt = ArrayElementTypes.getValue(arrayDt)
                when(index) {
                    is NumericLiteralValue -> {
                        val indexValue = index.number.toInt() * elementDt.memorySize()
                        when(elementDt) {
                            in ByteDatatypes -> asmgen.out(if (incr) "  inc  $what+$indexValue" else "  dec  $what+$indexValue")
                            in WordDatatypes -> {
                                if(incr)
                                    asmgen.out(" inc  $what+$indexValue |  bne  + |  inc  $what+$indexValue+1 |+")
                                else
                                    asmgen.out("""
        lda  $what+$indexValue
        bne  +
        dec  $what+$indexValue+1
+       dec  $what+$indexValue 
""")
                            }
                            DataType.FLOAT -> {
                                asmgen.out("  lda  #<$what+$indexValue |  ldy  #>$what+$indexValue")
                                asmgen.out(if(incr) "  jsr  c64flt.inc_var_f" else "  jsr  c64flt.dec_var_f")
                            }
                            else -> throw AssemblyError("need numeric type")
                        }
                    }
                    is RegisterExpr -> {
                        // TODO optimize common cases
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        incrDecrArrayvalueWithIndexA(incr, arrayDt, what)
                    }
                    is IdentifierReference -> {
                        // TODO optimize common cases
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        incrDecrArrayvalueWithIndexA(incr, arrayDt, what)
                    }
                    else -> {
                        // TODO optimize common cases
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        incrDecrArrayvalueWithIndexA(incr, arrayDt, what)
                    }
                }
            }
            else -> throw AssemblyError("weird target type ${stmt.target}")
        }
    }

    private fun incrDecrArrayvalueWithIndexA(incr: Boolean, arrayDt: DataType, arrayVarName: String) {
        asmgen.out("  stx  ${MachineDefinition.C64Zeropage.SCRATCH_REG_X} |  tax")
        when(arrayDt) {
            DataType.STR, DataType.STR_S,
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                asmgen.out(if(incr) "  inc  $arrayVarName,x" else "  dec  $arrayVarName,x")
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                if(incr)
                    asmgen.out(" inc  $arrayVarName,x |  bne  + |  inc  $arrayVarName+1,x |+")
                else
                    asmgen.out("""
        lda  $arrayVarName,x
        bne  +
        dec  $arrayVarName+1,x
+       dec  $arrayVarName 
""")
            }
            DataType.ARRAY_F -> {
                asmgen.out("  lda  #<$arrayVarName |  ldy  #>$arrayVarName")
                asmgen.out(if(incr) "  jsr  c64flt.inc_indexed_var_f" else "  jsr  c64flt.dec_indexed_var_f")
            }
            else -> throw AssemblyError("weird array dt")
        }
        asmgen.out("  ldx  ${MachineDefinition.C64Zeropage.SCRATCH_REG_X}")
    }

}
