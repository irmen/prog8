package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.toHex


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translate(assign: Assignment) {
        when {
            assign.value is NumericLiteralValue -> translateConstantValueAssignment(assign)
            assign.value is IdentifierReference -> translateVariableAssignment(assign)
            assign.isAugmentable -> {
                println("TODO: optimize augmentable assignment  ${assign.position}")   // TODO
                translateOtherAssignment(assign) // TODO generate better code here for augmentable assignments
            }
            else -> translateOtherAssignment(assign)
        }
    }

    internal fun assignToRegister(reg: CpuRegister, value: Short?, identifier: IdentifierReference?) {
        if(value!=null) {
            asmgen.out("  ld${reg.toString().toLowerCase()}  #${value.toHex()}")
        } else if(identifier!=null) {
            val name = asmgen.asmIdentifierName(identifier)
            asmgen.out("  ld${reg.toString().toLowerCase()}  $name")
        }
    }

    private fun translateVariableAssignment(assign: Assignment) {
        val identifier = assign.value as IdentifierReference
        when (val type = assign.target.inferType(program, assign).typeOrElse(DataType.STRUCT)) {
            DataType.UBYTE, DataType.BYTE -> assignFromByteVariable(assign.target, identifier)
            DataType.UWORD, DataType.WORD -> assignFromWordVariable(assign.target, identifier)
            DataType.FLOAT -> assignFromFloatVariable(assign.target, identifier)
            in PassByReferenceDatatypes -> assignFromAddressOf(assign.target, identifier)
            else -> throw AssemblyError("unsupported assignment target type $type")
        }
    }

    private fun translateConstantValueAssignment(assign: Assignment) {
        val numVal = assign.value as NumericLiteralValue
        when (numVal.type) {
            DataType.UBYTE, DataType.BYTE -> assignFromByteConstant(assign.target, numVal.number.toShort())
            DataType.UWORD, DataType.WORD -> assignFromWordConstant(assign.target, numVal.number.toInt())
            DataType.FLOAT -> assignFromFloatConstant(assign.target, numVal.number.toDouble())
            else -> throw AssemblyError("weird numval type")
        }
    }

    private fun translateOtherAssignment(assign: Assignment) {
        when (assign.value) {
            is AddressOf -> {
                val identifier = (assign.value as AddressOf).identifier
                assignFromAddressOf(assign.target, identifier)
            }
            is DirectMemoryRead -> {
                val read = (assign.value as DirectMemoryRead)
                when (read.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (read.addressExpression as NumericLiteralValue).number.toInt()
                        assignFromMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignFromMemoryByte(assign.target, null, read.addressExpression as IdentifierReference)
                    }
                    else -> {
                        asmgen.translateExpression(read.addressExpression)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address |  inx")
                        assignFromRegister(assign.target, CpuRegister.A)
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
                val arrayDt = arrayExpr.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                val index = arrayExpr.arrayspec.index
                if (index is NumericLiteralValue) {
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
                if (sourceType.isKnown && targetType.isKnown &&
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
            is ArrayLiteralValue, is StringLiteralValue -> throw AssemblyError("no asm gen for string/array assignment  $assign")
            is RangeExpr -> throw AssemblyError("range expression should have been changed into array values ${assign.value.position}")
            else -> throw AssemblyError("assignment value type should have been handled elsewhere")
        }
    }

    private fun assignFromEvalResult(target: AssignTarget) {
        val targetIdent = target.identifier
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                when (val targetDt = targetIdent.inferType(program).typeOrElse(DataType.STRUCT)) {
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
            target.memoryAddress != null -> {
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("$ESTACK_LO_HEX,x", target.memoryAddress)
            }
            target.arrayindexed != null -> {
                val arrayDt = target.arrayindexed!!.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                val arrayVarName = asmgen.asmIdentifierName(target.arrayindexed!!.identifier)
                asmgen.translateExpression(target.arrayindexed!!.arrayspec.index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, arrayVarName)
            }
            else -> throw AssemblyError("weird assignment target $target")
        }
    }

    private fun assignFromAddressOf(target: AssignTarget, name: IdentifierReference) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        val struct = name.memberOfStruct(program.namespace)
        val sourceName = if (struct != null) {
            // take the address of the first struct member instead
            val decl = name.targetVarDecl(program.namespace)!!
            val firstStructMember = struct.nameOfFirstMember()
            // find the flattened var that belongs to this first struct member
            val firstVarName = listOf(decl.name, firstStructMember)
            val firstVar = name.definingScope().lookup(firstVarName, name) as VarDecl
            firstVar.name
        } else {
            asmgen.fixNameSymbols(name.nameInSource.joinToString("."))
        }

        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  $targetName
                        sty  $targetName+1
                    """)
            }
            target.memoryAddress != null -> {
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            targetArrayIdx != null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                throw AssemblyError("no asm gen for assign address $sourceName to array $targetName [ $index ]")
            }
            else -> throw AssemblyError("no asm gen for assign address $sourceName to $target")
        }
    }

    private fun assignFromWordVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  $targetName
                    sty  $targetName+1
                """)
            }
            target.memoryAddress != null -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.memoryAddress}")
            }
            targetArrayIdx != null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  lda  $sourceName+1 |  sta  $ESTACK_HI_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                popAndWriteArrayvalueWithIndexA(arrayDt, targetName)
            }
            else -> throw AssemblyError("no asm gen for assign wordvar to $target")
        }
    }

    private fun assignFromFloatVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent != null -> {
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
            targetArrayIdx != null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<$targetName |  ldy  #>$targetName |  jsr  c64flt.pop_float_to_indexed_var")
            }
            else -> throw AssemblyError("no asm gen for assign floatvar to $target")
        }
    }

    private fun assignFromByteVariable(target: AssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("""
                    lda  $sourceName
                    sta  $targetName
                    """)
            }
            targetArrayIdx != null -> {
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
                        storeByteViaRegisterAInMemoryAddress(sourceName, target.memoryAddress)
                    }
                }
            }
            else -> throw AssemblyError("no asm gen for assign bytevar to $target")
        }
    }

    private fun assignFromRegister(target: AssignTarget, register: CpuRegister) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out("  st${register.name.toLowerCase()}  $targetName")
            }
            target.memoryAddress != null -> {
                storeRegisterInMemoryAddress(register, target.memoryAddress)
            }
            targetArrayIdx != null -> {
                val index = targetArrayIdx.arrayspec.index
                val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                when (index) {
                    is NumericLiteralValue -> {
                        val memindex = index.number.toInt()
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  $targetName+$memindex")
                            CpuRegister.X -> asmgen.out("  stx  $targetName+$memindex")
                            CpuRegister.Y -> asmgen.out("  sty  $targetName+$memindex")
                        }
                    }
                    is IdentifierReference -> {
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${C64Zeropage.SCRATCH_B1}")
                            CpuRegister.X -> asmgen.out("  stx  ${C64Zeropage.SCRATCH_B1}")
                            CpuRegister.Y -> asmgen.out("  sty  ${C64Zeropage.SCRATCH_B1}")
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
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${C64Zeropage.SCRATCH_B1}")
                            CpuRegister.X -> asmgen.out("  stx  ${C64Zeropage.SCRATCH_B1}")
                            CpuRegister.Y -> asmgen.out("  sty  ${C64Zeropage.SCRATCH_B1}")
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
            else -> throw AssemblyError("no asm gen for assign register $register to $target")
        }
    }

    private fun storeByteViaRegisterAInMemoryAddress(ldaInstructionArg: String, memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        when {
            addressLv != null -> asmgen.out("  lda $ldaInstructionArg |  sta  ${addressLv.number.toHex()}")
            addressExpr is IdentifierReference -> {
                val targetName = asmgen.asmIdentifierName(addressExpr)
                asmgen.out("""
        lda  $targetName
        sta  ${C64Zeropage.SCRATCH_W1}
        lda  $targetName+1
        sta  ${C64Zeropage.SCRATCH_W1+1}
        lda  $ldaInstructionArg
        ldy  #0
        sta  (${C64Zeropage.SCRATCH_W1}),y""")
            }
            else -> {
                asmgen.translateExpression(addressExpr)
                asmgen.out("""
        inx
        lda  $ESTACK_LO_HEX,x
        sta  ${C64Zeropage.SCRATCH_W1}
        lda  $ESTACK_HI_HEX,x
        sta  ${C64Zeropage.SCRATCH_W1+1}
        lda  $ldaInstructionArg
        ldy  #0
        sta  (${C64Zeropage.SCRATCH_W1}),y""")
            }
        }
    }

    private fun storeRegisterInMemoryAddress(register: CpuRegister, memoryAddress: DirectMemoryWrite) {
        // this is optimized for register A.
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        val registerName = register.name.toLowerCase()
        when {
            addressLv != null -> asmgen.out("  st$registerName  ${addressLv.number.toHex()}")
            addressExpr is IdentifierReference -> {
                val targetName = asmgen.asmIdentifierName(addressExpr)
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                asmgen.out("""
        ldy  $targetName
        sty  ${C64Zeropage.SCRATCH_W1}
        ldy  $targetName+1
        sty  ${C64Zeropage.SCRATCH_W1+1}
        ldy  #0
        sta  (${C64Zeropage.SCRATCH_W1}),y""")
            }
            else -> {
                asmgen.saveRegister(register)
                asmgen.translateExpression(addressExpr)
                asmgen.restoreRegister(CpuRegister.A)
                asmgen.out("""
        inx
        ldy  $ESTACK_LO_HEX,x
        sty  ${C64Zeropage.SCRATCH_W1}
        ldy  $ESTACK_HI_HEX,x
        sty  ${C64Zeropage.SCRATCH_W1+1}
        ldy  #0
        sta  (${C64Zeropage.SCRATCH_W1}),y""")
            }
        }
    }

    private fun assignFromWordConstant(target: AssignTarget, word: Int) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                if (word ushr 8 == word and 255) {
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
            target.memoryAddress != null -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.memoryAddress}")
            }
            targetArrayIdx != null -> {
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
            else -> throw AssemblyError("no asm gen for assign word $word to $target")
        }
    }

    private fun assignFromByteConstant(target: AssignTarget, byte: Short) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        val targetMemory = target.memoryAddress
        when {
            targetIdent != null -> {
                val targetName = asmgen.asmIdentifierName(targetIdent)
                asmgen.out(" lda  #${byte.toHex()} |  sta  $targetName ")
            }
            targetMemory != null -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", targetMemory)
            }
            targetArrayIdx != null -> {
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
            else -> throw AssemblyError("no asm gen for assign byte $byte to $target")
        }
    }

    private fun assignFromFloatConstant(target: AssignTarget, float: Double) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        if (float == 0.0) {
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
                targetArrayIdx != null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    if (index is NumericLiteralValue) {
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
                            lda  #<${targetName}
                            sta  ${C64Zeropage.SCRATCH_W1}
                            lda  #>${targetName}
                            sta  ${C64Zeropage.SCRATCH_W1 + 1}
                            jsr  c64flt.set_0_array_float
                        """)
                    }
                }
                else -> throw AssemblyError("no asm gen for assign float 0.0 to $target")
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
                targetArrayIdx != null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val arrayVarName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    if (index is NumericLiteralValue) {
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
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            lda  #<${constFloat}
                            sta  ${C64Zeropage.SCRATCH_W1}
                            lda  #>${constFloat}
                            sta  ${C64Zeropage.SCRATCH_W1 + 1}
                            lda  #<${arrayVarName}
                            sta  ${C64Zeropage.SCRATCH_W2}
                            lda  #>${arrayVarName}
                            sta  ${C64Zeropage.SCRATCH_W2 + 1}
                            jsr  c64flt.set_array_float
                        """)
                    }
                }
                else -> throw AssemblyError("no asm gen for assign float $float to $target")
            }
        }
    }

    private fun assignFromMemoryByte(target: AssignTarget, address: Int?, identifier: IdentifierReference?) {
        val targetIdent = target.identifier
        val targetArrayIdx = target.arrayindexed
        if (address != null) {
            when {
                targetIdent != null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  $targetName
                        """)
                }
                target.memoryAddress != null -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.memoryAddress)
                }
                targetArrayIdx != null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    throw AssemblyError("no asm gen for assign memory byte at $address to array $targetName [ $index ]")
                }
                else -> throw AssemblyError("no asm gen for assign memory byte $target")
            }
        } else if (identifier != null) {
            val sourceName = asmgen.asmIdentifierName(identifier)
            when {
                targetIdent != null -> {
                    val targetName = asmgen.asmIdentifierName(targetIdent)
                    asmgen.out("""
    lda  $sourceName
    sta  (+) + 1
    lda  $sourceName+1
    sta  (+) + 2
+   lda  ${'$'}ffff\t; modified
    sta  $targetName""")
                }
                target.memoryAddress != null -> {
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memoryAddress)
                }
                targetArrayIdx != null -> {
                    val index = targetArrayIdx.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(targetArrayIdx.identifier)
                    throw AssemblyError("no asm gen for assign memory byte $sourceName to array $targetName [ $index ]")
                }
                else -> throw AssemblyError("no asm gen for assign memory byte $target")
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
