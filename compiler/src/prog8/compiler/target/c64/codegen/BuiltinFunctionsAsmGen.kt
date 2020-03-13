package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.Register
import prog8.ast.base.WordDatatypes
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.FunctionCallStatement
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex
import prog8.compiler.AssemblyError
import prog8.functions.FSignature

internal class BuiltinFunctionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctioncallExpression(fcall: FunctionCall, func: FSignature) {
        translateFunctioncall(fcall, func, false)
    }

    internal fun translateFunctioncallStatement(fcall: FunctionCallStatement, func: FSignature) {
        translateFunctioncall(fcall, func, true)
    }

    private fun translateFunctioncall(fcall: IFunctionCall, func: FSignature, discardResult: Boolean) {
        val functionName = fcall.target.nameInSource.last()
        if (discardResult) {
            if (func.pure)
                return  // can just ignore the whole function call altogether
            else if (func.returntype != null)
                throw AssemblyError("discarding result of non-pure function $fcall")
        }

        when (functionName) {
            "msb" -> {
                val arg = fcall.args.single()
                if (arg.inferType(program).typeOrElse(DataType.STRUCT) !in WordDatatypes)
                    throw AssemblyError("msb required word argument")
                if (arg is NumericLiteralValue)
                    throw AssemblyError("should have been const-folded")
                if (arg is IdentifierReference) {
                    val sourceName = asmgen.asmIdentifierName(arg)
                    asmgen.out("  lda  $sourceName+1 |  sta  $ESTACK_LO_HEX,x |  dex")
                } else {
                    asmgen.translateExpression(arg)
                    asmgen.out("  lda  $ESTACK_HI_PLUS1_HEX,x |  sta  $ESTACK_LO_PLUS1_HEX,x")
                }
            }
            "mkword" -> {
                translateFunctionArguments(fcall.args, func)
                asmgen.out("  inx | lda  $ESTACK_LO_HEX,x  | sta  $ESTACK_HI_PLUS1_HEX,x")
            }
            "abs" -> {
                translateFunctionArguments(fcall.args, func)
                val dt = fcall.args.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w")
                    DataType.FLOAT -> asmgen.out("  jsr  c64flt.abs_f")
                    else -> throw AssemblyError("weird type")
                }
            }
            "swap" -> {
                val first = fcall.args[0]
                val second = fcall.args[1]
                asmgen.translateExpression(first)
                asmgen.translateExpression(second)
                // pop in reverse order
                val firstTarget = AssignTarget.fromExpr(first)
                val secondTarget = AssignTarget.fromExpr(second)
                asmgen.assignFromEvalResult(firstTarget)
                asmgen.assignFromEvalResult(secondTarget)
            }
            "strlen" -> {
                outputPushAddressOfIdentifier(fcall.args[0])
                asmgen.out("  jsr  prog8_lib.func_strlen")
            }
            "min", "max", "sum" -> {
                outputPushAddressAndLenghtOfArray(fcall.args[0])
                val dt = fcall.args.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${functionName}_ub")
                    DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${functionName}_b")
                    DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${functionName}_uw")
                    DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${functionName}_w")
                    DataType.ARRAY_F -> asmgen.out("  jsr  c64flt.func_${functionName}_f")
                    else -> throw AssemblyError("weird type $dt")
                }
            }
            "any", "all" -> {
                outputPushAddressAndLenghtOfArray(fcall.args[0])
                val dt = fcall.args.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${functionName}_b")
                    DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${functionName}_w")
                    DataType.ARRAY_F -> asmgen.out("  jsr  c64flt.func_${functionName}_f")
                    else -> throw AssemblyError("weird type $dt")
                }
            }
            "sgn" -> {
                translateFunctionArguments(fcall.args, func)
                val dt = fcall.args.single().inferType(program)
                when(dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> asmgen.out("  jsr  math.sign_ub")
                    DataType.BYTE -> asmgen.out("  jsr  math.sign_b")
                    DataType.UWORD -> asmgen.out("  jsr  math.sign_uw")
                    DataType.WORD -> asmgen.out("  jsr  math.sign_w")
                    DataType.FLOAT -> asmgen.out("  jsr  c64flt.sign_f")
                    else -> throw AssemblyError("weird type $dt")
                }
            }
            "sin", "cos", "tan", "atan",
            "ln", "log2", "sqrt", "rad",
            "deg", "round", "floor", "ceil",
            "rdnf" -> {
                translateFunctionArguments(fcall.args, func)
                asmgen.out("  jsr  c64flt.func_$functionName")
            }
            "lsl" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> {
                        when (what) {
                            is RegisterExpr -> {
                                when (what.register) {
                                    Register.A -> asmgen.out("  asl  a")
                                    Register.X -> asmgen.out("  txa  |  asl  a |  tax")
                                    Register.Y -> asmgen.out("  tya  |  asl  a |  tay")
                                }
                            }
                            is IdentifierReference -> asmgen.out("  asl  ${asmgen.asmIdentifierName(what)}")
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  asl  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    sta  (+) + 1
                    lda  $ESTACK_HI_HEX,x
                    sta  (+) + 2
+                   asl  ${'$'}ffff            ; modified                    
                                    """)
                                }
                            }
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsl_array_b")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    in WordDatatypes -> {
                        when (what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsl_array_w")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out(" asl  $variable |  rol  $variable+1")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "lsr" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        when (what) {
                            is RegisterExpr -> {
                                when (what.register) {
                                    Register.A -> asmgen.out("  lsr  a")
                                    Register.X -> asmgen.out("  txa  |  lsr  a |  tax")
                                    Register.Y -> asmgen.out("  tya  |  lsr  a |  tay")
                                }
                            }
                            is IdentifierReference -> asmgen.out("  lsr  ${asmgen.asmIdentifierName(what)}")
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  lsr  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    sta  (+) + 1
                    lda  $ESTACK_HI_HEX,x
                    sta  (+) + 2
+                   lsr  ${'$'}ffff            ; modified                    
                                    """)
                                }
                            }
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsr_array_ub")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.BYTE -> {
                        when (what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsr_array_b")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  lda  $variable |  asl  a |  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when (what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsr_array_uw")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out(" lsr  $variable+1 |  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.WORD -> {
                        when (what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.lsr_array_w")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  lda  $variable+1 |  asl a  |  ror  $variable+1 |  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "rol" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.rol_array_ub")
                            }
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  rol  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    sta  (+) + 1
                    lda  $ESTACK_HI_HEX,x
                    sta  (+) + 2
+                   rol  ${'$'}ffff            ; modified                    
                                    """)
                                }
                            }
                            is RegisterExpr -> {
                                when(what.register) {
                                    Register.A -> asmgen.out("  rol  a")
                                    Register.X -> asmgen.out("  txa |  rol  a |  tax")
                                    Register.Y -> asmgen.out("  tya |  rol  a |  tay")
                                }
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  rol  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.rol_array_uw")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  rol  $variable |  rol  $variable+1")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "rol2" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.rol2_array_ub")
                            }
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  lda  ${number.toHex()} |  cmp  #\$80 |  rol  a |  sta  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("  jsr  prog8_lib.rol2_mem_ub")
                                }
                            }
                            is RegisterExpr -> {
                                when(what.register) {
                                    Register.A -> asmgen.out("  cmp  #\$80 |  rol  a  ")
                                    Register.X -> asmgen.out("  txa  |  cmp  #\$80 |  rol  a  |  tax")
                                    Register.Y -> asmgen.out("  tya  |  cmp  #\$80 |  rol  a  |  tay")
                                }
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  lda  $variable |  cmp  #\$80 |  rol  a |  sta  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.rol2_array_uw")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  asl  $variable |  rol  $variable+1 |  bcc  + |  inc  $variable |+  ")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "ror" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.ror_array_ub")
                            }
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  ror  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    sta  (+) + 1
                    lda  $ESTACK_HI_HEX,x
                    sta  (+) + 2
+                   ror  ${'$'}ffff            ; modified                    
                                    """)                                }
                            }
                            is RegisterExpr -> {
                                when(what.register) {
                                    Register.A -> asmgen.out("  ror  a")
                                    Register.X -> asmgen.out("  txa |  ror  a |  tax")
                                    Register.Y -> asmgen.out("  tya |  ror  a |  tay")
                                }
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.ror_array_uw")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  ror  $variable+1 |  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "ror2" -> {
                // in-place
                val what = fcall.args.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.ror2_array_ub")
                            }
                            is DirectMemoryRead -> {
                                if (what.addressExpression is NumericLiteralValue) {
                                    val number = (what.addressExpression as NumericLiteralValue).number
                                    asmgen.out("  lda  ${number.toHex()} |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  ${number.toHex()}")
                                } else {
                                    asmgen.translateExpression(what.addressExpression)
                                    asmgen.out("  jsr  prog8_lib.ror2_mem_ub")
                                }
                            }
                            is RegisterExpr -> {
                                when(what.register) {
                                    Register.A -> asmgen.out("  lsr  a |  bcc  + |  ora  #\$80 |+  ")
                                    Register.X -> asmgen.out("  txa |  lsr  a |  bcc  + |  ora  #\$80 |+  tax ")
                                    Register.Y -> asmgen.out("  tya |  lsr  a |  bcc  + |  ora  #\$80 |+  tay ")
                                }
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when(what) {
                            is ArrayIndexedExpression -> {
                                asmgen.translateExpression(what.identifier)
                                asmgen.translateExpression(what.arrayspec.index)
                                asmgen.out("  jsr  prog8_lib.ror2_array_uw")
                            }
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out("  lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #\$80 |  sta  $variable+1 |+  ")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "sort" -> {
                val variable = fcall.args.single()
                if(variable is IdentifierReference) {
                    val decl = variable.targetVarDecl(program.namespace)!!
                    val varName = asmgen.asmIdentifierName(variable)
                    val numElements = decl.arraysize!!.size()
                    when(decl.datatype) {
                        DataType.ARRAY_UB, DataType.ARRAY_B -> {
                            asmgen.out("""
                                lda  #<$varName
                                ldy  #>$varName
                                sta  ${C64Zeropage.SCRATCH_W1}
                                sty  ${C64Zeropage.SCRATCH_W1+1}
                                lda  #$numElements
                                sta  ${C64Zeropage.SCRATCH_B1}
                            """)
                            asmgen.out(if(decl.datatype==DataType.ARRAY_UB) "  jsr  prog8_lib.sort_ub" else "  jsr  prog8_lib.sort_b")
                        }
                        DataType.ARRAY_UW, DataType.ARRAY_W -> {
                            asmgen.out("""
                                lda  #<$varName
                                ldy  #>$varName
                                sta  ${C64Zeropage.SCRATCH_W1}
                                sty  ${C64Zeropage.SCRATCH_W1+1}
                                lda  #$numElements
                                sta  ${C64Zeropage.SCRATCH_B1}
                            """)
                            asmgen.out(if(decl.datatype==DataType.ARRAY_UW) "  jsr  prog8_lib.sort_uw" else "  jsr  prog8_lib.sort_w")
                        }
                        DataType.ARRAY_F -> throw AssemblyError("sorting of floating point array is not supported")
                        else -> throw AssemblyError("weird type")
                    }
                }
                else
                    throw AssemblyError("weird type")
            }
            "reverse" -> {
                val variable = fcall.args.single()
                if (variable is IdentifierReference) {
                    val decl = variable.targetVarDecl(program.namespace)!!
                    val varName = asmgen.asmIdentifierName(variable)
                    val numElements = decl.arraysize!!.size()
                    when (decl.datatype) {
                        DataType.ARRAY_UB, DataType.ARRAY_B -> {
                            asmgen.out("""
                                lda  #<$varName
                                ldy  #>$varName
                                sta  ${C64Zeropage.SCRATCH_W1}
                                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                                lda  #$numElements
                                jsr  prog8_lib.reverse_b
                            """)
                        }
                        DataType.ARRAY_UW, DataType.ARRAY_W -> {
                            asmgen.out("""
                                lda  #<$varName
                                ldy  #>$varName
                                sta  ${C64Zeropage.SCRATCH_W1}
                                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                                lda  #$numElements
                                jsr  prog8_lib.reverse_w
                            """)
                        }
                        DataType.ARRAY_F -> {
                            asmgen.out("""
                                lda  #<$varName
                                ldy  #>$varName
                                sta  ${C64Zeropage.SCRATCH_W1}
                                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                                lda  #$numElements
                                jsr  prog8_lib.reverse_f
                            """)
                        }
                        else -> throw AssemblyError("weird type")
                    }
                }
            }
            "rsave" -> {
                // save cpu status flag and all registers A, X, Y.
                // see http://6502.org/tutorials/register_preservation.html
                asmgen.out(" php |  sta  ${C64Zeropage.SCRATCH_REG} | pha  | txa  | pha  | tya  | pha  | lda  ${C64Zeropage.SCRATCH_REG}")
            }
            "rrestore" -> {
                // restore all registers and cpu status flag
                asmgen.out(" pla |  tay |  pla |  tax |  pla |  plp")
            }
            else -> {
                translateFunctionArguments(fcall.args, func)
                asmgen.out("  jsr  prog8_lib.func_$functionName")
            }
        }
    }

    private fun outputPushAddressAndLenghtOfArray(arg: Expression) {
        arg as IdentifierReference
        val identifierName = asmgen.asmIdentifierName(arg)
        val size = arg.targetVarDecl(program.namespace)!!.arraysize!!.size()!!
        asmgen.out("""
                    lda  #<$identifierName
                    sta  $ESTACK_LO_HEX,x
                    lda  #>$identifierName
                    sta  $ESTACK_HI_HEX,x
                    dex
                    lda  #$size
                    sta  $ESTACK_LO_HEX,x
                    dex
                    """)
    }

    private fun outputPushAddressOfIdentifier(arg: Expression) {
        val identifierName = asmgen.asmIdentifierName(arg as IdentifierReference)
        asmgen.out("""
                    lda  #<$identifierName
                    sta  $ESTACK_LO_HEX,x
                    lda  #>$identifierName
                    sta  $ESTACK_HI_HEX,x
                    dex
                    """)
    }

    private fun translateFunctionArguments(args: MutableList<Expression>, signature: FSignature) {
        args.forEach {
            asmgen.translateExpression(it)
        }
    }

}
