; tic tac toe via the amiga_gfx library: click squares to play X and O.
; Left click places a mark, R restarts, ESC quits.
; Runs on Kickstart 1.3 and up, on amiga500 and amiga1200.

%import amiga_gfx
%import diskfont
%import graphics
%import intuition

main {
    uword xbits             ; bit i set = X occupies cell i
    uword obits             ; bit i set = O occupies cell i
    ubyte turn = Player::X
    bool over = false

    const word CELL = 60
    const word BX = 70      ; (320 - 3*60) / 2
    const word BY = 38      ; (256 - 3*60) / 2

    enum Player {
        NONE,
        X,
        O
    }

    enum Outcome {
        ONGOING,
        XWINS,
        OWINS,
        DRAW
    }

    sub start() {
        if amiga_gfx.fullscreen(320, 256, 2, graphics.LORES, "Tic Tac Toe") {
            defer amiga_gfx.close()
            ; try to set custom font, silently keeps Topaz 8 when unavailable
            ^^graphics.TextFont font = 0
            if diskfont.openlib() {
                font = diskfont.openfont("ruby.font", 15, graphics.FS_NORMAL)
                diskfont.closelib()
            }
            if font != 0 {
                ^^graphics.RastPort rp = amiga_gfx.rastport
                void graphics.SetFont(rp, font)
            }
            new_game()
            interact()
            if font != 0
                graphics.CloseFont(font)
        }
    }

    sub new_game() {
        xbits = 0
        obits = 0
        turn = Player::X
        over = false
        amiga_gfx.clear(0)
        draw_grid()
        status("X to move - click a square")
    }

    sub draw_grid() {
        ubyte i
        for i in 1 to 2 {
            amiga_gfx.line(BX + i*CELL, BY, BX + i*CELL, BY + 3*CELL, 1)
            amiga_gfx.line(BX, BY + i*CELL, BX + 3*CELL, BY + i*CELL, 1)
        }
    }

    sub draw_mark(ubyte col, ubyte row, ubyte player) {
        word cx = BX + (col as word) * CELL
        word cy = BY + (row as word) * CELL
        if player == Player::X {
            amiga_gfx.line(cx+10, cy+10, cx+CELL-10, cy+CELL-10, 2)
            amiga_gfx.line(cx+CELL-10, cy+10, cx+10, cy+CELL-10, 2)
            amiga_gfx.line(cx+11, cy+10, cx+CELL-9, cy+CELL-10, 2)
            amiga_gfx.line(cx+CELL-9, cy+10, cx+11, cy+CELL-10, 2)
        } else {
            amiga_gfx.circle(cx+CELL/2, cy+CELL/2, CELL/2-10, 3)
            amiga_gfx.circle(cx+CELL/2, cy+CELL/2, CELL/2-11, 3)
        }
    }

    sub cell_at(word mx, word my) -> byte, byte {
        ; (col, row) under client coords, or (-1, -1) when outside the grid.
        if mx < BX or mx >= BX+3*CELL or my < BY or my >= BY+3*CELL
            return -1, -1
        byte col = 0
        if mx >= BX+2*CELL
            col = 2
        else {
            if mx >= BX+CELL
                col = 1
        }
        byte row = 0
        if my >= BY+2*CELL
            row = 2
        else {
            if my >= BY+CELL
                row = 1
        }
        return col, row
    }

    sub check_win() -> ubyte {
        ; Each player's squares are a 9-bit bitmask packed in an integer — bit i is set if they own cell i.
        ; The 8 winning lines are precomputed masks, so a win test is just (bits & mask) == mask for each:
        ; a subset check in a single AND. Occupancy is (x | o) & bit, and a full board is (x | o) == 0x1FF.
        ; No arrays, no loops over cells — every query is constant-time bitwise logic.

        for i in 0 to 7 {
            uword m = wins[i]
            if (xbits & m) == m
                return Outcome::XWINS
            if (obits & m) == m
                return Outcome::OWINS
        }
        if (xbits | obits) == $1ff
            return Outcome::DRAW
        return Outcome::ONGOING

        uword[8] wins = [$007, $038, $1c0, $049, $092, $124, $111, $054]
    }

    sub status(str msg) {
        ; JAM2 so the text background overwrites the previous line;
        ; with the default JAM1 black pixels are transparent and text ghosts.
        ; amiga_gfx.fillrect(0, BY + 3*CELL + 16, 320, 12, 3)
        ^^graphics.RastPort rp = amiga_gfx.rastport
        graphics.SetBPen(rp, 3)
        amiga_gfx.drawmode(graphics.RP_JAM2)
        word tw = amiga_gfx.textwidth(msg)
        amiga_gfx.text(160 - tw/2, BY + 3*CELL + 16, msg, 1)
        amiga_gfx.drawmode(graphics.RP_JAM1)
    }

    sub interact() {
        ^^intuition.IntuiMessage msg
        bool done = false
        while not done {
            msg = amiga_gfx.imsg_wait()
            while msg != 0 {
                when msg.Class {
                    intuition.IDCMP_CLOSEWINDOW -> done = true
                    intuition.IDCMP_MOUSEBUTTONS -> handle_click()
                    intuition.IDCMP_RAWKEY -> handle_key()
                }
                msg = amiga_gfx.imsg_reply(msg)
            }
        }

        sub play_at(ubyte ucol, ubyte urow) {
            uword bit = (1 as uword) << (urow*3+ucol)
            if ((xbits | obits) & bit) == 0 {
                if turn == Player::X
                    xbits = xbits | bit
                else
                    obits = obits | bit
                draw_mark(ucol, urow, turn)
                when check_win() {
                    Outcome::XWINS -> {
                        status("X wins! click for a new game")
                        over = true
                    }
                    Outcome::OWINS -> {
                        status("O wins! click for a new game")
                        over = true
                    }
                    Outcome::DRAW -> {
                        status("Draw! click for a new game")
                        over = true
                    }
                    else -> {
                        if turn == Player::X
                            turn = Player::O
                        else
                            turn = Player::X
                        if turn == Player::X
                            status("X to move - click a square")
                        else
                            status("O to move - click a square")
                    }
                }
            }
        }

        sub handle_click() {
            if msg.Code != intuition.SELECTDOWN
                return
            if over {
                new_game()
                return
            }
            word mx, my = amiga_gfx.mouse_xy(msg)
            byte col, row = cell_at(mx, my)
            if col >= 0
                play_at(col as ubyte, row as ubyte)
        }

        sub handle_key() {
            if (msg.Code & $80) == 0 {
                when (msg.Code & $7f) {
                    $45 -> done = true   ; ESC
                    $13 -> new_game()    ; R
                }
            }
        }
    }
}
