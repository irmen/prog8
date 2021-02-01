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

        cx16.vpoke(lsb(cx16.r1), cx16.r0, cbits4)       ; TODO r0 is alreay in r0, avoid bogus assignment here
        cbits4 &= gfx2.plot.mask4c[lower2_x_bits]       ; TODO why lda..and instead of  and mask,y?
        cbits4 |= colorbits[lower2_x_bits]              ; TODO why lda..ora instead of  ora mask,y?

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
