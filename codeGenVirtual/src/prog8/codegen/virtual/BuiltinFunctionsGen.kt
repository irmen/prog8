package prog8.codegen.virtual

import prog8.code.ast.PtBuiltinFunctionCall
import prog8.code.ast.PtNumber
import prog8.vm.Instruction
import prog8.vm.Opcode

internal class BuiltinFunctionsGen(val codegen: CodeGen) {
    fun translate(call: PtBuiltinFunctionCall): VmCodeChunk {
        val chunk = VmCodeChunk()
        when(call.name) {
            "syscall" -> {
                val vExpr = call.args.single() as PtNumber
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall1" -> {
                val vExpr = call.args[0] as PtNumber
                val vExpr1 = call.args[1] as PtNumber
                // TODO assign regs
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall2" -> {
                val vExpr = call.args[0] as PtNumber
                val vExpr1 = call.args[1] as PtNumber
                val vExpr2 = call.args[2] as PtNumber
                // TODO assign regs
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            "syscall3" -> {
                val vExpr = call.args[0] as PtNumber
                val vExpr1 = call.args[1] as PtNumber
                val vExpr2 = call.args[2] as PtNumber
                val vExpr3 = call.args[3] as PtNumber
                // TODO assign regs
                chunk += VmCodeInstruction(Instruction(Opcode.SYSCALL, value=vExpr.number.toInt()))
            }
            else -> {
                TODO("builtinfunc ${call.name}")
            }
        }
        return chunk
    }
}
