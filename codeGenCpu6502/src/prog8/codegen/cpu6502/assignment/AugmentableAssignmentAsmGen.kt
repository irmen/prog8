package prog8.codegen.cpu6502.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.Subroutine
import prog8.ast.toHex
import prog8.codegen.cpu6502.AsmGen
import prog8.compilerinterface.AssemblyError
import prog8.compilerinterface.CpuType


internal class AugmentableAssignmentAsmGen(private val program: Program,
                                           private val assignmentAsmGen: AssignmentAsmGen,
                                           private val asmgen: AsmGen
) {
    fun translate(assign: AsmAssignment) {
        require(assign.isAugmentable)
        require(assign.source.kind== SourceStorageKind.EXPRESSION)

        when (val value = assign.source.expression!!) {
            is PrefixExpression -> {
                // A = -A , A = +A, A = ~A, A = not A
                val target = assignmentAsmGen.virtualRegsToVariables(assign.target)
                val itype = value.inferType(program)
                val type = itype.getOrElse { throw AssemblyError("unknown dt") }
                when (value.operator) {
                    "+" -> {}
                    "-" -> inplaceNegate(target, type)
                    "~" -> inplaceInvert(target, type)
                    "not" -> inplaceBooleanNot(target, type)
                    else -> throw AssemblyError("invalid prefix operator")
                }
            }
            is TypecastExpression -> inplaceCast(assign.target, value, assign.position)
            is BinaryExpression -> inplaceBinary(assign.target, value)
            else -> throw AssemblyError("invalid aug assign value type")
        }
    }

    private fun inplaceBinary(target: AsmAssignTarget, binExpr: BinaryExpression) {
        val astTarget = target.origAstTarget!!
        if (binExpr.left isSameAs astTarget) {
            // A = A <operator> Something
            return inplaceModification(target, binExpr.operator, binExpr.right)
        }

        if (binExpr.operator in AssociativeOperators) {
            if (binExpr.right isSameAs astTarget) {
                // A = 5 <operator> A
                return inplaceModification(target, binExpr.operator, binExpr.left)
            }

            val leftBinExpr = binExpr.left as? BinaryExpression
            if (leftBinExpr?.operator == binExpr.operator) {
                // TODO better optimize the chained asm to avoid intermediate stores/loads?
                when {
                    binExpr.right isSameAs astTarget -> {
                        // A = (x <associative-operator> y) <same-operator> A
                        inplaceModification(target, binExpr.operator, leftBinExpr.left)
                        inplaceModification(target, binExpr.operator, leftBinExpr.right)
                        return
                    }
                    leftBinExpr.left isSameAs astTarget -> {
                        // A = (A <associative-operator> x) <same-operator> y
                        inplaceModification(target, binExpr.operator, leftBinExpr.right)
                        inplaceModification(target, binExpr.operator, binExpr.right)
                        return
                    }
                    leftBinExpr.right isSameAs astTarget -> {
                        // A = (x <associative-operator> A) <same-operator> y
                        inplaceModification(target, binExpr.operator, leftBinExpr.left)
                        inplaceModification(target, binExpr.operator, binExpr.right)
                        return
                    }
                }
            }
            val rightBinExpr = binExpr.right as? BinaryExpression
            if (rightBinExpr?.operator == binExpr.operator) {
                when {
                    binExpr.left isSameAs astTarget -> {
                        // A = A <associative-operator> (x <same-operator> y)
                        inplaceModification(target, binExpr.operator, rightBinExpr.left)
                        inplaceModification(target, binExpr.operator, rightBinExpr.right)
                        return
                    }
                    rightBinExpr.left isSameAs astTarget -> {
                        // A = y <associative-operator> (A <same-operator> x)
                        inplaceModification(target, binExpr.operator, binExpr.left)
                        inplaceModification(target, binExpr.operator, rightBinExpr.right)
                        return
                    }
                    rightBinExpr.right isSameAs astTarget -> {
                        // A = y <associative-operator> (x <same-operator> y)
                        inplaceModification(target, binExpr.operator, binExpr.left)
                        inplaceModification(target, binExpr.operator, rightBinExpr.left)
                        return
                    }
                }
            }
        }

        val leftBinExpr = binExpr.left as? BinaryExpression
        val rightBinExpr = binExpr.right as? BinaryExpression
        if(leftBinExpr!=null && rightBinExpr==null) {
            if(leftBinExpr.left isSameAs astTarget) {
                // X = (X <oper> Right) <oper> Something
                inplaceModification(target, leftBinExpr.operator, leftBinExpr.right)
                inplaceModification(target, binExpr.operator, binExpr.right)
                return
            }
            if(leftBinExpr.right isSameAs astTarget) {
                // X = (Left <oper> X) <oper> Something
                if(leftBinExpr.operator in AssociativeOperators) {
                    inplaceModification(target, leftBinExpr.operator, leftBinExpr.left)
                    inplaceModification(target, binExpr.operator, binExpr.right)
                    return
                } else {
                    throw AssemblyError("operands in wrong order for non-associative operator")
                }
            }
        }
        if(leftBinExpr==null && rightBinExpr!=null) {
            if(rightBinExpr.left isSameAs astTarget) {
                // X = Something <oper> (X <oper> Right)
                if(binExpr.operator in AssociativeOperators) {
                    inplaceModification(target, rightBinExpr.operator, rightBinExpr.right)
                    inplaceModification(target, binExpr.operator, binExpr.left)
                    return
                } else {
                    throw AssemblyError("operands in wrong order for non-associative operator")
                }
            }
            if(rightBinExpr.right isSameAs astTarget) {
                // X = Something <oper> (Left <oper> X)
                if(binExpr.operator in AssociativeOperators && rightBinExpr.operator in AssociativeOperators) {
                    inplaceModification(target, rightBinExpr.operator, rightBinExpr.left)
                    inplaceModification(target, binExpr.operator, binExpr.left)
                    return
                } else {
                    throw AssemblyError("operands in wrong order for non-associative operator")
                }
            }
        }

        throw FatalAstException("assignment should follow augmentable rules $binExpr")
    }

    private fun inplaceModification(target: AsmAssignTarget, operator: String, origValue: Expression) {

        // the asm-gen code can deal with situations where you want to assign a byte into a word.
        // it will create the most optimized code to do this (so it type-extends for us).
        // But we can't deal with writing a word into a byte - explicit typeconversion is required
        val value = if(program.memsizer.memorySize(origValue.inferType(program).getOrElse { throw AssemblyError("unknown dt") }) > program.memsizer.memorySize(target.datatype)) {
            val typecast = TypecastExpression(origValue, target.datatype, true, origValue.position)
            typecast.linkParents(origValue.parent)
            typecast
        }
        else {
            origValue
        }

        val valueLv = (value as? NumericLiteralValue)?.number
        val ident = value as? IdentifierReference
        val memread = value as? DirectMemoryRead

        when (target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    in ByteDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(target.asmVarname, target.datatype, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(target.asmVarname, target.datatype, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable(target.asmVarname, target.datatype, operator, memread)
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
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
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                            }
                            else -> inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                        }
                    }
                    DataType.FLOAT -> {
                        when {
                            valueLv != null -> inplaceModification_float_litval_to_variable(target.asmVarname, operator, valueLv.toDouble(), target.scope!!)
                            ident != null -> inplaceModification_float_variable_to_variable(target.asmVarname, operator, ident, target.scope!!)
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
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
                when (memory.addressExpression) {
                    is NumericLiteralValue -> {
                        val addr = (memory.addressExpression as NumericLiteralValue).number.toInt()
                        // re-use code to assign a variable, instead this time, use a direct memory address
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(addr.toHex(), DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(addr.toHex(), DataType.UBYTE, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                        }
                    }
                    is IdentifierReference -> {
                        val pointer = memory.addressExpression as IdentifierReference
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_pointer(pointer, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_pointer(pointer, operator, ident)
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_pointer(pointer, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_pointer(pointer, operator, value)
                        }
                    }
                    else -> {
                        // TODO use some other evaluation here; don't use the estack to transfer the address to read/write from
                        asmgen.assignExpressionTo(memory.addressExpression, AsmAssignTarget(TargetStorageKind.STACK, program, asmgen, DataType.UWORD, memory.definingSubroutine))
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  sta  P8ZP_SCRATCH_B1")
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, ident)
                            memread != null -> inplaceModification_byte_memread_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, memread)
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable("P8ZP_SCRATCH_B1", DataType.UBYTE, operator, value)
                        }
                        asmgen.out("  lda  P8ZP_SCRATCH_B1 |  jsr  prog8_lib.write_byte_to_address_on_stack | inx")
                    }
                }
            }
            TargetStorageKind.ARRAY -> {
                with(target.array!!.indexer) {
                    val indexNum = indexExpr as? NumericLiteralValue
                    val indexVar = indexExpr as? IdentifierReference
                    when {
                        indexNum!=null -> {
                            val targetVarName = "${target.asmVarname} + ${indexNum.number.toInt()*program.memsizer.memorySize(target.datatype)}"
                            when (target.datatype) {
                                in ByteDatatypes -> {
                                    when {
                                        valueLv != null -> inplaceModification_byte_litval_to_variable(targetVarName, target.datatype, operator, valueLv.toInt())
                                        ident != null -> inplaceModification_byte_variable_to_variable(targetVarName, target.datatype, operator, ident)
                                        memread != null -> inplaceModification_byte_memread_to_variable(targetVarName, target.datatype, operator, memread)
                                        value is TypecastExpression -> {
                                            if (tryRemoveRedundantCast(value, target, operator)) return
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
                                        value is TypecastExpression -> {
                                            if (tryRemoveRedundantCast(value, target, operator)) return
                                            inplaceModification_word_value_to_variable(targetVarName, target.datatype, operator, value)
                                        }
                                        else -> inplaceModification_word_value_to_variable(targetVarName, target.datatype, operator, value)
                                    }
                                }
                                DataType.FLOAT -> {
                                    when {
                                        valueLv != null -> inplaceModification_float_litval_to_variable(targetVarName, operator, valueLv.toDouble(), target.scope!!)
                                        ident != null -> inplaceModification_float_variable_to_variable(targetVarName, operator, ident, target.scope!!)
                                        value is TypecastExpression -> {
                                            if (tryRemoveRedundantCast(value, target, operator)) return
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
                                            program,
                                            asmgen
                                        )
                                    val assign = AsmAssignment(target.origAssign.source, tgt, false, program.memsizer, value.position)
                                    assignmentAsmGen.translateNormalAssignment(assign)
                                    assignmentAsmGen.assignRegisterByte(target, CpuRegister.A)
                                }
                                in WordDatatypes -> {
                                    val tgt =
                                        AsmAssignTarget.fromRegisters(
                                            RegisterOrPair.AY,
                                            target.datatype == DataType.WORD, null,
                                            program,
                                            asmgen
                                        )
                                    val assign = AsmAssignment(target.origAssign.source, tgt, false, program.memsizer, value.position)
                                    assignmentAsmGen.translateNormalAssignment(assign)
                                    assignmentAsmGen.assignRegisterpairWord(target, RegisterOrPair.AY)
                                }
                                DataType.FLOAT -> {
                                    val tgt =
                                        AsmAssignTarget.fromRegisters(
                                            RegisterOrPair.FAC1,
                                            true, null,
                                            program,
                                            asmgen
                                        )
                                    val assign = AsmAssignment(target.origAssign.source, tgt, false, program.memsizer, value.position)
                                    assignmentAsmGen.translateNormalAssignment(assign)
                                    assignmentAsmGen.assignFAC1float(target)
                                }
                                else -> throw AssemblyError("weird type to do in-place modification on ${target.datatype}")
                            }
                        }
                        else -> throw AssemblyError("indexer expression should have been replaced by auto indexer var")
                    }
                }
            }
            TargetStorageKind.REGISTER -> throw AssemblyError("no asm gen for reg in-place modification")
            TargetStorageKind.STACK -> throw AssemblyError("no asm gen for stack in-place modification")
        }
    }

    private fun tryRemoveRedundantCast(value: TypecastExpression, target: AsmAssignTarget, operator: String): Boolean {
        if (target.datatype == value.type) {
            val childIDt = value.expression.inferType(program)
            val childDt = childIDt.getOrElse { throw AssemblyError("unknown dt") }
            if (value.type!=DataType.FLOAT && (value.type.equalsSize(childDt) || value.type.largerThan(childDt))) {
                // this typecast is redundant here; the rest of the code knows how to deal with the uncasted value.
                // (works for integer types, not for float.)
                inplaceModification(target, operator, value.expression)
                return true
            }
        }
        return false
    }

    private fun inplaceModification_byte_value_to_pointer(pointervar: IdentifierReference, operator: String, value: Expression) {
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
            "&", "and" -> asmgen.out(" and  P8ZP_SCRATCH_B1")
            "|", "or" -> asmgen.out(" ora  P8ZP_SCRATCH_B1")
            "^", "xor" -> asmgen.out(" eor  P8ZP_SCRATCH_B1")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.storeAIntoZpPointerVar(sourceName)
    }

    private fun inplaceModification_byte_variable_to_pointer(pointervar: IdentifierReference, operator: String, value: IdentifierReference) {
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
            "&", "and" -> asmgen.out(" and  $otherName")
            "|", "or" -> asmgen.out(" ora  $otherName")
            "^", "xor" -> asmgen.out(" eor  $otherName")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.storeAIntoZpPointerVar(sourceName)
    }

    private fun inplaceModification_byte_litval_to_pointer(pointervar: IdentifierReference, operator: String, value: Int) {
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
            "&", "and" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  and  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "|", "or" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  ora  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            "^", "xor" -> {
                val sourceName = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out("  eor  #$value")
                asmgen.storeAIntoZpPointerVar(sourceName)
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_value_to_variable(name: String, dt: DataType, operator: String, value: Expression) {
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
            "&", "and" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  and  $name |  sta  $name")
            }
            "|", "or" -> {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                asmgen.out("  ora  $name |  sta  $name")
            }
            "^", "xor" -> {
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

    private fun inplaceModification_byte_variable_to_variable(name: String, dt: DataType, operator: String, ident: IdentifierReference) {
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
            "&", "and" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name")
            "|", "or" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name")
            "^", "xor" -> asmgen.out(" lda  $name |  eor  $otherName |  sta  $name")
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
            "&", "and" -> asmgen.out(" lda  $name |  and  #$value |  sta  $name")
            "|", "or" -> asmgen.out(" lda  $name |  ora  #$value |  sta  $name")
            "^", "xor" -> asmgen.out(" lda  $name |  eor  #$value |  sta  $name")
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

    private fun inplaceModification_byte_memread_to_variable(name: String, dt: DataType, operator: String, memread: DirectMemoryRead) {
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
            "|", "or" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  ora  $name  |  sta  $name")
            }
            "&", "and" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  and  $name  |  sta  $name")
            }
            "^", "xor" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  eor  $name  |  sta  $name")
            }
            // TODO: tuned code for more operators
            else -> {
                inplaceModification_byte_value_to_variable(name, dt, operator, memread)
            }
        }
    }

    private fun inplaceModification_word_memread_to_variable(name: String, dt: DataType, operator: String, memread: DirectMemoryRead) {
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
            "|", "or" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  ora  $name  |  sta  $name")
            }
            "&", "and" -> {
                asmgen.translateDirectMemReadExpressionToRegAorStack(memread, false)
                asmgen.out("  and  $name  |  sta  $name")
                if(dt in WordDatatypes) {
                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                        asmgen.out("  stz  $name+1")
                    else
                        asmgen.out("  lda  #0 |  sta  $name+1")
                }
            }
            "^", "xor" -> {
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
            "&", "and" -> {
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
            "|", "or" -> {
                when {
                    value == 0 -> {}
                    value and 255 == 0 -> asmgen.out("  lda  $name+1 |  ora  #>$value |  sta  $name+1")
                    value < 0x0100 -> asmgen.out("  lda  $name |  ora  #$value |  sta  $name")
                    else -> asmgen.out("  lda  $name |  ora  #<$value |  sta  $name |  lda  $name+1 |  ora  #>$value |  sta  $name+1")
                }
            }
            "^", "xor" -> {
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

    private fun inplaceModification_word_variable_to_variable(name: String, dt: DataType, operator: String, ident: IdentifierReference) {
        val otherName = asmgen.asmVariableName(ident)
        when (val valueDt = ident.targetVarDecl(program)!!.datatype) {
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
                        asmgen.out("  lda  $otherName |  sta  P8ZP_SCRATCH_W1")
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  stz  P8ZP_SCRATCH_W1+1")
                        else
                            asmgen.out("  lda  #0 |  sta  P8ZP_SCRATCH_W1+1")
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
                    "&", "and" -> {
                        asmgen.out("  lda  $otherName |  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|", "or" -> asmgen.out("  lda  $otherName |  ora  $name |  sta  $name")
                    "^", "xor" -> asmgen.out("  lda  $otherName |  eor  $name |  sta  $name")
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
                    "&", "and" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name |  lda  $name+1 |  and  $otherName+1 |  sta  $name+1")
                    "|", "or" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name |  lda  $name+1 |  ora  $otherName+1 |  sta  $name+1")
                    "^", "xor" -> asmgen.out(" lda  $name |  eor  $otherName |  sta  $name |  lda  $name+1 |  eor  $otherName+1 |  sta  $name+1")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> {
                throw AssemblyError("can only use integer datatypes here")
            }
        }
    }

    private fun inplaceModification_word_value_to_variable(name: String, dt: DataType, operator: String, value: Expression) {
        // this should be the last resort for code generation for this,
        // because the value is evaluated onto the eval stack (=slow).

        val valueiDt = value.inferType(program)
        val valueDt = valueiDt.getOrElse { throw AssemblyError("unknown dt") }

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

        when (valueDt) {
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
                    "&", "and" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  and  $name |  sta  $name")
                        if(dt in WordDatatypes) {
                            if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out("  stz  $name+1")
                            else
                                asmgen.out("  lda  #0 |  sta  $name+1")
                        }
                    }
                    "|", "or" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("  ora  $name |  sta  $name")
                    }
                    "^", "xor" -> {
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
                    "&", "and" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  and  $name |  sta  $name |  tya |  and  $name+1 |  sta  $name+1")
                    }
                    "|", "or" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  ora  $name |  sta  $name |  tya |  ora  $name+1 |  sta  $name+1")
                    }
                    "^", "xor" -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                        asmgen.out("  eor  $name |  sta  $name |  tya |  eor  $name+1 |  sta  $name+1")
                    }
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> throw AssemblyError("can only use integer datatypes here")
        }
    }

    private fun inplaceModification_float_value_to_variable(name: String, operator: String, value: Expression, scope: Subroutine) {
        asmgen.assignExpressionToRegister(value, RegisterOrPair.FAC1)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "**" -> {
                asmgen.out("""
                    lda  #<$name
                    ldy  #>$name
                    jsr  floats.CONUPK
                    jsr  floats.FPWRT
                """)
            }
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

    private fun inplaceModification_float_variable_to_variable(name: String, operator: String, ident: IdentifierReference, scope: Subroutine) {
        val valueDt = ident.targetVarDecl(program)!!.datatype
        if(valueDt != DataType.FLOAT)
            throw AssemblyError("float variable expected")

        val otherName = asmgen.asmVariableName(ident)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "**" -> {
                if(asmgen.haveFPWRcall()) {
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.CONUPK
                        lda  #<$otherName
                        ldy  #>$otherName
                        jsr  floats.FPWR
                    """)
                } else
                    // cx16 doesn't have FPWR() only FPWRT()
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.CONUPK
                        lda  #<$otherName
                        ldy  #>$otherName
                        jsr  floats.MOVFM
                        jsr  floats.FPWRT
                    """)
            }
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

    private fun inplaceModification_float_litval_to_variable(name: String, operator: String, value: Double, scope: Subroutine) {
        val constValueName = asmgen.getFloatAsmConst(value)
        asmgen.saveRegisterLocal(CpuRegister.X, scope)
        when (operator) {
            "**" -> {
                if(asmgen.haveFPWRcall()) {
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.CONUPK
                        lda  #<$constValueName
                        ldy  #>$constValueName
                        jsr  floats.FPWR
                    """)
                } else
                    // cx16 doesn't have FPWR() only FPWRT()
                    asmgen.out("""
                        lda  #<$name
                        ldy  #>$name
                        jsr  floats.CONUPK
                        lda  #<$constValueName
                        ldy  #>$constValueName
                        jsr  floats.MOVFM
                        jsr  floats.FPWRT
                    """)
            }
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

    private fun inplaceCast(target: AsmAssignTarget, cast: TypecastExpression, position: Position) {
        val outerCastDt = cast.type
        val innerCastDt = (cast.expression as? TypecastExpression)?.type
        if (innerCastDt == null) {
            // simple typecast where the value is the target
            when (target.datatype) {
                DataType.UBYTE, DataType.BYTE -> { /* byte target can't be typecasted to anything else at all */ }
                DataType.UWORD, DataType.WORD -> {
                    when (outerCastDt) {
                        DataType.UBYTE, DataType.BYTE -> {
                            when (target.kind) {
                                TargetStorageKind.VARIABLE -> {
                                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                        asmgen.out(" stz  ${target.asmVarname}+1")
                                    else
                                        asmgen.out(" lda  #0 |  sta  ${target.asmVarname}+1")
                                }
                                TargetStorageKind.ARRAY -> {
                                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, target.datatype, CpuRegister.Y, true)
                                    asmgen.out("  lda  #0 |  sta  ${target.asmVarname},y")
                                }
                                TargetStorageKind.STACK -> {
                                    if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                        asmgen.out(" stz  P8ESTACK_HI+1,x")
                                    else
                                        asmgen.out(" lda  #0 |  sta  P8ESTACK_HI+1,x")
                                }
                                else -> throw AssemblyError("weird target")
                            }
                        }
                        DataType.UWORD, DataType.WORD, in IterableDatatypes -> {}
                        DataType.FLOAT -> throw AssemblyError("can't cast float in-place")
                        else -> throw AssemblyError("weird cast type")
                    }
                }
                DataType.FLOAT -> {
                    if (outerCastDt != DataType.FLOAT)
                        throw AssemblyError("in-place cast of a float makes no sense")
                }
                else -> throw AssemblyError("invalid cast target type")
            }
        } else {
            // typecast with nested typecast, that has the target as a value
            // calculate singular cast that is required
            val castDt = if (outerCastDt largerThan innerCastDt) innerCastDt else outerCastDt
            val value = (cast.expression as TypecastExpression).expression
            val resultingCast = TypecastExpression(value, castDt, false, position)
            inplaceCast(target, resultingCast, position)
        }
    }

    internal fun inplaceBooleanNot(target: AsmAssignTarget, dt: DataType) {
        when (dt) {
            DataType.UBYTE -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.MEMORY -> {
                        val mem = target.memory!!
                        when (mem.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (mem.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    beq  +
                                    lda  #1
+                                   eor  #1
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val sourceName = asmgen.loadByteFromPointerIntoA(mem.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    beq  +
                                    lda  #1
+                                   eor  #1""")
                                asmgen.storeAIntoZpPointerVar(sourceName)
                            }
                            else -> {
                                asmgen.assignExpressionToVariable(mem.addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD, target.scope)
                                asmgen.loadAFromZpPointerVar("P8ZP_SCRATCH_W2")
                                asmgen.out("""
                                    beq  +
                                    lda  #1
+                                   eor  #1""")
                                asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                            }
                        }
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out("""
                                cmp  #0
                                beq  +
                                lda  #1
+                               eor  #1""")
                            RegisterOrPair.X -> asmgen.out("""
                                txa
                                beq  +
                                lda  #1
+                               eor  #1
                                tax""")
                            RegisterOrPair.Y -> asmgen.out("""
                                tya
                                beq  +
                                lda  #1
+                               eor  #1
                                tay""")
                            else -> throw AssemblyError("invalid reg dt for byte not")
                        }
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for byte stack not")
                    else -> throw AssemblyError("no asm gen for in-place not of ubyte ${target.kind}")
                }
            }
            DataType.UWORD -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            ora  ${target.asmVarname}+1
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  ${target.asmVarname}
                            lsr  a
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> {
                                asmgen.out("""
                                    stx  P8ZP_SCRATCH_REG
                                    ora  P8ZP_SCRATCH_REG
                                    beq  +
                                    lda  #0
                                    tax
                                    beq  ++
        +                           lda  #1
        +""")
                            }
                            RegisterOrPair.AY -> {
                                asmgen.out("""
                                    sty  P8ZP_SCRATCH_REG
                                    ora  P8ZP_SCRATCH_REG
                                    beq  +
                                    lda  #0
                                    tay
                                    beq  ++
        +                           lda  #1
        +""")
                            }
                            RegisterOrPair.XY -> {
                                asmgen.out("""
                                    stx  P8ZP_SCRATCH_REG
                                    tya
                                    ora  P8ZP_SCRATCH_REG
                                    beq  +
                                    ldy  #0
                                    ldx  #0
                                    beq  ++
        +                           ldx  #1
        +""")
                            }
                            in Cx16VirtualRegisters -> throw AssemblyError("cx16 virtual regs should be variables, not real registers")
                            else -> throw AssemblyError("invalid reg dt for word not")
                        }
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for word stack not")
                    else -> throw AssemblyError("no asm gen for in-place not of uword for ${target.kind}")
                }
            }
            else -> throw AssemblyError("boolean-not of invalid type")
        }
    }

    internal fun inplaceInvert(target: AsmAssignTarget, dt: DataType) {
        when (dt) {
            DataType.UBYTE -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.MEMORY -> {
                        val memory = target.memory!!
                        when (memory.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (memory.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    eor  #255
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val sourceName = asmgen.loadByteFromPointerIntoA(memory.addressExpression as IdentifierReference)
                                asmgen.out("  eor  #255")
                                asmgen.out("  sta  ($sourceName),y")
                            }
                            else -> {
                                asmgen.assignExpressionToVariable(memory.addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD, target.scope)
                                asmgen.out("""
                                    ldy  #0
                                    lda  (P8ZP_SCRATCH_W2),y
                                    eor  #255""")
                                asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2")
                            }
                        }
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out("  eor  #255")
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #255 |  tax")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #255 |  tay")
                            else -> throw AssemblyError("invalid reg dt for byte invert")
                        }
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for byte stack invert")
                    else -> throw AssemblyError("no asm gen for in-place invert ubyte for ${target.kind}")
                }
            }
            DataType.UWORD -> {
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
                            else -> throw AssemblyError("invalid reg dt for word invert")
                        }
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for word stack invert")
                    else -> throw AssemblyError("no asm gen for in-place invert uword for ${target.kind}")
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }

    internal fun inplaceNegate(target: AsmAssignTarget, dt: DataType) {
        when (dt) {
            DataType.BYTE -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  ${target.asmVarname}
                            sta  ${target.asmVarname}""")
                    }
                    TargetStorageKind.REGISTER -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> {
                                if(asmgen.isTargetCpu(CpuType.CPU65c02))
                                    asmgen.out("  eor  #255 |  ina")
                                else
                                    asmgen.out("  eor  #255 |  clc |  adc  #1")

                            }
                            RegisterOrPair.X -> asmgen.out("  txa |  eor  #255 |  tax |  inx")
                            RegisterOrPair.Y -> asmgen.out("  tya |  eor  #255 |  tay |  iny")
                            else -> throw AssemblyError("invalid reg dt for byte negate")
                        }
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("memory is ubyte, can't in-place negate")
                    TargetStorageKind.STACK -> TODO("no asm gen for byte stack negate")
                    else -> throw AssemblyError("no asm gen for in-place negate byte")
                }
            }
            DataType.WORD -> {
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
                                    pha
                                    tya
                                    eor  #255
                                    adc  #0
                                    tay
                                    pla""")
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
                            else -> throw AssemblyError("invalid reg dt for word neg")
                        }
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for word stack negate")
                    else -> throw AssemblyError("no asm gen for in-place negate word")
                }
            }
            DataType.FLOAT -> {
                when (target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        // simply flip the sign bit in the float
                        asmgen.out("""
                            lda  ${target.asmVarname}+1
                            eor  #$80
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    TargetStorageKind.STACK -> TODO("no asm gen for float stack negate")
                    else -> throw AssemblyError("weird target kind for inplace negate float ${target.kind}")
                }
            }
            else -> throw AssemblyError("negate of invalid type $dt")
        }
    }

}
