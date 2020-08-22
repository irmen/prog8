%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte a = 1
        ubyte b = 2
        ubyte c = 20
        ubyte d = 4

        a = c % 6

        c64scr.print_ub(a)
    }
}

