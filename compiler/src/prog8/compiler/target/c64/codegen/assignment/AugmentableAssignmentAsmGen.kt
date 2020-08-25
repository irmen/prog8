package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.toHex

internal class AugmentableAssignmentAsmGen(private val program: Program,
                                           private val assignmentAsmGen: AssignmentAsmGen,
                                           private val asmgen: AsmGen) {
    fun translate(assign: AsmAssignment) {
        require(assign.isAugmentable)
        require(assign.source.kind== SourceStorageKind.EXPRESSION)

        val value = assign.source.expression!!
        when (value) {
            is PrefixExpression -> {
                // A = -A , A = +A, A = ~A, A = not A
                val type = value.inferType(program).typeOrElse(DataType.STRUCT)
                when (value.operator) {
                    "+" -> {
                    }
                    "-" -> inplaceNegate(assign.target, type)
                    "~" -> inplaceInvert(assign.target, type)
                    "not" -> inplaceBooleanNot(assign.target, type)
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

        if (binExpr.operator in associativeOperators) {
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

        throw FatalAstException("assignment should be augmentable $binExpr")
    }

    private fun inplaceModification(target: AsmAssignTarget, operator: String, value: Expression) {
        val valueLv = (value as? NumericLiteralValue)?.number
        val ident = value as? IdentifierReference

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    in ByteDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(target.asmVarname, target.datatype, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(target.asmVarname, target.datatype, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
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
                            // TODO more specialized code for types such as memory read etc.
//                            value is DirectMemoryRead -> {
//                                println("warning: slow stack evaluation used (8):  $name $operator= ${value::class.simpleName} at ${value.position}") // TODO
//                                // assignmentAsmGen.translateOtherAssignment(origAssign)
//                                asmgen.translateExpression(value.addressExpression)
//                                asmgen.out("""
//                                    jsr  prog8_lib.read_byte_from_address_on_stack
//                                    sta  ...
//                                    inx
//                                    """)
//                                inplaceModification_word_value_to_variable(name, operator, )
//                                // TODO
//                            }
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                            }
                            else -> inplaceModification_word_value_to_variable(target.asmVarname, target.datatype, operator, value)
                        }
                    }
                    DataType.FLOAT -> {
                        when {
                            valueLv != null -> inplaceModification_float_litval_to_variable(target.asmVarname, operator, valueLv.toDouble())
                            ident != null -> inplaceModification_float_variable_to_variable(target.asmVarname, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_float_value_to_variable(target.asmVarname, operator, value)
                            }
                            else -> inplaceModification_float_value_to_variable(target.asmVarname, operator, value)
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
                            // TODO more specialized code for types such as memory read etc.
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
                            valueLv != null -> inplaceModification_byte_litval_to_memory(pointer, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_memory(pointer, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_memory(pointer, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_memory(pointer, operator, value)
                        }
                    }
                    else -> {
                        println("warning: slow stack evaluation used (1): ${memory.addressExpression::class.simpleName} at ${memory.addressExpression.position}") // TODO optimize...
                        asmgen.translateExpression(memory.addressExpression)
                        // TODO buggy?:
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  sta  P8ZP_SCRATCH_B1")
                        val zp = CompilationTarget.machine.zeropage
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(zp.SCRATCH_B1.toHex(), DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(zp.SCRATCH_B1.toHex(), DataType.UBYTE, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator)) return
                                inplaceModification_byte_value_to_variable(zp.SCRATCH_B1.toHex(), DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(zp.SCRATCH_B1.toHex(), DataType.UBYTE, operator, value)
                        }
                        asmgen.out("  lda  P8ZP_SCRATCH_B1 |  jsr  prog8_lib.write_byte_to_address_on_stack | inx")
                    }
                }
            }
            TargetStorageKind.ARRAY -> {
                println("*** TODO optimize simple inplace array assignment ${target.array}  $operator=  $value")
                assignmentAsmGen.translateNormalAssignment(target.origAssign) // TODO get rid of this fallback for the most common cases here
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun tryRemoveRedundantCast(value: TypecastExpression, target: AsmAssignTarget, operator: String): Boolean {
        if (target.datatype == value.type) {
            val childDt = value.expression.inferType(program).typeOrElse(DataType.STRUCT)
            if (value.type.equalsSize(childDt) || value.type.largerThan(childDt)) {
                // this typecast is redundant here; the rest of the code knows how to deal with the uncasted value.
                inplaceModification(target, operator, value.expression)
                return true
            }
        }
        return false
    }

    private fun inplaceModification_float_value_to_variable(name: String, operator: String, value: Expression) {
        // this should be the last resort for code generation for this,
        // because the value is evaluated onto the eval stack (=slow).
        println("warning: slow stack evaluation used (2):  $name $operator= ${value::class.simpleName} at ${value.position}") // TODO
        asmgen.translateExpression(value)
        when (operator) {
            "**" -> TODO("pow")
            "+" -> {
                asmgen.out("""
                            jsr  c64flt.pop_float_fac1
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FADD
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
            }
            "-" -> {
                asmgen.out("""
                            jsr  c64flt.pop_float_fac1
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FSUB
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
            }
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> {
                TODO("div")
                // asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            }
            "%" -> {
                TODO("float remainder???")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
    }

    private fun inplaceModification_float_variable_to_variable(name: String, operator: String, ident: IdentifierReference) {
        val valueDt = ident.targetVarDecl(program.namespace)!!.datatype
        if(valueDt != DataType.FLOAT)
            throw AssemblyError("float variable expected")

        val otherName = asmgen.asmIdentifierName(ident)
        when (operator) {
            "**" -> TODO("pow")
            "+" -> TODO("+")
            "-" -> TODO("-")
            "*" -> {
                asmgen.out("""
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            lda  #<$otherName
                            ldy  #>$otherName
                            jsr  c64flt.FMULT
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
            }
            "/" -> TODO("div")
            "%" -> TODO("float remainder???")
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
    }

    private fun inplaceModification_float_litval_to_variable(name: String, operator: String, value: Double) {
        val constValueName = asmgen.getFloatAsmConst(value)
        when (operator) {
            "**" -> TODO("pow")
            "+" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            lda  #<$constValueName
                            ldy  #>$constValueName
                            jsr  c64flt.FADD
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
            }
            "-" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<$constValueName
                            ldy  #>$constValueName
                            jsr  c64flt.MOVFM
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FSUB
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
            }
            "*" -> TODO("mul")
            "/" -> {
                if (value == 0.0)
                    throw AssemblyError("division by zero")
                TODO("div")
            }
            "%" -> {
                if (value == 0.0)
                    throw AssemblyError("division by zero")
                TODO("float remainder???")
            }
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
    }

    private fun inplaceModification_byte_value_to_memory(pointervar: IdentifierReference, operator: String, value: Expression) {
        println("warning: slow stack evaluation used (3):  @(${pointervar.nameInSource.last()}) $operator= ${value::class.simpleName} at ${value.position}") // TODO
        asmgen.translateExpression(value)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" clc |  adc  P8ESTACK_LO+1,x")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "-" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" sec |  sbc  P8ESTACK_LO+1,x")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            "%" -> {
                TODO("byte remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> TODO("ubyte asl")
            ">>" -> TODO("ubyte lsr")
            "&" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" and  P8ESTACK_LO+1,x")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "^" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" xor  P8ESTACK_LO+1,x")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "|" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" ora  P8ESTACK_LO+1,x")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.out(" inx")
    }

    private fun inplaceModification_byte_variable_to_memory(pointervar: IdentifierReference, operator: String, value: IdentifierReference) {
        val otherName = asmgen.asmIdentifierName(value)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" clc |  adc  $otherName")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "-" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" sec |  sbc  $otherName")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            "%" -> {
                TODO("byte remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> TODO("ubyte asl")
            ">>" -> TODO("ubyte lsr")
            "&" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" and  $otherName")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "^" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" xor  $otherName")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "|" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" ora  $otherName")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_litval_to_memory(pointervar: IdentifierReference, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" clc |  adc  #$value")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "-" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" sec |  sbc  #$value")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                TODO("div")
                // asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            }
            "%" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                TODO("byte remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> {
                if (value > 0) {
                    val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out(" asl  a") }
                    if(ptrOnZp)
                        asmgen.out("  sta  ($sourceName),y")
                    else
                        asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
                }
            }
            ">>" -> {
                if (value > 0) {
                    val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                    repeat(value) { asmgen.out(" lsr  a") }
                    if(ptrOnZp)
                        asmgen.out("  sta  ($sourceName),y")
                    else
                        asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
                }
            }
            "&" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" and  #$value")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "^" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" xor  #$value")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            "|" -> {
                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(pointervar)
                asmgen.out(" ora  #$value")
                if(ptrOnZp)
                    asmgen.out("  sta  ($sourceName),y")
                else
                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_word_litval_to_variable(name: String, dt: DataType, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            // TODO use the + and - optimizations in the expression asm code as well.
            "+" -> {
                when {
                    value<0x0100 -> asmgen.out("""
                        lda  $name
                        clc
                        adc  #$value
                        sta  $name
                        bcc  +
                        inc  $name+1
+                           """)
                    value==0x0100 -> asmgen.out(" inc  $name+1")
                    value==0x0200 -> asmgen.out(" inc  $name+1 |  inc  $name+1")
                    value==0x0300 -> asmgen.out(" inc  $name+1 |  inc  $name+1 |  inc  $name+1")
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
                    value<0x0100 -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  #$value
                        sta  $name
                        bcs  +
                        dec  $name+1
+                           """)
                    value==0x0100 -> asmgen.out(" dec  $name+1")
                    value==0x0200 -> asmgen.out(" dec  $name+1 |  dec  $name+1")
                    value==0x0300 -> asmgen.out(" dec  $name+1 |  dec  $name+1 |  dec  $name+1")
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
                // TODO what about the optimized routines?
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
            "/" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                TODO("word   $name  /=  $value")
                // asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            }
            "%" -> {
                if(value==0)
                    throw AssemblyError("division by zero")
                TODO("word remainder $value")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> {
                repeat(value) { asmgen.out(" asl  $name |  rol  $name+1") }
            }
            ">>" -> {
                if (value > 0) {
                    if(dt==DataType.UWORD) {
                        repeat(value) { asmgen.out("  lsr  $name+1 |  ror  $name")}
                    } else {
                        repeat(value) { asmgen.out("  lda  $name+1 |  asl  a |  ror  $name+1 |  ror  $name") }
                    }
                }
            }
            "&" -> {
                when {
                    value and 255 == 0 -> TODO("only high byte")
                    value < 0x0100 -> TODO("only low byte")
                    else -> asmgen.out(" lda  $name |  and  #<$value |  sta  $name |  lda  $name+1 |  and  #>$value |  sta  $name+1")
                }
            }
            "^" -> {
                when {
                    value and 255 == 0 -> TODO("only high byte")
                    value < 0x0100 -> TODO("only low byte")
                    else -> asmgen.out(" lda  $name |  xor  #<$value |  sta  $name |  lda  $name+1 |  xor  #>$value |  sta  $name+1")
                }
            }
            "|" -> {
                when {
                    value and 255 == 0 -> TODO("only high byte")
                    value < 0x0100 -> TODO("only low byte")
                    else -> asmgen.out(" lda  $name |  ora  #<$value |  sta  $name |  lda  $name+1 |  ora  #>$value |  sta  $name+1")
                }
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_word_variable_to_variable(name: String, dt: DataType, operator: String, ident: IdentifierReference) {
        val otherName = asmgen.asmIdentifierName(ident)
        val valueDt = ident.targetVarDecl(program.namespace)!!.datatype
        when (valueDt) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> asmgen.out("""
                        lda  $name
                        clc
                        adc  $otherName
                        sta  $name
                        bcc  +
                        inc  $name+1
+                           """)
                    "-" -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  $otherName
                        sta  $name
                        bcs  +
                        dec  $name+1
+                           """)
                    "*" -> TODO("mul")
                    "/" -> TODO("div")
                    "%" -> TODO("word remainder")
                    "<<" -> {
                        asmgen.out("""
                        ldy  $otherName
-                       asl  $name
                        rol  $name+1
                        dey
                        bne  -""")
                    }
                    ">>" -> {
                        if(dt==DataType.UWORD) {
                            asmgen.out("""
                            ldy  $otherName
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -""")
                        } else {
                            asmgen.out("""
                            ldy  $otherName
-                           lda  $name+1
                            asl  a
                            ror  $name+1
                            ror  $name
                            dey
                            bne  -""")
                        }
                    }
                    "&" -> TODO("bitand")
                    "^" -> TODO("bitxor")
                    "|" -> TODO("bitor")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> asmgen.out(" lda  $name |  clc |  adc  $otherName |  sta  $name |  lda  $name+1 |  adc  $otherName+1 |  sta  $name+1")
                    "-" -> asmgen.out(" lda  $name |  sec |  sbc  $otherName |  sta  $name |  lda  $name+1 |  sbc  $otherName+1 |  sta  $name+1")
                    "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
                    "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
                    "%" -> {
                        TODO("word remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
                    }
                    "<<", ">>" -> throw AssemblyError("shift by a word value not supported, max is a byte")
                    "&" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name |  lda  $name+1 |  and  $otherName+1 |  sta  $name+1")
                    "^" -> asmgen.out(" lda  $name |  xor  $otherName |  sta  $name |  lda  $name+1 |  xor  $otherName+1 |  sta  $name+1")
                    "|" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name |  lda  $name+1 |  ora  $otherName+1 |  sta  $name+1")
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
        println("warning: slow stack evaluation used (4):  $name $operator= ${value::class.simpleName} at ${value.position}") // TODO
        asmgen.translateExpression(value)
        val valueDt = value.inferType(program).typeOrElse(DataType.STRUCT)

        when(valueDt) {
            in ByteDatatypes -> {
                // the other variable is a BYTE type so optimize for that
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> asmgen.out("""
                        lda  $name
                        clc
                        adc  P8ESTACK_LO+1,x
                        sta  $name
                        bcc  +
                        inc  $name+1
+                           """)
                    "-" -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  P8ESTACK_LO+1,x
                        sta  $name
                        bcs  +
                        dec  $name+1
+                           """)
                    "*" -> TODO("mul")
                    "/" -> TODO("div")
                    "%" -> TODO("word remainder")
                    "<<" -> {
                        asmgen.translateExpression(value)
                        asmgen.out("""
                        inx
                        ldy  P8ESTACK_LO,x
                        beq  +
-                   	asl  $name
                        rol  $name+1
                        dey
                        bne  -
+""")
                    }
                    ">>" -> {
                        asmgen.translateExpression(value)
                        if(dt==DataType.UWORD) {
                        asmgen.out("""
                            inx
                            ldy  P8ESTACK_LO,x
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""") }
                        else {
                            asmgen.out("""
                            inx
                            ldy  P8ESTACK_LO,x
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
                    "&" -> TODO("bitand")
                    "^" -> TODO("bitxor")
                    "|" -> TODO("bitor")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            in WordDatatypes -> {
                // the value is a proper 16-bit word, so use both bytes of it.
                when (operator) {
                    // note: ** (power) operator requires floats.
                    "+" -> asmgen.out(" lda  $name |  clc |  adc  P8ESTACK_LO+1,x |  sta  $name |  lda  $name+1 |  adc  P8ESTACK_HI+1,x |  sta  $name+1")
                    "-" -> asmgen.out(" lda  $name |  sec |  sbc  P8ESTACK_LO+1,x |  sta  $name |  lda  $name+1 |  sbc  P8ESTACK_HI+1,x |  sta  $name+1")
                    "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
                    "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
                    "%" -> {
                        TODO("word remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
                    }
                    "<<", ">>" -> throw AssemblyError("shift by a word value not supported, max is a byte")
                    "&" -> asmgen.out(" lda  $name |  and  P8ESTACK_LO+1,x |  sta  $name | lda  $name+1 |  and  P8ESTACK_HI+1,x  |  sta  $name+1")
                    "^" -> asmgen.out(" lda  $name |  xor  P8ESTACK_LO+1,x |  sta  $name | lda  $name+1 |  xor  P8ESTACK_HI+1,x  |  sta  $name+1")
                    "|" -> asmgen.out(" lda  $name |  ora  P8ESTACK_LO+1,x |  sta  $name | lda  $name+1 |  ora  P8ESTACK_HI+1,x  |  sta  $name+1")
                    else -> throw AssemblyError("invalid operator for in-place modification $operator")
                }
            }
            else -> {
                throw AssemblyError("can only use integer datatypes here")
            }
        }

        asmgen.out(" inx")
    }

    private fun inplaceModification_byte_value_to_variable(name: String, dt: DataType, operator: String, value: Expression) {
        // this should be the last resort for code generation for this,
        // because the value is evaluated onto the eval stack (=slow).
        println("warning: slow stack evaluation used (5):  $name $operator= ${value::class.simpleName} at ${value.position}") // TODO
        asmgen.translateExpression(value)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out(" lda  $name |  clc |  adc  P8ESTACK_LO+1,x |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  P8ESTACK_LO+1,x |  sta  $name")
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            "%" -> {
                TODO("byte remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> {
                asmgen.translateExpression(value)
                asmgen.out("""
                    inx
                    ldy  P8ESTACK_LO,x
                    beq  +
-                   asl  $name
                    dey
                    bne  -
+""")
            }
            ">>" -> {
                asmgen.translateExpression(value)
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        inx
                        ldy  P8ESTACK_LO,x
                        beq  +
-                       lsr  $name
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        inx
                        ldy  P8ESTACK_LO,x
                        beq  +
-                       lda  $name
                        asl  a
                        ror  $name
                        dey
                        bne  -
+""")
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  P8ESTACK_LO+1,x |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  xor  P8ESTACK_LO+1,x |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  P8ESTACK_LO+1,x |  sta  $name")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.out(" inx")
    }

    private fun inplaceModification_byte_variable_to_variable(name: String, dt: DataType, operator: String, ident: IdentifierReference) {
        val otherName = asmgen.asmIdentifierName(ident)
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out(" lda  $name |  clc |  adc  $otherName |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  $otherName |  sta  $name")
            "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
            "%" -> {
                TODO("byte remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
            }
            "<<" -> {
                asmgen.out("""
                    ldy  $otherName
-                   asl  $name
                    dey
                    bne  -""")
            }
            ">>" -> {
                if(dt==DataType.UBYTE) {
                    asmgen.out("""
                        ldy  $otherName
-                       lsr  $name
                        dey
                        bne  -""")
                } else {
                    asmgen.out("""
                        ldy  $otherName
-                       lda  $name
                        asl  a
                        ror  $name
                        dey
                        bne  -""")
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  $otherName |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  xor  $otherName |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  $otherName |  sta  $name")
            "and" -> asmgen.out("""
                lda  $name
                and  $otherName
                beq  +
                lda  #1
+               sta  $name""")
            "or" -> asmgen.out("""
                lda  $name
                ora  $otherName
                beq  +
                lda  #1
+               sta  $name""")
            "xor" -> asmgen.out("""
                lda  $name
                eor  $otherName
                beq  +
                lda  #1
+               sta  $name""")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_litval_to_variable(name: String, dt: DataType, operator: String, value: Int) {
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> asmgen.out(" lda  $name |  clc |  adc  #$value |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  #$value |  sta  $name")
            "*" -> {
                // TODO what about the optimized routines?
                TODO("$dt mul  $name *=  $value")
                // asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
            }
            "/" -> {
                if (dt == DataType.UBYTE) {
                    asmgen.out("""
                        lda  $name
                        ldy  #$value
                        jsr  math.divmod_ub
                        sty  $name                                              
                    """)
                } else {
                    // BYTE
                    // requires to use unsigned division and fix sign afterwards, see idiv_b in prog8lib
                    TODO("BYTE div  $name /=  $value")
                }
            }
            "%" -> {
                if(dt==DataType.BYTE)
                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                asmgen.out("""
                    lda  $name
                    ldy  #$value
                    jsr  math.divmod_ub
                    sta  $name""")
            }
            "<<" -> {
                repeat(value) { asmgen.out(" asl  $name") }
            }
            ">>" -> {
                if(value>0) {
                    if (dt == DataType.UBYTE) {
                        repeat(value) { asmgen.out(" lsr  $name") }
                    } else {
                        repeat(value) { asmgen.out(" lda  $name | asl  a |  ror  $name") }
                    }
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  #$value |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  xor  #$value |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  #$value |  sta  $name")
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceCast(target: AsmAssignTarget, cast: TypecastExpression, position: Position) {
        val outerCastDt = cast.type
        val innerCastDt = (cast.expression as? TypecastExpression)?.type

        if (innerCastDt == null) {
            // simple typecast where the value is the target
            when (target.datatype) {
                DataType.UBYTE, DataType.BYTE -> { /* byte target can't be casted to anything else at all */ }
                DataType.UWORD, DataType.WORD -> {
                    when (outerCastDt) {
                        DataType.UBYTE, DataType.BYTE -> {
                            when(target.kind) {
                                TargetStorageKind.VARIABLE -> asmgen.out(" lda  #0 |  sta  ${target.asmVarname}+1")
                                TargetStorageKind.ARRAY -> {
                                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, target.datatype, CpuRegister.Y, true)
                                    asmgen.out("  lda  #0 |  sta  ${target.asmVarname},y")
                                }
                                TargetStorageKind.STACK -> asmgen.out(" lda  #0 |  sta  P8ESTACK_HI+1,x")
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

    private fun inplaceBooleanNot(target: AsmAssignTarget, dt: DataType) {
        when (dt) {
            DataType.UBYTE -> {
                when(target.kind) {
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
                                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(mem.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    beq  +
                                    lda  #1
+                                   eor  #1""")
                                if(ptrOnZp)
                                    asmgen.out("  sta  ($sourceName),y")
                                else
                                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
                            }
                            else -> {
                                println("warning: slow stack evaluation used (6): ${mem.addressExpression::class.simpleName} at ${mem.addressExpression.position}") // TODO
                                asmgen.translateExpression(mem.addressExpression)
                                asmgen.out("""
                                    jsr  prog8_lib.read_byte_from_address_on_stack
                                    beq  +
                                    lda  #1
+                                   eor  #1                                    
                                    jsr  prog8_lib.write_byte_to_address_on_stack
                                    inx""")
                            }
                        }
                    }
                    TargetStorageKind.ARRAY -> {
                        TODO("in-place not of ubyte array")
                    }
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            DataType.UWORD -> {
                when(target.kind) {
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
                    TargetStorageKind.MEMORY -> throw AssemblyError("no asm gen for uword-memory not")
                    TargetStorageKind.ARRAY -> TODO("in-place not of uword array")
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            else -> throw AssemblyError("boolean-not of invalid type")
        }
    }

    private fun inplaceInvert(target: AsmAssignTarget, dt: DataType) {
        when (dt) {
            DataType.UBYTE -> {
                when(target.kind) {
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
                                val (ptrOnZp, sourceName) = asmgen.loadByteFromPointerIntoA(memory.addressExpression as IdentifierReference)
                                asmgen.out("  eor  #255")
                                if(ptrOnZp)
                                    asmgen.out("  sta  ($sourceName),y")
                                else
                                    asmgen.out("  sta  (P8ZP_SCRATCH_W1),y")
                            }
                            else -> {
                                println("warning: slow stack evaluation used (7): ${memory.addressExpression::class.simpleName} at ${memory.addressExpression.position}") // TODO
                                asmgen.translateExpression(memory.addressExpression)
                                asmgen.out("""
                                    jsr  prog8_lib.read_byte_from_address_on_stack
                                    eor  #255
                                    jsr  prog8_lib.write_byte_to_address_on_stack
                                    inx""")
                            }
                        }
                    }
                    TargetStorageKind.ARRAY -> {
                        TODO("in-place invert ubyte array")
                    }
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            DataType.UWORD -> {
                when(target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            lda  ${target.asmVarname}
                            eor  #255
                            sta  ${target.asmVarname}
                            lda  ${target.asmVarname}+1
                            eor  #255
                            sta  ${target.asmVarname}+1""")
                    }
                    TargetStorageKind.MEMORY -> throw AssemblyError("no asm gen for uword-memory invert")
                    TargetStorageKind.ARRAY -> TODO("in-place invert uword array")
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }

    private fun inplaceNegate(target: AsmAssignTarget, dt: DataType) {
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
                    TargetStorageKind.MEMORY -> throw AssemblyError("can't in-place negate memory ubyte")
                    TargetStorageKind.ARRAY -> TODO("in-place negate byte array")
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            DataType.WORD -> {
                when(target.kind) {
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
                    TargetStorageKind.ARRAY -> TODO("in-place negate word array")
                    TargetStorageKind.MEMORY -> throw AssemblyError("no asm gen for word memory negate")
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            DataType.FLOAT -> {
                when(target.kind) {
                    TargetStorageKind.VARIABLE -> {
                        asmgen.out("""
                            stx  P8ZP_SCRATCH_REG_X
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  c64flt.MOVFM
                            jsr  c64flt.NEGOP
                            ldx  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  c64flt.MOVMF
                            ldx  P8ZP_SCRATCH_REG_X
                        """)
                    }
                    TargetStorageKind.ARRAY -> TODO("in-place negate float array")
                    TargetStorageKind.MEMORY -> throw AssemblyError("no asm gen for float memory negate")
                    TargetStorageKind.REGISTER -> TODO()
                    TargetStorageKind.STACK -> TODO()
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

}
