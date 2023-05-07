%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] envelope_attacks = 99

        ; expect nope yep yep nope yep yep
        cx16.r0L = 8
        cx16.r2L = 9
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0L = 9
        cx16.r2L = 9
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0L = 10
        cx16.r2L = 9
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0L = 0
        cx16.r2L = 9
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0L = 9
        cx16.r2L = 0
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0L = 255
        cx16.r2L = 9
        if (cx16.r0L >= cx16.r2L) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }
    }
}

