%import c64textio

; bitmap pixel graphics module for the C64
; only black/white monchrome for now

; you could put this code at $4000 which is after the bitmap screen in memory ($2000-$3fff),
; this leaves more space for user program code.

graphics {
    const uword bitmap_address = $2000

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        c64.SCROLY |= %00100000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00001000   ; $2000-$3fff
        clear_screen()
    }

    sub clear_screen() {
        memset(bitmap_address, 320*200/8, 0)
        txt.clear_screen($10, 0)         ; pixel color $1 (white) backround $0 (black)
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2) {
        ; Bresenham algorithm.
        ; This code special cases various quadrant loops to allow simple ++ and -- operations.
        if y1>y2 {
            ; make sure dy is always positive to avoid 8 instead of just 4 special cases
            swap(x1, x2)
            swap(y1, y2)
        }
        word d = 0
        ubyte positive_ix = true
        word dx = x2 - x1 as word
        word dy = y2 as word - y1 as word
        if dx < 0 {
            dx = -dx
            positive_ix = false
        }
        dx *= 2
        dy *= 2
        plotx = x1

        if dx >= dy {
            if positive_ix {
                repeat {
                    plot(y1)
                    if plotx==x2
                        return
                    plotx++
                    d += dy
                    if d > dx {
                        y1++
                        d -= dx
                    }
                }
            } else {
                repeat {
                    plot(y1)
                    if plotx==x2
                        return
                    plotx--
                    d += dy
                    if d > dx {
                        y1++
                        d -= dx
                    }
                }
            }
        }
        else {
            if positive_ix {
                repeat {
                    plot(y1)
                    if y1 == y2
                        return
                    y1++
                    d += dx
                    if d > dy {
                        plotx++
                        d -= dy
                    }
                }
            } else {
                repeat {
                    plot(y1)
                    if y1 == y2
                        return
                    y1++
                    d += dx
                    if d > dy {
                        plotx--
                        d -= dy
                    }
                }
            }
        }
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm
        ubyte ploty
        ubyte xx = radius
        ubyte yy = 0
        byte decisionOver2 = 1-xx as byte

        while xx>=yy {
            plotx = xcenter + xx
            ploty = ycenter + yy
            plot(ploty)
            plotx = xcenter - xx
            plot(ploty)
            plotx = xcenter + xx
            ploty = ycenter - yy
            plot(ploty)
            plotx = xcenter - xx
            plot(ploty)
            plotx = xcenter + yy
            ploty = ycenter + xx
            plot(ploty)
            plotx = xcenter - yy
            plot(ploty)
            plotx = xcenter + yy
            ploty = ycenter - xx
            plot(ploty)
            plotx = xcenter - yy
            plot(ploty)
            yy++
            if decisionOver2<=0
                decisionOver2 += 2*yy+1
            else {
                xx--
                decisionOver2 += 2*(yy-xx)+1
            }
        }
    }

    sub disc(uword cx, ubyte cy, ubyte radius) {
        ; Midpoint algorithm, filled
        ubyte xx = radius
        ubyte yy = 0
        byte decisionOver2 = 1-xx as byte

        while xx>=yy {
            ubyte cy_plus_yy = cy + yy
            ubyte cy_min_yy = cy - yy
            ubyte cy_plus_xx = cy + xx
            ubyte cy_min_xx = cy - xx

            for plotx in cx to cx+xx {
                plot(cy_plus_yy)
                plot(cy_min_yy)
            }
            for plotx in cx-xx to cx-1 {
                plot(cy_plus_yy)
                plot(cy_min_yy)
            }
            for plotx in cx to cx+yy {
                plot(cy_plus_xx)
                plot(cy_min_xx)
            }
            for plotx in cx-yy to cx {
                plot(cy_plus_xx)
                plot(cy_min_xx)
            }
            yy++
            if decisionOver2<=0
                decisionOver2 += 2*yy+1
            else {
                xx--
                decisionOver2 += 2*(yy-xx)+1
            }
        }
    }


; here is the non-asm code for the plot routine below:
;    sub plot_nonasm(uword px, ubyte py) {
;        ubyte[] ormask = [128, 64, 32, 16, 8, 4, 2, 1]
;        uword addr = bitmap_address + 320*(py>>3) + (py & 7) + (px & %0000000111111000)
;        @(addr) |= ormask[lsb(px) & 7]
;    }

    uword plotx     ; 0..319        ; separate 'parameter' for plot()

    asmsub plot(ubyte ploty @A) {           ; plotx is 16 bits 0 to 319... doesn't fit in a register
        %asm {{
        tay
        stx  P8ZP_SCRATCH_REG_X
        lda  plotx+1
        sta  P8ZP_SCRATCH_W2+1
        lsr  a            ; 0
        sta  P8ZP_SCRATCH_W2
        lda  plotx
        pha
        and  #7
        tax

        lda  _y_lookup_lo,y
        clc
        adc  P8ZP_SCRATCH_W2
        sta  P8ZP_SCRATCH_W2
        lda  _y_lookup_hi,y
        adc  P8ZP_SCRATCH_W2+1
        sta  P8ZP_SCRATCH_W2+1

        pla     ; plotx
        and  #%11111000
        tay
        lda  (P8ZP_SCRATCH_W2),y
        ora  _ormask,x
        sta  (P8ZP_SCRATCH_W2),y

        ldx  P8ZP_SCRATCH_REG_X
        rts

_ormask     .byte 128, 64, 32, 16, 8, 4, 2, 1

; note: this can be even faster if we also have a 256 byte x-lookup table, but hey.
; see http://codebase64.org/doku.php?id=base:various_techniques_to_calculate_adresses_fast_common_screen_formats_for_pixel_graphics
; the y lookup tables encodes this formula:  bitmap_address + 320*(py>>3) + (py & 7)    (y from 0..199)
_y_lookup_hi
            .byte  $20, $20, $20, $20, $20, $20, $20, $20, $21, $21, $21, $21, $21, $21, $21, $21
            .byte  $22, $22, $22, $22, $22, $22, $22, $22, $23, $23, $23, $23, $23, $23, $23, $23
            .byte  $25, $25, $25, $25, $25, $25, $25, $25, $26, $26, $26, $26, $26, $26, $26, $26
            .byte  $27, $27, $27, $27, $27, $27, $27, $27, $28, $28, $28, $28, $28, $28, $28, $28
            .byte  $2a, $2a, $2a, $2a, $2a, $2a, $2a, $2a, $2b, $2b, $2b, $2b, $2b, $2b, $2b, $2b
            .byte  $2c, $2c, $2c, $2c, $2c, $2c, $2c, $2c, $2d, $2d, $2d, $2d, $2d, $2d, $2d, $2d
            .byte  $2f, $2f, $2f, $2f, $2f, $2f, $2f, $2f, $30, $30, $30, $30, $30, $30, $30, $30
            .byte  $31, $31, $31, $31, $31, $31, $31, $31, $32, $32, $32, $32, $32, $32, $32, $32
            .byte  $34, $34, $34, $34, $34, $34, $34, $34, $35, $35, $35, $35, $35, $35, $35, $35
            .byte  $36, $36, $36, $36, $36, $36, $36, $36, $37, $37, $37, $37, $37, $37, $37, $37
            .byte  $39, $39, $39, $39, $39, $39, $39, $39, $3a, $3a, $3a, $3a, $3a, $3a, $3a, $3a
            .byte  $3b, $3b, $3b, $3b, $3b, $3b, $3b, $3b, $3c, $3c, $3c, $3c, $3c, $3c, $3c, $3c
            .byte  $3e, $3e, $3e, $3e, $3e, $3e, $3e, $3e

_y_lookup_lo
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07, $40, $41, $42, $43, $44, $45, $46, $47
            .byte $80, $81, $82, $83, $84, $85, $86, $87, $c0, $c1, $c2, $c3, $c4, $c5, $c6, $c7
            .byte $00, $01, $02, $03, $04, $05, $06, $07
        }}
    }

}


