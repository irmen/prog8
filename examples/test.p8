%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        uword screen=$0400
        ubyte[256] xbuf = 1
        ubyte[256] ybuf = 3


        ubyte ix = 0
        ubyte cc

;        cc = @(screen+2)
;        cc++
;        @(screen+2) = cc

        @(screen + ix + cc*$0008) = cc

;        cc = @(screen+ix)
;        cc++
;        @(screen+ix) = cc
;        for ii in 24 downto 0 {
;            for i in 39 downto 0 {
;                @(screen+i) = xbuf[i] + ybuf[ii]
;            }
;            screen+=40
;        }
    }

}
