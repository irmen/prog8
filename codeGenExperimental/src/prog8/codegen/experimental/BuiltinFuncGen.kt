package prog8.codegen.experimental

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.vm.Opcode
import prog8.vm.Syscall
import prog8.vm.VmDataType

internal class BuiltinFuncGen(private val codeGen: CodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
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
            "rrestorex" -> VmCodeChunk() // vm doesn't have registers to save/restore
            "rnd" -> funcRnd(resultRegister)
            "rndw" -> funcRndw(resultRegister)
            "callfar" -> throw AssemblyError("callfar() is for cx16 target only")
            "callrom" -> throw AssemblyError("callrom() is for cx16 target only")
            "msb" -> funcMsb(call, resultRegister)
            "lsb" -> funcLsb(call, resultRegister)
            "memory" -> funcMemory(call, resultRegister)
            "peek" -> funcPeek(call, resultRegister)
            "peekw" -> funcPeekW(call, resultRegister)
            "poke" -> funcPoke(call)
            "pokew" -> funcPokeW(call)
            "pokemon" -> VmCodeChunk()
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

    private fun funcCmp(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val leftRegister = codeGen.vmRegisters.nextFree()
        val rightRegister = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args[0], leftRegister, -1)
        code += exprGen.translateExpression(call.args[1], rightRegister, -1)
        code += VmCodeInstruction(Opcode.CMP, codeGen.vmType(call.args[0].type), reg1=leftRegister, reg2=rightRegister)
        return code
    }

    private fun funcAny(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val code = VmCodeChunk()
        val syscall =
            when (array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> Syscall.ANY_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> Syscall.ANY_WORD
                DataType.ARRAY_F -> Syscall.ANY_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1 = 1, value = array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value = syscall.ordinal)
        if (resultRegister != 0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1 = resultRegister, reg2 = 0)
        return code
    }

    private fun funcAll(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> Syscall.ALL_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> Syscall.ALL_WORD
                DataType.ARRAY_F -> Syscall.ALL_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcAbs(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val sourceDt = call.args.single().type
        if(sourceDt!=DataType.UWORD) {
            code += exprGen.translateExpression(call.args[0], resultRegister, -1)
            when (sourceDt) {
                DataType.UBYTE -> {
                    code += VmCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                }
                DataType.BYTE -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=compareReg, reg2=resultRegister)
                    code += VmCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=compareReg, value=0x80)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=compareReg, labelSymbol = notNegativeLabel)
                    code += VmCodeInstruction(Opcode.NEG, VmDataType.BYTE, reg1=resultRegister)
                    code += VmCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                    code += VmCodeLabel(notNegativeLabel)
                }
                DataType.WORD -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.vmRegisters.nextFree()
                    code += VmCodeInstruction(Opcode.LOADR, VmDataType.WORD, reg1=compareReg, reg2=resultRegister)
                    code += VmCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=compareReg, value=0x8000)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.WORD, reg1=compareReg, labelSymbol = notNegativeLabel)
                    code += VmCodeInstruction(Opcode.NEG, VmDataType.WORD, reg1=resultRegister)
                    code += VmCodeLabel(notNegativeLabel)
                }
                else -> throw AssemblyError("weird type")
            }
        }
        return code
    }

    private fun funcSgn(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += VmCodeInstruction(Opcode.SGN, codeGen.vmType(call.type), reg1=resultRegister, reg2=reg)
        return code
    }

    private fun funcSqrt16(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += VmCodeInstruction(Opcode.SQRT, VmDataType.WORD, reg1=resultRegister, reg2=reg)
        return code
    }

    private fun funcPop(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += VmCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=reg)
        code += assignRegisterTo(call.args.single(), reg)
        return code
    }

    private fun funcPopw(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1=reg)
        code += assignRegisterTo(call.args.single(), reg)
        return code
    }

    private fun funcPush(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=reg)
        return code
    }

    private fun funcPushw(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1=reg)
        return code
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val sortSyscall =
            when(array.dt) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> Syscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> Syscall.REVERSE_WORDS
                DataType.ARRAY_F -> Syscall.REVERSE_FLOATS
                else -> throw IllegalArgumentException("weird type to reverse")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
        return code
    }

    private fun funcSort(call: PtBuiltinFunctionCall): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val sortSyscall =
            when(array.dt) {
                DataType.ARRAY_UB -> Syscall.SORT_UBYTE
                DataType.ARRAY_B -> Syscall.SORT_BYTE
                DataType.ARRAY_UW -> Syscall.SORT_UWORD
                DataType.ARRAY_W -> Syscall.SORT_WORD
                DataType.STR -> Syscall.SORT_UBYTE
                DataType.ARRAY_F -> throw IllegalArgumentException("sorting a floating point array is not supported")
                else -> throw IllegalArgumentException("weird type to sort")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
        return code
    }

    private fun funcMkword(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val msbReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], msbReg, -1)
        code += exprGen.translateExpression(call.args[1], resultRegister, -1)
        code += VmCodeInstruction(Opcode.CONCAT, VmDataType.BYTE, reg1=resultRegister, reg2=msbReg)
        return code
    }

    private fun funcPokeW(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += VmCodeInstruction(Opcode.STOREZM, VmDataType.WORD, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += VmCodeInstruction(Opcode.STOREZI, VmDataType.WORD, reg2 = addressReg)
            }
        } else {
            val valueReg = codeGen.vmRegisters.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += VmCodeInstruction(Opcode.STOREM, VmDataType.WORD, reg1 = valueReg, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += VmCodeInstruction(Opcode.STOREI, VmDataType.WORD, reg1 = valueReg, reg2 = addressReg)
            }
        }
        return code
    }

    private fun funcPoke(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += VmCodeInstruction(Opcode.STOREZM, VmDataType.BYTE, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += VmCodeInstruction(Opcode.STOREZI, VmDataType.BYTE, reg2 = addressReg)
            }
        } else {
            val valueReg = codeGen.vmRegisters.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += VmCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1 = valueReg, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += VmCodeInstruction(Opcode.STOREI, VmDataType.BYTE, reg1 = valueReg, reg2 = addressReg)
            }
        }
        return code
    }

    private fun funcPeekW(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += VmCodeInstruction(Opcode.LOADM, VmDataType.WORD, reg1 = resultRegister, value = address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args.single(), addressReg, -1)
            code += VmCodeInstruction(Opcode.LOADI, VmDataType.WORD, reg1 = resultRegister, reg2 = addressReg)
        }
        return code
    }

    private fun funcPeek(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += VmCodeInstruction(Opcode.LOADM, VmDataType.BYTE, reg1 = resultRegister, value = address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args.single(), addressReg, -1)
            code += VmCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2 = addressReg)
        }
        return code
    }

    private fun funcRnd(resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.RND, VmDataType.BYTE, reg1=resultRegister)
        return code
    }

    private fun funcRndw(resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.RND, VmDataType.WORD, reg1=resultRegister)
        return code
    }

    private fun funcMemory(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val name = (call.args[0] as PtString).value
        val size = (call.args[1] as PtNumber).number.toUInt()
        val align = (call.args[2] as PtNumber).number.toUInt()
        val label = codeGen.addMemorySlab(name, size, align, call.position)
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.WORD, reg1=resultRegister, labelSymbol = listOf(label))
        return code
    }

    private fun funcLsb(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), resultRegister, -1)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), resultRegister, -1)
        code += VmCodeInstruction(Opcode.MSIG, VmDataType.BYTE, reg1 = resultRegister, reg2=resultRegister)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcRolRor(opcode: Opcode, call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val vmDt = codeGen.vmType(call.args[0].type)
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], resultRegister, -1)
        code += VmCodeInstruction(opcode, vmDt, reg1=resultRegister)
        code += assignRegisterTo(call.args[0], resultRegister)
        return code
    }

    private fun assignRegisterTo(target: PtExpression, register: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val assignment = PtAssignment(target.position)
        val assignTarget = PtAssignTarget(target.position)
        assignTarget.children.add(target)
        assignment.children.add(assignTarget)
        assignment.children.add(PtMachineRegister(register, target.type, target.position))
        code += codeGen.translateNode(assignment)
        return code
    }
}
