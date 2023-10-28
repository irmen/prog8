package prog8.codegen.intermediate

import prog8.code.StRomSub
import prog8.code.StSub
import prog8.code.ast.*
import prog8.code.core.*
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
                ExpressionCodeResult(emptyList(), irType(expr.type), expr.register, -1)
            }
            is PtNumber -> {
                val vmDt = irType(expr.type)
                val code = IRCodeChunk(null, null)
                if(vmDt==IRDataType.FLOAT) {
                    val resultFpRegister = codeGen.registers.nextFreeFloat()
                    code += IRInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, immediateFp = expr.number.toFloat())
                    ExpressionCodeResult(code, vmDt,-1, resultFpRegister)
                }
                else {
                    val resultRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, immediate = expr.number.toInt())
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtIdentifier -> {
                val code = IRCodeChunk(null, null)
                if (expr.type in PassByValueDatatypes) {
                    val vmDt = irType(expr.type)
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
                    val vmDt = if(expr.type==DataType.UNDEFINED) IRDataType.WORD else irType(expr.type)
                    val resultRegister = codeGen.registers.nextFree()
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, labelSymbol = expr.name)
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtAddressOf -> {
                val vmDt = irType(expr.type)
                val symbol = expr.identifier.name
                // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
                val result = mutableListOf<IRCodeChunkBase>()
                val resultRegister = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, labelSymbol = symbol), null)
                if(expr.isFromArrayElement) {
                    val indexTr = translateExpression(expr.arrayIndexExpr!!)
                    addToResult(result, indexTr, indexTr.resultReg, -1)
                    if(expr.identifier.type in SplitWordArrayTypes) {
                        result += IRCodeChunk(null, null).also {
                            // multiply indexTr resultreg by the eltSize and add this to the resultRegister.
                            it += IRInstruction(Opcode.ADDR, IRDataType.BYTE, reg1=resultRegister, reg2=indexTr.resultReg)
                        }
                    } else {
                        val eltSize = codeGen.program.memsizer.memorySize(expr.identifier.type, 1)
                        result += IRCodeChunk(null, null).also {
                            // multiply indexTr resultreg by the eltSize and add this to the resultRegister.
                            if(eltSize>1)
                                it += IRInstruction(Opcode.MUL, IRDataType.BYTE, reg1=indexTr.resultReg, immediate = eltSize)
                            it += IRInstruction(Opcode.ADDR, IRDataType.BYTE, reg1=resultRegister, reg2=indexTr.resultReg)
                        }
                    }
                }
                ExpressionCodeResult(result, vmDt, resultRegister, -1)
            }
            is PtMemoryByte -> {
                val result = mutableListOf<IRCodeChunkBase>()
                if(expr.address is PtNumber) {
                    val address = (expr.address as PtNumber).number.toInt()
                    val resultRegister = codeGen.registers.nextFree()
                    addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, address = address), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
                } else {
                    val tr = translateExpression(expr.address)
                    addToResult(result, tr, tr.resultReg, -1)
                    val resultReg = codeGen.registers.nextFree()
                    addInstr(result, IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1=resultReg, reg2=tr.resultReg), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
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
        when(check.iterable.type) {
            DataType.STR -> {
                addInstr(result, IRInstruction(Opcode.PREPARECALL, immediate = 2), null)
                val elementTr = translateExpression(check.element)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(check.iterable)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                result += codeGen.makeSyscall(IMSyscall.STRING_CONTAINS, listOf(IRDataType.BYTE to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg), IRDataType.BYTE to elementTr.resultReg)
                return ExpressionCodeResult(result, IRDataType.BYTE, elementTr.resultReg, -1)
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                addInstr(result, IRInstruction(Opcode.PREPARECALL, immediate = 3), null)
                val elementTr = translateExpression(check.element)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(check.iterable)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val lengthReg = codeGen.registers.nextFree()
                val iterableLength = codeGen.symbolTable.getLength(check.iterable.name)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, immediate = iterableLength!!), null)
                result += codeGen.makeSyscall(IMSyscall.BYTEARRAY_CONTAINS, listOf(IRDataType.BYTE to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to elementTr.resultReg)
                return ExpressionCodeResult(result, IRDataType.BYTE, elementTr.resultReg, -1)
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                addInstr(result, IRInstruction(Opcode.PREPARECALL, immediate = 3), null)
                val elementTr = translateExpression(check.element)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(check.iterable)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val lengthReg = codeGen.registers.nextFree()
                val iterableLength = codeGen.symbolTable.getLength(check.iterable.name)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, immediate = iterableLength!!), null)
                result += codeGen.makeSyscall(IMSyscall.WORDARRAY_CONTAINS, listOf(IRDataType.WORD to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to elementTr.resultReg)
                return ExpressionCodeResult(result, IRDataType.BYTE, elementTr.resultReg, -1)
            }
            DataType.ARRAY_F -> throw AssemblyError("containment check in float-array not supported")
            else -> throw AssemblyError("weird iterable dt ${check.iterable.type} for ${check.iterable.name}")
        }
    }

    private fun translate(arrayIx: PtArrayIndexer): ExpressionCodeResult {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type)
        val vmDt = irType(arrayIx.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val arrayVarSymbol = arrayIx.variable.name

        if(arrayIx.usesPointerVariable) {
            if(eltSize!=1)
                throw AssemblyError("non-array var indexing requires bytes dt")
            if(arrayIx.index.type!=DataType.UBYTE)
                throw AssemblyError("non-array var indexing requires bytes index")
            val tr = translateExpression(arrayIx.index)
            addToResult(result, tr, tr.resultReg, -1)
            val resultReg = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOADIX, vmDt, reg1=resultReg, reg2=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            return ExpressionCodeResult(result, vmDt, resultReg, -1)
        }

        var resultRegister = -1

        if(arrayIx.splitWords) {
            require(vmDt==IRDataType.WORD)
            val arrayLength = codeGen.symbolTable.getLength(arrayIx.variable.name)
            resultRegister = codeGen.registers.nextFree()
            val finalResultReg = codeGen.registers.nextFree()
            if(arrayIx.index is PtNumber) {
                val memOffset = (arrayIx.index as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    val tmpRegMsb = codeGen.registers.nextFree()
                    it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=tmpRegMsb, immediate = arrayLength, labelSymbol= "${arrayVarSymbol}_msb+$memOffset")
                    it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, immediate = arrayLength, labelSymbol= "${arrayVarSymbol}_lsb+$memOffset")
                    it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=finalResultReg, reg2=tmpRegMsb, reg3=resultRegister)
                }
            } else {
                val tr = translateExpression(arrayIx.index)
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    val tmpRegMsb = codeGen.registers.nextFree()
                    it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegMsb, reg2 = tr.resultReg, immediate = arrayLength, labelSymbol= "${arrayVarSymbol}_msb")
                    it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=resultRegister, reg2 = tr.resultReg, immediate = arrayLength, labelSymbol= "${arrayVarSymbol}_lsb")
                    it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=finalResultReg, reg2=tmpRegMsb, reg3=resultRegister)
                }
            }
            return ExpressionCodeResult(result, vmDt, finalResultReg, -1)
        }

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
                resultRegister = codeGen.registers.nextFree()
                addInstr(result, IRInstruction(Opcode.LOADX, vmDt, reg1=resultRegister, reg2=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            }
        }
        return ExpressionCodeResult(result, vmDt, resultRegister, resultFpRegister)
    }

    private fun translate(expr: PtPrefix): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(expr.value)
        addToResult(result, tr, tr.resultReg, tr.resultFpReg)
        val vmDt = irType(expr.type)
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
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, immediate = mask), null)
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
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2 = tr.resultReg), null)
                    }
                    DataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2 = tr.resultReg), null)
                    }
                    DataType.WORD -> {
                        actualResultReg2 = tr.resultReg
                    }
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
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2=tr.resultReg), null)
                    }
                    DataType.UBYTE -> {
                        // byte -> word:   sign extend
                        actualResultReg2 = codeGen.registers.nextFree()
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2=tr.resultReg), null)
                    }
                    DataType.UWORD -> {
                        actualResultReg2 = tr.resultReg
                    }
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

        return ExpressionCodeResult(result, irType(cast.type), actualResultReg2, actualResultFpReg2)
    }

    private fun translate(binExpr: PtBinaryExpression): ExpressionCodeResult {
        val vmDt = irType(binExpr.left.type)
        val signed = binExpr.left.type in SignedDatatypes
        return when(binExpr.operator) {
            "+" -> operatorPlus(binExpr, vmDt)
            "-" -> operatorMinus(binExpr, vmDt)
            "*" -> operatorMultiply(binExpr, vmDt)
            "/" -> operatorDivide(binExpr, vmDt, signed)
            "%" -> operatorModulo(binExpr, vmDt)
            "|" -> operatorOr(binExpr, vmDt)
            "&" -> operatorAnd(binExpr, vmDt)
            "^" -> operatorXor(binExpr, vmDt)
            "<<" -> operatorShiftLeft(binExpr, vmDt)
            ">>" -> operatorShiftRight(binExpr, vmDt, signed)
            "==" -> operatorEquals(binExpr, vmDt, false)
            "!=" -> operatorEquals(binExpr, vmDt, true)
            "<" -> operatorLessThan(binExpr, vmDt, signed, false)
            ">" -> operatorGreaterThan(binExpr, vmDt, signed, false)
            "<=" -> operatorLessThan(binExpr, vmDt, signed, true)
            ">=" -> operatorGreaterThan(binExpr, vmDt, signed, true)
            else -> throw AssemblyError("weird operator ${binExpr.operator}")
        }
    }

    fun translate(fcall: PtFunctionCall): ExpressionCodeResult {
        when (val callTarget = codeGen.symbolTable.flat.getValue(fcall.name)) {
            is StSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                addInstr(result, IRInstruction(Opcode.PREPARECALL, immediate = callTarget.parameters.size), null)
                // assign the arguments
                val argRegisters = mutableListOf<FunctionCallArgs.ArgumentSpec>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = irType(parameter.type)
                    val tr = translateExpression(arg)
                    if(paramDt==IRDataType.FLOAT)
                        argRegisters.add(FunctionCallArgs.ArgumentSpec(parameter.name, null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, tr.resultFpReg, null)))
                    else
                        argRegisters.add(FunctionCallArgs.ArgumentSpec(parameter.name, null, FunctionCallArgs.RegSpec(paramDt, tr.resultReg, null)))
                    result += tr.chunks
                }
                // return value
                val returnRegSpec = if(fcall.void) null else {
                    val returnIrType = irType(callTarget.returnType!!)
                    if(returnIrType==IRDataType.FLOAT)
                        FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFreeFloat(), null)
                    else
                        FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFree(), null)
                }
                // create the call
                addInstr(result, IRInstruction(Opcode.CALL, labelSymbol = fcall.name, fcallArgs = FunctionCallArgs(argRegisters, returnRegSpec)), null)
                return if(fcall.void)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                else if(fcall.type==DataType.FLOAT)
                    ExpressionCodeResult(result, returnRegSpec!!.dt, -1, returnRegSpec.registerNum)
                else
                    ExpressionCodeResult(result, returnRegSpec!!.dt, returnRegSpec.registerNum, -1)
            }
            is StRomSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                addInstr(result, IRInstruction(Opcode.PREPARECALL, immediate = callTarget.parameters.size), null)
                // assign the arguments
                val argRegisters = mutableListOf<FunctionCallArgs.ArgumentSpec>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = irType(parameter.type)
                    val tr = translateExpression(arg)
                    if(paramDt==IRDataType.FLOAT)
                        argRegisters.add(FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, tr.resultFpReg, parameter.register)))
                    else
                        argRegisters.add(FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(paramDt, tr.resultReg, parameter.register)))
                    result += tr.chunks
                }
                // return value
                val returnRegSpec = if(fcall.void) null else {
                    if(callTarget.returns.isEmpty())
                        null
                    else if(callTarget.returns.size==1) {
                        val returns = callTarget.returns[0]
                        val returnIrType = irType(returns.type)
                        if(returnIrType==IRDataType.FLOAT)
                            FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFreeFloat(), returns.register)
                        else
                            FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFree(), returns.register)
                    } else {
                        // multiple return values: take the first *register* (not status flag) return value and ignore the rest.
                        val returns = callTarget.returns.first { it.register.registerOrPair!=null }
                        val returnIrType = irType(returns.type)
                        if(returnIrType==IRDataType.FLOAT)
                            FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFreeFloat(), returns.register)
                        else
                            FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.nextFree(), returns.register)
                    }
                }
                // create the call
                val call =
                    if(callTarget.address==null)
                        IRInstruction(Opcode.CALL, labelSymbol = fcall.name, fcallArgs = FunctionCallArgs(argRegisters, returnRegSpec))
                    else
                        IRInstruction(Opcode.CALL, address = callTarget.address!!.toInt(), fcallArgs = FunctionCallArgs(argRegisters, returnRegSpec))
                addInstr(result, call, null)
                return if(fcall.void)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                else if(fcall.type==DataType.FLOAT)
                    ExpressionCodeResult(result, returnRegSpec!!.dt, -1, returnRegSpec.registerNum)
                else
                    ExpressionCodeResult(result, returnRegSpec!!.dt, returnRegSpec.registerNum, -1)
            }
            else -> throw AssemblyError("invalid node type")
        }
    }

    private fun operatorGreaterThan(
        binExpr: PtBinaryExpression,
        vmDt: IRDataType,
        signed: Boolean,
        greaterEquals: Boolean
    ): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val cmpResultReg = codeGen.registers.nextFree()
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            val resultRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            val zeroRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, immediate = 0), null)
            val ins = if (signed) {
                if (greaterEquals) Opcode.SGES else Opcode.SGTS
            } else {
                if (greaterEquals) Opcode.SGE else Opcode.SGT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1=cmpResultReg, reg2 = resultRegister, reg3 = zeroRegister), null)
            return ExpressionCodeResult(result, IRDataType.BYTE, cmpResultReg, -1)
        } else {
            if(binExpr.left.type==DataType.STR || binExpr.right.type==DataType.STR) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val ins = if (signed) {
                    if (greaterEquals) Opcode.SGES else Opcode.SGTS
                } else {
                    if (greaterEquals) Opcode.SGE else Opcode.SGT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1=cmpResultReg, reg2 = leftTr.resultReg, reg3 = rightTr.resultReg), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, cmpResultReg, -1)
            }
        }
    }

    private fun operatorLessThan(
        binExpr: PtBinaryExpression,
        vmDt: IRDataType,
        signed: Boolean,
        lessEquals: Boolean
    ): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val cmpResultRegister = codeGen.registers.nextFree()
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            val resultRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            val zeroRegister = codeGen.registers.nextFree()
            addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroRegister, immediate = 0), null)
            val ins = if (signed) {
                if (lessEquals) Opcode.SLES else Opcode.SLTS
            } else {
                if (lessEquals) Opcode.SLE else Opcode.SLT
            }
            addInstr(result, IRInstruction(ins, IRDataType.BYTE, reg1=cmpResultRegister, reg2 = resultRegister, reg3 = zeroRegister), null)
            return ExpressionCodeResult(result, IRDataType.BYTE, cmpResultRegister, -1)
        } else {
            if(binExpr.left.type==DataType.STR || binExpr.right.type==DataType.STR) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val ins = if (signed) {
                    if (lessEquals) Opcode.SLES else Opcode.SLTS
                } else {
                    if (lessEquals) Opcode.SLE else Opcode.SLT
                }
                addInstr(result, IRInstruction(ins, vmDt, reg1=cmpResultRegister, reg2 = leftTr.resultReg, reg3 = rightTr.resultReg), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, cmpResultRegister, -1)
            }
        }
    }

    private fun operatorEquals(binExpr: PtBinaryExpression, vmDt: IRDataType, notEquals: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            val resultRegister = codeGen.registers.nextFree()
            val valueReg = codeGen.registers.nextFree()
            val label = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, immediate = 1)
                it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=valueReg, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg)
                it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=valueReg, immediate = 0)
                it += if (notEquals)
                    IRInstruction(Opcode.BSTNE, labelSymbol = label)
                else
                    IRInstruction(Opcode.BSTEQ, labelSymbol = label)
                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, immediate = 0)
            }
            result += IRCodeChunk(label, null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            if(binExpr.left.type==DataType.STR || binExpr.right.type==DataType.STR) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                return if(constValue(binExpr.right)==0.0) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, tr.resultReg, -1)
                    val opcode = if (notEquals) Opcode.SNZ else Opcode.SZ
                    val resultReg = codeGen.registers.nextFree()
                    addInstr(result, IRInstruction(opcode, vmDt, reg1 = resultReg, reg2 = tr.resultReg), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    val opcode = if (notEquals) Opcode.SNE else Opcode.SEQ
                    val resultReg = codeGen.registers.nextFree()
                    addInstr(result, IRInstruction(opcode, vmDt, reg1 = resultReg, reg2 = leftTr.resultReg, reg3 = rightTr.resultReg), null)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
                }
            }
        }
    }

    private fun operatorShiftRight(binExpr: PtBinaryExpression, vmDt: IRDataType, signed: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(codeGen.isOne(binExpr.right)) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            val opc = if (signed) Opcode.ASR else Opcode.LSR
            addInstr(result, IRInstruction(opc, vmDt, reg1 = tr.resultReg), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            val opc = if (signed) Opcode.ASRN else Opcode.LSRN
            addInstr(result, IRInstruction(opc, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorShiftLeft(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(codeGen.isOne(binExpr.right)){
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.LSL, vmDt, reg1=tr.resultReg), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.LSLN, vmDt, reg1=leftTr.resultReg, rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.XORR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.ANDR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        return if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.OR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.ORR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorModulo(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        require(vmDt!=IRDataType.FLOAT) {"floating-point modulo not supported ${binExpr.position}"}
        val result = mutableListOf<IRCodeChunkBase>()
        return if(binExpr.right is PtNumber) {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.MOD, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
            ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
        } else {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.MODR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorDivide(binExpr: PtBinaryExpression, vmDt: IRDataType, signed: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.divideByConstFloat(tr.resultFpReg, factor)
                return ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, leftTr.resultFpReg)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, if(signed)
                        IRInstruction(Opcode.DIVSR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2=rightTr.resultFpReg)
                    else
                        IRInstruction(Opcode.DIVR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2=rightTr.resultFpReg)
                        , null)
                return ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
            }
        } else {
            return if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConst(vmDt, tr.resultReg, factor, signed)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else {
                if(binExpr.right is PtNumber) {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    addInstr(result, if (signed)
                            IRInstruction(Opcode.DIVS, vmDt, reg1 = leftTr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt())
                        else
                            IRInstruction(Opcode.DIV, vmDt, reg1 = leftTr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt())
                            , null)
                    ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, if (signed)
                            IRInstruction(Opcode.DIVSR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg)
                        else
                            IRInstruction(Opcode.DIVR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg)
                            , null)
                    ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
                }
            }
        }
    }

    private fun operatorMultiply(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorLeft = binExpr.left as? PtNumber
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            return if(constFactorLeft!=null) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorLeft.number.toFloat()
                result += codeGen.multiplyByConstFloat(tr.resultFpReg, factor)
                ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else if(constFactorRight!=null) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorRight.number.toFloat()
                result += codeGen.multiplyByConstFloat(tr.resultFpReg, factor)
                ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, leftTr.resultFpReg)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
                ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
            }
        } else {
            return if(constFactorLeft!=null && constFactorLeft.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorLeft.number.toInt()
                result += codeGen.multiplyByConst(vmDt, tr.resultReg, factor)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else if(constFactorRight!=null && constFactorRight.type!=DataType.FLOAT) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConst(vmDt, tr.resultReg, factor)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.MULR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
                ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
            }
        }
    }

    private fun operatorMinus(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, fpReg1 = tr.resultFpReg), null)
                return ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            }
            else {
                return if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, -1, tr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, fpReg1 = tr.resultFpReg, immediateFp = (binExpr.right as PtNumber).number.toFloat()), null)
                    ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, -1, leftTr.resultFpReg)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, -1, rightTr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
                    ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
                }
            }
        } else {
            if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.DEC, vmDt, reg1=tr.resultReg), null)
                return ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
            else {
                return if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.SUBR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
                    ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
                }
            }
        }
    }

    private fun operatorPlus(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=tr.resultFpReg), null)
                return ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, fpReg1=tr.resultFpReg), null)
                return ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            }
            else {
                return if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, -1, tr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, fpReg1 = tr.resultFpReg, immediateFp = (binExpr.right as PtNumber).number.toFloat()), null)
                    ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, -1, leftTr.resultFpReg)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, -1, rightTr.resultFpReg)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
                    ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
                }
            }
        } else {
            if((binExpr.left as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=tr.resultReg), null)
                return ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
            else if((binExpr.right as? PtNumber)?.number==1.0) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.INC, vmDt, reg1=tr.resultReg), null)
                return ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
            else {
                return if(binExpr.right is PtNumber) {
                    val tr = translateExpression(binExpr.left)
                    addToResult(result, tr, tr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ADDR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
                    ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
                }
            }
        }
    }


    internal fun operatorAndInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, address = knownAddress)
        else
            IRInstruction(Opcode.ANDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
            ,null)
        return result
    }

    internal fun operatorOrInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, address = knownAddress)
        else
            IRInstruction(Opcode.ORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
            , null)
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
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, fpReg1 = tr.resultFpReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, fpReg1 = tr.resultFpReg, address = knownAddress)
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
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.DIVSM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
                }
                else {
                    if(knownAddress!=null)
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.DIVM, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
                }
                addInstr(result, ins, null)
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
                    IRInstruction(Opcode.MULM, vmDt, fpReg1 = tr.resultFpReg, address = knownAddress)
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
                    IRInstruction(Opcode.MULM, vmDt, reg1=tr.resultReg, address = knownAddress)
                else
                    IRInstruction(Opcode.MULM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    internal fun operatorMinusInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, address = knownAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, address = knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, fpReg1=tr.resultFpReg, labelSymbol = symbol)
                    , null)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.DECM, vmDt, address = knownAddress)
                else
                    IRInstruction(Opcode.DECM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, address = knownAddress)
                else
                    IRInstruction(Opcode.SUBM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    internal fun operatorPlusInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.INCM, vmDt, address = knownAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val tr = translateExpression(operand)
                addToResult(result, tr, -1, tr.resultFpReg)
                addInstr(result, if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, vmDt, fpReg1=tr.resultFpReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.ADDM, vmDt, fpReg1=tr.resultFpReg, labelSymbol = symbol)
                        , null)
            }
        } else {
            if((operand as? PtNumber)?.number==1.0) {
                addInstr(result, if(knownAddress!=null)
                    IRInstruction(Opcode.INCM, vmDt, address = knownAddress)
                else
                    IRInstruction(Opcode.INCM, vmDt, labelSymbol = symbol)
                    , null)
            }
            else {
                val tr = translateExpression(operand)
                addToResult(result, tr, tr.resultReg, -1)
                addInstr(result, if(knownAddress!=null)
                        IRInstruction(Opcode.ADDM, vmDt, reg1=tr.resultReg, address = knownAddress)
                    else
                        IRInstruction(Opcode.ADDM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                    , null)
            }
        }
        return result
    }

    internal fun operatorShiftRightInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)) {
            val opc = if (signed) Opcode.ASRM else Opcode.LSRM
            val ins = if(knownAddress!=null)
                IRInstruction(opc, vmDt, address = knownAddress)
            else
                IRInstruction(opc, vmDt, labelSymbol = symbol)
            addInstr(result, ins, null)
        } else {
            val tr = translateExpression(operand)
            addToResult(result, tr, tr.resultReg, -1)
            val opc = if (signed) Opcode.ASRNM else Opcode.LSRNM
            val ins = if(knownAddress!=null)
                IRInstruction(opc, vmDt, reg1 = tr.resultReg, address = knownAddress)
            else
                IRInstruction(opc, vmDt, reg1 = tr.resultReg, labelSymbol = symbol)
            addInstr(result, ins, null)
        }
        return result
    }

    internal fun operatorShiftLeftInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        if(codeGen.isOne(operand)){
            addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.LSLM, vmDt, address = knownAddress)
            else
                IRInstruction(Opcode.LSLM, vmDt, labelSymbol = symbol)
                , null)
        } else {
            val tr = translateExpression(operand)
            addToResult(result, tr, tr.resultReg, -1)
            addInstr(result, if(knownAddress!=null)
                IRInstruction(Opcode.LSLNM, vmDt, reg1=tr.resultReg, address = knownAddress)
            else
                IRInstruction(Opcode.LSLNM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
                ,null)
        }
        return result
    }

    internal fun operatorXorInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(operand)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, if(knownAddress!=null)
            IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, address = knownAddress)
        else
            IRInstruction(Opcode.XORM, vmDt, reg1=tr.resultReg, labelSymbol = symbol)
            ,null)
        return result
    }

    fun operatorModuloInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultReg = codeGen.registers.nextFree()
        if(operand is PtNumber) {
            val number = operand.number.toInt()
            if (knownAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, address = knownAddress)
                    it += IRInstruction(Opcode.MOD, vmDt, reg1 = resultReg, immediate = number)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, address = knownAddress)
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.MOD, vmDt, reg1 = resultReg, immediate = number)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                }
            }
        } else {
            val tr = translateExpression(operand)
            result += tr.chunks
            if (knownAddress != null) {
                // @(address) = @(address) %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, address = knownAddress)
                    it += IRInstruction(Opcode.MODR, vmDt, reg1 = resultReg, reg2 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, address = knownAddress)
                }
            } else {
                // symbol = symbol %= operand
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.MODR, vmDt, reg1 = resultReg, reg2 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = resultReg, labelSymbol = symbol)
                }
            }
        }
        return result
    }

    fun operatorEqualsInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, Opcode.SEQ)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, Opcode.SEQ)
        }
    }

    fun operatorNotEqualsInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, operand: PtExpression): IRCodeChunks {
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, Opcode.SNE)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, Opcode.SNE)
        }

    }

    fun operatorGreaterInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val opcode = if(signed) Opcode.SGTS else Opcode.SGT
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, opcode)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, opcode)
        }
    }

    fun operatorLessInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val opcode = if(signed) Opcode.SLTS else Opcode.SLT
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, opcode)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, opcode)
        }
    }

    fun operatorGreaterEqualInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val opcode = if(signed) Opcode.SGES else Opcode.SGE
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, opcode)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, opcode)
        }
    }

    fun operatorLessEqualInplace(knownAddress: Int?, symbol: String?, vmDt: IRDataType, signed: Boolean, operand: PtExpression): IRCodeChunks {
        val opcode = if(signed) Opcode.SLES else Opcode.SLE
        return if(vmDt==IRDataType.FLOAT) {
            createInplaceFloatComparison(knownAddress, symbol, operand, opcode)
        } else {
            createInplaceComparison(knownAddress, symbol, vmDt, operand, opcode)
        }
    }

    private fun createInplaceComparison(
        knownAddress: Int?,
        symbol: String?,
        vmDt: IRDataType,
        operand: PtExpression,
        compareAndSetOpcode: Opcode
    ): MutableList<IRCodeChunkBase> {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueReg = codeGen.registers.nextFree()
        val cmpResultReg = codeGen.registers.nextFree()
        if(operand is PtNumber) {
            val numberReg = codeGen.registers.nextFree()
            if (knownAddress != null) {
                // in-place modify a memory location
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = valueReg, address = knownAddress)
                    it += IRInstruction(Opcode.LOAD, vmDt, reg1=numberReg, immediate = operand.number.toInt())
                    it += IRInstruction(compareAndSetOpcode, vmDt, reg1 = cmpResultReg, reg2 = valueReg, reg3 = numberReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = cmpResultReg, address = knownAddress)
                }
            } else {
                // in-place modify a symbol (variable)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = valueReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.LOAD, vmDt, reg1=numberReg, immediate = operand.number.toInt())
                    it += IRInstruction(compareAndSetOpcode, vmDt, reg1=cmpResultReg, reg2 = valueReg, reg3 = numberReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = cmpResultReg, labelSymbol = symbol)
                }
            }
        } else {
            val tr = translateExpression(operand)
            addToResult(result, tr, tr.resultReg, -1)
            if (knownAddress != null) {
                // in-place modify a memory location
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = valueReg, address = knownAddress)
                    it += IRInstruction(compareAndSetOpcode, vmDt, reg1=cmpResultReg, reg2 = valueReg, reg3 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = cmpResultReg, address = knownAddress)
                }
            } else {
                // in-place modify a symbol (variable)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, vmDt, reg1 = valueReg, labelSymbol = symbol)
                    it += IRInstruction(compareAndSetOpcode, vmDt, reg1=cmpResultReg, reg2 = valueReg, reg3 = tr.resultReg)
                    it += IRInstruction(Opcode.STOREM, vmDt, reg1 = cmpResultReg, labelSymbol = symbol)
                }
            }
        }
        return result
    }

    private fun createInplaceFloatComparison(
        knownAddress: Int?,
        symbol: String?,
        operand: PtExpression,
        compareAndSetOpcode: Opcode
    ): MutableList<IRCodeChunkBase> {
        val result = mutableListOf<IRCodeChunkBase>()
        val valueReg = codeGen.registers.nextFreeFloat()
        val cmpReg = codeGen.registers.nextFree()
        val zeroReg = codeGen.registers.nextFree()
        if(operand is PtNumber) {
            val numberReg = codeGen.registers.nextFreeFloat()
            val cmpResultReg = codeGen.registers.nextFree()
            if (knownAddress != null) {
                // in-place modify a memory location
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1 = valueReg, address = knownAddress)
                    it += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = numberReg, immediateFp = operand.number.toFloat())
                    it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=cmpReg, fpReg1 = valueReg, fpReg2 = numberReg)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroReg, immediate = 0)
                    it += IRInstruction(compareAndSetOpcode, IRDataType.BYTE, reg1=cmpResultReg, reg2=cmpReg, reg3=zeroReg)
                    it += IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=cmpResultReg, fpReg1 = valueReg)
                    it += IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = valueReg, address = knownAddress)
                }
            } else {
                // in-place modify a symbol (variable)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1 = valueReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = numberReg, immediateFp = operand.number.toFloat())
                    it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=cmpReg, fpReg1 = valueReg, fpReg2 = numberReg)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroReg, immediate = 0)
                    it += IRInstruction(compareAndSetOpcode, IRDataType.BYTE, reg1=cmpResultReg, reg2=cmpReg, reg3=zeroReg)
                    it += IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=cmpResultReg, fpReg1 = valueReg)
                    it += IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = valueReg, labelSymbol = symbol)
                }
            }
        } else {
            val tr = translateExpression(operand)
            val cmpResultReg = codeGen.registers.nextFree()
            addToResult(result, tr, -1, tr.resultFpReg)
            if (knownAddress != null) {
                // in-place modify a memory location
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1 = valueReg, address = knownAddress)
                    it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=cmpReg, fpReg1 = valueReg, fpReg2 = tr.resultFpReg)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroReg, immediate = 0)
                    it += IRInstruction(compareAndSetOpcode, IRDataType.BYTE, reg1=cmpResultReg, reg2=cmpReg, reg3=zeroReg)
                    it += IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=cmpResultReg, fpReg1 = valueReg)
                    it += IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = valueReg, address = knownAddress)
                }
            } else {
                // in-place modify a symbol (variable)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1 = valueReg, labelSymbol = symbol)
                    it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=cmpReg, fpReg1 = valueReg, fpReg2 = tr.resultFpReg)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=zeroReg, immediate = 0)
                    it += IRInstruction(compareAndSetOpcode, IRDataType.BYTE, reg1=cmpResultReg, reg2=cmpReg, reg3=zeroReg)
                    it += IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=cmpResultReg, fpReg1 = valueReg)
                    it += IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = valueReg, labelSymbol = symbol)
                }
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
