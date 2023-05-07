%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] envelope_attacks = 99


        ; signed and unsigned word:
        ; ==
        ; !=
        ; >
        ; >=
        ; <
        ; <=


        ; expect yep yep nope nope nope
        cx16.r0sL = -10
        cx16.r2sL = -9
        if (cx16.r0sL <= cx16.r2sL) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0sL = -9
        cx16.r2sL = -9
        if (cx16.r0sL <= cx16.r2sL) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0sL = -8
        cx16.r2sL = -9
        if (cx16.r0sL <= cx16.r2sL) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0sL = 0
        cx16.r2sL = -9
        if (cx16.r0sL <= cx16.r2sL) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0sL = 10
        cx16.r2sL = 1
        if (cx16.r0sL <= cx16.r2sL) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }
    }
}

