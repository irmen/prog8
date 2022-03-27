package prog8.codegen.virtual

import prog8.code.ast.PtBuiltinFunctionCall
import prog8.code.ast.PtNumber
import prog8.code.ast.PtString
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.Syscall
import prog8.vm.VmDataType

internal class BuiltinFuncGen(val codeGen: CodeGen, val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        when(call.name) {
            "syscall" -> {
                val vExpr = call.args.single() as PtNumber
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall1" -> {
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                val callNr = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], 0, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            "syscall2" -> {
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1))
                while(regUsage.firstFree<2) {
                    regUsage.nextFree()
                }
                val callNr = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], 0, regUsage)
                code += exprGen.translateExpression(call.args[2], 1, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 1))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            "syscall3" -> {
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1))
                code += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 2))
                while(regUsage.firstFree<3) {
                    regUsage.nextFree()
                }
                val callNr = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], 0, regUsage)
                code += exprGen.translateExpression(call.args[2], 1, regUsage)
                code += exprGen.translateExpression(call.args[3], 2, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 2))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 1))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            "msb" -> {
                code += exprGen.translateExpression(call.args.single(), resultRegister, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.SWAP, VmDataType.BYTE, reg1 = resultRegister, reg2=resultRegister))
                // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
            }
            "lsb" -> {
                code += exprGen.translateExpression(call.args.single(), resultRegister, regUsage)
                // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
            }
            "memory" -> {
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
                code += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.WORD, reg1=resultRegister, value=address.toInt()))
            }
            "rnd" -> {
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value= Syscall.RND.ordinal))
                if(resultRegister!=0)
                    code += VmCodeInstruction(Instruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0))
            }
            "peek" -> {
                val addressReg = regUsage.nextFree()
                code += exprGen.translateExpression(call.args.single(), addressReg, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2=addressReg))
            }
            "peekw" -> {
                val addressReg = regUsage.nextFree()
                code += exprGen.translateExpression(call.args.single(), addressReg, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.LOADI, VmDataType.WORD, reg1 = resultRegister, reg2=addressReg))
            }
            else -> {
                TODO("builtinfunc ${call.name}")
//                code += VmCodeInstruction(Instruction(Opcode.NOP))
//                for (arg in call.args) {
//                    code += translateExpression(arg, resultRegister, regUsage)
//                    code += when(arg.type) {
//                        in ByteDatatypes -> VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1=resultRegister))
//                        in WordDatatypes -> VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1=resultRegister))
//                        else -> throw AssemblyError("weird arg dt")
//                    }
//                }
//                code += VmCodeInstruction(Instruction(Opcode.CALL), labelArg = listOf("_prog8_builtin", call.name))
//                for (arg in call.args) {
//                    code += when(arg.type) {
//                        in ByteDatatypes -> VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1=resultRegister))
//                        in WordDatatypes -> VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1=resultRegister))
//                        else -> throw AssemblyError("weird arg dt")
//                    }
//                }
//                code += VmCodeInstruction(Instruction(Opcode.NOP))
            }
        }
        return code
    }

}
