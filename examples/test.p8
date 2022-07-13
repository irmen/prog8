%import textio
%zeropage basicsafe


main {
    sub start() {
        byte tx = 1
        uword @shared zzzz= $2000 + (tx as ubyte)
        txt.print_uwhex(zzzz,true)
    }
}

