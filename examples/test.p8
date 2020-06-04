%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {
    sub start() {

        A += 50

        A += Y + 1
        A -= Y + 1
        A += Y - 1
        A -= Y - 1

        A += Y + 2
        A -= Y + 2
        A += Y - 2
        A -= Y - 2

;        ubyte ubb
;        byte bb
;        uword uww
;        word ww
;        word ww2
;
;        A = ubb*0
;        Y = ubb*1
;        A = ubb*2
;        Y = ubb*4
;        A = ubb*8
;        Y = ubb*16
;        A = ubb*32
;        Y = ubb*64
;        A = ubb*128
;        Y = ubb+ubb+ubb
;        A = ubb+ubb+ubb+ubb
;        ww = ww2+ww2
;        ww = ww2+ww2+ww2
;        ww = ww2+ww2+ww2+ww2

    }
}


