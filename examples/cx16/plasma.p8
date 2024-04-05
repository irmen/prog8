%import syslib
%import textio
%import math
%zeropage basicsafe

;  converted from plasma test program for cc65.
;  which is (w)2001 by groepaz/hitmen
;
;  Cleanup and porting to C by Ullrich von Bassewitz.
;  See https://github.com/cc65/cc65/tree/master/samples/cbm/plasma.c
;
;  Optimized and Converted to prog8 by Irmen de Jong.


main {
    const uword VERA_CHARSET = $f000        ; $1f000
    const uword VERA_TXTSCREEN = $b000      ; %1b000

    sub start() {
        txt.color(6)
        txt.clear_screen()
        makechar()

        uword frames = 0
        cbm.SETTIM(0,0,0)

        do {
            ; sys.waitvsync()
            doplasma()
            frames ++
            void, cx16.r0L = cbm.GETIN()
        } until cx16.r0L!=0

        uword jiffies = cbm.RDTIM16()

        ; restore screen and displays stats

        cx16.screen_set_charset(2, 0)
        txt.color(7)
        txt.clear_screen()
        txt.print("time in jiffies: ")
        txt.print_uw(jiffies)
        txt.print("\nframes: ")
        txt.print_uw(frames)
        uword fps = (frames*60)/jiffies
        txt.print("\nfps: ")
        txt.print_uw(fps)
        txt.nl()
    }

    ; several variables outside of doplasma to make them retain their value
    ubyte c1A
    ubyte c1B
    ubyte c2A
    ubyte c2B

    sub doplasma() {
        ubyte[txt.DEFAULT_WIDTH] xbuf
        ubyte[txt.DEFAULT_HEIGHT] ybuf
        ubyte c1a = c1A
        ubyte c1b = c1B
        ubyte c2a = c2A
        ubyte c2b = c2B
        ubyte @zp x
        ubyte @zp y

        for y in 0 to txt.DEFAULT_HEIGHT-1 {
            ybuf[y] = math.sin8u(c1a) + math.sin8u(c1b)
            c1a += 2
            c1b += 5
        }
        c1A += 3
        c1B -= 5
        for x in 0 to txt.DEFAULT_WIDTH-1 {
            xbuf[x] = math.sin8u(c2a) + math.sin8u(c2b)
            c2a += 4
            c2b += 1
        }
        c2A += 2
        c2B -= 3

        ; sys.waitvsync()    ; if you put this in it will run at 30 fps synced which looks really nice and smooth

        ; use vera auto increment writes to avoid slow txt.setchr(x, y, xbuf[x] + ybuf[y])
        for y in 0 to txt.DEFAULT_HEIGHT-1 {
            cx16.vaddr_autoincr(1, VERA_TXTSCREEN + y*$0100, 0, 2)
            ubyte @zp yvalue = ybuf[y]
            for x in 0 to txt.DEFAULT_WIDTH-1 {
                cx16.VERA_DATA0 = xbuf[x] + yvalue
            }
        }
    }

    sub makechar() {
        ubyte[8] bittab = [ $01, $02, $04, $08, $10, $20, $40, $80 ]
        ubyte c
        cx16.vaddr_autoincr(1, VERA_CHARSET, 0, 1)
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
                cx16.VERA_DATA0 = b
            }
        }
    }
}
