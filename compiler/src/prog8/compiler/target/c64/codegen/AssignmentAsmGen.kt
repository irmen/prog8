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


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translate(assign: Assignment) {
        if(assign.aug_op!=null)
            throw AssemblyError("aug-op assignments should have been transformed to normal ones")

        when(assign.value) {
            is NumericLiteralValue -> {
                val numVal = assign.value as NumericLiteralValue
                when(numVal.type) {
                    DataType.UBYTE, DataType.BYTE -> assignFromByteConstant(assign.target, numVal.number.toShort())
                    DataType.UWORD, DataType.WORD -> assignFromWordConstant(assign.target, numVal.number.toInt())
                    DataType.FLOAT -> assignFromFloatConstant(assign.target, numVal.number.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            is RegisterExpr -> {
                assignFromRegister(assign.target, (assign.value as RegisterExpr).register)
            }
            is IdentifierReference -> {
                val type = assign.target.inferType(program, assign).typeOrElse(DataType.STRUCT)
                when(type) {
                    DataType.UBYTE, DataType.BYTE -> assignFromByteVariable(assign.target, assign.value as IdentifierReference)
                    DataType.UWORD, DataType.WORD -> assignFromWordVariable(assign.target, assign.value as IdentifierReference)
                    DataType.FLOAT -> assignFromFloatVariable(assign.target, assign.value as IdentifierReference)
                    else -> throw AssemblyError("unsupported assignment target type $type")
                }
            }
            is AddressOf -> {
                val identifier = (assign.value as AddressOf).identifier
                assignFromAddressOf(assign.target, identifier)
            }
            is DirectMemoryRead -> {
                val read = (assign.value as DirectMemoryRead)
                when(read.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (read.addressExpression as NumericLiteralValue).number.toInt()
                        assignFromMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignFromMemoryByte(assign.target, null, read.addressExpression as IdentifierReference)
                    }
                    else -> {
                        asmgen.translateExpression(read.addressExpression)
                        TODO("read memory byte from result and put that in ${assign.target}")
                    }
                }
            }
            is PrefixExpression -> {
                // TODO optimize common cases
                asmgen.translateExpression(assign.value as PrefixExpression)
                assignFromEvalResult(assign.target)
            }
            is BinaryExpression -> {
                // TODO optimize common cases
                asmgen.translateExpression(assign.value as BinaryExpression)
                assignFromEvalResult(assign.target)
            }
            is ArrayIndexedExpression -> {
                // TODO optimize common cases
                val arrayExpr = assign.value as ArrayIndexedExpression
                val arrayDt = arrayExpr.identifier.targetVarDecl(program.namespace)!!.datatype
                val index = arrayExpr.arrayspec.index
                if(index is NumericLiteralValue) {
                    // constant array index value
                    val arrayVarName = asmgen.asmIdentifierName(arrayExpr.identifier)
                    val indexValue = index.number.toInt() * ArrayElementTypes.getValue(arrayDt).memorySize()
                    when (arrayDt) {
                        DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  $ESTACK_LO_HEX,x |  dex")
                        DataType.ARRAY_UW, DataType.ARRAY_W ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  $ESTACK_LO_HEX,x |  lda  $arrayVarName+$indexValue+1 |  sta  $ESTACK_HI_HEX,x | dex")
                        DataType.ARRAY_F ->
                            asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  c64flt.push_float")
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    asmgen.translateArrayIndexIntoA(arrayExpr)
                    asmgen.readAndPushArrayvalueWithIndexA(arrayDt, arrayExpr.identifier)
                }
                assignFromEvalResult(assign.target)
            }
            is TypecastExpression -> {
                val cast = assign.value as TypecastExpression
                val sourceType = cast.expression.inferType(program)
                val targetType = assign.target.inferType(program, assign)
                if(sourceType.isKnown && targetType.isKnown &&
                        (sourceType.typeOrElse(DataType.STRUCT) in ByteDatatypes && targetType.typeOrElse(DataType.STRUCT) in ByteDatatypes) ||
                        (sourceType.typeOrElse(DataType.STRUCT) in WordDatatypes && targetType.typeOrElse(DataType.STRUCT) in WordDatatypes)) {
                    // no need for a type cast
                    assign.value = cast.expression
                    translate(assign)
                } else {
                    asmgen.translateExpression(assign.value as TypecastExpression)
                    assignFromEvalResult(assign.target)
                }
            }
            is FunctionCall -> {
                asmgen.translateExpression(assign.value as FunctionCall)
                assignFromEvalResult(assign.target)
            }
            is ArrayLiteralValue, is StringLiteralValue -> TODO("string/array/struct assignment?")
            is StructLiteralValue -> throw AssemblyError("struct literal value assignment should have been flattened ${assign.value.position}")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values ${assign.value.position}")
        }
    }

    internal fun assignFromEvalResult(target: AssignTarget) {
        val targetIdent = target.identifier
        when {
            target.register!=null -> {
                if(target.register== Register.X)
                    throw AssemblyError("can't pop into X register - use variable instead")
                asmgen.out(" inx | ld${target.register.name.toLowerCase()}  $ESTACK_LO_HEX,x ")
            }
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                val targetDt = targetIdent.inferType(program).typeOrElse(DataType.STRUCT)
                when(targetDt) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  $ESTACK_LO_HEX,x  | sta  $targetName")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  $ESTACK_LO_HEX,x
                            sta  $targetName
                            lda  $ESTACK_HI_HEX,x
                            sta  $targetName+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<$targetName
                            ldy  #>$targetName
                            jsr  c64flt.pop_float
                        """)
                    }
                    else -> throw AssemblyError("weird target variable type $targetDt")
                }
            }
            target.memoryAddress!=null -> {
                asmgen.out("  inx  | ldy  $ESTACK_LO_HEX,x")
                storeRegisterInMemoryAddress(Register.Y, target.memoryAddress)
            }
            target.arrayindexed!=null -> {
                val arrayDt = target.arrayindexed!!.identifier.targetVarDecl(program.namespace)!!.datatype
                val arrayVarName = asmgen.asmIdentifierName(target.arrayindexed!!.identifier)
                asmgen.translateExpression(target.arrayindexed!!.arrayspec.index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, arrayVarName)
            }
            else -> throw AssemblyError("weird assignment target $target")
        }
    }

    internal fun assignFromAddressOf(target: AssignTarget, name: IdentifierReference) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        val struct = name.memberOfStruct(program.namespace)
        val sourceName = if(struct!=null) {
            // take the address of the first struct member instead
            val decl = name.targetVarDecl(program.namespace)!!
            val firstStructMember = struct.nameOfFirstMember()
            // find the flattened var that belongs to this first struct member
            val firstVarName = listOf(decl.name, firstStructMember)
            val firstVar = name.definingScope().lookup(firstVarName, name) as VarDecl
            firstVar.name
        } else {
            asmgen.fixNameSymbols(name.nameInSource.joinToString ("."))
        }

        when {
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  $targetName
                        sty  $targetName+1
                    """)
            }
            target.memoryAddress!=null -> {
                TODO("assign address $sourceName to memory word $target")
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                TODO("assign address $sourceName to array $targetName [ $index ]")
            }
            else -> TODO("assign address $sourceName to $target")
        }
    }

    internal fun assignFromWordVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  $targetName
                    sty  $targetName+1
                """)
            }
            target.memoryAddress!=null -> {
                TODO("assign wordvar $sourceName to memory ${target.memoryAddress}")
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  lda  $sourceName+1 |  sta  $ESTACK_HI_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                popAndWriteArrayvalueWithIndexA(arrayDt, targetName)
            }
            else -> TODO("assign wordvar to $target")
        }
    }

    internal fun assignFromFloatVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                    lda  $sourceName
                    sta  $targetName
                    lda  $sourceName+1
                    sta  $targetName+1
                    lda  $sourceName+2
                    sta  $targetName+2
                    lda  $sourceName+3
                    sta  $targetName+3
                    lda  $sourceName+4
                    sta  $targetName+4
                """)
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<$targetName |  ldy  #>$targetName |  jsr  c64flt.pop_float_to_indexed_var")
            }
            else -> TODO("assign floatvar to $target")
        }
    }

    internal fun assignFromByteVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            target.register!=null -> {
                asmgen.out("  ld${target.register.name.toLowerCase()}  $sourceName")
            }
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                    lda  $sourceName
                    sta  $targetName
                    """)
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, targetName)
            }
            target.memoryAddress != null -> {
                val addressExpr = target.memoryAddress.addressExpression
                val addressLv = addressExpr as? NumericLiteralValue
                when {
                    addressLv != null -> asmgen.out("  lda  $sourceName |  sta  ${addressLv.number.toHex()}")
                    addressExpr is IdentifierReference -> {
                        val targetName = asmgen.asmIdentifierName(addressExpr)
                        asmgen.out("  lda  $sourceName |  sta  $targetName")
                    }
                    else -> {
                        asmgen.translateExpression(addressExpr)
                        asmgen.out("""
     inx
     lda  $ESTACK_LO_HEX,x
     ldy  $ESTACK_HI_HEX,x
     sta  (+) +1
     sty  (+) +2
     lda  $sourceName
+    sta  ${'$'}ffff      ; modified              
                            """)
                    }
                }
            }
            else -> TODO("assign bytevar to $target")
        }
    }

    internal fun assignFromRegister(target: AssignTarget, register: Register) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("  st${register.name.toLowerCase()}  $targetName")
            }
            target.register!=null -> {
                when(register) {
                    Register.A -> when(target.register) {
                        Register.A -> {}
                        Register.X -> asmgen.out("  tax")
                        Register.Y -> asmgen.out("  tay")
                    }
                    Register.X -> when(target.register) {
                        Register.A -> asmgen.out("  txa")
                        Register.X -> {}
                        Register.Y -> asmgen.out("  txy")
                    }
                    Register.Y -> when(target.register) {
                        Register.A -> asmgen.out("  tya")
                        Register.X -> asmgen.out("  tyx")
                        Register.Y -> {}
                    }
                }
            }
            target.memoryAddress!=null -> {
                storeRegisterInMemoryAddress(register, target.memoryAddress)
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                when (index) {
                    is NumericLiteralValue -> {
                        val memindex = index.number.toInt()
                        when(register) {
                            Register.A -> asmgen.out("  sta  $targetName+$memindex")
                            Register.X -> asmgen.out("  stx  $targetName+$memindex")
                            Register.Y -> asmgen.out("  sty  $targetName+$memindex")
                        }
                    }
                    is RegisterExpr -> {
                        when(register) {
                            Register.A -> asmgen.out("  sta  ${C64Zeropage.SCRATCH_B1}")
                            Register.X -> asmgen.out("  stx  ${C64Zeropage.SCRATCH_B1}")
                            Register.Y -> asmgen.out("  sty  ${C64Zeropage.SCRATCH_B1}")
                        }
                        when(index.register) {
                            Register.A -> {}
                            Register.X -> asmgen.out("  txa")
                            Register.Y -> asmgen.out("  tya")
                        }
                        asmgen.out("""
                            tay
                            lda  ${C64Zeropage.SCRATCH_B1}
                            sta  $targetName,y
                            """)
                    }
                    is IdentifierReference -> {
                        when(register) {
                            Register.A -> asmgen.out("  sta  ${C64Zeropage.SCRATCH_B1}")
                            Register.X -> asmgen.out("  stx  ${C64Zeropage.SCRATCH_B1}")
                            Register.Y -> asmgen.out("  sty  ${C64Zeropage.SCRATCH_B1}")
                        }
                        asmgen.out("""
                            lda  ${asmgen.asmIdentifierName(index)}
                            tay
                            lda  ${C64Zeropage.SCRATCH_B1}
                            sta  $targetName,y
                        """)
                    }
                    else -> {
                        asmgen.saveRegister(register)
                        asmgen.translateExpression(index)
                        asmgen.restoreRegister(register)
                        when(register) {
                            Register.A -> asmgen.out("  sta  ${C64Zeropage.SCRATCH_B1}")
                            Register.X -> asmgen.out("  stx  ${C64Zeropage.SCRATCH_B1}")
                            Register.Y -> asmgen.out("  sty  ${C64Zeropage.SCRATCH_B1}")
                        }
                        asmgen.out("""
                            inx
                            lda  $ESTACK_LO_HEX,x
                            tay
                            lda  ${C64Zeropage.SCRATCH_B1}
                            sta  $targetName,y  
                        """)
                    }
                }
            }
            else -> TODO("assign register $register to $target")
        }
    }

    private fun storeRegisterInMemoryAddress(register: Register, memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        val registerName = register.name.toLowerCase()
        when {
            addressLv != null -> asmgen.out("  st$registerName  ${addressLv.number.toHex()}")
            addressExpr is IdentifierReference -> {
                val targetName = asmgen.asmIdentifierName(addressExpr)
                when(register) {
                    Register.A -> asmgen.out("""
                        ldy  $targetName
                        sty  ${C64Zeropage.SCRATCH_W1}
                        ldy  $targetName+1
                        sty  ${C64Zeropage.SCRATCH_W1+1}
                        ldy  #0
                        sta  (${C64Zeropage.SCRATCH_W1}),y
                        """)
                    Register.X -> asmgen.out("""
                        txa
                        ldy  $targetName
                        sty  ${C64Zeropage.SCRATCH_W1}
                        ldy  $targetName+1
                        sty  ${C64Zeropage.SCRATCH_W1+1}
                        ldy  #0
                        sta  (${C64Zeropage.SCRATCH_W1}),y
                        """)
                    Register.Y -> asmgen.out("""
                        tya
                        ldy  $targetName
                        sty  ${C64Zeropage.SCRATCH_W1}
                        ldy  $targetName+1
                        sty  ${C64Zeropage.SCRATCH_W1+1}
                        ldy  #0
                        sta  (${C64Zeropage.SCRATCH_W1}),y
                        """)
                }
            }
            else -> {
                asmgen.saveRegister(register)
                asmgen.translateExpression(addressExpr)
                asmgen.restoreRegister(register)
                when (register) {
                    Register.A -> asmgen.out("  tay")
                    Register.X -> throw AssemblyError("can't use X register here")
                    Register.Y -> {}
                }
                asmgen.out("""
     inx
     lda  $ESTACK_LO_HEX,x
     sta  (+) +1
     lda  $ESTACK_HI_HEX,x
     sta  (+) +2
+    sty  ${'$'}ffff      ; modified              
                            """)
            }
        }
    }

    internal fun assignFromWordConstant(target: AssignTarget, word: Int) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                if(word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  $targetName
                    sta  $targetName+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  $targetName
                    sty  $targetName+1
                """)
                }
            }
            target.memoryAddress!=null -> {
                TODO("assign word $word to memory ${target.memoryAddress}")
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                // TODO optimize common cases
                asmgen.translateExpression(index)
                asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    asl  a
                    tay
                    lda  #<${word.toHex()}
                    sta  $targetName,y
                    lda  #>${word.toHex()}
                    sta  $targetName+1,y
                """)
            }
            else -> TODO("assign word $word to $target")
        }
    }

    internal fun assignFromByteConstant(target: AssignTarget, byte: Short) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            target.register!=null -> {
                asmgen.out("  ld${target.register.name.toLowerCase()}  #${byte.toHex()}")
            }
            targetIdent!=null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out(" lda  #${byte.toHex()} |  sta  $targetName ")
            }
            target.memoryAddress!=null -> {
                asmgen.out("  ldy  #${byte.toHex()}")
                storeRegisterInMemoryAddress(Register.Y, target.memoryAddress)
            }
            targetArrayIdx!=null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                // TODO optimize common cases
                asmgen.translateExpression(index)
                asmgen.out("""
                    inx
                    ldy  $ESTACK_LO_HEX,x
                    lda  #${byte.toHex()}
                    sta  $targetName,y
                """)
            }
            else -> TODO("assign byte $byte to $target")
        }
    }

    internal fun assignFromFloatConstant(target: AssignTarget, float: Double) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        if(float==0.0) {
            // optimized case for float zero
            when {
                targetIdent != null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
                            lda  #0
                            sta  $targetName
                            sta  $targetName+1
                            sta  $targetName+2
                            sta  $targetName+3
                            sta  $targetName+4
                        """)
                }
                targetArrayIdx!=null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    if(index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * C64MachineDefinition.FLOAT_MEM_SIZE
                        asmgen.out("""
                            lda  #0
                            sta  $targetName+$indexValue
                            sta  $targetName+$indexValue+1
                            sta  $targetName+$indexValue+2
                            sta  $targetName+$indexValue+3
                            sta  $targetName+$indexValue+4
                        """)
                    } else {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                        inx
                        lda  $ESTACK_LO_HEX,x
                        asl  a
                        asl  a
                        clc
                        adc  $ESTACK_LO_HEX,x
                        tay
                        lda  #0
                        sta  $targetName,y
                        sta  $targetName+1,y
                        sta  $targetName+2,y
                        sta  $targetName+3,y
                        sta  $targetName+4,y
                    """) // TODO use a subroutine for this
                    }
                }
                else -> TODO("assign float 0.0 to $target")
            }
        } else {
            // non-zero value
            val constFloat = asmgen.getFloatConst(float)
            when {
                targetIdent != null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
                            lda  $constFloat
                            sta  $targetName
                            lda  $constFloat+1
                            sta  $targetName+1
                            lda  $constFloat+2
                            sta  $targetName+2
                            lda  $constFloat+3
                            sta  $targetName+3
                            lda  $constFloat+4
                            sta  $targetName+4
                        """)
                }
                targetArrayIdx!=null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val arrayVarName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    if(index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * C64MachineDefinition.FLOAT_MEM_SIZE
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
                        asmgen.translateArrayIndexIntoA(targetArrayIdx)
                        asmgen.out("""
                            sta  ${C64Zeropage.SCRATCH_REG}
                            asl  a
                            asl  a
                            clc
                            adc  ${C64Zeropage.SCRATCH_REG}
                            tay
                            lda  $constFloat
                            sta  $arrayVarName,y
                            lda  $constFloat+1
                            sta  $arrayVarName+1,y
                            lda  $constFloat+2
                            sta  $arrayVarName+2,y
                            lda  $constFloat+3
                            sta  $arrayVarName+3,y
                            lda  $constFloat+4
                            sta  $arrayVarName+4,y
                        """)        // TODO use a subroutine for this
                    }
                }
                else -> TODO("assign float $float to $target")
            }
        }
    }

    internal fun assignFromMemoryByte(target: AssignTarget, address: Int?, identifier: IdentifierReference?) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        if(address!=null) {
            when {
                target.register!=null -> {
                    asmgen.out("  ld${target.register.name.toLowerCase()}  ${address.toHex()}")
                }
                targetIdent!=null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  $targetName
                        """)
                }
                target.memoryAddress!=null -> {
                    asmgen.out("  ldy  ${address.toHex()}")
                    storeRegisterInMemoryAddress(Register.Y, target.memoryAddress)
                }
                targetArrayIdx!=null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    TODO("assign memory byte at $address to array $targetName [ $index ]")
                }
                else -> TODO("assign memory byte $target")
            }
        }
        else if(identifier!=null) {
            val sourceName = asmgen.asmIdentifierName(identifier)
            when {
                target.register!=null -> {
                    asmgen.out("""
                        ldy  #0
                        lda  ($sourceName),y
                    """)
                    when(target.register){
                        Register.A -> {}
                        Register.X -> asmgen.out("  tax")
                        Register.Y -> asmgen.out("  tay")
                    }
                }
                targetIdent!=null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
                        ldy  #0
                        lda  ($sourceName),y
                        sta  $targetName
                    """)
                }
                target.memoryAddress!=null -> {
                    asmgen.out("  ldy  $sourceName")
                    storeRegisterInMemoryAddress(Register.Y, target.memoryAddress)
                }
                targetArrayIdx!=null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    TODO("assign memory byte $sourceName to array $targetName [ $index ]")
                }
                else -> TODO("assign memory byte $target")
            }
        }
    }

    private fun popAndWriteArrayvalueWithIndexA(arrayDt: DataType, variablename: String) {
        when (arrayDt) {
            DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B ->
                asmgen.out("  tay |  inx |  lda  $ESTACK_LO_HEX,x  | sta  $variablename,y")
            DataType.ARRAY_UW, DataType.ARRAY_W ->
                asmgen.out("  asl  a |  tay |  inx |  lda  $ESTACK_LO_HEX,x |  sta  $variablename,y |  lda  $ESTACK_HI_HEX,x |  sta $variablename+1,y")
            DataType.ARRAY_F ->
                // index * 5 is done in the subroutine that's called
                asmgen.out("""
                    sta  $ESTACK_LO_HEX,x
                    dex
                    lda  #<$variablename
                    ldy  #>$variablename
                    jsr  c64flt.pop_float_to_indexed_var
                """)
            else ->
                throw AssemblyError("weird array type")
        }
    }
}
