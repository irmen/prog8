%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        uword[4] words1 = [1,2,3,4]
        uword[4] words2 = [99,88,77,66]

        for cx16.r0 in words1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        sys.memcopy(words2, words1, sizeof(words1))
        for cx16.r0 in words1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
        sys.memcopy([2222,3333,4444,5555], words1, sizeof(words1))
        for cx16.r0 in words1 {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
    }
}
