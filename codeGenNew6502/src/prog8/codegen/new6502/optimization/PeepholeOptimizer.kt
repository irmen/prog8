package prog8.codegen.new6502.optimization

import prog8.intermediate.*

internal class PeepholeOptimizer(private val program: IRProgram) {

    fun optimize() {
        optimizeCalliToCall()
    }

    private fun optimizeCalliToCall() {
        for (block in program.blocks) {
            for (element in block.children) {
                when (element) {
                    is IRCodeChunk -> optimizeChunkCalli(element)
                    is IRSubroutine -> element.chunks.filterIsInstance<IRCodeChunk>().forEach { optimizeChunkCalli(it) }
                    else -> {}
                }
            }
        }
        optimizeChunkCalli(program.globalInits)
    }

    private fun optimizeChunkCalli(chunk: IRCodeChunk) {
        val instructions = chunk.instructions
        var i = 0
        while (i < instructions.size - 1) {
            val cur = instructions[i]
            val next = instructions[i + 1]
            val constImm = cur.immediate
            if (cur.opcode == Opcode.LOAD && cur.type == IRDataType.WORD && constImm != null
                && next.opcode == Opcode.CALLI && next.reg1 == cur.reg1) {
                val constAddr = constImm.toUInt()
                val callReplacement =
                    IRInstruction(Opcode.CALL, address = constAddr.toAddress(), fcallArgs = next.fcallArgs)
                instructions[i] = callReplacement
                instructions.removeAt(i + 1)
            }
            i++
        }
    }
}
