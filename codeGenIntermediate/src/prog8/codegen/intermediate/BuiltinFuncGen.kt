package prog8.codegen.intermediate

import prog8.code.StMemorySlabBlockName
import prog8.code.StStructInstanceBlockName
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        return when(call.name) {
            "abs__byte", "abs__word", "abs__long", "abs__float" -> funcAbs(call)
            "cmp" -> funcCmp(call)
            "sgn" -> funcSgn(call)
            "sqrt__ubyte", "sqrt__uword", "sqrt__long", "sqrt__float" -> funcSqrt(call)
            "divmod__ubyte" -> funcDivmod(call, IRDataType.BYTE)
            "divmod__uword" -> funcDivmod(call, IRDataType.WORD)
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
            "memory" -> funcMemory(call)
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
            "prog8_lib_structalloc" -> funcStructAlloc(call)
            "sizeof" -> throw AssemblyError("sizeof must have been replaced with a constant")
            "offsetof" -> throw AssemblyError("offsetof must have been replaced with a constant")
            else -> throw AssemblyError("missing builtinfunc for ${call.name}")
        }
    }

    private fun funcSquare(call: PtBuiltinFunctionCall, resultType: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueTr = exprGen.translateExpression(call.args[0])
        addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
        return if(resultType==IRDataType.FLOAT) {
            val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
            addInstr(result, IRInstruction(Opcode.SQUARE, resultType, fpReg1 = resultFpReg, fpReg2 = valueTr.resultFpReg), null)
            ExpressionCodeResult(result, resultType, -1, resultFpReg)
        }
        else {
            val resultReg = codeGen.registers.next(resultType)
            addInstr(result, IRInstruction(Opcode.SQUARE, resultType, reg1 = resultReg, reg2 = valueTr.resultReg), null)
            ExpressionCodeResult(result, resultType, resultReg, -1)
        }
    }

    private fun funcCall(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val addressTr = exprGen.translateExpression(call.args[0])
        val resultvalueReg = codeGen.registers.next(IRDataType.WORD)
        addToResult(result, addressTr, addressTr.resultReg, -1)
        addInstr(result, IRInstruction(Opcode.CALLI, IRDataType.WORD, reg1 = resultvalueReg, reg2 = addressTr.resultReg), null)
        return if(call.void)
            ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
        else
            ExpressionCodeResult(result, IRDataType.WORD, resultvalueReg, -1)
    }

    private fun funcCallfar(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val bankTr = exprGen.translateExpression(call.args[0])
        val addressTr = exprGen.translateExpression(call.args[1])
        val argumentwordTr = exprGen.translateExpression(call.args[2])
        addToResult(result, bankTr, bankTr.resultReg, -1)
        addToResult(result, addressTr, addressTr.resultReg, -1)
        addToResult(result, argumentwordTr, argumentwordTr.resultReg, -1)
        result += codeGen.makeSyscall(IMSyscall.CALLFAR, listOf(IRDataType.BYTE to bankTr.resultReg, IRDataType.WORD to addressTr.resultReg, IRDataType.WORD to argumentwordTr.resultReg), IRDataType.WORD to argumentwordTr.resultReg)
        return ExpressionCodeResult(result, IRDataType.WORD, argumentwordTr.resultReg, -1)
    }

    private fun funcCallfar2(call: PtBuiltinFunctionCall): ExpressionCodeResult {
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
        result += codeGen.makeSyscall(IMSyscall.CALLFAR2, listOf(IRDataType.BYTE to bankTr.resultReg, IRDataType.WORD to addressTr.resultReg,
            IRDataType.BYTE to argumentA.resultReg,
            IRDataType.BYTE to argumentX.resultReg,
            IRDataType.BYTE to argumentY.resultReg,
            IRDataType.BYTE to argumentCarry.resultReg), IRDataType.WORD to addressTr.resultReg)
        return ExpressionCodeResult(result, IRDataType.WORD, addressTr.resultReg, -1)
    }

    private fun funcDivmod(call: PtBuiltinFunctionCall, type: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val number = call.args[0]
        val divident = call.args[1]
        val divisionReg: Int
        val remainderReg: Int
        if(divident is PtNumber) {
            val tr = exprGen.translateExpression(number)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.DIVMOD, type, reg1 = tr.resultReg, immediate = divident.number.toInt()), null)
            divisionReg = tr.resultReg
            remainderReg = codeGen.registers.next(type)
        } else {
            val numTr = exprGen.translateExpression(number)
            addToResult(result, numTr, numTr.resultReg, -1)
            val dividentTr = exprGen.translateExpression(divident)
            addToResult(result, dividentTr, dividentTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.DIVMODR, type, reg1 = numTr.resultReg, reg2=dividentTr.resultReg), null)
            divisionReg = numTr.resultReg
            remainderReg = dividentTr.resultReg
        }
        // DIVMOD result convention: on value stack, division and remainder on top.
        addInstr(result, IRInstruction(Opcode.POP, type, reg1=remainderReg), null)
        addInstr(result, IRInstruction(Opcode.POP, type, reg1=divisionReg), null)
        result += assignRegisterTo(call.args[2], divisionReg)
        result += assignRegisterTo(call.args[3], remainderReg)
        return ExpressionCodeResult(result, type, -1, -1)
    }

    private fun funcStringCompare(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val left  = exprGen.translateExpression(call.args[0])
        val right = exprGen.translateExpression(call.args[1])
        addToResult(result, left, left.resultReg, -1)
        addToResult(result, right, right.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        result += codeGen.makeSyscall(IMSyscall.COMPARE_STRINGS, listOf(IRDataType.WORD to left.resultReg, IRDataType.WORD to right.resultReg), IRDataType.BYTE to resultReg)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcCmp(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val dt = irType(call.args[0].type)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CMP, dt, reg1=leftTr.resultReg, reg2=rightTr.resultReg)
        }
        return ExpressionCodeResult(result, dt, leftTr.resultReg, -1)
    }

    private fun funcAbs(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val sourceDt = call.args.single().type
        val result = mutableListOf<IRCodeChunkBase>()
        if(sourceDt.isUnsignedWord)
            return ExpressionCodeResult.EMPTY

        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        when (sourceDt.base) {
            BaseDataType.BYTE -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
            }
            BaseDataType.WORD -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.WORD)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.WORD, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.WORD, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
            }
            BaseDataType.LONG -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.next(IRDataType.LONG)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.LONG, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.LONG, tr.resultReg, -1)
            }
            BaseDataType.FLOAT -> {
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(result, IRInstruction(Opcode.FABS, IRDataType.FLOAT, fpReg1 = resultFpReg, fpReg2 = tr.resultFpReg), null)
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcSgn(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        val resultReg = codeGen.registers.next(IRDataType.BYTE)

        if(tr.dt==IRDataType.FLOAT) {
            addToResult(result, tr, -1, tr.resultFpReg)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.SGN, tr.dt, reg1 = resultReg, fpReg1 = tr.resultFpReg)
            }
        } else {
            addToResult(result, tr, tr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.SGN, tr.dt, reg1 = resultReg, reg2 = tr.resultReg)
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcSqrt(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        val dt = call.args[0].type
        when(dt.base) {
            BaseDataType.UBYTE -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.BYTE, reg1=resultReg, reg2=tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            BaseDataType.UWORD -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)     // sqrt of a word still produces just a byte result
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.WORD, reg1=resultReg, reg2=tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            BaseDataType.LONG -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.WORD)     // sqrt of a long still produces just a word result
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.LONG, reg1=resultReg, reg2=tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
            }
            BaseDataType.FLOAT -> {
                addToResult(result, tr, -1, tr.resultFpReg)
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.FLOAT, fpReg1 = resultFpReg, fpReg2 = tr.resultFpReg)
                }
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun funcMkword(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        if((call.args[0] as? PtNumber)?.number == 0.0) {
            // msb is 0, use EXT
            val lsbTr = exprGen.translateExpression(call.args[1])
            addToResult(result, lsbTr, lsbTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=resultReg, reg2 = lsbTr.resultReg), null)
        } else {
            val msbTr = exprGen.translateExpression(call.args[0])
            addToResult(result, msbTr, msbTr.resultReg, -1)
            val lsbTr = exprGen.translateExpression(call.args[1])
            addToResult(result, lsbTr, lsbTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=resultReg, reg2 = msbTr.resultReg, reg3 = lsbTr.resultReg), null)
        }
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcMklong(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.next(IRDataType.LONG)
        if(call.args.size==2) {
            // mklong2(word, word)
            if((call.args[0] as? PtNumber)?.number == 0.0) {
                // msw is 0, use EXT
                val lswTr = exprGen.translateExpression(call.args[1])
                addToResult(result, lswTr, lswTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.EXT, IRDataType.WORD, reg1=resultReg, reg2 = lswTr.resultReg), null)
            } else {
                val mswTr = exprGen.translateExpression(call.args[0])
                addToResult(result, mswTr, mswTr.resultReg, -1)
                val lswTr = exprGen.translateExpression(call.args[1])
                addToResult(result, lswTr, lswTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.CONCAT, IRDataType.WORD, reg1=resultReg, reg2 = mswTr.resultReg, reg3 = lswTr.resultReg), null)
            }
        } else {
            // mklong(msb, b3, b2, lsb)
            if((call.args[0] as? PtNumber)?.number == 0.0 && (call.args[1] as? PtNumber)?.number == 0.0 && (call.args[2] as? PtNumber)?.number == 0.0) {
                // use EXT.b + EXT.w
                val lsbTr = exprGen.translateExpression(call.args[3])
                addToResult(result, lsbTr, lsbTr.resultReg, -1)
                val wordReg = codeGen.registers.next(IRDataType.WORD)
                addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=wordReg, reg2 = lsbTr.resultReg), null)
                addInstr(result, IRInstruction(Opcode.EXT, IRDataType.WORD, reg1=resultReg, reg2 = wordReg), null)
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
                addInstr(result, IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=mswReg, reg2 = msbTr.resultReg, reg3 = b2Tr.resultReg), null)
                addInstr(result, IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=lswReg, reg2 = b1Tr.resultReg, reg3 = lsbTr.resultReg), null)
                addInstr(result, IRInstruction(Opcode.CONCAT, IRDataType.WORD, reg1=resultReg, reg2 = mswReg, reg3 = lswReg), null)
            }

        }
        return ExpressionCodeResult(result, IRDataType.LONG, resultReg, -1)
    }

    private fun funcClamp(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val type = irType(call.type)
        val valueTr = exprGen.translateExpression(call.args[0])
        val minimumTr = exprGen.translateExpression(call.args[1])
        val maximumTr = exprGen.translateExpression(call.args[2])
        result += valueTr.chunks
        result += minimumTr.chunks
        result += maximumTr.chunks
        if(type==IRDataType.FLOAT) {
            result += codeGen.makeSyscall(
                IMSyscall.CLAMP_FLOAT, listOf(
                    valueTr.dt to valueTr.resultFpReg,
                    minimumTr.dt to minimumTr.resultFpReg,
                    maximumTr.dt to maximumTr.resultFpReg,
                ), type to valueTr.resultFpReg
            )
            return ExpressionCodeResult(result, type, -1, valueTr.resultFpReg)
        } else {
            val syscall = when(call.type.base) {
                BaseDataType.UBYTE -> IMSyscall.CLAMP_UBYTE
                BaseDataType.BYTE -> IMSyscall.CLAMP_BYTE
                BaseDataType.UWORD -> IMSyscall.CLAMP_UWORD
                BaseDataType.WORD -> IMSyscall.CLAMP_WORD
                BaseDataType.LONG -> IMSyscall.CLAMP_LONG
                else -> throw AssemblyError("invalid dt")
            }
            result += codeGen.makeSyscall(syscall, listOf(
                    valueTr.dt to valueTr.resultReg,
                    minimumTr.dt to minimumTr.resultReg,
                    maximumTr.dt to maximumTr.resultReg,
                ), type to valueTr.resultReg
            )
            return ExpressionCodeResult(result, type, valueTr.resultReg, -1)
        }
    }

    private fun funcMin(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val type = irType(call.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val comparisonOpcode = if(call.type.isSigned) Opcode.BGTSR else Opcode.BGTR
        val after = codeGen.createLabelName()
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(comparisonOpcode, type, reg1 = rightTr.resultReg, reg2 = leftTr.resultReg, labelSymbol = after)
            // right <= left, take right
            it += IRInstruction(Opcode.LOADR, type, reg1=leftTr.resultReg, reg2=rightTr.resultReg)
            it += IRInstruction(Opcode.JUMP, labelSymbol = after)
        }
        result += IRCodeChunk(after, null)
        return ExpressionCodeResult(result, type, leftTr.resultReg, -1)
    }

    private fun funcMax(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val type = irType(call.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val comparisonOpcode = if(call.type.isSigned) Opcode.BGTSR else Opcode.BGTR
        val after = codeGen.createLabelName()
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(comparisonOpcode, type, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg, labelSymbol = after)
            // right >= left, take right
            it += IRInstruction(Opcode.LOADR, type, reg1=leftTr.resultReg, reg2=rightTr.resultReg)
            it += IRInstruction(Opcode.JUMP, labelSymbol = after)
        }
        result += IRCodeChunk(after, null)
        return ExpressionCodeResult(result, type, leftTr.resultReg, -1)
    }

    private fun funcPoke(call: PtBuiltinFunctionCall, dt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, dt, address = address)
                }
            } else {
                val tr = exprGen.translateExpression(call.args[0])
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, dt, reg1 = tr.resultReg)
                }
            }
        } else {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                val tr = exprGen.translateExpression(call.args[1])
                if(dt==IRDataType.FLOAT) {
                    addToResult(result, tr, -1, tr.resultFpReg)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREM, dt, fpReg1 = tr.resultFpReg, address = address)
                    }
                } else {
                    addToResult(result, tr, tr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREM, dt, reg1 = tr.resultReg, address = address)
                    }
                }
            } else {
                val addressTr = exprGen.translateExpression(call.args[0])
                addToResult(result, addressTr, addressTr.resultReg, -1)
                val valueTr = exprGen.translateExpression(call.args[1])
                if(dt==IRDataType.FLOAT) {
                    addToResult(result, valueTr, -1, valueTr.resultFpReg)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREI, IRDataType.FLOAT, reg1 = addressTr.resultReg, fpReg1 = valueTr.resultFpReg)
                    }
                } else {
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.STOREI, dt, reg1 = valueTr.resultReg, reg2 = addressTr.resultReg)
                    }
                }
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPeek(call: PtBuiltinFunctionCall, dt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(dt==IRDataType.FLOAT) {
            if(call.args[0] is PtNumber) {
                val resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1 = resultFpRegister, address = address)
                }
                ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpRegister)
            } else {
                val tr = exprGen.translateExpression(call.args.single())
                addToResult(result, tr, tr.resultReg, -1)
                val resultFpReg = codeGen.registers.next(IRDataType.FLOAT)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADI, IRDataType.FLOAT, reg1 = tr.resultReg, fpReg1 = resultFpReg)
                }
                ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
        } else {
            if (call.args[0] is PtNumber) {
                val resultRegister = codeGen.registers.next(dt)
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, dt, reg1 = resultRegister, address = address)
                }
                ExpressionCodeResult(result, dt, resultRegister, -1)
            } else {
                val tr = exprGen.translateExpression(call.args.single())
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.next(dt)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADI, dt, reg1 = resultReg, reg2 = tr.resultReg)
                }
                ExpressionCodeResult(result, dt, resultReg, -1)
            }
        }
    }

    private fun funcPokemon(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val address = call.args[0]

        fun pokeM(result: MutableList<IRCodeChunkBase>, address: Int, value: PtExpression) {
            if(codeGen.isZero(value)) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, address = address)
                }
            } else {
                val tr = exprGen.translateExpression(value)
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = tr.resultReg, address = address)
                }
            }
        }

        fun pokeI(result: MutableList<IRCodeChunkBase>, register: Int, value: PtExpression) {
            if(codeGen.isZero(value)) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, IRDataType.BYTE, reg1 = register)
                }
            } else {
                val valueTr = exprGen.translateExpression(value)
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = valueTr.resultReg, reg2 = register)
                }
            }
        }

        return if(address is PtNumber) {
            val resultRegister = codeGen.registers.next(IRDataType.BYTE)
            val addressNum = address.number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1 = resultRegister, address = addressNum)
            }
            pokeM(result, addressNum, call.args[1])
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val addressTr = exprGen.translateExpression(address)
            addToResult(result, addressTr, addressTr.resultReg, -1)
            val resultReg = codeGen.registers.next(IRDataType.BYTE)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1 = resultReg, reg2 = addressTr.resultReg)
            }
            pokeI(result, addressTr.resultReg, call.args[1])
            ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
        }
    }


    private fun funcMemory(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val name = (call.args[0] as PtString).value
        val code = IRCodeChunk(null, null)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=resultReg, labelSymbol = "$StMemorySlabBlockName.memory_$name")
        return ExpressionCodeResult(code, IRDataType.WORD, resultReg, -1)
    }

    private fun funcStructAlloc(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val code = IRCodeChunk(null, null)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        val labelname = SymbolTable.labelnameForStructInstance(call)
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=resultReg, labelSymbol = "${StStructInstanceBlockName}.$labelname")
        return ExpressionCodeResult(code, IRDataType.WORD, resultReg, -1)
    }

    private fun funcLsb(call: PtBuiltinFunctionCall, fromLong: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        if(fromLong)
            addInstr(result, IRInstruction(Opcode.LSIGB, IRDataType.LONG, reg1 = resultReg, reg2 = tr.resultReg), null)
        else
            addInstr(result, IRInstruction(Opcode.LSIGB, IRDataType.WORD, reg1 = resultReg, reg2 = tr.resultReg), null)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcLsw(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        addInstr(result, IRInstruction(Opcode.LSIGW, IRDataType.LONG, reg1 = resultReg, reg2 = tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, fromLong: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        if(fromLong)
            addInstr(result, IRInstruction(Opcode.MSIGB, IRDataType.LONG, reg1 = resultReg, reg2 = tr.resultReg), null)
        else
            addInstr(result, IRInstruction(Opcode.MSIGB, IRDataType.WORD, reg1 = resultReg, reg2 = tr.resultReg), null)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcMsw(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.next(IRDataType.WORD)
        addInstr(result, IRInstruction(Opcode.MSIGW, IRDataType.LONG, reg1 = resultReg, reg2 = tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
    }

    private fun funcRolRor(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val arg = call.args[0]
        val vmDt = irType(arg.type)
        val opcodeMemAndReg = when(call.name) {
            "rol" -> Opcode.ROXLM to Opcode.ROXL
            "ror" -> Opcode.ROXRM to Opcode.ROXR
            "rol2" -> Opcode.ROLM to Opcode.ROL
            "ror2" -> Opcode.RORM to Opcode.ROR
            else -> throw AssemblyError("wrong func")
        }

        val ident = arg as? PtIdentifier
        if(ident!=null) {
            addInstr(result, IRInstruction(opcodeMemAndReg.first, vmDt, labelSymbol = ident.name), null)
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val memAddr  = (arg as? PtMemoryByte)?.address?.asConstInteger()
        if(memAddr!=null) {
            addInstr(result, IRInstruction(opcodeMemAndReg.first, vmDt, address = memAddr), null)
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val arr = (arg as? PtArrayIndexer)
        val index = arr?.index?.asConstInteger()
        if(arr!=null && index!=null) {
            if(arr.variable==null)
                TODO("support for ptr indexing ${arr.position}")
            val variable = arr.variable!!.name
            if(arr.splitWords) {
                result += IRCodeChunk(null, null).also {
                    when(opcodeMemAndReg.first) {
                        Opcode.ROXRM, Opcode.RORM -> {
                            it += IRInstruction(opcodeMemAndReg.first, IRDataType.BYTE, labelSymbol = "${variable}_msb", symbolOffset = index)
                            it += IRInstruction(opcodeMemAndReg.first, IRDataType.BYTE, labelSymbol = "${variable}_lsb", symbolOffset = index)
                        }
                        Opcode.ROXLM, Opcode.ROLM -> {
                            it += IRInstruction(opcodeMemAndReg.first, IRDataType.BYTE, labelSymbol = "${variable}_lsb", symbolOffset = index)
                            it += IRInstruction(opcodeMemAndReg.first, IRDataType.BYTE, labelSymbol = "${variable}_msb", symbolOffset = index)
                        }
                        else -> throw AssemblyError("wrong rol/ror opcode")
                    }
                }
            } else {
                val offset = codeGen.program.memsizer.memorySize(arr.type, index)
                addInstr(result, IRInstruction(opcodeMemAndReg.first, vmDt, labelSymbol = variable, symbolOffset = offset), null)
            }
            return ExpressionCodeResult(result, vmDt, -1, -1)
        }

        val opcode = opcodeMemAndReg.second
        val saveCarry = opcode in OpcodesThatDependOnCarry && !arg.isSimple()
        if(saveCarry)
            addInstr(result, IRInstruction(Opcode.PUSHST), null)    // save Carry
        val tr = exprGen.translateExpression(arg)
        addToResult(result, tr, tr.resultReg, -1)
        if(saveCarry)
            addInstr(result, IRInstruction(Opcode.POPST), null)
        addInstr(result, IRInstruction(opcode, vmDt, reg1 = tr.resultReg), null)
        if(saveCarry)
            addInstr(result, IRInstruction(Opcode.PUSHST), null)    // save Carry
        result += assignRegisterTo(arg, tr.resultReg)
        if(saveCarry)
            addInstr(result, IRInstruction(Opcode.POPST), null)
        return ExpressionCodeResult(result, vmDt, -1, -1)
    }

    private fun funcSetLsbMsb(call: PtBuiltinFunctionCall, msb: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val target = call.args[0]
        val isConstZeroValue = call.args[1].asConstInteger()==0
        when(target) {
            is PtIdentifier -> {
                if(isConstZeroValue) {
                    result += IRCodeChunk(null, null).also {
                        val pointerReg = codeGen.registers.next(IRDataType.WORD)
                        it += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = pointerReg, labelSymbol = target.name)
                        if (msb)
                            it += IRInstruction(Opcode.INC, IRDataType.WORD, reg1 = pointerReg)
                        it += IRInstruction(Opcode.STOREZI, IRDataType.BYTE, reg1 = pointerReg)
                    }
                } else {
                    val valueTr = exprGen.translateExpression(call.args[1])
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        val pointerReg = codeGen.registers.next(IRDataType.WORD)
                        it += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = pointerReg, labelSymbol = target.name)
                        if (msb)
                            it += IRInstruction(Opcode.INC, IRDataType.WORD, reg1 = pointerReg)
                        it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = valueTr.resultReg, reg2 = pointerReg)
                    }
                }
            }
            is PtArrayIndexer -> {
                if(target.splitWords) {
                    // lsb/msb in split arrays, element index 'size' is always 1
                    val constIndex = target.index.asConstInteger()
                    if(target.variable==null)
                        TODO("support for ptr indexing ${target.position}")
                    val varName = target.variable!!.name + if(msb) "_msb" else "_lsb"
                    if(isConstZeroValue) {
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.next(IRDataType.BYTE)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = constIndex)
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=offsetReg, labelSymbol = varName)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=indexTr.resultReg, labelSymbol = varName)
                            }
                        }
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.next(IRDataType.BYTE)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = constIndex)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=offsetReg, labelSymbol = varName)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=indexTr.resultReg, labelSymbol = varName)
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
                            val offsetReg = codeGen.registers.next(IRDataType.BYTE)
                            val offset = eltSize*constIndex + if(msb) 1 else 0
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = offset)
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=offsetReg, labelSymbol = targetVariable.name)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                if(eltSize>1)
                                    it += codeGen.multiplyByConst(DataType.UBYTE, indexTr.resultReg, eltSize)
                                if(msb)
                                    it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexTr.resultReg)
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=indexTr.resultReg, labelSymbol = targetVariable.name)
                            }
                        }
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.next(IRDataType.BYTE)
                            val offset = eltSize*constIndex + if(msb) 1 else 0
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = offset)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=offsetReg, labelSymbol = targetVariable.name)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                if(eltSize>1)
                                    it += codeGen.multiplyByConst(DataType.UBYTE, indexTr.resultReg, eltSize)
                                if(msb)
                                    it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexTr.resultReg)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=indexTr.resultReg, labelSymbol = targetVariable.name)
                            }
                        }
                    }
                }
            }
            else -> throw AssemblyError("weird target for setlsb/setmsb: $target")
        }
        return ExpressionCodeResult(result, IRDataType.WORD, -1, -1)
    }


    private fun assignRegisterTo(target: PtExpression, register: Int): IRCodeChunks {
        val assignment = PtAssignment(target.position)
        val assignTarget = PtAssignTarget(false, target.position)
        assignTarget.children.add(target)
        assignment.children.add(assignTarget)
        assignment.children.add(PtIrRegister(register, target.type, target.position))
        val result = mutableListOf<IRCodeChunkBase>()
        result += codeGen.translateNode(assignment)
        return result
    }
}
