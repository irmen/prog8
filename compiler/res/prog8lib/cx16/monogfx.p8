; Monochrome Bitmap pixel graphics routines for the CommanderX16
; Using the full-screen 640x480 and 320x240 screen modes, in 1 bpp mode (black/white).
;
; No text layer is currently shown, but text can be drawn as part of the bitmap itself.
; For color bitmap graphics, see the gfx_lores or gfx_hires libraries.
;
; NOTE: For sake of speed, NO BOUNDS CHECKING is performed in most routines!
;       You'll have to make sure yourself that you're not writing outside of bitmap boundaries!

%import buffers

monogfx {

    %option ignore_unused

    ; read-only control variables:
    uword width = 0
    uword height = 0
    bool lores_mode
    ubyte mode
    const ubyte MODE_NORMAL  = %00000000
    const ubyte MODE_STIPPLE = %00000001
    const ubyte MODE_INVERT  = %00000010

    uword buffer_visible, buffer_back


    sub lores() {
        ; enable 320*240 bitmap mode
        buffer_visible = buffer_back = $0000
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 64
        cx16.VERA_DC_VSCALE = 64
        cx16.VERA_L1_CONFIG = %00000100
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = 0           ; lores
        width = 320
        height = 240
        lores_mode = true
        buffer_visible = buffer_back = $0000
        mode = MODE_NORMAL
        clear_screen(false)
    }

    sub hires() {
        ; enable 640*480 bitmap mode
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 128
        cx16.VERA_DC_VSCALE = 128
        cx16.VERA_L1_CONFIG = %00000100
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = %00000001       ; hires
        width = 640
        height = 480
        lores_mode = false
        buffer_visible = buffer_back = $0000
        mode = MODE_NORMAL
        clear_screen(false)
    }

    sub enable_doublebuffer() {
        ; enable double buffering mode
        if lores_mode {
            buffer_visible = $0000
            buffer_back = $2800
        } else {
            buffer_visible = $0000
            buffer_back = $9800
        }
    }

    sub swap_buffers(bool wait_for_vsync) {
        ; flip the buffers: make the back buffer visible and the other one now the backbuffer.
        ; to avoid any screen tearing it is advised to call this during the vertical blank (pass true)
        if wait_for_vsync
            sys.waitvsync()
        cx16.r0 = buffer_back
        buffer_back = buffer_visible
        buffer_visible = cx16.r0
        cx16.VERA_CTRL = 0
        cx16.r0 &= %1111110000000000
        cx16.VERA_L1_TILEBASE = cx16.VERA_L1_TILEBASE & 1 | (cx16.r0H >>1 )
    }


    sub textmode() {
        ; back to normal text mode
        cx16.r15L = cx16.VERA_DC_VIDEO & %00000111 ; retain chroma + output mode
        cbm.CINT()
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11111000) | cx16.r15L
    }

    sub drawmode(ubyte dm) {
        mode = dm
    }

    sub clear_screen(bool draw) {
        position(0, 0)
        if lores_mode {
            repeat 240/2/8
                cs_innerloop640(draw)
        } else {
            repeat 480/8
                cs_innerloop640(draw)
        }
        position(0, 0)
    }

    sub rect(uword xx, uword yy, uword rwidth, uword rheight, bool draw) {
        if rwidth==0 or rheight==0
            return
        horizontal_line(xx, yy, rwidth, draw)
        if rheight==1
            return
        horizontal_line(xx, yy+rheight-1, rwidth, draw)
        vertical_line(xx, yy+1, rheight-2, draw)
        if rwidth==1
            return
        vertical_line(xx+rwidth-1, yy+1, rheight-2, draw)
    }

    sub fillrect(uword xx, uword yy, uword rwidth, uword rheight, bool draw) {
        ; Draw a filled rectangle of the given size.
        ; To fill the whole screen, use clear_screen(draw) instead - it is much faster.
        if rwidth==0
            return
        repeat rheight {
            horizontal_line(xx, yy, rwidth, draw)
            yy++
        }
    }

    sub horizontal_line(uword xx, uword yy, uword length, bool draw) {
        ubyte[9] masked_starts = [ 0, %00000001, %00000011, %00000111, %00001111, %00011111, %00111111, %01111111, %11111111]
        ubyte[9] masked_ends   = [ 0, %10000000, %11000000, %11100000, %11110000, %11111000, %11111100, %11111110, %11111111]

        if length==0
            return
        if length<=8 {
            ; just use 2 byte writes with shifted mask
            position2(xx,yy)
            %asm {{
                ldy  p8v_length
                lda  p8v_masked_ends,y
                sta  cx16.r0L           ; save left byte
                stz  P8ZP_SCRATCH_B1
                lda  p8v_xx
                and  #7
                beq  +
                tay
                lda  cx16.r0L
-               lsr  a
                ror  P8ZP_SCRATCH_B1
                dey
                bne  -
                sta  cx16.r0L           ; new left byte
+
                lda  p8v_mode
                lsr  a
                bcc  _dontstipple
                ; determine stipple pattern
                lda  p8v_yy
                and  #1
                beq  +
                lda  #%10101010
                bne  ++
+               lda  #%01010101
+               sta  P8ZP_SCRATCH_REG
                lda  cx16.r0L
                and  P8ZP_SCRATCH_REG
                sta  cx16.r0L
                lda  P8ZP_SCRATCH_B1
                and  P8ZP_SCRATCH_REG
                sta  P8ZP_SCRATCH_B1
_dontstipple
                lda  p8v_draw
                beq  _clear
                lda  cx16.r0L           ; left byte
                ora  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                lda  P8ZP_SCRATCH_B1    ; right byte
                ora  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                rts
_clear
                lda  cx16.r0L           ; left byte
                eor  #255
                and  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                lda  P8ZP_SCRATCH_B1    ; right byte
                eor  #255
                and  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                rts
            }}
        }

        ubyte separate_pixels = (8-lsb(xx)) & 7
        if separate_pixels!=0 {
            when mode {
                MODE_NORMAL -> {
                    position(xx,yy)
                    cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                    if draw
                        cx16.VERA_DATA0 |= masked_starts[separate_pixels]
                    else
                        cx16.VERA_DATA0 &= ~masked_starts[separate_pixels]
                    xx += separate_pixels
                }
                MODE_STIPPLE -> {
                    repeat separate_pixels {
                        plot(xx, yy, draw)
                        xx++
                    }
                }
                MODE_INVERT -> {
                    position(xx,yy)
                    cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                    if draw
                        cx16.VERA_DATA0 ^= masked_starts[separate_pixels]
                    else
                        cx16.VERA_DATA0 &= masked_starts[separate_pixels]
                    xx += separate_pixels
                }
            }
            length -= separate_pixels
        }
        if length!=0 {
            position(xx, yy)
            separate_pixels = lsb(length) & 7
            xx += length & $fff8
            %asm {{
                lsr  p8v_length+1
                ror  p8v_length
                lsr  p8v_length+1
                ror  p8v_length
                lsr  p8v_length+1
                ror  p8v_length
                lda  p8v_draw
                bne  +
                ldy  #0     ; black
                bra  _loop
+               lda  p8v_mode
                lsr  a
                bcs  _stipple
                lsr  a
                bcs  _inverted
                ldy  #255       ; normal drawing mode
                bra  _loop

_inverted       lda  #0
                jsr  cx16.vaddr_clone
_invertedloop   lda  p8v_length
                ora  p8v_length+1
                beq  _done
                lda  cx16.VERA_DATA1
                eor  #255
                sta  cx16.VERA_DATA0
                lda  p8v_length
                bne  +
                dec  p8v_length+1
+               dec  p8v_length
                bra  _invertedloop

_stipple        lda  p8v_yy
                and  #1         ; determine stipple pattern to use
                bne  +
                ldy  #%01010101
                bra  _loop
+               ldy  #%10101010
_loop           lda  p8v_length
                ora  p8v_length+1
                beq  _done
                sty  cx16.VERA_DATA0
                lda  p8v_length
                bne  +
                dec  p8v_length+1
+               dec  p8v_length
                bra  _loop
_done
            }}

            when mode {
                MODE_NORMAL -> {
                    cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                    if draw
                        cx16.VERA_DATA0 |= masked_ends[separate_pixels]
                    else
                        cx16.VERA_DATA0 &= ~masked_ends[separate_pixels]
                }
                MODE_STIPPLE -> {
                    repeat separate_pixels {
                        plot(xx, yy, draw)
                        xx++
                    }
                }
                MODE_INVERT -> {
                cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                if draw
                    cx16.VERA_DATA0 ^= masked_ends[separate_pixels]
                else
                    cx16.VERA_DATA0 &= masked_ends[separate_pixels]
                }
            }
        }
        cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off again
    }

    sub safe_horizontal_line(uword xx, uword yy, uword length, bool draw) {
        ; does bounds checking and clipping
        if msb(yy)&$80!=0 or yy>=height
            return
        if msb(xx)&$80!=0 {
            length += xx
            xx = 0
        }
        if xx>=width
            return
        if xx+length>width
            length = width-xx
        if length>width
            return

        horizontal_line(xx, yy, length, draw)
    }

    sub vertical_line(uword xx, uword yy, uword lheight, bool draw) {
        cx16.r15L = monogfx.plot.maskbits[xx as ubyte & 7]           ; bitmask
        if draw {
            %asm {{
                lda  p8v_mode
                and  #p8c_MODE_INVERT
                beq  +
                lda  #$45       ; eor ZP modifying code
                bne  ++
+               lda  #$05       ; ora ZP modifying code
+               sta  drawmode
         }}
            if mode!=MODE_STIPPLE {
                ; draw continuous line.
                position2(xx,yy)
                if lores_mode
                    set_both_strides(11)    ; 40 increment = 1 line in 320 px monochrome
                else
                    set_both_strides(12)    ; 80 increment = 1 line in 640 px monochrome
                repeat lheight {
                    %asm {{
                        lda  cx16.VERA_DATA0
drawmode:               ora  cx16.r15L
                        sta  cx16.VERA_DATA1
                    }}
                }
            } else {
                ; draw stippled line.
                if (xx ^ yy)&1==0  {
                    yy++
                    lheight--
                }
                lheight++   ; because it is divided by 2 later, don't round off the last pixel
                position2(xx,yy)
                if lores_mode
                    set_both_strides(12)    ; 80 increment = 2 line in 320 px monochrome
                else
                    set_both_strides(13)    ; 160 increment = 2 line in 640 px monochrome
                repeat lheight/2 {
                    %asm {{
                        lda  cx16.VERA_DATA0
                        ora  cx16.r15L
                        sta  cx16.VERA_DATA1
                    }}
                }
            }
        } else {
            position2(xx,yy)
            cx16.r15 = ~cx16.r15    ; erase pixels
            if lores_mode
                set_both_strides(11)    ; 40 increment = 1 line in 320 px monochrome
            else
                set_both_strides(12)    ; 80 increment = 1 line in 640 px monochrome
            repeat lheight {
                %asm {{
                    lda  cx16.VERA_DATA0
                    and  cx16.r15L
                    sta  cx16.VERA_DATA1
                }}
            }
        }

        sub set_both_strides(ubyte stride) {
            stride <<= 4
            cx16.VERA_CTRL = 1
            cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | stride
            cx16.VERA_CTRL = 0
            cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | stride
        }

    }

    sub line(uword @zp x1, uword @zp y1, uword @zp x2, uword @zp y2, bool draw) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        if y1>y2 {
            ; make sure dy is always positive to have only 4 instead of 8 special cases
            cx16.r0 = x1
            x1 = x2
            x2 = cx16.r0
            cx16.r0 = y1
            y1 = y2
            y2 = cx16.r0
        }
        word @zp dx = (x2 as word)-x1
        word @zp dy = (y2 as word)-y1

        if dx==0 {
            vertical_line(x1, y1, abs(dy) as uword +1, draw)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            horizontal_line(x1, y1, abs(dx) as uword +1, draw)
            return
        }

        word @zp d = 0
        cx16.r1L = 1 ;; true      ; 'positive_ix'
        if dx < 0 {
            dx = -dx
            cx16.r1L = 0 ;; false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2

        if dx >= dy {
            if cx16.r1L!=0 {
                repeat {
                    plot(x1, y1, draw)
                    if x1==x2
                        return
                    x1++
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            } else {
                repeat {
                    plot(x1, y1, draw)
                    if x1==x2
                        return
                    x1--
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            }
        }
        else {
            if cx16.r1L!=0 {
                repeat {
                    plot(x1, y1, draw)
                    if y1 == y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        x1++
                        d -= dy2
                    }
                }
            } else {
                repeat {
                    plot(x1, y1, draw)
                    if y1 == y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        x1--
                        d -= dy2
                    }
                }
            }
        }
    }

    sub circle(uword @zp xcenter, uword @zp ycenter, ubyte radius, bool draw) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm.
        if radius==0
            return

        ubyte @zp xx = radius
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-xx
        ; R14 = internal plot X
        ; R15 = internal plot Y

        while xx>=yy {
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter + yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter - yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter + xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter - xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()

            yy++
            if decisionOver2>=0 {
                xx--
                decisionOver2 -= xx*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }

        sub plotq() {
            ; cx16.r14 = x, cx16.r15 = y, draw=draw
            plot(cx16.r14, cx16.r15, draw)
        }
    }

    sub safe_circle(uword @zp xcenter, uword @zp ycenter, ubyte radius, bool draw) {
        ; Does bounds checking and clipping.
        ; Midpoint algorithm.
        if radius==0
            return

        ubyte @zp xx = radius
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-xx
        ; R14 = internal plot X
        ; R15 = internal plot Y

        while xx>=yy {
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter + yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter - yy
            plotq()
            cx16.r14 = xcenter - xx
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter + xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter - xx
            plotq()
            cx16.r14 = xcenter - yy
            plotq()

            yy++
            if decisionOver2>=0 {
                xx--
                decisionOver2 -= xx*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }

        sub plotq() {
            ; cx16.r14 = x, cx16.r15 = y, draw=draw
            safe_plot(cx16.r14, cx16.r15, draw)
        }
    }

    sub disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, bool draw) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm, filled
        ; Note: has problems with INVERT draw mode because of horizontal span overdrawing. Horizontal lines may occur.
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        uword last_y3 = ycenter+radius
        uword last_y4 = ycenter-radius
        uword new_y3, new_y4

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, draw)
            horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, draw)
            new_y3 = ycenter+radius
            if new_y3 != last_y3 {
                horizontal_line(xcenter-yy, last_y3, yy*$0002+1, draw)
                last_y3 = new_y3
            }
            new_y4 = ycenter-radius
            if new_y4 != last_y4 {
                horizontal_line(xcenter-yy, last_y4, yy*$0002+1, draw)
                last_y4 = new_y4
            }
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
        ; draw the final two spans
        yy--
        horizontal_line(xcenter-yy, last_y3, yy*$0002+1, draw)
        horizontal_line(xcenter-yy, last_y4, yy*$0002+1, draw)
    }

    sub safe_disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, bool draw) {
        ; Does bounds checking and clipping.
        ; Midpoint algorithm, filled
        ; Note: has problems with INVERT draw mode because of horizontal span overdrawing. Horizontal lines may occur.
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        uword last_y3 = ycenter+radius
        uword last_y4 = ycenter-radius
        uword new_y3, new_y4

        while radius>=yy {
            safe_horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, draw)
            safe_horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, draw)
            new_y3 = ycenter+radius
            if new_y3 != last_y3 {
                safe_horizontal_line(xcenter-yy, last_y3, yy*$0002+1, draw)
                last_y3 = new_y3
            }
            new_y4 = ycenter-radius
            if new_y4 != last_y4 {
                safe_horizontal_line(xcenter-yy, last_y4, yy*$0002+1, draw)
                last_y4 = new_y4
            }
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
        ; draw the final two spans
        yy--
        safe_horizontal_line(xcenter-yy, last_y3, yy*$0002+1, draw)
        safe_horizontal_line(xcenter-yy, last_y4, yy*$0002+1, draw)
    }

    sub plot(uword @zp xx, uword @zp yy, bool @zp draw) {
        ubyte[8] @shared maskbits = [128, 64, 32, 16, 8, 4, 2, 1]
        if draw {
            ; solid color or perhaps stipple
            %asm {{
                lda  p8v_mode
                lsr  a
                bcs  +
                lsr  a
                bcs  p8l_invert
                bra  p8l_nostipple
+               ; stipple mode
                lda  p8v_xx
                eor  p8v_yy
                and  #1
            }}
            if_nz {
nostipple:
                prepare()
                %asm {{
                    tsb  cx16.VERA_DATA0
                }}
            } else {
                prepare()
                %asm {{
                    trb  cx16.VERA_DATA0
                }}
            }
        } else {
            ; only erase
            prepare()
            %asm {{
                trb  cx16.VERA_DATA0
            }}
        }
        return

invert:
        prepare()
        %asm {{
            eor  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
        }}
        return

        sub prepare() {
            if lores_mode {
                %asm {{
                    stz  cx16.VERA_CTRL
                    stz  cx16.VERA_ADDR_H

                    lda  p8v_xx+1
                    lsr  a
                    lda  p8v_xx
                    ror  a
                    lsr  a
                    lsr  a

                    clc
                    ldy  p8v_yy
                    adc  p8v_times40_lsb,y
                    sta  cx16.VERA_ADDR_L
                    lda  p8v_times40_msb,y
                    adc  p8v_buffer_back+1
                    sta  cx16.VERA_ADDR_M

                    lda  p8v_xx
                    and  #7
                    tax
                    lda  p8v_maskbits,x
                }}
            } else {
                ; width=640 (hires)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  p8v_xx
                    and  #7
                    pha     ; xbits

                    ; xx /= 8
                    lsr  p8v_xx+1
                    ror  p8v_xx
                    lsr  p8v_xx+1
                    ror  p8v_xx
                    lsr  p8v_xx
                }}
                ;xx /= 8
                xx += yy*(640/8)
                %asm {{
                    lda  p8v_xx
                    sta  cx16.VERA_ADDR_L
                    lda  p8v_xx+1
                    clc
                    adc  p8v_buffer_back+1
                    sta  cx16.VERA_ADDR_M
                    lda  #0
                    rol  a   ; hi bit carry also needed when double-buffering
                    sta  cx16.VERA_ADDR_H
                    plx     ; xbits
                    lda  p8v_maskbits,x
                }}
            }
        }
    }

    sub safe_plot(uword xx, uword yy, bool draw) {
        ; A plot that does bounds checks to see if the pixel is inside the screen.
        if msb(xx)&$80!=0 or msb(yy)&$80!=0
            return
        if xx >= width or yy >= height
            return
        plot(xx, yy, draw)
    }

    sub pget(uword @zp xx, uword yy) -> bool {
        %asm {{
            lda  p8v_xx
            and  #7
            pha     ; xbits
        }}
        xx /= 8
        if lores_mode {
            %asm {{
                ; xx += yy * 40
                ldy  p8v_yy
                lda  p8v_xx
                clc
                adc  p8v_times40_lsb,y
                sta  p8v_xx
                lda  p8v_xx+1
                adc  p8v_times40_msb,y
                sta  p8v_xx+1
            }}
        }
        else
            xx += yy*(640/8)

        %asm {{
            stz  cx16.VERA_CTRL
            lda  p8v_xx
            sta  cx16.VERA_ADDR_L
            lda  p8v_xx+1
            clc
            adc  p8v_buffer_back+1
            sta  cx16.VERA_ADDR_M
            lda  #0
            rol  a   ; hi bit carry also needed when double-buffering
            sta  cx16.VERA_ADDR_H
            ply         ; xbits
            lda  p8s_plot.p8v_maskbits,y
            and  cx16.VERA_DATA0
            beq  +
            lda  #1
+           rts
        }}
    }

    sub fill(uword x, uword y, bool draw, ubyte stack_rambank) {
        ; Non-recursive scanline flood fill.
        ; based loosely on code found here https://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm
        ; with the fixes applied to the seedfill_4 routine as mentioned in the comments.
        ; Also see https://lodev.org/cgtutor/floodfill.html
        word @zp xx = x as word
        word @zp yy = y as word
        word x1
        word x2
        byte dy
        stack.init(stack_rambank)
        cx16.r10L = draw as ubyte
        sub push_stack(word sxl, word sxr, word sy, byte sdy) {
            cx16.r0s = sy+sdy
            if cx16.r0s>=0 and cx16.r0s<=height-1 {
                stack.pushw(sxl as uword)
                stack.pushw(sxr as uword)
                stack.pushw(sy as uword)
                stack.push(sdy as ubyte)
            }
        }
        sub pop_stack() {
            dy = stack.pop() as byte
            yy = stack.popw() as word
            x2 = stack.popw() as word
            x1 = stack.popw() as word
            yy+=dy
        }
        cx16.r11L = pget(xx as uword, yy as uword) as ubyte        ; old_color
        if cx16.r11L == cx16.r10L
            return
        if xx<0 or xx>width-1 or yy<0 or yy>height-1
            return
        push_stack(xx, xx, yy, 1)
        push_stack(xx, xx, yy + 1, -1)
        word left = 0
        while not stack.isempty() {
            pop_stack()
            xx = x1
            if fill_scanline_left() goto skip
            left = xx + 1
            if left < x1
                push_stack(left, x1 - 1, yy, -dy)
            xx = x1 + 1

            do {
                fill_scanline_right()
                push_stack(left, xx - 1, yy, dy)
                if xx > x2 + 1
                    push_stack(x2 + 1, xx - 1, yy, -dy)
skip:
                xx++
                while xx <= x2 {
                    if pget(xx as uword, yy as uword) as ubyte == cx16.r11L
                        break
                    xx++
                }
                left = xx
            } until xx>x2
        }

        sub fill_scanline_left() -> bool {
            ; TODO maybe this could use vera auto decrement, but that requires some clever masking calculations
            cx16.r9s = xx
            while xx >= 0 {
                if pgetset()
                    break
                xx--
            }
            return xx==cx16.r9s
        }

        sub fill_scanline_right() {
            ; TODO maybe this could use vera auto increment, but that requires some clever masking calculations
            cx16.r9s = xx
            while xx <= width-1 {
                if pgetset()
                    break
                xx++
            }
        }

        sub pgetset() -> bool {
            ; test and optionally set a pixel
            word @zp xpos = xx
            %asm {{
                lda  p8v_xpos
                and  #7
                pha     ; xbits
            }}
            xpos /= 8
            if lores_mode {
                %asm {{
                    ; xpos += yy*40
                    ldy  p8v_yy
                    lda  p8v_xpos
                    clc
                    adc  p8v_times40_lsb,y
                    sta  p8v_xpos
                    lda  p8v_xpos+1
                    adc  p8v_times40_msb,y
                    sta  p8v_xpos+1
                }}
            }
            else
                xpos += yy*(640/8) as uword

            %asm {{
                stz  cx16.VERA_CTRL
                lda  p8v_xpos
                sta  cx16.VERA_ADDR_L
                lda  p8v_xpos+1
                clc
                adc  p8v_buffer_back+1
                sta  cx16.VERA_ADDR_M
                lda  #0
                rol  a   ; hi bit carry also needed when double-buffering
                sta  cx16.VERA_ADDR_H
                ply         ; xbits
                lda  p8s_plot.p8v_maskbits,y
                and  cx16.VERA_DATA0
                beq  +
                lda  #1
+
                ; cx16.r11L = seed color to check against
                eor  cx16.r11L
                beq  +
                rts
+               ; cx16.r10L = new color to set, check stipple mode
                lda  p8v_mode
                and  #1
                beq  _normal
                ; stipple drawing
                lda  p8v_xx
                eor  p8v_yy
                and  #1
                php
                lda  p8s_plot.p8v_maskbits,y
                plp
                bra  _doplot

_normal         ; cx16.r10L = new color to set
                lda  p8s_plot.p8v_maskbits,y
                ldx  cx16.r10L
_doplot         beq  +
                tsb  cx16.VERA_DATA0
                bra  ++
+               trb  cx16.VERA_DATA0
+               lda  #0
                rts
            }}
        }
    }

    sub position(uword xx, uword yy) {
        if lores_mode {
            %asm {{
                stz  cx16.VERA_CTRL
                lda  p8v_xx+1
                lsr  a
                lda  p8v_xx
                ror  a
                lsr  a
                lsr  a
                ldy  p8v_yy
                clc
                adc  p8v_times40_lsb,y
                sta  cx16.VERA_ADDR_L
                lda  p8v_times40_msb,y
                adc  p8v_buffer_back+1
                sta  cx16.VERA_ADDR_M
                lda  #%00010000     ; autoincr
                sta  cx16.VERA_ADDR_H
            }}
        }
        else {
            cx16.r0 = yy*(640/8)
            ;cx16.r0 += xx/8
            %asm {{
            	ldy  p8v_xx+1
                lda  p8v_xx
                sty  P8ZP_SCRATCH_B1
                lsr  P8ZP_SCRATCH_B1
                ror  a
                lsr  P8ZP_SCRATCH_B1
                ror  a
                lsr  a
                clc
                adc  cx16.r0
                sta  cx16.r0
                bcc  +
                inc  cx16.r0+1
+
                stz  cx16.VERA_CTRL
                lda  cx16.r0L
                sta  cx16.VERA_ADDR_L
                lda  cx16.r0H
                clc
                adc  p8v_buffer_back+1
                sta  cx16.VERA_ADDR_M
                lda  #%00001000     ; autoincr (1 bit shifted)
                rol  a              ; hi bit carry also needed when double-buffering
                sta  cx16.VERA_ADDR_H
            }}
        }
        return
    }

    sub position2(uword xx, uword yy) {
        position(xx, yy)
        ; also set port 1 like that
        cx16.vaddr_clone(0)
    }

    ; y*40 lookup table. Pretty compact because it all fits in a word and we only need 240 y positions.
    ; a y*80 lookup table would be very large (lo,mid,hi for 480 values...)
    uword[240] @split @shared times40 = [
        0, 40, 80, 120, 160, 200, 240, 280, 320, 360, 400, 440, 480, 520, 560, 600,
        640, 680, 720, 760, 800, 840, 880, 920, 960, 1000, 1040, 1080, 1120, 1160,
        1200, 1240, 1280, 1320, 1360, 1400, 1440, 1480, 1520, 1560, 1600, 1640, 1680,
        1720, 1760, 1800, 1840, 1880, 1920, 1960, 2000, 2040, 2080, 2120, 2160, 2200,
        2240, 2280, 2320, 2360, 2400, 2440, 2480, 2520, 2560, 2600, 2640, 2680, 2720,
        2760, 2800, 2840, 2880, 2920, 2960, 3000, 3040, 3080, 3120, 3160, 3200, 3240,
        3280, 3320, 3360, 3400, 3440, 3480, 3520, 3560, 3600, 3640, 3680, 3720, 3760,
        3800, 3840, 3880, 3920, 3960, 4000, 4040, 4080, 4120, 4160, 4200, 4240, 4280,
        4320, 4360, 4400, 4440, 4480, 4520, 4560, 4600, 4640, 4680, 4720, 4760, 4800,
        4840, 4880, 4920, 4960, 5000, 5040, 5080, 5120, 5160, 5200, 5240, 5280, 5320,
        5360, 5400, 5440, 5480, 5520, 5560, 5600, 5640, 5680, 5720, 5760, 5800, 5840,
        5880, 5920, 5960, 6000, 6040, 6080, 6120, 6160, 6200, 6240, 6280, 6320, 6360,
        6400, 6440, 6480, 6520, 6560, 6600, 6640, 6680, 6720, 6760, 6800, 6840, 6880,
        6920, 6960, 7000, 7040, 7080, 7120, 7160, 7200, 7240, 7280, 7320, 7360, 7400,
        7440, 7480, 7520, 7560, 7600, 7640, 7680, 7720, 7760, 7800, 7840, 7880, 7920,
        7960, 8000, 8040, 8080, 8120, 8160, 8200, 8240, 8280, 8320, 8360, 8400, 8440,
        8480, 8520, 8560, 8600, 8640, 8680, 8720, 8760, 8800, 8840, 8880, 8920, 8960,
        9000, 9040, 9080, 9120, 9160, 9200, 9240, 9280, 9320, 9360, 9400, 9440, 9480,
        9520, 9560]

    const ubyte charset_bank = $1
    const uword charset_addr = $f000       ; in bank 1, so $1f000

    sub text_charset(ubyte charset) {
        ; -- select the text charset to use with the text() routine
        ;    the charset number is the same as for the cx16.screen_set_charset() ROM function.
        ;    1 = ISO charset, 2 = PETSCII uppercase+graphs, 3= PETSCII uppercase+lowercase.
        cx16.screen_set_charset(charset, 0)
    }

    sub text(uword @zp xx, uword yy, bool draw, str sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        cx16.r3 = sctextptr
        %asm {{
            lda  p8v_mode
            cmp  #p8c_MODE_INVERT
            beq  +
            lda  #$0d       ; ORA abs   modifying code
            bne  ++
+           lda  #$4d       ; EOR abs   modifying code
+           sta  cdraw_mod1
            sta  cdraw_mod2
        }}

        while @(cx16.r3)!=0 {
            chardataptr = charset_addr + @(cx16.r3) * $0008
            ; copy the character bitmap into RAM
            cx16.vaddr_autoincr(charset_bank, chardataptr, 0, 1)
            %asm {{
                ; pre-shift the bits
                lda  p8s_text.p8v_xx
                and  #7
                sta  P8ZP_SCRATCH_B1
                ldy  #0
-               lda  cx16.VERA_DATA0
                stz  P8ZP_SCRATCH_REG
                ldx  P8ZP_SCRATCH_B1
                cpx  #0
                beq  +
-               lsr  a
                ror  P8ZP_SCRATCH_REG
                dex
                bne  -
+               sta  p8v_char_bitmap_bytes_left,y
                lda  P8ZP_SCRATCH_REG
                sta  p8v_char_bitmap_bytes_right,y
                iny
                cpy  #8
                bne  --
            }}
            ; left part of shifted char
            position2(xx, yy)
            set_autoincrs()
            if draw {
                %asm {{
                    ldy  #0
-                   lda  p8v_char_bitmap_bytes_left,y
cdraw_mod1          ora  cx16.VERA_DATA1
                    sta  cx16.VERA_DATA0
                    iny
                    cpy  #8
                    bne  -
                }}
            } else {
                %asm {{
                    ldy  #0
-                   lda  p8v_char_bitmap_bytes_left,y
                    eor  #255
                    and  cx16.VERA_DATA1
                    sta  cx16.VERA_DATA0
                    iny
                    cpy  #8
                    bne  -
                }}
            }
            ; right part of shifted char
            if lsb(xx) & 7 !=0 {
                position2(xx+8, yy)
                set_autoincrs()
                if draw {
                    %asm {{
                        ldy  #0
-                       lda  p8v_char_bitmap_bytes_right,y
cdraw_mod2              ora  cx16.VERA_DATA1
                        sta  cx16.VERA_DATA0
                        iny
                        cpy  #8
                        bne  -
                    }}
                } else {
                    %asm {{
                        ldy  #0
-                       lda  p8v_char_bitmap_bytes_right,y
                        eor  #255
                        and  cx16.VERA_DATA1
                        sta  cx16.VERA_DATA0
                        iny
                        cpy  #8
                        bne  -
                    }}
                }
            }
            cx16.r3++
            xx += 8
        }

        sub set_autoincrs() {
            ; set autoincrements to go to next pixel row (40 or 80 increment)
            if lores_mode {
                cx16.VERA_CTRL = 1
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (11<<4)
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (11<<4)
            } else {
                cx16.VERA_CTRL = 1
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (12<<4)
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (12<<4)
            }
        }
    }

    asmsub cs_innerloop640(bool draw @A) clobbers(Y) {
        ; using verafx 32 bits writes here would make this faster but it's safer to
        ; use verafx only explicitly when you know what you're doing.
        %asm {{
            cmp  #0
            beq  +
            lda  #255
+           ldy  #40
-
            .rept  16
            sta  cx16.VERA_DATA0
            .endrept
            dey
            bne  -
            rts
        }}
    }
}
