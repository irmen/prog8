%import textio
%zeropage basicsafe

main {

    sub start() {
;        cx16.rambank(4)
;        cx16.rambank(4)
;        cx16.rambank(4)
;        cx16.rambank(4)
;        cx16.rambank(4)
        uword yy = 12345
        ubyte xx
        xx = calc2(41, 12345)
        xx = calc2(41, 12345)
        xx = calc2(41, 12345)
        xx = calc2(41, 12345)
        txt.print_ub(xx)        ; must be 99
    }

    inline sub calc2(ubyte a1, uword a2) -> ubyte {
        uword thesum = a2 + a1
        return lsb(thesum+a2)
    }
}
