package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.PassByValueDatatypes
import prog8.code.core.SignedDatatypes
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
            is PtBuiltinFunctionCall -> code += codeGen.translateBuiltinFunc(expr, resultRegister, regUsage)
            is PtFunctionCall -> code += translate(expr, resultRegister, regUsage)
            is PtContainmentCheck -> code += translate(expr, resultRegister, regUsage)
            is PtPipe -> code += translate(expr, resultRegister, regUsage)
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
        return code
    }

    internal fun translate(pipe: PtPipe, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        TODO("Not yet implemented: pipe expression")
    }

    private fun translate(check: PtContainmentCheck, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(check.element, resultRegister, regUsage)   // load the element to check in resultRegister
        val iterable = codeGen.symbolTable.flat.getValue(check.iterable.targetName) as StStaticVariable
        when(iterable.dt) {
            DataType.STR -> {
                val call = PtFunctionCall(listOf("prog8_lib", "string_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                code += translate(call, resultRegister, regUsage)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                val call = PtFunctionCall(listOf("prog8_lib", "bytearray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister, regUsage)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                val call = PtFunctionCall(listOf("prog8_lib", "wordarray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister, regUsage)
            }
            DataType.ARRAY_F -> TODO("containment check in float-array")
            else -> throw AssemblyError("weird iterable dt ${iterable.dt} for ${check.iterable.targetName}")
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
                code += VmCodeInstruction(Instruction(Opcode.BZ, vmDt, reg1=resultRegister, symbol = label))
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
//                when(cast.value.type) {
//                    DataType.BYTE -> {
//                    }
//                    DataType.UBYTE -> {
//                    }
//                    DataType.WORD -> {
//                    }
//                    DataType.UWORD -> {
//                    }
//                    else -> throw AssemblyError("weird cast value type")
//                }
            }
            else -> throw AssemblyError("weird cast type")
        }
        return code
    }

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int, regUsage: RegisterUsage): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = regUsage.nextFree()
        val rightResultReg = regUsage.nextFree()
        // TODO: optimized codegen when left or right operand is known 0 or 1 or whatever. But only if this would result in a different opcode such as ADD 1 -> INC, MUL 1 -> NOP
        //       actually optimizing the code should not be done here but in a tailored code optimizer step.
        val leftCode = translateExpression(binExpr.left, leftResultReg, regUsage)
        val rightCode = translateExpression(binExpr.right, rightResultReg, regUsage)
        code += leftCode
        code += rightCode
        val vmDt = codeGen.vmType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
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
                val opc = if(signed) Opcode.ASR else Opcode.LSR
                code += VmCodeInstruction(Instruction(opc, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "==" -> {
                code += VmCodeInstruction(Instruction(Opcode.SEQ, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "!=" -> {
                code += VmCodeInstruction(Instruction(Opcode.SNE, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "<" -> {
                val ins = if(signed) Opcode.SLTS else Opcode.SLT
                code += VmCodeInstruction(Instruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            ">" -> {
                val ins = if(signed) Opcode.SGTS else Opcode.SGT
                code += VmCodeInstruction(Instruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            "<=" -> {
                val ins = if(signed) Opcode.SLES else Opcode.SLE
                code += VmCodeInstruction(Instruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            ">=" -> {
                val ins = if(signed) Opcode.SGES else Opcode.SGE
                code += VmCodeInstruction(Instruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            else -> throw AssemblyError("weird operator ${binExpr.operator}")
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
        code += VmCodeInstruction(Instruction(Opcode.CALL, symbol=fcall.functionName))
        if(!fcall.void && resultRegister!=0) {
            // Call convention: result value is in r0, so put it in the required register instead.   TODO does this work correctly?
            code += VmCodeInstruction(Instruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1=resultRegister, reg2=0))
        }
        return code
    }

}
