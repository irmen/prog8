%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] envelope_attacks = 99


        ; signed and unsigned word:
        ; >
        ; >=
        ; <
        ; <=


        ; expect nope nope yep yep yep
        cx16.r0 = $ea30
        cx16.r2 = $ea31
        if (cx16.r0 > cx16.r2) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0 = $ea31
        cx16.r2 = $ea31
        if (cx16.r0 > cx16.r2) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0 = $ea32
        cx16.r2 = $ea31
        if (cx16.r0 > cx16.r2) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0 = $ee30
        cx16.r2 = $ea31
        if (cx16.r0 > cx16.r2) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }

        cx16.r0 = $ffff
        cx16.r2 = $ee31
        if (cx16.r0 > cx16.r2) or (envelope_attacks[cx16.r1L]==0) {
            txt.print("\nyep\n")
        } else {
            txt.print("\nnope\n")
        }
    }
}

