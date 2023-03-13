package prog8.codegen.intermediate

import prog8.code.StRomSub
import prog8.code.StStaticVariable
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.DataType
import prog8.code.core.PassByValueDatatypes
import prog8.code.core.SignedDatatypes
import prog8.intermediate.*

internal class ExpressionCodeResult(val chunks: IRCodeChunks, val dt: IRDataType, val resultReg: Int, val resultFpReg: Int) {
    constructor(chunks: IRCodeChunk, dt: IRDataType, resultReg: Int, resultFpReg: Int) : this(listOf(chunks), dt, resultReg, resultFpReg)

    companion object {
        val EMPTY: ExpressionCodeResult = ExpressionCodeResult(emptyList(), IRDataType.BYTE, -1, -1)
    }

    init {
        if(resultReg!=-1) require(resultFpReg==-1)
        if(resultFpReg!=-1) require(resultReg==-1)
    }
}

internal class ExpressionGen(private val codeGen: IRCodeGen) {

    fun translateExpression(expr: PtExpression): ExpressionCodeResult {
        return when (expr) {
            is PtMachineRegister -> {
                ExpressionCodeResult(emptyList(), codeGen.irType(expr.type), expr.register, -1)
            }
            is PtNumber -> {
                val vmDt = codeGen.irType(expr.type)
                val code = IRCodeChunk(null, null)
                if(vmDt==IRDataType.FLOAT) {
                    val resultFpRegister = codeGen.registers.nextFreeFloat()
                    code += IRInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, fpValue = expr.number.toFloat())
                    ExpressionCodeResult(code, vmDt,-1, resultFpRegister)
                }
                else {
                    val resultRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, value = expr.number.toInt())
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtIdentifier -> {
                val vmDt = codeGen.irType(expr.type)
                val code = IRCodeChunk(null, null)
                if (expr.type in PassByValueDatatypes) {
                    if(vmDt==IRDataType.FLOAT) {
                        val resultFpRegister = codeGen.registers.nextFreeFloat()
                        code += IRInstruction(Opcode.LOADM, vmDt, fpReg1 = resultFpRegister, labelSymbol = expr.name)
                        ExpressionCodeResult(code, vmDt, -1, resultFpRegister)
                    }
                    else {
                        val resultRegister = codeGen.registers.nextFree()
                        code += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, labelSymbol = expr.name)
                        ExpressionCodeResult(code, vmDt, resultRegister, -1)
                    }
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    val resultRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, labelSymbol = expr.name)
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtAddressOf -> {
                val vmDt = codeGen.irType(expr.type)
                val symbol = expr.identifier.name
                // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
                val code = IRCodeChunk(null, null)
                val resultRegister = codeGen.registers.nextFree()
                code += IRInstruction(Opcode.LOAD, vmDt, reg1=resultRegister, labelSymbol = symbol)
                ExpressionCodeResult(code, vmDt, resultRegister, -1)
            }
            is PtMemoryByte -> {
                val result = mutableListOf<IRCodeChunkBase>()
                if(expr.address is PtNumber) {
                    val address = (expr.address as PtNumber).number.toInt()
                    val resultRegister = codeGen.registers.nextFree()
                    addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, value = address), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
                } else {
                    val tr = translateExpression(expr.address)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1=tr.resultReg, reg2=tr.resultReg), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, tr.resultReg, -1)
                }
            }
            is PtTypeCast -> translate(expr)
            is PtPrefix -> translate(expr)
            is PtArrayIndexer -> translate(expr)
            is PtBinaryExpression -> translate(expr)
            is PtBuiltinFunctionCall -> codeGen.translateBuiltinFunc(expr)
            is PtFunctionCall -> translate(expr)
            is PtContainmentCheck -> translate(expr)
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
            else -> throw AssemblyError("weird expression")
        }
    }

    private fun translate(check: PtContainmentCheck): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        var tr = translateExpression(check.element)
        addToResult(result, tr, tr.resultReg, -1)
        val iterable = codeGen.symbolTable.flat.getValue(check.iterable.name) as StStaticVariable
        when(iterable.dt) {
            DataType.STR -> {
                tr = translateExpression(check.element)
                addToResult(result, tr, SyscallRegisterBase, -1)
                tr = translateExpression(check.iterable)
                addToResult(result, tr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.STRING_CONTAINS.number)
                }
                // SysCall call convention: return value in register r0
                return ExpressionCodeResult(result, IRDataType.BYTE, 0, -1)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                tr = translateExpression(check.element)
                addToResult(result, tr, SyscallRegisterBase, -1)
                tr = translateExpression(check.iterable)
                addToResult(result, tr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=SyscallRegisterBase+2, value = iterable.length!!)
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.BYTEARRAY_CONTAINS.number)
                }
                // SysCall call convention: return value in register r0
                return ExpressionCodeResult(result, IRDataType.BYTE, 0, -1)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                tr = translateExpression(check.element)
                addToResult(result, tr, SyscallRegisterBase, -1)
                tr = translateExpression(check.iterable)
                addToResult(result, tr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=SyscallRegisterBase+2, value = iterable.length!!)
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.WORDARRAY_CONTAINS.number)
                }
                // SysCall call convention: return value in register r0
                return ExpressionCodeResult(result, IRDataType.BYTE, 0, -1)
            }
            DataType.ARRAY_F -> throw AssemblyError("containment check in float-array not supported")
            else -> throw AssemblyError("weird iterable dt ${iterable.dt} for ${check.iterable.name}")
        }
    }

    private fun translate(arrayIx: PtArrayIndexer): ExpressionCodeResult {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = codeGen.irType(arrayIx.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val arrayVarSymbol = arrayIx.variable.name

        if(arrayIx.variable.type==DataType.UWORD) {
            // indexing a pointer var instead of a real array or string
            if(eltSize!=1)
                throw AssemblyError("non-array var indexing requires bytes dt")
            if(arrayIx.index.type!=DataType.UBYTE)
                throw AssemblyError("non-array var indexing requires bytes index")
            val tr = translateExpression(arrayIx.index)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.LOADIX, vmDt, reg1=tr.resultReg, reg2=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            return ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        }

        var resultRegister = -1
        var resultFpRegister = -1
        if(arrayIx.index is PtNumber) {
            val memOffset = ((arrayIx.index as PtNumber).number.toInt() * eltSize).toString()
            if(vmDt==IRDataType.FLOAT) {
                resultFpRegister = codeGen.registers.nextFreeFloat()
                addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1=resultFpRegister, labelSymbol = "$arrayVarSymbol+$memOffset"), null)
            }
            else {
                resultRegister = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOADM, vmDt, reg1=resultRegister, labelSymbol = "$arrayVarSymbol+$memOffset"), null)
            }
        } else {
            val tr = translateExpression(arrayIx.index)
            addToResult(result, tr, tr.resultReg, -1)
            if(eltSize>1)
                result += codeGen.multiplyByConst(IRDataType.BYTE, tr.resultReg, eltSize)
            if(vmDt==IRDataType.FLOAT) {
                resultFpRegister = codeGen.registers.nextFreeFloat()
                addInstr(result, IRInstruction(Opcode.LOADX, IRDataType.FLOAT, fpReg1 = resultFpRegister, reg1=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            }
            else {
                resultRegister = tr.resultReg
                addInstr(result, IRInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            }
        }
        return ExpressionCodeResult(result, vmDt, resultRegister, resultFpRegister)
    }

    private fun translate(expr: PtPrefix): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(expr.value)
        addToResult(result, tr, tr.resultReg, tr.resultFpReg)
        val vmDt = codeGen.irType(expr.type)
        when(expr.operator) {
            "+" -> { }
            "-" -> {
                if(vmDt==IRDataType.FLOAT)
                    addInstr(result, IRInstruction(Opcode.NEG, vmDt, fpReg1 = tr.resultFpReg), null)
                else
                    addInstr(result, IRInstruction(Opcode.NEG, vmDt, reg1 = tr.resultReg), null)
            }
            "~" -> {
                val mask = if(vmDt==IRDataType.BYTE) 0x00ff else 0xffff
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, value = mask), null)
            }
            else -> throw AssemblyError("weird prefix operator")
        }
        return ExpressionCodeResult(result, vmDt, tr.resultReg, tr.resultFpReg)
    }

    private fun translate(cast: PtTypeCast): ExpressionCodeResult {
        if(cast.type==cast.value.type)
            return ExpressionCodeResult.EMPTY
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(cast.value)
        addToResult(result, tr, tr.resultReg, tr.resultFpReg)
        var actualResultReg2 = -1
        var actualResultFpReg2 = -1
        when(cast.type) {
            DataType.UBYTE -> {
                when(cast.value.type) {
                    DataType.BYTE, DataType.UWORD, DataType.WORD -> {
                        actualResultReg2 = tr.resultReg  // just keep the LSB as it is
                    }
                    DataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.FTOUB, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.BYTE -> {
                when(cast.value.type) {
                    DataType.UBYTE, DataType.UWORD, DataType.WORD -> {
                        actualResultReg2 = tr.resultReg  // just keep the LSB as it is
                    }
                    DataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.FTOSB, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.UWORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> uword:   sign extend
                        actualResultReg2 = tr.resultReg
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2), null)
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        actualResultReg2 = tr.resultReg
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2), null)
                    }
                    DataType.WORD -> { }
                    DataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.FTOUW, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.WORD -> {
                when(cast.value.type) {
                    DataType.BYTE -> {
                        // byte -> word:   sign extend
                        actualResultReg2 = tr.resultReg
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2), null)
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        actualResultReg2 = tr.resultReg
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2), null)
                    }
                    DataType.UWORD -> { }
                    DataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.FTOSW, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            DataType.FLOAT -> {
                actualResultFpReg2 = codeGen.registers.nextFreeFloat()
                when(cast.value.type) {
                    DataType.UBYTE -> {
                        addInstr(result, IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    DataType.BYTE -> {
                        addInstr(result, IRInstruction(Opcode.FFROMSB, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    DataType.UWORD -> {
                        addInstr(result, IRInstruction(Opcode.FFROMUW, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    DataType.WORD -> {
                        addInstr(result, IRInstruction(Opcode.FFROMSW, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            else -> throw AssemblyError("weird cast type")
        }
        return ExpressionCodeResult(result, codeGen.irType(cast.type), actualResultReg2, actualResultFpReg2)
    }

    private fun translate(binExpr: PtBinaryExpression): ExpressionCodeResult {
        val vmDt = codeGen.irType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
        val resultRegister = if(vmDt==IRDataType.FLOAT) -1 else codeGen.registers.nextFree()       // TODO weg
        val resultFpRegister = if(vmDt!=IRDataType.FLOAT) -1 else codeGen.registers.nextFreeFloat()    // TODO weg
        // TODO fix the operator methods return type as well
        val code = when(binExpr.operator) {
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
        return if(vmDt==IRDataType.FLOAT)
            ExpressionCodeResult(code, vmDt, -1, resultFpRegister)
        else
            ExpressionCodeResult(code, vmDt, resultRegister, -1)
    }

    fun translate(fcall: PtFunctionCall): ExpressionCodeResult {
        when (val callTarget = codeGen.symbolTable.flat.getValue(fcall.name)) {
            is StSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = codeGen.irType(parameter.type)
                    val symbol = "${fcall.name}.${parameter.name}"
                    if(codeGen.isZero(arg)) {
                        addInstr(result, IRInstruction(Opcode.STOREZM, paramDt, labelSymbol = symbol), null)
                    } else {
                        if (paramDt == IRDataType.FLOAT) {
                            val tr = translateExpression(arg)
                            addToResult(result, tr, -1, tr.resultFpReg)
                            addInstr(result, IRInstruction(Opcode.STOREM, paramDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol), null)
                        } else {
                            val tr = translateExpression(arg)
                            addToResult(result, tr, tr.resultReg, -1)
                            addInstr(result, IRInstruction(Opcode.STOREM, paramDt, reg1 = tr.resultReg, labelSymbol = symbol), null)
                        }
                    }
                }
                return if(fcall.void) {
                    addInstr(result, IRInstruction(Opcode.CALL, labelSymbol = fcall.name), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                } else {
                    var resultReg = -1
                    var resultFpReg = -1
                    if(fcall.type==DataType.FLOAT) {
                        resultFpReg = codeGen.registers.nextFreeFloat()
                        addInstr(result, IRInstruction(Opcode.CALLRVAL, IRDataType.FLOAT, fpReg1=resultFpReg, labelSymbol=fcall.name), null)
                    } else {
                        resultReg = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.CALLRVAL, codeGen.irType(fcall.type), reg1=resultReg, labelSymbol=fcall.name), null)
                    }
                    ExpressionCodeResult(result, codeGen.irType(fcall.type), resultReg, resultFpReg)
                }
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
                        val tr = translateExpression(arg)
                        addToResult(result, tr, tr.resultReg, -1)
                        addInstr(result, IRInstruction(Opcode.STORECPU, paramDt, reg1 = tr.resultReg, labelSymbol = paramRegStr), null)
                    }
                }
                // just a regular call without using Vm register call convention: the value is returned in CPU registers!
                addInstr(result, IRInstruction(Opcode.CALL, value=callTarget.address.toInt()), null)
                val resultReg = codeGen.registers.nextFree()
                if(!fcall.void) {
                    when(callTarget.returns.size) {
                        0 -> throw AssemblyError("expect a return value")
                        1 -> {
                            if(fcall.type==DataType.FLOAT)
                                throw AssemblyError("doesn't support float register result in asm romsub")
                            val returns = callTarget.returns.single()
                            val regStr = if(returns.register.registerOrPair!=null) returns.register.registerOrPair.toString() else returns.register.statusflag.toString()
                            addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultReg, labelSymbol = regStr), null)
                        }
                        else -> {
                            val returnRegister = callTarget.returns.singleOrNull{ it.register.registerOrPair!=null }
                            if(returnRegister!=null) {
                                // we skip the other values returned in the status flags.
                                val regStr = returnRegister.register.registerOrPair.toString()
                                addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultReg, labelSymbol = regStr), null)
                            } else {
                                val firstReturnRegister = callTarget.returns.firstOrNull{ it.register.registerOrPair!=null }
                                if(firstReturnRegister!=null) {
                                    // we just take the first register return value and ignore the rest.
                                    val regStr = firstReturnRegister.register.registerOrPair.toString()
                                    addInstr(result, IRInstruction(Opcode.LOADCPU, codeGen.irType(fcall.type), reg1=resultReg, labelSymbol = regStr), null)
                                } else {
                                    throw AssemblyError("invalid number of return values from call")
                                }
                            }
                        }
                    }
                }
                return ExpressionCodeResult(result, if(fcall.void) IRDataType.BYTE else codeGen.irType(fcall.type), resultReg, -1)
            }
            else -> throw AssemblyError("invalid node type")
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
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultFpReg!=leftTr.resultFpReg)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            val zeroRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
            val ins = if (signed) {
                if (greaterEquals) Opcode.SGES else Opcode.SGTS
            } else {
                if (greaterEquals) Opcode.SGE else Opcode.SGT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister), null)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, SyscallRegisterBase, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.COMPARE_STRINGS.number)
                    if(resultRegister!=0)
                        it += IRInstruction(Opcode.LOADR, vmDt, reg1 = resultRegister, reg2 = 0)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, value = 0)
                    it += if (greaterEquals)
                        IRInstruction(Opcode.SGES, IRDataType.BYTE, reg1 = resultRegister, reg2 = 1)
                    else
                        IRInstruction(Opcode.SGTS, IRDataType.BYTE, reg1 = resultRegister, reg2 = 1)
                }
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, resultRegister, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val ins = if (signed) {
                    if (greaterEquals) Opcode.SGES else Opcode.SGTS
                } else {
                    if (greaterEquals) Opcode.SGE else Opcode.SGT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultFpReg!=leftTr.resultFpReg)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            val zeroRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, value=0), null)
            val ins = if (signed) {
                if (lessEquals) Opcode.SLES else Opcode.SLTS
            } else {
                if (lessEquals) Opcode.SLE else Opcode.SLT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1 = resultRegister, reg2 = zeroRegister), null)
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, SyscallRegisterBase, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.COMPARE_STRINGS.number)
                    if(resultRegister!=0)
                        it += IRInstruction(Opcode.LOADR, vmDt, reg1 = resultRegister, reg2 = 0)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, value = 0)
                    it += if (lessEquals)
                        IRInstruction(Opcode.SLES, IRDataType.BYTE, reg1 = resultRegister, reg2 = 1)
                    else
                        IRInstruction(Opcode.SLTS, IRDataType.BYTE, reg1 = resultRegister, reg2 = 1)
                }
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, resultRegister, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val ins = if (signed) {
                    if (lessEquals) Opcode.SLES else Opcode.SLTS
                } else {
                    if (lessEquals) Opcode.SLE else Opcode.SLT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
            }
        }
        return result
    }

    private fun operatorEquals(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, notEquals: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultFpReg!=leftTr.resultFpReg)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            if (notEquals) {
                addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            } else {
                val label = codeGen.createLabelName()
                val valueReg = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, value=1), null)
                addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=valueReg, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
                addInstr(result, IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=valueReg, labelSymbol = label), null)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, value=0), null)
                result += IRCodeChunk(label, null)
            }
        } else {
            if(binExpr.left.type==DataType.STR && binExpr.right.type==DataType.STR) {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, SyscallRegisterBase, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, SyscallRegisterBase+1, -1)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.SYSCALL, value = IMSyscall.COMPARE_STRINGS.number)
                    if(resultRegister!=0)
                        it += IRInstruction(Opcode.LOADR, vmDt, reg1 = resultRegister, reg2 = 0)
                    if (!notEquals)
                        it += IRInstruction(Opcode.INV, vmDt, reg1 = resultRegister)
                    it += IRInstruction(Opcode.AND, vmDt, reg1 = resultRegister, value = 1)
                }
            } else {
                if(constValue(binExpr.right)==0.0) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, resultRegister, -1)
                    val opcode = if (notEquals) Opcode.SNZ else Opcode.SZ
                    addInstr(result, IRInstruction(opcode, vmDt, reg1 = resultRegister, reg2 = resultRegister), null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, resultRegister, -1)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultReg!=leftTr.resultReg)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    val opcode = if (notEquals) Opcode.SNE else Opcode.SEQ
                    addInstr(result, IRInstruction(opcode, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
                }
            }
        }
        return result
    }

    private fun operatorShiftRight(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, signed: Boolean): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(binExpr.right)) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            val opc = if (signed) Opcode.ASR else Opcode.LSR
            addInstr(result, IRInstruction(opc, vmDt, reg1 = resultRegister), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            val opc = if (signed) Opcode.ASRN else Opcode.LSRN
            addInstr(result, IRInstruction(opc, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
            val tr = translateExpression(operand)
            addToResult(result, tr, tr.resultReg, -1)
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            val ins = if(knownAddress!=null)
                IRInstruction(opc, vmDt, reg1 = tr.resultReg, value=knownAddress)
            else
                IRInstruction(opc, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
            addInstr(result, ins, null)
        }
        return result
    }

    private fun operatorShiftLeft(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(binExpr.right)){
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.LSL, vmDt, reg1=resultRegister), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.LSLN, vmDt, reg1=resultRegister, rightTr.resultReg), null)
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
            val tr = translateExpression(operand)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.LSLNM, vmDt, reg1=tr.resultReg, value=knownAddress)
                else
                    IRInstruction(Opcode.LSLNM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    ,null)
        }
        return result
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.XORR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
        }
        return result
    }

    internal fun operatorXorInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, value = knownAddress)
            else
                IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                ,null)
        return result
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.ANDR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
        }
        return result
    }

    internal  fun operatorAndInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, value=knownAddress)
            else
                IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                ,null)
        return result
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.OR, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.ORR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
        }
        return result
    }

    internal fun operatorOrInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, value = knownAddress)
            else
                IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                , null)
        return result
    }

    private fun operatorModulo(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int): IRCodeChunks {
        require(vmDt!=IRDataType.FLOAT) {"floating-point modulo not supported ${binExpr.position}"}
        val result = mutableListOf<IRCodeChunkBase>()
        if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, resultRegister, -1)
            addInstr(result, IRInstruction(Opcode.MOD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, resultRegister, -1)
            val rightTr = translateExpression(binExpr.right)
            require(rightTr.resultReg!=leftTr.resultReg)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.MODR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.divideByConstFloat(resultFpRegister, factor)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, resultFpRegister)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultFpReg!=leftTr.resultFpReg)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, if(signed)
                        IRInstruction(Opcode.DIVSR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightTr.resultFpReg)
                    else
                        IRInstruction(Opcode.DIVR, vmDt, fpReg1 = resultFpRegister, fpReg2=rightTr.resultFpReg)
                        , null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConst(vmDt, resultRegister, factor, signed)
            } else {
                if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, resultRegister, -1)
                    addInstr(result, if (signed)
                            IRInstruction(Opcode.DIVS, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                        else
                            IRInstruction(Opcode.DIV, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt())
                            , null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, resultRegister, -1)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultReg!=leftTr.resultReg)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, if (signed)
                            IRInstruction(Opcode.DIVSR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg)
                        else
                            IRInstruction(Opcode.DIVR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                val ins = if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = tr.resultFpReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConstInplace(vmDt, knownAddress, symbol, factor, signed)
            } else {
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                val ins = if(signed) {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
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
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, -1, resultFpRegister)
                val factor = constFactorLeft.number.toFloat()
                result += codeGen.multiplyByConstFloat(resultFpRegister, factor)
            } else if(constFactorRight!=null) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, resultFpRegister)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.multiplyByConstFloat(resultFpRegister, factor)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, resultFpRegister)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultFpReg!=leftTr.resultFpReg)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightTr.resultFpReg), null)
            }
        } else {
            if(constFactorLeft!=null && constFactorLeft.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, resultRegister, -1)
                val factor = constFactorLeft.number.toInt()
                result += codeGen.multiplyByConst(vmDt, resultRegister, factor)
            } else if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, resultRegister, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConst(vmDt, resultRegister, factor)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, resultRegister, -1)
                val rightTr = translateExpression(binExpr.right)
                require(rightTr.resultReg!=leftTr.resultReg)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, vmDt, fpReg1 = tr.resultFpReg, value = knownAddress)
                else
                    IRInstruction(Opcode.MULM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConstInplace(vmDt, knownAddress, symbol, factor)
            } else {
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.MULM, vmDt, reg1=tr.resultReg, value = knownAddress)
                else
                    IRInstruction(Opcode.MULM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    private fun operatorMinus(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, fpReg1 = resultFpRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, -1, resultFpRegister)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat()), null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, -1, resultFpRegister)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultFpReg!=leftTr.resultFpReg)
                    addToResult(result, rightTr, -1, rightTr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightTr.resultFpReg), null)
                }
            }
        } else {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, reg1=resultRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left,)
                    addToResult(result, tr, resultRegister, -1)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, reg1 = resultRegister, value = (binExpr.right as PtNumber).number.toInt()), null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, resultRegister, -1)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultReg!=leftTr.resultReg)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, value=knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, labelSymbol = symbol)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, value = knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    private fun operatorPlus(binExpr: PtBinaryExpression, vmDt: IRDataType, resultRegister: Int, resultFpRegister: Int): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister), null)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, resultFpRegister)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=resultFpRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, -1, resultFpRegister)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, fpReg1 = resultFpRegister, fpValue = (binExpr.right as PtNumber).number.toFloat()), null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, -1, resultFpRegister)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultFpReg!=leftTr.resultFpReg)
                    addToResult(result, rightTr, -1, rightTr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, fpReg1 = resultFpRegister, fpReg2 = rightTr.resultFpReg), null)
                }
            }
        } else {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=resultRegister), null)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, resultRegister, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=resultRegister), null)
            }
            else {
                if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, resultRegister, -1)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, reg1 = resultRegister, value=(binExpr.right as PtNumber).number.toInt()), null)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, resultRegister, -1)
                    val rightTr = translateExpression(binExpr.right)
                    require(rightTr.resultReg!=leftTr.resultReg)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, reg1 = resultRegister, reg2 = rightTr.resultReg), null)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, vmDt, fpReg1=tr.resultFpReg, value = knownAddress)
                    else
                        IRInstruction(Opcode.ADDM, vmDt, fpReg1=tr.resultFpReg, labelSymbol = symbol)
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
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, vmDt, reg1=tr.resultReg, value=knownAddress)
                    else
                        IRInstruction(Opcode.ADDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

}


internal fun addInstr(code: MutableList<IRCodeChunkBase>, instr: IRInstruction, label: String?) {
    code += IRCodeChunk(label, null).also {
        it += instr
    }
}

internal fun addToResult(
    result: MutableList<IRCodeChunkBase>,
    codeResult: ExpressionCodeResult,
    requiredResultReg: Int,
    requiredResultFpReg: Int
) {
    if(requiredResultReg!=-1) require(requiredResultFpReg==-1)
    if(requiredResultFpReg!=-1) require(requiredResultReg==-1)

    if(requiredResultReg>=0 && requiredResultReg!=codeResult.resultReg) {
        codeResult.chunks.last().instructions += IRInstruction(Opcode.LOADR, codeResult.dt, reg1=requiredResultReg, reg2=codeResult.resultReg)
    }
    if(requiredResultFpReg>=0 && requiredResultFpReg!=codeResult.resultFpReg) {
        codeResult.chunks.last().instructions += IRInstruction(Opcode.LOADR, IRDataType.FLOAT, fpReg1 = requiredResultFpReg, fpReg2 = codeResult.resultFpReg)
    }
    result += codeResult.chunks
}
