%import custom
%import exec
%import copper
%import blitter
%import math

; 3d spinning Dodecahedron.  Needs a Amiga1200 with fast ram to display smoothly (there's no double buffering)

main {
    sub setup_copper(pointer bitplane, pointer copper_list) {
        copper.init(copper_list)
        copper.move($096, $83C0)
        copper.move($0e0, msw(bitplane))
        copper.move($0e2, lsw(bitplane))
        copper.move($100, $1200)
        copper.move($08e, $2c81)
        copper.move($090, $2cc1)
        copper.move($092, $0038)
        copper.move($094, $00d0)
        copper.move($108, $0000)
        copper.move($180, $0000)
        copper.move($182, $fff)

        ; The copper paints a static black-purple-green-red raster gradient.
        copper.wait(48, 0, $fffe)
        copper.move($180, $0000)
        copper.wait(53, 0, $fffe)
        copper.move($180, $101)
        copper.wait(58, 0, $fffe)
        copper.move($180, $202)
        copper.wait(63, 0, $fffe)
        copper.move($180, $303)
        copper.wait(68, 0, $fffe)
        copper.move($180, $404)
        copper.wait(73, 0, $fffe)
        copper.move($180, $505)
        copper.wait(78, 0, $fffe)
        copper.move($180, $606)
        copper.wait(83, 0, $fffe)
        copper.move($180, $707)
        copper.wait(88, 0, $fffe)
        copper.move($180, $808)
        copper.wait(93, 0, $fffe)
        copper.move($180, $909)
        copper.wait(98, 0, $fffe)
        copper.move($180, $a0a)
        copper.wait(103, 0, $fffe)
        copper.move($180, $b0b)
        copper.wait(108, 0, $fffe)
        copper.move($180, $c0c)
        copper.wait(113, 0, $fffe)
        copper.move($180, $d0d)
        copper.wait(118, 0, $fffe)
        copper.move($180, $e0e)
        copper.wait(123, 0, $fffe)
        copper.move($180, $f0f)
        copper.wait(128, 0, $fffe)
        copper.move($180, $e2e)
        copper.wait(133, 0, $fffe)
        copper.move($180, $d4d)
        copper.wait(138, 0, $fffe)
        copper.move($180, $c6c)
        copper.wait(143, 0, $fffe)
        copper.move($180, $a8a)
        copper.wait(148, 0, $fffe)
        copper.move($180, $8a8)
        copper.wait(153, 0, $fffe)
        copper.move($180, $6a6)
        copper.wait(158, 0, $fffe)
        copper.move($180, $4c4)
        copper.wait(163, 0, $fffe)
        copper.move($180, $2e2)
        copper.wait(168, 0, $fffe)
        copper.move($180, $0f0)
        copper.wait(173, 0, $fffe)
        copper.move($180, $1e0)
        copper.wait(178, 0, $fffe)
        copper.move($180, $2d0)
        copper.wait(183, 0, $fffe)
        copper.move($180, $3c0)
        copper.wait(188, 0, $fffe)
        copper.move($180, $4b0)
        copper.wait(193, 0, $fffe)
        copper.move($180, $5a0)
        copper.wait(198, 0, $fffe)
        copper.move($180, $690)
        copper.wait(203, 0, $fffe)
        copper.move($180, $780)
        copper.wait(208, 0, $fffe)
        copper.move($180, $870)
        copper.wait(213, 0, $fffe)
        copper.move($180, $960)
        copper.wait(218, 0, $fffe)
        copper.move($180, $a50)
        copper.wait(223, 0, $fffe)
        copper.move($180, $b40)
        copper.wait(228, 0, $fffe)
        copper.move($180, $c30)
        copper.wait(233, 0, $fffe)
        copper.move($180, $f00)
        copper.wait(238, 0, $fffe)
        copper.move($180, $d00)
        copper.wait(243, 0, $fffe)
        copper.move($180, $b00)
        copper.wait(248, 0, $fffe)
        copper.move($180, $800)
        copper.wait(253, 0, $fffe)
        copper.move($180, $400)
        copper.wait(255, 0, $fffe)
        copper.move($180, $0000)
        copper.end()
        copper.start(copper_list)
    }

    sub start() {
        pointer bitplane = exec.AllocMem(10240, exec.MEMF_CHIP)
        pointer copper_list = exec.AllocMem(512, exec.MEMF_CHIP)

        ; Take over the Amiga hardware and configure a 320x256 one-bitplane display.
        custom.grab_system()
        custom.DMACON = $83C0

        setup_copper(bitplane, copper_list)

        const ubyte TEXT_Y = 240
        ; Copy the generated monochrome message into the reserved bottom strip.
        pointer text_screen = bitplane + (TEXT_Y * 40)
        blitter.copy_rect(textdata.text_bitmap_words, text_screen, textdata.TEXT_WIDTH_WORDS, textdata.TEXT_HEIGHT, 0, 26, $f0, 0)
        blitter.wait()

        const ubyte VERTEX_COUNT = 20
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
        word[VERTEX_COUNT] rotatedx
        word[VERTEX_COUNT] rotatedy
        word[VERTEX_COUNT] rotatedz

        while not custom.left_button() {
            word[VERTEX_COUNT] px
            word[VERTEX_COUNT] py

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
                rotatedx[i] = Axx * vx[i] + Axy * vy[i] + Axz * vz[i]
                rotatedy[i] = Ayx * vx[i] + Ayy * vy[i] + Ayz * vz[i]
                rotatedz[i] = Azx * vx[i] + Azy * vy[i] + Azz * vz[i]
            }

            for i in 0 to VERTEX_COUNT - 1 {
                ; Perspective projection: positive Z is nearer to the camera.
                word persp = PERSPECTIVE_DISTANCE - (rotatedz[i] >> 8)
                if persp < 32
                    persp = 32
                px[i] = rotatedx[i] / persp + CX
                py[i] = CY - rotatedy[i] / persp
                if px[i] < 0
                    px[i] = 0
                if px[i] > 319
                    px[i] = 319
                if py[i] < 0
                    py[i] = 0
                if py[i] > TEXT_Y - 1
                    py[i] = TEXT_Y - 1
            }

            custom.waitvsync()
            blitter.clear_plane(bitplane, 20, TEXT_Y)
            blitter.line_init($ffff, $8000, $ffff, 40)

            ; Draw the dodecahedron skeleton with the hardware line blitter.
            ubyte e
            for e in 0 to EDGE_COUNT - 1 {
                ubyte v1 = edgesFrom[e]
                ubyte v2 = edgesTo[e]
                blitter.line_draw(px[v1] as uword, py[v1] as uword, px[v2] as uword, py[v2] as uword, 40, bitplane)
            }

            angle_x += ROT_X_SPEED
            angle_y += ROT_Y_SPEED
            angle_z += ROT_Z_SPEED
        }

        custom.return_system()
        exec.FreeMem(copper_list, 512)
        exec.FreeMem(bitplane, 10240)
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
