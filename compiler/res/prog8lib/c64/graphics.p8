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
        c64.SCROLY |= %00100000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00001000   ; $2000-$3fff
        clear_screen(1, 0)
    }

    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        memset(BITMAP_ADDRESS, 320*200/8, 0)
        txt.fill_screen(pixelcolor << 4 | bgcolor, 0)
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        ; Bresenham algorithm.
        ; This code special cases various quadrant loops to allow simple ++ and -- operations.
        ; TODO rewrite this in optimized assembly
        if y1>y2 {
            ; make sure dy is always positive to avoid 8 instead of just 4 special cases
            swap(x1, x2)
            swap(y1, y2)
        }
        word @zp d = 0
        ubyte positive_ix = true
        word @zp dx = x2-x1 as word
        word @zp dy = y2-y1
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

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm
        ubyte @zp ploty
        ubyte @zp xx = radius
        ubyte @zp yy = 0
        byte @zp decisionOver2 = 1-xx as byte

        while xx>=yy {
            internal_plotx = xcenter + xx
            ploty = ycenter + yy
            internal_plot(ploty)
            internal_plotx = xcenter - xx
            internal_plot(ploty)
            internal_plotx = xcenter + xx
            ploty = ycenter - yy
            internal_plot(ploty)
            internal_plotx = xcenter - xx
            internal_plot(ploty)
            internal_plotx = xcenter + yy
            ploty = ycenter + xx
            internal_plot(ploty)
            internal_plotx = xcenter - yy
            internal_plot(ploty)
            internal_plotx = xcenter + yy
            ploty = ycenter - xx
            internal_plot(ploty)
            internal_plotx = xcenter - yy
            internal_plot(ploty)
            yy++
            if decisionOver2<=0
                decisionOver2 += 2*yy+1
            else {
                xx--
                decisionOver2 += 2*(yy-xx)+1
            }
        }
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm, filled
        ubyte xx = radius
        ubyte yy = 0
        byte decisionOver2 = 1-xx as byte

        while xx>=yy {
            ubyte ycenter_plus_yy = ycenter + yy
            ubyte ycenter_min_yy = ycenter - yy
            ubyte ycenter_plus_xx = ycenter + xx
            ubyte ycenter_min_xx = ycenter - xx

            for internal_plotx in xcenter to xcenter+xx {
                internal_plot(ycenter_plus_yy)
                internal_plot(ycenter_min_yy)
            }
            for internal_plotx in xcenter-xx to xcenter-1 {
                internal_plot(ycenter_plus_yy)
                internal_plot(ycenter_min_yy)
            }
            for internal_plotx in xcenter to xcenter+yy {
                internal_plot(ycenter_plus_xx)
                internal_plot(ycenter_min_xx)
            }
            for internal_plotx in xcenter-yy to xcenter {
                internal_plot(ycenter_plus_xx)
                internal_plot(ycenter_min_xx)
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
;        uword addr = BITMAP_ADDRESS + 320*(py>>3) + (py & 7) + (px & %0000000111111000)
;        @(addr) |= ormask[lsb(px) & 7]
;    }

    asmsub  plot(uword plotx @XY, ubyte ploty @A) clobbers (A, X, Y) {
        %asm {{
            stx  internal_plotx
            sty  internal_plotx+1
            jmp  internal_plot
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

}


