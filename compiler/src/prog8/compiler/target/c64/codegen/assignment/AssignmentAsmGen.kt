package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.CpuType
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.target.c64.codegen.ExpressionsAsmGen
import prog8.compiler.toHex
import prog8.functions.BuiltinFunctions
import prog8.functions.builtinFunctionReturnType


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen, private val exprAsmgen: ExpressionsAsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, exprAsmgen, asmgen)

    fun translate(assignment: Assignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)

        val assign = AsmAssignment(source, target, assignment.isAugmentable, assignment.position)
        target.origAssign = assign

        if(assign.isAugmentable)
            augmentableAsmGen.translate(assign)
        else
            translateNormalAssignment(assign)
    }

    fun translateNormalAssignment(assign: AsmAssignment) {
        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toShort())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
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
                if (value.indexer.indexNum!=null) {
                    // constant array index value
                    val indexValue = value.indexer.constIndex()!! * elementDt.memorySize()
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
                val value = assign.source.memory!!
                when (value.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignMemoryByte(assign.target, null, value.addressExpression as IdentifierReference)
                    }
                    else -> {
                        assignExpressionToVariable(value.addressExpression, asmgen.asmVariableName("P8ZP_SCRATCH_W2"), DataType.UWORD, assign.target.scope)
                        asmgen.out("  ldy  #0 |  lda  (P8ZP_SCRATCH_W2),y")
                        assignRegisterByte(assign.target, CpuRegister.A)
                    }
                }
            }
            SourceStorageKind.EXPRESSION -> {
                when(val value = assign.source.expression!!) {
                    is AddressOf -> {
                        val sourceName = value.identifier.firstStructVarName(program.namespace) ?: asmgen.asmVariableName(value.identifier)
                        assignAddressOf(assign.target, sourceName)
                    }
                    is NumericLiteralValue -> throw AssemblyError("source kind should have been literalnumber")
                    is IdentifierReference -> throw AssemblyError("source kind should have been variable")
                    is ArrayIndexedExpression -> throw AssemblyError("source kind should have been array")
                    is DirectMemoryRead -> throw AssemblyError("source kind should have been memory")
                    is TypecastExpression -> assignTypeCastedValue(assign.target, value.type, value.expression, value)
                    is FunctionCall -> {
                        when (val sub = value.target.targetStatement(program.namespace)) {
                            is Subroutine -> {
                                val preserveStatusRegisterAfterCall = sub.asmReturnvaluesRegisters.any { it.statusflag != null }
                                asmgen.translateFunctionCall(value, preserveStatusRegisterAfterCall)
                                val returnValue = sub.returntypes.zip(sub.asmReturnvaluesRegisters).single { it.second.registerOrPair!=null }
                                when (returnValue.first) {
                                    DataType.STR -> {
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
                                        assignFAC1float(assign.target)
                                    }
                                    else -> {
                                        when (returnValue.second.registerOrPair) {
                                            RegisterOrPair.A -> assignRegisterByte(assign.target, CpuRegister.A)
                                            RegisterOrPair.X -> assignRegisterByte(assign.target, CpuRegister.X)
                                            RegisterOrPair.Y -> assignRegisterByte(assign.target, CpuRegister.Y)
                                            RegisterOrPair.AX -> assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                                            RegisterOrPair.AY -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                            RegisterOrPair.XY -> assignRegisterpairWord(assign.target, RegisterOrPair.XY)
                                            else -> throw AssemblyError("should be just one register byte result value")
                                        }
                                    }
                                }
                                if (preserveStatusRegisterAfterCall)
                                    asmgen.out("  plp\t; restore status flags from call")
                            }
                            is BuiltinFunctionStatementPlaceholder -> {
                                val signature = BuiltinFunctions.getValue(sub.name)
                                asmgen.translateBuiltinFunctionCallExpression(value, signature, false)
                                val returntype = builtinFunctionReturnType(sub.name, value.args, program)
                                if(!returntype.isKnown)
                                    throw AssemblyError("unknown dt")
                                when(returntype.typeOrElse(DataType.STRUCT)) {
                                    in ByteDatatypes -> assignRegisterByte(assign.target, CpuRegister.A)            // function's byte result is in A
                                    in WordDatatypes -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)    // function's word result is in AY
                                    DataType.STR -> throw AssemblyError("missing code for assign string from builtin func => copy string or assign string address")
                                    DataType.FLOAT -> {
                                        // float result from function sits in FAC1
                                        assignFAC1float(assign.target)
                                    }
                                    else -> throw AssemblyError("weird result type")
                                }
                            }
                            else -> {
                                throw AssemblyError("weird func call")
                            }
                        }
                    }
                    else -> {
                        // Everything else just evaluate via the stack.
                        // (we can't use the assignment helper functions to do it via registers here,
                        // because the code here is the implementation of exactly that...)
                        asmgen.translateExpression(value)
                        if(assign.target.datatype in WordDatatypes && assign.source.datatype in ByteDatatypes)
                            asmgen.signExtendStackLsb(assign.source.datatype)
                        assignStackValue(assign.target)
                    }
                }
            }
            SourceStorageKind.REGISTER -> {
                assignRegisterByte(assign.target, assign.source.register!!)
            }
            SourceStorageKind.STACK -> {
                assignStackValue(assign.target)
            }
        }
    }

    private fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: Expression, origTypeCastExpression: TypecastExpression) {
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("unknown dt")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
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
                    if (value.addressExpression is NumericLiteralValue) {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByteIntoWord(target, address, null)
                        return
                    }
                    else if (value.addressExpression is IdentifierReference) {
                        assignMemoryByteIntoWord(target, null, value.addressExpression as IdentifierReference)
                        return
                    }
                }
            }
            is NumericLiteralValue -> throw AssemblyError("a cast of a literal value should have been const-folded away")
            else -> {}
        }


        // special case optimizations
        if(target.kind==TargetStorageKind.VARIABLE) {
            if(value is IdentifierReference && valueDt != DataType.STRUCT)
                return assignTypeCastedIdentifier(target.asmVarname, targetDt, asmgen.asmVariableName(value), valueDt)

            when (valueDt) {
                in ByteDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.A)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.A, valueDt)
                }
                in WordDatatypes -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY)
                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, valueDt)
                }
                DataType.FLOAT -> {
                    assignExpressionToRegister(value, RegisterOrPair.FAC1)
                    assignTypecastedFloatFAC1(target.asmVarname, targetDt)
                }
                in PassByReferenceDatatypes -> {
                    // str/array value cast (most likely to UWORD, take address-of)
                    assignExpressionToVariable(value, target.asmVarname, targetDt, null)
                }
                else -> throw AssemblyError("strange dt in typecast assign to var: $valueDt  -->  $targetDt")
            }
            return
        }

        // give up, do it via eval stack
        // TODO optimize typecasts for more special cases?
        // note: cannot use assignTypeCastedValue because that is ourselves :P
        if(this.asmgen.options.slowCodegenWarnings)
            println("warning: slow stack evaluation used for typecast: $value into $targetDt (target=${target.kind} at ${value.position}")
        asmgen.translateExpression(origTypeCastExpression)      // this performs the actual type cast in translateExpression(Typecast)
        assignStackValue(target)
    }

    private fun assignTypecastedFloatFAC1(targetAsmVarName: String, targetDt: DataType) {

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
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
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
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
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
            DataType.STR -> {
                if (targetDt != DataType.UWORD && targetDt == DataType.STR)
                    throw AssemblyError("cannot typecast a string into another incompatitble type")
                TODO("assign typecasted string into target var")
            }
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
                        asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                            asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
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
                        asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName")
                    }
                    DataType.UWORD -> {
                        if(CompilationTarget.instance.machine.cpu==CpuType.CPU65c02)
                            asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  st${regs.toString().toLowerCase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
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
                        asmgen.out("  st${regs.toString().toLowerCase().first()}  $targetAsmVarName")
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
                        asmgen.out("  st${regs.toString().toLowerCase().first()}  $targetAsmVarName")
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
            DataType.STR -> {
                if (targetDt != DataType.UWORD && targetDt == DataType.STR)
                    throw AssemblyError("cannot typecast a string into another incompatitble type")
                TODO("assign typecasted string into target var")
            }
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
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("P8ESTACK_LO,x", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
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
                            RegisterOrPair.AX -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldx  #0")
                            RegisterOrPair.AY -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  #0")
                            else -> throw AssemblyError("can't assign byte from stack to register pair XY")
                        }
                    }
                    DataType.UWORD, DataType.WORD, in PassByReferenceDatatypes -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.AY-> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  P8ESTACK_HI,x")
                            RegisterOrPair.XY-> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
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
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            TargetStorageKind.ARRAY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to array ${target.asmVarname}")
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  #<$sourceName |  ldx  #>$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<$sourceName |  ldy  #>$sourceName")
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
                            lda #<$sourceName
                            ldy #>$sourceName
                            sta ${target.asmVarname}
                            sty ${target.asmVarname}+1
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
                    sty  P8ESTACK_HI,x
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
                    val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
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
+                                   jsr  floats.copy_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  $sourceName+1")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  $sourceName+1")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  $sourceName+1")
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
                if(target.array!!.indexer.indexNum!=null) {
                    val index = target.array.indexer.constIndex()!!
                    asmgen.out(" lda  #$index")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
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
                if(target.array!!.indexer.indexNum!=null) {
                    val index = target.array.indexer.constIndex()!!
                    asmgen.out(" lda  #$index")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
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
                if(target.array!!.indexer.indexNum!=null) {
                    val index = target.array.indexer.constIndex()!!
                    asmgen.out(" lda  #$index")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
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
                storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
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
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
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
                // TODO optimize slow stack evaluation for this case, see assignVariableUByteIntoWord
                if(this.asmgen.options.slowCodegenWarnings)
                    println("warning: slow stack evaluation used for sign-extend byte typecast at ${bytevar.position}")
                asmgen.translateExpression(wordtarget.origAssign.source.expression!!)
                assignStackValue(wordtarget)
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
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    lda  #0
                    sta  ${wordtarget.asmVarname}+1
                    """)
            }
            TargetStorageKind.ARRAY -> {
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2
                    asmgen.out(" lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx |  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.Y)
                    asmgen.out(" lda  $sourceName |  sta  ${wordtarget.asmVarname},y |  lda  #0 |  iny |  sta  ${wordtarget.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                    else -> throw AssemblyError("only reg pairs are words")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    lda  #0
                    sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        require(target.datatype in ByteDatatypes)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.toLowerCase()}  ${target.asmVarname}")
            }
            TargetStorageKind.MEMORY -> {
                storeRegisterInMemoryAddress(register, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                when {
                    target.constArrayIndexValue!=null -> {
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
                            CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+${target.constArrayIndexValue}")
                            CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+${target.constArrayIndexValue}")
                        }
                    }
                    else -> {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        asmgen.out(" ldy  ${asmgen.asmVariableName(target.array!!.indexer.indexVar!!)} |  sta  ${target.asmVarname},y")
                    }
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
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  txy") }
                        RegisterOrPair.AY -> { asmgen.out("  txa |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  tyx") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> { asmgen.out("  tya |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  tya |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tya |  tax |  ldy  #0") }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
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
        require(target.datatype in NumericDatatypes)
        if(target.datatype==DataType.FLOAT)
            throw AssemblyError("float value should be from FAC1 not from registerpair memory pointer")

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(regs) {
                    RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname} |  stx  ${target.asmVarname}+1")
                    RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.ARRAY -> {
                // TODO can be a bit more optimized if the array indexer is a number
                when(regs) {
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
            }
            TargetStorageKind.REGISTER -> {
                when(regs) {
                    RegisterOrPair.AX -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.AX -> { }
                        RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    RegisterOrPair.AY -> when(target.register!!) {
                        RegisterOrPair.AY -> { }
                        RegisterOrPair.AX -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { asmgen.out("  tax") }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    RegisterOrPair.XY -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  txa") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.STACK -> {
                when(regs) {
                    RegisterOrPair.AY -> asmgen.out(" sta  P8ESTACK_LO,x |  sty  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.AX, RegisterOrPair.XY -> throw AssemblyError("can't use X here")
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't store word into memory byte")
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
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
                    RegisterOrPair.AX -> asmgen.out("  lda  #<${word.toHex()} |  ldx  #>${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    else -> throw AssemblyError("can't assign word to single byte register")
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

    private fun assignConstantByte(target: AsmAssignTarget, byte: Short) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", target.memory!!)
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
                RegisterOrPair.AX -> asmgen.out("  lda  #${byte.toHex()} |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  #${byte.toHex()} |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  #${byte.toHex()} |  ldy  #0")
                RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
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
                    if(CompilationTarget.instance.machine.cpu == CpuType.CPU65c02)
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
                    if (target.array!!.indexer.indexNum!=null) {
                        val indexValue = target.array.indexer.constIndex()!! * DataType.FLOAT.memorySize()
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}+$indexValue
                            sta  ${target.asmVarname}+$indexValue+1
                            sta  ${target.asmVarname}+$indexValue+2
                            sta  ${target.asmVarname}+$indexValue+3
                            sta  ${target.asmVarname}+$indexValue+4
                        """)
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
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
                    if (target.array!!.indexer.indexNum!=null) {
                        val indexValue = target.array.indexer.constIndex()!! * DataType.FLOAT.memorySize()
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
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
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

    private fun assignMemoryByte(target: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmVarname}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${target.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  ${address.toHex()}")
                    RegisterOrPair.X -> asmgen.out("  ldx  ${address.toHex()}")
                    RegisterOrPair.Y -> asmgen.out("  ldy  ${address.toHex()}")
                    RegisterOrPair.AX -> asmgen.out("  lda  ${address.toHex()} |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldy  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
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
                    val sourceName = asmgen.asmVariableName(identifier)
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
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
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${wordtarget.asmVarname}
                        lda  #0
                        sta  ${wordtarget.asmVarname}+1
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${wordtarget.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  ${address.toHex()} |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldy  ${address.toHex()} |  ldy  #0")
                    else -> throw AssemblyError("word regs can only be pair")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        lda  #0
                        sta  P8ESTACK_HI,x
                        dex""")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        } else if (identifier != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${wordtarget.asmVarname} |  lda  #0 |  sta ${wordtarget.asmVarname}+1")
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
                    asmgen.out(" sta  P8ESTACK_LO,x |  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        }
    }

    private fun storeByteViaRegisterAInMemoryAddress(ldaInstructionArg: String, memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        when {
            addressLv != null -> {
                asmgen.out("  lda $ldaInstructionArg |  sta  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                asmgen.storeByteIntoPointer(addressExpr, ldaInstructionArg)
            }
            else -> {
                assignExpressionToVariable(addressExpr, asmgen.asmVariableName("P8ZP_SCRATCH_W2"), DataType.UWORD, null)
                asmgen.out("  ldy  #0 |  lda  $ldaInstructionArg |  sta  (P8ZP_SCRATCH_W2),y")
            }
        }
    }

    private fun storeRegisterInMemoryAddress(register: CpuRegister, memoryAddress: DirectMemoryWrite) {
        // this is optimized for register A.
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        val registerName = register.name.toLowerCase()
        when {
            addressLv != null -> {
                asmgen.out("  st$registerName  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                asmgen.storeByteIntoPointer(addressExpr, null)
            }
            else -> {
                asmgen.saveRegister(register, false, memoryAddress.definingSubroutine()!!)
                assignExpressionToVariable(addressExpr, asmgen.asmVariableName("P8ZP_SCRATCH_W2"), DataType.UWORD, null)
                asmgen.restoreRegister(CpuRegister.A, false)
                asmgen.out("  ldy  #0 |  sta  (P8ZP_SCRATCH_W2),y")
            }
        }
    }

    internal fun assignExpressionToRegister(expr: Expression, register: RegisterOrPair) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget.fromRegisters(register, null, program, asmgen)
        val assign = AsmAssignment(src, tgt, false, expr.position)
        translateNormalAssignment(assign)
    }

    internal fun assignExpressionToVariable(expr: Expression, asmVarName: String, dt: DataType, scope: Subroutine?) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, dt, scope, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, tgt, false, expr.position)
        translateNormalAssignment(assign)
    }

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair) {
        val tgt = AsmAssignTarget.fromRegisters(register, null, program, asmgen)
        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, tgt.datatype, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, tgt, false, Position.DUMMY)
        translateNormalAssignment(assign)
    }
}
