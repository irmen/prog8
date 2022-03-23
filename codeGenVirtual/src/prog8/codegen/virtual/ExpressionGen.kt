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

        val chunk = VmCodeChunk()
        val vmDt = codeGen.vmType(expr.type)

        when (expr) {
            is PtNumber -> {
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt()))
            }
            is PtIdentifier -> {
                val mem = codeGen.allocations.get(expr.targetName)
                chunk += if(expr.type in PassByValueDatatypes) {
                    VmCodeInstruction(Instruction(Opcode.LOADM, vmDt, reg1=resultRegister, value=mem))
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem))
                }
            }
            is PtAddressOf -> {
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem))
            }
            is PtMemoryByte -> {
                val addressRegister = regUsage.nextFree()
                val addressExprCode = translateExpression(expr.address, addressRegister, regUsage)
                chunk += addressExprCode
            }
            is PtTypeCast -> chunk += translate(expr, resultRegister, regUsage)
            is PtPrefix -> chunk += translate(expr, resultRegister, regUsage)
            is PtArrayIndexer -> chunk += translate(expr, resultRegister, regUsage)
            is PtBinaryExpression -> chunk += translate(expr, resultRegister, regUsage)
            is PtBuiltinFunctionCall -> chunk += translate(expr, resultRegister, regUsage)
            is PtFunctionCall -> chunk += translate(expr, resultRegister, regUsage)
            is PtContainmentCheck -> TODO()
            is PtPipe -> TODO()
            is PtRange,
            is PtArrayLiteral,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
        return chunk
    }

    private fun translate(arrayIx: PtArrayIndexer, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.vmType(arrayIx.type)
        val chunk = VmCodeChunk()
        val idxReg = regUsage.nextFree()
        chunk += translateExpression(arrayIx.index, idxReg, regUsage)
        if(eltSize>1) {
            val factorReg = regUsage.nextFree()
            chunk += VmCodeInstruction(Instruction(Opcode.LOAD, VmDataType.BYTE, reg1=factorReg, value=eltSize))
            chunk += VmCodeInstruction(Instruction(Opcode.MUL, VmDataType.BYTE, reg1=idxReg, reg2=factorReg))
        }
        val arrayLocation = codeGen.allocations.get(arrayIx.variable.targetName)
        chunk += VmCodeInstruction(Instruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation))
        return chunk
    }

    private fun translate(expr: PtPrefix, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        // operator can be: +, -, ~, not
        val chunk = VmCodeChunk()
        chunk += translateExpression(expr.value, resultRegister, regUsage)
        val vmDt = codeGen.vmType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.NEG, vmDt, reg1=resultRegister))
            }
            "~" -> {
                val regMask = regUsage.nextFree()
                val mask = if(vmDt==VmDataType.BYTE) 0x00ff else 0xffff
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=regMask, value=mask))
                chunk += VmCodeInstruction(Instruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask))
            }
            "not" -> {
                val label = codeGen.createLabelName()
                chunk += VmCodeInstruction(Instruction(Opcode.BZ, vmDt, reg1=resultRegister), labelArg = label)
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=1))
                chunk += VmCodeLabel(label)
                val regMask = regUsage.nextFree()
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=regMask, value=1))
                chunk += VmCodeInstruction(Instruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask))
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return chunk
    }

    private fun translate(cast: PtTypeCast, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        if(cast.type==cast.value.type)
            return chunk
        chunk += translateExpression(cast.value, resultRegister, regUsage)
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
                        chunk += VmCodeInstruction(Instruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        chunk += VmCodeInstruction(Instruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister))
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
                        chunk += VmCodeInstruction(Instruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister))
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        chunk += VmCodeInstruction(Instruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister))
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
        return chunk
    }

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        val leftResultReg = regUsage.nextFree()
        val rightResultReg = regUsage.nextFree()
        val leftCode = translateExpression(binExpr.left, leftResultReg, regUsage)
        val rightCode = translateExpression(binExpr.right, rightResultReg, regUsage)
        chunk += leftCode
        chunk += rightCode
        val vmDt = codeGen.vmType(binExpr.type)
        when(binExpr.operator) {
            "+" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.ADD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "-" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.SUB, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "*" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.MUL, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "/" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.DIV, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "%" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.MOD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            else -> TODO("operator ${binExpr.operator}")
        }
        return chunk
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val subroutine = codeGen.symbolTable.flat.getValue(fcall.functionName) as StSub
        val chunk = VmCodeChunk()
        for ((arg, parameter) in fcall.args.zip(subroutine.parameters)) {
            val argReg = regUsage.nextFree()
            chunk += translateExpression(arg, argReg, regUsage)
            val vmDt = codeGen.vmType(parameter.type)
            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
            chunk += VmCodeInstruction(Instruction(Opcode.STOREM, vmDt, reg1=argReg, value=mem))
        }
        chunk += VmCodeInstruction(Instruction(Opcode.CALL), labelArg=fcall.functionName)
        if(!fcall.void && resultRegister!=0) {
            // Call convention: result value is in r0, so put it in the required register instead.
            chunk += VmCodeInstruction(Instruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1=resultRegister, reg2=0))
        }
        return chunk
    }

    fun translate(call: PtBuiltinFunctionCall, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val chunk = VmCodeChunk()
        when(call.name) {
            "syscall" -> {
                val vExpr = call.args.single() as PtNumber
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall1" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                val callNr = (call.args[0] as PtNumber).number.toInt()
                chunk += translateExpression(call.args[1], 0, regUsage)
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            "syscall2" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1))
                while(regUsage.firstFree<2) {
                    regUsage.nextFree()
                }
                val callNr = (call.args[0] as PtNumber).number.toInt()
                chunk += translateExpression(call.args[1], 0, regUsage)
                chunk += translateExpression(call.args[2], 1, regUsage)
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 1))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            "syscall3" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.BYTE, reg1 = 0))
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 1))
                chunk += VmCodeInstruction(Instruction(Opcode.PUSH, VmDataType.WORD, reg1 = 2))
                while(regUsage.firstFree<3) {
                    regUsage.nextFree()
                }
                val callNr = (call.args[0] as PtNumber).number.toInt()
                chunk += translateExpression(call.args[1], 0, regUsage)
                chunk += translateExpression(call.args[2], 1, regUsage)
                chunk += translateExpression(call.args[3], 2, regUsage)
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=callNr))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 2))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.WORD, reg1 = 1))
                chunk += VmCodeInstruction(Instruction(Opcode.POP, VmDataType.BYTE, reg1 = 0))
            }
            else -> {
                TODO("builtinfunc ${call.name}")
            }
        }
        return chunk
    }

}
