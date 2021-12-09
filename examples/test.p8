%import textio
%zeropage basicsafe

main {
    sub start() {
        ; TODO other variants of this const folding
        word llw = 300
        cx16.r0s = 9 * 2 * 10 * llw
        cx16.r1s = llw * 9 * 2 * 10
        cx16.r2s = llw * 9 / 4
        cx16.r3s = llw / 20 / 5
        cx16.r4s = llw / 20 * 5
    }
}
