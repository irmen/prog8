%import custom
%import exec
%import blitter
%import math

; 3d spinning Dodecahedron with double buffering for smooth display

main {
    const ubyte VERTEX_COUNT = 20
    word[VERTEX_COUNT] px
    word[VERTEX_COUNT] py

    sub start() {
        pointer bitplane1 = exec.AllocMem(10240, exec.MEMF_CHIP | exec.MEMF_CLEAR)
        pointer bitplane2 = exec.AllocMem(10240, exec.MEMF_CHIP | exec.MEMF_CLEAR)

        ; Take over the Amiga hardware and configure a 320x256 one-bitplane display.
        custom.grab_system()
        custom.DMACON = $83C0


        pointer current_bitplane = bitplane1
        pointer back_bitplane = bitplane2

        ; Patch the bitplane pointer into the copper list (words 3 and 5) and start it.
        copperdata.copper_list[3] = msw(current_bitplane)
        copperdata.copper_list[5] = lsw(current_bitplane)
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = $8280    ; SET | DMAEN | COPEN
        custom.COPJMP1 = 0

        const ubyte TEXT_Y = 240
        ; Copy the generated monochrome message into the reserved bottom strip of BOTH bitplanes.
        pointer text_screen1 = bitplane1 + (TEXT_Y * 40)
        pointer text_screen2 = bitplane2 + (TEXT_Y * 40)
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
            ; Swap the bitplane pointer in the copper list (words 3 and 5).
            copperdata.copper_list[3] = msw(back_bitplane)
            copperdata.copper_list[5] = lsw(back_bitplane)
            pointer temp = current_bitplane
            current_bitplane = back_bitplane
            back_bitplane = temp

            ; Clear the back buffer and prepare for drawing.
            blitter.clear_plane(back_bitplane, 20, TEXT_Y)

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
                blitter.line_draw(px[v1] as uword, py[v1] as uword, px[v2] as uword, py[v2] as uword, 40, back_bitplane)
            }

            angle_x += ROT_X_SPEED
            angle_y += ROT_Y_SPEED
            angle_z += ROT_Z_SPEED
        }

        custom.restore_system()
        exec.FreeMem(bitplane1, 10240)
        exec.FreeMem(bitplane2, 10240)
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

    ; Raw copper list words, one MOVE/WAIT instruction per line.
    ; MOVE = register offset, value (bit 0 of word 1 clear).
    ; WAIT = (vpos<<8) | (hpos<<1) | 1, $fffe (bit 0 set, no blitter wait).
    ; END = $ffff, $fffe.
    ; Words 3 and 5 are the bitplane pointer, patched at startup and each frame.
    ; The copper paints a static black-purple-green-red raster gradient.
    uword[196] copper_list = [
        $0096, $83c0,    ; MOVE DMACON: SET | DMAEN | BLTEN | BPLEN | COPEN
        $00e0, $0000,    ; MOVE BPL1PTH (patched at runtime)
        $00e2, $0000,    ; MOVE BPL1PTL (patched at runtime)
        $0100, $1200,    ; MOVE BPLCON0: 1 bitplane, color burst on
        $008e, $2c81,    ; MOVE DIWSTRT
        $0090, $2cc1,    ; MOVE DIWSTOP
        $0092, $0038,    ; MOVE DDFSTRT
        $0094, $00d0,    ; MOVE DDFSTOP
        $0108, $0000,    ; MOVE BPL1MOD
        $0180, $0000,    ; MOVE COLOR00 = black (background)
        $0182, $0fff,    ; MOVE COLOR01 = white (foreground)
        $3001, $fffe, $0180, $0000,    ; WAIT 48
        $3501, $fffe, $0180, $0101,    ; WAIT 53
        $3a01, $fffe, $0180, $0202,    ; WAIT 58
        $3f01, $fffe, $0180, $0303,    ; WAIT 63
        $4401, $fffe, $0180, $0404,    ; WAIT 68
        $4901, $fffe, $0180, $0505,    ; WAIT 73
        $4e01, $fffe, $0180, $0606,    ; WAIT 78
        $5301, $fffe, $0180, $0707,    ; WAIT 83
        $5801, $fffe, $0180, $0808,    ; WAIT 88
        $5d01, $fffe, $0180, $0909,    ; WAIT 93
        $6201, $fffe, $0180, $0a0a,    ; WAIT 98
        $6701, $fffe, $0180, $0b0b,    ; WAIT 103
        $6c01, $fffe, $0180, $0c0c,    ; WAIT 108
        $7101, $fffe, $0180, $0d0d,    ; WAIT 113
        $7601, $fffe, $0180, $0e0e,    ; WAIT 118
        $7b01, $fffe, $0180, $0f0f,    ; WAIT 123 magenta
        $8001, $fffe, $0180, $0e2e,    ; WAIT 128
        $8501, $fffe, $0180, $0d4d,    ; WAIT 133
        $8a01, $fffe, $0180, $0c6c,    ; WAIT 138
        $8f01, $fffe, $0180, $0a8a,    ; WAIT 143
        $9401, $fffe, $0180, $08a8,    ; WAIT 148
        $9901, $fffe, $0180, $06a6,    ; WAIT 153
        $9e01, $fffe, $0180, $04c4,    ; WAIT 158
        $a301, $fffe, $0180, $02e2,    ; WAIT 163
        $a801, $fffe, $0180, $00f0,    ; WAIT 168 green
        $ad01, $fffe, $0180, $01e0,    ; WAIT 173
        $b201, $fffe, $0180, $02d0,    ; WAIT 178
        $b701, $fffe, $0180, $03c0,    ; WAIT 183
        $bc01, $fffe, $0180, $04b0,    ; WAIT 188
        $c101, $fffe, $0180, $05a0,    ; WAIT 193
        $c601, $fffe, $0180, $0690,    ; WAIT 198
        $cb01, $fffe, $0180, $0780,    ; WAIT 203
        $d001, $fffe, $0180, $0870,    ; WAIT 208
        $d501, $fffe, $0180, $0960,    ; WAIT 213
        $da01, $fffe, $0180, $0a50,    ; WAIT 218
        $df01, $fffe, $0180, $0b40,    ; WAIT 223
        $e401, $fffe, $0180, $0c30,    ; WAIT 228
        $e901, $fffe, $0180, $0f00,    ; WAIT 233 red
        $ee01, $fffe, $0180, $0d00,    ; WAIT 238
        $f301, $fffe, $0180, $0b00,    ; WAIT 243
        $f801, $fffe, $0180, $0800,    ; WAIT 248
        $fd01, $fffe, $0180, $0400,    ; WAIT 253
        $ff01, $fffe, $0180, $0000,    ; WAIT 255 back to black
        $ffff, $fffe     ; END
    ]
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
