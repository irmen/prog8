package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.CpuType
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.target.c64.codegen.ExpressionsAsmGen
import prog8.compiler.toHex


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen, private val exprAsmgen: ExpressionsAsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, exprAsmgen, asmgen)

    fun translate(assignment: Assignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program, asmgen).adjustSignedUnsigned(target)

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
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toShort())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.asmVarname
                when (assign.target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignVariableByte(assign.target, variable)
                    DataType.UWORD, DataType.WORD -> assignVariableWord(assign.target, variable)
                    DataType.FLOAT -> assignVariableFloat(assign.target, variable)
                    DataType.STR -> assignVariableString(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val arrayVarName = asmgen.asmVariableName(value.arrayvar)
                if (value.indexer.indexNum!=null) {
                    // constant array index value
                    val indexValue = value.indexer.constIndex()!! * elementDt.memorySize()
                    when (elementDt) {
                        in ByteDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  dex")
                        in WordDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+$indexValue+1 |  sta  P8ESTACK_HI,x | dex")
                        DataType.FLOAT ->
                            asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  floats.push_float")
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  dex")
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+1,y |  sta  P8ESTACK_HI,x |  dex")
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>$arrayVarName
                                clc
                                adc  #<$arrayVarName
                                bcc  +
                                iny
+                               jsr  floats.push_float""")
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
                when(val value = assign.source.expression!!) {
                    is AddressOf -> {
                        val sourceName = value.identifier.firstStructVarName(program.namespace) ?: asmgen.asmVariableName(value.identifier)
                        assignAddressOf(assign.target, sourceName)
                    }
                    is NumericLiteralValue -> throw AssemblyError("source kind should have been literalnumber")
                    is IdentifierReference -> throw AssemblyError("source kind should have been variable")
                    is ArrayIndexedExpression -> throw AssemblyError("source kind should have been array")
                    is DirectMemoryRead -> throw AssemblyError("source kind should have been memory")
                    is TypecastExpression -> assignTypeCastedValue(assign.target, value.type, value.expression, assign)
                    is FunctionCall -> {
                        if(value.target.targetSubroutine(program.namespace)?.isAsmSubroutine==true) {
                            // handle asmsub functioncalls specifically, without shoving stuff on the estack
                            val sub = value.target.targetSubroutine(program.namespace)!!
                            val preserveStatusRegisterAfterCall = sub.asmReturnvaluesRegisters.any { it.statusflag != null }
                            asmgen.translateFunctionCall(value, preserveStatusRegisterAfterCall)
                            when((sub.asmReturnvaluesRegisters.single { it.registerOrPair!=null }).registerOrPair) {
                                RegisterOrPair.A -> assignRegisterByte(assign.target, CpuRegister.A)
                                RegisterOrPair.X -> assignRegisterByte(assign.target, CpuRegister.X)
                                RegisterOrPair.Y -> assignRegisterByte(assign.target, CpuRegister.Y)
                                RegisterOrPair.AX -> assignRegisterpairWord(assign.target, RegisterOrPair.AX)
                                RegisterOrPair.AY -> assignRegisterpairWord(assign.target, RegisterOrPair.AY)
                                RegisterOrPair.XY -> assignRegisterpairWord(assign.target, RegisterOrPair.XY)
                                else -> throw AssemblyError("should be just one register byte result value")
                            }
                            if(preserveStatusRegisterAfterCall)
                                asmgen.out("  plp\t; restore status flags from call")
                        } else {
                            // regular subroutine, return values are (for now) always done via the stack...  TODO optimize this
                            asmgen.translateExpression(value)
                            if(assign.target.datatype in WordDatatypes && assign.source.datatype in ByteDatatypes)
                                asmgen.signExtendStackLsb(assign.source.datatype)
                            assignStackValue(assign.target)
                        }
                    }
                    else -> {
                        // everything else just evaluate via the stack.
                        asmgen.translateExpression(value)
                        if(assign.target.datatype in WordDatatypes && assign.source.datatype in ByteDatatypes)
                            asmgen.signExtendStackLsb(assign.source.datatype)
                        assignStackValue(assign.target)
                    }
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

    private fun assignTypeCastedValue(target: AsmAssignTarget, targetDt: DataType, value: Expression, origAssign: AsmAssignment) {
        val valueDt = value.inferType(program).typeOrElse(DataType.STRUCT)
        when(value) {
            is IdentifierReference -> {
                if(targetDt in WordDatatypes) {
                    if(valueDt==DataType.UBYTE) {
                        assignVariableUByteIntoWord(target, value)
                        return
                    }
                    if(valueDt==DataType.BYTE) {
                        assignVariableByteIntoWord(target, value)
                        return
                    }
                }
            }
            is DirectMemoryRead -> {
                if(targetDt in WordDatatypes) {
                    if (value.addressExpression is NumericLiteralValue) {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByteIntoWord(target, address, null)
                        return
                    }
                    else if (value.addressExpression is IdentifierReference) {
                        assignMemoryByteIntoWord(target, null, value.addressExpression as IdentifierReference)
                        return
                    }
                }
            }
            is NumericLiteralValue -> throw AssemblyError("a cast of a literal value should have been const-folded away")
            else -> {}
        }

        when(value) {
            is PrefixExpression -> {}
            is BinaryExpression -> {}
            is ArrayIndexedExpression -> {}
            is TypecastExpression -> {}
            is RangeExpr -> {}
            is FunctionCall -> {}
            else -> {
                // TODO optimize the others further?
                println("warning: slow stack evaluation used for typecast: into $targetDt at ${value.position}")
            }
        }

        // give up, do it via eval stack
        asmgen.translateExpression(origAssign.source.expression!!)
        assignStackValue(target)
    }

    private fun assignStackValue(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            sta  ${target.asmVarname}
                            lda  P8ESTACK_HI,x
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  floats.pop_float
                        """)
                    }
                    DataType.STR -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            inx
                            lda  P8ESTACK_HI,x
                            tay
                            lda  P8ESTACK_LO,x
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("weird target variable type ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("P8ESTACK_LO,x", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                if(target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                    when(target.datatype) {
                        in ByteDatatypes -> {
                            asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}+$scaledIdx")
                        }
                        in WordDatatypes -> {
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname}+$scaledIdx
                                lda  P8ESTACK_HI,x
                                sta  ${target.asmVarname}+$scaledIdx+1
                            """)
                        }
                        DataType.FLOAT -> {
                            asmgen.out("""
                                lda  #<${target.asmVarname}+$scaledIdx
                                ldy  #>${target.asmVarname}+$scaledIdx
                                jsr  floats.pop_float
                            """)
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
                    target.array!!
                    when(target.datatype) {
                        DataType.UBYTE, DataType.BYTE -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out(" inx |  lda  P8ESTACK_LO,x |  sta  ${target.asmVarname},y")
                        }
                        DataType.UWORD, DataType.WORD -> {
                            asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                            asmgen.out("""
                                inx
                                lda  P8ESTACK_LO,x
                                sta  ${target.asmVarname},y
                                lda  P8ESTACK_HI,x
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
+                               jsr  floats.pop_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out(" inx |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.X -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.Y -> asmgen.out(" inx |  ldy  P8ESTACK_LO,x")
                            RegisterOrPair.AX -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldx  #0")
                            RegisterOrPair.AY -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  #0")
                            else -> throw AssemblyError("can't assign byte from stack to register pair XY")
                        }
                    }
                    DataType.UWORD, DataType.WORD, in PassByReferenceDatatypes -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.AY-> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  P8ESTACK_HI,x")
                            RegisterOrPair.XY-> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            else -> throw AssemblyError("can't assign word to single byte register")
                        }
                    }
                    else -> throw AssemblyError("weird dt")
                }
            }
            TargetStorageKind.STACK -> {}
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, sourceName: String) {
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
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  #<$sourceName |  ldx  #>$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<$sourceName |  ldy  #>$sourceName")
                    else -> throw AssemblyError("can't load address in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #>$sourceName
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignVariableString(target: AsmAssignTarget, sourceName: String) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(target.datatype) {
                    DataType.UWORD -> {
                        asmgen.out("""
                            lda #<$sourceName
                            sta ${target.asmVarname}
                            lda #>$sourceName
                            sta ${target.asmVarname}+1
                        """)
                    }
                    DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  #<$sourceName
                            ldy  #>$sourceName
                            jsr  prog8_lib.strcpy""")
                    }
                    else -> throw AssemblyError("assign string to incompatible variable type")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #>$sourceName+1
                    sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("string-assign to weird target")
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, sourceName: String) {
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
                target.array!!
                if(target.constArrayIndexValue!=null) {
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
                                sta  P8ZP_SCRATCH_W1
                                sty  P8ZP_SCRATCH_W1+1
                                lda  #<${target.asmVarname}+$scaledIdx
                                ldy  #>${target.asmVarname}+$scaledIdx
                                jsr  floats.copy_float
                            """)
                        }
                        else -> throw AssemblyError("weird target variable type ${target.datatype}")
                    }
                }
                else
                {
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
                                sty  P8ZP_SCRATCH_W1
                                ldy  #>$sourceName
                                sty  P8ZP_SCRATCH_W1+1
                                ldy  #>${target.asmVarname}
                                clc
                                adc  #<${target.asmVarname}
                                bcc  +
                                iny
+                                   jsr  floats.copy_float""")
                        }
                        else -> throw AssemblyError("weird dt")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  $sourceName+1")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  $sourceName+1")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  $sourceName+1")
                    else -> throw AssemblyError("can't load word in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #$sourceName+1
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignVariableFloat(target: AsmAssignTarget, sourceName: String) {
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
                asmgen.out("""
                    lda  #<$sourceName
                    ldy  #>$sourceName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<${target.asmVarname} 
                    ldy  #>${target.asmVarname}
                    sta  P8ZP_SCRATCH_W2
                    sty  P8ZP_SCRATCH_W2+1""")
                if(target.array!!.indexer.indexNum!=null) {
                    val index = target.array.indexer.constIndex()!!
                    asmgen.out(" lda  #$index")
                } else {
                    val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
                    asmgen.out(" lda  $asmvarname")
                }
                asmgen.out(" jsr  floats.set_array_float")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
            TargetStorageKind.STACK -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  floats.push_float")
        }
    }

    private fun assignVariableByte(target: AsmAssignTarget, sourceName: String) {
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
                if (target.constArrayIndexValue!=null) {
                    val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                    asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, target.datatype, CpuRegister.Y)
                    asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #$sourceName
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignVariableByteIntoWord(wordtarget: AsmAssignTarget, bytevar: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when (wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  ${wordtarget.asmVarname}+1
                    """)
            }
            TargetStorageKind.ARRAY -> {
                // TODO optimize slow stack evaluation for this case, see assignVariableUByteIntoWord
                println("warning: slow stack evaluation used for sign-extend byte typecast at ${bytevar.position}")
                asmgen.translateExpression(wordtarget.origAssign.source.expression!!)
                assignStackValue(wordtarget)
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        ldx  #0
+                       tax
                        pla""")
                    RegisterOrPair.AY -> asmgen.out("""
                        lda  $sourceName
                        pha
                        ora  #$7f
                        bmi  +
                        ldy  #0
+                       tay
                        pla""")
                    RegisterOrPair.XY -> asmgen.out("""
                        lda  $sourceName
                        tax 
                        ora  #$7f
                        bmi  +
                        ldy  #0
+                       tay""")
                    else -> throw AssemblyError("only reg pairs are words")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    ora  #$7f
                    bmi  +                    
                    lda  #0
+                   sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("target type isn't word")
        }
    }

    private fun assignVariableUByteIntoWord(wordtarget: AsmAssignTarget, bytevar: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(bytevar)
        when(wordtarget.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${wordtarget.asmVarname}
                    lda  #0
                    sta  ${wordtarget.asmVarname}+1
                    """)
            }
            TargetStorageKind.ARRAY -> {
                if (wordtarget.constArrayIndexValue!=null) {
                    val scaledIdx = wordtarget.constArrayIndexValue!! * 2
                    asmgen.out(" lda  $sourceName  | sta  ${wordtarget.asmVarname}+$scaledIdx |  lda  #0  | sta  ${wordtarget.asmVarname}+$scaledIdx+1")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(wordtarget.array!!, wordtarget.datatype, CpuRegister.Y)
                    asmgen.out(" lda  $sourceName |  sta  ${wordtarget.asmVarname},y |  lda  #0 |  iny |  sta  ${wordtarget.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> {
                when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                    else -> throw AssemblyError("only reg pairs are words")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  P8ESTACK_LO,x
                    lda  #0
                    sta  P8ESTACK_HI,x
                    dex""")
            }
            else -> throw AssemblyError("target type isn't word")
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
                when {
                    target.constArrayIndexValue!=null -> {
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+${target.constArrayIndexValue}")
                            CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+${target.constArrayIndexValue}")
                            CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+${target.constArrayIndexValue}")
                        }
                    }
                    else -> {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        asmgen.out(" ldy  ${asmgen.asmVariableName(target.array!!.indexer.indexVar!!)} |  sta  ${target.asmVarname},y")
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tax |  ldy  #0") }
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  txy") }
                        RegisterOrPair.AY -> { asmgen.out("  txa |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  ldy  #0") }
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  tyx") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> { asmgen.out("  tya |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  tya |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tya |  tax |  ldy  #0") }
                    }
                }
            }
            TargetStorageKind.STACK -> {
                when(register) {
                    CpuRegister.A -> asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                    CpuRegister.X -> throw AssemblyError("can't use X here")
                    CpuRegister.Y -> asmgen.out(" tya |  sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun assignRegisterpairWord(target: AsmAssignTarget, regs: RegisterOrPair) {
        require(target.datatype in WordDatatypes)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when(regs) {
                    RegisterOrPair.AX -> asmgen.out("  sta  ${target.asmVarname} |  stx  ${target.asmVarname}+1")
                    RegisterOrPair.AY -> asmgen.out("  sta  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    RegisterOrPair.XY -> asmgen.out("  stx  ${target.asmVarname} |  sty  ${target.asmVarname}+1")
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.ARRAY -> {
                TODO("store register pair $regs into word-array ${target.array}")
            }
            TargetStorageKind.REGISTER -> {
                when(regs) {
                    RegisterOrPair.AX -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.AX -> { }
                        RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    RegisterOrPair.AY -> when(target.register!!) {
                        RegisterOrPair.AY -> { }
                        RegisterOrPair.AX -> { asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { asmgen.out("  tax") }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    RegisterOrPair.XY -> when(target.register!!) {
                        RegisterOrPair.AY -> { asmgen.out("  txa") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG") }
                        RegisterOrPair.XY -> { }
                        else -> throw AssemblyError("expected reg pair")
                    }
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.STACK -> {
                when(regs) {
                    RegisterOrPair.AY -> asmgen.out(" sta  P8ESTACK_LO,x |  sty  P8ESTACK_HI,x |  dex")
                    RegisterOrPair.AX, RegisterOrPair.XY -> throw AssemblyError("can't use X here")
                    else -> throw AssemblyError("expected reg pair")
                }
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't store word into memory byte")
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
                asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UWORD, CpuRegister.Y)
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  ${target.asmVarname},y
                    lda  #>${word.toHex()}
                    sta  ${target.asmVarname}+1,y
                """)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  #<${word.toHex()} |  ldx  #>${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    else -> throw AssemblyError("can't assign word to single byte register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  P8ESTACK_LO,x
                    lda  #>${word.toHex()}
                    sta  P8ESTACK_HI,x
                    dex""")
            }
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
                if (target.constArrayIndexValue!=null) {
                    val indexValue = target.constArrayIndexValue!!
                    asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                }
                else {
                    asmgen.loadScaledArrayIndexIntoRegister(target.array!!, DataType.UBYTE, CpuRegister.Y)
                    asmgen.out("  lda  #<${byte.toHex()} |  sta  ${target.asmVarname},y")
                }
            }
            TargetStorageKind.REGISTER -> when(target.register!!) {
                RegisterOrPair.A -> asmgen.out("  lda  #${byte.toHex()}")
                RegisterOrPair.X -> asmgen.out("  ldx  #${byte.toHex()}")
                RegisterOrPair.Y -> asmgen.out("  ldy  #${byte.toHex()}")
                RegisterOrPair.AX -> asmgen.out("  lda  #${byte.toHex()} |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  #${byte.toHex()} |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  #${byte.toHex()} |  ldy  #0")
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #${byte.toHex()}
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(CompilationTarget.instance.machine.cpu == CpuType.CPU65c02)
                        asmgen.out("""
                            stz  ${target.asmVarname}
                            stz  ${target.asmVarname}+1
                            stz  ${target.asmVarname}+2
                            stz  ${target.asmVarname}+3
                            stz  ${target.asmVarname}+4
                        """)
                    else
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
                    if (target.array!!.indexer.indexNum!=null) {
                        val indexValue = target.array.indexer.constIndex()!! * DataType.FLOAT.memorySize()
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}+$indexValue
                            sta  ${target.asmVarname}+$indexValue+1
                            sta  ${target.asmVarname}+$indexValue+2
                            sta  ${target.asmVarname}+$indexValue+3
                            sta  ${target.asmVarname}+$indexValue+4
                        """)
                    } else {
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  $asmvarname  
                            jsr  floats.set_0_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
                }
            }
        } else {
            // non-zero value
            val constFloat = asmgen.getFloatAsmConst(float)
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
                    val arrayVarName = target.asmVarname
                    if (target.array!!.indexer.indexNum!=null) {
                        val indexValue = target.array.indexer.constIndex()!! * DataType.FLOAT.memorySize()
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
                        val asmvarname = asmgen.asmVariableName(target.array.indexer.indexVar!!)
                        asmgen.out("""
                            lda  #<${constFloat}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${constFloat}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  #<${arrayVarName}
                            sta  P8ZP_SCRATCH_W2
                            lda  #>${arrayVarName}
                            sta  P8ZP_SCRATCH_W2+1
                            lda  $asmvarname
                            jsr  floats.set_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  floats.push_float")
                }
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
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  ${address.toHex()}")
                    RegisterOrPair.X -> asmgen.out("  ldx  ${address.toHex()}")
                    RegisterOrPair.Y -> asmgen.out("  ldy  ${address.toHex()}")
                    RegisterOrPair.AX -> asmgen.out("  lda  ${address.toHex()} |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldy  ${address.toHex()} |  ldy  #0")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        dex""")
                }
            }
        } else if (identifier != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${target.asmVarname}")
                }
                TargetStorageKind.MEMORY -> {
                    val sourceName = asmgen.asmVariableName(identifier)
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $identifier to array ${target.asmVarname} ")
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> asmgen.out("  tax")
                        RegisterOrPair.Y -> asmgen.out("  tay")
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun assignMemoryByteIntoWord(wordtarget: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${wordtarget.asmVarname}
                        lda  #0
                        sta  ${wordtarget.asmVarname}+1
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${wordtarget.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(wordtarget.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  ${address.toHex()} |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldy  ${address.toHex()} |  ldy  #0")
                    else -> throw AssemblyError("word regs can only be pair")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        lda  #0
                        sta  P8ESTACK_HI,x
                        dex""")
                }
                else -> throw AssemblyError("other types aren't word")
            }
        } else if (identifier != null) {
            when(wordtarget.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${wordtarget.asmVarname} |  lda  #0 |  sta ${wordtarget.asmVarname}+1")
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $identifier to array ${wordtarget.asmVarname} ")
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(wordtarget.register!!) {
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                        else -> throw AssemblyError("word regs can only be pair")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  lda  #0 |  sta  P8ESTACK_HI,x |  dex")
                }
                else -> throw AssemblyError("other types aren't word")
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
                asmgen.storeByteIntoPointer(addressExpr, ldaInstructionArg)
            }
            else -> {
                asmgen.out(" lda  $ldaInstructionArg |  pha")
                asmgen.translateExpression(addressExpr)
                asmgen.out("""
                    inx
                    lda  P8ESTACK_LO,x
                    sta  P8ZP_SCRATCH_W2
                    lda  P8ESTACK_HI,x
                    sta  P8ZP_SCRATCH_W2+1
                    ldy  #0
                    pla
                    sta  (P8ZP_SCRATCH_W2),y""")
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
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                asmgen.storeByteIntoPointer(addressExpr, null)
            }
            else -> {
                asmgen.saveRegister(register, false, memoryAddress.definingSubroutine())
                asmgen.translateExpression(addressExpr)
                asmgen.restoreRegister(CpuRegister.A, false)
                asmgen.out("""
                    inx
                    ldy  P8ESTACK_LO,x
                    sty  P8ZP_SCRATCH_W2
                    ldy  P8ESTACK_HI,x
                    sty  P8ZP_SCRATCH_W2+1
                    ldy  #0
                    sta  (P8ZP_SCRATCH_W2),y""")
            }
        }
    }
}
