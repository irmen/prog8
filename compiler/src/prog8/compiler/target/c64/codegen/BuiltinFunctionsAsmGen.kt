package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.Cx16Target
import prog8.compiler.target.c64.codegen.assignment.AsmAssignSource
import prog8.compiler.target.c64.codegen.assignment.AsmAssignTarget
import prog8.compiler.target.c64.codegen.assignment.AsmAssignment
import prog8.compiler.target.c64.codegen.assignment.SourceStorageKind
import prog8.compiler.target.c64.codegen.assignment.TargetStorageKind
import prog8.compiler.target.subroutineFloatEvalResultVar2
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

        val scope = (fcall as Node).definingSubroutine()!!
        when (func.name) {
            "msb" -> funcMsb(fcall, resultToStack)
            "lsb" -> funcLsb(fcall, resultToStack)
            "mkword" -> funcMkword(fcall, resultToStack)
            "abs" -> funcAbs(fcall, func, resultToStack, scope)
            "swap" -> funcSwap(fcall)
            "min", "max" -> funcMinMax(fcall, func, resultToStack)
            "sum" -> funcSum(fcall, resultToStack)
            "any", "all" -> funcAnyAll(fcall, func, resultToStack)
            "sin8", "sin8u", "sin16", "sin16u",
            "cos8", "cos8u", "cos16", "cos16u" -> funcSinCosInt(fcall, func, resultToStack, scope)
            "sgn" -> funcSgn(fcall, func, resultToStack, scope)
            "sin", "cos", "tan", "atan",
            "ln", "log2", "sqrt", "rad",
            "deg", "round", "floor", "ceil",
            "rndf" -> funcVariousFloatFuncs(fcall, func, resultToStack, scope)
            "rnd", "rndw" -> funcRnd(func, resultToStack)
            "sqrt16" -> funcSqrt16(fcall, func, resultToStack, scope)
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
                    asmgen.out("  jsr  prog8_lib.func_read_flags_stack")
                else
                    asmgen.out("  php |  pla")
            }
            "clear_carry" -> asmgen.out("  clc")
            "set_carry" -> asmgen.out("  sec")
            "clear_irqd" -> asmgen.out("  cli")
            "set_irqd" -> asmgen.out("  sei")
            "strlen" -> funcStrlen(fcall, resultToStack)
            "strcmp" -> funcStrcmp(fcall, func, resultToStack, scope)
            "strcopy" -> {
                translateArguments(fcall.args, func, scope)
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_strcopy_to_stack")
                else
                    asmgen.out("  jsr  prog8_lib.func_strcopy")
            }
            "memcopy", "memset", "memsetw" -> funcMemSetCopy(fcall, func, scope)
            "substr", "leftstr", "rightstr" -> {
                translateArguments(fcall.args, func, scope)
                asmgen.out("  jsr  prog8_lib.func_${func.name}")
            }
            "exit" -> {
                translateArguments(fcall.args, func, scope)
                asmgen.out("  jmp  prog8_lib.func_exit")
            }
            "progend" -> {
                if(resultToStack)
                    asmgen.out("""
                        lda  #<prog8_program_end
                        sta  P8ESTACK_LO,x
                        lda  #>prog8_program_end
                        sta  P8ESTACK_HI,x
                        dex""")
                else
                    asmgen.out("  lda  #<prog8_program_end |  ldy  #>prog8_program_end")
            }
            else -> TODO("missing asmgen for builtin func ${func.name}")
        }
    }

    private fun funcMemSetCopy(fcall: IFunctionCall, func: FSignature, scope: Subroutine) {
        if(CompilationTarget.instance is Cx16Target) {
            when(func.name) {
                "memset" -> {
                    // use the ROM function of the Cx16
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.R0)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.R1)
                    asmgen.assignExpressionToRegister(fcall.args[2], RegisterOrPair.A)
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
                        translateArguments(fcall.args, func, scope)
                        asmgen.out("  jsr  prog8_lib.func_memcopy255")
                        return
                    }

                    // use the ROM function of the Cx16
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.R0)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.R1)
                    asmgen.assignExpressionToRegister(fcall.args[2], RegisterOrPair.R2)
                    val sub = (fcall as FunctionCallStatement).definingSubroutine()!!
                    asmgen.saveRegister(CpuRegister.X, false, sub)
                    asmgen.out("  jsr  cx16.memory_copy")
                    asmgen.restoreRegister(CpuRegister.X, false)
                }
                "memsetw" -> {
                    translateArguments(fcall.args, func, scope)
                    asmgen.out("  jsr  prog8_lib.func_memsetw")
                }
            }
        } else {
            if(func.name=="memcopy") {
                val count = fcall.args[2].constValue(program)?.number?.toInt()
                val countDt = fcall.args[2].inferType(program)
                if((count!=null && count <= 255) || countDt.istype(DataType.UBYTE) || countDt.istype(DataType.BYTE)) {
                    translateArguments(fcall.args, func, scope)
                    asmgen.out("  jsr  prog8_lib.func_memcopy255")
                    return
                }
            }
            translateArguments(fcall.args, func, scope)
            asmgen.out("  jsr  prog8_lib.func_${func.name}")
        }
    }

    private fun funcStrcmp(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_strcmp_stack")
        else
            asmgen.out("  jsr  prog8_lib.func_strcmp")
    }

    private fun funcSqrt16(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_sqrt16_stack")
        else
            asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
    }

    private fun funcSinCosInt(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_${func.name}_stack")
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
                        jsr  prog8_lib.func_reverse_b""")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements
                        jsr  prog8_lib.func_reverse_w""")
                }
                DataType.ARRAY_F -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements
                        jsr  floats.func_reverse_f""")
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
                        lda  #$numElements""")
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UB) "  jsr  prog8_lib.func_sort_ub" else "  jsr  prog8_lib.func_sort_b")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements""")
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UW) "  jsr  prog8_lib.func_sort_uw" else "  jsr  prog8_lib.func_sort_w")
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror2", 'b')
                        asmgen.out("  jsr  prog8_lib.ror2_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteralValue) {
                            val number = (what.addressExpression as NumericLiteralValue).number
                            asmgen.out("  lda  ${number.toHex()} |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror2", 'w')
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror", 'b')
                        asmgen.out("  jsr  prog8_lib.ror_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteralValue) {
                            val number = (what.addressExpression as NumericLiteralValue).number
                            asmgen.out("  ror  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                            asmgen.out("""
                                sta  (+) + 1
                                sty  (+) + 2
+                               ror  ${'$'}ffff            ; modified""")
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror", 'w')
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol2", 'b')
                        asmgen.out("  jsr  prog8_lib.rol2_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteralValue) {
                            val number = (what.addressExpression as NumericLiteralValue).number
                            asmgen.out("  lda  ${number.toHex()} |  cmp  #\$80 |  rol  a |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol2", 'w')
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol", 'b')
                        asmgen.out("  jsr  prog8_lib.rol_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteralValue) {
                            val number = (what.addressExpression as NumericLiteralValue).number
                            asmgen.out("  rol  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                            asmgen.out("""
                                sta  (+) + 1
                                sty  (+) + 2
+                               rol  ${'$'}ffff            ; modified""")
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
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol", 'w')
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

    private fun translateRolRorArrayArgs(arrayvar: IdentifierReference, indexer: ArrayIndex, operation: String, dt: Char) {
        asmgen.assignExpressionToVariable(AddressOf(arrayvar, arrayvar.position), "prog8_lib.${operation}_array_u${dt}._arg_target", DataType.UWORD, null)
        val indexerExpr = if(indexer.indexVar!=null) indexer.indexVar!! else indexer.indexNum!!
        asmgen.assignExpressionToVariable(indexerExpr, "prog8_lib.${operation}_array_u${dt}._arg_index", DataType.UBYTE, null)
    }

    private fun funcVariousFloatFuncs(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        if(resultToStack)
            asmgen.out("  jsr  floats.func_${func.name}_stack")
        else
            asmgen.out("  jsr  floats.func_${func.name}_fac1")
    }

    private fun funcSgn(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_stack")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_stack")
                DataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_stack")
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_stack")
                DataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_stack")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_into_A")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_into_A")
                DataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_into_A")
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_into_A")
                DataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_into_A")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcAnyAll(fcall: IFunctionCall, function: FSignature, resultToStack: Boolean) {
        outputAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_stack")
                DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_stack")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_stack")
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
        outputAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_ub_stack")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_stack")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${function.name}_uw_stack")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_stack")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_stack")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_ub_into_A")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_into_A")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_${function.name}_uw_into_AY")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_into_AY")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcSum(fcall: IFunctionCall, resultToStack: Boolean) {
        outputAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_sum_ub_stack")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_sum_b_stack")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_sum_uw_stack")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_sum_w_stack")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_sum_f_stack")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.typeOrElse(DataType.STRUCT)) {
                DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_sum_ub_into_AY")
                DataType.ARRAY_B -> asmgen.out("  jsr  prog8_lib.func_sum_b_into_AY")
                DataType.ARRAY_UW -> asmgen.out("  jsr  prog8_lib.func_sum_uw_into_AY")
                DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_sum_w_into_AY")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_sum_f_fac1")
                else -> throw AssemblyError("weird type $dt")
            }
        }
    }

    private fun funcStrlen(fcall: IFunctionCall, resultToStack: Boolean) {
        if (fcall.args[0] is IdentifierReference) {
            // use the address of the variable
            val name = asmgen.asmVariableName(fcall.args[0] as IdentifierReference)
            val type = fcall.args[0].inferType(program)
            when {
                type.istype(DataType.STR) -> asmgen.out("  lda  #<$name |  ldy  #>$name")
                type.istype(DataType.UWORD) -> asmgen.out("  lda  $name |  ldy  $name+1")
                else -> throw AssemblyError("strlen requires str or uword arg")
            }
        }
        else {
            // use the expression value as address of the string
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
        }
        if (resultToStack)
            asmgen.out("  jsr  prog8_lib.func_strlen_stack")
        else
            asmgen.out("  jsr  prog8_lib.func_strlen_into_A")
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
                    jsr  floats.func_swap_f
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

        // all other types of swap() calls are done via a temporary variable

        fun targetFromExpr(expr: Expression, datatype: DataType): AsmAssignTarget {
            return when (expr) {
                is IdentifierReference -> AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, datatype, expr.definingSubroutine(), variableAsmName = asmgen.asmVariableName(expr))
                is ArrayIndexedExpression -> AsmAssignTarget(TargetStorageKind.ARRAY, program, asmgen, datatype, expr.definingSubroutine(), array = expr)
                is DirectMemoryRead -> AsmAssignTarget(TargetStorageKind.MEMORY, program, asmgen, datatype, expr.definingSubroutine(), memory = DirectMemoryWrite(expr.addressExpression, expr.position))
                else -> throw AssemblyError("invalid expression object $expr")
            }
        }

        val datatype = first.inferType(program).typeOrElse(DataType.STRUCT)
        when(datatype) {
            in ByteDatatypes, in WordDatatypes -> {
                asmgen.assignExpressionToVariable(first, "P8ZP_SCRATCH_W1", datatype, null)
                asmgen.assignExpressionToVariable(second, "P8ZP_SCRATCH_W2", datatype, null)
                val assignFirst = AsmAssignment(
                        AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, datatype, variableAsmName = "P8ZP_SCRATCH_W2"),
                        targetFromExpr(first, datatype),
                        false, first.position
                )
                val assignSecond = AsmAssignment(
                        AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, datatype, variableAsmName = "P8ZP_SCRATCH_W1"),
                        targetFromExpr(second, datatype),
                        false, second.position
                )
                asmgen.translateNormalAssignment(assignFirst)
                asmgen.translateNormalAssignment(assignSecond)
            }
            DataType.FLOAT -> {
                // via evaluation stack
                asmgen.translateExpression(first)
                asmgen.translateExpression(second)
                val assignFirst = AsmAssignment(
                        AsmAssignSource(SourceStorageKind.STACK, program, asmgen, DataType.FLOAT),
                        targetFromExpr(first, datatype),
                        false, first.position
                )
                val assignSecond = AsmAssignment(
                        AsmAssignSource(SourceStorageKind.STACK, program, asmgen, DataType.FLOAT),
                        targetFromExpr(second, datatype),
                        false, second.position
                )
                asmgen.translateNormalAssignment(assignFirst)
                asmgen.translateNormalAssignment(assignSecond)
            }
            else -> throw AssemblyError("weird swap dt")
        }
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

    private fun funcAbs(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, scope: Subroutine) {
        translateArguments(fcall.args, func, scope)
        val dt = fcall.args.single().inferType(program).typeOrElse(DataType.STRUCT)
        if(resultToStack) {
            when (dt) {
                in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b_stack")
                in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w_stack")
                DataType.FLOAT -> asmgen.out("  jsr  floats.abs_f_stack")
                else -> throw AssemblyError("weird type")
            }
        } else {
            when (dt) {
                in ByteDatatypes -> asmgen.out("  jsr  prog8_lib.abs_b_into_A")
                in WordDatatypes -> asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                DataType.FLOAT -> asmgen.out("  jsr  floats.abs_f_fac1")
                else -> throw AssemblyError("weird type")
            }
        }
    }

    private fun funcRnd(func: FSignature, resultToStack: Boolean) {
        when(func.name) {
            "rnd" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rnd_stack")
                else
                    asmgen.out("  jsr  math.randbyte")
            }
            "rndw" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rndw_stack")
                else
                    asmgen.out("  jsr  math.randword")
            }
            else -> throw AssemblyError("wrong func")
        }
    }

    private fun funcMkword(fcall: IFunctionCall, resultToStack: Boolean) {
        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
        if(resultToStack)
            asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
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
            if (resultToStack)
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
        } else {
            asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
            if (resultToStack)
                asmgen.out("  tya |  sta  P8ESTACK_LO,x |  dex")
            else
                asmgen.out("  tya")
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
            if (resultToStack)
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
        } else {
            asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
            if (resultToStack)
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
        }
    }

    private fun outputAddressAndLenghtOfArray(arg: Expression) {
        // address in P8ZP_SCRATCH_W1,  number of elements in A
        arg as IdentifierReference
        val identifierName = asmgen.asmVariableName(arg)
        val size = arg.targetVarDecl(program.namespace)!!.arraysize!!.constIndex()!!
        asmgen.out("""
                    lda  #<$identifierName
                    ldy  #>$identifierName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #$size
                    """)
    }

    private fun translateArguments(args: MutableList<Expression>, signature: FSignature, scope: Subroutine) {
        val callConv = signature.callConvention(args.map { it.inferType(program).typeOrElse(DataType.STRUCT) })

        fun getSourceForFloat(value: Expression): AsmAssignSource {
            return when (value) {
                is IdentifierReference -> {
                    val addr = AddressOf(value, value.position)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
                is NumericLiteralValue -> {
                    throw AssemblyError("float literals should have been converted into autovar")
                }
                else -> {
                    scope.asmGenInfo.usedFloatEvalResultVar2 = true
                    val variable = IdentifierReference(listOf(subroutineFloatEvalResultVar2), value.position)
                    val addr = AddressOf(variable, value.position)
                    addr.linkParents(value)
                    asmgen.assignExpressionToVariable(value, asmgen.asmVariableName(variable), DataType.FLOAT, scope)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
            }
        }

        args.zip(callConv.params).zip(signature.parameters).forEach {
            val paramName = it.second.name
            val conv = it.first.second
            val value = it.first.first
            when {
                conv.variable -> {
                    val varname = "prog8_lib.func_${signature.name}._arg_${paramName}"
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = AddressOf(value as IdentifierReference, value.position)
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, conv.dt, null, variableAsmName = varname)
                    val assign = AsmAssignment(src, tgt, false, value.position)
                    asmgen.translateNormalAssignment(assign)
                }
                conv.reg != null -> {
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = AddressOf(value as IdentifierReference, value.position)
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget.fromRegisters(conv.reg, null, program, asmgen)
                    val assign = AsmAssignment(src, tgt, false, value.position)
                    asmgen.translateNormalAssignment(assign)
                }
                else -> throw AssemblyError("callconv")
            }
        }
    }

}
