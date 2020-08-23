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

// TODO optimize the array indexes where the index is a constant


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen)

    internal fun translate(assignment: Assignment) {
        val source = AsmAssignSource.fromAstSource(assignment.value, program)
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)
        val assign = AsmAssignment(source, target, assignment.isAugmentable, assignment.position)

        when {
            source.type==AsmSourceStorageType.LITERALNUMBER -> translateConstantValueAssignment(assign)
            source.type==AsmSourceStorageType.VARIABLE -> translateVariableAssignment(assign)
            assign.isAugmentable -> augmentableAsmGen.translate(assign)
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

    private fun translateVariableAssignment(assign: AsmAssignment) {
        val identifier = assign.source.astVariable!!
        when (assign.target.datatype) {
            DataType.UBYTE, DataType.BYTE -> assignFromByteVariable(assign.target, identifier)
            DataType.UWORD, DataType.WORD -> assignFromWordVariable(assign.target, identifier)
            DataType.FLOAT -> assignFromFloatVariable(assign.target, identifier)
            in PassByReferenceDatatypes -> assignFromAddressOf(assign.target, identifier)
            else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
        }
    }

    private fun translateConstantValueAssignment(assign: AsmAssignment) {
        val numVal = assign.source.numLitval!!
        when (numVal.type) {
            DataType.UBYTE, DataType.BYTE -> assignFromByteConstant(assign.target, numVal.number.toShort())
            DataType.UWORD, DataType.WORD -> assignFromWordConstant(assign.target, numVal.number.toInt())
            DataType.FLOAT -> assignFromFloatConstant(assign.target, numVal.number.toDouble())
            else -> throw AssemblyError("weird numval type")
        }
    }

    internal fun translateOtherAssignment(assign: AsmAssignment) {
        // source: expression, register, stack  (only expression implemented for now)

        when(assign.source.type) {
            AsmSourceStorageType.LITERALNUMBER,
            AsmSourceStorageType.VARIABLE -> {
                throw AssemblyError("assignment value type ${assign.source.type} should have been handled elsewhere")
            }
            AsmSourceStorageType.ARRAY -> {
                val value = assign.source.astArray!!
                val elementDt = value.inferType(program).typeOrElse(DataType.STRUCT)
                val index = value.arrayspec.index
                if (index is NumericLiteralValue) {
                    // constant array index value
                    val arrayVarName = asmgen.asmIdentifierName(value.identifier)
                    val indexValue = index.number.toInt() * elementDt.memorySize()
                    when (elementDt) {
                        in ByteDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  $ESTACK_LO_HEX,x |  dex")
                        in WordDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  $ESTACK_LO_HEX,x |  lda  $arrayVarName+$indexValue+1 |  sta  $ESTACK_HI_HEX,x | dex")
                        DataType.FLOAT ->
                            asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  c64flt.push_float")
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    asmgen.translateArrayIndexIntoA(value)
                    asmgen.readAndPushArrayvalueWithIndexA(elementDt, value.identifier)
                }
                assignFromEvalResult(assign.target)
            }
            AsmSourceStorageType.MEMORY -> {
                val value = assign.source.astMemory!!
                when (value.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignFromMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignFromMemoryByte(assign.target, null, value.addressExpression as IdentifierReference)
                    }
                    else -> {
                        asmgen.translateExpression(value.addressExpression)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  inx")
                        assignByteFromRegister(assign.target, CpuRegister.A)
                    }
                }
            }
            AsmSourceStorageType.EXPRESSION -> {
                val value = assign.source.astExpression!!
                if (value is AddressOf) {
                    assignFromAddressOf(assign.target, value.identifier)
                }
                else {
                    asmgen.translateExpression(value)
                    assignFromEvalResult(assign.target)
                }
            }
            AsmSourceStorageType.REGISTER -> {
                assignByteFromRegister(assign.target, assign.source.register!!)
            }
            AsmSourceStorageType.STACK -> {
                when(assign.source.datatype) {
                    in ByteDatatypes -> {
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  inx")
                        assignByteFromRegister(assign.target, CpuRegister.A)
                    }
                    in WordDatatypes -> TODO("assign word from stack")
                    DataType.FLOAT -> TODO("assign float from stack")
                    else -> throw AssemblyError("weird stack value type")
                }
            }
        }
    }

    private fun assignFromEvalResult(target: AsmAssignTarget) {
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  $ESTACK_LO_HEX,x  | sta  ${target.asmName}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  $ESTACK_LO_HEX,x
                            sta  ${target.asmName}
                            lda  $ESTACK_HI_HEX,x
                            sta  ${target.asmName}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmName}
                            ldy  #>${target.asmName}
                            jsr  c64flt.pop_float
                        """)
                    }
                    else -> throw AssemblyError("weird target variable type ${target.datatype}")
                }
            }
            AsmTargetStorageType.MEMORY -> {
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("$ESTACK_LO_HEX,x", target.astMemory!!)
            }
            AsmTargetStorageType.ARRAY -> {
                val targetArrayIdx = target.astArray!!
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                asmgen.translateExpression(targetArrayIdx.arrayspec.index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun assignFromAddressOf(target: AsmAssignTarget, name: IdentifierReference) {
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

        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmName}
                        sty  ${target.asmName}+1
                    """)
            }
            AsmTargetStorageType.MEMORY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            AsmTargetStorageType.ARRAY -> {
                val targetArrayIdx = target.astArray!!
                val index = targetArrayIdx.arrayspec.index
                throw AssemblyError("no asm gen for assign address $sourceName to array ${target.asmName} [ $index ]")
            }
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun assignFromWordVariable(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  ${target.asmName}
                    sty  ${target.asmName}+1
                """)
            }
            AsmTargetStorageType.MEMORY -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.astMemory}")
            }
            AsmTargetStorageType.ARRAY -> {
                val targetArrayIdx = target.astArray!!
                val index = targetArrayIdx.arrayspec.index
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  lda  $sourceName+1 |  sta  $ESTACK_HI_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun assignFromFloatVariable(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmName}
                    lda  $sourceName+1
                    sta  ${target.asmName}+1
                    lda  $sourceName+2
                    sta  ${target.asmName}+2
                    lda  $sourceName+3
                    sta  ${target.asmName}+3
                    lda  $sourceName+4
                    sta  ${target.asmName}+4
                """)
            }
            AsmTargetStorageType.ARRAY -> {
                val index = target.astArray!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.astArray.identifier)
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<$targetName |  ldy  #>$targetName |  jsr  c64flt.pop_float_to_indexed_var")
            }
            else -> throw AssemblyError("no asm gen for assign floatvar to $target")
        }
    }

    private fun assignFromByteVariable(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmName}
                    """)
            }
            AsmTargetStorageType.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress(sourceName, target.astMemory!!)
            }
            AsmTargetStorageType.ARRAY -> {
                val targetArrayIdx = target.astArray!!
                val index = targetArrayIdx.arrayspec.index
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun assignByteFromRegister(target: AsmAssignTarget, register: CpuRegister) {
        require(target.datatype in ByteDatatypes)
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out("  st${register.name.toLowerCase()}  ${target.asmName}")
            }
            AsmTargetStorageType.MEMORY -> {
                storeRegisterInMemoryAddress(register, target.astMemory!!)
            }
            AsmTargetStorageType.ARRAY -> {
                val targetArrayIdx = target.astArray!!
                val index = targetArrayIdx.arrayspec.index
                when (index) {
                    is NumericLiteralValue -> {
                        val memindex = index.number.toInt()
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${target.asmName}+$memindex")
                            CpuRegister.X -> asmgen.out("  stx  ${target.asmName}+$memindex")
                            CpuRegister.Y -> asmgen.out("  sty  ${target.asmName}+$memindex")
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
                            sta  ${target.asmName},y
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
                            sta  ${target.asmName},y  
                        """)
                    }
                }
            }
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun storeByteViaRegisterAInMemoryAddress(ldaInstructionArg: String, memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        when {
            addressLv != null -> {
                asmgen.out("  lda $ldaInstructionArg |  sta  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                val pointerVarName = asmgen.asmIdentifierName(addressExpr)
                asmgen.out("""
                    lda  $pointerVarName
                    sta  ${C64Zeropage.SCRATCH_W2}
                    lda  $pointerVarName+1
                    sta  ${C64Zeropage.SCRATCH_W2+1}
                    lda  $ldaInstructionArg
                    ldy  #0
                    sta  (${C64Zeropage.SCRATCH_W2}),y""")
            }
            else -> {
                asmgen.translateExpression(addressExpr)
                asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    sta  ${C64Zeropage.SCRATCH_W2}
                    lda  $ESTACK_HI_HEX,x
                    sta  ${C64Zeropage.SCRATCH_W2+1}
                    lda  $ldaInstructionArg
                    ldy  #0
                    sta  (${C64Zeropage.SCRATCH_W2}),y""")
            }
        }
    }

    private fun storeRegisterInMemoryAddress(register: CpuRegister, memoryAddress: DirectMemoryWrite) {
        // this is optimized for register A.
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        val registerName = register.name.toLowerCase()
        when {
            addressLv != null -> {
                asmgen.out("  st$registerName  ${addressLv.number.toHex()}")
            }
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

    private fun assignFromWordConstant(target: AsmAssignTarget, word: Int) {
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                if (word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmName}
                    sta  ${target.asmName}+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmName}
                    sty  ${target.asmName}+1
                """)
                }
            }
            AsmTargetStorageType.MEMORY -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.astMemory}")
            }
            AsmTargetStorageType.ARRAY -> {
                val index = target.astArray!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.astArray.identifier)
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
            AsmTargetStorageType.REGISTER -> TODO()
            AsmTargetStorageType.STACK -> TODO()
        }
    }

    private fun assignFromByteConstant(target: AsmAssignTarget, byte: Short) {
        when(target.type) {
            AsmTargetStorageType.VARIABLE -> {
                asmgen.out(" lda  #${byte.toHex()} |  sta  ${target.asmName} ")
            }
            AsmTargetStorageType.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", target.astMemory!!)
            }
            AsmTargetStorageType.ARRAY -> {
                val index = target.astArray!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.astArray.identifier)
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

    private fun assignFromFloatConstant(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.type) {
                AsmTargetStorageType.VARIABLE -> {
                    asmgen.out("""
                            lda  #0
                            sta  ${target.asmName}
                            sta  ${target.asmName}+1
                            sta  ${target.asmName}+2
                            sta  ${target.asmName}+3
                            sta  ${target.asmName}+4
                        """)
                }
                AsmTargetStorageType.ARRAY -> {
                    val index = target.astArray!!.arrayspec.index
                    if (index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * C64MachineDefinition.FLOAT_MEM_SIZE
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmName}+$indexValue
                            sta  ${target.asmName}+$indexValue+1
                            sta  ${target.asmName}+$indexValue+2
                            sta  ${target.asmName}+$indexValue+3
                            sta  ${target.asmName}+$indexValue+4
                        """)
                    } else {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            lda  #<${target.asmName}
                            sta  ${C64Zeropage.SCRATCH_W1}
                            lda  #>${target.asmName}
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
            when(target.type) {
                AsmTargetStorageType.VARIABLE -> {
                    asmgen.out("""
                            lda  $constFloat
                            sta  ${target.asmName}
                            lda  $constFloat+1
                            sta  ${target.asmName}+1
                            lda  $constFloat+2
                            sta  ${target.asmName}+2
                            lda  $constFloat+3
                            sta  ${target.asmName}+3
                            lda  $constFloat+4
                            sta  ${target.asmName}+4
                        """)
                }
                AsmTargetStorageType.ARRAY -> {
                    val index = target.astArray!!.arrayspec.index
                    val arrayVarName = asmgen.asmIdentifierName(target.astArray.identifier)
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

    private fun assignFromMemoryByte(target: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(target.type) {
                AsmTargetStorageType.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmName}
                        """)
                }
                AsmTargetStorageType.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.astMemory!!)
                }
                AsmTargetStorageType.ARRAY -> {
                    val index = target.astArray!!.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(target.astArray.identifier)
                    throw AssemblyError("no asm gen for assign memory byte at $address to array $targetName [ $index ]")
                }
                AsmTargetStorageType.REGISTER -> TODO()
                AsmTargetStorageType.STACK -> TODO()
            }
        } else if (identifier != null) {
            val sourceName = asmgen.asmIdentifierName(identifier)
            when(target.type) {
                AsmTargetStorageType.VARIABLE -> {
                    asmgen.out("""
    lda  $sourceName
    sta  ${C64Zeropage.SCRATCH_W1}
    lda  $sourceName+1
    sta  ${C64Zeropage.SCRATCH_W1+1}
    ldy  #0
    lda  (${C64Zeropage.SCRATCH_W1}),y
    sta  ${target.asmName}""")
                }
                AsmTargetStorageType.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.astMemory!!)
                }
                AsmTargetStorageType.ARRAY -> {
                    val index = target.astArray!!.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(target.astArray.identifier)
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
