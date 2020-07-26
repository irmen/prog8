package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.DirectMemoryWrite
import prog8.ast.statements.VarDecl
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.toHex


// OLD inplace-assignment code.
// should really come up with a more compact way to generate this kind of code...

/***

internal class InplaceAssignmentAsmGen(private val program: Program, private val errors: ErrorReporter, private val asmgen: AsmGen) {

    internal fun translate(assign: Assignment) {
        if (assign.aug_op == null)
            translateNormalAssignment(assign)
        else
            translateInplaceAssignment(assign)
    }

    private fun translateInplaceAssignment(assign: Assignment) {
        require(assign.aug_op != null)

        when {
            assign.target.identifier != null -> {
                if (inplaceAssignToIdentifier(assign))
                    return
            }
            assign.target.memoryAddress != null -> {
                if (inplaceAssignToMemoryByte(assign))
                    return
            }
            assign.target.arrayindexed != null -> {
                if (inplaceAssignToArrayOrString(assign))
                    return
            }
        }

        // TODO this is the slow FALLBACK, eventually we don't want to have to use it anymore:
        errors.warn("using suboptimal in-place assignment code (this should still be optimized)", assign.position)
        val normalAssignment = assign.asDesugaredNonaugmented()
        return translateNormalAssignment(normalAssignment)
    }

    private fun inplaceAssignToArrayOrString(assign: Assignment): Boolean {
        val targetArray = assign.target.arrayindexed!!
        val arrayName = targetArray.identifier
        val arrayIndex = targetArray.arrayspec.index
        val targetName = asmgen.asmIdentifierName(arrayName)
        val arrayDt = arrayName.targetVarDecl(program.namespace)!!.datatype
        val constValue = assign.value.constValue(program)?.number
        if (constValue != null) {
            // constant value to set in array
            val hexValue = constValue.toHex()
            if (assign.aug_op == "setvalue") {
                when (arrayDt) {
                    DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> {
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #${arrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoY(targetArray)
                        asmgen.out(" lda  #$hexValue |  sta  $targetName,y")
                    }
                    DataType.ARRAY_W, DataType.ARRAY_UW -> {
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" lda  #${arrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoA(targetArray)
                        asmgen.out("""
                            asl  a
                            tay
                            lda  #<$hexValue
                            sta  $targetName,y
                            lda  #>$hexValue
                            sta  $targetName+1,y
                        """)
                    }
                    DataType.ARRAY_F -> {
                        assignFromFloatConstant(assign.target, constValue.toDouble())
                    }
                    else -> throw AssemblyError("assignment to array: invalid array dt $arrayDt")
                }
            } else {
                TODO("aug assignment to element in array/string")
            }
            return true
        }

        // non-const value.
        // !!! DON'T FORGET :  CAN BE AUGMENTED ASSIGNMENT !!!
        when (assign.value) {
            is IdentifierReference -> {
                val sourceName = asmgen.asmIdentifierName(assign.value as IdentifierReference)
                when(arrayDt) {
                    DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> {
                        asmgen.out(" lda  $sourceName")
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #${arrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoY(targetArray)
                        asmgen.out(" sta  $targetName,y")
                    }
                    DataType.ARRAY_W, DataType.ARRAY_UW -> {
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" lda  #${arrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoA(targetArray)
                        asmgen.out("""
                            asl  a
                            tay
                            lda  $sourceName
                            sta  $targetName,y
                            lda  $sourceName+1
                            sta  $targetName+1,y
                        """)
                    }
                    DataType.ARRAY_F -> return false        // TODO optimize instead of fallback?
                    else -> throw AssemblyError("assignment to array: invalid array dt $arrayDt")
                }
                return true
            }
            is AddressOf -> {
                TODO("assign address into array $assign")
            }
            is DirectMemoryRead -> {
                TODO("assign memory read into array $assign")
            }
            is ArrayIndexedExpression -> {
                if(assign.aug_op != "setvalue")
                    return false   // we don't put effort into optimizing anything beside simple assignment
                val valueArrayExpr = assign.value as ArrayIndexedExpression
                val valueArrayIndex = valueArrayExpr.arrayspec.index
                val valueVariablename = asmgen.asmIdentifierName(valueArrayExpr.identifier)
                // val valueDt = valueArrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                when(arrayDt) {
                    DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> {
                        if (valueArrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #${valueArrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoY(valueArrayExpr)
                        asmgen.out(" lda  $valueVariablename,y")
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #${arrayIndex.number.toHex()}")
                        else
                            asmgen.translateArrayIndexIntoY(targetArray)
                        asmgen.out(" sta  $targetName,y")
                    }
                    DataType.ARRAY_UW, DataType.ARRAY_W -> {
                        if (valueArrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #2*${valueArrayIndex.number.toHex()}")
                        else {
                            asmgen.translateArrayIndexIntoA(valueArrayExpr)
                            asmgen.out(" asl  a |  tay")
                        }
                        asmgen.out("""
                            lda  $valueVariablename,y
                            pha
                            lda  $valueVariablename+1,y
                            pha
                        """)
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #2*${arrayIndex.number.toHex()}")
                        else {
                            asmgen.translateArrayIndexIntoA(targetArray)
                            asmgen.out(" asl  a |  tay")
                        }
                        asmgen.out("""
                            pla
                            sta  $targetName+1,y
                            pla
                            sta  $targetName,y
                        """)
                        return true
                    }
                    DataType.ARRAY_F -> {
                        if (valueArrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #5*${valueArrayIndex.number.toHex()}")
                        else {
                            asmgen.translateArrayIndexIntoA(valueArrayExpr)
                            asmgen.out("""
                                sta  ${C64Zeropage.SCRATCH_REG}
                                asl  a
                                asl  a
                                clc
                                adc  ${C64Zeropage.SCRATCH_REG}
                                tay
                            """)
                        }
                        asmgen.out("""
                            lda  $valueVariablename,y
                            pha
                            lda  $valueVariablename+1,y
                            pha
                            lda  $valueVariablename+2,y
                            pha
                            lda  $valueVariablename+3,y
                            pha
                            lda  $valueVariablename+4,y
                            pha
                        """)
                        if (arrayIndex is NumericLiteralValue)
                            asmgen.out(" ldy  #5*${arrayIndex.number.toHex()}")
                        else {
                            asmgen.translateArrayIndexIntoA(targetArray)
                            asmgen.out("""
                                sta  ${C64Zeropage.SCRATCH_REG}
                                asl  a
                                asl  a
                                clc
                                adc  ${C64Zeropage.SCRATCH_REG}
                                tay
                            """)
                        }
                        asmgen.out("""
                            pla
                            sta  $targetName+4,y
                            pla
                            sta  $targetName+3,y
                            pla
                            sta  $targetName+2,y
                            pla
                            sta  $targetName+1,y
                            pla
                            sta  $targetName,y
                        """)
                        return true
                    }
                    else -> throw AssemblyError("assignment to array: invalid array dt")
                }
                return true
            }
            else -> {
                fallbackAssignment(assign)
                return true
            }
        }

        return false
    }

    private fun inplaceAssignToMemoryByte(assign: Assignment): Boolean {
        val address = assign.target.memoryAddress?.addressExpression?.constValue(program)?.number
                ?: return inplaceAssignToNonConstMemoryByte(assign)

        val hexAddr = address.toHex()
        val constValue = assign.value.constValue(program)
        if (constValue != null) {
            val hexValue = constValue.number.toHex()
            when (assign.aug_op) {
                "setvalue" -> asmgen.out(" lda  #$hexValue |  sta  $hexAddr")
                "+=" -> asmgen.out(" lda  $hexAddr |  clc |  adc  #$hexValue |  sta  $hexAddr")
                "-=" -> asmgen.out(" lda  $hexAddr |  sec |  sbc  #$hexValue |  sta  $hexAddr")
                "/=" -> TODO("membyte /= const $hexValue")
                "*=" -> TODO("membyte *= const $hexValue")
                "&=" -> asmgen.out(" lda  $hexAddr |  and  #$hexValue |  sta  $hexAddr")
                "|=" -> asmgen.out(" lda  $hexAddr |  ora  #$hexValue |  sta  $hexAddr")
                "^=" -> asmgen.out(" lda  $hexAddr |  eor  #$hexValue |  sta  $hexAddr")
                "%=" -> TODO("membyte %= const $hexValue")
                "<<=" -> throw AssemblyError("<<= should have been replaced by lsl()")
                ">>=" -> throw AssemblyError("<<= should have been replaced by lsr()")
                else -> throw AssemblyError("invalid aug_op ${assign.aug_op}")
            }
            return true
        }

        // non-const value.
        when (assign.value) {
            is IdentifierReference -> {
                val sourceName = asmgen.asmIdentifierName(assign.value as IdentifierReference)
                when(assign.aug_op) {
                    "setvalue" -> asmgen.out(" lda  $sourceName |  sta  $hexAddr")
                    else -> TODO("membyte aug.assign  variable  $assign")
                }
                return true
            }
            is DirectMemoryRead -> {
                val memory = (assign.value as DirectMemoryRead).addressExpression.constValue(program)!!.number.toHex()
                when(assign.aug_op) {
                    "setvalue" -> asmgen.out(" lda  $memory |  sta  $hexAddr")
                    else -> TODO("membyte aug.assign  memread  $assign")
                }
                return true
            }
            is ArrayIndexedExpression -> {
                TODO("membyte = array value $assign")
            }
            is AddressOf -> throw AssemblyError("can't assign address to byte")
            else -> {
                fallbackAssignment(assign)
                return true
            }
        }

        return false
    }

    private fun inplaceAssignToNonConstMemoryByte(assign: Assignment): Boolean {
        // target address is not constant, so evaluate it from the stack
        asmgen.translateExpression(assign.target.memoryAddress!!.addressExpression)
        asmgen.out("""
            inx
            lda  $ESTACK_LO_HEX,x
            sta  ${C64Zeropage.SCRATCH_W1}
            lda  $ESTACK_HI_HEX,x
            sta  ${C64Zeropage.SCRATCH_W1}+1
        """)

        val constValue = assign.value.constValue(program)
        if (constValue != null) {
            val hexValue = constValue.number.toHex()
            asmgen.out("  ldy  #0")
            when (assign.aug_op) {
                "setvalue" -> asmgen.out(" lda  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "+=" -> asmgen.out(" lda  (${C64Zeropage.SCRATCH_W1}),y |  clc |  adc  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "-=" -> asmgen.out(" lda  (${C64Zeropage.SCRATCH_W1}),y |  sec |  sbc  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "/=" -> TODO("membyte /= const $hexValue")
                "*=" -> TODO("membyte *= const $hexValue")
                "&=" -> asmgen.out(" lda  (${C64Zeropage.SCRATCH_W1}),y |  and  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "|=" -> asmgen.out(" lda  (${C64Zeropage.SCRATCH_W1}),y |  ora  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "^=" -> asmgen.out(" lda  (${C64Zeropage.SCRATCH_W1}),y |  eor  #$hexValue |  sta  (${C64Zeropage.SCRATCH_W1}),y")
                "%=" -> TODO("membyte %= const $hexValue")
                "<<=" -> throw AssemblyError("<<= should have been replaced by lsl()")
                ">>=" -> throw AssemblyError("<<= should have been replaced by lsr()")
                else -> throw AssemblyError("invalid aug_op ${assign.aug_op}")
            }
            return true
        }

        // non-const value.
        // !!! DON'T FORGET :  CAN BE AUGMENTED ASSIGNMENT !!!
        when (assign.value) {
            is IdentifierReference -> {
                val sourceName = asmgen.asmIdentifierName(assign.value as IdentifierReference)
                TODO("membyte = variable $assign")
            }
            is DirectMemoryRead -> {
                TODO("membyte = memread $assign")
            }
            is ArrayIndexedExpression -> {
                if (assign.aug_op == "setvalue") {
                    val arrayExpr = assign.value as ArrayIndexedExpression
                    val arrayIndex = arrayExpr.arrayspec.index
                    val variablename = asmgen.asmIdentifierName(arrayExpr.identifier)
                    val arrayDt = arrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                    if (arrayDt != DataType.ARRAY_B && arrayDt != DataType.ARRAY_UB && arrayDt != DataType.STR)
                        throw AssemblyError("assign to memory byte: expected byte array or string source")
                    if (arrayIndex is NumericLiteralValue)
                        asmgen.out(" ldy  #${arrayIndex.number.toHex()}")
                    else
                        asmgen.translateArrayIndexIntoY(arrayExpr)
                    asmgen.out("""
                        lda  $variablename,y
                        ldy  #0
                        sta  (${C64Zeropage.SCRATCH_W1}),y
                    """)
                } else {
                    // TODO optimize more augmented assignment cases
                    val normalAssign = assign.asDesugaredNonaugmented()
                    asmgen.translateExpression(normalAssign.value)
                    assignFromEvalResult(normalAssign.target)
                }
                return true
            }
            is AddressOf -> throw AssemblyError("can't assign memory address to memory byte")
            else -> {
                fallbackAssignment(assign)
                return true
            }
        }

        return false   // TODO optimized
    }

    private fun inplaceAssignToIdentifier(assign: Assignment): Boolean {
        val targetType = assign.target.inferType(program, assign)
        val constNumber = assign.value.constValue(program)?.number
        val targetName = asmgen.asmIdentifierName(assign.target.identifier!!)

        when (targetType.typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE, DataType.BYTE -> {
                // (u)byte assignment
                if (constNumber != null) {
                    val hexValue = constNumber.toHex()
                    when (assign.aug_op) {
                        "setvalue" -> asmgen.out(" lda  #$hexValue |  sta  $targetName")
                        "+=" -> asmgen.out(" lda  $targetName |  clc |  adc  #$hexValue |  sta  $targetName")
                        "-=" -> asmgen.out(" lda  $targetName |  sec |  sbc  #$hexValue |  sta  $targetName")
                        "/=" -> TODO("variable /= const $hexValue")
                        "*=" -> TODO("variable *= const $hexValue")
                        "&=" -> asmgen.out(" lda  $targetName |  and  #$hexValue |  sta  $targetName")
                        "|=" -> asmgen.out(" lda  $targetName |  ora  #$hexValue |  sta  $targetName")
                        "^=" -> asmgen.out(" lda  $targetName |  eor  #$hexValue |  sta  $targetName")
                        "%=" -> TODO("variable %= const $hexValue")
                        "<<=" -> throw AssemblyError("<<= should have been replaced by lsl()")
                        ">>=" -> throw AssemblyError("<<= should have been replaced by lsr()")
                        else -> throw AssemblyError("invalid aug_op ${assign.aug_op}")
                    }
                    return true
                }

                // non-const (u)byte value
                // !!! DON'T FORGET :  CAN BE AUGMENTED ASSIGNMENT !!!
                when (assign.value) {
                    is IdentifierReference -> {
                        val sourceName = asmgen.asmIdentifierName(assign.value as IdentifierReference)
                        when (assign.aug_op) {
                            "setvalue" -> asmgen.out(" lda  $sourceName |  sta  $targetName")
                            "+=" -> asmgen.out(" lda  $targetName |  clc |  adc  $sourceName |  sta  $targetName")
                            "-=" -> asmgen.out(" lda  $targetName |  sec |  sbc  $sourceName |  sta  $targetName")
                            "/=" -> TODO("variable /= variable")
                            "*=" -> TODO("variable *= variable")
                            "&=" -> asmgen.out(" lda  $targetName |  and  $sourceName |  sta  $targetName")
                            "|=" -> asmgen.out(" lda  $targetName |  ora  $sourceName |  sta  $targetName")
                            "^=" -> asmgen.out(" lda  $targetName |  eor  $sourceName |  sta  $targetName")
                            "%=" -> TODO("variable %= variable")
                            "<<=" -> throw AssemblyError("<<= should have been replaced by lsl()")
                            ">>=" -> throw AssemblyError("<<= should have been replaced by lsr()")
                            else -> throw AssemblyError("invalid aug_op ${assign.aug_op}")
                        }
                        return true
                    }
                    is DirectMemoryRead -> {
                        TODO("variable = memory read $assign")
                    }
                    is ArrayIndexedExpression -> {
                        if (assign.aug_op == "setvalue") {
                            val arrayExpr = assign.value as ArrayIndexedExpression
                            val arrayIndex = arrayExpr.arrayspec.index
                            val variablename = asmgen.asmIdentifierName(arrayExpr.identifier)
                            val arrayDt = arrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                            if (arrayDt != DataType.ARRAY_B && arrayDt != DataType.ARRAY_UB && arrayDt != DataType.STR)
                                throw AssemblyError("assign to identifier: expected byte array or string source")
                            if (arrayIndex is NumericLiteralValue)
                                asmgen.out(" ldy  #${arrayIndex.number.toHex()}")
                            else
                                asmgen.translateArrayIndexIntoY(arrayExpr)
                            asmgen.out(" lda  $variablename,y |  sta  $targetName")
                        } else {
                            // TODO optimize more augmented assignment cases
                            val normalAssign = assign.asDesugaredNonaugmented()
                            asmgen.translateExpression(normalAssign.value)
                            assignFromEvalResult(normalAssign.target)
                        }
                        return true
                    }
                    else -> {
                        fallbackAssignment(assign)
                        return true
                    }
                }
            }
            DataType.UWORD, DataType.WORD -> {
                if (constNumber != null) {
                    val hexNumber = constNumber.toHex()
                    when (assign.aug_op) {
                        "setvalue" -> {
                            asmgen.out("""
                                lda  #<$hexNumber
                                sta  $targetName
                                lda  #>$hexNumber
                                sta  $targetName+1
                            """)
                        }
                        "+=" -> {
                            asmgen.out("""
                                lda  $targetName
                                clc
                                adc  #<$hexNumber
                                sta  $targetName
                                lda  $targetName+1
                                adc  #>$hexNumber
                                sta  $targetName+1
                            """)
                        }
                        "-=" -> {
                            asmgen.out("""
                                lda  $targetName
                                sec
                                sbc  #<$hexNumber
                                sta  $targetName
                                lda  $targetName+1
                                sbc  #>$hexNumber
                                sta  $targetName+1
                            """)
                        }
                        else -> TODO("variable aug.assign ${assign.aug_op} const $hexNumber")
                    }
                    return true
                }

                // non-const value
                // !!! DON'T FORGET :  CAN BE AUGMENTED ASSIGNMENT !!!
                when (assign.value) {
                    is IdentifierReference -> {
                        val sourceName = asmgen.asmIdentifierName(assign.value as IdentifierReference)
                        when (assign.aug_op) {
                            "setvalue" -> {
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  $targetName
                                    lda  $sourceName+1
                                    sta  $targetName+1
                                """)
                            }
                            "+=" -> {
                                asmgen.out("""
                                    lda  $targetName
                                    clc
                                    adc  $sourceName
                                    sta  $targetName
                                    lda  $targetName+1
                                    adc  $sourceName+1
                                    sta  $targetName+1
                                """)
                                return true
                            }
                            "-=" -> {
                                asmgen.out("""
                                    lda  $targetName
                                    sec
                                    sbc  $sourceName
                                    sta  $targetName
                                    lda  $targetName+1
                                    sbc  $sourceName+1
                                    sta  $targetName+1
                                """)
                                return true
                            }
                            else -> {
                                TODO("variable aug.assign variable")
                            }
                        }
                        return true
                    }
                    is DirectMemoryRead -> throw AssemblyError("expected a typecast for assigning memory read byte to word")
                    is AddressOf -> {
                        val name = asmgen.asmIdentifierName((assign.value as AddressOf).identifier)
                        asmgen.out(" lda  #<$name |  sta  $targetName |  lda  #>$name |  sta  $targetName+1")
                        return true
                    }
                    is ArrayIndexedExpression -> {
                        if (assign.aug_op == "setvalue") {
                            val arrayExpr = assign.value as ArrayIndexedExpression
                            val arrayIndex = arrayExpr.arrayspec.index
                            val variablename = asmgen.asmIdentifierName(arrayExpr.identifier)
                            val arrayDt = arrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                            if (arrayDt != DataType.ARRAY_W && arrayDt != DataType.ARRAY_UW)
                                throw AssemblyError("assign to identifier: expected word array source")
                            if (arrayIndex is NumericLiteralValue)
                                asmgen.out(" lda  #${arrayIndex.number.toHex()}")
                            else
                                asmgen.translateArrayIndexIntoA(arrayExpr)
                            asmgen.out("""
                                asl  a
                                tay
                                lda  $variablename,y
                                sta  $targetName
                                lda  $variablename+1,y
                                sta  $targetName+1
                            """)
                        } else {
                            // TODO optimize more augmented assignment cases
                            val normalAssign = assign.asDesugaredNonaugmented()
                            asmgen.translateExpression(normalAssign.value)
                            assignFromEvalResult(normalAssign.target)
                        }
                        return true
                    }
                    else -> {
                        fallbackAssignment(assign)
                        return true
                    }
                }
            }
            DataType.FLOAT -> {
                if (constNumber != null) {
                    // assign a constant
                    val floatConst = asmgen.getFloatConst(constNumber.toDouble())
                    when (assign.aug_op) {
                        "setvalue" -> assignFromFloatConstant(assign.target, constNumber.toDouble())
                        "+=" -> {
                            if (constNumber == 0.5) {
                                asmgen.out("""
                                lda  #<$targetName
                                ldy  #>$targetName
                                jsr  c64flt.MOVFM
                                jsr  c64flt.FADDH
                                stx  c64.SCRATCH_ZPREGX
                                ldx  #<$targetName
                                ldy  #>$targetName
                                jsr  c64flt.MOVMF
                                ldx  c64.SCRATCH_ZPREGX
                            """)
                            } else {
                                asmgen.out("""
                                lda  #<$targetName
                                ldy  #>$targetName
                                jsr  c64flt.MOVFM
                                lda  #<$floatConst
                                ldy  #>$floatConst
                                jsr  c64flt.FADD
                                stx  c64.SCRATCH_ZPREGX
                                ldx  #<$targetName
                                ldy  #>$targetName
                                jsr  c64flt.MOVMF
                                ldx  c64.SCRATCH_ZPREGX
                            """)
                            }
                            return true
                        }
                        "-=" -> {
                            asmgen.out("""
                            lda  #<$floatConst
                            ldy  #>$floatConst
                            jsr  c64flt.MOVFM
                            lda  #<$targetName
                            ldy  #>$targetName
                            jsr  c64flt.FSUB
                            stx  c64.SCRATCH_ZPREGX
                            ldx  #<$targetName
                            ldy  #>$targetName
                            jsr  c64flt.MOVMF
                            ldx  c64.SCRATCH_ZPREGX
                        """)
                            return true
                        }
                        else -> TODO("float const value aug.assign $assign")
                    }
                    return true
                }

                // non-const float value.
                // !!! DON'T FORGET :  CAN BE AUGMENTED ASSIGNMENT !!!
                when (assign.value) {
                    is IdentifierReference -> {
                        when (assign.aug_op) {
                            "setvalue" -> assignFromFloatVariable(assign.target, assign.value as IdentifierReference)
                            "+=" -> return false        // TODO optimized float += variable
                            "-=" -> return false        // TODO optimized float -= variable
                            else -> TODO("float non-const value aug.assign $assign")
                        }
                        return true
                    }
                    is ArrayIndexedExpression -> {
                        when(assign.aug_op) {
                            "setvalue" -> {
                                val arrayExpr = assign.value as ArrayIndexedExpression
                                val arrayIndex = arrayExpr.arrayspec.index
                                val variablename = asmgen.asmIdentifierName(arrayExpr.identifier)
                                val arrayDt = arrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                                if (arrayDt != DataType.ARRAY_F)
                                    throw AssemblyError("assign to identifier: expected float array source")
                                if (arrayIndex is NumericLiteralValue)
                                    asmgen.out(" lda  #${arrayIndex.number.toHex()}")
                                else
                                    asmgen.translateArrayIndexIntoA(arrayExpr)
                                asmgen.out("""
                                    sta  c64.SCRATCH_ZPB1
                                    asl  a
                                    asl  a
                                    clc
                                    adc  c64.SCRATCH_ZPB1
                                    tay
                                    lda  $variablename,y
                                    sta  $targetName
                                    lda  $variablename+1,y
                                    sta  $targetName+1
                                    lda  $variablename+2,y
                                    sta  $targetName+2
                                    lda  $variablename+3,y
                                    sta  $targetName+3
                                    lda  $variablename+4,y
                                    sta  $targetName+4
                                """)
                            }
                            else -> TODO("float $assign")
                        }
                        return true
                    }
                    else -> {
                        fallbackAssignment(assign)
                        return true
                    }
                }
            }
            DataType.STR -> {
                val identifier = assign.value as? IdentifierReference
                        ?: throw AssemblyError("string value assignment expects identifier value")
                val sourceName = asmgen.asmIdentifierName(identifier)
                asmgen.out("""
                    lda  #<$targetName
                    sta  ${C64Zeropage.SCRATCH_W1}
                    lda  #>$targetName
                    sta  ${C64Zeropage.SCRATCH_W1+1}
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    jsr  prog8_lib.strcpy
                """)
                return true
            }
            else -> throw AssemblyError("assignment to identifier: invalid target datatype: $targetType")
        }
        return false
    }

    private fun fallbackAssignment(assign: Assignment) {
        if (assign.aug_op != "setvalue") {
            /* stack-based evaluation of the expression is required */
            val normalAssign = assign.asDesugaredNonaugmented()
            asmgen.translateExpression(normalAssign.value)
            assignFromEvalResult(normalAssign.target)
        } else {
            when (assign.value) {
                is FunctionCall -> {
                    // TODO is there a way to avoid function return value being passed via the stack?
                    //      for instance, 1 byte return value always in A, etc
                    val normalAssign = assign.asDesugaredNonaugmented()
                    asmgen.translateExpression(normalAssign.value)
                    assignFromEvalResult(normalAssign.target)
                }
                else -> {
                    /* stack-based evaluation of the expression is required */
                    val normalAssign = assign.asDesugaredNonaugmented()
                    asmgen.translateExpression(normalAssign.value)
                    assignFromEvalResult(normalAssign.target)
                }
            }
        }
    }
}


***/
