%option ignore_unused

strings {
    sub isdigit(ubyte char) -> bool {
        return char >= '0' and char <= '9'
    }

    sub isxdigit(ubyte char) -> bool {
        return (char >= '0' and char <= '9') or (char >= 'a' and char <= 'f') or (char >= 'A' and char <= 'F')
    }

    sub lowerchar(ubyte char) -> ubyte {
        if char >= 'A' and char <= 'Z'
            return char + 32
        return char
    }


    asmsub copy(str source @D0, str target @D1) -> ubyte @D0 {
        ; Copy a string to another, overwriting that one.
        ; Returns the length of the string that was copied.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
            moveq    #0,d0
.loop:
            move.b   (a0)+,d2
            move.b   d2,(a1)+
            beq      .done
            addq.l   #1,d0
            bra      .loop
.done:
            rts
        }}
    }

    asmsub ncopy(str source @D0, str target @D1, ubyte maxlength @D2) -> ubyte @D0 {
        ; Copy a string to another, overwriting that one, but limited to the given length.
        ; Always null-terminates the result.
        ; Returns the length of the string that was copied.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
            moveq    #0,d0
            tst.b    d2
            beq      .zero
.loop:
            move.b   (a0)+,d1
            move.b   d1,(a1)+
            beq      .done
            addq.l   #1,d0
            subq.b   #1,d2
            bne      .loop
            ; maxlength bytes reached - null-terminate
            clr.b    (a1)
            rts
.zero:
            clr.b    (a1)
.done:
            rts
        }}
    }
}
