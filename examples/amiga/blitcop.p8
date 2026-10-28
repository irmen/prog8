%import custom
%import exec
%import blitter
%import math

main {
    sub start() {
        custom.grab_system()

        ; Allocate bitplane in CHIP RAM (320x256 = 10240 bytes, NOT cleared)
        pointer bitplane = exec.AllocMem(10240, exec.MEMF_CHIP)

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
            blitter.line_draw(160, 128, x2 as uword, y2 as uword, 40, bitplane)
        }
        blitter.wait()

        ; Patch the bitplane pointer into the copper list (words 3 and 5)
        copperdata.copper_list[3] = msw(bitplane)    ; BPL1PTH value
        copperdata.copper_list[5] = lsw(bitplane)    ; BPL1PTL value

        ; Activate the copper list
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = $8280    ; SET | DMAEN | COPEN
        custom.COPJMP1 = 0

        ; Wait for left mouse click
        while not custom.left_button() {
        }

        custom.restore_system()
        exec.FreeMem(bitplane, 10240)
    }
}

copperdata {
    %option amiga_chipram

    ; Raw copper list words, one MOVE/WAIT instruction per line.
    ; MOVE = register offset, value (bit 0 of word 1 clear).
    ; WAIT = (vpos<<8) | (hpos<<1) | 1, $fffe (bit 0 set, no blitter wait).
    ; END = $ffff, $fffe.
    uword[120] copper_list = [
        $0096, $83c0,    ; MOVE DMACON: SET | DMAEN | BLTEN | BPLEN | COPEN
        $00e0, $0000,    ; MOVE BPL1PTH (patched at startup)
        $00e2, $0000,    ; MOVE BPL1PTL (patched at startup)
        $0100, $1200,    ; MOVE BPLCON0: 1 bitplane, color burst on
        $008e, $2c81,    ; MOVE DIWSTRT
        $0090, $2cc1,    ; MOVE DIWSTOP
        $0092, $0038,    ; MOVE DDFSTRT
        $0094, $00d0,    ; MOVE DDFSTOP
        $0108, $0000,    ; MOVE BPL1MOD
        $0180, $0000,    ; MOVE COLOR00 = black (background)
        $0182, $0fff,    ; MOVE COLOR01 = white (foreground)
        $5a01, $fffe, $0180, $0010,    ; WAIT 90  + COLOR00 dark purple
        $5b01, $fffe, $0180, $0104,    ; WAIT 91
        $5c01, $fffe, $0180, $0207,    ; WAIT 92
        $5d01, $fffe, $0180, $0309,    ; WAIT 93
        $5e01, $fffe, $0180, $050b,    ; WAIT 94
        $5f01, $fffe, $0180, $070d,    ; WAIT 95
        $6001, $fffe, $0180, $090e,    ; WAIT 96
        $6101, $fffe, $0180, $0b0f,    ; WAIT 97
        $6201, $fffe, $0180, $0d0f,    ; WAIT 98
        $6301, $fffe, $0180, $0e0f,    ; WAIT 99
        $6401, $fffe, $0180, $0f0f,    ; WAIT 100 bright magenta
        $6501, $fffe, $0180, $0f0f,    ; WAIT 101 peak
        $6601, $fffe, $0180, $0f0f,    ; WAIT 102
        $6701, $fffe, $0180, $0f0f,    ; WAIT 103
        $6801, $fffe, $0180, $0e0f,    ; WAIT 104
        $6901, $fffe, $0180, $0d0f,    ; WAIT 105
        $6a01, $fffe, $0180, $0b0f,    ; WAIT 106
        $6b01, $fffe, $0180, $090e,    ; WAIT 107
        $6c01, $fffe, $0180, $070d,    ; WAIT 108
        $6d01, $fffe, $0180, $050b,    ; WAIT 109
        $6e01, $fffe, $0180, $0309,    ; WAIT 110
        $6f01, $fffe, $0180, $0207,    ; WAIT 111
        $7001, $fffe, $0180, $0104,    ; WAIT 112
        $7101, $fffe, $0180, $0000,    ; WAIT 113 back to black
        $ffff, $fffe     ; END
    ]
}
