%import easygfx
%import graphics
%import intuition
%import dos
%import lineclip
%import math

main {
    const word CLIP_LEFT = 100
    ; The Workbench's 256-line default screen uses half the CX16 test's Y coordinates.
    const word CLIP_TOP = 35
    const word CLIP_RIGHT = 570
    const word CLIP_BOTTOM = 205

    sub start() {
        if easygfx.windowed(10, 10, 620, 240, "Line clipping test") {
            defer easygfx.close()

            ; Window mode shares the Workbench palette, so preserve the affected pens.
            ^^graphics.ViewPort vp = intuition.ViewPortAddress(easygfx.window)
            long old_black = graphics.GetRGB4(vp.ColorMap, 0)
            long old_white = graphics.GetRGB4(vp.ColorMap, 1)
            long old_red = graphics.GetRGB4(vp.ColorMap, 2)

            easygfx.setcolor(0, 0, 0, 0)
            easygfx.setcolor(1, 15, 15, 15)
            easygfx.setcolor(2, 15, 0, 0)
            lineclip.set_cliprect(CLIP_LEFT, CLIP_TOP, CLIP_RIGHT, CLIP_BOTTOM)
            draw_frame()

            bool running = true
            while running {
                word x1 = (math.randrangew(2001) as word) - 1000
                word y1 = (math.randrangew(2001) as word) - 1000
                word x2 = (math.randrangew(2001) as word) - 1000
                word y2 = (math.randrangew(2001) as word) - 1000
                bool visible
                word clipped_x1, clipped_y1, clipped_x2, clipped_y2

                visible, clipped_x1, clipped_y1, clipped_x2, clipped_y2 = lineclip.clip(x1, y1, x2, y2)
                if visible
                    easygfx.line(clipped_x1, clipped_y1, clipped_x2, clipped_y2, 2)

                ^^intuition.IntuiMessage msg = easygfx.imsg_poll()
                while msg != 0 {
                    when msg.Class {
                        intuition.IDCMP_CLOSEWINDOW -> running = false
                        intuition.IDCMP_REFRESHWINDOW -> {
                            intuition.BeginRefresh(easygfx.window)
                            easygfx.clear(0)
                            draw_frame()
                            intuition.EndRefresh(easygfx.window, 1)
                        }
                    }
                    msg = easygfx.imsg_reply(msg)
                }
            }

            easygfx.setcolor(0, ((old_black >> 8) & $f) as ubyte, ((old_black >> 4) & $f) as ubyte, (old_black & $f) as ubyte)
            easygfx.setcolor(1, ((old_white >> 8) & $f) as ubyte, ((old_white >> 4) & $f) as ubyte, (old_white & $f) as ubyte)
            easygfx.setcolor(2, ((old_red >> 8) & $f) as ubyte, ((old_red >> 4) & $f) as ubyte, (old_red & $f) as ubyte)
        }
    }

    sub draw_frame() {
        easygfx.clear(0)
        ; The one-pixel border surrounds the half-height clipping rectangle.
        easygfx.rect(99, 34, 473, 173, 1)
    }
}
