; gfx interactive demo: fullscreen drawing with the mouse.
; Left click draws a small circle, right click a small rectangle, ESC quits.
; Runs on Kickstart 1.3 and up, on amiga500 and amiga1200.

%import easygfx
%import graphics
%import intuition

main {
    uword[8] palette = [$0112, $0f00, $00f0, $000f, $0ff0, $0f0f, $00ff, $0fff]

    sub start() {
        if easygfx.fullscreen(320, 256, 3, graphics.LORES, "amiga_gfx Demo") {
            defer easygfx.close()
            draw_all()
            interact()
        }
    }

    sub draw_all() {
        easygfx.palette(palette, len(palette))
        easygfx.clear(0)
        easygfx.rect(5, 5, 310, 246, 1)
        easygfx.fillrect(10, 10, 60, 40, 2)
        easygfx.line(0, 0, 319, 255, 3)
        easygfx.circle(160, 128, 60, 4)
        easygfx.line(160, 68, 160, 188, 4)
        easygfx.fill(190, 128, 5)
        easygfx.disc(80, 200, 30, 5)
        easygfx.oval(240, 200, 50, 20, 6)
        easygfx.filled_oval(40, 60, 30, 12, 5)
        easygfx.plot(160, 128, 7)
        easygfx.text(160, 30, "amiga_gfx demo", 5)
        word tw = easygfx.textwidth("click to draw, arrows scroll")
        easygfx.text(160 - tw/2, 60, "click to draw, arrows scroll", 6)
        tw = easygfx.textwidth("ESC=quit")
        easygfx.text(160 - tw/2, 70, "ESC=quit", 7)
        easygfx.setcolor(7, $f, $f, $f)
        ; direct library escape hatch via the exposed RastPort:
        ^^graphics.RastPort rp = easygfx.rastport
        graphics.SetAPen(rp, 3)
        graphics.Move(rp, 10, 240)
        graphics.Draw(rp, 310, 240)
    }

    sub interact() {
        bool done = false
        while not done {
            ^^intuition.IntuiMessage msg = easygfx.imsg_wait()
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> done = true
                    intuition.IDCMP_MOUSEBUTTONS -> {
                        word mx, my = easygfx.mouse_xy(msg)
                        if msg.Code == intuition.SELECTDOWN
                            easygfx.circle(mx, my, 8, 6)
                        if msg.Code == intuition.MENUDOWN
                            easygfx.fillrect(mx-10, my-6, 20, 12, 2)
                    }
                    intuition.IDCMP_RAWKEY -> {
                        ; keyboard arrives as rawkey only: cursor keys scroll (key repeat
                        ; scrolls smoothly), ESC quits. Key-up events (bit 7) are ignored.
                        ; raw keycodes are positional (same on all national keymaps):
                        ; $00 `  $01-$0A 1..0  $0B -  $0C =  $0D \  $0F numpad-0
                        ; $10-$19 Q..P  $1A [  $1B ]  $1D-$1F numpad-1..3
                        ; $20-$28 A..L  $29 ;  $2A '  $2D-$2F numpad-4..6
                        ; $31-$37 Z..M  $38 ,  $39 .  $3A /  $3C numpad-.  $3D-$3F numpad-7..9
                        ; $40 space  $41 backspace  $42 tab  $43 numpad-enter  $44 return
                        ; $45 ESC  $46 del  $4A numpad--  $4C-$4F cursor up/down/right/left
                        ; $50-$59 F1-F10  $5A-$5E numpad ( ) / * +  $5F help
                        ; $60/$61 shift l/r  $62 caps  $63 ctrl  $64/$65 alt l/r  $66/$67 amiga l/r
                        ; source: https://wiki.amigaos.net/wiki/Keymap_Library
                        if (msg.Code & $80) == 0 {
                            when (msg.Code & $7f) {
                                ; note: ScrollRaster moves bits towards (0,0), so the signs are flipped
                                $45 -> done = true   ; ESC
                                $4c -> easygfx.scrollrect(0, 8, 80, 88, 160, 120)    ; up
                                $4d -> easygfx.scrollrect(0, -8, 80, 88, 160, 120)   ; down
                                $4e -> easygfx.scrollrect(-8, 0, 80, 88, 160, 120)   ; right
                                $4f -> easygfx.scrollrect(8, 0, 80, 88, 160, 120)    ; left
                            }
                        }
                    }
                }
                msg = easygfx.imsg_reply(msg)
            }
        }
    }
}
