%target cx16
%import syslib
%import textio

; bitmap pixel graphics module for the CommanderX16
; wraps the graphics functions that are in ROM.
; only black/white monchrome 320x200 for now.

graphics {
    const uword WIDTH = 320
    const ubyte HEIGHT = 200

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        void cx16.screen_set_mode($80)
        cx16.GRAPH_init(0)
        clear_screen(1, 0)
    }

    sub disable_bitmap_mode() {
        ; enables text mode, erase the text screen, color white
        void cx16.screen_set_mode(2)
        txt.fill_screen(' ', 1)     ; doesn't seem to fully clear the text screen after returning from gfx mode
    }


    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        cx16.GRAPH_set_colors(pixelcolor, pixelcolor, bgcolor)
        cx16.GRAPH_clear()
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        cx16.GRAPH_draw_line(x1, y1, x2, y2)
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ;cx16.r0 = xcenter - radius/2
        ;cx16.r1 = ycenter - radius/2
        ;cx16.r2 = radius*2
        ;cx16.r3 = radius*2
        ;cx16.GRAPH_draw_oval(false)          ; currently this call is not implemented on cx16, does a BRK

        ; Midpoint algorithm
        ubyte @zp xx = radius
        ubyte @zp yy = 0
        byte @zp decisionOver2 = 1-xx as byte

        while xx>=yy {
            cx16.r0 = xcenter + xx
            cx16.r1 = ycenter + yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter + xx
            cx16.r1 = ycenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter + yy
            cx16.r1 = ycenter + xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter + yy
            cx16.r1 = ycenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            cx16.r0 = xcenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(1)
            yy++
            if decisionOver2<=0 {
                decisionOver2 += 2*yy+1
            } else {
                xx--
                decisionOver2 += 2*(yy-xx)+1
            }
        }
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
;        cx16.r0 = xcenter - radius/2
;        cx16.r1 = ycenter - radius/2
;        cx16.r2 = radius*2
;        cx16.r3 = radius*2
;        cx16.GRAPH_draw_oval(true)          ; currently this call is not implemented on cx16, does a BRK

        ubyte xx = radius
        ubyte yy = 0
        byte decisionOver2 = 1-xx as byte

        while xx>=yy {
            ubyte ycenter_plus_yy = ycenter + yy
            ubyte ycenter_min_yy = ycenter - yy
            ubyte ycenter_plus_xx = ycenter + xx
            ubyte ycenter_min_xx = ycenter - xx
            uword @zp plotx

            cx16.FB_cursor_position(xcenter-xx, ycenter_plus_yy)
            repeat xx*2+1
                cx16.FB_set_pixel(1)

            cx16.FB_cursor_position(xcenter-xx, ycenter_min_yy)
            repeat xx*2+1
                cx16.FB_set_pixel(1)

            cx16.FB_cursor_position(xcenter-yy, ycenter_plus_xx)
            repeat yy*2+1
                cx16.FB_set_pixel(1)

            cx16.FB_cursor_position(xcenter-yy, ycenter_min_xx)
            repeat yy*2+1
                cx16.FB_set_pixel(1)

            yy++
            if decisionOver2<=0
                decisionOver2 += 2*yy+1
            else {
                xx--
                decisionOver2 += 2*(yy-xx)+1
            }
        }
    }

    sub  plot(uword plotx, ubyte ploty) {
        cx16.FB_cursor_position(plotx, ploty)
        cx16.FB_set_pixel(1)
    }
}


