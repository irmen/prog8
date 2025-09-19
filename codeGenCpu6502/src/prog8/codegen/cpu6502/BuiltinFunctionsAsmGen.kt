package prog8.codegen.cpu6502

import prog8.code.StMemorySlabBlockName
import prog8.code.StStructInstanceBlockName
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.*


internal class BuiltinFunctionsAsmGen(private val program: PtProgram,
                                      private val asmgen: AsmGen6502Internal,
                                      private val assignAsmGen: AssignmentAsmGen) {

    internal fun translateFunctioncallExpression(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?): BaseDataType? {
        return translateFunctioncall(fcall, discardResult = false, resultRegister = resultRegister)
    }

    internal fun translateFunctioncallStatement(fcall: PtBuiltinFunctionCall) {
        translateFunctioncall(fcall, discardResult = true, resultRegister = null)
    }

    private fun translateFunctioncall(fcall: PtBuiltinFunctionCall, discardResult: Boolean, resultRegister: RegisterOrPair?): BaseDataType? {
        if (discardResult && fcall.hasNoSideEffects)
            return null  // can just ignore the whole function call altogether

        val sscope = fcall.definingISub()

        when (fcall.name) {
            "msw" -> funcMsw(fcall, resultRegister)
            "lsw" -> funcLsw(fcall, resultRegister)
            "msb" -> funcMsb(fcall, resultRegister)
            "lsb" -> funcLsb(fcall, resultRegister)
            "mkword" -> funcMkword(fcall, resultRegister)
            "clamp__byte", "clamp__ubyte", "clamp__word", "clamp__uword" -> funcClamp(fcall, resultRegister)
            "min__byte", "min__ubyte", "min__word", "min__uword" -> funcMin(fcall, resultRegister)
            "max__byte", "max__ubyte", "max__word", "max__uword" -> funcMax(fcall, resultRegister)
            "abs__byte", "abs__word", "abs__float" -> funcAbs(fcall, resultRegister, sscope)
            "sgn" -> funcSgn(fcall, resultRegister, sscope)
            "sqrt__ubyte", "sqrt__uword", "sqrt__float" -> funcSqrt(fcall, resultRegister, sscope)
            "divmod__ubyte" -> funcDivmod(fcall)
            "divmod__uword" -> funcDivmodW(fcall)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "setlsb" -> funcSetLsbMsb(fcall, false)
            "setmsb" -> funcSetLsbMsb(fcall, true)
            "memory" -> funcMemory(fcall, discardResult, resultRegister)
            "peekw" -> funcPeekW(fcall, resultRegister)
            "peekf" -> funcPeekF(fcall, resultRegister)
            "peekbool" -> funcPeekBool(fcall, resultRegister)
            "peek" -> throw AssemblyError("peek() should have been replaced by @()")
            "pokew" -> funcPokeW(fcall)
            "pokef" -> funcPokeF(fcall)
            "pokemon" -> {
                val memread = PtMemoryByte(fcall.position)
                memread.add(fcall.args[0])
                memread.parent = fcall
                asmgen.assignExpressionToRegister(memread, RegisterOrPair.A)
                asmgen.out("  pha")
                val memtarget = AsmAssignTarget(TargetStorageKind.MEMORY, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.position, memory=memread)
                asmgen.assignExpressionTo(fcall.args[1], memtarget)
                asmgen.out("  pla")
            }
            "poke" -> throw AssemblyError("poke() should have been replaced by @()")
            "pokebool" -> funcPokeBool(fcall)
            "rsave" -> funcRsave()
            "rrestore" -> funcRrestore()
            "cmp" -> funcCmp(fcall)
            "callfar" -> funcCallFar(fcall, resultRegister)
            "callfar2" -> funcCallFar2(fcall, resultRegister)
            "call" -> funcCall(fcall)
            "prog8_lib_structalloc" -> funcStructAlloc(fcall, discardResult, resultRegister)
            "prog8_lib_stringcompare" -> funcStringCompare(fcall, resultRegister)
            "prog8_lib_square_byte" -> funcSquare(fcall, BaseDataType.UBYTE, resultRegister)
            "prog8_lib_square_word" -> funcSquare(fcall, BaseDataType.UWORD, resultRegister)
            else -> throw AssemblyError("missing asmgen for builtin func ${fcall.name}")
        }

        return BuiltinFunctions.getValue(fcall.name).returnType
    }

    private fun funcSquare(fcall: PtBuiltinFunctionCall, resultType: BaseDataType, resultRegister: RegisterOrPair?) {
        // square of word value is faster with dedicated routine, square of byte just use the regular multiplication routine.
        when (resultType) {
            BaseDataType.UBYTE -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)
                asmgen.out("  tay  |  jsr  prog8_math.multiply_bytes")
                if(resultRegister!=null)  {
                    assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister, false, fcall.position, null, asmgen), CpuRegister.A, false, false)
                }
            }
            BaseDataType.UWORD -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("  jsr  prog8_math.square")
                if(resultRegister!=null)  {
                    assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister, false, fcall.position, null, asmgen), RegisterOrPair.AY)
                }
            }
            else -> {
                throw AssemblyError("optimized square only for integer types")
            }
        }
    }

    private fun funcDivmod(fcall: PtBuiltinFunctionCall) {
        assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, false)
        asmgen.saveRegisterStack(CpuRegister.A, false)
        assignAsmGen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.Y, false)
        asmgen.restoreRegisterStack(CpuRegister.A ,false)
        // math.divmod_ub_asm: -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
        asmgen.out("  jsr  prog8_math.divmod_ub_asm")
        val var2name = asmgen.asmVariableName(fcall.args[2] as PtIdentifier)
        val var3name = asmgen.asmVariableName(fcall.args[3] as PtIdentifier)
        val divisionTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[2].position, var2name)
        val remainderTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[3].position, var3name)
        assignAsmGen.assignRegisterByte(remainderTarget, CpuRegister.A, false, false)
        assignAsmGen.assignRegisterByte(divisionTarget, CpuRegister.Y, false, false)
    }

    private fun funcDivmodW(fcall: PtBuiltinFunctionCall) {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        // math.divmod_uw_asm: -- divide two unsigned words (16 bit each) into 16 bit results
        //    input:  P8ZP_SCRATCH_W1 in ZP: 16-bit number, A/Y: 16 bit divisor
        //    output: P8ZP_SCRATCH_W2 in ZP: 16-bit remainder, A/Y: 16 bit division result
        asmgen.out("  jsr  prog8_math.divmod_uw_asm")
        val var2name = asmgen.asmVariableName(fcall.args[2] as PtIdentifier)
        val divisionTarget = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.args[2].position, var2name)
        val remainderVar = asmgen.asmVariableName(fcall.args[3] as PtIdentifier)
        assignAsmGen.assignRegisterpairWord(divisionTarget, RegisterOrPair.AY)
        asmgen.out("""
            lda  P8ZP_SCRATCH_W2
            ldy  P8ZP_SCRATCH_W2+1
            sta  $remainderVar
            sty  $remainderVar+1""")
    }

    private fun funcStringCompare(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[0], fcall.args[1], "P8ZP_SCRATCH_W2")
        asmgen.out("  jsr  prog8_lib.strcmp_mem")
        if(resultRegister!=null)  {
            assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister, false, fcall.position, null, asmgen), CpuRegister.A, false, false)
        }
    }

    private fun funcRsave() {
        if (asmgen.isTargetCpu(CpuType.CPU65C02))
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

    private fun funcRrestore() {
        if (asmgen.isTargetCpu(CpuType.CPU65C02))
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

    private fun funcCall(fcall: PtBuiltinFunctionCall) {
        // note: the routine can return a word value (in AY)
        val constAddr = fcall.args[0].asConstInteger()
        if(constAddr!=null) {
            asmgen.out("  jsr  ${constAddr.toHex()}")
            return
        }

        val identifier = fcall.args[0] as? PtIdentifier
        if(identifier!=null) {
            asmgen.out("""
                ; push a return address so the jmp becomes indirect jsr
                lda  #>((+)-1)
                pha
                lda  #<((+)-1)
                pha
                jmp  (${asmgen.asmSymbolName(identifier)})
+""")
            return
        }

        asmgen.assignExpressionToVariable(fcall.args[0], asmgen.asmVariableName("P8ZP_SCRATCH_W2"), DataType.UWORD)     // jump address
        asmgen.out("""
                ; push a return address so the jmp becomes indirect jsr
                lda  #>((+)-1)
                pha
                lda  #<((+)-1)
                pha
                jmp  (P8ZP_SCRATCH_W2)
+""")
    }

    private fun funcCallFar(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val targetName = asmgen.options.compTarget.name
        if(targetName !in arrayOf("cx16", "c64", "c128"))
            throw AssemblyError("callfar only works on cx16, c64 and c128 targets at this time")

        val jsrfar = when(targetName) {
            "cx16" -> "cx16.JSRFAR"
            "c64" -> "c64.x16jsrfar"
            "c128" -> "c128.x16jsrfar"
            else -> TODO("jsrfar routine")
        }
        val constBank = fcall.args[0].asConstInteger()
        val constAddress = fcall.args[1].asConstInteger()
        if(constBank!=null && constAddress!=null) {
            asmgen.assignExpressionToRegister(fcall.args[2], RegisterOrPair.AY)     // uword argument
            asmgen.out("""
                jsr  $jsrfar
                .word  ${constAddress.toHex()}
                .byte  $constBank""")
        } else {
            if(asmgen.options.romable)
                TODO("no code for non-const callfar (jsrfar) yet that's usable in ROM  ${fcall.position}")
            // self-modifying code: set jsrfar arguments
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)      // bank
            asmgen.out("  sta  (++)+0")
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)     // jump address
            asmgen.out("  sta  (+)+0 |  sty  (+)+1")
            asmgen.assignExpressionToRegister(fcall.args[2], RegisterOrPair.AY)     // uword argument
            asmgen.out("""
                jsr  $jsrfar
+               .word  0
+               .byte  0""")
        }

        // note that by convention the values in A+Y registers are now the return value of the call.
        if(resultRegister!=null)  {
            assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister, false, fcall.position, null, asmgen), RegisterOrPair.AY)
        }
    }

    private fun funcCallFar2(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val targetName = asmgen.options.compTarget.name
        if(targetName !in arrayOf("cx16", "c64", "c128"))
            throw AssemblyError("callfar2 only works on cx16, c64 and c128 targets at this time")

        fun assignArgs() {
            fun assign(value: PtExpression, register: Char) {
                when(value) {
                    is PtBool -> asmgen.out("  ld$register  #${value.asInt()}")
                    is PtNumber -> asmgen.out("  ld$register  #${value.number.toInt()}")
                    is PtIdentifier -> asmgen.out("  ld$register  ${asmgen.asmVariableName(value)}")
                    else -> TODO("callfar2: support non-simple expressions for arguments")
                }
            }
            assign(fcall.args[2], 'a')
            assign(fcall.args[3], 'x')
            assign(fcall.args[4], 'y')
            val carry = fcall.args[5].asConstInteger()
            if(carry!=null)
                asmgen.out(if(carry==0) "  clc" else "  sec")
            else
                TODO("callfar2: support non-const argument values")
        }

        val jsrfar = when(targetName) {
            "cx16" -> "cx16.JSRFAR"
            "c64" -> "c64.x16jsrfar"
            "c128" -> "c128.x16jsrfar"
            else -> TODO("jsrfar routine")
        }
        val constBank = fcall.args[0].asConstInteger()
        val constAddress = fcall.args[1].asConstInteger()
        if(constBank!=null && constAddress!=null) {
            assignArgs()
            asmgen.out("""
                jsr  $jsrfar
                .word  ${constAddress.toHex()}
                .byte  $constBank""")
        } else {
            if(asmgen.options.romable)
                TODO("no code for non-const callfar2 (jsrfar) yet that's usable in ROM  ${fcall.position}")
            // self-modifying code: set jsrfar arguments
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)      // bank
            asmgen.out("  sta  (++)+0")
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)     // jump address
            asmgen.out("  sta  (+)+0 |  sty  (+)+1")
            assignArgs()
            asmgen.out("""
                jsr  $jsrfar
+               .word  0
+               .byte  0""")
        }

        // note that by convention the values in A+Y registers are now the return value of the call.
        if(resultRegister!=null)  {
            assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister, false, fcall.position, null, asmgen), RegisterOrPair.AY)
        }
    }

    private fun funcCmp(fcall: PtBuiltinFunctionCall) {
        val arg1 = fcall.args[0]
        val arg2 = fcall.args[1]
        if(arg1.type.isByte) {
            if(arg2.type.isByte) {
                when (arg2) {
                    is PtIdentifier -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  ${asmgen.asmVariableName(arg2)}")
                    }
                    is PtNumber -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  #${arg2.number.toInt()}")
                    }
                    is PtBool -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                        asmgen.out("  cmp  #${arg2.asInt()}")
                    }
                    is PtMemoryByte -> {
                        if(arg2.address is PtNumber) {
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.A)
                            asmgen.out("  cmp  ${arg2.address.asConstInteger()!!.toHex()}")
                        } else {
                            asmgen.assignByteOperandsToAAndVar(arg1, arg2, "P8ZP_SCRATCH_B1")
                            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                        }
                    }
                    else -> {
                        asmgen.assignByteOperandsToAAndVar(arg1, arg2, "P8ZP_SCRATCH_B1")
                        asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                    }
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        } else {
            // arg1 is a word
            if(arg2.type.isWord) {
                when (arg2) {
                    is PtIdentifier -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  ${asmgen.asmVariableName(arg2)}+1
                            bne  +
                            cmp  ${asmgen.asmVariableName(arg2)}
+""")
                    }
                    is PtBool -> TODO("word compare against bool  ${arg2.position}")
                    is PtNumber -> {
                        asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                        asmgen.out("""
                            cpy  #>${arg2.number.toInt()}
                            bne  +
                            cmp  #<${arg2.number.toInt()}
+""")
                    }
                    else -> {
                        asmgen.assignWordOperandsToAYAndVar(arg1, arg2, "P8ZP_SCRATCH_W1")
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

    private fun funcMemory(fcall: PtBuiltinFunctionCall, discardResult: Boolean, resultRegister: RegisterOrPair?) {
        if(discardResult)
            throw AssemblyError("should not discard result of memory allocation at $fcall")
        val name = (fcall.args[0] as PtString).value
        require(name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name ${fcall.position}"}

        val slabname = PtIdentifier("$StMemorySlabBlockName.memory_$name", DataType.UWORD, fcall.position)
        val addressOf = PtAddressOf(DataType.pointer(BaseDataType.UBYTE), false, fcall.position)
        addressOf.add(slabname)
        addressOf.parent = fcall
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = addressOf)
        val target = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, fcall.position, null, asmgen)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign, fcall.definingISub())
    }

    private fun funcStructAlloc(fcall: PtBuiltinFunctionCall, discardResult: Boolean, resultRegister: RegisterOrPair?) {
        if(discardResult)
            throw AssemblyError("should not discard result of struct allocation at $fcall")
        // ... don't need to pay attention to args here because struct instance is put together elsewhere we just have to get a pointer to it
        val prefix = if(fcall.args.isEmpty()) "${StStructInstanceBlockName}_bss" else StStructInstanceBlockName
        val labelname = PtIdentifier("$prefix.${SymbolTable.labelnameForStructInstance(fcall)}", fcall.type, fcall.position)
        val addressOf = PtAddressOf(fcall.type, true, fcall.position)
        addressOf.add(labelname)
        addressOf.parent = fcall
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, fcall.type, expression = addressOf)
        val target = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, fcall.position, null, asmgen)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign, fcall.definingISub())
    }


    private fun funcSqrt(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        when(fcall.args[0].type.base) {
            BaseDataType.UBYTE -> {
                asmgen.out("  ldy  #0 |  jsr  prog8_lib.func_sqrt16_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, false, false)
            }
            BaseDataType.UWORD -> {
                asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, false, false)
            }
            BaseDataType.FLOAT -> {
                asmgen.out("  jsr  floats.func_sqrt_into_FAC1")
                assignAsmGen.assignFAC1float(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.FAC1, true, fcall.position, scope, asmgen))
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcRor2(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")

                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        val varname = asmgen.asmVariableName(what.variable!!)
                        asmgen.out("  lda  ${varname},x |  lsr  a |  bcc  + |  ora  #$80 |+  |  sta  ${varname},x")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  lda  ${number.toHex()} |  lsr  a |  bcc  + |  ora  #$80 |+  |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.ror2_mem_ub")
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  lsr  a |  bcc  + |  ora  #$80 |+  |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        val varname = asmgen.asmVariableName(what.variable!!)
                        if(what.splitWords)
                            asmgen.out("  lsr  ${varname}_msb,x |  ror  ${varname}_lsb,x |  bcc  + |  lda  ${varname}_msb,x |  ora  #$80 |  sta  ${varname}_msb,x |+ ")
                        else
                            asmgen.out("  lsr  ${varname}+1,x |  ror  ${varname},x |  bcc  +  |  lda  ${varname}+1,x  |  ora  #$80 |  sta  ${varname}+1,x |+ ")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #$80 |  sta  $variable+1 |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRor(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")

                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        val varname = asmgen.asmVariableName(what.variable!!)
                        asmgen.out("  ror  ${varname},x")
                    }
                    is PtMemoryByte -> {
                        when {
                            what.address is PtNumber -> {
                                val number = (what.address as PtNumber).number
                                asmgen.out("  ror  ${number.toHex()}")
                            }
                            what.address is PtIdentifier -> {
                                asmgen.out("  php")   // save Carry
                                val sourceName = asmgen.loadByteFromPointerIntoA(what.address as PtIdentifier)
                                asmgen.out("  plp |  ror  a")
                                asmgen.storeAIntoZpPointerVar(sourceName, false)
                            }
                            else -> {
                                TODO("ror ptr-expression ${what.position}")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        val varname = asmgen.asmVariableName(what.variable!!)
                        if(what.splitWords)
                            asmgen.out("  ror  ${varname}_msb,x |  ror  ${varname}_lsb,x")
                        else
                            asmgen.out("  ror  ${varname}+1,x |  ror  ${varname},x")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable+1 |  ror  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol2(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        val varname = asmgen.asmVariableName(what.variable!!)
                        asmgen.out("  lda  ${varname},x |  cmp  #$80 |  rol  a |  sta  ${varname},x")
                    }
                    is PtMemoryByte -> {
                        if (what.address is PtNumber) {
                            val number = (what.address as PtNumber).number
                            asmgen.out("  lda  ${number.toHex()} |  cmp  #$80 |  rol  a |  sta  ${number.toHex()}")
                        } else {
                            asmgen.assignExpressionToRegister(what.address, RegisterOrPair.AY)
                            asmgen.out("  jsr  prog8_lib.rol2_mem_ub")
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  lda  $variable |  cmp  #$80 |  rol  a |  sta  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        val varname = asmgen.asmVariableName(what.variable!!)
                        if(what.splitWords)
                            asmgen.out("  asl  ${varname}_lsb,x |  rol  ${varname}_msb,x |  bcc  + |  inc  ${varname}_lsb,x |+")
                        else
                            asmgen.out("  asl  ${varname},x |  rol  ${varname}+1,x |  bcc  + |  inc  ${varname},x |+  ")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  asl  $variable |  rol  $variable+1 |  bcc  + |  inc  $variable |+  ")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcRol(fcall: PtBuiltinFunctionCall) {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        val varname = asmgen.asmVariableName(what.variable!!)
                        asmgen.out("  rol  ${varname},x")
                    }
                    is PtMemoryByte -> {
                        when {
                            what.address is PtNumber -> {
                                val number = (what.address as PtNumber).number
                                asmgen.out("  rol  ${number.toHex()}")
                            }
                            what.address is PtIdentifier -> {
                                asmgen.out("  php")   // save Carry
                                val sourceName = asmgen.loadByteFromPointerIntoA(what.address as PtIdentifier)
                                asmgen.out("  plp |  rol  a")
                                asmgen.storeAIntoZpPointerVar(sourceName, false)
                            }
                            else -> {
                                TODO("rol ptr-expression ${what.position}")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("support for ptr indexing ${what.position}")
                        val varname = asmgen.asmVariableName(what.variable!!)
                        if(what.splitWords)
                            asmgen.out("  rol  ${varname}_lsb,x |  rol  ${varname}_msb,x")
                        else
                            asmgen.out("  rol  ${varname},x |  rol  ${varname}+1,x")
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable |  rol  $variable+1")
                    }
                    else -> throw AssemblyError("weird type")
                }
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcSetLsbMsb(fcall: PtBuiltinFunctionCall, msb: Boolean) {
        val target: AsmAssignTarget
        when(fcall.args[0]) {
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(fcall.args[0] as PtIdentifier) + if(msb) "+1" else ""
                target = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingSub(), fcall.position, variableAsmName = varname)
            }
            is PtArrayIndexer -> {
                val indexer = fcall.args[0] as PtArrayIndexer
                val elementSize: Int
                val msbAdd: Int
                if(indexer.splitWords) {
                    val arrayVariable = indexer.variable ?: TODO("support for ptr indexing ${indexer.position}")
                    indexer.children[0] = PtIdentifier(arrayVariable.name + if(msb) "_msb" else "_lsb", DataType.arrayFor(BaseDataType.UBYTE, false), arrayVariable.position)
                    indexer.children[0].parent = indexer
                    elementSize = 1
                    msbAdd = 0
                } else {
                    elementSize = 2
                    msbAdd = if(msb) 1 else 0
                }

                // double the index because of word array (if not split), add one if msb (if not split)
                val constIndexNum = (indexer.index as? PtNumber)?.number
                if(constIndexNum!=null) {
                    indexer.children[1] = PtNumber(indexer.index.type.base, constIndexNum*elementSize + msbAdd, indexer.position)
                    indexer.children[1].parent = indexer
                } else {
                    val multipliedIndex: PtExpression
                    if(elementSize==1) {
                        multipliedIndex = indexer.index
                    } else {
                        multipliedIndex = PtBinaryExpression("<<", indexer.index.type, indexer.position)
                        multipliedIndex.add(indexer.index)
                        multipliedIndex.add(PtNumber(BaseDataType.UBYTE, 1.0, indexer.position))
                    }
                    if(msbAdd>0) {
                        val msbIndex = PtBinaryExpression("+", indexer.index.type, indexer.position)
                        msbIndex.add(multipliedIndex)
                        msbIndex.add(PtNumber(BaseDataType.UBYTE, msbAdd.toDouble(), indexer.position))
                        indexer.children[1] = msbIndex
                        msbIndex.parent = indexer
                    } else {
                        indexer.children[1] = multipliedIndex
                        multipliedIndex.parent=indexer
                    }
                }
                target = AsmAssignTarget(TargetStorageKind.ARRAY, asmgen, DataType.UBYTE, fcall.definingSub(), fcall.position, array = indexer)
            }
            else -> throw AssemblyError("setlsb/setmsb on weird target ${fcall.args[0]}")
        }

        if(fcall.args[1].asConstInteger() == 0) {
            assignAsmGen.assignConstantByte(target, 0)
        } else {
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
            assignAsmGen.assignRegisterByte(target, CpuRegister.A, false, false)
        }
    }

    private fun funcSgn(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        when (val dt = fcall.args.single().type.base) {
            BaseDataType.UBYTE -> asmgen.out("  jsr  prog8_lib.func_sign_ub_into_A")
            BaseDataType.BYTE -> asmgen.out("  jsr  prog8_lib.func_sign_b_into_A")
            BaseDataType.UWORD -> asmgen.out("  jsr  prog8_lib.func_sign_uw_into_A")
            BaseDataType.WORD -> asmgen.out("  jsr  prog8_lib.func_sign_w_into_A")
            BaseDataType.FLOAT -> asmgen.out("  jsr  floats.func_sign_f_into_A")
            else -> throw AssemblyError("weird type $dt")
        }
        assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A, true, true)
    }

    private fun funcAbs(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?, scope: IPtSubroutine?) {
        translateArguments(fcall, scope)
        val dt = fcall.args.single().type.base
        when (dt) {
            BaseDataType.BYTE -> {
                asmgen.out("  jsr  prog8_lib.abs_b_into_A")
                assignAsmGen.assignRegisterByte(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, false, fcall.position, scope, asmgen), CpuRegister.A,false, true)
            }
            BaseDataType.WORD -> {
                asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                assignAsmGen.assignRegisterpairWord(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, false, fcall.position, scope, asmgen), RegisterOrPair.AY)
            }
            BaseDataType.FLOAT -> {
                asmgen.out("  jsr  floats.func_abs_f_into_FAC1")
                assignAsmGen.assignFAC1float(AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.FAC1, true, fcall.position, scope, asmgen))
            }
            BaseDataType.UBYTE -> {
                asmgen.assignRegister(RegisterOrPair.A, AsmAssignTarget.fromRegisters(resultRegister?:RegisterOrPair.A, false, fcall.position, scope, asmgen))
            }
            BaseDataType.UWORD -> {
                asmgen.assignRegister(RegisterOrPair.AY, AsmAssignTarget.fromRegisters(resultRegister?:RegisterOrPair.AY, false, fcall.position, scope, asmgen))
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcPokeF(fcall: PtBuiltinFunctionCall) {
        when(val number = fcall.args[1]) {
            is PtIdentifier -> {
                val varName = asmgen.asmVariableName(number)
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("""
                    pha
                    lda  #<$varName
                    sta  P8ZP_SCRATCH_W1
                    lda  #>$varName
                    sta  P8ZP_SCRATCH_W1+1
                    pla
                    jsr  floats.copy_float""")
            }
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.assignConstFloatToPointerAY(number)
            }
            else -> {
                val tempvar = asmgen.createTempVarReused(BaseDataType.FLOAT, false, fcall)
                asmgen.assignExpressionToVariable(fcall.args[1], tempvar, DataType.FLOAT)
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("""
                    pha
                    lda  #<$tempvar
                    sta  P8ZP_SCRATCH_W1
                    lda  #>$tempvar
                    sta  P8ZP_SCRATCH_W1+1
                    pla
                    jsr  floats.copy_float""")
            }
        }
    }

    private fun funcPokeBool(fcall: PtBuiltinFunctionCall) {
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr")
                return
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                    asmgen.storeIndirectByteReg(CpuRegister.A, varname, 0u, false, false)
                    return
                }
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                    asmgen.out("  sta  ($varname),y")
                    return
                }
            }
            else -> { /* fall through */ }
        }

        // fall through method:
        if(fcall.args[1].isSimple()) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", DataType.UWORD)
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
        }  else {
            asmgen.pushCpuStack(BaseDataType.UBYTE, fcall.args[1])
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", DataType.UWORD)
            asmgen.restoreRegisterStack(CpuRegister.A, false)
        }
        asmgen.storeIndirectByteReg(CpuRegister.A, "P8ZP_SCRATCH_W1", 0u, false, false)
    }

    private fun funcPokeW(fcall: PtBuiltinFunctionCall) {
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr |  sty  ${addr}+1")
                return
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    asmgen.storeIndirectWordReg(RegisterOrPair.AX, varname, 0u)
                    return
                }
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                    asmgen.out("""
                            sta  ($varname),y
                            txa
                            iny
                            sta  ($varname),y""")
                    return
                }
            }
            else -> { /* fall through */ }
        }

        // fall through method:
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        asmgen.out("  jsr  prog8_lib.func_pokew")
    }

    private fun funcPeekF(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
        asmgen.out("  jsr  floats.MOVFM")
        if(resultRegister!=null) {
            assignAsmGen.assignFAC1float(
                AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.FLOAT, fcall.definingISub(), register=resultRegister, position=fcall.position))
        }
    }

    private fun funcPeekBool(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        fun fallback() {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
            asmgen.out("  jsr  prog8_lib.func_peek")
        }
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                val addr = addrExpr.number.toHex()
                asmgen.out("  lda  $addr")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr))
                    asmgen.loadIndirectByte(varname, 0u)
                else
                    fallback()
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.out("  lda  ($varname),y")
                } else fallback()
            }
            else -> fallback()
        }

        when(resultRegister ?: RegisterOrPair.A) {
            RegisterOrPair.A -> {}
            RegisterOrPair.X -> asmgen.out("  tax")
            RegisterOrPair.Y -> asmgen.out("  tay")
            in Cx16VirtualRegisters -> asmgen.out("  sta  cx16.${resultRegister.toString().lowercase()}")
            else -> throw AssemblyError("invalid reg")
        }
    }

    private fun funcPeekW(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        fun fallback() {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
            asmgen.out("  jsr  prog8_lib.func_peekw")
        }
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                val addr = addrExpr.number.toHex()
                asmgen.out("  lda  $addr |  ldy  ${addr}+1")
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr))
                    asmgen.loadIndirectWord(varname, 0u)
                else
                    fallback()
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                if(result!=null && pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do ZP,Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.out("""
                        lda  ($varname),y
                        tax
                        iny
                        lda  ($varname),y
                        tay
                        txa""")
                } else fallback()
            }
            else -> fallback()
        }

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

    private fun funcClamp(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type.isSigned
        when {
            fcall.type.isByte -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W1+1", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
                assignAsmGen.assignRegisterByte(targetReg, CpuRegister.A, signed, true)
            }
            fcall.type.isWord -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W2", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
                assignAsmGen.assignRegisterpairWord(targetReg, RegisterOrPair.AY)
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun funcMin(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type.isSigned
        when {
            fcall.type.isByte -> {
                asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_B1", fcall.type)     // right
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)          // left
                asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                if(signed) asmgen.out("  bmi  +") else asmgen.out("  bcc  +")
                asmgen.out("""
                    lda  P8ZP_SCRATCH_B1
    +""")
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
                asmgen.assignRegister(RegisterOrPair.A, targetReg)
            }
            fcall.type.isWord -> {
                asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", fcall.type)     // left
                asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W2", fcall.type)     // right
                if(signed) {
                    asmgen.out("""
                        lda  P8ZP_SCRATCH_W1
                        ldy  P8ZP_SCRATCH_W1+1
                        cmp  P8ZP_SCRATCH_W2
                        tya
                        sbc  P8ZP_SCRATCH_W2+1
                        bvc  +
                        eor  #$80
    +                   bpl  +
                        lda  P8ZP_SCRATCH_W1                   
                        ldy  P8ZP_SCRATCH_W1+1
                        jmp  ++
    +                   lda  P8ZP_SCRATCH_W2
                        ldy  P8ZP_SCRATCH_W2+1
    +""")
                } else {
                    asmgen.out("""
                        lda  P8ZP_SCRATCH_W1+1
                        cmp  P8ZP_SCRATCH_W2+1
                        bcc  ++
                        bne  +
                        lda  P8ZP_SCRATCH_W1
                        cmp  P8ZP_SCRATCH_W2
                        bcc  ++
    +                   lda  P8ZP_SCRATCH_W2
                        ldy  P8ZP_SCRATCH_W2+1
                        jmp  ++
    +                   lda  P8ZP_SCRATCH_W1
                        ldy  P8ZP_SCRATCH_W1+1
    +""")
                }
                val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
                asmgen.assignRegister(RegisterOrPair.AY, targetReg)
            }
            else -> {
                throw AssemblyError("min float not supported")
            }
        }
    }

    private fun funcMax(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val signed = fcall.type.isSigned
        if(fcall.type.isByte) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_B1", fcall.type)     // left
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)          // right
            asmgen.out("  cmp  P8ZP_SCRATCH_B1")
            if(signed) asmgen.out("  bpl  +") else asmgen.out("  bcs  +")
            asmgen.out("""
                lda  P8ZP_SCRATCH_B1
+""")
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.A, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.A, targetReg)
        } else if(fcall.type.isWord) {
            asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", fcall.type)     // left
            asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W2", fcall.type)     // right
            if(signed) {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2
                    tya
                    sbc  P8ZP_SCRATCH_W2+1
                    bvc  +
                    eor  #$80
+                   bmi  +
                    lda  P8ZP_SCRATCH_W1                   
                    ldy  P8ZP_SCRATCH_W1+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
+""")
            } else {
                asmgen.out("""
                    lda  P8ZP_SCRATCH_W1+1
                    cmp  P8ZP_SCRATCH_W2+1
                    bcc  ++
                    bne  +
                    lda  P8ZP_SCRATCH_W1
                    cmp  P8ZP_SCRATCH_W2
                    bcc  ++
+                   lda  P8ZP_SCRATCH_W1
                    ldy  P8ZP_SCRATCH_W1+1
                    jmp  ++
+                   lda  P8ZP_SCRATCH_W2
                    ldy  P8ZP_SCRATCH_W2+1
+""")
            }
            val targetReg = AsmAssignTarget.fromRegisters(resultRegister ?: RegisterOrPair.AY, signed, fcall.position, fcall.definingISub(), asmgen)
            asmgen.assignRegister(RegisterOrPair.AY, targetReg)
        } else {
            throw AssemblyError("max float not supported")
        }
    }

    private fun funcMkword(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val reg = resultRegister ?: RegisterOrPair.AY
        var needAsaveForArg0 = asmgen.needAsaveForExpr(fcall.args[0])
        if(!needAsaveForArg0) {
            val mr0 = fcall.args[0] as? PtMemoryByte
            val mr1 = fcall.args[1] as? PtMemoryByte
            if (mr0 != null)
                needAsaveForArg0 =  mr0.address !is PtNumber
            if (mr1 != null)
                needAsaveForArg0 = needAsaveForArg0 or (mr1.address !is PtNumber)
        }
        when(reg) {
            RegisterOrPair.AX -> {
                if(needAsaveForArg0 && !asmgen.needAsaveForExpr(fcall.args[1])) {
                    // first 0 then 1
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.X)      // msb
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                } else if(!needAsaveForArg0 && asmgen.needAsaveForExpr(fcall.args[1])) {
                    // first 1 then 0
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.X)      // msb
                } else {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    if (needAsaveForArg0)
                        asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.X)      // msb
                    if (needAsaveForArg0)
                        asmgen.out("  pla")
                }
            }
            RegisterOrPair.AY -> {
                if(needAsaveForArg0 && !asmgen.needAsaveForExpr(fcall.args[1])) {
                    // first 0 then 1
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                } else if(!needAsaveForArg0 && asmgen.needAsaveForExpr(fcall.args[1])) {
                    // first 1 then 0
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                } else {
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                    if (needAsaveForArg0)
                        asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                    if (needAsaveForArg0)
                        asmgen.out("  pla")
                }
            }
            RegisterOrPair.XY -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // lsb
                if (needAsaveForArg0)
                    asmgen.out("  pha")
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.Y)      // msb
                if (needAsaveForArg0)
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

    private fun funcMsb(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.type.isWord)
            throw AssemblyError("msb requires word argument")
        if (arg is PtNumber)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName+1")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName+1")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName+1")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName+1 |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName+1 |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName+1 |  ldy  #0")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  stz  cx16.$regname+1")
                    else
                        asmgen.out("  lda  $sourceName+1 |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            if(arg is PtArrayIndexer && resultRegister in arrayOf(null, RegisterOrPair.A, RegisterOrPair.Y, RegisterOrPair.X)) {
                // just read the msb byte out of the word array
                if(arg.splitWords) {
                    if(arg.variable==null)
                        TODO("support for ptr indexing ${arg.position}")
                    val arrayVar = asmgen.asmVariableName(arg.variable!!)+"_msb"
                    when(resultRegister) {
                        null, RegisterOrPair.A -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVar,y")
                        }
                        RegisterOrPair.Y -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                            asmgen.out("  ldy  $arrayVar,x")
                        }
                        RegisterOrPair.X -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  ldx  $arrayVar,y")
                        }
                        else -> throw AssemblyError("invalid reg")
                    }
                } else {
                    if(arg.variable==null)
                        TODO("support for ptr indexing ${arg.position}")
                    val arrayVar = asmgen.asmVariableName(arg.variable!!)
                    when(resultRegister) {
                        null, RegisterOrPair.A -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVar+1,y")
                        }
                        RegisterOrPair.Y -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                            asmgen.out("  ldy  $arrayVar+1,x")
                        }
                        RegisterOrPair.X -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  ldx  $arrayVar+1,y")
                        }
                        else -> throw AssemblyError("invalid reg")
                    }
                }
            } else when(resultRegister) {
                null, RegisterOrPair.A -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  tya")
                }
                RegisterOrPair.X -> {
                    asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AX)
                    asmgen.out("  pla")
                }
                RegisterOrPair.Y -> {
                    asmgen.out("  pha")
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  pla")
                }
                RegisterOrPair.AY -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  tya |  ldy  #0 |  cmp  #0")
                }
                RegisterOrPair.AX -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AX)
                    asmgen.out("  txa |  ldx  #0 |  cmp  #0")
                }
                RegisterOrPair.XY -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.XY)
                    asmgen.out("  tya |  tax |  ldy  #0 |  cpx  #0")
                }
                in Cx16VirtualRegisters -> {
                    val reg = "cx16.${resultRegister.toString().lowercase()}"
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  sty  ${reg}L |  lda  #0 |  sta  ${reg}H |  lda  ${reg}L")
                }
                else -> throw AssemblyError("invalid reg")
            }
        }
    }

    private fun funcLsb(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.type.isWord)
            throw AssemblyError("lsb required word argument")
        if (arg is PtNumber)
            throw AssemblyError("lsb(const) should have been const-folded away")

        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                null, RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    if(asmgen.isTargetCpu(CpuType.CPU65C02))
                        asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  stz  cx16.$regname+1")
                    else
                        asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  #0 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            if(arg is PtArrayIndexer && resultRegister in arrayOf(null, RegisterOrPair.A, RegisterOrPair.Y, RegisterOrPair.X)) {
                // just read the lsb byte out of the word array
                if(arg.variable==null)
                    TODO("support for ptr indexing ${arg.position}")

                val arrayVar = if(arg.splitWords) asmgen.asmVariableName(arg.variable!!)+"_lsb" else asmgen.asmVariableName(arg.variable!!)
                when(resultRegister) {
                    null, RegisterOrPair.A -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                        asmgen.out("  lda  $arrayVar,y")
                    }
                    RegisterOrPair.Y -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                        asmgen.out("  ldy  $arrayVar,x")
                    }
                    RegisterOrPair.X -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                        asmgen.out("  ldx  $arrayVar,y")
                    }
                    else -> throw AssemblyError("invalid reg")
                }
            } else  when(resultRegister) {
                null, RegisterOrPair.A -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    // NOTE: we rely on the fact that the above assignment to AY, assigns the Lsb to A as the last instruction.
                    //       this is required because the compiler assumes the status bits are set according to what A is (lsb)
                    //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                }
                RegisterOrPair.X -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.XY)
                    // NOTE: we rely on the fact that the above assignment to XY, assigns the Lsb to X as the last instruction.
                    //       this is required because the compiler assumes the status bits are set according to what X is (lsb)
                    //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                }
                RegisterOrPair.Y -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  tay |  cpy  #0")
                }
                RegisterOrPair.AY -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  ldy  #0 |  cmp  #0")
                }
                RegisterOrPair.AX -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AX)
                    asmgen.out("  ldx  #0 |  cmp  #0")
                }
                RegisterOrPair.XY -> {
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.XY)
                    asmgen.out("  ldy  #0 |  cpx  #0")
                }
                in Cx16VirtualRegisters -> {
                    val reg = "cx16.${resultRegister.toString().lowercase()}"
                    asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    asmgen.out("  sta  ${reg}L |  ldy  #0 |  sty  ${reg}H |  cmp  #0")
                }
                else -> throw AssemblyError("invalid reg")
            }
        }
    }

    private fun funcMsw(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.type.isLong)
            throw AssemblyError("msw requires long argument")
        if (arg is PtNumber)
            throw AssemblyError("msw(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName+2 |  ldx  $sourceName+3")
                null, RegisterOrPair.AY -> asmgen.out("  lda  $sourceName+2 |  ldy  $sourceName+3")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName+2 |  ldy  $sourceName+3")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    asmgen.out("  lda  $sourceName+2 |  sta  cx16.$regname |  lda  $sourceName+3 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            TODO("msw(expression) ${fcall.position} - use a temporary variable for now")
        }
    }

    private fun funcLsw(fcall: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?) {
        val arg = fcall.args.single()
        if (!arg.type.isLong)
            throw AssemblyError("lsw requires long argument")
        if (arg is PtNumber)
            throw AssemblyError("lsw(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultRegister) {
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  $sourceName+1")
                null, RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  $sourceName+1")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  $sourceName+1")
                in Cx16VirtualRegisters -> {
                    val regname = resultRegister.name.lowercase()
                    asmgen.out("  lda  $sourceName |  sta  cx16.$regname |  lda  $sourceName+1 |  sta  cx16.$regname+1")
                }
                else -> throw AssemblyError("invalid reg")
            }
        } else {
            TODO("lsw(expression) ${fcall.position} - use a temporary variable for now")
        }

    }

    private fun translateArguments(call: PtBuiltinFunctionCall, scope: IPtSubroutine?) {
        val signature = BuiltinFunctions.getValue(call.name)
        val callConv = signature.callConvention(call.args.map {
            require(it.type.isNumericOrBool)
            it.type.base
        })

        fun getSourceForFloat(value: PtExpression): AsmAssignSource {
            return when (value) {
                is PtIdentifier -> {
                    val addr = PtAddressOf(DataType.pointer(BaseDataType.FLOAT), false, value.position)
                    addr.add(value)
                    addr.parent = call
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
                is PtNumber -> {
                    throw AssemblyError("float literals should have been converted into autovar")
                }
                else -> {
                    if(scope==null)
                        throw AssemblyError("cannot use float arguments outside of a subroutine scope")

                    asmgen.subroutineExtra(scope).usedFloatEvalResultVar2 = true
                    val variable = PtIdentifier(subroutineFloatEvalResultVar2, DataType.FLOAT, value.position)
                    val addr = PtAddressOf(DataType.pointer(BaseDataType.FLOAT), false, value.position)
                    addr.add(variable)
                    addr.parent = call
                    asmgen.assignExpressionToVariable(value, asmgen.asmVariableName(variable), DataType.FLOAT)
                    AsmAssignSource.fromAstSource(addr, program, asmgen)
                }
            }
        }

        call.args.zip(callConv.params).zip(signature.parameters).forEach {
            val paramName = it.second.name
            val conv = it.first.second
            val value = it.first.first
            when {
                conv.variable -> {
                    val varname = "prog8_lib.func_${call.name}._arg_${paramName}"
                    val src = when  {
                        conv.dt==BaseDataType.FLOAT -> getSourceForFloat(value)
                        conv.dt.isPassByRef -> {
                            // put the address of the argument in AY
                            val addr = PtAddressOf(DataType.forDt(conv.dt).typeForAddressOf(false), false, value.position)
                            addr.add(value)
                            addr.parent = call
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.forDt(conv.dt), null, value.position, variableAsmName = varname)
                    val assign = AsmAssignment(src, listOf(tgt), program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign, scope)
                }
                conv.reg != null -> {
                    val src = when {
                        conv.dt==BaseDataType.FLOAT -> getSourceForFloat(value)
                        conv.dt.isPassByRef -> {
                            // put the address of the argument in AY
                            val addr = PtAddressOf(DataType.forDt(conv.dt).typeForAddressOf(false), false,value.position)
                            addr.add(value)
                            addr.parent = call
                            AsmAssignSource.fromAstSource(addr, program, asmgen)
                        }
                        else -> {
                            AsmAssignSource.fromAstSource(value, program, asmgen)
                        }
                    }
                    val tgt = AsmAssignTarget.fromRegisters(conv.reg!!, false, value.position, null, asmgen)
                    val assign = AsmAssignment(src, listOf(tgt), program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign, scope)
                }
                else -> throw AssemblyError("callconv")
            }
        }
    }

}
