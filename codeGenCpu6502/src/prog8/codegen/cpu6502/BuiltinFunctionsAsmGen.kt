package prog8.codegen.cpu6502

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.*


internal class BuiltinFunctionsAsmGen(private val program: PtProgram,
                                      private val asmgen: AsmGen6502Internal,
                                      private val assignAsmGen: AssignmentAsmGen) {

    internal fun translateFunctioncallExpression(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?): DataType? {
        return translateFunctioncall(fcall, discardResult = false, resultRegister = resultRegister)
    }

    internal fun translateFunctioncallStatement(fcall: PtBuiltinFunctionCall) {
        translateFunctioncall(fcall, discardResult = true, resultRegister = null)
    }

    private fun translateFunctioncall(fcall: PtBuiltinFunctionCall, discardResult: Boolean, resultRegister: RegisterOrPair?): DataType? {
        if (discardResult && fcall.hasNoSideEffects)
            return null  // can just ignore the whole function call altogether

        val sscope = fcall.definingISub()

        when (fcall.name) {
            "msb" -> funcMsb(fcall, resultRegister)
            "lsb" -> funcLsb(fcall, resultRegister)
            "mkword" -> funcMkword(fcall, resultRegister)
            "clamp__byte", "clamp__ubyte", "clamp__word", "clamp__uword" -> funcClamp(fcall, resultRegister)
            "min__byte", "min__ubyte", "min__word", "min__uword" -> funcMin(fcall, resultRegister)
            "max__byte", "max__ubyte", "max__word", "max__uword" -> funcMax(fcall, resultRegister)
            "abs__byte", "abs__word", "abs__float" -> funcAbs(fcall, resultRegister, sscope)
            "any", "all" -> funcAnyAll(fcall, resultRegister, sscope)
            "sgn" -> funcSgn(fcall, resultRegister, sscope)
            "sqrt__ubyte", "sqrt__uword", "sqrt__float" -> funcSqrt(fcall, resultRegister, sscope)
            "divmod__ubyte" -> funcDivmod(fcall)
            "divmod__uword" -> funcDivmodW(fcall)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "sort" -> funcSort(fcall)
            "reverse" -> funcReverse(fcall)
            "memory" -> funcMemory(fcall, discardResult, resultRegister)
            "peekw" -> funcPeekW(fcall, resultRegister)
            "peek" -> throw AssemblyError("peek() should have been replaced by @()")
            "pokew" -> funcPokeW(fcall)
            "pokemon" -> { /* meme function */ }
            "poke" -> throw AssemblyError("poke() should have been replaced by @()")
            "push" -> asmgen.pushCpuStack(DataType.UBYTE, fcall.args[0])
            "pushw" -> asmgen.pushCpuStack(DataType.UWORD, fcall.args[0])
            "pop" -> {
                require(fcall.args[0] is PtIdentifier) {
                    "attempt to pop a value into a differently typed variable, or in something else that isn't supported ${fcall.position}"
                }
                val symbol = asmgen.symbolTable.lookup((fcall.args[0] as PtIdentifier).name)
                val target = symbol!!.astNode as IPtVariable
                asmgen.popCpuStack(DataType.UBYTE, target, fcall.definingISub())
            }
            "popw" -> {
                require(fcall.args[0] is PtIdentifier) {
                    "attempt to pop a value into a differently typed variable, or in something else that isn't supported ${fcall.position}"
                }
                val symbol = asmgen.symbolTable.lookup((fcall.args[0] as PtIdentifier).name)
                val target = symbol!!.astNode as IPtVariable
                asmgen.popCpuStack(DataType.UWORD, target, fcall.definingISub())
            }
            "rsave" -> funcRsave()
            "rrestore" -> funcRrestore()
            "cmp" -> funcCmp(fcall)
            "callfar" -> funcCallFar(fcall)
            "prog8_lib_stringcompare" -> funcStringCompare(fcall)
            "prog8_lib_square_byte" -> funcSquare(fcall, DataType.UBYTE)
            "prog8_lib_square_word" -> funcSquare(fcall, DataType.UWORD)
            else -> throw AssemblyError("missing asmgen for builtin func ${fcall.name}")
        }

        return BuiltinFunctions.getValue(fcall.name).returnType
    }

    private fun funcSquare(fcall: PtBuiltinFunctionCall, resultType: DataType) {
        // square of word value is faster with dedicated routine, square of byte just use the regular multiplication routine.
        when (resultType) {
            DataType.UBYTE -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)
                asmgen.out("  tay  |  jsr  math.multiply_bytes")
            }
            DataType.UWORD -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("  jsr  math.square")
            }
            else -> {
                throw AssemblyError("optimized square only for integer types")
            }
        }
    }

    private fun funcDivmod(fcall: PtBuiltinFunctionCall) {
        assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, false)
        asmgen.saveRegisterStack(CpuRegister.A, false)
        assignAsmGen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.Y, false)
        asmgen.restoreRegisterStack(CpuRegister.A ,false)
        // math.divmod_ub_asm: -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
        asmgen.out("  jsr  math.divmod_ub_asm")
        val var2name = asmgen.asmVariableName(fcall.args[2] as PtIdentifier)
        val var3name = asmgen.asmVariableName(fcall.args[3] as PtIdentifier)
        val divisionTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[2].position, var2name)
        val remainderTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[3].position, var3name)
        assignAsmGen.assignRegisterByte(remainderTarget, CpuRegister.A, false)
        assignAsmGen.assignRegisterByte(divisionTarget, CpuRegister.Y, false)
    }

    private fun funcDivmodW(fcall: PtBuiltinFunctionCall) {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        // math.divmod_uw_asm: -- divide two unsigned words (16 bit each) into 16 bit results
        //    input:  P8ZP_SCRATCH_W1 in ZP: 16 bit number, A/Y: 16 bit divisor
        //    output: P8ZP_SCRATCH_W2 in ZP: 16 bit remainder, A/Y: 16 bit division result
        asmgen.out("  jsr  math.divmod_uw_asm")
        val var2name = asmgen.asmVariableName(fcall.args[2] as PtIdentifier)
        val divisionTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[2].position, var2name)
        val remainderVar = asmgen.asmVariableName(fcall.args[3] as PtIdentifier)
        assignAsmGen.assignRegisterpairWord(divisionTarget, RegisterOrPair.AY)
        asmgen.out("""
            lda  P8ZP_SCRATCH_W2
            ldy  P8ZP_SCRATCH_W2+1
            sta  $remainderVar
            sty  $remainderVar+1""")
    }

    private fun funcStringCompare(fcall: PtBuiltinFunctionCall) {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[0], fcall.args[1], "P8ZP_SCRATCH_W2")
        asmgen.out("  jsr  prog8_lib.strcmp_mem")
    }

    private fun funcRsave() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("""
                php
                pha
                phy
                phx""")
        else
            // see http://6502.org/tutorials/register_preservation.html
            asmgen.out("""
                php
                sta  P8ZP_SCRATCH_REG
                pha
                txa
                pha
                tya
                pha
                lda  P8ZP_SCRATCH_REG""")
    }

    private fun funcRrestore() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("""
                plx
                ply
                pla
                plp""")
        else
            asmgen.out("""
                pla
                tay
                pla
                tax
                pla
                plp""")
    }

    private fun funcCallFar(fcall: PtBuiltinFunctionCall) {
        if(asmgen.options.compTarget.name != "cx16")
            throw AssemblyError("callfar only works on cx16 target at this time")

        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)      // bank
        asmgen.out("  sta  (++)+0")
        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)     // jump address
        asmgen.out("  sta  (+)+0 |  sty  (+)+1")
        asmgen.assignExpressionToRegister(fcall.args[2], RegisterOrPair.AY)     // uword argument
        asmgen.out("""
            jsr  cx16.jsrfar
+           .word  0
+           .byte  0""")
        // note that by convention the values in A+Y registers are now the return value of the call.
    }

    private fun funcCmp(fcall: PtBuiltinFunctionCall) {
        val arg1 = fcall.args[0]
        val arg2 = fcall.args[1]
        if(arg1.type in ByteDatatypes) {
            if(arg2.type in ByteDatatypes) {
                when (arg2) {
                    is PtIdentifier -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  ${asmgen.asmVariableName(arg2)}")
                    }
                    is PtNumber -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  #${arg2.number.toInt()}")
                    }
                    is PtMemoryByte -> {
                        if(arg2.address is PtNumber) {
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                            asmgen.out("  cmp  ${arg2.address.asConstInteger()!!.toHex()}")
                        } else {
                            asmgen.assignByteOperandsToAAndVar(arg1, arg2, "P8ZP_SCRATCH_B1")
                            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                        }
                    }
                    else -> {
                        asmgen.assignByteOperandsToAAndVar(arg1, arg2, "P8ZP_SCRATCH_B1")
                        asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                    }
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        } else {
            // arg1 is a word
            if(arg2.type in WordDatatypes) {
                when (arg2) {
                    is PtIdentifier -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  ${asmgen.asmVariableName(arg2)}+1
                            bne  +
                            cmp  ${asmgen.asmVariableName(arg2)}
+""")
                    }
                    is PtNumber -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  #>${arg2.number.toInt()}
                            bne  +
                            cmp  #<${arg2.number.toInt()}
+""")
                    }
                    else -> {
                        asmgen.assignWordOperandsToAYAndVar(arg1, arg2, "P8ZP_SCRATCH_W1")
                        asmgen.out("""
                            cpy  P8ZP_SCRATCH_W1+1
                            bne  +
                            cmp  P8ZP_SCRATCH_W1
+""")
                    }
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        }
    }

    private fun funcMemory(fcall: PtBuiltinFunctionCall, discardResult: Boolean, resultRegister: RegisterOrPair?) {
        if(discardResult)
            throw AssemblyError("should not discard result of memory allocation at $fcall")
        val name = (fcall.args[0] as PtString).value
        require(name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name ${fcall.position}"}
        val slabname = PtIdentifier("prog8_slabs.prog8_memoryslab_$name", DataType.UWORD, fcall.position)
        val addressOf = PtAddressOf(fcall.position)
        addressOf.add(slabname)
        addressOf.parent = fcall
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = addressOf)
        val target = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, fcall.position, null, asmgen)
        val assign = AsmAssignment(src, target, program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign, fcall.definingISub())
    }

    private fun funcSqrt(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        when(fcall.args[0].type) {
            DataType.UBYTE -> {
                asmgen.out("  ldy  #0 |  jsr  prog8_lib.func_sqrt16_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, false)
            }
            DataType.UWORD -> {
                asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, false)
            }
            DataType.FLOAT -> {
                asmgen.out("  jsr  floats.func_sqrt_into_FAC1")
                assignAsmGen.assignFAC1float(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.FAC1, true, fcall.position, scope, asmgen))
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcReverse(fcall: PtBuiltinFunctionCall) {
        val variable = fcall.args.single()
        if (variable is PtIdentifier) {
            val symbol = asmgen.symbolTable.lookup(variable.name)
            val decl = symbol!!.astNode as IPtVariable
            val numElements = when(decl) {
                is PtConstant -> throw AssemblyError("cannot reverse a constant")
                is PtMemMapped -> decl.arraySize
                is PtVariable -> decl.arraySize
            }
            val varName = asmgen.asmVariableName(variable)
            when (decl.type) {
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
                DataType.STR -> {
                    val stringLength = (symbol as StStaticVariable).length!!-1
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$stringLength
                        jsr  prog8_lib.func_reverse_b""")
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
                in SplitWordArrayTypes -> TODO("split word reverse")
                else -> throw AssemblyError("weird type")
            }
        }
    }

    private fun funcSort(fcall: PtBuiltinFunctionCall) {
        val variable = fcall.args.single()
        if (variable is PtIdentifier) {
            val symbol = asmgen.symbolTable.lookup(variable.name)
            val decl = symbol!!.astNode as IPtVariable
            val varName = asmgen.asmVariableName(variable)
            val numElements = when(decl) {
                is PtConstant -> throw AssemblyError("cannot sort a constant")
                is PtMemMapped -> decl.arraySize
                is PtVariable -> decl.arraySize
            }
            when (decl.type) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements""")
                    asmgen.out(if (decl.type == DataType.ARRAY_UB) "  jsr  prog8_lib.func_sort_ub" else "  jsr  prog8_lib.func_sort_b")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements""")
                    asmgen.out(if (decl.type == DataType.ARRAY_UW) "  jsr  prog8_lib.func_sort_uw" else "  jsr  prog8_lib.func_sort_w")
                }
                DataType.STR -> {
                    val stringLength = (symbol as StStaticVariable).length!!-1
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$stringLength
                        jsr  prog8_lib.func_sort_ub""")
                }
                DataType.ARRAY_F -> throw AssemblyError("sorting of floating point array is not supported")
                in SplitWordArrayTypes -> TODO("split word sort")
                else -> throw AssemblyError("weird type")
            }
        } else
            throw AssemblyError("weird type")
    }

    private fun funcRor2(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type) {
            DataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        translateRolRorArrayArgs(what.variable, what, "ror2", 'b')
                        asmgen.out("  jsr  prog8_lib.ror2_array_ub")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  lda  ${number.toHex()} |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.ror2_mem_ub")
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.splitWords)
                            TODO("ror2 split words ${what.position}")
                        translateRolRorArrayArgs(what.variable, what, "ror2", 'w')
                        asmgen.out("  jsr  prog8_lib.ror2_array_uw")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #\$80 |  sta  $variable+1 |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRor(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type) {
            DataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        translateRolRorArrayArgs(what.variable, what, "ror", 'b')
                        asmgen.out("  jsr  prog8_lib.ror_array_ub")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  ror  ${number.toHex()}")
                        } else {
                            val ptrAndIndex = asmgen.pointerViaIndexRegisterPossible(what.address)
                            if(ptrAndIndex!=null) {
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.A)
                                asmgen.saveRegisterStack(CpuRegister.A, true)
                                asmgen.assignExpressionToRegister(ptrAndIndex.first, RegisterOrPair.AY)
                                asmgen.out("  sta  (+) + 1 |  sty  (+) + 2")
                                asmgen.restoreRegisterStack(CpuRegister.X, false)
                                asmgen.out("""
+                                   ror  ${'$'}ffff,x           ; modified""")
                            } else {
                                asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   ror  ${'$'}ffff            ; modified""")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.splitWords)
                            TODO("ror split words ${what.position}")
                        translateRolRorArrayArgs(what.variable, what, "ror", 'w')
                        asmgen.out("  jsr  prog8_lib.ror_array_uw")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable+1 |  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol2(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type) {
            DataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        translateRolRorArrayArgs(what.variable, what, "rol2", 'b')
                        asmgen.out("  jsr  prog8_lib.rol2_array_ub")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  lda  ${number.toHex()} |  cmp  #\$80 |  rol  a |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.rol2_mem_ub")
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  cmp  #\$80 |  rol  a |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.splitWords)
                            TODO("rol2 split words ${what.position}")
                        translateRolRorArrayArgs(what.variable, what, "rol2", 'w')
                        asmgen.out("  jsr  prog8_lib.rol2_array_uw")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  asl  $variable |  rol  $variable+1 |  bcc  + |  inc  $variable |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type) {
            DataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        translateRolRorArrayArgs(what.variable, what, "rol", 'b')
                        asmgen.out("  jsr  prog8_lib.rol_array_ub")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  rol  ${number.toHex()}")
                        } else {
                            val ptrAndIndex = asmgen.pointerViaIndexRegisterPossible(what.address)
                            if(ptrAndIndex!=null) {
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.A)
                                asmgen.saveRegisterStack(CpuRegister.A, true)
                                asmgen.assignExpressionToRegister(ptrAndIndex.first, RegisterOrPair.AY)
                                asmgen.out("  sta  (+) + 1 |  sty  (+) + 2")
                                asmgen.restoreRegisterStack(CpuRegister.X, false)
                                asmgen.out("""
+                                   rol  ${'$'}ffff,x           ; modified""")
                            } else {
                                asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   rol  ${'$'}ffff            ; modified""")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.splitWords)
                            TODO("rol split words ${what.position}")
                        translateRolRorArrayArgs(what.variable, what, "rol", 'w')
                        asmgen.out("  jsr  prog8_lib.rol_array_uw")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable |  rol  $variable+1")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateRolRorArrayArgs(arrayvar: PtIdentifier, indexer: PtArrayIndexer, operation: String, dt: Char) {
        if(indexer.splitWords)
            TODO("rol/ror split words access ${indexer.position}")
        if(arrayvar.type==DataType.UWORD) {
            if(dt!='b')
                throw AssemblyError("non-array var indexing requires bytes dt")
            asmgen.assignExpressionToVariable(arrayvar, "prog8_lib.${operation}_array_u${dt}._arg_target", DataType.UWORD)
        } else {
            val p = arrayvar.parent
            val addressOf = PtAddressOf(arrayvar.position)
            addressOf.add(arrayvar)
            addressOf.parent = p
            asmgen.assignExpressionToVariable(addressOf, "prog8_lib.${operation}_array_u${dt}._arg_target", DataType.UWORD)
        }
        asmgen.assignExpressionToVariable(indexer.index, "prog8_lib.${operation}_array_u${dt}._arg_index", DataType.UBYTE)
    }

    private fun funcSgn(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        val dt = fcall.args.single().type
        when (dt) {
            DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_into_A")
            DataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_into_A")
            DataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_into_A")
            DataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_into_A")
            DataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_into_A")
            else -> throw AssemblyError("weird type $dt")
        }
        assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, true)
    }

    private fun funcAnyAll(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        outputAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().type
        when (dt) {
            DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${fcall.name}_b_into_A |  ldy  #0")
            DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${fcall.name}_w_into_A |  ldy  #0")
            DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${fcall.name}_f_into_A |  ldy  #0")
            in SplitWordArrayTypes -> TODO("split word any/all")
            else -> throw AssemblyError("weird type $dt")
        }
        assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, dt in SignedDatatypes)
    }

    private fun funcAbs(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        val dt = fcall.args.single().type
        when (dt) {
            DataType.BYTE -> {
                asmgen.out("  jsr  prog8_lib.abs_b_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A,false)
            }
            DataType.WORD -> {
                asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, fcall.position, scope, asmgen), RegisterOrPair.AY)
            }
            DataType.FLOAT -> {
                asmgen.out("  jsr  floats.func_abs_f_into_FAC1")
                assignAsmGen.assignFAC1float(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.FAC1, true, fcall.position, scope, asmgen))
            }
            DataType.UBYTE -> {
                asmgen.assignRegister(RegisterOrPair.A, AsmAssignTarget.fromRegisters(resultRegister?:RegisterOrPair.A, false, fcall.position, scope, asmgen))
            }
            DataType.UWORD -> {
                asmgen.assignRegister(RegisterOrPair.AY, AsmAssignTarget.fromRegisters(resultRegister?:RegisterOrPair.AY, false, fcall.position, scope, asmgen))
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcPokeW(fcall: PtBuiltinFunctionCall) {
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr |  sty  ${addr}+1")
                return
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    if (asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            sta  ($varname)
                            txa
                            ldy  #1
                            sta  ($varname),y""")
                    } else {
                        asmgen.out("""
                            ldy  #0
                            sta  ($varname),y
                            txa
                            iny
                            sta  ($varname),y""")
                    }
                    return
                }
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                    asmgen.out("""
                            sta  ($varname),y
                            txa
                            iny
                            sta  ($varname),y""")
                    return
                }
            }
            else -> { /* fall through */ }
        }

        // fall through method:
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        asmgen.out("  jsr  prog8_lib.func_pokew")
    }

    private fun funcPeekW(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        fun fallback() {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
            asmgen.out("  jsr  prog8_lib.func_peekw")
        }
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                val addr = addrExpr.number.toHex()
                asmgen.out("  lda  $addr |  ldy  ${addr}+1")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    if (asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            ldy  #1
                            lda  ($varname),y
                            tay
                            lda  ($varname)""")
                    } else {
                        asmgen.out("""
                            ldy  #0
                            lda  ($varname),y
                            tax
                            iny
                            lda  ($varname),y
                            tay
                            txa""")
                    }
                } else fallback()
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.out("""
                        lda  ($varname),y
                        tax
                        iny
                        lda  ($varname),y
                        tay
                        txa""")
                } else fallback()
            }
            else -> fallback()
        }

        when(resultRegister ?: RegisterOrPair.AY) {
            RegisterOrPair.AY -> {}
            RegisterOrPair.AX -> asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG")
            RegisterOrPair.XY -> asmgen.out("  tax")
            in Cx16VirtualRegisters -> asmgen.out(
                "  sta  cx16.${
                    resultRegister.toString().lowercase()
                } |  sty  cx16.${resultRegister.toString().lowercase()}+1")
            else -> throw AssemblyError("invalid reg")
        }
    }

    private fun funcClamp(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type in SignedDatatypes
        when(fcall.type) {
            in ByteDatatypes -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W1+1", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
                assignAsmGen.assignRegisterByte(targetReg, CpuRegister.A, signed)
            }
            in WordDatatypes -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W2", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
                assignAsmGen.assignRegisterpairWord(targetReg, RegisterOrPair.AY)
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun funcMin(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type in SignedDatatypes
        if(fcall.type in ByteDatatypes) {
            asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_B1", fcall.type)     // right
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)          // left
            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
            if(signed) asmgen.out("  bmi  +") else asmgen.out("  bcc  +")
            asmgen.out("""
                lda  P8ZP_SCRATCH_B1
+""")
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.A, targetReg)
        } else if(fcall.type in WordDatatypes) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", fcall.type)     // left
            asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W2", fcall.type)     // right
            if(signed) {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2
                    tya
                    sbc  P8ZP_SCRATCH_W2+1
                    bvc  +
                    eor  #$80
+                   bpl  +
                    lda  P8ZP_SCRATCH_W1                   
                    ldy  P8ZP_SCRATCH_W1+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
+""")
            } else {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2+1
                    bcc  ++
                    bne  +
                    lda  P8ZP_SCRATCH_W1
                    cmp  P8ZP_SCRATCH_W2
                    bcc  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
+""")
            }
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.AY, targetReg)
        } else {
            throw AssemblyError("min float not supported")
        }
    }

    private fun funcMax(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type in SignedDatatypes
        if(fcall.type in ByteDatatypes) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_B1", fcall.type)     // left
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)          // right
            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
            if(signed) asmgen.out("  bpl  +") else asmgen.out("  bcs  +")
            asmgen.out("""
                lda  P8ZP_SCRATCH_B1
+""")
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.A, targetReg)
        } else if(fcall.type in WordDatatypes) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", fcall.type)     // left
            asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W2", fcall.type)     // right
            if(signed) {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2
                    tya
                    sbc  P8ZP_SCRATCH_W2+1
                    bvc  +
                    eor  #$80
+                   bmi  +
                    lda  P8ZP_SCRATCH_W1                   
                    ldy  P8ZP_SCRATCH_W1+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
+""")
            } else {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2+1
                    bcc  ++
                    bne  +
                    lda  P8ZP_SCRATCH_W1
                    cmp  P8ZP_SCRATCH_W2
                    bcc  ++
+                   lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
+""")
            }
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.AY, targetReg)
        } else {
            throw AssemblyError("max float not supported")
        }
    }

    private fun funcMkword(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val reg = resultRegister ?: RegisterOrPair.AY
        var needAsave = asmgen.needAsaveForExpr(fcall.args[0])
        if(!needAsave) {
            val mr0 = fcall.args[0] as? PtMemoryByte
            val mr1 = fcall.args[1] as? PtMemoryByte
            if (mr0 != null)
                needAsave =  mr0.address !is PtNumber
            if (mr1 != null)
                needAsave = needAsave or (mr1.address !is PtNumber)
        }
        when(reg) {
            RegisterOrPair.AX -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                if(needAsave)
                    asmgen.out("  pha")
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.X)      // msb
                if(needAsave)
                    asmgen.out("  pla")
            }
            RegisterOrPair.AY -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                if(needAsave)
                    asmgen.out("  pha")
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                if(needAsave)
                    asmgen.out("  pla")
            }
            RegisterOrPair.XY -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                if(needAsave)
                    asmgen.out("  pha")
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                if(needAsave)
                    asmgen.out("  pla")
                asmgen.out("  tax")
            }
            in Cx16VirtualRegisters -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                asmgen.out("  sta  cx16.${reg.toString().lowercase()}")
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)      // msb
                asmgen.out("  sta  cx16.${reg.toString().lowercase()}+1")
            }
            else -> throw AssemblyError("invalid mkword target reg")
        }
    }

    private fun funcMsb(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (arg.type !in WordDatatypes)
            throw AssemblyError("msb required word argument")
        if (arg is PtNumber)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName+1")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName+1")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName+1")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName+1 |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName+1 |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName+1 |  ldy  #0")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  stz  cx16.$regname+1")
                    else
                        asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            when(resultRegister) {
                null, RegisterOrPair.A -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                    asmgen.out("  tya")
                }
                RegisterOrPair.X -> {
                    asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AX)
                    asmgen.out("  pla")
                }
                RegisterOrPair.Y -> {
                    asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                    asmgen.out("  pla")
                }
                else -> throw AssemblyError("invalid reg")
            }
        }
    }

    private fun funcLsb(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (arg.type !in WordDatatypes)
            throw AssemblyError("lsb required word argument")
        if (arg is PtNumber)
            throw AssemblyError("lsb(const) should have been const-folded away")

        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  stz  cx16.$regname+1")
                    else
                        asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            when(resultRegister) {
                null, RegisterOrPair.A -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                    // NOTE: we rely on the fact that the above assignment to AY, assigns the Lsb to A as the last instruction.
                    //       this is required because the compiler assumes the status bits are set according to what A is (lsb)
                    //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                }
                RegisterOrPair.X -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.XY)
                    // NOTE: we rely on the fact that the above assignment to XY, assigns the Lsb to X as the last instruction.
                    //       this is required because the compiler assumes the status bits are set according to what X is (lsb)
                    //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                }
                RegisterOrPair.Y -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                    asmgen.out("  tay |  cpy  #0")
                }
                RegisterOrPair.AY -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                    asmgen.out("  ldy  #0 |  cmp  #0")
                }
                RegisterOrPair.AX -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AX)
                    asmgen.out("  ldx  #0 |  cmp  #0")
                }
                RegisterOrPair.XY -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.XY)
                    asmgen.out("  ldy  #0 |  cpx  #0")
                }
                in Cx16VirtualRegisters -> {
                    asmgen.assignExpressionToRegister(fcall.args.single(), resultRegister)
                    val zero = PtNumber(DataType.UBYTE, 0.0, Position.DUMMY)
                    zero.parent=fcall
                    assignAsmGen.assignExpressionToVariable(zero, "cx16.${resultRegister.toString().lowercase()}H", DataType.UBYTE)
                    asmgen.out("  lda  cx16.r0L")
                }
                else -> throw AssemblyError("invalid reg")
            }
        }
    }

    private fun outputAddressAndLenghtOfArray(arg: PtExpression) {
        // address in P8ZP_SCRATCH_W1,  number of elements in A
        arg as PtIdentifier
        val symbol = asmgen.symbolTable.lookup(arg.name)
        val arrayVar = symbol!!.astNode as IPtVariable
        val numElements = when(arrayVar) {
            is PtConstant -> null
            is PtMemMapped -> arrayVar.arraySize
            is PtVariable -> arrayVar.arraySize
        } ?: throw AssemblyError("length of non-array requested")
        val identifierName = asmgen.asmVariableName(arg)
        asmgen.out("""
                    lda  #<$identifierName
                    ldy  #>$identifierName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #$numElements
                    """)
    }

    private fun translateArguments(call: PtBuiltinFunctionCall, scope: IPtSubroutine?) {
        val signature = BuiltinFunctions.getValue(call.name)
        val callConv = signature.callConvention(call.args.map { it.type})

        fun getSourceForFloat(value: PtExpression): AsmAssignSource {
            return when (value) {
                is PtIdentifier -> {
                    val addr = PtAddressOf(value.position)
                    addr.add(value)
                    addr.parent = call
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
                is PtNumber -> {
                    throw AssemblyError("float literals should have been converted into autovar")
                }
                else -> {
                    if(scope==null)
                        throw AssemblyError("cannot use float arguments outside of a subroutine scope")

                    asmgen.subroutineExtra(scope).usedFloatEvalResultVar2 = true
                    val variable = PtIdentifier(subroutineFloatEvalResultVar2, DataType.FLOAT, value.position)
                    val addr = PtAddressOf(value.position)
                    addr.add(variable)
                    addr.parent = call
                    asmgen.assignExpressionToVariable(value, asmgen.asmVariableName(variable), DataType.FLOAT)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
            }
        }

        call.args.zip(callConv.params).zip(signature.parameters).forEach {
            val paramName = it.second.name
            val conv = it.first.second
            val value = it.first.first
            when {
                conv.variable -> {
                    val varname = "prog8_lib.func_${call.name}._arg_${paramName}"
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = PtAddressOf(value.position)
                            addr.add(value)
                            addr.parent = call
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, conv.dt, null, value.position, variableAsmName = varname)
                    val assign = AsmAssignment(src, tgt, program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign, scope)
                }
                conv.reg != null -> {
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = PtAddressOf(value.position)
                            addr.add(value)
                            addr.parent = call
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget.fromRegisters(conv.reg!!, false, value.position, null, asmgen)
                    val assign = AsmAssignment(src, tgt, program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign, scope)
                }
                else -> throw AssemblyError("callconv")
            }
        }
    }

}
