%import floats
%import textio

%option no_sysinit
%zeropage basicsafe

main {

    str name = "xyz" * 3
    bool[3] boolarray   = true
    ubyte[3] bytearray  = 52
    uword[3] wordarray  = 5544
    float[3] floatarray = 123.45

    ubyte[3] bytearray2 = 10 to 12
    uword[3] wordarray2 = 5540 to 5542
    float[3] floatarray2 = 123 to 125

    bool[3] boolarray3
    ubyte[3] bytearray3
    uword[3] wordarray3
    float[3] floatarray3

    sub start() {
        txt.print(name)
        txt.nl()
        for cx16.r1L in 0 to 2 {
            txt.print_bool(boolarray[cx16.r1L])
            txt.spc()
            txt.print_ub(bytearray[cx16.r1L])
            txt.spc()
            txt.print_uw(wordarray[cx16.r1L])
            txt.spc()
            floats.print(floatarray[cx16.r1L])
            txt.nl()
        }
        txt.nl()
        txt.nl()

        for cx16.r1L in 0 to 2 {
            txt.print_ub(bytearray2[cx16.r1L])
            txt.spc()
            txt.print_uw(wordarray2[cx16.r1L])
            txt.spc()
            floats.print(floatarray2[cx16.r1L])
            txt.nl()
        }
        txt.nl()
        txt.nl()

        for cx16.r1L in 0 to 2 {
            txt.print_bool(boolarray3[cx16.r1L])
            txt.spc()
            txt.print_ub(bytearray3[cx16.r1L])
            txt.spc()
            txt.print_uw(wordarray3[cx16.r1L])
            txt.spc()
            floats.print(floatarray3[cx16.r1L])
            txt.nl()
        }
        txt.nl()
    }
}
