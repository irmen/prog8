%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

; DIRTY tests

main {
    uword @shared @dirty globw
    uword @shared globwi = 4444
    float @shared @dirty globf
    float @shared globfi = 4
    ubyte[5] @shared @dirty globarr1
    ubyte[] @shared globarr2 = [11,22,33,44,55]

    sub start() {
        testdirty()
        txt.nl()
        testdirty()
        txt.nl()
    }

    sub testdirty() {
        uword @shared @dirty locw
        uword @shared locwi = 4444
        float @shared @dirty locf
        float @shared locfi = 4.0
        ubyte[5] @shared @dirty locarr1
        ubyte[] @shared locarr2 = [11,22,33,44,55]

        txt.print("globals: ")
        txt.print_uw(globw)
        txt.spc()
        floats.print(globf)
        txt.print("  with init: ")
        txt.print_uw(globwi)
        txt.spc()
        floats.print(globfi)
        txt.print("  arrays: ")
        txt.print_ub(globarr1[2])
        txt.spc()
        txt.print_ub(globarr2[2])
        txt.print("\nlocals:  ")
        txt.print_uw(locw)
        txt.spc()
        floats.print(locf)
        txt.print("  with init: ")
        txt.print_uw(locwi)
        txt.spc()
        floats.print(locfi)
        txt.print("  arrays: ")
        txt.print_ub(locarr1[2])
        txt.spc()
        txt.print_ub(locarr2[2])
        txt.nl()


        globw++
        globwi++
        globf++
        globfi++
        globarr1[2]++
        globarr2[2]++
        locw++
        locwi++
        locf++
        locfi++
        locarr1[2]++
        locarr2[2]++
    }
}
