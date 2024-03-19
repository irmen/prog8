; Bitmap pixel graphics routines for the CommanderX16
; Custom routines to use the full-screen 640x480 and 320x240 screen modes.
; (These modes are not supported by the documented GRAPH_xxxx kernal routines)
;
; No text layer is currently shown, text can be drawn as part of the bitmap itself.
; Note: for similar graphics routines that also work on the C-64, use the "graphics" module instead.
; Note: for identical routines for a monochrome 1 bpp screen, use the "monogfx" module instead.
; Note: for color palette manipulation, use the "palette" module or write Vera registers yourself.
; Note: this library implements code for various resolutions and color depths. This takes up memory.
;       If you're memory constrained you should probably not use this built-in library,
;       but make a copy in your project only containing the code for the required resolution.
;
; NOTE: For sake of speed, NO BOUNDS CHECKING is performed in most routines!
;       You'll have to make sure yourself that you're not writing outside of bitmap boundaries!
;
;
; SCREEN MODE LIST:
;   mode 0 = reset back to default text mode
;   mode 1 = bitmap 320 x 240 x 256c (8 bpp)
;   mode 2 = bitmap 640 x 480 x 4c (2 bpp)
;   mode 3 = bitmap 320 x 240 x 4c (not yet implemented: just use 256c, there's enough vram for that)
;   mode 4 = bitmap 320 x 240 x 16c (not yet implemented: just use 256c, there's enough vram for that)
;   mode 5 = bitmap 640 x 400 x 16c (not yet implemented)

gfx2 {

    %option ignore_unused

    ; read-only control variables:
    ubyte active_mode = 0
    uword width = 0
    uword height = 0
    ubyte bpp = 0

    sub screen_mode(ubyte mode) {
        cx16.VERA_CTRL=0
        when mode {
            1 -> {
                ; lores 256c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 64
                cx16.VERA_DC_VSCALE = 64
                cx16.VERA_L1_CONFIG = %00000111
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = 0
            }
            2 -> {
                ; highres 4c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 128
                cx16.VERA_DC_VSCALE = 128
                cx16.VERA_L1_CONFIG = %00000101
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = %00000001
            }
            else -> {
                ; back to default text mode
                if active_mode!=0 {
                    cx16.r15L = cx16.VERA_DC_VIDEO & %00000111 ; retain chroma + output mode
                    cbm.CINT()
                    cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11111000) | cx16.r15L
                }
            }
        }

        init_mode(mode)
        if active_mode!=0
            clear_screen(0)
    }

    sub init_mode(ubyte mode) {
        ; set the internal configuration variables corresponding to the given screenmode
        ; doesn't manipulate Vera / the actual display mode
        active_mode = mode
        when mode {
            1 -> {
                width = 320
                height = 240
                bpp = 8
            }
            2 -> {
                width = 640
                height = 480
                bpp = 2
            }
            else -> {
                width = 0
                height = 0
                bpp = 0
                active_mode = 0
            }
        }
    }

    sub clear_screen(ubyte color) {
        position(0, 0)
        when active_mode {
            1 -> {
                ; lores 256c
                repeat 240/2
                    cs_innerloop640(color)
            }
            2 -> {
                ; highres 4c
                ubyte[] colors = [%00000000, %01010101, %10101010, %11111111]
                color = colors[color&3]
                repeat 480/4
                    cs_innerloop640(color)
            }
        }
        position(0, 0)
    }

    sub rect(uword xx, uword yy, uword rwidth, uword rheight, ubyte color) {
        if rwidth==0 or rheight==0
            return
        horizontal_line(xx, yy, rwidth, color)
        if rheight==1
            return
        horizontal_line(xx, yy+rheight-1, rwidth, color)
        vertical_line(xx, yy+1, rheight-2, color)
        if rwidth==1
            return
        vertical_line(xx+rwidth-1, yy+1, rheight-2, color)
    }

    sub fillrect(uword xx, uword yy, uword rwidth, uword rheight, ubyte color) {
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
        if rwidth==0
            return
        repeat rheight {
            horizontal_line(xx, yy, rwidth, color)
            yy++
        }
    }

    sub horizontal_line(uword xx, uword yy, uword length, ubyte color) {
        if length==0
            return
        when active_mode {
            1 -> {
                ; lores 256c
                position(xx, yy)
                %asm {{
                    lda  p8v_color
                    ldx  p8v_length+1
                    beq  +
                    ldy  #0
-                   sta  cx16.VERA_DATA0
                    iny
                    bne  -
                    dex
                    bne  -
+                   ldy  p8v_length     ; remaining
                    beq  +
-                   sta  cx16.VERA_DATA0
                    dey
                    bne  -
+
                }}
            }
            2 -> {
                ; highres 4c ....also mostly usable for lores 4c?
                color &= 3
                ubyte[4] colorbits
                ubyte ii
                for ii in 3 downto 0 {
                    colorbits[ii] = color
                    color <<= 2
                }
                void addr_mul_24_for_highres_4c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    lda  cx16.VERA_ADDR_H
                    and  #%00000111         ; no auto advance
                    sta  cx16.VERA_ADDR_H
                    stz  cx16.VERA_CTRL     ; setup vera addr 0
                    lda  cx16.r1
                    and  #1
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0
                    sta  cx16.VERA_ADDR_L
                    lda  cx16.r0+1
                    sta  cx16.VERA_ADDR_M
                    ldx  p8v_xx
                }}

                repeat length {
                    %asm {{
                        txa
                        and  #3
                        tay
                        lda  cx16.VERA_DATA0
                        and  p8b_gfx2.p8s_plot.p8v_mask4c,y
                        ora  p8v_colorbits,y
                        sta  cx16.VERA_DATA0
                        cpy  #%00000011         ; next vera byte?
                        bne  ++
                        inc  cx16.VERA_ADDR_L
                        bne  ++
                        inc  cx16.VERA_ADDR_M
+                       bne  +
                        inc  cx16.VERA_ADDR_H
+                       inx                     ; next pixel
                    }}
                }
            }
        }
    }

    sub safe_horizontal_line(uword xx, uword yy, uword length, ubyte color) {
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

        horizontal_line(xx, yy, length, color)
    }

    sub vertical_line(uword xx, uword yy, uword lheight, ubyte color) {
        when active_mode {
            1 -> {
                ; lores 256c
                ; set vera auto-increment to 320 pixel increment (=next line)
                position(xx,yy)
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (14<<4)
                %asm {{
                    ldy  p8v_lheight
                    beq  +
                    lda  p8v_color
-                   sta  cx16.VERA_DATA0
                    dey
                    bne  -
+
                }}
            }
            2 -> {
                ; highres 4c
                ; use TWO vera adress pointers simultaneously one for reading, one for writing, so auto-increment is possible
                if lheight==0
                    return
                position2(xx,yy,true)
                set_both_strides(13)    ; 160 increment = 1 line in 640 px 4c mode
                ;; color &= 3
                ;; color <<= gfx2.plot.shift4c[lsb(xx) & 3]
                cx16.r2L = lsb(xx) & 3
                when color & 3 {
                    1 -> color = gfx2.plot.shiftedleft_4c_1[cx16.r2L]
                    2 -> color = gfx2.plot.shiftedleft_4c_2[cx16.r2L]
                    3 -> color = gfx2.plot.shiftedleft_4c_3[cx16.r2L]
                }
                ubyte @shared mask = gfx2.plot.mask4c[lsb(xx) & 3]
                repeat lheight {
                    %asm {{
                        lda  cx16.VERA_DATA0
                        and  p8v_mask
                        ora  p8v_color
                        sta  cx16.VERA_DATA1
                    }}
                }
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

    sub line(uword @zp x1, uword @zp y1, uword @zp x2, uword @zp y2, ubyte color) {
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
            vertical_line(x1, y1, abs(dy) as uword +1, color)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            horizontal_line(x1, y1, abs(dx) as uword +1, color)
            return
        }

        word @zp d = 0
        cx16.r1L = 1  ;; true      ; 'positive_ix'
        if dx < 0 {
            dx = -dx
            cx16.r1L = 0 ;; false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2
        cx16.r14 = x1       ; internal plot X

        if dx >= dy {
            if cx16.r1L!=0 {
                repeat {
                    plot(cx16.r14, y1, color)
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
                    plot(cx16.r14, y1, color)
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
            if cx16.r1L!=0 {
                repeat {
                    plot(cx16.r14, y1, color)
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
                    plot(cx16.r14, y1, color)
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

    sub circle(uword @zp xcenter, uword @zp ycenter, ubyte radius, ubyte color) {
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
            ; cx16.r14 = x, cx16.r15 = y, color=color.
            plot(cx16.r14, cx16.r15, color)
        }
    }

    sub safe_circle(uword @zp xcenter, uword @zp ycenter, ubyte radius, ubyte color) {
        ; This version does bounds checks and clipping, but is a lot slower.
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
            ; cx16.r14 = x, cx16.r15 = y, color=color.
            safe_plot(cx16.r14, cx16.r15, color)
        }
    }

    sub disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, ubyte color) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, color)
            horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, color)
            horizontal_line(xcenter-yy, ycenter+radius, yy*$0002+1, color)
            horizontal_line(xcenter-yy, ycenter-radius, yy*$0002+1, color)
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
    }

    sub safe_disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, ubyte color) {
        ; This version does bounds checks and clipping, but is a lot slower.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius

        while radius>=yy {
            safe_horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, color)
            safe_horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, color)
            safe_horizontal_line(xcenter-yy, ycenter+radius, yy*$0002+1, color)
            safe_horizontal_line(xcenter-yy, ycenter-radius, yy*$0002+1, color)
            yy++
            if decisionOver2>=0 {
                radius--
                decisionOver2 -= radius*$0002
            }
            decisionOver2 += yy*$0002
            decisionOver2++
        }
    }

    sub plot(uword @zp xx, uword @zp yy, ubyte @zp color) {
        ubyte[4] @shared mask4c = [%00111111, %11001111, %11110011, %11111100]
        ubyte[4] @shared shift4c = [6,4,2,0]
        ubyte[4] shiftedleft_4c_1 = [1<<6, 1<<4, 1<<2, 1<<0]
        ubyte[4] shiftedleft_4c_2 = [2<<6, 2<<4, 2<<2, 2<<0]
        ubyte[4] shiftedleft_4c_3 = [3<<6, 3<<4, 3<<2, 3<<0]

        when active_mode {
            1 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1
                    ora  #%00010000     ; enable auto-increment so next_pixel() can be used after this
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0+1
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0
                    sta  cx16.VERA_ADDR_L
                    lda  p8v_color
                    sta  cx16.VERA_DATA0
                }}
            }
            2 -> {
                ; highres 4c   ....also mostly usable for lores 4c?
                void addr_mul_24_for_highres_4c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
                cx16.r2L = lsb(xx) & 3       ; xbits
                ; color &= 3
                ; color <<= shift4c[cx16.r2L]
                when color & 3 {
                    1 -> color = shiftedleft_4c_1[cx16.r2L]
                    2 -> color = shiftedleft_4c_2[cx16.r2L]
                    3 -> color = shiftedleft_4c_3[cx16.r2L]
                }
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1L
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0H
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0L
                    sta  cx16.VERA_ADDR_L
                    ldy  cx16.r2L           ; xbits
                    lda  p8v_mask4c,y
                    and  cx16.VERA_DATA0
                    ora  p8v_color
                    sta  cx16.VERA_DATA0
                }}
            }
        }
    }

    sub safe_plot(uword xx, uword yy, ubyte color) {
        ; A plot that does bounds checks to see if the pixel is inside the screen.
        if msb(xx)&$80!=0 or msb(yy)&$80!=0
            return
        if xx >= width or yy >= height
            return
        plot(xx, yy, color)
    }

    sub pget(uword @zp xx, uword yy) -> ubyte {
        when active_mode {
            1 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0+1
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0
                    sta  cx16.VERA_ADDR_L
                    lda  cx16.VERA_DATA0
                }}
            }
            2 -> {
                ; hires 4c
                void addr_mul_24_for_highres_4c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1L
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0H
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0L
                    sta  cx16.VERA_ADDR_L
                    lda  cx16.VERA_DATA0
                    pha
                    lda  p8v_xx
                    and  #3
                    tay
                    lda  p8b_gfx2.p8s_plot.p8v_shift4c,y
                    tay
                    pla
                    cpy  #0
                    beq  +
-                   lsr  a
                    dey
                    bne  -
+                   and #3
                }}
            }
            else -> return 0
        }
    }

    sub fill(uword x, uword y, ubyte new_color) {
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
        cx16.r10L = new_color
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
                    lda  p8v_sxl
                    sta  p8v_stack_xl_lsb,y
                    lda  p8v_sxl+1
                    sta  p8v_stack_xl_msb,y
                    lda  p8v_sxr
                    sta  p8v_stack_xr_lsb,y
                    lda  p8v_sxr+1
                    sta  p8v_stack_xr_msb,y
                    lda  p8v_sy
                    sta  p8v_stack_y_lsb,y
                    lda  p8v_sy+1
                    sta  p8v_stack_y_msb,y
                    ldy  cx16.r12L
                    lda  p8v_sdy
                    sta  p8v_stack_dy,y
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
                lda  p8v_stack_xl_lsb,y
                sta  p8v_x1
                lda  p8v_stack_xl_msb,y
                sta  p8v_x1+1
                lda  p8v_stack_xr_lsb,y
                sta  p8v_x2
                lda  p8v_stack_xr_msb,y
                sta  p8v_x2+1
                lda  p8v_stack_y_lsb,y
                sta  p8v_yy
                lda  p8v_stack_y_msb,y
                sta  p8v_yy+1
                ldy  cx16.r12L
                lda  p8v_stack_dy,y
                sta  p8v_dy
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
        while cx16.r12L!=0 {
            pop_stack()
            xx = x1
            ; possible speed optimization: if mode==1 (256c) use vera autodecrement instead of pget(), but code bloat not worth it?
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
                cx16.r9s = xx
                ; possible speed optimization: if mode==1 (256c) use vera autoincrement instead of pget(), but code bloat not worth it?
                while xx <= width-1 {
                    if pget(xx as uword, yy as uword) != cx16.r11L
                        break
                    xx++
                }
                if cx16.r9s!=xx
                    horizontal_line(cx16.r9, yy as uword, xx-cx16.r9s as uword, cx16.r10L)

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
        when active_mode {
            1 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
            }
            2 -> {
                ; highres 4c
                void addr_mul_24_for_highres_4c(yy, xx)      ; 24 bits result is in r0 and r1L (highest byte)
            }
        }
        cx16.r2L = cx16.r1L
        cx16.vaddr(cx16.r2L, cx16.r0, 0, 1)
    }

    sub position2(uword @zp xx, uword yy, bool also_port_1) {
        position(xx, yy)
        if also_port_1
            cx16.vaddr_clone(0)
    }

    inline asmsub next_pixel(ubyte color @A) {
        ; -- sets the next pixel byte to the graphics chip.
        ;    for 8 bpp screens this will plot 1 pixel.
        ;    for 2 bpp screens it will plot 4 pixels at once (color = bit pattern).
        %asm {{
            sta  cx16.VERA_DATA0
        }}
    }

    asmsub next_pixels(uword pixels @AY, uword amount @R0) clobbers(A, X, Y)  {
        ; -- sets the next bunch of pixels from a prepared array of bytes.
        ;    for 8 bpp screens this will plot 1 pixel per byte.
        ;    for 2 bpp screens it will plot 4 pixels at once (colors are the bit patterns per byte).
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldx  cx16.r0+1
            beq  +
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  cx16.VERA_DATA0
            iny
            bne  -
            inc  P8ZP_SCRATCH_W1+1       ; next page of 256 pixels
            dex
            bne  -

+           ldx  cx16.r0           ; remaining pixels
            beq  +
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  cx16.VERA_DATA0
            iny
            dex
            bne  -
+           rts
        }}
    }

    asmsub set_8_pixels_from_bits(ubyte bits @R0, ubyte oncolor @A, ubyte offcolor @Y) clobbers(X) {
        ; this is only useful in 256 color mode where one pixel equals one byte value.
        %asm {{
            ldx  #8
-           asl  cx16.r0
            bcc  +
            sta  cx16.VERA_DATA0
            bra  ++
+           sty  cx16.VERA_DATA0
+           dex
            bne  -
            rts
        }}
    }

    const ubyte charset_bank = $1
    const uword charset_addr = $f000       ; in bank 1, so $1f000

    sub text_charset(ubyte charset) {
        ; -- select the text charset to use with the text() routine
        ;    the charset number is the same as for the cx16.screen_set_charset() ROM function.
        ;    1 = ISO charset, 2 = PETSCII uppercase+graphs, 3= PETSCII uppercase+lowercase.
        cx16.screen_set_charset(charset, 0)
    }

    sub text(uword @zp xx, uword yy, ubyte color, uword sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        when active_mode {
            1 -> {
                ; lores 256c
                while @(sctextptr)!=0 {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    cx16.vaddr(charset_bank, chardataptr, 1, 1)
                    repeat 8 {
                        position(xx,yy)
                        yy++
                        %asm {{
                            ldx  p8v_color
                            lda  cx16.VERA_DATA1
                            sta  P8ZP_SCRATCH_B1
                            ldy  #8
-                           asl  P8ZP_SCRATCH_B1
                            bcc  +
                            stx  cx16.VERA_DATA0    ; write a pixel
                            bra  ++
+                           lda  cx16.VERA_DATA0    ; don't write a pixel, but do advance to the next address
+                           dey
                            bne  -
                        }}
                    }
                    xx+=8
                    yy-=8
                    sctextptr++
                }
            }
            2 -> {
                ; hires 4c
                ; we're going to use a few cx16 registers to make sure every variable is in zeropage in the inner loop.
                cx16.r11L = color
                while @(sctextptr)!=0 {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    cx16.vaddr(charset_bank, chardataptr, 1, 1)  ; for reading the chardata from Vera data channel 1
                    position(xx, yy)              ; only calculated once, we update vera address in the loop instead
                    cx16.VERA_ADDR_H &= $0f     ; no auto increment
                    repeat 8 {
                        cx16.r10L = cx16.VERA_DATA1  ; get the next 8 horizontal character bits
                        cx16.r7 = xx
                        repeat 8 {
                            cx16.r10L <<= 1
                            if_cs {
                                cx16.r2L = cx16.r7L & 3       ; xbits
                                when cx16.r11L & 3 {
                                    1 -> cx16.r12L = gfx2.plot.shiftedleft_4c_1[cx16.r2L]
                                    2 -> cx16.r12L = gfx2.plot.shiftedleft_4c_2[cx16.r2L]
                                    3 -> cx16.r12L = gfx2.plot.shiftedleft_4c_3[cx16.r2L]
                                    else -> cx16.r12L = 0
                                }
                                cx16.VERA_DATA0 = cx16.VERA_DATA0 & gfx2.plot.mask4c[cx16.r2L] | cx16.r12L
                            }
                            cx16.r7++
                            if (cx16.r7 & 3) == 0 {
                                ; increment the pixel address by one
                                %asm {{
                                    stz  cx16.VERA_CTRL
                                    clc
                                    lda  cx16.VERA_ADDR_L
                                    adc  #1
                                    sta  cx16.VERA_ADDR_L
                                    lda  cx16.VERA_ADDR_M
                                    adc  #0
                                    sta  cx16.VERA_ADDR_M
                                    lda  cx16.VERA_ADDR_H
                                    adc  #0
                                    sta  cx16.VERA_ADDR_H
                                }}
                            }
                        }

                        ; increment pixel address to the next line
                        %asm {{
                            stz  cx16.VERA_CTRL
                            clc
                            lda  cx16.VERA_ADDR_L
                            adc  #(640-8)/4
                            sta  cx16.VERA_ADDR_L
                            lda  cx16.VERA_ADDR_M
                            adc  #0
                            sta  cx16.VERA_ADDR_M
                            lda  cx16.VERA_ADDR_H
                            adc  #0
                            sta  cx16.VERA_ADDR_H
                        }}
                    }
                    xx+=8
                    sctextptr++
                }
            }
        }
    }

    asmsub cs_innerloop640(ubyte color @A) clobbers(Y) {
        ; using verafx 32 bits writes here would make this faster but it's safer to
        ; use verafx only explicitly when you know what you're doing.
        %asm {{
            ldy  #80
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

    asmsub addr_mul_24_for_highres_4c(uword yy @R2, uword xx @R3)  clobbers(A, Y) -> uword @R0, uword @R1 {
        ; yy * 160 + xx/4  (24 bits calculation)
        ; 24 bits result is in r0 and r1L (highest byte)
        %asm {{
            ldy  #5
-           asl  cx16.r2
            rol  cx16.r2+1
            dey
            bne  -
            lda  cx16.r2
            sta  cx16.r0
            lda  cx16.r2+1
            sta  cx16.r0+1
            asl  cx16.r0
            rol  cx16.r0+1
            asl  cx16.r0
            rol  cx16.r0+1

            ; xx >>= 2  (xx=R3)
            lsr  cx16.r3+1
            ror  cx16.r3
            lsr  cx16.r3+1
            ror  cx16.r3

            ; add r2 and xx (r3) to r0 (24-bits)
            stz  cx16.r1
            clc
            lda  cx16.r0
            adc  cx16.r2
            sta  cx16.r0
            lda  cx16.r0+1
            adc  cx16.r2+1
            sta  cx16.r0+1
            bcc  +
            inc  cx16.r1
+           clc
            lda  cx16.r0
            adc  cx16.r3
            sta  cx16.r0
            lda  cx16.r0+1
            adc  cx16.r3+1
            sta  cx16.r0+1
            bcc  +
            inc  cx16.r1
+
            rts
        }}
    }

    asmsub addr_mul_24_for_lores_256c(uword yy @R0, uword xx @AY) clobbers(A) -> uword @R0, ubyte @R1  {
        ; yy * 320 + xx (24 bits calculation)
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            lda  cx16.r0
            sta  P8ZP_SCRATCH_B1
            lda  cx16.r0+1
            sta  cx16.r1
            sta  P8ZP_SCRATCH_REG
            lda  cx16.r0
            asl  a
            rol  P8ZP_SCRATCH_REG
            asl  a
            rol  P8ZP_SCRATCH_REG
            asl  a
            rol  P8ZP_SCRATCH_REG
            asl  a
            rol  P8ZP_SCRATCH_REG
            asl  a
            rol  P8ZP_SCRATCH_REG
            asl  a
            rol  P8ZP_SCRATCH_REG
            sta  cx16.r0
            lda  P8ZP_SCRATCH_B1
            clc
            adc  P8ZP_SCRATCH_REG
            sta  cx16.r0+1
            bcc  +
            inc  cx16.r1
+           ; now add the value to this 24-bits number
            lda  cx16.r0
            clc
            adc  P8ZP_SCRATCH_W1
            sta  cx16.r0
            lda  cx16.r0+1
            adc  P8ZP_SCRATCH_W1+1
            sta  cx16.r0+1
            bcc  +
            inc  cx16.r1
+           lda  cx16.r1
            rts
        }}
    }

}
