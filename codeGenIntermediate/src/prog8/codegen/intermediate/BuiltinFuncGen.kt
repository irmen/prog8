package prog8.codegen.intermediate

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        return when(call.name) {
            "any" -> funcAny(call, resultRegister)
            "all" -> funcAll(call, resultRegister)
            "abs" -> funcAbs(call, resultRegister)
            "cmp" -> funcCmp(call)
            "sgn" -> funcSgn(call, resultRegister)
            "sqrt16" -> funcSqrt16(call, resultRegister)
            "pop" -> funcPop(call)
            "popw" -> funcPopw(call)
            "push" -> funcPush(call)
            "pushw" -> funcPushw(call)
            "rsave",
            "rsavex",
            "rrestore",
            "rrestorex" -> emptyList()  // vm doesn't have registers to save/restore
            "callfar" -> throw AssemblyError("callfar() is for cx16 target only")
            "msb" -> funcMsb(call, resultRegister)
            "lsb" -> funcLsb(call, resultRegister)
            "memory" -> funcMemory(call, resultRegister)
            "peek" -> funcPeek(call, resultRegister)
            "peekw" -> funcPeekW(call, resultRegister)
            "poke" -> funcPoke(call)
            "pokew" -> funcPokeW(call)
            "pokemon" -> emptyList()
            "mkword" -> funcMkword(call, resultRegister)
            "sort" -> funcSort(call)
            "reverse" -> funcReverse(call)
            "rol" -> funcRolRor(Opcode.ROXL, call, resultRegister)
            "ror" -> funcRolRor(Opcode.ROXR, call, resultRegister)
            "rol2" -> funcRolRor(Opcode.ROL, call, resultRegister)
            "ror2" -> funcRolRor(Opcode.ROR, call, resultRegister)
            else -> throw AssemblyError("missing builtinfunc for ${call.name}")
        }
    }

    private fun funcCmp(call: PtBuiltinFunctionCall): IRCodeChunks {
        val leftRegister = codeGen.registers.nextFree()
        val rightRegister = codeGen.registers.nextFree()
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args[0], leftRegister, -1)
        result += exprGen.translateExpression(call.args[1], rightRegister, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CMP, codeGen.irType(call.args[0].type), reg1=leftRegister, reg2=rightRegister)
        }
        return result
    }

    private fun funcAny(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
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
        result += exprGen.translateExpression(call.args[0], SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
            if(resultRegister!=0)
                it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1 = resultRegister, reg2 = 0)
        }
        return result
    }

    private fun funcAll(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
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
        result += exprGen.translateExpression(call.args[0], SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
            if(resultRegister!=0)
                it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1 = resultRegister, reg2 = 0)
        }
        return result
    }

    private fun funcAbs(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val sourceDt = call.args.single().type
        val result = mutableListOf<IRCodeChunkBase>()
        if(sourceDt!=DataType.UWORD) {
            result += exprGen.translateExpression(call.args[0], resultRegister, -1)
            when (sourceDt) {
                DataType.UBYTE -> {
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1 = resultRegister)
                    }
                }
                DataType.BYTE -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.registers.nextFree()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=compareReg, reg2=resultRegister)
                        it += IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=compareReg, value=0x80)
                        it += IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=compareReg, labelSymbol = notNegativeLabel)
                        it += IRInstruction(Opcode.NEG, IRDataType.BYTE, reg1=resultRegister)
                        it += IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=resultRegister)
                    }
                    result += IRCodeChunk(notNegativeLabel, null)
                }
                DataType.WORD -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.registers.nextFree()
                    result += IRCodeChunk(null, null).also {
                        it += IRInstruction(Opcode.LOADR, IRDataType.WORD, reg1=compareReg, reg2=resultRegister)
                        it += IRInstruction(Opcode.AND, IRDataType.WORD, reg1=compareReg, value=0x8000)
                        it += IRInstruction(Opcode.BZ, IRDataType.WORD, reg1=compareReg, labelSymbol = notNegativeLabel)
                        it += IRInstruction(Opcode.NEG, IRDataType.WORD, reg1=resultRegister)
                    }
                    result += IRCodeChunk(notNegativeLabel, null)
                }
                else -> throw AssemblyError("weird type")
            }
        }
        return result
    }

    private fun funcSgn(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val reg = codeGen.registers.nextFree()
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args.single(), reg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.SGN, codeGen.irType(call.type), reg1 = resultRegister, reg2 = reg)
        }
        return result
    }

    private fun funcSqrt16(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val reg = codeGen.registers.nextFree()
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args.single(), reg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.SQRT, IRDataType.WORD, reg1=resultRegister, reg2=reg)
        }
        return result
    }

    private fun funcPop(call: PtBuiltinFunctionCall): IRCodeChunks {
        val code = IRCodeChunk(null, null)
        val reg = codeGen.registers.nextFree()
        code += IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=reg)
        val result = mutableListOf<IRCodeChunkBase>(code)
        result += assignRegisterTo(call.args.single(), reg)
        return result
    }

    private fun funcPopw(call: PtBuiltinFunctionCall): IRCodeChunks {
        val code = IRCodeChunk(null, null)
        val reg = codeGen.registers.nextFree()
        code += IRInstruction(Opcode.POP, IRDataType.WORD, reg1=reg)
        val result = mutableListOf<IRCodeChunkBase>(code)
        result += assignRegisterTo(call.args.single(), reg)
        return result
    }

    private fun funcPush(call: PtBuiltinFunctionCall): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val reg = codeGen.registers.nextFree()
        result += exprGen.translateExpression(call.args.single(), reg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=reg)
        }
        return result
    }

    private fun funcPushw(call: PtBuiltinFunctionCall): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val reg = codeGen.registers.nextFree()
        result += exprGen.translateExpression(call.args.single(), reg, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.PUSH, IRDataType.WORD, reg1 = reg)
        }
        return result
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): IRCodeChunks {
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
        result += exprGen.translateExpression(call.args[0], SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
        }
        return result
    }

    private fun funcSort(call: PtBuiltinFunctionCall): IRCodeChunks {
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
        result += exprGen.translateExpression(call.args[0], SyscallRegisterBase, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = SyscallRegisterBase+1, value = array.length)
            it += IRInstruction(Opcode.SYSCALL, value = syscall.number)
        }
        return result
    }

    private fun funcMkword(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val msbReg = codeGen.registers.nextFree()
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args[0], msbReg, -1)
        result += exprGen.translateExpression(call.args[1], resultRegister, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1 = resultRegister, reg2 = msbReg)
        }
        return result
    }

    private fun funcPokeW(call: PtBuiltinFunctionCall): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.WORD, value = address)
                }
            } else {
                val addressReg = codeGen.registers.nextFree()
                result += exprGen.translateExpression(call.args[0], addressReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, IRDataType.WORD, reg2 = addressReg)
                }
            }
        } else {
            val valueReg = codeGen.registers.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += exprGen.translateExpression(call.args[1], valueReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1 = valueReg, value = address)
                }
            } else {
                val addressReg = codeGen.registers.nextFree()
                result += exprGen.translateExpression(call.args[0], addressReg, -1)
                result += exprGen.translateExpression(call.args[1], valueReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREI, IRDataType.WORD, reg1 = valueReg, reg2 = addressReg)
                }
            }
        }
        return result
    }

    private fun funcPoke(call: PtBuiltinFunctionCall): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZM, IRDataType.BYTE, value = address)
                }
            } else {
                val addressReg = codeGen.registers.nextFree()
                result += exprGen.translateExpression(call.args[0], addressReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREZI, IRDataType.BYTE, reg2 = addressReg)
                }
            }
        } else {
            val valueReg = codeGen.registers.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                result += exprGen.translateExpression(call.args[1], valueReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1 = valueReg, value = address)
                }
            } else {
                val addressReg = codeGen.registers.nextFree()
                result += exprGen.translateExpression(call.args[0], addressReg, -1)
                result += exprGen.translateExpression(call.args[1], valueReg, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.STOREI, IRDataType.BYTE, reg1 = valueReg, reg2 = addressReg)
                }
            }
        }
        return result
    }

    private fun funcPeekW(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1 = resultRegister, value = address)
            }
        } else {
            val addressReg = codeGen.registers.nextFree()
            result += exprGen.translateExpression(call.args.single(), addressReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.WORD, reg1 = resultRegister, reg2 = addressReg)
            }
        }
        return result
    }

    private fun funcPeek(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1 = resultRegister, value = address)
            }
        } else {
            val addressReg = codeGen.registers.nextFree()
            result += exprGen.translateExpression(call.args.single(), addressReg, -1)
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1 = resultRegister, reg2 = addressReg)
            }
        }
        return result
    }

    private fun funcMemory(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val name = (call.args[0] as PtString).value
        val code = IRCodeChunk(null, null)
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=resultRegister, labelSymbol = "prog8_slabs.prog8_memoryslab_$name")
        return listOf(code)
    }

    private fun funcLsb(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        return exprGen.translateExpression(call.args.single(), resultRegister, -1)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args.single(), resultRegister, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(Opcode.MSIG, IRDataType.BYTE, reg1 = resultRegister, reg2 = resultRegister)
        }
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return result
    }

    private fun funcRolRor(opcode: Opcode, call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunks {
        val vmDt = codeGen.irType(call.args[0].type)
        val result = mutableListOf<IRCodeChunkBase>()
        result += exprGen.translateExpression(call.args[0], resultRegister, -1)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(opcode, vmDt, reg1 = resultRegister)
        }
        result += assignRegisterTo(call.args[0], resultRegister)
        return result
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
