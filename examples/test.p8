
main {
    sub start() {
        ubyte xx = 10
        ubyte yy = 10

        simple(xx+yy)
        void routine(xx+yy, yy+99, 99, true)
        uword @shared zz = mkword(xx+yy,yy+99)
        zz = routine(xx+yy, yy+99, 99, true)
        memory.mem()
    }

    uword @shared r_arg
    ubyte @shared r_arg2
    ubyte @shared r_arg3
    ubyte @shared r_arg4

    asmsub simple(ubyte arg @A) {
        %asm {{
            rts
        }}
    }

    asmsub routine(uword arg @AY, ubyte arg2 @X, ubyte arg3 @R0, ubyte arg4 @Pc) -> ubyte @A {
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
            lda  #99
            rts
        }}
    }
}

memory {
    sub mem() {
        %asm {{
            nop
        }}
    }
}
