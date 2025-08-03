%import textio
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    ^^bool  bptr = 30000
    ^^bool  bptr2
    ^^ubyte ubptr = 30100
    ^^ubyte ubptr2
    ^^uword wptr = 30200
    ^^uword wptr2
    ^^float fptr = 30300
    ^^float fptr2

    bool @shared bv = true
    ubyte @shared ubv = 123
    uword @shared uwv = 44444
    float @shared fv = 3.1415927


    sub start() {
        bptr^^ = bv
        ubptr^^ = ubv
        wptr^^ = uwv
        fptr^^ = fv

        bv = bptr^^
        ubv = ubptr^^
        uwv = wptr^^
        fv = fptr^^

        bv = bptr^^ or bv
        ubv = ubptr^^ + 1
        uwv = wptr^^ + 1
        fv = fptr^^ + 1.1

        bptr = fptr as ^^bool

        bptr^^ = bptr^^ xor bv
        ubptr^^ += 10
        wptr^^ += 1000
        fptr^^ += 1.1

        bptr2 = bptr
        ubptr2 = ubptr
        wptr2 = wptr
        fptr2 = fptr

        txt.print_bool(bptr2^^)
        txt.spc()
        txt.print_ub(ubptr2^^)
        txt.spc()
        txt.print_uw(wptr2^^)
        txt.spc()
        txt.print_f(fptr2^^)
        txt.nl()
    }
}
