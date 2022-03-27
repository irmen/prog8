package prog8.codegen.virtual

import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.PassByValueDatatypes
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.VmDataType


internal class RegisterUsage(var firstFree: Int=0) {
    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        return result
    }
}


internal class ExpressionGen(val codeGen: CodeGen) {
    fun translateExpression(expr: PtExpression, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        require(regUsage.firstFree > resultRegister)

        val code = VmCodeChunk()
        val vmDt = codeGen.vmType(expr.type)

        when (expr) {
            is PtNumber -> {
                code += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt()))
            }
            is PtIdentifier -> {
                val mem = codeGen.allocations.get(expr.targetName)
                code += if(expr.type in PassByValueDatatypes) {
                    VmCodeInstruction(Instruction(Opcode.LOADM, vmDt, reg1=resultRegister, value=mem))
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem))
                }
            }
            is PtAddressOf -> {
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                code += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem))
            }
            is PtMemoryByte -> {
                val addressRegister = regUsage.nextFree()
                val addressExprCode = translateExpression(expr.address, addressRegister, regUsage)
                code += addressExprCode
            }
            is PtTypeCast -> code += translate(expr, resultRegister, regUsage)
            is PtPrefix -> code += translate(expr, resultRegister, regUsage)
            is PtArrayIndexer -> code += translate(expr, resultRegister, regUsage)
            is PtBinaryExpression -> code += translate(expr, resultRegister, regUsage)
            is PtBuiltinFunctionCall -> code += translate(expr, resultRegister, regUsage)
            is PtFunctionCall -> code += translate(expr, resultRegister, regUsage)
            is PtContainmentCheck -> TODO()
            is PtPipe -> TODO()
            is PtRange,
            is PtArrayLiteral,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
        return code
    }

    private fun translate(arrayIx: PtArrayIndexer, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.vmType(arrayIx.type)
        val code = VmCodeChunk()
        val idxReg = regUsage.nextFree()
        code += translateExpression(arrayIx.index, idxReg, regUsage)
        if(eltSize>1) {
            val factorReg = regUsage.nextFree()
            code += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.BYTE, reg1=factorReg, value=eltSize))
            code += VmCodeInstruction(Instruction(Opcode.MUL, VmDataType.BYTE, reg1=idxReg, reg2=factorReg))
        }
        val arrayLocation = codeGen.allocations.get(arrayIx.variable.targetName)
        code += VmCodeInstruction(Instruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation))
        return code
    }

    private fun translate(expr: PtPrefix, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(expr.value, resultRegister, regUsage)
        val vmDt = codeGen.vmType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                code += VmCodeInstruction(Instruction(Opcode.NEG, vmDt, reg1=resultRegister))
            }
            "~" -> {
                val regMask = regUsage.nextFree()
                val mask = if(vmDt==VmDataType.BYTE) 0x00ff else 0xffff
                code += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=regMask, value=mask))
                code += VmCodeInstruction(Instruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask))
            }
            "not" -> {
                val label = codeGen.createLabelName()
                code += VmCodeInstruction(Instruction(Opcode.BZ, vmDt, reg1=resultRegister), labelArg = label)
                code += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=1))
                code += VmCodeLabel(label)
                val regMask = regUsage.nextFree()
                code += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=regMask, value=1))
                code += VmCodeInstruction(Instruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask))
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return code
    }

    private fun translate(cast: PtTypeCast, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        if(cast.type==cast.value.type)
            return code
        code += translateExpression(cast.value, resultRegister, regUsage)
        when(cast.type) {
            DataType.UBYTE -> {
                when(cast.value.type) {
                    DataType.BYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> {
                        TODO("float -> ubyte") // float not yet supported
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.BYTE -> {
                when(cast.value.type) {
                    DataType.UBYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> {
                        TODO("float -> byte") // float not yet supported
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.UWORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> uword:   sign extend
                        code += VmCodeInstruction(Instruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        code += VmCodeInstruction(Instruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.WORD -> { }
                    DataType.FLOAT -> {
                        TODO("float -> uword") // float not yet supported
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.WORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> word:   sign extend
                        code += VmCodeInstruction(Instruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        code += VmCodeInstruction(Instruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.UWORD -> { }
                    DataType.FLOAT -> {
                        TODO("float -> word") // float not yet supported
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.FLOAT -> {
                TODO("floating point not yet supported")
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // TODO("byte -> float")
                    }
                    DataType.UBYTE -> {
                        // TODO("ubyte -> float")
                    }
                    DataType.WORD -> {
                        // TODO("word -> float")
                    }
                    DataType.UWORD -> {
                        // TODO("uword -> float")
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            else -> throw AssemblyError("weird cast type")
        }
        return code
    }

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = regUsage.nextFree()
        val rightResultReg = regUsage.nextFree()
        val leftCode = translateExpression(binExpr.left, leftResultReg, regUsage)
        val rightCode = translateExpression(binExpr.right, rightResultReg, regUsage)
        // TODO: optimized codegen when left or right operand is known 0 or 1 or whatever.
        code += leftCode
        code += rightCode
        val vmDt = codeGen.vmType(binExpr.type)
        when(binExpr.operator) {
            "+" -> {
                code += VmCodeInstruction(Instruction(Opcode.ADD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "-" -> {
                code += VmCodeInstruction(Instruction(Opcode.SUB, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "*" -> {
                code += VmCodeInstruction(Instruction(Opcode.MUL, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "/" -> {
                code += VmCodeInstruction(Instruction(Opcode.DIV, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "%" -> {
                code += VmCodeInstruction(Instruction(Opcode.MOD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "|", "or" -> {
                code += VmCodeInstruction(Instruction(Opcode.OR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "&", "and" -> {
                code += VmCodeInstruction(Instruction(Opcode.AND, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "^", "xor" -> {
                code += VmCodeInstruction(Instruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "<<" -> {
                code += VmCodeInstruction(Instruction(Opcode.LSL, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            ">>" -> {
                code += VmCodeInstruction(Instruction(Opcode.LSR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "**" -> throw AssemblyError("** operator requires floating point ${binExpr.position}")
            // TODO the other operators: "==", "!=", "<", ">", "<=", ">="
            else -> TODO("operator ${binExpr.operator}")
        }
        return code
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val subroutine = codeGen.symbolTable.flat.getValue(fcall.functionName) as StSub
        val code = VmCodeChunk()
        for ((arg, parameter) in fcall.args.zip(subroutine.parameters)) {
            val argReg = regUsage.nextFree()
            code += translateExpression(arg, argReg, regUsage)
            val vmDt = codeGen.vmType(parameter.type)
            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
            code += VmCodeInstruction(Instruction(Opcode.STOREM, vmDt, reg1=argReg, value=mem))
        }
        code += VmCodeInstruction(Instruction(Opcode.CALL), labelArg=fcall.functionName)
        if(!fcall.void && resultRegister!=0) {
            // Call convention: result value is in r0, so put it in the required register instead.
            code += VmCodeInstruction(Instruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1=resultRegister, reg2=0))
        }
        return code
    }

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
                code += translateExpression(call.args[1], 0, regUsage)
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
                code += translateExpression(call.args[1], 0, regUsage)
                code += translateExpression(call.args[2], 1, regUsage)
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
                code += translateExpression(call.args[1], 0, regUsage)
                code += translateExpression(call.args[2], 1, regUsage)
                code += translateExpression(call.args[3], 2, regUsage)
                code += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 2))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 1))
                code += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            else -> {
                // TODO builtin functions...
                TODO("builtinfunc ${call.name}")
            }
        }
        return code
    }

}
