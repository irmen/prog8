%import monogfx

%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        monogfx.lores()

        uword x
        for x in 128 to 319 step 40 {
            monogfx.line(x, 0, x-100, 200, true)
        }
        for x in 32 to 200 step 40 {
            monogfx.line(x, 239, x+100, 20, true)
        }
        monogfx.fill(310, 230, true, 44)

        sys.wait(100)
    }

;    sub start() {
;        monogfx.hires()
;
;        uword x
;        for x in 128 to 639 step 64 {
;            monogfx.line(x, 0, x-100, 400, true)
;        }
;        for x in 32 to 500 step 64 {
;            monogfx.line(x, 479, x+100, 100, true)
;        }
;        monogfx.fill(630, 440, true, 44)
;
;        sys.wait(100)
;    }

;    sub start() {
;        gfx_hires.graphics_mode()
;
;        uword x
;        for x in 128 to 639 step 64 {
;            gfx_hires.line(x, 0, x-100, 400, 1)
;        }
;        for x in 32 to 500 step 64 {
;            gfx_hires.line(x, 479, x+100, 100, 1)
;        }
;        gfx_hires.fill(630, 440, 2, 44)
;
;        sys.wait(100)
;    }

;    sub start() {
;        gfx_lores.graphics_mode()
;
;        uword x
;        for x in 128 to 319 step 40 {
;            gfx_lores.line(x, 0, x-100, 200, 1)
;        }
;        for x in 32 to 200 step 40 {
;            gfx_lores.line(x, 239, x+100, 20, 1)
;        }
;        gfx_lores.fill(310, 230, 2, 44)
;
;        sys.wait(100)
;    }
}
