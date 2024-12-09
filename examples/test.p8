%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        cx16.r0L = %11111110
        while cx16.r0L &32 == 32 {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        }
        txt.nl()

        cx16.r0L = %11111110
        while cx16.r0L &32 != 0 {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        }
        txt.nl()

        ; this one must not be changed and stop after 3 iterations instead of 5!
        cx16.r0L = %11111110
        while cx16.r0L &40 == 40 {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        }
        txt.nl()
        txt.nl()

        cx16.r0L = %11111110
        do {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        } until cx16.r0L &32 != 32
        txt.nl()

        cx16.r0L = %11111110
        do {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        } until cx16.r0L &32 == 0
        txt.nl()

        ; this one must not be changed and stop after 3 iterations instead of 5!
        cx16.r0L = %11111110
        do {
            cx16.r0L <<= 1
            txt.print_ubbin(cx16.r0L, true)
            txt.nl()
        } until cx16.r0L &40 != 40
        txt.nl()


;        while cx16.r0L & cx16.r1L == 0 {
;            cx16.r0L++
;        }
;
;        while cx16.r0L & cx16.r1L == cx16.r1L {
;            cx16.r0L++
;        }

/*
        sys.set_irqd()
        cx16.VERA_IEN = 1 ; only vsync irqs

        repeat {
            while (cx16.VERA_ISR & 1)==0 {
                ; wait for vsync
            }
            cx16.VERA_ISR = 1 ; clear vsync irq status

            palette.set_color(6, $ff0)
            repeat 2000 {
                cx16.r0++
            }
            palette.set_color(6, $00f)

        }
*/
    }
}
