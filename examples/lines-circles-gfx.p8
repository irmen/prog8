%import c64lib
%import c64utils
%zeropage basicsafe


main {

    const uword bitmap_address = $2000


    sub start() {

        ; enable bitmap screen, erase it and set colors to black/white.
        c64.SCROLY |= %00100000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00001000   ; $2000-$3fff
        memset(bitmap_address, 320*200/8, 0)
        c64scr.clear_screen($10, 0)

        lines()
        circles()
        forever {
        }
    }



    sub circles() {
        ubyte xx
        for xx in 3 to 7 {
            circle(xx*50-100, 10+xx*16, (xx+6)*4)
            disc(xx*50-100, 10+xx*16, (xx+6)*2)
        }
    }

    sub lines() {
        ubyte ix
        for ix in 1 to 15 {
            line(10, 10, ix*4, 50)               ; TODO fix lines of lenghts > 128
        }
    }

    sub line(ubyte x1, ubyte y1, ubyte x2, ubyte y2) {
        ; Bresenham algorithm
        byte d = 0
        ubyte dx = abs(x2 - x1)
        ubyte dy = abs(y2 - y1)
        ubyte dx2 = 2 * dx
        ubyte dy2 = 2 * dy
        word ix = sgn(x2 as byte - x1 as byte)
        word iy = sgn(y2 as byte - y1 as byte)
        plotx = x1

        if dx >= dy {
            forever {
                plot(y1)
                if plotx==x2
                    return
                plotx += ix
                d += dy2
                if d > dx {
                    y1 += iy
                    d -= dx2
                }
            }
        } else {
            forever {
                plot(y1)
                if y1 == y2
                    return
                y1 += iy
                d += dx2
                if d > dy {
                    plotx += ix
                    d -= dy2
                }
            }
        }
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm
        ubyte ploty
        ubyte xx = radius
        ubyte yy = 0
        byte decisionOver2 = 1-xx

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
        byte decisionOver2 = 1-xx

        while xx>=yy {
            for plotx in cx to cx+xx {
                plot(cy + yy)
                plot(cy - yy)
            }
            for plotx in cx-xx to cx-1 {
                plot(cy + yy)
                plot(cy - yy)
            }
            for plotx in cx to cx+yy {
                plot(cy + xx)
                plot(cy - xx)
            }
            for plotx in cx-yy to cx {
                plot(cy + xx)
                plot(cy - xx)
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


    uword plotx     ; 0..319

    asmsub plot(ubyte ploty @A) {           ; plotx is 16 bits 0 to 319... doesn't fit in a register
        %asm {{
        tay
        stx  c64.SCRATCH_ZPREGX
        lda  plotx+1
        sta  c64.SCRATCH_ZPWORD2+1
        lsr  a            ; 0
        sta  c64.SCRATCH_ZPWORD2
        lda  plotx
        pha
        and  #7
        tax

        lda  _y_lookup_lo,y
        clc
        adc  c64.SCRATCH_ZPWORD2
        sta  c64.SCRATCH_ZPWORD2
        lda  _y_lookup_hi,y
        adc  c64.SCRATCH_ZPWORD2+1
        sta  c64.SCRATCH_ZPWORD2+1

        pla     ; plotx
        and  #%11111000
        tay
        lda  (c64.SCRATCH_ZPWORD2),y
        ora  _ormask,x
        sta  (c64.SCRATCH_ZPWORD2),y

        ldx  c64.SCRATCH_ZPREGX
        rts

_ormask     .byte 128, 64, 32, 16, 8, 4, 2, 1

; note: this can be even faster if we also have a 256 byte x-lookup table, but hey.
; see http://codebase64.org/doku.php?id=base:various_techniques_to_calculate_adresses_fast_common_screen_formats_for_pixel_graphics
; the y lookup tables encode this formula:  bitmap_address + 320*(py>>3) + (py & 7)    (y from 0..199)
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

; here is the non-asm code for the same plot routine:
;    sub plot_nonasm(uword px, ubyte py) {
;        ubyte[] ormask = [128, 64, 32, 16, 8, 4, 2, 1]
;        uword addr = bitmap_address + 320*(py>>3) + (py & 7) + (px & %0000000111111000)
;        @(addr) |= ormask[lsb(px) & 7]
;    }

}


