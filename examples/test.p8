%import c64lib
%import c64utils
%zeropage dontuse


main {
    sub start() {
        ubyte ub
        byte bb
        word ww
        uword uw
        uword auw
        word aww
        byte ab
        ubyte aub

        ; TODO optimize all of these:
        ab = bb+bb      ; TODO bb * 2?  (bb<<1)
        ab = bb+bb+bb
        aww = ww+ww
        aww = ww+ww+ww

        aub = ub+ub
        aub = ub+ub+ub
        auw = uw+uw
        auw = uw+uw+uw


        A = A+A
        Y=Y+Y+Y

    }
}


