package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.ArrayToElementTypes
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
            "sin" -> TODO("floats not yet implemented")
            "cos" -> TODO("floats not yet implemented")
            "tan" -> TODO("floats not yet implemented")
            "atan" -> TODO("floats not yet implemented")
            "ln" -> TODO("floats not yet implemented")
            "log2" -> TODO("floats not yet implemented")
            "sqrt16" -> funcSqrt16(call, resultRegister)
            "sqrt" -> TODO("floats not yet implemented")
            "rad" -> TODO("floats not yet implemented")
            "deg" -> TODO("floats not yet implemented")
            "round" -> TODO("floats not yet implemented")
            "floor" -> TODO("floats not yet implemented")
            "ceil" -> TODO("floats not yet implemented")
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
            "rndf" -> TODO("floats not yet implemented")
            "callfar" -> throw AssemblyError("callfar() is for cx16 target only")
            "callrom" -> throw AssemblyError("callrom() is for cx16 target only")
            "syscall" -> funcSyscall(call)
            "syscall1" -> funcSyscall1(call)
            "syscall2" -> funcSyscall2(call)
            "syscall3" -> funcSyscall3(call)
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
            "swap" -> funcSwap(call)
            "rol" -> funcRolRor2(Opcode.ROXL, call, resultRegister)
            "ror" -> funcRolRor2(Opcode.ROXR, call, resultRegister)
            "rol2" -> funcRolRor2(Opcode.ROL, call, resultRegister)
            "ror2" -> funcRolRor2(Opcode.ROR, call, resultRegister)
            else -> TODO("builtinfunc ${call.name}")
        }
    }

    private fun funcCmp(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val leftRegister = codeGen.vmRegisters.nextFree()
        val rightRegister = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args[0], leftRegister)
        code += exprGen.translateExpression(call.args[1], rightRegister)
        code += VmCodeInstruction(Opcode.CMP, codeGen.vmType(call.args[0].type), reg1=leftRegister, reg2=rightRegister)
        return code
    }

    private fun funcAny(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val elementDt: VmDataType = codeGen.vmType(ArrayToElementTypes.getValue(array.dt))
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> Syscall.ANY_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> Syscall.ANY_WORD
                DataType.ARRAY_F -> TODO("float any")
                else -> throw IllegalArgumentException("weird type")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, elementDt, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcAll(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val elementDt: VmDataType = codeGen.vmType(ArrayToElementTypes.getValue(array.dt))
        val syscall =
            when(array.dt) {
                DataType.ARRAY_UB,
                DataType.ARRAY_B -> Syscall.ALL_BYTE
                DataType.ARRAY_UW,
                DataType.ARRAY_W -> Syscall.ALL_WORD
                DataType.ARRAY_F -> TODO("float all")
                else -> throw IllegalArgumentException("weird type")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=syscall.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, elementDt, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcAbs(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val sourceDt = call.args.single().type
        if(sourceDt!=DataType.UWORD) {
            code += exprGen.translateExpression(call.args[0], resultRegister)
            when (sourceDt) {
                DataType.UBYTE -> {
                    code += VmCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                }
                DataType.BYTE -> {
                    val andReg = codeGen.vmRegisters.nextFree()
                    val notNegativeLabel = codeGen.createLabelName()
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=andReg, value=0x80)
                    code += VmCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=andReg, reg2=resultRegister, reg3=andReg)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=andReg, symbol = notNegativeLabel)
                    code += VmCodeInstruction(Opcode.NEG, VmDataType.BYTE, reg1=resultRegister)
                    code += VmCodeInstruction(Opcode.EXT, VmDataType.BYTE, reg1=resultRegister)
                    code += VmCodeLabel(notNegativeLabel)
                }
                DataType.WORD -> {
                    val andReg = codeGen.vmRegisters.nextFree()
                    val notNegativeLabel = codeGen.createLabelName()
                    code += VmCodeInstruction(Opcode.LOAD, VmDataType.WORD, reg1=andReg, value=0x8000)
                    code += VmCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=andReg, reg2=resultRegister, reg3=andReg)
                    code += VmCodeInstruction(Opcode.BZ, VmDataType.WORD, reg1=andReg, symbol = notNegativeLabel)
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
        code += exprGen.translateExpression(call.args.single(), 0)
        code += VmCodeInstruction(Opcode.SGN, codeGen.vmType(call.type), reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcSqrt16(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), 0)
        code += VmCodeInstruction(Opcode.SQRT, VmDataType.WORD, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcPop(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=0)
        code += assignRegisterTo(call.args.single(), 0)
        return code
    }

    private fun funcPopw(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1=0)
        code += assignRegisterTo(call.args.single(), 0)
        return code
    }

    private fun funcPush(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), 0)
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=0)
        return code
    }

    private fun funcPushw(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), 0)
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1=0)
        return code
    }

    private fun funcSwap(call: PtBuiltinFunctionCall): VmCodeChunk {
        val left = call.args[0]
        val right = call.args[1]
        val leftReg = codeGen.vmRegisters.nextFree()
        val rightReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(left, leftReg)
        code += exprGen.translateExpression(right, rightReg)
        code += assignRegisterTo(left, rightReg)
        code += assignRegisterTo(right, leftReg)
        return code
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val sortSyscall =
            when(array.dt) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> Syscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> Syscall.REVERSE_WORDS
                DataType.ARRAY_F -> TODO("float reverse")
                else -> throw IllegalArgumentException("weird type to reverse")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
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
                DataType.ARRAY_F -> TODO("float sort")
                DataType.STR -> Syscall.SORT_UBYTE
                else -> throw IllegalArgumentException("weird type to sort")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
        return code
    }

    private fun funcMkword(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val msbReg = codeGen.vmRegisters.nextFree()
        val lsbReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], msbReg)
        code += exprGen.translateExpression(call.args[1], lsbReg)
        code += VmCodeInstruction(Opcode.CONCAT, VmDataType.BYTE, reg1=resultRegister, reg2=msbReg, reg3=lsbReg)
        return code
    }

    private fun funcPokeW(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val valueReg = codeGen.vmRegisters.nextFree()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += exprGen.translateExpression(call.args[1], valueReg)
            code += VmCodeInstruction(Opcode.STOREM, VmDataType.WORD, reg1 = valueReg, value=address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args[0], addressReg)
            code += exprGen.translateExpression(call.args[1], valueReg)
            code += VmCodeInstruction(Opcode.STOREI, VmDataType.WORD, reg1 = valueReg, reg2 = addressReg)
        }
        return code
    }

    private fun funcPoke(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val valueReg = codeGen.vmRegisters.nextFree()
        if(call.args[0] is PtNumber) {
            val address = (call.args[0] as PtNumber).number.toInt()
            code += exprGen.translateExpression(call.args[1], valueReg)
            code += VmCodeInstruction(Opcode.STOREM, VmDataType.BYTE, reg1 = valueReg, value=address)
        } else {
            val addressReg = codeGen.vmRegisters.nextFree()
            code += exprGen.translateExpression(call.args[0], addressReg)
            code += exprGen.translateExpression(call.args[1], valueReg)
            code += VmCodeInstruction(Opcode.STOREI, VmDataType.BYTE, reg1 = valueReg, reg2 = addressReg)
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
            code += exprGen.translateExpression(call.args.single(), addressReg)
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
            code += exprGen.translateExpression(call.args.single(), addressReg)
            code += VmCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2 = addressReg)
        }
        return code
    }

    private fun funcRnd(resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.SYSCALL, value= Syscall.RND.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcRndw(resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.SYSCALL, value= Syscall.RNDW.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.WORD, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcMemory(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val name = (call.args[0] as PtString).value
        val size = (call.args[1] as PtNumber).number.toUInt()
        val align = (call.args[2] as PtNumber).number.toUInt()
        val existing = codeGen.allocations.getMemorySlab(name)
        val address = if(existing==null)
            codeGen.allocations.allocateMemorySlab(name, size, align)
        else if(existing.second!=size || existing.third!=align) {
            codeGen.errors.err("memory slab '$name' already exists with a different size or alignment", call.position)
            return VmCodeChunk()
        }
        else
            existing.first
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.WORD, reg1=resultRegister, value=address.toInt())
        return code
    }

    private fun funcLsb(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), resultRegister)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), resultRegister)
        code += VmCodeInstruction(Opcode.MSIG, VmDataType.BYTE, reg1 = resultRegister, reg2=resultRegister)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcSyscall(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val vExpr = call.args.single() as PtNumber
        code += VmCodeInstruction(Opcode.SYSCALL, value=vExpr.number.toInt())
        return code
    }

    private fun funcSyscall1(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        val callNr = (call.args[0] as PtNumber).number.toInt()
        code += exprGen.translateExpression(call.args[1], 0)
        code += VmCodeInstruction(Opcode.SYSCALL, value=callNr)
        return code
    }

    private fun funcSyscall2(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1)
        while(codeGen.vmRegisters.peekNext()<2) {
            codeGen.vmRegisters.nextFree()
        }
        val callNr = (call.args[0] as PtNumber).number.toInt()
        code += exprGen.translateExpression(call.args[1], 0)
        code += exprGen.translateExpression(call.args[2], 1)
        code += VmCodeInstruction(Opcode.SYSCALL, value=callNr)
        code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1 = 1)
        return code
    }

    private fun funcSyscall3(call: PtBuiltinFunctionCall): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1)
        code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1 = 2)
        while(codeGen.vmRegisters.peekNext()<3) {
            codeGen.vmRegisters.nextFree()
        }
        val callNr = (call.args[0] as PtNumber).number.toInt()
        code += exprGen.translateExpression(call.args[1], 0)
        code += exprGen.translateExpression(call.args[2], 1)
        code += exprGen.translateExpression(call.args[3], 2)
        code += VmCodeInstruction(Opcode.SYSCALL, value=callNr)
        code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1 = 2)
        code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1 = 1)
        return code
    }

    private fun funcRolRor2(opcode: Opcode, call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val vmDt = codeGen.vmType(call.args[0].type)
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], resultRegister)
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
