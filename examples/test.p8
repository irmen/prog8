%import textio
%import gfx_hires4
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        gfx_hires4.graphics_mode()
        gfx_hires4.circle(300, 250, 200, 3)
        gfx_hires4.rect(320, 10, 20, 200, 3)
        gfx_hires4.fill(310, 310, 2)

        repeat {
        }
    }
}

