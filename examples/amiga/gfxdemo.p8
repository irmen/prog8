; amiga_gfx interactive demo: fullscreen drawing with the mouse.
; Left click draws a small circle, right click a small rectangle, ESC quits.
; Runs on Kickstart 1.3 and up, on amiga500 and amiga1200.

%import amiga_gfx
%import graphics
%import intuition
%import exec

main {
    uword[8] palette = [$0112, $0f00, $00f0, $000f, $0ff0, $0f0f, $00ff, $0fff]

    sub start() {
        if amiga_gfx.fullscreen(320, 256, 3, 0, "amiga_gfx Demo") {
            defer amiga_gfx.close()
            draw_all()
            interact()
        }
    }

    sub draw_all() {
        amiga_gfx.palette(palette, len(palette))
        amiga_gfx.clear(0)
        amiga_gfx.rect(5, 5, 310, 246, 1)
        amiga_gfx.fillrect(10, 10, 60, 40, 2)
        amiga_gfx.line(0, 0, 319, 255, 3)
        amiga_gfx.circle(160, 128, 60, 4)
        amiga_gfx.line(160, 68, 160, 188, 4)
        amiga_gfx.fill(190, 128, 5)
        amiga_gfx.disc(80, 200, 30, 5)
        amiga_gfx.oval(240, 200, 50, 20, 6)
        amiga_gfx.filled_oval(40, 60, 30, 12, 5)
        amiga_gfx.plot(160, 128, 7)
        amiga_gfx.text(160, 30, "amiga_gfx demo", 7)
        word tw = amiga_gfx.textwidth("click to draw stuff, ESC=quit")
        amiga_gfx.text(160 - tw/2, 60, "click to draw stuff, ESC=quit", 7)
        amiga_gfx.setcolor(7, $f, $f, $f)
        ; direct library escape hatch via the exposed RastPort:
        ^^graphics.RastPort rp = amiga_gfx.rastport
        graphics.SetAPen(rp, 3)
        graphics.Move(rp, 10, 240)
        graphics.Draw(rp, 310, 240)

        ; TODO make amiga_gfx.scroll() work
    }

    sub interact() {
        bool done = false
        while not done {
            void exec.WaitPort(amiga_gfx.window.UserPort)
            ^^intuition.IntuiMessage msg = exec.GetMsg(amiga_gfx.window.UserPort) as ^^intuition.IntuiMessage
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> done = true
                    intuition.IDCMP_MOUSEBUTTONS -> {
                        word mx, my = amiga_gfx.mouse_xy(msg)
                        if msg.Code == intuition.SELECTDOWN
                            amiga_gfx.circle(mx, my, 8, 6)
                        if msg.Code == intuition.MENUDOWN
                            amiga_gfx.fillrect(mx-10, my-6, 20, 12, 2)
                    }
                    intuition.IDCMP_VANILLAKEY -> if msg.Code == 27 { done = true }   ;  ESC pressed?
                }
                exec.ReplyMsg(msg as ^^exec.Message)
                msg = exec.GetMsg(amiga_gfx.window.UserPort) as ^^intuition.IntuiMessage
            }
        }
    }
}
