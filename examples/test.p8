%import textio
%import palette
%import syslib
%zeropage basicsafe

main {


    sub start() {
        uword screencolorRGB
        uword drawcolorRGB
        ubyte ll
        ubyte hh

        cx16.vpoke(1, mkword(hh, ll), lsb(screencolorRGB))


;        ubyte value
;        ubyte bb1
;
;        value = cx16.vpeek(lsb(cx16.r0), mkword(value, bb1))
;        value = cx16.vpeek(lsb(cx16.r0), mkword(value, bb1))
;
;        ubyte lx = lsb(cx16.r0)
;        value = cx16.vpeek(lx, mkword(value, bb1))
;        value = cx16.vpeek(lx, mkword(value, bb1))
    }
}
