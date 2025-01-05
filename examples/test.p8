%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared bb0, bb1, bb2, bb3, bb4
        uword @shared ww0, ww1, ww2, ww3, ww4

        bb4 = 100
        ww4 = 100

        bb0 = min(bb1+10, 100)
        bb0 = min(100, bb1+10)
;        bb0 = min(bb1, bb4)
;        bb2 = min(bb1+bb2, bb3+bb4)
;
;        bb0 = max(bb1, 100)
;        bb0 = max(100, bb1)
;        bb0 = max(bb1, bb4)
;        bb2 = max(bb1+bb2, bb3+bb4)
;
;        ww0 = min(ww1, 100)
;        ww0 = min(100, ww1)
;        ww0 = min(ww1, ww4)
;        ww2 = min(ww1+ww2, ww3+ww4)
;
;        ww0 = max(ww1, 100)
;        ww0 = max(100, ww1)
;        ww0 = max(ww1, ww4)
;        ww2 = max(ww1+ww2, ww3+ww4)
    }
}
