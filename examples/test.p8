%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] envelope_attacks = 99

        ; signed word:
        ; >


        ; expect yep nope nope nope yep nope
        cx16.r0s = -1000
        cx16.r2s = -999
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0s = -999
        cx16.r2s = -999
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0s = -998
        cx16.r2s = -999
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0s = 0
        cx16.r2s = -999
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0s = -999
        cx16.r2s = 0
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0s = $7fff
        cx16.r2s = $7eff
        if (cx16.r0s < cx16.r2s) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }
    }
}

