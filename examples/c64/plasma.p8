%import syslib
%import textio
%import math

;  converted from plasma test program for cc65.
;  which is (w)2001 by groepaz/hitmen
;
;  Cleanup and porting to C by Ullrich von Bassewitz.
;  See https://github.com/cc65/cc65/tree/master/samples/cbm/plasma.c
;
;  Converted to prog8 by Irmen de Jong.


main {
    const uword SCREEN1 = $E000
    const uword SCREEN2 = $E400
    const uword CHARSET = $E800

    const ubyte PAGE1 = ((SCREEN1 >> 6) & $F0) | ((CHARSET >> 10) & $0E)
    const ubyte PAGE2 = ((SCREEN2 >> 6) & $F0) | ((CHARSET >> 10) & $0E)

    sub start() {
        txt.color(1)
        txt.clear_screen()
        txt.print("creating charset...\n\nwhile running, press key to stop.\n\n")
        makechar()

        ubyte block = c64.CIA2PRA
        ubyte v = c64.VMCSB
        c64.CIA2PRA = (block & $FC) | (lsb(SCREEN1 >> 14) ^ $03)

        uword frames = 0
        cbm.SETTIM(0,0,0)

        while cbm.GETIN()==0 {
            doplasma(SCREEN1)
            c64.VMCSB = PAGE1
            doplasma(SCREEN2)
            c64.VMCSB = PAGE2
            frames += 2
        }

        uword jiffies = cbm.RDTIM16()

        ; restore screen and displays stats
        c64.VMCSB = v
        c64.CIA2PRA = block
        txt.print("time in jiffies: ")
        txt.print_uw(jiffies)
        txt.print("\nframes: ")
        txt.print_uw(frames)
        uword fps = (frames*60)/jiffies
        txt.print("\nfps: ")
        txt.print_uw(fps)
        txt.print("\ndone!\n")
        repeat {
        }
    }

    ; several variables outside of doplasma to make them retain their value
    ubyte c1A
    ubyte c1B
    ubyte c2A
    ubyte c2B

    sub doplasma(uword @zp screen) {
        ubyte[40] xbuf
        ubyte[25] ybuf
        ubyte c1a = c1A
        ubyte c1b = c1B
        ubyte c2a = c2A
        ubyte c2b = c2B
        ubyte @zp x
        ubyte @zp y

        for y in 24 downto 0 {
            ybuf[y] = math.sin8u(c1a) + math.sin8u(c1b)
            c1a += 4
            c1b += 9
        }
        c1A += 3
        c1B -= 5
        for x in 39 downto 0 {
            xbuf[x] = math.sin8u(c2a) + math.sin8u(c2b)
            c2a += 3
            c2b += 7
        }
        c2A += 2
        c2B -= 3

        for y in 0 to 24 {
            for x in 0 to 39 {
                @(screen+x) = xbuf[x] + ybuf[y]
; max optimized asm is this: (achieving ~21 fps on the C64):
;                %asm {{
;                     ldy  p8_y
;                     lda  p8_ybuf,y
;                     ldy  p8_x
;                     clc
;                     adc  p8_xbuf,y
;                     sta  (p8_screen),y
;                 }}
            }
            screen += 40
        }
    }

    sub makechar() {
        ubyte[8] bittab = [ $01, $02, $04, $08, $10, $20, $40, $80 ]
        ubyte c
        for c in 0 to 255 {
            ubyte @zp s = math.sin8u(c)     ; chance
            ubyte i
            ; for all the pixels in the 8x8 character grid, determine (with a rnd chance) if they should be on or off
            for i in 0 to 7 {
                ubyte b=0
                ubyte @zp ii
                for ii in 0 to 7 {
                    if math.rnd() > s
                        b |= bittab[ii]
                }
                @(CHARSET + i + c*$0008) = b
            }
        }
    }
}
