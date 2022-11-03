package prog8.codegen.intermediate

import prog8.code.StRomSub
import prog8.code.StStaticVariable
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*


internal class ExpressionGen(private val codeGen: IRCodeGen) {
    fun translateExpression(expr: PtExpression, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        require(codeGen.registers.peekNext() > resultRegister)

        return when (expr) {
            is PtMachineRegister -> {
                if(resultRegister!=expr.register) {
                    val vmDt = codeGen.irType(expr.type)
                    val code = IRCodeChunk(null, null)
                    code += IRInstruction(Opcode.LOADR, vmDt, reg1=resultRegister, reg2=expr.register)
                    listOf(code)
                } else {
                    emptyList()
                }
            }
            is PtNumber -> {
                val vmDt = codeGen.irType(expr.type)
                val code = IRCodeChunk(null, null)
                code += if(vmDt==IRDataType.FLOAT)
                    IRInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, fpValue = expr.number.toFloat())
                else
                    IRInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt())
                listOf(code)
            }
            is PtIdentifier -> {
                val vmDt = codeGen.irType(expr.type)
                val symbol = expr.targetName.joinToString(".")
                val code = IRCodeChunk(null, null)
                code += if (expr.type in PassByValueDatatypes) {
                    if(vmDt==IRDataType.FLOAT)
                        IRInstruction(Opcode.LOADM, vmDt, fpReg1 = resultFpRegister, labelSymbol = symbol)
                    else
                        IRInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, labelSymbol = symbol)
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, labelSymbol = symbol)
                }
                listOf(code)
            }
            is PtAddressOf -> {
                val vmDt = codeGen.irType(expr.type)
                val symbol = expr.identifier.targetName.joinToString(".")
                // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
                val code = IRCodeChunk(null, null)
                code += IRInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, labelSymbol = symbol)
                listOf(code)
            }
            is PtMemoryByte -> {
                val result = mutableListOf<IRCodeChunkBase>()
                if(expr.address is PtNumber) {
                    val address = (expr.address as PtNumber).number.toInt()
                    addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, value = address), null)
                } else {
                    val addressRegister = codeGen.registers.nextFree()
                    result += translateExpression(expr.address, addressRegister, -1)
                    addInstr(result, IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1=resultRegister, reg2=addressRegister), null)
                }
                result
            }
            is PtTypeCast -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtPrefix -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtArrayIndexer -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtBinaryExpression -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtBuiltinFunctionCall -> {
                codeGen.translateBuiltinFunc(expr, resultRegister)
            }
            is PtFunctionCall -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtContainmentCheck -> {
                translate(expr, resultRegister, resultFpRegister)
            }
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
    }

    private fun translate(check: PtContainmentCheck, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        result += translateExpression(check.element, resultRegister, -1)   // load the element to check in resultRegister
        val iterable = codeGen.symbolTable.flat.getValue(check.iterable.targetName) as StStaticVariable
        when(iterable.dt) {
            DataType.STR -> {
                val call = PtFunctionCall(listOf("prog8_lib", "string_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                result += translate(call, resultRegister, resultFpRegister)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                val call = PtFunctionCall(listOf("prog8_lib", "bytearray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                result += translate(call, resultRegister, resultFpRegister)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                val call = PtFunctionCall(listOf("prog8_lib", "wordarray_contains"), false, DataType.UBYTE, check.position)
                call.children.add(check.element)
                call.children.add(check.iterable)
                call.children.add(PtNumber(DataType.UBYTE, iterable.length!!.toDouble(), iterable.position))
                result += translate(call, resultRegister, resultFpRegister)
            }
            DataType.ARRAY_F -> throw AssemblyError("containment check in float-array not supported")
            else -> throw AssemblyError("weird iterable dt ${iterable.dt} for ${check.iterable.targetName}")
        }
        return result
    }

    private fun translate(arrayIx: PtArrayIndexer, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.irType(arrayIx.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val idxReg = codeGen.registers.nextFree()
        val arrayVarSymbol = arrayIx.variable.targetName.joinToString(".")

        if(arrayIx.variable.type==DataType.UWORD) {
            // indexing a pointer var instead of a real array or string
            if(eltSize!=1)
                throw AssemblyError("non-array var indexing requires bytes dt")
            if(arrayIx.index.type!=DataType.UBYTE)
                throw AssemblyError("non-array var indexing requires bytes index")
            result += translateExpression(arrayIx.index, idxReg, -1)
            addInstr(result, IRInstruction(Opcode.LOADIX, vmDt, reg1=resultRegister, reg2=idxReg, labelSymbol = arrayVarSymbol), null)
            return result
        }

        if(arrayIx.index is PtNumber) {
            val memOffset = ((arrayIx.index as PtNumber).number.toInt() * eltSize).toString()
            if(vmDt==IRDataType.FLOAT)
                addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1=resultFpRegister, labelSymbol = "$arrayVarSymbol+$memOffset"), null)
            else
                addInstr(result, IRInstruction(Opcode.LOADM, vmDt, reg1=resultRegister, labelSymbol = "$arrayVarSymbol+$memOffset"), null)
        } else {
            result += translateExpression(arrayIx.index, idxReg, -1)
            if(eltSize>1)
                result += codeGen.multiplyByConst(IRDataType.BYTE, idxReg, eltSize)
            if(vmDt==IRDataType.FLOAT)
                addInstr(result, IRInstruction(Opcode.LOADX, IRDataType.FLOAT, fpReg1 = resultFpRegister, reg1=idxReg, labelSymbol = arrayVarSymbol), null)
            else
                addInstr(result, IRInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=idxReg, labelSymbol = arrayVarSymbol), null)
        }
        return result
    }

    private fun translate(expr: PtPrefix, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        result += translateExpression(expr.value, resultRegister, resultFpRegister)
        val vmDt = codeGen.irType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                if(vmDt==IRDataType.FLOAT)
                    addInstr(result, IRInstruction(Opcode.NEG, vmDt, fpReg1 = resultFpRegister), null)
                else
                    addInstr(result, IRInstruction(Opcode.NEG, vmDt, reg1 = resultRegister), null)
            }
            "~" -> {
                val mask = if(vmDt==IRDataType.BYTE) 0x00ff else 0xffff
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = resultRegister, value = mask), null)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return result
    }

    private fun translate(cast: PtTypeCast, predefinedResultRegister: Int, predefinedResultFpRegister: Int): IRCodeChunks {
        if(cast.type==cast.value.type)
            return emptyList()
        val result = mutableListOf<IRCodeChunkBase>()
        val actualResultFpReg = if(predefinedResultFpRegister>=0) predefinedResultFpRegister else codeGen.registers.nextFreeFloat()
        val actualResultReg = if(predefinedResultRegister>=0) predefinedResultRegister else codeGen.registers.nextFree()
        if(cast.value.type==DataType.FLOAT) {
            // a cast from float to integer, so evaluate the value into a float register first
            result += translateExpression(cast.value, -1, actualResultFpReg)
        }
        else
            result += translateExpression(cast.value, actualResultReg, -1)
        when(cast.type) {
            DataType.UBYTE -> {
                when(cast.value.type) {
                    DataType.BYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> addInstr(result, IRInstruction(Opcode.FTOUB, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg), null)
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.BYTE -> {
                when(cast.value.type) {
                    DataType.UBYTE, DataType.UWORD, DataType.WORD -> { /* just keep the LSB as it is */ }
                    DataType.FLOAT -> addInstr(result, IRInstruction(Opcode.FTOSB, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg), null)
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.UWORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> uword:   sign extend
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg), null)
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg), null)
                    }
                    DataType.WORD -> { }
                    DataType.FLOAT -> {
                        addInstr(result, IRInstruction(Opcode.FTOUW, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.WORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> word:   sign extend
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg), null)
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg), null)
                    }
                    DataType.UWORD -> { }
                    DataType.FLOAT -> {
                        addInstr(result, IRInstruction(Opcode.FTOSW, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.FLOAT -> {
                val instr = when(cast.value.type) {
                    DataType.UBYTE -> IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    DataType.BYTE -> IRInstruction(Opcode.FFROMSB, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    DataType.UWORD -> IRInstruction(Opcode.FFROMUW, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    DataType.WORD -> IRInstruction(Opcode.FFROMSW, IRDataType.FLOAT, reg1=actualResultReg, fpReg1 = actualResultFpReg)
                    else -> throw AssemblyError("weird cast value type")
                }
                addInstr(result, instr, null)
            }
            else -> throw AssemblyError("weird cast type")
        }
        return result
    }

    private fun translate(binExpr: PtBinaryExpression, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val vmDt = codeGen.irType(binExpr.left.type)
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
        vmDt: IRDataType,
        resultRegister: Int,
        signed: Boolean,
        greaterEquals: Boolean
    ): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftFpReg = codeGen.registers.nextFreeFloat()
            val rightFpReg = codeGen.registers.nextFreeFloat()
            val zeroRegister = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, -1, leftFpReg)
            result += translateExpression(binExpr.right, -1, rightFpReg)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg), null)
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
            val ins = if (signed) {
                if (greaterEquals) Opcode.SGES else Opcode.SGTS
            } else {
                if (greaterEquals) Opcode.SGE else Opcode.SGT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister), null)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                result += translate(comparisonCall, resultRegister, -1)
                val zeroRegister = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
                val instr = if(greaterEquals)
                    IRInstruction(Opcode.SGES, IRDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                else
                    IRInstruction(Opcode.SGTS, IRDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                addInstr(result, instr, null)
            } else {
                val rightResultReg = codeGen.registers.nextFree()
                result += translateExpression(binExpr.left, resultRegister, -1)
                result += translateExpression(binExpr.right, rightResultReg, -1)
                val ins = if (signed) {
                    if (greaterEquals) Opcode.SGES else Opcode.SGTS
                } else {
                    if (greaterEquals) Opcode.SGE else Opcode.SGT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
            }
        }
        return result
    }

    private fun operatorLessThan(
        binExpr: PtBinaryExpression,
        vmDt: IRDataType,
        resultRegister: Int,
        signed: Boolean,
        lessEquals: Boolean
    ): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftFpReg = codeGen.registers.nextFreeFloat()
            val rightFpReg = codeGen.registers.nextFreeFloat()
            val zeroRegister = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, -1, leftFpReg)
            result += translateExpression(binExpr.right, -1, rightFpReg)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg), null)
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
            val ins = if (signed) {
                if (lessEquals) Opcode.SLES else Opcode.SLTS
            } else {
                if (lessEquals) Opcode.SLE else Opcode.SLT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister), null)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                result += translate(comparisonCall, resultRegister, -1)
                val zeroRegister = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
                val ins = if(lessEquals)
                    IRInstruction(Opcode.SLES, IRDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                else
                    IRInstruction(Opcode.SLTS, IRDataType.BYTE, reg1=resultRegister, reg2=zeroRegister)
                addInstr(result, ins, null)
            } else {
                val rightResultReg = codeGen.registers.nextFree()
                result += translateExpression(binExpr.left, resultRegister, -1)
                result += translateExpression(binExpr.right, rightResultReg, -1)
                val ins = if (signed) {
                    if (lessEquals) Opcode.SLES else Opcode.SLTS
                } else {
                    if (lessEquals) Opcode.SLE else Opcode.SLT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
            }
        }
        return result
    }

    private fun operatorEquals(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, notEquals: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftFpReg = codeGen.registers.nextFreeFloat()
            val rightFpReg = codeGen.registers.nextFreeFloat()
            result += translateExpression(binExpr.left, -1, leftFpReg)
            result += translateExpression(binExpr.right, -1, rightFpReg)
            if (notEquals) {
                addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftFpReg, fpReg2 = rightFpReg), null)
            } else {
                val label = codeGen.createLabelName()
                val valueReg = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, value=1), null)
                addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=valueReg, fpReg1 = leftFpReg, fpReg2 = rightFpReg), null)
                addInstr(result, IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=valueReg, labelSymbol = label), null)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, value=0), null)
                result += IRCodeChunk(label, null)
            }
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val comparisonCall = PtFunctionCall(listOf("prog8_lib", "string_compare"), false, DataType.BYTE, Position.DUMMY)
                comparisonCall.children.add(binExpr.left)
                comparisonCall.children.add(binExpr.right)
                result += translate(comparisonCall, resultRegister, -1)
                if(!notEquals)
                    addInstr(result, IRInstruction(Opcode.INV, vmDt, reg1=resultRegister), null)
                addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1=resultRegister, value=1), null)
            } else {
                val rightResultReg = codeGen.registers.nextFree()
                result += translateExpression(binExpr.left, resultRegister, -1)
                result += translateExpression(binExpr.right, rightResultReg, -1)
                val opcode = if (notEquals) Opcode.SNE else Opcode.SEQ
                addInstr(result, IRInstruction(opcode, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
            }
        }
        return result
    }

    private fun operatorShiftRight(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, signed: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(binExpr.right)) {
            result += translateExpression(binExpr.left, resultRegister, -1)
            val opc = if (signed) Opcode.ASR else Opcode.LSR
            addInstr(result, IRInstruction(opc, vmDt, reg1 = resultRegister), null)
        } else {
            val rightResultReg = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            val opc = if (signed) Opcode.ASRN else Opcode.LSRN
            addInstr(result, IRInstruction(opc, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
        }
        return result
    }

    internal fun operatorShiftRightInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)) {
            val opc = if (signed) Opcode.ASRM else Opcode.LSRM
            val ins = if(knownAddress!=null)
                IRInstruction(opc, vmDt, value=knownAddress)
            else
                IRInstruction(opc, vmDt, labelSymbol = symbol)
            addInstr(result, ins, null)
        } else {
            val operandReg = codeGen.registers.nextFree()
            result += translateExpression(operand, operandReg, -1)
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            val ins = if(knownAddress!=null)
                IRInstruction(opc, vmDt, reg1 = operandReg, value=knownAddress)
            else
                IRInstruction(opc, vmDt, reg1 = operandReg, labelSymbol = symbol)
            addInstr(result, ins, null)
        }
        return result
    }

    private fun operatorShiftLeft(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(binExpr.right)){
            result += translateExpression(binExpr.left, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.LSL, vmDt, reg1=resultRegister), null)
        } else {
            val rightResultReg = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            addInstr(result, IRInstruction(Opcode.LSLN, vmDt, reg1=resultRegister, rightResultReg), null)
        }
        return result
    }

    internal fun operatorShiftLeftInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)){
            addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.LSLM, vmDt, value=knownAddress)
            else
                IRInstruction(Opcode.LSLM, vmDt, labelSymbol = symbol)
                , null)
        } else {
            val operandReg = codeGen.registers.nextFree()
            result += translateExpression(operand, operandReg, -1)
            addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.LSLNM, vmDt, reg1=operandReg, value=knownAddress)
            else
                IRInstruction(Opcode.LSLNM, vmDt, reg1=operandReg, labelSymbol = symbol)
                ,null)
        }
        return result
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            result += translateExpression(binExpr.left, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val rightResultReg = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            addInstr(result, IRInstruction(Opcode.XORR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
        }
        return result
    }

    internal fun operatorXorInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val operandReg = codeGen.registers.nextFree()
        result += translateExpression(operand, operandReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.XORM, vmDt, reg1=operandReg, value = knownAddress)
        else
            IRInstruction(Opcode.XORM, vmDt, reg1=operandReg, labelSymbol = symbol)
            ,null)
        return result
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            result += translateExpression(binExpr.left, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val rightResultReg = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            addInstr(result, IRInstruction(Opcode.ANDR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
        }
        return result
    }

    internal  fun operatorAndInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val operandReg = codeGen.registers.nextFree()
        result += translateExpression(operand, operandReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.ANDM, vmDt, reg1=operandReg, value=knownAddress)
        else
            IRInstruction(Opcode.ANDM, vmDt, reg1=operandReg, labelSymbol = symbol)
            ,null)
        return result
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            result += translateExpression(binExpr.left, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.OR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val rightResultReg = codeGen.registers.nextFree()
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            addInstr(result, IRInstruction(Opcode.ORR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
        }
        return result
    }

    internal fun operatorOrInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val operandReg = codeGen.registers.nextFree()
        result += translateExpression(operand, operandReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.ORM, vmDt, reg1=operandReg, value = knownAddress)
        else
            IRInstruction(Opcode.ORM, vmDt, reg1=operandReg, labelSymbol = symbol)
            , null)
        return result
    }

    private fun operatorModulo(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        require(vmDt!=IRDataType.FLOAT) {"floating-point modulo not supported"}
        val result = mutableListOf<IRCodeChunkBase>()
        val rightResultReg = codeGen.registers.nextFree()
        if(binExpr.right is PtNumber) {
            result += translateExpression(binExpr.left, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.MOD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            result += translateExpression(binExpr.left, resultRegister, -1)
            result += translateExpression(binExpr.right, rightResultReg, -1)
            addInstr(result, IRInstruction(Opcode.MODR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
        }
        return result
    }

    private fun operatorDivide(binExpr: PtBinaryExpression,
                               vmDt: IRDataType,
                               resultRegister: Int,
                               resultFpRegister: Int,
                               signed: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.divideByConstFloat(resultFpRegister, factor)
            } else {
                val rightResultFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                result += translateExpression(binExpr.right, -1, rightResultFpReg)
                addInstr(result, if(signed)
                    IRInstruction(Opcode.DIVSR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightResultFpReg)
                else
                    IRInstruction(Opcode.DIVR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightResultFpReg)
                    , null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                result += translateExpression(binExpr.left, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConst(vmDt, resultRegister, factor, signed)
            } else {
                val rightResultReg = codeGen.registers.nextFree()
                if(binExpr.right is PtNumber) {
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    addInstr(result, if (signed)
                        IRInstruction(Opcode.DIVS, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                    else
                        IRInstruction(Opcode.DIV, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                        , null)
                } else {
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    result += translateExpression(binExpr.right, rightResultReg, -1)
                    addInstr(result, if (signed)
                        IRInstruction(Opcode.DIVSR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                    else
                        IRInstruction(Opcode.DIVR, vmDt, reg1 = resultRegister, reg2 = rightResultReg)
                        , null)
                }
            }
        }
        return result
    }

    internal fun operatorDivideInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toFloat()
                result += codeGen.divideByConstFloatInplace(knownAddress, symbol, factor)
            } else {
                val operandFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(operand, -1, operandFpReg)
                val ins = if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = operandFpReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = operandFpReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = operandFpReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = operandFpReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConstInplace(vmDt, knownAddress, symbol, factor, signed)
            } else {
                val operandReg = codeGen.registers.nextFree()
                result += translateExpression(operand, operandReg, -1)
                val ins = if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = operandReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = operandReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = operandReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = operandReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
            }
        }
        return result
    }

    private fun operatorMultiply(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorLeft = binExpr.left as? PtNumber
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorLeft!=null) {
                result += translateExpression(binExpr.right, -1, resultFpRegister)
                val factor = constFactorLeft.number.toFloat()
                result += codeGen.multiplyByConstFloat(resultFpRegister, factor)
            } else if(constFactorRight!=null) {
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.multiplyByConstFloat(resultFpRegister, factor)
            } else {
                val rightResultFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                result += translateExpression(binExpr.right, -1, rightResultFpReg)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg), null)
            }
        } else {
            if(constFactorLeft!=null && constFactorLeft.type!=DataType.FLOAT) {
                result += translateExpression(binExpr.right, resultRegister, -1)
                val factor = constFactorLeft.number.toInt()
                result += codeGen.multiplyByConst(vmDt, resultRegister, factor)
            } else if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                result += translateExpression(binExpr.left, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConst(vmDt, resultRegister, factor)
            } else {
                val rightResultReg = codeGen.registers.nextFree()
                result += translateExpression(binExpr.left, resultRegister, -1)
                result += translateExpression(binExpr.right, rightResultReg, -1)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
            }
        }
        return result
    }

    internal fun operatorMultiplyInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = operand as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null) {
                val factor = constFactorRight.number.toFloat()
                result += codeGen.multiplyByConstFloatInplace(knownAddress, symbol, factor)
            } else {
                val operandFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(operand, -1, operandFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, vmDt, fpReg1 = operandFpReg, value = knownAddress)
                else
                    IRInstruction(Opcode.MULM, vmDt, fpReg1 = operandFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConstInplace(vmDt, knownAddress, symbol, factor)
            } else {
                val operandReg = codeGen.registers.nextFree()
                result += translateExpression(operand, operandReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, vmDt, reg1=operandReg, value = knownAddress)
                else
                    IRInstruction(Opcode.MULM, vmDt, reg1=operandReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    private fun operatorMinus(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, fpReg1 = resultFpRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    result += translateExpression(binExpr.left, -1, resultFpRegister)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat()), null)
                } else {
                    val rightResultFpReg = codeGen.registers.nextFreeFloat()
                    result += translateExpression(binExpr.left, -1, resultFpRegister)
                    result += translateExpression(binExpr.right, -1, rightResultFpReg)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg), null)
                }
            }
        } else {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.left, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, reg1=resultRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, reg1 = resultRegister, value = (binExpr.right as PtNumber).number.toInt()), null)
                } else {
                    val rightResultReg = codeGen.registers.nextFree()
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    result += translateExpression(binExpr.right, rightResultReg, -1)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
                }
            }
        }
        return result
    }

    internal fun operatorMinusInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, value=knownAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val operandFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(operand, -1, operandFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=operandFpReg, value=knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=operandFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, value=knownAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val operandReg = codeGen.registers.nextFree()
                result += translateExpression(operand, operandReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, reg1=operandReg, value = knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, reg1=operandReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    private fun operatorPlus(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.right, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister), null)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.left, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    result += translateExpression(binExpr.left, -1, resultFpRegister)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat()), null)
                } else {
                    val rightResultFpReg = codeGen.registers.nextFreeFloat()
                    result += translateExpression(binExpr.left, -1, resultFpRegister)
                    result += translateExpression(binExpr.right, -1, rightResultFpReg)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightResultFpReg), null)
                }
            }
        } else {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.right, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=resultRegister), null)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                result += translateExpression(binExpr.left, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=resultRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
                } else {
                    val rightResultReg = codeGen.registers.nextFree()
                    result += translateExpression(binExpr.left, resultRegister, -1)
                    result += translateExpression(binExpr.right, rightResultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, reg1 = resultRegister, reg2 = rightResultReg), null)
                }
            }
        }
        return result
    }

    internal fun operatorPlusInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.INCM, vmDt, value = knownAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val operandFpReg = codeGen.registers.nextFreeFloat()
                result += translateExpression(operand, -1, operandFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.ADDM, vmDt, fpReg1=operandFpReg, value = knownAddress)
                else
                    IRInstruction(Opcode.ADDM, vmDt, fpReg1=operandFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.INCM, vmDt, value = knownAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val operandReg = codeGen.registers.nextFree()
                result += translateExpression(operand, operandReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.ADDM, vmDt, reg1=operandReg, value=knownAddress)
                else
                    IRInstruction(Opcode.ADDM, vmDt, reg1=operandReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    fun translate(fcall: PtFunctionCall, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        when (val callTarget = codeGen.symbolTable.flat.getValue(fcall.functionName)) {
            is StSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = codeGen.irType(parameter.type)
                    val symbol = (fcall.functionName + parameter.name).joinToString(".")
                    if(codeGen.isZero(arg)) {
                        addInstr(result, IRInstruction(Opcode.STOREZM, paramDt, labelSymbol = symbol), null)
                    } else {
                        if (paramDt == IRDataType.FLOAT) {
                            val argFpReg = codeGen.registers.nextFreeFloat()
                            result += translateExpression(arg, -1, argFpReg)
                            addInstr(result, IRInstruction(Opcode.STOREM, paramDt, fpReg1 = argFpReg, labelSymbol = symbol), null)
                        } else {
                            val argReg = codeGen.registers.nextFree()
                            result += translateExpression(arg, argReg, -1)
                            addInstr(result, IRInstruction(Opcode.STOREM, paramDt, reg1 = argReg, labelSymbol = symbol), null)
                        }
                    }
                }
                addInstr(result, IRInstruction(Opcode.CALL, labelSymbol=fcall.functionName.joinToString(".")), null)
                if(fcall.type==DataType.FLOAT) {
                    if (!fcall.void && resultFpRegister != 0) {
                        // Call convention: result value is in fr0, so put it in the required register instead.
                        addInstr(result, IRInstruction(Opcode.LOADR, IRDataType.FLOAT, fpReg1 = resultFpRegister, fpReg2 = 0), null)
                    }
                } else {
                    if (!fcall.void && resultRegister != 0) {
                        // Call convention: result value is in r0, so put it in the required register instead.
                        addInstr(result, IRInstruction(Opcode.LOADR, codeGen.irType(fcall.type), reg1 = resultRegister, reg2 = 0), null)
                    }
                }
                return result
            }
            is StRomSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = codeGen.irType(parameter.type)
                    val paramRegStr = if(parameter.register.registerOrPair!=null) parameter.register.registerOrPair.toString() else parameter.register.statusflag.toString()
                    if(codeGen.isZero(arg)) {
                        addInstr(result, IRInstruction(Opcode.STOREZCPU, paramDt, labelSymbol = paramRegStr), null)
                    } else {
                        if (paramDt == IRDataType.FLOAT)
                            throw AssemblyError("doesn't support float register argument in asm romsub")
                        val argReg = codeGen.registers.nextFree()
                        result += translateExpression(arg, argReg, -1)
                        addInstr(result, IRInstruction(Opcode.STORECPU, paramDt, reg1 = argReg, labelSymbol = paramRegStr), null)
                    }
                }
                addInstr(result, IRInstruction(Opcode.CALL, value=callTarget.address.toInt()), null)
                if(!fcall.void) {
                    when(callTarget.returns.size) {
                        0 -> throw AssemblyError("expect a return value")
                        1 -> {
                            if(fcall.type==DataType.FLOAT)
                                throw AssemblyError("doesn't support float register result in asm romsub")
                            val returns = callTarget.returns.single()
                            val regStr = if(returns.registerOrPair!=null) returns.registerOrPair.toString() else returns.statusflag.toString()
                            addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultRegister, labelSymbol = regStr), null)
                        }
                        else -> {
                            val returnRegister = callTarget.returns.singleOrNull{ it.registerOrPair!=null }
                            if(returnRegister!=null) {
                                // we skip the other values returned in the status flags.
                                val regStr = returnRegister.registerOrPair.toString()
                                addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultRegister, labelSymbol = regStr), null)
                            } else {
                                val firstReturnRegister = callTarget.returns.firstOrNull{ it.registerOrPair!=null }
                                if(firstReturnRegister!=null) {
                                    // we just take the first register return value and ignore the rest.
                                    val regStr = firstReturnRegister.registerOrPair.toString()
                                    addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultRegister, labelSymbol = regStr), null)
                                } else {
                                    throw AssemblyError("invalid number of return values from call")
                                }
                            }
                        }
                    }
                }
                return result
            }
            else -> throw AssemblyError("invalid node type")
        }
    }

}


internal fun addInstr(code: MutableList<IRCodeChunkBase>, instr: IRInstruction, label: String?) {
    code += IRCodeChunk(label, null).also {
        it += instr
    }
}
