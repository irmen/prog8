; Vertical rasterbars a.k.a. "Kefren bars", for the C64 with a Vera64X board.
; Port of examples/cx16/kefrenbars.p8  (also see: examples/c64/rasterbars.p8)
;
; A Vera64X is the same VERA chip as the X16 has, so the effect works the same way:
; a 320x240 8bpp bitmap whose first line is stretched over the full screen height,
; and a wide bar drawn into that first line at consecutive VERA scanline positions.
;
; There is no VERA irq handler setup on the C64: the current VERA scanline is polled directly.
; Scanline bit 8 is read from bit 6 of VERA_IEN, so this works across all 480 visible lines.
;
; A 1 MHz 6502 needs more time between two bar updates than the X16's 5 scanlines,
; hence the bigger (and thus fewer, wider) bands.

%import math
%import vera
%import vera_gfx
%import vera_palette

%option no_sysinit

main {

    sub start() {
        uword[32] colors = [
            $000,
            $011,  $112,  $213,  $214,
            $315,  $316,  $417,  $418,
            $519,  $51a,  $62b,  $62c,
            $73d,  $73e,  $84f,  $94f,
            $93e,  $83d,  $82c,  $72b,
            $71a,  $619,  $618,  $517,
            $516,  $415,  $414,  $313,
            $312,  $211,  $100
        ]

        vera.reset()
        vera_palette.set_rgb(&colors, len(colors), 0)
        vera_gfx.init()                 ; lores 320x240 8bpp bitmap on layer 1, bitmap at vram $00000
        vera.VERA_DC_VSCALE = 0         ; display trick spoiler.......: stretch 1 line of display all the way to the bottom

        repeat {
            wait_for_scanline(next_line)
            next_bar()
        }
    }

    const ubyte BAR_Y_OFFSET = 20       ; scanlines between two bar updates
    uword next_line = 0
    ubyte anim1 = 0
    ubyte anim2 = 0
    ubyte av1 = 0
    ubyte av2 = 0

    sub wait_for_scanline(uword line) {
        if line == 0 {
            while (vera.VERA_IEN & %01000000)!=0 or vera.VERA_SCANLINE_L > 1 {}
        } else if msb(line)==0 {
            while (vera.VERA_IEN & %01000000)!=0 or vera.VERA_SCANLINE_L < lsb(line) {}
        } else {
            while (vera.VERA_IEN & %01000000)==0 or vera.VERA_SCANLINE_L < lsb(line) {}
        }
    }

    sub next_bar() {
        next_line += BAR_Y_OFFSET
        anim1 += 28
        anim2 += 16
        if next_line > 479 {
            av1++
            av2 += 2
            anim1 = av1
            anim2 = av2
            next_line = 0
            ; erase the bars: only bitmap line 0 (320 pixels) has to be cleared
            verafx.clear(0, 0, 3, 80)
        } else {
            ; add new bar on top
            vera_gfx.position(math.sin8u(anim1)/2 + math.cos8u(anim2)/2 + $0010, 0)
            draw_bar()
        }
    }

    sub draw_bar() {
        ; the bar is always the same 32 pixel sequence, 0 to 31
        vera_gfx.next_pixel(0)
        vera_gfx.next_pixel(1)
        vera_gfx.next_pixel(2)
        vera_gfx.next_pixel(3)
        vera_gfx.next_pixel(4)
        vera_gfx.next_pixel(5)
        vera_gfx.next_pixel(6)
        vera_gfx.next_pixel(7)
        vera_gfx.next_pixel(8)
        vera_gfx.next_pixel(9)
        vera_gfx.next_pixel(10)
        vera_gfx.next_pixel(11)
        vera_gfx.next_pixel(12)
        vera_gfx.next_pixel(13)
        vera_gfx.next_pixel(14)
        vera_gfx.next_pixel(15)
        vera_gfx.next_pixel(16)
        vera_gfx.next_pixel(17)
        vera_gfx.next_pixel(18)
        vera_gfx.next_pixel(19)
        vera_gfx.next_pixel(20)
        vera_gfx.next_pixel(21)
        vera_gfx.next_pixel(22)
        vera_gfx.next_pixel(23)
        vera_gfx.next_pixel(24)
        vera_gfx.next_pixel(25)
        vera_gfx.next_pixel(26)
        vera_gfx.next_pixel(27)
        vera_gfx.next_pixel(28)
        vera_gfx.next_pixel(29)
        vera_gfx.next_pixel(30)
        vera_gfx.next_pixel(31)
    }
}
