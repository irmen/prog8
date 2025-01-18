%import textio
%option no_sysinit
%zeropage basicsafe
%import math


main {
    sub start() {
        ubyte x = math.rnd()
        ubyte a,b = multi1()
        ubyte c,d = 99

        x++
        txt.print_ub(a)
        txt.spc()
        txt.print_ub(b)
        txt.spc()
        txt.print_ub(c)
        txt.spc()
        txt.print_ub(d)
        txt.nl()
    }

    sub single() -> ubyte {
        return cx16.r0L+cx16.r1L
    }
    asmsub multi1() -> ubyte @A, ubyte @Y {
        %asm {{
            lda  #1
            ldy  #2
            rts
        }}
    }

    sub multi2() -> ubyte, ubyte {
        cx16.r0++
        return 3,4
    }
}
