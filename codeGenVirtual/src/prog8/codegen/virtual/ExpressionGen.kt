package prog8.codegen.virtual

import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.vm.Instruction
import prog8.vm.Opcode

internal class ExpressionGen(val codeGen: CodeGen) {
    fun translateExpression(expr: PtExpression): Pair<VmCodeChunk, Int> {
        // TODO("Not yet implemented")
        val chunk = VmCodeChunk()
        val vmDt = codeGen.vmType(expr.type)
        val resultRegister = 0  // TODO need a way to make this dynamic to avoid clobbering existing registers

        fun process(code: VmCodeChunk, actualResultReg: Int) {
            chunk += code
            if(actualResultReg!=resultRegister)
                chunk += VmCodeInstruction(Instruction(Opcode.LOADR, vmDt, reg1=resultRegister, reg2=actualResultReg))
        }

        when (expr) {
            is PtNumber -> {
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=expr.number.toInt()))
            }
            is PtIdentifier -> {
                val mem = codeGen.allocations.get(expr.targetName)
                chunk += VmCodeInstruction(Instruction(Opcode.LOADM, vmDt, reg1=resultRegister, value=mem))
            }
            is PtAddressOf -> {
                val mem = codeGen.allocations.get(expr.identifier.targetName)
                chunk += VmCodeInstruction(Instruction(Opcode.LOAD, vmDt, reg1=resultRegister, value=mem))
            }
            is PtMemoryByte -> {
                val (addressExprCode, addressRegister) = translateExpression(expr.address)
                process(addressExprCode, addressRegister)
            }
            is PtTypeCast -> TODO()
            is PtPrefix -> TODO()
            is PtArrayIndexer -> TODO()
            is PtBinaryExpression -> {
                val (exprCode, functionResultReg) = translate(expr)
                process(exprCode, functionResultReg)
            }
            is PtBuiltinFunctionCall -> TODO()
            is PtContainmentCheck -> TODO()
            is PtFunctionCall -> {
                val (callCode, functionResultReg) = translate(expr)
                process(callCode, functionResultReg)
            }
            is PtPipe -> TODO()
            is PtRange -> TODO()
            is PtArrayLiteral -> TODO()
            is PtString -> TODO()
            else -> throw AssemblyError("weird expression")
        }
        return Pair(chunk, resultRegister)
    }

    private fun translate(binExpr: PtBinaryExpression): Pair<VmCodeChunk, Int> {
        val chunk = VmCodeChunk()
        val (leftCode, leftResultReg) = translateExpression(binExpr.left)
        val (rightCode, rightResultReg) = translateExpression(binExpr.right)
        chunk += leftCode
        chunk += rightCode
        val resultRegister = 0   // TODO binexpr result can't always be in r0...
        when(binExpr.operator) {
            "+" -> {
                chunk += VmCodeInstruction(Instruction(Opcode.ADD, codeGen.vmType(binExpr.type), reg1=resultRegister, reg2=leftResultReg, reg3=rightResultReg))
            }
            else -> TODO("operator ${binExpr.operator}")
        }
        return Pair(chunk, resultRegister)
    }

    private fun translate(fcall: PtFunctionCall): Pair<VmCodeChunk, Int> {
        require(!fcall.void)
        val chunk = VmCodeChunk()
        // TODO evaluate arguments
        chunk += VmCodeOpcodeWithStringArg(Opcode.GOSUB, codeGen.gosubArg(fcall.functionName))
        return Pair(chunk, 0)   // TODO function result always in r0?
    }



    fun translate(call: PtBuiltinFunctionCall): VmCodeChunk {
        val chunk = VmCodeChunk()
        when(call.name) {
            "syscall" -> {
                val vExpr = call.args.single() as PtNumber
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall1" -> {
                val vExpr = call.args[0] as PtNumber
                // TODO make sure it evaluates into r0
                val (expressionChunk0, resultRegister0) = translateExpression(call.args[1])
                chunk += expressionChunk0
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall2" -> {
                val vExpr = call.args[0] as PtNumber
                // TODO make sure it evaluates into r0
                val (expressionChunk0, resultRegister0) = translateExpression(call.args[1])
                chunk += expressionChunk0
                // TODO make sure it evaluates into r1
                val (expressionChunk1, resultRegister1) = translateExpression(call.args[2])
                chunk += expressionChunk1
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall3" -> {
                val vExpr = call.args[0] as PtNumber
                // TODO make sure it evaluates into r0
                val (expressionChunk0, resultRegister0) = translateExpression(call.args[1])
                chunk += expressionChunk0
                // TODO make sure it evaluates into r1
                val (expressionChunk1, resultRegister1) = translateExpression(call.args[2])
                chunk += expressionChunk1
                // TODO make sure it evaluates into r2
                val (expressionChunk2, resultRegister2) = translateExpression(call.args[3])
                chunk += expressionChunk2
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            else -> {
                TODO("builtinfunc ${call.name}")
            }
        }
        return chunk
    }

}
