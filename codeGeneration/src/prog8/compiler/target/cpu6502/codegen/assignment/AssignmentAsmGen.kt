package prog8.compiler.target.cpu6502.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.toHex
import prog8.compiler.target.AssemblyError
import prog8.compiler.target.cpu6502.codegen.AsmGen
import prog8.compilerinterface.BuiltinFunctions
import prog8.compilerinterface.CpuType
import prog8.compilerinterface.builtinFunctionReturnType


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen)

    fun translate(assignment: Assignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)

        val assign = AsmAssignment(source, target, assignment.isAugmentable, program.memsizer, assignment.position)
        target.origAssign = assign

        if(assign.isAugmentable)
            augmentableAsmGen.translate(assign)
        else
            translateNormalAssignment(assign)
    }

    internal fun virtualRegsToVariables(origtarget: AsmAssignTarget): AsmAssignTarget {
        return if(origtarget.kind==TargetStorageKind.REGISTER && origtarget.register in Cx16VirtualRegisters) {
            AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, origtarget.datatype, origtarget.scope,
                variableAsmName = "cx16.${origtarget.register!!.name.lowercase()}", origAstTarget = origtarget.origAstTarget)
        } else origtarget
    }

    fun translateNormalAssignment(assign: AsmAssignment) {
        if(assign.isAugmentable) {
            augmentableAsmGen.translate(assign)
            return
        }

        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toInt())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num)
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.asmVarname
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignVariableByte(assign.target, variable)
                    DataType.WORD -> assignVariableWord(assign.target, variable)
                    DataType.UWORD -> {
                        if(assign.source.datatype in PassByReferenceDatatypes)
                            assignAddressOf(assign.target, variable)
                        else
                            assignVariableWord(assign.target, variable)
                    }
                    DataType.FLOAT -> assignVariableFloat(assign.target, variable)
                    DataType.STR -> assignVariableString(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val arrayVarName = asmgen.asmVariableName(value.arrayvar)
                val constIndex = value.indexer.constIndex()
                if (constIndex!=null) {
                    // constant array index value
                    val indexValue = constIndex * program.memsizer.memorySize(elementDt)
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue")
                            assignRegisterByte(assign.target, CpuRegister.A)
                        }
                        in WordDatatypes -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue |  ldy  $arrayVarName+$indexValue+1")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue")
                            assignFloatFromAY(assign.target)
                        }
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y")
                            assignRegisterByte(assign.target, CpuRegister.A)
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  pha |  lda  $arrayVarName+1,y |  tay |  pla")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>$arrayVarName
                                clc
                                adc  #<$arrayVarName
                                bcc  +
                                iny
+""")
                            assignFloatFromAY(assign.target)
                        }
                        else ->
                            throw AssemblyError("weird array elt type")
                    }
                }
            }
            SourceStorageKind.MEMORY -> {
                fun assignViaExprEval(expression: Expression) {
                    assignExpressionToVariable(expression, "P8ZP_SCRATCH_W2", DataType.UWORD, assign.target.scope)
                    asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                    assignRegisterByte(assign.target, CpuRegister.A)
                }

                val value = assign.source.memory!!
                when (value.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (value.addressExpression as NumericLiteralValue).number.toUInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignMemoryByte(assign.target, null, value.addressExpression as IdentifierReference)
                    }
                    is BinaryExpression -> {
                        if(asmgen.tryOptimizedPointerAccessWithA(value.addressExpression as BinaryExpression, false)) {
                            assignRegisterByte(assign.target, CpuRegister.A)
                        } else {
                            assignViaExprEval(value.addressExpression)
                        }
                    }
                    else -> assignViaExprEval(value.addressExpression)
                }
            }
            SourceStorageKind.EXPRESSION -> {
                when(val value = assign.source.expression!!) {
                    is AddressOf -> {
                        val sourceName = asmgen.asmSymbolName(value.identifier)
                        assignAddressOf(assign.target, sourceName)
                    }
                    is NumericLiteralValue -> throw AssemblyError("source kind should have been literalnumber")
                    is IdentifierReference -> throw AssemblyError("source kind should have been variable")
                    is ArrayIndexedExpression -> throw AssemblyError("source kind should have been array")
                    is DirectMemoryRead -> throw AssemblyError("source kind should have been memory")
                    is TypecastExpression -> assignTypeCastedValue(assign.target, value.type, value.expression, value)
                    is FunctionCall -> {
                        when (val sub = value.target.targetStatement(program)) {
                            is Subroutine -> {
                                asmgen.saveXbeforeCall(value)
                                asmgen.translateFunctionCall(value, true)
                                val returnValue = sub.returntypes.zip(sub.asmReturnvaluesRegisters).singleOrNull { it.second.registerOrPair!=null } ?:
                                    sub.returntypes.zip(sub.asmReturnvaluesRegisters).single { it.second.statusflag!=null }
                                when (returnValue.first) {
                                    DataType.STR -> {
                                        asmgen.restoreXafterCall(value)
                                        when(assign.target.datatype) {
                                            DataType.UWORD -> {
                                                // assign the address of the string result value
                                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                            }
                                            DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                                                // copy the actual string result into the target string variable
                                                asmgen.out("""
                                                    pha
                                                    lda  #<${assign.target.asmVarname}
                                                    sta  P8ZP_SCRATCH_W1
                                                    lda  #>${assign.target.asmVarname}
                                                    sta  P8ZP_SCRATCH_W1+1
                                                    pla
                                                    jsr  prog8_lib.strcpy""")
                                            }
                                            else -> throw AssemblyError("weird target dt")
                                        }
                                    }
                                    DataType.FLOAT -> {
                                        // float result from function sits in FAC1
                                        asmgen.restoreXafterCall(value)
                                        assignFAC1float(assign.target)
                                    }
                                    else -> {
                                        // do NOT restore X register before assigning the result values first
                                        when (returnValue.second.registerOrPair) {
                                            RegisterOrPair.A -> assignRegisterByte(assign.target, CpuRegister.A)
                                            RegisterOrPair.X -> assignRegisterByte(assign.target, CpuRegister.X)
                                            RegisterOrPair.Y -> assignRegisterByte(assign.target, CpuRegister.Y)
                                            RegisterOrPair.AX -> assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                                            RegisterOrPair.AY -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                            RegisterOrPair.XY -> assignRegisterpairWord(assign.target, RegisterOrPair.XY)
                                            else -> {
                                                val sflag = returnValue.second.statusflag
                                                if(sflag!=null)
                                                    assignStatusFlagByte(assign.target, sflag)
                                                else
                                                    throw AssemblyError("should be just one register byte result value")
                                            }
                                        }
                                        // we've processed the result value in the X register by now, so it's now finally safe to restore it
                                        asmgen.restoreXafterCall(value)
                                    }
                                }
                            }
                            is BuiltinFunctionStatementPlaceholder -> {
                                val signature = BuiltinFunctions.getValue(sub.name)
                                asmgen.translateBuiltinFunctionCallExpression(value, signature, false, assign.target.register)
                                if(assign.target.register==null) {
                                    // still need to assign the result to the target variable/etc.
                                    val returntype = builtinFunctionReturnType(sub.name, value.args, program)
                                    if(!returntype.isKnown)
                                        throw AssemblyError("unknown dt")
                                    when(returntype.getOr(DataType.UNDEFINED)) {
                                        in ByteDatatypes -> assignRegisterByte(assign.target, CpuRegister.A)            // function's byte result is in A
                                        in WordDatatypes -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)    // function's word result is in AY
                                        DataType.STR -> {
                                            when (assign.target.datatype) {
                                                DataType.STR -> {
                                                    asmgen.out("""
                                                        pha
                                                        lda  #<${assign.target.asmVarname}
                                                        sta  P8ZP_SCRATCH_W1
                                                        lda  #>${assign.target.asmVarname}
                                                        sta  P8ZP_SCRATCH_W1+1
                                                        pla
                                                        jsr  prog8_lib.strcpy""")
                                                }
                                                DataType.UWORD -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                                else -> throw AssemblyError("str return value type mismatch with target")
                                            }
                                        }
                                        DataType.FLOAT -> {
                                            // float result from function sits in FAC1
                                            assignFAC1float(assign.target)
                                        }
                                        else -> throw AssemblyError("weird result type")
                                    }
                                }
                            }
                            else -> {
                                throw AssemblyError("weird func call")
                            }
                        }
                    }
                    is PrefixExpression -> {
                        // first assign the value to the target then apply the operator in place on the target.
                        translateNormalAssignment(AsmAssignment(
                            AsmAssignSource.fromAstSource(value.expression, program, asmgen),
                            assign.target,
                            false, program.memsizer, assign.position
                        ))
                        val target = virtualRegsToVariables(assign.target)
                        when(value.operator) {
                            "+" -> {}
                            "-" -> augmentableAsmGen.inplaceNegate(target, target.datatype)
                            "~" -> augmentableAsmGen.inplaceInvert(target, target.datatype)
                            "not" -> augmentableAsmGen.inplaceBooleanNot(target, target.datatype)
                            else -> throw AssemblyError("invalid prefix operator")
                        }
                    }
                    else -> {
                        // Everything else just evaluate via the stack.
                        // (we can't use the assignment helper functions (assignExpressionTo...) to do it via registers here,
                        // because the code here is the implementation of exactly that...)
                        // TODO DON'T STACK-EVAL THIS... by using a temp var? so that it becomes augmentable assignment expression?
                        asmgen.translateExpression(value)
                        if (assign.target.datatype in WordDatatypes && assign.source.datatype in ByteDatatypes)
                            asmgen.signExtendStackLsb(assign.source.datatype)
                        if(assign.target.kind!=TargetStorageKind.STACK || assign.target.datatype != assign.source.datatype)
                            assignStackValue(assign.target)
                    }
                }
            }
            SourceStorageKind.REGISTER -> {
                asmgen.assignRegister(assign.source.register!!, assign.target)
            }
            SourceStorageKind.STACK -> {
                if(assign.target.kind!=TargetStorageKind.STACK || assign.target.datatype != assign.source.datatype)
                    assignStackValue(assign.target)
            }
        }
    }

    private fun assignStatusFlagByte(target: AsmAssignTarget, statusflag: Statusflag) {
        when(statusflag) {
            Statusflag.Pc -> {
                asmgen.out("  lda  #0 |  rol  a")
            }
            Statusflag.Pv -> {
                asmgen.out("""
                    bvs  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            else -> throw AssemblyError("can't use Z or N flags as return 'values'")
        }
        assignRegisterByte(target, CpuRegister.A)
    }

    private fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: Expression, origTypeCastExpression: TypecastExpression) {
        val valueIDt = value.inferType(program)
        val valueDt = valueIDt.getOrElse { throw AssemblyError("unknown dt") }
        if(valueDt==targetDt)
            throw AssemblyError("type cast to identical dt should have been removed")

        when(value) {
            is IdentifierReference -> {
                if(targetDt in WordDatatypes) {
                    if(valueDt==DataType.UBYTE) {
                        assignVariableUByteIntoWord(target, value)
                        return
                    }
                    if(valueDt==DataType.BYTE) {
                        assignVariableByteIntoWord(target, value)
                        return
                    }
                }
            }
            is DirectMemoryRead -> {
                if(targetDt in WordDatatypes) {

                    fun assignViaExprEval(addressExpression: Expression) {
                        asmgen.assignExpressionToVariable(addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                        asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                        assignRegisterByte(target, CpuRegister.A)
                    }

                    when (value.addressExpression) {
                        is NumericLiteralValue -> {
                            val address = (value.addressExpression as NumericLiteralValue).number.toUInt()
                            assignMemoryByteIntoWord(target, address, null)
                            return
                        }
                        is IdentifierReference -> {
                            assignMemoryByteIntoWord(target, null, value.addressExpression as IdentifierReference)
                            return
                        }
                        is BinaryExpression -> {
                            if(asmgen.tryOptimizedPointerAccessWithA(value.addressExpression as BinaryExpression, false)) {
                                asmgen.out("  ldy  #0")
                                assignRegisterpairWord(target, RegisterOrPair.AY)
                            } else {
                                assignViaExprEval(value.addressExpression)
                            }
                        }
                        else -> {
                            assignViaExprEval(value.addressExpression)
                        }
                    }
                }
            }
            is NumericLiteralValue -> throw AssemblyError("a cast of a literal value should have been const-folded away")
            else -> {}
        }


        // special case optimizations
        if(target.kind== TargetStorageKind.VARIABLE) {
            if(value is IdentifierReference && valueDt != DataType.UNDEFINED)
                return assignTypeCastedIdentifier(target.asmVarname, targetDt, asmgen.asmVariableName(value), valueDt)

            when (valueDt) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, valueDt==DataType.BYTE)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.A, valueDt)
                }
                in WordDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, valueDt==DataType.WORD)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, valueDt)
                }
                DataType.FLOAT -> {
                    assignExpressionToRegister(value, RegisterOrPair.FAC1, true)
                    assignTypeCastedFloatFAC1(target.asmVarname, targetDt)
                }
                in PassByReferenceDatatypes -> {
                    // str/array value cast (most likely to UWORD, take address-of)
                    assignExpressionToVariable(value, target.asmVarname, targetDt, null)
                }
                else -> throw AssemblyError("strange dt in typecast assign to var: $valueDt  -->  $targetDt")
            }
            return
        }

        if(origTypeCastExpression.type == DataType.UBYTE) {
            val parentTc = origTypeCastExpression.parent as? TypecastExpression
            if(parentTc!=null && parentTc.type==DataType.UWORD) {
                // typecast something to ubyte and directly back to uword
                // generate code for lsb(value) here instead of the ubyte typecast
                return assignCastViaLsbFunc(value, target)
            }
        }

        if(valueDt==DataType.UBYTE) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    // 'cast' an ubyte value to a byte register; no cast needed at all
                    return assignExpressionToRegister(value, target.register, false)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    // cast an ubyte value to a 16 bits register, just assign it and make use of the value extension
                    return assignExpressionToRegister(value, target.register!!, false)
                }
                else -> {}
            }
        } else if(valueDt==DataType.UWORD) {
            when(target.register) {
                RegisterOrPair.A,
                RegisterOrPair.X,
                RegisterOrPair.Y -> {
                    // cast an uword to a byte register, do this via lsb(value)
                    // generate code for lsb(value) here instead of the ubyte typecast
                    return assignCastViaLsbFunc(value, target)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    // 'cast' uword into a 16 bits register, just assign it
                    return assignExpressionToRegister(value, target.register!!, false)
                }
                else -> {}
            }
        }

        if(targetDt==DataType.FLOAT && (target.register==RegisterOrPair.FAC1 || target.register==RegisterOrPair.FAC2)) {
            when(valueDt) {
                DataType.UBYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.Y, false)
                    asmgen.out("  jsr  floats.FREADUY")
                }
                DataType.BYTE -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, true)
                    asmgen.out("  jsr  floats.FREADSA")
                }
                DataType.UWORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, false)
                    asmgen.out("  jsr  floats.GIVUAYFAY")
                }
                DataType.WORD -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  jsr  floats.GIVAYFAY")
                }
                else -> throw AssemblyError("invalid dt")
            }
            if(target.register==RegisterOrPair.FAC2) {
                asmgen.out("  jsr floats.MOVEF")
            }
        } else {
            // No more special optmized cases yet. Do the rest via more complex evaluation
            // note: cannot use assignTypeCastedValue because that is ourselves :P
            // NOTE: THIS MAY TURN INTO A STACK OVERFLOW ERROR IF IT CAN'T SIMPLIFY THE TYPECAST..... :-/
            asmgen.assignExpressionTo(origTypeCastExpression, target)
        }
    }

    private fun assignCastViaLsbFunc(value: Expression, target: AsmAssignTarget) {
        val lsb = FunctionCall(IdentifierReference(listOf("lsb"), value.position), mutableListOf(value), value.position)
        lsb.linkParents(value.parent)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UBYTE, expression = lsb)
        val assign = AsmAssignment(src, target, false, program.memsizer, value.position)
        translateNormalAssignment(assign)
    }

    private fun assignTypeCastedFloatFAC1(targetAsmVarName: String, targetDt: DataType) {

        if(targetDt==DataType.FLOAT)
            throw AssemblyError("typecast to identical type")

        when(targetDt) {
            DataType.UBYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName")
            DataType.BYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName")
            DataType.UWORD -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
            DataType.WORD -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedIdentifier(targetAsmVarName: String, targetDt: DataType,
                                           sourceAsmVarName: String, sourceDt: DataType) {
        if(sourceDt == targetDt)
            throw AssemblyError("typecast to identical type")

        // also see: ExpressionAsmGen,   fun translateExpression(typecast: TypecastExpression)
        when(sourceDt) {
            DataType.UBYTE -> {
                when(targetDt) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetAsmVarName
                            ldy  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            sty  P8ZP_SCRATCH_W2+1
                            ldy  $sourceAsmVarName
                            jsr  floats.cast_from_ub""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.BYTE -> {
                when(targetDt) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.WORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                        asmgen.signExtendVariableLsb(targetAsmVarName, DataType.BYTE)
                    }
                    DataType.FLOAT -> {
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
            DataType.UWORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.WORD, DataType.UWORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
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
            DataType.WORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName")
                    }
                    DataType.WORD, DataType.UWORD -> {
                        asmgen.out("  lda  $sourceAsmVarName |  sta  $targetAsmVarName |  lda  $sourceAsmVarName+1 |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
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
            DataType.FLOAT -> {
                asmgen.out("  lda  #<$sourceAsmVarName |  ldy  #>$sourceAsmVarName")
                when(targetDt) {
                    DataType.UBYTE -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName")
                    DataType.BYTE -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName")
                    DataType.UWORD -> asmgen.out("  jsr  floats.cast_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
                    DataType.WORD -> asmgen.out("  jsr  floats.cast_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedRegisters(targetAsmVarName: String, targetDt: DataType,
                                          regs: RegisterOrPair, sourceDt: DataType) {
        if(sourceDt == targetDt)
            throw AssemblyError("typecast to identical type")

        // also see: ExpressionAsmGen,   fun translateExpression(typecast: TypecastExpression)
        when(sourceDt) {
            DataType.UBYTE -> {
                when(targetDt) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
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
            DataType.BYTE -> {
                when(targetDt) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out(
                                "  st${
                                    regs.toString().lowercase()
                                }  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    DataType.WORD -> {
                        when(regs) {
                            RegisterOrPair.A -> {}
                            RegisterOrPair.X -> asmgen.out("  txa")
                            RegisterOrPair.Y -> asmgen.out("  tya")
                            else -> throw AssemblyError("non-byte regs")
                        }
                        asmgen.signExtendAYlsb(sourceDt)
                        asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                    }
                    DataType.FLOAT -> {
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
            DataType.UWORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    DataType.WORD, DataType.UWORD -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    DataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            pha
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            pla
                            jsr  floats.cast_from_uw""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.WORD -> {
                when(targetDt) {
                    DataType.BYTE, DataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    DataType.WORD, DataType.UWORD -> {
                        when(regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  $targetAsmVarName |  stx  $targetAsmVarName+1")
                            RegisterOrPair.AY -> asmgen.out("  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            RegisterOrPair.XY -> asmgen.out("  stx  $targetAsmVarName |  sty  $targetAsmVarName+1")
                            else -> throw AssemblyError("non-word regs")
                        }
                    }
                    DataType.FLOAT -> {
                        if(regs!=RegisterOrPair.AY)
                            throw AssemblyError("only supports AY here")
                        asmgen.out("""
                            pha
                            lda  #<$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2
                            lda  #>$targetAsmVarName
                            sta  P8ZP_SCRATCH_W2+1
                            pla
                            jsr  floats.cast_from_w""")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.STR -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignStackValue(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            sta  ${target.asmVarname}
                            lda  P8ESTACK_HI,x
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  floats.pop_float
                        """)
                    }
                    DataType.STR -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            inx
                            lda  P8ESTACK_HI,x
                            tay
                            lda  P8ESTACK_LO,x
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("weird target variable type ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  inx |  lda  P8ESTACK_LO,x")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname}+$scaledIdx
                                lda  P8ESTACK_HI,x
                                sta  ${target.asmVarname}+$scaledIdx+1
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<${target.asmVarname}+$scaledIdx
                                ldy  #>${target.asmVarname}+$scaledIdx
                                jsr  floats.pop_float
                            """)
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    target.array!!
                    when(target.datatype) {
                        DataType.UBYTE, DataType.BYTE -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out(" inx |  lda  P8ESTACK_LO,x |  sta  ${target.asmVarname},y")
                        }
                        DataType.UWORD, DataType.WORD -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname},y
                                lda  P8ESTACK_HI,x
                                sta  ${target.asmVarname}+1,y
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>${target.asmVarname}
                                clc
                                adc  #<${target.asmVarname}
                                bcc  +
                                iny
+                               jsr  floats.pop_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out(" inx |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.X -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.Y -> asmgen.out(" inx |  ldy  P8ESTACK_LO,x")
                            RegisterOrPair.AX -> asmgen.out(" inx |  txy |  ldx  #0 |  lda  P8ESTACK_LO,y")
                            RegisterOrPair.AY -> asmgen.out(" inx |  ldy  #0 |  lda  P8ESTACK_LO,x")
                            in Cx16VirtualRegisters -> {
                                asmgen.out(
                                    """
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  cx16.${target.register.toString().lowercase()}
                                    lda  #0
                                    sta  cx16.${target.register.toString().lowercase()}+1
                                """)
                            }
                            else -> throw AssemblyError("can't assign byte from stack to register pair XY")
                        }
                    }
                    DataType.UWORD, DataType.WORD, in PassByReferenceDatatypes -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.AY-> asmgen.out(" inx |  ldy  P8ESTACK_HI,x |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.XY-> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            in Cx16VirtualRegisters -> {
                                asmgen.out(
                                    """
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  cx16.${target.register.toString().lowercase()}
                                    lda  P8ESTACK_HI,x
                                    sta  cx16.${target.register.toString().lowercase()}+1
                                """)
                            }
                            else -> throw AssemblyError("can't assign word to single byte register")
                        }
                    }
                    DataType.FLOAT -> {
                        when(target.register!!) {
                            RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.pop_float_fac1")
                            RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.pop_float_fac2")
                            else -> throw AssemblyError("can only assign float to Fac1 or 2")
                        }
                    }

                    else -> throw AssemblyError("weird dt")
                }
            }
            TargetStorageKind.STACK -> {}
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1
                    """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("can't store word into memory byte")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("  lda  #<$sourceName |  ldy #>$sourceName")
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>$sourceName |  ldx  #<$sourceName")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  #<$sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #>$sourceName
                            sta  cx16.${target.register.toString().lowercase()}+1
                        """)
                    }
                    else -> throw AssemblyError("can't load address in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #>$sourceName
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignVariableString(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(target.datatype) {
                    DataType.UWORD -> {
                        asmgen.out("""
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            sta  ${target.asmVarname}
                            sty  ${target.asmVarname}+1
                        """)
                    }
                    DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("assign string to incompatible variable type")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName+1
                    sta  P8ESTACK_LO,x
                    tya
                    sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("string-assign to weird target")
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                target.array!!
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            asmgen.out("""
                                lda  $sourceName
                                sta  ${target.asmVarname}+$scaledIdx
                                lda  $sourceName+1
                                sta  ${target.asmVarname}+$scaledIdx+1
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<$sourceName
                                ldy  #>$sourceName
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  #<${target.asmVarname}+$scaledIdx
                                ldy  #>${target.asmVarname}+$scaledIdx
                                jsr  floats.copy_float
                            """)
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    when(target.datatype) {
                        DataType.UBYTE, DataType.BYTE -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                        }
                        DataType.UWORD, DataType.WORD -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out("""
                                lda  $sourceName
                                sta  ${target.asmVarname},y
                                lda  $sourceName+1
                                sta  ${target.asmVarname}+1,y
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                            asmgen.out("""
                                ldy  #<$sourceName
                                sty  P8ZP_SCRATCH_W1
                                ldy  #>$sourceName
                                sty  P8ZP_SCRATCH_W1+1
                                ldy  #>${target.asmVarname}
                                clc
                                adc  #<${target.asmVarname}
                                bcc  +
                                iny
+                               jsr  floats.copy_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  $sourceName+1 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  $sourceName+1 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  $sourceName+1 |  ldx  $sourceName")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  $sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  $sourceName+1
                            sta  cx16.${target.register.toString().lowercase()}+1
                        """)
                    }
                    else -> throw AssemblyError("can't load word in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    lda  $sourceName+1
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    internal fun assignFAC2float(target: AsmAssignTarget) {
        asmgen.out("  jsr  floats.MOVFA")       // fac2 -> fac1
        assignFAC1float(target)
    }

    internal fun assignFAC1float(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    stx  P8ZP_SCRATCH_REG
                    ldx  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.MOVMF
                    ldx  P8ZP_SCRATCH_REG
                """)
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1""")
                val constIndex = target.array!!.indexer.constIndex()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexExpr as IdentifierReference)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out("  jsr  floats.set_array_float_from_fac1")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                if (target.register!! != RegisterOrPair.FAC1)
                    throw AssemblyError("can't assign Fac1 float to another fac register")
            }
            TargetStorageKind.STACK -> asmgen.out("  jsr  floats.push_fac1")
        }
    }

    private fun assignFloatFromAY(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1                    
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    jsr  floats.copy_float""")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1""")
                val constIndex = target.array!!.indexer.constIndex()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexExpr as IdentifierReference)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out(" jsr  floats.set_array_float")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
            TargetStorageKind.STACK -> asmgen.out("  jsr  floats.push_float")
        }
    }

    private fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmVarname}
                    lda  $sourceName+1
                    sta  ${target.asmVarname}+1
                    lda  $sourceName+2
                    sta  ${target.asmVarname}+2
                    lda  $sourceName+3
                    sta  ${target.asmVarname}+3
                    lda  $sourceName+4
                    sta  ${target.asmVarname}+4
                """)
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1""")
                val constIndex = target.array!!.indexer.constIndex()
                if(constIndex!=null) {
                    asmgen.out(" lda  #$constIndex")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexExpr as IdentifierReference)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out(" jsr  floats.set_array_float")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
            TargetStorageKind.STACK -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  floats.push_float")
        }
    }

    private fun assignVariableByte(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmVarname}
                    """)
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  lda  $sourceName")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * program.memsizer.memorySize(target.datatype).toUInt()
                    asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, target.datatype, CpuRegister.Y)
                    asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  $sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                    }
                    else -> throw AssemblyError("weird register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when (wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  ${wordtarget.asmVarname}+1
                    """)
            }
            TargetStorageKind.ARRAY -> {
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname}+$scaledIdx |  sty  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.saveRegisterLocal(CpuRegister.X, wordtarget.scope!!)
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.X)
                    asmgen.out("  lda  $sourceName")
                    asmgen.signExtendAYlsb(DataType.BYTE)
                    asmgen.out("  sta  ${wordtarget.asmVarname},x |  inx |  tya |  sta  ${wordtarget.asmVarname},x")
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        ldx  #0
+                       tax
                        pla""")
                    RegisterOrPair.AY -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        ldy  #0
+                       tay
                        pla""")
                    RegisterOrPair.XY -> asmgen.out("""
                        lda  $sourceName
                        tax 
                        ora  #$7f
                        bmi  +
                        ldy  #0
+                       tay""")
                    else -> throw AssemblyError("only reg pairs are words")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    ora  #$7f
                    bmi  +                    
                    lda  #0
+                   sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    private fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when(wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  $sourceName |  sta  ${wordtarget.asmVarname}")
                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                    asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                else
                    asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
            }
            TargetStorageKind.ARRAY -> {
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                    asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+$scaledIdx+1")
                    else
                        asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.Y)
                    asmgen.out("""
                        lda  $sourceName
                        sta  ${wordtarget.asmVarname},y
                        iny
                        lda  #0
                        sta  ${wordtarget.asmVarname},y""")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                    else -> throw AssemblyError("only reg pairs are words")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("  lda  $sourceName |  sta  P8ESTACK_LO,x")
                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                    asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                else
                    asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        // we make an exception in the type check for assigning something to a cx16 virtual register, or a register pair
        // these will be correctly typecasted from a byte to a word value
        if(target.register !in Cx16VirtualRegisters &&
            target.register!=RegisterOrPair.AX && target.register!=RegisterOrPair.AY && target.register!=RegisterOrPair.XY) {
            if(target.kind== TargetStorageKind.VARIABLE) {
                val parts = target.asmVarname.split('.')
                if (parts.size != 2 || parts[0] != "cx16")
                    require(target.datatype in ByteDatatypes)
            } else {
                require(target.datatype in ByteDatatypes)
            }
        }

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.lowercase()}  ${target.asmVarname}")
            }
            TargetStorageKind.MEMORY -> {
                when(register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    when (register) {
                        CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
                        CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+${target.constArrayIndexValue}")
                        CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+${target.constArrayIndexValue}")
                    }
                }
                else {
                    when (register) {
                        CpuRegister.A -> {}
                        CpuRegister.X -> asmgen.out(" txa")
                        CpuRegister.Y -> asmgen.out(" tya")
                    }
                    val indexVar = target.array!!.indexer.indexExpr as IdentifierReference
                    asmgen.out(" ldy  ${asmgen.asmVariableName(indexVar)} |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tax |  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  sta  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  txy") }
                        RegisterOrPair.AY -> { asmgen.out("  txa |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  stx  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  tyx") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> { asmgen.out("  tya |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  tya |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tya |  tax |  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            // only assign a single byte to the virtual register's Lsb
                            asmgen.out("  sty  cx16.${target.register.toString().lowercase()}")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
            }
            TargetStorageKind.STACK -> {
                when(register) {
                    CpuRegister.A -> asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                    CpuRegister.X -> throw AssemblyError("can't use X here")
                    CpuRegister.Y -> asmgen.out(" tya |  sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    internal fun assignRegisterpairWord(target: AsmAssignTarget, regs: RegisterOrPair) {
        require(target.datatype in NumericDatatypes || target.datatype in PassByReferenceDatatypes)
        if(target.datatype==DataType.FLOAT)
            throw AssemblyError("float value should be from FAC1 not from registerpair memory pointer")

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(regs) {
                    RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname} |  stx  ${target.asmVarname}+1")
                    RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.out("""
                            lda  $srcReg
                            sta  ${target.asmVarname}
                            lda  $srcReg+1
                            sta  ${target.asmVarname}+1""")
                    }
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                }
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    val idx = target.constArrayIndexValue!! * 2u
                    when (regs) {
                        RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname}+$idx |  stx  ${target.asmVarname}+$idx+1")
                        RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname}+$idx |  sty  ${target.asmVarname}+$idx+1")
                        RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname}+$idx |  sty  ${target.asmVarname}+$idx+1")
                        in Cx16VirtualRegisters -> {
                            val srcReg = asmgen.asmSymbolName(regs)
                            asmgen.out("""
                                lda  $srcReg
                                sta  ${target.asmVarname}+$idx
                                lda  $srcReg+1
                                sta  ${target.asmVarname}+$idx+1""")
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                }
                else {
                    if (regs !in Cx16VirtualRegisters) {
                        when (regs) {
                            RegisterOrPair.AX -> asmgen.out("  pha |  txa |  pha")
                            RegisterOrPair.AY -> asmgen.out("  pha |  tya |  pha")
                            RegisterOrPair.XY -> asmgen.out("  txa |  pha |  tya |  pha")
                            else -> throw AssemblyError("expected reg pair")
                        }
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y, true)
                        asmgen.out("""
                            pla
                            sta  ${target.asmVarname},y
                            dey
                            pla
                            sta  ${target.asmVarname},y""")
                    } else {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y, true)
                        asmgen.out("""
                            lda  $srcReg+1
                            sta  ${target.asmVarname},y
                            dey
                            lda  $srcReg
                            sta  ${target.asmVarname},y""")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(regs) {
                    RegisterOrPair.AX -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.AX -> { }
                        RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    sta  cx16.${target.register.toString().lowercase()}
                                    stx  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.AY -> when(target.register!!) {
                        RegisterOrPair.AY -> { }
                        RegisterOrPair.AX -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { asmgen.out("  tax") }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    sta  cx16.${target.register.toString().lowercase()}
                                    sty  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.XY -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  txa") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { }
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                    stx  cx16.${target.register.toString().lowercase()}
                                    sty  cx16.${target.register.toString().lowercase()}+1
                                """)
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        if(regs!=target.register) {
                            when(target.register) {
                                RegisterOrPair.AX -> asmgen.out("  lda  $srcReg |  ldx  $srcReg+1")
                                RegisterOrPair.AY -> asmgen.out("  lda  $srcReg |  ldy  $srcReg+1")
                                RegisterOrPair.XY -> asmgen.out("  ldx  $srcReg |  ldy  $srcReg+1")
                                in Cx16VirtualRegisters -> {
                                    val targetReg = asmgen.asmSymbolName(target.register!!)
                                    asmgen.out("  lda  $srcReg |  sta  $targetReg |  lda  $srcReg+1 |  sta  $targetReg+1")
                                }
                                else -> throw AssemblyError("invalid reg")
                            }
                        }
                    }
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                when(regs) {
                    RegisterOrPair.AY -> asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.AX, RegisterOrPair.XY -> throw AssemblyError("can't use X here")
                    in Cx16VirtualRegisters -> {
                        val srcReg = asmgen.asmSymbolName(regs)
                        asmgen.out("""
                            lda  $srcReg
                            sta  P8ESTACK_LO,x
                            lda  $srcReg+1
                            sta  P8ESTACK_HI,x
                            dex""")
                    }
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                }
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't store word into memory byte")
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
        if(word==0 && asmgen.isTargetCpu(CpuType.CPU65c02)) {
            // optimize setting zero value for this processor
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} |  stz  ${target.asmVarname}+1")
                }
                TargetStorageKind.MEMORY -> {
                    throw AssemblyError("memory is bytes not words")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y)
                    asmgen.out("""
                        lda  #0
                        sta  ${target.asmVarname},y
                        sta  ${target.asmVarname}+1,y
                    """)
                }
                TargetStorageKind.REGISTER -> {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  lda  #0 |  tax")
                        RegisterOrPair.AY -> asmgen.out("  lda  #0 |  tay")
                        RegisterOrPair.XY -> asmgen.out("  ldx  #0 |  ldy  #0")
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                "  stz  cx16.${
                                    target.register.toString().lowercase()
                                } |  stz  cx16.${target.register.toString().lowercase()}+1")
                        }
                        else -> throw AssemblyError("invalid register for word value")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("  stz  P8ESTACK_LO,x |  stz  P8ESTACK_HI,x |  dex")
                }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if (word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmVarname}
                    sta  ${target.asmVarname}+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
                }
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y)
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  ${target.asmVarname},y
                    lda  #>${word.toHex()}
                    sta  ${target.asmVarname}+1,y
                """)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>${word.toHex()} |  ldx  #<${word.toHex()}")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  #<${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #>${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                    }
                    else -> throw AssemblyError("invalid register for word value")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  P8ESTACK_LO,x
                    lda  #>${word.toHex()}
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignConstantByte(target: AsmAssignTarget, byte: Int) {
        if(byte==0 && asmgen.isTargetCpu(CpuType.CPU65c02)) {
            // optimize setting zero value for this cpu
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} ")
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.out("  lda  #0")
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    if (target.constArrayIndexValue!=null) {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  stz  ${target.asmVarname}+$indexValue")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                        asmgen.out("  lda  #0 |  sta  ${target.asmVarname},y")
                    }
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  #0")
                    RegisterOrPair.X -> asmgen.out("  ldx  #0")
                    RegisterOrPair.Y -> asmgen.out("  ldy  #0")
                    RegisterOrPair.AX -> asmgen.out("  lda  #0 |  tax")
                    RegisterOrPair.AY -> asmgen.out("  lda  #0 |  tay")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #0 |  ldy  #0")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            "  stz  cx16.${
                                target.register.toString().lowercase()
                            } |  stz  cx16.${target.register.toString().lowercase()}+1")
                    }
                    else -> throw AssemblyError("weird register")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("  stz  P8ESTACK_LO,x |  dex")
                }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  lda  #${byte.toHex()}")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    val indexValue = target.constArrayIndexValue!!
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                    asmgen.out("  lda  #<${byte.toHex()} |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> when(target.register!!) {
                RegisterOrPair.A -> asmgen.out("  lda  #${byte.toHex()}")
                RegisterOrPair.X -> asmgen.out("  ldx  #${byte.toHex()}")
                RegisterOrPair.Y -> asmgen.out("  ldy  #${byte.toHex()}")
                RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  #${byte.toHex()}")
                RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  #${byte.toHex()}")
                RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  #${byte.toHex()}")
                RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                in Cx16VirtualRegisters -> {
                    asmgen.out(
                        "  lda  #${byte.toHex()} |  sta  cx16.${
                            target.register.toString().lowercase()
                        }")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1\n")
                    else
                        asmgen.out(
                            "  lda  #0 |  sta  cx16.${
                                target.register.toString().lowercase()
                            }+1\n")
                }
                else -> throw AssemblyError("weird register")
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #${byte.toHex()}
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("""
                            stz  ${target.asmVarname}
                            stz  ${target.asmVarname}+1
                            stz  ${target.asmVarname}+2
                            stz  ${target.asmVarname}+3
                            stz  ${target.asmVarname}+4
                        """)
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}
                            sta  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    val constIndex = target.array!!.indexer.constIndex()
                    if (constIndex!=null) {
                        val indexValue = constIndex * program.memsizer.memorySize(DataType.FLOAT)
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("""
                                stz  ${target.asmVarname}+$indexValue
                                stz  ${target.asmVarname}+$indexValue+1
                                stz  ${target.asmVarname}+$indexValue+2
                                stz  ${target.asmVarname}+$indexValue+3
                                stz  ${target.asmVarname}+$indexValue+4
                            """)
                        else
                            asmgen.out("""
                                lda  #0
                                sta  ${target.asmVarname}+$indexValue
                                sta  ${target.asmVarname}+$indexValue+1
                                sta  ${target.asmVarname}+$indexValue+2
                                sta  ${target.asmVarname}+$indexValue+3
                                sta  ${target.asmVarname}+$indexValue+4
                            """)
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexExpr as IdentifierReference)
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  $asmvarname  
                            jsr  floats.set_0_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
                }
            }
        } else {
            // non-zero value
            val constFloat = asmgen.getFloatAsmConst(float)
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                            lda  $constFloat
                            sta  ${target.asmVarname}
                            lda  $constFloat+1
                            sta  ${target.asmVarname}+1
                            lda  $constFloat+2
                            sta  ${target.asmVarname}+2
                            lda  $constFloat+3
                            sta  ${target.asmVarname}+3
                            lda  $constFloat+4
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    val arrayVarName = target.asmVarname
                    val constIndex = target.array!!.indexer.constIndex()
                    if (constIndex!=null) {
                        val indexValue = constIndex * program.memsizer.memorySize(DataType.FLOAT)
                        asmgen.out("""
                            lda  $constFloat
                            sta  $arrayVarName+$indexValue
                            lda  $constFloat+1
                            sta  $arrayVarName+$indexValue+1
                            lda  $constFloat+2
                            sta  $arrayVarName+$indexValue+2
                            lda  $constFloat+3
                            sta  $arrayVarName+$indexValue+3
                            lda  $constFloat+4
                            sta  $arrayVarName+$indexValue+4
                        """)
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexExpr as IdentifierReference)
                        asmgen.out("""
                            lda  #<${constFloat}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${constFloat}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  #<${arrayVarName}
                            sta  P8ZP_SCRATCH_W2
                            lda  #>${arrayVarName}
                            sta  P8ZP_SCRATCH_W2+1
                            lda  $asmvarname
                            jsr  floats.set_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
                }
            }
        }
    }

    private fun assignMemoryByte(target: AsmAssignTarget, address: UInt?, identifier: IdentifierReference?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmVarname}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.out("  lda  ${address.toHex()}")
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${target.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  ${address.toHex()}")
                    RegisterOrPair.X -> asmgen.out("  ldx  ${address.toHex()}")
                    RegisterOrPair.Y -> asmgen.out("  ldy  ${address.toHex()}")
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldy  ${address.toHex()}")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out(
                            """
                            lda  ${address.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1
                        """)
                    }
                    else -> throw AssemblyError("weird register")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        dex""")
                }
            }
        } else if (identifier != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${target.asmVarname}")
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $identifier to array ${target.asmVarname} ")
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> asmgen.out("  tax")
                        RegisterOrPair.Y -> asmgen.out("  tay")
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                        in Cx16VirtualRegisters -> {
                            asmgen.out(
                                """
                                sta  cx16.${target.register.toString().lowercase()}
                                lda  #0
                                sta  cx16.${target.register.toString().lowercase()}+1
                            """)
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: UInt?, identifier: IdentifierReference?) {
        if (address != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  lda  ${address.toHex()} |  sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${wordtarget.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldy  ${address.toHex()}")
                    else -> throw AssemblyError("word regs can only be pair")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("  lda  ${address.toHex()} |  sta  P8ESTACK_LO,x")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                    else
                        asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        } else if (identifier != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $identifier to array ${wordtarget.asmVarname} ")
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(wordtarget.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        else -> throw AssemblyError("word regs can only be pair")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out("  sta  P8ESTACK_LO,x")
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  P8ESTACK_HI,x |  dex")
                    else
                        asmgen.out("  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        }
    }

    private fun storeRegisterAInMemoryAddress(memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue

        fun storeViaExprEval() {
            when(addressExpr) {
                is NumericLiteralValue, is IdentifierReference -> {
                    assignExpressionToVariable(addressExpr, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                    asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                }
                else -> {
                    // same as above but we need to save the A register
                    asmgen.out("  pha")
                    assignExpressionToVariable(addressExpr, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                    asmgen.out("  pla")
                    asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                }
            }
        }

        when {
            addressLv != null -> {
                asmgen.out("  sta  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                asmgen.storeAIntoPointerVar(addressExpr)
            }
            addressExpr is BinaryExpression -> {
                if(!asmgen.tryOptimizedPointerAccessWithA(addressExpr, true))
                    storeViaExprEval()
            }
            else -> storeViaExprEval()
        }
    }

    internal fun assignExpressionToRegister(expr: Expression, register: RegisterOrPair, signed: Boolean) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget.fromRegisters(register, signed, null, program, asmgen)
        val assign = AsmAssignment(src, tgt, false, program.memsizer, expr.position)
        translateNormalAssignment(assign)
    }

    internal fun assignExpressionToVariable(expr: Expression, asmVarName: String, dt: DataType, scope: Subroutine?) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, dt, scope, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, tgt, false, program.memsizer, expr.position)
        translateNormalAssignment(assign)
    }

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean) {
        val tgt = AsmAssignTarget.fromRegisters(register, signed, null, program, asmgen)
        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, tgt.datatype, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, tgt, false, program.memsizer, Position.DUMMY)
        translateNormalAssignment(assign)
    }
}
