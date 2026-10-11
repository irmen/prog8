; easygfx - friendly OS graphics wrapper, shared by the amiga500 and amiga1200 targets.
;
; Hides NewScreen/NewWindow boilerplate behind a gfx_lores-style API:
; per-call pen, client-relative word coordinates, explicit open/close.
; Kickstart 1.3 compatible: uses only OpenScreen/OpenWindow (no taglists,
; no AllocBitMap, no DisplayID), except fillto() which needs SetOutlinePen (3.0+).
; All rasterization is done by
; graphics.library; filled ellipses and flood fills use AreaEllipse/Flood
; with a chip-ram TmpRas scratch buffer allocated when the window opens.
; No double buffering (use two screens/windows or raw calls for that).
; Use rastport for direct graphics.library calls, window to pump IDCMP.

%import intuition
%import graphics
%import strings
%import exec

easygfx {
    %option merge, ignore_unused

    uword width                 ; drawable client size, set by fullscreen()/windowed()
    uword height
    ubyte depth                 ; screen depth (0 = workbench default, unknown)
    ^^intuition.Window window
    ^^graphics.RastPort rastport
    private ^^intuition.Screen screen

    private word origin_x       ; window border offset added to all coordinates
    private word origin_y
    private bool is_custom      ; true when we opened our own screen
    private ubyte aspect  ; x-radius scale for non-square pixels: 0=halve, 1=as-is, 2=double, 4=quadruple

    ; chip-ram scratch for Flood()/AreaEnd(); the control structs may live anywhere
    private graphics.TmpRas[1] tmpras_store
    private graphics.AreaInfo[1] area_store
    private graphics.TextAttr[1] font_store
    private ubyte[20] area_vectors    ; 5 bytes per vector, room for 4
    private pointer tmpras_buf
    private long tmpras_size

    sub fullscreen(uword w, uword h, ubyte ndepth, uword viewmodes, str title) -> bool {
        ; fullscreen custom screen + backdrop borderless window.
        ; viewmodes: 0 = lores, graphics.HIRES for 640px, + graphics.INTERLACE for interlaced (doubled height).
        if w == 0 or h == 0
            return false
        if rastport != 0
            close()
        bool untitled = title == 0
        if not untitled
            untitled = title[0] == 0
        ; NOTE: struct literals must be constant, so allocate zeroed and patch fields.
        ^^intuition.NewScreen ns = [0, 0, 0, 0, 0,                     ; edges, size, depth
                                    0, 1, 0, intuition.CUSTOMSCREEN,   ; pens, modes, type
                                    0, 0, 0, 0]                        ; font, title, gadgets, bitmap
        ns.Width = w as word
        ns.Height = h as word
        ns.Depth = ndepth
        ns.ViewModes = viewmodes
        if not untitled
            ns.DefaultTitle = title
        ; explicit Topaz 8 screen font (ROM-resident, no diskfont needed)
        ^^graphics.TextAttr fa = &font_store[0]
        fa.Name = "topaz.font"
        fa.YSize = 8
        fa.Style = 0
        fa.Flags = 0
        ns.Font = fa
        screen = intuition.OpenScreen(ns)
        if screen == 0
            return false
        ; bar policy is applied below once the window exists: empty title hides
        ; the bar for true fullscreen, otherwise the window opens below it.
        ; null and "" both count as titleless.
        word bar = 0
        if not untitled {
            bar = screen.WBorTop
            if h - bar <= 0 {
                void intuition.CloseScreen(screen)
                screen = 0
                return false
            }
        }
        ^^intuition.NewWindow nw = [0, 0, 0, 0,             ; edges, size
                                    0, 1,                   ; pens
                                    ; RAWKEY only, never add VANILLAKEY here: with both flags set Intuition
                                    ; diverts mappable keys to the vanilla channel and starves the raw one,
                                    ; so cursor keys (and on Kickstart 1.3, all keys) stop arriving as rawkey.
                                    ; Map raw codes yourself instead; ESC is $45, cursor keys are $4c-$4f.
                                    intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
                                    intuition.WFLG_BACKDROP | intuition.WFLG_BORDERLESS | intuition.WFLG_ACTIVATE | intuition.WFLG_RMBTRAP | intuition.WFLG_NOCAREREFRESH,
                                    0, 0, 0, 0,             ; no gadgets, default checkmark, title, screen
                                    0, 0, 0, 0, 0, intuition.CUSTOMSCREEN]   ; bitmap, limits, type
        nw.TopEdge = bar
        nw.Width = w as word
        nw.Height = h - bar
        nw.Screen = screen
        window = intuition.OpenWindow(nw)
        if window == 0 {
            void intuition.CloseScreen(screen)
            screen = 0
            return false
        }
        depth = ndepth
        intuition.ActivateWindow(window)
        finish_open(true, bar)
        width = w
        height = (h - bar) as uword
        graphics.SetDrMd(rastport, graphics.RP_JAM1)
        if untitled {
            ; ShowTitle(FALSE) renders the bar behind all windows; do it with
            ; the window present so the redisplay pass sees the real layers.
            intuition.ShowTitle(screen, false)
            clear(0)      ; wipe the hidden bar's leftover pixels
        }
        return true
    }

    sub windowed(uword x, uword y, uword w, uword h, str title) -> bool {
        ; fixed-size window on the Workbench screen at position (x,y). Works everywhere,
        ; but depth/palette are the Workbench's: use palette() sparingly.
        if w == 0 or h == 0
            return false
        if rastport != 0
            close()
        screen = 0
        ^^intuition.NewWindow nw = [0, 0, 0, 0,               ; edges, size
                                    255, 255,                 ; pens
                                    ; RAWKEY only, see fullscreen() for why.
                                    intuition.IDCMP_CLOSEWINDOW | intuition.IDCMP_REFRESHWINDOW | intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
                                    intuition.WFLG_CLOSEGADGET | intuition.WFLG_DRAGBAR | intuition.WFLG_DEPTHGADGET | intuition.WFLG_ACTIVATE,
                                    0, 0, 0, 0,               ; no gadgets, default checkmark, title, screen
                                    0, 0, 0, 0, 0, intuition.WBENCHSCREEN]   ; bitmap, limits, type
        nw.LeftEdge = x as word
        nw.TopEdge = y as word
        nw.Width = w as word
        nw.Height = h as word
        nw.Title = title
        window = intuition.OpenWindow(nw)
        if window == 0
            return false
        depth = 0
        intuition.ActivateWindow(window)
        finish_open(false, 0)
        graphics.SetDrMd(rastport, graphics.RP_JAM1)
        return true
    }

    sub close() {
        ; close window and screen (if ours), back to Workbench/textmode
        if window != 0 {
            intuition.CloseWindow(window)
            window = 0
            rastport = 0
        }
        if is_custom and screen != 0 {
            void intuition.CloseScreen(screen)
        }
        screen = 0
        is_custom = false
        width = 0
        height = 0
        aspect = 1
        if tmpras_buf != 0 {
            exec.FreeMem(tmpras_buf, tmpras_size)
            tmpras_buf = 0
            tmpras_size = 0
        }
    }

    sub clear(ubyte pen) {
        ; RectFill, not SetRast: SetRast blasts the whole bitmap, borders included
        if width == 0 or height == 0
            return
        graphics.SetAPen(rastport, pen)
        graphics.RectFill(rastport, origin_x, origin_y, (origin_x + width) - 1, (origin_y + height) - 1)
    }

    sub plot(word x, word y, ubyte pen) {
        graphics.SetAPen(rastport, pen)
        void graphics.WritePixel(rastport, origin_x+x, origin_y+y)
    }

    sub line(word x1, word y1, word x2, word y2, ubyte pen) {
        graphics.SetAPen(rastport, pen)
        graphics.Move(rastport, origin_x+x1, origin_y+y1)
        graphics.Draw(rastport, origin_x+x2, origin_y+y2)
    }

    sub rect(word x, word y, word w, word h, ubyte pen) {
        if w <= 0 or h <= 0
            return
        graphics.SetAPen(rastport, pen)
        word xa = origin_x+x
        word ya = origin_y+y
        word x2 = xa+w-1
        word y2 = ya+h-1
        graphics.Move(rastport, xa, ya)
        graphics.Draw(rastport, x2, ya)
        graphics.Draw(rastport, x2, y2)
        graphics.Draw(rastport, xa, y2)
        graphics.Draw(rastport, xa, ya)
    }

    sub fillrect(word x, word y, word w, word h, ubyte pen) {
        if w <= 0 or h <= 0
            return
        graphics.SetAPen(rastport, pen)
        graphics.RectFill(rastport, origin_x+x, origin_y+y, origin_x+x+w-1, origin_y+y+h-1)
    }

    sub circle(word cx, word cy, word radius, ubyte pen) {
        if radius == 0 {
            plot(cx, cy, pen)
            return
        }
        if radius < 0
            return
        graphics.SetAPen(rastport, pen)
        graphics.DrawEllipse(rastport, origin_x+cx, origin_y+cy, aspect_rx(radius), radius)
    }

    sub oval(word cx, word cy, word rx, word ry, ubyte pen) {
        if rx <= 0 or ry <= 0
            return
        graphics.SetAPen(rastport, pen)
        graphics.DrawEllipse(rastport, origin_x+cx, origin_y+cy, aspect_rx(rx), ry)
    }

    sub disc(word cx, word cy, word radius, ubyte pen) {
        ; filled circle via the library area-fill
        if radius == 0 {
            plot(cx, cy, pen)
            return
        }
        if radius < 0
            return
        graphics.SetAPen(rastport, pen)
        do_area_ellipse(cx, cy, aspect_rx(radius), radius)
    }

    sub filled_oval(word cx, word cy, word rx, word ry, ubyte pen) {
        ; filled ellipse via the library area-fill
        if rx <= 0 or ry <= 0
            return
        graphics.SetAPen(rastport, pen)
        do_area_ellipse(cx, cy, aspect_rx(rx), ry)
    }

    sub text(word x, word y, str s, ubyte pen) {
        ; NOTE: on Kickstart 1.3 a single Text() call is limited to ~1008 pixels wide
        graphics.SetAPen(rastport, pen)
        graphics.Move(rastport, origin_x+x, origin_y+y)
        void graphics.Text(rastport, s, strings.length(s))
    }

    inline sub drawmode(ubyte mode) {
        ; graphics.RP_JAM1, RP_JAM2, RP_COMPLEMENT, RP_INVERSVID
        graphics.SetDrMd(easygfx.rastport, mode)
    }

    sub palette(pointer colors, uword count) {
        ; LoadRGB4 4-bit nibble table into the screen colormap
        pointer vp = viewport()
        if vp != 0
            graphics.LoadRGB4(vp, colors, count as word)
    }

    sub setcolor(ubyte index, ubyte red, ubyte green, ubyte blue) {
        ; tweak a single colormap pen (4-bit nibbles)
        pointer vp = viewport()
        if vp != 0
            graphics.SetRGB4(vp, index, red, green, blue)
    }

    sub scrollrect(word dx, word dy, word x, word y, word w, word h) {
        ; scroll a client-relative rectangle
        ; the rectangle should lie inside the client area.
        if w <= 0 or h <= 0
            return
        graphics.ScrollRaster(rastport, dx, dy, origin_x+x, origin_y+y, origin_x+x+w-1, origin_y+y+h-1)
    }

    inline sub readpixel(word x, word y) -> long {
        ; pen number at the pixel, or -1 when outside
        return graphics.ReadPixel(easygfx.rastport, easygfx.origin_x+x, easygfx.origin_y+y)
    }

    inline sub textwidth(str s) -> word {
        ; raster width of the string in the current font, e.g. to center text
        return graphics.TextLength(easygfx.rastport, s, strings.length(s))
    }

    sub mouse_xy(^^intuition.IntuiMessage msg) -> word, word {
        ; IDCMP cursor as client-relative coords (msg.MouseX/Y include the window borders)
        if msg == 0
            return -1, -1
        return msg.MouseX - origin_x, msg.MouseY - origin_y
    }

    sub imsg_wait() -> ^^intuition.IntuiMessage {
        ; block until the next IDCMP message arrives on our window
        void exec.WaitPort(window.UserPort)
        return exec.GetMsg(window.UserPort) as ^^intuition.IntuiMessage
    }

    sub imsg_reply(^^intuition.IntuiMessage msg) -> ^^intuition.IntuiMessage {
        ; reply the message and fetch the next one (0 when drained)
        exec.ReplyMsg(msg as ^^exec.Message)
        return exec.GetMsg(window.UserPort) as ^^intuition.IntuiMessage
    }


    sub fill(word x, word y, ubyte pen) {
        ; bucket fill: recolor the connected region holding (x,y)
        do_flood(x, y, pen, 1)
    }

    sub fillto(word x, word y, ubyte pen, ubyte outline) {
        ; boundary fill: fill until the outline-colored border
        void graphics.SetOutlinePen(rastport, outline)
        do_flood(x, y, pen, 0)
    }

    private sub aspect_rx(word rx) -> word {
        ; x-radius corrected for non-square pixels so circles stay round
        when aspect {
            2 -> return rx*2
            4 -> return rx*4
            0 -> return rx/2
            else -> return rx
        }
    }

    private sub viewport() -> pointer {
        ^^intuition.Screen s = screen
        if s == 0 and window != 0
            s = window.WScreen
        if s == 0
            return 0
        return intuition.GetScreenViewPort(s)
    }

    private sub do_flood(word x, word y, ubyte pen, long mode) {
        if rastport == 0 or tmpras_buf == 0
            return
        graphics.SetAPen(rastport, pen)
        ^^graphics.TmpRas saved = rastport.TmpRas
        ^^graphics.TmpRas tp = &tmpras_store[0]
        void graphics.InitTmpRas(tp, tmpras_buf, tmpras_size)
        rastport.TmpRas = tp
        void graphics.Flood(rastport, mode, origin_x+x, origin_y+y)
        rastport.TmpRas = saved
    }

    private sub finish_open(bool custom, word bar) {
        rastport = window.RPort
        origin_x = window.BorderLeft
        origin_y = window.BorderTop
        width = (window.Width - origin_x - window.BorderRight) as uword
        height = (window.Height - origin_y - window.BorderBottom) as uword
        ; pixel shape from the screen's ViewPort.Modes (big-endian word at offset 32)
        aspect = 1
        pointer vp = viewport()
        if vp != 0 {
            uword modes = (@(vp+32) as uword) << 8 | @(vp+33)
            if (modes & graphics.SUPERHIRES) != 0 {
                aspect = 4
                if (modes & graphics.INTERLACE) != 0
                    aspect = 2
            } else {
                if (modes & graphics.V_HIRES) != 0 and (modes & graphics.INTERLACE) == 0
                    aspect = 2
                if (modes & graphics.INTERLACE) != 0 and (modes & graphics.V_HIRES) == 0
                    aspect = 0
            }
        }
        is_custom = custom
        ; bar rows the window didn't move past (backdrop positioning
        ; isn't always honored) still count as client offset
        word extra = bar - window.TopEdge
        if extra > 0
            origin_y += extra
        ; one-plane Flood scratch sized to the rastport's bitmap
        ; (Flood requires the TmpRas to cover the whole bitmap, not just the client area)
        if rastport.BitMap != 0 {
            tmpras_size = rastport.BitMap.BytesPerRow * rastport.BitMap.Rows
            tmpras_buf = exec.AllocMem(tmpras_size, exec.MEMF_CHIP)
        }
    }

    private sub do_area_ellipse(word cx, word cy, word rx, word ry) {
        if rastport == 0 or tmpras_buf == 0
            return
        ^^graphics.TmpRas saved_tr = rastport.TmpRas
        ^^graphics.AreaInfo saved_ai = rastport.AreaInfo
        ^^graphics.TmpRas tp = &tmpras_store[0]
        ^^graphics.AreaInfo ai = &area_store[0]
        void graphics.InitTmpRas(tp, tmpras_buf, tmpras_size)
        rastport.TmpRas = tp
        graphics.InitArea(ai, area_vectors, 4)
        rastport.AreaInfo = ai
        if graphics.AreaEllipse(rastport, origin_x+cx, origin_y+cy, rx, ry) == 0
            void graphics.AreaEnd(rastport)
        rastport.TmpRas = saved_tr
        rastport.AreaInfo = saved_ai
    }
}
