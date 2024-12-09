%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.r0 = (cx16.r0 & $a000) | $0055
        cx16.r0 = (cx16.r0 | $a000) ^ $0055
        cx16.r0 = (cx16.r0 ^ $a000) & $0055

        cx16.r0 = (cx16.r1 & $a000)
        cx16.r0 = (cx16.r1 | $a000)
        cx16.r0 = (cx16.r1 ^ $a000)

        ; these are optimized already:
        cx16.r0 = (cx16.r0 & $a000)
        cx16.r0 = (cx16.r0 | $a000)
        cx16.r0 = (cx16.r0 ^ $a000)

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
