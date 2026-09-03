package prog8.codegen.cpu6502.assignment

import prog8.code.StStruct
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator
import prog8.codegen.cpu6502.toLongHex

internal class PrimitiveAssignmentsGen(
    private val program: PtProgram,
    private val asmgen: AsmGen6502Internal,
    private val pointergen: PointerAssignmentsGen,
    private val allocator: VariableAllocator
) {
    lateinit var assignmentAsmGen: AssignmentAsmGen

    internal fun assignVirtualRegister(target: AsmAssignTarget, register: RegisterOrPair) {
        // Note: while the virtual register R0-R15 can hold a word value,
        // the actual datatype that gets assigned is determined by the assignment target.
        // This can be a single byte!
        when {
            target.datatype.isByteOrBool -> {
                if(register in Cx16VirtualRegisters) {
                    asmgen.out("  lda  cx16.${register.toString().lowercase()}L")
                } else {
                    TODO("LDA byte from $register  ${target.position}")
                }
                assignRegisterByte(target, CpuRegister.A, false, false)
            }
            target.datatype.isWord || target.datatype.isPointer -> assignRegisterpairWord(target, register)
            target.datatype.isLong -> {
                require(register in CombinedLongRegisters)
                assignRegisterLong(target, register)
            }
            else -> throw AssemblyError("expected byte or word")
        }
    }


    internal fun computeStructArrayOffsetY(arrayDt: DataType, indexExpr: PtExpression, fieldOffset: Int = 0) {
        require(arrayDt.sub == BaseDataType.STRUCT_INSTANCE) { "expected struct instance array $arrayDt" }
        val structSize = try {
            (arrayDt.subType as? StStruct)?.size?.toInt() ?: program.memsizer.memorySize(arrayDt.elementType(), 1)
        } catch(_: Exception) {
            program.memsizer.memorySize(arrayDt.elementType(), 1)
        }
        // load idx into A
        asmgen.assignExpressionToRegister(indexExpr, RegisterOrPair.A)
        when(structSize) {
            1 -> {}
            2 -> asmgen.out("  asl  a")
            4 -> asmgen.out("  asl  a |  asl  a")
            8 -> asmgen.out("  asl  a |  asl  a |  asl  a")
            else -> asmgen.out("  ldy  #$structSize |  jsr  prog8_math.multiply_bytes")
        }
        if(fieldOffset!=0) {
            asmgen.out("  clc |  adc  #$fieldOffset")
        }
        asmgen.out("  tay")
    }


    internal fun computeStructArrayOffsetA(arrayDt: DataType, indexExpr: PtExpression, fieldOffset: Int = 0) {
        require(arrayDt.sub == BaseDataType.STRUCT_INSTANCE) { "expected struct instance array $arrayDt" }
        val structSize = try {
            (arrayDt.subType as? StStruct)?.size?.toInt() ?: program.memsizer.memorySize(arrayDt.elementType(), 1)
        } catch(_: Exception) {
            program.memsizer.memorySize(arrayDt.elementType(), 1)
        }
        asmgen.assignExpressionToRegister(indexExpr, RegisterOrPair.A)
        when(structSize) {
            1 -> {}
            2 -> asmgen.out("  asl  a")
            4 -> asmgen.out("  asl  a |  asl  a")
            8 -> asmgen.out("  asl  a |  asl  a |  asl  a")
            else -> asmgen.out("  ldy  #$structSize |  jsr  prog8_math.multiply_bytes")
        }
        if(fieldOffset!=0) {
            asmgen.out("  clc |  adc  #$fieldOffset")
        }
    }


    internal fun assignAddressOf(target: AsmAssignTarget, sourceName: String, msb: Boolean, arrayDt: DataType?, arrayIndexExpr: PtExpression?) {
        var actualSourceName = sourceName
        var actualMsb = msb
        if (arrayDt?.isSplitWordArray(program.memsizer) == true) {
            if (!sourceName.endsWith("_lsb") && !sourceName.endsWith("_msb")) {
                actualSourceName = if (msb) sourceName + "_msb" else sourceName + "_lsb"
            }
            actualMsb = false
        }

        if(arrayIndexExpr!=null) {
            if(arrayDt?.isPointer==true) {
                require(!actualMsb)
                return pointergen.assignAddressOfIndexedPointer(target, actualSourceName, arrayDt, arrayIndexExpr)
            }
            val constIndex = arrayIndexExpr.asConstInteger()
            if(constIndex!=null) {
                if (arrayDt!!.isUnsignedWord) {
                    // using a UWORD pointer with array indexing, always bytes
                    require(!actualMsb)
                    assignVariableToRegister(actualSourceName, RegisterOrPair.AY, false, arrayIndexExpr.definingISub(), arrayIndexExpr.position)
                    if(constIndex in 1..255)
                        asmgen.out("""
                            clc
                            adc  #$constIndex
                            bcc  +
                            iny
+""")
                    else if(constIndex>=256) {
                        asmgen.out("""
                            clc
                            adc  #<$constIndex
                            pha
                            tya
                            adc  #>$constIndex
                            tay
                            pla""")
                    }
                }
                else {
                    if(constIndex>0) {
                        val offset = if(arrayDt.isSplitWordArray(program.memsizer)) constIndex else program.memsizer.memorySize(arrayDt, constIndex)  // add arrayIndexExpr * elementsize  to the address of the array variable.
                        asmgen.out("  lda  #<($actualSourceName + $offset) |  ldy  #>($actualSourceName + $offset)")
                    } else {
                        asmgen.out("  lda  #<$actualSourceName |  ldy  #>$actualSourceName")
                    }
                }
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return
            } else {
                if (arrayDt!!.isUnsignedWord) {
                    // using a UWORD pointer with array indexing, always bytes
                    require(!actualMsb)
                    assignVariableToRegister(actualSourceName, RegisterOrPair.AY, false, arrayIndexExpr.definingISub(), arrayIndexExpr.position)
                    asmgen.saveRegisterStack(CpuRegister.A, false)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    if(arrayIndexExpr.type.isWord) {
                        assignExpressionToRegister(arrayIndexExpr, RegisterOrPair.AY, false)
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            pla
                            tay
                            pla
                            clc
                            adc  P8ZP_SCRATCH_W1
                            pha
                            tya
                            adc  P8ZP_SCRATCH_W1+1
                            tay
                            pla""")
                    }
                    else {
                        assignExpressionToVariable(arrayIndexExpr, "P8ZP_SCRATCH_REG", DataType.UBYTE)
                        asmgen.restoreRegisterStack(CpuRegister.Y, false)
                        asmgen.restoreRegisterStack(CpuRegister.A, false)
                        asmgen.out("""
                            clc
                            adc  P8ZP_SCRATCH_REG
                            bcc  +
                            iny                            
+""")
                    }
                }
                else {
                    assignExpressionToRegister(arrayIndexExpr, RegisterOrPair.A, false)
                    val subtype = arrayDt.sub!!
                    if(subtype.isByteOrBool) {
                        // elt size 1, we're good
                    } else if(subtype.isWord)  {
                        if(!arrayDt.isSplitWordArray(program.memsizer)) {
                            // elt size 2
                            asmgen.out("  asl  a")
                        }
                    } else if(subtype==BaseDataType.FLOAT) {
                        if(asmgen.options.compTarget.FLOAT_MEM_SIZE != 5u)
                            TODO("support float size other than 5 ${arrayIndexExpr.position}")
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_REG
                            asl  a
                            asl  a
                            clc
                            adc  P8ZP_SCRATCH_REG"""
                        )
                    } else if(subtype==BaseDataType.STRUCT_INSTANCE) {
                        computeStructArrayOffsetA(arrayDt, arrayIndexExpr, 0)
                        asmgen.out("  sta  P8ZP_SCRATCH_REG")
                        asmgen.out("  lda  #<$actualSourceName |  clc |  adc  P8ZP_SCRATCH_REG |  sta  P8ZP_SCRATCH_W1")
                        asmgen.out("  lda  #>$actualSourceName |  adc  #0 |  sta  P8ZP_SCRATCH_W1+1")
                        asmgen.out("  lda  P8ZP_SCRATCH_W1 |  ldy  P8ZP_SCRATCH_W1+1")
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return
                    } else throw AssemblyError("weird type $subtype")
                    asmgen.out("""
                        ldy  #>$actualSourceName
                        clc
                        adc  #<$actualSourceName
                        bcc  +
                        iny
+""")
                }
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return
            }
        }

        // address of a normal variable
        require(!actualMsb)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$actualSourceName
                        ldy  #>$actualSourceName
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1""")
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("can't store word into memory byte")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("  lda  #<$actualSourceName |  ldy  #>$actualSourceName")
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>$actualSourceName |  lda  #<$actualSourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>$actualSourceName |  lda  #<$actualSourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>$actualSourceName |  ldx  #<$actualSourceName")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("""
                            lda  #<$actualSourceName
                            ldy  #>$actualSourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            sty  cx16.${target.register.toString().lowercase()}+1""")
                    }
                    else -> throw AssemblyError("can only load address into 16 bit register")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignAddressOf(PtrTarget(target), actualSourceName)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableString(target: AsmAssignTarget, varName: String) {
        if (target.kind == TargetStorageKind.VARIABLE) {
            if (target.datatype.isUnsignedWord) {
                asmgen.out("""
                    lda  #<$varName
                    ldy  #>$varName
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1""")
            } else throw AssemblyError("assign string to incompatible variable type ${target.position}")
        }
        else throw AssemblyError("string-assign to weird target ${target.position}")
    }


    internal fun assignVariableLong(target: AsmAssignTarget, varName: String, sourceDt: DataType) {
        require(sourceDt.isByte || sourceDt.isWord || sourceDt.isLong) {
            "need byte/word/long as source value to assign to long variable $varName  ${target.position}"
        }
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(sourceDt) {
                    DataType.BYTE -> {
                        TODO("signed byte to long var ${target.position}")
                    }
                    DataType.UBYTE -> {
                        TODO("ubyte to long var ${target.position}")
                    }
                    DataType.WORD -> {
                        TODO("signed word to long var ${target.position}")
                    }
                    DataType.UWORD -> {
                        TODO("ubyte to long var ${target.position}")
                    }
                    DataType.LONG -> {
                        // Long to long variable copy - use loop
                        asmgen.out("""
                            ldy  #3
-                           lda  $varName,y
                            sta  ${target.asmVarname},y
                            dey
                            bpl  -""")
                    }
                    else -> throw AssemblyError("wrong dt ${target.position}")
                }
            }
            TargetStorageKind.ARRAY -> {
                require(sourceDt.isLong)
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignLongVar(IndexedPtrTarget(target), varName)
                    return
                }
                asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                asmgen.out("""
                    lda  $varName
                    sta  ${target.asmVarname},y
                    lda  $varName+1
                    sta  ${target.asmVarname}+1,y
                    lda  $varName+2
                    sta  ${target.asmVarname}+2,y
                    lda  $varName+3
                    sta  ${target.asmVarname}+3,y""")
            }
            TargetStorageKind.REGISTER -> {
                require(target.register in CombinedLongRegisters)
                val regstart = target.register!!.startregname()
                when(sourceDt) {
                    DataType.BYTE -> {
                        asmgen.out("  lda  $varName |  sta  cx16.$regstart")
                        asmgen.signExtendLongVariable("cx16.$regstart", sourceDt.base)
                    }
                    DataType.UBYTE -> {
                        asmgen.out("""
                            lda  $varName
                            sta  cx16.$regstart
                            lda  #0
                            sta  cx16.$regstart+1
                            sta  cx16.$regstart+2
                            sta  cx16.$regstart+3""")
                    }
                    DataType.WORD -> {
                        asmgen.out("""
                            lda  $varName
                            sta  cx16.$regstart
                            lda  $varName+1
                            sta  cx16.$regstart+1""")
                        asmgen.signExtendLongVariable("cx16.$regstart", sourceDt.base)
                    }
                    DataType.UWORD -> {
                        asmgen.out("""
                            lda  $varName
                            sta  cx16.$regstart
                            lda  $varName+1
                            sta  cx16.$regstart+1
                            lda  #0
                            sta  cx16.$regstart+2
                            sta  cx16.$regstart+3""")
                    }
                    DataType.LONG -> {
                        asmgen.out("""
                            lda  $varName
                            sta  cx16.$regstart
                            lda  $varName+1
                            sta  cx16.$regstart+1
                            lda  $varName+2
                            sta  cx16.$regstart+2
                            lda  $varName+3
                            sta  cx16.$regstart+3""")
                    }
                    else -> throw AssemblyError("wrong dt ${target.position}")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignLongVar(target.pointer!!, varName)
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableLongIndexed(target: AsmAssignTarget, arrayVarName: String, index: PtArrayIndexer) {
        asmgen.loadScaledArrayIndexIntoRegister(index, CpuRegister.Y)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $arrayVarName,y
                    sta  ${target.asmVarname}
                    lda  $arrayVarName+1,y
                    sta  ${target.asmVarname}+1
                    lda  $arrayVarName+2,y
                    sta  ${target.asmVarname}+2
                    lda  $arrayVarName+3,y
                    sta  ${target.asmVarname}+3""")
            }
            TargetStorageKind.REGISTER -> {
                require(target.register in CombinedLongRegisters)
                val regstart = target.register!!.startregname()
                asmgen.out("""
                    lda  $arrayVarName,y
                    sta  cx16.$regstart
                    lda  $arrayVarName+1,y
                    sta  cx16.$regstart+1
                    lda  $arrayVarName+2,y
                    sta  cx16.$regstart+2
                    lda  $arrayVarName+3,y
                    sta  cx16.$regstart+3""")
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignLongVar(IndexedPtrTarget(target), arrayVarName)
                    return
                }
                asmgen.saveRegisterStack(CpuRegister.Y, false)
                asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.X)
                asmgen.restoreRegisterStack(CpuRegister.Y, false)
                asmgen.out("""
                    lda  $arrayVarName,y
                    sta  ${target.asmVarname},x
                    lda  $arrayVarName+1,y
                    sta  ${target.asmVarname}+1,x
                    lda  $arrayVarName+2,y
                    sta  ${target.asmVarname}+2,x
                    lda  $arrayVarName+3,y
                    sta  ${target.asmVarname}+3,x""")
            }
            TargetStorageKind.POINTER -> pointergen.assignIndexedPointer(target, arrayVarName, index)
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableWord(target: AsmAssignTarget, varName: String, sourceDt: DataType) {
        if(sourceDt.isSignedByte) {
            // need to sign extend
            asmgen.out("  lda  $varName")
            asmgen.signExtendAYlsb(BaseDataType.BYTE)
            assignRegisterpairWord(target, RegisterOrPair.AY)
            return
        }
        require(sourceDt.isWord || sourceDt.isUnsignedByte || sourceDt.isBool || sourceDt.isPointer) {
            if(sourceDt.isString)
                "str source type for word variable should have been uword or ^^ubyte, a && is likely missing on the source variable $sourceDt ${target.position}"
            else
                "weird source dt for word variable: $sourceDt ${target.position}"
        }
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if(sourceDt.isUnsignedByte || sourceDt.isBool) {
                    asmgen.out("  lda  $varName |  sta  ${target.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("  stz  ${target.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${target.asmVarname}+1")
                }
                else
                    asmgen.out("""
                        lda  $varName
                        ldy  $varName+1
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1""")
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("assign word to memory ${target.memory} should have gotten a typecast")
            }
            TargetStorageKind.ARRAY -> {
                if(sourceDt.isUnsignedByte) TODO("assign byte to word array")
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignWordVar(IndexedPtrTarget(target), varName)
                    return
                }
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = program.memsizer.memorySize(target.datatype, target.constArrayIndexValue!!.toInt())
                    when {
                        target.datatype.isByte -> {
                            asmgen.out(" lda  $varName  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        target.datatype.isWord || target.datatype.isPointer -> {
                            if(target.array.splitWords)
                                asmgen.out("""
                                    lda  $varName
                                    sta  ${target.asmVarname}_lsb+${target.constArrayIndexValue}
                                    lda  $varName+1
                                    sta  ${target.asmVarname}_msb+${target.constArrayIndexValue}""")
                            else
                                asmgen.out("""
                                    lda  $varName
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  $varName+1
                                    sta  ${target.asmVarname}+$scaledIdx+1""")
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    when {
                        target.datatype.isByte -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.out(" lda  $varName |  sta  ${target.asmVarname},y")
                        }
                        target.datatype.isWord || target.datatype.isPointer -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            if(target.array.splitWords)
                                asmgen.out("""
                                    lda  $varName
                                    sta  ${target.asmVarname}_lsb,y
                                    lda  $varName+1
                                    sta  ${target.asmVarname}_msb,y""")
                            else
                                asmgen.out("""
                                    lda  $varName
                                    sta  ${target.asmVarname},y
                                    lda  $varName+1
                                    sta  ${target.asmVarname}+1,y""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                if(sourceDt.isUnsignedByte) {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $varName")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $varName")
                        RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $varName")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("  lda  $varName |  sta  cx16.${target.register.toString().lowercase()}")
                            if(asmgen.isTargetCpu(CpuType.CPU65C02))
                                asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1")
                            else
                                asmgen.out("  lda  #0 |  sta  cx16.${target.register.toString().lowercase()}+1")
                        }
                        in CombinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                        else -> throw AssemblyError("can't load word in a single 8-bit register")
                    }
                } else {
                    when(target.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  $varName+1 |  lda  $varName")
                        RegisterOrPair.AY -> asmgen.out("  ldy  $varName+1 |  lda  $varName")
                        RegisterOrPair.XY -> asmgen.out("  ldy  $varName+1 |  ldx  $varName")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("""
                                lda  $varName
                                sta  cx16.${target.register.toString().lowercase()}
                                lda  $varName+1
                                sta  cx16.${target.register.toString().lowercase()}+1""")
                        }
                        in CombinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                        else -> throw AssemblyError("can't load word in a single 8-bit register")
                    }
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignWordVar(PtrTarget(target), varName, sourceDt)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignFAC2float(target: AsmAssignTarget) {
        asmgen.out("  jsr  floats.MOVFA")
        if(target.register != RegisterOrPair.FAC1)
            assignFAC1float(target)
    }


    internal fun assignFAC1float(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    ldx  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.MOVMF""")
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignFAC1(IndexedPtrTarget(target))
                    return
                }
                asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.A)
                asmgen.out("""
                    ldy  #<${target.asmVarname} 
                    sty  P8ZP_SCRATCH_W1
                    ldy  #>${target.asmVarname}
                    sty  P8ZP_SCRATCH_W1+1
                    jsr  floats.set_array_float_from_fac1""")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                if(target.register==RegisterOrPair.FAC2)
                    asmgen.out("  jsr  floats.MOVAF")
                else if (target.register!! != RegisterOrPair.FAC1)
                    throw AssemblyError("can't assign Fac1 float to another register")
            }
            TargetStorageKind.POINTER -> pointergen.assignFAC1(PtrTarget(target))
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignFloatFromAY(target: AsmAssignTarget) {
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
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignFloatAY(IndexedPtrTarget(target))
                    return
                }
                asmgen.saveRegisterStack(CpuRegister.A, false)
                asmgen.saveRegisterStack(CpuRegister.Y, false)
                asmgen.assignExpressionToVariable(target.array.index, "P8ZP_SCRATCH_REG", DataType.UBYTE)
                asmgen.restoreRegisterStack(CpuRegister.Y, false)
                asmgen.restoreRegisterStack(CpuRegister.A, false)
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1
                    lda  P8ZP_SCRATCH_REG
                    jsr  floats.set_array_float""")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignFloatAY(PtrTarget(target))
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname}
                    ldy  #>${target.asmVarname}
                    jsr  floats.copy_float""")
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignFloatVar(IndexedPtrTarget(target), sourceName)
                    return
                }
                asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.A)
                asmgen.out("""
                    ldy  #<$sourceName
                    sty  P8ZP_SCRATCH_W1
                    ldy  #>$sourceName
                    sty  P8ZP_SCRATCH_W1+1
                    ldy  #<${target.asmVarname} 
                    sty  P8ZP_SCRATCH_W2
                    ldy  #>${target.asmVarname}
                    sty  P8ZP_SCRATCH_W2+1
                    jsr  floats.set_array_float""")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.MOVFM")
                    RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$sourceName  | ldy  #>$sourceName |  jsr  floats.CONUPK")
                    else -> throw AssemblyError("can only assign float to Fac1 or 2")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignFloatVar(PtrTarget(target), sourceName)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableByte(target: AsmAssignTarget, varName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $varName
                    sta  ${target.asmVarname}""")
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  lda  $varName")
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = program.memsizer.memorySize(target.datatype, target.constArrayIndexValue!!.toInt())
                    asmgen.out(" lda  $varName  | sta  ${target.asmVarname}+$scaledIdx")
                }
                else {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignByteVar(IndexedPtrTarget(target), varName, false, false)
                        return
                    }
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                    asmgen.out(" lda  $varName |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  $varName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $varName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $varName")
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $varName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $varName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $varName")
                    RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected typecasted byte to float")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("""
                            lda  $varName
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1""")
                    }
                    in CombinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                    else -> throw AssemblyError("weird register")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteVar(PtrTarget(target), varName, false, false)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when (wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  ${wordtarget.asmVarname}+1""")
            }
            TargetStorageKind.ARRAY -> {
                val deref = wordtarget.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignByteVar(IndexedPtrTarget(wordtarget), sourceName, extendToWord=true, signed=true)
                    return
                }
                if(wordtarget.array.splitWords) {
                    // signed byte, we must sign-extend
                    if (wordtarget.constArrayIndexValue!=null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!!
                        asmgen.out("  lda  $sourceName |  sta  ${wordtarget.asmVarname}_lsb+$scaledIdx")
                        asmgen.signExtendAYlsb(BaseDataType.BYTE)
                        asmgen.out("  sty  ${wordtarget.asmVarname}_msb+$scaledIdx")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, CpuRegister.X)
                        asmgen.out("  lda  $sourceName |  sta  ${wordtarget.asmVarname}_msb,x")
                        asmgen.signExtendAYlsb(BaseDataType.BYTE)
                        asmgen.out("  tya |  sta  ${wordtarget.asmVarname}_msb,x")
                    }
                }
                else {
                    if (wordtarget.constArrayIndexValue != null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                        asmgen.out("  lda  $sourceName")
                        asmgen.signExtendAYlsb(BaseDataType.BYTE)
                        asmgen.out("  sta  ${wordtarget.asmVarname}+$scaledIdx |  sty  ${wordtarget.asmVarname}+$scaledIdx+1")
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, CpuRegister.X)
                        asmgen.out("  lda  $sourceName")
                        asmgen.signExtendAYlsb(BaseDataType.BYTE)
                        asmgen.out("  sta  ${wordtarget.asmVarname},x |  inx |  tya |  sta  ${wordtarget.asmVarname},x")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tax
                        pla""")
                    RegisterOrPair.AY -> asmgen.out("""
                        lda  $sourceName
                        tax
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tay
                        txa""")
                    RegisterOrPair.XY -> asmgen.out("""
                        lda  $sourceName
                        tax 
                        ora  #$7f
                        bmi  +
                        lda  #0
+                       tay""")
                    in Cx16VirtualRegisters -> {
                        val regname = wordtarget.register.name.lowercase()
                        asmgen.out("""
                            lda  $sourceName
                            sta  cx16.$regname
                            ora  #$7f
                            bmi  +
                            lda  #0
+                           sta  cx16.$regname+1""")
                    }
                    else -> throw AssemblyError("only reg pairs allowed as word target ${wordtarget.register}")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteVar(PtrTarget(wordtarget), sourceName, extendToWord=true, signed=true)
            else -> throw AssemblyError("target type isn't word")
        }
    }


    internal fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when(wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  $sourceName |  sta  ${wordtarget.asmVarname}")
                if(asmgen.isTargetCpu(CpuType.CPU65C02))
                    asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                else
                    asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
            }
            TargetStorageKind.ARRAY -> {
                val deref = wordtarget.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignByteVar(IndexedPtrTarget(wordtarget), sourceName, extendToWord=true, signed=false)
                    return
                }
                if(wordtarget.array.splitWords) {
                    if (wordtarget.constArrayIndexValue!=null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!!
                        asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}_lsb+$scaledIdx")
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  stz  ${wordtarget.asmVarname}_msb+$scaledIdx")
                        else
                            asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}_msb+$scaledIdx")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, CpuRegister.Y)
                        asmgen.out("""
                            lda  $sourceName
                            sta  ${wordtarget.asmVarname}_lsb,y
                            lda  #0
                            sta  ${wordtarget.asmVarname}_msb,y""")
                    }
                } else {
                    if (wordtarget.constArrayIndexValue!=null) {
                        val scaledIdx = wordtarget.constArrayIndexValue!! * 2u
                        asmgen.out("  lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx")
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  stz  ${wordtarget.asmVarname}+$scaledIdx+1")
                        else
                            asmgen.out("  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                    }
                    else {
                        asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array, CpuRegister.Y)
                        asmgen.out("""
                            lda  $sourceName
                            sta  ${wordtarget.asmVarname},y
                            iny
                            lda  #0
                            sta  ${wordtarget.asmVarname},y""")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  $sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  $sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldx  $sourceName")
                    in Cx16VirtualRegisters -> {
                        val regname = wordtarget.register.name.lowercase()
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  stz  cx16.$regname+1")
                        else
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                    }
                    else -> throw AssemblyError("only reg pairs allowed as word target")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteVar(PtrTarget(wordtarget), sourceName, extendToWord=true, signed=false)
            else -> throw AssemblyError("target type isn't word")
        }
    }


    internal fun extendToMSBofVirtualReg(cpuRegister: CpuRegister, vreg: String, signed: Boolean) {
        if(signed) {
            when(cpuRegister) {
                CpuRegister.A -> { }
                CpuRegister.X -> asmgen.out("  txa")
                CpuRegister.Y -> asmgen.out("  tya")
                else -> throw AssemblyError("register not available on this target")
            }
            asmgen.out("""
                ora  #$7f
                bmi  +
                lda  #0
+               sta  $vreg+1""")
        } else {
            if(asmgen.isTargetCpu(CpuType.CPU65C02))
                asmgen.out("  stz  $vreg+1")
            else
                asmgen.out("  lda  #0 |  sta  $vreg+1")
        }
    }


    internal fun assignRegisterLong(target: AsmAssignTarget, pairedRegisters: RegisterOrPair) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if(pairedRegisters in CombinedLongRegisters) {
                    val startreg = pairedRegisters.startregname()
                    asmgen.out("""
                        ldy  #3
-                       lda  cx16.$startreg,y
                        sta  ${target.asmVarname},y
                        dey
                        bpl  -""")
                }
                else throw AssemblyError("only combined vreg allowed as long target ${target.position}")
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    // indexed pointer array via struct (e.g. foo.longs[i])
                    pointergen.assignLongReg(IndexedPtrTarget(target), pairedRegisters)
                    return
                }
                asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                val arrayVarName = asmgen.asmSymbolName(target.array.variable!!)
                val startreg = pairedRegisters.startregname()
                // Unrolled copy to preserve array index in Y
                asmgen.out("""
                    lda  cx16.$startreg
                    sta  $arrayVarName,y
                    lda  cx16.$startreg+1
                    sta  $arrayVarName+1,y
                    lda  cx16.$startreg+2
                    sta  $arrayVarName+2,y
                    lda  cx16.$startreg+3
                    sta  $arrayVarName+3,y""")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.REGISTER -> {
                val targetreg = target.register!!
                require(targetreg in CombinedLongRegisters && pairedRegisters in CombinedLongRegisters)
                if(targetreg!=pairedRegisters) {
                    val sourceStartReg = pairedRegisters.startregname()
                    val targetStartReg = targetreg.startregname()
                    // Loop-based copy between register pairs
                    asmgen.out("""
                        ldy  #3
-                       lda  cx16.$sourceStartReg,y
                        sta  cx16.$targetStartReg,y
                        dey
                        bpl  -""")
                }
            }
            TargetStorageKind.POINTER -> {
                val startreg = pairedRegisters.startregname()
                pointergen.assignLongVar(target.pointer!!, "cx16.$startreg")
            }
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister, signed: Boolean, extendSignedBits: Boolean) {
        val assignAsWord = target.datatype.isWord

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.lowercase()}  ${target.asmVarname}")
                if(assignAsWord && extendSignedBits) {
                    if(target.datatype.isSigned) {
                        if(register!=CpuRegister.A)
                            asmgen.out("  t${register.name.lowercase()}a")
                        asmgen.signExtendAYlsb(if(target.datatype.isSigned) BaseDataType.BYTE else BaseDataType.UBYTE)
                        asmgen.out("  sty  ${target.asmVarname}+1")
                    } else {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  stz  ${target.asmVarname}+1")
                        else
                            asmgen.out("  lda  #0 |  sta  ${target.asmVarname}+1")
                    }
                }
            }
            TargetStorageKind.MEMORY -> {
                when(register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                    else -> throw AssemblyError("register not available on this target")
                }
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(assignAsWord) {
                    when(register) {
                        CpuRegister.A -> {}
                        CpuRegister.X -> asmgen.out("  txa")
                        CpuRegister.Y -> asmgen.out("  tya")
                        else -> throw AssemblyError("register not available on this target")
                    }
                    if(extendSignedBits) {
                        asmgen.signExtendAYlsb(if(target.datatype.isSigned) BaseDataType.BYTE else BaseDataType.UBYTE)
                    } else {
                        asmgen.out("  ldy  #0")
                    }
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                } else {
                    assignRegisterByteToByteArray(target, register)
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> {
                            require(extendSignedBits) {
                                "no extend but byte target is registerpair"
                            }
                            if(signed)
                                asmgen.out("""
                ldy  #0
                cmp  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                ldx  #0
                cmp  #$80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                tax
                ldy  #0
                cpx  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tax |  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            val reg = "cx16.${target.register.toString().lowercase()}"
                            asmgen.out("  sta  $reg")
                            if(extendSignedBits)
                                extendToMSBofVirtualReg(CpuRegister.A, reg, signed)
                        }
                        in CombinedLongRegisters -> {
                            val reg = target.register.startregname()
                            asmgen.out("  sta  cx16.$reg")
                            if(extendSignedBits) {
                                asmgen.signExtendLongVariable("cx16.$reg", if(signed) BaseDataType.BYTE else BaseDataType.UBYTE)
                            }
                        }
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.AY -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                txa
                ldy  #0
                cmp  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  txa |  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                txa
                ldx  #0
                cmp  #$80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  txa |  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                ldy  #0
                cpx  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            val reg = "cx16.${target.register.toString().lowercase()}"
                            asmgen.out("  stx  $reg")
                            if(extendSignedBits)
                                extendToMSBofVirtualReg(CpuRegister.X, reg, signed)
                        }
                        in CombinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                        else -> throw AssemblyError("weird register")
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                tya
                ldy  #0
                cmp  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tya |  ldy  #0")
                        }
                        RegisterOrPair.AX -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                tya
                ldx  #0
                cmp  #$80
                bcc  +
                dex
+""")
                            else
                                asmgen.out("  tya |  ldx  #0")
                        }
                        RegisterOrPair.XY -> {
                            require(extendSignedBits)
                            if(signed)
                                asmgen.out("""
                tya
                tax
                ldy  #0
                cpx  #$80
                bcc  +
                dey
+""")
                            else
                                asmgen.out("  tya |  tax |  ldy  #0")
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> throw AssemblyError("expected type cast to float")
                        in Cx16VirtualRegisters -> {
                            val reg = "cx16.${target.register.toString().lowercase()}"
                            asmgen.out("  sty  $reg")
                            if(extendSignedBits)
                                extendToMSBofVirtualReg(CpuRegister.Y, reg, signed)
                        }
                        in CombinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                        else -> throw AssemblyError("weird register")
                    }
                    else -> throw AssemblyError("register not available on this target")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteReg(PtrTarget(target), register, signed, extendSignedBits)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignRegisterByteToByteArray(target: AsmAssignTarget, register: CpuRegister) {
        val deref = target.array!!.pointerderef
        if(deref!=null) {
            pointergen.assignByteReg(IndexedPtrTarget(target), register)
            return
        }
        if(target.array.splitWords)
            throw AssemblyError("cannot assign byte to split word array here ${target.position}")

        // assign regular array indexing
        if (target.constArrayIndexValue!=null) {
            when (register) {
                CpuRegister.A -> {}
                CpuRegister.X -> asmgen.out(" txa")
                CpuRegister.Y -> asmgen.out(" tya")
                else -> throw AssemblyError("register not available on this target")
            }
            asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
        }
        else {
            when (register) {
                CpuRegister.A -> {}
                CpuRegister.X -> asmgen.out(" txa")
                CpuRegister.Y -> asmgen.out(" tya")
                else -> throw AssemblyError("register not available on this target")
            }
            val indexVar = target.array.index as? PtIdentifier
            if(indexVar!=null) {
                asmgen.out("  ldy  ${asmgen.asmVariableName(indexVar)} |  sta  ${target.asmVarname},y")
            } else {
                require(target.array.index.type.isByte) {
                    "wot"
                }
                asmgen.saveRegisterStack(register, false)
                asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.Y)
                asmgen.out("  pla |  sta  ${target.asmVarname},y")
            }
        }
    }


    internal fun assignRegisterpairWord(target: AsmAssignTarget, regs: RegisterOrPair) {
        require(target.datatype.isNumeric || target.datatype.isPassByRef || target.datatype.isPointer) {
            "assign target must be word type ${target.position}"
        }
        if(target.datatype.isFloat)
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
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignWordReg(IndexedPtrTarget(target), regs)
                    return
                }
                if(target.array.splitWords) {
                    // assign to split lsb/msb word array
                    if (target.constArrayIndexValue!=null) {
                        val idx = target.constArrayIndexValue!!
                        when (regs) {
                            RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname}_lsb+$idx |  stx  ${target.asmVarname}_msb+$idx")
                            RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname}_lsb+$idx |  sty  ${target.asmVarname}_msb+$idx")
                            RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname}_lsb+$idx |  sty  ${target.asmVarname}_msb+$idx")
                            in Cx16VirtualRegisters -> {
                                val srcReg = asmgen.asmSymbolName(regs)
                                asmgen.out("""
                                    lda  $srcReg
                                    sta  ${target.asmVarname}_lsb+$idx
                                    lda  $srcReg+1
                                    sta  ${target.asmVarname}_msb+$idx""")
                            }
                            else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                        }
                    }
                    else {
                        if (regs !in Cx16VirtualRegisters) {
                            if (asmgen.isTargetCpu(CpuType.CPU65C02)) {
                                when (regs) {
                                    RegisterOrPair.AX -> asmgen.out("  pha |  phx")
                                    RegisterOrPair.AY -> asmgen.out("  pha |  phy")
                                    RegisterOrPair.XY -> asmgen.out("  phx |  phy")
                                    else -> throw AssemblyError("expected reg pair")
                                }
                            } else {
                                when (regs) {
                                    RegisterOrPair.AX -> asmgen.out("  pha |  txa |  pha")
                                    RegisterOrPair.AY -> asmgen.out("  pha |  tya |  pha")
                                    RegisterOrPair.XY -> asmgen.out("  txa |  pha |  tya |  pha")
                                    else -> throw AssemblyError("expected reg pair")
                                }
                            }
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.out("""
                                pla
                                sta  ${target.asmVarname}_msb,y
                                pla
                                sta  ${target.asmVarname}_lsb,y""")
                        } else {
                            val srcReg = asmgen.asmSymbolName(regs)
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.out("""
                                lda  $srcReg
                                sta  ${target.asmVarname}_lsb,y
                                lda  $srcReg+1
                                sta  ${target.asmVarname}_msb,y""")
                        }
                    }
                } else {
                    // assign to normal word array
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
                            if (asmgen.isTargetCpu(CpuType.CPU65C02)) {
                                when (regs) {
                                    RegisterOrPair.AX -> asmgen.out("  pha |  phx")
                                    RegisterOrPair.AY -> asmgen.out("  pha |  phy")
                                    RegisterOrPair.XY -> asmgen.out("  phx |  phy")
                                    else -> throw AssemblyError("expected reg pair")
                                }
                            } else {
                                when (regs) {
                                    RegisterOrPair.AX -> asmgen.out("  pha |  txa |  pha")
                                    RegisterOrPair.AY -> asmgen.out("  pha |  tya |  pha")
                                    RegisterOrPair.XY -> asmgen.out("  txa |  pha |  tya |  pha")
                                    else -> throw AssemblyError("expected reg pair")
                                }
                            }
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.out("""
                                iny
                                pla
                                sta  ${target.asmVarname},y
                                dey
                                pla
                                sta  ${target.asmVarname},y""")
                        } else {
                            val srcReg = asmgen.asmSymbolName(regs)
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                            asmgen.out("""
                                iny
                                lda  $srcReg+1
                                sta  ${target.asmVarname},y
                                dey
                                lda  $srcReg
                                sta  ${target.asmVarname},y""")
                        }
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
                            asmgen.out("""
                                sta  cx16.${target.register.toString().lowercase()}
                                stx  cx16.${target.register.toString().lowercase()}+1""")
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.AY -> when(target.register!!) {
                        RegisterOrPair.AY -> { }
                        RegisterOrPair.AX -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { asmgen.out("  tax") }
                        in Cx16VirtualRegisters -> {
                            asmgen.out("""
                                sta  cx16.${target.register.toString().lowercase()}
                                sty  cx16.${target.register.toString().lowercase()}+1""")
                        }
                        else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register")
                    }
                    RegisterOrPair.XY -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  txa") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { }
                        in Cx16VirtualRegisters -> {
                            asmgen.out("""
                                stx  cx16.${target.register.toString().lowercase()}
                                sty  cx16.${target.register.toString().lowercase()}+1""")
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
                    else -> throw AssemblyError("expected reg pair or cx16 virtual 16-bit register ${target.position}")
                }
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't store word into memory byte")
            TargetStorageKind.POINTER -> pointergen.assignWordReg(PtrTarget(target), regs)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignConstantLong(target: AsmAssignTarget, long: Int) {
        if(long==0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
            // optimize setting zero value for this processor
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        stz  ${target.asmVarname}
                        stz  ${target.asmVarname}+1
                        stz  ${target.asmVarname}+2
                        stz  ${target.asmVarname}+3""")
                }
                TargetStorageKind.ARRAY -> {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignLong(IndexedPtrTarget(target), 0)
                        return
                    }
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                    asmgen.out("""
                        lda  #0
                        sta  ${target.asmVarname},y
                        sta  ${target.asmVarname}+1,y
                        sta  ${target.asmVarname}+2,y
                        sta  ${target.asmVarname}+3,y""")
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                TargetStorageKind.REGISTER -> {
                    val startreg = target.register!!.startregname()
                    asmgen.out("""
                        stz  cx16.$startreg
                        stz  cx16.$startreg+1
                        stz  cx16.$startreg+2
                        stz  cx16.$startreg+3""")
                }
                TargetStorageKind.POINTER -> pointergen.assignLong(target.pointer!!, 0)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }
            return
        }

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                fun store(hexbyte: String, offset: Int) {
                    if(asmgen.isTargetCpu(CpuType.CPU65C02) && hexbyte=="00") {
                        asmgen.out("  stz  ${target.asmVarname}+$offset")
                    } else {
                        asmgen.out("  lda  #$$hexbyte |  sta  ${target.asmVarname}+$offset")
                    }
                }
                val hex = long.toLongHex()
                store(hex.substring(6,8), 0)
                store(hex.substring(4,6), 1)
                store(hex.substring(2,4), 2)
                store(hex.substring(0,2), 3)
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignLong(IndexedPtrTarget(target), long)
                    return
                }
                asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                val hex = long.toLongHex()
                asmgen.out("""
                    lda  #$${hex.substring(6,8)}
                    sta  ${target.asmVarname},y
                    lda  #$${hex.substring(4, 6)}
                    sta  ${target.asmVarname}+1,y
                    lda  #$${hex.substring(2, 4)}
                    sta  ${target.asmVarname}+2,y
                    lda  #$${hex.take(2)}
                    sta  ${target.asmVarname}+3,y""")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.REGISTER -> {
                require(target.register in CombinedLongRegisters)
                val regstart = target.register!!.startregname()
                val hex = long.toLongHex()
                asmgen.out("""
                    lda  #$${hex.substring(6,8)}
                    sta  cx16.$regstart
                    lda  #$${hex.substring(4,6)}
                    sta  cx16.$regstart+1
                    lda  #$${hex.substring(2,4)}
                    sta  cx16.$regstart+2
                    lda  #$${hex.take(2)}
                    sta  cx16.$regstart+3""")
            }
            TargetStorageKind.POINTER -> pointergen.assignLong(target.pointer!!, long)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignConstantWord(target: AsmAssignTarget, word: Int) {
        if(word==0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
            // optimize setting zero value for this processor
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} |  stz  ${target.asmVarname}+1")
                }
                TargetStorageKind.MEMORY -> {
                    throw AssemblyError("memory is bytes not words")
                }
                TargetStorageKind.ARRAY -> {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignWord(IndexedPtrTarget(target), 0)
                        return
                    }
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                    if(target.array.splitWords)
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}_lsb,y
                            sta  ${target.asmVarname}_msb,y""")
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname},y
                            sta  ${target.asmVarname}+1,y""")
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
                TargetStorageKind.POINTER -> pointergen.assignWord(PtrTarget(target), 0)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if (asmgen.isTargetCpu(CpuType.CPU65C02) && word ushr 8 == 0) {
                    // constant fits in a single byte (high byte is zero): zero the
                    // high byte with stz instead of loading it into Y and storing it
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmVarname}
                    stz  ${target.asmVarname}+1""")
                } else if (word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmVarname}
                    sta  ${target.asmVarname}+1""")
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1""")
                }
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("assign word to memory ${target.memory} should have gotten a typecast")
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignWord(IndexedPtrTarget(target), word)
                    return
                }
                asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                if(target.array.splitWords)
                    asmgen.out("""
                        lda  #<${word.toHex()}
                        sta  ${target.asmVarname}_lsb,y
                        lda  #>${word.toHex()}
                        sta  ${target.asmVarname}_msb,y""")
                else
                    asmgen.out("""
                        lda  #<${word.toHex()}
                        sta  ${target.asmVarname},y
                        lda  #>${word.toHex()}
                        sta  ${target.asmVarname}+1,y""")
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>${word.toHex()} |  lda  #<${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>${word.toHex()} |  ldx  #<${word.toHex()}")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("""
                            lda  #<${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #>${word.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}+1""")
                    }
                    else -> throw AssemblyError("invalid register for word value")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignWord(PtrTarget(target), word)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignConstantByte(target: AsmAssignTarget, byte: Int) {
        if(byte==0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
            // optimize setting zero value for this cpu
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  stz  ${target.asmVarname} ")
                }
                TargetStorageKind.MEMORY -> {
                    val addr = target.memory!!.address
                    if(assignmentAsmGen.isConstReloadableAddress(addr)) {
                        // constant is reloaded after the pointer setup, so no leading lda is needed
                        storeRegisterAInMemoryAddress(target.memory, 0)
                    } else {
                        asmgen.out("  lda  #0")
                        storeRegisterAInMemoryAddress(target.memory)
                    }
                }
                TargetStorageKind.ARRAY -> {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignByte(IndexedPtrTarget(target), byte)
                        return
                    }
                    if(target.array.splitWords)
                        throw AssemblyError("cannot assign byte to split word array here ${target.position}")
                    if (target.constArrayIndexValue!=null) {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  stz  ${target.asmVarname}+$indexValue")
                    }
                    else {
                        asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.X)
                        asmgen.out("  stz  ${target.asmVarname},x")
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
                        asmgen.out("  stz  cx16.${target.register.toString().lowercase()}")
                        if(target.datatype.isWord)
                            asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1")
                    }
                    else -> throw AssemblyError("weird register")
                }
                TargetStorageKind.POINTER -> pointergen.assignByte(PtrTarget(target), 0)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }

            return
        }


        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                val addr = target.memory!!.address
                if(assignmentAsmGen.isConstReloadableAddress(addr)) {
                    storeRegisterAInMemoryAddress(target.memory, byte)
                } else {
                    asmgen.out("  lda  #${byte.toHex()}")
                    storeRegisterAInMemoryAddress(target.memory)
                }
            }
            TargetStorageKind.ARRAY -> {
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignByte(IndexedPtrTarget(target), byte)
                    return
                }
                require(!target.array.splitWords)
                if (target.constArrayIndexValue != null) {
                    val indexValue = target.constArrayIndexValue!!
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                } else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname},y")
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
                    asmgen.out("  lda  #${byte.toHex()} |  sta  cx16.${target.register.toString().lowercase()}")
                    if(target.datatype.isWord) {
                        if (asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  stz  cx16.${target.register.toString().lowercase()}+1\n")
                        else
                            asmgen.out("  lda  #0 |  sta  cx16.${target.register.toString().lowercase()}+1\n")
                    }
                }
                else -> throw AssemblyError("weird register")
            }
            TargetStorageKind.POINTER -> pointergen.assignByte(PtrTarget(target), byte)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }


    internal fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("""
                            stz  ${target.asmVarname}
                            stz  ${target.asmVarname}+1
                            stz  ${target.asmVarname}+2
                            stz  ${target.asmVarname}+3
                            stz  ${target.asmVarname}+4""")
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}
                            sta  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+4""")
                }
                TargetStorageKind.ARRAY -> {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignFloat(IndexedPtrTarget(target), float)
                        return
                    }
                    asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.A)
                    asmgen.out("""
                        ldy  #<${target.asmVarname}
                        sty  P8ZP_SCRATCH_W1
                        ldy  #>${target.asmVarname}
                        sty  P8ZP_SCRATCH_W1+1
                        jsr  floats.set_0_array_float""")
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
                TargetStorageKind.POINTER -> pointergen.assignFloat(PtrTarget(target), 0.0)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }
        } else {
            // non-zero value
            val constFloat = allocator.getFloatAsmConst(float)
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  #<$constFloat
                        ldy  #>$constFloat
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}
                        jsr  floats.copy_float""")
                }
                TargetStorageKind.ARRAY -> {
                    val deref = target.array!!.pointerderef
                    if(deref!=null) {
                        pointergen.assignFloat(IndexedPtrTarget(target), float)
                        return
                    }
                    asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.A)
                    asmgen.out("""
                        ldy  #<${constFloat}
                        sty  P8ZP_SCRATCH_W1
                        ldy  #>${constFloat}
                        sty  P8ZP_SCRATCH_W1+1
                        ldy  #<${target.asmVarname}
                        sty  P8ZP_SCRATCH_W2
                        ldy  #>${target.asmVarname}
                        sty  P8ZP_SCRATCH_W2+1
                        jsr  floats.set_array_float""")
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> {
                    val floatConst = allocator.getFloatAsmConst(float)
                    when(target.register!!) {
                        RegisterOrPair.FAC1 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.MOVFM")
                        RegisterOrPair.FAC2 -> asmgen.out("  lda  #<$floatConst  | ldy  #>$floatConst |  jsr  floats.CONUPK")
                        else -> throw AssemblyError("can only assign float to Fac1 or 2")
                    }
                }
                TargetStorageKind.POINTER -> pointergen.assignFloat(PtrTarget(target), float)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }
        }
    }


    internal fun assignMemoryByte(target: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmVarname}""")
                }
                TargetStorageKind.MEMORY -> {
                    asmgen.out("  lda  ${address.toHex()}")
                    storeRegisterAInMemoryAddress(target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.out("  lda  ${address.toHex()}")
                    assignRegisterByte(target, CpuRegister.A, false, true)
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
                        asmgen.out("""
                            lda  ${address.toHex()}
                            sta  cx16.${target.register.toString().lowercase()}
                            lda  #0
                            sta  cx16.${target.register.toString().lowercase()}+1""")
                    }
                    in CombinedLongRegisters -> TODO("assign memory byte into long ${target.position}")
                    else -> throw AssemblyError("weird register")
                }
                TargetStorageKind.POINTER -> pointergen.assignByteMemory(PtrTarget(target), address)
                TargetStorageKind.VOID -> { /* do nothing */ }
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
                    asmgen.loadByteFromPointerIntoA(identifier)
                    assignRegisterByte(target, CpuRegister.A, false, true)
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
                            asmgen.out("""
                                sta  cx16.${target.register.toString().lowercase()}
                                lda  #0
                                sta  cx16.${target.register.toString().lowercase()}+1""")
                        }
                        in CombinedLongRegisters -> {
                            val startreg = target.register.startregname()
                            asmgen.out("""
                                sta  cx16.$startreg
                                lda  #0
                                sta  cx16.$startreg+1
                                sta  cx16.$startreg+2
                                sta  cx16.$startreg+3""")
                        }
                        else -> throw AssemblyError("weird register")
                    }
                }
                TargetStorageKind.POINTER -> pointergen.assignByteMemory(PtrTarget(target), identifier)
                TargetStorageKind.VOID -> { /* do nothing */ }
            }
        }
    }


    internal fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
        if (address != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("  lda  ${address.toHex()} |  sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    assignRegisterpairWord(wordtarget, RegisterOrPair.AY)
                }
                TargetStorageKind.REGISTER -> when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #0 |  lda  ${address.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #0 |  ldy  ${address.toHex()}")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("  lda  ${address.toHex()} |  sta  cx16.${wordtarget.register.toString().lowercase()}")
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  stz  cx16.${wordtarget.register.toString().lowercase()}+1")
                        else
                            asmgen.out("  lda  #0 |  sta  cx16.${wordtarget.register.toString().lowercase()}+1")
                    }
                    else -> throw AssemblyError("word regs can only be pair")
                }
                TargetStorageKind.POINTER -> TODO("assign membyte into word pointer target ${wordtarget.position}")
                else -> throw AssemblyError("other types aren't word")
            }
        } else if (identifier != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${wordtarget.asmVarname}")
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("  stz  ${wordtarget.asmVarname}+1")
                    else
                        asmgen.out("  lda  #0 |  sta  ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out("  ldy  #0")
                    assignRegisterpairWord(wordtarget, RegisterOrPair.AY)
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(wordtarget.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("  sta  cx16.${wordtarget.register.toString().lowercase()}")
                            if(asmgen.isTargetCpu(CpuType.CPU65C02))
                                asmgen.out("  stz  cx16.${wordtarget.register.toString().lowercase()}+1")
                            else
                                asmgen.out("  lda  #0 |  sta  cx16.${wordtarget.register.toString().lowercase()}+1")
                        }
                        else -> throw AssemblyError("word regs can only be pair")
                    }
                }
                TargetStorageKind.POINTER -> TODO("assign membyte into word pointer ${wordtarget.position}")
                else -> throw AssemblyError("other types aren't word")
            }
        }
    }


    internal fun storeRegisterAInMemoryAddress(memoryAddress: PtMemoryByte, byteValue: Int? = null) {
        val addressExpr = memoryAddress.address
        val addressLv = addressExpr as? PtNumber
        val addressOf = addressExpr as? PtAddressOf

        fun storeViaExprEval() {
            when(addressExpr) {
                is PtNumber, is PtIdentifier -> assignmentAsmGen.storeByteInAToAddressExpression(addressExpr, false, byteValue)
                else -> assignmentAsmGen.storeByteInAToAddressExpression(addressExpr, true, byteValue)
            }
        }

        when {
            addressLv != null -> {
                asmgen.out("  sta  ${addressLv.number.toHex()}")
            }
            addressOf != null -> {
                if(addressOf.isFromArrayElement) {
                    val arrayId = addressOf.identifier
                    val arrayDt = arrayId?.type
                    if(arrayDt?.sub == BaseDataType.STRUCT_INSTANCE) {
                        val base = asmgen.asmSymbolName(arrayId)
                        if(byteValue==null) asmgen.out("  pha")
                        computeStructArrayOffsetY(arrayDt, addressOf.arrayIndexExpr!!, 0)
                        if(byteValue==null) asmgen.out("  pla") else asmgen.out("  lda  #${byteValue.toHex()}")
                        asmgen.out("  sta  $base,y")
                    } else {
                        TODO("address-of array element $addressOf")
                    }
                } else if(addressOf.dereference!=null) {
                    throw AssemblyError("write &dereference, makes no sense at ${addressOf.position}")
                } else {
                    var symbolName = asmgen.asmSymbolName(addressOf.identifier!!)
                    if(addressOf.identifier!!.type.isSplitWordArray(program.memsizer)) {
                        symbolName = if(addressOf.isMsbForSplitArray) symbolName+"_msb" else symbolName+"_lsb"
                    }
                    asmgen.out("  sta  $symbolName")
                }
            }
            addressExpr is PtIdentifier -> {
                asmgen.storeAIntoPointerVar(addressExpr)
            }
            addressExpr is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addressExpr)
                if(result!=null) {
                    val addrOf = result.first as? PtAddressOf
                    val arrayId = addrOf?.identifier
                    val arrayDt = arrayId?.type
                    if(addrOf!=null && addrOf.isFromArrayElement && arrayDt?.sub == BaseDataType.STRUCT_INSTANCE && result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        val base = asmgen.asmSymbolName(arrayId)
                        if(byteValue==null) asmgen.out("  pha")
                        computeStructArrayOffsetY(arrayDt, addrOf.arrayIndexExpr!!, offset)
                        if(byteValue==null) asmgen.out("  pla") else asmgen.out("  lda  #${byteValue.toHex()}")
                        asmgen.out("  sta  $base,y")
                        return
                    }
                    val addressOfIdentifier = addrOf?.identifier
                    if(addressOfIdentifier!=null) {
                        var varname = asmgen.asmVariableName(addressOfIdentifier)
                        if(addressOfIdentifier.type.isSplitWordArray(program.memsizer)) {
                            varname = if(addrOf.isMsbForSplitArray) varname+"_msb" else varname+"_lsb"
                        }
                        if(result.second is PtNumber) {
                            val offset = (result.second as PtNumber).number.toInt()
                            asmgen.out("  sta  $varname+$offset")
                            return
                        } else if (result.second is PtIdentifier) {
                            val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                            asmgen.out("  ldx  $offsetname |  sta  $varname,x")
                            return
                        }
                    }
                }

                if(byteValue!=null) {
                    // Constant byte value with reloadable address: need to load constant
                    // after setting up pointer, not before. Handle the common
                    // pointer+constant case efficiently.
                    val ptrVar = result?.first as? PtIdentifier
                    val offNum = result?.second as? PtNumber
                    if(ptrVar!=null && offNum!=null) {
                        val offset = offNum.number.toInt()
                        if(asmgen.isZpVar(ptrVar)) {
                            asmgen.out("  ldy  #$offset |  lda  #${byteValue.toHex()} |  sta  (${asmgen.asmSymbolName(ptrVar)}),y")
                            return
                        } else {
                            asmgen.out("  lda  ${asmgen.asmSymbolName(ptrVar)} |  sta  P8ZP_SCRATCH_W2 |  lda  ${asmgen.asmSymbolName(ptrVar)}+1 |  sta  P8ZP_SCRATCH_W2+1 |  ldy  #$offset |  lda  #${byteValue.toHex()} |  sta  (P8ZP_SCRATCH_W2),y")
                            return
                        }
                    }
                    assignmentAsmGen.storeByteInAToAddressExpression(addressExpr, true, byteValue)
                    return
                }
                if(!asmgen.tryOptimizedPointerAccessWithA(addressExpr, true))
                    storeViaExprEval()
            }
            else -> storeViaExprEval()
        }
    }


    internal fun assignExpressionToRegister(expr: PtExpression, register: RegisterOrPair, signed: Boolean) {
        val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
        val tgt = AsmAssignTarget.fromRegisters(register, signed, expr.position, null, asmgen)
        val assign = AsmAssignment(src, listOf(tgt), program.memsizer, expr.position)
        assignmentAsmGen.translateNormalAssignment(assign, expr.definingISub())
    }


    internal fun assignExpressionToVariable(expr: PtExpression, asmVarName: String, dt: DataType) {
        if(expr.type.isFloat && !dt.isFloat) {
            throw AssemblyError("can't directly assign a FLOAT expression to an integer variable $expr")
        } else {
            val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
            val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, dt, expr.definingISub(), expr.position, variableAsmName = asmVarName)
            val assign = AsmAssignment(src, listOf(tgt), program.memsizer, expr.position)
            assignmentAsmGen.translateNormalAssignment(assign, expr.definingISub())
        }
    }


    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean, scope: IPtSubroutine?, pos: Position) {
        val tgt = AsmAssignTarget.fromRegisters(register, signed, pos, null, asmgen)
        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, tgt.datatype, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, listOf(tgt), program.memsizer, pos)
        assignmentAsmGen.translateNormalAssignment(assign, scope)
    }


    internal fun inplaceInvert(assign: AsmAssignment, scope: IPtSubroutine?) {
        val target = assign.target
        val targetDt = assign.target.datatype
        when {
            targetDt.isUnsignedByte || targetDt.isBool -> {
                val eorValue = if(assign.target.datatype.isBool) 1 else 255
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #$eorValue
                            sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.MEMORY -> {
                        val memory = target.memory!!
                        when (memory.address) {
                            is PtNumber -> {
                                val addr = (memory.address as PtNumber).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    eor  #$eorValue
                                    sta  $addr""")
                            }
                            is PtIdentifier -> {
                                asmgen.loadByteFromPointerIntoA(memory.address as PtIdentifier)
                                asmgen.out("  eor  #$eorValue")
                                asmgen.storeAIntoPointerVar(memory.address as PtIdentifier)
                            }
                            else -> {
                                asmgen.assignExpressionToVariable(memory.address, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                                    asmgen.out("""
                                        lda  (P8ZP_SCRATCH_W2)
                                        eor  #$eorValue""")
                                } else {
                                    asmgen.out("""
                                        ldy  #0
                                        lda  (P8ZP_SCRATCH_W2),y
                                        eor  #$eorValue""")
                                }
                                asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2", false)
                            }
                        }
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out("  eor  #$eorValue")
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #$eorValue |  tax")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #$eorValue |  tay")
                            else -> throw AssemblyError("invalid reg dt for byte invert")
                        }
                    }
                    TargetStorageKind.ARRAY -> {
                        val invertOperator = if(assign.target.datatype.isBool) "not" else "~"
                        assignmentAsmGen.assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign(invertOperator, assign), scope)
                    }
                    TargetStorageKind.POINTER -> pointergen.inplaceByteInvert(PtrTarget(target))
                    TargetStorageKind.VOID -> { /* do nothing */ }
                }
            }
            targetDt.isUnsignedWord -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            eor  #255
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> asmgen.out("  pha |  txa |  eor  #255 |  tax |  pla |  eor  #255")
                            RegisterOrPair.AY -> asmgen.out("  pha |  tya |  eor  #255 |  tay |  pla |  eor  #255")
                            RegisterOrPair.XY -> asmgen.out("  txa |  eor  #255 |  tax |  tya |  eor  #255 |  tay")
                            in Cx16VirtualRegisters -> throw AssemblyError("cx16 virtual regs should be variables, not real registers")
                            in CombinedLongRegisters -> TODO("in place negate long invert ${target.position}")
                            else -> throw AssemblyError("invalid reg dt for word invert")
                        }
                    }
                    TargetStorageKind.ARRAY -> assignmentAsmGen.assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("~", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceWordInvert(PtrTarget(target))
                    else -> throw AssemblyError("weird target")
                }
            }
            targetDt.isLong -> {
                when(target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            eor  #255
                            sta  ${target.asmVarname}+1
                            lda  ${target.asmVarname}+2
                            eor  #255
                            sta  ${target.asmVarname}+2
                            lda  ${target.asmVarname}+3
                            eor  #255
                            sta  ${target.asmVarname}+3""")
                    }
                    TargetStorageKind.POINTER -> pointergen.inplaceLongInvert(PtrTarget(target))
                    else -> TODO("LONG INVERT ${target.kind}  ${target.position}")
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }


    internal fun inplaceNegate(assign: AsmAssignment, ignoreDatatype: Boolean, scope: IPtSubroutine?) {
        val target = assign.target
        val datatype = if(ignoreDatatype) {
            when {
                target.datatype.isByte -> DataType.BYTE
                target.datatype.isWord -> DataType.WORD
                else -> target.datatype
            }
        } else target.datatype
        when {
            datatype.isSignedByte -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("""
                                lda  ${target.asmVarname}
                                eor  #255
                                ina
                                sta  ${target.asmVarname}""")
                        else
                            asmgen.out("""
                                lda  #0
                                sec
                                sbc  ${target.asmVarname}
                                sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65C02))
                                    asmgen.out("  eor  #255 |  ina")
                                else
                                    asmgen.out("  eor  #255 |  clc |  adc  #1")
                            }
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #255 |  tax |  inx")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #255 |  tay |  iny")
                            else -> throw AssemblyError("invalid reg dt for byte negate")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't negate that")
                    TargetStorageKind.ARRAY -> assignmentAsmGen.assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceByteNegate(PtrTarget(target), scope)
                    TargetStorageKind.VOID -> { /* do nothing */ }
                }
            }
            datatype.isSignedWord -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  ${target.asmVarname}
                            sta  ${target.asmVarname}
                            lda  #0
                            sbc  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) { //P8ZP_SCRATCH_REG
                            RegisterOrPair.AX -> {
                                asmgen.out("""
                                    sec
                                    eor  #255
                                    adc  #0
                                    pha
                                    txa
                                    eor  #255
                                    adc  #0
                                    tax
                                    pla""")
                            }
                            RegisterOrPair.AY -> {
                                asmgen.out("""
                                    sec
                                    eor  #255
                                    adc  #0
                                    tax
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay
                                    txa""")
                            }
                            RegisterOrPair.XY -> {
                                asmgen.out("""
                                    sec
                                    txa
                                    eor  #255
                                    adc  #0
                                    tax
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay""")
                            }
                            in Cx16VirtualRegisters -> throw AssemblyError("cx16 virtual regs should be variables, not real registers")
                            in CombinedLongRegisters -> TODO("in place negate long reg ${target.position}")
                            else -> throw AssemblyError("invalid reg dt for word neg")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't negate that")
                    TargetStorageKind.ARRAY -> assignmentAsmGen.assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceWordNegate(PtrTarget(target), scope)
                    TargetStorageKind.VOID -> { /* do nothing */ }
                }
            }
            datatype.isLong -> {
                when(target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  ${target.asmVarname}
                            sta  ${target.asmVarname}
                            lda  #0
                            sbc  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+1
                            lda  #0
                            sbc  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+2
                            lda  #0
                            sbc  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+3""")
                    }
                    TargetStorageKind.ARRAY -> TODO(" - long array ${target.position}")
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                    TargetStorageKind.REGISTER -> {
                        val regstart = target.register!!.startregname()
                        asmgen.out("""
                            lda  #<cx16.$regstart
                            ldy  #>cx16.$regstart
                            jsr  prog8_lib.long_negate_inplace""")
                    }
                    TargetStorageKind.POINTER -> pointergen.inplaceLongNegate(PtrTarget(target), scope)
                    TargetStorageKind.VOID -> { /* do nothing */ }
                }
            }
            datatype.isFloat -> {
                when (target.kind) {
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.FAC1 -> asmgen.out("  jsr  floats.NEGOP")
                            RegisterOrPair.FAC2 -> asmgen.out("  jsr  floats.MOVFA |  jsr floats.NEGOP |  jsr  floats.MOVEF")
                            else -> throw AssemblyError("invalid float register")
                        }
                    }
                    TargetStorageKind.VARIABLE -> {
                        // simply flip the sign bit in the float
                        asmgen.out("""
                            lda  ${target.asmVarname}+1
                            eor  #$80
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.ARRAY -> assignmentAsmGen.assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceFloatNegate(PtrTarget(target), scope)
                    else -> throw AssemblyError("weird target for in-place float negation")
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }


    internal fun makePrefixedExprFromArrayExprAssign(operator: String, assign: AsmAssignment): AsmAssignment {
        val prefix = PtPrefix(operator, assign.source.datatype, assign.source.array!!.position)
        prefix.add(assign.source.array)
        prefix.parent = assign.target.origAstTarget ?: program
        val prefixSrc = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, assign.source.datatype, expression=prefix)
        return AsmAssignment(prefixSrc, assign.targets, assign.memsizer, assign.position)
    }

}
