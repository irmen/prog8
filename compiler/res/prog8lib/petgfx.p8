%import textio

petgfx {
    ; Plot at "petscii subpixel" x,y , which is twice the text screen
    ; resolution (so 40x25 -> 80 * 50, 80x25 -> 160 * 50)

    %option ignore_unused

    enum Mode {
        Draw, Erase, Xor
    }
    ubyte mode = Mode::Draw

    sub draw_mode() {
        mode = Mode::Draw
    }

    sub erase_mode() {
        mode = Mode::Erase
    }

    sub xor_mode() {
        mode = Mode::Xor
    }

    sub clear_screen() {
        txt.chrout(147)
    }

    sub hline(ubyte x, ubyte y, ubyte length) {
        ; horizontal line
        repeat length {
            plot(x, y)
            x++
        }
    }

    sub vline(ubyte x, ubyte y, ubyte length) {
        ; vertical line
        repeat length {
            plot(x, y)
            y++
        }
    }

    sub line(ubyte x1, ubyte y1, ubyte x2, ubyte y2) {
        ; arbitrary line
        byte dx = abs(x2 - x1) as byte
        byte sx = if x1 < x2 1 else -1
        byte dy = -(abs(y2 - y1) as byte)
        byte sy = if y1 < y2 1 else -1
        byte error = dx + dy
        byte x = x1 as byte
        byte y = y1 as byte

        repeat {
            plot(x as ubyte, y as ubyte)
            byte e2 = 2 * error
            if e2 >= dy {
                if x == x2 
                    break
                error += dy
                x += sx
            }
            if e2 <= dx {
                if y == y2 
                    break
                error += dx
                y += sy
            }
        }
    }

    sub plot(ubyte x, ubyte y) {
        ubyte subchar
        x >>= 1
        rol(subchar)
        y >>= 1
        if_cc
            subchar++
        else
            subchar = (subchar<<2) + 4

        ubyte existing = txt.getchr(x, y)

        ; search the index of the current subpixels in the cell
        %asm {{
            ldy  #0
            lda  p8v_existing
-           cmp  p8v_subpixels,y
            beq  +
            iny
            cpy  #16
            bne  -
+           sty  p8v_existing
        }}

        ; update screen cell with new subpixel
        when mode {
            Mode::Draw -> txt.setchr(x, y, 
                                 subpixels[existing | subchar ])
            Mode::Erase-> txt.setchr(x, y, 
                                 subpixels[existing & ~subchar & $f])
            Mode::Xor  -> txt.setchr(x, y, 
                                 subpixels[existing ^ subchar])
        }


;  0     1     2     3     4     5     6     7     8     9     A     B     C      D      E      F
; --    #-    -#    ##    --    #-    -#    ##    --    #-    -#    ##    --     #-     -#     ##
; --    --    --    --    #-    #-    #-    #-    -#    -#    -#    -#    ##     ##     ##     ##
;  32,  126,  124,  226,  123,  97,  255,  236,  108,  127,  225,  251,   98,   252,  254,  160

        ubyte[16] subpixels = [32,  126,  124,  226,  123,  97,  255,  236,  108,  127,  225,  251,  98,   252,  254,  160]
    }
}
