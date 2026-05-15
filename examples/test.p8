; Note: this program can be compiled for multiple target systems.

%import graphics
%import lineclip
%import math

main {
    sub start() {
        graphics.enable_bitmap_mode()
        lineclip.set_cliprect(70, 20, 290, 180)
        graphics.line(69, 19, 291, 19)
        graphics.line(291, 19, 291, 181)
        graphics.line(291, 181, 69, 181)
        graphics.line(69, 181, 69, 19)

        repeat {
            word x = math.randrangew(graphics.WIDTH*3) as word - graphics.HEIGHT
            word y = math.randrangew(graphics.HEIGHT*3) as word - graphics.WIDTH
            if lineclip.inside(x,y)
                graphics.plot(x as uword, y as ubyte)
        }

        repeat {
            word x1 = math.randrangew(graphics.WIDTH*3) as word - graphics.HEIGHT
            word y1 = math.randrangew(graphics.HEIGHT*3) as word - graphics.WIDTH
            word x2 = math.randrangew(graphics.WIDTH*3) as word - graphics.HEIGHT
            word y2 = math.randrangew(graphics.HEIGHT*3) as word - graphics.WIDTH

            bool visible
            visible, x1, y1, x2, y2 = lineclip.clip(x1, y1, x2, y2)

            if visible {
                graphics.line(x1 as uword, y1 as ubyte, x2 as uword, y2 as ubyte)
                ;sys.waitvsync()
            }
        }
    }
}
