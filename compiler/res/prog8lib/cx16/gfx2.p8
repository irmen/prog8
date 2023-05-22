; Bitmap pixel graphics routines for the CommanderX16
; Custom routines to use the full-screen 640x480 and 320x240 screen modes.
; (These modes are not supported by the documented GRAPH_xxxx kernal routines)
;
; No text layer is currently shown, text can be drawn as part of the bitmap itself.
; Note: for similar graphics routines that also work on the C-64, use the "graphics" module instead.
; Note: for color palette manipulation, use the "palette" module or write Vera registers yourself.
; Note: this library implements code for various resolutions and color depths. This takes up memory.
;       If you're memory constrained you should probably not use this built-in library,
;       but make a copy in your project only containing the code for the required resolution.
;
;
; SCREEN MODE LIST:
;   mode 0 = reset back to default text mode
;   mode 1 = bitmap 320 x 240 monochrome
;   mode 2 = bitmap 320 x 240 x 4c (TODO not yet implemented)
;   mode 3 = bitmap 320 x 240 x 16c (TODO not yet implemented)
;   mode 4 = bitmap 320 x 240 x 256c  (like SCREEN $80 but using this api instead of kernal)
;   mode 5 = bitmap 640 x 480 monochrome
;   mode 6 = bitmap 640 x 480 x 4c
;   higher color dephts in highres are not supported due to lack of VRAM


gfx2 {

    ; read-only control variables:
    ubyte active_mode = 0
    uword width = 0
    uword height = 0
    ubyte bpp = 0
    bool monochrome_dont_stipple_flag = false            ; set to false to enable stippling mode in monochrome displaymodes

    sub screen_mode(ubyte mode) {
        when mode {
            1 -> {
                ; lores monochrome
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 64
                cx16.VERA_DC_VSCALE = 64
                cx16.VERA_L1_CONFIG = %00000100
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = 0
                width = 320
                height = 240
                bpp = 1
            }
            ; TODO modes 2, 3 not yet implemented
            4 -> {
                ; lores 256c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 64
                cx16.VERA_DC_VSCALE = 64
                cx16.VERA_L1_CONFIG = %00000111
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = 0
                width = 320
                height = 240
                bpp = 8
            }
            5 -> {
                ; highres monochrome
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 128
                cx16.VERA_DC_VSCALE = 128
                cx16.VERA_L1_CONFIG = %00000100
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = %00000001
                width = 640
                height = 480
                bpp = 1
            }
            6 -> {
                ; highres 4c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 128
                cx16.VERA_DC_VSCALE = 128
                cx16.VERA_L1_CONFIG = %00000101
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = %00000001
                width = 640
                height = 480
                bpp = 2
            }
            else -> {
                ; back to default text mode
                cx16.r15L = cx16.VERA_DC_VIDEO & %00000111 ; retain chroma + output mode
                cbm.CINT()
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11111000) | cx16.r15L
                width = 0
                height = 0
                bpp = 0
                mode = 0
            }
        }

        active_mode = mode
        if bpp
            clear_screen()
    }

    sub clear_screen() {
        monochrome_stipple(false)
        position(0, 0)
        when active_mode {
            1 -> {
                ; lores monochrome
                repeat 240/2/8
                    cs_innerloop640()
            }
            ; TODO mode 2, 3
            4 -> {
                ; lores 256c
                repeat 240/2
                    cs_innerloop640()
            }
            5 -> {
                ; highres monochrome
                repeat 480/8
                    cs_innerloop640()
            }
            6 -> {
                ; highres 4c
                repeat 480/4
                    cs_innerloop640()
            }
            ; modes 7 and 8 not supported due to lack of VRAM
        }
        position(0, 0)
    }

    sub monochrome_stipple(bool enable) {
        monochrome_dont_stipple_flag = not enable
    }

    sub rect(uword x, uword y, uword rwidth, uword rheight, ubyte color) {
        if rwidth==0 or rheight==0
            return
        horizontal_line(x, y, rwidth, color)
        if rheight==1
            return
        horizontal_line(x, y+rheight-1, rwidth, color)
        vertical_line(x, y+1, rheight-2, color)
        if rwidth==1
            return
        vertical_line(x+rwidth-1, y+1, rheight-2, color)
    }

    sub fillrect(uword x, uword y, uword rwidth, uword rheight, ubyte color) {
        if rwidth==0
            return
        repeat rheight {
            horizontal_line(x, y, rwidth, color)
            y++
        }
    }

    sub horizontal_line(uword x, uword y, uword length, ubyte color) {
        if length==0
            return
        when active_mode {
            1, 5 -> {
                ; monochrome modes, either resolution
                ubyte separate_pixels = (8-lsb(x)) & 7
                if separate_pixels as uword > length
                    separate_pixels = lsb(length)
                repeat separate_pixels {
                    ; TODO optimize this by writing a masked byte in 1 go
                    plot(x, y, color)
                    x++
                }
                length -= separate_pixels
                if length {
                    position(x, y)
                    separate_pixels = lsb(length) & 7
                    x += length & $fff8
                    %asm {{
                        lsr  length+1
                        ror  length
                        lsr  length+1
                        ror  length
                        lsr  length+1
                        ror  length
                        lda  color
                        bne  +
                        ldy  #0     ; black
                        bra  _loop
+                       lda  monochrome_dont_stipple_flag
                        beq  _stipple
                        ldy  #255       ; don't stipple
                        bra  _loop
_stipple                lda  y
                        and  #1         ; determine stipple pattern to use
                        bne  +
                        ldy  #%01010101
                        bra  _loop
+                       ldy  #%10101010
_loop                   lda  length
                        ora  length+1
                        beq  _done
                        sty  cx16.VERA_DATA0
                        lda  length
                        bne  +
                        dec  length+1
+                       dec  length
                        bra  _loop
_done
                    }}
                    repeat separate_pixels {
                        ; TODO optimize this by writing a masked byte in 1 go
                        plot(x, y, color)
                        x++
                    }
                }
                cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off again
            }
            4 -> {
                ; lores 256c
                position(x, y)
                %asm {{
                    lda  color
                    phx
                    ldx  length+1
                    beq  +
                    ldy  #0
-                   sta  cx16.VERA_DATA0
                    iny
                    bne  -
                    dex
                    bne  -
+                   ldy  length     ; remaining
                    beq  +
-                   sta  cx16.VERA_DATA0
                    dey
                    bne  -
+                   plx
                }}
            }
            6 -> {
                ; highres 4c
                ; TODO also mostly usable for lores 4c?
                color &= 3
                ubyte[4] colorbits
                ubyte ii
                for ii in 3 downto 0 {
                    colorbits[ii] = color
                    color <<= 2
                }
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
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
                    phx
                    ldx  x
                }}

                repeat length {
                    %asm {{
                        txa
                        and  #3
                        tay
                        lda  cx16.VERA_DATA0
                        and  gfx2.plot.mask4c,y
                        ora  colorbits,y
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

                %asm {{
                    plx
                }}
            }
        }
    }

    sub vertical_line(uword x, uword y, uword lheight, ubyte color) {
        when active_mode {
            1, 5 -> {
                ; monochrome, lo-res
                cx16.r15L = gfx2.plot.bits[x as ubyte & 7]           ; bitmask
                if color {
                    if monochrome_dont_stipple_flag {
                        ; draw continuous line.
                        position2(x,y,true)
                        if active_mode==1
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
                        if x&1 {
                            y++
                            lheight--
                        }
                        position2(x,y,true)
                        if active_mode==1
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
                    position2(x,y,true)
                    cx16.r15 = ~cx16.r15    ; erase pixels
                    if active_mode==1
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
            }
            4 -> {
                ; lores 256c
                ; set vera auto-increment to 320 pixel increment (=next line)
                position(x,y)
                cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (14<<4)
                %asm {{
                    ldy  lheight
                    beq  +
                    lda  color
-                   sta  cx16.VERA_DATA0
                    dey
                    bne  -
+
                }}
            }
            6 -> {
                ; highres 4c
                ; use TWO vera adress pointers simultaneously one for reading, one for writing, so auto-increment is possible
                if lheight==0
                    return
                position2(x,y,true)
                set_both_strides(13)    ; 160 increment = 1 line in 640 px 4c mode
                color &= 3
                color <<= gfx2.plot.shift4c[lsb(x) & 3]         ; TODO with lookup table
                ubyte @shared mask = gfx2.plot.mask4c[lsb(x) & 3]
                repeat lheight {
                    %asm {{
                        lda  cx16.VERA_DATA0
                        and  mask
                        ora  color
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
        cx16.r13 = true      ; 'positive_ix'
        if dx < 0 {
            dx = -dx
            cx16.r13 = false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2
        cx16.r14 = x1       ; internal plot X

        if dx >= dy {
            if cx16.r13 {
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
            if cx16.r13 {
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
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter - xx
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter + xx
            cx16.r15 = ycenter - yy
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter - xx
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter + xx
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter - yy
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter + yy
            cx16.r15 = ycenter - xx
            plot(cx16.r14, cx16.r15, color)
            cx16.r14 = xcenter - yy
            plot(cx16.r14, cx16.r15, color)

            yy++
            if decisionOver2<=0
                decisionOver2 += (yy as word)*2+1
            else {
                xx--
                decisionOver2 += (yy as word -xx)*2+1
            }
        }
    }

    sub disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, ubyte color) {
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
            if decisionOver2<=0
                decisionOver2 += (yy as word)*2+1
            else {
                radius--
                decisionOver2 += (yy as word -radius)*2+1
            }
        }
    }

    sub plot(uword @zp x, uword @zp y, ubyte @zp color) {
        ubyte[8] @shared bits = [128, 64, 32, 16, 8, 4, 2, 1]
        ubyte[4] @shared mask4c = [%00111111, %11001111, %11110011, %11111100]
        ubyte[4] @shared shift4c = [6,4,2,0]

        when active_mode {
            1 -> {
                ; lores monochrome
                %asm {{
                    lda  x
                    eor  y
                    ora  monochrome_dont_stipple_flag
                    and  #1
                }}
                if_nz {
                    %asm {{
                        lda  x
                        and  #7
                        pha     ; xbits
                    }}
                    x /= 8
                    x += y*(320/8)
                    %asm {{
                        stz  cx16.VERA_CTRL
                        stz  cx16.VERA_ADDR_H
                        lda  x+1
                        sta  cx16.VERA_ADDR_M
                        lda  x
                        sta  cx16.VERA_ADDR_L
                        ply         ; xbits
                        lda  bits,y
                        ldy  color
                        beq  +
                        tsb  cx16.VERA_DATA0
                        bra  ++
+                       trb  cx16.VERA_DATA0
+
                    }}
                }
            }
            ; TODO mode 2,3
            4 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1
                    ora  #%00010000     ; enable auto-increment so next_pixel() can be used after this
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0+1
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0
                    sta  cx16.VERA_ADDR_L
                    lda  color
                    sta  cx16.VERA_DATA0
                }}
            }
            5 -> {
                ; highres monochrome
                %asm {{
                    lda  x
                    eor  y
                    ora  monochrome_dont_stipple_flag
                    and  #1
                }}
                if_nz {
                    %asm {{
                        lda  x
                        and  #7
                        pha     ; xbits
                    }}
                    x /= 8
                    x += y*(640/8)
                    %asm {{
                        stz  cx16.VERA_CTRL
                        stz  cx16.VERA_ADDR_H
                        lda  x+1
                        sta  cx16.VERA_ADDR_M
                        lda  x
                        sta  cx16.VERA_ADDR_L
                        ply     ; xbits
                        lda  bits,y
                        ldy  color
                        beq  +
                        tsb  cx16.VERA_DATA0
                        bra  ++
+                       trb  cx16.VERA_DATA0
+
                    }}
                }
            }
            6 -> {
                ; highres 4c
                ; TODO also mostly usable for lores 4c?
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                cx16.r2L = lsb(x) & 3       ; xbits
                color &= 3
                color <<= shift4c[cx16.r2L]     ; TODO with lookup table
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1L
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0H
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0L
                    sta  cx16.VERA_ADDR_L
                    ldy  cx16.r2L           ; xbits
                    lda  mask4c,y
                    and  cx16.VERA_DATA0
                    ora  color
                    sta  cx16.VERA_DATA0
                }}
            }
        }
    }

    sub pget(uword @zp x, uword y) -> ubyte {
        when active_mode {
            1 -> {
                ; lores monochrome
                %asm {{
                    lda  x
                    and  #7
                    pha     ; xbits
                }}
                x /= 8
                x += y*(320/8)
                %asm {{
                    stz  cx16.VERA_CTRL
                    stz  cx16.VERA_ADDR_H
                    lda  x+1
                    sta  cx16.VERA_ADDR_M
                    lda  x
                    sta  cx16.VERA_ADDR_L
                    ply         ; xbits
                    lda  plot.bits,y
                    and  cx16.VERA_DATA0
                    beq  +
                    lda  #1
+
                }}
            }
            ; TODO mode 2 and 3
            4 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
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
            5 -> {
                ; hires monochrome
                %asm {{
                    lda  x
                    and  #7
                    pha     ; xbits
                }}
                x /= 8
                x += y*(640/8)
                %asm {{
                    stz  cx16.VERA_CTRL
                    stz  cx16.VERA_ADDR_H
                    lda  x+1
                    sta  cx16.VERA_ADDR_M
                    lda  x
                    sta  cx16.VERA_ADDR_L
                    ply     ; xbits
                    lda  plot.bits,y
                    and  cx16.VERA_DATA0
                    beq  +
                    lda  #1
+
                }}
            }
            6 -> {
                ; hires 4c
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                %asm {{
                    stz  cx16.VERA_CTRL
                    lda  cx16.r1L
                    sta  cx16.VERA_ADDR_H
                    lda  cx16.r0H
                    sta  cx16.VERA_ADDR_M
                    lda  cx16.r0L
                    sta  cx16.VERA_ADDR_L
                    lda  cx16.VERA_DATA0
                    sta  cx16.r0L
                }}
                cx16.r1L = lsb(x) & 3
                cx16.r0L >>= gfx2.plot.shift4c[cx16.r1L]        ; TODO with lookup table
                return cx16.r0L & 3
            }
            else -> return 0
        }
    }

    sub fill(word @zp x, word @zp y, ubyte new_color) {
        ; Non-recursive scanline flood fill.
        ; based loosely on code found here https://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm
        ; with the fixes applied to the seedfill_4 routine as mentioned in the comments.
        const ubyte MAXDEPTH = 48
        word[MAXDEPTH] @shared stack_xl
        word[MAXDEPTH] @shared stack_xr
        word[MAXDEPTH] @shared stack_y
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
                    lda  cx16.r12L
                    asl  a
                    tay
                    lda  sxl
                    sta  stack_xl,y
                    lda  sxl+1
                    sta  stack_xl+1,y
                    lda  sxr
                    sta  stack_xr,y
                    lda  sxr+1
                    sta  stack_xr+1,y
                    lda  sy
                    sta  stack_y,y
                    lda  sy+1
                    sta  stack_y+1,y
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
;;            y += dy
            %asm {{
                dec  cx16.r12L
                lda  cx16.r12L
                asl  a
                tay
                lda  stack_xl,y
                sta  x1
                lda  stack_xl+1,y
                sta  x1+1
                lda  stack_xr,y
                sta  x2
                lda  stack_xr+1,y
                sta  x2+1
                lda  stack_y,y
                sta  y
                lda  stack_y+1,y
                sta  y+1
                ldy  cx16.r12L
                lda  stack_dy,y
                sta  dy
            }}
            y+=dy
        }
        cx16.r11L = pget(x as uword, y as uword)        ; old_color
        if cx16.r11L == cx16.r10L
            return
        if x<0 or x > width-1 or y<0 or y > height-1
            return
        push_stack(x, x, y, 1)
        push_stack(x, x, y + 1, -1)
        word left = 0
        while cx16.r12L {
            pop_stack()
            x = x1
            while x >= 0 and pget(x as uword, y as uword) == cx16.r11L {
                plot(x as uword, y as uword, cx16.r10L)
                x--
            }
            if x>= x1
                goto skip

            left = x + 1
            if left < x1
                push_stack(left, x1 - 1, y, -dy)
            x = x1 + 1

            do {
                while x <= width-1 and pget(x as uword, y as uword) == cx16.r11L {
                    plot(x as uword, y as uword, cx16.r10L)
                    x++
                }
                push_stack(left, x - 1, y, dy)
                if x > x2 + 1
                    push_stack(x2 + 1, x - 1, y, -dy)
skip:
                x++
                while x <= x2 and pget(x as uword, y as uword) != cx16.r11L
                    x++
                left = x
            } until x>x2
        }
    }

    sub position(uword @zp x, uword y) {
        ubyte bank
        when active_mode {
            1 -> {
                ; lores monochrome
                cx16.r0 = y*(320/8) + x/8
                cx16.vaddr(0, cx16.r0, 0, 1)
            }
            ; TODO modes 2,3
            4 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                bank = lsb(cx16.r1)
                cx16.vaddr(bank, cx16.r0, 0, 1)
            }
            5 -> {
                ; highres monochrome
                cx16.r0 = y*(640/8) + x/8
                cx16.vaddr(0, cx16.r0, 0, 1)
            }
            6 -> {
                ; highres 4c
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                bank = lsb(cx16.r1)
                cx16.vaddr(bank, cx16.r0, 0, 1)
            }
        }
    }

    sub position2(uword @zp x, uword y, bool also_port_1) {
        position(x, y)
        if also_port_1 {
            when active_mode {
                1, 5 -> cx16.vaddr(0, cx16.r0, 1, 1)
                ; TODO modes 2, 3
                4, 6 -> {
                    ubyte bank = lsb(cx16.r1)
                    cx16.vaddr(bank, cx16.r0, 1, 1)
                }
            }
        }
    }

    inline asmsub next_pixel(ubyte color @A) {
        ; -- sets the next pixel byte to the graphics chip.
        ;    for 8 bpp screens this will plot 1 pixel.
        ;    for 1 bpp screens it will plot 8 pixels at once (color = bit pattern).
        ;    for 2 bpp screens it will plot 4 pixels at once (color = bit pattern).
        %asm {{
            sta  cx16.VERA_DATA0
        }}
    }

    asmsub next_pixels(uword pixels @AY, uword amount @R0) clobbers(A, Y)  {
        ; -- sets the next bunch of pixels from a prepared array of bytes.
        ;    for 8 bpp screens this will plot 1 pixel per byte.
        ;    for 1 bpp screens it will plot 8 pixels at once (colors are the bit patterns per byte).
        ;    for 2 bpp screens it will plot 4 pixels at once (colors are the bit patterns per byte).
        %asm {{
            phx
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
+           plx
        }}
    }

    asmsub set_8_pixels_from_bits(ubyte bits @R0, ubyte oncolor @A, ubyte offcolor @Y) {
        ; this is only useful in 256 color mode where one pixel equals one byte value.
        %asm {{
            phx
            ldx  #8
-           asl  cx16.r0
            bcc  +
            sta  cx16.VERA_DATA0
            bra  ++
+           sty  cx16.VERA_DATA0
+           dex
            bne  -
            plx
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

    sub text(uword @zp x, uword y, ubyte color, uword sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        ;    NOTE: in monochrome (1bpp) screen modes, x position is currently constrained to multiples of 8 !  TODO allow per-pixel horizontal positioning
        ; TODO draw whole horizontal spans using vera auto increment if possible, instead of per-character columns
        uword chardataptr
        when active_mode {
            1, 5 -> {
                ; monochrome mode, either resolution
                cx16.r2 = 40
                if active_mode==5
                    cx16.r2 = 80
                while @(sctextptr) {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    cx16.vaddr(charset_bank, chardataptr, 1, 1)
                    position(x,y)
                    %asm {{
                        lda  cx16.VERA_ADDR_H
                        and  #%111              ; don't auto-increment, we have to do that manually because of the ora
                        sta  cx16.VERA_ADDR_H
                        lda  color
                        sta  P8ZP_SCRATCH_B1
                        ldy  #8
-                       lda  P8ZP_SCRATCH_B1
                        bne  +                  ; white color, plot normally
                        lda  cx16.VERA_DATA1
                        eor  #255               ; black color, keep only the other pixels
                        and  cx16.VERA_DATA0
                        bra  ++
+                       lda  cx16.VERA_DATA0
                        ora  cx16.VERA_DATA1
+                       sta  cx16.VERA_DATA0
                        lda  cx16.VERA_ADDR_L
                        clc
                        adc  cx16.r2
                        sta  cx16.VERA_ADDR_L
                        bcc  +
                        inc  cx16.VERA_ADDR_M
+                       inc  x
                        bne  +
                        inc  x+1
+                       dey
                        bne  -
                    }}
                    sctextptr++
                }
            }
            4 -> {
                ; lores 256c
                while @(sctextptr) {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    cx16.vaddr(charset_bank, chardataptr, 1, 1)
                    repeat 8 {
                        position(x,y)
                        y++
                        %asm {{
                            phx
                            ldx  color
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
                            plx
                        }}
                    }
                    x+=8
                    y-=8
                    sctextptr++
                }
            }
            6 -> {
                ; hires 4c
                ; we're going to use a few cx16 registers to make sure every variable is in zeropage in the inner loop.
                cx16.r11L = color
                cx16.r8 = y
                while @(sctextptr) {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    repeat 8 {
                        ; TODO rewrite this inner loop partly in assembly
                        ;      requires expanding the charbits to 2-bits per pixel (based on color)
                        ;      also it's way more efficient to draw whole horizontal spans instead of per-character
                        cx16.r9L = cx16.vpeek(charset_bank, chardataptr)  ; get the 8 horizontal character bits
                        cx16.r7 = x
                        repeat 8 {
                            cx16.r9L <<= 1
                            if_cs
                                plot(cx16.r7, cx16.r8, cx16.r11L)
                            cx16.r7++
                        }
                        chardataptr++
                        cx16.r8++
                    }
                    x+=8
                    cx16.r8-=8
                    sctextptr++
                }
            }
        }
    }

    asmsub cs_innerloop640() clobbers(Y) {
        %asm {{
            ldy  #80
-           stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
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
