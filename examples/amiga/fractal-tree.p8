; Amiga windowed fractal tree, drawn with the OS graphics library.
; Uses fixed-point integer math so it also runs on a stock 68000 (no FPU required).
; Uses native recursion, which works because of the m68k target's stack call convention

%import intuition
%import graphics
%import exec
%import math

main {

    ; Branch angle, in a 256-degree circle. 256/12 ~= 21, which is pi/6.
    const ubyte delta_theta = 21

    ; Length shrink factor: 2/3, applied as (len * 2) / 3 in the recursion.
    const word minimum_branch = 256     ; 1 pixel in 8.8 fixed point

    ^^graphics.RastPort rp

    ; The window's client area. Width and Height of a window include its borders and title bar,
    ; so the usable drawing area is only known after the window is open.
    word border_left
    word border_top
    word xmax
    word ymax
    word client_w
    word client_h
    word xscale

    sub update_geometry(^^intuition.Window win) {
        border_left = win.BorderLeft as word
        border_top = win.BorderTop as word
        client_w = win.Width - border_left - win.BorderRight as word
        client_h = win.Height - border_top - win.BorderBottom as word
        xmax = win.Width - win.BorderRight as word - 1
        ymax = win.Height - win.BorderBottom as word - 1
        if client_h > 256
            client_h = 256      ; draw_branch carries y as a ubyte

        ; Compensate for non-square pixels (e.g. the default 2:1 aspect on many Amiga screens).
        ^^intuition.Screen scr = win.WScreen
        xscale = 128
        if scr != 0 and scr.Height != 0 {
            long num = scr.Width as long * 384   ; 3*128
            long den = scr.Height as long * 4
            xscale = (num / den) as word
            if xscale == 0
                xscale = 128
        }
    }


    sub draw_tree() {
        graphics.SetAPen(rp, 0)
        graphics.RectFill(rp, border_left, border_top, xmax, ymax)
        graphics.SetAPen(rp, 1)
        ; Initial branch: upright (64 = pi/2 in a 256-degree circle), length = client_h / 3 pixels.
        word start_len = ((client_h as long) * 256 / 3) as word
        draw_branch(client_w / 2 as word, (client_h - 1) as word, 64, start_len)
    }

    sub draw_segment(word x1, word y1, word x2, word y2) {
        word dx1 = clamp(x1, border_left, xmax)
        word dy1 = clamp(y1, border_top, ymax)
        word dx2 = clamp(x2, border_left, xmax)
        word dy2 = clamp(y2, border_top, ymax)
        graphics.Move(rp, dx1, dy1)
        graphics.Draw(rp, dx2, dy2)
    }

    ; draw a branch starting at (x1, y1) at angle 'angle' with length 'len'
    ; recurses if length is large enough
    ; draw_branch is recursive, so its parameters and locals live in its own stack frame and
    ; x1, y1, angle and len keep their values across the recursive calls without saving anything
    sub draw_branch(word x1, word y1, ubyte angle, word branch_len) {

        ; math.cos8 / math.sin8 return signed values approximating 127*cos/sin.
        ; branch_len is in 8.8 fixed point, so shifting the 32-bit product by 15 gives integer pixels.
        ; X is then scaled by xscale/128 to correct for non-square screen pixels.
        word dx = (((((branch_len as long) * (math.cos8(angle) as long)) >> 15) as word * xscale) >> 7) as word
        word dy = (((branch_len as long) * (math.sin8(angle) as long)) >> 15) as word
        word x2 = x1 + dx
        word y2 = y1 - dy
        draw_segment(x1, y1, x2, y2)

        if branch_len >= minimum_branch {
            word new_len = ((branch_len as long) * 2 / 3) as word
            draw_branch(x2, y2, angle - delta_theta, new_len)

            ; x1, y1, angle and branch_len are untouched by the call above, so the same expressions
            ; give the same x2 and y2 again
            x2 = x1 + dx
            y2 = y1 - dy
            draw_segment(x1, y1, x2, y2)

            draw_branch(x2, y2, angle + delta_theta, new_len)
        }
    }

    sub start() {
        ; Same size window3d.p8 uses, so it fits whatever screen the Workbench is on.
        ^^intuition.NewWindow nw = [
            10, 20, 512, 220, -1 as ubyte, -1 as ubyte,
            intuition.IDCMP_CLOSEWINDOW | intuition.IDCMP_REFRESHWINDOW | intuition.IDCMP_NEWSIZE | intuition.IDCMP_VANILLAKEY,
            intuition.WFLG_CLOSEGADGET | intuition.WFLG_DRAGBAR | intuition.WFLG_DEPTHGADGET | intuition.WFLG_ACTIVATE | intuition.WFLG_SIZEGADGET,
            0, 0,
            "Fractal Tree",
            0, 0, 100, 50, 800, 600,
            intuition.WBENCHSCREEN
        ]

        ^^intuition.Window win = intuition.OpenWindow(nw)
        if win == 0
            return
        defer intuition.CloseWindow(win)

        rp = win.RPort
        update_geometry(win)
        graphics.SetDrMd(rp, graphics.RP_JAM1)
        draw_tree()

        ; Wait for the user to close the window or press a key.
        bool running = true
        while running {
            ^^intuition.IntuiMessage msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> running = false
                    intuition.IDCMP_REFRESHWINDOW -> {
                        intuition.BeginRefresh(win)
                        draw_tree()
                        intuition.EndRefresh(win, 1)
                    }
                    intuition.IDCMP_NEWSIZE -> {
                        update_geometry(win)
                        draw_tree()
                    }
                    intuition.IDCMP_VANILLAKEY -> running = false
                }
                exec.ReplyMsg(msg as ^^exec.Message)
                msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            }
        }
    }
}
