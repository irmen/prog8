package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.vm.Opcode
import prog8.vm.Syscall
import prog8.vm.VmDataType

internal class BuiltinFuncGen(private val codeGen: CodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        return when(call.name) {
            "syscall" -> funcSyscall(call)
            "syscall1" -> funcSyscall1(call)
            "syscall2" -> funcSyscall2(call)
            "syscall3" -> funcSyscall3(call)
            "msb" -> funcMsb(call, resultRegister)
            "lsb" -> funcLsb(call, resultRegister)
            "memory" -> funcMemory(call, resultRegister)
            "rnd" -> funcRnd(call, resultRegister)
            "peek" -> funcPeek(call, resultRegister)
            "peekw" -> funcPeekW(call, resultRegister)
            "poke" -> funcPoke(call, resultRegister)
            "pokew" -> funcPokeW(call, resultRegister)
            "pokemon" -> VmCodeChunk()
            "mkword" -> funcMkword(call, resultRegister)
            "sin8u" -> funcSin8u(call, resultRegister)
            "cos8u" -> funcCos8u(call, resultRegister)
            "sort" -> funcSort(call)
            "reverse" -> funcReverse(call)
            "rol" -> funcRolRor2(Opcode.ROXL, call, resultRegister)
            "ror" -> funcRolRor2(Opcode.ROXR, call, resultRegister)
            "rol2" -> funcRolRor2(Opcode.ROL, call, resultRegister)
            "ror2" -> funcRolRor2(Opcode.ROR, call, resultRegister)
            else -> {
                TODO("builtinfunc ${call.name}")
//                code += VmCodeInstruction(Opcode.NOP))
//                for (arg in call.args) {
//                    code += translateExpression(arg, resultRegister)
//                    code += when(arg.type) {
//                        in ByteDatatypes -> VmCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=resultRegister))
//                        in WordDatatypes -> VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1=resultRegister))
//                        else -> throw AssemblyError("weird arg dt")
//                    }
//                }
//                code += VmCodeInstruction(Opcode.CALL), labelArg = listOf("_prog8_builtin", call.name))
//                for (arg in call.args) {
//                    code += when(arg.type) {
//                        in ByteDatatypes -> VmCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=resultRegister))
//                        in WordDatatypes -> VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1=resultRegister))
//                        else -> throw AssemblyError("weird arg dt")
//                    }
//                }
//                code += VmCodeInstruction(Opcode.NOP))
            }
        }
    }

    private fun funcReverse(call: PtBuiltinFunctionCall): VmCodeChunk {
        val arrayName = call.args[0] as PtIdentifier
        val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
        val sortSyscall =
            when(array.dt) {
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> Syscall.REVERSE_BYTES
                DataType.ARRAY_UW, DataType.ARRAY_W -> Syscall.REVERSE_WORDS
                DataType.FLOAT -> TODO("reverse floats")
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
                DataType.FLOAT -> TODO("float sort")
                DataType.STR -> Syscall.SORT_UBYTE
                else -> throw IllegalArgumentException("weird type to sort")
            }
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
        code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
        return code
    }

    private fun funcCos8u(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.SYSCALL, value=Syscall.COS8U.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
        return code
    }

    private fun funcSin8u(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], 0)
        code += VmCodeInstruction(Opcode.SYSCALL, value=Syscall.SIN8U.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
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

    private fun funcPokeW(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val addressReg = codeGen.vmRegisters.nextFree()
        val valueReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], addressReg)
        code += exprGen.translateExpression(call.args[1], valueReg)
        code += VmCodeInstruction(Opcode.STOREI, VmDataType.WORD, reg1 = addressReg, reg2=valueReg)
        return code
    }

    private fun funcPoke(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val addressReg = codeGen.vmRegisters.nextFree()
        val valueReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args[0], addressReg)
        code += exprGen.translateExpression(call.args[1], valueReg)
        code += VmCodeInstruction(Opcode.STOREI, VmDataType.BYTE, reg1 = addressReg, reg2=valueReg)
        return code
    }

    private fun funcPeekW(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val addressReg = codeGen.vmRegisters.nextFree()
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), addressReg)
        code += VmCodeInstruction(Opcode.LOADI, VmDataType.WORD, reg1 = resultRegister, reg2=addressReg)
        return code
    }

    private fun funcPeek(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val addressReg = codeGen.vmRegisters.nextFree()
        code += exprGen.translateExpression(call.args.single(), addressReg)
        code += VmCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2=addressReg)
        return code
    }

    private fun funcRnd(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += VmCodeInstruction(Opcode.SYSCALL, value= Syscall.RND.ordinal)
        if(resultRegister!=0)
            code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
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
        // TODO optimized code gen
        val code = VmCodeChunk()
        code += exprGen.translateExpression(call.args.single(), resultRegister)
        // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
        return code
    }

    private fun funcMsb(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        // TODO optimized code gen
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
        val assignment = PtAssignment(call.position)
        val target = PtAssignTarget(call.position)
        target.children.add(call.args[0])
        assignment.children.add(target)
        assignment.children.add(PtIdentifier(listOf(":vmreg-$resultRegister"), listOf(":vmreg-$resultRegister"), call.args[0].type, call.position))
        code += codeGen.translateNode(assignment)
        return code
    }

}
