%import c64utils
%option enable_floats


~ main {

    sub start() {
        ubyte i=0


        repeat {
            c64scr.print_ub(X)
            c64.CHROUT('\n')
            ubyte ubx = fastsin8(i) as ubyte
            c64scr.print_ub(X)
            c64.CHROUT('\n')
            ;c64scr.print_ub(X)
            ;byte y = fastcos8(i)
            c64scr.print_ub(ubx)
            ;c64.CHROUT(',')
            ;c64scr.print_b(y)
            c64.CHROUT('\n')
            i++
        } until i==0
    }


asmsub fastsin8(ubyte angle8 @ Y) -> clobbers() -> (byte @ A)  {
    %asm {{
        lda  sinecos8hi,y
        ;sta  prog8_lib.ESTACK_LO,x
        ;dex
        rts
    }}
}

asmsub fastcos8(ubyte angle8 @ Y) -> clobbers() -> (byte @ A)  {
    %asm {{
        lda  sinecos8hi+64,y
        rts
    }}
}
asmsub fastsin16(ubyte angle8 @ Y) -> clobbers() -> (word @ AY)  {
    %asm {{
        lda  sinecos8lo,y
        pha
        lda  sinecos8hi,y
        tay
        pla
        rts
    }}
}
asmsub fastcos16(ubyte angle8 @ Y) -> clobbers() -> (word @ AY)  {
    %asm {{
        lda  sinecos8lo+64,y
        pha
        lda  sinecos8hi+64,y
        tay
        pla
        rts
    }}
}
    %asm {{
_  :=  32767.5 * sin(range(256+64) * rad(360.0/256.0))
sinecos8lo     .byte <_
sinecos8hi     .byte >_

    }}
}
