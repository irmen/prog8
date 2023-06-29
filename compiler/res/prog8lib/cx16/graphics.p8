%import syslib
%import textio

; Bitmap pixel graphics module for the CommanderX16
; Wraps the graphics functions that are in ROM.
; Only lo-res 320x240 256 color mode for now.
; Unlike graphics module on the C64, you can use colors() to set new drawing colors for every draw operation.
; For other resolutions or other color modes, use the "gfx2" module instead. (which is Cx16-specific)
; Note: there is no color palette manipulation here, you have to do that yourself or use the "palette" module.


graphics {
    %option no_symbol_prefixing

    const uword WIDTH = 320
    const ubyte HEIGHT = 240


    ubyte stroke_color = 1
    ubyte background_color = 0

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        void cx16.screen_mode($80, false)
        cx16.GRAPH_init(0)
        clear_screen(1, 0)
    }

    sub disable_bitmap_mode() {
        ; enables text mode, erase the text screen, color white
        void cx16.screen_mode(0, false)
        txt.fill_screen(' ', 1)     ; doesn't seem to fully clear the text screen after returning from gfx mode
    }


    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        stroke_color = pixelcolor
        background_color = bgcolor
        cx16.GRAPH_set_colors(pixelcolor, pixelcolor, bgcolor)
        cx16.GRAPH_clear()
    }

    sub colors(ubyte stroke, ubyte fill) {
        ; this routine is only available on the cx16, other targets can't change colors on the fly
        cx16.GRAPH_set_colors(stroke, fill, background_color)
        stroke_color = stroke
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        cx16.GRAPH_draw_line(x1, y1, x2, y2)
    }

    sub fillrect(uword x, uword y, uword width, uword height) {
        cx16.GRAPH_draw_rect(x, y, width, height, 0, 1)
    }

    sub rect(uword x, uword y, uword width, uword height) {
        cx16.GRAPH_draw_rect(x, y, width, height, 0, 0)
    }

    sub horizontal_line(uword x, uword y, uword length) {
        if length
            cx16.GRAPH_draw_line(x, y, x+length-1, y)
    }

    sub vertical_line(uword x, uword y, uword height) {
        if height
            cx16.GRAPH_draw_line(x, y, x, y+height-1)
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
        word @zp decisionOver2 = (1 as word)-xx

        while xx>=yy {
            cx16.r0 = xcenter + xx
            cx16.r1 = ycenter + yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter + xx
            cx16.r1 = ycenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter + yy
            cx16.r1 = ycenter + xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter + yy
            cx16.r1 = ycenter - xx
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            cx16.r0 = xcenter - yy
            cx16.FB_cursor_position2()
            cx16.FB_set_pixel(stroke_color)
            yy++
            if decisionOver2<=0 {
                decisionOver2 += (yy as word)*2+1
            } else {
                xx--
                decisionOver2 += (yy as word -xx)*2+1
            }
        }
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        if radius==0
            return
        ubyte @zp yy = 0
        word decisionOver2 = (1 as word)-radius

        while radius>=yy {
            horizontal_line(xcenter-radius, ycenter+yy, radius*2+1)
            horizontal_line(xcenter-radius, ycenter-yy, radius*2+1)
            horizontal_line(xcenter-yy, ycenter+radius, yy*2+1)
            horizontal_line(xcenter-yy, ycenter-radius, yy*2+1)
            yy++
            if decisionOver2<=0
                decisionOver2 += (yy as word)*2+1
            else {
                radius--
                decisionOver2 += (yy as word -radius)*2+1
            }
        }
    }

    inline asmsub  plot(uword plotx @R0, uword ploty @R1) clobbers(A, X, Y) {
        %asm {{
            jsr  cx16.FB_cursor_position
            lda  graphics.stroke_color
            jsr  cx16.FB_set_pixel
        }}
    }
}
