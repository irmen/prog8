package prog8.codegen.intermediate

internal class RegisterPool {
    // reserve 0,1,2 for return values of subroutine calls and syscalls in IR assembly code
    private var firstFree: Int=3
    private var firstFreeFloat: Int=3

    fun peekNext() = firstFree
    fun peekNextFloat() = firstFreeFloat

    fun nextFree(): Int {
        val result = firstFree
        firstFree++
        return result
    }

    fun nextFreeFloat(): Int {
        val result = firstFreeFloat
        firstFreeFloat++
        return result
    }
}
