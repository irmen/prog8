package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.PostIncrDecr
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.toHex


internal class PostIncrDecrAsmGen(private val program: Program, private val asmgen: AsmGen) {
    internal fun translate(stmt: PostIncrDecr) {
        val incr = stmt.operator=="++"
        val targetIdent = stmt.target.identifier
        val targetMemory = stmt.target.memoryAddress
        val targetArrayIdx = stmt.target.arrayindexed
        when {
            targetIdent!=null -> {
                val what = asmgen.asmIdentifierName(targetIdent)
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
                        asmgen.out(if(incr) "  jsr  c64flt.inc_var_f" else "  jsr  c64flt.dec_var_f")
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
                        val what = asmgen.asmIdentifierName(addressExpr)
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
                            lda  $ESTACK_LO_HEX,x
                            sta  (+) + 1
                            lda  $ESTACK_HI_HEX,x
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
                val what = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                val elementDt = targetArrayIdx.inferType(program).typeOrElse(DataType.STRUCT)
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
                    is IdentifierReference -> {
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        incrDecrArrayvalueWithIndexA(incr, elementDt, what)
                    }
                    else -> {
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        incrDecrArrayvalueWithIndexA(incr, elementDt, what)
                    }
                }
            }
            else -> throw AssemblyError("weird target type ${stmt.target}")
        }
    }

    private fun incrDecrArrayvalueWithIndexA(incr: Boolean, elementDt: DataType, arrayVarName: String) {
        asmgen.out("  stx  ${C64Zeropage.SCRATCH_REG_X} |  tax")
        when(elementDt) {
            in ByteDatatypes -> {
                asmgen.out(if(incr) "  inc  $arrayVarName,x" else "  dec  $arrayVarName,x")
            }
            in WordDatatypes -> {
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
            DataType.FLOAT -> {
                asmgen.out("  lda  #<$arrayVarName |  ldy  #>$arrayVarName")
                asmgen.out(if(incr) "  jsr  c64flt.inc_indexed_var_f" else "  jsr  c64flt.dec_indexed_var_f")
            }
            else -> throw AssemblyError("weird array elt dt")
        }
        asmgen.out("  ldx  ${C64Zeropage.SCRATCH_REG_X}")
    }

}
