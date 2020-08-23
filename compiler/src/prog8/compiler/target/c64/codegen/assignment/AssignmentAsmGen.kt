package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.toHex


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen)

    fun translate(assignment: Assignment) {
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
        target.origAssign = assign

        if(assign.isAugmentable)
            augmentableAsmGen.translate(assign)
        else
            translateNormalAssignment(assign)
    }

    fun translateNormalAssignment(assign: AsmAssignment) {
        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.source.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toShort())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
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
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val index = value.arrayspec.index
                val arrayVarName = asmgen.asmIdentifierName(value.identifier)
                if (index is NumericLiteralValue) {
                    // constant array index value
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
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  $ESTACK_LO_HEX,x |  dex")
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  $ESTACK_LO_HEX,x |  lda  $arrayVarName+1,y |  sta  $ESTACK_HI_HEX,x |  dex")
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>$arrayVarName
                                clc
                                adc  #<$arrayVarName
                                bcc  +
                                iny
+                               jsr  c64flt.push_float""")
                        }
                        else ->
                            throw AssemblyError("weird array elt type")
                    }
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

    fun assignToRegister(reg: CpuRegister, value: Int?, identifier: IdentifierReference?) {
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
                        asmgen.out(" inx | lda  $ESTACK_LO_HEX,x  | sta  ${target.asmVarname}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  $ESTACK_LO_HEX,x
                            sta  ${target.asmVarname}
                            lda  $ESTACK_HI_HEX,x
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
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
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        when(target.datatype) {
                            in ByteDatatypes -> {
                                asmgen.out(" inx | lda  $ESTACK_LO_HEX,x  | sta  ${target.asmVarname}+$scaledIdx")
                            }
                            in WordDatatypes -> {
                                asmgen.out("""
                                    inx
                                    lda  $ESTACK_LO_HEX,x
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  $ESTACK_HI_HEX,x
                                    sta  ${target.asmVarname}+$scaledIdx+1
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.out("""
                                    lda  #<${target.asmVarname}+$scaledIdx
                                    ldy  #>${target.asmVarname}+$scaledIdx
                                    jsr  c64flt.pop_float
                                """)
                            }
                            else -> throw AssemblyError("weird target variable type ${target.datatype}")
                        }
                    }
                    index is IdentifierReference -> {
                        when(target.datatype) {
                            DataType.UBYTE, DataType.BYTE -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out(" inx |  lda  $ESTACK_LO_HEX,x |  sta  ${target.asmVarname},y")
                            }
                            DataType.UWORD, DataType.WORD -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out("""
                                    inx
                                    lda  $ESTACK_LO_HEX,x
                                    sta  ${target.asmVarname},y
                                    lda  $ESTACK_HI_HEX,x
                                    sta  ${target.asmVarname}+1,y
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                                asmgen.out("""
                                    ldy  #>${target.asmVarname}
                                    clc
                                    adc  #<${target.asmVarname}
                                    bcc  +
                                    iny
+                                   jsr  c64flt.pop_float""")
                            }
                            else -> throw AssemblyError("weird dt")
                        }
                    }
                    else -> {
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
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
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1
                    """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            TargetStorageKind.ARRAY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to array ${target.asmVarname}")
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
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        when(target.datatype) {
                            in ByteDatatypes -> {
                                asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                            }
                            in WordDatatypes -> {
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+$scaledIdx+1
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.out("""
                                    lda  #<$sourceName
                                    ldy  #>$sourceName
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    sty  ${C64Zeropage.SCRATCH_W1+1}
                                    lda  #<${target.asmVarname}+$scaledIdx
                                    ldy  #>${target.asmVarname}+$scaledIdx
                                    jsr  c64flt.copy_float
                                """)
                            }
                            else -> throw AssemblyError("weird target variable type ${target.datatype}")
                        }
                    }
                    index is IdentifierReference -> {
                        when(target.datatype) {
                            DataType.UBYTE, DataType.BYTE -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                            }
                            DataType.UWORD, DataType.WORD -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname},y
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+1,y
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                                asmgen.out("""
                                    ldy  #<$sourceName
                                    sty  ${C64Zeropage.SCRATCH_W1}
                                    ldy  #>$sourceName
                                    sty  ${C64Zeropage.SCRATCH_W1+1}
                                    ldy  #>${target.asmVarname}
                                    clc
                                    adc  #<${target.asmVarname}
                                    bcc  +
                                    iny
+                                   jsr  c64flt.copy_float""")
                            }
                            else -> throw AssemblyError("weird dt")
                        }
                    }
                    else -> {
                        asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  lda  $sourceName+1 |  sta  $ESTACK_HI_HEX,x |  dex")
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
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
                    sta  ${target.asmVarname}
                    lda  $sourceName+1
                    sta  ${target.asmVarname}+1
                    lda  $sourceName+2
                    sta  ${target.asmVarname}+2
                    lda  $sourceName+3
                    sta  ${target.asmVarname}+3
                    lda  $sourceName+4
                    sta  ${target.asmVarname}+4
                """)
            }
            TargetStorageKind.ARRAY -> {
                // TODO optimize this, but the situation doesn't occur very often
//                if(target.constArrayIndexValue!=null) {
//                    TODO("const index ${target.constArrayIndexValue}")
//                } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                    TODO("array[var] ${target.constArrayIndexValue}")
//                }
                val index = target.array!!.arrayspec.index
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname} |  jsr  c64flt.pop_float_to_indexed_var")
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
                    sta  ${target.asmVarname}
                    """)
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                    }
                    index is IdentifierReference -> {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                        asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                    }
                    else -> {
                        asmgen.out("  lda  $sourceName |  sta  $ESTACK_LO_HEX,x |  dex")
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        require(target.datatype in ByteDatatypes)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.toLowerCase()}  ${target.asmVarname}")
            }
            TargetStorageKind.MEMORY -> {
                storeRegisterInMemoryAddress(register, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when (index) {
                    is NumericLiteralValue -> {
                        val memindex = index.number.toInt()
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+$memindex")
                            CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+$memindex")
                            CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+$memindex")
                        }
                    }
                    is IdentifierReference -> {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        asmgen.out(" ldy  ${asmgen.asmIdentifierName(index)} |  sta  ${target.asmVarname},y")
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
                            sta  ${target.asmVarname},y  
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
                    sta  ${target.asmVarname}
                    sta  ${target.asmVarname}+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
                }
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                // TODO optimize this, but the situation doesn't occur very often
//                if(target.constArrayIndexValue!=null) {
//                    TODO("const index ${target.constArrayIndexValue}")
//                } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                    TODO("array[var] ${target.constArrayIndexValue}")
//                }
                val index = target.array!!.arrayspec.index
                asmgen.translateExpression(index)
                asmgen.out("""
                    inx
                    lda  $ESTACK_LO_HEX,x
                    asl  a
                    tay
                    lda  #<${word.toHex()}
                    sta  ${target.asmVarname},y
                    lda  #>${word.toHex()}
                    sta  ${target.asmVarname}+1,y
                """)
            }
            TargetStorageKind.REGISTER -> TODO()
            TargetStorageKind.STACK -> TODO()
        }
    }

    private fun assignConstantByte(target: AsmAssignTarget, byte: Short) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                    }
                    index is IdentifierReference -> {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UBYTE, CpuRegister.Y)
                        asmgen.out("  lda  #<${byte.toHex()} |  sta  ${target.asmVarname},y |  lda  #>${byte.toHex()} |  sta  ${target.asmVarname}+1,y")
                    }
                    else -> {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            inx
                            ldy  $ESTACK_LO_HEX,x
                            lda  #${byte.toHex()}
                            sta  ${target.asmVarname},y
                        """)
                    }
                }
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
                            sta  ${target.asmVarname}
                            sta  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    // TODO optimize this, but the situation doesn't occur very often
//                    if(target.constArrayIndexValue!=null) {
//                        TODO("const index ${target.constArrayIndexValue}")
//                    } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                        TODO("array[var] ${target.constArrayIndexValue}")
//                    }
                    val index = target.array!!.arrayspec.index
                    if (index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * DataType.FLOAT.memorySize()
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}+$indexValue
                            sta  ${target.asmVarname}+$indexValue+1
                            sta  ${target.asmVarname}+$indexValue+2
                            sta  ${target.asmVarname}+$indexValue+3
                            sta  ${target.asmVarname}+$indexValue+4
                        """)
                    } else {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  ${C64Zeropage.SCRATCH_W1}
                            lda  #>${target.asmVarname}
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
                            sta  ${target.asmVarname}
                            lda  $constFloat+1
                            sta  ${target.asmVarname}+1
                            lda  $constFloat+2
                            sta  ${target.asmVarname}+2
                            lda  $constFloat+3
                            sta  ${target.asmVarname}+3
                            lda  $constFloat+4
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    // TODO optimize this, but the situation doesn't occur very often
//                    if(target.constArrayIndexValue!=null) {
//                        TODO("const index ${target.constArrayIndexValue}")
//                    } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                        TODO("array[var] ${target.constArrayIndexValue}")
//                    }
                    val index = target.array!!.arrayspec.index
                    val arrayVarName = target.asmVarname
                    if (index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * DataType.FLOAT.memorySize()
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
                        sta  ${target.asmVarname}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${target.asmVarname}")
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
    sta  ${target.asmVarname}""")
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $sourceName to array ${target.asmVarname} ")
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

    private fun popAndWriteArrayvalueWithUnscaledIndexA(elementDt: DataType, asmArrayvarname: String) {
        when (elementDt) {
            in ByteDatatypes ->
                asmgen.out("  tay |  inx |  lda  $ESTACK_LO_HEX,x  | sta  $asmArrayvarname,y")
            in WordDatatypes ->
                asmgen.out("  asl  a |  tay |  inx |  lda  $ESTACK_LO_HEX,x |  sta  $asmArrayvarname,y |  lda  $ESTACK_HI_HEX,x |  sta $asmArrayvarname+1,y")
            DataType.FLOAT ->
                // scaling * 5 is done in the subroutine that's called
                asmgen.out("""
                    sta  $ESTACK_LO_HEX,x
                    dex
                    lda  #<$asmArrayvarname
                    ldy  #>$asmArrayvarname
                    jsr  c64flt.pop_float_to_indexed_var
                """)
            else ->
                throw AssemblyError("weird array type")
        }
    }
}
