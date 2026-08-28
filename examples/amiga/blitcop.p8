%import custom
%import exec
%import copper
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

        ; Allocate copper list in CHIP RAM (NOT cleared)
        pointer copper_list = exec.AllocMem(256, exec.MEMF_CHIP)

        ; Build the copper list using copper module routines
        copper.init(copper_list)

        ; Enable DMA: SET | DMAEN | BLTEN | BPLEN | COPEN = $83C0
        copper.move($096, $83C0)

        ; Set display registers for 320x256 1-bitplane screen
        copper.move($0e0, msw(bitplane))    ; BPL1PTH - high word of bitplane pointer
        copper.move($0e2, lsw(bitplane))    ; BPL1PTL - low word of bitplane pointer
        copper.move($100, $1200)             ; BPLCON0: 1 bitplane, color burst on
        copper.move($08e, $2c81)             ; DIWSTRT
        copper.move($090, $2cc1)             ; DIWSTOP (256 lines, wraps around)
        copper.move($092, $0038)             ; DDFSTRT
        copper.move($094, $00d0)             ; DDFSTOP
        copper.move($108, $0000)             ; BPL1MOD

        ; Set initial background color to black
        copper.move($180, $0000)             ; COLOR00 = black (background)
        copper.move($182, $fff)              ; COLOR01 = white (foreground, for bitmap pixels)

        ; Neon purple raster bar gradient (lines 90 to 113, 24 lines tall).
        ; Amiga 12-bit RGB444: $RGB, where R/G/B are each 0..$F.
        ; Colors ramp from dark purple at the edges to bright magenta at the center.
        copper.wait(90, 0, $fffe)
        copper.move($180, $010)      ; Line 90:  dark purple
        copper.wait(91, 0, $fffe)
        copper.move($180, $104)      ; Line 91
        copper.wait(92, 0, $fffe)
        copper.move($180, $207)      ; Line 92
        copper.wait(93, 0, $fffe)
        copper.move($180, $309)      ; Line 93
        copper.wait(94, 0, $fffe)
        copper.move($180, $50b)      ; Line 94
        copper.wait(95, 0, $fffe)
        copper.move($180, $70d)      ; Line 95
        copper.wait(96, 0, $fffe)
        copper.move($180, $90e)      ; Line 96
        copper.wait(97, 0, $fffe)
        copper.move($180, $b0f)      ; Line 97
        copper.wait(98, 0, $fffe)
        copper.move($180, $d0f)      ; Line 98
        copper.wait(99, 0, $fffe)
        copper.move($180, $e0f)      ; Line 99
        copper.wait(100, 0, $fffe)
        copper.move($180, $f0f)      ; Line 100: bright magenta
        copper.wait(101, 0, $fffe)
        copper.move($180, $f0f)      ; Line 101: peak (4 lines wide)
        copper.wait(102, 0, $fffe)
        copper.move($180, $f0f)      ; Line 102
        copper.wait(103, 0, $fffe)
        copper.move($180, $f0f)      ; Line 103
        copper.wait(104, 0, $fffe)
        copper.move($180, $e0f)      ; Line 104
        copper.wait(105, 0, $fffe)
        copper.move($180, $d0f)      ; Line 105
        copper.wait(106, 0, $fffe)
        copper.move($180, $b0f)      ; Line 106
        copper.wait(107, 0, $fffe)
        copper.move($180, $90e)      ; Line 107
        copper.wait(108, 0, $fffe)
        copper.move($180, $70d)      ; Line 108
        copper.wait(109, 0, $fffe)
        copper.move($180, $50b)      ; Line 109
        copper.wait(110, 0, $fffe)
        copper.move($180, $309)      ; Line 110
        copper.wait(111, 0, $fffe)
        copper.move($180, $207)      ; Line 111
        copper.wait(112, 0, $fffe)
        copper.move($180, $104)      ; Line 112
        copper.wait(113, 0, $fffe)
        copper.move($180, $0000)     ; Line 113: back to black background

        copper.end()

        ; Activate the copper list
        copper.start(copper_list)

        ; Wait for left mouse click
        while not custom.left_button() {
        }

        custom.restore_system()
        exec.FreeMem(copper_list, 256)
        exec.FreeMem(bitplane, 10240)
    }
}
