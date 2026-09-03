package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal

internal class TypeCastAssignmentsGen(
    private val program: PtProgram,
    private val asmgen: AsmGen6502Internal,
    private val pointergen: PointerAssignmentsGen
) {
    lateinit var assignmentAsmGen: AssignmentAsmGen

    internal fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: PtExpression, origTypeCastExpression: PtTypeCast) {
        val valueDt = value.type
        if(valueDt==targetDt)
            throw AssemblyError("type cast to identical dt should have been removed: $valueDt at ${value.position}")

        when(value) {
            is PtIdentifier -> {
                if(targetDt.isWord) {
                    if(valueDt.isUnsignedByte || valueDt.isBool) {
                        assignmentAsmGen.assignVariableUByteIntoWord(target, value)
                        return
                    }
                    if(valueDt.isSignedByte) {
                        assignmentAsmGen.assignVariableByteIntoWord(target, value)
                        return
                    }
                }
            }
            is PtMemoryByte -> {
                if(targetDt.isWord) {

                    fun assignViaExprEval(addressExpression: PtExpression) {
                        asmgen.assignExpressionToVariable(addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                        asmgen.out("  ldy  #0")
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    }

                    when (value.address) {
                        is PtNumber -> {
                            val address = (value.address as PtNumber).number.toUInt()
                            assignmentAsmGen.assignMemoryByteIntoWord(target, address, null)
                        }
                        is PtIdentifier -> {
                            assignmentAsmGen.assignMemoryByteIntoWord(target, null, value.address as PtIdentifier)
                        }
                        is PtBinaryExpression -> {
                            val addrExpr = value.address as PtBinaryExpression
                            if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, false)) {
                                asmgen.out("  ldy  #0")
                                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                            } else {
                                assignViaExprEval(value.address)
                            }
                        }
                        else -> {
                            assignViaExprEval(value.address)
                        }
                    }
                    return
                }
            }
            is PtNumber -> {
                if(targetDt.isPointer) {
                    // assign a number to a pointer type
                    require(valueDt.isInteger)
                    return assignmentAsmGen.assignConstantWord(target, value.number.toInt())
                }
                else
                    throw AssemblyError("literal value cast should have been const-folded away (target type=$targetDt)")
            }
            is PtBool -> throw AssemblyError("literal value cast should have been const-folded away (target type=$targetDt)")
            is PtArrayIndexer -> {
                if(targetDt.isByte && valueDt.isWord) {
                    // just assign the lsb from the array value
                    return assignCastWordViaLsbFunc(value, target)
                } else if(targetDt.isBool && valueDt.isWord) {
                    return assignWordToBool(value, target)
                }
            }
            else -> {}
        }


        // special case optimizations
        if(target.kind == TargetStorageKind.VARIABLE) {
            if(value is PtIdentifier && !valueDt.isUndefined)
                return assignTypeCastedIdentifier(target.asmVarname, targetDt, asmgen.asmVariableName(value), valueDt)

            when {
                valueDt.isBool -> {
                    if(targetDt.isInteger) {
                        // optimization to assign boolean expression to integer target (just assign the 0 or 1 directly)
                        val assignDirect = AsmAssignment(
                            AsmAssignSource.fromAstSource(value, program, asmgen),
                            listOf(target),
                            program.memsizer,
                            target.position
                        )
                        assignmentAsmGen.assignExpression(assignDirect, target.scope)
                    } else {
                        TODO("assign bool to non-integer type $targetDt ${value.position}")
                    }
                }
                valueDt.isByte -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.A, valueDt.isSigned)
                    assignTypeCastedRegisters(target.asmVarname, targetDt.base, RegisterOrPair.A, valueDt.base, target.position)
                }
                valueDt.isLong -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14R15, valueDt.isSigned)
                    assignTypeCastedRegisters(target.asmVarname, targetDt.base, RegisterOrPair.R14R15, valueDt.base, target.position)
                }
                valueDt.isWord || valueDt.isPointer -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, valueDt.isSigned)
                    assignTypeCastedRegisters(target.asmVarname, targetDt.base, RegisterOrPair.AY, valueDt.base, target.position)
                }
                valueDt.isFloat -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.FAC1, true)
                    assignTypeCastedFloatFAC1(target.asmVarname, targetDt.base)
                }
                valueDt.isPassByRef -> {
                    // str/array value cast (most likely to UWORD, take address-of)
                    assignmentAsmGen.assignExpressionToVariable(value, target.asmVarname, targetDt)
                }
                else -> throw AssemblyError("strange dt in typecast assign to var: $valueDt  -->  $targetDt")
            }
            return
        }

        if(valueDt.isWord && origTypeCastExpression.type.isUnsignedByte) {
            val parentTc = origTypeCastExpression.parent as? PtTypeCast
            if(parentTc!=null && parentTc.type.isUnsignedWord) {
                // typecast a word value to ubyte and directly back to uword
                // generate code for lsb(value) here instead of the ubyte typecast
                return assignCastWordViaLsbFunc(value, target)
            }
        }

        if(valueDt.isByteOrBool) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    // 'cast' an ubyte value to a byte register; no cast needed at all
                    return assignmentAsmGen.assignExpressionToRegister(value, target.register, valueDt.isSigned)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.A, false)
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, valueDt.isSigned, true)
                    return
                }
                in CombinedLongRegisters -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.A, false)
                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, valueDt.isSigned, true)
                    return
                }
                else -> {}
            }
        } else if(valueDt.isWord) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    return if(targetDt.isBool)
                        assignWordToBool(value, target)
                    else
                        // cast an uword to a byte register, do this via lsb(value)
                        // generate code for lsb(value) here instead of the ubyte typecast
                        assignCastWordViaLsbFunc(value, target)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    // 'cast' uword into a 16 bits register, just assign it
                    return assignmentAsmGen.assignExpressionToRegister(value, target.register!!, targetDt.isSigned)
                }
                RegisterOrPair.R0R1 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R0, false)
                    return asmgen.signExtendLongVariable("cx16.r0", valueDt.base)
                }
                RegisterOrPair.R2R3 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R2, false)
                    return asmgen.signExtendLongVariable("cx16.r2", valueDt.base)
                }
                RegisterOrPair.R4R5 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R4, false)
                    return asmgen.signExtendLongVariable("cx16.r4", valueDt.base)
                }
                RegisterOrPair.R6R7 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R6, false)
                    return asmgen.signExtendLongVariable("cx16.r6", valueDt.base)
                }
                RegisterOrPair.R8R9 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R8, false)
                    return asmgen.signExtendLongVariable("cx16.r8", valueDt.base)
                }
                RegisterOrPair.R10R11 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R10, false)
                    return asmgen.signExtendLongVariable("cx16.r10", valueDt.base)
                }
                RegisterOrPair.R12R13 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R12, false)
                    return asmgen.signExtendLongVariable("cx16.r12", valueDt.base)
                }
                RegisterOrPair.R14R15 -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14, false)
                    return asmgen.signExtendLongVariable("cx16.r14", valueDt.base)
                }
                else -> {}
            }
        }

        if(target.kind==TargetStorageKind.REGISTER) {
            if(valueDt.isFloat && !target.datatype.isFloat) {
                // have to typecast the float number on the fly down to an integer
                assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.FAC1, targetDt.isSigned)
                assignTypeCastedFloatFAC1("P8ZP_SCRATCH_W1", targetDt.base)
                assignmentAsmGen.assignVariableToRegister("P8ZP_SCRATCH_W1", target.register!!, targetDt.isSigned, origTypeCastExpression.definingISub(), target.position)
                return
            } else {
                if(!(valueDt isAssignableTo targetDt)) {
                    return if(valueDt.isWord && targetDt.isByte) {
                        // word to byte, just take the lsb
                        assignCastWordViaLsbFunc(value, target)
                    } else if(valueDt.isWord && targetDt.isBool) {
                        // word to bool
                        assignWordToBool(value, target)
                    } else if(valueDt.isWord && targetDt.isWord) {
                        // word to word, just assign
                        assignmentAsmGen.assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isByteOrBool && targetDt.isByte) {
                        // byte to byte, just assign
                        assignmentAsmGen.assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isByteOrBool && targetDt.isWord) {
                        // byte to word, just assign
                        assignmentAsmGen.assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isLong && targetDt.isByte) {
                        // long to byte, just take the lsb
                        assignCastLongToByte(value, target)
                    } else if(valueDt.isLong && targetDt.isWord) {
                        // long to word, just take the lsw
                        assignCastViaLswFunc(value, target)
                    } else if(valueDt.isPointer && targetDt.isPointer) {
                        // pointer type A to pointer type B, just assign
                        assignmentAsmGen.assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else
                        throw AssemblyError("can't cast $valueDt to $targetDt, this should have been checked in the astchecker  ${value.position}")
                }
            }
        }

        if(targetDt.isInteger && valueDt.isByteOrBool && valueDt.isAssignableTo(targetDt)) {
            require(targetDt.isWord || targetDt.isLong) {
                "should be byte to word or long assignment ${origTypeCastExpression.position}"
            }
            when(target.kind) {
//                TargetStorageKind.VARIABLE -> {
//                    This has been handled already earlier on line 961.
//                    // byte to word, just assign to registers first, then assign to variable
//                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, targetDt==BaseDataType.WORD)
//                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, targetDt)
//                    return
//                }
                TargetStorageKind.ARRAY -> {
                    // byte to word, just assign to registers first, then assign into array
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, targetDt.isSigned)
                    val deref = target.array!!.pointerderef
                    if(deref!=null)
                        pointergen.assignWordReg(IndexedPtrTarget(target), RegisterOrPair.AY)
                    else
                        assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                    return
                }
                TargetStorageKind.REGISTER -> {
                    // byte to word or long, just assign to registers
                    assignmentAsmGen.assignExpressionToRegister(value, target.register!!, targetDt.isSigned)
                    return
                }
                TargetStorageKind.POINTER -> {
                    pointergen.assignByteToWord(PtrTarget(target), value)
                    return
                }
                else -> throw AssemblyError("weird target at ${target.position}")
            }
        }

        if(targetDt.isFloat && (target.register==RegisterOrPair.FAC1 || target.register==RegisterOrPair.FAC2)) {
            if(target.register==RegisterOrPair.FAC2)
                asmgen.pushFAC1()
            when {
                valueDt.isUnsignedByte -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.Y, false)
                    asmgen.out("  jsr  floats.FREADUY")
                }
                valueDt.isSignedByte -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.A, true)
                    asmgen.out("  jsr  floats.FREADSA")
                }
                valueDt.isUnsignedWord -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, false)
                    asmgen.out("  jsr  floats.GIVUAYFAY")
                }
                valueDt.isSignedWord -> {
                    assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  jsr  floats.GIVAYFAY")
                }
                valueDt.isLong -> {
                    when(value) {
                        is PtIdentifier -> {
                            val varname = asmgen.asmVariableName(value)
                            asmgen.out("""
                                lda  #<$varname
                                ldy  #>$varname
                                jsr  floats.internal_long_AY_to_FAC""")
                        }
                        else -> {
                            assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14R15, true)
                            asmgen.out("""
                                lda  #<cx16.r14
                                ldy  #>cx16.r14
                                jsr  floats.internal_long_AY_to_FAC""")
                        }
                    }
                }
                else -> throw AssemblyError("invalid dt at ${target.position}")
            }
            if(target.register==RegisterOrPair.FAC2) {
                asmgen.out("  jsr  floats.MOVEF")
                asmgen.popFAC1()
            }
            return
        }

        if(targetDt.isUnsignedWord && valueDt.isPointer) {
            assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, false)
            assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
            return
        }

        // No more special optimized cases yet. Do the rest via more complex evaluation
        // note: cannot use assignTypeCastedValue because that is ourselves :P
        // NOTE: THIS MAY TURN INTO A STACK OVERFLOW ERROR IF IT CAN'T SIMPLIFY THE TYPECAST..... :-/
        asmgen.assignExpressionTo(origTypeCastExpression, target)
    }


    internal fun assignCastLongToByte(value: PtExpression, target: AsmAssignTarget) {
        // long to byte, can't use lsb() because that only works on words
        when(value) {
            is PtIdentifier -> {
                val longvar = asmgen.asmVariableName(value)
                asmgen.out("  lda  $longvar")
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, true, false)
            }
            is PtNumber -> throw AssemblyError("casting a long number to byte should have been const-folded away ${value.position}")
            else -> {
                asmgen.pushLongRegisters(RegisterOrPair.R14R15, 1)
                assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.R14R15, true)
                asmgen.out("  lda  cx16.r14")
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, true, false)
                asmgen.popLongRegisters(RegisterOrPair.R14R15, 1)
            }
        }
    }


    internal fun assignCastWordViaLsbFunc(value: PtExpression, target: AsmAssignTarget) {
        val lsb = PtFunctionCall("lsb", true, true,arrayOf(DataType.UBYTE), value.position)
        lsb.parent = value.parent
        lsb.add(value)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UBYTE, expression = lsb)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, value.position)
        assignmentAsmGen.translateNormalAssignment(assign, value.definingISub())
    }


    internal fun assignCastViaLswFunc(value: PtExpression, target: AsmAssignTarget) {
        val lsb = PtFunctionCall("lsw", true, true, arrayOf(DataType.UWORD), value.position)
        lsb.parent = value.parent
        lsb.add(value)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = lsb)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, value.position)
        assignmentAsmGen.translateNormalAssignment(assign, value.definingISub())
    }


    internal fun assignWordToBool(value: PtExpression, target: AsmAssignTarget) {
        assignmentAsmGen.assignExpressionToRegister(value, RegisterOrPair.AY, false)
        asmgen.out("""
            sty  P8ZP_SCRATCH_REG
            ora  P8ZP_SCRATCH_REG
            beq  +
            lda  #1
+""")
        assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, false, false)
    }


    internal fun assignTypeCastedFloatFAC1(targetAsmVarName: String, targetDt: BaseDataType) {

        if(targetDt==BaseDataType.FLOAT)
            throw AssemblyError("typecast to identical type")

        when(targetDt) {
            BaseDataType.BOOL -> asmgen.out("  jsr  floats.cast_FAC1_as_bool_into_a |  sta  $targetAsmVarName")
            BaseDataType.UBYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName")
            BaseDataType.BYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName")
            BaseDataType.UWORD -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
            BaseDataType.WORD -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
            BaseDataType.LONG -> {
                asmgen.out("""
                    jsr  floats.QINT
                    lda  floats.FAC_ADDR+4
                    sta  $targetAsmVarName
                    lda  floats.FAC_ADDR+3
                    sta  $targetAsmVarName+1
                    lda  floats.FAC_ADDR+2
                    sta  $targetAsmVarName+2
                    lda  floats.FAC_ADDR+1
                    sta  $targetAsmVarName+3
                """.trimIndent())
            }
            else -> throw AssemblyError("weird type $targetDt")
        }
    }



    internal fun assignTypeCastedIdentifier(targetAsmVarName: String, targetDt: DataType,
                                           sourceAsmVarName: String, sourceDt: DataType) {
        if(sourceDt == targetDt)
            throw AssemblyError("typecast to identical type")

        // also see: PtExpressionAsmGen,   fun translateExpression(typecast: PtTypeCast)
        when {
            sourceDt.isUnsignedByte || sourceDt.isBool -> {
                when(targetDt.base) {
                    BaseDataType.BOOL -> {
                        asmgen.out("""
                        lda  $sourceAsmVarName
                        beq  +
                        lda  #1
+                       sta  $targetAsmVarName""")
                    }
                    BaseDataType.UBYTE, BaseDataType.BYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    BaseDataType.UWORD, BaseDataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.POINTER -> TODO("byte should not be cast to pointer")
                    BaseDataType.LONG -> {
                        asmgen.out("""
                            lda  $sourceAsmVarName
                            sta  $targetAsmVarName
                            lda  #0
                            sta  $targetAsmVarName+1
                            sta  $targetAsmVarName+2
                            sta  $targetAsmVarName+3""")
                    }
                    BaseDataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            ldy  $sourceAsmVarName
                            jsr  floats.cast_from_ub""")
                    }
                    else -> throw AssemblyError("weird type $targetDt")
                }
            }
            sourceDt.isSignedByte -> {
                when(targetDt.base) {
                    BaseDataType.UBYTE, BaseDataType.BOOL -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    BaseDataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.WORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                        asmgen.signExtendVariableLsb(targetAsmVarName, BaseDataType.BYTE)
                    }
                    BaseDataType.POINTER -> TODO("byte should not be cast to pointer")
                    BaseDataType.LONG -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                        asmgen.signExtendVariableLsb(targetAsmVarName, BaseDataType.BYTE)
                        asmgen.signExtendLongVariable(targetAsmVarName, BaseDataType.WORD)
                    }
                    BaseDataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            jsr  floats.cast_from_b""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            sourceDt.isUnsignedWord -> {
                when(targetDt.base) {
                    BaseDataType.BOOL -> {
                        asmgen.out("""
                            lda  $sourceAsmVarName
                            ora  $sourceAsmVarName+1
                            beq  +
                            lda  #1
+                           sta  $targetAsmVarName""")
                    }
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    BaseDataType.WORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.POINTER -> asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    BaseDataType.LONG -> {
                        asmgen.out("""
                            lda  $sourceAsmVarName
                            sta  $targetAsmVarName
                            lda  $sourceAsmVarName+1
                            sta  $targetAsmVarName+1
                            lda  #0
                            sta  $targetAsmVarName+2
                            sta  $targetAsmVarName+3""")
                    }
                    BaseDataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            ldy  $sourceAsmVarName+1
                            jsr  floats.cast_from_uw""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            sourceDt.isSignedWord || sourceDt.isPointer -> {
                when(targetDt.base) {
                    BaseDataType.BOOL -> {
                        asmgen.out("""
                            lda  $sourceAsmVarName
                            ora  $sourceAsmVarName+1
                            beq  +
                            lda  #1
+                           sta  $targetAsmVarName""")
                    }
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    BaseDataType.UWORD, BaseDataType.POINTER -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.LONG -> {
                        asmgen.out("""
                            lda  $sourceAsmVarName
                            sta  $targetAsmVarName
                            lda  $sourceAsmVarName+1
                            sta  $targetAsmVarName+1""")
                        asmgen.signExtendLongVariable(targetAsmVarName, BaseDataType.WORD)
                    }
                    BaseDataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            lda  $sourceAsmVarName
                            ldy  $sourceAsmVarName+1
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            sourceDt.isFloat -> {
                asmgen.out("  lda  #<$sourceAsmVarName |  ldy  #>$sourceAsmVarName")
                when(targetDt.base) {
                    BaseDataType.BOOL -> asmgen.out("  jsr  floats.cast_as_bool_into_a |  sta  $targetAsmVarName")
                    BaseDataType.UBYTE -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName")
                    BaseDataType.BYTE -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName")
                    BaseDataType.UWORD -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
                    BaseDataType.WORD -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    BaseDataType.LONG -> {
                        asmgen.out("""
                            sta  cx16.r0L
                            sty  cx16.r0H
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            jsr  floats.cast_as_long""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            sourceDt.isString -> throw AssemblyError("cannot typecast a string value")
            sourceDt.isLong -> {
                if(targetDt.isByteOrBool) {
                    asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                } else if(targetDt.isWord || targetDt.isPointer) {
                    asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                } else if(targetDt.isFloat) {
                    asmgen.out("""
                        lda  #<$sourceAsmVarName
                        ldy  #>$sourceAsmVarName
                        sta  cx16.r1L
                        sty  cx16.r1H
                        lda  #<$targetAsmVarName
                        ldy  #>$targetAsmVarName
                        jsr  floats.internal_long_R1_to_float_AY""")
                } else
                    throw AssemblyError("weird type")
            }
            else -> throw AssemblyError("weird type")
        }
    }



    internal fun assignTypeCastedRegisters(targetAsmVarName: String, targetDt: BaseDataType,
                                          regs: RegisterOrPair, sourceDt: BaseDataType, position: Position) {
        if(sourceDt == targetDt && sourceDt != BaseDataType.POINTER)
            throw AssemblyError("typecast to identical type")

        // also see: PtExpressionAsmGen,   fun translateExpression(typecast: PtTypeCast)
        when(sourceDt) {
            BaseDataType.BOOL -> {
                if (targetDt.isByteOrBool) asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                else throw AssemblyError("assign bool to non-byte variable")
            }
            BaseDataType.UBYTE -> {
                when(targetDt) {
                    BaseDataType.BOOL -> {
                        val compare = if(regs==RegisterOrPair.A) "cmp" else "cp${regs.toString().lowercase()}"
                        asmgen.out("""
                            $compare  #0
                            beq  +
                            ld${regs.toString().lowercase()}  #1
+                           st${regs.toString().lowercase()}  $targetAsmVarName""")
                    }
                    BaseDataType.BYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.POINTER -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.LONG -> {
                        asmgen.out("""
                            st${regs.toString().lowercase()}  $targetAsmVarName
                            lda  #0
                            sta  $targetAsmVarName+1
                            sta  $targetAsmVarName+2
                            sta  $targetAsmVarName+3""")
                    }
                    BaseDataType.FLOAT -> {
                        when(regs) {
                            RegisterOrPair.A -> asmgen.out("  tay")
                            RegisterOrPair.X -> asmgen.out("  txa |  tay")
                            RegisterOrPair.Y -> {}
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            jsr  floats.cast_from_ub""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.BYTE -> {
                when(targetDt) {
                    BaseDataType.BOOL -> TODO("assign byte to bool $position")
                    BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    BaseDataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.WORD, BaseDataType.POINTER -> {
                        when(regs) {
                            RegisterOrPair.A -> {}
                            RegisterOrPair.X -> asmgen.out("  txa")
                            RegisterOrPair.Y -> asmgen.out("  tya")
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.signExtendAYlsb(sourceDt)
                        asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    }
                    BaseDataType.LONG -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                        asmgen.signExtendLongVariable(targetAsmVarName, sourceDt)
                    }
                    BaseDataType.FLOAT -> {
                        when(regs) {
                            RegisterOrPair.A -> {}
                            RegisterOrPair.X -> asmgen.out("  txa")
                            RegisterOrPair.Y -> asmgen.out("  tya")
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.out("""
                            ldy  #<$targetAsmVarName
                            sty  P8ZP_SCRATCH_W2
                            ldy  #>$targetAsmVarName
                            sty  P8ZP_SCRATCH_W2+1
                            jsr  floats.cast_from_b""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.UWORD -> {
                when(targetDt) {
                    BaseDataType.BOOL -> TODO("assign uword to bool $position")
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    BaseDataType.WORD, BaseDataType.POINTER -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    BaseDataType.LONG -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                        asmgen.out("  lda  #0 |  sta  $targetAsmVarName+2 |  sta  $targetAsmVarName+3")
                    }
                    BaseDataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            tax
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            txa
                            jsr  floats.cast_from_uw""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.WORD -> {
                when(targetDt) {
                    BaseDataType.BOOL -> TODO("assign word to bool $position")
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    BaseDataType.UWORD, BaseDataType.POINTER -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    BaseDataType.LONG -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                        asmgen.signExtendLongVariable(targetAsmVarName, BaseDataType.WORD)
                    }
                    BaseDataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            tax
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            txa
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.STR -> throw AssemblyError("cannot typecast a string value")
            BaseDataType.POINTER -> {
                if(targetDt.isWord || targetDt.isPointer) {
                    when(regs) {
                        RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                        RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                        RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                        else -> throw AssemblyError("non-word regs")
                    }
                } else if(targetDt.isLong) {
                    when(regs) {
                        RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                        RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                        RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                        else -> throw AssemblyError("non-word regs")
                    }
                    asmgen.out("  lda  #0 |  sta  $targetAsmVarName+2 |  sta  $targetAsmVarName+3")
                } else {
                    throw AssemblyError("cannot assign pointer to $targetDt")
                }
            }
            BaseDataType.LONG -> {
                val startreg = regs.startregname()
                if(targetDt.isByteOrBool) {
                    asmgen.out("  lda  cx16.$startreg |  sta  $targetAsmVarName")
                } else if(targetDt.isWord || targetDt.isPointer) {
                    asmgen.out("  lda  cx16.$startreg |  sta  $targetAsmVarName |  lda  cx16.$startreg+1 |  sta  $targetAsmVarName+1")
                } else if(targetDt.isFloat) {
                    if(regs==RegisterOrPair.R0R1)
                        throw AssemblyError("cannot assign long in R0R1 to float because r1 is used as a parameter $position")
                    asmgen.out("""
                        lda  #<cx16.$startreg
                        ldy  #>cx16.$startreg
                        sta  cx16.r1L
                        sty  cx16.r1H
                        lda  #<$targetAsmVarName
                        ldy  #>$targetAsmVarName
                        jsr  floats.internal_long_R1_to_float_AY""")
                } else
                    throw AssemblyError("weird type $targetDt")
            }
            else -> throw AssemblyError("weird type $sourceDt")
        }
    }

    /**
     * Compute 8-bit offset Y = idx*structSize + fieldOffset for a struct-array element.
     * Assumes totalBytes <=256 so offset fits in Y. Uses A/Y and multiply_bytes when needed.
     */

}
