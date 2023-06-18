%import textio
%zeropage basicsafe

main {

    sub start() {
        txt.print_ub(danglingelse(32))
        txt.spc()
        txt.print_ub(danglingelse(99))
        txt.spc()
        txt.print_ub(danglingelse(1))
        txt.spc()
        txt.print_ub(danglingelse(100))
        txt.nl()
        txt.print_ub(danglingelse2(32))
        txt.spc()
        txt.print_ub(danglingelse2(99))
        txt.spc()
        txt.print_ub(danglingelse2(1))
        txt.spc()
        txt.print_ub(danglingelse2(100))
        txt.nl()
    }

    sub danglingelse(ubyte bb) -> ubyte {
        if bb==32
            return 32
        else if bb==99
            return 99
        else
            return 0
    }

    sub danglingelse2(ubyte bb) -> ubyte {
        if bb==32
            return 32
        if bb==99
            return 99
        return 0
    }
}
