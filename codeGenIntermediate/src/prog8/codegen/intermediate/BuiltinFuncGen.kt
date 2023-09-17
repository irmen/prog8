package prog8.codegen.intermediate

import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.SignedDatatypes
import prog8.code.core.SplitWordArrayTypes
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        return when(call.name) {
            "any" -> funcAny(call)
            "all" -> funcAll(call)
            "abs__byte", "abs__word", "abs__float" -> funcAbs(call)
            "cmp" -> funcCmp(call)
            "sgn" -> funcSgn(call)
            "sqrt__ubyte", "sqrt__uword", "sqrt__float" -> funcSqrt(call)
            "divmod__ubyte" -> funcDivmod(call, IRDataType.BYTE)
            "divmod__uword" -> funcDivmod(call, IRDataType.WORD)
            "pop" -> funcPop(call)
            "popw" -> funcPopw(call)
            "push" -> funcPush(call)
            "pushw" -> funcPushw(call)
            "rsave", "rrestore" -> ExpressionCodeResult.EMPTY  // vm doesn't have registers to save/restore
            "callfar" -> funcCallfar(call)
            "msb" -> funcMsb(call)
            "lsb" -> funcLsb(call)
            "memory" -> funcMemory(call)
            "peek" -> funcPeek(call)
            "peekw" -> funcPeekW(call)
            "poke" -> funcPoke(call)
            "pokew" -> funcPokeW(call)
            "pokemon" -> ExpressionCodeResult.EMPTY     // easter egg function
            "mkword" -> funcMkword(call)
            "clamp__byte", "clamp__ubyte", "clamp__word", "clamp__uword" -> funcClamp(call)
            "min__byte", "min__ubyte", "min__word", "min__uword" -> funcMin(call)
            "max__byte", "max__ubyte", "max__word", "max__uword" -> funcMax(call)
            "sort" -> funcSort(call)
            "reverse" -> funcReverse(call)
            "setlsb" -> funcSetLsbMsb(call, false)
            "setmsb" -> funcSetLsbMsb(call, true)
            "rol" -> funcRolRor(Opcode.ROXL, call)
            "ror" -> funcRolRor(Opcode.ROXR, call)
            "rol2" -> funcRolRor(Opcode.ROL, call)
            "ror2" -> funcRolRor(Opcode.ROR, call)
            "prog8_lib_stringcompare" -> funcStringCompare(call)
            "prog8_lib_square_byte" -> funcSquare(call, IRDataType.BYTE)
            "prog8_lib_square_word" -> funcSquare(call, IRDataType.WORD)
            else -> throw AssemblyError("missing builtinfunc for ${call.name}")
        }
    }

    private fun funcSquare(call: PtBuiltinFunctionCall, resultType: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueTr = exprGen.translateExpression(call.args[0])
        addToResult(result, valueTr, valueTr.resultReg, valueTr.resultFpReg)
        return if(resultType==IRDataType.FLOAT) {
            val resultFpReg = codeGen.registers.nextFreeFloat()
            addInstr(result, IRInstruction(Opcode.SQUARE, resultType, fpReg1 = resultFpReg, fpReg2 = valueTr.resultFpReg), null)
            ExpressionCodeResult(result, resultType, -1, resultFpReg)
        }
        else {
            val resultReg = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.SQUARE, resultType, reg1 = resultReg, reg2 = valueTr.resultReg), null)
            ExpressionCodeResult(result, resultType, resultReg, -1)
        }
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
            remainderReg = codeGen.registers.nextFree()
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
        result += codeGen.makeSyscall(IMSyscall.COMPARE_STRINGS, listOf(IRDataType.WORD to left.resultReg, IRDataType.WORD to right.resultReg), IRDataType.BYTE to left.resultReg)
        return ExpressionCodeResult(result, IRDataType.BYTE, left.resultReg, -1)
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

    private fun funcAny(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val arrayLength = codeGen.symbolTable.getLength(arrayName.name)
        val syscall =
            when (arrayName.type) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ANY_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ANY_WORD
                DataType.ARRAY_F -> IMSyscall.ANY_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        val lengthReg = codeGen.registers.nextFree()
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = lengthReg, immediate = arrayLength), null)
        result += codeGen.makeSyscall(syscall, listOf(IRDataType.WORD to tr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to tr.resultReg)
        return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
    }

    private fun funcAll(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val arrayLength = codeGen.symbolTable.getLength(arrayName.name)
        val syscall =
            when(arrayName.type) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ALL_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ALL_WORD
                DataType.ARRAY_F -> IMSyscall.ALL_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        val lengthReg = codeGen.registers.nextFree()
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = lengthReg, immediate = arrayLength), null)
        result += codeGen.makeSyscall(syscall, listOf(IRDataType.WORD to tr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to tr.resultReg)
        return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
    }

    private fun funcAbs(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val sourceDt = call.args.single().type
        val result = mutableListOf<IRCodeChunkBase>()
        if(sourceDt==DataType.UWORD)
            return ExpressionCodeResult.EMPTY

        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        when (sourceDt) {
            DataType.BYTE -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
            }
            DataType.WORD -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.WORD, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.WORD, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
            }
            DataType.FLOAT -> {
                val resultFpReg = codeGen.registers.nextFreeFloat()
                addInstr(result, IRInstruction(Opcode.FABS, IRDataType.FLOAT, fpReg1 = resultFpReg, fpReg2 = tr.resultFpReg), null)
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    private fun funcSgn(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val vmDt = irType(call.type)
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.nextFree()
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.SGN, vmDt, reg1 = resultReg, reg2 = tr.resultReg)
        }
        return ExpressionCodeResult(result, vmDt, resultReg, -1)
    }

    private fun funcSqrt(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        val dt = call.args[0].type
        when(dt) {
            DataType.UBYTE -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.BYTE, reg1=resultReg, reg2=tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            DataType.UWORD -> {
                addToResult(result, tr, tr.resultReg, -1)
                val resultReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.WORD, reg1=resultReg, reg2=tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
            }
            DataType.FLOAT -> {
                addToResult(result, tr, -1, tr.resultFpReg)
                val resultFpReg = codeGen.registers.nextFreeFloat()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SQRT, IRDataType.FLOAT, fpReg1 = resultFpReg, fpReg2 = tr.resultFpReg)
                }
                return ExpressionCodeResult(result, IRDataType.FLOAT, -1, resultFpReg)
            }
            else -> throw AssemblyError("invalid dt for sqrt")
        }
    }

    private fun funcPop(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val code = IRCodeChunk(null, null)
        val reg = codeGen.registers.nextFree()
        code += IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=reg)
        val result = mutableListOf<IRCodeChunkBase>(code)
        result += assignRegisterTo(call.args.single(), reg)
        return ExpressionCodeResult(result, IRDataType.BYTE, reg, -1)
    }

    private fun funcPopw(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val code = IRCodeChunk(null, null)
        val reg = codeGen.registers.nextFree()
        code += IRInstruction(Opcode.POP, IRDataType.WORD, reg1=reg)
        val result = mutableListOf<IRCodeChunkBase>(code)
        result += assignRegisterTo(call.args.single(), reg)
        return ExpressionCodeResult(result, IRDataType.WORD, reg, -1)
    }

    private fun funcPush(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=tr.resultReg)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPushw(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.PUSH, IRDataType.WORD, reg1 = tr.resultReg)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val arrayLength = codeGen.symbolTable.getLength(arrayName.name)
        val syscall =
            when(arrayName.type) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> IMSyscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> IMSyscall.REVERSE_WORDS
                DataType.ARRAY_F -> IMSyscall.REVERSE_FLOATS
                in SplitWordArrayTypes -> TODO("split word reverse")
                else -> throw IllegalArgumentException("weird type to reverse")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        val lengthReg = codeGen.registers.nextFree()
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = lengthReg, immediate = if(arrayName.type==DataType.STR) arrayLength!!-1 else arrayLength), null)
        result += codeGen.makeSyscall(syscall, listOf(IRDataType.WORD to tr.resultReg, IRDataType.BYTE to lengthReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcSort(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val arrayLength = codeGen.symbolTable.getLength(arrayName.name)
        val syscall =
            when(arrayName.type) {
                DataType.ARRAY_UB -> IMSyscall.SORT_UBYTE
                DataType.ARRAY_B -> IMSyscall.SORT_BYTE
                DataType.ARRAY_UW -> IMSyscall.SORT_UWORD
                DataType.ARRAY_W -> IMSyscall.SORT_WORD
                DataType.STR -> IMSyscall.SORT_UBYTE
                DataType.ARRAY_F -> throw IllegalArgumentException("sorting a floating point array is not supported")
                in SplitWordArrayTypes -> TODO("split word sort")
                else -> throw IllegalArgumentException("weird type to sort")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        val lengthReg = codeGen.registers.nextFree()
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = lengthReg, immediate = if(arrayName.type==DataType.STR) arrayLength!!-1 else arrayLength), null)
        result += codeGen.makeSyscall(syscall, listOf(IRDataType.WORD to tr.resultReg, IRDataType.BYTE to lengthReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcMkword(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val msbTr = exprGen.translateExpression(call.args[0])
        addToResult(result, msbTr, msbTr.resultReg, -1)
        val lsbTr = exprGen.translateExpression(call.args[1])
        addToResult(result, lsbTr, lsbTr.resultReg, -1)
        val resultReg = codeGen.registers.nextFree()
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=resultReg, reg2 = lsbTr.resultReg, reg3 = msbTr.resultReg)
        }
        return ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
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
            val syscall = when(call.type) {
                DataType.UBYTE -> IMSyscall.CLAMP_UBYTE
                DataType.BYTE -> IMSyscall.CLAMP_BYTE
                DataType.UWORD -> IMSyscall.CLAMP_UWORD
                DataType.WORD -> IMSyscall.CLAMP_WORD
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
        val comparisonOpcode = if(call.type in SignedDatatypes) Opcode.BGTSR else Opcode.BGTR
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
        val comparisonOpcode = if(call.type in SignedDatatypes) Opcode.BGTSR else Opcode.BGTR
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

    private fun funcPokeW(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.WORD, address = address)
                }
            } else {
                val tr = exprGen.translateExpression(call.args[0])
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, IRDataType.WORD, reg1 = tr.resultReg)
                }
            }
        } else {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                val tr = exprGen.translateExpression(call.args[1])
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = tr.resultReg, address = address)
                }
            } else {
                val addressTr = exprGen.translateExpression(call.args[0])
                addToResult(result, addressTr, addressTr.resultReg, -1)
                val valueTr = exprGen.translateExpression(call.args[1])
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREI, IRDataType.WORD, reg1 = valueTr.resultReg, reg2 = addressTr.resultReg)
                }
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPoke(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, address = address)
                }
            } else {
                val tr = exprGen.translateExpression(call.args[0])
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, IRDataType.BYTE, reg1 = tr.resultReg)
                }
            }
        } else {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                val tr = exprGen.translateExpression(call.args[1])
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = tr.resultReg, address = address)
                }
            } else {
                val addressTr = exprGen.translateExpression(call.args[0])
                addToResult(result, addressTr, addressTr.resultReg, -1)
                val valueTr = exprGen.translateExpression(call.args[1])
                addToResult(result, valueTr, valueTr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = valueTr.resultReg, reg2 = addressTr.resultReg)
                }
            }
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcPeekW(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(call.args[0] is PtNumber) {
            val resultRegister = codeGen.registers.nextFree()
            val address = (call.args[0] as PtNumber).number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1 = resultRegister, address = address)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val tr = exprGen.translateExpression(call.args.single())
            addToResult(result, tr, tr.resultReg, -1)
            val resultReg = codeGen.registers.nextFree()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.WORD, reg1 = resultReg, reg2 = tr.resultReg)
            }
            ExpressionCodeResult(result, IRDataType.WORD, resultReg, -1)
        }
    }

    private fun funcPeek(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(call.args[0] is PtNumber) {
            val resultRegister = codeGen.registers.nextFree()
            val address = (call.args[0] as PtNumber).number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1 = resultRegister, address = address)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val tr = exprGen.translateExpression(call.args.single())
            addToResult(result, tr, tr.resultReg, -1)
            val resultReg = codeGen.registers.nextFree()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1 = resultReg, reg2 = tr.resultReg)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
        }
    }

    private fun funcMemory(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val name = (call.args[0] as PtString).value
        val code = IRCodeChunk(null, null)
        val resultReg = codeGen.registers.nextFree()
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=resultReg, labelSymbol = "prog8_slabs.prog8_memoryslab_$name")
        return ExpressionCodeResult(code, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcLsb(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        return exprGen.translateExpression(call.args.single())
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        // TODO to be more strict, maybe we *should* introduce a new result register that is of type .b?
    }

    private fun funcMsb(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        val resultReg = codeGen.registers.nextFree()
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.MSIG, IRDataType.BYTE, reg1 = resultReg, reg2 = tr.resultReg)
        }
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
    }

    private fun funcRolRor(opcode: Opcode, call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val vmDt = irType(call.args[0].type)
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(opcode, vmDt, reg1 = tr.resultReg)
        }
        result += assignRegisterTo(call.args[0], tr.resultReg)
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
                        val pointerReg = codeGen.registers.nextFree()
                        it += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = pointerReg, labelSymbol = target.name)
                        if (msb)
                            it += IRInstruction(Opcode.INC, IRDataType.WORD, reg1 = pointerReg)
                        it += IRInstruction(Opcode.STOREZI, IRDataType.BYTE, reg1 = pointerReg)
                    }
                } else {
                    val valueTr = exprGen.translateExpression(call.args[1])
                    addToResult(result, valueTr, valueTr.resultReg, -1)
                    result += IRCodeChunk(null, null).also {
                        val pointerReg = codeGen.registers.nextFree()
                        it += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = pointerReg, labelSymbol = target.name)
                        if (msb)
                            it += IRInstruction(Opcode.INC, IRDataType.WORD, reg1 = pointerReg)
                        it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = valueTr.resultReg, reg2 = pointerReg)
                    }
                }
            }
            is PtArrayIndexer -> {
                require(!target.usesPointerVariable)
                if(target.splitWords) {
                    // lsb/msb in split arrays, element index 'size' is always 1
                    val constIndex = target.index.asConstInteger()
                    val varName = target.variable.name + if(msb) "_msb" else "_lsb"
                    if(isConstZeroValue) {
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.nextFree()
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
                            val offsetReg = codeGen.registers.nextFree()
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
                    val eltSize = codeGen.program.memsizer.memorySize(target.type)
                    val constIndex = target.index.asConstInteger()
                    if(isConstZeroValue) {
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.nextFree()
                            val offset = eltSize*constIndex + if(msb) 1 else 0
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = offset)
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=offsetReg, labelSymbol = target.variable.name)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                if(eltSize>1)
                                    it += codeGen.multiplyByConst(IRDataType.BYTE, indexTr.resultReg, eltSize)
                                if(msb)
                                    it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexTr.resultReg)
                                it += IRInstruction(Opcode.STOREZX, IRDataType.BYTE, reg1=indexTr.resultReg, labelSymbol = target.variable.name)
                            }
                        }
                    } else {
                        val valueTr = exprGen.translateExpression(call.args[1])
                        addToResult(result, valueTr, valueTr.resultReg, -1)
                        if(constIndex!=null) {
                            val offsetReg = codeGen.registers.nextFree()
                            val offset = eltSize*constIndex + if(msb) 1 else 0
                            result += IRCodeChunk(null, null).also {
                                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = offset)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=offsetReg, labelSymbol = target.variable.name)
                            }
                        } else {
                            val indexTr = exprGen.translateExpression(target.index)
                            addToResult(result, indexTr, indexTr.resultReg, -1)
                            result += IRCodeChunk(null, null).also {
                                if(eltSize>1)
                                    it += codeGen.multiplyByConst(IRDataType.BYTE, indexTr.resultReg, eltSize)
                                if(msb)
                                    it += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=indexTr.resultReg)
                                it += IRInstruction(Opcode.STOREX, IRDataType.BYTE, reg1=valueTr.resultReg, reg2=indexTr.resultReg, labelSymbol = target.variable.name)
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
        val assignTarget = PtAssignTarget(target.position)
        assignTarget.children.add(target)
        assignment.children.add(assignTarget)
        assignment.children.add(PtMachineRegister(register, target.type, target.position))
        val result = mutableListOf<IRCodeChunkBase>()
        result += codeGen.translateNode(assignment)
        return result
    }
}
