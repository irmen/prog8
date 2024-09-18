; optimized graphics routines for just the single screen mode: lores 320*240, 256c  (8bpp)
; bitmap image needs to start at VRAM addres $00000.
; This is compatible with the CX16's screen mode 128.  (void cx16.set_screen_mode(128))


gfx_lores {

    %option ignore_unused

    sub set_screen_mode() {
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 64
        cx16.VERA_DC_VSCALE = 64
        cx16.VERA_L1_CONFIG = %00000111
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = 0
        clear_screen(0)
    }

    sub clear_screen(ubyte color) {
        cx16.VERA_CTRL=0
        cx16.VERA_ADDR=0
        cx16.VERA_ADDR_H = 1<<4    ; 1 pixel auto increment
        repeat 240
            cs_innerloop320(color)
        cx16.VERA_ADDR=0
        cx16.VERA_ADDR_H = 0
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2, ubyte color) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        ; NOTE:  this is about twice as fast as the kernal routine GRAPH_draw_line, and ~3-4 times as fast as gfx2.line()
        ;        it trades memory for speed (uses inline plot routine and multiplication lookup tables)
        ;
        ; NOTE:  is currently still a regular 6502 routine, could likely be made much faster with the VeraFX line helper.

        cx16.r3L = y2    ; ensure zeropage
        cx16.r1L = y1    ; ensure zeropage

        if cx16.r1L > cx16.r3L {
            ; make sure dy is always positive to have only 4 instead of 8 special cases
            cx16.r0 = x1
            x1 = x2
            x2 = cx16.r0
            cx16.r0L = cx16.r1L
            cx16.r1L = cx16.r3L
            cx16.r3L = cx16.r0L
        }
        word @zp dx = x2 as word
        word @zp dy = cx16.r3L
        dx -= x1
        dy -= cx16.r1L

        if dx==0 {
            vertical_line(x1, cx16.r1L, lsb(dy)+1, color)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            horizontal_line(x1, cx16.r1L, abs(dx) as uword +1, color)
            return
        }

        word @zp d = 0
        bool positive_ix = true
        if dx < 0 {
            dx = -dx
            positive_ix = false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2

        cx16.r0  = x1    ; ensure zeropage
        cx16.r2  = x2    ; ensure zeropage

        cx16.VERA_CTRL = 0
        if dx >= dy {
            if positive_ix {
                repeat {
                    plot()
                    if cx16.r0==cx16.r2
                        return
                    cx16.r0++
                    d += dy2
                    if d > dx {
                        cx16.r1L++
                        d -= dx2
                    }
                }
            } else {
                repeat {
                    plot()
                    if cx16.r0==cx16.r2
                        return
                    cx16.r0--
                    d += dy2
                    if d > dx {
                        cx16.r1L++
                        d -= dx2
                    }
                }
            }
        }
        else {
            if positive_ix {
                repeat {
                    plot()
                    if cx16.r1L == cx16.r3L
                        return
                    cx16.r1L++
                    d += dx2
                    if d > dy {
                        cx16.r0++
                        d -= dy2
                    }
                }
            } else {
                repeat {
                    plot()
                    if cx16.r1L == cx16.r3L
                        return
                    cx16.r1L++
                    d += dx2
                    if d > dy {
                        cx16.r0--
                        d -= dy2
                    }
                }
            }
        }

        asmsub plot() {
            ; x in r0,  y in r1,   color.
            %asm {{
                ldy  cx16.r1L
                clc
                lda  times320_lo,y
                adc  cx16.r0L
                sta  cx16.VERA_ADDR_L
                lda  times320_mid,y
                adc  cx16.r0H
                sta  cx16.VERA_ADDR_M
                lda  #0
                adc  times320_hi,y
                sta  cx16.VERA_ADDR_H
                lda  p8v_color
                sta  cx16.VERA_DATA0
                rts
            }}
        }

        %asm {{

; multiplication by 320 lookup table
times320 := 320*range(240)

times320_lo     .byte <times320
times320_mid    .byte >times320
times320_hi     .byte `times320

            }}
    }

    sub horizontal_line(uword xx, ubyte yy, uword length, ubyte color) {
        if length==0
            return
        vera_setaddr(xx, yy)
        ; set vera auto-increment to 1 pixel
        cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (1<<4)

        %asm {{
            lda  p8v_color
            ldx  p8v_length+1
            beq  +
            ldy  #0
-           sta  cx16.VERA_DATA0
            iny
            bne  -
            dex
            bne  -
+           ldy  p8v_length     ; remaining
            beq  +
-           sta  cx16.VERA_DATA0
            dey
            bne  -
+
        }}
    }

    sub vertical_line(uword xx, ubyte yy, ubyte lheight, ubyte color) {
        vera_setaddr(xx,yy)
        ; set vera auto-increment to 320 pixel increment (=next line)
        cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (14<<4)
        %asm {{
            ldy  p8v_lheight
            beq  +
            lda  p8v_color
-           sta  cx16.VERA_DATA0
            dey
            bne  -
+
        }}
    }


    asmsub cs_innerloop320(ubyte color @A) clobbers(Y) {
        ; using verafx 32 bits writes here would make this faster but it's safer to
        ; use verafx only explicitly when you know what you're doing.
        %asm {{
            ldy  #40
-           sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            dey
            bne  -
            rts
        }}
    }

    inline asmsub vera_setaddr(uword xx @R0, ubyte yy @R1) {
        ; set the correct vera start address (no auto increment yet!)
        %asm {{
            jsr  p8s_line.p8s_plot
        }}
    }
}

