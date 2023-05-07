%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] envelope_attacks = 99
        ubyte @shared xx = 4
        ubyte yy
        ; 110
        xx = 4
        yy = 10
        xx = xx <= yy
        if xx
            txt.chrout('1')
        else
            txt.chrout('0')

        xx = 4
        yy = 4
        xx = xx <= yy
        if xx
            txt.chrout('1')
        else
            txt.chrout('0')

        xx = 4
        yy = 2
        xx = xx <= yy
        if xx
            txt.chrout('1')
        else
            txt.chrout('0')
    }
}

