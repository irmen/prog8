package prog8.codegen.intermediate

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.intermediate.*


internal class BuiltinFuncGen(private val codeGen: IRCodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
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
            "rrestorex" -> IRCodeChunk(call.position) // vm doesn't have registers to save/restore
            "rnd" -> funcRnd(resultRegister, call.position)
            "rndw" -> funcRndw(resultRegister, call.position)
            "callfar" -> throw AssemblyError("callfar() is for cx16 target only")
            "callrom" -> throw AssemblyError("callrom() is for cx16 target only")
            "msb" -> funcMsb(call, resultRegister)
            "lsb" -> funcLsb(call, resultRegister)
            "memory" -> funcMemory(call, resultRegister)
            "peek" -> funcPeek(call, resultRegister)
            "peekw" -> funcPeekW(call, resultRegister)
            "poke" -> funcPoke(call)
            "pokew" -> funcPokeW(call)
            "pokemon" -> IRCodeChunk(call.position)
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

    private fun funcCmp(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val leftRegister = codeGen.vmRegisters.nextFree()
        val rightRegister = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args[0], leftRegister, -1)
        code += exprGen.translateExpression(call.args[1], rightRegister, -1)
        code += IRCodeInstruction(Opcode.CMP, codeGen.vmType(call.args[0].type), reg1=leftRegister, reg2=rightRegister)
        return code
    }

    private fun funcAny(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val code = IRCodeChunk(call.position)
        val syscall =
            when (array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ANY_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ANY_WORD
                DataType.ARRAY_F -> IMSyscall.ANY_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1 = 1, value = array.length)
        code += IRCodeInstruction(Opcode.SYSCALL, value = syscall.ordinal)
        if (resultRegister != 0)
            code += IRCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1 = resultRegister, reg2 = 0)
        return code
    }

    private fun funcAll(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> IMSyscall.ALL_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> IMSyscall.ALL_WORD
                DataType.ARRAY_F -> IMSyscall.ALL_FLOAT
                else -> throw IllegalArgumentException("weird type")
            }
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += IRCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        if(resultRegister!=0)
            code += IRCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcAbs(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val sourceDt = call.args.single().type
        if(sourceDt!=DataType.UWORD) {
            code += exprGen.translateExpression(call.args[0], resultRegister, -1)
            when (sourceDt) {
                DataType.UBYTE -> {
                    code += IRCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                }
                DataType.BYTE -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.vmRegisters.nextFree()
                    code += IRCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=compareReg, reg2=resultRegister)
                    code += IRCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=compareReg, value=0x80)
                    code += IRCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=compareReg, labelSymbol = notNegativeLabel)
                    code += IRCodeInstruction(Opcode.NEG, VmDataType.BYTE, reg1=resultRegister)
                    code += IRCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                    code += IRCodeLabel(notNegativeLabel)
                }
                DataType.WORD -> {
                    val notNegativeLabel = codeGen.createLabelName()
                    val compareReg = codeGen.vmRegisters.nextFree()
                    code += IRCodeInstruction(Opcode.LOADR, VmDataType.WORD, reg1=compareReg, reg2=resultRegister)
                    code += IRCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=compareReg, value=0x8000)
                    code += IRCodeInstruction(Opcode.BZ, VmDataType.WORD, reg1=compareReg, labelSymbol = notNegativeLabel)
                    code += IRCodeInstruction(Opcode.NEG, VmDataType.WORD, reg1=resultRegister)
                    code += IRCodeLabel(notNegativeLabel)
                }
                else -> throw AssemblyError("weird type")
            }
        }
        return code
    }

    private fun funcSgn(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += IRCodeInstruction(Opcode.SGN, codeGen.vmType(call.type), reg1=resultRegister, reg2=reg)
        return code
    }

    private fun funcSqrt16(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += IRCodeInstruction(Opcode.SQRT, VmDataType.WORD, reg1=resultRegister, reg2=reg)
        return code
    }

    private fun funcPop(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += IRCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=reg)
        code += assignRegisterTo(call.args.single(), reg)
        return code
    }

    private fun funcPopw(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += IRCodeInstruction(Opcode.POP, VmDataType.WORD, reg1=reg)
        code += assignRegisterTo(call.args.single(), reg)
        return code
    }

    private fun funcPush(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += IRCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=reg)
        return code
    }

    private fun funcPushw(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        val reg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), reg, -1)
        code += IRCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1=reg)
        return code
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): IRCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> IMSyscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> IMSyscall.REVERSE_WORDS
                DataType.ARRAY_F -> IMSyscall.REVERSE_FLOATS
                else -> throw IllegalArgumentException("weird type to reverse")
            }
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += IRCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        return code
    }

    private fun funcSort(call: PtBuiltinFunctionCall): IRCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
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
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args[0], 0, -1)
        code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += IRCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        return code
    }

    private fun funcMkword(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val msbReg = codeGen.vmRegisters.nextFree()
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args[0], msbReg, -1)
        code += exprGen.translateExpression(call.args[1], resultRegister, -1)
        code += IRCodeInstruction(Opcode.CONCAT, VmDataType.BYTE, reg1=resultRegister, reg2=msbReg)
        return code
    }

    private fun funcPokeW(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += IRCodeInstruction(Opcode.STOREZM, VmDataType.WORD, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += IRCodeInstruction(Opcode.STOREZI, VmDataType.WORD, reg2 = addressReg)
            }
        } else {
            val valueReg = codeGen.vmRegisters.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += IRCodeInstruction(Opcode.STOREM, VmDataType.WORD, reg1 = valueReg, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += IRCodeInstruction(Opcode.STOREI, VmDataType.WORD, reg1 = valueReg, reg2 = addressReg)
            }
        }
        return code
    }

    private fun funcPoke(call: PtBuiltinFunctionCall): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        if(codeGen.isZero(call.args[1])) {
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += IRCodeInstruction(Opcode.STOREZM, VmDataType.BYTE, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += IRCodeInstruction(Opcode.STOREZI, VmDataType.BYTE, reg2 = addressReg)
            }
        } else {
            val valueReg = codeGen.vmRegisters.nextFree()
            if (call.args[0] is PtNumber) {
                val address = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += IRCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1 = valueReg, value = address)
            } else {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg, -1)
                code += exprGen.translateExpression(call.args[1], valueReg, -1)
                code += IRCodeInstruction(Opcode.STOREI, VmDataType.BYTE, reg1 = valueReg, reg2 = addressReg)
            }
        }
        return code
    }

    private fun funcPeekW(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += IRCodeInstruction(Opcode.LOADM, VmDataType.WORD, reg1 = resultRegister, value = address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args.single(), addressReg, -1)
            code += IRCodeInstruction(Opcode.LOADI, VmDataType.WORD, reg1 = resultRegister, reg2 = addressReg)
        }
        return code
    }

    private fun funcPeek(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += IRCodeInstruction(Opcode.LOADM, VmDataType.BYTE, reg1 = resultRegister, value = address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args.single(), addressReg, -1)
            code += IRCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2 = addressReg)
        }
        return code
    }

    private fun funcRnd(resultRegister: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        code += IRCodeInstruction(Opcode.RND, VmDataType.BYTE, reg1=resultRegister)
        return code
    }

    private fun funcRndw(resultRegister: Int, position: Position): IRCodeChunk {
        val code = IRCodeChunk(position)
        code += IRCodeInstruction(Opcode.RND, VmDataType.WORD, reg1=resultRegister)
        return code
    }

    private fun funcMemory(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val name = (call.args[0] as PtString).value
        val size = (call.args[1] as PtNumber).number.toUInt()
        val align = (call.args[2] as PtNumber).number.toUInt()
        val label = codeGen.addMemorySlab(name, size, align, call.position)
        val code = IRCodeChunk(call.position)
        code += IRCodeInstruction(Opcode.LOAD, VmDataType.WORD, reg1=resultRegister, labelSymbol = label)
        return code
    }

    private fun funcLsb(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args.single(), resultRegister, -1)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args.single(), resultRegister, -1)
        code += IRCodeInstruction(Opcode.MSIG, VmDataType.BYTE, reg1 = resultRegister, reg2=resultRegister)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcRolRor(opcode: Opcode, call: PtBuiltinFunctionCall, resultRegister: Int): IRCodeChunk {
        val vmDt = codeGen.vmType(call.args[0].type)
        val code = IRCodeChunk(call.position)
        code += exprGen.translateExpression(call.args[0], resultRegister, -1)
        code += IRCodeInstruction(opcode, vmDt, reg1=resultRegister)
        code += assignRegisterTo(call.args[0], resultRegister)
        return code
    }

    private fun assignRegisterTo(target: PtExpression, register: Int): IRCodeChunk {
        val code = IRCodeChunk(target.position)
        val assignment = PtAssignment(target.position)
        val assignTarget = PtAssignTarget(target.position)
        assignTarget.children.add(target)
        assignment.children.add(assignTarget)
        assignment.children.add(PtMachineRegister(register, target.type, target.position))
        code += codeGen.translateNode(assignment)
        return code
    }
}
