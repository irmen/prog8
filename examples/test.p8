%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        struct Color {
            ubyte red
            uword green
            float blue
        }

        Color c = [11,22222,3.1234]

;        c64scr.print_ub(c.red)
;        c64.CHROUT('\n')
;        c64scr.print_uw(c.green)
;        c64.CHROUT('\n')
;        c64flt.print_f(c.blue)
;        c64.CHROUT('\n')

        uword xx = 4.5678
        ubyte bb = 33
        float ff = 1.234

        foo(1.234, 4.456)   ; TODO truncation warning
        foo2(2.3456)        ; TODO truncation warning
        foo2(bb)
        foo2(4.55)          ; TODO truncation warning

        ;foo("zzz", 8.777)
        ;len(13)

;        uword size = len(Color)
;        c64scr.print_uw(size)
;        c64.CHROUT('\n')

;        c64scr.print_ub(len(Color))
;        c64.CHROUT('\n')
;        c64scr.print_ub(len(c))
;        c64.CHROUT('\n')
;        c64scr.print_ub(len(c.green))
;        c64.CHROUT('\n')
    }

    sub foo(ubyte aa, word ww) {
        ww += aa
    }

    asmsub foo2(ubyte aa @Pc) {
        %asm {{
            rts
        }}
    }
}
