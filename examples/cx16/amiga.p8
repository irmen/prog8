%import textio
%import palette
%import string
%import gfx2

; Mockup of a classic Amiga Workbench screen.

main {

    sub start() {
        gfx2.screen_mode(6)             ; select 640*480 mode, 4 colors
        uword[4] amigacolors = [$aaa, $000, $fff, $68c]     ; gray, black, white, lightblue
        palette.set_rgb(amigacolors, len(amigacolors))

        cx16.VERA_DC_VSCALE = 64        ; have the vertical resolution so it is 640*240 - more or less Amiga's default non interlaced mode
        cx16.mouse_config(1, 1)         ; enable mouse   TODO make it an Amiga mouse pointer if possible
        gfx2.text_charset(3)
        if gfx2.active_mode==5 {
            gfx2.monochrome_stipple(true)
            gfx2.fillrect(0,11,gfx2.width,gfx2.height/2-11,1)
            gfx2.monochrome_stipple(false)
        }

        screen_titlebar()
        window_workbench()
        window_system()
        window_shell()
        gfx2.text(280, 220, 1, @"Mockup drawn using Prog8 gfx2 library")

        repeat {
        }
    }

    sub screen_titlebar() {
        gfx2.fillrect(0, 0, gfx2.width, 10, 2)
        gfx2.text(8,1, 1, @"AmigaOS 3.1    2,002,448 graphics mem  16,504,384 other mem")
        gfx2.horizontal_line(0, 10, gfx2.width, 1)
        widget.window_order_icon(gfx2.width-widget.window_order_icon.width, 0, false)
    }


    sub window_workbench() {
        const uword win_x = 10
        const uword win_y = 16
        const uword width = 600
        const uword height = 220

        widget.window_titlebar(win_x, win_y, width, @"Workbench", false)
        ; gfx2.fillrect(win_x+3, win_y+11, width-4, height-11-2,0)    ; clear window pane
        widget.window_leftborder(win_x, win_y, height, false)
        widget.window_bottomborder(win_x, win_y, width, height)
        widget.window_rightborder(win_x, win_y, width, height, false)

        vector_v(win_x+width - 380, win_y+height-20)
        vector_v(win_x+width - 380 -14, win_y+height-20)

        widget.icon(45,40, @"Ram Disk")
        widget.icon(45,90, @"Workbench3.1")
    }

    sub vector_v(uword x, uword y) {
        gfx2.horizontal_line(x, y, 12, 1)
        gfx2.horizontal_line(x+16, y+16, 12,1)
        gfx2.line(x,y,x+16,y+16,1)
        gfx2.line(x+11,y,x+16+6,y+10,1)
        gfx2.line(x+16+6,y+10,x+48,y-16,1)
        gfx2.line(x+16+11,y+16,x+48+11,y-16,1)
    }

    sub window_system() {
        const uword width = 300
        const uword height = 120
        const uword win_x = 320
        const uword win_y = 40

        widget.window_titlebar(win_x, win_y, width, @"System", false)
        gfx2.fillrect(win_x+3, win_y+11, width-4, height-11-2, 0)    ; clear window pane
        widget.window_leftborder(win_x, win_y, height, false)
        widget.window_bottomborder(win_x, win_y, width, height)
        widget.window_rightborder(win_x, win_y, width, height, false)

        widget.icon(win_x+16, win_y+14, @"FixFonts")
        widget.icon(win_x+16+80, win_y+14, @"NoFastMem")
        widget.icon(win_x+16, win_y+56, @"Format")
        widget.icon(win_x+16+80, win_y+56, @"RexxMast")
        widget.icon(win_x+16+160, win_y+56, @"Shell")
    }

    sub window_shell() {
        const uword win_x = 64-4
        const uword win_y = 140
        const uword width = 500
        const uword height = 65

        widget.window_titlebar(win_x, win_y, width, @"AmigaShell", true)
        gfx2.fillrect(win_x+3, win_y+11, width-4, height-11-2,0)    ; clear window pane
        widget.window_leftborder(win_x, win_y, height, true)
        widget.window_bottomborder(win_x, win_y, width, height)
        widget.window_rightborder(win_x, win_y, width, height, true)

        gfx2.text(win_x+5, win_y+12, 1, @"New Shell process 3")
        gfx2.text(win_x+5, win_y+12+8, 1, @"3.Workbench3.1:>")
        gfx2.fillrect(win_x+5+17*8, win_y+12+8, 8, 8, 1)        ; cursor
    }
}



widget {

    sub highlightedrect(uword x, uword y, uword width, uword height, ubyte fill, ubyte active) {
        gfx2.horizontal_line(x, y, width, 2)
        gfx2.vertical_line(x, y+1, height-1, 2)
        gfx2.vertical_line(x+width-1, y+1, height-1, 1)
        gfx2.horizontal_line(x+1, y+height-1, width-2, 1)
        if fill {
            if active
                gfx2.fillrect(x+1,y+1,width-2,height-2, 3)
            else
                gfx2.fillrect(x+1,y+1,width-2,height-2, 0)
        }
    }

    sub icon(uword x, uword y, uword caption) {
        const ubyte width = 56
        const ubyte height = 28
        highlightedrect(x, y, width, height, false, false)
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
        widget.highlightedrect(x+widget.window_close_icon.width, y, width-64, height, true, active)
        gfx2.plot(x+widget.window_close_icon.width, y+height-1, 1) ; correct bottom left corner
        gfx2.text(x+32, y+1, 1, titlestr)
        widget.window_close_icon(x, y, active)
        widget.window_order_icon(x+width-22, y, active)
        widget.window_flipsize_icon(x+width-44, y, active)
    }

    sub window_flipsize_icon(uword x, uword y, ubyte active) {
        const uword width = 22
        const uword height = 11
        highlightedrect(x, y, width, height, true, active)
        gfx2.plot(x, y+height-1, 1) ; correct bottom left corner
        gfx2.rect(x+5, y+2, width-9, height-4, 1)
        gfx2.rect(x+5, y+2, 7, 4, 1)
        gfx2.fillrect(x+6, y+3, 5, 2, 2)
    }

    sub window_order_icon(uword x, uword y, ubyte active) {
        const uword width = 22
        const uword height = 11
        highlightedrect(x, y, width, height, true, active)
        gfx2.plot(x, y+height-1, 1) ; correct bottom left corner
        gfx2.rect(x+4, y+2, 10, 5, 1)       ; back
        gfx2.fillrect(x+9, y+5, 8, 3, 2)       ; white front
        gfx2.rect(x+8, y+4, 10, 5, 1)       ; front
    }

    sub window_close_icon(uword x, uword y, ubyte active) {
        const uword width = 20
        const uword height = 11
        highlightedrect(x, y, width, height, true, active)
        gfx2.plot(x, y+height-1, 1) ; correct bottom left corner
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

        highlightedrect(x+width-13, y+12, 10, height-23, false, false)
        ; TODO scroll icons
    }
}
