%import c64utils

~ main {

    sub start()  {

        ubyte   a=200
        ubyte   b=33
        ubyte   c

        byte   ab=100
        byte   bb=-6
        byte   cb

        uword   wa=50000
        uword   wb=999
        uword   wc
        word   wab=30000
        word   wbb=-99
        word   wcb

        c=a//b
        c64scr.print_ub(c)
        c64.CHROUT('\n')
        a=155
        b=11
        c=a//b
        c64scr.print_ub(c)
        c64.CHROUT('\n')
        cb=ab//bb
        c64scr.print_b(cb)
        c64.CHROUT('\n')
        ab=-100
        bb=6
        cb=ab//bb
        c64scr.print_b(cb)
        c64.CHROUT('\n')
        ab=-100
        bb=-6
        cb=ab//bb
        c64scr.print_b(cb)
        c64.CHROUT('\n')
        ab=100
        bb=6
        cb=ab//bb
        c64scr.print_b(cb)
        c64.CHROUT('\n')
        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }

}
