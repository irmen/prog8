%import intuition
%import graphics
%import exec
%import dos

intuition {
    %option merge

    struct NewWindow {
        word leftEdge, topEdge, width, height
        byte detailPen, blockPen
        long idcmpFlags
        long flags
        pointer firstGadget, checkmark
        str title
        pointer screen, bitmap
        word minWidth, minHeight
        uword maxWidth, maxHeight, type
    }

    ; IDCMP flags from NDK
    const long IDCMP_REFRESHWINDOW   = $00000004
    const long IDCMP_CLOSEWINDOW     = $00000200

    ; Window flags (from NDK)
    const long WFLG_SIZEGADGET       = 1
    const long WFLG_DRAGBAR          = 2
    const long WFLG_DEPTHGADGET      = 4
    const long WFLG_CLOSEGADGET      = 8
    const long WFLG_ACTIVATE         = $1000
    const uword WBENCHSCREEN         = 1

    ; IntuiMessage Class offset
    const ubyte IM_CLASS_OFFSET = $14

}

main {

    sub start() {
        ^^intuition.NewWindow nw = [
            20, 20, 300, 100, -1, -1,
            intuition.IDCMP_CLOSEWINDOW | intuition.IDCMP_REFRESHWINDOW,
            intuition.WFLG_CLOSEGADGET | intuition.WFLG_DRAGBAR | intuition.WFLG_DEPTHGADGET | intuition.WFLG_ACTIVATE | intuition.WFLG_SIZEGADGET,
            0, 0,
            "Hello from Prog8!",
            0, 0, 100, 50, 800, 600,
            intuition.WBENCHSCREEN
        ]

        pointer win = intuition.OpenWindow(nw)
        if win == 0
            return

        intuition.ActivateWindow(win)

        ; Get RastPort from Window struct (offset 0x32)
        pointer rp = peekp(win+$32)
        ; Get UserPort from Window struct (offset 0x56)
        pointer userport = peekp(win+$56)

        sub drawText() {
            graphics.SetDrMd(rp, 1)
            graphics.SetAPen(rp, 2)
            graphics.Move(rp, 15, 25)
            str message = "Woah it works!"
            void graphics.Text(rp, message, len(message))
        }

        drawText()

        ; Message pump / idle loop
        bool running = true
        while running {
            void exec.WaitPort(userport)
            pointer msg = exec.GetMsg(userport)
            while msg != 0 {
                ; Check msg.Class at offset 0x14
                long msgclass = peekl(msg+$14)
                if msgclass == intuition.IDCMP_CLOSEWINDOW {
                    running = false
                } else {
                    if msgclass == intuition.IDCMP_REFRESHWINDOW {
                        intuition.BeginRefresh(win)
                        drawText()
                        intuition.EndRefresh(win, 1)
                    }
                }
                exec.ReplyMsg(msg)
                msg = exec.GetMsg(userport)
            }
        }

        intuition.CloseWindow(win)
    }
}
