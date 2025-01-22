%import textio
%option no_sysinit
%zeropage basicsafe
%import math


main {

    ubyte @nozp @shared staticvar=51

    sub start() {
        ubyte a,b,c,d = multi4()        ; TODO FIX IR CODEGEN

        txt.print_ub(a)
        txt.spc()
        txt.print_ub(b)
        txt.spc()
        txt.print_ub(c)
        txt.spc()
        txt.print_ub(d)
        txt.nl()
    }

;    sub single() -> ubyte {
;        return cx16.r0L+cx16.r1L
;    }
;    asmsub multi1() -> ubyte @A, ubyte @Y {
;        %asm {{
;            lda  #1
;            ldy  #2
;            rts
;        }}
;    }
;
;    sub multi2() -> ubyte, ubyte {
;        cx16.r0++
;        return 3,4
;    }

    sub multi4() -> ubyte, ubyte, ubyte, ubyte {
        cx16.r0++
        return 3,4,5,6
    }
}
