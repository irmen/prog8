; Monochrome Bitmap pixel graphics routines for the CommanderX16
; Using the full-screen 640x480 and 320x240 screen modes, in 1 bpp mode (black/white).
;
; No text layer is currently shown, but text can be drawn as part of the bitmap itself.
; For color bitmap graphics, see the gfx2 library.
;
; NOTE: a lot of the code here is similar or the same to that in gfx2
; NOTE: For sake of speed, NO BOUNDS CHECKING is performed in most routines!
;       You'll have to make sure yourself that you're not writing outside of bitmap boundaries!
;

monogfx {

    %option no_symbol_prefixing

    ; read-only control variables:
    uword width = 0
    uword height = 0
    bool dont_stipple_flag = true            ; set to false to enable stippling mode

    sub lores() {
        ; enable 320*240 bitmap mode
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 64
        cx16.VERA_DC_VSCALE = 64
        cx16.VERA_L1_CONFIG = %00000100
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = 0
        width = 320
        height = 240
        clear_screen(0)
    }

    sub hires() {
        ; enable 640*480 bitmap mode
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 128
        cx16.VERA_DC_VSCALE = 128
        cx16.VERA_L1_CONFIG = %00000100
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = %00000001
        width = 640
        height = 480
        clear_screen(0)
    }

    sub textmode() {
        ; back to normal text mode
        cx16.r15L = cx16.VERA_DC_VIDEO & %00000111 ; retain chroma + output mode
        cbm.CINT()
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11111000) | cx16.r15L
    }

    sub clear_screen(ubyte color) {
        stipple(false)
        position(0, 0)
        when width {
            320 -> {
                repeat 240/2/8
                    cs_innerloop640(color)
            }
            640 -> {
                repeat 480/8
                    cs_innerloop640(color)
            }
        }
        position(0, 0)
    }

    sub stipple(bool enable) {
        dont_stipple_flag = not enable
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
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
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
            position2(xx,yy,true)
            %asm {{
                ldy  length
                lda  masked_ends,y
                sta  cx16.r0L           ; save left byte
                stz  P8ZP_SCRATCH_B1
                lda  xx
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
                lda  dont_stipple_flag
                bne  _dontstipple
                ; determine stipple pattern
                lda  yy
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
                lda  draw
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
        if separate_pixels {
            if dont_stipple_flag {
                position(xx,yy)
                cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                if draw
                    cx16.VERA_DATA0 |= masked_starts[separate_pixels]
                else
                    cx16.VERA_DATA0 &= ~masked_starts[separate_pixels]
                xx += separate_pixels
            } else {
                repeat separate_pixels {
                    plot(xx, yy, draw)
                    xx++
                }
            }
            length -= separate_pixels
        }
        if length {
            position(xx, yy)
            separate_pixels = lsb(length) & 7
            xx += length & $fff8
            %asm {{
                lsr  length+1
                ror  length
                lsr  length+1
                ror  length
                lsr  length+1
                ror  length
                lda  draw
                bne  +
                ldy  #0     ; black
                bra  _loop
+               lda  dont_stipple_flag
                beq  _stipple
                ldy  #255       ; don't stipple
                bra  _loop
_stipple        lda  yy
                and  #1         ; determine stipple pattern to use
                bne  +
                ldy  #%01010101
                bra  _loop
+               ldy  #%10101010
_loop           lda  length
                ora  length+1
                beq  _done
                sty  cx16.VERA_DATA0
                lda  length
                bne  +
                dec  length+1
+               dec  length
                bra  _loop
_done
            }}

            if dont_stipple_flag {
                cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                if draw
                    cx16.VERA_DATA0 |= masked_ends[separate_pixels]
                else
                    cx16.VERA_DATA0 &= ~masked_ends[separate_pixels]
            } else {
                repeat separate_pixels {
                    plot(xx, yy, draw)
                    xx++
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
            if dont_stipple_flag {
                ; draw continuous line.
                position2(xx,yy,true)
                if width==320
                    set_both_strides(11)    ; 40 increment = 1 line in 320 px monochrome
                else
                    set_both_strides(12)    ; 80 increment = 1 line in 640 px monochrome
                repeat lheight {
                    %asm {{
                        lda  cx16.VERA_DATA0
                        ora  cx16.r15L
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
                position2(xx,yy,true)
                if width==320
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
            position2(xx,yy,true)
            cx16.r15 = ~cx16.r15    ; erase pixels
            if width==320
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
            cx16.VERA_CTRL = 0
            cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | stride
            cx16.VERA_CTRL = 1
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
        cx16.r1L = true      ; 'positive_ix'
        if dx < 0 {
            dx = -dx
            cx16.r1L = false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2
        cx16.r14 = x1       ; internal plot X

        if dx >= dy {
            if cx16.r1L {
                repeat {
                    plot(cx16.r14, y1, draw)
                    if cx16.r14==x2
                        return
                    cx16.r14++
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            } else {
                repeat {
                    plot(cx16.r14, y1, draw)
                    if cx16.r14==x2
                        return
                    cx16.r14--
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            }
        }
        else {
            if cx16.r1L {
                repeat {
                    plot(cx16.r14, y1, draw)
                    if y1 == y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        cx16.r14++
                        d -= dy2
                    }
                }
            } else {
                repeat {
                    plot(cx16.r14, y1, draw)
                    if y1 == y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        cx16.r14--
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
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, draw)
            horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, draw)
            horizontal_line(xcenter-yy, ycenter+radius, yy*$0002+1, draw)
            horizontal_line(xcenter-yy, ycenter-radius, yy*$0002+1, draw)
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
    }

    sub safe_disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, bool draw) {
        ; Does bounds checking and clipping.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            safe_horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, draw)
            safe_horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, draw)
            safe_horizontal_line(xcenter-yy, ycenter+radius, yy*$0002+1, draw)
            safe_horizontal_line(xcenter-yy, ycenter-radius, yy*$0002+1, draw)
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
    }

    sub plot(uword @zp xx, uword @zp yy, bool @zp draw) {
        ubyte[8] @shared maskbits = [128, 64, 32, 16, 8, 4, 2, 1]
        if draw {
            ; solid color or perhaps stipple
            %asm {{
                lda  xx
                eor  yy
                ora  dont_stipple_flag
                and  #1
            }}
            if_nz {
                prepare()
                %asm {{
                    tsb  cx16.VERA_DATA0
                }}
            }
        } else {
            ; only erase
            prepare()
            %asm {{
                trb  cx16.VERA_DATA0
            }}
        }

        sub prepare() {
            %asm {{
                lda  xx
                and  #7
                pha     ; xbits
            }}
            xx /= 8
            if width==320
                xx += yy*(320/8)
            else
                xx += yy*(640/8)
            %asm {{
                stz  cx16.VERA_CTRL
                stz  cx16.VERA_ADDR_H
                lda  xx+1
                sta  cx16.VERA_ADDR_M
                lda  xx
                sta  cx16.VERA_ADDR_L
                ply     ; xbits
                lda  maskbits,y
            }}
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

    sub pget(uword @zp xx, uword yy) -> ubyte {
        %asm {{
            lda  xx
            and  #7
            pha     ; xbits
        }}
        xx /= 8
        if width==320
            xx += yy*(320/8)
        else
            xx += yy*(640/8)

        %asm {{
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            lda  xx+1
            sta  cx16.VERA_ADDR_M
            lda  xx
            sta  cx16.VERA_ADDR_L
            ply         ; xbits
            lda  plot.maskbits,y
            and  cx16.VERA_DATA0
            beq  +
            lda  #1
+           rts
        }}
    }

    sub fill(uword x, uword y, bool draw) {
        ; Non-recursive scanline flood fill.
        ; based loosely on code found here https://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm
        ; with the fixes applied to the seedfill_4 routine as mentioned in the comments.
        const ubyte MAXDEPTH = 64
        word @zp xx = x as word
        word @zp yy = y as word
        word[MAXDEPTH] @split @shared stack_xl
        word[MAXDEPTH] @split @shared stack_xr
        word[MAXDEPTH] @split @shared stack_y
        byte[MAXDEPTH] @shared stack_dy
        cx16.r12L = 0       ; stack pointer
        word x1
        word x2
        byte dy
        cx16.r10L = draw as ubyte
        sub push_stack(word sxl, word sxr, word sy, byte sdy) {
            if cx16.r12L==MAXDEPTH
                return
            cx16.r0s = sy+sdy
            if cx16.r0s>=0 and cx16.r0s<=height-1 {
;;                stack_xl[cx16.r12L] = sxl
;;                stack_xr[cx16.r12L] = sxr
;;                stack_y[cx16.r12L] = sy
;;                stack_dy[cx16.r12L] = sdy
;;                cx16.r12L++
                %asm {{
                    ldy  cx16.r12L
                    lda  sxl
                    sta  stack_xl_lsb,y
                    lda  sxl+1
                    sta  stack_xl_msb,y
                    lda  sxr
                    sta  stack_xr_lsb,y
                    lda  sxr+1
                    sta  stack_xr_msb,y
                    lda  sy
                    sta  stack_y_lsb,y
                    lda  sy+1
                    sta  stack_y_msb,y
                    ldy  cx16.r12L
                    lda  sdy
                    sta  stack_dy,y
                    inc  cx16.r12L
                }}
            }
        }
        sub pop_stack() {
;;            cx16.r12L--
;;            x1 = stack_xl[cx16.r12L]
;;            x2 = stack_xr[cx16.r12L]
;;            y = stack_y[cx16.r12L]
;;            dy = stack_dy[cx16.r12L]
            %asm {{
                dec  cx16.r12L
                ldy  cx16.r12L
                lda  stack_xl_lsb,y
                sta  x1
                lda  stack_xl_msb,y
                sta  x1+1
                lda  stack_xr_lsb,y
                sta  x2
                lda  stack_xr_msb,y
                sta  x2+1
                lda  stack_y_lsb,y
                sta  yy
                lda  stack_y_msb,y
                sta  yy+1
                ldy  cx16.r12L
                lda  stack_dy,y
                sta  dy
            }}
            yy+=dy
        }
        cx16.r11L = pget(xx as uword, yy as uword)        ; old_color
        if cx16.r11L == cx16.r10L
            return
        if xx<0 or xx>width-1 or yy<0 or yy>height-1
            return
        push_stack(xx, xx, yy, 1)
        push_stack(xx, xx, yy + 1, -1)
        word left = 0
        while cx16.r12L {
            pop_stack()
            xx = x1
            while xx >= 0 {
                if pget(xx as uword, yy as uword) != cx16.r11L
                    break
                xx--
            }
            if x1!=xx
                horizontal_line(xx as uword+1, yy as uword, x1-xx as uword, cx16.r10L)
            else
                goto skip

            left = xx + 1
            if left < x1
                push_stack(left, x1 - 1, yy, -dy)
            xx = x1 + 1

            do {
                cx16.r9 = xx
                while xx <= width-1 {
                    if pget(xx as uword, yy as uword) != cx16.r11L
                        break
                    xx++
                }
                if cx16.r9!=xx
                    horizontal_line(cx16.r9, yy as uword, (xx as uword)-cx16.r9, cx16.r10L)

                push_stack(left, xx - 1, yy, dy)
                if xx > x2 + 1
                    push_stack(x2 + 1, xx - 1, yy, -dy)
skip:
                xx++
                while xx <= x2 {
                    if pget(xx as uword, yy as uword) == cx16.r11L
                        break
                    xx++
                }
                left = xx
            } until xx>x2
        }
    }

    sub position(uword @zp xx, uword yy) {
        if width==320
            cx16.r0 = yy*(320/8)
        else
            cx16.r0 = yy*(640/8)
        cx16.vaddr(0, cx16.r0+(xx/8), 0, 1)
    }

    sub position2(uword @zp xx, uword yy, bool also_port_1) {
        position(xx, yy)
        if also_port_1
            cx16.vaddr_clone(0)
    }

    const ubyte charset_bank = $1
    const uword charset_addr = $f000       ; in bank 1, so $1f000

    sub text_charset(ubyte charset) {
        ; -- select the text charset to use with the text() routine
        ;    the charset number is the same as for the cx16.screen_set_charset() ROM function.
        ;    1 = ISO charset, 2 = PETSCII uppercase+graphs, 3= PETSCII uppercase+lowercase.
        cx16.screen_set_charset(charset, 0)
    }

    sub text(uword @zp xx, uword yy, bool draw, uword sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        cx16.r3 = sctextptr
        while @(cx16.r3) {
            chardataptr = charset_addr + @(cx16.r3) * $0008
            ; copy the character bitmap into RAM
            cx16.vaddr_autoincr(charset_bank, chardataptr, 0, 1)
            %asm {{
                ; pre-shift the bits
                lda  text.xx
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
+               sta  char_bitmap_bytes_left,y
                lda  P8ZP_SCRATCH_REG
                sta  char_bitmap_bytes_right,y
                iny
                cpy  #8
                bne  --
            }}
            ; left part of shifted char
            position2(xx, yy, true)
            set_autoincrs()
            if draw {
                %asm {{
                    ldy  #0
-                   lda  char_bitmap_bytes_left,y
                    ora  cx16.VERA_DATA1
                    sta  cx16.VERA_DATA0
                    iny
                    cpy  #8
                    bne  -
                }}
            } else {
                %asm {{
                    ldy  #0
-                   lda  char_bitmap_bytes_left,y
                    eor  #255
                    and  cx16.VERA_DATA1
                    sta  cx16.VERA_DATA0
                    iny
                    cpy  #8
                    bne  -
                }}
            }
            ; right part of shifted char
            if lsb(xx) & 7 {
                position2(xx+8, yy, true)
                set_autoincrs()
                if draw {
                    %asm {{
                        ldy  #0
-                       lda  char_bitmap_bytes_right,y
                        ora  cx16.VERA_DATA1
                        sta  cx16.VERA_DATA0
                        iny
                        cpy  #8
                        bne  -
                    }}
                } else {
                    %asm {{
                        ldy  #0
-                       lda  char_bitmap_bytes_right,y
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
            if width==320 {
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (11<<4)
                cx16.VERA_CTRL = 1
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (11<<4)
            } else {
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (12<<4)
                cx16.VERA_CTRL = 1
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & $0f | (12<<4)
            }
        }
    }

    asmsub cs_innerloop640(ubyte color @A) clobbers(Y) {
        ; using verafx 32 bits writes here would make this faster but it's safer to
        ; use verafx only explicitly when you know what you're doing.
        %asm {{
            cmp  #0
            beq  +
            lda  #255
+           ldy  #80
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
}
