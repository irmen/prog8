%target cx16
%import syslib
%import textio

; Bitmap pixel graphics module for the CommanderX16
; wraps the graphics functions that are in ROM.
; only black/white monchrome 320x200 for now. (i.e. truncated at the bottom)
; For full-screen 640x480 or 320x240 graphics, use the "gfx2" module instead. (but that is Cx16-specific)
; Note: there is no color palette manipulation here, you have to do that yourself or use the "palette" module.


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
        if radius==0
            return
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

        if radius==0
            return
        ubyte @zp xx = radius
        ubyte @zp yy = 0
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

    inline asmsub  plot(uword plotx @R0, uword ploty @R1) {
        %asm {{
            jsr  cx16.FB_cursor_position
            lda  #1
            jsr  cx16.FB_set_pixel
        }}
    }
}
