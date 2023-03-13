package prog8.codegen.intermediate

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        return when(call.name) {
            "any" -> funcAny(call)
            "all" -> funcAll(call)
            "abs" -> funcAbs(call)
            "cmp" -> funcCmp(call)
            "sgn" -> funcSgn(call)
            "sqrt16" -> funcSqrt16(call)
            "pop" -> funcPop(call)
            "popw" -> funcPopw(call)
            "push" -> funcPush(call)
            "pushw" -> funcPushw(call)
            "rsave",
            "rsavex",
            "rrestore",
            "rrestorex" -> ExpressionCodeResult.EMPTY  // vm doesn't have registers to save/restore
            "callfar" -> throw AssemblyError("callfar() is for cx16 target only")
            "msb" -> funcMsb(call)
            "lsb" -> funcLsb(call)
            "memory" -> funcMemory(call)
            "peek" -> funcPeek(call)
            "peekw" -> funcPeekW(call)
            "poke" -> funcPoke(call)
            "pokew" -> funcPokeW(call)
            "pokemon" -> ExpressionCodeResult.EMPTY     // easter egg function
            "mkword" -> funcMkword(call)
            "sort" -> funcSort(call)
            "reverse" -> funcReverse(call)
            "rol" -> funcRolRor(Opcode.ROXL, call)
            "ror" -> funcRolRor(Opcode.ROXR, call)
            "rol2" -> funcRolRor(Opcode.ROL, call)
            "ror2" -> funcRolRor(Opcode.ROR, call)
            else -> throw AssemblyError("missing builtinfunc for ${call.name}")
        }
    }

    private fun funcCmp(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val leftTr = exprGen.translateExpression(call.args[0])
        addToResult(result, leftTr, leftTr.resultReg, -1)
        val rightTr = exprGen.translateExpression(call.args[1])
        require(leftTr.resultReg!=rightTr.resultReg)
        addToResult(result, rightTr, rightTr.resultReg, -1)
        val dt = codeGen.irType(call.args[0].type)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CMP, dt, reg1=leftTr.resultReg, reg2=rightTr.resultReg)
        }
        return ExpressionCodeResult(result, dt, leftTr.resultReg, -1)
    }

    private fun funcAny(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.name) as StStaticVariable
        val syscall =
            when (array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ANY_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ANY_WORD
                DataType.ARRAY_F -> IMSyscall.ANY_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
            // SysCall call convention: return value in register r0
            if(tr.resultReg!=0)
                it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1 = tr.resultReg, reg2 = 0)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
    }

    private fun funcAll(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.name) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ALL_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ALL_WORD
                DataType.ARRAY_F -> IMSyscall.ALL_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
            // SysCall call convention: return value in register r0
            if(tr.resultReg!=0)
                it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1 = tr.resultReg, reg2 = 0)
        }
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
            DataType.UBYTE -> {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1 = tr.resultReg)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
            }
            DataType.BYTE -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=compareReg, value=0x80)
                    it += IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=compareReg, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1=tr.resultReg)
                    it += IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
            }
            DataType.WORD -> {
                val notNegativeLabel = codeGen.createLabelName()
                val compareReg = codeGen.registers.nextFree()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADR, IRDataType.WORD, reg1=compareReg, reg2=tr.resultReg)
                    it += IRInstruction(Opcode.AND, IRDataType.WORD, reg1=compareReg, value=0x8000)
                    it += IRInstruction(Opcode.BZ, IRDataType.WORD, reg1=compareReg, labelSymbol = notNegativeLabel)
                    it += IRInstruction(Opcode.NEG, IRDataType.WORD, reg1=tr.resultReg)
                }
                result += IRCodeChunk(notNegativeLabel, null)
                return ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
            }
            else -> throw AssemblyError("weird type")
        }
    }

    private fun funcSgn(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val vmDt = codeGen.irType(call.type)
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.SGN, vmDt, reg1 = tr.resultReg, reg2 = tr.resultReg)
        }
        return ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
    }

    private fun funcSqrt16(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.SQRT, IRDataType.WORD, reg1=tr.resultReg, reg2=tr.resultReg)
        }
        return ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
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
        val array = codeGen.symbolTable.flat.getValue(arrayName.name) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> IMSyscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> IMSyscall.REVERSE_WORDS
                DataType.ARRAY_F -> IMSyscall.REVERSE_FLOATS
                else -> throw IllegalArgumentException("weird type to reverse")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcSort(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.name) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB -> IMSyscall.SORT_UBYTE
                DataType.ARRAY_B -> IMSyscall.SORT_BYTE
                DataType.ARRAY_UW -> IMSyscall.SORT_UWORD
                DataType.ARRAY_W -> IMSyscall.SORT_WORD
                DataType.STR -> IMSyscall.SORT_UBYTE
                DataType.ARRAY_F -> throw IllegalArgumentException("sorting a floating point array is not supported")
                else -> throw IllegalArgumentException("weird type to sort")
            }
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
        }
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
    }

    private fun funcMkword(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val msbTr = exprGen.translateExpression(call.args[0])
        addToResult(result, msbTr, msbTr.resultReg, -1)
        val lsbTr = exprGen.translateExpression(call.args[1])
        require(lsbTr.resultReg!=msbTr.resultReg)
        addToResult(result, lsbTr, lsbTr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1 = lsbTr.resultReg, reg2 = msbTr.resultReg)
        }
        return ExpressionCodeResult(result, IRDataType.WORD, lsbTr.resultReg, -1)
    }

    private fun funcPokeW(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.WORD, value = address)
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
                    it += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = tr.resultReg, value = address)
                }
            } else {
                val addressTr = exprGen.translateExpression(call.args[0])
                addToResult(result, addressTr, addressTr.resultReg, -1)
                val valueTr = exprGen.translateExpression(call.args[1])
                require(valueTr.resultReg!=addressTr.resultReg)
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
                    it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, value = address)
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
                    it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = tr.resultReg, value = address)
                }
            } else {
                val addressTr = exprGen.translateExpression(call.args[0])
                addToResult(result, addressTr, addressTr.resultReg, -1)
                val valueTr = exprGen.translateExpression(call.args[1])
                require(valueTr.resultReg!=addressTr.resultReg)
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
                it += IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1 = resultRegister, value = address)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val tr = exprGen.translateExpression(call.args.single())
            addToResult(result, tr, tr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.WORD, reg1 = tr.resultReg, reg2 = tr.resultReg)
            }
            ExpressionCodeResult(result, IRDataType.WORD, tr.resultReg, -1)
        }
    }

    private fun funcPeek(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(call.args[0] is PtNumber) {
            val resultRegister = codeGen.registers.nextFree()
            val address = (call.args[0] as PtNumber).number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1 = resultRegister, value = address)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            val tr = exprGen.translateExpression(call.args.single())
            addToResult(result, tr, tr.resultReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1 = tr.resultReg, reg2 = tr.resultReg)
            }
            ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
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
    }

    private fun funcMsb(call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args.single())
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.MSIG, IRDataType.BYTE, reg1 = tr.resultReg, reg2 = tr.resultReg)
        }
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
    }

    private fun funcRolRor(opcode: Opcode, call: PtBuiltinFunctionCall): ExpressionCodeResult {
        val vmDt = codeGen.irType(call.args[0].type)
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = exprGen.translateExpression(call.args[0])
        addToResult(result, tr, tr.resultReg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(opcode, vmDt, reg1 = tr.resultReg)
        }
        result += assignRegisterTo(call.args[0], tr.resultReg)
        return ExpressionCodeResult(result, vmDt, -1, -1)
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
