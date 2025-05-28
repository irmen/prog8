%import floats
%import textio
%zeropage basicsafe

main {
    bool[10] barray
    uword[10] @nosplit warrayns
    uword[10] warray
    float[10] farray

    sub start() {
        dump()

        ; ALL OK
        barray[2] = true
        warrayns[2] = 1234
        warray[2] = 5678
        farray[2] = 3.1415
        dump()

        ; ALL OK
        cx16.r0L=2
        barray[cx16.r0L] = false
        warrayns[cx16.r0L] = 0
        warray[cx16.r0L] = 0
        farray[cx16.r0L] = 0
        dump()

        ; ALL OK
        cx16.r0L=2
        barray[cx16.r0L] = true
        warrayns[cx16.r0L] = 1234
        warray[cx16.r0L] = 5678
        farray[cx16.r0L] = 3.1415
        dump()

        ; ALL OK
        barray[2] = false
        warrayns[2] = 0
        warray[2] = 0
        farray[2] = 0.0
        dump()

        sub dump() {
            txt.print_bool(barray[2])
            txt.spc()
            txt.print_uw(warrayns[2])
            txt.spc()
            txt.print_uw(warray[2])
            txt.spc()
            txt.print_f(farray[2])
            txt.nl()
        }
    }
}

