; Amiga windowed spinning dodecahedron, drawn with the OS graphics library.
; Works on any Amiga with intuition/graphics.

%import intuition
%import graphics
%import exec
%import math

; 3d spinning Dodecahedron skeleton rendered into a window's RastPort.
; Geometry and fixed-point rotation taken from the 3d.p8 hardware example,
; but here we use the graphics.library line routines instead of the blitter.

main {

    sub start() {
        ^^intuition.NewWindow nw = [
            10, 20, 512, 220, -1 as ubyte, -1 as ubyte,
            intuition.IDCMP_CLOSEWINDOW | intuition.IDCMP_REFRESHWINDOW | intuition.IDCMP_NEWSIZE | intuition.IDCMP_VANILLAKEY | intuition.IDCMP_MOUSEBUTTONS,
            intuition.WFLG_CLOSEGADGET | intuition.WFLG_DRAGBAR | intuition.WFLG_DEPTHGADGET | intuition.WFLG_ACTIVATE | intuition.WFLG_SIZEGADGET,
            0, 0,
            "Spinning Dodecahedron",
            0, 0, 100, 50, 800, 600,
            intuition.WBENCHSCREEN
        ]

        ^^intuition.Window win = intuition.OpenWindow(nw)
        if win == 0
            return
        defer intuition.CloseWindow(win)

        ^^graphics.RastPort rp = win.RPort

        ; Client area geometry (word for cast-free arithmetic).
        word border_left
        word border_top
        word border_right
        word border_bottom
        word client_w
        word client_h
        word xmax
        word ymax
        word cx
        word cy

        sub update_geometry() {
            border_left = win.BorderLeft
            border_top = win.BorderTop
            border_right = win.BorderRight
            border_bottom = win.BorderBottom
            client_w = win.Width - border_left - border_right
            client_h = win.Height - border_top - border_bottom
            xmax = win.Width - border_right - 1
            ymax = win.Height - border_bottom - 1
            cx = client_w / 2
            cy = client_h / 2
        }
        update_geometry()

        ; Pixel aspect correction for line drawing (so the shape isn't stretched on hires/lores screens).
        ^^intuition.Screen scr = win.WScreen
        word xscale = 128
        if scr !=0 and scr.Height !=0 {
            long num = scr.Width as long * 384   ; 3*128
            long den = scr.Height as long * 4
            xscale = (num / den) as word
            if xscale==0
                xscale = 128
        }

        graphics.SetDrMd(rp, graphics.RP_JAM1)

        const ubyte VERTEX_COUNT = 20
        const ubyte EDGE_COUNT = 30
        ; Integer-scaled canonical coordinates from Wikipedia's regular dodecahedron model.
        word[VERTEX_COUNT] vx = [-75, -75, -75, -75, 75, 75, 75, 75, 0, 0, 0, 0, -46, -46, 46, 46, -121, -121, 121, 121]
        word[VERTEX_COUNT] vy = [-75, -75, 75, 75, -75, -75, 75, 75, -121, -121, 121, 121, 0, 0, 0, 0, -46, 46, -46, 46]
        word[VERTEX_COUNT] vz = [-75, 75, -75, 75, -75, 75, -75, 75, -46, 46, -46, 46, -121, 121, -121, 121, 0, 0, 0, 0]
        ubyte[EDGE_COUNT] edgesFrom = [
            0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3,
            4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7,
            8, 10, 12, 13, 16, 18
        ]
        ubyte[EDGE_COUNT] edgesTo = [
            8, 12, 16, 9, 13, 16, 10, 12, 17, 11, 13, 17,
            8, 14, 18, 9, 15, 18, 10, 14, 19, 11, 15, 19,
            9, 11, 14, 15, 17, 19
        ]
        const word PERSPECTIVE_DISTANCE = 192
        const uword ROT_X_SPEED = 500
        const uword ROT_Y_SPEED = 316
        const uword ROT_Z_SPEED = 216

        uword angle_x = 0
        uword angle_y = 0
        uword angle_z = 0
        word[VERTEX_COUNT] px
        word[VERTEX_COUNT] py

        sub draw_scene() {
            ; Clear the client area to gray as usual (pen 0).
            graphics.SetAPen(rp, 0)
            graphics.RectFill(rp, border_left, border_top, xmax, ymax)

            ; Build the fixed-point rotation matrix and rotate all vertices.
            word wcosa = cos8_fixed(angle_x)
            word wsina = sin8_fixed(angle_x)
            word wcosb = cos8_fixed(angle_y)
            word wsinb = sin8_fixed(angle_y)
            word wcosc = cos8_fixed(angle_z)
            word wsinc = sin8_fixed(angle_z)

            word wcosa_sinb = wcosa * wsinb >> 7
            word wsina_sinb = wsina * wsinb >> 7
            word Axx = wcosa * wcosb >> 7
            word Axy = (wcosa_sinb * wsinc - wsina * wcosc) >> 7
            word Axz = (wcosa_sinb * wcosc + wsina * wsinc) >> 7
            word Ayx = wsina * wcosb >> 7
            word Ayy = (wsina_sinb * wsinc + wcosa * wcosc) >> 7
            word Ayz = (wsina_sinb * wcosc - wcosa * wsinc) >> 7
            word Azx = -wsinb
            word Azy = wcosb * wsinc >> 7
            word Azz = wcosb * wcosc >> 7

            ubyte i
            for i in 0 to VERTEX_COUNT - 1 {
                ; Rotate the vertex.
                word rx = Axx * vx[i] + Axy * vy[i] + Axz * vz[i]
                word ry = Ayx * vx[i] + Ayy * vy[i] + Ayz * vz[i]
                word rz = Azx * vx[i] + Azy * vy[i] + Azz * vz[i]

                ; Perspective projection: positive Z is nearer to the camera.
                word persp = PERSPECTIVE_DISTANCE - (rz >> 8)
                if persp < 32
                    persp = 32

                ; Compute final screen coordinates (aspect-corrected X).
                px[i] = border_left + cx + (((rx / persp) as long * xscale >> 7) as word)
                py[i] = border_top + cy - (ry / persp)
                if px[i] < border_left
                    px[i] = border_left
                if px[i] > xmax
                    px[i] = xmax
                if py[i] < border_top
                    py[i] = border_top
                if py[i] > ymax
                    py[i] = ymax
            }

            ; Draw the dodecahedron skeleton with graphics.library line routines.
            graphics.SetAPen(rp, 2)   ; white edges
            ubyte e
            for e in 0 to EDGE_COUNT - 1 {
                ubyte ev1 = edgesFrom[e]
                ubyte ev2 = edgesTo[e]
                graphics.Move(rp, px[ev1], py[ev1])
                graphics.Draw(rp, px[ev2], py[ev2])
            }
        }

        sub sin8_fixed(uword angle) -> word {
            ; Blend adjacent 8-bit lookup samples using the phase fraction for smoother motion.
            ubyte index = msb(angle)
            word sample1 = math.sin8(index)
            word sample2 = math.sin8((index + 1) as ubyte)
            long fraction = angle & $ff
            long interpolated = (sample2 - sample1) as long * fraction / 256
            return (sample1 as long + interpolated) as word
        }

        sub cos8_fixed(uword angle) -> word {
            return sin8_fixed(angle + $4000)
        }

        ; Message pump / animation loop
        bool running = true
        while running {
            ; Process pending messages (non-blocking)
            ^^intuition.IntuiMessage msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> running = false
                    intuition.IDCMP_REFRESHWINDOW -> {
                        intuition.BeginRefresh(win)
                        draw_scene()
                        intuition.EndRefresh(win, 1)
                    }
                    intuition.IDCMP_NEWSIZE -> {
                        update_geometry()
                        draw_scene()
                    }
                    intuition.IDCMP_VANILLAKEY -> running = false
                    intuition.IDCMP_MOUSEBUTTONS -> draw_scene()
                }
                exec.ReplyMsg(msg as ^^exec.Message)
                msg = exec.GetMsg(win.UserPort) as ^^intuition.IntuiMessage
            }

            if not running
                break

            graphics.WaitTOF()
            draw_scene()
            angle_x += ROT_X_SPEED
            angle_y += ROT_Y_SPEED
            angle_z += ROT_Z_SPEED
            graphics.WaitTOF()
        }
    }
}
