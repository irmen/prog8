package prog8.codegen.intermediate

import prog8.code.core.AssemblyError

internal class RegisterPool {
    private var firstFree: Int=3        // integer registers 0,1,2 are reserved
    private var firstFreeFloat: Int=0

    fun peekNext() = firstFree
    fun peekNextFloat() = firstFreeFloat

    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        if(firstFree>65535)
            throw AssemblyError("out of virtual registers (int)")
        return result
    }

    fun nextFreeFloat(): Int {
        val result = firstFreeFloat
        firstFreeFloat++
        if(firstFreeFloat>65535)
            throw AssemblyError("out of virtual registers (fp)")
        return result
    }
}
