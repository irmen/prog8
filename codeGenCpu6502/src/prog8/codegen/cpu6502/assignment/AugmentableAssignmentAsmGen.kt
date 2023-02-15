package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator


internal class AugmentableAssignmentAsmGen(private val program: PtProgram,
                                           private val assignmentAsmGen: AssignmentAsmGen,
                                           private val asmgen: AsmGen6502Internal,
                                           private val allocator: VariableAllocator
) {
    fun translate(assign: AsmAugmentedAssignment) {

        when(assign.operator) {
            "-" -> {
                val a2 = AsmAssignment(assign.source, assign.target, assign.memsizer, assign.position)
                assignmentAsmGen.inplaceNegate(a2, false)
            }
            "~" -> {
                val a2 = AsmAssignment(assign.source, assign.target, assign.memsizer, assign.position)
                assignmentAsmGen.inplaceInvert(a2)
            }
            "+" -> { /* is a nop */ }
            else -> {
                if(assign.operator.endsWith('='))
                    augmentedAssignExpr(assign)
                else
                    throw AssemblyError("invalid augmented assign operator ${assign.operator}")
            }
        }
    }

    private fun augmentedAssignExpr(assign: AsmAugmentedAssignment) {
        val srcValue = assign.source.toAstExpression(assign.target.scope as PtNamedNode)
        when (assign.operator) {
            "+=" -> inplaceModification(assign.target, "+", srcValue)
            "-=" -> inplaceModification(assign.target, "-", srcValue)
            "*=" -> inplaceModification(assign.target, "*", srcValue)
            "/=" -> inplaceModification(assign.target, "/", srcValue)
            "|=" -> inplaceModification(assign.target, "|", srcValue)
            "&=" -> inplaceModification(assign.target, "&", srcValue)
            "^=" -> inplaceModification(assign.target, "^", srcValue)
            "<<=" -> inplaceModification(assign.target, "<<", srcValue)
            ">>=" -> inplaceModification(assign.target, ">>", srcValue)
            else -> throw AssemblyError("invalid augmented assign operator ${assign.operator}")     // TODO fallback to non-augmented Assign?
        }
    }

    private fun inplaceModification(target: AsmAssignTarget, operator: String, origValue: PtExpression) {

        // the asm-gen code can deal with situations where you want to assign a byte into a word.
        // it will create the most optimized code to do this (so it type-extends for us).
        // But we can't deal with writing a word into a byte - explicit typeconversion is required
        val value = if(program.memsizer.memorySize(origValue.type) > program.memsizer.memorySize(target.datatype)) {
            val typecast = PtTypeCast(target.datatype, origValue.position)
            typecast.add(origValue)
            require(typecast.type!=origValue.type)
            typecast
        }
        else {
            origValue
        }

        val valueLv = (value as? PtNumber)?.number
        val ident = value as? PtIdentifier
        val memread = value as? PtMemoryByte

        when (target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    in ByteDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(target.asmVarname, target.datatype, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(target.asmVarname, target.datatype, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable(target.asmVarname, target.datatype, operator, memread)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable(target.asmVarname, target.datatype, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(target.asmVarname, target.datatype, operator, value)
                        }
                    }
                    in WordDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_word_litval_to_variable(target.asmVarname, target.datatype, operator, valueLv.toInt())
                            ident != null -> inplaceModification_word_variable_to_variable(target.asmVarname, target.datatype, operator, ident)
                            memread != null -> inplaceModification_word_memread_to_variable(target.asmVarname, target.datatype, operator, memread)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator))
                                    return
                                inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                            }
                            else -> inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                        }
                    }
                    DataType.FLOAT -> {
                        when {
                            valueLv != null -> inplaceModification_float_litval_to_variable(target.asmVarname, operator, valueLv.toDouble(), target.scope!!)
                            ident != null -> inplaceModification_float_variable_to_variable(target.asmVarname, operator, ident, target.scope!!)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                inplaceModification_float_value_to_variable(target.asmVarname, operator, value, target.scope!!)
                            }
                            else -> inplaceModification_float_value_to_variable(target.asmVarname, operator, value, target.scope!!)
                        }
                    }
                    else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                val memory = target.memory!!
                when (memory.address) {
                    is PtNumber -> {
                        val addr = (memory.address as PtNumber).number.toInt()
                        // re-use code to assign a variable, instead this time, use a direct memory address
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(addr.toHex(), DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(addr.toHex(), DataType.UBYTE, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                        }
                    }
                    is PtIdentifier -> {
                        val pointer = memory.address as PtIdentifier
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_pointer(pointer, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_pointer(pointer, operator, ident)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_pointer(pointer, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_pointer(pointer, operator, value)
                        }
                    }
                    else -> {
                        // TODO use some other evaluation here; don't use the estack to transfer the address to read/write from
                        asmgen.assignExpressionTo(memory.address, AsmAssignTarget(TargetStorageKind.STACK, asmgen, DataType.UWORD, memory.definingISub()))
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  sta  P8ZP_SCRATCH_B1")
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, memread)
                            value is PtTypeCast -> {
                                if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value)
                        }
                        asmgen.out("  lda  P8ZP_SCRATCH_B1 |  jsr  prog8_lib.write_byte_to_address_on_stack | inx")
                    }
                }
            }
            TargetStorageKind.ARRAY -> {
                val indexNum = target.array!!.index as? PtNumber
                val indexVar = target.array.index as? PtIdentifier
                when {
                    indexNum!=null -> {
                        val targetVarName = "${target.asmVarname} + ${indexNum.number.toInt()*program.memsizer.memorySize(target.datatype)}"
                        when (target.datatype) {
                            in ByteDatatypes -> {
                                when {
                                    valueLv != null -> inplaceModification_byte_litval_to_variable(targetVarName, target.datatype, operator, valueLv.toInt())
                                    ident != null -> inplaceModification_byte_variable_to_variable(targetVarName, target.datatype, operator, ident)
                                    memread != null -> inplaceModification_byte_memread_to_variable(targetVarName, target.datatype, operator, memread)
                                    value is PtTypeCast -> {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                        inplaceModification_byte_value_to_variable(targetVarName, target.datatype, operator, value)
                                    }
                                    else -> inplaceModification_byte_value_to_variable(targetVarName, target.datatype, operator, value)
                                }
                            }
                            in WordDatatypes -> {
                                when {
                                    valueLv != null -> inplaceModification_word_litval_to_variable(targetVarName, target.datatype, operator, valueLv.toInt())
                                    ident != null -> inplaceModification_word_variable_to_variable(targetVarName, target.datatype, operator, ident)
                                    memread != null -> inplaceModification_word_memread_to_variable(targetVarName, target.datatype, operator, memread)
                                    value is PtTypeCast -> {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                        inplaceModification_word_value_to_variable(targetVarName, target.datatype, operator, value)
                                    }
                                    else -> inplaceModification_word_value_to_variable(targetVarName, target.datatype, operator, value)
                                }
                            }
                            DataType.FLOAT -> {
                                when {
                                    valueLv != null -> inplaceModification_float_litval_to_variable(targetVarName, operator, valueLv.toDouble(), target.scope!!)
                                    ident != null -> inplaceModification_float_variable_to_variable(targetVarName, operator, ident, target.scope!!)
                                    value is PtTypeCast -> {
                                        if (tryInplaceModifyWithRemovedRedundantCast(value, target, operator)) return
                                        inplaceModification_float_value_to_variable(targetVarName, operator, value, target.scope!!)
                                    }
                                    else -> inplaceModification_float_value_to_variable(targetVarName, operator, value, target.scope!!)
                                }
                            }
                            else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                        }
                    }
                    indexVar!=null -> {
                        when (target.datatype) {
                            in ByteDatatypes -> {
                                val tgt =
                                    AsmAssignTarget.fromRegisters(
                                        RegisterOrPair.A,
                                        target.datatype == DataType.BYTE, null,
                                        asmgen
                                    )
                                val assign = AsmAssignment(target.origAssign.source, tgt, program.memsizer, value.position)
                                assignmentAsmGen.translateNormalAssignment(assign)
                                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A)
                            }
                            in WordDatatypes -> {
                                val tgt =
                                    AsmAssignTarget.fromRegisters(
                                        RegisterOrPair.AY,
                                        target.datatype == DataType.WORD, null,
                                        asmgen
                                    )
                                val assign = AsmAssignment(target.origAssign.source, tgt, program.memsizer, value.position)
                                assignmentAsmGen.translateNormalAssignment(assign)
                                assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                            }
                            DataType.FLOAT -> {
                                val tgt =
                                    AsmAssignTarget.fromRegisters(
                                        RegisterOrPair.FAC1,
                                        true, null,
                                        asmgen
                                    )
                                val assign = AsmAssignment(target.origAssign.source, tgt, program.memsizer, value.position)
                                assignmentAsmGen.translateNormalAssignment(assign)
                                assignmentAsmGen.assignFAC1float(target)
                            }
                            else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                        }
                    }
                    else -> throw AssemblyError("indexer expression should have been replaced by auto indexer var")
                }
            }
            TargetStorageKind.REGISTER -> throw AssemblyError("no asm gen for reg in-place modification")
            TargetStorageKind.STACK -> throw AssemblyError("no asm gen for stack in-place modification")
        }
    }

    private fun tryInplaceModifyWithRemovedRedundantCast(value: PtTypeCast, target: AsmAssignTarget, operator: String): Boolean {
        if (target.datatype == value.type) {
            val childDt = value.value.type
            if (value.type!=DataType.FLOAT && (value.type.equalsSize(childDt) || value.type.largerThan(childDt))) {
                // this typecast is redundant here; the rest of the code knows how to deal with the uncasted value.
                // (works for integer types, not for float.)
                inplaceModification(target, operator, value.value)
                return true
            }
        }
        return false
    }

    private fun inplaceModification_byte_value_to_pointer(pointervar: PtIdentifier, operator: String, value: PtExpression) {
        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out("  clc |  adc  P8ZP_SCRATCH_B1")
            "-" -> asmgen.out("  sec |  sbc  P8ZP_SCRATCH_B1")
            "*" -> asmgen.out("  ldy  P8ZP_SCRATCH_B1 |  jsr  math.multiply_bytes")
            "/" -> asmgen.out("  ldy  P8ZP_SCRATCH_B1 |  jsr  math.divmod_ub_asm |  tya")
            "%" -> asmgen.out("  ldy  P8ZP_SCRATCH_B1 |  jsr  math.divmod_ub_asm")
            "<<" -> {
                asmgen.out("""
                    ldy  P8ZP_SCRATCH_B1
                    beq  +
-                   asl  a
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                asmgen.out("""
                    ldy  P8ZP_SCRATCH_B1
                    beq  +
-                   lsr  a
                    dey
                    bne  -
+""")
            }
            "&" -> asmgen.out(" and  P8ZP_SCRATCH_B1")
            "|" -> asmgen.out(" ora  P8ZP_SCRATCH_B1")
            "^" -> asmgen.out(" eor  P8ZP_SCRATCH_B1")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.storeAIntoZpPointerVar(sourceName)
    }

    private fun inplaceModification_byte_variable_to_pointer(pointervar: PtIdentifier, operator: String, value: PtIdentifier) {
        val otherName = asmgen.asmVariableName(value)
        val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)

        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out("  clc |  adc  $otherName")
            "-" -> asmgen.out("  sec |  sbc  $otherName")
            "*" -> asmgen.out("  ldy  $otherName |  jsr  math.multiply_bytes")
            "/" -> asmgen.out("  ldy  $otherName |  jsr  math.divmod_ub_asm |  tya")
            "%" -> asmgen.out("  ldy  $otherName |  jsr  math.divmod_ub_asm")
            "<<" -> {
                asmgen.out("""
                        ldy  $otherName
                        beq  + 
-                       asl  a
                        dey
                        bne  -
+""")
            }
            ">>" -> {
                asmgen.out("""
                        ldy  $otherName
                        beq  + 
-                       lsr  a
                        dey
                        bne  -
+""")
            }
            "&" -> asmgen.out(" and  $otherName")
            "|" -> asmgen.out(" ora  $otherName")
            "^" -> asmgen.out(" eor  $otherName")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.storeAIntoZpPointerVar(sourceName)
    }

    private fun inplaceModification_byte_litval_to_pointer(pointervar: PtIdentifier, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  clc |  adc  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "-" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  sec |  sbc  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "*" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value in asmgen.optimizedByteMultiplications)
                    asmgen.out("  jsr  math.mul_byte_${value}")
                else
                    asmgen.out("  ldy  #$value |  jsr  math.multiply_bytes")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "/" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value==0)
                    throw AssemblyError("division by zero")
                asmgen.out("  ldy  #$value |  jsr  math.divmod_ub_asm |  tya")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "%" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                if(value==0)
                    throw AssemblyError("division by zero")
                asmgen.out("  ldy  #$value |  jsr  math.divmod_ub_asm")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "<<" -> {
                if (value > 0) {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out("  asl  a") }
                    asmgen.storeAIntoZpPointerVar(sourceName)
                }
            }
            ">>" -> {
                if (value > 0) {
                    val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out("  lsr  a") }
                    asmgen.storeAIntoZpPointerVar(sourceName)
                }
            }
            "&" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  and  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "|"-> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  ora  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "^" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  eor  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_value_to_variable(name: String, dt: DataType, operator: String, value: PtExpression) {
        // this should be the last resort for code generation for this,
        // because the value is evaluated onto the eval stack (=slow).
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  clc |  adc  $name |  sta  $name")
            }
            "-" -> {
                asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", dt, null)
                asmgen.out(" lda  $name |  sec |  sbc  P8ZP_SCRATCH_B1 |  sta  $name")
            }
            "*" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  ldy  $name |  jsr  math.multiply_bytes |  sta  $name")
            }
            "/" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                if(dt==DataType.UBYTE)
                    asmgen.out("  lda  $name |  jsr  math.divmod_ub_asm |  sty  $name")
                else
                    asmgen.out("  lda  $name |  jsr  math.divmod_b_asm |  sty  $name")
            }
            "%" -> {
                if(dt==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                asmgen.out("  lda  $name |  jsr  math.divmod_ub_asm |  sta  $name")
            }
            "<<" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                asmgen.out("""
                    beq  +
-                   asl  $name
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        beq  +
-                       lsr  $name
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        beq  +
-                       lda  $name
                        asl  a
                        ror  $name
                        dey
                        bne  -
+""")
                }
            }
            "&" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  and  $name |  sta  $name")
            }
            "|" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  ora  $name |  sta  $name")
            }
            "^" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  eor  $name |  sta  $name")
            }
            "==" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("""
                    cmp  $name
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+                   sta  $name""")
            }
            "!=" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("""
                    cmp  $name
                    beq  +
                    lda  #1
                    bne  ++
+                   lda  #0
+                   sta  $name""")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_variable_to_variable(name: String, dt: DataType, operator: String, ident: PtIdentifier) {
        val otherName = asmgen.asmVariableName(ident)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out(" lda  $name |  clc |  adc  $otherName |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  $otherName |  sta  $name")
            "*" -> asmgen.out(" lda  $name |  ldy  $otherName  |  jsr  math.multiply_bytes |  sta  $name")
            "/" -> {
                if(dt==DataType.BYTE) {
                    asmgen.out(" lda  $name |  ldy  $otherName  |  jsr  math.divmod_b_asm |  sty  $name")
                }
                else {
                    asmgen.out(" lda  $name |  ldy  $otherName  |  jsr  math.divmod_ub_asm |  sty  $name")
                }
            }
            "%" -> {
                if(dt==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out(" lda  $name |  ldy  $otherName  |  jsr  math.divmod_ub_asm |  sta  $name")
            }
            "<<" -> {
                asmgen.out("""
                    ldy  $otherName
                    beq  +
-                   asl  $name
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        ldy  $otherName
                        beq  +
-                       lsr  $name
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        ldy  $otherName
                        beq  +
-                       lda  $name
                        asl  a
                        ror  $name
                        dey
                        bne  -
+""")
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  eor  $otherName |  sta  $name")
            "==" -> {
                asmgen.out("""
                    lda  $otherName
                    cmp  $name
                    beq  +
                    lda  #0
                    bne  ++
+                   lda  #1
+                   sta  $name""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  $otherName
                    cmp  $name
                    beq  +
                    lda  #1
                    bne  ++
+                   lda  #0
+                   sta  $name""")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_litval_to_variable(name: String, dt: DataType, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out(" lda  $name |  clc |  adc  #$value |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  #$value |  sta  $name")
            "*" -> {
                if(value in asmgen.optimizedByteMultiplications)
                    asmgen.out("  lda  $name |  jsr  math.mul_byte_$value |  sta  $name")
                else
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.multiply_bytes |  sta  $name")
            }
            "/" -> {
                if (dt == DataType.UBYTE)
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.divmod_ub_asm |  sty  $name")
                else
                    asmgen.out("  lda  $name |  ldy  #$value |  jsr  math.divmod_b_asm |  sty  $name")
            }
            "%" -> {
                if(dt==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("""
                    lda  $name
                    ldy  #$value
                    jsr  math.divmod_ub_asm
                    sta  $name""")
            }
            "<<" -> {
                if(value>=8) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  $name")
                    else
                        asmgen.out("  lda  #0 |  sta  $name")
                }
                else repeat(value) { asmgen.out("  asl  $name") }
            }
            ">>" -> {
                if(value>0) {
                    if (dt == DataType.UBYTE) {
                        if(value>=8) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name")
                            else
                                asmgen.out("  lda  #0 |  sta  $name")
                        }
                        else repeat(value) { asmgen.out("  lsr  $name") }
                    } else {
                        when {
                            value>=8 -> asmgen.out("""
                                lda  $name
                                bmi  +
                                lda  #0
                                beq  ++
+                               lda  #-1
+                               sta  $name""")
                            value>3 -> asmgen.out("""
                                lda  $name
                                ldy  #$value
                                jsr  math.lsr_byte_A
                                sta  $name""")
                            else -> repeat(value) { asmgen.out("  lda  $name | asl  a |  ror  $name") }
                        }
                    }
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  #$value |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  #$value |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  eor  #$value |  sta  $name")
            "==" -> {
                asmgen.out("""
                    lda  $name
                    cmp  #$value
                    beq  +
                    lda  #0
                    beq  ++
+                   lda  #1
+                   sta  $name""")
            }
            "!=" -> {
                asmgen.out("""
                    lda  $name
                    cmp  #$value
                    beq  +
                    lda  #1
                    bne  ++
+                   lda  #0
+                   sta  $name""")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_memread_to_variable(name: String, dt: DataType, operator: String, memread: PtMemoryByte) {
        when (operator) {
            "+" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("""
                    clc
                    adc  $name
                    sta  $name""")
            }
            "-" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("""
                    sta  P8ZP_SCRATCH_B1
                    lda  $name
                    sec
                    sbc  P8ZP_SCRATCH_B1
                    sta  $name""")
            }
            "|" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  ora  $name  |  sta  $name")
            }
            "&" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  and  $name  |  sta  $name")
            }
            "^" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  eor  $name  |  sta  $name")
            }
            // TODO: tuned code for more operators
            else -> {
                inplaceModification_byte_value_to_variable(name, dt, operator, memread)
            }
        }
    }

    private fun inplaceModification_word_memread_to_variable(name: String, dt: DataType, operator: String, memread: PtMemoryByte) {
        when (operator) {
            "+" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("""
                    clc
                    adc  $name
                    sta  $name
                    bcc  +
                    inc  $name+1
+""")
            }
            "-" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("""
                    sta  P8ZP_SCRATCH_B1
                    lda  $name
                    sec
                    sbc  P8ZP_SCRATCH_B1
                    sta  $name
                    bcc  +
                    dec  $name+1
+""")
            }
            "|" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  ora  $name  |  sta  $name")
            }
            "&" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  and  $name  |  sta  $name")
                if(dt in WordDatatypes) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  $name+1")
                    else
                        asmgen.out("  lda  #0 |  sta  $name+1")
                }
            }
            "^" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  eor  $name  |  sta  $name")
            }
            // TODO: tuned code for more operators
            else -> {
                inplaceModification_word_value_to_variable(name, dt, operator, memread)
            }
        }
    }

    private fun inplaceModification_word_litval_to_variable(name: String, dt: DataType, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                when {
                    value==0 -> {}
                    value in 1..0xff -> asmgen.out("""
                        lda  $name
                        clc
                        adc  #$value
                        sta  $name
                        bcc  +
                        inc  $name+1
+""")
                    value==0x0100 -> asmgen.out(" inc  $name+1")
                    value==0x0200 -> asmgen.out(" inc  $name+1 |  inc  $name+1")
                    value==0x0300 -> asmgen.out(" inc  $name+1 |  inc  $name+1 |  inc  $name+1")
                    value==0x0400 -> asmgen.out(" inc  $name+1 |  inc  $name+1 |  inc  $name+1 |  inc  $name+1")
                    value and 255==0 -> asmgen.out(" lda  $name+1 |  clc |  adc  #>$value |  sta  $name+1")
                    else -> asmgen.out("""
                        lda  $name
                        clc
                        adc  #<$value
                        sta  $name
                        lda  $name+1
                        adc  #>$value
                        sta  $name+1""")
                }
            }
            "-" -> {
                when {
                    value==0 -> {}
                    value in 1..0xff -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  #$value
                        sta  $name
                        bcs  +
                        dec  $name+1
+""")
                    value==0x0100 -> asmgen.out(" dec  $name+1")
                    value==0x0200 -> asmgen.out(" dec  $name+1 |  dec  $name+1")
                    value==0x0300 -> asmgen.out(" dec  $name+1 |  dec  $name+1 |  dec  $name+1")
                    value==0x0400 -> asmgen.out(" dec  $name+1 |  dec  $name+1 |  dec  $name+1 |  dec  $name+1")
                    value and 255==0 -> asmgen.out(" lda  $name+1 |  sec |  sbc  #>$value |  sta  $name+1")
                    else -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  #<$value
                        sta  $name
                        lda  $name+1
                        sbc  #>$value
                        sta  $name+1""")
                }
            }
            "*" -> {
                // the mul code works for both signed and unsigned
                if(value in asmgen.optimizedWordMultiplications) {
                    asmgen.out("  lda  $name |  ldy  $name+1 |  jsr  math.mul_word_$value |  sta  $name |  sty  $name+1")
                } else {
                    asmgen.out("""
                        lda  $name
                        sta  P8ZP_SCRATCH_W1
                        lda  $name+1
                        sta  P8ZP_SCRATCH_W1+1
                        lda  #<$value
                        ldy  #>$value
                        jsr  math.multiply_words
                        lda  math.multiply_words.result
                        sta  $name
                        lda  math.multiply_words.result+1
                        sta  $name+1""")
                }
            }
            "/" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                if(dt==DataType.WORD) {
                    asmgen.out("""
                        lda  $name
                        ldy  $name+1
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<$value
                        ldy  #>$value
                        jsr  math.divmod_w_asm
                        sta  $name
                        sty  $name+1
                    """)
                }
                else {
                    asmgen.out("""
                        lda  $name
                        ldy  $name+1
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #<$value
                        ldy  #>$value
                        jsr  math.divmod_uw_asm
                        sta  $name
                        sty  $name+1
                    """)
                }
            }
            "%" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                if(dt==DataType.WORD)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("""
                    lda  $name
                    ldy  $name+1
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<$value
                    ldy  #>$value
                    jsr  math.divmod_uw_asm
                    lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
                    sta  $name
                    sty  $name+1
                """)
            }
            "<<" -> {
                when {
                    value>=16 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name |  stz  $name+1")
                        else
                            asmgen.out("  lda  #0 |  sta  $name |  sta  $name+1")
                    }
                    value==8 -> {
                        asmgen.out("  lda  $name |  sta  $name+1")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name")
                        else
                            asmgen.out("  lda  #0 |  sta  $name")
                    }
                    value>3 -> asmgen.out("""
                        ldy  #$value
-                       asl  $name
                        rol  $name+1
                        dey
                        bne  -
                    """)
                    else -> repeat(value) { asmgen.out(" asl  $name |  rol  $name+1") }
                }
            }
            ">>" -> {
                if (value > 0) {
                    if(dt==DataType.UWORD) {
                        when {
                            value>=16 -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  $name |  stz  $name+1")
                                else
                                    asmgen.out("  lda  #0 |  sta  $name |  sta  $name+1")
                            }
                            value==8 -> {
                                asmgen.out("  lda  $name+1 |  sta  $name")
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  stz  $name+1")
                                else
                                    asmgen.out("  lda  #0 |  sta  $name+1")
                            }
                            value>2 -> asmgen.out("""
                                ldy  #$value
-                               lsr  $name+1
                                ror  $name
                                dey
                                bne  -""")
                            else -> repeat(value) { asmgen.out("  lsr  $name+1 |  ror  $name")}
                        }
                    } else {
                        when {
                            value>=16 -> asmgen.out("""
                                lda  $name+1
                                bmi  +
                                lda  #0
                                beq  ++
+                               lda  #-1
+                               sta  $name
                                sta  $name+1""")
                            value==8 -> asmgen.out("""
                                 lda  $name+1
                                 sta  $name
                                 bmi  +
                                 lda  #0
-                                sta  $name+1
                                 beq  ++
+                                lda  #-1
                                 sta  $name+1
+""")
                            value>2 -> asmgen.out("""
                                ldy  #$value
-                               lda  $name+1
                                asl  a
                                ror  $name+1
                                ror  $name
                                dey
                                bne  -""")
                            else -> repeat(value) { asmgen.out("  lda  $name+1 |  asl  a |  ror  $name+1 |  ror  $name") }
                        }
                    }
                }
            }
            "&" -> {
                when {
                    value == 0 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name |  stz  $name+1")
                        else
                            asmgen.out("  lda  #0 |  sta  $name |  sta  $name+1")
                    }
                    value == 0x00ff -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name+1")
                        else
                            asmgen.out("  lda  #0 |  sta  $name+1")
                    }
                    value == 0xff00 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name")
                        else
                            asmgen.out("  lda  #0 |  sta  $name")
                    }
                    value and 255 == 0 -> {
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name")
                        else
                            asmgen.out("  lda  #0 |  sta  $name")
                        asmgen.out("  lda  $name+1 |  and  #>$value |  sta  $name+1")
                    }
                    value < 0x0100 -> {
                        asmgen.out("  lda  $name |  and  #$value |  sta  $name")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  $name+1")
                        else
                            asmgen.out("  lda  #0 |  sta  $name+1")
                    }
                    else -> asmgen.out("  lda  $name |  and  #<$value |  sta  $name |  lda  $name+1 |  and  #>$value |  sta  $name+1")
                }
            }
            "|" -> {
                when {
                    value == 0 -> {}
                    value and 255 == 0 -> asmgen.out("  lda  $name+1 |  ora  #>$value |  sta  $name+1")
                    value < 0x0100 -> asmgen.out("  lda  $name |  ora  #$value |  sta  $name")
                    else -> asmgen.out("  lda  $name |  ora  #<$value |  sta  $name |  lda  $name+1 |  ora  #>$value |  sta  $name+1")
                }
            }
            "^" -> {
                when {
                    value == 0 -> {}
                    value and 255 == 0 -> asmgen.out("  lda  $name+1 |  eor  #>$value |  sta  $name+1")
                    value < 0x0100 -> asmgen.out("  lda  $name |  eor  #$value |  sta  $name")
                    else -> asmgen.out("  lda  $name |  eor  #<$value |  sta  $name |  lda  $name+1 |  eor  #>$value |  sta  $name+1")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_word_variable_to_variable(name: String, dt: DataType, operator: String, ident: PtIdentifier) {
        val otherName = asmgen.asmVariableName(ident)
        when (val valueDt = ident.type) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> {
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                clc
                                adc  $otherName
                                sta  $name
                                bcc  +
                                inc  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #0
                                lda  $otherName
                                bpl  +
                                dey     ; sign extend
+                               clc
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "-" -> {
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                sec
                                sbc  $otherName
                                sta  $name
                                bcs  +
                                dec  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #255
                                lda  $otherName
                                bpl  +
                                iny     ; sign extend
+                               eor  #255
                                sec
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "*" -> {
                        if(valueDt==DataType.UBYTE) {
                            asmgen.out("  lda  $otherName |  sta  P8ZP_SCRATCH_W1")
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  P8ZP_SCRATCH_W1+1")
                            else
                                asmgen.out("  lda  #0 |  sta  P8ZP_SCRATCH_W1+1")
                        } else {
                            asmgen.out("  lda  $otherName")
                            asmgen.signExtendAYlsb(valueDt)
                            asmgen.out("  sta  P8ZP_SCRATCH_W1 |  sty  P8ZP_SCRATCH_W1+1")
                        }
                        asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                jsr  math.multiply_words
                                lda  math.multiply_words.result
                                sta  $name
                                lda  math.multiply_words.result+1
                                sta  $name+1""")
                    }
                    "/" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  #0
                                jsr  math.divmod_uw_asm
                                sta  $name
                                sty  $name+1
                            """)
                        } else {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  #0
                                jsr  math.divmod_w_asm
                                sta  $name
                                sty  $name+1
                            """)
                        }
                    }
                    "%" -> {
                        if(valueDt!=DataType.UBYTE || dt!=DataType.UWORD)
                            throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                        asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  $otherName
                            ldy  #0
                            jsr  math.divmod_uw_asm
                            lda  P8ZP_SCRATCH_W2
                            sta  $name
                            lda  P8ZP_SCRATCH_W2+1
                            sta  $name+1
                        """)
                    }
                    "<<" -> {
                        asmgen.out("""
                        ldy  $otherName
                        beq  +
-                       asl  $name
                        rol  $name+1
                        dey
                        bne  -
+""")
                    }
                    ">>" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                            ldy  $otherName
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        } else {
                            asmgen.out("""
                            ldy  $otherName
                            beq  +
-                           lda  $name+1
                            asl  a
                            ror  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        }
                    }
                    "&" -> {
                        asmgen.out("  lda  $otherName |  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|" -> asmgen.out("  lda  $otherName |  ora  $name |  sta  $name")
                    "^" -> asmgen.out("  lda  $otherName |  eor  $name |  sta  $name")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> asmgen.out("  lda  $name |  clc |  adc  $otherName |  sta  $name |  lda  $name+1 |  adc  $otherName+1 |  sta  $name+1")
                    "-" -> asmgen.out("  lda  $name |  sec |  sbc  $otherName |  sta  $name |  lda  $name+1 |  sbc  $otherName+1 |  sta  $name+1")
                    "*" -> {
                        asmgen.out("""
                            lda  $otherName
                            ldy  $otherName+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  $name
                            ldy  $name+1
                            jsr  math.multiply_words
                            lda  math.multiply_words.result
                            sta  $name
                            lda  math.multiply_words.result+1
                            sta  $name+1
                        """)
                    }
                    "/" -> {
                        if(dt==DataType.WORD) {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  $otherName+1
                                jsr  math.divmod_w_asm
                                sta  $name
                                sty  $name+1
                            """)
                        }
                        else {
                            asmgen.out("""
                                lda  $name
                                ldy  $name+1
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  $otherName
                                ldy  $otherName+1
                                jsr  math.divmod_uw_asm
                                sta  $name
                                sty  $name+1
                            """)
                        }
                    }
                    "%" -> {
                        if(dt==DataType.WORD)
                            throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                        asmgen.out("""
                            lda  $name
                            ldy  $name+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  $otherName
                            ldy  $otherName+1
                            jsr  math.divmod_uw_asm
                            lda  P8ZP_SCRATCH_W2
                            sta  $name
                            lda  P8ZP_SCRATCH_W2+1
                            sta  $name+1
                        """)
                    }
                    "<<", ">>" -> throw AssemblyError("shift by a word value not supported, max is a byte")
                    "&" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name |  lda  $name+1 |  and  $otherName+1 |  sta  $name+1")
                    "|" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name |  lda  $name+1 |  ora  $otherName+1 |  sta  $name+1")
                    "^" -> asmgen.out(" lda  $name |  eor  $otherName |  sta  $name |  lda  $name+1 |  eor  $otherName+1 |  sta  $name+1")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> {
                throw AssemblyError("can only use integer datatypes here")
            }
        }
    }

    private fun inplaceModification_word_value_to_variable(name: String, dt: DataType, operator: String, value: PtExpression) {
        // this should be the last resort for code generation for this,
        // because the value is evaluated onto the eval stack (=slow).


        fun multiplyVarByWordInAY() {
            asmgen.out("""
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  $name
                ldy  $name+1
                jsr  math.multiply_words
                lda  math.multiply_words.result
                sta  $name
                lda  math.multiply_words.result+1
                sta  $name+1
            """)
        }

        fun divideVarByWordInAY() {
            asmgen.out("""
                    pha
                    lda  $name
                    sta  P8ZP_SCRATCH_W1
                    lda  $name+1
                    sta  P8ZP_SCRATCH_W1+1
                    pla""")
            if (dt == DataType.WORD)
                asmgen.out("  jsr  math.divmod_w_asm")
            else
                asmgen.out("  jsr  math.divmod_uw_asm")
            asmgen.out("  sta  $name |  sty  $name+1")
        }

        fun remainderVarByWordInAY() {
            if(dt==DataType.WORD)
                throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
            asmgen.out("""
                pha
                lda  $name
                sta  P8ZP_SCRATCH_W1
                lda  $name+1
                sta  P8ZP_SCRATCH_W1+1
                pla
                jsr  math.divmod_uw_asm
                lda  P8ZP_SCRATCH_W2
                ldy  P8ZP_SCRATCH_W2+1
                sta  $name
                sty  $name+1
            """)
        }

        when (val valueDt = value.type) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> {
                        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", valueDt, null)
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                clc
                                adc  P8ZP_SCRATCH_B1
                                sta  $name
                                bcc  +
                                inc  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #0
                                lda  P8ZP_SCRATCH_B1
                                bpl  +
                                dey         ; sign extend
+                               clc
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "-" -> {
                        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_B1", valueDt, null)
                        if(valueDt==DataType.UBYTE)
                            asmgen.out("""
                                lda  $name
                                sec
                                sbc  P8ZP_SCRATCH_B1
                                sta  $name
                                bcs  +
                                dec  $name+1
+""")
                        else
                            asmgen.out("""
                                ldy  #255
                                lda  P8ZP_SCRATCH_B1
                                bpl  +
                                iny         ; sign extend
+                               eor  #255
                                sec
                                adc  $name
                                sta  $name
                                tya
                                adc  $name+1
                                sta  $name+1""")
                    }
                    "*" -> {
                        // stack contains (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word * byte multiplication routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        multiplyVarByWordInAY()
                    }
                    "/" -> {
                        // stack contains (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word / byte divmod routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        divideVarByWordInAY()
                    }
                    "%" -> {
                        // stack contains (u) byte value, sign extend that and proceed with regular 16 bit operation
                        // TODO use an optimized word / byte divmod routine?
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.signExtendAYlsb(valueDt)
                        remainderVarByWordInAY()
                    }
                    "<<" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                        asmgen.out("""
                            beq  +
-                   	    asl  $name
                            rol  $name+1
                            dey
                            bne  -
+""")
                    }
                    ">>" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.Y)
                        if(dt==DataType.UWORD)
                            asmgen.out("""
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                        else
                            asmgen.out("""
                            beq  +
-                           lda  $name+1
                            asl  a
                            ror  $name+1
                            ror  $name
                            dey
                            bne  -
+""")
                    }
                    "&" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  ora  $name |  sta  $name")
                    }
                    "^" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  eor  $name |  sta  $name")
                    }
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  clc |  adc  $name |  sta  $name |  tya |  adc  $name+1 |  sta  $name+1")
                    }
                    "-" -> {
                        asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_W1", valueDt, null)
                        asmgen.out(" lda  $name |  sec |  sbc  P8ZP_SCRATCH_W1 |  sta  $name |  lda  $name+1 |  sbc  P8ZP_SCRATCH_W1+1 |  sta  $name+1")
                    }
                    "*" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        multiplyVarByWordInAY()
                    }
                    "/" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        divideVarByWordInAY()
                    }
                    "%" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        remainderVarByWordInAY()
                    }
                    "<<", ">>" -> throw AssemblyError("shift by a word value not supported, max is a byte")
                    "&" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  and  $name |  sta  $name |  tya |  and  $name+1 |  sta  $name+1")
                    }
                    "|" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  ora  $name |  sta  $name |  tya |  ora  $name+1 |  sta  $name+1")
                    }
                    "^" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  eor  $name |  sta  $name |  tya |  eor  $name+1 |  sta  $name+1")
                    }
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> throw AssemblyError("can only use integer datatypes here")
        }
    }

    private fun inplaceModification_float_value_to_variable(name: String, operator: String, value: PtExpression, scope: IPtSubroutine) {
        asmgen.assignExpressionToRegister(value, RegisterOrPair.FAC1)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "+" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FADD
                """)
            }
            "-" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FMULT
                """)
            }
            "/" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
        asmgen.restoreRegisterLocal(CpuRegister.X)
    }

    private fun inplaceModification_float_variable_to_variable(name: String, operator: String, ident: PtIdentifier, scope: IPtSubroutine) {
        val valueDt = ident.type
        if(valueDt != DataType.FLOAT)
            throw AssemblyError("float variable expected")

        val otherName = asmgen.asmVariableName(ident)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "+" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.FADD
                """)
            }
            "-" -> {
                asmgen.out("""
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.FMULT
                """)
            }
            "/" -> {
                asmgen.out("""
                    lda  #<$otherName
                    ldy  #>$otherName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        // store Fac1 back into memory
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
        asmgen.restoreRegisterLocal(CpuRegister.X)
    }

    private fun inplaceModification_float_litval_to_variable(name: String, operator: String, value: Double, scope: IPtSubroutine) {
        val constValueName = allocator.getFloatAsmConst(value)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "+" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.FADD
                """)
            }
            "-" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FSUB
                """)
            }
            "*" -> {
                // assume that code optimization is already done on the AST level for special cases such as 0, 1, 2...
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.MOVFM
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.FMULT
                """)
            }
            "/" -> {
                if (value == 0.0)
                    throw AssemblyError("division by zero")
                asmgen.out("""
                    lda  #<$constValueName
                    ldy  #>$constValueName
                    jsr  floats.MOVFM
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.FDIV
                """)
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
        // store Fac1 back into memory
        asmgen.out("""
            ldx  #<$name
            ldy  #>$name
            jsr  floats.MOVMF
        """)
        asmgen.restoreRegisterLocal(CpuRegister.X)
    }
}

private fun AsmAssignSource.toAstExpression(scope: PtNamedNode): PtExpression {
    return when(kind) {
        SourceStorageKind.LITERALNUMBER -> this.number!!
        SourceStorageKind.VARIABLE -> {
            val ident = PtIdentifier(scope.scopedName + '.' + asmVarname, datatype, Position.DUMMY)
            ident.parent = scope
            ident
        }
        SourceStorageKind.ARRAY -> this.array!!
        SourceStorageKind.MEMORY -> this.memory!!
        SourceStorageKind.EXPRESSION -> this.expression!!
        SourceStorageKind.REGISTER -> {
            if(register in Cx16VirtualRegisters) {
                val ident = PtIdentifier("cx16.${register!!.name.lowercase()}", DataType.UWORD, position = scope.position)
                ident.parent = scope
                ident
            } else {
                throw AssemblyError("no ast expr possible for source register $register")
            }
        }
        else -> throw AssemblyError("invalid assign source kind $kind")
    }
}

private fun AsmAssignTarget.toAstExpression(): PtExpression {
    return when(kind) {
        TargetStorageKind.VARIABLE -> {
            val ident = PtIdentifier((this.scope as PtNamedNode).scopedName + '.' + asmVarname, datatype, origAstTarget?.position ?: Position.DUMMY)
            ident.parent = this.scope
            ident
        }
        TargetStorageKind.ARRAY -> this.array!!
        TargetStorageKind.MEMORY -> this.memory!!
        TargetStorageKind.REGISTER -> {
            if(register in Cx16VirtualRegisters) {
                val ident = PtIdentifier("cx16.${register!!.name.lowercase()}", DataType.UWORD, position = this.origAssign.position)
                ident.parent =  (this.scope as? PtNamedNode) ?: this.origAstTarget!!
                ident
            } else {
                throw AssemblyError("no ast expr possible for target register $register")
            }
        }
        else -> throw AssemblyError("invalid assign target kind $kind")
    }
}
