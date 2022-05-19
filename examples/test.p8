%import textio
%import math
%zeropage dontuse

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        byte @shared xx = 11
        byte @shared yy = 62
        byte @shared yy2 = 15
        byte @shared yy3 = 127
        ubyte @shared ubx = 3

        xx = xx + 9
        txt.print_b(xx)     ; 20
        txt.nl()
        xx = xx * 8
        txt.print_b(xx)     ; -96
        txt.nl()
        xx = xx - 7
        txt.print_b(xx)     ; -103
        txt.nl()
        xx = xx / 6
        txt.print_b(xx)     ; -17
        txt.nl()
        xx = xx * 5
        txt.print_b(xx)     ; -85
        txt.nl()
        txt.nl()

;        xx = xx+3*yy
;        xx = xx/yy
;        xx = -xx
;        @($4000) = @($4000)
;        @($4000) = @($4000) + 2
;        xx = xx ^ yy
;        xx = xx | yy2
;        xx = xx & yy3
;        txt.print_b(xx)     ; 63
;        txt.nl()
;        xx = (not xx) as byte
;        xx = (~xx) as byte
;        xx++
;        txt.print_b(xx)     ; 0
;        txt.nl()
;
;        ubx = not ubx
;        ubx = ~ubx
    }
}
