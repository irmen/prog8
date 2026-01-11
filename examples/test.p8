%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ^^ubyte ubptr1 = 4000
        ^^ubyte ubptr2 = 4001
        ^^uword uwptr1 = 5000
        ^^uword uwptr2 = 5002
        ^^long lptr1 = 6000
        ^^long lptr2 = 6004


        @(4000) = 11
        @(4001) = 22
        pokew(5000, 1111)
        pokew(5002, 2222)
        pokel(6000, 11111111)
        pokel(6004, 22222222)

        txt.print_ub(ubptr2^^)
        txt.spc()
        txt.print_uw(uwptr2^^)
        txt.spc()
        txt.print_l(lptr2^^)
        txt.nl()

        poke(ubptr2, peek(ubptr1))
        ubptr2^^ = ubptr1^^

        pokew(uwptr2, peekw(uwptr1))        ; TODO rewrite as  uwptr2^^ = uwptr1^^
        uwptr2^^ = uwptr1^^

        pokel(lptr2, peekl(lptr1))          ; TODO rewrite as  lptr2^^ = lptr1^^
        lptr2^^ = lptr1^^

        txt.print_ub(ubptr2^^)
        txt.spc()
        txt.print_uw(uwptr2^^)
        txt.spc()
        txt.print_l(lptr2^^)
        txt.nl()
    }
}
