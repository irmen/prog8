; Monochrome Bitmap pixel graphics routines for the Virtual Machine
; Using the full-screen 640x480 and 320x240 screen modes, but just black/white.
;
; NOTE: For sake of speed, NO BOUNDS CHECKING is performed in most routines!
;       You'll have to make sure yourself that you're not writing outside of bitmap boundaries!


monogfx {

    %option ignore_unused

    ; read-only control variables:
    uword width = 0
    uword height = 0
    ubyte mode
    const ubyte MODE_NORMAL  = %00000000
    const ubyte MODE_STIPPLE = %00000001
    const ubyte MODE_INVERT  = %00000010

    sub lores() {
        ; enable 320*240 bitmap mode
        sys.gfx_enable(0)
        width = 320
        height = 240
        mode = MODE_NORMAL
        clear_screen(0)
    }

    sub hires() {
        ; enable 640*480 bitmap mode
        sys.gfx_enable(1)
        width = 640
        height = 480
        mode = MODE_NORMAL
        clear_screen(0)
    }

    sub textmode() {
        ; back to normal text mode
    }

    sub drawmode(ubyte dm) {
        mode = dm
    }

    sub clear_screen(ubyte color) {
        if color
            color=255
        sys.gfx_clear(color)
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
        uword xpos
        for xpos in xx to xx+length-1
            plot(xpos, yy, draw)
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
        uword ypos
        for ypos in yy to yy+lheight-1
            plot(xx, ypos, draw)
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
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
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
        if draw {
            when mode {
                MODE_NORMAL -> {
                    sys.gfx_plot(xx, yy, 255)
                }
                MODE_STIPPLE -> {
                    if (xx ^ yy)&1
                        sys.gfx_plot(xx, yy, 255)
                    else
                        sys.gfx_plot(xx, yy, 0)
                }
                MODE_INVERT -> {
                    sys.gfx_plot(xx, yy, 255 ^ sys.gfx_getpixel(xx, yy))
                }
            }
        }
        else
            sys.gfx_plot(xx, yy, 0)
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
        return sys.gfx_getpixel(xx, yy)
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
                stack_xl[cx16.r12L] = sxl
                stack_xr[cx16.r12L] = sxr
                stack_y[cx16.r12L] = sy
                stack_dy[cx16.r12L] = sdy
                cx16.r12L++
            }
        }
        sub pop_stack() {
            cx16.r12L--
            x1 = stack_xl[cx16.r12L]
            x2 = stack_xr[cx16.r12L]
            yy = stack_y[cx16.r12L]
            dy = stack_dy[cx16.r12L]
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

    sub text_charset(ubyte charset) {
        ; -- select the text charset to use with the text() routine
        ;    the charset number is the same as for the cx16.screen_set_charset() ROM function.
        ;    1 = ISO charset, 2 = PETSCII uppercase+graphs, 3= PETSCII uppercase+lowercase.
        ; TODO vm bitmap charset
    }

    sub text(uword @zp xx, uword yy, bool draw, uword sctextptr) {
        ; -- Write some text at the given pixel position. The text string must be in screencode encoding (not petscii!).
        ;    You must also have called text_charset() first to select and prepare the character set to use.
        ; TODO vm bitmap charset
    }
}
