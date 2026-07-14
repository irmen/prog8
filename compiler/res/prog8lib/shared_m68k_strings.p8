strings {
    %option merge, no_symbol_prefixing, ignore_unused

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

    sub upperchar(ubyte char) -> ubyte {
        if char >= 'a' and char <= 'z'
            return char - 32
        return char
    }

    alias lowerchar_iso = strings.lowerchar
    alias upperchar_iso = strings.upperchar


    asmsub length(str string @A0) -> ubyte @D0 {
        ; Returns the number of bytes in the string up to the first 0-terminator.
        %asm {{
            moveq    #0,d0
            bra.s    .enter
.loop:
            addq.w   #1,d0
.enter:
            tst.b    (a0)+
            bne      .loop
            rts
        }}
    }

    asmsub compare(str st1 @D0, str st2 @D1) -> byte @D0 {
        ; Compares two strings for sorting, case-sensitively.
        ; Returns -1 (255 as byte), 0 or 1.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
.loop:
            move.b   (a0)+,d0
            move.b   (a1)+,d1
            cmp.b    d1,d0
            bne      .diff
            tst.b    d0
            bne      .loop
            moveq    #0,d0
            rts
.diff:
            blo      .less
            moveq    #1,d0
            rts
.less:
            moveq    #-1,d0
            rts
        }}
    }

    asmsub hash(str string @D0) -> ubyte @D0 {
        ; 8-bit hashing function.
        ; hashcode = 179;  for each byte: ROL(hashcode) XOR byte
        %asm {{
            movea.l  d0,a0
            move.b   #179,d0
            moveq    #0,d1
            move.w   d1,ccr          ; clear flags (X=0 for roxl)
            bra      .enter
.loop:
            roxl.b   #1,d0
            eor.b    d1,d0
.enter:
            move.b   (a0)+,d1
            bne      .loop
            rts
        }}
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
            bra.s    .loop
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
