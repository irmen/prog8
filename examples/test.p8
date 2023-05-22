%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        uword[] @split split_uwords = [12345, 60000, 4096]
        word[] @split split_words = [-12345, -2222, 22222]
        uword[256] @split @shared split2
        word[256] @split @shared split3


        print_arrays()

        sub print_arrays() {
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
        for idx in 0 to 2 {
            cx16.r0 = split_uwords[idx]
            cx16.r1s = split_words[idx]
            txt.print_uw(cx16.r0)
            txt.spc()
            txt.print_w(cx16.r1s)
            txt.nl()
        }

        split_uwords[1] = 9999
        split_words[1] = -9999

        print_arrays()

        split_uwords[1]++
        split_words[1]--

        print_arrays()
    }
}

