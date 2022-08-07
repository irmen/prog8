%import textio
%zeropage basicsafe

main {
    romsub $ea31 = foobar(uword derp @AY) -> ubyte @A
    romsub $ea33 = foobar2() -> ubyte @A
    romsub $ea33 = foobar3()

    sub subroutine(ubyte subroutineArg) -> ubyte {
        return subroutineArg+22
    }

    asmsub asmsubje(uword arg @AY) -> ubyte @A {
        %asm {{
            rts
            return
        }}
    }

    sub start() {
        ubyte @shared qq0 = subroutine(11)
        ubyte @shared qq1 = foobar(12345)
        ubyte @shared qq2 = foobar2()
        foobar3()

        txt.print_ub(qq0)
    }
}
