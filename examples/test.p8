%import textio

main {
    sub start() {
        ubyte xx = 200
        ubyte yy = 100
        uword @shared cc

        ubyte[200] array

        cc=array[xx+yy+10]

        cc = xx-yy>10

        uword qq = 100
        cmp(qq,xx)

        simple(xx+yy)
        void routine(xx+yy, yy+99, 99, true)
        uword @shared zz = mkword(xx+yy,yy+99)
        zz = routine(1000+xx+yy, yy+99, 55, true)


        txt.print("1300 199 55 1 ?:\n")
        txt.print_uw(r_arg)
        txt.spc()
        txt.print_ub(r_arg2)
        txt.spc()
        txt.print_ub(r_arg3)
        txt.spc()
        txt.print_ub(r_arg4)
        txt.spc()

        memory.mem()
        repeat {
        }
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
            lda  #0
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
