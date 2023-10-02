%import textio
%import gfx2
%import verafx
%zeropage basicsafe

main {
    sub start() {
        gfx2.screen_mode(4)
        gfx2.disc(160, 120, 100, 2)
        ; verafx.transparency(true)
        gfx2.position(0, 70)
        repeat 3000 {
            gfx2.next_pixel(7)
            repeat 10
                gfx2.next_pixel(0)      ; transparent!
        }
        verafx.transparency(false)
    }
}

