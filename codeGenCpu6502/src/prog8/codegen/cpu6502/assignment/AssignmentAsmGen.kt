package prog8.codegen.cpu6502.assignment

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator
import kotlin.math.log2


internal class AssignmentAsmGen(
    private val program: PtProgram,
    private val asmgen: AsmGen6502Internal,
    private val pointergen: PointerAssignmentsGen,
    private val anyExprGen: AnyExprAsmGen,
    private val allocator: VariableAllocator
) {
    lateinit var augmentableAsmGen: AugmentableAssignmentAsmGen

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

        val extsub = asmgen.symbolTable.lookup(values.name) as? StExtSub
        if(extsub!=null) {
            require(extsub.returns.size>=2)
            if(extsub.returns.any { it.type.isFloat })
                TODO("deal with (multiple?) FP return registers  ${assignment.position}")

            asmgen.translate(values)

            val assignmentTargets = assignment.children.dropLast(1)
            if(extsub.returns.size==assignmentTargets.size) {
                // because we can only handle integer results right now we can just zip() it all up
                val (statusFlagResults, registersResults) = extsub.returns.zip(assignmentTargets).partition { it.first.register.statusflag!=null }
                if (statusFlagResults.isEmpty())
                    assignRegisterResults(registersResults)
                else if(registersResults.isEmpty())
                    assignOnlyTheStatusFlagsResults(false, statusFlagResults)
                else
                    assignStatusFlagsAndRegistersResults(statusFlagResults, registersResults)
            } else {
                throw AssemblyError("number of values and targets don't match")
            }
        } else {
            val sub = asmgen.symbolTable.lookup(values.name) as? StSub
            if(sub!=null) {
                val scope = assignment.definingISub()
                val source = AsmAssignSource.fromAstSource(assignment.children.last() as PtExpression, program, asmgen)
                val targets = AsmAssignTarget.fromAstAssignmentMulti(assignment.children.dropLast(1).map { it as PtAssignTarget }, scope, asmgen)
                val asmassign = AsmAssignment(source, targets, program.memsizer, assignment.position)
                assignExpression(asmassign, scope)
            }
            else throw AssemblyError("expected extsub or normal sub")
        }
    }

    private fun assignStatusFlagsAndRegistersResults(
        statusFlagResults: List<Pair<StExtSubParameter, PtNode>>,
        registersResults: List<Pair<StExtSubParameter, PtNode>>
    ) {

        fun needsToSaveA(registersResults: List<Pair<StExtSubParameter, PtNode>>): Boolean =
            if(registersResults.isEmpty())
                false
            else if(registersResults.all { (it.second as PtAssignTarget).identifier!=null})
                false
            else
                true

        if(registersResults.all {
                val tgt = it.second as PtAssignTarget
                tgt.void || tgt.identifier!=null})
        {
            // all other results are just stored into identifiers directly so first handle those
            // (simple store instructions that don't modify the carry flag)
            assignRegisterResults(registersResults)
            assignOnlyTheStatusFlagsResults(false, statusFlagResults)
        } else {
            val saveA = needsToSaveA(registersResults)
            assignOnlyTheStatusFlagsResults(saveA, statusFlagResults)
            assignRegisterResults(registersResults)
        }
    }

    private fun assignOnlyTheStatusFlagsResults(saveA: Boolean, statusFlagResults: List<Pair<StExtSubParameter, PtNode>>) {
        // assigning flags to their variables targets requires load-instructions that destroy flags
        // so if there's more than 1, we need to save and restore the flags
        val saveFlags = statusFlagResults.size>1

        fun hasFlag(statusFlagResults: List<Pair<StExtSubParameter, PtNode>>, flag: Statusflag): PtAssignTarget? {
            for ((returns, target) in statusFlagResults) {
                if(returns.register.statusflag!! == flag)
                    return target as PtAssignTarget
            }
            return null
        }

        val targetCarry = hasFlag(statusFlagResults, Statusflag.Pc)
        val targetZero = hasFlag(statusFlagResults, Statusflag.Pz)
        val targetNeg = hasFlag(statusFlagResults, Statusflag.Pn)
        val targetOverflow = hasFlag(statusFlagResults, Statusflag.Pv)

        if(saveA) asmgen.out("  pha")
        if(targetZero!=null && !targetZero.void)
            assignZeroFlagResult(targetZero, saveFlags)
        if(targetNeg!=null && !targetNeg.void)
            assignNegativeFlagResult(targetNeg, saveFlags)
        if(targetCarry!=null && !targetCarry.void)
            assignCarryFlagResult(targetCarry)
        if(targetOverflow!=null && !targetOverflow.void)
            assignOverflowFlagResult(targetOverflow)
        if(saveA) asmgen.out("  pla")
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

    fun translateNormalAssignment(assign: AsmAssignment, scope: IPtSubroutine?) {
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
                    targetDt.isUnsignedWord -> {
                        if(assign.source.datatype.isPassByRef)
                            assignAddressOf(assign.target, variable, false, assign.source.datatype, assign.source.array ?: PtNumber(BaseDataType.UBYTE, 0.0, assign.position))
                        else
                            assignVariableWord(assign.target, variable, assign.source.datatype)
                    }
                    targetDt.isLong -> assignVariableLong(assign.target, variable, assign.source.datatype)
                    targetDt.isFloat -> assignVariableFloat(assign.target, variable)
                    targetDt.isString -> assignVariableString(assign.target, variable)
                    targetDt.isPointer -> assignVariableWord(assign.target, variable, assign.source.datatype)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype} ${assign.position}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val valueVar = value.variable
                if(valueVar==null) {
                    TODO("translate assignment of pointer ${value.position}")
                    return
                }
                val arrayVarName = asmgen.asmVariableName(valueVar)

                if(valueVar.type.isPointer) {
                    pointergen.assignIndexedPointer(assign.target, arrayVarName, value.index, valueVar.type)
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

    private fun assignByteFromAddressExpression(address: PtExpression, target: AsmAssignTarget) {

        if (address is PtNumber) {
            val address = address.number.toUInt()
            assignMemoryByte(target, address, null)
            return
        }
        else if (address is PtIdentifier) {
            assignMemoryByte(target, null, address)
            return
        }
        else if (address is PtBinaryExpression) {
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

    private fun storeByteInAToAddressExpression(address: PtExpression, saveA: Boolean) {
        if(address is PtBinaryExpression) {
            if(address.operator=="+") {
                if (address.left is PtIdentifier && address.right.type.isUnsignedWord) {
                    // use (zp),Y instead of explicitly calculating the full zp pointer value
                    val pointer = (address.left as PtIdentifier).name
                    when(val index=address.right) {
                        is PtIdentifier -> {
                            val indexName = index.name
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
                            return
                        }
                        is PtNumber -> {
                            val indexValue = index.number.toInt().toString()
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


    private fun assignExpression(assign: AsmAssignment, scope: IPtSubroutine?) {
        when(val value = assign.source.expression!!) {
            is PtAddressOf -> {
                if (value.identifier != null || value.isFromArrayElement) {
                    val identifier = value.identifier!!
                    val source = asmgen.symbolTable.lookup(identifier.name)
                    require(source !is StConstant) { "addressOf of a constant should have been rewritten to a simple addition expression" }
                    val sourceName =
                        if (value.isMsbForSplitArray)
                            asmgen.asmSymbolName(identifier) + "_msb"
                        else if (identifier.type.isSplitWordArray)
                            asmgen.asmSymbolName(identifier) + "_lsb"  // the _lsb split array comes first in memory
                        else
                            asmgen.asmSymbolName(identifier)
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
            is PtBuiltinFunctionCall -> assignBuiltinFunctionCall(assign.target, value)
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
                            asmgen.loadIndirectWord(zpPtrVar, offset)
                            asmgen.assignRegister(RegisterOrPair.AY, assign.target)
                        }
                        dt.isFloat -> {
                            asmgen.loadIndirectFloat(zpPtrVar, offset)
                            asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
                        }
                        dt.isLong -> {
                            TODO("read long")
                        }
                        else -> throw AssemblyError("unsupported dereference type ${dt} ${value.position}")
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
            else -> throw AssemblyError("weird assignment value type $value")
        }
    }

    private fun assignPrefixExpr(assign: AsmAssignment, value: PtPrefix, scope: IPtSubroutine?) {
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

    private fun assignBuiltinFunctionCall(target: AsmAssignTarget, value: PtBuiltinFunctionCall) {
        val returnDt = asmgen.translateBuiltinFunctionCallExpression(value, target.register)
        if(target.register==null) {
            // still need to assign the result to the target variable/etc.
            when {
                returnDt?.isByteOrBool==true -> assignRegisterByte(target, CpuRegister.A, returnDt.isSigned, false)            // function's byte result is in A
                returnDt?.isWord==true -> assignRegisterpairWord(target, RegisterOrPair.AY)    // function's word result is in AY
                returnDt==BaseDataType.STR -> {
                    val targetDt = target.datatype
                    when {
                        targetDt.isString -> {
                            asmgen.out("""
                                        tax
                                        lda  #<${target.asmVarname}
                                        sta  P8ZP_SCRATCH_W1
                                        lda  #>${target.asmVarname}
                                        sta  P8ZP_SCRATCH_W1+1
                                        txa
                                        jsr  prog8_lib.strcpy""")
                        }
                        targetDt.isUnsignedWord -> assignRegisterpairWord(target, RegisterOrPair.AY)
                        else -> throw AssemblyError("str return value type mismatch with target")
                    }
                }
                returnDt== BaseDataType.LONG -> {
                    // longs are in R14:R15 (r14=lsw, r15=msw)
                    assignRegisterLong(target, RegisterOrPair.R14R15_32)
                }
                returnDt==BaseDataType.FLOAT -> {
                    // float result from function sits in FAC1
                    assignFAC1float(target)
                }
                else -> throw AssemblyError("weird result type")
            }
        }
    }

    private fun assignFunctionCall(assign: AsmAssignment, value: PtFunctionCall) {
        val symbol = asmgen.symbolTable.lookup(value.name)
        val sub = symbol!!.astNode as IPtSubroutine
        asmgen.translateFunctionCall(value)
        if(sub is PtSub && sub.signature.returns.size>1) {
            // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
            // (this allows unencumbered use of many Rx registers if you don't return that many values)
            val returnRegs = sub.returnsWhatWhere()
            assign.targets.zip(returnRegs).forEach { target ->
                if(target.first.kind != TargetStorageKind.VOID) {
                    asmgen.assignRegister(target.second.first.registerOrPair!!, target.first)
                }
            }
        } else {
            val target = assign.target
            val returnValue = sub.returnsWhatWhere().singleOrNull { it.first.registerOrPair!=null } ?: sub.returnsWhatWhere().single { it.first.statusflag!=null }
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
                        in combinedLongRegisters -> assignVirtualRegister(target, returnValue.first.registerOrPair!!)
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

    private fun assignPrefixedExpressionToArrayElt(assign: AsmAssignment, scope: IPtSubroutine?) {
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
                require(register in combinedLongRegisters)
                assignRegisterLong(target, register)
            }
            else -> throw AssemblyError("expected byte or word")
        }
    }

    private fun attemptAssignOptimizedBinexpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        val translatedOk = when (expr.operator) {
            in ComparisonOperators -> optimizedComparison(expr, assign)
            in BitwiseOperators -> optimizedBitwiseExpr(expr, assign.target)
            in LogicalOperators -> optimizedLogicalExpr(expr, assign.target)
            "+", "-" -> optimizedPlusMinExpr(expr, assign.target)
            "<<", ">>" -> optimizedBitshiftExpr(expr, assign.target)
            "*" -> optimizedMultiplyExpr(expr, assign.target)
            "/" -> optimizedDivideExpr(expr, assign.target)
            "%" -> optimizedRemainderExpr(expr, assign.target)
            else -> false
        }

        return if(translatedOk)
            true
        else
            anyExprGen.assignAnyExpressionUsingStack(expr, assign)
    }

    private fun optimizedComparison(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        if(expr.right.asConstInteger() == 0) {
            if(expr.operator == "==" || expr.operator=="!=") {
                if (assign.target.datatype.isByteOrBool) {
                    if(attemptAssignToByteCompareZero(expr, assign)) return true
                } else {
                    // do nothing, this is handled by a type cast.
                }
            }
        }

        if(expr.left.type.isUnsignedByte) {
            if(expr.operator=="<") {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                when(val right = expr.right) {
                    is PtIdentifier -> {
                        asmgen.out("""
                            cmp  ${right.name}
                            rol  a
                            and  #1
                            eor  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    is PtMemoryByte -> {
                        val addr = right.address as? PtNumber
                        if(addr!=null) {
                            asmgen.out("""
                                cmp  ${addr.number.toHex()}
                                rol  a
                                and  #1
                                eor  #1""")
                            assignRegisterByte(assign.target, CpuRegister.A, false, false)
                            return true
                        }
                        if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            val ptrvar = right.address as? PtIdentifier
                            if (ptrvar != null && asmgen.isZpVar(ptrvar)) {
                                asmgen.out("""
                                    cmp  (${ptrvar.name})
                                    rol  a
                                    and  #1
                                    eor  #1""")
                                assignRegisterByte(assign.target, CpuRegister.A, false, false)
                                return true
                            }
                        }
                    }
                    is PtNumber -> {
                        asmgen.out("""
                            cmp  #${right.number.toInt()}
                            rol  a
                            and  #1
                            eor  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else -> { /* not optimizable */ }
                }
            }
            else if(expr.operator==">=") {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                when(val right = expr.right) {
                    is PtIdentifier -> {
                        asmgen.out("""
                            cmp  ${right.name}
                            rol  a
                            and  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    is PtMemoryByte -> {
                        val addr = right.address as? PtNumber
                        if(addr!=null) {
                            asmgen.out("""
                                cmp  ${addr.number.toHex()}
                                rol  a
                                and  #1""")
                            assignRegisterByte(assign.target, CpuRegister.A, false, false)
                            return true
                        }
                        if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            val ptrvar = right.address as? PtIdentifier
                            if (ptrvar != null && asmgen.isZpVar(ptrvar)) {
                                asmgen.out("""
                                    cmp  (${ptrvar.name})
                                    rol  a
                                    and  #1""")
                                assignRegisterByte(assign.target, CpuRegister.A, false, false)
                                return true
                            }
                        }
                    }
                    is PtNumber -> {
                        asmgen.out("""
                            cmp  #${right.number.toInt()}
                            rol  a
                            and  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else -> { /* not optimizable */ }
                }
            }
        }

        // b = v > 99  -->  b=false ,  if v>99  b=true
        val targetReg=assign.target.register
        if(targetReg!=null) {
            // a register as target should be handled slightly differently to avoid overwriting the value
            val ifPart = PtNodeGroup()
            val elsePart = PtNodeGroup()
            val reg = when(targetReg) {
                RegisterOrPair.A -> "a"
                RegisterOrPair.X -> "x"
                RegisterOrPair.Y -> "y"
                else -> TODO("comparison to word register  ${expr.position}")
            }
            val assignTrue = PtInlineAssembly("\tld${reg}  #1", false, assign.target.position)
            val assignFalse = PtInlineAssembly("\tld${reg}  #0", false, assign.target.position)
            ifPart.add(assignTrue)
            elsePart.add(assignFalse)
            val ifelse = PtIfElse(assign.position)
            val exprClone: PtBinaryExpression
            if(!asmgen.isTargetCpu(CpuType.VIRTUAL)
                && (expr.operator==">" || expr.operator=="<=")
                && expr.right.type.isWord) {
                // word X>Y -> X<Y, X<=Y -> Y>=X  , easier to do in 6502  (codegen also expects these to no longe exist!)
                exprClone = PtBinaryExpression(if(expr.operator==">") "<" else ">=", expr.type, expr.position)
                exprClone.children.add(expr.children[1]) // doesn't seem to need a deep clone
                exprClone.children.add(expr.children[0]) // doesn't seem to need a deep clone
            } else {
                exprClone = PtBinaryExpression(expr.operator, expr.type, expr.position)
                exprClone.children.add(expr.children[0]) // doesn't seem to need a deep clone
                exprClone.children.add(expr.children[1]) // doesn't seem to need a deep clone
            }
            ifelse.add(exprClone)
            ifelse.add(ifPart)
            ifelse.add(elsePart)
            ifelse.parent = expr.parent
            asmgen.translate(ifelse)
            return true
        }
        val target = assign.target.origAstTarget
        assignConstantByte(assign.target, 0)
        val ifPart = PtNodeGroup()
        val assignTrue: PtNode
        if(target!=null) {
            // set target to true
            assignTrue = PtAssignment(assign.position)
            assignTrue.add(target)
            assignTrue.add(PtNumber.fromBoolean(true, assign.position))
        } else {
            when(assign.target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(assign.target.datatype.isWord) {
                        assignTrue = if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                            PtInlineAssembly("""  
  lda  #1
  sta  ${assign.target.asmVarname}
  stz  ${assign.target.asmVarname}+1""", false, assign.target.position)
                        } else {
                            PtInlineAssembly("""
  lda  #1
  sta  ${assign.target.asmVarname}
  lda  #0
  sta  ${assign.target.asmVarname}+1""", false, assign.target.position)
                        }
                    } else {
                        assignTrue = PtInlineAssembly("\tlda  #1\n  sta  ${assign.target.asmVarname}", false, assign.target.position)
                    }
                }
                TargetStorageKind.MEMORY -> {
                    val tgt = PtAssignTarget(false, assign.target.position)
                    val targetmem = assign.target.memory!!
                    val mem = PtMemoryByte(targetmem.position)
                    mem.add(targetmem.address)
                    tgt.add(mem)
                    assignTrue = PtAssignment(assign.position)
                    assignTrue.add(tgt)
                    assignTrue.add(PtNumber.fromBoolean(true, assign.position))
                }
                TargetStorageKind.ARRAY -> {
                    val deref = assign.target.array!!.pointerderef
                    if(deref!=null) {
                        TODO("array indexed pointer deref ${assign.position}")
                    } else {
                        val tgt = PtAssignTarget(false, assign.target.position)
                        val targetarray = assign.target.array!!
                        val array = PtArrayIndexer(assign.target.datatype, targetarray.position)

                        val targetArrayVar = targetarray.variable
                        if (targetArrayVar == null) {
                            TODO("optimized comparison on pointer ${targetarray.position}")
                        } else {
                            array.add(targetArrayVar)
                            array.add(targetarray.index)
                            tgt.add(array)
                            assignTrue = PtAssignment(assign.position)
                            assignTrue.add(tgt)
                            assignTrue.add(PtNumber.fromBoolean(true, assign.position))
                        }
                    }
                }
                TargetStorageKind.POINTER -> TODO("optimized comparison for pointer-deref $expr.position")
                TargetStorageKind.REGISTER -> { /* handled earlier */ return true }
                TargetStorageKind.VOID -> { /* do nothing */ return true }
            }
        }
        ifPart.add(assignTrue)
        val ifelse = PtIfElse(assign.position)
        val exprClone: PtBinaryExpression
        if(!asmgen.isTargetCpu(CpuType.VIRTUAL)
            && (expr.operator==">" || expr.operator=="<=")
            && expr.right.type.isWord) {
            // word X>Y -> X<Y, X<=Y -> Y>=X  , easier to do in 6502  (codegen also expects these to no longe exist!)
            exprClone = PtBinaryExpression(if(expr.operator==">") "<" else ">=", expr.type, expr.position)
            exprClone.children.add(expr.children[1]) // doesn't seem to need a deep clone
            exprClone.children.add(expr.children[0]) // doesn't seem to need a deep clone
        } else {
            exprClone = PtBinaryExpression(expr.operator, expr.type, expr.position)
            exprClone.children.add(expr.children[0]) // doesn't seem to need a deep clone
            exprClone.children.add(expr.children[1]) // doesn't seem to need a deep clone
        }
        ifelse.add(exprClone)
        ifelse.add(ifPart)
        ifelse.add(PtNodeGroup())
        ifelse.parent = expr.parent
        asmgen.translate(ifelse)
        return true
    }

    internal fun directIntoY(expr: PtExpression): Boolean {
        return when(expr) {
            is PtIdentifier -> true
            is PtIrRegister -> true
            is PtNumber -> true
            is PtBuiltinFunctionCall -> expr.name in arrayOf("lsb", "msb")
            else -> false
        }
    }

    private fun optimizedRemainderExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        when {
            expr.type.isUnsignedByte -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.remainder_ub_asm")
                if(target.register==RegisterOrPair.A)
                    asmgen.out("  cmp  #0")     // fix the status register
                else
                    assignRegisterByte(target, CpuRegister.A, false, true)
                return true
            }
            expr.type.isUnsignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_uw_asm")
                assignVariableWord(target, "P8ZP_SCRATCH_W2", DataType.UWORD)
                return true
            }
            else -> return false
        }
    }

    private fun optimizedDivideExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        // replacing division by shifting is done in an optimizer step.
        when {
            expr.type.isUnsignedByte -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.divmod_ub_asm")
                assignRegisterByte(target, CpuRegister.Y, false, true)
                return true
            }
            expr.type.isSignedByte -> {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, true)
                if(!directIntoY(expr.right)) asmgen.out("  pha")
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, true)
                if(!directIntoY(expr.right)) asmgen.out("  pla")
                asmgen.out("  jsr  prog8_math.divmod_b_asm")
                assignRegisterByte(target, CpuRegister.Y, true, true)
                return true
            }
            expr.type.isUnsignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_uw_asm")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            expr.type.isSignedWord -> {
                asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "P8ZP_SCRATCH_W1")
                asmgen.out("  jsr  prog8_math.divmod_w_asm")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            }
            else -> return false
        }
    }

    private fun optimizedMultiplyExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val value = expr.right.asConstInteger()
        if(value==null) {
            when {
                expr.type.isByte -> {
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type.isSigned)
                    if(!directIntoY(expr.right)) asmgen.out("  pha")
                    assignExpressionToRegister(expr.right, RegisterOrPair.Y, expr.type.isSigned)
                    if(!directIntoY(expr.right)) asmgen.out("  pla")
                    asmgen.out("  jsr  prog8_math.multiply_bytes")
                    assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
                expr.type.isWord -> {
                    if(expr.definingBlock()!!.options.veraFxMuls) {
                        // cx16 verafx hardware mul
                        if(expr.right.isSimple()) {
                            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R0, expr.left.type.isSigned)
                            asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R1, expr.left.type.isSigned)
                        } else {
                            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.left.type.isSigned)
                            asmgen.out("  pha")
                            asmgen.saveRegisterStack(CpuRegister.Y, false)
                            asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.R1, expr.left.type.isSigned)
                            asmgen.restoreRegisterStack(CpuRegister.Y, false)
                            asmgen.out("  pla")
                            asmgen.out("  sta  cx16.r0 |  sty  cx16.r0+1")
                        }
                        asmgen.out("  jsr  verafx.muls16")
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    } else {
                        asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "prog8_math.multiply_words.multiplier")
                        asmgen.out("  jsr  prog8_math.multiply_words")
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                    }
                    return true
                }
                else -> return false
            }
        } else {
            if(!expr.right.type.isFloat && (value==0 || value==1))
                throw AssemblyError("multiplication by 0 or 1 should not happen ${expr.position}")

            when {
                expr.type.isByte -> {
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, expr.type.isSigned)
                    if (value in asmgen.optimizedByteMultiplications)
                        asmgen.out("  jsr  prog8_math.mul_byte_${value}")
                    else if(value in powersOfTwoInt) {
                        val shifts = log2(value.toDouble()).toInt()
                        if(shifts>=8) {
                            asmgen.out("  lda  #0")
                        } else {
                            repeat(shifts) { asmgen.out("  asl  a") }
                        }
                    }
                    else
                        asmgen.out("  ldy  #$value |  jsr  prog8_math.multiply_bytes")
                    assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
                expr.type.isWord -> {
                    if (value in asmgen.optimizedWordMultiplications) {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                        asmgen.out("  jsr  prog8_math.mul_word_${value}")
                    }
                    else if(value in powersOfTwoInt) {
                        val shifts = log2(value.toDouble()).toInt()
                        if(shifts>=16) {
                            assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                            asmgen.out("  lda  #0 |  ldy  #0")
                        } else {
                            if(target.kind==TargetStorageKind.VARIABLE && target.datatype.isWord) {
                                assignExpressionToVariable(expr.left, target.asmVarname, target.datatype)
                                repeat(shifts) { asmgen.out("  asl  ${target.asmVarname} |  rol  ${target.asmVarname}+1") }
                                return true
                            } else {
                                assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                                asmgen.out("  sty  P8ZP_SCRATCH_REG")
                                repeat(shifts) { asmgen.out("  asl  a |  rol  P8ZP_SCRATCH_REG") }
                                asmgen.out("  ldy  P8ZP_SCRATCH_REG")
                            }
                        }
                    }
                    else {
                        assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.type.isSigned)
                        if(expr.definingBlock()!!.options.veraFxMuls){
                            // cx16 verafx hardware mul
                            asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "cx16.r1")
                            asmgen.out("""
                                sta  cx16.r0
                                sty  cx16.r0+1
                                jsr  verafx.muls16""")
                        } else {
                            asmgen.assignWordOperandsToAYAndVar(expr.right, expr.left, "prog8_math.multiply_words.multiplier")
                            asmgen.out("  jsr  prog8_math.multiply_words")
                        }
                    }
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                else -> return false
            }
        }
    }

    private fun optimizedBitshiftExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val signed = expr.left.type.isSigned
        val shifts = expr.right.asConstInteger()
        val dt = expr.left.type
        if(shifts==null) {
            // bit shifts with variable shifts
            when {
                expr.right.type.isByte -> {
                    assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                }
                expr.right.type.isWord -> {
                    assignExpressionToRegister(expr.right, RegisterOrPair.AY, false)
                    asmgen.out("""
                        cpy  #0
                        beq  +
                        lda  #127
+""")
                }
                else -> throw AssemblyError("weird shift value type")
            }
            asmgen.out("  pha")
            if(dt.isByte) {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                asmgen.restoreRegisterStack(CpuRegister.Y, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  prog8_math.lsr_byte_A")
                    else
                        asmgen.out("  jsr  prog8_math.lsr_ubyte_A")
                else
                    asmgen.out("  jsr  prog8_math.asl_byte_A")
                assignRegisterByte(target, CpuRegister.A, signed, true)
                return true
            } else if(dt.isWord) {
                assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                asmgen.restoreRegisterStack(CpuRegister.X, true)
                if(expr.operator==">>")
                    if(signed)
                        asmgen.out("  jsr  prog8_math.lsr_word_AY")
                    else
                        asmgen.out("  jsr  prog8_math.lsr_uword_AY")
                else
                    asmgen.out("  jsr  prog8_math.asl_word_AY")
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return true
            } else if(dt.isLong) {

                if(target.kind==TargetStorageKind.VARIABLE) {
                    asmgen.out("  pla")
                    asmgen.assignExpressionTo(expr.left, target)
                    require(expr.right.type.isByte)
                    asmgen.assignExpressionToRegister(expr.right, RegisterOrPair.X)
                    asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname}")
                    if (expr.operator == "<<") {
                        asmgen.out("  jsr  prog8_lib.long_shiftleftX_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_shiftrightX_inplace")
                    }
                    return true
                }

                assignExpressionToRegister(expr.left, RegisterOrPair.R0R1_32, signed)
                asmgen.out("  pla |  sta  P8ZP_SCRATCH_REG")
                augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r0", expr.operator, "P8ZP_SCRATCH_REG")
                assignRegisterLong(target, RegisterOrPair.R0R1_32)
                return true
            }
        }
        else {
            // bit shift with constant value
            if(dt.isByte) {
                assignExpressionToRegister(expr.left, RegisterOrPair.A, signed)
                when (shifts) {
                    in 0..7 -> {
                        if (expr.operator == "<<") {
                            repeat(shifts) {
                                asmgen.out("  asl  a")
                            }
                        } else {
                            if (signed && shifts > 0) {
                                if(shifts==1)
                                    asmgen.out("  cmp  #$80 |  ror  a")
                                else
                                    asmgen.out("  ldy  #$shifts |  jsr  prog8_math.lsr_byte_A")
                            } else {
                                repeat(shifts) {
                                    asmgen.out("  lsr  a")
                                }
                            }
                        }
                        assignRegisterByte(target, CpuRegister.A, signed, true)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldy  #$shifts |  jsr  prog8_math.lsr_byte_A")
                        } else {
                            asmgen.out("  lda  #0")
                        }
                        assignRegisterByte(target, CpuRegister.A, signed, true)
                        return true
                    }
                }
            } else if(dt.isWord) {
                if(shifts==7 && expr.operator == "<<") {
                    // optimized shift left 7 (*128) by swapping the lsb/msb and then doing just one final shift
                    assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                    asmgen.out("""
                        ; shift left 7
                        sty  P8ZP_SCRATCH_REG
                        lsr  P8ZP_SCRATCH_REG
                        ror  a
                        sta  P8ZP_SCRATCH_REG
                        lda  #0
                        ror  a
                        ldy  P8ZP_SCRATCH_REG""")
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }

                assignExpressionToRegister(expr.left, RegisterOrPair.AY, signed)
                when (shifts) {
                    in 0..7 -> {
                        if(expr.operator=="<<") {
                            if(shifts>0) {
                                asmgen.out("  sty  P8ZP_SCRATCH_B1")
                                repeat(shifts) {
                                    asmgen.out("  asl  a |  rol  P8ZP_SCRATCH_B1")
                                }
                                asmgen.out("  ldy  P8ZP_SCRATCH_B1")
                            }
                        } else {
                            if(signed && shifts>0) {
                                if(shifts==1) {
                                    asmgen.out("""
                                        pha
                                        tya
                                        cmp  #$80
                                        ror  a
                                        tay
                                        pla
                                        ror  a""")
                                }
                                else
                                    asmgen.out("  ldx  #$shifts |  jsr  prog8_math.lsr_word_AY")
                            } else {
                                if(shifts>0) {
                                    asmgen.out("  sty  P8ZP_SCRATCH_B1")
                                    repeat(shifts) {
                                        asmgen.out("  lsr  P8ZP_SCRATCH_B1 |  ror  a")
                                    }
                                    asmgen.out("  ldy  P8ZP_SCRATCH_B1")
                                }
                            }
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    in 8..15 -> {
                        if(expr.operator == "<<") {
                            // msb = lsb << (shifts-8),   lsb = 0
                            repeat(shifts-8) {
                                asmgen.out("  asl  a")
                            }
                            asmgen.out("  tay |  lda  #0")
                        } else {
                            asmgen.out("  ldx  #$shifts")
                            if(signed)
                                asmgen.out("  jsr  prog8_math.lsr_word_AY")
                            else
                                asmgen.out("  jsr  prog8_math.lsr_uword_AY")
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    else -> {
                        if(signed && expr.operator==">>") {
                            asmgen.out("  ldx  #$shifts |  jsr  prog8_math.lsr_word_AY")
                        } else {
                            asmgen.out("  lda  #0 |  ldy  #0")
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                }
            } else if(dt.isLong) {

                if(target.kind==TargetStorageKind.VARIABLE) {
                    asmgen.assignExpressionTo(expr.left, target)
                    require(expr.right.type.isByte)
                    asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname}")
                    asmgen.out("  ldx  #$shifts")
                    if (expr.operator == "<<") {
                        asmgen.out("  jsr  prog8_lib.long_shiftleftX_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_shiftrightX_inplace")
                    }
                    return true
                }

                assignExpressionToRegister(expr.left, RegisterOrPair.R0R1_32, signed)
                augmentableAsmGen.inplacemodificationLongWithLiteralval("cx16.r0", expr.operator, shifts)
                assignRegisterLong(target, RegisterOrPair.R0R1_32)
                return true
            }
        }
        return false
    }

    private fun optimizedPlusMinExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        // TODO can this use the target directly as intermediate, when it's a variable?  To save the final assignRegister call.
        val dt = expr.type
        val left = expr.left
        val right = expr.right
        if(dt.isByte) {
            when (right) {
                is PtIdentifier -> {
                    assignExpressionToRegister(left, RegisterOrPair.A, dt.isSigned)
                    val symname = asmgen.asmVariableName(right)
                    if(expr.operator=="+")
                        asmgen.out("  clc |  adc  $symname")
                    else
                        asmgen.out("  sec |  sbc  $symname")
                    assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                    return true
                }
                is PtNumber -> {
                    assignExpressionToRegister(left, RegisterOrPair.A, dt.isSigned)
                    if(right.number==1.0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
                        if (expr.operator == "+")
                            asmgen.out("  inc  a")
                        else
                            asmgen.out("  dec  a")
                    } else {
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  #${right.number.toHex()}")
                        else
                            asmgen.out("  sec |  sbc  #${right.number.toHex()}")
                    }
                    assignRegisterByte(target, CpuRegister.A, dt.isSigned, target.datatype.isWord)
                    return true
                }
                else -> {
                    val leftMemByte = expr.left as? PtMemoryByte
                    val rightMemByte = expr.right as? PtMemoryByte
                    val leftArrayIndexer = expr.left as? PtArrayIndexer
                    val rightArrayIndexer = expr.right as? PtArrayIndexer
                    if(expr.operator=="+" && leftArrayIndexer!=null && leftArrayIndexer.type.isByte && right.type.isByte) {
                        // special optimization for  bytearray[y] + bytevalue :  no need to use a tempvar, just use adc array,y
                        val leftArrayVar = leftArrayIndexer.variable
                        if(leftArrayVar==null) {
                            TODO("optimized plusmin pointer ${leftArrayIndexer.position}")
                        } else {
                            assignExpressionToRegister(right, RegisterOrPair.A, right.type.isSigned)
                            if (!leftArrayIndexer.index.isSimple()) asmgen.out("  pha")
                            asmgen.assignExpressionToRegister(leftArrayIndexer.index, RegisterOrPair.Y)
                            if (!leftArrayIndexer.index.isSimple()) asmgen.out("  pla")
                            val arrayvarname = asmgen.asmSymbolName(leftArrayVar)
                            asmgen.out("  clc |  adc  $arrayvarname,y")
                            assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        }
                    } else if(rightArrayIndexer!=null && rightArrayIndexer.type.isByte && left.type.isByte) {
                        // special optimization for  bytevalue +/- bytearray[y] :  no need to use a tempvar, just use adc array,y or sbc array,y
                        val rightArrayVar = rightArrayIndexer.variable
                        if(rightArrayVar==null) {
                            TODO("optimized plusmin pointer ${rightArrayIndexer.position}")
                        } else {
                            assignExpressionToRegister(left, RegisterOrPair.A, left.type.isSigned)
                            if (!rightArrayIndexer.index.isSimple()) asmgen.out("  pha")
                            asmgen.assignExpressionToRegister(rightArrayIndexer.index, RegisterOrPair.Y)
                            if (!rightArrayIndexer.index.isSimple()) asmgen.out("  pla")
                            val arrayvarname = asmgen.asmSymbolName(rightArrayVar)
                            if (expr.operator == "+")
                                asmgen.out("  clc |  adc  $arrayvarname,y")
                            else
                                asmgen.out("  sec |  sbc  $arrayvarname,y")
                            assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        }
                    } else if(expr.operator=="+" && leftMemByte!=null && right.type.isByte && optimizedPointerIndexPlusMinusByteIntoA(right, "+", leftMemByte)) {
                        assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        return true
                    } else if(rightMemByte!=null && left.type.isByte && optimizedPointerIndexPlusMinusByteIntoA(left, expr.operator, rightMemByte)) {
                        assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                        return true
                    } else {
                        assignExpressionToRegister(left, RegisterOrPair.A, left.type.isSigned)
                        if(directIntoY(right)) {
                            assignExpressionToRegister(right, RegisterOrPair.Y, left.type.isSigned)
                            asmgen.out("  sty  P8ZP_SCRATCH_B1")
                        } else {
                            asmgen.out("  pha")
                            assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", right.type)
                            asmgen.out("  pla")
                        }
                        if (expr.operator == "+")
                            asmgen.out("  clc |  adc  P8ZP_SCRATCH_B1")
                        else
                            asmgen.out("  sec |  sbc  P8ZP_SCRATCH_B1")
                        assignRegisterByte(target, CpuRegister.A, dt.isSigned, true)
                    }
                    return true
                }
            }
        } else if(dt.isWord || dt.isPointer) {

            fun doAddOrSubWordExpr() {
                if(expr.operator=="+" && expr.left.type.isWord && expr.left is PtIdentifier) {
                    val symname = asmgen.asmVariableName(expr.left as PtIdentifier)
                    assignExpressionToRegister(expr.right, RegisterOrPair.AY, expr.right.type.isSigned)
                    asmgen.out("""
                            clc
                            adc  $symname
                            tax
                            tya
                            adc  $symname+1
                            tay
                            txa""")
                }
                else if(expr.operator=="-" && expr.right.type.isWord && expr.right is PtIdentifier) {
                    val symname = asmgen.asmVariableName(expr.right as PtIdentifier)
                    assignExpressionToRegister(expr.left, RegisterOrPair.AY, expr.left.type.isSigned)
                    asmgen.out("""
                            sec
                            sbc  $symname
                            tax
                            tya
                            sbc  $symname+1
                            tay
                            txa""")
                } else {
                    asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
                    if(expr.operator=="+")
                        asmgen.out("""
                                    clc
                                    adc  P8ZP_SCRATCH_W1
                                    tax
                                    tya
                                    adc  P8ZP_SCRATCH_W1+1
                                    tay
                                    txa""")
                    else
                        asmgen.out("""
                                    sec
                                    sbc  P8ZP_SCRATCH_W1
                                    tax
                                    tya
                                    sbc  P8ZP_SCRATCH_W1+1
                                    tay
                                    txa""")
                }
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }

            when (right) {
                is PtAddressOf -> {
                    if(right.isFromArrayElement) {
                        TODO("address-of array element at ${right.position}")
                    } else if(right.dereference!=null) {
                        TODO("read &dereference")
                    } else {
                        var symbol = asmgen.asmVariableName(right.identifier!!)
                        if(right.identifier!!.type.isSplitWordArray) {
                            symbol = if(right.isMsbForSplitArray) symbol+"_msb" else symbol+"_lsb"
                        }
                        assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
                        if(expr.operator=="+")
                            asmgen.out("""
                                    clc
                                    adc  #<$symbol
                                    tax
                                    tya
                                    adc  #>$symbol
                                    tay
                                    txa""")
                        else
                            asmgen.out("""
                                    sec
                                    sbc  #<$symbol
                                    tax
                                    tya
                                    sbc  #>$symbol
                                    tay
                                    txa""")
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                }
                is PtIdentifier -> {
                    val symname = asmgen.asmVariableName(right)
                    assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
                    if(expr.operator=="+")
                        asmgen.out("""
                                clc
                                adc  $symname
                                tax
                                tya
                                adc  $symname+1
                                tay
                                txa""")
                    else
                        asmgen.out("""
                                sec
                                sbc  $symname
                                tax
                                tya
                                sbc  $symname+1
                                tay
                                txa""")
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtNumber -> {
                    assignExpressionToRegister(left, RegisterOrPair.AY, dt.isSigned)
                    if(right.number==1.0 && asmgen.isTargetCpu(CpuType.CPU65C02)) {
                        if(expr.operator=="+") {
                            asmgen.out("""
                                inc  a
                                bne  +
                                iny
+""")
                        } else {
                            asmgen.out("""
                                dec  a
                                cmp  #255
                                bne  +
                                dey
+""")
                        }
                    } else if(!dt.isSignedWord && right.number.toInt() in 0..255) {
                        if(expr.operator=="+") {
                            asmgen.out("""
                                clc
                                adc  #${right.number.toHex()}
                                bcc  +
                                iny
+""")                   } else if(expr.operator=="-") {
                            asmgen.out("""
                                sec
                                sbc  #${right.number.toHex()}
                                bcs  +
                                dey
+""")
                        }
                    } else {
                        if(expr.operator=="+") {
                            asmgen.out("""
                                clc
                                adc  #<${right.number.toHex()}
                                tax
                                tya
                                adc  #>${right.number.toHex()}
                                tay
                                txa""")
                        } else if(expr.operator=="-") {
                            asmgen.out("""
                                sec
                                sbc  #<${right.number.toHex()}
                                tax
                                tya
                                sbc  #>${right.number.toHex()}
                                tay
                                txa""")
                        }
                    }
                    assignRegisterpairWord(target, RegisterOrPair.AY)
                    return true
                }
                is PtTypeCast -> {
                    val castedValue = right.value
                    if(right.type.isWord && castedValue.type.isByte && castedValue is PtIdentifier) {
                        if(right.type.isSigned) {
                            // we need to sign extend, do this via temporary word variable
                            asmgen.assignExpressionToVariable(right, "P8ZP_SCRATCH_W1", DataType.WORD)
                            assignExpressionToRegister(left, RegisterOrPair.AY, left.type.isSigned)
                            if(expr.operator=="+") {
                                asmgen.out("""
                                clc
                                adc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                adc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                            } else if(expr.operator=="-") {
                                asmgen.out("""
                                sec
                                sbc  P8ZP_SCRATCH_W1
                                tax
                                tya
                                sbc  P8ZP_SCRATCH_W1+1
                                tay
                                txa""")
                            }
                        } else {
                            assignExpressionToRegister(left, RegisterOrPair.AY, left.type.isSigned)
                            val castedSymname = asmgen.asmVariableName(castedValue)
                            if (expr.operator == "+")
                                asmgen.out("""
                                    clc
                                    adc  $castedSymname
                                    bcc  +
                                    iny
+"""
                            )
                            else
                                asmgen.out("""
                                    sec
                                    sbc  $castedSymname
                                    bcs  +
                                    dey
+"""
                            )
                        }
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                        return true
                    }
                    doAddOrSubWordExpr()
                    return true
                }
                else -> {
                    doAddOrSubWordExpr()
                    return true
                }
            }
        }
        else if(dt.isLong) {
            return optimizedPlusMinLongExpr(expr, target)
        }
        return false
    }

    private fun optimizedPlusMinLongExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        val left = expr.left
        val right = expr.right
        when(right) {
            is PtIdentifier -> {
                if(target.kind == TargetStorageKind.VARIABLE) {
                    asmgen.assignExpressionTo(left, target)
                    val rightsym = asmgen.asmVariableName(right)
                    asmgen.out("""
                        lda  #<$rightsym
                        ldy  #>$rightsym
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    if (expr.operator == "+") {
                        asmgen.out("  jsr  prog8_lib.long_add_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_sub_inplace")
                    }
                    return true
                } else if(target.kind == TargetStorageKind.REGISTER) {
                    val startreg = target.register!!.startregname()
                    asmgen.assignExpressionTo(left, target)
                    val rightsym = asmgen.asmVariableName(right)
                    asmgen.out(
                        """
                        lda  #<$rightsym
                        ldy  #>$rightsym
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<cx16.$startreg
                        ldy  #>cx16.$startreg"""
                    )
                    if (expr.operator == "+") {
                        asmgen.out("  jsr  prog8_lib.long_add_inplace")
                    } else {
                        asmgen.out("  jsr  prog8_lib.long_sub_inplace")
                    }
                    return true
                } else {
                    TODO("add/subtract long into ${target.kind} at ${target.position} - use simple expressions and temporary variables for now")
                }
            }
            is PtNumber -> {
                val hex = right.number.toInt().toString(16).padStart(8, '0')
                if (target.kind == TargetStorageKind.VARIABLE) {
                    asmgen.assignExpressionTo(left, target)
                    if (expr.operator == "+") {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            clc
                            adc  #$${hex.substring(6,8)}
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            adc  #$${hex.substring(4, 6)}
                            sta  ${target.asmVarname}+1
                            lda  ${target.asmVarname}+2
                            adc  #$${hex.substring(2, 4)}
                            sta  ${target.asmVarname}+2
                            lda  ${target.asmVarname}+3
                            adc  #$${hex.take(2)}
                            sta  ${target.asmVarname}+3""")
                    } else {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            sec
                            sbc  #$${hex.substring(6, 8)}
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            sbc  #$${hex.substring(4, 6)}
                            sta  ${target.asmVarname}+1
                            lda  ${target.asmVarname}+2
                            sbc  #$${hex.substring(2, 4)}
                            sta  ${target.asmVarname}+2
                            lda  ${target.asmVarname}+3
                            sbc  #$${hex.take(2)}
                            sta  ${target.asmVarname}+3""")
                    }
                    return true
                } else {
                    TODO("add/subtract long into ${target.kind} ${target.position} - use simple expressions and temporary variables for now")
                }
            }
            else -> {
                val targetreg = target.register
                if(targetreg==RegisterOrPair.R14R15_32)
                    asmgen.pushLongRegisters(RegisterOrPair.R12R13_32, 1)
                else
                    asmgen.pushLongRegisters(RegisterOrPair.R12R13_32, 2)
                assignExpressionToRegister(expr.left, RegisterOrPair.R14R15_32, expr.left.type.isSigned)
                assignExpressionToRegister(expr.right, RegisterOrPair.R12R13_32, expr.right.type.isSigned)
                augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r14", expr.operator, "cx16.r12")
                assignRegisterLong(target, RegisterOrPair.R14R15_32)
                if(targetreg==RegisterOrPair.R14R15_32)
                    asmgen.popLongRegisters(RegisterOrPair.R12R13_32, 1)
                else
                    asmgen.popLongRegisters(RegisterOrPair.R14R15_32, 2)
                return true
            }
        }
    }

    private fun optimizedPointerIndexPlusMinusByteIntoA(value: PtExpression, operator: String, mem: PtMemoryByte): Boolean {
        // special optimization for  bytevalue +/- pointervar[y]  (actually: bytevalue +/-  @(address) )
        val address = mem.address as? PtBinaryExpression
        if(address is PtBinaryExpression) {
            val constOffset = address.right.asConstInteger()
            // check that the offset is actually a byte (so that it fits in a single register)
            if(constOffset==null && !address.right.type.isByte)
                return false
            if(constOffset!=null && constOffset !in -128..255)
                return false
            val ptrVar = address.left as? PtIdentifier
            if(ptrVar!=null && asmgen.isZpVar(ptrVar)) {
                assignExpressionToRegister(value, RegisterOrPair.A, false)
                val pointername = asmgen.asmVariableName(ptrVar)
                if (constOffset != null) {
                    // we have value + @(zpptr + 255), or value - @(zpptr+255).   the offset is always <256.
                    asmgen.out("  ldy  #$constOffset")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                } else if (address.right.type.isByte) {
                    // we have @(ptr + bytevar) ++ , or  @(ptr+bytevar)--
                    asmgen.out("  pha")
                    assignExpressionToRegister(address.right, RegisterOrPair.Y, false)
                    asmgen.out("  pla")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                } else if ((address.right as? PtTypeCast)?.value?.type?.isByte==true) {
                    // we have @(ptr + bytevar as uword) ++ , or  @(ptr+bytevar as uword)--
                    asmgen.out("  pha")
                    assignExpressionToRegister((address.right as PtTypeCast).value, RegisterOrPair.Y, false)
                    asmgen.out("  pla")
                    if (operator == "+")
                        asmgen.out("  clc |  adc  ($pointername),y")
                    else
                        asmgen.out("  sec |  sbc  ($pointername),y")
                }
                return true
            }
        }
        return false
    }

    private fun optimizedBitwiseExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {
        if (expr.left.type.isByte && expr.right.type.isByte) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandByte(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandByte(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }
            val rightArray = expr.right as? PtArrayIndexer
            if(rightArray!=null) {
                val rightArrayVar = rightArray.variable
                if(rightArrayVar==null) {
                    TODO("optimized bitwise assignment from not a variable ${rightArray.position}")
                    return false
                }

                val constIndex = rightArray.index.asConstInteger()
                if(constIndex!=null) {
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    val valueVarname = "${asmgen.asmSymbolName(rightArrayVar)} + ${program.memsizer.memorySize(rightArray.type, constIndex)}"
                    when(expr.operator) {
                        "&" -> asmgen.out("  and  $valueVarname")
                        "|" -> asmgen.out("  ora  $valueVarname")
                        "^" -> asmgen.out("  eor  $valueVarname")
                        else -> throw AssemblyError("invalid logical operator")
                    }
                    assignRegisterByte(target, CpuRegister.A, false, true)
                    return true
                }
            }

            assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
            if(directIntoY(expr.right)) {
                assignExpressionToRegister(expr.right, RegisterOrPair.Y, false)
                asmgen.out("  sty  P8ZP_SCRATCH_B1")
            } else {
                asmgen.out("  pha")
                assignExpressionToVariable(expr.right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                asmgen.out("  pla")
            }
            when (expr.operator) {
                "&" -> asmgen.out("  and  P8ZP_SCRATCH_B1")
                "|" -> asmgen.out("  ora  P8ZP_SCRATCH_B1")
                "^" -> asmgen.out("  eor  P8ZP_SCRATCH_B1")
                else -> throw AssemblyError("invalid bitwise operator")
            }
            assignRegisterByte(target, CpuRegister.A, false, true)
            return true
        }
        else if (expr.left.type.isWord && expr.right.type.isWord) {
            if (expr.right.isSimple()) {
                if (expr.right is PtNumber || expr.right is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandWord(target, expr.left, expr.operator, expr.right)
                    return true
                }
                else if (expr.left is PtNumber || expr.left is PtIdentifier) {
                    assignBitwiseWithSimpleRightOperandWord(target, expr.right, expr.operator, expr.left)
                    return true
                }
            }
            asmgen.assignWordOperandsToAYAndVar(expr.left, expr.right, "P8ZP_SCRATCH_W1")
            when (expr.operator) {
                "&" -> asmgen.out("  and  P8ZP_SCRATCH_W1 |  tax |  tya |  and  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "|" -> asmgen.out("  ora  P8ZP_SCRATCH_W1 |  tax |  tya |  ora  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                "^" -> asmgen.out("  eor  P8ZP_SCRATCH_W1 |  tax |  tya |  eor  P8ZP_SCRATCH_W1+1 |  tay |  txa")
                else -> throw AssemblyError("invalid bitwise operator")
            }
            assignRegisterpairWord(target, RegisterOrPair.AY)
            return true
        }
        else if (expr.left.type.isLong && expr.right.type.isLong) {

            if(target.kind == TargetStorageKind.VARIABLE) {
                if(expr.left is PtIdentifier) {
                    asmgen.assignExpressionTo(expr.right, target)
                    val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                    asmgen.out("""
                        lda  #<$varname
                        ldy  #>$varname
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    when(expr.operator) {
                        "|" -> asmgen.out("  jsr  prog8_lib.long_or_inplace")
                        "&" -> asmgen.out("  jsr  prog8_lib.long_and_inplace")
                        "^" -> asmgen.out("  jsr  prog8_lib.long_xor_inplace")
                        else -> throw AssemblyError("wrong bitwise operator")
                    }
                    return true
                } else if(expr.right is PtIdentifier) {
                    asmgen.assignExpressionTo(expr.left, target)
                    val varname = asmgen.asmVariableName(expr.right as PtIdentifier)
                    asmgen.out("""
                        lda  #<$varname
                        ldy  #>$varname
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<${target.asmVarname}
                        ldy  #>${target.asmVarname}""")
                    when(expr.operator) {
                        "|" -> asmgen.out("  jsr  prog8_lib.long_or_inplace")
                        "&" -> asmgen.out("  jsr  prog8_lib.long_and_inplace")
                        "^" -> asmgen.out("  jsr  prog8_lib.long_xor_inplace")
                        else -> throw AssemblyError("wrong bitwise operator")
                    }
                    return true
                }
            }

            val targetreg = target.register
            if(targetreg!=RegisterOrPair.R12R13_32) {
                asmgen.pushLongRegisters(RegisterOrPair.R12R13_32, 1)
            }
            asmgen.assignExpressionToRegister(expr.left, RegisterOrPair.R12R13_32, expr.left.type.isSigned)
            val constval = expr.right.asConstInteger()
            val varname = (expr.right as? PtIdentifier)?.name
            if(constval!=null)
                augmentableAsmGen.inplacemodificationLongWithLiteralval("cx16.r12", expr.operator, constval)
            else if(varname!=null)
                augmentableAsmGen.inplacemodificationLongWithVariable("cx16.r12", expr.operator, varname)
            else
                augmentableAsmGen.inplacemodificationLongWithExpression("cx16.r12", expr.operator, expr.right)
            assignRegisterLong(target, RegisterOrPair.R12R13_32)
            if(targetreg!=RegisterOrPair.R12R13_32) {
                asmgen.popLongRegisters(RegisterOrPair.R12R13_32, 1)
            }
            return true
        }
        return false
    }

    internal fun optimizedLogicalExpr(expr: PtBinaryExpression, target: AsmAssignTarget): Boolean {

        fun swapOperands(): Boolean =
            if(expr.right is PtIdentifier || expr.right is PtMemoryByte)
                false
            else
                expr.left is PtIdentifier || expr.left is PtMemoryByte

        fun assignResultIntoA(left: PtExpression, operator: String, right: PtExpression) {
            // non short-circuit evaluation it is *likely* shorter and faster because of the simple operands.

            fun assignViaScratch() {
                if(directIntoY(right)) {
                    assignExpressionToRegister(right, RegisterOrPair.Y, false)
                    asmgen.out("  sty  P8ZP_SCRATCH_B1")
                } else {
                    asmgen.out("  pha")
                    assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
                    asmgen.out("  pla")
                }
                when (operator) {
                    "and" -> asmgen.out("  and  P8ZP_SCRATCH_B1")
                    "or" -> asmgen.out("  ora  P8ZP_SCRATCH_B1")
                    "xor" -> asmgen.out("  eor  P8ZP_SCRATCH_B1")
                    else -> throw AssemblyError("invalid logical operator")
                }
            }

            assignExpressionToRegister(left, RegisterOrPair.A, false)
            when(right) {
                is PtBool -> throw AssemblyError("bool literal in logical expr should have been optimized away")
                is PtIdentifier -> {
                    val varname = asmgen.asmVariableName(right)
                    when (operator) {
                        "and" -> asmgen.out("  and  $varname")
                        "or" -> asmgen.out("  ora  $varname")
                        "xor" -> asmgen.out("  eor  $varname")
                        else -> throw AssemblyError("invalid logical operator")
                    }
                }
                is PtMemoryByte -> {
                    val constAddress = right.address.asConstInteger()
                    if(constAddress!=null) {
                        when (operator) {
                            "and" -> asmgen.out("  and  ${constAddress.toHex()}")
                            "or" -> asmgen.out("  ora  ${constAddress.toHex()}")
                            "xor" -> asmgen.out("  eor  ${constAddress.toHex()}")
                            else -> throw AssemblyError("invalid logical operator")
                        }
                    }
                    else assignViaScratch()
                }
                is PtArrayIndexer -> {
                    val constIndex = right.index.asConstInteger()
                    if(constIndex!=null) {
                        val rightArrayVar = right.variable
                        if(rightArrayVar==null) {
                            TODO("assign result into A pointer ${right.position}")
                        } else {
                            val valueVarname = "${asmgen.asmSymbolName(rightArrayVar)} + ${program.memsizer.memorySize(right.type, constIndex)}"
                            when(operator) {
                                "and" -> asmgen.out("  and  $valueVarname")
                                "or" -> asmgen.out("  ora  $valueVarname")
                                "xor" -> asmgen.out("  eor  $valueVarname")
                                else -> throw AssemblyError("invalid logical operator")
                            }
                        }
                    }
                    else assignViaScratch()
                }
                else -> assignViaScratch()
            }
        }

        fun requiresCmp(expr: PtExpression) =
            when (expr) {
                is PtFunctionCall -> {
                    val function = asmgen.symbolTable.lookup(expr.name)
                    function is StExtSub        // don't assume the extsub/asmsub has set the cpu flags correctly on exit, add an explicit cmp
                }
                is PtBuiltinFunctionCall -> true
                is PtIfExpression -> true
                else -> false
            }

        if(!expr.right.isSimple() && expr.operator!="xor") {
            // shortcircuit evaluation into A
            val shortcutLabel = asmgen.makeLabel("shortcut")
            when (expr.operator) {
                "and" -> {
                    // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    if(requiresCmp(expr.left))
                        asmgen.out("  cmp  #0")
                    asmgen.out("  beq  $shortcutLabel")
                    assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                    if(requiresCmp(expr.right))
                        asmgen.out("  cmp  #0")
                    asmgen.out(shortcutLabel)
                }
                "or" -> {
                    // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
                    assignExpressionToRegister(expr.left, RegisterOrPair.A, false)
                    if(requiresCmp(expr.left))
                        asmgen.out("  cmp  #0")
                    asmgen.out("  bne  $shortcutLabel")
                    assignExpressionToRegister(expr.right, RegisterOrPair.A, false)
                    if(requiresCmp(expr.right))
                        asmgen.out("  cmp  #0")
                    asmgen.out(shortcutLabel)
                }
                else -> throw AssemblyError("invalid logical operator")
            }
        } else if(swapOperands()) {
            // non short-circuit evaluation is *likely* shorter and faster because of the simple operands.
            assignResultIntoA(expr.right, expr.operator, expr.left)
        } else {
            // non short-circuit evaluation is *likely* shorter and faster because of the simple operands.
            assignResultIntoA(expr.left, expr.operator, expr.right)
        }

        assignRegisterByte(target, CpuRegister.A, false, true)
        return true
    }

    private fun assignBitwiseWithSimpleRightOperandByte(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignExpressionToRegister(left, RegisterOrPair.A, false)
        val operand = when(right) {
            is PtNumber -> "#${right.number.toHex()}"
            is PtIdentifier -> asmgen.asmSymbolName(right)
            else -> throw AssemblyError("wrong right operand type")
        }
        when (operator) {
            "&" -> asmgen.out("  and  $operand")
            "|" -> asmgen.out("  ora  $operand")
            "^" -> asmgen.out("  eor  $operand")
            else -> throw AssemblyError("invalid operator")
        }
        assignRegisterByte(target, CpuRegister.A, false, true)
    }

    private fun assignBitwiseWithSimpleRightOperandWord(target: AsmAssignTarget, left: PtExpression, operator: String, right: PtExpression) {
        assignExpressionToRegister(left, RegisterOrPair.AY, false)
        when(right) {
            is PtNumber -> {
                val value = right.number.toInt()
                when (operator) {
                    "&" -> {
                        when {
                            value == 0 -> asmgen.out("  lda  #0 |  tay")
                            value == 0x00ff -> asmgen.out("  lda  #0")
                            value == 0xff00 -> asmgen.out("  ldy  #0")
                            value and 255 == 0 -> asmgen.out("  tya |  and  #>$value |  tay |  lda  #0")
                            value < 0x0100 -> asmgen.out("  and  #<$value |  ldy  #0")
                            else -> asmgen.out("  and  #<$value |  tax |  tya |  and  #>$value |  tay |  txa")
                        }
                    }
                    "|" -> {
                        when {
                            value == 0 -> {}
                            value and 255 == 0 -> asmgen.out("  tax |  tya |  ora  #>$value |  tay |  txa")
                            value < 0x0100 -> asmgen.out("  ora  #$value")
                            else -> asmgen.out("  ora  #<$value |  tax |  tya |  ora  #>$value |  tay |  txa")
                        }
                    }
                    "^" -> {
                        when {
                            value == 0 -> {}
                            value and 255 == 0 -> asmgen.out("  tax |  tya |  eor  #>$value |  tay |  txa")
                            value < 0x0100 -> asmgen.out("  eor  #$value")
                            else -> asmgen.out("  eor  #<$value |  tax |  tya |  eor  #>$value |  tay |  txa")
                        }
                    }
                    else -> throw AssemblyError("invalid bitwise operator")
                }
            }
            is PtIdentifier -> {
                val name = asmgen.asmSymbolName(right)
                when (operator) {
                    "&" -> asmgen.out("  and  $name |  tax |  tya |  and  $name+1 |  tay |  txa")
                    "|" -> asmgen.out("  ora  $name |  tax |  tya |  ora  $name+1 |  tay |  txa")
                    "^" -> asmgen.out("  eor  $name |  tax |  tya |  eor  $name+1 |  tay |  txa")
                    else -> throw AssemblyError("invalid bitwise operator")
                }
            }
            else -> throw AssemblyError("wrong right operand type")
        }
        assignRegisterpairWord(target, RegisterOrPair.AY)
    }

    private fun attemptAssignToByteCompareZero(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
        when (expr.operator) {
            "==" -> {
                val dt = expr.left.type
                when {
                    dt.isBool || dt.isByte -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.A, dt.isSigned)
                        asmgen.out("""
                            cmp  #0
                            beq  +
                            lda  #1
+                           eor  #1""")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isWord || dt.isPointer -> {
                        if(expr.left is PtIdentifier) {
                            val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                            asmgen.out("""
                                lda  $varname
                                ora  $varname+1
                                beq  +
                                lda  #1
+                               eor  #1""")
                        } else {
                            assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt.isSigned)
                            asmgen.out("""
                                sty  P8ZP_SCRATCH_B1
                                ora  P8ZP_SCRATCH_B1
                                beq  +
                                lda  #1
+                               eor  #1""")
                        }
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isFloat -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN |  and  #1 |  eor  #1")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            "!=" -> {
                val dt = expr.left.type
                when {
                    dt.isBool || dt.isByte -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.A, dt.isSigned)
                        asmgen.out("  beq  + |  lda  #1")
                        asmgen.out("+")
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isWord || dt.isPointer -> {
                        if(expr.left is PtIdentifier) {
                            val varname = asmgen.asmVariableName(expr.left as PtIdentifier)
                            asmgen.out("""
                                lda  $varname
                                ora  $varname+1
                                beq  +
                                lda  #1
+""")
                        } else {
                            assignExpressionToRegister(expr.left, RegisterOrPair.AY, dt.isSigned)
                            asmgen.out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                            asmgen.out("  beq  + |  lda  #1")
                            asmgen.out("+")
                        }
                        assignRegisterByte(assign.target, CpuRegister.A, false, false)
                        return true
                    }
                    dt.isFloat -> {
                        assignExpressionToRegister(expr.left, RegisterOrPair.FAC1, true)
                        asmgen.out("  jsr  floats.SIGN")
                        assignRegisterByte(assign.target, CpuRegister.A, true, false)
                        return true
                    }
                    else->{
                        return false
                    }
                }
            }
            else -> return false
        }
    }

    private fun containmentCheckIntoA(containment: PtContainmentCheck) {
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
                if(dt.isSplitWordArray) {
                    assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W2"), symbolName+"_lsb", false, null, null)
                    asmgen.out("  ldy  #$numElements")
                    asmgen.out("  jsr  prog8_lib.containment_splitwordarray")
                } else {
                    assignAddressOf(AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UWORD, containment.definingISub(), containment.position, "P8ZP_SCRATCH_W2"), symbolName, false, null, null)
                    asmgen.out("  ldy  #$numElements")
                    asmgen.out("  jsr  prog8_lib.containment_linearwordarray")
                }
            }
            else -> throw AssemblyError("invalid dt")
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

    private fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: PtExpression, origTypeCastExpression: PtTypeCast) {
        val valueDt = value.type
        if(valueDt==targetDt)
            throw AssemblyError("type cast to identical dt should have been removed")

        when(value) {
            is PtIdentifier -> {
                if(targetDt.isWord) {
                    if(valueDt.isUnsignedByte || valueDt.isBool) {
                        assignVariableUByteIntoWord(target, value)
                        return
                    }
                    if(valueDt.isSignedByte) {
                        assignVariableByteIntoWord(target, value)
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
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                    }

                    when (value.address) {
                        is PtNumber -> {
                            val address = (value.address as PtNumber).number.toUInt()
                            assignMemoryByteIntoWord(target, address, null)
                        }
                        is PtIdentifier -> {
                            assignMemoryByteIntoWord(target, null, value.address as PtIdentifier)
                        }
                        is PtBinaryExpression -> {
                            val addrExpr = value.address as PtBinaryExpression
                            if(asmgen.tryOptimizedPointerAccessWithA(addrExpr, false)) {
                                asmgen.out("  ldy  #0")
                                assignRegisterpairWord(target, RegisterOrPair.AY)
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
                    return assignConstantWord(target, value.number.toInt())
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
                        assignExpression(assignDirect, target.scope)
                    } else {
                        TODO("assign bool to non-integer type $targetDt")
                    }
                }
                valueDt.isByte -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, valueDt.isSigned)
                    assignTypeCastedRegisters(target.asmVarname, targetDt.base, RegisterOrPair.A, valueDt.base)
                }
                valueDt.isLong -> {
                    TODO("assign typecasted long to $targetDt ${value.position}")
                }
                valueDt.isWord || valueDt.isPointer -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, valueDt.isSigned)
                    assignTypeCastedRegisters(target.asmVarname, targetDt.base, RegisterOrPair.AY, valueDt.base)
                }
                valueDt.isFloat -> {
                    assignExpressionToRegister(value, RegisterOrPair.FAC1, true)
                    assignTypeCastedFloatFAC1(target.asmVarname, targetDt.base)
                }
                valueDt.isPassByRef -> {
                    // str/array value cast (most likely to UWORD, take address-of)
                    assignExpressionToVariable(value, target.asmVarname, targetDt)
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
                    return assignExpressionToRegister(value, target.register, valueDt.isSigned)
                }
                RegisterOrPair.AX,
                RegisterOrPair.AY,
                RegisterOrPair.XY,
                in Cx16VirtualRegisters -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, false)
                    assignRegisterByte(target, CpuRegister.A, valueDt.isSigned, true)
                    return
                }
                in combinedLongRegisters -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, false)
                    assignRegisterByte(target, CpuRegister.A, valueDt.isSigned, true)
                    return
                }
                else -> {}
            }
        } else if(valueDt.isUnsignedWord) {
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
                    return assignExpressionToRegister(value, target.register!!, targetDt.isSigned)
                }
                in combinedLongRegisters -> TODO("assign wprd to long reg ${value.position}")
                else -> {}
            }
        }

        if(target.kind==TargetStorageKind.REGISTER) {
            if(valueDt.isFloat && !target.datatype.isFloat) {
                // have to typecast the float number on the fly down to an integer
                assignExpressionToRegister(value, RegisterOrPair.FAC1, targetDt.isSigned)
                assignTypeCastedFloatFAC1("P8ZP_SCRATCH_W1", targetDt.base)
                assignVariableToRegister("P8ZP_SCRATCH_W1", target.register!!, targetDt.isSigned, origTypeCastExpression.definingISub(), target.position)
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
                        assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isByteOrBool && targetDt.isByte) {
                        // byte to byte, just assign
                        assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isByteOrBool && targetDt.isWord) {
                        // byte to word, just assign
                        assignExpressionToRegister(value, target.register!!, valueDt.isSigned)
                    } else if(valueDt.isLong && targetDt.isByte) {
                        // long to byte, just take the lsb
                        assignCastLongToByte(value, target)
                    } else if(valueDt.isLong && targetDt.isWord) {
                        // long to word, just take the lsw
                        assignCastViaLswFunc(value, target)
                    } else
                        throw AssemblyError("can't cast $valueDt to $targetDt, this should have been checked in the astchecker")
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
//                    assignExpressionToRegister(value, RegisterOrPair.AY, targetDt==BaseDataType.WORD)
//                    assignTypeCastedRegisters(target.asmVarname, targetDt, RegisterOrPair.AY, targetDt)
//                    return
//                }
                TargetStorageKind.ARRAY -> {
                    // byte to word, just assign to registers first, then assign into array
                    assignExpressionToRegister(value, RegisterOrPair.AY, targetDt.isSigned)
                    val deref = target.array!!.pointerderef
                    if(deref!=null)
                        pointergen.assignWordReg(IndexedPtrTarget(target), RegisterOrPair.AY)
                    else
                        assignRegisterpairWord(target, RegisterOrPair.AY)
                    return
                }
                TargetStorageKind.REGISTER -> {
                    // byte to word or long, just assign to registers
                    assignExpressionToRegister(value, target.register!!, targetDt.isSigned)
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
                    assignExpressionToRegister(value, RegisterOrPair.Y, false)
                    asmgen.out("  jsr  floats.FREADUY")
                }
                valueDt.isSignedByte -> {
                    assignExpressionToRegister(value, RegisterOrPair.A, true)
                    asmgen.out("  jsr  floats.FREADSA")
                }
                valueDt.isUnsignedWord -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, false)
                    asmgen.out("  jsr  floats.GIVUAYFAY")
                }
                valueDt.isSignedWord -> {
                    assignExpressionToRegister(value, RegisterOrPair.AY, true)
                    asmgen.out("  jsr  floats.GIVAYFAY")
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
            assignExpressionToRegister(value, RegisterOrPair.AY, false)
            assignRegisterpairWord(target, RegisterOrPair.AY)
            return
        }

        // No more special optimized cases yet. Do the rest via more complex evaluation
        // note: cannot use assignTypeCastedValue because that is ourselves :P
        // NOTE: THIS MAY TURN INTO A STACK OVERFLOW ERROR IF IT CAN'T SIMPLIFY THE TYPECAST..... :-/
        asmgen.assignExpressionTo(origTypeCastExpression, target)
    }

    private fun assignCastLongToByte(value: PtExpression, target: AsmAssignTarget) {
        // long to byte, can't use lsb() because that only works on words
        when(value) {
            is PtIdentifier -> {
                val longvar = asmgen.asmVariableName(value)
                asmgen.out("  lda  $longvar")
                assignRegisterByte(target, CpuRegister.A, true, false)
            }
            is PtNumber -> throw AssemblyError("casting a long number to byte should have been const-folded away ${value.position}")
            else -> {
                assignExpressionToRegister(value, RegisterOrPair.R0R1_32, true)
                asmgen.out("  lda  cx16.r0")
                assignRegisterByte(target, CpuRegister.A, true, false)
            }
        }
    }

    private fun assignCastWordViaLsbFunc(value: PtExpression, target: AsmAssignTarget) {
        val lsb = PtBuiltinFunctionCall("lsb", false, true, DataType.UBYTE, value.position)
        lsb.parent = value.parent
        lsb.add(value)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UBYTE, expression = lsb)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, value.position)
        translateNormalAssignment(assign, value.definingISub())
    }

    private fun assignCastViaLswFunc(value: PtExpression, target: AsmAssignTarget) {
        val lsb = PtBuiltinFunctionCall("lsw", false, true, DataType.UWORD, value.position)
        lsb.parent = value.parent
        lsb.add(value)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = lsb)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, value.position)
        translateNormalAssignment(assign, value.definingISub())
    }

    private fun assignWordToBool(value: PtExpression, target: AsmAssignTarget) {
        assignExpressionToRegister(value, RegisterOrPair.AY, false)
        asmgen.out("""
            sty  P8ZP_SCRATCH_REG
            ora  P8ZP_SCRATCH_REG
            beq  +
            lda  #1
+""")
        assignRegisterByte(target, CpuRegister.A, false, false)
    }

    private fun assignTypeCastedFloatFAC1(targetAsmVarName: String, targetDt: BaseDataType) {

        if(targetDt==BaseDataType.FLOAT)
            throw AssemblyError("typecast to identical type")

        when(targetDt) {
            BaseDataType.BOOL -> asmgen.out("  jsr  floats.cast_FAC1_as_bool_into_a |  sta  $targetAsmVarName")
            BaseDataType.UBYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName")
            BaseDataType.BYTE -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName")
            BaseDataType.UWORD -> asmgen.out("  jsr  floats.cast_FAC1_as_uw_into_ya |  sty  $targetAsmVarName |  sta  $targetAsmVarName+1")
            BaseDataType.WORD -> asmgen.out("  jsr  floats.cast_FAC1_as_w_into_ay |  sta  $targetAsmVarName |  sty  $targetAsmVarName+1")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedIdentifier(targetAsmVarName: String, targetDt: DataType,
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
                    else -> throw AssemblyError("weird type")
                }
            }
            sourceDt.isString -> throw AssemblyError("cannot typecast a string value")
            else -> throw AssemblyError("weird type")
        }
    }


    private fun assignTypeCastedRegisters(targetAsmVarName: String, targetDt: BaseDataType,
                                          regs: RegisterOrPair, sourceDt: BaseDataType) {
        if(sourceDt == targetDt)
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
                    BaseDataType.UWORD, BaseDataType.WORD -> {
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
                    BaseDataType.BOOL -> TODO("assign byte to bool")
                    BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName")
                    }
                    BaseDataType.UWORD -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65C02))
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  stz  $targetAsmVarName+1")
                        else
                            asmgen.out("  st${regs.toString().lowercase()}  $targetAsmVarName |  lda  #0  |  sta  $targetAsmVarName+1")
                    }
                    BaseDataType.WORD -> {
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
                    BaseDataType.BOOL -> TODO("assign uword to bool")
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    BaseDataType.WORD -> {
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
                    BaseDataType.BOOL -> TODO("assign word to bool")
                    BaseDataType.BYTE, BaseDataType.UBYTE -> {
                        asmgen.out("  st${regs.toString().lowercase().first()}  $targetAsmVarName")
                    }
                    BaseDataType.UWORD -> {
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
            else -> throw AssemblyError("weird type")
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, sourceName: String, msb: Boolean, arrayDt: DataType?, arrayIndexExpr: PtExpression?) {
        if(arrayIndexExpr!=null) {
            if(arrayDt?.isPointer==true) {
                require(!msb)
                return pointergen.assignAddressOfIndexedPointer(target, sourceName, arrayDt, arrayIndexExpr)
            }
            val constIndex = arrayIndexExpr.asConstInteger()
            if(constIndex!=null) {
                if (arrayDt!!.isUnsignedWord) {
                    // using a UWORD pointer with array indexing, always bytes
                    require(!msb)
                    assignVariableToRegister(sourceName, RegisterOrPair.AY, false, arrayIndexExpr.definingISub(), arrayIndexExpr.position)
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
                        val offset = if(arrayDt.isSplitWordArray) constIndex else program.memsizer.memorySize(arrayDt, constIndex)  // add arrayIndexExpr * elementsize  to the address of the array variable.
                        asmgen.out("  lda  #<($sourceName + $offset) |  ldy  #>($sourceName + $offset)")
                    } else {
                        asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName")
                    }
                }
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return
            } else {
                if (arrayDt!!.isUnsignedWord) {
                    // using a UWORD pointer with array indexing, always bytes
                    require(!msb)
                    assignVariableToRegister(sourceName, RegisterOrPair.AY, false, arrayIndexExpr.definingISub(), arrayIndexExpr.position)
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
                        if(!arrayDt.isSplitWordArray) {
                            // elt size 2
                            asmgen.out("  asl  a")
                        }
                    } else if(subtype==BaseDataType.FLOAT) {
                        if(asmgen.options.compTarget.FLOAT_MEM_SIZE != 5)
                            TODO("support float size other than 5 ${arrayIndexExpr.position}")
                        asmgen.out("""
                            sta  P8ZP_SCRATCH_REG
                            asl  a
                            asl  a
                            clc
                            adc  P8ZP_SCRATCH_REG"""
                        )
                    } else throw AssemblyError("weird type $subtype")
                    asmgen.out("""
                        ldy  #>$sourceName
                        clc
                        adc  #<$sourceName
                        bcc  +
                        iny
+""")
                }
                assignRegisterpairWord(target, RegisterOrPair.AY)
                return
            }
        }

        // address of a normal variable
        require(!msb)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1""")
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("can't store word into memory byte")
            }
            TargetStorageKind.ARRAY -> {
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName")
                assignRegisterpairWord(target, RegisterOrPair.AY)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  ldx  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  ldy  #>$sourceName |  lda  #<$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldy  #>$sourceName |  ldx  #<$sourceName")
                    in Cx16VirtualRegisters -> {
                        asmgen.out("""
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            sta  cx16.${target.register.toString().lowercase()}
                            sty  cx16.${target.register.toString().lowercase()}+1""")
                    }
                    else -> throw AssemblyError("can only load address into 16 bit register")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignAddressOf(PtrTarget(target), sourceName)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }

    private fun assignVariableString(target: AsmAssignTarget, varName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when {
                    target.datatype.isUnsignedWord -> {
                        asmgen.out("""
                            lda  #<$varName
                            ldy  #>$varName
                            sta  ${target.asmVarname}
                            sty  ${target.asmVarname}+1""")
                    }
                    target.datatype.isString || target.datatype.isUnsignedByteArray || target.datatype.isByteArray -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  #<$varName
                            ldy  #>$varName
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("assign string to incompatible variable type")
                }
            }
            else -> throw AssemblyError("string-assign to weird target")
        }
    }

    private fun assignVariableLong(target: AsmAssignTarget, varName: String, sourceDt: DataType) {
        require(sourceDt.isByte || sourceDt.isWord || sourceDt.isLong) {
            "need byte/word/long as source value to assign to long variable $varName  ${target.position}"
        }
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(sourceDt) {
                    DataType.BYTE -> {
                        TODO("signed byte to long var")
                    }
                    DataType.UBYTE -> {
                        TODO("ubyte to long var")
                    }
                    DataType.WORD -> {
                        TODO("signed word to long var")
                    }
                    DataType.UWORD -> {
                        TODO("ubyte to long var")
                    }
                    DataType.LONG -> {
                        asmgen.out("""
                            lda  $varName
                            sta  ${target.asmVarname}
                            lda  $varName+1
                            sta  ${target.asmVarname}+1
                            lda  $varName+2
                            sta  ${target.asmVarname}+2
                            lda  $varName+3
                            sta  ${target.asmVarname}+3""")
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
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.REGISTER -> {
                require(target.register in combinedLongRegisters)
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
            TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, varName: String, sourceDt: DataType) {
        if(sourceDt.isSignedByte) {
            // need to sign extend
            asmgen.out("  lda  $varName")
            asmgen.signExtendAYlsb(BaseDataType.BYTE)
            assignRegisterpairWord(target, RegisterOrPair.AY)
            return
        }
        require(sourceDt.isWord || sourceDt.isUnsignedByte || sourceDt.isBool || sourceDt.isPointer) { "weird source dt for word variable" }
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
                        in combinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
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
                        in combinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
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
                val deref = target.array!!.pointerderef
                if(deref!=null) {
                    pointergen.assignFloatAY(IndexedPtrTarget(target))
                    return
                }
                asmgen.out("  pha")
                asmgen.saveRegisterStack(CpuRegister.Y, false)
                asmgen.assignExpressionToRegister(target.array.index, RegisterOrPair.A)
                asmgen.restoreRegisterStack(CpuRegister.Y, false)
                asmgen.out("  pla")
                asmgen.out("""
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1
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

    private fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) {
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

    private fun assignVariableByte(target: AsmAssignTarget, varName: String) {
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
                    in combinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                    else -> throw AssemblyError("weird register")
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteVar(PtrTarget(target), varName, false, false)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }

    private fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
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

    private fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: PtIdentifier) {
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

    private fun extendToMSBofVirtualReg(cpuRegister: CpuRegister, vreg: String, signed: Boolean) {
        if(signed) {
            when(cpuRegister) {
                CpuRegister.A -> { }
                CpuRegister.X -> asmgen.out("  txa")
                CpuRegister.Y -> asmgen.out("  tya")
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
                if(pairedRegisters in combinedLongRegisters) {
                    val startreg = pairedRegisters.startregname()
                    asmgen.out("""
                        lda  cx16.$startreg
                        sta  ${target.asmVarname}
                        lda  cx16.$startreg+1
                        sta  ${target.asmVarname}+1
                        lda  cx16.$startreg+2
                        sta  ${target.asmVarname}+2
                        lda  cx16.$startreg+3
                        sta  ${target.asmVarname}+3""")
                }
                else throw AssemblyError("only combined vreg allowed as long target ${target.position}")
            }
            TargetStorageKind.ARRAY -> {
                TODO("assign 32 bits int into array ${target.position}")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
            TargetStorageKind.REGISTER -> {
                val targetreg = target.register!!
                require(targetreg in combinedLongRegisters && pairedRegisters in combinedLongRegisters)
                if(targetreg!=pairedRegisters) {
                    val sourceStartReg = pairedRegisters.startregname()
                    val targetStartReg = targetreg.startregname()
                    asmgen.out("""
                        lda  cx16.$sourceStartReg
                        sta  cx16.$targetStartReg
                        lda  cx16.$sourceStartReg+1
                        sta  cx16.$targetStartReg+1
                        lda  cx16.$sourceStartReg+2
                        sta  cx16.$targetStartReg+2
                        lda  cx16.$sourceStartReg+3
                        sta  cx16.$targetStartReg+3""")
                }
            }
            TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
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
                }
                storeRegisterAInMemoryAddress(target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(assignAsWord) {
                    when(register) {
                        CpuRegister.A -> {}
                        CpuRegister.X -> asmgen.out("  txa")
                        CpuRegister.Y -> asmgen.out("  tya")
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
                        in combinedLongRegisters -> {
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
                        in combinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
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
                        in combinedLongRegisters -> TODO("assign byte to long reg ${target.position}")
                        else -> throw AssemblyError("weird register")
                    }
                }
            }
            TargetStorageKind.POINTER -> pointergen.assignByteReg(PtrTarget(target), register, signed, extendSignedBits)
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }

    private fun assignRegisterByteToByteArray(target: AsmAssignTarget, register: CpuRegister) {
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
            }
            asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
        }
        else {
            when (register) {
                CpuRegister.A -> {}
                CpuRegister.X -> asmgen.out(" txa")
                CpuRegister.Y -> asmgen.out(" tya")
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

    private fun assignConstantLong(target: AsmAssignTarget, long: Int) {
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
                        stz  $startreg
                        stz  $startreg+1
                        stz  $startreg+2
                        stz  $startreg+3""")
                }
                TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
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
                val hex = long.toUInt().toString(16).padStart(8, '0')
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
                val hex = long.toUInt().toString(16).padStart(8, '0')
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
                require(target.register in combinedLongRegisters)
                val regstart = target.register!!.startregname()
                val hex = long.toUInt().toString(16).padStart(8, '0')
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
            TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
            TargetStorageKind.VOID -> { /* do nothing */ }
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
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
                if (word ushr 8 == word and 255) {
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
                    asmgen.out("  lda  #0")
                    storeRegisterAInMemoryAddress(target.memory!!)
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
                asmgen.out("  lda  #${byte.toHex()}")
                storeRegisterAInMemoryAddress(target.memory!!)
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

    private fun assignMemoryByte(target: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
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
                    in combinedLongRegisters -> TODO("assign memory byte into long ${target.position}")
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
                        in combinedLongRegisters -> {
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

    private fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: UInt?, identifier: PtIdentifier?) {
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

    internal fun storeRegisterAInMemoryAddress(memoryAddress: PtMemoryByte) {
        val addressExpr = memoryAddress.address
        val addressLv = addressExpr as? PtNumber
        val addressOf = addressExpr as? PtAddressOf

        fun storeViaExprEval() {
            when(addressExpr) {
                is PtNumber, is PtIdentifier -> storeByteInAToAddressExpression(addressExpr, false)
                else -> storeByteInAToAddressExpression(addressExpr, true)
            }
        }

        when {
            addressLv != null -> {
                asmgen.out("  sta  ${addressLv.number.toHex()}")
            }
            addressOf != null -> {
                if(addressOf.isFromArrayElement) {
                    TODO("address-of array element $addressOf")
                } else if(addressOf.dereference!=null) {
                    throw AssemblyError("write &dereference, makes no sense at ${addressOf.position}")
                } else {
                    asmgen.out("  sta  ${asmgen.asmSymbolName(addressOf.identifier!!)}")
                }
            }
            addressExpr is PtIdentifier -> {
                asmgen.storeAIntoPointerVar(addressExpr)
            }
            addressExpr is PtBinaryExpression -> {
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
        translateNormalAssignment(assign, expr.definingISub())
    }

    internal fun assignExpressionToVariable(expr: PtExpression, asmVarName: String, dt: DataType) {
        if(expr.type.isFloat && !dt.isFloat) {
            throw AssemblyError("can't directly assign a FLOAT expression to an integer variable $expr")
        } else {
            val src = AsmAssignSource.fromAstSource(expr, program, asmgen)
            val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, dt, expr.definingISub(), expr.position, variableAsmName = asmVarName)
            val assign = AsmAssignment(src, listOf(tgt), program.memsizer, expr.position)
            translateNormalAssignment(assign, expr.definingISub())
        }
    }

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean, scope: IPtSubroutine?, pos: Position) {
        val tgt = AsmAssignTarget.fromRegisters(register, signed, pos, null, asmgen)
        val src = AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, tgt.datatype, variableAsmName = asmVarName)
        val assign = AsmAssignment(src, listOf(tgt), program.memsizer, Position.DUMMY)
        translateNormalAssignment(assign, scope)
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
                        assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign(invertOperator, assign), scope)
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
                            in combinedLongRegisters -> TODO("in place negate long invert ${target.position}")
                            else -> throw AssemblyError("invalid reg dt for word invert")
                        }
                    }
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("~", assign), scope)
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
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceByteNegate(PtrTarget(target), ignoreDatatype, scope)
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
                            in combinedLongRegisters -> TODO("in place negate long reg ${target.position}")
                            else -> throw AssemblyError("invalid reg dt for word neg")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't negate that")
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceWordNegate(PtrTarget(target), ignoreDatatype, scope)
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
                    TargetStorageKind.REGISTER -> TODO("32 bits register negate ${target.position}")
                    TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
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
                    TargetStorageKind.ARRAY -> assignPrefixedExpressionToArrayElt(makePrefixedExprFromArrayExprAssign("-", assign), scope)
                    TargetStorageKind.POINTER -> pointergen.inplaceFloatNegate(PtrTarget(target), scope)
                    else -> throw AssemblyError("weird target for in-place float negation")
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

    private fun makePrefixedExprFromArrayExprAssign(operator: String, assign: AsmAssignment): AsmAssignment {
        val prefix = PtPrefix(operator, assign.source.datatype, assign.source.array!!.position)
        prefix.add(assign.source.array)
        prefix.parent = assign.target.origAstTarget ?: program
        val prefixSrc = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, assign.source.datatype, expression=prefix)
        return AsmAssignment(prefixSrc, assign.targets, assign.memsizer, assign.position)
    }
}
