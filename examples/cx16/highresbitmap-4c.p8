%target cx16
%import gfx2
%import string
%import textio
%zeropage basicsafe

main {

    sub start () {
        gfx2.text_charset(3)
        gfx2.screen_mode(6)

        testrect()
        ; testhorizontal()
        ;testvertical()
        sys.wait(9900)

        gfx2.screen_mode(0)
        txt.print("done!\n")
    }

    sub testrect() {
        uword siz
        for siz in 10 to 40 step 3 {
            widget.highlightedrect(100-siz, 100-siz, 100+siz*2, 10+siz*2, 1)
        }
    }

    sub testhorizontal() {
        uword width
        uword yy = 100
        for width in 40 to 60 {
            gfx2.horizontal_line(100, yy, width, 1)
            uword xx
            yy+=2
            for xx in 100 to 99+width {
                gfx2.plot(xx, yy, 2)
            }
            yy += 4
        }


    }

    sub testvertical() {
        uword height
        uword xx = 100
        for height in 40 to 60 {
            gfx2.vertical_line(xx, 100, height, 1)
            uword yy
            xx+=2
            for yy in 100 to 99+height {
                gfx2.plot(xx, yy, 2)
            }
            xx += 4
        }


    }

    sub testplot() {
        uword xx = 100
        uword yy = 100
        ubyte color

        repeat 6 {
            for color in 0 to 3 {
                for xx in 200 to 600 step 3 {
                    gfx2.plot(xx, yy, color)
                }
                yy += 5
            }
        }

        xx = 50
        repeat 6 {
            for color in 0 to 3 {
                for yy in 100 to 400 step 3 {
                    gfx2.plot(xx, yy, color)
                }
                xx += 5
            }
        }

        yy = 101
        repeat 6 {
            for color in 0 to 3 {
                gfx2.horizontal_line(200, yy, 400, color)
                yy += 5
            }
        }

        xx = 51
        repeat 6 {
            for color in 0 to 3 {
                gfx2.vertical_line(xx, 100, 300, color)
                xx += 5
            }
        }
    }
}

widget {

    sub highlightedrect(uword x, uword y, uword width, uword height, ubyte active) {
        gfx2.horizontal_line(x, y, width, 2)
        gfx2.vertical_line(x, y+1, height-1, 2)
        gfx2.vertical_line(x+width-1, y+1, height-1, 3)
        gfx2.horizontal_line(x+1, y+height-1, width-2, 2)       ; TODO SOMETIMES MISSES THE LAST PIXEL
;        if active
;            gfx2.fillrect(x+1,y+1,width-2,height-2, 3)
;        else
;            gfx2.fillrect(x+1,y+1,width-2,height-2, 0)
    }

    sub horizontal_line(uword x, uword y, uword length, ubyte color) {
        if length==0
            return

        uword xx
        for xx in x to x+length-1 {
            gfx2.plot(xx, y, color)
        }
    }

    sub icon(uword x, uword y, uword caption) {
        const ubyte width = 56
        const ubyte height = 28
        highlightedrect(x, y, width, height, false)
        uword middlex = x+width/2+1
        ubyte halfstring = string.length(caption) * 4
        gfx2.text(middlex-halfstring,y+height+1,1,caption)

        gfx2.monochrome_stipple(true)
        gfx2.disc(x+width/4+4, y+height/2, height/2-4, 1)
        gfx2.monochrome_stipple(false)
        gfx2.circle(x+width/4+4, y+height/2, height/2-4, 1)
        gfx2.fillrect(x+20,y+12,width/2,height/2-4,1)
    }


    sub window_titlebar(uword x, uword y, uword width, uword titlestr, ubyte active) {
        const ubyte height = 11
        widget.highlightedrect(x+widget.window_close_icon.width, y, width-62, height, active)
        gfx2.text(x+32, y+1, 1, titlestr)
        widget.window_close_icon(x, y, active)
        widget.window_order_icon(x+width-22, y, active)
        widget.window_flipsize_icon(x+width-44, y, active)
    }

    sub window_flipsize_icon(uword x, uword y, ubyte active) {
        const uword width = 22
        const uword height = 11
        highlightedrect(x, y, width, height, active)
        gfx2.rect(x+5, y+2, width-9, height-4, 1)
        gfx2.rect(x+5, y+2, 7, 4, 1)
        gfx2.fillrect(x+6, y+3, 5, 2, 2)
    }

    sub window_order_icon(uword x, uword y, ubyte active) {
        const uword width = 22
        const uword height = 11
        highlightedrect(x, y, width, height, active)
        gfx2.rect(x+4, y+2, 10, 5, 1)       ; back
        gfx2.fillrect(x+9, y+5, 8, 3, 2)       ; white front
        gfx2.rect(x+8, y+4, 10, 5, 1)       ; front
    }

    sub window_close_icon(uword x, uword y, ubyte active) {
        const uword width = 20
        const uword height = 11
        highlightedrect(x, y, width, height, active)
        gfx2.rect(x+7, y+3, 5, 5, 1)
        gfx2.fillrect(x+8, y+4, 3, 3, 2)
    }

    sub window_leftborder(uword x, uword y, uword height, ubyte active) {
        gfx2.vertical_line(x, y, height, 2)
        ubyte color = 0
        if active
            color = 3
        gfx2.vertical_line(x+1, y+11, height-11, color)
        gfx2.vertical_line(x+2, y+11, height-11, 1)
    }

    sub window_bottomborder(uword x, uword y, uword width, uword height) {
        gfx2.horizontal_line(x+3, y+height-2, width-3, 2)
        gfx2.horizontal_line(x, y+height-1, width, 1)
    }

    sub window_rightborder(uword x, uword y, uword width, uword height, ubyte active) {
        ; TODO scrollbar and scroll icons
        gfx2.vertical_line(x+width-1-16, y+11, height-13,2)
        gfx2.vertical_line(x+width-1, y+11, height-11,1)
        ubyte color = 0
        if active
            color = 3
        gfx2.fillrect(x+width-1-15, y+11, 15, height-12, color)

        gfx2.horizontal_line(x+width-1-13, y+height-3, 11, 1)
        gfx2.vertical_line(x+width-1-3, y+height-3-5, 5, 1)
        gfx2.line(x+width-1-13,y+height-3, x+width-1-3, y+height-3-5, 1)
        gfx2.horizontal_line(x+width-1-16, y+height-10, 16, 2)

    }
}
