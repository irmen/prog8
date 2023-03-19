%import math
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
;        ubyte[255] BC
;        bool[255] DX

        if math.rnd() & 22 {
            cx16.r0L++
        }
;        BC[2] = math.rnd() & 15
;        BC[3] = math.rnd() & 15
;        BC[4] = math.rnd() & 15
        ;DX[2] = math.rnd() & 1
    }
}
