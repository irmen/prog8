%import custom
%import playfield
%import copper
%import blitter
%import math

; 3d spinning Dodecahedron with double buffering for smooth display

main {
    const ubyte VERTEX_COUNT = 20
    word[VERTEX_COUNT] px
    word[VERTEX_COUNT] py

    sub start() {
        ; Take over the Amiga hardware and configure a 320x256 one-bitplane display.
        ; The front buffer comes from the playfield helper (which also computes
        ; the display registers); the back buffer is the helper's second buffer.
        if not playfield.create(320, 256, 320, 256, 1, custom.DISPLAY_DEFAULT, 0)
            return
        if not playfield.create_backplane() {
            playfield.destroy()
            return
        }
        ^^custom.DisplayRegs regs = playfield.regs
        custom.grab_system(true)
        custom.DMACON = $83C0


        ; Build the copper list with the copper helper and point it at the
        ; front buffer.
        uword planeBase = build_copperlist(regs)
        playfield.patch_copper(copper.list, planeBase)
        custom.COP1LC = copper.list
        custom.DMACON = $8280    ; SET | DMAEN | COPEN
        custom.COPJMP1 = 0

        const ubyte TEXT_Y = 240
        ; Copy the generated monochrome message into the reserved bottom strip of BOTH bitplanes.
        pointer text_screen1 = playfield.bitmap.Planes[0] + (TEXT_Y * 40)
        pointer text_screen2 = playfield.back.Planes[0] + (TEXT_Y * 40)
        blitter.copy_rect(textdata.text_bitmap_words, text_screen1, textdata.TEXT_WIDTH_WORDS, textdata.TEXT_HEIGHT, 0, 26, $f0, 0)
        blitter.wait()
        blitter.copy_rect(textdata.text_bitmap_words, text_screen2, textdata.TEXT_WIDTH_WORDS, textdata.TEXT_HEIGHT, 0, 26, $f0, 0)
        blitter.wait()

        const ubyte EDGE_COUNT = 30
        ; Integer-scaled canonical coordinates from Wikipedia's regular dodecahedron model.
        word[VERTEX_COUNT] vx = [-75, -75, -75, -75, 75, 75, 75, 75, 0, 0, 0, 0, -46, -46, 46, 46, -121, -121, 121, 121]
        word[VERTEX_COUNT] vy = [-75, -75, 75, 75, -75, -75, 75, 75, -121, -121, 121, 121, 0, 0, 0, 0, -46, 46, -46, 46]
        word[VERTEX_COUNT] vz = [-75, 75, -75, 75, -75, 75, -75, 75, -46, 46, -46, 46, -121, 121, -121, 121, 0, 0, 0, 0]
        ubyte[EDGE_COUNT] edgesFrom = [
            0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3,
            4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7,
            8, 10, 12, 13, 16, 18
        ]
        ubyte[EDGE_COUNT] edgesTo = [
            8, 12, 16, 9, 13, 16, 10, 12, 17, 11, 13, 17,
            8, 14, 18, 9, 15, 18, 10, 14, 19, 11, 15, 19,
            9, 11, 14, 15, 17, 19
        ]

        const word CX = 160
        const word CY = 128
        const word PERSPECTIVE_DISTANCE = 192
        const uword ROT_X_SPEED = 250
        const uword ROT_Y_SPEED = 158
        const uword ROT_Z_SPEED = 108

        uword angle_x = 0
        uword angle_y = 0
        uword angle_z = 0
        while not custom.left_button() {

            ; Wait for vertical blank, then ensure blitter is done before swapping.
            custom.waitvsync()
            blitter.wait()
            ; Flip the buffers and point the copper list at the new front.
            playfield.flip()
            playfield.patch_copper(copper.list, planeBase)
            pointer backplane = playfield.back.Planes[0]

            ; Clear the back buffer and prepare for drawing.
            blitter.clear_plane(backplane, 20, TEXT_Y)

            ; Build the fixed-point rotation matrix and rotate all vertices.
            word wcosa = cos8_fixed(angle_x)
            word wsina = sin8_fixed(angle_x)
            word wcosb = cos8_fixed(angle_y)
            word wsinb = sin8_fixed(angle_y)
            word wcosc = cos8_fixed(angle_z)
            word wsinc = sin8_fixed(angle_z)

            word wcosa_sinb = wcosa * wsinb >> 7
            word wsina_sinb = wsina * wsinb >> 7
            word Axx = wcosa * wcosb >> 7
            word Axy = (wcosa_sinb * wsinc - wsina * wcosc) >> 7
            word Axz = (wcosa_sinb * wcosc + wsina * wsinc) >> 7
            word Ayx = wsina * wcosb >> 7
            word Ayy = (wsina_sinb * wsinc + wcosa * wcosc) >> 7
            word Ayz = (wsina_sinb * wcosc - wcosa * wsinc) >> 7
            word Azx = -wsinb
            word Azy = wcosb * wsinc >> 7
            word Azz = wcosb * wcosc >> 7

            ubyte i
            for i in 0 to VERTEX_COUNT - 1 {
                ; Rotate the vertex.
                word rx = Axx * vx[i] + Axy * vy[i] + Axz * vz[i]
                word ry = Ayx * vx[i] + Ayy * vy[i] + Ayz * vz[i]
                word rz = Azx * vx[i] + Azy * vy[i] + Azz * vz[i]

                ; Perspective projection: positive Z is nearer to the camera.
                word persp = PERSPECTIVE_DISTANCE - (rz >> 8)
                if persp < 32
                    persp = 32
                px[i] = rx / persp + CX
                py[i] = CY - ry / persp
                if px[i] < 0
                    px[i] = 0
                if px[i] > 319
                    px[i] = 319
                if py[i] < 0
                    py[i] = 0
                if py[i] > TEXT_Y - 1
                    py[i] = TEXT_Y - 1
            }

            blitter.line_init($ffff, $8000, $ffff, 40)

            ; Draw the dodecahedron skeleton with the hardware line blitter.
            ubyte e
            for e in 0 to EDGE_COUNT - 1 {
                ubyte v1 = edgesFrom[e]
                ubyte v2 = edgesTo[e]
                blitter.line_draw(px[v1] as uword, py[v1] as uword, px[v2] as uword, py[v2] as uword, 40, backplane)
            }

            angle_x += ROT_X_SPEED
            angle_y += ROT_Y_SPEED
            angle_z += ROT_Z_SPEED
        }

        custom.FMODE = $0000
        custom.restore_system()
        playfield.destroy()
    }

    const ubyte BAR_TOP = $30
    const ubyte BAR_STEP = 5
    const ubyte BAR_LINES = 42

    ; Static black-purple-green-red raster gradient, one COLOR00 per line,
    ; plus the final line-255 reset to black.
    uword[BAR_LINES+1] barcolors = [
        $0000, $0101, $0202, $0303, $0404, $0505, $0606, $0707,
        $0808, $0909, $0a0a, $0b0b, $0c0c, $0d0d, $0e0e, $0f0f,
        $0e2e, $0d4d, $0c6c, $0a8a, $08a8, $06a6, $04c4, $02e2,
        $00f0, $01e0, $02d0, $03c0, $04b0, $05a0, $0690, $0780,
        $0870, $0960, $0a50, $0b40, $0c30, $0f00, $0d00, $0b00,
        $0800, $0400, $0000
    ]

    ; Value-word slots of the gradient COLOR00 MOVEs, filled by build_copperlist().
    uword[BAR_LINES+1] barslots

    sub build_copperlist(^^custom.DisplayRegs display) -> uword {
        ; Build the copper list with the copper helper. Returns the base
        ; value-word index of the bitplane pointer slots: the main loop
        ; rewrites them with copper.set() on every buffer swap.
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
        ubyte line = BAR_TOP
        for i in 0 to BAR_LINES-1 {
            copper.wait(line, 0)
            barslots[i] = copper.move($0180, 0)
            line += BAR_STEP
        }
        copper.wait($ff, 0)
        barslots[BAR_LINES] = copper.move($0180, 0)
        for i in 0 to BAR_LINES
            copper.set(barslots[i], barcolors[i])
        copper.end()
        return planeBase
    }

    sub sin8_fixed(uword angle) -> word {
        ; Blend adjacent 8-bit lookup samples using the phase fraction for smoother motion.
        ubyte index = msb(angle)
        word sample1 = math.sin8(index)
        word sample2 = math.sin8((index + 1) as ubyte)
        long fraction = angle & $ff
        long interpolated = (sample2 - sample1) as long * fraction / 256
        return (sample1 as long + interpolated) as word
    }

    sub cos8_fixed(uword angle) -> word {
        return sin8_fixed(angle + $4000)
    }

}

copperdata {
    %option amiga_chipram

    ; Word buffer owned by the copper builder (see main.build_copperlist()):
    ; DMACON + 1 plane + 7 register MOVEs + 2 colors + 43 gradient
    ; WAIT/MOVE pairs + END = 198 words.
    uword[208] copper_list
}

textdata {
    %option amiga_chipram

    const uword TEXT_WIDTH_WORDS = 7
    const uword TEXT_HEIGHT = 16
    const uword TEXT_BYTES = TEXT_WIDTH_WORDS * 2 * TEXT_HEIGHT

    uword[TEXT_WIDTH_WORDS * TEXT_HEIGHT] text_bitmap_words = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0202, $400e, $0000, $0000, $3f00, $0000, $0782,
        $0202, $4010, $0000, $0000, $2180, $0000, $1862,
        $0202, $0010, $0000, $0000, $2080, $0000, $1022,
        $0202, $403c, $b9c1, $79e0, $2097, $1c0e, $9022,
        $0202, $4010, $c221, $8e30, $2098, $2211, $9862,
        $03fe, $4010, $8411, $0410, $2190, $4120, $8782,
        $0202, $4010, $8411, $0410, $3f10, $4120, $9862,
        $0202, $4010, $8411, $0410, $2010, $4120, $9020,
        $0202, $4010, $8411, $0410, $2010, $4120, $9020,
        $0202, $4010, $8221, $0410, $2010, $2211, $9862,
        $0202, $4010, $81c1, $0410, $2010, $1c0e, $8fc2,
        $0000, $0000, $0000, $0000, $0000, $0000, $8000,
        $0000, $0000, $0000, $0000, $0000, $0011, $8000,
        $0000, $0000, $0000, $0000, $0000, $000f, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000
    ]

}
