 package prog8.codegen.cpu6502

import prog8.code.StMemorySlabBlockName
import prog8.code.StStructInstanceBlockName
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.*


 internal class BuiltinFunctionsAsmGen(private val program: PtProgram,
                                      private val asmgen: AsmGen6502Internal,
                                      private val ptrgen: PointerAssignmentsGen,
                                      private val assignAsmGen: AssignmentAsmGen) {

    internal fun translateFunctioncallExpression(fcall: PtFunctionCall, firstReturnRegister: RegisterOrPair?): Array<RegisterOrPair> {
        return translateFunctioncall(fcall, firstReturnRegister, discardResult = false)
    }

    internal fun translateFunctioncallStatement(fcall: PtFunctionCall) {
        translateFunctioncall(fcall, null, discardResult = true)
    }

    private fun translateFunctioncall(fcall: PtFunctionCall, firstReturnRegister: RegisterOrPair?, discardResult: Boolean): Array<RegisterOrPair> {
        require(fcall.builtin)
        if (discardResult && fcall.hasNoSideEffects)
            return emptyArray()  // can just ignore the whole function call altogether

        val sscope = fcall.definingISub()

        return when (fcall.name) {
            "msw" -> funcMsw(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "lsw" -> funcLsw(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "msb" -> funcMsb(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "msb__long" -> funcMsbLong(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "lsb" -> funcLsb(fcall, false, firstReturnRegister ?: RegisterOrPair.A)
            "lsb__long" -> funcLsb(fcall,true, firstReturnRegister ?: RegisterOrPair.A)
            "mkword" -> funcMkword(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "mklong", "mklong2" -> funcMklong(fcall, firstReturnRegister ?: RegisterOrPair.R14R15)
            "clamp__byte", "clamp__ubyte" -> funcClamp(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "clamp__word", "clamp__uword" -> funcClamp(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "clamp__long" -> funcClamp(fcall, firstReturnRegister ?: RegisterOrPair.R14R15)
            "min__byte", "min__ubyte" -> funcMin(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "min__word", "min__uword" -> funcMin(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "min__long" -> funcMin(fcall, firstReturnRegister ?: RegisterOrPair.R14R15)
            "max__byte", "max__ubyte" -> funcMax(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "max__word", "max__uword" -> funcMax(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "max__long" -> funcMax(fcall, firstReturnRegister ?: RegisterOrPair.R14R15)
            "abs__byte" -> funcAbs(fcall, sscope, firstReturnRegister ?: RegisterOrPair.A)
            "abs__word" -> funcAbs(fcall, sscope, firstReturnRegister ?: RegisterOrPair.AY)
            "abs__long"-> funcAbs(fcall, sscope, firstReturnRegister ?: RegisterOrPair.R14R15)
            "abs__float" -> funcAbs(fcall, sscope, firstReturnRegister ?: RegisterOrPair.FAC1)
            "sgn" -> funcSgn(fcall, sscope, firstReturnRegister ?: RegisterOrPair.A)
            "sqrt__ubyte", "sqrt__uword" -> funcSqrt(fcall, sscope, firstReturnRegister ?: RegisterOrPair.A)
            "sqrt__long" -> funcSqrt(fcall, sscope, firstReturnRegister ?: RegisterOrPair.AY)
            "sqrt__float" -> funcSqrt(fcall, sscope, firstReturnRegister ?: RegisterOrPair.FAC1)
            "divmod__ubyte" -> funcDivmod(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "divmod__uword" -> funcDivmodW(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "lmh" -> funcLmh(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "rol" -> funcRol(fcall)
            "rol2" -> funcRol2(fcall)
            "ror" -> funcRor(fcall)
            "ror2" -> funcRor2(fcall)
            "setlsb" -> funcSetLsbMsb(fcall, false)
            "setmsb" -> funcSetLsbMsb(fcall, true)
            "memory" -> {
                require(!discardResult) { "${fcall.position} should not discard result"}
                funcMemory(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            }
            "peekw" -> funcPeekW(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "peekl" -> funcPeekL(fcall, firstReturnRegister ?: RegisterOrPair.R14R15)
            "peekf" -> funcPeekF(fcall, firstReturnRegister ?: RegisterOrPair.FAC1)
            "peekbool" -> funcPeekBool(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "pokew" -> funcPokeW(fcall)
            "pokel" -> funcPokeL(fcall)
            "pokef" -> funcPokeF(fcall)
            "pokemon" -> funcPokemon(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "peek" -> throw AssemblyError("peek() should have been replaced by @()")
            "poke" -> throw AssemblyError("poke() should have been replaced by @()")
            "pokebool" -> funcPokeBool(fcall)
            "rsave" -> funcRsave()
            "rrestore" -> funcRrestore()
            "cmp" -> funcCmp(fcall)
            "callfar" -> funcCallFar(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "callfar2" -> funcCallFar2(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "call" -> funcCall(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            "prog8_lib_structalloc" -> {
                require(!discardResult) { "${fcall.position} should not discard result"}
                funcStructAlloc(fcall, firstReturnRegister ?: RegisterOrPair.AY)
            }
            "prog8_lib_stringcompare" -> funcStringCompare(fcall, firstReturnRegister ?: RegisterOrPair.A)
            "prog8_lib_square_byte" -> funcSquare(fcall, BaseDataType.UBYTE, firstReturnRegister ?: RegisterOrPair.A)
            "prog8_lib_square_word" -> funcSquare(fcall, BaseDataType.UWORD, firstReturnRegister ?: RegisterOrPair.AY)
            "prog8_lib_copylong" -> funcCopyFromPointer1ToPointer2(fcall, BaseDataType.LONG)
            "prog8_lib_copyfloat" -> funcCopyFromPointer1ToPointer2(fcall, BaseDataType.FLOAT)
            "push" -> funcPush(fcall)
            "pushw" -> funcPushW(fcall)
            "pushl" -> funcPushL(fcall)
            "pushf" -> funcPushF(fcall)
            "pop" -> funcPop(firstReturnRegister ?: RegisterOrPair.A)
            "popw" -> funcPopW(firstReturnRegister ?: RegisterOrPair.AY)
            "popl" -> funcPopL(firstReturnRegister ?: RegisterOrPair.R14R15)
            "popf" -> funcPopF(firstReturnRegister ?: RegisterOrPair.FAC1)
            else -> throw AssemblyError("missing asmgen for builtin func ${fcall.name}")
        }
    }

    private fun funcPush(fcall: PtFunctionCall): Array<RegisterOrPair> {
        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)
        asmgen.out("  pha")
        return emptyArray()
    }

    private fun funcPushW(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val value = fcall.args.single()
        if(value is PtIdentifier) {
            val varname = asmgen.asmVariableName(value)
            asmgen.out("""
                lda  $varname
                pha
                lda  $varname+1
                pha""")
        } else {
            if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                asmgen.out("  pha |  phy")
            } else {
                asmgen.assignExpressionToRegister(value, RegisterOrPair.AY)
                asmgen.out("  pha |  tya |  pha")
            }
        }

        return emptyArray()
    }

    private fun funcPushL(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val value = fcall.args.single()
        if(value is PtIdentifier) {
            val varname = asmgen.asmVariableName(value)
            asmgen.out("""
                lda  $varname
                pha
                lda  $varname+1
                pha
                lda  $varname+2
                pha
                lda  $varname+3
                pha""")
        } else {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.R14R15, true)
            asmgen.out("""
                lda  cx16.r14
                pha
                lda  cx16.r14+1
                pha
                lda  cx16.r14+2
                pha
                lda  cx16.r14+3
                pha""")
        }

        return emptyArray()
    }

    private fun funcPushF(fcall: PtFunctionCall): Array<RegisterOrPair> {
        asmgen.assignExpressionToRegister(fcall.args.single(), RegisterOrPair.FAC1, true)
        asmgen.out("  jsr  floats.pushFAC1")

        return emptyArray()
    }

    private fun funcPop(resultReg: RegisterOrPair): Array<RegisterOrPair> {
        require(resultReg in arrayOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) { "pop must have result register A, X or Y" }
        asmgen.restoreRegisterStack(resultReg.asCpuRegister(), resultReg!=RegisterOrPair.A)
        return arrayOf(resultReg)
    }

    private fun funcPopW(resultReg: RegisterOrPair): Array<RegisterOrPair> {
        if (asmgen.isTargetCpu(CpuType.CPU65C02)) {
            when(resultReg) {
                RegisterOrPair.AX -> asmgen.out("  plx |  pla")
                RegisterOrPair.AY -> asmgen.out("  ply |  pla")
                RegisterOrPair.XY -> asmgen.out("  ply |  plx")
                else -> throw AssemblyError("unsupported popw register $resultReg")
            }
        } else {
            when(resultReg) {
                RegisterOrPair.AX -> asmgen.out("  pla |  tax |  pla")
                RegisterOrPair.AY -> asmgen.out("  pla |  tay |  pla")
                RegisterOrPair.XY -> asmgen.out("  pla |  tay |  pla |  tax")
                else -> throw AssemblyError("unsupported popw register $resultReg")
            }
        }

        return arrayOf(resultReg)
    }

    private fun funcPopL(resultReg: RegisterOrPair): Array<RegisterOrPair> {
        asmgen.out("""
            pla
            sta  cx16.r14+3
            pla
            sta  cx16.r14+2
            pla
            sta  cx16.r14+1
            pla
            sta  cx16.r14""")
        return arrayOf(RegisterOrPair.R14R15)
    }

    private fun funcPopF(resultReg: RegisterOrPair): Array<RegisterOrPair> {
        asmgen.out("  clc | jsr  floats.popFAC")
        return arrayOf(RegisterOrPair.FAC1)
    }

    private fun funcCopyFromPointer1ToPointer2(fcall: PtFunctionCall, type: BaseDataType): Array<RegisterOrPair> {
        if(fcall.args[1].isSimple()) {
            asmgen.assignExpressionToVariable(fcall.args[0], asmgen.asmVariableName("P8ZP_SCRATCH_W1"), DataType.UWORD)
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY, false)
        }
        else {
            asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY, false)
            asmgen.saveRegisterStack(CpuRegister.A, false)
            asmgen.saveRegisterStack(CpuRegister.Y, false)
            asmgen.assignExpressionToVariable(fcall.args[0], asmgen.asmVariableName("P8ZP_SCRATCH_W1"), DataType.UWORD)
            asmgen.restoreRegisterStack(CpuRegister.Y, false)
            asmgen.restoreRegisterStack(CpuRegister.A, false)
        }
        when(type) {
            BaseDataType.WORD -> asmgen.out("  jsr  prog8_lib.copyfrompointer1topointer2_word")
            BaseDataType.LONG -> asmgen.out("  jsr  prog8_lib.copyfrompointer1topointer2_long")
            BaseDataType.FLOAT -> asmgen.out("  jsr  floats.copy_float")
            else -> throw AssemblyError("unsupported type for copyfrompointer1topointer2: $type")
        }

        return emptyArray()
    }

    private fun funcSquare(fcall: PtFunctionCall, resultType: BaseDataType, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        // square of word value is faster with dedicated routine, square of byte just use the regular multiplication routine.
        when (resultType) {
            BaseDataType.UBYTE -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)
                asmgen.out("  tay  |  jsr  prog8_math.multiply_bytes")   // result is in A
                return arrayOf(RegisterOrPair.A)
            }
            BaseDataType.UWORD -> {
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
                asmgen.out("  jsr  prog8_math.square")     // result is in AY
                return arrayOf(RegisterOrPair.AY)
            }
            else -> {
                throw AssemblyError("optimized square only for integer types")
            }
        }
    }

    private fun funcDivmod(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, false)
        asmgen.saveRegisterStack(CpuRegister.A, false)
        assignAsmGen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.Y, false)
        asmgen.restoreRegisterStack(CpuRegister.A ,false)
        // math.divmod_ub_asm: -- divide A by Y, result quotient in Y, remainder in A   (unsigned)
        asmgen.out("  jsr  prog8_math.divmod_ub_asm")
        return arrayOf(RegisterOrPair.Y, RegisterOrPair.A)
    }

    private fun funcDivmodW(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        // math.divmod_uw_asm: -- divide two unsigned words (16 bit each) into 16 bit results
        //    input:  P8ZP_SCRATCH_W1 in ZP: 16-bit number, A/Y: 16 bit divisor
        //    output: cx16.r15: 16-bit remainder, A/Y: 16 bit division result
        asmgen.out("  jsr  prog8_math.divmod_uw_asm")
        return arrayOf(RegisterOrPair.AY, RegisterOrPair.R15)
    }

     private fun funcLmh(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
         // low byte returned in A, mid in R15, high (bank) in R14
         val arg = fcall.args[0]
         if(arg is PtIdentifier) {
             val varname = asmgen.asmVariableName(arg)
             asmgen.out("""
                 lda  $varname
                 ldy  $varname+1
                 sty  cx16.r15
                 ldy  $varname+2
                 sty  cx16.r14""")
         } else {
             asmgen.assignExpressionToRegister(arg, RegisterOrPair.R14R15, signed = true)
             asmgen.out("""
                 lda  cx16.r14
                 ldy  cx16.r15
                 sty  cx16.r14
                 ldy  cx16.r14+1
                 sty  cx16.r15""")
         }
         return arrayOf(RegisterOrPair.A, RegisterOrPair.R15, RegisterOrPair.R14)
     }

    private fun funcStringCompare(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        asmgen.assignWordOperandsToAYAndVar(fcall.args[0], fcall.args[1], "P8ZP_SCRATCH_W2")
        asmgen.out("  jsr  prog8_lib.strcmp_mem")   // result in A
        return arrayOf(RegisterOrPair.A)
    }

    private fun funcRsave(): Array<RegisterOrPair> {
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

        return emptyArray()
    }

    private fun funcRrestore(): Array<RegisterOrPair> {
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

        return emptyArray()
    }

    private fun funcCall(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        // note: the routine can return a word value (in AY)
        val constAddr = fcall.args[0].asConstInteger()
        if(constAddr!=null) {
            asmgen.out("  jsr  ${constAddr.toHex()}")
            return arrayOf(RegisterOrPair.AY)
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
            return arrayOf(RegisterOrPair.AY)
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
        // result word assumed to be in AY
        return arrayOf(RegisterOrPair.AY)
    }

    private fun funcCallFar(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
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
                TODO("non-const callfar (jsrfar) yet that's usable in ROM  ${fcall.position}")
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

        // result word assumed to be in AY
        return arrayOf(RegisterOrPair.AY)
    }

    private fun funcCallFar2(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
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
                TODO("non-const callfar2 (jsrfar) yet that's usable in ROM  ${fcall.position}")
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

        // result word assumed to be in AY
        return arrayOf(RegisterOrPair.AY)
    }

    private fun funcCmp(fcall: PtFunctionCall): Array<RegisterOrPair> {
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
        } else if(arg1.type.isWord) {
            if(arg2.type.isWord) {
                if(arg1.type.isSigned) {
                    when (arg2) {
                        is PtIdentifier -> {
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                            asmgen.out("""
                                sec
                                sbc  ${asmgen.asmVariableName(arg2)}
                                tya
                                sbc  ${asmgen.asmVariableName(arg2)}+1""")
                        }
                        is PtBool -> TODO("word compare against bool  ${arg2.position}")
                        is PtNumber -> {
                            asmgen.assignExpressionToRegister(arg1, RegisterOrPair.AY)
                            asmgen.out("""
                                sec
                                sbc  #<${arg2.number.toInt()}
                                tya
                                sbc  #>${arg2.number.toInt()}""")
                        }
                        else -> {
                            asmgen.assignWordOperandsToAYAndVar(arg1, arg2, "P8ZP_SCRATCH_W1")
                            asmgen.out("""
                                sec
                                sbc  P8ZP_SCRATCH_W1
                                tya
                                sbc  P8ZP_SCRATCH_W1+1""")
                        }
                    }
                } else {
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
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        } else if(arg1.type.isLong) {
            if(arg2.type.isLong) {
                if(arg1 is PtIdentifier && arg2 is PtNumber) {
                    val var1 = asmgen.asmVariableName(arg1)
                    val hex = arg2.number.toLongHex()
                    asmgen.out("""
                        sec
                        lda  $var1
                        sbc  #$${hex.substring(6, 8)}
                        lda  $var1+1
                        sbc  #$${hex.substring(4, 6)}
                        lda  $var1+2
                        sbc  #$${hex.substring(2, 4)}
                        lda  $var1+3
                        sbc  #$${hex.take(2)}""")
                } else if(arg1 is PtIdentifier && arg2 is PtIdentifier) {
                    val var1 = asmgen.asmVariableName(arg1)
                    val var2 = asmgen.asmVariableName(arg2)
                    asmgen.out("""
                        sec
                        lda  $var1
                        sbc  $var2
                        lda  $var1+1
                        sbc  $var2+1
                        lda  $var1+2
                        sbc  $var2+2
                        lda  $var1+3
                        sbc  $var2+3""")
                } else {
                    // cmp() doesn't return a value and as such can't be used in an expression, so no need to save the temp registers' original values
                    assignAsmGen.assignExpressionToRegister(arg2, RegisterOrPair.R14R15, true)
                    assignAsmGen.assignExpressionToRegister(arg1, RegisterOrPair.R12R13, true)
                    asmgen.out("""
                        sec
                        lda  cx16.r12
                        sbc  cx16.r14
                        lda  cx16.r12+1
                        sbc  cx16.r14+1
                        lda  cx16.r12+2
                        sbc  cx16.r14+2
                        lda  cx16.r12+3
                        sbc  cx16.r14+3""")
                }
            } else
                throw AssemblyError("args for cmp() should have same dt")
        }

        // there is no result value, the result is in the CPU's status bits
        return emptyArray()
    }

    private fun funcMemory(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val name = (fcall.args[0] as PtString).value
        require(name.all { it.isLetterOrDigit() || it=='_' }) {"memory name should be a valid symbol name ${fcall.position}"}

        val slabname = PtIdentifier("$StMemorySlabBlockName.memory_$name", DataType.UWORD, fcall.position)
        val addressOf = PtAddressOf(DataType.pointer(BaseDataType.UBYTE), false, fcall.position)
        addressOf.add(slabname)
        addressOf.parent = fcall
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, DataType.UWORD, expression = addressOf)
        val target = AsmAssignTarget.fromRegisters(resultReg, false, fcall.position, null, asmgen)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign, fcall.definingISub())
        return arrayOf(resultReg)
    }

    private fun funcStructAlloc(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        // ... don't need to pay attention to args here because struct instance is put together elsewhere we just have to get a pointer to it
        val prefix = if(fcall.args.isEmpty()) "${StStructInstanceBlockName}_bss" else StStructInstanceBlockName
        val labelname = PtIdentifier("$prefix.${SymbolTable.labelnameForStructInstance(fcall)}", fcall.type, fcall.position)
        val addressOf = PtAddressOf(fcall.type, true, fcall.position)
        addressOf.add(labelname)
        addressOf.parent = fcall
        val src = AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, fcall.type, expression = addressOf)
        val target = AsmAssignTarget.fromRegisters(resultReg, false, fcall.position, null, asmgen)
        val assign = AsmAssignment(src, listOf(target), program.memsizer, fcall.position)
        asmgen.translateNormalAssignment(assign, fcall.definingISub())
        return arrayOf(resultReg)
    }

    private fun funcSqrt(fcall: PtFunctionCall, scope: IPtSubroutine?, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        when(fcall.args[0].type.base) {
            BaseDataType.UBYTE -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  ldy  #0 |  jsr  prog8_lib.func_sqrt16_into_A")
                return arrayOf(RegisterOrPair.A)
            }
            BaseDataType.UWORD -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  prog8_lib.func_sqrt16_into_A")
                return arrayOf(RegisterOrPair.A)
            }
            BaseDataType.LONG -> {
                // sqrt_long expects the argument in its "num" parameter
                assignAsmGen.assignExpressionToVariable(fcall.args[0], "prog8_lib.sqrt_long.num", DataType.LONG)
                asmgen.out("  jsr  prog8_lib.sqrt_long")
                return arrayOf(RegisterOrPair.AY)
            }
            BaseDataType.FLOAT -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  floats.func_sqrt_into_FAC1")
                return arrayOf(RegisterOrPair.FAC1)
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcRor2(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")

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
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.LONG -> {
                // a bit strange, rotating a signed type, but it has to do for now
                when(what) {
                    is PtArrayIndexer -> TODO("ror2 long array ${what.position}")
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("""
                            lsr  $variable+3
                            ror  $variable+2
                            ror  $variable+1
                            ror  $variable
                            bcc  +
                            lda  $variable+3
                            ora  #$80
                            sta  $variable+3
+""")
                    }
                    else -> throw AssemblyError("weird node $what")
                }
            }
            else -> throw AssemblyError("weird type")
        }

        return emptyArray()
    }

    private fun funcRor(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")

                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                                asmgen.out("  php")   // save Carry
                                asmgen.assignExpressionToVariable(what.address, "P8ZP_SCRATCH_PTR", DataType.UWORD)
                                asmgen.out("""
                                    ldy  #0
                                    lda  (P8ZP_SCRATCH_PTR),y
                                    plp
                                    ror  a
                                    sta  (P8ZP_SCRATCH_PTR),y""")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable")
                    }
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                    is PtPointerDeref -> {
                        asmgen.out("  php")   // save Carry
                        val (zpPtrVar, offset) = ptrgen.deref(what, false)
                        asmgen.out("""
                            ldy  #$offset+1
                            lda  ($zpPtrVar),y
                            plp
                            ror  a
                            sta  ($zpPtrVar),y
                            dey
                            lda  ($zpPtrVar),y
                            ror  a
                            sta  ($zpPtrVar),y""")
                    }
                    else -> {
                        asmgen.out("  php")   // save Carry
                        asmgen.assignExpressionToVariable(what, "P8ZP_SCRATCH_PTR", DataType.UWORD)
                        asmgen.out("""
                            ldy  #1
                            lda  (P8ZP_SCRATCH_PTR),y
                            plp
                            ror  a
                            sta  (P8ZP_SCRATCH_PTR),y
                            dey
                            lda  (P8ZP_SCRATCH_PTR),y
                            ror  a
                            sta  (P8ZP_SCRATCH_PTR),y""")
                    }
                }
            }
            BaseDataType.LONG -> {
                // a bit strange, rotating a signed type, but it has to do for now
                when(what) {
                    is PtArrayIndexer -> TODO("ror long array ${what.position}")
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  ror  $variable+3 |  ror  $variable+2 |  ror  $variable+1 |  ror  $variable")
                    }
                    else -> {
                        TODO("ror long on expression $what")
                    }
                }
            }
            else -> throw AssemblyError("weird type")
        }

        return emptyArray()
    }

    private fun funcRol2(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.LONG -> {
                // a bit strange, rotating a signed type, but it has to do for now
                when(what) {
                    is PtArrayIndexer -> TODO("rol2 long array ${what.position}")
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("""
                            asl  $variable
                            rol  $variable+1
                            rol  $variable+2
                            rol  $variable+3
                            bcc  +
                            inc  $variable
+""")
                    }
                    else -> throw AssemblyError("weird node $what")
                }
            }
            else -> throw AssemblyError("weird type")
        }

        return emptyArray()
    }

    private fun funcRol(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val what = fcall.args.single()
        when (what.type.base) {
            BaseDataType.UBYTE -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                                asmgen.out("  php")   // save Carry
                                asmgen.assignExpressionToVariable(what.address, "P8ZP_SCRATCH_PTR", DataType.UWORD)
                                asmgen.out("""
                                    ldy  #0
                                    lda  (P8ZP_SCRATCH_PTR),y
                                    plp
                                    rol  a
                                    sta  (P8ZP_SCRATCH_PTR),y""")
                            }
                        }
                    }
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable")
                    }
                    else -> throw AssemblyError("weird node $what")
                }
            }
            BaseDataType.UWORD -> {
                when (what) {
                    is PtArrayIndexer -> {
                        if(!what.index.isSimple()) asmgen.out("  php")   // save Carry
                        asmgen.loadScaledArrayIndexIntoRegister(what, CpuRegister.X)
                        if(!what.index.isSimple()) asmgen.out("  plp")
                        if(what.variable==null)
                            TODO("ptr indexing ${what.position}")
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
                    is PtPointerDeref -> {
                        asmgen.out("  php")   // save Carry
                        val (zpPtrVar, offset) = ptrgen.deref(what, false)
                        asmgen.out("""
                            ldy  #$offset
                            lda  ($zpPtrVar),y
                            plp
                            rol  a
                            sta  ($zpPtrVar),y
                            iny
                            lda  ($zpPtrVar),y
                            rol  a
                            sta  ($zpPtrVar),y""")
                    }
                    else -> {
                        asmgen.out("  php")   // save Carry
                        asmgen.assignExpressionToVariable(what, "P8ZP_SCRATCH_PTR", DataType.UWORD)
                        asmgen.out("""
                            ldy  #0
                            lda  (P8ZP_SCRATCH_PTR),y
                            plp
                            rol  a
                            sta  (P8ZP_SCRATCH_PTR),y
                            iny
                            lda  (P8ZP_SCRATCH_PTR),y
                            rol  a
                            sta  (P8ZP_SCRATCH_PTR),y""")
                    }
                }
            }
            BaseDataType.LONG -> {
                // a bit strange, rotating a signed type, but it has to do for now
                when(what) {
                    is PtArrayIndexer -> TODO("rol long array ${what.position}")
                    is PtIdentifier -> {
                        val variable = asmgen.asmVariableName(what)
                        asmgen.out("  rol  $variable |  rol  $variable+1 |  rol  $variable+2 |  rol  $variable+3")
                    }
                    else -> {
                        TODO("rol long on expression $what")
                    }
                }
            }
            else -> throw AssemblyError("weird type")
        }

        return emptyArray()
    }

    private fun funcSetLsbMsb(fcall: PtFunctionCall, msb: Boolean): Array<RegisterOrPair> {
        val target: AsmAssignTarget
        when(fcall.args[0]) {
            is PtIdentifier -> {
                val msbOffset = if(!msb) "" else if(fcall.args[0].type.isLong) "+3" else "+1"
                val varname = asmgen.asmVariableName(fcall.args[0] as PtIdentifier) + msbOffset
                target = AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, DataType.UBYTE, fcall.definingSub(), fcall.position, variableAsmName = varname)
            }
            is PtArrayIndexer -> {
                val indexer = fcall.args[0] as PtArrayIndexer
                val elementSize: Int
                val msbAdd: Int
                if(indexer.splitWords) {
                    val arrayVariable = indexer.variable ?: TODO("ptr indexing ${indexer.position}")
                    indexer.children[0] = PtIdentifier(arrayVariable.name + if(msb) "_msb" else "_lsb", DataType.arrayFor(BaseDataType.UBYTE, false), arrayVariable.position)
                    indexer.children[0].parent = indexer
                    elementSize = 1
                    msbAdd = 0
                } else {
                    elementSize = indexer.type.size(program.memsizer)
                    msbAdd = if(msb) elementSize-1 else 0
                }

                // double the index because of word array (if not split), add one if msb (if not split)
                val constIndexNum = (indexer.index as? PtNumber)?.number
                if(constIndexNum!=null) {
                    indexer.children[1] = PtNumber(indexer.index.type.base, constIndexNum*elementSize + msbAdd, indexer.position)
                    indexer.children[1].parent = indexer
                } else {
                    val multipliedIndex: PtExpression
                    when (elementSize) {
                        1 -> {
                            multipliedIndex = indexer.index
                        }
                        2 -> {
                            multipliedIndex = PtBinaryExpression("<<", indexer.index.type, indexer.position)
                            multipliedIndex.add(indexer.index)
                            multipliedIndex.add(PtNumber(BaseDataType.UBYTE, 1.0, indexer.position))
                        }
                        4 -> {
                            TODO("setlsb/msb on array of long ${indexer.position}")
                        }
                        else -> throw AssemblyError("weird element size")
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

        return emptyArray()
    }

    private fun funcSgn(fcall: PtFunctionCall, scope: IPtSubroutine?, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        // TODO optimize when the arg is a variable and we can directly check the msb
        when (val dt = fcall.args.single().type.base) {
            BaseDataType.UBYTE -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  prog8_lib.func_sign_ub_into_A")
            }
            BaseDataType.BYTE -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  prog8_lib.func_sign_b_into_A")
            }
            BaseDataType.UWORD -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  prog8_lib.func_sign_uw_into_A")
            }
            BaseDataType.WORD -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  prog8_lib.func_sign_w_into_A")
            }
            BaseDataType.LONG -> {

                translateArguments(fcall, funcname="func_sign_l_into_A", scope)
                asmgen.out("  jsr  prog8_lib.func_sign_l_into_A")
            }
            BaseDataType.FLOAT -> {
                translateArguments(fcall, null, scope)
                asmgen.out("  jsr  floats.func_sign_f_into_A")
            }
            else -> throw AssemblyError("weird type $dt")
        }
        // result in A
        return arrayOf(RegisterOrPair.A)
    }

    private fun funcAbs(fcall: PtFunctionCall, scope: IPtSubroutine?, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        translateArguments(fcall, null, scope)
        val dt = fcall.args.single().type.base
        when (dt) {
            BaseDataType.BYTE -> {
                asmgen.out("  jsr  prog8_lib.abs_b_into_A")
                return arrayOf(RegisterOrPair.A)
            }
            BaseDataType.WORD -> {
                asmgen.out("  jsr  prog8_lib.abs_w_into_AY")
                return arrayOf(RegisterOrPair.AY)
            }
            BaseDataType.LONG -> {
                asmgen.out("  jsr  prog8_lib.abs_l_into_R14R15")
                return arrayOf(RegisterOrPair.R14R15)
            }
            BaseDataType.FLOAT -> {
                asmgen.out("  jsr  floats.func_abs_f_into_FAC1")
                return arrayOf(RegisterOrPair.FAC1)
            }
            BaseDataType.UBYTE, BaseDataType.UWORD -> TODO("abs of unsigned value ${fcall.position}")
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcPokemon(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val memread = PtMemoryByte(fcall.position)
        memread.add(fcall.args[0])
        memread.parent = fcall
        asmgen.assignExpressionToRegister(memread, RegisterOrPair.A)
        if(fcall.args[1] is PtNumber || fcall.args[1] is PtIdentifier)
            asmgen.out("  tay")
        else
            asmgen.out("  pha")
        val memtarget = AsmAssignTarget(TargetStorageKind.MEMORY, asmgen, DataType.UBYTE, fcall.definingISub(), fcall.position, memory=memread)
        asmgen.assignExpressionTo(fcall.args[1], memtarget)
        if(fcall.args[1] is PtNumber || fcall.args[1] is PtIdentifier)
            asmgen.out("  tya")
        else
            asmgen.out("  pla")

        return arrayOf(RegisterOrPair.A)
    }

    private fun funcPokeF(fcall: PtFunctionCall): Array<RegisterOrPair> {
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

        return emptyArray()
    }

    private fun funcPokeBool(fcall: PtFunctionCall): Array<RegisterOrPair> {
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr")
                return emptyArray()
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                    asmgen.storeIndirectByteReg(CpuRegister.A, varname, 0u, false, false)
                    return emptyArray()
                }
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
                if(pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do (ZP),Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.saveRegisterStack(CpuRegister.Y, false)
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                    asmgen.out("  sta  ($varname),y")
                    return emptyArray()
                } else if(addressOfIdentifier!=null && (addressOfIdentifier.type.isByteOrBool || addressOfIdentifier.type.isBoolArray || addressOfIdentifier.type.isByteArray)) {
                    val varname = asmgen.asmVariableName(addressOfIdentifier)
                    if(result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                        asmgen.out("  sta  $varname+$offset")
                        return emptyArray()
                    } else if(result.second is PtIdentifier) {
                        val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)
                        asmgen.out("  ldx  $offsetname |  sta  $varname,x")
                        return emptyArray()
                    }
                } else if(addrExpr.operator=="+" && addrExpr.left is PtIdentifier) {
                    asmgen.assignExpressionToRegister(addrExpr.right, RegisterOrPair.AY, false)
                    val ptrName = asmgen.asmVariableName(addrExpr.left as PtIdentifier)
                    asmgen.out("""
                        clc
                        adc  $ptrName
                        sta  P8ZP_SCRATCH_W2
                        tya
                        adc  $ptrName+1
                        sta  P8ZP_SCRATCH_W2+1""")
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)      // TODO *could* overwrite SCRATCH_W2 if it's a compliated expression...
                    asmgen.storeIndirectByteReg(CpuRegister.A, "P8ZP_SCRATCH_W2", 0u, false, false)
                    return emptyArray()
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

        return emptyArray()
    }

    private fun funcPokeW(fcall: PtFunctionCall): Array<RegisterOrPair> {
        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                val addr = addrExpr.number.toHex()
                asmgen.out("  sta  $addr |  sty  ${addr}+1")
                return emptyArray()
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    // pointervar is already in the zero page, no need to copy
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AX)
                    asmgen.storeIndirectWordReg(RegisterOrPair.AX, varname, 0u)
                    return emptyArray()
                }
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
                if(pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do (ZP),Y indexing
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
                    return emptyArray()
                } else if(addressOfIdentifier!=null && (addressOfIdentifier.type.isWord || addressOfIdentifier.type.isPointer || addressOfIdentifier.type.isByteArray)) {
                    val varname = asmgen.asmVariableName(addressOfIdentifier)
                    if(result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                        asmgen.out("  sta  $varname+$offset |  sty  $varname+${offset + 1}")
                        return emptyArray()
                    } else if(result.second is PtIdentifier) {
                        val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                        asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)
                        asmgen.out("""
                            ldx  $offsetname
                            sta  $varname,x
                            tya
                            sta  $varname+1,x""")
                        return emptyArray()
                    }
                } else if(addrExpr.operator=="+" && addrExpr.left is PtIdentifier) {
                    asmgen.assignExpressionToRegister(addrExpr.right, RegisterOrPair.AY, false)
                    val ptrName = asmgen.asmVariableName(addrExpr.left as PtIdentifier)
                    asmgen.out("""
                        clc
                        adc  $ptrName
                        sta  P8ZP_SCRATCH_W2
                        tya
                        adc  $ptrName+1
                        sta  P8ZP_SCRATCH_W2+1""")
                    asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.AY)      // TODO *could* overwrite SCRATCH_W2 if it's a compliated expression...
                    asmgen.out("  jsr  prog8_lib.func_pokew_scratchW2")
                    //asmgen.storeIndirectWordReg(RegisterOrPair.AY, "P8ZP_SCRATCH_W2", 0u)
                    return emptyArray()
                }
            }
            else -> { /* fall through */ }
        }

        // fall through method:
        asmgen.assignWordOperandsToAYAndVar(fcall.args[1], fcall.args[0], "P8ZP_SCRATCH_W1")
        asmgen.out("  jsr  prog8_lib.func_pokew")
        return emptyArray()
    }

    private fun funcPokeL(fcall: PtFunctionCall): Array<RegisterOrPair> {
        val (targetArg, valueArg) = fcall.args
        if(targetArg is PtNumber) {
            val address = targetArg.number.toInt()
            if(valueArg is PtNumber) {
                val hex = valueArg.number.toLongHex()
                asmgen.out("""
                    lda  #$${hex.substring(6,8)}
                    sta  $address
                    lda  #$${hex.substring(4, 6)}
                    sta  $address+1
                    lda  #$${hex.substring(2, 4)}
                    sta  $address+2
                    lda  #$${hex.take(2)}
                    sta  $address+3""")
                return emptyArray()
            } else if(valueArg is PtIdentifier) {
                val varname = asmgen.asmVariableName(valueArg)
                asmgen.out("""
                    lda  $varname
                    sta  $address
                    lda  $varname+1
                    sta  $address+1
                    lda  $varname+2
                    sta  $address+2
                    lda  $varname+3
                    sta  $address+3""")
                return emptyArray()
            }
        }

        else if(targetArg is PtIdentifier) {
            val ptrname = asmgen.asmVariableName(targetArg)
            if(asmgen.isZpVar(targetArg)) {
                if(valueArg is PtNumber) {
                    val hex = valueArg.number.toLongHex()
                    asmgen.out("""
                        ldy  #0
                        lda  #$${hex.substring(6,8)}
                        sta  ($ptrname),y
                        iny
                        lda  #$${hex.substring(4, 6)}
                        sta  ($ptrname),y
                        iny
                        lda  #$${hex.substring(2, 4)}
                        sta  ($ptrname),y
                        iny
                        lda  #$${hex.take(2)}
                        sta  ($ptrname),y""")
                    return emptyArray()
                } else if(valueArg is PtIdentifier) {
                    val varname = asmgen.asmVariableName(valueArg)
                    asmgen.out("""
                        lda  $varname
                        ldy  #0
                        sta  ($ptrname),y
                        iny
                        lda  $varname+1
                        sta  ($ptrname),y
                        iny
                        lda  $varname+2
                        sta  ($ptrname),y
                        iny
                        lda  $varname+3
                        sta  ($ptrname),y""")
                    return emptyArray()
                }
            } else {
                if(valueArg is PtNumber) {
                    val hex = valueArg.number.toLongHex()
                    asmgen.out("""
                        lda  $ptrname
                        ldy  $ptrname+1
                        sta  P8ZP_SCRATCH_PTR
                        sty  P8ZP_SCRATCH_PTR+1
                        ldy  #0
                        lda  #$${hex.substring(6,8)}
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  #$${hex.substring(4, 6)}
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  #$${hex.substring(2, 4)}
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  #$${hex.take(2)}
                        sta  (P8ZP_SCRATCH_PTR),y""")
                    return emptyArray()
                } else if(valueArg is PtIdentifier) {
                    val varname = asmgen.asmVariableName(valueArg)
                    asmgen.out("""
                        lda  $ptrname
                        ldy  $ptrname+1
                        sta  P8ZP_SCRATCH_PTR
                        sty  P8ZP_SCRATCH_PTR+1
                        lda  $varname
                        ldy  #0
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  $varname+1
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  $varname+2
                        sta  (P8ZP_SCRATCH_PTR),y
                        iny
                        lda  $varname+3
                        sta  (P8ZP_SCRATCH_PTR),y""")
                    return emptyArray()
                }
            }
        }

        else if(targetArg is PtBinaryExpression) {
            val result = asmgen.pointerViaIndexRegisterPossible(targetArg)
            val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
            if(addressOfIdentifier!=null && (addressOfIdentifier.type.isLong || addressOfIdentifier.type.isByteArray)) {
                val varname = asmgen.asmVariableName(addressOfIdentifier)
                if(result.second is PtNumber) {
                    val offset = (result.second as PtNumber).number.toInt()
                    when(valueArg) {
                        is PtNumber -> {
                            val hex = valueArg.number.toLongHex()
                            asmgen.out("""
                                lda  #$${hex.substring(6,8)}
                                sta  $varname+$offset
                                lda  #$${hex.substring(4, 6)}
                                sta  $varname+${offset+1}
                                lda  #$${hex.substring(2, 4)}
                                sta  $varname+${offset + 2}
                                lda  #$${hex.take(2)}
                                sta  $varname+${offset + 3}""")
                        }
                        is PtIdentifier -> {
                            val srcvar = asmgen.asmVariableName(valueArg)
                            asmgen.out("""
                                lda  $srcvar
                                sta  $varname+$offset
                                lda  $srcvar+1
                                sta  $varname+${offset+1}
                                lda  $srcvar+2
                                sta  $varname+${offset + 2}
                                lda  $srcvar+3
                                sta  $varname+${offset + 3}""")
                        }
                        else -> {
                            asmgen.assignExpressionToRegister(valueArg, RegisterOrPair.R14R15, signed=valueArg.type.isSigned)
                            asmgen.out("""
                                lda  cx16.r14
                                sta  $varname+$offset
                                lda  cx16.r14+1
                                sta  $varname+${offset+1}
                                lda  cx16.r14+2
                                sta  $varname+${offset+2}
                                lda  cx16.r14+3
                                sta  $varname+${offset+3}""")
                        }
                    }
                    return emptyArray()
                } else if(result.second is PtIdentifier) {
                    val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                    when(valueArg) {
                        is PtNumber -> {
                            val hex = valueArg.number.toLongHex()
                            asmgen.out("""
                                ldx  $offsetname
                                lda  #$${hex.substring(6,8)}
                                sta  $varname,x
                                lda  #$${hex.substring(4, 6)}
                                sta  $varname+1,x
                                lda  #$${hex.substring(2, 4)}
                                sta  $varname+2,x
                                lda  #$${hex.take(2)}
                                sta  $varname+3,x""")
                        }
                        is PtIdentifier -> {
                            val srcvar = asmgen.asmVariableName(valueArg)
                            asmgen.out("""
                                ldx  $offsetname
                                lda  $srcvar
                                sta  $varname,x
                                lda  $srcvar+1
                                sta  $varname+$1,x
                                lda  $srcvar+2
                                sta  $varname+2,x
                                lda  $srcvar+3
                                sta  $varname+3,x""")
                        }
                        else -> {
                            asmgen.assignExpressionToRegister(valueArg, RegisterOrPair.R14R15, signed=valueArg.type.isSigned)
                            asmgen.out("""
                                ldx  $offsetname
                                lda  cx16.r14
                                sta  $varname,x
                                lda  cx16.r14+1
                                sta  $varname+1,x
                                lda  cx16.r14+2
                                sta  $varname+2,x
                                lda  cx16.r14+3
                                sta  $varname+3,x""")
                        }
                    }
                    return emptyArray()
                }
            }
        }

        asmgen.assignExpressionToRegister(targetArg, RegisterOrPair.AY)
        asmgen.saveRegisterStack(CpuRegister.A, false)
        asmgen.saveRegisterStack(CpuRegister.Y, false)
        // it's a statement so no need to preserve R14:R15
        asmgen.assignExpressionToRegister(valueArg, RegisterOrPair.R14R15, true)
        asmgen.restoreRegisterStack(CpuRegister.Y, false)
        asmgen.restoreRegisterStack(CpuRegister.A, false)
        asmgen.out("  jsr  prog8_lib.func_pokel")
        return emptyArray()
    }

    private fun funcPeekF(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
        asmgen.out("  jsr  floats.MOVFM")       // result in FAC1
        return arrayOf(RegisterOrPair.FAC1)
    }

    private fun funcPeekBool(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
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
                val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
                if(pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do (ZP),Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    asmgen.out("  lda  ($varname),y")
                } else if(addressOfIdentifier!=null && (addressOfIdentifier.type.isByteOrBool || addressOfIdentifier.type.isBoolArray || addressOfIdentifier.type.isByteArray)) {
                    val varname = asmgen.asmVariableName(addressOfIdentifier)
                    if(result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        asmgen.out("  lda  $varname+$offset")
                    } else if(result.second is PtIdentifier) {
                        val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                        asmgen.out("  ldx  $offsetname |  lda  $varname,x")
                    } else fallback()
                } else if(addrExpr.operator=="+" && addrExpr.left is PtIdentifier) {
                    readValueFromPointerPlusOffset(addrExpr.left as PtIdentifier, addrExpr.right, BaseDataType.BOOL)
                } else fallback()
            }
            else -> fallback()
        }

        return arrayOf(RegisterOrPair.A)
    }

    private fun funcPeekW(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {

        fun fallback() {
            asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
            asmgen.out("  jsr  prog8_lib.func_peekw")
        }

        when(val addrExpr = fcall.args[0]) {
            is PtNumber -> {
                val addr = addrExpr.number.toHex()
                when(resultReg) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $addr |  ldx  ${addr}+1")
                    RegisterOrPair.AY -> asmgen.out("  lda  $addr |  ldy  ${addr}+1")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $addr |  ldy  ${addr}+1")
                    else -> throw AssemblyError("peekw must have result in AX, AY or XY ${fcall.position}")
                }
                return arrayOf(resultReg)
            }
            is PtIdentifier -> {
                val varname = asmgen.asmVariableName(addrExpr)
                if(asmgen.isZpVar(addrExpr)) {
                    if(resultReg in arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                        asmgen.loadIndirectWordIntoRegisters(varname, 0u, resultReg)
                        return arrayOf(resultReg)
                    } else {
                        asmgen.loadIndirectWordAY(varname, 0u)
                    }
                }
                else
                    fallback()
            }
            is PtBinaryExpression -> {
                val result = asmgen.pointerViaIndexRegisterPossible(addrExpr)
                val pointer = result?.first as? PtIdentifier
                val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
                if(pointer!=null && asmgen.isZpVar(pointer)) {
                    // can do (ZP),Y indexing
                    val varname = asmgen.asmVariableName(pointer)
                    asmgen.assignExpressionToRegister(result.second, RegisterOrPair.Y)
                    when(resultReg) {
                        RegisterOrPair.AX -> {
                            asmgen.out("""
                                iny
                                lda  ($varname),y
                                tax
                                dey
                                lda  ($varname),y""")
                            return arrayOf(RegisterOrPair.AX)
                        }
                        RegisterOrPair.AY -> {
                            asmgen.out("""
                                lda  ($varname),y
                                tax
                                iny
                                lda  ($varname),y
                                tay
                                txa""")
                            return arrayOf(RegisterOrPair.AY)
                        }
                        RegisterOrPair.XY -> TODO("peekw into xy ${fcall.position}")
                        in Cx16VirtualRegisters -> {
                            asmgen.out("""
                                iny
                                lda  ($varname),y
                                tax
                                dey
                                lda  ($varname),y""")
                            val regname = resultReg.asScopedNameVirtualReg(null).joinToString(".")
                            asmgen.out(" sta  $regname |  stx  $regname+1")
                            return arrayOf(resultReg)
                        }
                        else -> throw AssemblyError("invalid register for indirect load word $resultReg  ${fcall.position}")
                    }
                } else if(addressOfIdentifier!=null && (addressOfIdentifier.type.isWord || addressOfIdentifier.type.isPointer || addressOfIdentifier.type.isByteArray)) {
                    val varname = asmgen.asmVariableName(addressOfIdentifier)
                    if(result.second is PtNumber) {
                        val offset = (result.second as PtNumber).number.toInt()
                        when(resultReg) {
                            RegisterOrPair.AX -> asmgen.out("  lda  $varname+$offset |  ldx  $varname+${offset + 1}")
                            RegisterOrPair.AY -> asmgen.out("  lda  $varname+$offset |  ldy  $varname+${offset + 1}")
                            RegisterOrPair.XY -> asmgen.out("  ldx  $varname+$offset |  ldy  $varname+${offset + 1}")
                            else -> throw AssemblyError("peekw must have result in AX, AY or XY ${fcall.position}")
                        }
                    } else if(result.second is PtIdentifier) {
                        val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                        when(resultReg) {
                            RegisterOrPair.AX -> {
                                asmgen.out("""
                                    ldy  $offsetname
                                    lda  $varname+1,y
                                    tax
                                    lda  $varname,y""")
                                return arrayOf(RegisterOrPair.AX)
                            }
                            RegisterOrPair.AY -> {
                                asmgen.out("""
                                    ldx  $offsetname
                                    lda  $varname+1,x
                                    tay
                                    lda  $varname,x""")
                                return arrayOf(RegisterOrPair.AY)
                            }
                            RegisterOrPair.XY -> {
                                asmgen.out("""
                                    ldx  $offsetname
                                    lda  $varname+1,x
                                    tay
                                    lda  $varname,x
                                    tax""")
                                return arrayOf(RegisterOrPair.XY)
                            }
                            else -> throw AssemblyError("peekw must have result in AX, AY or XY ${fcall.position}")
                        }
                        // TODO load directly into other registers
                    } else fallback()
                } else if(addrExpr.operator=="+" && addrExpr.left is PtIdentifier) {
                    readValueFromPointerPlusOffset(addrExpr.left as PtIdentifier, addrExpr.right, BaseDataType.UWORD)
                } else fallback()
            }
            else -> fallback()
        }

        return arrayOf(RegisterOrPair.AY)
    }

    private fun readValueFromPointerPlusOffset(ptr: PtIdentifier, offset: PtExpression, dt: BaseDataType) {
        val varname = asmgen.asmVariableName(ptr)
        asmgen.assignExpressionToRegister(offset, RegisterOrPair.AY)
        asmgen.out("""
            clc
            adc  $varname
            sta  P8ZP_SCRATCH_W1
            tya
            adc  $varname+1
            sta  P8ZP_SCRATCH_W1+1""")
        if (dt.isByteOrBool) {
            if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
                asmgen.out("  lda  (P8ZP_SCRATCH_W1)")
            } else {
                asmgen.out("  ldy  #0 |  lda  (P8ZP_SCRATCH_W1),y")
            }
        } else if(dt.isWord) {
            asmgen.out("  jsr  prog8_lib.func_peekw.from_scratchW1")
        } else throw AssemblyError("unsupported type for peek $dt")
    }

    private fun funcPeekL(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val addressArg = fcall.args[0]
        val result = asmgen.pointerViaIndexRegisterPossible(addressArg)
        val addressOfIdentifier = (result?.first as? PtAddressOf)?.identifier
        if(addressOfIdentifier!=null && (addressOfIdentifier.type.isLong || addressOfIdentifier.type.isByteArray)) {
            val varname = asmgen.asmVariableName(addressOfIdentifier)
            if (result.second is PtNumber) {
                val offset = (result.second as PtNumber).number.toInt()
                asmgen.out("""
                    lda  $varname+$offset
                    sta  cx16.r14
                    lda  $varname+${offset + 1}
                    sta  cx16.r14+1
                    lda  $varname+${offset + 2}
                    sta  cx16.r14+2
                    lda  $varname+${offset + 3}
                    sta  cx16.r14+3""")
            } else if(result.second is PtIdentifier) {
                val offsetname = asmgen.asmVariableName(result.second as PtIdentifier)
                asmgen.out("""
                    ldx  $offsetname
                    lda  $varname,x
                    sta  cx16.r14
                    lda  $varname+1,x
                    sta  cx16.r14+1
                    lda  $varname+2,x
                    sta  cx16.r14+2
                    lda  $varname+3,x
                    sta  cx16.r14+3""")
            }
        }

        asmgen.assignExpressionToRegister(addressArg, RegisterOrPair.AY)
        asmgen.out("  jsr  prog8_lib.func_peekl")   // result in R14:R15

        return arrayOf(RegisterOrPair.R14R15)
    }

    private fun funcClamp(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val signed = fcall.type.isSigned
        when {
            fcall.type.isByte -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W1+1", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")  // result in A
                return arrayOf(RegisterOrPair.A)
            }
            fcall.type.isWord -> {
                assignAsmGen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W1", fcall.args[1].type)  // minimum
                assignAsmGen.assignExpressionToVariable(fcall.args[2], "P8ZP_SCRATCH_W2", fcall.args[2].type)  // maximum
                assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY, signed)    // value
                asmgen.out("  jsr  prog8_lib.func_clamp_${fcall.type.toString().lowercase()}")  // result in AY
                return arrayOf(RegisterOrPair.AY)
            }
            fcall.type.isLong -> {
                TODO("clamp long into R14:R15  ${fcall.position}")
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun funcMin(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val signed = fcall.type.isSigned
        when {
            fcall.type.isByte -> {
                asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_B1", fcall.type)     // right
                asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.A)          // left
                asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                if(signed) asmgen.out("  bmi  +") else asmgen.out("  bcc  +")
                asmgen.out("""
                    lda  P8ZP_SCRATCH_B1
    +""")   // result in A
                return arrayOf(RegisterOrPair.A)
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
                // result in AY
                return arrayOf(RegisterOrPair.AY)
            }
            fcall.type.isLong -> {
                TODO("min long into R14:R15 ${fcall.position}")
            }
            else -> {
                throw AssemblyError("min float not supported")
            }
        }
    }

    private fun funcMax(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val signed = fcall.type.isSigned
        when {
            fcall.type.isByte -> {
                asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_B1", fcall.type)     // left
                asmgen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.A)          // right
                asmgen.out("  cmp  P8ZP_SCRATCH_B1")
                if (signed) asmgen.out("  bpl  +") else asmgen.out("  bcs  +")
                asmgen.out("""
                    lda  P8ZP_SCRATCH_B1
+"""
                ) // result in A
                return arrayOf(RegisterOrPair.A)
            }
            fcall.type.isWord -> {
                asmgen.assignExpressionToVariable(fcall.args[0], "P8ZP_SCRATCH_W1", fcall.type)     // left
                asmgen.assignExpressionToVariable(fcall.args[1], "P8ZP_SCRATCH_W2", fcall.type)     // right
                if (signed) {
                    asmgen.out("""
                        lda  P8ZP_SCRATCH_W1
                        ldy  P8ZP_SCRATCH_W1+1
                        cmp  P8ZP_SCRATCH_W2
                        tya
                        sbc  P8ZP_SCRATCH_W2+1
                        bvc  +
                        eor  #$80
+                       bmi  +
                        lda  P8ZP_SCRATCH_W1                   
                        ldy  P8ZP_SCRATCH_W1+1
                        jmp  ++
+                       lda  P8ZP_SCRATCH_W2
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
+                       lda  P8ZP_SCRATCH_W1
                        ldy  P8ZP_SCRATCH_W1+1
                        jmp  ++
+                       lda  P8ZP_SCRATCH_W2
                        ldy  P8ZP_SCRATCH_W2+1
+""")
                }
                // result in AY
                return arrayOf(RegisterOrPair.AY)
            }
            fcall.type.isLong -> {
                TODO("max long into R14:R15 ${fcall.position}")
            }
            else -> {
                throw AssemblyError("max float not supported")
            }
        }
    }

    private fun funcMklong(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        fun isArgRegister(expression: PtExpression, reg: RegisterOrPair): Boolean {
            if(expression !is PtIdentifier)
                return false
            return expression.name.startsWith("cx16.${reg.name.lowercase()}")
        }

        val (r1, r2) = when(resultReg) {
            RegisterOrPair.R0R1 -> RegisterOrPair.R0 to RegisterOrPair.R1
            RegisterOrPair.R2R3 -> RegisterOrPair.R2 to RegisterOrPair.R3
            RegisterOrPair.R4R5 -> RegisterOrPair.R4 to RegisterOrPair.R5
            RegisterOrPair.R6R7 -> RegisterOrPair.R6 to RegisterOrPair.R7
            RegisterOrPair.R8R9 -> RegisterOrPair.R8 to RegisterOrPair.R9
            RegisterOrPair.R10R11 -> RegisterOrPair.R10 to RegisterOrPair.R11
            RegisterOrPair.R12R13 -> RegisterOrPair.R12 to RegisterOrPair.R13
            RegisterOrPair.R14R15 -> RegisterOrPair.R14 to RegisterOrPair.R15
            else -> null to null
        }

        if(fcall.args.size==2) {
            // mklong2(msw, lsw)
            if(isArgRegister(fcall.args[0], RegisterOrPair.R14) || isArgRegister(fcall.args[0], RegisterOrPair.R15) ||
                isArgRegister(fcall.args[1], RegisterOrPair.R14) || isArgRegister(fcall.args[1], RegisterOrPair.R15)) {
                error("cannot use R14 and/or R15 as arguments for mklong2 because the result should go into R0:R1 ${fcall.position}")
            } else {
                if(r1==null || r2==null) {
                    assignAsmGen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.R15, signed = false)
                    assignAsmGen.assignExpressionToRegister(fcall.args[1], RegisterOrPair.R14, signed = false)
                } else {
                    assignAsmGen.assignExpressionToRegister(fcall.args[0], r2, signed = false)
                    assignAsmGen.assignExpressionToRegister(fcall.args[1], r1, signed = false)
                    return arrayOf(resultReg)
                }
            }
        } else {
            // mklong(msb, b2, b1, lsb)
            if(isArgRegister(fcall.args[0], RegisterOrPair.R14) || isArgRegister(fcall.args[0], RegisterOrPair.R15) ||
                isArgRegister(fcall.args[1], RegisterOrPair.R14) || isArgRegister(fcall.args[1], RegisterOrPair.R15) ||
                isArgRegister(fcall.args[2], RegisterOrPair.R14) || isArgRegister(fcall.args[2], RegisterOrPair.R15) ||
                isArgRegister(fcall.args[3], RegisterOrPair.R14) || isArgRegister(fcall.args[3], RegisterOrPair.R15)) {
                error("cannot use R14 and/or R15 as arguments for mklong because the result should go into R14:R15 ${fcall.position}")
            } else {
                if(r1==null || r2==null) {
                    assignAsmGen.assignExpressionToVariable(fcall.args[0], "cx16.r15H", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[1], "cx16.r15L", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[2], "cx16.r14H", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[3], "cx16.r14L", DataType.UBYTE)
                } else {
                    val r1name = "cx16.${r1.name.lowercase()}"
                    val r2name = "cx16.${r2.name.lowercase()}"
                    assignAsmGen.assignExpressionToVariable(fcall.args[0], "${r2name}H", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[1], "${r2name}L", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[2], "${r1name}H", DataType.UBYTE)
                    assignAsmGen.assignExpressionToVariable(fcall.args[3], "${r1name}L", DataType.UBYTE)
                    return arrayOf(resultReg)
                }
            }
        }

        return arrayOf(RegisterOrPair.R14R15)
    }

    private fun funcMkword(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {

        if(resultReg in Cx16VirtualRegisters) {
            val regname = "cx16.${resultReg.name.lowercase()}"
            asmgen.assignExpressionToVariable(fcall.args[0], "$regname+1", DataType.UBYTE)      // msb
            asmgen.assignExpressionToVariable(fcall.args[1], regname, DataType.UBYTE)      // lsb
            return arrayOf(resultReg)
        }

        var needAsaveForArg0 = asmgen.needAsaveForExpr(fcall.args[0])
        if(!needAsaveForArg0) {
            val mr0 = fcall.args[0] as? PtMemoryByte
            val mr1 = fcall.args[1] as? PtMemoryByte
            if (mr0 != null)
                needAsaveForArg0 =  mr0.address !is PtNumber
            if (mr1 != null)
                needAsaveForArg0 = needAsaveForArg0 or (mr1.address !is PtNumber)
        }

        if(resultReg==RegisterOrPair.AX) {
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
            return arrayOf(RegisterOrPair.AX)
        }

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

        return arrayOf(RegisterOrPair.AY)
    }

    private fun funcMsbLong(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val arg = fcall.args.single()
        if (!arg.type.isLong)
            throw AssemblyError("msb__long requires long argument")
        if (arg is PtNumber)
            throw AssemblyError("msb(const) should have been const-folded away")

        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultReg) {
                RegisterOrPair.A -> asmgen.out("  lda  $sourceName+3")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName+3")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName+3")
                else -> throw AssemblyError("invalid register for msb long: $resultReg")
            }
        } else if(arg is PtArrayIndexer) {
            TODO("msb of long array element ${fcall.position}")
        } else {
            asmgen.assignExpressionToRegister(arg, RegisterOrPair.R14R15, arg.type.isSigned)
            when(resultReg) {
                RegisterOrPair.A -> asmgen.out("  lda  cx16.r14+3")
                RegisterOrPair.X -> asmgen.out("  ldx  cx16.r14+3")
                RegisterOrPair.Y -> asmgen.out("  ldy  cx16.r14+3")
                else -> throw AssemblyError("invalid register for msb long: $resultReg")
            }
        }

        return arrayOf(resultReg)
    }

    private fun funcMsb(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val arg = fcall.args.single()
        if (!arg.type.isWord)
            throw AssemblyError("msb requires word argument")
        if (arg is PtNumber)
            throw AssemblyError("msb(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultReg) {
                RegisterOrPair.A -> asmgen.out("  lda  $sourceName+1")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName+1")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName+1")
                else -> throw AssemblyError("invalid register for msb: $resultReg")
            }
        } else {
            if(arg is PtArrayIndexer) {
                // just read the msb byte out of the word array
                if(arg.splitWords) {
                    if(arg.variable==null)
                        TODO("ptr indexing ${arg.position}")
                    val arrayVar = asmgen.asmVariableName(arg.variable!!)+"_msb"
                    when(resultReg) {
                        RegisterOrPair.A -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVar,y")
                        }
                        RegisterOrPair.X -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  ldx  $arrayVar,y")
                        }
                        RegisterOrPair.Y -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                            asmgen.out("  ldy  $arrayVar,x")
                        }
                        else -> throw AssemblyError("invalid register for msb: $resultReg")
                    }
                } else {
                    if(arg.variable==null)
                        TODO("ptr indexing ${arg.position}")
                    val arrayVar = asmgen.asmVariableName(arg.variable!!)
                    asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                    when(resultReg) {
                        RegisterOrPair.A -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVar+1,y")
                        }
                        RegisterOrPair.X -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                            asmgen.out("  ldx  $arrayVar+1,y")
                        }
                        RegisterOrPair.Y -> {
                            asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                            asmgen.out("  ldy  $arrayVar+1,x")
                        }
                        else -> throw AssemblyError("invalid register for msb: $resultReg")
                    }
                }
            } else {
                asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                asmgen.out("  tya")
            }
        }

        return arrayOf(resultReg)
    }

    private fun funcLsb(fcall: PtFunctionCall, fromLong: Boolean, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val arg = fcall.args.single()
        if(fromLong) {
            if (!arg.type.isLong) throw AssemblyError("lsb__long requires long")
        } else {
            if (!arg.type.isWord) throw AssemblyError("lsb requires word")
        }
        if (arg is PtNumber)
            throw AssemblyError("lsb(const) should have been const-folded away")

        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultReg) {
                RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                else -> throw AssemblyError("invalid register for lsb: $resultReg")
            }
        } else {
            if(fromLong) {
                asmgen.assignExpressionToRegister(arg, RegisterOrPair.R14R15, arg.type.isSigned)
                when(resultReg) {
                    RegisterOrPair.A -> asmgen.out("  lda  cx16.r14")
                    RegisterOrPair.X -> asmgen.out("  ldx  cx16.r14")
                    RegisterOrPair.Y -> asmgen.out("  ldy  cx16.r14")
                    else -> throw AssemblyError("invalid register for lsb: $resultReg")
                }
            } else if(arg is PtArrayIndexer) {
                // just read the lsb byte out of the word array
                if(arg.variable==null)
                    TODO("ptr indexing ${arg.position}")

                val arrayVar = if(arg.splitWords) asmgen.asmVariableName(arg.variable!!)+"_lsb" else asmgen.asmVariableName(arg.variable!!)
                asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                when(resultReg) {
                    RegisterOrPair.A -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                        asmgen.out("  lda  $arrayVar,y")
                    }
                    RegisterOrPair.X -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.Y)
                        asmgen.out("  ldx  $arrayVar,y")
                    }
                    RegisterOrPair.Y -> {
                        asmgen.loadScaledArrayIndexIntoRegister(arg, CpuRegister.X)
                        asmgen.out("  ldy  $arrayVar,x")
                    }
                    else -> throw AssemblyError("invalid register for lsb: $resultReg")
                }
            } else {
                // NOTE: we rely on the fact that the above assignment to AY, assigns the Lsb to A as the last instruction.
                //       this is required because the compiler assumes the status bits are set according to what A is (lsb)
                //       and will not generate another cmp when lsb() is directly used inside a comparison expression.
                when(resultReg) {
                    RegisterOrPair.A -> asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                    RegisterOrPair.X -> asmgen.assignExpressionToRegister(arg, RegisterOrPair.XY)
                    RegisterOrPair.Y -> {
                        asmgen.assignExpressionToRegister(arg, RegisterOrPair.AY)
                        asmgen.out("  tay")
                    }
                    else -> throw AssemblyError("invalid register for lsb: $resultReg")
                }
            }
        }

        return arrayOf(resultReg)
    }

    private fun funcMsw(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val arg = fcall.args.single()
        if (!arg.type.isLong)
            throw AssemblyError("msw requires long argument")
        if (arg is PtNumber)
            throw AssemblyError("msw(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultReg) {
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName+2 |  ldy  $sourceName+3")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName+2 |  ldx  $sourceName+3")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName+2 |  ldy  $sourceName+3")
                else -> throw AssemblyError("invalid register for msw: $resultReg")
            }
        } else {
            if(arg.type.isLong) {
                asmgen.assignExpressionToRegister(arg, RegisterOrPair.R14R15, true)
                when(resultReg) {
                    RegisterOrPair.AY -> asmgen.out("  lda  cx16.r14+2 |  ldy  cx16.r14+3")
                    RegisterOrPair.AX -> asmgen.out("  lda  cx16.r14+2 |  ldx  cx16.r14+3")
                    RegisterOrPair.XY -> asmgen.out("  ldx  cx16.r14+2 |  ldy  cx16.r14+3")
                    else -> throw AssemblyError("invalid register for msw: $resultReg")
                }
            } else {
                asmgen.assignExpressionToRegister(arg, resultReg, true)
            }
        }
        return arrayOf(resultReg)
    }

    private fun funcLsw(fcall: PtFunctionCall, resultReg: RegisterOrPair): Array<RegisterOrPair> {
        val arg = fcall.args.single()
        if (!arg.type.isLong)
            throw AssemblyError("lsw requires long argument")
        if (arg is PtNumber)
            throw AssemblyError("lsw(const) should have been const-folded away")
        if (arg is PtIdentifier) {
            val sourceName = asmgen.asmVariableName(arg)
            when(resultReg) {
                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  $sourceName+1")
                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  $sourceName+1")
                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  $sourceName+1")
                else -> throw AssemblyError("invalid register for lsw: $resultReg")
            }
        } else {
            if(arg.type.isLong) {
                asmgen.assignExpressionToRegister(arg, RegisterOrPair.R14R15, true)
                when(resultReg) {
                    RegisterOrPair.AY -> asmgen.out("  lda  cx16.r14 |  ldy  cx16.r14+1")
                    RegisterOrPair.AX -> asmgen.out("  lda  cx16.r14 |  ldx  cx16.r14+1")
                    RegisterOrPair.XY -> asmgen.out("  ldx  cx16.r14 |  ldy  cx16.r14+1")
                    else -> throw AssemblyError("invalid register for lsw: $resultReg")
                }
            } else {
                asmgen.assignExpressionToRegister(arg, resultReg, true)
            }
        }

        return arrayOf(resultReg)
    }

    private fun translateArguments(call: PtFunctionCall, funcname: String?, scope: IPtSubroutine?) {
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

        fun getSourceForLong(value: PtExpression): AsmAssignSource {
            return when(value) {
                is PtIdentifier -> AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, DataType.LONG, variableAsmName = asmgen.asmVariableName(value))
                is PtNumber -> AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, asmgen, DataType.LONG, number = value)
                else -> {
                    asmgen.assignExpressionToRegister(value, RegisterOrPair.R14R15, true)
                    AsmAssignSource(SourceStorageKind.REGISTER, program, asmgen, DataType.LONG, register = RegisterOrPair.R14R15)
                }
            }
        }

        val libfuncname = funcname ?: "func_${call.name}"

        call.args.zip(callConv.params).zip(signature.parameters).forEach {
            val paramName = it.second.name
            val conv = it.first.second
            val value = it.first.first
            when {
                conv.variable -> {
                    val varname = "prog8_lib.${libfuncname}._arg_${paramName}"
                    val src = when  {
                        conv.dt==BaseDataType.FLOAT -> getSourceForFloat(value)
                        conv.dt==BaseDataType.LONG -> getSourceForLong(value)
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
                        conv.dt==BaseDataType.LONG -> AsmAssignSource.fromAstSource(value, program, asmgen)
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
                    val tgt = AsmAssignTarget.fromRegisters(conv.reg!!, conv.dt.isSigned, value.position, null, asmgen)
                    val assign = AsmAssignment(src, listOf(tgt), program.memsizer, value.position)
                    asmgen.translateNormalAssignment(assign, scope)
                }
                else -> throw AssemblyError("callconv")
            }
        }
    }

    internal fun optimizedPeeklIntoLongvar(target: AsmAssignTarget, value: PtFunctionCall): Boolean {
        // TODO  just integrate this into funcPeekL
        val arg = value.args[0]
        if(arg is PtNumber) {
            val address = arg.number.toInt()
            asmgen.out("""
                lda  $address
                sta  ${target.asmVarname}
                lda  $address+1
                sta  ${target.asmVarname}+1
                lda  $address+2
                sta  ${target.asmVarname}+2
                lda  $address+3
                sta  ${target.asmVarname}+3""")
            return true
        } else if(arg is PtIdentifier) {
            val varname = asmgen.asmVariableName(arg)
            if(asmgen.isZpVar(arg)) {
                asmgen.out("""
                    ldy  #0
                    lda  ($varname),y
                    sta  ${target.asmVarname}
                    iny
                    lda  ($varname),y
                    sta  ${target.asmVarname}+1
                    iny
                    lda  ($varname),y
                    sta  ${target.asmVarname}+2
                    iny
                    lda  ($varname),y
                    sta  ${target.asmVarname}+3""")
                return true
            }
        }
        return false
    }

    internal fun optimizedMklongIntoLongvar(target: AsmAssignTarget, value: PtFunctionCall): Boolean {
        // TODO  just integrate this into funcMklong
        return false
        // TODO("mklong")
     }

     internal fun optimizedMklong2IntoLongvar(target: AsmAssignTarget, value: PtFunctionCall): Boolean {
         // TODO  just integrate this into funcMklong2
         return false
         // TODO("mklong2")
     }
 }
