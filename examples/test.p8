%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        bool @shared blerp = test(100)

        if test(100)
            goto skip
        txt.print("no0")

skip:
        if test(100) {
            txt.print("yes1")
            goto skip2
        }
        txt.print("no1")

skip2:
        if test(100)
            txt.print("yes2")
        else
            txt.print("no2")


        while test(100) {
            cx16.r0++
        }

        do {
            cx16.r0++
        } until test(100)
    }

    asmsub test(ubyte value @A) -> bool @Pz {
        %asm {{
            lda  #0
            rts
        }}
    }
}
