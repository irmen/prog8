package prog8.codegen.intermediate

import prog8.code.core.AssemblyError
import prog8.intermediate.IRDataType

internal class RegisterPool {
    private var nextRegister: Int=1
    private val registerTypes: MutableMap<Int, IRDataType> = mutableMapOf()

    // everything from 99000 onwards is reserved for special purposes:
    // 99000 - 99099 : WORD registers for syscall arguments and response value(s)
    // 99100 - 99199 : BYTE registers for syscall arguments and response value(s)


    fun next(): Int {
        if(nextRegister>=99000)
            throw AssemblyError("register pool depleted")
        val result = nextRegister
        // registerTypes[result] = type   // TODO: make callers of this one supply the integer type they want as well and register it here
        nextRegister++
        return result
    }

    fun next(type: IRDataType): Int {
        if(nextRegister>=99000)
            throw AssemblyError("register pool depleted")
        val result = nextRegister
        nextRegister++
        registerTypes[result] = type
        return result
    }
}
