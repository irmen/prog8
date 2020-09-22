%target cx16
%import syslib

; bitmap pixel graphics module for the CommanderX16
; wraps the graphics functions that are in ROM.
; only black/white monchrome 320x200 for now.

graphics {

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        void cx16.screen_set_mode($80)
        cx16.r0 = 0
        cx16.GRAPH_init()
        clear_screen(1, 0)
    }

    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        cx16.GRAPH_set_colors(pixelcolor, pixelcolor, bgcolor)
        cx16.GRAPH_clear()
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        cx16.r0 = x1
        cx16.r1 = y1
        cx16.r2 = x2
        cx16.r3 = y2
        cx16.GRAPH_draw_line()
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        cx16.r0 = xcenter - radius/2
        cx16.r1 = ycenter - radius/2
        cx16.r2 = radius*2
        cx16.r3 = radius*2
        cx16.GRAPH_draw_oval(false)          ; TODO  currently is not implemented on cx16, does a BRK
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        cx16.r0 = xcenter - radius/2
        cx16.r1 = ycenter - radius/2
        cx16.r2 = radius*2
        cx16.r3 = radius*2
        cx16.GRAPH_draw_oval(true)          ; TODO  currently is not implemented on cx16, does a BRK
    }

    sub  plot(uword plotx, ubyte ploty) {
        cx16.r0 = plotx
        cx16.r1 = ploty
        cx16.FB_cursor_position()
        cx16.FB_cursor_position()
        cx16.FB_set_pixel(1)
    }
}


