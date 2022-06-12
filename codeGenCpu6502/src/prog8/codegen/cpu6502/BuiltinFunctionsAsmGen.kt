package prog8.codegen.cpu6502

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.ArrayIndex
import prog8.ast.statements.BuiltinFunctionCallStatement
import prog8.ast.statements.Subroutine
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.*
import prog8.compiler.BuiltinFunctions
import prog8.compiler.FSignature


internal class BuiltinFunctionsAsmGen(private val program: Program,
                                      private val asmgen: AsmGen,
                                      private val assignAsmGen: AssignmentAsmGen,
                                      private val allocations: VariableAllocator) {

    internal fun translateFunctioncallExpression(fcall: BuiltinFunctionCall, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        val func = BuiltinFunctions.getValue(fcall.target.nameInSource.single())
        translateFunctioncall(fcall, func, discardResult = false, resultToStack = resultToStack, resultRegister = resultRegister)
    }

    internal fun translateFunctioncallStatement(fcall: BuiltinFunctionCallStatement) {
        val func = BuiltinFunctions.getValue(fcall.name)
        translateFunctioncall(fcall, func, discardResult = true, resultToStack = false, resultRegister = null)
    }

    private fun translateFunctioncall(fcall: IFunctionCall, func: FSignature, discardResult: Boolean, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        if (discardResult && func.pure)
            return  // can just ignore the whole function call altogether

        if(discardResult && resultToStack)
            throw AssemblyError("cannot both discard the result AND put it onto stack")

        val sscope = (fcall as Node).definingSubroutine

        when (func.name) {
            "msb" -> funcMsb(fcall, resultToStack, resultRegister)
            "lsb" -> funcLsb(fcall, resultToStack, resultRegister)
            "mkword" -> funcMkword(fcall, resultToStack, resultRegister)
            "abs" -> funcAbs(fcall, func, resultToStack, resultRegister, sscope)
            "any", "all" -> funcAnyAll(fcall, func, resultToStack, resultRegister, sscope)
            "sgn" -> funcSgn(fcall, func, resultToStack, resultRegister, sscope)
            "rnd", "rndw" -> funcRnd(func, resultToStack, resultRegister, sscope)
            "sqrt16" -> funcSqrt16(fcall, func, resultToStack, resultRegister, sscope)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "sort" -> funcSort(fcall)
            "reverse" -> funcReverse(fcall)
            "memory" -> funcMemory(fcall, discardResult, resultToStack, resultRegister)
            "peekw" -> funcPeekW(fcall, resultToStack, resultRegister)
            "peek" -> throw AssemblyError("peek() should have been replaced by @()")
            "pokew" -> funcPokeW(fcall)
            "pokemon" -> { /* meme function */ }
            "poke" -> throw AssemblyError("poke() should have been replaced by @()")
            "push" -> asmgen.pushCpuStack(DataType.UBYTE, fcall.args[0])
            "pushw" -> asmgen.pushCpuStack(DataType.UWORD, fcall.args[0])
            "pop" -> {
                require(fcall.args[0] is IdentifierReference) {
                    "attempt to pop a value into a differently typed variable, or in something else that isn't supported ${(fcall as Node).position}"
                }
                asmgen.popCpuStack(DataType.UBYTE, (fcall.args[0] as IdentifierReference).targetVarDecl(program)!!, (fcall as Node).definingSubroutine)
            }
            "popw" -> {
                require(fcall.args[0] is IdentifierReference) {
                    "attempt to pop a value into a differently typed variable, or in something else that isn't supported ${(fcall as Node).position}"
                }
                asmgen.popCpuStack(DataType.UWORD, (fcall.args[0] as IdentifierReference).targetVarDecl(program)!!, (fcall as Node).definingSubroutine)
            }
            "rsave" -> funcRsave()
            "rsavex" -> funcRsaveX()
            "rrestore" -> funcRrestore()
            "rrestorex" -> funcRrestoreX()
            "cmp" -> funcCmp(fcall)
            "callfar" -> funcCallFar(fcall)
            "callrom" -> funcCallRom(fcall)
            else -> throw AssemblyError("missing asmgen for builtin func ${func.name}")
        }
    }

    private fun funcRsave() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("""
                php
                pha
                phy
                phx""")
        else
            // see http://6502.org/tutorials/register_preservation.html
            asmgen.out("""
                php
                sta  P8ZP_SCRATCH_REG
                pha
                txa
                pha
                tya
                pha
                lda  P8ZP_SCRATCH_REG""")
    }

    private fun funcRsaveX() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("  phx")
        else
            asmgen.out("  txa |  pha")
    }

    private fun funcRrestore() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("""
                plx
                ply
                pla
                plp""")
        else
            asmgen.out("""
                pla
                tay
                pla
                tax
                pla
                plp""")
    }

    private fun funcRrestoreX() {
        if (asmgen.isTargetCpu(CpuType.CPU65c02))
            asmgen.out("  plx")
        else
            asmgen.out("  sta  P8ZP_SCRATCH_B1 |  pla |  tax |  lda  P8ZP_SCRATCH_B1")
    }

    private fun funcCallFar(fcall: IFunctionCall) {
        if(asmgen.options.compTarget.name != "cx16")
            throw AssemblyError("callfar only works on cx16 target at this time")

        val bank = fcall.args[0].constValue(program)?.number?.toInt()
        val address = fcall.args[1].constValue(program)?.number?.toInt()
        if(bank==null || address==null)
            throw AssemblyError("callfar (jsrfar) requires constant arguments")

        if(address !in 0xa000..0xbfff)
            throw AssemblyError("callfar done on address outside of cx16 banked ram")
        if(bank==0)
            throw AssemblyError("callfar done on bank 0 which is reserved for the kernal")

        val argAddrArg = fcall.args[2]
        if(argAddrArg.constValue(program)?.number == 0.0) {
            asmgen.out("""
                jsr  cx16.jsrfar
                .word  ${address.toHex()}
                .byte  ${bank.toHex()}""")
        } else {
            when(argAddrArg) {
                is AddressOf -> {
                    if(argAddrArg.identifier.targetVarDecl(program)?.datatype != DataType.UBYTE)
                        throw AssemblyError("callfar done with 'arg' pointer to variable that's not UBYTE")
                    asmgen.out("""
                        lda  ${asmgen.asmVariableName(argAddrArg.identifier)}
                        jsr  cx16.jsrfar
                        .word  ${address.toHex()}
                        .byte  ${bank.toHex()}
                        sta  ${asmgen.asmVariableName(argAddrArg.identifier)}""")
                }
                is NumericLiteral -> {
                    asmgen.out("""
                        lda  ${argAddrArg.number.toHex()}
                        jsr  cx16.jsrfar
                        .word  ${address.toHex()}
                        .byte  ${bank.toHex()}
                        sta  ${argAddrArg.number.toHex()}""")
                }
                else -> throw AssemblyError("callfar only accepts pointer-of a (ubyte) variable or constant memory address for the 'arg' parameter")
            }
        }
    }

    private fun funcCallRom(fcall: IFunctionCall) {
        if(asmgen.options.compTarget.name != "cx16")
            throw AssemblyError("callrom only works on cx16 target at this time")

        val bank = fcall.args[0].constValue(program)?.number?.toInt()
        val address = fcall.args[1].constValue(program)?.number?.toInt()
        if(bank==null || address==null)
            throw AssemblyError("callrom requires constant arguments")

        if(address !in 0xc000..0xffff)
            throw AssemblyError("callrom done on address outside of cx16 banked rom")
        if(bank>=32)
            throw AssemblyError("callrom bank must be <32")

        val argAddrArg = fcall.args[2]
        if(argAddrArg.constValue(program)?.number == 0.0) {
            asmgen.out("""
                lda  $01
                pha
                lda  #${bank}
                sta  $01
                jsr  ${address.toHex()}
                pla
                sta  $01""")
        } else {
            when(argAddrArg) {
                is AddressOf -> {
                    if(argAddrArg.identifier.targetVarDecl(program)?.datatype != DataType.UBYTE)
                        throw AssemblyError("callrom done with 'arg' pointer to variable that's not UBYTE")
                    asmgen.out("""
                        lda  $01
                        pha
                        lda  #${bank}
                        sta  $01
                        lda  ${asmgen.asmVariableName(argAddrArg.identifier)}                
                        jsr  ${address.toHex()}
                        sta  ${asmgen.asmVariableName(argAddrArg.identifier)}
                        pla
                        sta  $01""")
                }
                is NumericLiteral -> {
                    asmgen.out("""
                        lda  $01
                        pha
                        lda  #${bank}
                        sta  $01
                        lda  ${argAddrArg.number.toHex()}
                        jsr  ${address.toHex()}
                        sta  ${argAddrArg.number.toHex()}
                        pla
                        sta  $01""")
                }
                else -> throw AssemblyError("callrom only accepts pointer-of a (ubyte) variable or constant memory address for the 'arg' parameter")
            }
        }
    }

    private fun funcCmp(fcall: IFunctionCall) {
        val arg1 = fcall.args[0]
        val arg2 = fcall.args[1]
        val dt1 = arg1.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
        val dt2 = arg2.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
        if(dt1 in ByteDatatypes) {
            if(dt2 in ByteDatatypes) {
                when (arg2) {
                    is IdentifierReference -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  ${asmgen.asmVariableName(arg2)}")
                    }
                    is NumericLiteral -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  #${arg2.number.toInt()}")
                    }
                    is DirectMemoryRead -> {
                        if(arg2.addressExpression is NumericLiteral) {
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                            asmgen.out("  cmp  ${arg2.addressExpression.constValue(program)!!.number.toHex()}")
                        } else {
                            asmgen.assignExpressionToVariable(arg2, "P8ZP_SCRATCH_B1", DataType.UBYTE, (fcall as Node).definingSubroutine)
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                        }
                    }
                    else -> {
                        asmgen.assignExpressionToVariable(arg2, "P8ZP_SCRATCH_B1", DataType.UBYTE, (fcall as Node).definingSubroutine)
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                    }
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        } else {
            // dt1 is a word
            if(dt2 in WordDatatypes) {
                when (arg2) {
                    is IdentifierReference -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  ${asmgen.asmVariableName(arg2)}+1
                            bne  +
                            cmp  ${asmgen.asmVariableName(arg2)}
+""")
                    }
                    is NumericLiteral -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  #>${arg2.number.toInt()}
                            bne  +
                            cmp  #<${arg2.number.toInt()}
+""")
                    }
                    else -> {
                        asmgen.assignExpressionToVariable(arg2, "P8ZP_SCRATCH_W1", DataType.UWORD, (fcall as Node).definingSubroutine)
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  P8ZP_SCRATCH_W1+1
                            bne  +
                            cmp  P8ZP_SCRATCH_W1
+""")
                    }
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        }
    }

    private fun funcMemory(fcall: IFunctionCall, discardResult: Boolean, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        if(discardResult || fcall !is BuiltinFunctionCall)
            throw AssemblyError("should not discard result of memory allocation at $fcall")
        val name = (fcall.args[0] as StringLiteral).value
        require(name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name"}
        val size = (fcall.args[1] as NumericLiteral).number.toUInt()
        val align = (fcall.args[2] as NumericLiteral).number.toUInt()

        val existing = allocations.getMemorySlab(name)
        if(existing!=null && (existing.first!=size || existing.second!=align))
            throw AssemblyError("memory slab '$name' already exists with a different size or alignment at ${fcall.position}")

        val slabname = IdentifierReference(listOf("prog8_slabs", name), fcall.position)
        slabname.linkParents(fcall)
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = AddressOf(slabname, fcall.position))
        val target =
            if(resultToStack)
                AsmAssignTarget(TargetStorageKind.STACK, program, asmgen, DataType.UWORD, null)
            else
                AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, null, program, asmgen)
        val assign = AsmAssignment(src, target, false, program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign)
        allocations.allocateMemorySlab(name, size, align)
    }

    private fun funcSqrt16(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?, scope: Subroutine?) {
        translateArguments(fcall.args, func, scope)
        if(resultToStack)
            asmgen.out("  jsr  prog8_lib.func_sqrt16_stack")
        else {
            asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
            assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, scope, program, asmgen), CpuRegister.A)
        }
    }

    private fun funcReverse(fcall: IFunctionCall) {
        val variable = fcall.args.single()
        if (variable is IdentifierReference) {
            val decl = variable.targetVarDecl(program)!!
            val varName = asmgen.asmVariableName(variable)
            val numElements = decl.arraysize!!.constIndex()
            when (decl.datatype) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements
                        jsr  prog8_lib.func_reverse_b""")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements
                        jsr  prog8_lib.func_reverse_w""")
                }
                DataType.ARRAY_F -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements
                        jsr  floats.func_reverse_f""")
                }
                else -> throw AssemblyError("weird type")
            }
        }
    }

    private fun funcSort(fcall: IFunctionCall) {
        val variable = fcall.args.single()
        if (variable is IdentifierReference) {
            val decl = variable.targetVarDecl(program)!!
            val varName = asmgen.asmVariableName(variable)
            val numElements = decl.arraysize!!.constIndex()
            when (decl.datatype) {
                DataType.ARRAY_UB, DataType.ARRAY_B -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements""")
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UB) "  jsr  prog8_lib.func_sort_ub" else "  jsr  prog8_lib.func_sort_b")
                }
                DataType.ARRAY_UW, DataType.ARRAY_W -> {
                    asmgen.out("""
                        lda  #<$varName
                        ldy  #>$varName
                        sta  P8ZP_SCRATCH_W1
                        sty  P8ZP_SCRATCH_W1+1
                        lda  #$numElements""")
                    asmgen.out(if (decl.datatype == DataType.ARRAY_UW) "  jsr  prog8_lib.func_sort_uw" else "  jsr  prog8_lib.func_sort_w")
                }
                DataType.ARRAY_F -> throw AssemblyError("sorting of floating point array is not supported")
                else -> throw AssemblyError("weird type")
            }
        } else
            throw AssemblyError("weird type")
    }

    private fun funcRor2(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror2", 'b')
                        asmgen.out("  jsr  prog8_lib.ror2_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteral) {
                            val number = (what.addressExpression as NumericLiteral).number
                            asmgen.out("  lda  ${number.toHex()} |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.ror2_mem_ub")
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+  |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror2", 'w')
                        asmgen.out("  jsr  prog8_lib.ror2_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #\$80 |  sta  $variable+1 |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRor(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror", 'b')
                        asmgen.out("  jsr  prog8_lib.ror_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteral) {
                            val number = (what.addressExpression as NumericLiteral).number
                            asmgen.out("  ror  ${number.toHex()}")
                        } else {
                            val ptrAndIndex = asmgen.pointerViaIndexRegisterPossible(what.addressExpression)
                            if(ptrAndIndex!=null) {
                                asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.X)
                                asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                                asmgen.assignExpressionToRegister(ptrAndIndex.first, RegisterOrPair.AY)
                                asmgen.restoreRegisterLocal(CpuRegister.X)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   ror  ${'$'}ffff,x           ; modified""")
                                asmgen.restoreRegisterLocal(CpuRegister.X)
                            } else {
                                asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   ror  ${'$'}ffff            ; modified""")
                            }
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "ror", 'w')
                        asmgen.out("  jsr  prog8_lib.ror_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable+1 |  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol2(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol2", 'b')
                        asmgen.out("  jsr  prog8_lib.rol2_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteral) {
                            val number = (what.addressExpression as NumericLiteral).number
                            asmgen.out("  lda  ${number.toHex()} |  cmp  #\$80 |  rol  a |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.rol2_mem_ub")
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  cmp  #\$80 |  rol  a |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol2", 'w')
                        asmgen.out("  jsr  prog8_lib.rol2_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  asl  $variable |  rol  $variable+1 |  bcc  + |  inc  $variable |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol(fcall: IFunctionCall) {
        val what = fcall.args.single()
        val dt = what.inferType(program)
        when (dt.getOr(DataType.UNDEFINED)) {
            DataType.UBYTE -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol", 'b')
                        asmgen.out("  jsr  prog8_lib.rol_array_ub")
                    }
                    is DirectMemoryRead -> {
                        if (what.addressExpression is NumericLiteral) {
                            val number = (what.addressExpression as NumericLiteral).number
                            asmgen.out("  rol  ${number.toHex()}")
                        } else {
                            val ptrAndIndex = asmgen.pointerViaIndexRegisterPossible(what.addressExpression)
                            if(ptrAndIndex!=null) {
                                asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.X)
                                asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                                asmgen.assignExpressionToRegister(ptrAndIndex.first, RegisterOrPair.AY)
                                asmgen.restoreRegisterLocal(CpuRegister.X)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   rol  ${'$'}ffff,x           ; modified""")
                                asmgen.restoreRegisterLocal(CpuRegister.X)
                            } else {
                                asmgen.assignExpressionToRegister(what.addressExpression, RegisterOrPair.AY)
                                asmgen.out("""
                                    sta  (+) + 1
                                    sty  (+) + 2
+                                   rol  ${'$'}ffff            ; modified""")
                            }
                        }
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            DataType.UWORD -> {
                when (what) {
                    is ArrayIndexedExpression -> {
                        translateRolRorArrayArgs(what.arrayvar, what.indexer, "rol", 'w')
                        asmgen.out("  jsr  prog8_lib.rol_array_uw")
                    }
                    is IdentifierReference -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable |  rol  $variable+1")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun translateRolRorArrayArgs(arrayvar: IdentifierReference, indexer: ArrayIndex, operation: String, dt: Char) {
        if(arrayvar.targetVarDecl(program)!!.datatype==DataType.UWORD) {
            if(dt!='b')
                throw AssemblyError("non-array var indexing requires bytes dt")
            asmgen.assignExpressionToVariable(arrayvar, "prog8_lib.${operation}_array_u${dt}._arg_target", DataType.UWORD, null)
        } else {
            asmgen.assignExpressionToVariable(AddressOf(arrayvar, arrayvar.position), "prog8_lib.${operation}_array_u${dt}._arg_target", DataType.UWORD, null)
        }
        asmgen.assignExpressionToVariable(indexer.indexExpr, "prog8_lib.${operation}_array_u${dt}._arg_index", DataType.UBYTE, null)
    }

    private fun funcSgn(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?, scope: Subroutine?) {
        translateArguments(fcall.args, func, scope)
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.getOr(DataType.UNDEFINED)) {
                DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_stack")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_stack")
                DataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_stack")
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_stack")
                DataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_stack")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.getOr(DataType.UNDEFINED)) {
                DataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_into_A")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_into_A")
                DataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_into_A")
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_into_A")
                DataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_into_A")
                else -> throw AssemblyError("weird type $dt")
            }
            assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, scope, program, asmgen), CpuRegister.A)
        }
    }

    private fun funcAnyAll(fcall: IFunctionCall, function: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?, scope: Subroutine?) {
        outputAddressAndLenghtOfArray(fcall.args[0])
        val dt = fcall.args.single().inferType(program)
        if(resultToStack) {
            when (dt.getOr(DataType.UNDEFINED)) {
                DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_stack")
                DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_stack")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_stack")
                else -> throw AssemblyError("weird type $dt")
            }
        } else {
            when (dt.getOr(DataType.UNDEFINED)) {
                DataType.ARRAY_B, DataType.ARRAY_UB, DataType.STR -> asmgen.out("  jsr  prog8_lib.func_${function.name}_b_into_A |  ldy  #0")
                DataType.ARRAY_UW, DataType.ARRAY_W -> asmgen.out("  jsr  prog8_lib.func_${function.name}_w_into_A |  ldy  #0")
                DataType.ARRAY_F -> asmgen.out("  jsr  floats.func_${function.name}_f_into_A |  ldy  #0")
                else -> throw AssemblyError("weird type $dt")
            }
            assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, scope, program, asmgen), CpuRegister.A)
        }
    }

    private fun funcAbs(fcall: IFunctionCall, func: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?, scope: Subroutine?) {
        translateArguments(fcall.args, func, scope)
        val dt = fcall.args.single().inferType(program).getOr(DataType.UNDEFINED)
        if(resultToStack) {
            when (dt) {
                DataType.UBYTE -> asmgen.out("  ldy  #0")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.abs_b_stack")
                DataType.UWORD -> {}
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.abs_w_stack")
                else -> throw AssemblyError("weird type")
            }
        } else {
            when (dt) {
                DataType.UBYTE -> asmgen.out("  ldy  #0")
                DataType.BYTE -> asmgen.out("  jsr  prog8_lib.abs_b_into_AY")
                DataType.UWORD -> {}
                DataType.WORD -> asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                else -> throw AssemblyError("weird type")
            }
            assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, scope, program, asmgen), RegisterOrPair.AY)
        }
    }

    private fun funcRnd(func: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?, scope: Subroutine?) {
        when(func.name) {
            "rnd" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rnd_stack")
                else {
                    asmgen.out("  jsr  math.randbyte")
                    assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, scope, program, asmgen), CpuRegister.A)
                }
            }
            "rndw" -> {
                if(resultToStack)
                    asmgen.out("  jsr  prog8_lib.func_rndw_stack")
                else {
                    asmgen.out("  jsr  math.randword")
                    assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, scope, program, asmgen), RegisterOrPair.AY)
                }
            }
            else -> throw AssemblyError("wrong func")
        }
    }

    private fun funcPokeW(fcall: IFunctionCall) {
        when(val addrExpr = fcall.args[0]) {
            is NumericLiteral -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr |  sty  ${addr}+1")
                return
            }
            is IdentifierReference -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    if (asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            sta  ($varname)
                            txa
                            ldy  #1
                            sta  ($varname),y""")
                    } else {
                        asmgen.out("""
                            ldy  #0
                            sta  ($varname),y
                            txa
                            iny
                            sta  ($varname),y""")
                    }
                    asmgen.restoreRegisterLocal(CpuRegister.X)
                    return
                }
            }
            is BinaryExpression -> {
                if(addrExpr.operator=="+" && addrExpr.left is IdentifierReference && addrExpr.right is NumericLiteral) {
                    val varname = asmgen.asmVariableName(addrExpr.left as IdentifierReference)
                    if(asmgen.isZpVar(addrExpr.left as IdentifierReference)) {
                        // pointervar is already in the zero page, no need to copy
                        asmgen.saveRegisterLocal(CpuRegister.X, (fcall as Node).definingSubroutine!!)
                        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                        val index = (addrExpr.right as NumericLiteral).number.toHex()
                        asmgen.out("""
                            ldy  #$index
                            sta  ($varname),y
                            txa
                            iny
                            sta  ($varname),y""")
                        asmgen.restoreRegisterLocal(CpuRegister.X)
                        return
                    }
                }
            }
            else -> throw AssemblyError("wrong pokew arg type")
        }

        asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", DataType.UWORD, null)
        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
        asmgen.out("  jsr  prog8_lib.func_pokew")
    }

    private fun funcPeekW(fcall: IFunctionCall, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        when(val addrExpr = fcall.args[0]) {
            is NumericLiteral -> {
                val addr = addrExpr.number.toHex()
                asmgen.out("  lda  $addr |  ldy  ${addr}+1")
            }
            is IdentifierReference -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    if (asmgen.isTargetCpu(CpuType.CPU65c02)) {
                        asmgen.out("""
                            ldy  #1
                            lda  ($varname),y
                            tay
                            lda  ($varname)""")
                    } else {
                        asmgen.out("""
                            ldy  #0
                            lda  ($varname),y
                            pha
                            iny
                            lda  ($varname),y
                            tay
                            pla""")
                    }
                } else {
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                    asmgen.out("  jsr  prog8_lib.func_peekw")
                }
            }
            is BinaryExpression -> {
                if(addrExpr.operator=="+" && addrExpr.left is IdentifierReference && addrExpr.right is NumericLiteral) {
                    val varname = asmgen.asmVariableName(addrExpr.left as IdentifierReference)
                    if(asmgen.isZpVar(addrExpr.left as IdentifierReference)) {
                        // pointervar is already in the zero page, no need to copy
                        val index = (addrExpr.right as NumericLiteral).number.toHex()
                        asmgen.out("""
                            ldy  #$index
                            lda  ($varname),y
                            pha
                            iny
                            lda  ($varname),y
                            tay
                            pla""")
                    }  else {
                        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                        asmgen.out("  jsr  prog8_lib.func_peekw")
                    }
                } else {
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                    asmgen.out("  jsr  prog8_lib.func_peekw")
                }
            }
            else -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("  jsr  prog8_lib.func_peekw")
            }
        }

        if(resultToStack){
            asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
        } else {
            when(resultRegister ?: RegisterOrPair.AY) {
                RegisterOrPair.AY -> {}
                RegisterOrPair.AX -> asmgen.out("  sty  P8ZP_SCRATCH_REG |  ldx  P8ZP_SCRATCH_REG")
                RegisterOrPair.XY -> asmgen.out("  tax")
                in Cx16VirtualRegisters -> asmgen.out(
                    "  sta  cx16.${
                        resultRegister.toString().lowercase()
                    } |  sty  cx16.${resultRegister.toString().lowercase()}+1")
                else -> throw AssemblyError("invalid reg")
            }
        }
    }

    private fun funcMkword(fcall: IFunctionCall, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        if(resultToStack) {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
            asmgen.out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
        } else {
            val reg = resultRegister ?: RegisterOrPair.AY
            var needAsave = needAsaveForOtherArg(fcall.args[0])
            if(!needAsave) {
                val mr0 = fcall.args[0] as? DirectMemoryRead
                val mr1 = fcall.args[1] as? DirectMemoryRead
                if (mr0 != null)
                    needAsave =  mr0.addressExpression !is NumericLiteral && mr0.addressExpression !is IdentifierReference
                if (mr1 != null)
                    needAsave = needAsave or (mr1.addressExpression !is NumericLiteral && mr1.addressExpression !is IdentifierReference)
            }
            when(reg) {
                RegisterOrPair.AX -> {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    if(needAsave)
                        asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.X)      // msb
                    if(needAsave)
                        asmgen.out("  pla")
                }
                RegisterOrPair.AY -> {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    if(needAsave)
                        asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                    if(needAsave)
                        asmgen.out("  pla")
                }
                RegisterOrPair.XY -> {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    if(needAsave)
                        asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                    if(needAsave)
                        asmgen.out("  pla")
                    asmgen.out("  tax")
                }
                in Cx16VirtualRegisters -> {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    asmgen.out("  sta  cx16.${reg.toString().lowercase()}")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)      // msb
                    asmgen.out("  sta  cx16.${reg.toString().lowercase()}+1")
                }
                else -> throw AssemblyError("invalid mkword target reg")
            }
        }
    }

    private fun funcMsb(fcall: IFunctionCall, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.inferType(program).isWords)
            throw AssemblyError("msb required word argument")
        if (arg is NumericLiteral)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmVariableName(arg)
            if(resultToStack) {
                asmgen.out("  lda  $sourceName+1 |  sta  P8ESTACK_LO,x |  dex")
            } else {
                when(resultRegister) {
                    null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName+1")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName+1")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName+1")
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName+1 |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName+1 |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName+1 |  ldy  #0")
                    in Cx16VirtualRegisters -> {
                        val regname = resultRegister.name.lowercase()
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  stz  cx16.$regname+1")
                        else
                            asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                    }
                    else -> throw AssemblyError("invalid reg")
                }
            }
        } else {
            if(resultToStack) {
                asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                asmgen.out("  tya |  sta  P8ESTACK_LO,x |  dex")
            } else {
                when(resultRegister) {
                    null, RegisterOrPair.A -> {
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                        asmgen.out("  tya")
                    }
                    RegisterOrPair.X -> {
                        asmgen.out("  pha")
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AX)
                        asmgen.out("  pla")
                    }
                    RegisterOrPair.Y -> {
                        asmgen.out("  pha")
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                        asmgen.out("  pla")
                    }
                    else -> throw AssemblyError("invalid reg")
                }
            }
        }
    }

    private fun funcLsb(fcall: IFunctionCall, resultToStack: Boolean, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.inferType(program).isWords)
            throw AssemblyError("lsb required word argument")
        if (arg is NumericLiteral)
            throw AssemblyError("lsb(const) should have been const-folded away")

        if (arg is IdentifierReference) {
            val sourceName = asmgen.asmVariableName(arg)
            if(resultToStack) {
                asmgen.out("  lda  $sourceName |  sta  P8ESTACK_LO,x |  dex")
            } else {
                when(resultRegister) {
                    null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                    in Cx16VirtualRegisters -> {
                        val regname = resultRegister.name.lowercase()
                        if(asmgen.isTargetCpu(CpuType.CPU65c02))
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  stz  cx16.$regname+1")
                        else
                            asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                    }
                    else -> throw AssemblyError("invalid reg")
                }
            }
        } else {
            if(resultToStack) {
                asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                // NOTE: we rely on the fact that the above assignment to AY, assigns the Lsb to A as the last instruction.
                //       this is required because the compiler assumes the status bits are set according to what A is (lsb)
                //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                asmgen.out("  sta  P8ESTACK_LO,x |  dex")
            } else {
                when(resultRegister) {
                    null, RegisterOrPair.A -> {
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                        // NOTE: we rely on the fact that the above assignment to AY, assigns the Lsb to A as the last instruction.
                        //       this is required because the compiler assumes the status bits are set according to what A is (lsb)
                        //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                    }
                    RegisterOrPair.X -> {
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.XY)
                        // NOTE: we rely on the fact that the above assignment to XY, assigns the Lsb to X as the last instruction.
                        //       this is required because the compiler assumes the status bits are set according to what X is (lsb)
                        //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                    }
                    RegisterOrPair.Y -> {
                        asmgen.out("  pha")
                        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.AY)
                        asmgen.out("  tay |  pla |  cpy  #0")
                    }
                    else -> throw AssemblyError("invalid reg")
                }
            }
        }
    }

    private fun outputAddressAndLenghtOfArray(arg: Expression) {
        // address in P8ZP_SCRATCH_W1,  number of elements in A
        arg as IdentifierReference
        val arrayVar = arg.targetVarDecl(program)!!
        if(!arrayVar.isArray)
            throw AssemblyError("length of non-array requested")
        val size = arrayVar.arraysize!!.constIndex()!!
        val identifierName = asmgen.asmVariableName(arg)
        asmgen.out("""
                    lda  #<$identifierName
                    ldy  #>$identifierName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #$size
                    """)
    }

    private fun translateArguments(args: MutableList<Expression>, signature: FSignature, scope: Subroutine?) {
        val callConv = signature.callConvention(args.map {
            it.inferType(program).getOrElse { throw AssemblyError("unknown dt") }
        })

        fun getSourceForFloat(value: Expression): AsmAssignSource {
            return when (value) {
                is IdentifierReference -> {
                    val addr = AddressOf(value, value.position)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
                is NumericLiteral -> {
                    throw AssemblyError("float literals should have been converted into autovar")
                }
                else -> {
                    if(scope==null)
                        throw AssemblyError("cannot use float arguments outside of a subroutine scope")

                    asmgen.subroutineExtra(scope).usedFloatEvalResultVar2 = true
                    val variable = IdentifierReference(listOf(subroutineFloatEvalResultVar2), value.position)
                    val addr = AddressOf(variable, value.position)
                    addr.linkParents(value)
                    asmgen.assignExpressionToVariable(value, asmgen.asmVariableName(variable), DataType.FLOAT, scope)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
            }
        }

        args.zip(callConv.params).zip(signature.parameters).forEach {
            val paramName = it.second.name
            val conv = it.first.second
            val value = it.first.first
            when {
                conv.variable -> {
                    val varname = "prog8_lib.func_${signature.name}._arg_${paramName}"
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = AddressOf(value as IdentifierReference, value.position)
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, conv.dt, null, variableAsmName = varname)
                    val assign = AsmAssignment(src, tgt, false, program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign)
                }
                conv.reg != null -> {
                    val src = when (conv.dt) {
                        DataType.FLOAT -> getSourceForFloat(value)
                        in PassByReferenceDatatypes -> {
                            // put the address of the argument in AY
                            val addr = AddressOf(value as IdentifierReference, value.position)
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget.fromRegisters(conv.reg!!, false, null, program, asmgen)
                    val assign = AsmAssignment(src, tgt, false, program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign)
                }
                else -> throw AssemblyError("callconv")
            }
        }
    }

    private fun needAsaveForOtherArg(arg: Expression): Boolean =
        arg !is NumericLiteral && arg !is IdentifierReference && arg !is DirectMemoryRead

}
