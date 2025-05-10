%import textio

main {
    sub start() {
        ^^uword ptr = 2000
        pokew(2000, 1111)

        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ += 5
        txt.print_uw(ptr^^)
        txt.nl()
        ptr^^ -= 9
        txt.print_uw(ptr^^)
        txt.nl()
;        ptr^^ *= 5
;        txt.print_uw(ptr^^)
;        txt.nl()

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
