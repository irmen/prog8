package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.DirectMemoryWrite
import prog8.ast.statements.FunctionCallStatement
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.c64.codegen.assignment.AsmAssignSource
import prog8.compiler.target.c64.codegen.assignment.AsmAssignTarget
import prog8.compiler.target.c64.codegen.assignment.AsmAssignment
import prog8.compiler.target.c64.codegen.assignment.SourceStorageKind
import prog8.compiler.target.c64.codegen.assignment.TargetStorageKind
import prog8.compiler.toHex
import prog8.functions.FSignature

internal class BuiltinFunctionsAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctioncallExpression(fcall: FunctionCall, func: FSignature, resultToStack: Boolean) {
        translateFunctioncall(fcall, func, discardResult = false, resultToStack = resultToStack)
    }

    internal fun translateFunctioncallStatement(fcall: FunctionCallStatement, func: FSignature) {
        translateFunctioncall(fcall, func, discardResult = true, resultToStack = false)
    }

    private fun translateFunctioncall(fcall: IFunctionCall, func: FSignature, discardResult: Boolean, resultToStack: Boolean) {
        if (discardResult && func.pure)
            return  // can just ignore the whole function call altogether

        if(discardResult && resultToStack)
            throw AssemblyError("cannot both discard the result AND put it onto stack")

        when (func.name) {
            "msb" -> funcMsb(fcall, resultToStack)
            "lsb" -> funcLsb(fcall, resultToStack)
            "mkword" -> funcMkword(fcall, resultToStack)
            "abs" -> funcAbs(fcall, func, resultToStack)
            "swap" -> funcSwap(fcall)
            "min", "max" -> funcMinMax(fcall, func, resultToStack)
            "sum" -> funcSum(fcall, resultToStack)
            "any", "all" -> funcAnyAll(fcall, func, resultToStack)
            "sin8", "sin8u", "sin16", "sin16u",
            "cos8", "cos8u", "cos16", "cos16u" -> funcSinCosInt(fcall, func, resultToStack)
            "sgn" -> funcSgn(fcall, func, resultToStack)
            "sin", "cos", "tan", "atan",
            "ln", "log2", "sqrt", "rad",
            "deg", "round", "floor", "ceil",
            "rndf" -> funcVariousFloatFuncs(fcall, func, resultToStack)
            "rnd", "rndw" -> funcRnd(func, resultToStack)
            "sqrt16" -> funcSqrt16(fcall, func, resultToStack)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "sort" -> funcSort(fcall)
            "reverse" -> funcReverse(fcall)
            "rsave" -> {
                // save cpu status flag and all registers A, X, Y.
                // see http://6502.org/tutorials/register_preservation.html
                asmgen.out(" php |  sta  P8ZP_SCRATCH_REG | pha  | txa  | pha  | tya  | pha  | lda  P8ZP_SCRATCH_REG")
            }
            "rrestore" -> {
                // restore all registers and cpu status flag
                asmgen.out(" pla |  tay |  pla |  tax |  pla |  plp")
            }
            "read_flags" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_read_flags")
                else
                    asmgen.out("  php |  pla")
            }
            "clear_carry" -> asmgen.out("  clc")
            "set_carry" -> asmgen.out("  sec")
            "clear_irqd" -> asmgen.out("  cli")
            "set_irqd" -> asmgen.out("  sei")
            "strlen" -> funcStrlen(fcall, resultToStack)
            "strcmp" -> funcStrcmp(fcall, func, resultToStack)
            "memcopy", "memset", "memsetw" -> funcMemSetCopy(fcall, func)
            "substr", "leftstr", "rightstr" -> {
                translateArguments(fcall.args, func)
                asmgen.out("  jsr  prog8_lib.func_${func.name}")
            }
            "exit" -> asmgen.out("  jmp  prog8_lib.func_exit")
            else -> TODO("missing asmgen for builtin func ${func.name}")
        }
    }

    private fun funcMemSetCopy(fcall: IFunctionCall, func: FSignature) {
        if(CompilationTarget.instance is Cx16Target) {
            when(func.name) {
                "memset" -> {
                    // use the ROM function of the Cx16
                    var src = AsmAssignSource.fromAstSource(fcall.args[0], program, asmgen)
                    var tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, DataType.UWORD, null, variableAsmName = "cx16.r0")
                    var assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    src = AsmAssignSource.fromAstSource(fcall.args[1], program, asmgen)
                    tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, DataType.UWORD, null, variableAsmName = "cx16.r1")
                    assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    src = AsmAssignSource.fromAstSource(fcall.args[2], program, asmgen)
                    tgt = AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, DataType.UBYTE, null, register = RegisterOrPair.A)
                    assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    val sub = (fcall as FunctionCallStatement).definingSubroutine()!!
                    asmgen.saveRegister(CpuRegister.X, false, sub)
                    asmgen.out("  jsr  cx16.memory_fill")
                    asmgen.restoreRegister(CpuRegister.X, false)
                }
                "memcopy" -> {
                    val count = fcall.args[2].constValue(program)?.number?.toInt()
                    val countDt = fcall.args[2].inferType(program)
                    if((count!=null && count <= 255) || countDt.istype(DataType.UBYTE) || countDt.istype(DataType.BYTE)) {
                        // fast memcopy of up to 255
                        translateArguments(fcall.args, func)
                        asmgen.out("  jsr  prog8_lib.func_memcopy255")
                        return
                    }

                    // use the ROM function of the Cx16
                    var src = AsmAssignSource.fromAstSource(fcall.args[0], program, asmgen)
                    var tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, DataType.UWORD, null, variableAsmName = "cx16.r0")
                    var assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    src = AsmAssignSource.fromAstSource(fcall.args[1], program, asmgen)
                    tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, DataType.UWORD, null, variableAsmName = "cx16.r1")
                    assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    src = AsmAssignSource.fromAstSource(fcall.args[2], program, asmgen)
                    tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, DataType.UWORD, null, variableAsmName = "cx16.r2")
                    assign = AsmAssignment(src, tgt, false, Position.DUMMY)
                    asmgen.translateNormalAssignment(assign)
                    val sub = (fcall as FunctionCallStatement).definingSubroutine()!!
                    asmgen.saveRegister(CpuRegister.X, false, sub)
                    asmgen.out("  jsr  cx16.memory_copy")
                    asmgen.restoreRegister(CpuRegister.X, false)
                }
                "memsetw" -> {
                    translateArguments(fcall.args, func)
                    asmgen.out("  jsr  prog8_lib.func_memsetw")
                }
            }
        } else {
            if(func.name=="memcopy") {
                val count = fcall.args[2].constValue(program)?.number?.toInt()
                val countDt = fcall.args[2].inferType(program)
                if((count!=null && count <= 255) || countDt.istype(DataType.UBYTE) || countDt.istype(DataType.BYTE)) {
                    translateArguments(fcall.args, func)
                    asmgen.out("  jsr  prog8_lib.func_memcopy255")
                    return
                }
            }
            translateArguments(fcall.args, func)
            asmgen.out("  jsr  prog8_lib.func_${func.name}")
        }
    }

    private fun funcStrcmp(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_strcmp")
        else
            asmgen.out("  jsr  prog8_lib.func_strcmp |  inx")       // result is also in register A
    }

    private fun funcSqrt16(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_sqrt16")
        else
            asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
    }

    private fun funcSinCosInt(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_${func.name}")
        else
            when(func.name) {
                "sin8", "sin8u", "cos8", "cos8u" -> asmgen.out("  jsr  prog8_lib.func_${func.name}_into_A")
                "sin16", "sin16u", "cos16", "cos16u" -> asmgen.out("  jsr  prog8_lib.func_${func.name}_into_AY")
            }
    }

    private fun funcReverse(fcall: IFunctionCall) {
        val variable = fcall.args.single()
        if (variable is IdentifierReference) {
            val decl = variable.targetVarDecl(program.namespace)!!
            val varName = asmgen.asmVariableName(variable)
            val numElements = decl.arraysize!!.constIndex()
            when (decl.datatype) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
                                    lda  #$numElements
                                    jsr  prog8_lib.reverse_b
                                """)
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
                                    lda  #$numElements
                                    jsr  prog8_lib.reverse_w
                                """)
                }
                DataType.ARRAY_F -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
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
            val varName = asmgen.asmVariableName(variable)
            val numElements = decl.arraysize!!.constIndex()
            when (decl.datatype) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
                                    lda  #$numElements
                                    sta  P8ZP_SCRATCH_B1
                                """)
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UB) "  jsr  prog8_lib.sort_ub" else "  jsr  prog8_lib.sort_b")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                                    lda  #<$varName
                                    ldy  #>$varName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
                                    lda  #$numElements
                                    sta  P8ZP_SCRATCH_B1
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
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
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
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
                        asmgen.out("  jsr  prog8_lib.ror2_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
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
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
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
                        lda  P8ESTACK_LO,x
                        sta  (+) + 1
                        lda  P8ESTACK_HI,x
                        sta  (+) + 2
    +                   ror  ${'$'}ffff            ; modified                    
                                        """)
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
                        asmgen.out("  jsr  prog8_lib.ror_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
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
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
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
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  cmp  #\$80 |  rol  a |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
                        asmgen.out("  jsr  prog8_lib.rol2_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
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
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
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
                        lda  P8ESTACK_LO,x
                        sta  (+) + 1
                        lda  P8ESTACK_HI,x
                        sta  (+) + 2
    +                   rol  ${'$'}ffff            ; modified                    
                                        """)
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        asmgen.translateExpression(what.arrayvar)
                        asmgen.translateExpression(what.indexer)
                        asmgen.out("  jsr  prog8_lib.rol_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable |  rol  $variable+1")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcVariousFloatFuncs(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        asmgen.out("  jsr  floats.func_${func.name}_into_fac1")
        if(resultToStack)
            asmgen.out("  jsr  floats.push_fac1")
    }

    private fun funcSgn(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.UBYTE -> asmgen.out("  jsr  math.sign_ub")
                DataType.BYTE -> asmgen.out("  jsr  math.sign_b")
                DataType.UWORD -> asmgen.out("  jsr  math.sign_uw")
                DataType.WORD -> asmgen.out("  jsr  math.sign_w")
                DataType.FLOAT -> asmgen.out("  jsr  floats.sign_f")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.UBYTE -> asmgen.out("  jsr  math.sign_ub_into_A")
                DataType.BYTE -> asmgen.out("  jsr  math.sign_b_into_A")
                DataType.UWORD -> asmgen.out("  jsr  math.sign_uw_into_A")
                DataType.WORD -> asmgen.out("  jsr  math.sign_w_into_A")
                DataType.FLOAT -> asmgen.out("  jsr  floats.pop_float_fac1 |  jsr  floats.SIGN")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcAnyAll(fcall: IFunctionCall, function: FSignature, resultToStack: Boolean) {
        outputPushAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b")
                DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_into_A")
                DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_into_A")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_into_A")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcMinMax(fcall: IFunctionCall, function: FSignature, resultToStack: Boolean) {
        outputPushAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_ub")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${function.name}_uw")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_into_fac1 |  jsr  floats.push_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_ub_into_A")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_into_A")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${function.name}_uw_into_AY")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_into_AY")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_into_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcSum(fcall: IFunctionCall, resultToStack: Boolean) {
        outputPushAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_sum_ub")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_sum_b")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_sum_uw")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_sum_w")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_sum_f_into_fac1 |  jsr  floats.push_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_sum_ub_into_AY")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_sum_b_into_AY")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_sum_uw_into_AY")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_sum_w_into_AY")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_sum_f_into_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcStrlen(fcall: IFunctionCall, resultToStack: Boolean) {
        val name = asmgen.asmVariableName(fcall.args[0] as IdentifierReference)
        val type = fcall.args[0].inferType(program)
        when {
            type.istype(DataType.STR) -> {
                asmgen.out("  lda  #<$name |  ldy  #>$name |  jsr  prog8_lib.strlen")
                if(resultToStack)
                    asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            }
            type.istype(DataType.UWORD) -> {
                asmgen.out("  lda  $name |  ldy  $name+1 |  jsr  prog8_lib.strlen")
                if(resultToStack)
                    asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            }
            else -> throw AssemblyError("strlen requires str or uword arg")
        }
    }

    private fun funcSwap(fcall: IFunctionCall) {
        val first = fcall.args[0]
        val second = fcall.args[1]

        // optimized simple case: swap two variables
        if(first is IdentifierReference && second is IdentifierReference) {
            val firstName = asmgen.asmVariableName(first)
            val secondName = asmgen.asmVariableName(second)
            val dt = first.inferType(program)
            if(dt.istype(DataType.BYTE) || dt.istype(DataType.UBYTE)) {
                asmgen.out(" ldy  $firstName |  lda  $secondName |  sta  $firstName |  sty  $secondName")
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
                    sta  P8ZP_SCRATCH_W1
                    lda  #>$firstName
                    sta  P8ZP_SCRATCH_W1+1
                    lda  #<$secondName
                    sta  P8ZP_SCRATCH_W2
                    lda  #>$secondName
                    sta  P8ZP_SCRATCH_W2+1
                    jsr  floats.swap_floats
                """)
                return
            }
        }

        // optimized simple case: swap two memory locations
        if(first is DirectMemoryRead && second is DirectMemoryRead) {
            val addr1 = (first.addressExpression as? NumericLiteralValue)?.number?.toHex()
            val addr2 = (second.addressExpression as? NumericLiteralValue)?.number?.toHex()
            val name1 = if(first.addressExpression is IdentifierReference) asmgen.asmVariableName(first.addressExpression as IdentifierReference) else null
            val name2 = if(second.addressExpression is IdentifierReference) asmgen.asmVariableName(second.addressExpression as IdentifierReference) else null

            when {
                addr1!=null && addr2!=null -> {
                    asmgen.out("  ldy  $addr1 |  lda  $addr2 |  sta  $addr1 |  sty  $addr2")
                    return
                }
                addr1!=null && name2!=null -> {
                    asmgen.out("  ldy  $addr1 |  lda  $name2 |  sta  $addr1 |  sty  $name2")
                    return
                }
                name1!=null && addr2 != null -> {
                    asmgen.out("  ldy  $name1 |  lda  $addr2 |  sta  $name1 |  sty  $addr2")
                    return
                }
                name1!=null && name2!=null -> {
                    asmgen.out("  ldy  $name1 |  lda  $name2 |  sta  $name1 |  sty  $name2")
                    return
                }
            }
        }

        if(first is ArrayIndexedExpression && second is ArrayIndexedExpression) {
            val arrayVarName1 = asmgen.asmVariableName(first.arrayvar)
            val arrayVarName2 = asmgen.asmVariableName(second.arrayvar)
            val elementIDt = first.inferType(program)
            if(!elementIDt.isKnown)
                throw AssemblyError("unknown dt")
            val elementDt = elementIDt.typeOrElse(DataType.STRUCT)

            val firstNum = first.indexer.indexNum
            val firstVar = first.indexer.indexVar
            val secondNum = second.indexer.indexNum
            val secondVar = second.indexer.indexVar

            if(firstNum!=null && secondNum!=null) {
                swapArrayValues(elementDt, arrayVarName1, firstNum, arrayVarName2, secondNum)
                return
            } else if(firstVar!=null && secondVar!=null) {
                swapArrayValues(elementDt, arrayVarName1, firstVar, arrayVarName2, secondVar)
                return
            } else if(firstNum!=null && secondVar!=null) {
                swapArrayValues(elementDt, arrayVarName1, firstNum, arrayVarName2, secondVar)
                return
            } else if(firstVar!=null && secondNum!=null) {
                swapArrayValues(elementDt, arrayVarName1, firstVar, arrayVarName2, secondNum)
                return
            }
        }

        // all other types of swap() calls are done via the evaluation stack
        fun targetFromExpr(expr: Expression, datatype: DataType): AsmAssignTarget {
            return when (expr) {
                is IdentifierReference -> AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, datatype, expr.definingSubroutine(), variableAsmName = asmgen.asmVariableName(expr))
                is ArrayIndexedExpression -> AsmAssignTarget(TargetStorageKind.ARRAY, program, asmgen, datatype, expr.definingSubroutine(), array = expr)
                is DirectMemoryRead -> AsmAssignTarget(TargetStorageKind.MEMORY, program, asmgen, datatype, expr.definingSubroutine(), memory = DirectMemoryWrite(expr.addressExpression, expr.position))
                else -> throw AssemblyError("invalid expression object $expr")
            }
        }

        asmgen.translateExpression(first)
        asmgen.translateExpression(second)
        val idatatype = first.inferType(program)
        if(!idatatype.isKnown)
            throw AssemblyError("unknown dt")
        val datatype = idatatype.typeOrElse(DataType.STRUCT)
        val assignFirst = AsmAssignment(
                AsmAssignSource(SourceStorageKind.STACK, program, asmgen, datatype),
                targetFromExpr(first, datatype),
                false, first.position
        )
        val assignSecond = AsmAssignment(
                AsmAssignSource(SourceStorageKind.STACK, program, asmgen, datatype),
                targetFromExpr(second, datatype),
                false, second.position
        )
        asmgen.translateNormalAssignment(assignFirst)
        asmgen.translateNormalAssignment(assignSecond)
    }

    private fun swapArrayValues(elementDt: DataType, arrayVarName1: String, indexValue1: NumericLiteralValue, arrayVarName2: String, indexValue2: NumericLiteralValue) {
        val index1 = indexValue1.number.toInt() * elementDt.memorySize()
        val index2 = indexValue2.number.toInt() * elementDt.memorySize()
        when(elementDt) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("""
                    lda  $arrayVarName1+$index1
                    ldy  $arrayVarName2+$index2
                    sta  $arrayVarName2+$index2
                    sty  $arrayVarName1+$index1
                """)
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("""
                    lda  $arrayVarName1+$index1
                    ldy  $arrayVarName2+$index2
                    sta  $arrayVarName2+$index2
                    sty  $arrayVarName1+$index1
                    lda  $arrayVarName1+$index1+1
                    ldy  $arrayVarName2+$index2+1
                    sta  $arrayVarName2+$index2+1
                    sty  $arrayVarName1+$index1+1
                """)
            }
            DataType.FLOAT -> {
                asmgen.out("""
                    lda  #<(${arrayVarName1}+$index1)
                    sta  P8ZP_SCRATCH_W1
                    lda  #>(${arrayVarName1}+$index1)
                    sta  P8ZP_SCRATCH_W1+1
                    lda  #<(${arrayVarName2}+$index2)
                    sta  P8ZP_SCRATCH_W2
                    lda  #>(${arrayVarName2}+$index2)
                    sta  P8ZP_SCRATCH_W2+1
                    jsr  floats.swap_floats
                """)
            }
            else -> throw AssemblyError("invalid aray elt type")
        }
    }

    private fun swapArrayValues(elementDt: DataType, arrayVarName1: String, indexName1: IdentifierReference, arrayVarName2: String, indexName2: IdentifierReference) {
        val idxAsmName1 = asmgen.asmVariableName(indexName1)
        val idxAsmName2 = asmgen.asmVariableName(indexName2)
        when(elementDt) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("""
                    stx  P8ZP_SCRATCH_REG
                    ldx  $idxAsmName1
                    ldy  $idxAsmName2
                    lda  $arrayVarName1,x
                    pha
                    lda  $arrayVarName2,y
                    sta  $arrayVarName1,x
                    pla
                    sta  $arrayVarName2,y
                    ldx  P8ZP_SCRATCH_REG
                """)
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("""
                    stx  P8ZP_SCRATCH_REG
                    lda  $idxAsmName1
                    asl  a
                    tax
                    lda  $idxAsmName2
                    asl  a
                    tay
                    lda  $arrayVarName1,x
                    pha
                    lda  $arrayVarName2,y
                    sta  $arrayVarName1,x
                    pla
                    sta  $arrayVarName2,y
                    lda  $arrayVarName1+1,x
                    pha
                    lda  $arrayVarName2+1,y
                    sta  $arrayVarName1+1,x
                    pla
                    sta  $arrayVarName2+1,y
                    ldx  P8ZP_SCRATCH_REG
                """)
            }
            DataType.FLOAT -> {
                asmgen.out("""
                    lda  #>$arrayVarName1
                    sta  P8ZP_SCRATCH_W1+1
                    lda  $idxAsmName1
                    asl  a
                    asl  a
                    clc
                    adc  $idxAsmName1
                    adc  #<$arrayVarName1
                    sta  P8ZP_SCRATCH_W1
                    bcc  +
                    inc  P8ZP_SCRATCH_W1+1
+                   lda  #>$arrayVarName2
                    sta  P8ZP_SCRATCH_W2+1
                    lda  $idxAsmName2
                    asl  a
                    asl  a
                    clc
                    adc  $idxAsmName2
                    adc  #<$arrayVarName2
                    sta  P8ZP_SCRATCH_W2
                    bcc  +
                    inc  P8ZP_SCRATCH_W2+1
+                   jsr  floats.swap_floats                                   
                """)
            }
            else -> throw AssemblyError("invalid aray elt type")
        }
    }

    private fun swapArrayValues(elementDt: DataType, arrayVarName1: String, indexValue1: NumericLiteralValue, arrayVarName2: String, indexName2: IdentifierReference) {
        val index1 = indexValue1.number.toInt() * elementDt.memorySize()
        val idxAsmName2 = asmgen.asmVariableName(indexName2)
        when(elementDt) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("""
                    lda  $arrayVarName1 + $index1
                    pha
                    ldy  $idxAsmName2
                    lda  $arrayVarName2,y
                    sta  $arrayVarName1 + $index1
                    pla
                    sta  $arrayVarName2,y
                """)
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("""
                    lda  $arrayVarName1 + $index1
                    pha
                    lda  $idxAsmName2
                    asl  a
                    tay
                    lda  $arrayVarName2,y
                    sta  $arrayVarName1 + $index1
                    pla
                    sta  $arrayVarName2,y
                    lda  $arrayVarName1 + $index1+1
                    pha
                    lda  $arrayVarName2+1,y
                    sta  $arrayVarName1 + $index1+1
                    pla
                    sta  $arrayVarName2+1,y
                """)
            }
            DataType.FLOAT -> {
                asmgen.out("""
                    lda  #<(${arrayVarName1}+$index1)
                    sta  P8ZP_SCRATCH_W1
                    lda  #>(${arrayVarName1}+$index1)
                    sta  P8ZP_SCRATCH_W1+1
                    lda  #>$arrayVarName1
                    sta  P8ZP_SCRATCH_W1+1
                    lda  $idxAsmName2
                    asl  a
                    asl  a
                    clc
                    adc  $idxAsmName2
                    adc  #<$arrayVarName1
                    sta  P8ZP_SCRATCH_W1
                    bcc  +
                    inc  P8ZP_SCRATCH_W1+1
+                   jsr  floats.swap_floats
                """)
            }
            else -> throw AssemblyError("invalid aray elt type")
        }
    }

    private fun swapArrayValues(elementDt: DataType, arrayVarName1: String, indexName1: IdentifierReference, arrayVarName2: String, indexValue2: NumericLiteralValue) {
        val idxAsmName1 = asmgen.asmVariableName(indexName1)
        val index2 = indexValue2.number.toInt() * elementDt.memorySize()
        when(elementDt) {
            DataType.UBYTE, DataType.BYTE -> {
                asmgen.out("""
                    lda  $arrayVarName2 + $index2
                    pha
                    ldy  $idxAsmName1
                    lda  $arrayVarName1,y
                    sta  $arrayVarName2 + $index2
                    pla
                    sta  $arrayVarName1,y
                """)
            }
            DataType.UWORD, DataType.WORD -> {
                asmgen.out("""
                    lda  $arrayVarName2 + $index2
                    pha
                    lda  $idxAsmName1
                    asl  a
                    tay
                    lda  $arrayVarName1,y
                    sta  $arrayVarName2 + $index2
                    pla
                    sta  $arrayVarName1,y
                    lda  $arrayVarName2 + $index2+1
                    pha
                    lda  $arrayVarName1+1,y
                    sta  $arrayVarName2 + $index2+1
                    pla
                    sta  $arrayVarName1+1,y
                """)
            }
            DataType.FLOAT -> {
                asmgen.out("""
                    lda  #>$arrayVarName1
                    sta  P8ZP_SCRATCH_W1+1
                    lda  $idxAsmName1
                    asl  a
                    asl  a
                    clc
                    adc  $idxAsmName1
                    adc  #<$arrayVarName1
                    sta  P8ZP_SCRATCH_W1
                    bcc  +
                    inc  P8ZP_SCRATCH_W1+1
+                   lda  #<(${arrayVarName2}+$index2)
                    sta  P8ZP_SCRATCH_W2
                    lda  #>(${arrayVarName2}+$index2)
                    sta  P8ZP_SCRATCH_W2+1
                    jsr  floats.swap_floats
                """)
            }
            else -> throw AssemblyError("invalid aray elt type")
        }
    }

    private fun funcAbs(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean) {
        translateArguments(fcall.args, func)
        val dt = fcall.args.single().inferType(program).typeOrElse(DataType.STRUCT)
        if(resultToStack) {
            when (dt) {
                in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b")
                in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w")
                DataType.FLOAT -> asmgen.out("  jsr  floats.abs_f")
                else -> throw AssemblyError("weird type")
            }
        } else {
            when (dt) {
                in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b_into_A")
                in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                DataType.FLOAT -> asmgen.out("  jsr  floats.abs_f_into_fac1")
                else -> throw AssemblyError("weird type")
            }
        }
    }

    private fun funcRnd(func: FSignature, resultToStack: Boolean) {
        when(func.name) {
            "rnd" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rnd")
                else
                    asmgen.out("  jsr  math.randbyte")
            }
            "rndw" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rndw")
                else
                    asmgen.out("  jsr  math.randword")
            }
            else -> throw AssemblyError("wrong func")
        }
    }

    private fun funcMkword(fcall: IFunctionCall, resultToStack: Boolean) {
        if(resultToStack) {
            // trick: push the args in reverse order (lsb first, msb second) this saves some instructions
            asmgen.translateExpression(fcall.args[1])
            asmgen.translateExpression(fcall.args[0])
            asmgen.out("  inx | lda  P8ESTACK_LO,x  | sta  P8ESTACK_HI+1,x")
        } else {
            // TODO some args without stack usage...
            // trick: push the args in reverse order (lsb first, msb second) this saves some instructions
            asmgen.translateExpression(fcall.args[1])
            asmgen.translateExpression(fcall.args[0])
            asmgen.out("  inx |  ldy  P8ESTACK_LO,x  |  inx |  lda  P8ESTACK_LO,x")
        }
    }

    private fun funcMsb(fcall: IFunctionCall, resultToStack: Boolean) {
        val arg = fcall.args.single()
        if (arg.inferType(program).typeOrElse(DataType.STRUCT) !in WordDatatypes)
            throw AssemblyError("msb required word argument")
        if (arg is NumericLiteralValue)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmVariableName(arg)
            asmgen.out("  lda  $sourceName+1")
            if(resultToStack)
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
        } else {
            asmgen.translateExpression(arg)  // TODO this evalutes onto stack, use registers instead
            if(resultToStack)
                asmgen.out("  lda  P8ESTACK_HI+1,x |  sta  P8ESTACK_LO+1,x")
            else
                asmgen.out("  inx |  lda  P8ESTACK_HI,x")
        }
    }

    private fun funcLsb(fcall: IFunctionCall, resultToStack: Boolean) {
        val arg = fcall.args.single()
        if (arg.inferType(program).typeOrElse(DataType.STRUCT) !in WordDatatypes)
            throw AssemblyError("lsb required word argument")
        if (arg is NumericLiteralValue)
            throw AssemblyError("lsb(const) should have been const-folded away")
        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmVariableName(arg)
            asmgen.out("  lda  $sourceName")
            if(resultToStack)
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
        } else {
            // TODO this evalutes onto stack, use registers instead
            asmgen.translateExpression(arg)
            if(resultToStack) {
                // simply ignore the high-byte of what's on the stack now
            } else {
                asmgen.out("  inx |  lda  P8ESTACK_LO,x")
            }
        }
    }

    private fun outputPushAddressAndLenghtOfArray(arg: Expression) {
        arg as IdentifierReference
        val identifierName = asmgen.asmVariableName(arg)
        val size = arg.targetVarDecl(program.namespace)!!.arraysize!!.constIndex()!!
        asmgen.out("""
                    lda  #<$identifierName
                    sta  P8ESTACK_LO,x
                    lda  #>$identifierName
                    sta  P8ESTACK_HI,x
                    dex
                    lda  #$size
                    sta  P8ESTACK_LO,x
                    dex
                    """)
    }

    private fun translateArguments(args: MutableList<Expression>, signature: FSignature) {
        val callConv = signature.callConvention(args.map { it.inferType(program).typeOrElse(DataType.STRUCT) })
        print("ARGS FOR ${signature.name} -> CALLCONV = $callConv") // TODO actually use the call convention

        args.forEach {
            asmgen.translateExpression(it)      // TODO if possible, function args via registers
        }
    }

}
