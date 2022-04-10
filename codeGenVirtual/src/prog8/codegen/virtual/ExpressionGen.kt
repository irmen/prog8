package prog8.codegen.virtual

import prog8.code.StStaticVariable
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.PassByValueDatatypes
import prog8.code.core.SignedDatatypes
import prog8.vm.Opcode
import prog8.vm.VmDataType


internal class ExpressionGen(private val codeGen: CodeGen) {
    fun translateExpression(expr: PtExpression, resultRegister: Int): VmCodeChunk {
        require(codeGen.vmRegisters.peekNext() > resultRegister)

        val code = VmCodeChunk()

        when (expr) {
            is PtNumber -> {
                val vmDt = codeGen.vmType(expr.type)
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt())
            }
            is PtIdentifier -> {
                val vmDt = codeGen.vmType(expr.type)
                if(expr.targetName[0].startsWith(":vmreg-")) {
                    // special direct reference to a register in the VM
                    val reg = expr.targetName[0].substring(7).toInt()
                    code += VmCodeInstruction(Opcode.LOADR, vmDt, reg1=resultRegister, reg2=reg)
                } else {
                    val mem = codeGen.allocations.get(expr.targetName)
                    code += if (expr.type in PassByValueDatatypes) {
                        VmCodeInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, value = mem)
                    } else {
                        // for strings and arrays etc., load the *address* of the value instead
                        VmCodeInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, value = mem)
                    }
                }
            }
            is PtAddressOf -> {
                val vmDt = codeGen.vmType(expr.type)
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem)
            }
            is PtMemoryByte -> {
                val addressRegister = codeGen.vmRegisters.nextFree()
                val addressExprCode = translateExpression(expr.address, addressRegister)
                code += addressExprCode
            }
            is PtTypeCast -> code += translate(expr, resultRegister)
            is PtPrefix -> code += translate(expr, resultRegister)
            is PtArrayIndexer -> code += translate(expr, resultRegister)
            is PtBinaryExpression -> code += translate(expr, resultRegister)
            is PtBuiltinFunctionCall -> code += codeGen.translateBuiltinFunc(expr, resultRegister)
            is PtFunctionCall -> code += translate(expr, resultRegister)
            is PtContainmentCheck -> code += translate(expr, resultRegister)
            is PtPipe -> code += translate(expr, resultRegister)
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
        return code
    }

    internal fun translate(pipe: PtPipe, resultRegister: Int): VmCodeChunk {
        val segments = pipe.segments
        var valueDt = segments[0].type
        var valueReg = if(pipe.void) codeGen.vmRegisters.nextFree() else resultRegister

        fun addImplicitArgToSegment(segment: PtExpression, sourceReg: Int, sourceDt: DataType): PtExpression {
            return when (segment) {
                is PtFunctionCall -> {
                    val segWithArg = PtFunctionCall(segment.functionName, segment.void, segment.type, segment.position)
                    segWithArg.children.add(0, PtIdentifier(listOf(":vmreg-$sourceReg"), listOf(":vmreg-$sourceReg"), sourceDt, segment.position))
                    segWithArg
                }
                is PtBuiltinFunctionCall -> {
                    val segWithArg = PtBuiltinFunctionCall(segment.name, segment.void, segment.hasNoSideEffects, segment.type, segment.position)
                    segWithArg.children.add(0, PtIdentifier(listOf(":vmreg-$sourceReg"), listOf(":vmreg-$sourceReg"), sourceDt, segment.position))
                    segWithArg
                }
                else -> throw AssemblyError("weird segment type")
            }
        }

        val code = VmCodeChunk()
        code += translateExpression(segments[0], valueReg)
        for (segment in segments.subList(1, segments.size-1)) {
            val sourceReg = valueReg
            val sourceDt = valueDt
            if(segment.type!=valueDt) {
                valueDt = segment.type
                valueReg = codeGen.vmRegisters.nextFree()
            }
            val segmentWithImplicitArgument = addImplicitArgToSegment(segment, sourceReg, sourceDt)
            code += translateExpression(segmentWithImplicitArgument, valueReg)
        }
        val segWithArg = addImplicitArgToSegment(segments.last(), valueReg, valueDt)
        code += translateExpression(segWithArg, resultRegister)
        return code
    }

    private fun translate(check: PtContainmentCheck, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(check.element, resultRegister)   // load the element to check in resultRegister
        val iterable = codeGen.symbolTable.flat.getValue(check.iterable.targetName) as StStaticVariable
        when(iterable.dt) {
            DataType.STR -> {
                val call = PtFunctionCall(listOf("prog8_lib", "string_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                code += translate(call, resultRegister)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                val call = PtFunctionCall(listOf("prog8_lib", "bytearray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                val call = PtFunctionCall(listOf("prog8_lib", "wordarray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister)
            }
            DataType.ARRAY_F -> TODO("containment check in float-array")
            else -> throw AssemblyError("weird iterable dt ${iterable.dt} for ${check.iterable.targetName}")
        }
        return code
    }

    private fun translate(arrayIx: PtArrayIndexer, resultRegister: Int): VmCodeChunk {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.vmType(arrayIx.type)
        val code = VmCodeChunk()
        val idxReg = codeGen.vmRegisters.nextFree()
        val arrayLocation = codeGen.allocations.get(arrayIx.variable.targetName)
        if(arrayIx.index is PtNumber) {
            // optimized code when index is known - just calculate the memory address here
            val memOffset = (arrayIx.index as PtNumber).number.toInt() * eltSize
            code += VmCodeInstruction(Opcode.LOADM, vmDt, reg1=resultRegister, value=arrayLocation+memOffset)
        } else {
            code += translateExpression(arrayIx.index, idxReg)
            if(eltSize>1)
                code += codeGen.multiplyByConst(VmDataType.BYTE, idxReg, eltSize)
            code += VmCodeInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation)
        }
        return code
    }

    private fun translate(expr: PtPrefix, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(expr.value, resultRegister)
        val vmDt = codeGen.vmType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                code += VmCodeInstruction(Opcode.NEG, vmDt, reg1=resultRegister)
            }
            "~" -> {
                val regMask = codeGen.vmRegisters.nextFree()
                val mask = if(vmDt==VmDataType.BYTE) 0x00ff else 0xffff
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=regMask, value=mask)
                code += VmCodeInstruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask)
            }
            "not" -> {
                val label = codeGen.createLabelName()
                code += VmCodeInstruction(Opcode.BZ, vmDt, reg1=resultRegister, symbol = label)
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=1)
                code += VmCodeLabel(label)
                val regMask = codeGen.vmRegisters.nextFree()
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=regMask, value=1)
                code += VmCodeInstruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=resultRegister, reg3=regMask)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return code
    }

    private fun translate(cast: PtTypeCast, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if(cast.type==cast.value.type)
            return code
        code += translateExpression(cast.value, resultRegister)
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
                        code += VmCodeInstruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister)
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        code += VmCodeInstruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister)
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
                        code += VmCodeInstruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = resultRegister)
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        code += VmCodeInstruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = resultRegister)
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

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        // TODO: optimized codegen when left or right operand is known 0 or 1 or whatever. But only if this would result in a different opcode such as ADD 1 -> INC, MUL 1 -> NOP
        //       actually optimizing the code should not be done here but in a tailored code optimizer step.
        //       multiplyByConst()
        val leftCode = translateExpression(binExpr.left, leftResultReg)
        val rightCode = translateExpression(binExpr.right, rightResultReg)
        code += leftCode
        code += rightCode
        val vmDt = codeGen.vmType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
        when(binExpr.operator) {
            "+" -> {
                code += VmCodeInstruction(Opcode.ADD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "-" -> {
                code += VmCodeInstruction(Opcode.SUB, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "*" -> {
                code += VmCodeInstruction(Opcode.MUL, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "/" -> {
                code += VmCodeInstruction(Opcode.DIV, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "%" -> {
                code += VmCodeInstruction(Opcode.MOD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "|", "or" -> {
                code += VmCodeInstruction(Opcode.OR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "&", "and" -> {
                code += VmCodeInstruction(Opcode.AND, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "^", "xor" -> {
                code += VmCodeInstruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "<<" -> {
                code += VmCodeInstruction(Opcode.LSLM, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            ">>" -> {
                val opc = if(signed) Opcode.ASRM else Opcode.LSRM
                code += VmCodeInstruction(opc, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "==" -> {
                code += VmCodeInstruction(Opcode.SEQ, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "!=" -> {
                code += VmCodeInstruction(Opcode.SNE, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "<" -> {
                val ins = if(signed) Opcode.SLTS else Opcode.SLT
                code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            ">" -> {
                val ins = if(signed) Opcode.SGTS else Opcode.SGT
                code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            "<=" -> {
                val ins = if(signed) Opcode.SLES else Opcode.SLE
                code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            ">=" -> {
                val ins = if(signed) Opcode.SGES else Opcode.SGE
                code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
            }
            else -> throw AssemblyError("weird operator ${binExpr.operator}")
        }
        return code
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int): VmCodeChunk {
        val subroutine = codeGen.symbolTable.flat.getValue(fcall.functionName) as StSub
        val code = VmCodeChunk()
        for ((arg, parameter) in fcall.args.zip(subroutine.parameters)) {
            val argReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(arg, argReg)
            val vmDt = codeGen.vmType(parameter.type)
            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
            code += VmCodeInstruction(Opcode.STOREM, vmDt, reg1=argReg, value=mem)
        }
        code += VmCodeInstruction(Opcode.CALL, symbol=fcall.functionName)
        if(!fcall.void && resultRegister!=0) {
            // Call convention: result value is in r0, so put it in the required register instead.
            code += VmCodeInstruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1=resultRegister, reg2=0)
        }
        return code
    }

}
