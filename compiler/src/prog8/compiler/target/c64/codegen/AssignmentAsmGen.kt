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
        var source = AsmAssignSource.fromAstSource(assignment.value, program)
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)

        // allow some signed/unsigned relaxations
        if(target.datatype!=source.datatype) {
            if(target.datatype in ByteDatatypes && source.datatype in ByteDatatypes) {
                source = source.withAdjustedDt(target.datatype)
            } else if(target.datatype in WordDatatypes && source.datatype in WordDatatypes) {
                source = source.withAdjustedDt(target.datatype)
            }
        }

        val assign = AsmAssignment(source, target, assignment.isAugmentable, assignment.position)

        when {
            source.kind==SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.source.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toShort())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            source.kind==SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.variable!!
                when (assign.source.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignVariableByte(assign.target, variable)
                    DataType.UWORD, DataType.WORD -> assignVariableWord(assign.target, variable)
                    DataType.FLOAT -> assignVariableFloat(assign.target, variable)
                    in PassByReferenceDatatypes -> assignAddressOf(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
                }
            }
            assign.isAugmentable -> {
                // special case: the in-place assignment / modification
                augmentableAsmGen.translate(assign)
            }
            else -> translateOtherAssignment(assign)
        }
    }

    internal fun translateOtherAssignment(assign: AsmAssignment) {
        // source kind: expression, register, stack  (only expression implemented for now)

        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER,
            SourceStorageKind.VARIABLE -> {
                throw AssemblyError("assignment value type ${assign.source.kind} should have been handled elsewhere")
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
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
                assignStackValue(assign.target)
            }
            SourceStorageKind.MEMORY -> {
                val value = assign.source.memory!!
                when (value.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignMemoryByte(assign.target, null, value.addressExpression as IdentifierReference)
                    }
                    else -> {
                        asmgen.translateExpression(value.addressExpression)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  inx")
                        assignRegisterByte(assign.target, CpuRegister.A)
                    }
                }
            }
            SourceStorageKind.EXPRESSION -> {
                val value = assign.source.expression!!
                if (value is AddressOf) {
                    assignAddressOf(assign.target, value.identifier)
                }
                else {
                    asmgen.translateExpression(value)
                    assignStackValue(assign.target)
                }
            }
            SourceStorageKind.REGISTER -> {
                assignRegisterByte(assign.target, assign.source.register!!)
            }
            SourceStorageKind.STACK -> {
                assignStackValue(assign.target)
            }
        }
    }

    internal fun assignToRegister(reg: CpuRegister, value: Int?, identifier: IdentifierReference?) {
        if(value!=null) {
            asmgen.out("  ld${reg.toString().toLowerCase()}  #${value.toHex()}")
        } else if(identifier!=null) {
            val name = asmgen.asmIdentifierName(identifier)
            asmgen.out("  ld${reg.toString().toLowerCase()}  $name")
        }
    }

    private fun assignStackValue(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
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
            TargetStorageKind.MEMORY -> {
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("$ESTACK_LO_HEX,x", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val targetArrayIdx = target.array!!
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                asmgen.translateExpression(targetArrayIdx.arrayspec.index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, name: IdentifierReference) {
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

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmName}
                        sty  ${target.asmName}+1
                    """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            TargetStorageKind.ARRAY -> {
                val targetArrayIdx = target.array!!
                val index = targetArrayIdx.arrayspec.index
                throw AssemblyError("no asm gen for assign address $sourceName to array ${target.asmName} [ $index ]")
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  ${target.asmName}
                    sty  ${target.asmName}+1
                """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                val targetArrayIdx = target.array!!
                val index = targetArrayIdx.arrayspec.index
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  lda  $sourceName+1 |  sta  $ESTACK_HI_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignVariableFloat(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
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
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.array.identifier)
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<$targetName |  ldy  #>$targetName |  jsr  c64flt.pop_float_to_indexed_var")
            }
            else -> throw AssemblyError("no asm gen for assign floatvar to $target")
        }
    }

    private fun assignVariableByte(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmIdentifierName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmName}
                    """)
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val targetArrayIdx = target.array!!
                val index = targetArrayIdx.arrayspec.index
                val arrayDt = targetArrayIdx.identifier.inferType(program).typeOrElse(DataType.STRUCT)
                asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  dex")
                asmgen.translateExpression(index)
                asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                popAndWriteArrayvalueWithIndexA(arrayDt, target.asmName)
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        require(target.datatype in ByteDatatypes)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.toLowerCase()}  ${target.asmName}")
            }
            TargetStorageKind.MEMORY -> {
                storeRegisterInMemoryAddress(register, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val targetArrayIdx = target.array!!
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
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
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
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.array.identifier)
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
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignConstantByte(target: AsmAssignTarget, byte: Short) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out(" lda  #${byte.toHex()} |  sta  ${target.asmName} ")
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                val targetName = asmgen.asmIdentifierName(target.array.identifier)
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

    private fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                            lda  #0
                            sta  ${target.asmName}
                            sta  ${target.asmName}+1
                            sta  ${target.asmName}+2
                            sta  ${target.asmName}+3
                            sta  ${target.asmName}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    val index = target.array!!.arrayspec.index
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
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
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
                TargetStorageKind.ARRAY -> {
                    val index = target.array!!.arrayspec.index
                    val arrayVarName = asmgen.asmIdentifierName(target.array.identifier)
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

    private fun assignMemoryByte(target: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmName}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    val index = target.array!!.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(target.array.identifier)
                    throw AssemblyError("no asm gen for assign memory byte at $address to array $targetName [ $index ]")
                }
                TargetStorageKind.REGISTER -> TODO()
                TargetStorageKind.STACK -> TODO()
            }
        } else if (identifier != null) {
            val sourceName = asmgen.asmIdentifierName(identifier)
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
    lda  $sourceName
    sta  ${C64Zeropage.SCRATCH_W1}
    lda  $sourceName+1
    sta  ${C64Zeropage.SCRATCH_W1+1}
    ldy  #0
    lda  (${C64Zeropage.SCRATCH_W1}),y
    sta  ${target.asmName}""")
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    val index = target.array!!.arrayspec.index
                    val targetName = asmgen.asmIdentifierName(target.array.identifier)
                    throw AssemblyError("no asm gen for assign memory byte $sourceName to array $targetName [ $index ]")
                }
                else -> throw AssemblyError("no asm gen for assign memory byte $target")
            }
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

    private fun popAndWriteArrayvalueWithIndexA(arrayDt: DataType, arrayvarname: String) {
        when (arrayDt) {
            DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B ->
                asmgen.out("  tay |  inx |  lda  $ESTACK_LO_HEX,x  | sta  $arrayvarname,y")
            DataType.ARRAY_UW, DataType.ARRAY_W ->
                asmgen.out("  asl  a |  tay |  inx |  lda  $ESTACK_LO_HEX,x |  sta  $arrayvarname,y |  lda  $ESTACK_HI_HEX,x |  sta $arrayvarname+1,y")
            DataType.ARRAY_F ->
                // index * 5 is done in the subroutine that's called
                asmgen.out("""
                    sta  $ESTACK_LO_HEX,x
                    dex
                    lda  #<$arrayvarname
                    ldy  #>$arrayvarname
                    jsr  c64flt.pop_float_to_indexed_var
                """)
            else ->
                throw AssemblyError("weird array type")
        }
    }
}
