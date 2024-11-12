%import syslib

; Bitmap pixel graphics module for the CommanderX16
; Wraps the graphics functions that are in ROM.
; Only lo-res 320x240 256 color mode for now.
; Unlike graphics module on the C64, you can use colors() to set new drawing colors for every draw operation.
; For other resolutions or other color modes, use the "gfx_lores", "gfx_hires", or "monogfx" module instead.
; Note: there is no color palette manipulation here, you have to do that yourself or use the "palette" module.
;
; NOTE: For sake of speed, NO BOUNDS CHECKING is performed in most routines!
;       You'll have to make sure yourself that you're not writing outside of bitmap boundaries!
;


graphics {
    %option ignore_unused

    extsub $feff = FB_cursor_position2()  clobbers(A,X,Y)     ; alias for the normal FB_cursor_position() call but reuses existing r0 and r1

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
        cbm.CHROUT(147)
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

    sub fillrect(uword xx, uword yy, uword width, uword height) {
        cx16.GRAPH_draw_rect(xx, yy, width, height, 0, true)
    }

    sub rect(uword xx, uword yy, uword width, uword height) {
        cx16.GRAPH_draw_rect(xx, yy, width, height, 0, false)
    }

    sub horizontal_line(uword xx, uword yy, uword length) {
        if length!=0
            cx16.GRAPH_draw_line(xx, yy, xx+length-1, yy)
    }

    sub vertical_line(uword xx, uword yy, uword height) {
        if height!=0
            cx16.GRAPH_draw_line(xx, yy, xx, yy+height-1)
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        if radius==0
            return
        cx16.GRAPH_draw_oval(xcenter - radius, ycenter - radius, radius*2, radius*2, false)
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Warning: NO BOUNDS CHECKS. Make sure circle fits in the screen.
        if radius==0
            return
        cx16.GRAPH_draw_oval(xcenter - radius, ycenter - radius, radius*2, radius*2, true)
    }

    sub oval(uword xcenter, ubyte ycenter, uword h_radius, ubyte v_radius) {
        ; specific for the X16 which has a kernal routine that can draw ovals
        if h_radius==0 or v_radius==0
            return
        cx16.GRAPH_draw_oval(xcenter - h_radius, ycenter - v_radius, h_radius*2, v_radius*2, false)
    }

    sub filled_oval(uword xcenter, ubyte ycenter, uword h_radius, ubyte v_radius) {
        ; specific for the X16 which has a kernal routine that can draw ovals
        if h_radius==0 or v_radius==0
            return
        cx16.GRAPH_draw_oval(xcenter - h_radius, ycenter - v_radius, h_radius*2, v_radius*2, true)
    }

    inline asmsub  plot(uword plotx @R0, uword ploty @R1) clobbers(A, X, Y) {
        %asm {{
            jsr  cx16.FB_cursor_position
            lda  p8b_graphics.p8v_stroke_color
            jsr  cx16.FB_set_pixel
        }}
    }
}
