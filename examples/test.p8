%import diskio
%import textio
;%import gfx_hires4
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        txt.print_bool(diskio.exists("doesntexist.prg"))
        txt.nl()
        txt.print_bool(diskio.exists("test.prg"))
        txt.nl()

        diskio.f_open_w("dump.bin")
        diskio.f_close_w()
        diskio.f_close_w()
        diskio.f_close_w()
        diskio.f_close_w()

;        gfx_hires4.graphics_mode()
;        gfx_hires4.circle(300, 250, 200, 3)
;        gfx_hires4.rect(320, 10, 20, 200, 3)
;        gfx_hires4.fill(310, 310, 2)
;
;        repeat {
;        }
    }
}

