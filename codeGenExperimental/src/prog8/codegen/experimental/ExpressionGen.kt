package prog8.codegen.experimental

import prog8.code.StRomSub
import prog8.code.StStaticVariable
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*


internal class ExpressionGen(private val codeGen: CodeGen) {
    fun translateExpression(expr: PtExpression, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        require(codeGen.vmRegisters.peekNext() > resultRegister)

        val code = IRCodeChunk(expr.position)

        when (expr) {
            is PtMachineRegister -> {
                if(resultRegister!=expr.register) {
                    val vmDt = codeGen.vmType(expr.type)
                    code += IRCodeInstruction(Opcode.LOADR, vmDt, reg1=resultRegister, reg2=expr.register)
                }
            }
            is PtNumber -> {
                val vmDt = codeGen.vmType(expr.type)
                code += if(vmDt==VmDataType.FLOAT)
                    IRCodeInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, fpValue = expr.number.toFloat())
                else
                    IRCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt())
            }
            is PtIdentifier -> {
                val vmDt = codeGen.vmType(expr.type)
                val mem = codeGen.allocations.get(expr.targetName)
                code += if (expr.type in PassByValueDatatypes) {
                    if(vmDt==VmDataType.FLOAT)
                        IRCodeInstruction(Opcode.LOADM, vmDt, fpReg1 = resultFpRegister, value = mem)
                    else
                        IRCodeInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, value = mem)
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    IRCodeInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, value = mem)
                }
            }
            is PtAddressOf -> {
                val vmDt = codeGen.vmType(expr.type)
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                code += IRCodeInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem)
            }
            is PtMemoryByte -> {
                if(expr.address is PtNumber) {
                    val address = (expr.address as PtNumber).number.toInt()
                    code += IRCodeInstruction(Opcode.LOADM, VmDataType.BYTE, reg1=resultRegister, value = address)
                } else {
                    val addressRegister = codeGen.vmRegisters.nextFree()
                    code += translateExpression(expr.address, addressRegister, -1)
                    code += IRCodeInstruction(Opcode.LOADI, VmDataType.BYTE, reg1=resultRegister, reg2=addressRegister)
                }
            }
            is PtTypeCast -> code += translate(expr, resultRegister, resultFpRegister)
            is PtPrefix -> code += translate(expr, resultRegister)
            is PtArrayIndexer -> code += translate(expr, resultRegister, resultFpRegister)
            is PtBinaryExpression -> code += translate(expr, resultRegister, resultFpRegister)
            is PtBuiltinFunctionCall -> code += codeGen.translateBuiltinFunc(expr, resultRegister)
            is PtFunctionCall -> code += translate(expr, resultRegister, resultFpRegister)
            is PtContainmentCheck -> code += translate(expr, resultRegister, resultFpRegister)
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
        return code
    }

    private fun translate(check: PtContainmentCheck, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(check.position)
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
            DataType.ARRAY_F -> throw AssemblyError("containment check in float-array not supported")
            else -> throw AssemblyError("weird iterable dt ${iterable.dt} for ${check.iterable.targetName}")
        }
        return code
    }

    private fun translate(arrayIx: PtArrayIndexer, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.vmType(arrayIx.type)
        val code = IRCodeChunk(arrayIx.position)
        val idxReg = codeGen.vmRegisters.nextFree()
        val arrayLocation = codeGen.allocations.get(arrayIx.variable.targetName)

        if(arrayIx.variable.type==DataType.UWORD) {
            // indexing a pointer var instead of a real array or string
            if(eltSize!=1)
                throw AssemblyError("non-array var indexing requires bytes dt")
            if(arrayIx.index.type!=DataType.UBYTE)
                throw AssemblyError("non-array var indexing requires bytes index")
            code += translateExpression(arrayIx.index, idxReg, -1)
            code += IRCodeInstruction(Opcode.LOADIX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation)
            return code
        }

        if(arrayIx.index is PtNumber) {
            // optimized code when index is known - just calculate the memory address here
            val memOffset = (arrayIx.index as PtNumber).number.toInt() * eltSize
            if(vmDt==VmDataType.FLOAT)
                code += IRCodeInstruction(Opcode.LOADM, VmDataType.FLOAT, fpReg1=resultFpRegister, value=arrayLocation+memOffset)
            else
                code += IRCodeInstruction(Opcode.LOADM, vmDt, reg1=resultRegister, value=arrayLocation+memOffset)
        } else {
            code += translateExpression(arrayIx.index, idxReg, -1)
            if(eltSize>1)
                code += codeGen.multiplyByConst(VmDataType.BYTE, idxReg, eltSize, arrayIx.position)
            if(vmDt==VmDataType.FLOAT)
                code += IRCodeInstruction(Opcode.LOADX, VmDataType.FLOAT, fpReg1 = resultFpRegister, reg1=idxReg, value = arrayLocation)
            else
                code += IRCodeInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, value = arrayLocation)
        }
        return code
    }

    private fun translate(expr: PtPrefix, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(expr.position)
        code += translateExpression(expr.value, resultRegister, -1)
        val vmDt = codeGen.vmType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                code += IRCodeInstruction(Opcode.NEG, vmDt, reg1=resultRegister)
            }
            "~" -> {
                val mask = if(vmDt==VmDataType.BYTE) 0x00ff else 0xffff
                code += IRCodeInstruction(Opcode.XOR, vmDt, reg1=resultRegister, value=mask)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return code
    }

    private fun translate(cast: PtTypeCast, predefinedResultRegister: Int, predefinedResultFpRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(cast.position)
        if(cast.type==cast.value.type)
            return code
        val actualResultFpReg = if(predefinedResultFpRegister>=0) predefinedResultFpRegister else codeGen.vmRegisters.nextFreeFloat()
        val actualResultReg = if(predefinedResultRegister>=0) predefinedResultRegister else codeGen.vmRegisters.nextFree()
        if(cast.value.type==DataType.FLOAT) {
            // a cast from float to integer, so evaluate the value into a float register first
            code += translateExpression(cast.value, -1, actualResultFpReg)
        }
        else
            code += translateExpression(cast.value, actualResultReg, -1)
        when(cast.type) {
            DataType.UBYTE -> {
                when(cast.value.type) {
                    DataType.BYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> code += IRCodeInstruction(Opcode.FTOUB, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.BYTE -> {
                when(cast.value.type) {
                    DataType.UBYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> code += IRCodeInstruction(Opcode.FTOSB, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.UWORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> uword:   sign extend
                        code += IRCodeInstruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = actualResultReg)
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        code += IRCodeInstruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = actualResultReg)
                    }
                    DataType.WORD -> { }
                    DataType.FLOAT -> {
                        code += IRCodeInstruction(Opcode.FTOUW, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.WORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> word:   sign extend
                        code += IRCodeInstruction(Opcode.EXTS, type = VmDataType.BYTE, reg1 = actualResultReg)
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        code += IRCodeInstruction(Opcode.EXT, type = VmDataType.BYTE, reg1 = actualResultReg)
                    }
                    DataType.UWORD -> { }
                    DataType.FLOAT -> {
                        code += IRCodeInstruction(Opcode.FTOSW, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.FLOAT -> {
                code += when(cast.value.type) {
                    DataType.UBYTE -> {
                        IRCodeInstruction(Opcode.FFROMUB, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    DataType.BYTE -> {
                        IRCodeInstruction(Opcode.FFROMSB, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    DataType.UWORD -> {
                        IRCodeInstruction(Opcode.FFROMUW, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    DataType.WORD -> {
                        IRCodeInstruction(Opcode.FFROMSW, VmDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            else -> throw AssemblyError("weird cast type")
        }
        return code
    }

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val vmDt = codeGen.vmType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
        return when(binExpr.operator) {
            "+" -> operatorPlus(binExpr, vmDt, resultRegister, resultFpRegister)
            "-" -> operatorMinus(binExpr, vmDt, resultRegister, resultFpRegister)
            "*" -> operatorMultiply(binExpr, vmDt, resultRegister, resultFpRegister)
            "/" -> operatorDivide(binExpr, vmDt, resultRegister, resultFpRegister, signed)
            "%" -> operatorModulo(binExpr, vmDt, resultRegister)
            "|" -> operatorOr(binExpr, vmDt, resultRegister)
            "&" -> operatorAnd(binExpr, vmDt, resultRegister)
            "^" -> operatorXor(binExpr, vmDt, resultRegister)
            "<<" -> operatorShiftLeft(binExpr, vmDt, resultRegister)
            ">>" -> operatorShiftRight(binExpr, vmDt, resultRegister, signed)
            "==" -> operatorEquals(binExpr, vmDt, resultRegister, false)
            "!=" -> operatorEquals(binExpr, vmDt, resultRegister, true)
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
    ): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(vmDt==VmDataType.FLOAT) {
            val leftFpReg = codeGen.vmRegisters.nextFreeFloat()
            val rightFpReg = codeGen.vmRegisters.nextFreeFloat()
            val zeroRegister = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, -1, leftFpReg)
            code += translateExpression(binExpr.right, -1, rightFpReg)
            code += IRCodeInstruction(Opcode.FCOMP, VmDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg)
            code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=zeroRegister, value=0)
            val ins = if (signed) {
                if (greaterEquals) Opcode.SGES else Opcode.SGTS
            } else {
                if (greaterEquals) Opcode.SGE else Opcode.SGT
            }
            code += IRCodeInstruction(ins, VmDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                code += translate(comparisonCall, resultRegister, -1)
                val zeroRegister = codeGen.vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=zeroRegister, value=0)
                code += if(greaterEquals)
                    IRCodeInstruction(Opcode.SGES, VmDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                else
                    IRCodeInstruction(Opcode.SGTS, VmDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
            } else {
                val rightResultReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += translateExpression(binExpr.right, rightResultReg, -1)
                val ins = if (signed) {
                    if (greaterEquals) Opcode.SGES else Opcode.SGTS
                } else {
                    if (greaterEquals) Opcode.SGE else Opcode.SGT
                }
                code += IRCodeInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
            }
        }
        return code
    }

    private fun operatorLessThan(
        binExpr: PtBinaryExpression,
        vmDt: VmDataType,
        resultRegister: Int,
        signed: Boolean,
        lessEquals: Boolean
    ): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(vmDt==VmDataType.FLOAT) {
            val leftFpReg = codeGen.vmRegisters.nextFreeFloat()
            val rightFpReg = codeGen.vmRegisters.nextFreeFloat()
            val zeroRegister = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, -1, leftFpReg)
            code += translateExpression(binExpr.right, -1, rightFpReg)
            code += IRCodeInstruction(Opcode.FCOMP, VmDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg)
            code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=zeroRegister, value=0)
            val ins = if (signed) {
                if (lessEquals) Opcode.SLES else Opcode.SLTS
            } else {
                if (lessEquals) Opcode.SLE else Opcode.SLT
            }
            code += IRCodeInstruction(ins, VmDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                code += translate(comparisonCall, resultRegister, -1)
                val zeroRegister = codeGen.vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=zeroRegister, value=0)
                code += if(lessEquals)
                    IRCodeInstruction(Opcode.SLES, VmDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                else
                    IRCodeInstruction(Opcode.SLTS, VmDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
            } else {
                val rightResultReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += translateExpression(binExpr.right, rightResultReg, -1)
                val ins = if (signed) {
                    if (lessEquals) Opcode.SLES else Opcode.SLTS
                } else {
                    if (lessEquals) Opcode.SLE else Opcode.SLT
                }
                code += IRCodeInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
            }
        }
        return code
    }

    private fun operatorEquals(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, notEquals: Boolean): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(vmDt==VmDataType.FLOAT) {
            val leftFpReg = codeGen.vmRegisters.nextFreeFloat()
            val rightFpReg = codeGen.vmRegisters.nextFreeFloat()
            code += translateExpression(binExpr.left, -1, leftFpReg)
            code += translateExpression(binExpr.right, -1, rightFpReg)
            if (notEquals) {
                code += IRCodeInstruction(Opcode.FCOMP, VmDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg)
            } else {
                val label = codeGen.createLabelName()
                val valueReg = codeGen.vmRegisters.nextFree()
                code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=resultRegister, value=1)
                code += IRCodeInstruction(Opcode.FCOMP, VmDataType.FLOAT, reg1=valueReg, fpReg1 = leftFpReg, fpReg2 = rightFpReg)
                code += IRCodeInstruction(Opcode.BZ, VmDataType.BYTE, reg1=valueReg, labelSymbol = label)
                code += IRCodeInstruction(Opcode.LOAD, VmDataType.BYTE, reg1=resultRegister, value=0)
                code += IRCodeLabel(label)
            }
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                code += translate(comparisonCall, resultRegister, -1)
                if(!notEquals)
                    code += IRCodeInstruction(Opcode.INV, vmDt, reg1=resultRegister)
                code += IRCodeInstruction(Opcode.AND, vmDt, reg1=resultRegister, value=1)
            } else {
                val rightResultReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += translateExpression(binExpr.right, rightResultReg, -1)
                val opcode = if (notEquals) Opcode.SNE else Opcode.SEQ
                code += IRCodeInstruction(opcode, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
            }
        }
        return code
    }

    private fun operatorShiftRight(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(codeGen.isOne(binExpr.right)) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            val opc = if (signed) Opcode.ASR else Opcode.LSR
            code += IRCodeInstruction(opc, vmDt, reg1 = resultRegister)
        } else {
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            val opc = if (signed) Opcode.ASRN else Opcode.LSRN
            code += IRCodeInstruction(opc, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
        }
        return code
    }

    internal fun operatorShiftRightInplace(address: Int, vmDt: VmDataType,  signed: Boolean, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        if(codeGen.isOne(operand)) {
            val opc = if (signed) Opcode.ASRM else Opcode.LSRM
            code += IRCodeInstruction(opc, vmDt, value=address)
        } else {
            val operandReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(operand, operandReg, -1)
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            code += IRCodeInstruction(opc, vmDt, reg1 = operandReg, value=address)
        }
        return code
    }

    private fun operatorShiftLeft(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(codeGen.isOne(binExpr.right)){
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += IRCodeInstruction(Opcode.LSL, vmDt, reg1=resultRegister)
        } else {
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += IRCodeInstruction(Opcode.LSLN, vmDt, reg1=resultRegister, rightResultReg)
        }
        return code
    }

    internal fun operatorShiftLeftInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        if(codeGen.isOne(operand)){
            code += IRCodeInstruction(Opcode.LSLM, vmDt, value=address)
        } else {
            val operandReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(operand, operandReg, -1)
            code += IRCodeInstruction(Opcode.LSLNM, vmDt, reg1=operandReg, value=address)
        }
        return code
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(binExpr.right is PtNumber) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += IRCodeInstruction(Opcode.XOR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
        } else {
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += IRCodeInstruction(Opcode.XORR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
        }
        return code
    }

    internal fun operatorXorInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        val operandReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(operand, operandReg, -1)
        code += IRCodeInstruction(Opcode.XORM, vmDt, reg1=operandReg, value = address)
        return code
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(binExpr.right is PtNumber) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += IRCodeInstruction(Opcode.AND, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
        } else {
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += IRCodeInstruction(Opcode.ANDR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
        }
        return code
    }

    internal  fun operatorAndInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        val operandReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(operand, operandReg, -1)
        code += IRCodeInstruction(Opcode.ANDM, vmDt, reg1=operandReg, value=address)
        return code
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(binExpr.right is PtNumber) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += IRCodeInstruction(Opcode.OR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
        } else {
            val rightResultReg = codeGen.vmRegisters.nextFree()
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += IRCodeInstruction(Opcode.ORR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
        }
        return code
    }

    internal fun operatorOrInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        val operandReg = codeGen.vmRegisters.nextFree()
        code += translateExpression(operand, operandReg, -1)
        code += IRCodeInstruction(Opcode.ORM, vmDt, reg1=operandReg, value = address)
        return code
    }

    private fun operatorModulo(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int): IRCodeChunk {
        if(vmDt==VmDataType.FLOAT)
            throw IllegalArgumentException("floating-point modulo not supported")
        val code = IRCodeChunk(binExpr.position)
        val rightResultReg = codeGen.vmRegisters.nextFree()
        if(binExpr.right is PtNumber) {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += IRCodeInstruction(Opcode.MOD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
        } else {
            code += translateExpression(binExpr.left, resultRegister, -1)
            code += translateExpression(binExpr.right, rightResultReg, -1)
            code += IRCodeInstruction(Opcode.MODR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
        }
        return code
    }

    private fun operatorDivide(binExpr: PtBinaryExpression,
                               vmDt: VmDataType,
                               resultRegister: Int,
                               resultFpRegister: Int,
                               signed: Boolean): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==VmDataType.FLOAT) {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                code += codeGen.divideByConstFloat(resultFpRegister, factor, binExpr.position)
            } else {
                val rightResultFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                code += translateExpression(binExpr.right, -1, rightResultFpReg)
                code += if(signed)
                    IRCodeInstruction(Opcode.DIVSR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightResultFpReg)
                else
                    IRCodeInstruction(Opcode.DIVR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightResultFpReg)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                code += translateExpression(binExpr.left, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                code += codeGen.divideByConst(vmDt, resultRegister, factor, signed, binExpr.position)
            } else {
                val rightResultReg = codeGen.vmRegisters.nextFree()
                if(binExpr.right is PtNumber) {
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += if (signed)
                        IRCodeInstruction(Opcode.DIVS, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                    else
                        IRCodeInstruction(Opcode.DIV, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                } else {
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += translateExpression(binExpr.right, rightResultReg, -1)
                    code += if (signed)
                        IRCodeInstruction(Opcode.DIVSR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                    else
                        IRCodeInstruction(Opcode.DIVR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                }
            }
        }
        return code
    }

    internal fun operatorDivideInplace(address: Int, vmDt: VmDataType, signed: Boolean, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        val constFactorRight = operand as? PtNumber
        if(vmDt==VmDataType.FLOAT) {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toFloat()
                code += codeGen.divideByConstFloatInplace(address, factor, operand.position)
            } else {
                val operandFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(operand, -1, operandFpReg)
                code += if(signed)
                    IRCodeInstruction(Opcode.DIVSM, vmDt, fpReg1 = operandFpReg, value=address)
                else
                    IRCodeInstruction(Opcode.DIVM, vmDt, fpReg1 = operandFpReg, value=address)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                code += codeGen.divideByConstInplace(vmDt, address, factor, signed, operand.position)
            } else {
                val operandReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(operand, operandReg, -1)
                code += if(signed)
                    IRCodeInstruction(Opcode.DIVSM, vmDt, reg1=operandReg, value = address)
                else
                    IRCodeInstruction(Opcode.DIVM, vmDt, reg1=operandReg, value = address)
            }
        }
        return code
    }

    private fun operatorMultiply(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        val constFactorLeft = binExpr.left as? PtNumber
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==VmDataType.FLOAT) {
            if(constFactorLeft!=null) {
                code += translateExpression(binExpr.right, -1, resultFpRegister)
                val factor = constFactorLeft.number.toFloat()
                code += codeGen.multiplyByConstFloat(resultFpRegister, factor, constFactorLeft.position)
            } else if(constFactorRight!=null) {
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                code += codeGen.multiplyByConstFloat(resultFpRegister, factor, constFactorRight.position)
            } else {
                val rightResultFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                code += translateExpression(binExpr.right, -1, rightResultFpReg)
                code += IRCodeInstruction(Opcode.MULR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg)
            }
        } else {
            if(constFactorLeft!=null && constFactorLeft.type!=DataType.FLOAT) {
                code += translateExpression(binExpr.right, resultRegister, -1)
                val factor = constFactorLeft.number.toInt()
                code += codeGen.multiplyByConst(vmDt, resultRegister, factor, constFactorLeft.position)
            } else if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                code += translateExpression(binExpr.left, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                code += codeGen.multiplyByConst(vmDt, resultRegister, factor, constFactorRight.position)
            } else {
                val rightResultReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += translateExpression(binExpr.right, rightResultReg, -1)
                code += IRCodeInstruction(Opcode.MULR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
            }
        }
        return code
    }

    internal fun operatorMultiplyInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        val constFactorRight = operand as? PtNumber
        if(vmDt==VmDataType.FLOAT) {
            if(constFactorRight!=null) {
                val factor = constFactorRight.number.toFloat()
                code += codeGen.multiplyByConstFloatInplace(address, factor, constFactorRight.position)
            } else {
                val operandFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(operand, -1, operandFpReg)
                code += IRCodeInstruction(Opcode.MULM, vmDt, fpReg1 = operandFpReg, value = address)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                code += codeGen.multiplyByConstInplace(vmDt, address, factor, constFactorRight.position)
            } else {
                val operandReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(operand, operandReg, -1)
                code += IRCodeInstruction(Opcode.MULM, vmDt, reg1=operandReg, value = address)
            }
        }
        return code
    }

    private fun operatorMinus(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(vmDt==VmDataType.FLOAT) {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                code += IRCodeInstruction(Opcode.DEC, vmDt, fpReg1 = resultFpRegister)
            }
            else {
                if(binExpr.right is PtNumber) {
                    code += translateExpression(binExpr.left, -1, resultFpRegister)
                    code += IRCodeInstruction(Opcode.SUB, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat())
                } else {
                    val rightResultFpReg = codeGen.vmRegisters.nextFreeFloat()
                    code += translateExpression(binExpr.left, -1, resultFpRegister)
                    code += translateExpression(binExpr.right, -1, rightResultFpReg)
                    code += IRCodeInstruction(Opcode.SUBR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg)
                }
            }
        } else {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += IRCodeInstruction(Opcode.DEC, vmDt, reg1=resultRegister)
            }
            else {
                if(binExpr.right is PtNumber) {
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += IRCodeInstruction(Opcode.SUB, vmDt, reg1 = resultRegister, value = (binExpr.right as PtNumber).number.toInt())
                } else {
                    val rightResultReg = codeGen.vmRegisters.nextFree()
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += translateExpression(binExpr.right, rightResultReg, -1)
                    code += IRCodeInstruction(Opcode.SUBR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                }
            }
        }
        return code
    }

    internal fun operatorMinusInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        if(vmDt==VmDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                code += IRCodeInstruction(Opcode.DECM, vmDt, value=address)
            }
            else {
                val operandFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(operand, -1, operandFpReg)
                code += IRCodeInstruction(Opcode.SUBM, vmDt, fpReg1=operandFpReg, value=address)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                code += IRCodeInstruction(Opcode.DECM, vmDt, value=address)
            }
            else {
                val operandReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(operand, operandReg, -1)
                code += IRCodeInstruction(Opcode.SUBM, vmDt, reg1=operandReg, value = address)
            }
        }
        return code
    }

    private fun operatorPlus(binExpr: PtBinaryExpression, vmDt: VmDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        val code = IRCodeChunk(binExpr.position)
        if(vmDt==VmDataType.FLOAT) {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.right, -1, resultFpRegister)
                code += IRCodeInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.left, -1, resultFpRegister)
                code += IRCodeInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister)
            }
            else {
                if(binExpr.right is PtNumber) {
                    code += translateExpression(binExpr.left, -1, resultFpRegister)
                    code += IRCodeInstruction(Opcode.ADD, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat())
                } else {
                    val rightResultFpReg = codeGen.vmRegisters.nextFreeFloat()
                    code += translateExpression(binExpr.left, -1, resultFpRegister)
                    code += translateExpression(binExpr.right, -1, rightResultFpReg)
                    code += IRCodeInstruction(Opcode.ADDR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg)
                }
            }
        } else {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.right, resultRegister, -1)
                code += IRCodeInstruction(Opcode.INC, vmDt, reg1=resultRegister)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                code += translateExpression(binExpr.left, resultRegister, -1)
                code += IRCodeInstruction(Opcode.INC, vmDt, reg1=resultRegister)
            }
            else {
                if(binExpr.right is PtNumber) {
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += IRCodeInstruction(Opcode.ADD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                } else {
                    val rightResultReg = codeGen.vmRegisters.nextFree()
                    code += translateExpression(binExpr.left, resultRegister, -1)
                    code += translateExpression(binExpr.right, rightResultReg, -1)
                    code += IRCodeInstruction(Opcode.ADDR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                }
            }
        }
        return code
    }

    internal fun operatorPlusInplace(address: Int, vmDt: VmDataType, operand: PtExpression): IRCodeChunk {
        val code = IRCodeChunk(operand.position)
        if(vmDt==VmDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                code += IRCodeInstruction(Opcode.INCM, vmDt, value = address)
            }
            else {
                val operandFpReg = codeGen.vmRegisters.nextFreeFloat()
                code += translateExpression(operand, -1, operandFpReg)
                code += IRCodeInstruction(Opcode.ADDM, vmDt, fpReg1=operandFpReg, value=address)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                code += IRCodeInstruction(Opcode.INCM, vmDt, value = address)
            }
            else {
                val operandReg = codeGen.vmRegisters.nextFree()
                code += translateExpression(operand, operandReg, -1)
                code += IRCodeInstruction(Opcode.ADDM, vmDt, reg1=operandReg, value=address)
            }
        }
        return code
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int, resultFpRegister: Int): IRCodeChunk {
        when (val callTarget = codeGen.symbolTable.flat.getValue(fcall.functionName)) {
            is StSub -> {
                val code = IRCodeChunk(fcall.position)
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = codeGen.vmType(parameter.type)
                    if(codeGen.isZero(arg)) {
                        if (paramDt == VmDataType.FLOAT) {
                            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                            code += IRCodeInstruction(Opcode.STOREZM, paramDt, value = mem)
                        } else {
                            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                            code += IRCodeInstruction(Opcode.STOREZM, paramDt, value = mem)
                        }
                    } else {
                        if (paramDt == VmDataType.FLOAT) {
                            val argFpReg = codeGen.vmRegisters.nextFreeFloat()
                            code += translateExpression(arg, -1, argFpReg)
                            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                            code += IRCodeInstruction(Opcode.STOREM, paramDt, fpReg1 = argFpReg, value = mem)
                        } else {
                            val argReg = codeGen.vmRegisters.nextFree()
                            code += translateExpression(arg, argReg, -1)
                            val mem = codeGen.allocations.get(fcall.functionName + parameter.name)
                            code += IRCodeInstruction(Opcode.STOREM, paramDt, reg1 = argReg, value = mem)
                        }
                    }
                }
                code += IRCodeInstruction(Opcode.CALL, labelSymbol=fcall.functionName)
                if(fcall.type==DataType.FLOAT) {
                    if (!fcall.void && resultFpRegister != 0) {
                        // Call convention: result value is in fr0, so put it in the required register instead.
                        code += IRCodeInstruction(Opcode.LOADR, VmDataType.FLOAT, fpReg1 = resultFpRegister, fpReg2 = 0)
                    }
                } else {
                    if (!fcall.void && resultRegister != 0) {
                        // Call convention: result value is in r0, so put it in the required register instead.
                        code += IRCodeInstruction(Opcode.LOADR, codeGen.vmType(fcall.type), reg1 = resultRegister, reg2 = 0)
                    }
                }
                return code
            }
            is StRomSub -> {
                throw AssemblyError("virtual machine doesn't yet support calling romsub $fcall")
            }
            else -> throw AssemblyError("invalid node type")
        }
    }

}
