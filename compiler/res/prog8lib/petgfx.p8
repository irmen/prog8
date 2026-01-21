%import textio

petgfx {
    %option ignore_unused

    ; Plot at "petscii subpixel" x,y , which is twice the text screen resolution so 80 * 50 , instead of 40*25.
    ; NOTE: assumes the screen is cleared with spaces beforehand.

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

    sub plot(ubyte x, ubyte y) {
        ubyte subchar
        x >>= 1
        rol(subchar)
        y >>= 1
        if_cc
            subchar++
        else
            subchar = (subchar<<2) + 4

        ubyte existing = cbm.Screen[y*(txt.DEFAULT_WIDTH as uword) + x]

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
;        for cx16.r0H in 0 to 15 {
;            if subpixels[cx16.r0H] == existing {
;                existing = cx16.r0H
;                break
;            }
;        }

        ; update screen cell with new subpixel
        txt.setchr(x, y, subpixels[subchar | existing])

;  0     1     2     3     4     5     6     7     8     9     A     B     C      D      E      F
; --    #-    -#    ##    --    #-    -#    ##    --    #-    -#    ##    --     #-     -#     ##
; --    --    --    --    #-    #-    #-    #-    -#    -#    -#    -#    ##     ##     ##     ##
;  32,  126,  124,  226,  123,  97,  255,  236,  108,  127,  225,  251,   98,   252,  254,  160

        ubyte[16] subpixels = [32,  126,  124,  226,  123,  97,  255,  236,  108,  127,  225,  251,  98,   252,  254,  160]
    }
}
