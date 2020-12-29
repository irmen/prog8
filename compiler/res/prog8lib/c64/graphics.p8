%target c64
%import textio

; bitmap pixel graphics module for the C64
; only black/white monchrome 320x200 for now
; assumes bitmap screen memory is $2000-$3fff

graphics {
    const uword BITMAP_ADDRESS = $2000
    const uword WIDTH = 320
    const ubyte HEIGHT = 200

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        c64.SCROLY = %00111011
        c64.SCROLX = %00001000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00001000   ; $2000-$3fff
        clear_screen(1, 0)
    }

    sub disable_bitmap_mode() {
        ; enables text mode, erase the text screen, color white
        c64.SCROLY = %00011011
        c64.SCROLX = %00001000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00000100   ; $1000-$2fff
        txt.fill_screen(' ', 1)
    }

    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        memset(BITMAP_ADDRESS, 320*200/8, 0)
        txt.fill_screen(pixelcolor << 4 | bgcolor, 0)
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        ; TODO rewrite this in optimized assembly
        if y1>y2 {
            ; make sure dy is always positive to have only 4 instead of 8 special cases
            swap(x1, x2)
            swap(y1, y2)
        }
        word @zp dx = x2-x1 as word
        word @zp dy = y2-y1

        if dx==0 {
            vertical_line(x1, y1, abs(dy)+1 as ubyte)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            horizontal_line(x1, y1, abs(dx)+1 as uword)
            return
        }

        word @zp d = 0
        ubyte positive_ix = true
        if dx < 0 {
            dx = -dx
            positive_ix = false
        }
        dx *= 2
        dy *= 2
        internal_plotx = x1

        if dx >= dy {
            if positive_ix {
                repeat {
                    internal_plot(y1)
                    if internal_plotx==x2
                        return
                    internal_plotx++
                    d += dy
                    if d > dx {
                        y1++
                        d -= dx
                    }
                }
            } else {
                repeat {
                    internal_plot(y1)
                    if internal_plotx==x2
                        return
                    internal_plotx--
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
                    internal_plot(y1)
                    if y1 == y2
                        return
                    y1++
                    d += dx
                    if d > dy {
                        internal_plotx++
                        d -= dy
                    }
                }
            } else {
                repeat {
                    internal_plot(y1)
                    if y1 == y2
                        return
                    y1++
                    d += dx
                    if d > dy {
                        internal_plotx--
                        d -= dy
                    }
                }
            }
        }
    }

    sub rect(uword x, ubyte y, uword width, ubyte height) {
        if width==0 or height==0
            return
        horizontal_line(x, y, width)
        if height==1
            return
        horizontal_line(x, y+height-1, width)
        vertical_line(x, y+1, height-2)
        if width==1
            return
        vertical_line(x+width-1, y+1, height-2)
    }

    sub fillrect(uword x, ubyte y, uword width, ubyte height) {
        if width==0
            return
        repeat height {
            horizontal_line(x, y, width)
            y++
        }
    }

    sub horizontal_line(uword x, ubyte y, uword length) {
        if not length
            return

        ; TODO optimized drawing 8 pixels at a time (+edges) should use  get_y_lookup(y) in this
        internal_plotx = x
        repeat length {
            internal_plot(y)
            internal_plotx++
        }
    }

    sub vertical_line(uword x, ubyte y, ubyte height) {
        internal_plotx = x
        repeat height {
            internal_plot(y)
            y++
        }
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm
        if radius==0
            return
        ubyte @zp ploty
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            internal_plotx = xcenter + radius
            ploty = ycenter + yy
            internal_plot(ploty)
            internal_plotx = xcenter - radius
            internal_plot(ploty)
            internal_plotx = xcenter + radius
            ploty = ycenter - yy
            internal_plot(ploty)
            internal_plotx = xcenter - radius
            internal_plot(ploty)
            internal_plotx = xcenter + yy
            ploty = ycenter + radius
            internal_plot(ploty)
            internal_plotx = xcenter - yy
            internal_plot(ploty)
            internal_plotx = xcenter + yy
            ploty = ycenter - radius
            internal_plot(ploty)
            internal_plotx = xcenter - yy
            internal_plot(ploty)
            yy++
            if decisionOver2<=0
                decisionOver2 += (yy as word)*2+1
            else {
                radius--
                decisionOver2 += (yy as word -radius)*2+1
            }
        }
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word decisionOver2 = (1 as word)-radius

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*2+1)
            horizontal_line(xcenter-radius, ycenter-yy, radius*2+1)
            horizontal_line(xcenter-yy, ycenter+radius, yy*2+1)
            horizontal_line(xcenter-yy, ycenter-radius, yy*2+1)
            yy++
            if decisionOver2<=0
                decisionOver2 += (yy as word)*2+1
            else {
                radius--
                decisionOver2 += (yy as word -radius)*2+1
            }
        }
    }


; here is the non-asm code for the plot routine below:
;    sub plot_nonasm(uword px, ubyte py) {
;        ubyte[] ormask = [128, 64, 32, 16, 8, 4, 2, 1]
;        uword addr = BITMAP_ADDRESS + 320*(py>>3) + (py & 7) + (px & %0000000111111000)
;        @(addr) |= ormask[lsb(px) & 7]
;    }

    inline asmsub  plot(uword plotx @XY, ubyte ploty @A) clobbers (A, X, Y) {
        %asm {{
            stx  graphics.internal_plotx
            sty  graphics.internal_plotx+1
            jsr  graphics.internal_plot
        }}
    }

    ; for efficiency of internal algorithms here is the internal plot routine
    ; that takes the plotx coordinate in a separate variable instead of the XY register pair:

    uword internal_plotx     ; 0..319        ; separate 'parameter' for internal_plot()

    asmsub  internal_plot(ubyte ploty @A) clobbers (A, X, Y) {      ; internal_plotx is 16 bits 0 to 319... doesn't fit in a register
        %asm {{
        tay
        lda  internal_plotx+1
        sta  P8ZP_SCRATCH_W2+1
        lsr  a            ; 0
        sta  P8ZP_SCRATCH_W2
        lda  internal_plotx
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

        pla     ; internal_plotx
        and  #%11111000
        tay
        lda  (P8ZP_SCRATCH_W2),y
        ora  _ormask,x
        sta  (P8ZP_SCRATCH_W2),y
        rts

_ormask     .byte 128, 64, 32, 16, 8, 4, 2, 1

; note: this can be even faster if we also have a 256 byte x-lookup table, but hey.
; see http://codebase64.org/doku.php?id=base:various_techniques_to_calculate_adresses_fast_common_screen_formats_for_pixel_graphics
; the y lookup tables encodes this formula:  BITMAP_ADDRESS + 320*(py>>3) + (py & 7)    (y from 0..199)
; We use the 64tass syntax for range expressions to calculate this table on assembly time.

_plot_y_values := $2000 + 320*(range(200)>>3) + (range(200) & 7)

_y_lookup_lo    .byte  <_plot_y_values
_y_lookup_hi    .byte  >_plot_y_values

        }}
    }

    asmsub get_y_lookup(ubyte y @Y) -> uword @AY {
        %asm {{
            lda  _y_lookup_lo,y
            pha
            lda  _y_lookup_hi,y
            tay
            pla
            rts
        }}
    }

}


