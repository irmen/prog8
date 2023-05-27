%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        uword[] @split split_uwords = [12345, 60000, 4096]
        word[] @split split_words = [-12345, -2222, 22222]
        uword[256] @split @shared split2
        word[256] @split @shared split3
        ubyte[200] @shared dummy
        uword[256] @split split_large = 1000 to 1255

        print_arrays()
        txt.nl()
        uword ww
        for ww in split_large {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.nl()
        txt.nl()


        ubyte xx=1
        split_uwords[1] = 0
        txt.print_uw(split_uwords[1])
        txt.spc()
        txt.print_w(split_words[1])
        txt.spc()
        split_words[1] = -9999
        txt.print_w(split_words[1])
        txt.spc()
        txt.print_uw(split_uwords[xx])
        txt.spc()
        txt.print_w(split_words[xx])
        txt.spc()
        split_words[1]=-1111
        txt.print_w(split_words[xx])
        txt.nl()
        txt.nl()

        sub print_arrays() {
            ubyte[200] @shared dummy2 = 22
            for cx16.r0 in split_uwords {
                txt.print_uw(cx16.r0)
                txt.spc()
            }
            txt.nl()

            for cx16.r0s in split_words {
                txt.print_w(cx16.r0s)
                txt.spc()
            }
            txt.nl()
        }

        ubyte idx
        for idx in 0 to len(split_uwords)-1 {
            cx16.r0 = split_uwords[idx]
            cx16.r1s = split_words[idx]
            txt.print_uw(cx16.r0)
            txt.spc()
            txt.print_w(cx16.r1s)
            txt.nl()
        }
        txt.nl()

        split_uwords[1] = 9999
        split_words[1] = -9999

        print_arrays()
        txt.nl()

        split_uwords[1]++
        split_words[1]++

        print_arrays()
        txt.nl()
        split_uwords[1] |= 4095
        split_words[1] |= 127

        print_arrays()
        txt.nl()
    }
}

