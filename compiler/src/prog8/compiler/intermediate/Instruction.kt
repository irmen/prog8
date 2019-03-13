package prog8.compiler.intermediate

import prog8.stackvm.Syscall

open class Instruction(val opcode: Opcode,
                       val arg: Value? = null,
                       val arg2: Value? = null,
                       val callLabel: String? = null,
                       val callLabel2: String? = null)
{
    var branchAddress: Int? = null

    override fun toString(): String {
        val argStr = arg?.toString() ?: ""
        val result =
                when {
                    opcode==Opcode.LINE -> "_line  $callLabel"
                    opcode==Opcode.INLINE_ASSEMBLY -> {
                        // inline assembly is not written out (it can't be processed as intermediate language)
                        // instead, it is converted into a system call that can be intercepted by the vm
                        if(callLabel!=null)
                            "syscall  SYSASM.$callLabel\n    return"
                        else
                            "inline_assembly"
                    }
                    opcode==Opcode.SYSCALL -> {
                        val syscall = Syscall.values().find { it.callNr==arg!!.numericValue() }
                        "syscall  $syscall"
                    }
                    opcode in opcodesWithVarArgument -> {
                        // opcodes that manipulate a variable
                        "${opcode.name.toLowerCase()}  ${callLabel?:""}  ${callLabel2?:""}".trimEnd()
                    }
                    callLabel==null -> "${opcode.name.toLowerCase()}  $argStr"
                    else -> "${opcode.name.toLowerCase()}  $callLabel  $argStr"
                }
                .trimEnd()

        return "    $result"
    }
}

class LabelInstr(val name: String, val asmProc: Boolean) : Instruction(Opcode.NOP, null, null) {
    override fun toString(): String {
        return "\n$name:"
    }
}
