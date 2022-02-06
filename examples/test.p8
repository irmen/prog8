%import textio
%zeropage basicsafe

main {
    ubyte mainglobal1 = 10
    ubyte mainglobal2 = 20
    ubyte mainglobal3 = 30
    ubyte mainglobal4 = 40

    sub start() {
        ubyte startval1 = 100
        ubyte startval2 = 110
        ubyte startval3 = 120
        ubyte startval4 = 130

        txt.print_ub(mainglobal1)
        txt.nl()
        txt.print_ub(startval1)
        txt.nl()
        derp()
        derp()
        foobar()
        startval1++
        mainglobal1++
        start2()

        sub start2() {
            uword @shared startval1 = 2002
            ubyte[2] @shared barr
            uword[2] @shared warr
            uword[] @shared warr2 = [1,2]
        }
    }

    asmsub derp() {
    }

    sub foobar() {
        uword @shared startval1 = 2002
        uword @shared mainglobal1 = 2002
        txt.print("foobar\n")
    }
}
