%import floats
%import textio

%option no_sysinit
%zeropage basicsafe

main {

    sub arrayinit_with_multiplier() {
        str name = "xyz" * 3
        bool[3] boolarray   = [true] * 3
        ubyte[3] bytearray  = [42] * 3
        uword[3] wordarray  = [5555] * 3
        float[3] floatarray = [123.45] * 3

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
    }

    sub arrayinit_with_range() {
        ubyte[3] bytearray2 = 10 to 12
        uword[3] wordarray2 = 5000 to 5002
        float[3] floatarray2 = 100 to 102

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
    }

    sub arrayassign() {
        bool[4] boolarray3
        ubyte[4] bytearray3
        uword[4] wordarray3
        float[4] floatarray3

        boolarray3 = [true] *4
        bytearray3 = [42]*4
        wordarray3 = [999]*4
        wordarray3 = [&bytearray3]*4
        wordarray3 = [bytearray3]*4
        floatarray3 = [99.77]*4

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

    sub start() {
        arrayinit_with_multiplier()
        arrayinit_with_range()
        arrayassign()
    }
}
