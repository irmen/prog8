%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {
        float @shared fv,fv2,fv3,fv4 = 1.11111
        uword @shared @nozp uw,uw2,uw3,uw4 = 1111
        ubyte @shared @nozp ub,ub2,ub3,ub4 = 111


        txt.print_ub(peek(&ub2 + 2))
        txt.spc()
        cx16.r0++
        poke(&ub2+2, 222)
        cx16.r0--
        txt.print_ub(peek(&ub2 + 2))
        txt.spc()
        txt.print_ub(pokemon(&ub2 + 2, 99))
        txt.spc()
        txt.print_ub(peek(&ub2 + 2))
        txt.nl()
        txt.nl()


        txt.print_uw(peekw(&uw2 + 4))
        txt.spc()
        cx16.r0++
        pokew(&uw2+ 4, 9999)
        cx16.r0--
        txt.print_uw(peekw(&uw2 + 4))
        txt.nl()
        txt.nl()

        txt.print_f(peekf(&fv + sizeof(float)))
        txt.spc()
        cx16.r0++
        pokef(&fv+ sizeof(float), 2.22222)
        cx16.r0--
        txt.print_f(peekf(&fv + sizeof(float)))
        txt.nl()

    }
}
