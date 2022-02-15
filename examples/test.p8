%import textio

main {
    sub start() {
        ubyte xx = 10
        ubyte yy = 10

        routine(xx+yy, yy+99, 99, true)

    }

    uword @shared r_arg
    ubyte @shared r_arg2
    ubyte @shared r_arg3
    ubyte @shared r_arg4

    asmsub routine(uword arg @AY, ubyte arg2 @X, ubyte arg3 @R0, ubyte arg4 @Pc) {
        %asm {{
            pha
            adc  #0
            sta  r_arg4
            pla
            sta  r_arg
            sty  r_arg+1
            stx  r_arg2
            lda  cx16.r0
            sta  r_arg3
            rts
        }}
    }
}
