%import c64lib
%zeropage basicsafe


;/*****************************************************************************\
;** plasma test program for cc65.                                             **
;**                                                                           **
;** (w)2001 by groepaz/hitmen                                                 **
;**                                                                           **
;** Cleanup and porting by Ullrich von Bassewitz.                             **
;** Converted to prog8 by Irmen de Jong                                       **
;**                                                                           **
;\*****************************************************************************/

main {
    const uword SCREEN1 = $E000
    const uword SCREEN2 = $E400
    const uword CHARSET = $E800

    const ubyte PAGE1 = ((SCREEN1 >> 6) & $F0) | ((CHARSET >> 10) & $0E)
    const ubyte PAGE2 = ((SCREEN2 >> 6) & $F0) | ((CHARSET >> 10) & $0E)

    sub start() {
        c64.COLOR = 1
        c64scr.print("creating charset...")
        makechar()

        ubyte block = c64.CIA2PRA
        c64.CIA2PRA = (block & $FC) | (lsb(SCREEN1 >> 14) ^ $03)

        repeat {
            doplasma(SCREEN1)
            c64.VMCSB = PAGE1
            doplasma(SCREEN2)
            c64.VMCSB = PAGE2
        }
    }

    ; several variables outside of doplasma to make them retain their value
    ubyte c1A
    ubyte c1B
    ubyte c2A
    ubyte c2B

    sub doplasma(uword screen) {
        ubyte[40] xbuf
        ubyte[25] ybuf
        ubyte c1a = c1A
        ubyte c1b = c1B
        ubyte c2a = c2A
        ubyte c2b = c2B
        ubyte @zp i
        ubyte @zp ii

        for ii in 0 to 24 {
            ybuf[ii] = sin8u(c1a) + sin8u(c1b)
            c1a += 4
            c1b += 9
        }
        c1A += 3
        c1B -= 5
        for i in 0 to 39 {
            xbuf[i] = sin8u(c2a) + sin8u(c2b)
            c2a += 3
            c2b += 7
        }
        c2A += 2
        c2B -= 3
        for ii in 0 to 24 {
            for i in 0 to 39 {
                @(screen) = xbuf[i] + ybuf[ii]
                screen++
            }
        }
    }

    sub makechar() {

        ubyte[8] bittab = [ $01, $02, $04, $08, $10, $20, $40, $80 ]
        ubyte c
        for c in 0 to 255 {
            ubyte @zp s = sin8u(c)
            ubyte i
            for i in 0 to 7 {
                ubyte b=0
                ubyte @zp ii
                for ii in 0 to 7 {
                    ; use 16 bit rng for a bit more randomness instead of the 8-bit rng
                    if lsb(rndw()) > s {
                        b |= bittab[ii]
                    }
                }
                @(CHARSET + i + c*8.w) = b
            }
        }
    }
}
