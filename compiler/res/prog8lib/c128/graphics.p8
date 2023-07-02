%import syslib
%import textio

; Bitmap pixel graphics module for the Commodore 128

; TODO c128 actually implement the graphics routines. Ideally a way to 'borrow' the code form the C64 version without just copy-pasting that here?

graphics {
    %option no_symbol_prefixing

    const uword WIDTH = 320
    const ubyte HEIGHT = 200

    sub enable_bitmap_mode() {
        ; enable bitmap screen, erase it and set colors to black/white.
        ; TODO
    }

    sub disable_bitmap_mode() {
        ; enables text mode, erase the text screen, color white
        ; TODO
    }


    sub clear_screen(ubyte pixelcolor, ubyte bgcolor) {
        ; TODO
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        ; TODO
    }

    sub fillrect(uword x, uword y, uword width, uword height) {
        ; TODO
    }

    sub rect(uword x, uword y, uword width, uword height) {
        ; TODO
    }

    sub horizontal_line(uword x, uword y, uword length) {
        ; TODO
    }

    sub vertical_line(uword x, uword y, uword height) {
        ; TODO
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; TODO
    }

    sub disc(uword xcenter, ubyte ycenter, ubyte radius) {
        ; TODO
    }

    inline asmsub  plot(uword plotx @R0, uword ploty @R1) clobbers(A, X, Y) {
        %asm {{
            nop     ; TODO
        }}
    }
}
