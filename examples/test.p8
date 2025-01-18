%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        const ubyte x = 88
        const ubyte z = 99

        x=20
        x,z = multi1()
;        ubyte p,q,r,s
;        p,q = multi1()
;        r,s = multi2()
;        ubyte a,b = multi1()
;        ubyte c,d = multi2()
;
;        txt.print_ub(p)
;        txt.spc()
;        txt.print_ub(q)
;        txt.spc()
;        txt.print_ub(r)
;        txt.spc()
;        txt.print_ub(s)
;        txt.nl()
;
;        txt.print_ub(a)
;        txt.spc()
;        txt.print_ub(b)
;        txt.spc()
;        txt.print_ub(c)
;        txt.spc()
;        txt.print_ub(d)
;        txt.nl()
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
