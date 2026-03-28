%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte a,b = multi()
        txt.print_ub(a)
        txt.nl()
        txt.print_ub(b)
        txt.nl()
    }

    sub multi() -> ubyte, ubyte {
        cx16.r0++
        return multi2(99)
    }

    sub multi2(ubyte x) -> ubyte, ubyte {
        x++
        return 42, 99
    }
}
