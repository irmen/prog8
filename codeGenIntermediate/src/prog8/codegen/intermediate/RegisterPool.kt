package prog8.codegen.intermediate

import prog8.code.core.AssemblyError

internal class RegisterPool {
    // reserve first 3 registers a subroutine return registers   TODO is this still needed? how does returning values go in IR? Double types?
    private var firstFree: Int=3
    private var firstFreeFloat: Int=3


    // everything from 99000 onwards is reserved for special purposes:
    // 99000 - 99099 : WORD registers for syscall arguments and response value(s)
    // 99100 - 99199 : BYTE registers for syscall arguments and response value(s)


    fun nextFree(): Int {
        if(firstFree>=99000)
            throw AssemblyError("register pool depleted")
        val result = firstFree
        firstFree++
        return result
    }

    fun nextFreeFloat(): Int {
        if(firstFreeFloat>=99000)
            throw AssemblyError("float register pool depleted")
        val result = firstFreeFloat
        firstFreeFloat++
        return result
    }
}
