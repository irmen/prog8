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
;   mode 2 = bitmap 320 x 240 x 4c (not yet implemented: just use 256c, there's enough vram for that)
;   mode 3 = bitmap 320 x 240 x 16c (not yet implemented: just use 256c, there's enough vram for that)
;   mode 4 = bitmap 320 x 240 x 256c  (like SCREEN $80 but using this api instead of kernal)
;   mode 5 = bitmap 640 x 480 monochrome
;   mode 6 = bitmap 640 x 480 x 4c
;   higher color dephts in highres are not supported due to lack of VRAM

; TODO remove the phx/plx pairs in non-stack compiler version

gfx2 {

    %option no_symbol_prefixing

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
            ; TODO modes 2, 3
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
            ; TODO modes 2, 3
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
        ubyte[9] masked_ends   = [ 0, %10000000, %11000000, %11100000, %11110000, %11111000, %11111100, %11111110, %11111111]
        ubyte[9] masked_starts = [ 0, %00000001, %00000011, %00000111, %00001111, %00011111, %00111111, %01111111, %11111111]

        if length==0
            return
        when active_mode {
            1, 5 -> {
                ; monochrome modes, either resolution
                ubyte separate_pixels = (8-lsb(x)) & 7
                if separate_pixels as uword > length
                    separate_pixels = lsb(length)
                if separate_pixels {
                    position(x,y)
                    cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                    cx16.VERA_DATA0 = cx16.VERA_DATA0 | masked_starts[separate_pixels]
                    length -= separate_pixels
                    x += separate_pixels
                }
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

                    cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off
                    cx16.VERA_DATA0 = cx16.VERA_DATA0 | masked_ends[separate_pixels]
                }
                cx16.VERA_ADDR_H &= %00000111   ; vera auto-increment off again
            }
            4 -> {
                ; lores 256c
                position(x, y)
                %asm {{
                    lda  color
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
+
                }}
            }
            6 -> {
                ; highres 4c ....also mostly usable for mode 2, lores 4c?
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
                ;; color &= 3
                ;; color <<= gfx2.plot.shift4c[lsb(x) & 3]
                cx16.r2L = lsb(x) & 3
                when color & 3 {
                    1 -> color = gfx2.plot.shiftedleft_4c_1[cx16.r2L]
                    2 -> color = gfx2.plot.shiftedleft_4c_2[cx16.r2L]
                    3 -> color = gfx2.plot.shiftedleft_4c_3[cx16.r2L]
                }
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
        ubyte[4] shiftedleft_4c_1 = [1<<6, 1<<4, 1<<2, 1<<0]
        ubyte[4] shiftedleft_4c_2 = [2<<6, 2<<4, 2<<2, 2<<0]
        ubyte[4] shiftedleft_4c_3 = [3<<6, 3<<4, 3<<2, 3<<0]

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
            ; TODO modes 2, 3
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
                ; highres 4c   ....also mostly usable for mode 2, lores 4c?
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                cx16.r2L = lsb(x) & 3       ; xbits
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
            ; TODO modes 2, 3
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
                cx16.r0L >>= gfx2.plot.shift4c[cx16.r1L]
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
                sta  y
                lda  stack_y_msb,y
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
        when active_mode {
            1 -> {
                ; lores monochrome
                cx16.r0 = y*(320/8) + x/8
                cx16.vaddr(0, cx16.r0, 0, 1)
            }
            ; TODO modes 2, 3
            4 -> {
                ; lores 256c
                void addr_mul_24_for_lores_256c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                cx16.r2L = cx16.r1L
                cx16.vaddr(cx16.r2L, cx16.r0, 0, 1)
            }
            5 -> {
                ; highres monochrome
                cx16.r0 = y*(640/8) + x/8
                cx16.vaddr(0, cx16.r0, 0, 1)
            }
            6 -> {
                ; highres 4c
                void addr_mul_24_for_highres_4c(y, x)      ; 24 bits result is in r0 and r1L (highest byte)
                cx16.r2L = cx16.r1L
                cx16.vaddr(cx16.r2L, cx16.r0, 0, 1)
            }
        }
    }

    sub position2(uword @zp x, uword y, bool also_port_1) {
        position(x, y)
        if also_port_1
            cx16.vaddr_clone(0)
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

    asmsub next_pixels(uword pixels @AY, uword amount @R0) clobbers(A, X, Y)  {
        ; -- sets the next bunch of pixels from a prepared array of bytes.
        ;    for 8 bpp screens this will plot 1 pixel per byte.
        ;    for 1 bpp screens it will plot 8 pixels at once (colors are the bit patterns per byte).
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

    sub text(uword @zp x, uword y, ubyte color, uword sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        when active_mode {
            1, 5 -> {
                ; monochrome mode, either resolution
                cx16.r3 = sctextptr
                while @(cx16.r3) {
                    chardataptr = charset_addr + @(cx16.r3) * $0008
                    ; copy the character bitmap into RAM
                    cx16.vaddr_autoincr(charset_bank, chardataptr, 0, 1)
                    %asm {{
                        ; pre-shift the bits
                        phx ; TODO remove in non-stack version
                        lda  text.x
                        and  #7
                        sta  P8ZP_SCRATCH_B1
                        ldy  #0
-                       lda  cx16.VERA_DATA0
                        stz  P8ZP_SCRATCH_REG
                        ldx  P8ZP_SCRATCH_B1
                        cpx  #0
                        beq  +
-                       lsr  a
                        ror  P8ZP_SCRATCH_REG
                        dex
                        bne  -
+                       sta  char_bitmap_bytes_left,y
                        lda  P8ZP_SCRATCH_REG
                        sta  char_bitmap_bytes_right,y
                        iny
                        cpy  #8
                        bne  --
                        plx     ; TODO remove in non-stack version
                    }}
                    ; left part of shifted char
                    position2(x, y, true)
                    set_autoincrs_mode1_or_5()
                    if color {
                        %asm {{
                            ldy  #0
-                           lda  char_bitmap_bytes_left,y
                            ora  cx16.VERA_DATA1
                            sta  cx16.VERA_DATA0
                            iny
                            cpy  #8
                            bne  -
                        }}
                    } else {
                        %asm {{
                            ldy  #0
-                           lda  char_bitmap_bytes_left,y
                            eor  #255
                            and  cx16.VERA_DATA1
                            sta  cx16.VERA_DATA0
                            iny
                            cpy  #8
                            bne  -
                        }}
                    }
                    ; right part of shifted char
                    if lsb(x) & 7 {
                        position2(x+8, y, true)
                        set_autoincrs_mode1_or_5()
                        if color {
                            %asm {{
                                ldy  #0
    -                           lda  char_bitmap_bytes_right,y
                                ora  cx16.VERA_DATA1
                                sta  cx16.VERA_DATA0
                                iny
                                cpy  #8
                                bne  -
                            }}
                        } else {
                            %asm {{
                                ldy  #0
    -                           lda  char_bitmap_bytes_right,y
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
                    x += 8
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
                while @(sctextptr) {
                    chardataptr = charset_addr + (@(sctextptr) as uword)*8
                    cx16.vaddr(charset_bank, chardataptr, 1, true)  ; for reading the chardata from Vera data channel 1
                    position(x, y)              ; only calculated once, we update vera address in the loop instead
                    cx16.VERA_ADDR_H &= $0f     ; no auto increment
                    repeat 8 {
                        cx16.r10L = cx16.VERA_DATA1  ; get the next 8 horizontal character bits
                        cx16.r7 = x
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
                    x+=8
                    sctextptr++
                }
            }
        }

        sub set_autoincrs_mode1_or_5() {
            ; set autoincrements to go to next pixel row (40 or 80 increment)
            if active_mode==1 {
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
