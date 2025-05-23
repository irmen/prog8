package prog8.codegen.intermediate

import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.intermediate.*


internal class ExpressionCodeResult(val chunks: IRCodeChunks, val dt: IRDataType, val resultReg: Int, val resultFpReg: Int,
                                    val multipleResultRegs: List<Int> = emptyList(), val multipleResultFpRegs: List<Int> = emptyList()
) {
    constructor(chunk: IRCodeChunk, dt: IRDataType, resultReg: Int, resultFpReg: Int, multipleResultRegs: List<Int> = emptyList(), multipleResultFpRegs: List<Int> = emptyList())
            : this(listOf(chunk), dt, resultReg, resultFpReg, multipleResultRegs, multipleResultFpRegs)

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
            is PtIrRegister -> {
                if(expr.type.isFloat)
                    ExpressionCodeResult(emptyList(), IRDataType.FLOAT, -1, expr.register)
                else
                    ExpressionCodeResult(emptyList(), irType(expr.type), expr.register, -1)
            }
            is PtBool -> {
                val code = IRCodeChunk(null, null)
                val resultRegister = codeGen.registers.next(IRDataType.BYTE)
                code += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultRegister, immediate = expr.asInt())
                ExpressionCodeResult(code, IRDataType.BYTE, resultRegister, -1)
            }
            is PtNumber -> {
                val vmDt = irType(expr.type)
                val code = IRCodeChunk(null, null)
                if(vmDt==IRDataType.FLOAT) {
                    val resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                    code += IRInstruction(Opcode.LOAD, vmDt, fpReg1 = resultFpRegister, immediateFp = expr.number)
                    ExpressionCodeResult(code, vmDt,-1, resultFpRegister)
                }
                else {
                    val resultRegister = codeGen.registers.next(vmDt)
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, immediate = expr.number.toInt())
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtIdentifier -> {
                val code = IRCodeChunk(null, null)
                if (expr.type.isPassByValue) {
                    val vmDt = irType(expr.type)
                    if(vmDt==IRDataType.FLOAT) {
                        val resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                        code += IRInstruction(Opcode.LOADM, vmDt, fpReg1 = resultFpRegister, labelSymbol = expr.name)
                        ExpressionCodeResult(code, vmDt, -1, resultFpRegister)
                    }
                    else {
                        val resultRegister = codeGen.registers.next(vmDt)
                        code += IRInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, labelSymbol = expr.name)
                        ExpressionCodeResult(code, vmDt, resultRegister, -1)
                    }
                } else {
                    // for strings and arrays etc., load the *address* of the value instead
                    // for arrays this could mean a split word array, in which case we take the address of the _lsb array which comes first
                    val vmDt = if(expr.type.isUndefined) IRDataType.WORD else irType(expr.type)
                    val resultRegister = codeGen.registers.next(vmDt)
                    val labelsymbol = if(expr.type.isSplitWordArray) expr.name+"_lsb" else expr.name
                    code += IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, labelSymbol = labelsymbol)
                    ExpressionCodeResult(code, vmDt, resultRegister, -1)
                }
            }
            is PtAddressOf -> translate(expr)
            is PtMemoryByte -> translate(expr)
            is PtTypeCast -> translate(expr)
            is PtPrefix -> translate(expr)
            is PtArrayIndexer -> translate(expr)
            is PtBinaryExpression -> translate(expr)
            is PtIfExpression -> translate(expr)
            is PtBuiltinFunctionCall -> codeGen.translateBuiltinFunc(expr)
            is PtFunctionCall -> translate(expr)
            is PtContainmentCheck -> translate(expr)
            is PtRange,
            is PtArray,
            is PtString -> throw AssemblyError("range/arrayliteral/string should no longer occur as expression")
        }
    }

    private fun translate(ifExpr: PtIfExpression): ExpressionCodeResult {

        if((ifExpr.condition as? PtPrefix)?.operator=="not")
            throw AssemblyError("not prefix in ifexpression should have been replaced by swapped values")

        val result = mutableListOf<IRCodeChunkBase>()
        val trueTr = translateExpression(ifExpr.truevalue)
        val falseTr = translateExpression(ifExpr.falsevalue)
        val falseLabel = codeGen.createLabelName()
        val endLabel = codeGen.createLabelName()
        val irDt = irType(ifExpr.type)

        if(ifExpr.condition is PtBinaryExpression) {
            val useBIT = checkIfConditionCanUseBIT(ifExpr.condition as PtBinaryExpression)
            if(useBIT!=null) {
                // use a BIT instruction to test for bit 7 or 6 set/clear
                val (testBitSet, variable, bitmask) = useBIT
                val bitBranchOpcode = when(testBitSet) {
                    true -> when(bitmask) {
                        64 -> Opcode.BSTVC
                        128 -> Opcode.BSTPOS
                        else -> throw AssemblyError("need bit 6 or 7")
                    }
                    false -> when(bitmask) {
                        64 -> Opcode.BSTVS
                        128 -> Opcode.BSTNEG
                        else -> throw AssemblyError("need bit 6 or 7")
                    }
                }
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.BIT, IRDataType.BYTE, labelSymbol = variable.name)
                    it += IRInstruction(bitBranchOpcode, labelSymbol = falseLabel)
                }
                addToResult(result, trueTr, trueTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
                result += IRCodeChunk(falseLabel, null)
                addToResult(result, falseTr, trueTr.resultReg, -1)
                result += IRCodeChunk(endLabel, null)
                return ExpressionCodeResult(result, irDt, trueTr.resultReg, -1)
            }
        }


        // TODO don't store condition as expression result but just use the flags, like a normal PtIfElse translation does
        val condTr = translateExpression(ifExpr.condition)
        addToResult(result, condTr, condTr.resultReg, -1)
        addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=condTr.resultReg, immediate = 0), null)
        addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = falseLabel), null)

        if (irDt != IRDataType.FLOAT) {
            addToResult(result, trueTr, trueTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
            result += IRCodeChunk(falseLabel, null)
            addToResult(result, falseTr, trueTr.resultReg, -1)
            result += IRCodeChunk(endLabel, null)
            return ExpressionCodeResult(result, irDt, trueTr.resultReg, -1)
        } else {
            addToResult(result, trueTr, -1, trueTr.resultFpReg)
            addInstr(result, IRInstruction(Opcode.JUMP, labelSymbol = endLabel), null)
            result += IRCodeChunk(falseLabel, null)
            addToResult(result, falseTr, -1, trueTr.resultFpReg)
            result += IRCodeChunk(endLabel, null)
            return ExpressionCodeResult(result, irDt, -1, trueTr.resultFpReg)
        }
    }

    private fun translate(expr: PtAddressOf): ExpressionCodeResult {
        val vmDt = irType(expr.type)
        val symbol = expr.identifier.name
        // note: LOAD <symbol>  gets you the address of the symbol, whereas LOADM <symbol> would get you the value stored at that location
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(vmDt)

        fun loadAddressOfArrayLabel(reg: Int) {
            if (expr.isMsbForSplitArray) {
                addInstr(result, IRInstruction(Opcode.LOAD, vmDt, reg1 = reg, labelSymbol = symbol + "_msb"), null)
            } else if (expr.identifier.type.isSplitWordArray) {
                // the _lsb split array comes first in memory
                addInstr(result, IRInstruction(Opcode.LOAD, vmDt, reg1 = reg, labelSymbol = symbol + "_lsb"), null)
            } else
                addInstr(result, IRInstruction(Opcode.LOAD, vmDt, reg1 = reg, labelSymbol = symbol), null)
        }

        if(expr.isFromArrayElement) {
            val indexTr = translateExpression(expr.arrayIndexExpr!!)
            addToResult(result, indexTr, indexTr.resultReg, -1)
            val indexWordReg = if(indexTr.dt==IRDataType.BYTE) {
                val ixWord = codeGen.registers.next(IRDataType.WORD)
                addInstr(result, IRInstruction(Opcode.EXT, IRDataType.BYTE, reg1=ixWord, reg2=indexTr.resultReg), null)
                ixWord
            } else indexTr.resultReg
            if(expr.identifier.type.isUnsignedWord) {
                require(!expr.isMsbForSplitArray)
                result += IRCodeChunk(null, null).also {
                    val ptr = codeGen.symbolTable.lookup(expr.identifier.name)
                    it += if(ptr is StConstant)
                        IRInstruction(Opcode.LOAD, vmDt, reg1 = resultRegister, immediate = ptr.value.toInt())
                    else
                        IRInstruction(Opcode.LOADM, vmDt, reg1 = resultRegister, labelSymbol = symbol)
                    it += IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=resultRegister, reg2=indexWordReg)
                }
            } else {
                val eltSize = codeGen.program.memsizer.memorySize(expr.identifier.type, 1)
                result += IRCodeChunk(null, null).also {
                    loadAddressOfArrayLabel(resultRegister)
                    if(eltSize>1 && !expr.identifier.type.isSplitWordArray) {
                        it += IRInstruction(Opcode.MUL, IRDataType.WORD, reg1=indexWordReg, immediate = eltSize)
                    }
                    it += IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=resultRegister, reg2=indexWordReg)
                }
            }
        } else {
            loadAddressOfArrayLabel(resultRegister)
        }
        return ExpressionCodeResult(result, vmDt, resultRegister, -1)
    }

    private fun translate(mem: PtMemoryByte): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val resultRegister = codeGen.registers.next(IRDataType.BYTE)

        val constAddress = mem.address as? PtNumber
        if(constAddress!=null) {
            addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, address = constAddress.number.toInt()), null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        }

        val ptrWithOffset = mem.address as? PtBinaryExpression
        if(ptrWithOffset!=null && ptrWithOffset.operator=="+" && ptrWithOffset.left is PtIdentifier) {
            if((ptrWithOffset.right as? PtNumber)?.number?.toInt() in 0..255) {
                // LOADIX only works with byte index.
                val ptrName = (ptrWithOffset.left as PtIdentifier).name
                val offsetReg = codeGen.registers.next(IRDataType.BYTE)
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=offsetReg, immediate = ptrWithOffset.right.asConstInteger())
                    it += IRInstruction(Opcode.LOADIX, IRDataType.BYTE, reg1=resultRegister, reg2=offsetReg, labelSymbol = ptrName)
                }
                return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
            }
        }
        val offsetTypecast = ptrWithOffset?.right as? PtTypeCast
        if(ptrWithOffset!=null && ptrWithOffset.operator=="+" && ptrWithOffset.left is PtIdentifier
            && (ptrWithOffset.right.type.isByte || offsetTypecast?.value?.type?.isByte==true)) {
            // LOADIX only works with byte index.
            val tr = if(offsetTypecast?.value?.type?.isByte==true)
                translateExpression(offsetTypecast.value)
            else
                translateExpression(ptrWithOffset.right)
            addToResult(result, tr, tr.resultReg, -1)
            val ptrName = (ptrWithOffset.left as PtIdentifier).name
            addInstr(result, IRInstruction(Opcode.LOADIX, IRDataType.BYTE, reg1=resultRegister, reg2=tr.resultReg, labelSymbol = ptrName), null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        }

        val tr = translateExpression(mem.address)
        addToResult(result, tr, tr.resultReg, -1)
        addInstr(result, IRInstruction(Opcode.LOADI, IRDataType.BYTE, reg1=resultRegister, reg2=tr.resultReg), null)
        return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
    }

    private fun translate(check: PtContainmentCheck): ExpressionCodeResult {
        val elementDt = check.needle.type
        val result = mutableListOf<IRCodeChunkBase>()

        if(check.haystackValues!=null) {
            val haystack = check.haystackValues!!.children.map {
                if(it is PtBool) it.asInt()
                else (it as PtNumber).number.toInt()
            }
            when {
                elementDt.isIntegerOrBool -> {
                    if (elementDt.isByteOrBool) require(haystack.size in 0..PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_BYTE)
                    if (elementDt.isWord) require(haystack.size in 0..PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_WORD)
                    val gottemLabel = codeGen.createLabelName()
                    val endLabel = codeGen.createLabelName()
                    val elementTr = translateExpression(check.needle)
                    addToResult(result, elementTr, elementTr.resultReg, -1)
                    val boolResultRegister = if(elementDt.isByteOrBool) elementTr.resultReg else codeGen.registers.next(IRDataType.BYTE)
                    result += IRCodeChunk(null, null).also {
                        for(value in haystack){
                            it += IRInstruction(Opcode.CMPI, irType(elementDt), elementTr.resultReg, immediate = value)
                            it += IRInstruction(Opcode.BSTEQ, labelSymbol = gottemLabel)
                        }
                        it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, boolResultRegister, immediate = 0)
                        it += IRInstruction(Opcode.JUMP, labelSymbol = endLabel)
                    }
                    addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, boolResultRegister, immediate = 1), gottemLabel)
                    result += IRCodeChunk(endLabel, null)
                    return ExpressionCodeResult(result, IRDataType.BYTE, boolResultRegister, -1)
                }
                elementDt.isFloat -> throw AssemblyError("containmentchecks for floats should always be done on an array variable with subroutine")
                else -> throw AssemblyError("weird dt $elementDt")
            }
        }

        val haystackVar = check.haystackHeapVar!!
        when {
            haystackVar.type.isString -> {
                val elementTr = translateExpression(check.needle)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(haystackVar)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                result += codeGen.makeSyscall(IMSyscall.STRING_CONTAINS, listOf(IRDataType.BYTE to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg), IRDataType.BYTE to resultReg)
                addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultReg, immediate = 0), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            haystackVar.type.isByteArray -> {
                val elementTr = translateExpression(check.needle)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(haystackVar)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val lengthReg = codeGen.registers.next(IRDataType.BYTE)
                val iterableLength = codeGen.symbolTable.getLength(haystackVar.name)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, immediate = iterableLength!!), null)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                result += codeGen.makeSyscall(IMSyscall.BYTEARRAY_CONTAINS, listOf(IRDataType.BYTE to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to resultReg)
                addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultReg, immediate = 0), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            haystackVar.type.isWordArray -> {
                val elementTr = translateExpression(check.needle)
                addToResult(result, elementTr, elementTr.resultReg, -1)
                val iterableTr = translateExpression(haystackVar)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val lengthReg = codeGen.registers.next(IRDataType.BYTE)
                val iterableLength = codeGen.symbolTable.getLength(haystackVar.name)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, immediate = iterableLength!!), null)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                result += codeGen.makeSyscall(IMSyscall.WORDARRAY_CONTAINS, listOf(IRDataType.WORD to elementTr.resultReg, IRDataType.WORD to iterableTr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to resultReg)
                addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultReg, immediate = 0), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            haystackVar.type.isFloatArray -> {
                val elementTr = translateExpression(check.needle)
                addToResult(result, elementTr, -1, elementTr.resultFpReg)
                val iterableTr = translateExpression(haystackVar)
                addToResult(result, iterableTr, iterableTr.resultReg, -1)
                val lengthReg = codeGen.registers.next(IRDataType.BYTE)
                val resultReg = codeGen.registers.next(IRDataType.BYTE)
                val iterableLength = codeGen.symbolTable.getLength(haystackVar.name)
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=lengthReg, immediate = iterableLength!!), null)
                result += codeGen.makeSyscall(IMSyscall.FLOATARRAY_CONTAINS, listOf(IRDataType.FLOAT to elementTr.resultFpReg, IRDataType.WORD to iterableTr.resultReg, IRDataType.BYTE to lengthReg), IRDataType.BYTE to resultReg)
                addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultReg, immediate = 0), null)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
            }
            else -> throw AssemblyError("weird iterable dt ${haystackVar.type} for ${haystackVar.name}")
        }
    }

    private fun translate(arrayIx: PtArrayIndexer): ExpressionCodeResult {
        val eltSize = codeGen.program.memsizer.memorySize(arrayIx.type, null)
        val vmDt = irType(arrayIx.type)
        val result = mutableListOf<IRCodeChunkBase>()
        val arrayVarSymbol = arrayIx.variable.name
        var resultRegister = -1

        if(arrayIx.splitWords) {
            require(vmDt==IRDataType.WORD)
            resultRegister = codeGen.registers.next(IRDataType.BYTE)
            val finalResultReg = codeGen.registers.next(IRDataType.WORD)
            if(arrayIx.index is PtNumber) {
                val memOffset = (arrayIx.index as PtNumber).number.toInt()
                result += IRCodeChunk(null, null).also {
                    val tmpRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=tmpRegMsb, labelSymbol= "${arrayVarSymbol}_msb", symbolOffset = memOffset)
                    it += IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=resultRegister, labelSymbol= "${arrayVarSymbol}_lsb", symbolOffset = memOffset)
                    it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=finalResultReg, reg2=tmpRegMsb, reg3=resultRegister)
                }
            } else {
                val tr = translateExpression(arrayIx.index)
                addToResult(result, tr, tr.resultReg, -1)
                result += IRCodeChunk(null, null).also {
                    val tmpRegMsb = codeGen.registers.next(IRDataType.BYTE)
                    it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=tmpRegMsb, reg2 = tr.resultReg, labelSymbol= "${arrayVarSymbol}_msb")
                    it += IRInstruction(Opcode.LOADX, IRDataType.BYTE, reg1=resultRegister, reg2 = tr.resultReg, labelSymbol= "${arrayVarSymbol}_lsb")
                    it += IRInstruction(Opcode.CONCAT, IRDataType.BYTE, reg1=finalResultReg, reg2=tmpRegMsb, reg3=resultRegister)
                }
            }
            return ExpressionCodeResult(result, vmDt, finalResultReg, -1)
        }

        var resultFpRegister = -1
        if(arrayIx.index is PtNumber) {
            val memOffset = ((arrayIx.index as PtNumber).number.toInt() * eltSize)
            if(vmDt==IRDataType.FLOAT) {
                resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(result, IRInstruction(Opcode.LOADM, IRDataType.FLOAT, fpReg1=resultFpRegister, labelSymbol = arrayVarSymbol, symbolOffset = memOffset), null)
            }
            else {
                resultRegister = codeGen.registers.next(vmDt)
                addInstr(result, IRInstruction(Opcode.LOADM, vmDt, reg1=resultRegister, labelSymbol = arrayVarSymbol, symbolOffset = memOffset), null)
            }
        } else {
            val tr = translateExpression(arrayIx.index)
            addToResult(result, tr, tr.resultReg, -1)
            if(eltSize>1)
                result += codeGen.multiplyByConst(DataType.UBYTE, tr.resultReg, eltSize)
            if(vmDt==IRDataType.FLOAT) {
                resultFpRegister = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(result, IRInstruction(Opcode.LOADX, IRDataType.FLOAT, fpReg1 = resultFpRegister, reg1=tr.resultReg, labelSymbol = arrayVarSymbol), null)
            }
            else {
                resultRegister = codeGen.registers.next(vmDt)
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
                addInstr(result, IRInstruction(Opcode.INV, vmDt, reg1 = tr.resultReg), null)
            }
            "not" -> {
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, immediate = 1), null)
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
        val valueDt = cast.value.type
        when(cast.type.base) {
            BaseDataType.BOOL -> {
                when {
                    valueDt.isByte -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=tr.resultReg, immediate = 0), null)
                        actualResultReg2 = loadStatusAsBooleanResult(Opcode.BSTNE, result)
                    }
                    valueDt.isWord -> {
                        addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.WORD, reg1=tr.resultReg, immediate = 0), null)
                        actualResultReg2 =loadStatusAsBooleanResult(Opcode.BSTNE, result)
                    }
                    valueDt.isFloat -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.BYTE)
                        result += IRCodeChunk(null, null).also {
                            it += IRInstruction(Opcode.SGN, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg)
                            it += IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=actualResultReg2, immediate = 1)
                        }
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            BaseDataType.UBYTE -> {
                when(valueDt.base) {
                    BaseDataType.BOOL, BaseDataType.BYTE -> {
                        actualResultReg2 = tr.resultReg
                    }
                    BaseDataType.UWORD, BaseDataType.WORD -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.BYTE)
                        addInstr(result, IRInstruction(Opcode.LSIG, IRDataType.BYTE, reg1=actualResultReg2, reg2=tr.resultReg, immediate = 0), null)
                    }
                    BaseDataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.BYTE)
                        addInstr(result, IRInstruction(Opcode.FTOUB, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            BaseDataType.BYTE -> {
                when(valueDt.base) {
                    BaseDataType.BOOL, BaseDataType.UBYTE -> {
                        actualResultReg2 = tr.resultReg
                    }
                    BaseDataType.UWORD, BaseDataType.WORD -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.BYTE)
                        addInstr(result, IRInstruction(Opcode.LSIG, IRDataType.BYTE, reg1=actualResultReg2, reg2=tr.resultReg, immediate = 0), null)
                    }
                    BaseDataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.BYTE)
                        addInstr(result, IRInstruction(Opcode.FTOSB, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            BaseDataType.UWORD -> {
                when(valueDt.base) {
                    BaseDataType.BYTE -> {
                        // byte -> uword:   sign extend
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2 = tr.resultReg), null)
                    }
                    BaseDataType.BOOL, BaseDataType.UBYTE -> {
                        // ubyte -> uword:   sign extend
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2 = tr.resultReg), null)
                    }
                    BaseDataType.WORD -> {
                        actualResultReg2 = tr.resultReg
                    }
                    BaseDataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.FTOUW, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            BaseDataType.WORD -> {
                when(valueDt.base) {
                    BaseDataType.BYTE -> {
                        // byte -> word:   sign extend
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.EXTS, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2=tr.resultReg), null)
                    }
                    BaseDataType.BOOL, BaseDataType.UBYTE -> {
                        // byte -> word:   sign extend
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.EXT, type = IRDataType.BYTE, reg1 = actualResultReg2, reg2=tr.resultReg), null)
                    }
                    BaseDataType.UWORD -> {
                        actualResultReg2 = tr.resultReg
                    }
                    BaseDataType.FLOAT -> {
                        actualResultReg2 = codeGen.registers.next(IRDataType.WORD)
                        addInstr(result, IRInstruction(Opcode.FTOSW, IRDataType.FLOAT, reg1=actualResultReg2, fpReg1 = tr.resultFpReg), null)
                    }
                    else -> throw AssemblyError("weird cast value type")
                }
            }
            BaseDataType.FLOAT -> {
                actualResultFpReg2 = codeGen.registers.next(IRDataType.FLOAT)
                when(valueDt.base) {
                    BaseDataType.BOOL, BaseDataType.UBYTE -> {
                        addInstr(result, IRInstruction(Opcode.FFROMUB, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    BaseDataType.BYTE -> {
                        addInstr(result, IRInstruction(Opcode.FFROMSB, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    BaseDataType.UWORD -> {
                        addInstr(result, IRInstruction(Opcode.FFROMUW, IRDataType.FLOAT, reg1=tr.resultReg, fpReg1 = actualResultFpReg2), null)
                    }
                    BaseDataType.WORD -> {
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
        val signed = binExpr.left.type.isSigned
        return when(binExpr.operator) {
            "+" -> operatorPlus(binExpr, vmDt)
            "-" -> operatorMinus(binExpr, vmDt)
            "*" -> operatorMultiply(binExpr, binExpr.left.type)
            "/" -> operatorDivide(binExpr, binExpr.left.type)
            "%" -> operatorModulo(binExpr, vmDt)
            "|" -> operatorOr(binExpr, vmDt, true)
            "&" -> operatorAnd(binExpr, vmDt, true)
            "^", "xor" -> operatorXor(binExpr, vmDt)
            "or" -> operatorOr(binExpr, vmDt, false)
            "and" -> operatorAnd(binExpr, vmDt, false)
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

    internal fun translate(fcall: PtFunctionCall): ExpressionCodeResult {
        val callTarget = codeGen.symbolTable.lookup(fcall.name)!!

        if(callTarget.scopedName in listOf("sys.push", "sys.pushw", "sys.pop", "sys.popw", "floats.push", "floats.pop")) {
            // special case, these should be inlined, or even use specialized instructions. Instead of doing a normal subroutine call.
            return translateStackFunctions(fcall, callTarget)
        }
        when(callTarget.scopedName) {
            "sys.clear_carry" -> {
                val chunk = mutableListOf<IRCodeChunkBase>()
                addInstr(chunk, IRInstruction(Opcode.CLC), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, -1, -1)
            }
            "sys.set_carry" -> {
                val chunk = mutableListOf<IRCodeChunkBase>()
                addInstr(chunk, IRInstruction(Opcode.SEC), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, -1, -1)
            }
            "sys.clear_irqd" -> {
                val chunk = mutableListOf<IRCodeChunkBase>()
                addInstr(chunk, IRInstruction(Opcode.CLI), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, -1, -1)
            }
            "sys.set_irqd" -> {
                val chunk = mutableListOf<IRCodeChunkBase>()
                addInstr(chunk, IRInstruction(Opcode.SEI), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, -1, -1)
            }
        }

        when (callTarget) {
            is StSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
                // assign the arguments
                val argRegisters = mutableListOf<FunctionCallArgs.ArgumentSpec>()
                for ((arg, parameter) in fcall.args.zip(callTarget.parameters)) {
                    val paramDt = irType(parameter.type)
                    if(parameter.register==null) {
                        val tr = translateExpression(arg)
                        result += tr.chunks
                        if(paramDt==IRDataType.FLOAT)
                            argRegisters.add(FunctionCallArgs.ArgumentSpec(parameter.name, null, FunctionCallArgs.RegSpec(IRDataType.FLOAT, tr.resultFpReg, null)))
                        else
                            argRegisters.add(FunctionCallArgs.ArgumentSpec(parameter.name, null, FunctionCallArgs.RegSpec(paramDt, tr.resultReg, null)))
                    } else {
                        require(parameter.register in Cx16VirtualRegisters) { "can only use R0-R15 'registers' here" }
                        val regname = parameter.register!!.asScopedNameVirtualReg(parameter.type).joinToString(".")
                        val assign = PtAssignment(fcall.position)
                        val target = PtAssignTarget(true, fcall.position)
                        target.add(PtIdentifier(regname, parameter.type, fcall.position))
                        assign.add(target)
                        assign.add(arg)
                        result += codeGen.translateNode(assign)
                    }
                }
                // return value(s)
                // TODO: for current implemenation of the call convention in case of multiple return values,
                // a list of Ir virtual registers to hold the results is NOT correct (they're loaded into AY + R15..R0 instead!)
                // So we use an empty list to avoid confusion here.   This may change in a future version.
                val returnRegSpecs = if(fcall.void || callTarget.returns.size>1) emptyList() else {
                    callTarget.returns.map {
                        val returnIrType = irType(it)
                        FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.next(returnIrType), null)
                    }
                }
                // create the call
                addInstr(result, IRInstruction(Opcode.CALL, labelSymbol = fcall.name,
                    fcallArgs = FunctionCallArgs(argRegisters, returnRegSpecs)), null)
                return if(fcall.void)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)       // TODO datatype void?
                else if(returnRegSpecs.size==1) {
                    val returnRegSpec = returnRegSpecs.single()
                    if (fcall.type.isFloat)
                        ExpressionCodeResult(result, returnRegSpec.dt, -1, returnRegSpec.registerNum)
                    else
                        ExpressionCodeResult(result, returnRegSpec.dt, returnRegSpec.registerNum, -1)
                } else {
                    // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
                    // so the actual result of the expression here is 'void' (doesn't use IR virtual registers at all)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                }
            }
            is StExtSub -> {
                val result = mutableListOf<IRCodeChunkBase>()
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
                    result += codeGen.setCpuRegister(parameter.register, paramDt, tr.resultReg, tr.resultFpReg)
                }

                if(callTarget.returns.size>1)
                    return callExtSubWithMultipleReturnValues(callTarget, fcall, argRegisters, result)

                // return a single value (or nothing)
                val returnRegSpec = if(fcall.void) null else {
                    if(callTarget.returns.isEmpty())
                        null
                    else {
                        val returns = callTarget.returns[0]
                        val returnIrType = irType(returns.type)
                        FunctionCallArgs.RegSpec(returnIrType, codeGen.registers.next(returnIrType), returns.register)
                    }
                }
                // create the call
                val returnRegs = if(returnRegSpec==null) emptyList() else listOf(returnRegSpec)
                val call =
                    if(callTarget.address==null)
                        IRInstruction(Opcode.CALL, labelSymbol = fcall.name, fcallArgs = FunctionCallArgs(argRegisters, returnRegs))
                    else {
                        val address = callTarget.address!!
                        if(address.constbank==null && address.varbank==null) {
                            IRInstruction(
                                Opcode.CALL,
                                address = address.address.toInt(),
                                fcallArgs = FunctionCallArgs(argRegisters, returnRegs))
                        } else if(address.constbank!=null) {
                            IRInstruction(
                                Opcode.CALLFAR,
                                address = address.address.toInt(),
                                immediate = address.constbank!!.toInt()
                            )
                        } else {
                            val tr = translateExpression(address.varbank!!)
                            require(tr.dt==IRDataType.BYTE)
                            result += tr.chunks
                            IRInstruction(
                                Opcode.CALLFARVB,
                                address = address.address.toInt(),
                                reg1 = tr.resultReg
                            )
                        }
                    }
                addInstr(result, call, null)
                var finalReturnRegister = returnRegSpec?.registerNum ?: -1

                if(fcall.parent is PtAssignment || fcall.parent is PtTypeCast) {
                    // look if the status flag bit should actually be returned as a 0/1 byte value in a result register (so it can be assigned)
                    val statusFlagResult = returnRegSpec?.cpuRegister?.statusflag
                    if(statusFlagResult!=null) {
                        // assign status flag bit to the return value register
                        finalReturnRegister = returnRegSpec.registerNum
                        if(finalReturnRegister<0)
                            finalReturnRegister = codeGen.registers.next(returnRegSpec.dt)
                        when(statusFlagResult) {
                            Statusflag.Pc -> {
                                result += IRCodeChunk(null, null).also {
                                    it += IRInstruction(Opcode.LOAD, returnRegSpec.dt, reg1 = finalReturnRegister, immediate = 0)
                                    it += IRInstruction(Opcode.ROXL, returnRegSpec.dt, reg1 = finalReturnRegister)
                                }
                            }
                            else -> {
                                val branchOpcode = when(statusFlagResult) {
                                    Statusflag.Pc -> throw AssemblyError("carry should be treated separately")
                                    Statusflag.Pz -> Opcode.BSTEQ
                                    Statusflag.Pv -> Opcode.BSTVS
                                    Statusflag.Pn -> Opcode.BSTNEG
                                }
                                val setLabel = codeGen.createLabelName()
                                val endLabel = codeGen.createLabelName()
                                result += IRCodeChunk(null, null).also {
                                    it += IRInstruction(branchOpcode, labelSymbol = setLabel)
                                    it += IRInstruction(Opcode.LOAD, returnRegSpec.dt, reg1=finalReturnRegister, immediate = 0)
                                    it += IRInstruction(Opcode.JUMP, labelSymbol = endLabel)
                                }
                                result += IRCodeChunk(setLabel, null).also {
                                    it += IRInstruction(Opcode.LOAD, returnRegSpec.dt, reg1=finalReturnRegister, immediate = 1)
                                }
                                result += IRCodeChunk(endLabel, null)
                            }
                        }
                    }
                }

                return if(fcall.void)
                    ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                else if(fcall.type.isFloat)
                    ExpressionCodeResult(result, returnRegSpec!!.dt, -1, finalReturnRegister)
                else
                    ExpressionCodeResult(result, returnRegSpec!!.dt, finalReturnRegister, -1)
            }
            else -> {
                if(callTarget.type == StNodeType.LABEL) {
                    require(fcall.void)
                    val result = mutableListOf<IRCodeChunkBase>()
                    val args = FunctionCallArgs(emptyList(), emptyList())
                    addInstr(result, IRInstruction(Opcode.CALL, labelSymbol = fcall.name, fcallArgs = args), null)
                    return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1)
                }
                else {
                    throw AssemblyError("invalid node type")
                }
            }
        }
    }

    internal fun checkIfConditionCanUseBIT(condition: PtBinaryExpression): Triple<Boolean, PtIdentifier, Int>? {
        // test for occurrence of:  x & 64 != 0    (or 128) , this can be performed with a BIT instruction
        if(condition.operator == "==" || condition.operator == "!=") {
            if (condition.right.asConstInteger() == 0) {
                val and = condition.left as? PtBinaryExpression
                if (and != null && and.operator == "&" && and.type.isUnsignedByte) {
                    val bitmask = and.right.asConstInteger()
                    if(bitmask==128 || bitmask==64) {
                        val variable = and.left as? PtIdentifier
                        if (variable != null && variable.type.isByte) {
                            return Triple(condition.operator=="!=", variable, bitmask)
                        }
                        val typecast = and.left as? PtTypeCast
                        if (typecast != null && typecast.type.isUnsignedByte) {
                            val castedVariable = typecast.value as? PtIdentifier
                            if(castedVariable!=null && castedVariable.type.isByte)
                                return Triple(condition.operator=="!=", castedVariable, bitmask)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun translateStackFunctions(fcall: PtFunctionCall, callTarget: StNode): ExpressionCodeResult {
        val chunk = mutableListOf<IRCodeChunkBase>()
        when(callTarget.scopedName) {
            "sys.push" -> {
                // push byte
                val tr = translateExpression(fcall.args.single())
                chunk += tr.chunks
                addInstr(chunk, IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=tr.resultReg), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, -1, -1)
            }
            "sys.pushw" -> {
                // push word
                val tr = translateExpression(fcall.args.single())
                chunk += tr.chunks
                addInstr(chunk, IRInstruction(Opcode.PUSH, IRDataType.WORD, reg1=tr.resultReg), null)
                return ExpressionCodeResult(chunk, IRDataType.WORD, -1, -1)
            }
            "sys.pop" -> {
                // pop byte
                val popReg = codeGen.registers.next(IRDataType.BYTE)
                addInstr(chunk, IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=popReg), null)
                return ExpressionCodeResult(chunk, IRDataType.BYTE, popReg, -1)
            }
            "sys.popw" -> {
                // pop word
                val popReg = codeGen.registers.next(IRDataType.WORD)
                addInstr(chunk, IRInstruction(Opcode.POP, IRDataType.WORD, reg1=popReg), null)
                return ExpressionCodeResult(chunk, IRDataType.WORD, popReg, -1)
            }
            "floats.push" -> {
                // push float
                val tr = translateExpression(fcall.args.single())
                chunk += tr.chunks
                addInstr(chunk, IRInstruction(Opcode.PUSH, IRDataType.FLOAT, fpReg1 = tr.resultFpReg), null)
                return ExpressionCodeResult(chunk, IRDataType.FLOAT, -1, -1)
            }
            "floats.pop" -> {
                // pop float
                val popReg = codeGen.registers.next(IRDataType.FLOAT)
                addInstr(chunk, IRInstruction(Opcode.POP, IRDataType.FLOAT, fpReg1 = popReg), null)
                return ExpressionCodeResult(chunk, IRDataType.FLOAT, -1, resultFpReg = popReg)
            }
            else -> throw AssemblyError("unknown stack subroutine called")
        }
    }

    private fun callExtSubWithMultipleReturnValues(
        callTarget: StExtSub,
        fcall: PtFunctionCall,
        argRegisters: MutableList<FunctionCallArgs.ArgumentSpec>,
        result: MutableList<IRCodeChunkBase>
    ): ExpressionCodeResult {
        // return multiple values
        val returnRegisters = callTarget.returns.map {
            val regnum = codeGen.registers.next(irType(it.type))
            FunctionCallArgs.RegSpec(irType(it.type), regnum, it.register)
        }
        // create the call
        val call =
            if(callTarget.address==null)
                IRInstruction(Opcode.CALL, labelSymbol = fcall.name, fcallArgs = FunctionCallArgs(argRegisters, returnRegisters))
            else {
                val address = callTarget.address!!
                if(address.constbank==null && address.varbank==null) {
                    IRInstruction(
                        Opcode.CALL,
                        address = address.address.toInt(),
                        fcallArgs = FunctionCallArgs(argRegisters, returnRegisters)
                    )
                }
                else TODO("extsub with banked address got called ${callTarget.name}")
            }
        addInstr(result, call, null)
        val resultRegs = returnRegisters.filter{it.dt!=IRDataType.FLOAT}.map{it.registerNum}
        val resultFpRegs = returnRegisters.filter{it.dt==IRDataType.FLOAT}.map{it.registerNum}
        return ExpressionCodeResult(result, IRDataType.BYTE, -1, -1, resultRegs, resultFpRegs)
    }

    private fun operatorGreaterThan(
        binExpr: PtBinaryExpression,
        vmDt: IRDataType,
        signed: Boolean,
        greaterEquals: Boolean
    ): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            val resultRegister = codeGen.registers.next(IRDataType.BYTE)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultRegister, immediate = 0), null)
            val other = codeGen.createLabelName()
            val after = codeGen.createLabelName()
            val resultReg = codeGen.registers.next(IRDataType.BYTE)
            // TODO can this be done more efficiently? also see operatorLessThan
            if(greaterEquals) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.BSTPOS, labelSymbol = other)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 0)
                    it += IRInstruction(Opcode.JUMP, labelSymbol = after)
                }
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 1), other)
            } else {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.BSTNEG, labelSymbol = other)
                    it += IRInstruction(Opcode.BSTEQ, labelSymbol = other)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 1)
                    it += IRInstruction(Opcode.JUMP, labelSymbol = after)
                }
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 0), other)
            }
            result += IRCodeChunk(after, null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
        } else {
            if(binExpr.left.type.isString || binExpr.right.type.isString) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val branch = if (signed) {
                    if (greaterEquals) Opcode.BGESR else Opcode.BGTSR
                } else {
                    if (greaterEquals) Opcode.BGER else Opcode.BGTR
                }
                val resultReg = compareRegisterAsBooleanResult(branch, leftTr.dt, leftTr.resultReg, rightTr.resultReg, result)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
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
        if(vmDt==IRDataType.FLOAT) {
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, -1, leftTr.resultFpReg)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, -1, rightTr.resultFpReg)
            val resultRegister = codeGen.registers.next(IRDataType.BYTE)
            addInstr(result, IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=resultRegister, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
            addInstr(result, IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=resultRegister, immediate = 0), null)
            val other = codeGen.createLabelName()
            val after = codeGen.createLabelName()
            val resultReg = codeGen.registers.next(IRDataType.BYTE)
            // TODO can this be done more efficiently? also see operatorGreaterThan
            if(lessEquals) {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.BSTNEG, labelSymbol = other)
                    it += IRInstruction(Opcode.BSTEQ, labelSymbol = other)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 0)
                    it += IRInstruction(Opcode.JUMP, labelSymbol = after)
                }
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 1), other)
            } else {
                result += IRCodeChunk(null, null).also {
                    it += IRInstruction(Opcode.BSTNEG, labelSymbol = other)
                    it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 0)
                    it += IRInstruction(Opcode.JUMP, labelSymbol = after)
                }
                addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 1), other)
            }
            result += IRCodeChunk(after, null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
        } else {
            if(binExpr.left.type.isString || binExpr.right.type.isString) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val branch = if (signed) {
                    if (lessEquals) Opcode.BGESR else Opcode.BGTSR
                } else {
                    if (lessEquals) Opcode.BGER else Opcode.BGTR
                }
                val resultReg = compareRegisterAsBooleanResult(branch, leftTr.dt, rightTr.resultReg, leftTr.resultReg, result)
                return ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
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
            val resultRegister = codeGen.registers.next(IRDataType.BYTE)
            val valueReg = codeGen.registers.next(IRDataType.BYTE)
            val label = codeGen.createLabelName()
            result += IRCodeChunk(null, null).also {
                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, immediate = 0)
                it += IRInstruction(Opcode.FCOMP, IRDataType.FLOAT, reg1=valueReg, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg)
                it += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=valueReg, immediate = 0)
                it += if (notEquals)
                    IRInstruction(Opcode.BSTEQ, labelSymbol = label)
                else
                    IRInstruction(Opcode.BSTNE, labelSymbol = label)
                it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultRegister, immediate = 1)
            }
            result += IRCodeChunk(label, null)
            return ExpressionCodeResult(result, IRDataType.BYTE, resultRegister, -1)
        } else {
            if(binExpr.left.type.isString || binExpr.right.type.isString) {
                throw AssemblyError("str compares should have been replaced with builtin function call to do the compare")
            } else {
                val rightConst = binExpr.right.asConstValue()
                return if(rightConst!=null) {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.CMPI, leftTr.dt, reg1 = leftTr.resultReg, immediate = rightConst.toInt()), null)
                    val resultReg = loadStatusAsBooleanResult(if(notEquals) Opcode.BSTNE else Opcode.BSTEQ, result)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
                } else {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.CMP, leftTr.dt, reg1=leftTr.resultReg, reg2=rightTr.resultReg), null)
                    val resultReg = loadStatusAsBooleanResult(if(notEquals) Opcode.BSTNE else Opcode.BSTEQ, result)
                    ExpressionCodeResult(result, IRDataType.BYTE, resultReg, -1)
                }
            }
        }
    }

    private fun loadStatusAsBooleanResult(branchForTrue: Opcode, result: MutableList<IRCodeChunkBase>): Int {
        // TODO this used to be a single instruction like SCC, SCS, SZ etc
        val other = codeGen.createLabelName()
        val after = codeGen.createLabelName()
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(branchForTrue, labelSymbol = other)
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 0)
            it += IRInstruction(Opcode.JUMP, labelSymbol = after)
        }
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 1), other)
        result += IRCodeChunk(after, null)
        return resultReg
    }

    private fun compareRegisterAsBooleanResult(branchForTrue: Opcode, dt: IRDataType, reg1: Int, reg2: Int, result: MutableList<IRCodeChunkBase>): Int {
        // TODO this used to be a single instruction like SCC, SCS, SZ etc
        val other = codeGen.createLabelName()
        val after = codeGen.createLabelName()
        val resultReg = codeGen.registers.next(IRDataType.BYTE)
        result += IRCodeChunk(null, null).also {
            it += IRInstruction(branchForTrue, dt, reg1=reg1, reg2=reg2, labelSymbol = other)
            it += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = resultReg, immediate = 0)
            it += IRInstruction(Opcode.JUMP, labelSymbol = after)
        }
        addInstr(result, IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=resultReg, immediate = 1), other)
        result += IRCodeChunk(after, null)
        return resultReg
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
            require(rightTr.dt== IRDataType.BYTE) { "can only shift by 0-255" }
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
            require(rightTr.dt== IRDataType.BYTE) { "can only shift by 0-255" }
            addToResult(result, rightTr, rightTr.resultReg, -1)
            addInstr(result, IRInstruction(Opcode.LSLN, vmDt, reg1=leftTr.resultReg, rightTr.resultReg), null)
            ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        }
    }

    private fun operatorXor(binExpr: PtBinaryExpression, vmDt: IRDataType): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        val tr = translateExpression(binExpr.left)
        addToResult(result, tr, tr.resultReg, -1)
        return when (binExpr.right) {
            is PtNumber -> {
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
            is PtBool -> {
                addInstr(result, IRInstruction(Opcode.XOR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtBool).asInt()), null)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
            else -> {
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                addInstr(result, IRInstruction(Opcode.XORR, vmDt, reg1 = tr.resultReg, reg2 = rightTr.resultReg), null)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            }
        }
    }

    private fun operatorAnd(binExpr: PtBinaryExpression, vmDt: IRDataType, bitwise: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(!bitwise && !binExpr.right.isSimple()) {
            // short-circuit  LEFT and RIGHT  -->  if LEFT then RIGHT else LEFT   (== if !LEFT then LEFT else RIGHT)
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val shortcutLabel = codeGen.createLabelName()
            addInstr(result, IRInstruction(Opcode.BSTEQ, labelSymbol = shortcutLabel), null)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, leftTr.resultReg, -1)
            result += IRCodeChunk(shortcutLabel, null)
            return ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        } else {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            return when (binExpr.right) {
                is PtNumber -> {
                    addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
                is PtBool -> {
                    addInstr(result, IRInstruction(Opcode.AND, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtBool).asInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
                else -> {
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ANDR, vmDt, reg1 = tr.resultReg, reg2 = rightTr.resultReg), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
            }
        }
    }

    private fun operatorOr(binExpr: PtBinaryExpression, vmDt: IRDataType, bitwise: Boolean): ExpressionCodeResult {
        val result = mutableListOf<IRCodeChunkBase>()
        if(!bitwise && !binExpr.right.isSimple()) {
            // short-circuit  LEFT or RIGHT  -->  if LEFT then LEFT else RIGHT
            val leftTr = translateExpression(binExpr.left)
            addToResult(result, leftTr, leftTr.resultReg, -1)
            val shortcutLabel = codeGen.createLabelName()
            addInstr(result, IRInstruction(Opcode.BSTNE, labelSymbol = shortcutLabel), null)
            val rightTr = translateExpression(binExpr.right)
            addToResult(result, rightTr, leftTr.resultReg, -1)
            result += IRCodeChunk(shortcutLabel, null)
            return ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
        } else {
            val tr = translateExpression(binExpr.left)
            addToResult(result, tr, tr.resultReg, -1)
            return when (binExpr.right) {
                is PtNumber -> {
                    addInstr(result, IRInstruction(Opcode.OR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtNumber).number.toInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
                is PtBool -> {
                    addInstr(result, IRInstruction(Opcode.OR, vmDt, reg1 = tr.resultReg, immediate = (binExpr.right as PtBool).asInt()), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
                else -> {
                    val rightTr = translateExpression(binExpr.right)
                    addToResult(result, rightTr, rightTr.resultReg, -1)
                    addInstr(result, IRInstruction(Opcode.ORR, vmDt, reg1 = tr.resultReg, reg2 = rightTr.resultReg), null)
                    ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
                }
            }
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

    private fun operatorDivide(binExpr: PtBinaryExpression, dt: DataType): ExpressionCodeResult {
        val vmDt = irType(dt)
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorRight.number
                result += codeGen.divideByConstFloat(tr.resultFpReg, factor)
                return ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, leftTr.resultFpReg)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, if(dt.isSigned)
                        IRInstruction(Opcode.DIVSR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2=rightTr.resultFpReg)
                    else
                        IRInstruction(Opcode.DIVR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2=rightTr.resultFpReg)
                        , null)
                return ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
            }
        } else {
            return if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.divideByConst(vmDt, tr.resultReg, factor, dt.isSigned)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else {
                if(binExpr.right is PtNumber) {
                    val leftTr = translateExpression(binExpr.left)
                    addToResult(result, leftTr, leftTr.resultReg, -1)
                    addInstr(result, if (dt.isSigned)
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
                    addInstr(result, if (dt.isSigned)
                            IRInstruction(Opcode.DIVSR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg)
                        else
                            IRInstruction(Opcode.DIVR, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg)
                            , null)
                    ExpressionCodeResult(result, vmDt, leftTr.resultReg, -1)
                }
            }
        }
    }

    private fun operatorMultiply(binExpr: PtBinaryExpression, dt: DataType): ExpressionCodeResult {
        val vmDt = irType(dt)
        val result = mutableListOf<IRCodeChunkBase>()
        val constFactorLeft = binExpr.left as? PtNumber
        val constFactorRight = binExpr.right as? PtNumber
        if(vmDt==IRDataType.FLOAT) {
            return if(constFactorLeft!=null) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorLeft.number
                result += codeGen.multiplyByConstFloat(tr.resultFpReg, factor)
                ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else if(constFactorRight!=null) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, -1, tr.resultFpReg)
                val factor = constFactorRight.number
                result += codeGen.multiplyByConstFloat(tr.resultFpReg, factor)
                ExpressionCodeResult(result, vmDt, -1, tr.resultFpReg)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, -1, leftTr.resultFpReg)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, -1, rightTr.resultFpReg)
                addInstr(result, IRInstruction(Opcode.MULSR, vmDt, fpReg1 = leftTr.resultFpReg, fpReg2 = rightTr.resultFpReg), null)
                ExpressionCodeResult(result, vmDt, -1, leftTr.resultFpReg)
            }
        } else {
            return if(constFactorLeft!=null && !constFactorLeft.type.isFloat) {
                val tr = translateExpression(binExpr.right)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorLeft.number.toInt()
                result += codeGen.multiplyByConst(dt, tr.resultReg, factor)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else if(constFactorRight!=null && !constFactorRight.type.isFloat) {
                val tr = translateExpression(binExpr.left)
                addToResult(result, tr, tr.resultReg, -1)
                val factor = constFactorRight.number.toInt()
                result += codeGen.multiplyByConst(dt, tr.resultReg, factor)
                ExpressionCodeResult(result, vmDt, tr.resultReg, -1)
            } else {
                val leftTr = translateExpression(binExpr.left)
                addToResult(result, leftTr, leftTr.resultReg, -1)
                val rightTr = translateExpression(binExpr.right)
                addToResult(result, rightTr, rightTr.resultReg, -1)
                val opcode = if(dt.isSigned) Opcode.MULSR else Opcode.MULR
                addInstr(result, IRInstruction(opcode, vmDt, reg1 = leftTr.resultReg, reg2 = rightTr.resultReg), null)
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
                    addInstr(result, IRInstruction(Opcode.SUB, vmDt, fpReg1 = tr.resultFpReg, immediateFp = (binExpr.right as PtNumber).number), null)
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
                    addInstr(result, IRInstruction(Opcode.ADD, vmDt, fpReg1 = tr.resultFpReg, immediateFp = (binExpr.right as PtNumber).number), null)
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
