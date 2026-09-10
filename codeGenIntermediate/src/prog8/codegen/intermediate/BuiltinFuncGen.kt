package prog8.codegen.intermediate

import prog8.code.StStructInstanceBlockName
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtFunctionCall): ExpressionCodeResult {
        require(call.builtin)
        return when(call.name) {
            "abs__byte", "abs__word", "abs__long", "abs__float" -> funcAbs(call)
            "cmp" -> funcCmp(call)
            "sgn" -> funcSgn(call)
            "sqrt__ubyte", "sqrt__uword", "sqrt__long", "sqrt__float" -> funcSqrt(call)
            "divmod__ubyte" -> funcDivmod(call, IRDataType.BYTE, false)
            "divmod__uword" -> funcDivmod(call, IRDataType.WORD, false)
            "divmod__byte" -> funcDivmod(call, IRDataType.BYTE, true)
            "divmod__word" -> funcDivmod(call, IRDataType.WORD, true)
            "rsave", "rrestore" -> ExpressionCodeResult.EMPTY  // vm doesn't have registers to save/restore
            "callfar" -> funcCallfar(call)
            "callfar2" -> funcCallfar2(call)
            "call" -> funcCall(call)
            "msw" -> funcMsw(call)
            "lsw" -> funcLsw(call)
            "msb" -> funcMsb(call, false)
            "msb__long" -> funcMsb(call, true)
            "lsb" -> funcLsb(call, false)
            "lsb__long" -> funcLsb(call, true)
            "lmh" -> funcLmh(call)
            "peek" -> funcPeek(call, IRDataType.BYTE)
            "peekbool" -> funcPeek(call, IRDataType.BYTE)
            "peekw" -> funcPeek(call, IRDataType.WORD)
            "peekl" -> funcPeek(call, IRDataType.LONG)
            "peekf" -> funcPeek(call, IRDataType.FLOAT)
            "poke" -> funcPoke(call, IRDataType.BYTE)
            "pokebool" -> funcPoke(call, IRDataType.BYTE)
            "pokebowl" -> funcPoke(call, IRDataType.BYTE)
            "pokew" -> funcPoke(call, IRDataType.WORD)
            "pokel" -> funcPoke(call, IRDataType.LONG)
            "pokef" -> funcPoke(call, IRDataType.FLOAT)
            "pokemon" -> funcPokemon(call)
            "mkword" -> funcMkword(call)
            "mklong", "mklong2" -> funcMklong(call)
            "clamp__byte", "clamp__ubyte", "clamp__word", "clamp__uword", "clamp__long" -> funcClamp(call)
            "min__byte", "min__ubyte", "min__word", "min__uword", "min__long" -> funcMin(call)
            "max__byte", "max__ubyte", "max__word", "max__uword", "max__long" -> funcMax(call)
            "setlsb" -> funcSetLsbMsb(call, false)
            "setmsb" -> funcSetLsbMsb(call, true)
            "rol" -> funcRolRor(call)
            "ror" -> funcRolRor(call)
            "rol2" -> funcRolRor(call)
            "ror2" -> funcRolRor(call)
            "prog8_lib_stringcompare" -> funcStringCompare(call)
            "prog8_lib_square_byte" -> funcSquare(call, IRDataType.BYTE)
            "prog8_lib_square_word" -> funcSquare(call, IRDataType.WORD)
            "prog8_lib_square_long" -> funcSquare(call, IRDataType.LONG)
            "prog8_lib_structalloc" -> funcStructAlloc(call)
            "prog8_lib_copylong" -> funcCopyFromPointer1ToPointer2(call, IRDataType.LONG)
            "prog8_lib_copyfloat" -> funcCopyFromPointer1ToPointer2(call, IRDataType.FLOAT)
            "push" -> funcPush(call)
            "pushw" -> funcPushW(call)
            "pushl" -> funcPushL(call)
            "pushf" -> funcPushF(call)
            "pop" -> funcPop()
            "popw" -> funcPopW()
            "popl" -> funcPopL()
            "popf" -> funcPopF()
            "sizeof" -> throw AssemblyError("sizeof must have been replaced with a constant  ${call.position}")
            "offsetof" -> throw AssemblyError("offsetof must have been replaced with a constant  ${call.position}")
            else -> throw AssemblyError("missing builtinfunc for ${call.name}  ${call.position}")
        }
    }

    private fun funcPush(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, IRInstructions.push(IRDataType.BYTE, tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPushW(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, IRInstructions.push(IRDataType.WORD, tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPushL(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, IRInstructions.push(IRDataType.LONG, tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPushF(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, -1, tr.resultFpReg)
        addInstr(result, IRInstructions.push(IRDataType.FLOAT, tr.resultFpReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPop(): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(IRDataType.BYTE)
        addInstr(result, IRInstructions.pop(IRDataType.BYTE, resultRegister), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
    }

    private fun funcPopW(): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(IRDataType.WORD)
        addInstr(result, IRInstructions.pop(IRDataType.WORD, resultRegister), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
    }

    private fun funcPopL(): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(IRDataType.LONG)
        addInstr(result, IRInstructions.pop(IRDataType.LONG, resultRegister), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
    }

    private fun funcPopF(): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(IRDataType.FLOAT)
        addInstr(result, IRInstructions.pop(IRDataType.FLOAT, resultRegister), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, resultRegister)
    }

    private fun funcCopyFromPointer1ToPointer2(call: PtFunctionCall, type: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val trSourceAddr = exprGen.translateExpression(call.args[0])
        val trTargetAddr = exprGen.translateExpression(call.args[1])
        addToResult(result, trSourceAddr, trSourceAddr.resultReg, -1)
        addToResult(result, trTargetAddr, trTargetAddr.resultReg, -1)
        when(type) {
            IRDataType.LONG -> {
                val intermediateReg = codeGen.registers.next(IRDataType.LONG)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, type, intermediateReg, IRMemory.indirect(trSourceAddr.resultReg))
                    it += IRInstructions.storeMemory(Opcode.STOREI, type, intermediateReg, IRMemory.indirect(trTargetAddr.resultReg))
                }
            }
            IRDataType.FLOAT -> {
                val intermediateReg = codeGen.registers.next(IRDataType.FLOAT)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.loadMemory(Opcode.LOADI, type, intermediateReg, IRMemory.indirect(trSourceAddr.resultReg))
                    it += IRInstructions.storeMemory(Opcode.STOREI, type, intermediateReg, IRMemory.indirect(trTargetAddr.resultReg))
                }
            }
            else -> throw AssemblyError("invalid type $type")
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcSquare(call: PtFunctionCall, resultType: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueTr = exprGen.translateExpression(call.args[0])
        addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
        return if(resultType==IRDataType.FLOAT) {
            val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
            addInstr(result, IRInstructions.binary(Opcode.SQUARE, resultType, resultFpReg, valueTr.resultFpReg), null)
            ExpressionCodeResult(result, resultType, -1, resultFpReg)
        }
        else {
            val resultReg = codeGen.registers.next(resultType)
            addInstr(result, IRInstructions.binary(Opcode.SQUARE, resultType, resultReg, valueTr.resultReg), null)
            ExpressionCodeResult(result, resultType, resultReg, -1)
        }
    }

    private fun funcCall(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val addressTr = exprGen.translateExpression(call.args[0])
        addToResult(result, addressTr, addressTr.resultReg, -1)
        addInstr(result, IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(addressTr.resultReg)))), null)
        return if(call.void)
            ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
        else {
            val resultvalueReg = codeGen.registers.next(IRDataType.WORD)
            val slot = if(codeGen.options.compTarget.cpu.usesM68kConvention) 10 else 4
            addInstr(result, IRInstructions.hardwareLoad(IRDataType.WORD, resultvalueReg, CallingConventionSlot(slot)), null)
            ExpressionCodeResult(result, IRDataType.WORD, resultvalueReg, -1)
        }
    }

    private fun funcCallfar(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val bankTr = exprGen.translateExpression(call.args[0])
        val addressTr = exprGen.translateExpression(call.args[1])
        val argumentwordTr = exprGen.translateExpression(call.args[2])
        addToResult(result, bankTr, bankTr.resultReg, -1)
        addToResult(result, addressTr, addressTr.resultReg, -1)
        addToResult(result, argumentwordTr, argumentwordTr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        result += codeGen.makeSyscall(IMSyscall.CALLFAR, listOf(IRDataType.BYTE to bankTr.resultReg, codeGen.addressDt to addressTr.resultReg, IRDataType.WORD to argumentwordTr.resultReg), IRDataType.WORD to resultReg)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcCallfar2(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val bankTr = exprGen.translateExpression(call.args[0])
        val addressTr = exprGen.translateExpression(call.args[1])
        val argumentA = exprGen.translateExpression(call.args[2])
        val argumentX = exprGen.translateExpression(call.args[3])
        val argumentY = exprGen.translateExpression(call.args[4])
        val argumentCarry = exprGen.translateExpression(call.args[5])
        addToResult(result, bankTr, bankTr.resultReg, -1)
        addToResult(result, addressTr, addressTr.resultReg, -1)
        addToResult(result, argumentA, argumentA.resultReg, -1)
        addToResult(result, argumentX, argumentX.resultReg, -1)
        addToResult(result, argumentY, argumentY.resultReg, -1)
        addToResult(result, argumentCarry, argumentCarry.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        result += codeGen.makeSyscall(IMSyscall.CALLFAR2, listOf(IRDataType.BYTE to bankTr.resultReg, codeGen.addressDt to addressTr.resultReg,
            IRDataType.BYTE to argumentA.resultReg,
            IRDataType.BYTE to argumentX.resultReg,
            IRDataType.BYTE to argumentY.resultReg,
            IRDataType.BYTE to argumentCarry.resultReg), IRDataType.WORD to resultReg)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcDivmod(call: PtFunctionCall, type: IRDataType, signed: Boolean): ExpressionCodeResult {
        if(signed && codeGen.options.compTarget.cpu.is6502) {
            codeGen.errors.err("expected all ubyte or all uword arguments (no signed divmod support on 6502 yet)", call.position)
            return ExpressionCodeResult(mutableListOf(), type, -1, -1)
        }
        val result = mutableListOf<IRCodeChunkBase>()
        val number = call.args[0]
        val divident = call.args[1]
        val divisionReg: Int
        val remainderReg: Int
        val divmodOpcode = if(signed) Opcode.SDIVMOD else Opcode.DIVMOD
        val divmodrOpcode = if(signed) Opcode.SDIVMODR else Opcode.DIVMODR
        if(divident is PtNumber) {
            val tr = exprGen.translateExpression(number)
            addToResult(result, tr, tr.resultReg, -1)
            remainderReg = codeGen.registers.next(type)
            addInstr(result, IRInstructions.divmodImmediate(divmodOpcode, type, tr.resultReg, remainderReg, divident.number.toInt()), null)
            divisionReg = tr.resultReg
        } else {
            val numTr = exprGen.translateExpression(number)
            addToResult(result, numTr, numTr.resultReg, -1)
            val dividentTr = exprGen.translateExpression(divident)
            addToResult(result, dividentTr, dividentTr.resultReg, -1)
            remainderReg = dividentTr.resultReg
            addInstr(result, IRInstructions.divmodRegister(divmodrOpcode, type, numTr.resultReg, dividentTr.resultReg), null)
            divisionReg = numTr.resultReg
        }
        // DIVMOD result convention: quotient in reg1, remainder in reg2.
        // Route to the return value locations expected by the caller (via returnsWhatWhere).
        if(codeGen.options.compTarget.cpu.usesM68kConvention) {
            // m68k: first return value in D0 (slot 10), second in D1 (slot 11)
            if(type==IRDataType.BYTE) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.hardwareStore(IRDataType.BYTE, remainderReg, CallingConventionSlot(11))
                    it += IRInstructions.hardwareStore(IRDataType.BYTE, divisionReg, CallingConventionSlot(10))
                }
            } else if(type==IRDataType.WORD) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.hardwareStore(IRDataType.WORD, remainderReg, CallingConventionSlot(11))
                    it += IRInstructions.hardwareStore(IRDataType.WORD, divisionReg, CallingConventionSlot(10))
                }
            } else throw AssemblyError("invalid type for DIVMOD")
        } else {
            // 6502/cx16: quotient to A (slot 0) or AY (slot 4), remainder to cx16.r15
            // Note: STOREM must come before STOREHR, otherwise STOREM clobbers A before LOADHR reads it.
            if(type==IRDataType.BYTE) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, remainderReg, IRMemory.direct("cx16.r15"))
                    it += IRInstructions.hardwareStore(IRDataType.BYTE, divisionReg, CallingConventionSlot(0))
                }
            } else if(type==IRDataType.WORD) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, remainderReg, IRMemory.direct("cx16.r15"))
                    it += IRInstructions.hardwareStore(IRDataType.WORD, divisionReg, CallingConventionSlot(4))
                }
            } else throw AssemblyError("invalid type for DIVMOD")
        }
        return ExpressionCodeResult(result, type, -1, -1)
    }

    private fun funcStringCompare(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val left  = exprGen.translateExpression(call.args[0])
        val right = exprGen.translateExpression(call.args[1])
        addToResult(result, left, left.resultReg, -1)
        addToResult(result, right, right.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        val addressDt = codeGen.addressDt
        result += codeGen.makeSyscall(IMSyscall.COMPARE_STRINGS, listOf(addressDt to left.resultReg, addressDt to right.resultReg), IRDataType.BYTE to resultReg)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcCmp(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val dt = codeGen.irType(call.args[0].type)
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.compare(dt, leftTr.resultReg, rightTr.resultReg)
        }
        return ExpressionCodeResult(result, dt, leftTr.resultReg, -1)
    }

    private fun funcAbs(call: PtFunctionCall): ExpressionCodeResult {
        val sourceDt = call.args.single().type
        val result = mutableListOf<IRCodeChunkBase>()
        if(sourceDt.isUnsignedWord)
            return ExpressionCodeResult.EMPTY

        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        val needsExplicitCmpi = !codeGen.options.compTarget.cpu.statusBitsOnMultiByteOps
        when (sourceDt.base) {
            BaseDataType.BYTE -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.move(IRDataType.BYTE, compareReg, tr.resultReg)
                    if(needsExplicitCmpi)
                        it += IRInstructions.compareImmediate(IRDataType.BYTE, compareReg, 0)
                    it += IRInstructions.branch(Opcode.BSTPOS, codeLabel(notNegativeLabel))
                    it += IRInstructions.unary(Opcode.NEG, IRDataType.BYTE, tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
            }
            BaseDataType.WORD -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.WORD)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.move(IRDataType.WORD, compareReg, tr.resultReg)
                    if(needsExplicitCmpi)
                        it += IRInstructions.compareImmediate(IRDataType.WORD, compareReg, 0)
                    it += IRInstructions.branch(Opcode.BSTPOS, codeLabel(notNegativeLabel))
                    it += IRInstructions.unary(Opcode.NEG, IRDataType.WORD, tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
            }
            BaseDataType.LONG -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.LONG)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.move(IRDataType.LONG, compareReg, tr.resultReg)
                    if(needsExplicitCmpi)
                        it += IRInstructions.compareImmediate(IRDataType.LONG, compareReg, 0)
                    it += IRInstructions.branch(Opcode.BSTPOS, codeLabel(notNegativeLabel))
                    it += IRInstructions.unary(Opcode.NEG, IRDataType.LONG, tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.LONG, tr.resultReg, -1)
            }
            BaseDataType.FLOAT -> {
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(result, IRInstructions.binary(Opcode.FABS, IRDataType.FLOAT, resultFpReg, tr.resultFpReg), null)
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            BaseDataType.POINTER -> {
                // pointer is unsigned, abs has no effect
                return ExpressionCodeResult(result, IRDataType.POINTER, tr.resultReg, -1)
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcSgn(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        val resultReg = codeGen.registers.next(IRDataType.BYTE)

        if(tr.dt==IRDataType.FLOAT) {
            addToResult(result, tr, -1, tr.resultFpReg)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.binary(Opcode.SGN, tr.dt, resultReg, tr.resultFpReg)
            }
        } else {
            addToResult(result, tr, tr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.binary(Opcode.SGN, tr.dt, resultReg, tr.resultReg)
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcSqrt(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        val dt = call.args[0].type
        when(dt.base) {
            BaseDataType.UBYTE -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.SQRT, IRDataType.BYTE, resultReg, tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            BaseDataType.UWORD -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)     // sqrt of a word still produces just a byte result
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.SQRT, IRDataType.WORD, resultReg, tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            BaseDataType.LONG -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.WORD)     // sqrt of a long still produces just a word result
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.SQRT, IRDataType.LONG, resultReg, tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
            }
            BaseDataType.FLOAT -> {
                addToResult(result, tr, -1, tr.resultFpReg)
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.binary(Opcode.SQRT, IRDataType.FLOAT, resultFpReg, tr.resultFpReg)
                }
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun funcMkword(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        if((call.args[0] as? PtNumber)?.number == 0.0) {
            // msb is 0, use EXT
            val lsbTr = exprGen.translateExpression(call.args[1])
            addToResult(result, lsbTr, lsbTr.resultReg, -1)
            addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, resultReg, lsbTr.resultReg), null)
        } else {
            val msbTr = exprGen.translateExpression(call.args[0])
            addToResult(result, msbTr, msbTr.resultReg, -1)
            val lsbTr = exprGen.translateExpression(call.args[1])
            addToResult(result, lsbTr, lsbTr.resultReg, -1)
            addInstr(result, IRInstructions.concat(IRDataType.BYTE, resultReg, msbTr.resultReg, lsbTr.resultReg), null)
        }
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcMklong(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(IRDataType.LONG)
        if(call.args.size==2) {
            // mklong2(word, word)
            if((call.args[0] as? PtNumber)?.number == 0.0) {
                // msw is 0, use EXT
                val lswTr = exprGen.translateExpression(call.args[1])
                addToResult(result, lswTr, lswTr.resultReg, -1)
                addInstr(result, IRInstructions.binary(Opcode.EXT, IRDataType.WORD, resultReg, lswTr.resultReg), null)
            } else {
                val mswTr = exprGen.translateExpression(call.args[0])
                addToResult(result, mswTr, mswTr.resultReg, -1)
                val lswTr = exprGen.translateExpression(call.args[1])
                addToResult(result, lswTr, lswTr.resultReg, -1)
                addInstr(result, IRInstructions.concat(IRDataType.WORD, resultReg, mswTr.resultReg, lswTr.resultReg), null)
            }
        } else {
            // mklong(msb, b3, b2, lsb)
            if((call.args[0] as? PtNumber)?.number == 0.0 && (call.args[1] as? PtNumber)?.number == 0.0 && (call.args[2] as? PtNumber)?.number == 0.0) {
                // use EXTL.b: zero-extend byte directly to long
                val lsbTr = exprGen.translateExpression(call.args[3])
                addToResult(result, lsbTr, lsbTr.resultReg, -1)
                addInstr(result, IRInstructions.binary(Opcode.EXTL, IRDataType.BYTE, resultReg, lsbTr.resultReg), null)
            } else {
                val msbTr = exprGen.translateExpression(call.args[0])
                val b2Tr = exprGen.translateExpression(call.args[1])
                val b1Tr = exprGen.translateExpression(call.args[2])
                val lsbTr = exprGen.translateExpression(call.args[3])
                addToResult(result, msbTr, msbTr.resultReg, -1)
                addToResult(result, b2Tr, b2Tr.resultReg, -1)
                addToResult(result, b1Tr, b1Tr.resultReg, -1)
                addToResult(result, lsbTr, lsbTr.resultReg, -1)
                val lswReg = codeGen.registers.next(IRDataType.WORD)
                val mswReg = codeGen.registers.next(IRDataType.WORD)
                addInstr(result, IRInstructions.concat(IRDataType.BYTE, mswReg, msbTr.resultReg, b2Tr.resultReg), null)
                addInstr(result, IRInstructions.concat(IRDataType.BYTE, lswReg, b1Tr.resultReg, lsbTr.resultReg), null)
                addInstr(result, IRInstructions.concat(IRDataType.WORD, resultReg, mswReg, lswReg), null)
            }

        }
        return ExpressionCodeResult(result, IRDataType.LONG, resultReg, -1)
    }

    private fun funcClamp(call: PtFunctionCall): ExpressionCodeResult {
        val type = codeGen.irType(call.type)
        require(type != IRDataType.FLOAT)
        val result = mutableListOf<IRCodeChunkBase>()
        val valueTr = exprGen.translateExpression(call.args[0])
        val minimumTr = exprGen.translateExpression(call.args[1])
        val maximumTr = exprGen.translateExpression(call.args[2])
        result += valueTr.chunks
        result += minimumTr.chunks
        result += maximumTr.chunks
        val syscall = when(call.type.base) {
            BaseDataType.UBYTE -> IMSyscall.CLAMP_UBYTE
            BaseDataType.BYTE -> IMSyscall.CLAMP_BYTE
            BaseDataType.UWORD -> IMSyscall.CLAMP_UWORD
            BaseDataType.WORD -> IMSyscall.CLAMP_WORD
            BaseDataType.LONG -> IMSyscall.CLAMP_LONG
            else -> throw AssemblyError("invalid dt")
        }
        val resultReg = codeGen.registers.next(type)
        result += codeGen.makeSyscall(syscall, listOf(
                valueTr.dt to valueTr.resultReg,
                minimumTr.dt to minimumTr.resultReg,
                maximumTr.dt to maximumTr.resultReg,
            ), type to resultReg
        )
        return ExpressionCodeResult(result, type, resultReg, -1)
    }

    private fun funcMin(call: PtFunctionCall): ExpressionCodeResult {
        val type = codeGen.irType(call.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val comparisonOpcode = if(call.type.isSigned) Opcode.BGTSR else Opcode.BGTR
        val after = codeGen.createLabelName()
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.branchRegister(comparisonOpcode, type, rightTr.resultReg, leftTr.resultReg, codeLabel(after))
            // right <= left, take right
            it += IRInstructions.move(type, leftTr.resultReg, rightTr.resultReg)
            it += IRInstructions.jump(codeLabel(after))
        }
        result += IRCodeChunk(after, null)
        return ExpressionCodeResult(result, type, leftTr.resultReg, -1)
    }

    private fun funcMax(call: PtFunctionCall): ExpressionCodeResult {
        val type = codeGen.irType(call.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val comparisonOpcode = if(call.type.isSigned) Opcode.BGTSR else Opcode.BGTR
        val after = codeGen.createLabelName()
        result += IRCodeChunk(null, null).also {
            it += IRInstructions.branchRegister(comparisonOpcode, type, leftTr.resultReg, rightTr.resultReg, codeLabel(after))
            // right >= left, take right
            it += IRInstructions.move(type, leftTr.resultReg, rightTr.resultReg)
            it += IRInstructions.jump(codeLabel(after))
        }
        result += IRCodeChunk(after, null)
        return ExpressionCodeResult(result, type, leftTr.resultReg, -1)
    }

    private fun funcPoke(call: PtFunctionCall, dt: IRDataType): ExpressionCodeResult {
        // Try struct-array field folding: pokew(&arr[idx]+fieldOff, value) -> STOREX arr+fieldOff,S=structSize
        tryFoldStructArrayPoke(call, dt)?.let { return it }
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toUInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstructions.storeZero(Opcode.STOREZM, dt, IRMemory.direct(address.toAddress()))
                }
            } else {
                val (address, offset) = exprGen.getAddressAndOffset(call.args[0])
                if(address!=null) {
                    val tr = exprGen.translateExpression(address)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, dt, IRMemory.indirect(tr.resultReg, offset!!)), null)
                } else {
                    val tr = exprGen.translateExpression(call.args[0])
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, dt, IRMemory.indirect(tr.resultReg)), null)
                }
            }
        } else {
            val valueTr = exprGen.translateExpression(call.args[1])
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toUInt()
                addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
                if(dt==IRDataType.FLOAT) {
                    addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, dt, valueTr.resultFpReg, IRMemory.direct(address.toAddress())), null)
                } else {
                    addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, dt, valueTr.resultReg, IRMemory.direct(address.toAddress())), null)
                }
            } else {
                val (address, offset) = exprGen.getAddressAndOffset(call.args[0])
                if(address!=null) {
                    val addressTr = exprGen.translateExpression(address)
                    addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
                    addToResult(result, addressTr, addressTr.resultReg, -1)
                    if(dt==IRDataType.FLOAT) {
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, valueTr.resultFpReg, IRMemory.indirect(addressTr.resultReg, offset!!)), null)
                    } else {
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, dt, valueTr.resultReg, IRMemory.indirect(addressTr.resultReg, offset!!)), null)
                    }
                } else {
                    val addressTr = exprGen.translateExpression(call.args[0])
                    addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
                    addToResult(result, addressTr, addressTr.resultReg, -1)
                    if(dt==IRDataType.FLOAT) {
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, IRDataType.FLOAT, valueTr.resultFpReg, IRMemory.indirect(addressTr.resultReg)), null)
                    } else {
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, dt, valueTr.resultReg, IRMemory.indirect(addressTr.resultReg)), null)
                    }
                }
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPeek(call: PtFunctionCall, dt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(dt==IRDataType.FLOAT) {
            if(call.args[0] is PtNumber) {
                val resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                val address = (call.args[0] as PtNumber).number.toUInt()
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, IRDataType.FLOAT, resultFpRegister, IRMemory.direct(address.toAddress())), null)
                ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpRegister)
            } else {
                val tr = exprGen.translateExpression(call.args.single())
                addToResult(result, tr, tr.resultReg, -1)
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADI, IRDataType.FLOAT, resultFpReg, IRMemory.indirect(tr.resultReg)), null)
                ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
        } else {
            if (call.args[0] is PtNumber) {
                val resultRegister = codeGen.registers.next(dt)
                val address = (call.args[0] as PtNumber).number.toUInt()
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, dt, resultRegister, IRMemory.direct(address.toAddress())), null)
                ExpressionCodeResult(result, dt, resultRegister, -1)
            } else {
                val tr = exprGen.translateExpression(call.args.single())
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(dt)
                addInstr(result, IRInstructions.loadMemory(Opcode.LOADI, dt, resultReg, IRMemory.indirect(tr.resultReg)), null)
                ExpressionCodeResult(result, dt, resultReg, -1)
            }
        }
    }

    private fun funcPokemon(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val address = call.args[0]
        val newValue = call.args[1]

        val newValueTr = if (codeGen.isZero(newValue)) null else exprGen.translateExpression(newValue)

        return if (address is PtNumber) {
            val addressNum = address.number.toUInt()
            val resultRegister = codeGen.registers.next(IRDataType.BYTE)
            if (newValueTr != null) addToResult(result, newValueTr, newValueTr.resultReg, -1)
            addInstr(result, IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, resultRegister, IRMemory.direct(addressNum.toAddress())), null)
            if (newValueTr == null) {
                addInstr(result, IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.direct(addressNum.toAddress())), null)
            } else {
                addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, newValueTr.resultReg, IRMemory.direct(addressNum.toAddress())), null)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val resultReg = codeGen.registers.next(IRDataType.BYTE)
            val (baseaddress, offset) = exprGen.getAddressAndOffset(address)
            val addrExpr = baseaddress ?: address
            val finalOffset = if (baseaddress != null) offset!! else 0

            val addressTr = exprGen.translateExpression(addrExpr)
            if (newValueTr != null) addToResult(result, newValueTr, newValueTr.resultReg, -1)
            addToResult(result, addressTr, addressTr.resultReg, -1)

            addInstr(result, IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, resultReg, IRMemory.indirect(addressTr.resultReg, finalOffset)), null)
            if (newValueTr == null) {
                addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, IRDataType.BYTE, IRMemory.indirect(addressTr.resultReg, finalOffset)), null)
            } else {
                addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, newValueTr.resultReg, IRMemory.indirect(addressTr.resultReg, finalOffset)), null)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
        }
    }


    private fun funcStructAlloc(call: PtFunctionCall): ExpressionCodeResult {
        val code = IRCodeChunk(null, null)
        val resultReg = codeGen.registers.next(IRDataType.POINTER)
        val labelname = SymbolTable.labelnameForStructInstance(call)
        code += IRInstructions.loadAddress(IRDataType.POINTER, resultReg, "${StStructInstanceBlockName}.$labelname")
        return ExpressionCodeResult(code, IRDataType.POINTER, resultReg, -1)
    }

    private fun funcLsb(call: PtFunctionCall, fromLong: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        if(fromLong)
            addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, resultReg, tr.resultReg), null)
        else
            addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.WORD, resultReg, tr.resultReg), null)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcLmh(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        // TODO this can be more optimal if the argument is a variable or memory address
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val byteReg = codeGen.registers.next(IRDataType.BYTE)
        if(codeGen.options.compTarget.cpu.usesM68kConvention) {
            // m68k: 3 return values in D0 (slot 10), D1 (slot 11), D2 (slot 12)
            // store high byte first, then mid, then low (to avoid clobbering)
            addInstr(result, IRInstructions.binary(Opcode.BSIGB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.hardwareStore(IRDataType.BYTE, byteReg, CallingConventionSlot(12)), null)
            addInstr(result, IRInstructions.binary(Opcode.MIDB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.hardwareStore(IRDataType.BYTE, byteReg, CallingConventionSlot(11)), null)
            addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.hardwareStore(IRDataType.BYTE, byteReg, CallingConventionSlot(10)), null)
        } else {
            // 6502/cx16: low byte in A (slot 0), mid in cx16.r15, high (bank) in cx16.r14
            addInstr(result, IRInstructions.binary(Opcode.BSIGB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, byteReg, IRMemory.direct("cx16.r14")), null)
            addInstr(result, IRInstructions.binary(Opcode.MIDB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, byteReg, IRMemory.direct("cx16.r15")), null)
            addInstr(result, IRInstructions.binary(Opcode.LSIGB, IRDataType.LONG, byteReg, tr.resultReg), null)
            addInstr(result, IRInstructions.hardwareStore(IRDataType.BYTE, byteReg, CallingConventionSlot(0)), null)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcLsw(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        addInstr(result, IRInstructions.binary(Opcode.LSIGW, IRDataType.LONG, resultReg, tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcMsb(call: PtFunctionCall, fromLong: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        if(fromLong)
            addInstr(result, IRInstructions.binary(Opcode.MSIGB, IRDataType.LONG, resultReg, tr.resultReg), null)
        else
            addInstr(result, IRInstructions.binary(Opcode.MSIGB, IRDataType.WORD, resultReg, tr.resultReg), null)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcMsw(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        addInstr(result, IRInstructions.binary(Opcode.MSIGW, IRDataType.LONG, resultReg, tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcRolRor(call: PtFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val arg = call.args[0]
        val vmDt = codeGen.irType(arg.type)
        val opcodeMemAndReg = when(call.name) {
            "rol" -> Opcode.ROXLM to Opcode.ROXL
            "ror" -> Opcode.ROXRM to Opcode.ROXR
            "rol2" -> Opcode.ROLM to Opcode.ROL
            "ror2" -> Opcode.RORM to Opcode.ROR
            else -> throw AssemblyError("wrong func")
        }

        val ident = arg as? PtIdentifier
        if(ident!=null) {
            addInstr(result, IRInstructions.memoryOp(opcodeMemAndReg.first, vmDt, IRMemory.direct(ident.name)), null)
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val memAddr: UInt? = (arg as? PtMemoryByte)?.address?.asConstInteger()?.toUInt()
        if(memAddr!=null) {
            addInstr(result, IRInstructions.memoryOp(opcodeMemAndReg.first, vmDt, IRMemory.direct(memAddr.toAddress())), null)
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val arr = (arg as? PtArrayIndexer)
        val index = arr?.index?.asConstInteger()
        if(arr!=null && index!=null && arr.variable!=null) {
            val variable = arr.variable!!.name
            if(arr.splitWords) {
                result += IRCodeChunk(null, null).also {
                    when(opcodeMemAndReg.first) {
                        Opcode.ROXRM, Opcode.RORM -> {
                            it += IRInstructions.memoryOp(opcodeMemAndReg.first, IRDataType.BYTE, IRMemory.direct("${variable}_msb", index))
                            it += IRInstructions.memoryOp(opcodeMemAndReg.first, IRDataType.BYTE, IRMemory.direct("${variable}_lsb", index))
                        }
                        Opcode.ROXLM, Opcode.ROLM -> {
                            it += IRInstructions.memoryOp(opcodeMemAndReg.first, IRDataType.BYTE, IRMemory.direct("${variable}_lsb", index))
                            it += IRInstructions.memoryOp(opcodeMemAndReg.first, IRDataType.BYTE, IRMemory.direct("${variable}_msb", index))
                        }
                        else -> throw AssemblyError("wrong rol/ror opcode")
                    }
                }
            } else {
                val offset = codeGen.program.memsizer.memorySize(arr.type, index)
                addInstr(result, IRInstructions.memoryOp(opcodeMemAndReg.first, vmDt, IRMemory.direct(variable, offset)), null)
            }
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val opcode = opcodeMemAndReg.second
        val saveCarry = opcode in OpcodesThatDependOnCarry && !arg.isSimple()
        if(saveCarry)
            addInstr(result, IRInstructions.simple(Opcode.PUSHST), null)    // save Carry
        val tr = exprGen.translateExpression(arg)
        addToResult(result, tr, tr.resultReg, -1)
        if(saveCarry)
            addInstr(result, IRInstructions.simple(Opcode.POPST), null)
        addInstr(result, IRInstructions.unary(opcode, vmDt, tr.resultReg), null)
        if(saveCarry)
            addInstr(result, IRInstructions.simple(Opcode.PUSHST), null)    // save Carry
        result += codeGen.assignRegisterTo(arg, tr.resultReg)
        if(saveCarry)
            addInstr(result, IRInstructions.simple(Opcode.POPST), null)
        return ExpressionCodeResult(result, vmDt, -1, -1)
    }

    private fun funcSetLsbMsb(call: PtFunctionCall, msb: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val target = call.args[0]
        val isConstZeroValue = call.args[1].asConstInteger()==0
        val isBigEndian = codeGen.options.compTarget.cpu.isBigEndian
        fun byteOffset(elementSize: Int) = if(msb != isBigEndian) elementSize - 1 else 0
        when(target) {
            is PtIdentifier -> {
                val offset = byteOffset(if(target.type.isLong) 4 else 2)
                if(isConstZeroValue) {
                    result += IRCodeChunk(null, null).also {
                        val pointerReg = codeGen.registers.next(IRDataType.POINTER)
                        it += IRInstructions.loadAddress(IRDataType.POINTER, pointerReg, target.name)
                        it += IRInstructions.storeZero(Opcode.STOREZI, IRDataType.BYTE, IRMemory.indirect(pointerReg, offset))
                    }
                } else {
                    val valueTr = exprGen.translateExpression(call.args[1])
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        val pointerReg = codeGen.registers.next(IRDataType.POINTER)
                        it += IRInstructions.loadAddress(IRDataType.POINTER, pointerReg, target.name)
                        it += IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, valueTr.resultReg, IRMemory.indirect(pointerReg, offset))
                    }
                }
            }
            is PtArrayIndexer -> {
                if(target.variable==null) {
                    // pointer-based array indexing: compute memory address and store byte
                    val eltSize = codeGen.program.memsizer.memorySize(target.type, null)
                    val pointerTr = exprGen.translateExpression(target.pointerderef!!)
                    addToResult(result, pointerTr, pointerTr.resultReg, -1)
                    val constIndex = target.index.asConstInteger()
                    val elementByteOffset = byteOffset(eltSize)
                    if(constIndex != null) {
                        val offset = eltSize * constIndex + elementByteOffset
                        if(offset > 0)
                            addInstr(result, IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.POINTER, pointerTr.resultReg, offset), null)
                    } else {
                        val (code, indexWordReg) = codeGen.loadIndexReg(target.index, eltSize, true, false)
                        result += code
                        if(eltSize!=1)
                            result += codeGen.multiplyByConst(DataType.UWORD, indexWordReg, eltSize)
                        addInstr(result, IRInstructions.binary(Opcode.ADDR, IRDataType.POINTER, pointerTr.resultReg, indexWordReg), null)
                        if(elementByteOffset > 0)
                            addInstr(result, IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.POINTER, pointerTr.resultReg, elementByteOffset), null)
                    }
                    if(isConstZeroValue) {
                        addInstr(result, IRInstructions.storeZero(Opcode.STOREZI, IRDataType.BYTE, IRMemory.indirect(pointerTr.resultReg)), null)
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        addInstr(result, IRInstructions.storeMemory(Opcode.STOREI, IRDataType.BYTE, valueTr.resultReg, IRMemory.indirect(pointerTr.resultReg)), null)
                    }
                }
                else if(target.splitWords) {
                    // lsb/msb in split arrays, element index 'size' is always 1
                    val constIndex = target.index.asConstInteger()
                    val varName = target.variable!!.name + if(msb) "_msb" else "_lsb"
                    if(isConstZeroValue) {
                        if(constIndex!=null) {
                            val idxType = codeGen.options.compTarget.indexRegType
                            val offsetReg = codeGen.registers.next(idxType)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.load(idxType, offsetReg, constIndex)
                                it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(varName, offsetReg, codeGen.options.compTarget.indexRegType))
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            val indexReg = codeGen.canonicalizeIndexReg(result, indexTr)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(varName, indexReg, codeGen.options.compTarget.indexRegType))
                            }
                        }
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        if(constIndex!=null) {
                            val idxType = codeGen.options.compTarget.indexRegType
                            val offsetReg = codeGen.registers.next(idxType)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.load(idxType, offsetReg, constIndex)
                                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, valueTr.resultReg, IRMemory.indexed(varName, offsetReg, codeGen.options.compTarget.indexRegType))
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            val indexReg = codeGen.canonicalizeIndexReg(result, indexTr)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, valueTr.resultReg, IRMemory.indexed(varName, indexReg, codeGen.options.compTarget.indexRegType))
                            }
                        }
                    }
                }
                else {
                    val targetVariable = target.variable ?: TODO("support for ptr indexing ${target.position}")

                    val eltSize = codeGen.program.memsizer.memorySize(target.type, null)
                    val constIndex = target.index.asConstInteger()
                    if(isConstZeroValue) {
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.next(if(codeGen.wordArrayIndex) IRDataType.WORD else IRDataType.BYTE)
                            val offset = eltSize*constIndex + byteOffset(eltSize)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.load(if(codeGen.wordArrayIndex) IRDataType.WORD else IRDataType.BYTE, offsetReg, offset)
                                it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(targetVariable.name, offsetReg, codeGen.options.compTarget.indexRegType))
                            }
                        } else {
                            val (code, indexReg) = codeGen.loadIndexReg(target.index, eltSize, codeGen.wordArrayIndex, false)
                            result += code
                            result += IRCodeChunk(null, null).also {
                                val offset = byteOffset(eltSize)
                                it += IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.indexed(targetVariable.name, indexReg, codeGen.options.compTarget.indexRegType, scale = eltSize, displacement = offset))
                            }
                        }
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.next(if(codeGen.wordArrayIndex) IRDataType.WORD else IRDataType.BYTE)
                            val offset = eltSize*constIndex + byteOffset(eltSize)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstructions.load(if(codeGen.wordArrayIndex) IRDataType.WORD else IRDataType.BYTE, offsetReg, offset)
                                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, valueTr.resultReg, IRMemory.indexed(targetVariable.name, offsetReg, codeGen.options.compTarget.indexRegType))
                            }
                        } else {
                            val (code, indexReg) = codeGen.loadIndexReg(target.index, eltSize, codeGen.wordArrayIndex, false)
                            result += code
                            result += IRCodeChunk(null, null).also {
                                val offset = byteOffset(eltSize)
                                it += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, valueTr.resultReg, IRMemory.indexed(targetVariable.name, indexReg, codeGen.options.compTarget.indexRegType, scale = eltSize, displacement = offset))
                            }
                        }
                    }
                }
            }
            else -> throw AssemblyError("weird target for setlsb/setmsb: $target")
        }
        return ExpressionCodeResult(result, IRDataType.WORD, -1, -1)
    }

    private fun tryFoldStructArrayPoke(call: PtFunctionCall, dt: IRDataType): ExpressionCodeResult? {
        // Detect pokew(&arr[idx]+fieldOff, value) where arr is struct array with variable idx
        // and fold to single STOREX/STOREZX with scale=structSize.
        val info = extractStructArrayIndexInfo(call.args[0], codeGen) ?: return null
        val isZero = codeGen.isZero(call.args[1])
        val result = mutableListOf<IRCodeChunkBase>()
        val (idxCode, indexReg) = codeGen.loadIndexReg(info.idxExpr, info.structSize, codeGen.wordArrayIndex, false)
        result += idxCode
        return if(isZero) {
            result += IRCodeChunk(null, null).also {
                it += IRInstructions.storeZero(Opcode.STOREZX, dt, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize, displacement=info.fieldOffset))
            }
            ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
        } else {
            val valueTr = exprGen.translateExpression(call.args[1])
            addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
            result += IRCodeChunk(null, null).also {
                if(dt==IRDataType.FLOAT) {
                    it += IRInstructions.storeMemory(Opcode.STOREX, dt, valueTr.resultFpReg, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize, displacement=info.fieldOffset))
                } else {
                    it += IRInstructions.storeMemory(Opcode.STOREX, dt, valueTr.resultReg, IRMemory.indexed(info.arrayName, indexReg, codeGen.options.compTarget.indexRegType, scale=info.structSize, displacement=info.fieldOffset))
                }
            }
            ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
        }
    }
}
