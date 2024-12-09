%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        while cx16.r0L & 1 == 0 {
        }

        while cx16.r0L & 1 == 1 {
        }

        while cx16.r0L & 64 == 0 {
        }

        while cx16.r0L & 64 == 64 {
        }

        do { }
        until cx16.r0L & 1 == 0

        do { }
        until cx16.r0L & 1 == 1

        do { }
        until cx16.r0L & 64 == 0

        do { }
        until cx16.r0L & 64 == 64

        while cx16.r0L & 1 == 0 {
            cx16.r0L++
        }

        while cx16.r0L & 1 == 1 {
            cx16.r0L++
        }

        while cx16.r0L & 64 == 0 {
            cx16.r0L++
        }

        while cx16.r0L & 64 == 64 {
            cx16.r0L++
        }

        do {
            cx16.r0L++
        }
        until cx16.r0L & 1 == 0

        do {
            cx16.r0L++
        }
        until cx16.r0L & 1 == 1

        do {
            cx16.r0L++
        }
        until cx16.r0L & 64 == 0

        do {
            cx16.r0L++
        }
        until cx16.r0L & 64 == 64

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
