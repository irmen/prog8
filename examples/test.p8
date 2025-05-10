%import textio

main {
    sub start() {
        ^^uword ptr = 2000

        ptr^^ <<= 2
        ptr^^ >>= 3

        pokew(2000, 1111)

        cx16.r0 ^= $ffff

        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ += 5
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ -= 9
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ *= 3
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ /= 3
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ |= $7f0f
        txt.print_uwhex(ptr^^,true)
        txt.nl()
        ptr^^ &= $f0f0
        txt.print_uwhex(ptr^^,true)
        txt.nl()
        ptr^^ ^= $ffff
        txt.print_uwhex(ptr^^,true)
        txt.nl()

;        const uword buffer = $2000
;        uword @shared addr = &buffer[2]
;
;        const ubyte width = 100
;        ubyte @shared i
;        ubyte @shared j
;        uword @shared addr2 = &buffer[i * width + j]
;        txt.print_uw(addr)
    }
}
