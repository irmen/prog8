%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    float[5] flarray
    byte[5] barray
    uword[5] uwarray

    sub start() {
        c64scr.print_uw(flarray)
        c64.CHROUT('=')
        c64flt.print_f(flarray[0])
        c64.CHROUT('\n')
        c64scr.print_uw(barray)
        c64.CHROUT('=')
        c64scr.print_b(barray[0])
        c64.CHROUT('\n')
        c64scr.print_uw(uwarray)
        c64.CHROUT('=')
        c64scr.print_uw(uwarray[0])
        c64.CHROUT('\n')
    }

}
