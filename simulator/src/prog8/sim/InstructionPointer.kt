package prog8.sim

import prog8.compilerinterface.intermediate.PtNode

class InstructionPointer(var instructions: List<PtNode>, start: Int=0) {
    var currentIdx = start
    val current: PtNode
        get() {
            if(currentIdx<instructions.size)
                return instructions[currentIdx]
            else
                throw IllegalArgumentException("expected Return statement at end of statement list")
        }

    init {
        require(instructions.isNotEmpty())
    }

    operator fun inc(): InstructionPointer {
        currentIdx++
        return this
    }
}