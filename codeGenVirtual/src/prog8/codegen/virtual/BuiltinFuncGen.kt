package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.ast.PtBuiltinFunctionCall
import prog8.code.ast.PtIdentifier
import prog8.code.ast.PtNumber
import prog8.code.ast.PtString
import prog8.code.core.DataType
import prog8.vm.Opcode
import prog8.vm.Syscall
import prog8.vm.VmDataType

internal class BuiltinFuncGen(private val codeGen: CodeGen, private val exprGen: ExpressionGen) {

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        when(call.name) {
            "syscall" -> {
                val vExpr = call.args.single() as PtNumber
                code += VmCodeInstruction(Opcode.SYSCALL, value=vExpr.number.toInt())
            }
            "syscall1" -> {
                val callNr = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], 0)
                code += VmCodeInstruction(Opcode.SYSCALL, value=callNr)
            }
            "syscall2" -> {
                code += VmCodeInstruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1)
                while(codeGen.vmRegisters.peekNext()<2) {
                    codeGen.vmRegisters.nextFree()
                }
                val callNr = (call.args[0] as PtNumber).number.toInt()
                code += exprGen.translateExpression(call.args[1], 0)
                code += exprGen.translateExpression(call.args[2], 1)
                code += VmCodeInstruction(Opcode.SYSCALL, value=callNr)
                code += VmCodeInstruction(Opcode.POP, VmDataType.WORD, reg1 = 1)
            }
            "syscall3" -> {
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
            }
            "msb" -> {
                code += exprGen.translateExpression(call.args.single(), resultRegister)
                code += VmCodeInstruction(Opcode.SWAP, VmDataType.BYTE, reg1 = resultRegister)
                // note: if a word result is needed, the upper byte is cleared by the typecast that follows. No need to do it here.
            }
            "lsb" -> {
                code += exprGen.translateExpression(call.args.single(), resultRegister)
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
                code += VmCodeInstruction(Opcode.LOAD, VmDataType.WORD, reg1=resultRegister, value=address.toInt())
            }
            "rnd" -> {
                code += VmCodeInstruction(Opcode.SYSCALL, value= Syscall.RND.ordinal)
                if(resultRegister!=0)
                    code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
            }
            "peek" -> {
                // should just be a memory read
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args.single(), addressReg)
                code += VmCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1 = resultRegister, reg2=addressReg)
            }
            "peekw" -> {
                val addressReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args.single(), addressReg)
                code += VmCodeInstruction(Opcode.LOADI, VmDataType.WORD, reg1 = resultRegister, reg2=addressReg)
            }
            "poke" -> {
                // should just be a memory write
                val addressReg = codeGen.vmRegisters.nextFree()
                val valueReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg)
                code += exprGen.translateExpression(call.args[1], valueReg)
                code += VmCodeInstruction(Opcode.STOREI, VmDataType.BYTE, reg1 = addressReg, reg2=valueReg)
            }
            "pokew" -> {
                val addressReg = codeGen.vmRegisters.nextFree()
                val valueReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], addressReg)
                code += exprGen.translateExpression(call.args[1], valueReg)
                code += VmCodeInstruction(Opcode.STOREI, VmDataType.WORD, reg1 = addressReg, reg2=valueReg)
            }
            "mkword" -> {
                val msbReg = codeGen.vmRegisters.nextFree()
                val lsbReg = codeGen.vmRegisters.nextFree()
                code += exprGen.translateExpression(call.args[0], msbReg)
                code += exprGen.translateExpression(call.args[1], lsbReg)
                code += VmCodeInstruction(Opcode.CONCAT, VmDataType.BYTE, reg1=resultRegister, reg2=msbReg, reg3=lsbReg)
            }
            "sin8u" -> {
                code += exprGen.translateExpression(call.args[0], 0)
                code += VmCodeInstruction(Opcode.SYSCALL, value=Syscall.SIN8U.ordinal)
                if(resultRegister!=0)
                    code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
            }
            "cos8u" -> {
                code += exprGen.translateExpression(call.args[0], 0)
                code += VmCodeInstruction(Opcode.SYSCALL, value=Syscall.COS8U.ordinal)
                if(resultRegister!=0)
                    code += VmCodeInstruction(Opcode.LOADR, VmDataType.BYTE, reg1=resultRegister, reg2=0)
            }
            "sort" -> {
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
                code += exprGen.translateExpression(call.args[0], 0)
                code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
                code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
            }
            "reverse" -> {
                val arrayName = call.args[0] as PtIdentifier
                val array = codeGen.symbolTable.flat.getValue(arrayName.targetName) as StStaticVariable
                val sortSyscall =
                    when(array.dt) {
                        DataType.ARRAY_UB, DataType.ARRAY_B, DataType.STR -> Syscall.REVERSE_BYTES
                        DataType.ARRAY_UW, DataType.ARRAY_W -> Syscall.REVERSE_WORDS
                        DataType.FLOAT -> TODO("reverse floats")
                        else -> throw IllegalArgumentException("weird type to reverse")
                    }
                code += exprGen.translateExpression(call.args[0], 0)
                code += VmCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=1, value=array.length)
                code += VmCodeInstruction(Opcode.SYSCALL, value=sortSyscall.ordinal)
            }
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
        return code
    }

}
