%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {

    sub start() {

        byte bb
        word ww
        float fl
        bb=-1
        c64scr.print_b(sgn(bb))
        c64.CHROUT('\n')
        bb=0
        c64scr.print_b(sgn(bb))
        c64.CHROUT('\n')
        bb=1
        c64scr.print_b(sgn(bb))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        ww=-1
        c64scr.print_b(sgn(ww))
        c64.CHROUT('\n')
        ww=0
        c64scr.print_b(sgn(ww))
        c64.CHROUT('\n')
        ww=1
        c64scr.print_b(sgn(ww))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        fl=-1.1
        c64scr.print_b(sgn(fl))
        c64.CHROUT('\n')
        fl=0.0
        c64scr.print_b(sgn(fl))
        c64.CHROUT('\n')
        fl=1.0
        c64scr.print_b(sgn(fl))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }

}
