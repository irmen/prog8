package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.FunctionCallStatement
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex
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
            "msb" -> funcMsb(fcall)
            "lsb" -> funcLsb(fcall)
            "mkword" -> funcMkword(fcall, func)
            "abs" -> funcAbs(fcall, func)
            "swap" -> funcSwap(fcall)
            "strlen" -> funcStrlen(fcall)
            "min", "max", "sum" -> funcMinMaxSum(fcall, functionName)
            "any", "all" -> funcAnyAll(fcall, functionName)
            "sgn" -> funcSgn(fcall, func)
            "sin", "cos", "tan", "atan",
            "ln", "log2", "sqrt", "rad",
            "deg", "round", "floor", "ceil",
            "rdnf" -> funcVariousFloatFuncs(fcall, func, functionName)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "sort" -> funcSort(fcall)
            "reverse" -> funcReverse(fcall)
            "rsave" -> {
                // save cpu status flag and all registers A, X, Y.
                // see http://6502.org/tutorials/register_preservation.html
                asmgen.out(" php |  sta  ${C64Zeropage.SCRATCH_REG} | pha  | txa  | pha  | tya  | pha  | lda  ${C64Zeropage.SCRATCH_REG}")
            }
            "rrestore" -> {
                // restore all registers and cpu status flag
                asmgen.out(" pla |  tay |  pla |  tax |  pla |  plp")
            }
            "clear_carry" -> asmgen.out("  clc")
            "set_carry" -> asmgen.out("  sec")
            "clear_irqd" -> asmgen.out("  cli")
            "set_irqd" -> asmgen.out("  sei")
            else -> {
                translateFunctionArguments(fcall.args, func)
                asmgen.out("  jsr  prog8_lib.func_$functionName")
            }
        }
    }

    private fun funcReverse(fcall: IFunctionCall) {
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

    private fun funcSort(fcall: IFunctionCall) {
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
                                    sta  ${C64Zeropage.SCRATCH_B1}
                                """)
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UB) "  jsr  prog8_lib.sort_ub" else "  jsr  prog8_lib.sort_b")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    sty  ${C64Zeropage.SCRATCH_W1 + 1}
                                    lda  #$numElements
                                    sta  ${C64Zeropage.SCRATCH_B1}
                                """)
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UW) "  jsr  prog8_lib.sort_uw" else "  jsr  prog8_lib.sort_w")
                }
                DataType.ARRAY_F -> throw AssemblyError("sorting of floating point array is not supported")
                else -> throw AssemblyError("weird type")
            }
        } else
            throw AssemblyError("weird type")
    }

    private fun funcRor2(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when (what) {
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
                    is IdentifierReference -> {
                        val variable = asmgen.asmIdentifierName(what)
                        asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
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

    private fun funcRor(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when (what) {
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
                                        """)
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
                when (what) {
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

    private fun funcRol2(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when (what) {
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
                    is IdentifierReference -> {
                        val variable = asmgen.asmIdentifierName(what)
                        asmgen.out("  lda  $variable |  cmp  #\$80 |  rol  a |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
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

    private fun funcRol(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> {
                when (what) {
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
                    is IdentifierReference -> {
                        val variable = asmgen.asmIdentifierName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
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

    private fun funcVariousFloatFuncs(fcall: IFunctionCall, func: FSignature, functionName: String) {
        translateFunctionArguments(fcall.args, func)
        asmgen.out("  jsr  c64flt.func_$functionName")
    }

    private fun funcSgn(fcall: IFunctionCall, func: FSignature) {
        translateFunctionArguments(fcall.args, func)
        val dt = fcall.args.single().inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE -> asmgen.out("  jsr  math.sign_ub")
            DataType.BYTE -> asmgen.out("  jsr  math.sign_b")
            DataType.UWORD -> asmgen.out("  jsr  math.sign_uw")
            DataType.WORD -> asmgen.out("  jsr  math.sign_w")
            DataType.FLOAT -> asmgen.out("  jsr  c64flt.sign_f")
            else -> throw AssemblyError("weird type $dt")
        }
    }

    private fun funcAnyAll(fcall: IFunctionCall, functionName: String) {
        outputPushAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${functionName}_b")
            DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${functionName}_w")
            DataType.ARRAY_F -> asmgen.out("  jsr  c64flt.func_${functionName}_f")
            else -> throw AssemblyError("weird type $dt")
        }
    }

    private fun funcMinMaxSum(fcall: IFunctionCall, functionName: String) {
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

    private fun funcStrlen(fcall: IFunctionCall) {
        outputPushAddressOfIdentifier(fcall.args[0])
        asmgen.out("  jsr  prog8_lib.func_strlen")
    }

    private fun funcSwap(fcall: IFunctionCall) {
        val first = fcall.args[0]
        val second = fcall.args[1]
        if(first is IdentifierReference && second is IdentifierReference) {
            val firstName = asmgen.asmIdentifierName(first)
            val secondName = asmgen.asmIdentifierName(second)
            val dt = first.inferType(program)
            if(dt.istype(DataType.BYTE) || dt.istype(DataType.UBYTE)) {
                asmgen.out(" ldy  $firstName |  lda  $secondName |  sta  $firstName |  tya |  sta  $secondName")
                return
            }
            if(dt.istype(DataType.WORD) || dt.istype(DataType.UWORD)) {
                asmgen.out("""
                    ldy  $firstName
                    lda  $secondName
                    sta  $firstName
                    sty  $secondName
                    ldy  $firstName+1
                    lda  $secondName+1
                    sta  $firstName+1
                    sty  $secondName+1
                """)
                return
            }
            if(dt.istype(DataType.FLOAT)) {
                asmgen.out("""
                    lda  #<$firstName
                    sta  ${C64Zeropage.SCRATCH_W1}
                    lda  #>$firstName
                    sta  ${C64Zeropage.SCRATCH_W1+1}
                    lda  #<$secondName
                    sta  ${C64Zeropage.SCRATCH_W2}
                    lda  #>$secondName
                    sta  ${C64Zeropage.SCRATCH_W2+1}
                    jsr  c64flt.swap_floats
                """)
                return
            }
        }

        // other types of swap() calls should have been replaced by a different statement sequence involving a temp variable
        throw AssemblyError("no asm generation for swap funccall $fcall")
    }

    private fun funcAbs(fcall: IFunctionCall, func: FSignature) {
        translateFunctionArguments(fcall.args, func)
        val dt = fcall.args.single().inferType(program)
        when (dt.typeOrElse(DataType.STRUCT)) {
            in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b")
            in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w")
            DataType.FLOAT -> asmgen.out("  jsr  c64flt.abs_f")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcMkword(fcall: IFunctionCall, func: FSignature) {
        translateFunctionArguments(fcall.args, func)
        asmgen.out("  inx | lda  $ESTACK_LO_HEX,x  | sta  $ESTACK_HI_PLUS1_HEX,x")
    }

    private fun funcMsb(fcall: IFunctionCall) {
        val arg = fcall.args.single()
        if (arg.inferType(program).typeOrElse(DataType.STRUCT) !in WordDatatypes)
            throw AssemblyError("msb required word argument")
        if (arg is NumericLiteralValue)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmIdentifierName(arg)
            asmgen.out("  lda  $sourceName+1 |  sta  $ESTACK_LO_HEX,x |  dex")
        } else {
            asmgen.translateExpression(arg)
            asmgen.out("  lda  $ESTACK_HI_PLUS1_HEX,x |  sta  $ESTACK_LO_PLUS1_HEX,x")
        }
    }

    private fun funcLsb(fcall: IFunctionCall) {
        val arg = fcall.args.single()
        if (arg.inferType(program).typeOrElse(DataType.STRUCT) !in WordDatatypes)
            throw AssemblyError("lsb required word argument")
        if (arg is NumericLiteralValue)
            throw AssemblyError("lsb(const) should have been const-folded away")
        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmIdentifierName(arg)
            asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  dex")
        } else {
            asmgen.translateExpression(arg)
            // just ignore any high-byte
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
