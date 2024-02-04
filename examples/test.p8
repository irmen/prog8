%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool[3] barr
        bool @shared bb

        barr[1] = barr[0] and barr[2]
        barr[1] = barr[0] or barr[2]
        barr[1] = barr[0] xor barr[2]
        barr[1] = not barr[0]
        barr[1] = not barr[1]
        barr[1] = barr[1] and bb
        barr[1] = barr[1] or bb
        barr[1] = barr[1] xor bb

        bb = bb and barr[1]
        bb = bb or barr[1]
        bb = bb xor barr[1]
        bb = not bb
    }
}
