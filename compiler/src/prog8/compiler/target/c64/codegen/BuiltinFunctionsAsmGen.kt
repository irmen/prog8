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
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex
import prog8.functions.FunctionSignature

internal class BuiltinFunctionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctioncallExpression(fcall: FunctionCall, func: FunctionSignature) {
        translateFunctioncall(fcall, func, false)
    }

    internal fun translateFunctioncallStatement(fcall: FunctionCallStatement, func: FunctionSignature) {
        translateFunctioncall(fcall, func, true)
    }

    private fun translateFunctioncall(fcall: IFunctionCall, func: FunctionSignature, discardResult: Boolean) {
        val functionName = fcall.target.nameInSource.last()
        if (discardResult) {
            if (func.pure)
                return  // can just ignore the whole function call altogether
            else if (func.returntype != null)
                throw AssemblyError("discarding result of non-pure function $fcall")
        }

        when (functionName) {
            "msb" -> {
                val arg = fcall.arglist.single()
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
                translateFunctionArguments(fcall.arglist, func)
                asmgen.out("  inx | lda  $ESTACK_LO_HEX,x  | sta  $ESTACK_HI_PLUS1_HEX,x")
            }
            "abs" -> {
                translateFunctionArguments(fcall.arglist, func)
                val dt = fcall.arglist.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b")
                    in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w")
                    DataType.FLOAT -> asmgen.out("  jsr  c64flt.abs_f")
                    else -> throw AssemblyError("weird type")
                }
            }
            "swap" -> {
                val first = fcall.arglist[0]
                val second = fcall.arglist[1]
                asmgen.translateExpression(first)
                asmgen.translateExpression(second)
                // pop in reverse order
                val firstTarget = AssignTarget.fromExpr(first)
                val secondTarget = AssignTarget.fromExpr(second)
                asmgen.assignFromEvalResult(firstTarget)
                asmgen.assignFromEvalResult(secondTarget)
            }
            "strlen" -> {
                outputPushAddressOfIdentifier(fcall.arglist[0])
                asmgen.out("  jsr  prog8_lib.func_strlen")
            }
            "min", "max", "sum" -> {
                outputPushAddressAndLenghtOfArray(fcall.arglist[0])
                val dt = fcall.arglist.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.ARRAY_UB, DataType.STR_S, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${functionName}_ub")
                    DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${functionName}_b")
                    DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${functionName}_uw")
                    DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${functionName}_w")
                    DataType.ARRAY_F -> asmgen.out("  jsr  c64flt.func_${functionName}_f")
                    else -> throw AssemblyError("weird type $dt")
                }
            }
            "any", "all" -> {
                outputPushAddressAndLenghtOfArray(fcall.arglist[0])
                val dt = fcall.arglist.single().inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR_S, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${functionName}_b")
                    DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${functionName}_w")
                    DataType.ARRAY_F -> asmgen.out("  jsr  c64flt.func_${functionName}_f")
                    else -> throw AssemblyError("weird type $dt")
                }
            }
            "sin", "cos", "tan", "atan",
            "ln", "log2", "sqrt", "rad",
            "deg", "round", "floor", "ceil",
            "rdnf" -> {
                translateFunctionArguments(fcall.arglist, func)
                asmgen.out("  jsr  c64flt.func_$functionName")
            }
/*
        TODO this was the old code for bit rotations:
            Opcode.SHL_BYTE -> AsmFragment(" asl  $variable+$index", 8)
            Opcode.SHR_UBYTE -> AsmFragment(" lsr  $variable+$index", 8)
            Opcode.SHR_SBYTE -> AsmFragment(" lda  $variable+$index |  asl  a |  ror  $variable+$index")
            Opcode.SHL_WORD -> AsmFragment(" asl  $variable+${index * 2 + 1} |  rol  $variable+${index * 2}", 8)
            Opcode.SHR_UWORD -> AsmFragment(" lsr  $variable+${index * 2 + 1} |  ror  $variable+${index * 2}", 8)
            Opcode.SHR_SWORD -> AsmFragment(" lda  $variable+${index * 2 + 1} |  asl  a |  ror  $variable+${index * 2 + 1} |  ror  $variable+${index * 2}", 8)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $variable+$index", 8)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $variable+$index", 8)
            Opcode.ROL_WORD -> AsmFragment(" rol  $variable+${index * 2 + 1} |  rol  $variable+${index * 2}", 8)
            Opcode.ROR_WORD -> AsmFragment(" ror  $variable+${index * 2 + 1} |  ror  $variable+${index * 2}", 8)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $variable+$index |  cmp  #\$80 |  rol  $variable+$index", 8)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $variable+$index |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable+$index", 10)
            Opcode.ROL2_WORD -> AsmFragment(" asl  $variable+${index * 2 + 1} |  rol  $variable+${index * 2} |  bcc  + |  inc  $variable+${index * 2 + 1} |+", 20)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $variable+${index * 2 + 1} |  ror  $variable+${index * 2} |  bcc  + |  lda  $variable+${index * 2 + 1} |  ora  #\$80 |  sta  $variable+${index * 2 + 1} |+", 30)

 */
            "lsl" -> {
                // in-place
                val what = fcall.arglist.single()
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
                                    asmgen.out("  asl  ${(what.addressExpression as NumericLiteralValue).number.toHex()}")
                                } else {
                                    TODO("lsl memory byte $what")
                                }
                            }
                            is ArrayIndexedExpression -> {
                                TODO("lsl byte array $what")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    in WordDatatypes -> {
                        when (what) {
                            is ArrayIndexedExpression -> TODO("lsl sbyte $what")
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
                val what = fcall.arglist.single()
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
                                    asmgen.out("  lsr  ${(what.addressExpression as NumericLiteralValue).number.toHex()}")
                                } else {
                                    TODO("lsr memory byte $what")
                                }
                            }
                            is ArrayIndexedExpression -> {
                                TODO("lsr byte array $what")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.BYTE -> {
                        when (what) {
                            is ArrayIndexedExpression -> TODO("lsr sbyte $what")
                            is DirectMemoryRead -> TODO("lsr sbyte $what")
                            is RegisterExpr -> TODO("lsr sbyte $what")
                            is IdentifierReference -> TODO("lsr sbyte $what")
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.UWORD -> {
                        when (what) {
                            is ArrayIndexedExpression -> TODO("lsr uword $what")
                            is IdentifierReference -> {
                                val variable = asmgen.asmIdentifierName(what)
                                asmgen.out(" lsr  $variable+1 |  ror  $variable")
                            }
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    DataType.WORD -> {
                        when (what) {
                            is ArrayIndexedExpression -> TODO("lsr sword $what")
                            is IdentifierReference -> TODO("lsr sword $what")
                            else -> throw AssemblyError("weird type")
                        }
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "rol" -> {
                // in-place
                val what = fcall.arglist.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        TODO("rol ubyte")
                    }
                    DataType.UWORD -> {
                        TODO("rol uword")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "rol2" -> {
                // in-place
                val what = fcall.arglist.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        TODO("rol2 ubyte")
                    }
                    DataType.UWORD -> {
                        TODO("rol2 uword")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "ror" -> {
                // in-place
                val what = fcall.arglist.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        TODO("ror ubyte")
                    }
                    DataType.UWORD -> {
                        TODO("ror uword")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            "ror2" -> {
                // in-place
                val what = fcall.arglist.single()
                val dt = what.inferType(program)
                when (dt.typeOrElse(DataType.STRUCT)) {
                    DataType.UBYTE -> {
                        TODO("ror2 ubyte")
                    }
                    DataType.UWORD -> {
                        TODO("ror2 uword")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> {
                translateFunctionArguments(fcall.arglist, func)
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

    private fun translateFunctionArguments(args: MutableList<Expression>, signature: FunctionSignature) {
        args.forEach {
            asmgen.translateExpression(it)
        }
    }

}
