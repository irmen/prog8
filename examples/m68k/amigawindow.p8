%import intuition
%import graphics
%import exec
%import dos

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

        ^^intuition.Window win = intuition.OpenWindow(nw)
        if win == 0
            return

        intuition.ActivateWindow(win)

        ^^graphics.RastPort rp = win.RPort
        word font_height = rp.TxHeight
        word font_baseline = rp.TxBaseline
        word font_descent = font_height - font_baseline
        str message = "Woah it works!"
        word text_width = graphics.TextLength(rp, message, len(message))
        word x_pos = 100
        word y_pos = 40
        word dx = 2
        word dy = 1
        ubyte color_idx = 1
        ; Client area offset (word for cast-free arithmetic)
        word border_left = win.BorderLeft
        word border_top = win.BorderTop
        word border_right = win.BorderRight
        word border_bottom = win.BorderBottom

        sub drawText() {
            graphics.SetDrMd(rp, 1)       ; JAM2 - solid character cells, readable
            graphics.SetAPen(rp, color_idx)
            graphics.Move(rp, x_pos + border_left, y_pos + border_top)
            void graphics.Text(rp, message, len(message))
        }

        drawText()

        ; Message pump / animation loop
        bool running = true
        while running {
            ; Process pending messages (non-blocking)
            ^^intuition.IntuiMessage msg = exec.GetMsg(win.UserPort)
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> running = false
                    intuition.IDCMP_REFRESHWINDOW -> {
                        intuition.BeginRefresh(win)
                        drawText()
                        intuition.EndRefresh(win, 1)
                    }
                }
                exec.ReplyMsg(msg)
                msg = exec.GetMsg(win.UserPort)
            }

            ; Bounce the text within client area
            x_pos += dx
            y_pos += dy
            word client_w = win.Width - border_left - border_right
            word client_h = win.Height - border_top - border_bottom
            if x_pos < 0 {
                x_pos = 0
                dx = -dx
            }
            if x_pos + text_width > client_w {
                x_pos = client_w - text_width
                dx = -dx
            }
            if y_pos < font_baseline {
                y_pos = font_baseline
                dy = -dy
            }
            if y_pos > client_h - 1 - font_descent {
                y_pos = client_h - 1 - font_descent
                dy = -dy
            }

            ; Cycle color: 1 -> 2 -> 3 -> 1 -> ...
            color_idx = (color_idx % 3) + 1

            drawText()
            dos.Delay(1)
        }

        intuition.CloseWindow(win)
    }
}
