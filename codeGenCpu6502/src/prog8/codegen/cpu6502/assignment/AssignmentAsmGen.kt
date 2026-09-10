package prog8.codegen.cpu6502.assignment

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator


internal class AssignmentAsmGen(
    private val program: PtProgram,
    private val asmgen: AsmGen6502Internal,
    private val pointergen: PointerAssignmentsGen,
    anyExprGen: AnyExprAsmGen,
    allocator: VariableAllocator
) {
    lateinit var augmentableAsmGen: AugmentableAssignmentAsmGen
    private val primitiveGen = PrimitiveAssignmentsGen(program, asmgen, pointergen, allocator)
    private val binaryOpGen = BinaryOpAssignmentsGen(program, asmgen, pointergen, anyExprGen)
    private val typeCastGen = TypeCastAssignmentsGen(program, asmgen, pointergen)

    init {
        primitiveGen.assignmentAsmGen = this
        binaryOpGen.assignmentAsmGen = this
        typeCastGen.assignmentAsmGen = this
    }

    internal fun isRightTrivial(expr: PtExpression): Boolean =
        expr is PtNumber || expr is PtBool || expr is PtIdentifier

    fun translate(assignment: PtAssignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment.target, assignment.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)
        val pos = if(assignment.position !== Position.DUMMY) assignment.position else if(assignment.target.position !== Position.DUMMY) assignment.target.position else assignment.value.position
        val assign = AsmAssignment(source, listOf(target), program.memsizer, pos)
        translateNormalAssignment(assign, assignment.definingISub())
    }

    fun translate(augmentedAssign: PtAugmentedAssign) {
        val target = AsmAssignTarget.fromAstAssignment(augmentedAssign.target, augmentedAssign.definingISub(), asmgen)
        val source = AsmAssignSource.fromAstSource(augmentedAssign.value, program, asmgen).adjustSignedUnsigned(target)
        val pos = if(augmentedAssign.position !== Position.DUMMY) augmentedAssign.position else if(augmentedAssign.target.position !== Position.DUMMY) augmentedAssign.target.position else augmentedAssign.value.position
        val assign = AsmAugmentedAssignment(source, augmentedAssign.operator, target, program.memsizer, pos)
        augmentableAsmGen.translate(assign, augmentedAssign.definingISub())
    }

    fun translateMultiAssign(assignment: PtAssignment) {
        val values = assignment.value as? PtFunctionCall
            ?: throw AssemblyError("only function calls can return multiple values in a multi-assign")

        val thing = asmgen.symbolTable.lookup(values.name)
        if(thing?.type== StNodeType.EXTSUB) {
            val extsub = thing as StExtSub
            require(extsub.returns.size>=2) {
                "extsub ${extsub.name} must return at least 2 values but got ${extsub.returns.size}  ${assignment.position}"
            }

            asmgen.translate(values)

            val assignmentTargets = assignment.children.dropLast(1)
            if(extsub.returns.size==assignmentTargets.size) {
                // Filter out void targets first, then separate by result type
                val allResults = extsub.returns.zip(assignmentTargets)
                val floatResults = allResults.filter { it.first.type.isFloat && !(it.second as PtAssignTarget).void }
                val nonVoidResults = allResults.filter { !(it.second as PtAssignTarget).void && !it.first.type.isFloat }
                val (statusFlagResults, registersResults) = nonVoidResults.partition { it.first.register.statusflag!=null }
                val saveFlags = statusFlagResults.size > 1

                // Check if we need to save A register before extracting status flags
                // (flag extraction functions like assignCarryFlagResult overwrite A)
                val hasByteInA = registersResults.any { (ret, _) ->
                    ret.type.isByteOrBool && ret.register.registerOrPair == RegisterOrPair.A
                }
                if(hasByteInA && statusFlagResults.isNotEmpty()) {
                    asmgen.out("  pha")  // Save A (contains return value) before flag extraction
                }

                // Save status flags first (before float MOVMF or other ops clobber them)
                if(statusFlagResults.isNotEmpty()) {
                    if(saveFlags) asmgen.out("  php")
                    statusFlagResults.forEach { (returns, target) ->
                        when(returns.register.statusflag) {
                            Statusflag.Pc -> assignCarryFlagResult(target as PtAssignTarget)
                            Statusflag.Pz -> assignZeroFlagResult(target as PtAssignTarget, saveFlags)
                            Statusflag.Pn -> assignNegativeFlagResult(target as PtAssignTarget, saveFlags)
                            Statusflag.Pv -> assignOverflowFlagResult(target as PtAssignTarget)
                            else -> throw AssemblyError("unknown status flag")
                        }
                    }
                    if(saveFlags) asmgen.out("  plp")
                }

                // Save float results (these use ldx/ldy/jsr which clobber flags)
                floatResults.forEach { (returns, target) ->
                    val asmTarget = AsmAssignTarget.fromAstAssignment(target as PtAssignTarget, target.definingISub(), asmgen)
                    when (returns.register.registerOrPair) {
                        RegisterOrPair.FAC1 -> assignFAC1float(asmTarget)
                        RegisterOrPair.FAC2 -> assignFAC2float(asmTarget)
                        else -> throw AssemblyError("float result must be in FAC1 or FAC2")
                    }
                }

                // Restore A if we saved it for flag extraction
                if(hasByteInA && statusFlagResults.isNotEmpty()) {
                    asmgen.out("  pla")
                }

                // Handle non-float register results (not status flags)
                if(registersResults.isNotEmpty()) {
                    assignRegisterResults(registersResults)
                }
            } else {
                throw AssemblyError("number of values and targets don't match")
            }
        } else if(thing?.type==StNodeType.SUBROUTINE || thing?.type==StNodeType.BUILTINFUNC) {
            val scope = assignment.definingISub()
            val source = AsmAssignSource.fromAstSource(assignment.children.last() as PtExpression, program, asmgen)
            val targets = AsmAssignTarget.fromAstAssignmentMulti(assignment.children.dropLast(1).map { it as PtAssignTarget }, scope, asmgen)
            val asmassign = AsmAssignment(source, targets, program.memsizer, assignment.position)
            assignExpression(asmassign, scope)
        } else throw AssemblyError("expected extsub or normal sub or builtinfunc")
    }

    private fun assignRegisterResults(registersResults: List<Pair<StExtSubParameter, PtNode>>) {
        registersResults.forEach { (returns, target) ->
            target as PtAssignTarget
            if(!target.void) {
                val targetIdent = target.identifier
                val targetMem = target.memory
                if(targetIdent!=null || targetMem!=null) {
                    val tgt = AsmAssignTarget.fromAstAssignment(target, target.definingISub(), asmgen)
                    when {
                        returns.type.isByteOrBool -> {
                            if(returns.register.registerOrPair in Cx16VirtualRegisters) {
                                assignVirtualRegister(tgt, returns.register.registerOrPair!!)
                            } else {
                                assignRegisterByte(tgt, returns.register.registerOrPair!!.asCpuRegister(), false, false)
                            }
                        }
                        returns.type.isWord -> {
                            assignRegisterpairWord(tgt, returns.register.registerOrPair!!)
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
                else TODO("array target for multi-value assignment  ${target.position}")        // Not done yet due to result register clobbering complexity
            }
        }
    }

    private fun assignCarryFlagResult(target: PtAssignTarget) {
        // overflow is not clobbered so no need to save/restore it
        asmgen.out("  lda  #0  |  rol  a")
        val tgt = AsmAssignTarget.fromAstAssignment(target, target.definingISub(), asmgen)
        assignRegisterByte(tgt, CpuRegister.A, false, false)
    }

    private fun assignZeroFlagResult(target: PtAssignTarget, saveFlags: Boolean) {
        if(saveFlags) asmgen.out("  php")
        asmgen.out("""
                beq  +
                lda  #0
                beq  ++
+               lda  #1
+""")
        val tgt = AsmAssignTarget.fromAstAssignment(target, target.definingISub(), asmgen)
        assignRegisterByte(tgt, CpuRegister.A, false, false)
        if(saveFlags) asmgen.out("  plp")
    }

    private fun assignNegativeFlagResult(target: PtAssignTarget, saveFlags: Boolean) {
        if(saveFlags) asmgen.out("  php")
        asmgen.out("""
                bmi  +
                lda  #0
                beq  ++
+               lda  #1
+""")
        val tgt = AsmAssignTarget.fromAstAssignment(target, target.definingISub(), asmgen)
        assignRegisterByte(tgt, CpuRegister.A, false, false)
        if(saveFlags) asmgen.out("  plp")
    }

    private fun assignOverflowFlagResult(target: PtAssignTarget) {
        // overflow is not clobbered so no need to save/restore it
        asmgen.out("""
                bvs  +
                lda  #0
                beq  ++
+               lda  #1
+""")
        val tgt = AsmAssignTarget.fromAstAssignment(target, target.definingISub(), asmgen)
        assignRegisterByte(tgt, CpuRegister.A, false, false)
    }

    internal fun translateNormalAssignment(assign: AsmAssignment, scope: IPtSubroutine?) {
        when(assign.source.kind) {
            SourceStorageKind.LITERALBOOLEAN -> {
                // simple case: assign a constant boolean (0 or 1)
                require(assign.target.datatype.isNumericOrBool)
                val num = assign.source.boolean!!.asInt()
                when (assign.target.datatype.base) {
                    BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> assignConstantByte(assign.target, num)
                    BaseDataType.UWORD, BaseDataType.WORD -> assignConstantWord(assign.target, num)
                    BaseDataType.LONG -> assignConstantLong(assign.target, num)
                    BaseDataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                require(assign.target.datatype.isNumericOrBool || (assign.target.datatype.isPointer))
                val num = assign.source.number!!.number
                when (assign.target.datatype.base) {
                    BaseDataType.BOOL -> assignConstantByte(assign.target, if(num==0.0) 0 else 1)
                    BaseDataType.UBYTE, BaseDataType.BYTE -> assignConstantByte(assign.target, num.toInt())
                    BaseDataType.UWORD, BaseDataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    BaseDataType.LONG -> assignConstantLong(assign.target, num.toInt())
                    BaseDataType.FLOAT -> assignConstantFloat(assign.target, num)
                    BaseDataType.POINTER -> assignConstantWord(assign.target, num.toInt())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.asmVarname
                val targetDt = assign.target.datatype
                when {
                    targetDt.isBool -> {
                        if (assign.source.datatype.isBool) assignVariableByte(assign.target, variable)
                        else throw AssemblyError("assigning non-bool variable to boolean, should have been typecasted")
                    }
                    targetDt.isByte -> assignVariableByte(assign.target, variable)
                    targetDt.isSignedWord -> assignVariableWord(assign.target, variable, assign.source.datatype)
                    targetDt.isUnsignedWord || targetDt.isPointer -> {
                        if(assign.source.datatype.isPassByRef)
                            assignAddressOf(assign.target, variable, false, assign.source.datatype, assign.source.array ?: PtNumber(BaseDataType.UBYTE, 0.0, assign.position))
                        else
                            assignVariableWord(assign.target, variable, assign.source.datatype)
                    }
                    targetDt.isLong -> assignVariableLong(assign.target, variable, assign.source.datatype)
                    targetDt.isFloat -> assignVariableFloat(assign.target, variable)
                    targetDt.isString -> assignVariableString(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype} ${assign.position}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val valueVar = value.variable
                if(valueVar==null) {
                    val pointerDeref = value.pointerderef!!
                    val constIndex = value.index.asConstInteger()
                    val eltSize = program.memsizer.memorySize(elementDt, null)

                    if(constIndex != null) {
                        val (zpPtrVar, structOffset) = pointergen.deref(pointerDeref, false)
                        val totalOffset = eltSize * constIndex + structOffset.toInt()

                        when {
                            elementDt.isByteOrBool -> {
                                if(totalOffset > 255) {
                                    if(zpPtrVar.startsWith("P8ZP_SCRATCH_")) {
                                        asmgen.out("  lda  $zpPtrVar+1 |  clc  |  adc  #>($totalOffset) |  sta  $zpPtrVar+1 |  ldy  #<($totalOffset) |  lda  ($zpPtrVar),y")
                                    } else {
                                        asmgen.out("  lda  $zpPtrVar |  sta  P8ZP_SCRATCH_PTR |  lda  $zpPtrVar+1 |  clc  |  adc  #>($totalOffset) |  sta  P8ZP_SCRATCH_PTR+1 |  ldy  #<($totalOffset) |  lda  (P8ZP_SCRATCH_PTR),y")
                                    }
                                } else {
                                    asmgen.loadIndirectByte(zpPtrVar, totalOffset.toUByte())
                                }
                                assignRegisterByte(assign.target, CpuRegister.A, elementDt.isSigned, false)
                            }
                            elementDt.isWord -> {
                                if(totalOffset > 255) {
                                    val yOffset = totalOffset and 0xff
                                    if(zpPtrVar.startsWith("P8ZP_SCRATCH_")) {
                                        asmgen.out("""
                                            lda  $zpPtrVar+1
                                            clc
                                            adc  #>($totalOffset)
                                            sta  $zpPtrVar+1
                                            ldy  #$yOffset
                                            lda  ($zpPtrVar),y
                                            tax
                                            iny
                                            lda  ($zpPtrVar),y
                                            tay
                                            txa""")
                                    } else {
                                        asmgen.out("""
                                            lda  $zpPtrVar
                                            sta  P8ZP_SCRATCH_PTR
                                            lda  $zpPtrVar+1
                                            clc
                                            adc  #>($totalOffset)
                                            sta  P8ZP_SCRATCH_PTR+1
                                            ldy  #$yOffset
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            tax
                                            iny
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            tay
                                            txa""")
                                    }
                                } else {
                                    asmgen.loadIndirectWordAY(zpPtrVar, totalOffset.toUByte())
                                }
                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                            }
                            elementDt.isLong -> {
                                if(totalOffset > 255) {
                                    val yOffset = totalOffset and 0xff
                                    if(zpPtrVar.startsWith("P8ZP_SCRATCH_")) {
                                        asmgen.out("""
                                            lda  $zpPtrVar+1
                                            clc
                                            adc  #>($totalOffset)
                                            sta  $zpPtrVar+1
                                            ldy  #$yOffset
                                            lda  ($zpPtrVar),y
                                            sta  P8ZP_SCRATCH_W1
                                            iny
                                            lda  ($zpPtrVar),y
                                            sta  P8ZP_SCRATCH_W1+1
                                            iny
                                            lda  ($zpPtrVar),y
                                            sta  P8ZP_SCRATCH_W1+2
                                            iny
                                            lda  ($zpPtrVar),y
                                            sta  P8ZP_SCRATCH_W1+3""")
                                    } else {
                                        asmgen.out("""
                                            lda  $zpPtrVar
                                            sta  P8ZP_SCRATCH_PTR
                                            lda  $zpPtrVar+1
                                            clc
                                            adc  #>($totalOffset)
                                            sta  P8ZP_SCRATCH_PTR+1
                                            ldy  #$yOffset
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            sta  P8ZP_SCRATCH_W1
                                            iny
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            sta  P8ZP_SCRATCH_W1+1
                                            iny
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            sta  P8ZP_SCRATCH_W1+2
                                            iny
                                            lda  (P8ZP_SCRATCH_PTR),y
                                            sta  P8ZP_SCRATCH_W1+3""")
                                    }
                                } else {
                                    asmgen.out("""
                                        ldy  #$totalOffset
                                        lda  ($zpPtrVar),y
                                        sta  P8ZP_SCRATCH_W1
                                        iny
                                        lda  ($zpPtrVar),y
                                        sta  P8ZP_SCRATCH_W1+1
                                        iny
                                        lda  ($zpPtrVar),y
                                        sta  P8ZP_SCRATCH_W1+2
                                        iny
                                        lda  ($zpPtrVar),y
                                        sta  P8ZP_SCRATCH_W1+3""")
                                }
                                assignVariableLong(assign.target, "P8ZP_SCRATCH_W1", DataType.LONG)
                            }
                            elementDt.isFloat -> {
                                asmgen.out("""
                                    lda  $zpPtrVar
                                    clc
                                    adc  #<($totalOffset)
                                    pha
                                    lda  $zpPtrVar+1
                                    adc  #>($totalOffset)
                                    tay
                                    pla""")
                                assignFloatFromAY(assign.target)
                            }
                            else ->
                                throw AssemblyError("weird array element type for pointer indexed read $elementDt")
                        }
                    } else {
                        val (zpPtrVar, _) = pointergen.deref(pointerDeref, addOffsetToPointer = true, loadPointerFieldValue = true)

                        when {
                            elementDt.isByteOrBool -> {
                                val idx = value.index
                                if(idx is PtIdentifier) {
                                    if(eltSize > 1) {
                                        TODO("non-byte element size $eltSize for pointer array index read with variable index ${value.position}")
                                    }
                                    val indexVarName = asmgen.asmVariableName(idx)
                                    asmgen.out("  ldy  $indexVarName |  lda  ($zpPtrVar),y")
                                } else if(idx.type.isByte) {
                                    asmgen.pushCpuStack(BaseDataType.UBYTE, idx)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("  lda  ($zpPtrVar),y")
                                } else {
                                    asmgen.pushCpuStack(BaseDataType.UWORD, idx)
                                    asmgen.out("  pla |  clc  |  adc  $zpPtrVar+1 |  sta  $zpPtrVar+1")
                                    if(asmgen.isTargetCpu(CpuType.CPU65C02)) asmgen.out("  ply") else asmgen.out("  pla |  tay")
                                    asmgen.out("  lda  ($zpPtrVar),y")
                                }
                                assignRegisterByte(assign.target, CpuRegister.A, elementDt.isSigned, true)
                            }
                            elementDt.isWord -> {
                                val idx = value.index
                                if(idx is PtIdentifier) {
                                    if(eltSize > 1) {
                                        TODO("non-byte element size $eltSize for pointer array index read with variable index ${value.position}")
                                    }
                                    val indexVarName = asmgen.asmVariableName(idx)
                                    asmgen.out("""
                                        ldy  $indexVarName
                                        lda  ($zpPtrVar),y
                                        tax
                                        iny
                                        lda  ($zpPtrVar),y
                                        tay
                                        txa""")
                                } else if(idx.type.isByte) {
                                    asmgen.pushCpuStack(BaseDataType.UBYTE, idx)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("""
                                        lda  ($zpPtrVar),y
                                        tax
                                        iny
                                        lda  ($zpPtrVar),y
                                        tay
                                        txa""")
                                } else {
                                    asmgen.pushCpuStack(BaseDataType.UWORD, idx)
                                    asmgen.out("  pla |  clc  |  adc  $zpPtrVar+1 |  sta  $zpPtrVar+1")
                                    if(asmgen.isTargetCpu(CpuType.CPU65C02)) asmgen.out("  ply") else asmgen.out("  pla |  tay")
                                    asmgen.out("""
                                        lda  ($zpPtrVar),y
                                        tax
                                        iny
                                        lda  ($zpPtrVar),y
                                        tay
                                        txa""")
                                }
                                assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                            }
                            elementDt.isLong -> {
                                TODO("variable index pointer array read of long ${value.position}")
                            }
                            elementDt.isFloat -> {
                                TODO("variable index pointer array read of float ${value.position}")
                            }
                            else ->
                                throw AssemblyError("weird array element type for pointer indexed read $elementDt")
                        }
                    }
                    return
                }
                val arrayVarName = asmgen.asmVariableName(valueVar)

                if(valueVar.type.isPointer) {
                    pointergen.assignIndexedPointer(assign.target, arrayVarName, value)
                    return
                }

                val constIndex = value.index.asConstInteger()
                if(value.splitWords) {
                    require(elementDt.isWord || elementDt.isPointer)
                    if(constIndex!=null) {
                        asmgen.out("  lda  ${arrayVarName}_lsb+$constIndex |  ldy  ${arrayVarName}_msb+$constIndex")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                    } else {
                        asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                        asmgen.out("  lda  ${arrayVarName}_lsb,y |  ldx  ${arrayVarName}_msb,y")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                    }
                    return
                }

                if (constIndex!=null) {
                    // constant array index value
                    val indexValue = program.memsizer.memorySize(elementDt, constIndex)
                    when {
                        elementDt.isByteOrBool -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue")
                            assignRegisterByte(assign.target, CpuRegister.A, elementDt.isSigned, false)
                        }
                        elementDt.isWord -> {
                            asmgen.out("  lda  $arrayVarName+$indexValue |  ldy  $arrayVarName+$indexValue+1")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                        }
                        elementDt.isLong -> {
                            assignVariableLong(assign.target, "$arrayVarName+$indexValue", DataType.LONG)
                        }
                        elementDt.isFloat -> {
                            asmgen.out("  lda  #<($arrayVarName+$indexValue) |  ldy  #>($arrayVarName+$indexValue)")
                            assignFloatFromAY(assign.target)
                        }
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    when {
                        elementDt.isByteOrBool -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y")
                            assignRegisterByte(assign.target, CpuRegister.A, elementDt.isSigned, true)
                        }
                        elementDt.isWord -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  ldx  $arrayVarName+1,y")
                            assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                        }
                        elementDt.isLong -> {
                            assignVariableLongIndexed(assign.target, arrayVarName, value)
                        }
                        elementDt.isFloat -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, CpuRegister.A)
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
            SourceStorageKind.MEMORY -> assignByteFromAddressExpression(assign.source.memory!!.address, assign.target)
            SourceStorageKind.EXPRESSION -> assignExpression(assign, scope)
            SourceStorageKind.REGISTER -> asmgen.assignRegister(assign.source.register!!, assign.target)
        }
    }

    internal fun assignByteFromAddressExpression(address: PtExpression, target: AsmAssignTarget) {

        if (address is PtNumber) {
            val address = address.number.toUInt()
            assignMemoryByte(target, address, null)
            return
        }
        else if (address is PtIdentifier) {
            assignMemoryByte(target, null, address)
            return
        }
        else if(address is PtAddressOf) {
            if(address.identifier!=null) {
                if(asmgen.isZpVar(address.identifier!!)) {
                    asmgen.loadIndirectByte(asmgen.asmVariableName(address.identifier!!), 0u)
                    assignRegisterByte(target, CpuRegister.A, false, true)
                    return
                }
            } else if(address.dereference!=null) {
                val (zpPtrVar, offset) = pointergen.deref(address.dereference!!, false)
                asmgen.loadIndirectByte(zpPtrVar, offset)
                assignRegisterByte(target, CpuRegister.A, false, true)
                return
            }
            asmgen.assignExpressionToVariable(address, "P8ZP_SCRATCH_PTR", DataType.UWORD)
            asmgen.loadIndirectByte("P8ZP_SCRATCH_PTR", 0u)
            assignRegisterByte(target, CpuRegister.A, false, true)
            return
        }
        else if (address is PtBinaryExpression) {
            val result = asmgen.pointerViaIndexRegisterPossible(address)
            if(result!=null) {
                val addressOfIdentifier = (result.first as? PtAddressOf)?.identifier
                if(addressOfIdentifier!=null) {
                    var varname = asmgen.asmVariableName(addressOfIdentifier)
                    if(addressOfIdentifier.type.isSplitWordArray(program.target)) {
                        val addrOf = result.first as PtAddressOf
                        varname = if(addrOf.isMsbForSplitArray) varname+"_msb" else varname+"_lsb"
                    }
                    if(result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        asmgen.out("  lda  $varname+$offset")
                        assignRegisterByte(target, CpuRegister.A, false, true)
                        return
                    } else if (result.second is PtIdentifier) {
                        val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                        asmgen.out("  ldx  $offsetname |  lda  $varname,x")
                        assignRegisterByte(target, CpuRegister.A, false, true)
                        return
                    }
                }
            }

            if(asmgen.tryOptimizedPointerAccessWithA(address, false)) {
                assignRegisterByte(target, CpuRegister.A, false, true)
                return
            }

            if(address.operator=="+" && address.right.type.isUnsignedWord) {
                if (address.left is PtIdentifier) {
                    // use (zp),Y instead of explicitly calculating the full zp pointer value
                    val pointer = (address.left as PtIdentifier).name
                    when(val index=address.right) {
                        is PtIdentifier -> {
                            val indexName = index.name
                            asmgen.out("""
                                lda  $pointer
                                sta  P8ZP_SCRATCH_W2
                                lda  $pointer+1
                                clc
                                adc  $indexName+1
                                sta  P8ZP_SCRATCH_W2+1
                                ldy  $indexName
                                lda  (P8ZP_SCRATCH_W2),y""")
                            assignRegisterByte(target, CpuRegister.A, false, true)
                            return
                        }
                        is PtNumber -> {
                            val indexValue = index.number.toInt().toString()
                            asmgen.out("""
                                lda  $pointer
                                sta  P8ZP_SCRATCH_W2
                                lda  $pointer+1
                                clc
                                adc  #>$indexValue
                                sta  P8ZP_SCRATCH_W2+1
                                ldy  #<$indexValue
                                lda  (P8ZP_SCRATCH_W2),y""")
                            assignRegisterByte(target, CpuRegister.A, false, true)
                            return
                        }
                        else -> {}
                    }
                }
            }
//          else if(address.operator=="-") {
//              // TODO does this ever occur? we could optimize it too, but it seems like a pathological case
//          }
        }

        // fallback assignmen through temporary pointer var
        assignExpressionToVariable(address, "P8ZP_SCRATCH_W2", DataType.UWORD)
        asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
        assignRegisterByte(target, CpuRegister.A, false, true)
    }

    internal fun isConstReloadableAddress(address: PtExpression): Boolean {
        // addresses for which storeByteInAToAddressExpression can reload a constant byte
        // itself (after computing the pointer), avoiding a tax/txa round-trip through X
        return address is PtBinaryExpression && address.operator=="+" &&
                address.left is PtIdentifier && address.right.type.isUnsignedWord &&
                (address.right is PtIdentifier || address.right is PtNumber)
    }

    internal fun storeByteInAToAddressExpression(address: PtExpression, saveA: Boolean, byteValue: Int? = null) {
        if(address is PtBinaryExpression) {
            if(address.operator=="+") {
                if (address.left is PtIdentifier && address.right.type.isUnsignedWord) {
                    // use (zp),Y instead of explicitly calculating the full zp pointer value
                    val pointer = (address.left as PtIdentifier).name
                    when(val index=address.right) {
                        is PtIdentifier -> {
                            val indexName = index.name
                            if(byteValue!=null) {
                                // the byte is a known constant: set up the pointer first, then load
                                // the constant into A, so no tax/txa round-trip through X is needed
                                asmgen.out("""
                                    lda  $pointer
                                    sta  P8ZP_SCRATCH_W2
                                    lda  $pointer+1
                                    clc
                                    adc  $indexName+1
                                    sta  P8ZP_SCRATCH_W2+1
                                    ldy  $indexName
                                    lda  #${byteValue.toHex()}
                                    sta  (P8ZP_SCRATCH_W2),y""")
                            } else {
                                asmgen.out("""
                                    tax
                                    lda  $pointer
                                    sta  P8ZP_SCRATCH_W2
                                    lda  $pointer+1
                                    clc
                                    adc  $indexName+1
                                    sta  P8ZP_SCRATCH_W2+1
                                    ldy  $indexName
                                    txa
                                    sta  (P8ZP_SCRATCH_W2),y""")
                            }
                            return
                        }
                        is PtNumber -> {
                            val indexValue = index.number.toInt().toString()
                            if(byteValue!=null) {
                                asmgen.out("""
                                    lda  $pointer
                                    sta  P8ZP_SCRATCH_W2
                                    lda  $pointer+1
                                    clc
                                    adc  #>$indexValue
                                    sta  P8ZP_SCRATCH_W2+1
                                    ldy  #<$indexValue
                                    lda  #${byteValue.toHex()}
                                    sta  (P8ZP_SCRATCH_W2),y""")
                            } else {
                                asmgen.out("""
                                    tax
                                    lda  $pointer
                                    sta  P8ZP_SCRATCH_W2
                                    lda  $pointer+1
                                    clc
                                    adc  #>$indexValue
                                    sta  P8ZP_SCRATCH_W2+1
                                    ldy  #<$indexValue
                                    txa
                                    sta  (P8ZP_SCRATCH_W2),y""")
                            }
                            return
                        }
                        else -> {}
                    }
                }
            }
//          else if(address.operator=="-") {
//              // does this ever occur? we could optimize it too, but it seems like a pathological case
//          }
        }
        if(saveA) asmgen.out("  pha")
        assignExpressionToVariable(address, "P8ZP_SCRATCH_W2", DataType.UWORD)
        if(saveA) asmgen.out("  pla")
        asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2", false)
    }


    internal fun assignExpression(assign: AsmAssignment, scope: IPtSubroutine?) {
        when(val value = assign.source.expression!!) {
            is PtAddressOf -> {
                if (value.identifier != null || value.isFromArrayElement) {
                    val identifier = value.identifier!!
                    val source = asmgen.symbolTable.lookup(identifier.name)
                    require(source !is StConstant) { "addressOf of a constant should have been rewritten to a simple addition expression" }
                    val sourceName = asmgen.asmSymbolName(identifier)
                    assignAddressOf(assign.target, sourceName, value.isMsbForSplitArray, identifier.type, value.arrayIndexExpr)
                } else {
                    val ptrderef = value.dereference!!
                    val (zpPtrVar, offset) = pointergen.deref(ptrderef)
                    if (offset > 0u) {
                        // need to add offset to pointer but not modify the original!
                        asmgen.out("""
                            lda  $zpPtrVar
                            ldx  $zpPtrVar+1
                            clc
                            adc  #$offset
                            bcc  +
                            inx                            
+""")
                        assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                    } else {
                        assignVariableWord(assign.target, zpPtrVar, DataType.UWORD)
                    }
                }
            }
            is PtBool -> throw AssemblyError("source kind should have been literalboolean")
            is PtNumber -> throw AssemblyError("source kind should have been literalnumber")
            is PtIdentifier -> throw AssemblyError("source kind should have been variable")
            is PtArrayIndexer -> throw AssemblyError("source kind should have been array")
            is PtMemoryByte -> throw AssemblyError("source kind should have been memory")
            is PtTypeCast -> assignTypeCastedValue(assign.target, value.type, value.value, value)
            is PtFunctionCall -> assignFunctionCall(assign, value)
            is PtPrefix -> assignPrefixExpr(assign, value, scope)
            is PtContainmentCheck -> {
                containmentCheckIntoA(value)
                assignRegisterByte(assign.target, CpuRegister.A, false, true)
            }
            is PtBinaryExpression -> {
                if(value.operator==".") {
                    val (zpPtrVar, offset, dt) = pointergen.operatorDereference(value)
                    when {
                        dt.isByteOrBool -> {
                            asmgen.loadIndirectByte(zpPtrVar, offset)
                            asmgen.assignRegister(RegisterOrPair.A, assign.target)
                        }
                        dt.isWord ||dt.isPointer -> {
                            if(assign.target.register in arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                                asmgen.loadIndirectWordIntoRegisters(zpPtrVar, offset, assign.target.register!!)
                            } else {
                                asmgen.loadIndirectWordAY(zpPtrVar, offset)
                                asmgen.assignRegister(RegisterOrPair.AY, assign.target)
                            }
                        }
                        dt.isFloat -> {
                            asmgen.loadIndirectFloat(zpPtrVar, offset)
                            asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                        }
                        dt.isLong -> {
                            TODO("read long ${value.position}")
                        }
                        else -> throw AssemblyError("unsupported dereference type $dt ${value.position}")
                    }
                }
                else {
                    if (!attemptAssignOptimizedBinexpr(value, assign)) {
                        // TOO BAD: the expression was too complex to translate into assembly.
                        val pos = if (value.position !== Position.DUMMY) value.position else assign.position
                        throw AssemblyError("Expression is too complex to translate into assembly. Split it up into several separate statements, introduce a temporary variable, or otherwise rewrite it. Location: $pos")
                    }
                }
            }
            is PtIfExpression -> asmgen.assignIfExpression(assign.target, value)
            is PtBranchCondExpression -> asmgen.assignBranchCondExpression(assign.target, value)
            is PtPointerDeref -> pointergen.assignPointerDerefExpression(assign.target, value)
            is PtConstant -> {
                val slab = value.memorySlab
                require(slab != null) { "remaining PtConstant in asmgen as assignment value can only be a memory slab reference at ${value.position}" }
                val label = "$StMemorySlabBlockName.${slab.name}"
                assignAddressOf(assign.target, label, false, DataType.UWORD, null)
            }
            else -> throw AssemblyError("weird assignment value type $value")
        }
    }

    internal fun assignPrefixExpr(assign: AsmAssignment, value: PtPrefix, scope: IPtSubroutine?) {
        if(assign.target.array==null) {
            if(assign.source.datatype isAssignableTo assign.target.datatype || (assign.source.datatype.isBool && assign.target.datatype.isByte)) {
                if(assign.source.datatype.isWordOrByteOrBool) {
                    val signed = assign.source.datatype.isSigned
                    if(assign.source.datatype.isByteOrBool) {
                        assignExpressionToRegister(value.value, RegisterOrPair.A, signed)
                        when(value.operator) {
                            "+" -> {}
                            "-" -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65C02))
                                    asmgen.out("  eor  #255 |  ina")
                                else
                                    asmgen.out("  eor  #255 |  clc |  adc  #1")
                            }
                            "~" -> asmgen.out("  eor  #255")
                            "not" -> asmgen.out("  eor  #1")
                            else -> throw AssemblyError("invalid prefix operator")
                        }
                        assignRegisterByte(assign.target, CpuRegister.A, signed, false)
                    } else {
                        assignExpressionToRegister(value.value, RegisterOrPair.AY, signed)
                        when(value.operator) {
                            "+" -> {}
                            "-" -> {
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
                            "~" -> asmgen.out("  tax |  tya |  eor  #255 |  tay |  txa |  eor  #255")
                            "not" -> throw AssemblyError("not shouldn't exist for an integer")
                            else -> throw AssemblyError("invalid prefix operator")
                        }
                        assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                    }
                } else {
                    // First assign the value to the target then apply the operator in place on the target.
                    // This saves a temporary variable
                    translateNormalAssignment(
                        AsmAssignment(
                            AsmAssignSource.fromAstSource(value.value, program, asmgen),
                            assign.targets, program.memsizer, assign.position
                        ), scope
                    )
                    when (value.operator) {
                        "+" -> {}
                        "-" -> inplaceNegate(assign, true, scope)
                        "~" -> inplaceInvert(assign, scope)
                        "not" -> inplaceInvert(assign, scope)
                        else -> throw AssemblyError("invalid prefix operator")
                    }
                }
            } else {
                // use a temporary variable
                val tempvar = if(value.type.isByteOrBool) "P8ZP_SCRATCH_B1" else "P8ZP_SCRATCH_W1"
                assignExpressionToVariable(value.value, tempvar, value.type)
                when (value.operator) {
                    "+" -> {}
                    "-", "~" -> {
                        val assignTempvar = AsmAssignment(
                            AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, value.type, variableAsmName = tempvar),
                            listOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, value.type, scope, assign.position, variableAsmName = tempvar)),
                            program.memsizer, assign.position)
                        if(value.operator=="-")
                            inplaceNegate(assignTempvar, true, scope)
                        else
                            inplaceInvert(assignTempvar, scope)
                    }
                    "not" -> {
                        val assignTempvar = AsmAssignment(
                            AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, value.type, variableAsmName = tempvar),
                            listOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, value.type, scope, assign.position, variableAsmName = tempvar)),
                            program.memsizer, assign.position)
                        inplaceInvert(assignTempvar, scope)
                    }
                    else -> throw AssemblyError("invalid prefix operator")
                }
                if(value.type.isByteOrBool)
                    assignVariableByte(assign.target, tempvar)
                else
                    assignVariableWord(assign.target, tempvar, value.type)
            }
        } else {
            assignPrefixedExpressionToArrayElt(assign, scope)
        }
    }

    internal fun assignFunctionCall(assign: AsmAssignment, value: PtFunctionCall) {
        val symbol = asmgen.symbolTable.lookup(value.name)
        if(symbol!!.type==StNodeType.BUILTINFUNC) {
            // builtin function call

            if (value.type.isLong && assign.targets.single().kind == TargetStorageKind.VARIABLE) {

                val targetVarName = assign.targets.single().asmVarname

                fun handleResultInRegister(resultReg: Array<RegisterOrPair>) {
                    resultReg.singleOrNull()?.let {
                        asmgen.out("  ;--REG TO VAR")
                        asmgen.assignRegister(it, assign.targets[0])
                        asmgen.out("  ;--REG TO VAR DONE")
                    }
                }
                
                when(value.name) {
                    "peekl" -> {
                        val resultInRegister = asmgen.builtinFunctionsAsmGen.funcPeekL(value, targetVarName)
                        return handleResultInRegister(resultInRegister)
                    }
                    "mklong" -> {
                        val resultInRegister = asmgen.builtinFunctionsAsmGen.funcMklong(value, RegisterOrPair.R14R15, targetVarName)
                        return handleResultInRegister(resultInRegister)
                    }
                    "mklong2" -> {
                        val resultInRegister = asmgen.builtinFunctionsAsmGen.funcMklong(value, RegisterOrPair.R14R15, targetVarName)
                        return handleResultInRegister(resultInRegister)
                    }
                }
            }

            if(value.name=="lmh") {
                // avoid temporary variable use when assigning lmh results
                val arg = value.args.single()
                if(arg is PtIdentifier) {
                    val (targetL, targetM, targetH) = assign.targets
                    val acceptedTargets = arrayOf(TargetStorageKind.VARIABLE, TargetStorageKind.VOID)
                    if(targetL.kind in acceptedTargets && targetM.kind in acceptedTargets && targetH.kind in acceptedTargets) {
                        val varname = asmgen.asmVariableName(arg)
                        if(targetL.kind==TargetStorageKind.VARIABLE) asmgen.out("  lda  $varname |  sta  ${targetL.asmVarname}")
                        if(targetM.kind==TargetStorageKind.VARIABLE) asmgen.out("  lda  $varname+1 |  sta  ${targetM.asmVarname}")
                        if(targetH.kind==TargetStorageKind.VARIABLE) asmgen.out("  lda  $varname+2 |  sta  ${targetH.asmVarname}")
                        return
                    }
                }
            }

            // TODO optimized float functions into variable? (to avoid needless FAC1 register copying?)

            val firstTarget = assign.targets.firstOrNull()
            val actualResultRegisters = asmgen.translateBuiltinFunctionCallExpression(value, firstTarget?.register)

            require(actualResultRegisters.size==assign.targets.size) { "builtin function call should have same number return values as assignment targets ${value.position}" }
//            assign.targets.zip(actualResultRegisters).forEach { (target, register) ->
//                if(target.kind==TargetStorageKind.REGISTER) {
//                    if(target.register!! != register) {
//                        println("OPTIMIZE: ${value.name} builtin func output register mismatch ${target.register}  got $register  ${value.position}")
//                    }
//                }
//            }

            if(assign.targets.size>1) {
                assign.targets.zip(actualResultRegisters)
                    .filter { it.first.kind != TargetStorageKind.VOID }
                    .forEach { target -> asmgen.assignRegister(target.second, target.first) }
            } else if(actualResultRegisters.isNotEmpty() && assign.targets.single().kind != TargetStorageKind.VOID) {
                val target = assign.targets.single()
                if(target.kind != TargetStorageKind.VOID) {
                    asmgen.assignRegister(actualResultRegisters.single(), target)
                }
            }
            return
        }

        // regular subroutine call
        val sub = symbol.astNode as IPtSubroutine
        val returns = sub.returnsWhatWhere(asmgen.options.compTarget)
        asmgen.translateFunctionCall(value)
        if(sub is PtSub && sub.signature.returns.size>1) {
            // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
            // (this allows unencumbered use of many Rx registers if you don't return that many values)
            assign.targets.zip(returns).forEach { target ->
                if(target.first.kind != TargetStorageKind.VOID) {
                    asmgen.assignRegister(target.second.first.registerOrPair!!, target.first)
                }
            }
        } else {
            val target = assign.target
            if(target.kind==TargetStorageKind.VOID)
                return
            val returnValue = returns.singleOrNull { it.first.registerOrPair!=null } ?: returns.single { it.first.statusflag!=null }
            when {
                returnValue.second.isString -> {
                    val targetDt = target.datatype
                    when {
                        targetDt.isUnsignedWord -> {
                            // assign the address of the string result value
                            assignRegisterpairWord(target, RegisterOrPair.AY)
                        }
                        targetDt.isString || targetDt.isUnsignedByteArray || targetDt.isByteArray -> {
                            throw AssemblyError("stringvalue assignment should have been replaced by a call to strcpy")
                        }
                        else -> throw AssemblyError("weird target dt")
                    }
                }
                returnValue.second.isFloat -> {
                    // float result from function sits in FAC1
                    assignFAC1float(target)
                }
                else -> {
                    // do NOT restore X register before assigning the result values first
                    when (returnValue.first.registerOrPair) {
                        RegisterOrPair.A -> assignRegisterByte(target, CpuRegister.A, returnValue.second.isSigned, true)
                        RegisterOrPair.X -> assignRegisterByte(target, CpuRegister.X, returnValue.second.isSigned, true)
                        RegisterOrPair.Y -> assignRegisterByte(target, CpuRegister.Y, returnValue.second.isSigned, true)
                        RegisterOrPair.AX -> assignVirtualRegister(target, RegisterOrPair.AX)
                        RegisterOrPair.AY -> assignVirtualRegister(target, RegisterOrPair.AY)
                        RegisterOrPair.XY -> assignVirtualRegister(target, RegisterOrPair.XY)
                        in Cx16VirtualRegisters -> assignVirtualRegister(target, returnValue.first.registerOrPair!!)
                        in CombinedLongRegisters -> assignVirtualRegister(target, returnValue.first.registerOrPair!!)
                        else -> {
                            val sflag = returnValue.first.statusflag
                            if(sflag!=null)
                                assignStatusFlagByte(target, sflag)
                            else
                                throw AssemblyError("should be just one register byte result value")
                        }
                    }
                }
            }
        }
    }

    internal fun assignPrefixedExpressionToArrayElt(assign: AsmAssignment, scope: IPtSubroutine?) {
        require(assign.source.expression is PtPrefix)
        if(assign.source.datatype.isFloat) {
            // floatarray[x] = -value   ... just use FAC1 to calculate the expression into and then store that back into the array.
            assignExpressionToRegister(assign.source.expression, RegisterOrPair.FAC1, true)
            assignFAC1float(assign.target)
        } else {
            val register = if(assign.source.datatype.isByteOrBool) RegisterOrPair.A else RegisterOrPair.AY
            val assignToRegister = AsmAssignment(assign.source,
                listOf(
                    AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, assign.target.datatype, assign.target.scope, assign.target.position,
                        register = register, origAstTarget = assign.target.origAstTarget)
                ),
                program.memsizer, assign.position)
            asmgen.translateNormalAssignment(assignToRegister, scope)
            val signed = assign.target.datatype.isSigned
            val targetDt = assign.target.datatype
            when {
                targetDt.isByteOrBool -> assignRegisterByte(assign.target, CpuRegister.A, signed, false)
                targetDt.isWord -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                else -> throw AssemblyError("weird dt")
            }
        }
    }

    internal fun assignVirtualRegister(target: AsmAssignTarget, register: RegisterOrPair) = primitiveGen.assignVirtualRegister(target, register)

    internal fun attemptAssignOptimizedBinexpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean = binaryOpGen.attemptAssignOptimizedBinexpr(expr, assign)

    internal fun directIntoY(expr: PtExpression): Boolean = binaryOpGen.directIntoY(expr)

    internal fun optimizedLogicalExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean = binaryOpGen.optimizedLogicalExpr(expr, target)

    internal fun containmentCheckIntoA(containment: PtContainmentCheck) {
        val elementDt = containment.needle.type

        if(containment.haystackValues!=null) {
            val haystack = containment.haystackValues!!.children.map {
                if(it is PtBool) it.asInt()
                else (it as PtNumber).number.toInt()
            }
            when {
                elementDt.isByteOrBool -> {
                    require(haystack.size in 0..PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_BYTE)
                    assignExpressionToRegister(containment.needle, RegisterOrPair.A, elementDt.isSigned)
                    for(number in haystack) {
                        asmgen.out("""
                            cmp  #$number
                            beq  +""")
                    }
                    asmgen.out("""
                        lda  #0
                        beq  ++
+                       lda  #1
+""")
                }
                elementDt.isWord -> {
                    require(haystack.size in 0..PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_WORD)
                    assignExpressionToRegister(containment.needle, RegisterOrPair.AY, elementDt.isSigned)
                    val gottemLabel = asmgen.makeLabel("gottem")
                    val endLabel = asmgen.makeLabel("end")
                    for(number in haystack) {
                        asmgen.out("""
                            cmp  #<$number
                            bne  +
                            cpy  #>$number
                            beq  $gottemLabel
+                       """)
                    }
                    asmgen.out("""
                        lda  #0
                        beq  $endLabel
$gottemLabel            lda  #1
$endLabel""")
                }
                elementDt.isFloat -> throw AssemblyError("containmentchecks for floats should always be done on an array variable with subroutine")
                else -> throw AssemblyError("weird dt $elementDt")
            }

            return
        }

        val symbol = asmgen.symbolTable.lookup(containment.haystackHeapVar!!.name)!!
        val symbolName = asmgen.asmVariableName(symbol, containment.definingISub())
        val (dt, numElements) = when(symbol) {
            is StStaticVariable  -> symbol.dt to symbol.length!!
            is StMemVar -> symbol.dt to symbol.length!!
            else -> DataType.UNDEFINED to 0u
        }
        when {
            dt.isString -> {
                assignExpressionToRegister(containment.needle, RegisterOrPair.A, elementDt.isSigned)
                asmgen.out("  pha")     // need to keep the scratch var safe so we have to do it in this order
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position,"P8ZP_SCRATCH_W1"), symbolName, false, null, null)
                asmgen.out("  pla")
                asmgen.out("  ldy  #${numElements-1u}")
                asmgen.out("  jsr  prog8_lib.containment_bytearray")
            }
            dt.isFloatArray -> {
                assignExpressionToRegister(containment.needle, RegisterOrPair.FAC1, true)
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W1"), symbolName, false, null, null)
                asmgen.out("  ldy  #$numElements")
                asmgen.out("  jsr  floats.containment_floatarray")
            }
            dt.isByteArray -> {
                assignExpressionToRegister(containment.needle, RegisterOrPair.A, elementDt.isSigned)
                asmgen.out("  pha")     // need to keep the scratch var safe so we have to do it in this order
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W1"), symbolName, false, null, null)
                asmgen.out("  pla")
                asmgen.out("  ldy  #$numElements")
                asmgen.out("  jsr  prog8_lib.containment_bytearray")
            }
            dt.isWordArray -> {
                assignExpressionToVariable(containment.needle, "P8ZP_SCRATCH_W1", elementDt)
                if(dt.isSplitWordArray(program.memsizer)) {
                    assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W2"), symbolName+"_lsb", false, null, null)
                    asmgen.out("  ldy  #$numElements")
                    asmgen.out("  jsr  prog8_lib.containment_splitwordarray")
                } else {
                    assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W2"), symbolName, false, null, null)
                    asmgen.out("  ldy  #$numElements")
                    asmgen.out("  jsr  prog8_lib.containment_linearwordarray")
                }
            }
            dt.isLongArray -> {
                asmgen.assignExpressionToRegister(containment.needle, RegisterOrPair.R14R15, true)
                asmgen.out("""
                    lda  cx16.r14
                    sta  P8ZP_SCRATCH_W1
                    lda  cx16.r14+1
                    sta  P8ZP_SCRATCH_W1+1
                    lda  cx16.r15
                    sta  P8ZP_SCRATCH_W2
                    lda  cx16.r15+1
                    sta  P8ZP_SCRATCH_W2+1""")
                assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_PTR"), symbolName, false, null, null)
                asmgen.out("  ldy  #$numElements")
                asmgen.out("  jsr  prog8_lib.containment_longarray")
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    internal fun assignStatusFlagByte(target: AsmAssignTarget, statusflag: Statusflag) {
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
            Statusflag.Pz -> {
                asmgen.out("""
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
            Statusflag.Pn -> {
                asmgen.out("""
                    bmi  +
                    lda  #0
                    beq  ++
+                   lda  #1
+""")
            }
        }
        assignRegisterByte(target, CpuRegister.A, false, true)
    }

    internal fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: PtExpression, origTypeCastExpression: PtTypeCast) = typeCastGen.assignTypeCastedValue(target, targetDt, value, origTypeCastExpression)


    /**
     * Compute 8-bit offset Y = idx*structSize + fieldOffset for a struct-array element.
     * Assumes totalBytes <=256 so offset fits in Y. Uses A/Y and multiply_bytes when needed.
     */
    internal fun computeStructArrayOffsetY(arrayDt: DataType, indexExpr: PtExpression, fieldOffset: Int = 0) = primitiveGen.computeStructArrayOffsetY(arrayDt, indexExpr, fieldOffset)

    internal fun assignAddressOf(target: AsmAssignTarget, sourceName: String, msb: Boolean, arrayDt: DataType?, arrayIndexExpr: PtExpression?) = primitiveGen.assignAddressOf(target, sourceName, msb, arrayDt, arrayIndexExpr)

    internal fun assignVariableString(target: AsmAssignTarget, varName: String) = primitiveGen.assignVariableString(target, varName)

    internal fun assignVariableLong(target: AsmAssignTarget, varName: String, sourceDt: DataType) = primitiveGen.assignVariableLong(target, varName, sourceDt)

    internal fun assignVariableLongIndexed(target: AsmAssignTarget, arrayVarName: String, index: PtArrayIndexer) = primitiveGen.assignVariableLongIndexed(target, arrayVarName, index)

    internal fun assignVariableWord(target: AsmAssignTarget, varName: String, sourceDt: DataType) = primitiveGen.assignVariableWord(target, varName, sourceDt)

    internal fun assignFAC2float(target: AsmAssignTarget) = primitiveGen.assignFAC2float(target)

    internal fun assignFAC1float(target: AsmAssignTarget) = primitiveGen.assignFAC1float(target)

    internal fun assignFloatFromAY(target: AsmAssignTarget) = primitiveGen.assignFloatFromAY(target)

    internal fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) = primitiveGen.assignVariableFloat(target, sourceName)

    internal fun assignVariableByte(target: AsmAssignTarget, varName: String) = primitiveGen.assignVariableByte(target, varName)

    internal fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) = primitiveGen.assignVariableByteIntoWord(wordtarget, bytevar)

    internal fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) = primitiveGen.assignVariableUByteIntoWord(wordtarget, bytevar)

    internal fun assignRegisterLong(target: AsmAssignTarget, pairedRegisters: RegisterOrPair) = primitiveGen.assignRegisterLong(target, pairedRegisters)

    internal fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister, signed: Boolean, extendSignedBits: Boolean) = primitiveGen.assignRegisterByte(target, register, signed, extendSignedBits)

    internal fun assignRegisterpairWord(target: AsmAssignTarget, regs: RegisterOrPair) = primitiveGen.assignRegisterpairWord(target, regs)

    internal fun assignConstantLong(target: AsmAssignTarget, long: Int) = primitiveGen.assignConstantLong(target, long)

    internal fun assignConstantWord(target: AsmAssignTarget, word: Int) = primitiveGen.assignConstantWord(target, word)

    internal fun assignConstantByte(target: AsmAssignTarget, byte: Int) = primitiveGen.assignConstantByte(target, byte)

    internal fun assignConstantFloat(target: AsmAssignTarget, float: Double) = primitiveGen.assignConstantFloat(target, float)

    internal fun assignMemoryByte(target: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) = primitiveGen.assignMemoryByte(target, address, identifier)

    internal fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) = primitiveGen.assignMemoryByteIntoWord(wordtarget, address, identifier)
    
    internal fun assignExpressionToRegister(expr: PtExpression, register: RegisterOrPair, signed: Boolean) = primitiveGen.assignExpressionToRegister(expr, register, signed)

    internal fun assignExpressionToVariable(expr: PtExpression, asmVarName: String, dt: DataType) = primitiveGen.assignExpressionToVariable(expr, asmVarName, dt)

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean, scope: IPtSubroutine?, pos: Position) = primitiveGen.assignVariableToRegister(asmVarName, register, signed, scope, pos)

    internal fun inplaceInvert(assign: AsmAssignment, scope: IPtSubroutine?) = primitiveGen.inplaceInvert(assign, scope)

    internal fun inplaceNegate(assign: AsmAssignment, ignoreDatatype: Boolean, scope: IPtSubroutine?) = primitiveGen.inplaceNegate(assign, ignoreDatatype, scope)

}
