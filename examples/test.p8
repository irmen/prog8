%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        ubyte @shared bb = 255
        txt.print_w((bb as byte) as word)       ; should print -1 !
        txt.nl()
        txt.print_w((bb as word))       ; should print 255 !
        txt.nl()
        txt.nl()

        bb= 30
        word @shared offset=1000
        cx16.r2s = (math.sin8u(bb) as word) + offset    ; 1213
        txt.print_w(cx16.r2s)
        txt.nl()
        txt.nl()

        ; expected results:
        ; -96
        ; -96
        ; 947
        ; 947

        word @shared wcosa = 1111
        word @shared wsinb = -22

        txt.print_w(wcosa*wsinb / 256)
        txt.nl()
        txt.print_w((wcosa*wsinb) >>8)
        txt.nl()

        word[] rotatedz = [-11111,-12222,-13333,-14444,-15555]

        word @shared persp1 = 1000 + rotatedz[2]/256
        txt.print_w(persp1)
        txt.nl()
        persp1 = 1000 + (rotatedz[2]>>8)
        txt.print_w(persp1)
        txt.nl()


;        ubyte[3]    cycle_reverseflags
;
;        ubyte @shared flags=2
;        bool @shared b1
;        bool @shared b2
;
;        cycle_reverseflags[1]= b1 and b2 ; flags & 2 != 0 as bool

    }
}
