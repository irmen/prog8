%import floats
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword[] split_uwords = [12345, 60000, 4096]
        word[] split_words = [12345, -6000, 4096]
        float[] floats = [1.1,2.2,3.3]
        reverse(split_uwords)
        reverse(split_words)
        reverse(floats)
        uword ww
        for ww in split_uwords {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()
        word sw
        for sw in split_words {
            txt.print_w(sw)
            txt.spc()
        }
        txt.nl()
        ubyte ix
        for ix in 0 to len(floats)-1 {
            floats.print_f(floats[ix])
            txt.spc()
        }
    }
}

