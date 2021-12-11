%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared yy
        yy = foobar()
    }
    sub foobar() -> ubyte {
        if_mi {
            foobar2()
            foobar2()
            return true
        }
        return 22
    }
    sub foobar2() {
        main.start.yy++
    }
}
