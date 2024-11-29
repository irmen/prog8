; optimized graphics routines for just the single screen mode: lores 320*240, 256c  (8bpp)
; bitmap image needs to start at VRAM addres $00000.
; This is compatible with the CX16's screen mode 128.  (void cx16.set_screen_mode(128))


%import syslib
%import verafx
%import buffers

gfx_lores {
    %option ignore_unused

    const uword WIDTH = 320
    const ubyte HEIGHT = 240

    sub graphics_mode() {
        ; enable 320x240 256c bitmap graphics mode
        cx16.VERA_CTRL=0
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 64
        cx16.VERA_DC_VSCALE = 64
        cx16.VERA_L1_CONFIG = %00000111
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = 0
        clear_screen(0)
        drawmode_eor(false)
    }

    sub text_mode() {
        ; back to normal text mode
        cx16.r15L = cx16.VERA_DC_VIDEO & %00000111 ; retain chroma + output mode
        cbm.CINT()
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11111000) | cx16.r15L
    }

    sub drawmode_eor(bool enabled) {
        ; with EOR drawing mode you can have non destructive drawing (2*EOR=restore original)
        eor_mode = enabled
    }

    bool eor_mode

    sub clear_screen(ubyte color) {
        if verafx.available() {
            ; use verafx cache writes to quicly clear the screen
            const ubyte vbank = 0
            const uword vaddr = 0
            cx16.VERA_CTRL = 0
            cx16.VERA_ADDR_H = vbank | %00110000       ; 4-byte increment
            cx16.VERA_ADDR_M = msb(vaddr)
            cx16.VERA_ADDR_L = lsb(vaddr)
            cx16.VERA_CTRL = 6<<1       ; dcsel = 6, fill the 32 bits cache
            cx16.VERA_FX_CACHE_L = color
            cx16.VERA_FX_CACHE_M = color
            cx16.VERA_FX_CACHE_H = color
            cx16.VERA_FX_CACHE_U = color
            cx16.VERA_CTRL = 2<<1       ; dcsel = 2
            cx16.VERA_FX_MULT = 0
            cx16.VERA_FX_CTRL = %01000000    ; cache write enable
            repeat 320/4/4 {
                %asm {{
                    ldy  #240
-                   stz  cx16.VERA_DATA0
                    stz  cx16.VERA_DATA0
                    stz  cx16.VERA_DATA0
                    stz  cx16.VERA_DATA0
                    dey
                    bne  -
                }}
            }
            cx16.VERA_FX_CTRL = 0       ; cache write disable
            cx16.VERA_CTRL = 0
            return
        }
        ; fallback to cpu clear
        cx16.VERA_CTRL=0
        cx16.VERA_ADDR=0
        cx16.VERA_ADDR_H = 1<<4    ; 1 pixel auto increment
        repeat HEIGHT {
            %asm {{
                lda  p8v_color
                ldy  #p8c_WIDTH/8
-               .rept 8
                sta  cx16.VERA_DATA0
                .endrept
                dey
                bne  -
            }}
        }
        cx16.VERA_ADDR=0
        cx16.VERA_ADDR_H = 0
    }

    sub rect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
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

    sub safe_rect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; does bounds checking and clipping
        safe_horizontal_line(xx, yy, rwidth, color)
        if rheight==1
            return
        uword bottomyy = yy as uword + rheight -1
        if bottomyy<HEIGHT
            safe_horizontal_line(xx, lsb(bottomyy), rwidth, color)
        safe_vertical_line(xx, yy+1, rheight-2, color)
        if rwidth==1
            return
        safe_vertical_line(xx+rwidth-1, yy+1, rheight-2, color)
    }

    sub fillrect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
        if rwidth==0
            return
        repeat rheight {
            horizontal_line(xx, yy, rwidth, color)
            yy++
        }
    }

    sub safe_fillrect(uword xx, ubyte yy, uword rwidth, ubyte rheight, ubyte color) {
        ; Draw a filled rectangle of the given size and color.
        ; To fill the whole screen, use clear_screen(color) instead - it is much faster.
        ; This safe version does bounds checking and clipping.
        if xx>=WIDTH or yy>=HEIGHT
            return
        if msb(xx)&$80!=0 {
            rwidth += xx
            xx = 0
        }
        if xx>=WIDTH
            return
        if xx+rwidth>WIDTH
            rwidth = WIDTH-xx
        if rwidth>WIDTH
            return

        if yy as uword + rheight > HEIGHT
            rheight = HEIGHT-yy
        if rheight>HEIGHT
            return

        repeat rheight {
            horizontal_line(xx, yy, rwidth, color)
            yy++
        }
    }

    sub horizontal_line(uword xx, ubyte yy, uword length, ubyte color) {
        if length==0
            return
        position(xx, yy)
        ; set vera auto-increment to 1 pixel
        cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (1<<4)
        if eor_mode {
            cx16.vaddr_clone(0)      ; also setup port 1, for reading
            %asm {{
                ldx  p8v_length+1
                beq  +
                ldy  #0
-               lda  p8v_color
                eor  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                iny
                bne  -
                dex
                bne  -
+               ldy  p8v_length     ; remaining
                beq  +
-               lda  p8v_color
                eor  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                dey
                bne  -
+
            }}
        } else {
            %asm {{
                lda  p8v_color
                ldx  p8v_length+1
                beq  +
                ldy  #0
-               sta  cx16.VERA_DATA0
                iny
                bne  -
                dex
                bne  -
+               ldy  p8v_length     ; remaining
                beq  +
-               sta  cx16.VERA_DATA0
                dey
                bne  -
+
            }}
        }
    }

    sub safe_horizontal_line(uword xx, ubyte yy, uword length, ubyte color) {
        ; does bounds checking and clipping
        if yy>=HEIGHT
            return
        if msb(xx)&$80!=0 {
            length += xx
            xx = 0
        }
        if xx>=WIDTH
            return
        if xx+length>WIDTH
            length = WIDTH-xx
        if length>WIDTH
            return

        horizontal_line(xx, yy, length, color)
    }

    sub vertical_line(uword xx, ubyte yy, ubyte lheight, ubyte color) {
        if lheight==0
            return
        position(xx, yy)
        ; set vera auto-increment to 320 pixel increment (=next line)
        cx16.VERA_ADDR_H = cx16.VERA_ADDR_H & %00000111 | (14<<4)
        if eor_mode {
            cx16.vaddr_clone(0)      ; also setup port 1, for reading
            %asm {{
                ldy  p8v_lheight
                beq  +
-               lda  p8v_color
                eor  cx16.VERA_DATA1
                sta  cx16.VERA_DATA0
                dey
                bne  -
+
            }}
        } else {
            %asm {{
                ldy  p8v_lheight
                lda  p8v_color
-               sta  cx16.VERA_DATA0
                dey
                bne  -
            }}
        }
    }

    sub safe_vertical_line(uword xx, ubyte yy, ubyte lheight, ubyte color) {
        ; does bounds checking and clipping
        if yy>=HEIGHT
            return
        if msb(xx)&$80!=0 or xx>=WIDTH
            return
        if yy as uword + lheight > HEIGHT
            lheight = HEIGHT-yy
        if lheight>HEIGHT
            return

        vertical_line(xx, yy, lheight, color)
    }

    sub line(uword x1, ubyte y1, uword x2, ubyte y2, ubyte color) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        ; NOTE:  this is about twice as fast as the kernal routine GRAPH_draw_line
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
            ; internal plot routine for the line algorithm: x in r0,  y in r1,  color in variable.
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

                lda  p8v_eor_mode
                bne  +
                lda  p8v_color
                sta  cx16.VERA_DATA0
                rts
+               lda  p8v_color
                eor  cx16.VERA_DATA0
                sta  cx16.VERA_DATA0
                rts
            }}
        }
    }

    sub circle(uword @zp xcenter, ubyte @zp ycenter, ubyte radius, ubyte color) {
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
            plot(cx16.r14, cx16.r15L, color)
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
            if cx16.r15 < HEIGHT
                safe_plot(cx16.r14, cx16.r15L, color)
        }
    }

    sub disc(uword @zp xcenter, ubyte @zp ycenter, ubyte @zp radius, ubyte color) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        ubyte last_y3 = ycenter+radius
        ubyte last_y4 = ycenter-radius
        ubyte new_y3, new_y4

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*$0002+1, color)
            horizontal_line(xcenter-radius, ycenter-yy, radius*$0002+1, color)

            new_y3 = ycenter+radius
            if new_y3 != last_y3 {
                horizontal_line(xcenter-yy, last_y3, yy*$0002+1, color)
                last_y3 = new_y3
            }
            new_y4 = ycenter-radius
            if new_y4 != last_y4 {
                horizontal_line(xcenter-yy, last_y4, yy*$0002+1, color)
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
        horizontal_line(xcenter-yy, last_y3, yy*$0002+1, color)
        horizontal_line(xcenter-yy, last_y4, yy*$0002+1, color)
    }

    sub safe_disc(uword @zp xcenter, uword @zp ycenter, ubyte @zp radius, ubyte color) {
        ; This version does bounds checks and clipping, but is a lot slower.
        ; Midpoint algorithm, filled
        if radius==0
            return
        ubyte @zp yy = 0
        word @zp decisionOver2 = (1 as word)-radius
        uword last_y3 = ycenter+radius
        uword last_y4 = ycenter-radius
        uword new_y3, new_y4

        while radius>=yy {
            uword liney = ycenter+yy
            if msb(liney)==0
                safe_horizontal_line(xcenter-radius, lsb(ycenter+yy), radius*$0002+1, color)
            liney = ycenter-yy
            if msb(liney)==0
                safe_horizontal_line(xcenter-radius, lsb(ycenter-yy), radius*$0002+1, color)
            new_y3 = ycenter+radius
            if new_y3 != last_y3 {
                if msb(last_y3)==0
                    safe_horizontal_line(xcenter-yy, lsb(last_y3), yy*$0002+1, color)
                last_y3 = new_y3
            }
            new_y4 = ycenter-radius
            if new_y4 != last_y4 {
                if msb(last_y4)==0
                    safe_horizontal_line(xcenter-yy, lsb(last_y4), yy*$0002+1, color)
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
        if msb(last_y3)==0
            safe_horizontal_line(xcenter-yy, lsb(last_y3), yy*$0002+1, color)
        if msb(last_y4)==0
            safe_horizontal_line(xcenter-yy, lsb(last_y4), yy*$0002+1, color)
    }

    asmsub plot(uword x @AX, ubyte y @Y, ubyte color @R0) {
        ; x in r0,  y in r1,   color.
        %asm {{
            clc
            adc  times320_lo,y
            sta  cx16.VERA_ADDR_L
            txa
            adc  times320_mid,y
            sta  cx16.VERA_ADDR_M
            lda  #0
            adc  times320_hi,y
            sta  cx16.VERA_ADDR_H

            lda  p8v_eor_mode
            bne  +
            lda  cx16.r0L
            sta  cx16.VERA_DATA0
            rts
+           lda  cx16.r0L
            eor  cx16.VERA_DATA0
            sta  cx16.VERA_DATA0
            rts
        }}
    }

    sub safe_plot(uword xx, ubyte yy, ubyte color) {
        ; A plot that does bounds checks to see if the pixel is inside the screen.
        if msb(xx)&$80!=0
            return
        if xx >= WIDTH or yy >= HEIGHT
            return
        plot(xx, yy, color)
    }

    asmsub pget(uword x @AX, ubyte y @Y) -> ubyte @A {
        ; returns the color of the pixel
        %asm {{
            jsr  p8s_position
            lda  cx16.VERA_DATA0
            rts
        }}
    }

    sub fill(uword x, ubyte y, ubyte new_color, ubyte stack_rambank) {
        ; reuse a few virtual registers in ZP for variables
        &ubyte fillm = &cx16.r7L
        &ubyte seedm = &cx16.r8L
        &ubyte cmask = &cx16.r8H
        &ubyte vub   = &cx16.r13L
        &ubyte nvub  = &cx16.r13H
        ubyte[4] amask = [$c0,$30,$0c,$03] ; array of cmask bytes

        ; Non-recursive scanline flood fill.
        ; based loosely on code found here https://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm
        ; with the fixes applied to the seedfill_4 routine as mentioned in the comments.
        word @zp xx = x as word
        word @zp yy = y as word
        word x1
        word x2
        byte dy
        cx16.r10L = new_color
        stack.init(stack_rambank)

        sub push_stack(word sxl, word sxr, word sy, byte sdy) {
            cx16.r0s = sy+sdy
            if cx16.r0s>=0 and cx16.r0s<=HEIGHT-1 {
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
        cx16.r11L = pget(xx as uword, lsb(yy))        ; old_color
        if cx16.r11L == cx16.r10L
            return
        if xx<0 or xx>WIDTH-1 or yy<0 or yy>HEIGHT-1
            return
        push_stack(xx, xx, yy, 1)
        push_stack(xx, xx, yy + 1, -1)
        word left = 0
        while not stack.isempty() {
            pop_stack()
            xx = x1
            if fill_scanline_left_8bpp() goto skip
            left = xx + 1
            if left < x1
                push_stack(left, x1 - 1, yy, -dy)
            xx = x1 + 1

            do {
                fill_scanline_right_8bpp()
                push_stack(left, xx - 1, yy, dy)
                if xx > x2 + 1
                    push_stack(x2 + 1, xx - 1, yy, -dy)
skip:
                xx++
                while xx <= x2 {
                    if pget(xx as uword, lsb(yy)) == cx16.r11L
                        break
                    xx++
                }
                left = xx
            } until xx>x2
        }

        sub set_vera_address(bool decr) {
            ; set both data0 and data1 addresses
            position(xx as uword, lsb(yy))
            cx16.r0 = cx16.VERA_ADDR
            cx16.r1L = cx16.VERA_ADDR_H & 1 | if decr %00011000 else %00010000
            cx16.VERA_ADDR_H = cx16.r1L
            cx16.VERA_CTRL = 1
            cx16.VERA_ADDR = cx16.r0
            cx16.VERA_ADDR_H = cx16.r1L
            cx16.VERA_CTRL = 0
        }

        sub fill_scanline_left_8bpp() -> bool {
            set_vera_address(true)
            cx16.r9s = xx
            while xx >= 0 {
                if cx16.VERA_DATA0 != cx16.r11L
                    break
                cx16.VERA_DATA1 = cx16.r10L
                xx--
            }
            return xx==cx16.r9s
        }

        sub fill_scanline_right_8bpp() {
            set_vera_address(false)
            while xx <= WIDTH-1 {
                if cx16.VERA_DATA0 != cx16.r11L
                    break
                cx16.VERA_DATA1 = cx16.r10L
                xx++
            }
        }
    }

    sub text_charset(ubyte charset) {
        ; -- select the text charset to use with the text() routine
        ;    the charset number is the same as for the cx16.screen_set_charset() ROM function.
        ;    1 = ISO charset, 2 = PETSCII uppercase+graphs, 3= PETSCII uppercase+lowercase etc. etc.
        cx16.screen_set_charset(charset, 0)
    }

    const ubyte charset_bank = $1
    const uword charset_addr = $f000       ; in bank 1, so $1f000

    sub text(uword @zp xx, uword yy, ubyte color, uword textptr) {
        ; -- Write some text at the given pixel position. The text string must be in an encoding approprite for the charset.
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        uword chardataptr
        ubyte[8] @shared char_bitmap_bytes_left
        ubyte[8] @shared char_bitmap_bytes_right

        while @(textptr)!=0 {
            chardataptr = charset_addr + (@(textptr) as uword)*8
            cx16.vaddr(charset_bank, chardataptr, 1, 1)
            repeat 8 {
                position(xx,lsb(yy))
                yy++
                %asm {{
                    ldx  p8v_color
                    lda  cx16.VERA_DATA1
                    sta  P8ZP_SCRATCH_B1
                    ldy  #8
-                   asl  P8ZP_SCRATCH_B1
                    bcc  +
                    stx  cx16.VERA_DATA0    ; write a pixel
                    bra  ++
+                   lda  cx16.VERA_DATA0    ; don't write a pixel, but do advance to the next address
+                   dey
                    bne  -
                }}
            }
            xx+=8
            yy-=8
            textptr++
        }
    }

    asmsub position(uword x @AX, ubyte y @Y) {
        %asm {{
            clc
            adc  times320_lo,y
            sta  cx16.VERA_ADDR_L
            txa
            adc  times320_mid,y
            sta  cx16.VERA_ADDR_M
            lda  #%00010000         ; auto increment on
            adc  times320_hi,y
            sta  cx16.VERA_ADDR_H
            rts
        }}
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

    %asm {{
; multiplication by 320 lookup table
times320 := 320*range(240)

times320_lo     .byte <times320
times320_mid    .byte >times320
times320_hi     .byte `times320
    }}
}
