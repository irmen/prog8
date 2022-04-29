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
    fun translateExpression(expr: PtExpression, resultRegister: Int, resultFpRegister: Int): VmCodeChunk {
        require(codeGen.vmRegisters.peekNext() > resultRegister)

        val code = VmCodeChunk()

        when (expr) {
            is PtMachineRegister -> {
                if(resultRegister!=expr.register) {
                    val vmDt = codeGen.vmType(expr.type)
                    code += VmCodeInstruction(Opcode.LOADR, vmDt, reg1=resultRegister, reg2=expr.register)
                }
            }
            is PtNumber -> {
                val vmDt = codeGen.vmType(expr.type)
                code += if(vmDt==VmDataType.FLOAT)
                    VmCodeInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, fpValue = expr.number.toFloat())
                else
                    VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt())
            }
            is PtIdentifier -> {
                val vmDt = codeGen.vmType(expr.type)
                val mem = codeGen.allocations.get(expr.targetName)
                code += if (expr.type in PassByValueDatatypes) {
                    if(vmDt==VmDataType.FLOAT)
                        VmCodeInstruction(Opcode.LOADM, vmDt, fpReg1 = resultFpRegister, value = mem)
                    else
                        VmCodeInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, value = mem)
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    VmCodeInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, value = mem)
                }
            }
            is PtAddressOf -> {
                val vmDt = codeGen.vmType(expr.type)
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                code += VmCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem)
            }
            is PtMemoryByte -> {
                if(expr.address is PtNumber) {
                    val address = (expr.address as PtNumber).number.toInt()
                    code += VmCodeInstruction(Opcode.LOADM, VmDataType.BYTE, reg1=resultRegister, value = address)
                } else {
                    val addressRegister = codeGen.vmRegisters.nextFree()
                    code += translateExpression(expr.address, addressRegister, -1)
                    code += VmCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1=resultRegister, reg2=addressRegister)
                }
            }
            is PtTypeCast -> code += translate(expr, resultRegister)
            is PtPrefix -> code += translate(expr, resultRegister)
            is PtArrayIndexer -> code += translate(expr, resultRegister)
            is PtBinaryExpression -> code += translate(expr, resultRegister)
            is PtBuiltinFunctionCall -> code += codeGen.translateBuiltinFunc(expr, resultRegister)
            is PtFunctionCall -> code += translate(expr, resultRegister, resultFpRegister)
            is PtContainmentCheck -> code += translate(expr, resultRegister, resultFpRegister)
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
                    segWithArg.children.add(0, PtMachineRegister(sourceReg, sourceDt, segment.position))
                    segWithArg
                }
                is PtBuiltinFunctionCall -> {
                    val segWithArg = PtBuiltinFunctionCall(segment.name, segment.void, segment.hasNoSideEffects, segment.type, segment.position)
                    segWithArg.children.add(0, PtMachineRegister(sourceReg, sourceDt, segment.position))
                    segWithArg
                }
                else -> throw AssemblyError("weird segment type")
            }
        }

        val code = VmCodeChunk()
        code += translateExpression(segments[0], valueReg, -1)
        for (segment in segments.subList(1, segments.size-1)) {
            val sourceReg = valueReg
            val sourceDt = valueDt
            if(segment.type!=valueDt) {
                valueDt = segment.type
                valueReg = codeGen.vmRegisters.nextFree()
            }
            val segmentWithImplicitArgument = addImplicitArgToSegment(segment, sourceReg, sourceDt)
            code += translateExpression(segmentWithImplicitArgument, valueReg, -1)
        }
        val segWithArg = addImplicitArgToSegment(segments.last(), valueReg, valueDt)
        code += translateExpression(segWithArg, resultRegister, -1)
        return code
    }

    private fun translate(check: PtContainmentCheck, resultRegister: Int, resultFpRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(check.element, resultRegister, -1)   // load the element to check in resultRegister
        val iterable = codeGen.symbolTable.flat.getValue(check.iterable.targetName) as StStaticVariable
        when(iterable.dt) {
            DataType.STR -> {
                val call = PtFunctionCall(listOf("prog8_lib", "string_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                code += translate(call, resultRegister, resultFpRegister)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                val call = PtFunctionCall(listOf("prog8_lib", "bytearray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister, resultFpRegister)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                val call = PtFunctionCall(listOf("prog8_lib", "wordarray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                code += translate(call, resultRegister, resultFpRegister)
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
            code += translateExpression(arrayIx.index, idxReg, -1)
            if(eltSize>1)
                code += codeGen.multiplyByConst(VmDataType.BYTE, idxReg, eltSize)
            code += VmCodeInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation)
        }
        return code
    }

    private fun translate(expr: PtPrefix, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        code += translateExpression(expr.value, resultRegister, -1)
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
        code += translateExpression(cast.value, resultRegister, -1)
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
        val vmDt = codeGen.vmType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
        return when(binExpr.operator) {
            "+" -> operatorPlus(binExpr, vmDt, resultRegister)
            "-" -> operatorMinus(binExpr, vmDt, resultRegister)
            "*" -> operatorMultiply(binExpr, vmDt, resultRegister)
            "/" -> operatorDivide(binExpr, vmDt, resultRegister)
            "%" -> operatorModulo(binExpr, vmDt, resultRegister)
            "|", "or" -> operatorOr(binExpr, vmDt, resultRegister)
            "&", "and" -> operatorAnd(binExpr, vmDt, resultRegister)
            "^", "xor" -> operatorXor(binExpr, vmDt, resultRegister)
            "<<" -> operatorShiftLeft(binExpr, vmDt, resultRegister)
            ">>" -> operatorShiftRight(binExpr, vmDt, resultRegister, signed)
            "==" -> operatorEquals(binExpr, vmDt, resultRegister, true)
            "!=" -> operatorEquals(binExpr, vmDt, resultRegister, false)
            "<" -> operatorLessThan(binExpr, vmDt, resultRegister, signed, false)
            ">" -> operatorGreaterThan(binExpr, vmDt, resultRegister, signed, false)
            "<=" -> operatorLessThan(binExpr, vmDt, resultRegister, signed, true)
            ">=" -> operatorGreaterThan(binExpr, vmDt, resultRegister, signed, true)
            else -> throw AssemblyError("weird operator ${binExpr.operator}")
        }
    }

    private fun operatorGreaterThan(
        binExpr: PtBinaryExpression,
        vmDt: VmDataType,
        resultRegister: Int,
        signed: Boolean,
        greaterEquals: Boolean
    ): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        val ins = if(signed) {
            if(greaterEquals) Opcode.SGES else Opcode.SGTS
        } else {
            if(greaterEquals) Opcode.SGE else Opcode.SGT
        }
        code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorLessThan(
        binExpr: PtBinaryExpression,
        vmDt: VmDataType,
        resultRegister: Int,
        signed: Boolean,
        lessEquals: Boolean
    ): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        val ins = if(signed) {
            if(lessEquals) Opcode.SLES else Opcode.SLTS
        } else {
            if(lessEquals) Opcode.SLE else Opcode.SLT
        }
        code += VmCodeInstruction(ins, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorEquals(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, notEquals: Boolean): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        val opcode = if(notEquals) Opcode.SNE else Opcode.SEQ
        code += VmCodeInstruction(opcode, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorShiftRight(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, signed: Boolean): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        val opc = if(signed) Opcode.ASRX else Opcode.LSRX
        code += VmCodeInstruction(opc, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorShiftLeft(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        code += VmCodeInstruction(Opcode.LSLX, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        code += VmCodeInstruction(Opcode.XOR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        code += VmCodeInstruction(Opcode.AND, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        code += VmCodeInstruction(Opcode.OR, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorModulo(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val leftResultReg = codeGen.vmRegisters.nextFree()
        val rightResultReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(binExpr.left, leftResultReg, -1)
        code += translateExpression(binExpr.right, rightResultReg, -1)
        code += VmCodeInstruction(Opcode.MOD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        return code
    }

    private fun operatorDivide(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val constFactorRight = binExpr.right as? PtNumber
        if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            val factor = constFactorRight.number.toInt()
            code += codeGen.divideByConst(vmDt, resultRegister, factor)
        } else {
            val leftResultReg = codeGen.vmRegisters.nextFree()
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, leftResultReg, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += VmCodeInstruction(Opcode.DIV, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        }
        return code
    }

    private fun operatorMultiply(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        val constFactorLeft = binExpr.left as? PtNumber
        val constFactorRight = binExpr.right as? PtNumber
        if(constFactorLeft!=null && constFactorLeft.type!=DataType.FLOAT) {
            code += translateExpression(binExpr.right, resultRegister, -1)
            val factor = constFactorLeft.number.toInt()
            code += codeGen.multiplyByConst(vmDt, resultRegister, factor)
        } else if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            val factor = constFactorRight.number.toInt()
            code += codeGen.multiplyByConst(vmDt, resultRegister, factor)
        } else {
            val leftResultReg = codeGen.vmRegisters.nextFree()
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, leftResultReg, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += VmCodeInstruction(Opcode.MUL, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        }
        return code
    }

    private fun operatorMinus(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if((binExpr.right as? PtNumber)?.number==1.0) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += VmCodeInstruction(Opcode.DEC, vmDt, reg1=resultRegister)
        }
        else {
            val leftResultReg = codeGen.vmRegisters.nextFree()
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, leftResultReg, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += VmCodeInstruction(Opcode.SUB, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        }
        return code
    }

    private fun operatorPlus(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): VmCodeChunk {
        val code = VmCodeChunk()
        if((binExpr.left as? PtNumber)?.number==1.0) {
            code += translateExpression(binExpr.right, resultRegister, -1)
            code += VmCodeInstruction(Opcode.INC, vmDt, reg1=resultRegister)
        }
        else if((binExpr.right as? PtNumber)?.number==1.0) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += VmCodeInstruction(Opcode.INC, vmDt, reg1=resultRegister)
        }
        else {
            val leftResultReg = codeGen.vmRegisters.nextFree()
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, leftResultReg, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += VmCodeInstruction(Opcode.ADD, vmDt, reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg)
        }
        return code
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int, resultFpRegister: Int): VmCodeChunk {
        val subroutine = codeGen.symbolTable.flat.getValue(fcall.functionName) as StSub
        val code = VmCodeChunk()
        for ((arg, parameter) in fcall.args.zip(subroutine.parameters)) {
            val paramDt = codeGen.vmType(parameter.type)
            if(paramDt==VmDataType.FLOAT) {
                val argFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(arg, -1, argFpReg)
                val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                code += VmCodeInstruction(Opcode.STOREM, paramDt, fpReg1 = argFpReg, value = mem)
            } else {
                val argReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(arg, argReg, -1)
                val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                code += VmCodeInstruction(Opcode.STOREM, paramDt, reg1 = argReg, value = mem)
            }
        }
        code += VmCodeInstruction(Opcode.CALL, symbol=fcall.functionName)
        if(fcall.type==DataType.FLOAT) {
            if (!fcall.void && resultFpRegister != 0) {
                // Call convention: result value is in fr0, so put it in the required register instead.
                code += VmCodeInstruction(Opcode.LOADR, VmDataType.FLOAT, fpReg1 = resultFpRegister, fpReg2 = 0)
            }
        } else {
            if (!fcall.void && resultRegister != 0) {
                // Call convention: result value is in r0, so put it in the required register instead.
                code += VmCodeInstruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1 = resultRegister, reg2 = 0)
            }
        }
        return code
    }

}
