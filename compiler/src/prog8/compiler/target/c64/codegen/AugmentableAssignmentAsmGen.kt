package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex

internal class AugmentableAssignmentAsmGen(private val program: Program,
                                           private val assignmentAsmGen: AssignmentAsmGen,
                                           private val asmgen: AsmGen) {
    fun translate(assign: Assignment) {
        require(assign.isAugmentable)

        when (assign.value) {
            is PrefixExpression -> {
                // A = -A , A = +A, A = ~A, A = not A
                val px = assign.value as PrefixExpression
                val type = px.inferType(program).typeOrElse(DataType.STRUCT)
                when (px.operator) {
                    "+" -> {
                    }
                    "-" -> inplaceNegate(assign.target, type)
                    "~" -> inplaceInvert(assign.target, type)
                    "not" -> inplaceBooleanNot(assign.target, type)
                    else -> throw AssemblyError("invalid prefix operator")
                }
            }
            is TypecastExpression -> inplaceCast(assign.target, assign.value as TypecastExpression, assign)
            is BinaryExpression -> inplaceBinary(assign.target, assign.value as BinaryExpression, assign)
            else -> throw AssemblyError("invalid aug assign value type")
        }
    }

    private fun inplaceBinary(target: AssignTarget, binExpr: BinaryExpression, assign: Assignment) {
        if (binExpr.left isSameAs target) {
            // A = A <operator> Something
            return inplaceModification(target, binExpr.operator, binExpr.right, assign)
        }

        if (binExpr.operator in associativeOperators) {
            if (binExpr.right isSameAs target) {
                // A = 5 <operator> A
                return inplaceModification(target, binExpr.operator, binExpr.left, assign)
            }

            val leftBinExpr = binExpr.left as? BinaryExpression
            if (leftBinExpr?.operator == binExpr.operator) {
                // TODO better optimize the chained asm to avoid intermediate stores/loads?
                when {
                    binExpr.right isSameAs target -> {
                        // A = (x <associative-operator> y) <same-operator> A
                        inplaceModification(target, binExpr.operator, leftBinExpr.left, assign)
                        inplaceModification(target, binExpr.operator, leftBinExpr.right, assign)
                        return
                    }
                    leftBinExpr.left isSameAs target -> {
                        // A = (A <associative-operator> x) <same-operator> y
                        inplaceModification(target, binExpr.operator, leftBinExpr.right, assign)
                        inplaceModification(target, binExpr.operator, binExpr.right, assign)
                        return
                    }
                    leftBinExpr.right isSameAs target -> {
                        // A = (x <associative-operator> A) <same-operator> y
                        inplaceModification(target, binExpr.operator, leftBinExpr.left, assign)
                        inplaceModification(target, binExpr.operator, binExpr.right, assign)
                        return
                    }
                }
            }
            val rightBinExpr = binExpr.right as? BinaryExpression
            if (rightBinExpr?.operator == binExpr.operator) {
                when {
                    binExpr.left isSameAs target -> {
                        // A = A <associative-operator> (x <same-operator> y)
                        inplaceModification(target, binExpr.operator, rightBinExpr.left, assign)
                        inplaceModification(target, binExpr.operator, rightBinExpr.right, assign)
                        return
                    }
                    rightBinExpr.left isSameAs target -> {
                        // A = y <associative-operator> (A <same-operator> x)
                        inplaceModification(target, binExpr.operator, binExpr.left, assign)
                        inplaceModification(target, binExpr.operator, rightBinExpr.right, assign)
                        return
                    }
                    rightBinExpr.right isSameAs target -> {
                        // A = y <associative-operator> (x <same-operator> y)
                        inplaceModification(target, binExpr.operator, binExpr.left, assign)
                        inplaceModification(target, binExpr.operator, rightBinExpr.left, assign)
                        return
                    }
                }
            }
        }

        throw FatalAstException("assignment should be augmentable  $assign\nleft=${binExpr.left}\nright=${binExpr.right}")
    }

    private fun inplaceModification(target: AssignTarget, operator: String, value: Expression, origAssign: Assignment) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress
        val valueLv = (value as? NumericLiteralValue)?.number
        val ident = value as? IdentifierReference

        when {
            identifier != null -> {
                val name = asmgen.asmIdentifierName(identifier)
                val dt = identifier.inferType(program).typeOrElse(DataType.STRUCT)
                when (dt) {
                    in ByteDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(name, dt, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(name, dt, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_byte_value_to_variable(name, dt, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(name, dt, operator, value)
                        }
                    }
                    in WordDatatypes -> {
                        when {
                            valueLv != null -> inplaceModification_word_litval_to_variable(name, dt, operator, valueLv.toInt())
                            ident != null -> inplaceModification_word_variable_to_variable(name, dt, operator, ident)
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
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_word_value_to_variable(name, dt, operator, value)
                            }
                            else -> inplaceModification_word_value_to_variable(name, dt, operator, value)
                        }
                    }
                    DataType.FLOAT -> {
                        when {
                            valueLv != null -> inplaceModification_float_litval_to_variable(name, operator, valueLv.toDouble())
                            ident != null -> inplaceModification_float_variable_to_variable(name, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_float_value_to_variable(name, operator, value)
                            }
                            else -> inplaceModification_float_value_to_variable(name, operator, value)
                        }
                    }
                    else -> throw AssemblyError("weird type to do in-place modification on $dt")
                }
            }
            memory != null -> {
                when (memory.addressExpression) {
                    is NumericLiteralValue -> {
                        val addr = (memory.addressExpression as NumericLiteralValue).number.toInt()
                        // re-use code to assign a variable, instead this time, use a direct memory address
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(addr.toHex(), DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(addr.toHex(), DataType.UBYTE, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(addr.toHex(), DataType.UBYTE, operator, value)
                        }
                    }
                    is IdentifierReference -> {
                        val name = asmgen.asmIdentifierName(memory.addressExpression as IdentifierReference)
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_memory(name, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_memory(name, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_byte_value_to_memory(name, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_memory(name, operator, value)
                        }
                    }
                    else -> {
                        println("warning: slow stack evaluation used (1): ${memory.addressExpression::class.simpleName} at ${memory.addressExpression.position}") // TODO optimize...
                        asmgen.translateExpression(memory.addressExpression)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  sta  ${C64Zeropage.SCRATCH_B1}")
                        // the original memory byte's value is now in the scratch B1 location.
                        when {
                            valueLv != null -> inplaceModification_byte_litval_to_variable(C64Zeropage.SCRATCH_B1.toHex(), DataType.UBYTE, operator, valueLv.toInt())
                            ident != null -> inplaceModification_byte_variable_to_variable(C64Zeropage.SCRATCH_B1.toHex(), DataType.UBYTE, operator, ident)
                            // TODO more specialized code for types such as memory read etc.
                            value is TypecastExpression -> {
                                if (tryRemoveRedundantCast(value, target, operator, origAssign)) return
                                inplaceModification_byte_value_to_variable(C64Zeropage.SCRATCH_B1.toHex(), DataType.UBYTE, operator, value)
                            }
                            else -> inplaceModification_byte_value_to_variable(C64Zeropage.SCRATCH_B1.toHex(), DataType.UBYTE, operator, value)
                        }
                        asmgen.out("  lda  ${C64Zeropage.SCRATCH_B1} |  jsr  prog8_lib.write_byte_to_address_on_stack | inx")
                    }
                }
            }
            arrayIdx != null -> {
                println("*** TODO optimize simple inplace array assignment $arrayIdx  $operator=  $value")
                assignmentAsmGen.translateOtherAssignment(origAssign) // TODO get rid of this fallback for the most common cases here
            }
        }
    }

    private fun tryRemoveRedundantCast(value: TypecastExpression, target: AssignTarget, operator: String, origAssign: Assignment): Boolean {
        val targetDt = target.inferType(program, origAssign).typeOrElse(DataType.STRUCT)
        if (targetDt == value.type) {
            val childDt = value.expression.inferType(program).typeOrElse(DataType.STRUCT)
            if (value.type.equalsSize(childDt) || value.type.largerThan(childDt)) {
                // this typecast is redundant here; the rest of the code knows how to deal with the uncasted value.
                inplaceModification(target, operator, value.expression, origAssign)
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
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FADD
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
                        """)
            }
            "-" -> {
                asmgen.out("""
                            jsr  c64flt.pop_float_fac1
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FSUB
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
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
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            lda  #<$otherName
                            ldy  #>$otherName
                            jsr  c64flt.FMULT
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
                        """)
            }
            "/" -> TODO("div")
            "%" -> TODO("float remainder???")
            else -> throw AssemblyError("invalid operator for in-place float modification $operator")
        }
    }

    private fun inplaceModification_float_litval_to_variable(name: String, operator: String, value: Double) {
        val constValueName = asmgen.getFloatConst(value)
        when (operator) {
            "**" -> TODO("pow")
            "+" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            lda  #<$constValueName
                            ldy  #>$constValueName
                            jsr  c64flt.FADD
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
                        """)
            }
            "-" -> {
                if (value == 0.0)
                    return
                asmgen.out("""
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$constValueName
                            ldy  #>$constValueName
                            jsr  c64flt.MOVFM
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.FSUB
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
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

    private fun inplaceModification_byte_value_to_memory(pointername: String, operator: String, value: Expression) {
        println("warning: slow stack evaluation used (3):  @($pointername) $operator= ${value::class.simpleName} at ${value.position}") // TODO
        asmgen.translateExpression(value)
        fun loadByteFromPointerIntoA() {
            asmgen.out("""
                lda  $pointername
                ldy  $pointername+1
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                ldy  #0
                lda  (${C64Zeropage.SCRATCH_W1}),y""")
        }
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" clc |  adc  $ESTACK_LO_PLUS1_HEX,x | sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "-" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" sec |  sbc  $ESTACK_LO_PLUS1_HEX,x | sta  (${C64Zeropage.SCRATCH_W1}),y")
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
                loadByteFromPointerIntoA()
                asmgen.out(" and  $ESTACK_LO_PLUS1_HEX,x |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "^" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" xor  $ESTACK_LO_PLUS1_HEX,x |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "|" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" ora  $ESTACK_LO_PLUS1_HEX,x |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
        asmgen.out(" inx")
    }

    private fun inplaceModification_byte_variable_to_memory(pointername: String, operator: String, ident: IdentifierReference) {
        val otherName = asmgen.asmIdentifierName(ident)
        fun loadByteFromPointerIntoA() {
            asmgen.out("""
                lda  $pointername
                ldy  $pointername+1
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                ldy  #0
                lda  (${C64Zeropage.SCRATCH_W1}),y""")
        }
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" clc |  adc  $otherName | sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "-" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" sec |  sbc  $otherName | sta  (${C64Zeropage.SCRATCH_W1}),y")
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
                loadByteFromPointerIntoA()
                asmgen.out(" and  $otherName |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "^" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" xor  $otherName |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "|" -> {
                loadByteFromPointerIntoA()
                asmgen.out(" ora  $otherName |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            else -> throw AssemblyError("invalid operator for in-place modification $operator")
        }
    }

    private fun inplaceModification_byte_litval_to_memory(pointername: String, operator: String, value: Int) {
        fun loadByteFromPointerInA() {
            asmgen.out("""
                lda  $pointername
                ldy  $pointername+1
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                ldy  #0
                lda  (${C64Zeropage.SCRATCH_W1}),y""")
        }
        when (operator) {
            // note: ** (power) operator requires floats.
            "+" -> {
                loadByteFromPointerInA()
                asmgen.out(" clc |  adc  #$value | sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "-" -> {
                loadByteFromPointerInA()
                asmgen.out(" sec |  sbc  #$value | sta  (${C64Zeropage.SCRATCH_W1}),y")
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
                    loadByteFromPointerInA()
                    repeat(value) { asmgen.out(" asl  a") }
                    asmgen.out(" sta  (${C64Zeropage.SCRATCH_W1}),y")
                }
            }
            ">>" -> {
                if (value > 0) {
                    loadByteFromPointerInA()
                    repeat(value) { asmgen.out(" lsr  a") }
                    asmgen.out(" sta  (${C64Zeropage.SCRATCH_W1}),y")
                }
            }
            "&" -> {
                loadByteFromPointerInA()
                asmgen.out(" and  #$value |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "^" -> {
                loadByteFromPointerInA()
                asmgen.out(" xor  #$value |  sta  (${C64Zeropage.SCRATCH_W1}),y")
            }
            "|" -> {
                loadByteFromPointerInA()
                asmgen.out(" ora  #$value |  sta  (${C64Zeropage.SCRATCH_W1}),y")
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
                    sta  c64.SCRATCH_ZPWORD1
                    lda  $name+1
                    sta  c64.SCRATCH_ZPWORD1+1
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
                        adc  $ESTACK_LO_PLUS1_HEX,x
                        sta  $name
                        bcc  +
                        inc  $name+1
+                           """)
                    "-" -> asmgen.out("""
                        lda  $name
                        sec
                        sbc  $ESTACK_LO_PLUS1_HEX,x
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
                        ldy  c64.ESTACK_LO,x
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
                            ldy  c64.ESTACK_LO,x
                            beq  +
-                           lsr  $name+1
                            ror  $name
                            dey
                            bne  -
+""") }
                        else {
                            asmgen.out("""
                            inx
                            ldy  c64.ESTACK_LO,x
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
                    "+" -> asmgen.out(" lda  $name |  clc |  adc  $ESTACK_LO_PLUS1_HEX,x |  sta  $name |  lda  $name+1 |  adc  $ESTACK_HI_PLUS1_HEX,x |  sta  $name+1")
                    "-" -> asmgen.out(" lda  $name |  sec |  sbc  $ESTACK_LO_PLUS1_HEX,x |  sta  $name |  lda  $name+1 |  sbc  $ESTACK_HI_PLUS1_HEX,x |  sta  $name+1")
                    "*" -> TODO("mul")// asmgen.out("  jsr  prog8_lib.mul_byte")  //  the optimized routines should have been checked earlier
                    "/" -> TODO("div")// asmgen.out(if(types==DataType.UBYTE) "  jsr  prog8_lib.idiv_ub" else "  jsr  prog8_lib.idiv_b")
                    "%" -> {
                        TODO("word remainder")
//                if(types==DataType.BYTE)
//                    throw AssemblyError("remainder of signed integers is not properly defined/implemented, use unsigned instead")
//                asmgen.out("  jsr prog8_lib.remainder_ub")
                    }
                    "<<", ">>" -> throw AssemblyError("shift by a word value not supported, max is a byte")
                    "&" -> asmgen.out(" lda  $name |  and  $ESTACK_LO_PLUS1_HEX,x |  sta  $name | lda  $name+1 |  and  $ESTACK_HI_PLUS1_HEX,x  |  sta  $name+1")
                    "^" -> asmgen.out(" lda  $name |  xor  $ESTACK_LO_PLUS1_HEX,x |  sta  $name | lda  $name+1 |  xor  $ESTACK_HI_PLUS1_HEX,x  |  sta  $name+1")
                    "|" -> asmgen.out(" lda  $name |  ora  $ESTACK_LO_PLUS1_HEX,x |  sta  $name | lda  $name+1 |  ora  $ESTACK_HI_PLUS1_HEX,x  |  sta  $name+1")
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
            "+" -> asmgen.out(" lda  $name |  clc |  adc  $ESTACK_LO_PLUS1_HEX,x |  sta  $name")
            "-" -> asmgen.out(" lda  $name |  sec |  sbc  $ESTACK_LO_PLUS1_HEX,x |  sta  $name")
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
                    ldy  c64.ESTACK_LO,x
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
                        ldy  c64.ESTACK_LO,x
                        beq  +
-                       lsr  $name
                        dey
                        bne  -
+""")
                } else {
                    asmgen.out("""
                        inx
                        ldy  c64.ESTACK_LO,x
                        beq  +
-                       lda  $name
                        asl  a
                        ror  $name
                        dey
                        bne  -
+""")
                }
            }
            "&" -> asmgen.out(" lda  $name |  and  $ESTACK_LO_PLUS1_HEX,x |  sta  $name")
            "^" -> asmgen.out(" lda  $name |  xor  $ESTACK_LO_PLUS1_HEX,x |  sta  $name")
            "|" -> asmgen.out(" lda  $name |  ora  $ESTACK_LO_PLUS1_HEX,x |  sta  $name")
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

    private fun inplaceCast(target: AssignTarget, cast: TypecastExpression, assign: Assignment) {
        val targetDt = target.inferType(program, assign).typeOrElse(DataType.STRUCT)
        val outerCastDt = cast.type
        val innerCastDt = (cast.expression as? TypecastExpression)?.type

        if (innerCastDt == null) {
            // simple typecast where the value is the target
            when (targetDt) {
                DataType.UBYTE, DataType.BYTE -> { /* byte target can't be casted to anything else at all */
                }
                DataType.UWORD, DataType.WORD -> {
                    when (outerCastDt) {
                        DataType.UBYTE, DataType.BYTE -> {
                            if (target.identifier != null) {
                                val name = asmgen.asmIdentifierName(target.identifier!!)
                                asmgen.out(" lda  #0 |  sta  $name+1")
                            } else
                                throw AssemblyError("weird value")
                        }
                        DataType.UWORD, DataType.WORD, in IterableDatatypes -> {
                        }
                        DataType.FLOAT -> throw AssemblyError("incompatible cast type")
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
            val resultingCast = TypecastExpression(value, castDt, false, assign.position)
            inplaceCast(target, resultingCast, assign)
        }
    }

    private fun inplaceBooleanNot(target: AssignTarget, dt: DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when (dt) {
            DataType.UBYTE -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  $name""")
                    }
                    memory != null -> {
                        when (memory.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (memory.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    beq  +
                                    lda  #1
+                                   eor  #1
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val name = asmgen.asmIdentifierName(memory.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    lda  $name
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    lda  $name+1
                                    sta  ${C64Zeropage.SCRATCH_W1 + 1}
                                    ldy  #0
                                    lda  (${C64Zeropage.SCRATCH_W1}),y
                                    beq  +
                                    lda  #1
+                                   eor  #1
                                    sta  (${C64Zeropage.SCRATCH_W1}),y""")
                            }
                            else -> {
                                println("warning: slow stack evaluation used (6): ${memory.addressExpression::class.simpleName} at ${memory.addressExpression.position}") // TODO
                                asmgen.translateExpression(memory.addressExpression)
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
                    arrayIdx != null -> {
                        TODO("in-place not of ubyte array")
                    }
                }
            }
            DataType.UWORD -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            ora  $name+1
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  $name
                            lsr  a
                            sta  $name+1""")
                    }
                    arrayIdx != null -> TODO("in-place not of uword array")
                    memory != null -> throw AssemblyError("no asm gen for uword-memory not")
                }
            }
            else -> throw AssemblyError("boolean-not of invalid type")
        }
    }

    private fun inplaceInvert(target: AssignTarget, dt: DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when (dt) {
            DataType.UBYTE -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            eor  #255
                            sta  $name""")
                    }
                    memory != null -> {
                        when (memory.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (memory.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    eor  #255
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val name = asmgen.asmIdentifierName(memory.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    lda  $name
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    lda  $name+1
                                    sta  ${C64Zeropage.SCRATCH_W1 + 1}
                                    ldy  #0
                                    lda  (${C64Zeropage.SCRATCH_W1}),y
                                    eor  #255
                                    sta  (${C64Zeropage.SCRATCH_W1}),y""")
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
                    arrayIdx != null -> {
                        TODO("in-place invert ubyte array")
                    }
                }
            }
            DataType.UWORD -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            eor  #255
                            sta  $name
                            lda  $name+1
                            eor  #255
                            sta  $name+1""")
                    }
                    arrayIdx != null -> TODO("in-place invert uword array")
                    memory != null -> throw AssemblyError("no asm gen for uword-memory invert")
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }

    private fun inplaceNegate(target: AssignTarget, dt: DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when (dt) {
            DataType.BYTE -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  $name
                            sta  $name""")
                    }
                    memory != null -> throw AssemblyError("can't in-place negate memory ubyte")
                    arrayIdx != null -> TODO("in-place negate byte array")
                }
            }
            DataType.WORD -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  $name
                            sta  $name
                            lda  #0
                            sbc  $name+1
                            sta  $name+1""")
                    }
                    arrayIdx != null -> TODO("in-place negate word array")
                    memory != null -> throw AssemblyError("no asm gen for word memory negate")
                }
            }
            DataType.FLOAT -> {
                when {
                    identifier != null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            jsr  c64flt.NEGOP
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
                        """)
                    }
                    arrayIdx != null -> TODO("in-place negate float array")
                    memory != null -> throw AssemblyError("no asm gen for float memory negate")
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

}
