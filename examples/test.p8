%import textio
%zeropage basicsafe


main {
    sub calc(ubyte x, ubyte y) -> uword {
        repeat x+y {
            x++
        }
        return x as uword * y
    }
    sub start()  {
        txt.print_uw(calc(22, 33))
    }
}
