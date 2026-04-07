package prog8.codegen.intermediate

import prog8.code.core.AssemblyError
import prog8.intermediate.IRDataType
import prog8.intermediate.RegisterNum

internal class RegisterPool {
    // everything from 99000 onwards is reserved for special purposes:
    // 99000 - 99099 : WORD registers for syscall arguments and response value(s)
    // 99100 - 99199 : BYTE registers for syscall arguments and response value(s)
    // 99200 - 99299 : LONG registers for syscall arguments and response value(s)

    private var nextRegister: Int=1
    private val registerTypes: MutableMap<RegisterNum, IRDataType> = mutableMapOf()

    fun getTypes(): Map<RegisterNum, IRDataType> = registerTypes

    init {
        for(i in 99000..99099)
            registerTypes[RegisterNum(i)] = IRDataType.WORD
        for(i in 99100..99199)
            registerTypes[RegisterNum(i)] = IRDataType.BYTE
        for(i in 99200..99299)
            registerTypes[RegisterNum(i)] = IRDataType.LONG
    }

    fun next(type: IRDataType): Int {
        if(nextRegister>=99000)
            throw AssemblyError("register pool depleted")
        val result = nextRegister
        nextRegister++
        registerTypes[RegisterNum(result)] = type
        return result
    }
}
