%zeropage basicsafe

main {

    sub pget(uword x, uword y) -> ubyte {
        return lsb(x+y)
    }

    sub start() {

        word xx
        word x2
        word yy

        if xx <= x2 and pget(xx as uword, yy as uword) == cx16.r11L
            xx++

;        if xx <= x2
;            if pget(xx as uword, yy as uword) == cx16.r11L
;                xx++
    }
}
