%option no_sysinit
%import gfx_lores
%zeropage basicsafe

main {
    sub start() {
        gfx_lores.set_screen_mode()
        gfx_lores.clear_screen(0)
        gfx_lores.line(0,0,319,239,5)
    }
}
