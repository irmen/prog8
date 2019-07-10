%import c64utils
%zeropage basicsafe
%import c64flt

~ main {

    sub start() {
        ubyte xx=99
        ubyte yy=99
        ubyte aa=99

        rsave()
        c64flt.GETADR()
        rrestore()
        c64scr.print_ub(yy)
        c64.CHROUT(',')
        c64scr.print_ub(aa)
        c64.CHROUT('\n')

        rsave()
        c64utils.ubyte2hex($9c)
        rrestore()
        c64scr.print_ub(aa)
        c64.CHROUT(',')
        c64scr.print_ub(yy)
        c64.CHROUT('\n')

;        rsave()
;        A,Y=c64flt.FOUT()       ; @ todo accept A,Y  for AY response
;        rrestore()
    }

}
