%import custom
%import playfield
%import copper
%import exec
%import blitter
%import math
%import text

main {
    sub start() {
        ^^custom.DisplayRegs regs = custom.compute_display(320, 256, 320, 1, custom.DISPLAY_DEFAULT, 0)
        custom.grab_system(true)

        ; Allocate bitplane via playfield helper (320x256 = 10240 bytes, NOT cleared)
        if not playfield.create(320, 256, 320, 256, 1, custom.DISPLAY_DEFAULT, 0) {
            return
        }
        pointer bitplane = playfield.bitmap.Planes[0]

        ; Enable blitter DMA before using it
        custom.DMACON = $83C0  ; SET | DMAEN | BLTEN | BPLEN | COPEN

        ; Fill bitplane with a pattern using blitter (faster than CPU)
        blitter.fill_rect(bitplane, 20, 256, $AAAA, 0)
        blitter.wait()  ; wait for blit to complete before continuing

        ; Clear bottom half of screen (lines 128-255)
        pointer bottom_half = bitplane + (128 * 40)  ; offset to line 128
        blitter.fill_rect(bottom_half, 20, 128, $0000, 0)
        blitter.wait()

        ; Fill bottom 1/4th of screen (lines 192-255) solid white.
        ; The region was cleared to zero by the previous fill_rect, so a solid
        ; fill produces the same visual result as inverting it used to.
        pointer bottom_quarter = bitplane + (192 * 40)  ; offset to line 192
        blitter.fill_plane(bottom_quarter, 20, 64, $ffff) ; 20 words * 64 lines
        blitter.wait()

        ; Fill a 112x100 square in the middle of the screen with pattern $1010
        ; Position: centered at (112, 78) with size 112x100
        pointer middle_square = bitplane + (78 * 40) + (7 * 2)  ; line 78, word 7
        blitter.fill_rect(middle_square, 7, 100, $1010, 26)     ; 7 words wide, 100 lines, modulo=26 (40-14)
        blitter.wait()

        ; Copy the square to position (20,20) - note: 20px is not word-aligned, using 16px (1 word)
        pointer dest_square = bitplane + (20 * 40) + (1 * 2)  ; line 20, word 1
        blitter.copy_rect(middle_square, dest_square, 7, 100, 26, 26, $F0, 0)
        blitter.wait()

        ; Draw 16 lines from center (160, 128) to points evenly spaced
        ; every 22.5 degrees around an imaginary circle of radius 100.
        ; Screen is 320x256, pitch = 40 bytes.
        blitter.line_init($ffff, $8000, $ffff, 40)
        const ubyte RADIUS = 100
        ubyte i
        for i in 0 to 15 {
            ; angle = i * 16  (16 out of 256 = 22.5 degrees)
            ubyte angle = i * 16
            byte cs = math.cos8(angle)
            byte sn = math.sin8(angle)
            ; math.cos8/sin8 return -127..127, so scale to radius
            word x2 = 160 + (cs as word * RADIUS as word) / 127
            word y2 = 128 + (sn as word * RADIUS as word) / 127
            blitter.line_draw(160, 128, x2 as uword, y2 as uword, bitplane)
        }
        blitter.wait()

        ; CPU text into the white bottom quarter (color 0 clears to black)
        text.init(40, 1, &playfield.bitmap.Planes)
        text.print(10, 200, iso:"bare-metal text", 0)
        text.print(15, 210, iso:"reverse: \x82\x81", 0)
        text.print(16, 220, iso:"some byte aligned text", 0)

        ; Build the copper list with the copper helper (bitplane slots,
        ; display registers and the raster gradient) and start it.
        uword planeBase = build_copperlist(regs)
        playfield.patch_copper(copper.list, planeBase)
        custom.COP1LC = copper.list
        custom.DMACON = $8280    ; SET | DMAEN | COPEN
        custom.COPJMP1 = 0

        ; Wait for left mouse click
        while not custom.left_button() {
        }

        custom.FMODE = $0000
        custom.restore_system()
        playfield.destroy()
    }

    const ubyte BAR_TOP = 90
    const ubyte BAR_LINES = 24

    ; Static purple-to-magenta raster gradient, one COLOR00 per line.
    uword[BAR_LINES] gradient = [
        $0010, $0104, $0207, $0309, $050b, $070d, $090e, $0b0f,
        $0d0f, $0e0f, $0f0f, $0f0f, $0f0f, $0f0f, $0e0f, $0d0f,
        $0b0f, $090e, $070d, $050b, $0309, $0207, $0104, $0000
    ]

    sub build_copperlist(^^custom.DisplayRegs display) -> uword {
        ; Build the copper list with the copper helper. Returns the base
        ; value-word index of the bitplane pointer slots, ready to pass to
        ; playfield.patch_copper().
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $83c0)    ; DMACON: SET | DMAEN | BLTEN | BPLEN | COPEN
        uword planeBase = copper.attach_planes(1)
        void copper.move($01fc, display.fmode)
        void copper.move($0100, display.bplcon0)
        void copper.move($008e, display.diwstrt)
        void copper.move($0090, display.diwstop)
        void copper.move($0092, display.ddfstrt)
        void copper.move($0094, display.ddfstop)
        void copper.move($0108, display.bpl1mod)
        void copper.move($0180, 0)        ; COLOR00 = black (background)
        void copper.move($0182, $0fff)    ; COLOR01 = white (foreground)
        uword bar = copper.rasterbar(BAR_TOP, BAR_LINES)
        for i in 0 to BAR_LINES-1
            copper.set(bar + (i as uword)*4, gradient[i])
        copper.end()
        return planeBase
    }
}

copperdata {
    %option amiga_chipram

    ; Word buffer owned by the copper builder (see main.build_copperlist()):
    ; DMACON + 1 plane + 7 register MOVEs + 2 colors + 24 rasterbar
    ; lines + END = 122 words.
    uword[128] copper_list
}
