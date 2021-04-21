; 0-terminated string manipulation routines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0


string {

    asmsub length(uword string @AY) clobbers(A) -> ubyte @Y {
        ; Returns the number of bytes in the string.
        ; This value is determined during runtime and counts upto the first terminating 0 byte in the string,
        ; regardless of the size of the string during compilation time. Don’t confuse this with len and sizeof!

        %asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		iny
		bne  -
+		rts
        }}
    }

    asmsub left(uword source @R0, ubyte length @A, uword target @R1) clobbers(A, Y) {
        ; Copies the left side of the source string of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        %asm {{
                ; need to copy the the cx16 virtual registers to zeropage to be compatible with C64...
		ldy  cx16.r0
		sty  P8ZP_SCRATCH_W1
		ldy  cx16.r0+1
		sty  P8ZP_SCRATCH_W1+1
		ldy  cx16.r1
		sty  P8ZP_SCRATCH_W2
		ldy  cx16.r1+1
		sty  P8ZP_SCRATCH_W2+1
		tay
		lda  #0
		sta  (P8ZP_SCRATCH_W2),y
		cpy  #0
		bne  _loop
		rts
_loop		dey
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		cpy  #0
		bne  _loop
+		rts
        }}
;                asmgen.out("  jsr  prog8_lib.func_leftstr")
    }

    asmsub right(uword source @R0, ubyte length @A, uword target @R1) clobbers(A,Y) {
        ; Copies the right side of the source string of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that length is smaller or equal to the length of the source string.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        %asm {{
                ; need to copy the the cx16 virtual registers to zeropage to be compatible with C64...
                sta  P8ZP_SCRATCH_B1
                lda  cx16.r0
                ldy  cx16.r0+1
                jsr  string.length
                tya
                sec
                sbc  P8ZP_SCRATCH_B1
                clc
                adc  cx16.r0
		sta  P8ZP_SCRATCH_W1
		lda  cx16.r0+1
		adc  #0
		sta  P8ZP_SCRATCH_W1+1
		ldy  cx16.r1
		sty  P8ZP_SCRATCH_W2
		ldy  cx16.r1+1
		sty  P8ZP_SCRATCH_W2+1
		ldy  P8ZP_SCRATCH_B1
		lda  #0
		sta  (P8ZP_SCRATCH_W2),y
		cpy  #0
		bne  _loop
		rts
_loop		dey
		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
		cpy  #0
		bne  _loop
+		rts
        }}
    }

    asmsub slice(uword source @R0, ubyte start @A, ubyte length @Y, uword target @R1) clobbers(A, Y) {
        ; Copies a segment from the source string, starting at the given index,
        ;  and of the given length to target string.
        ; It is assumed the target string buffer is large enough to contain the result.
        ; Also, you have to make sure yourself that start and length are within bounds of the strings.
        ; Modifies in-place, doesn’t return a value (so can’t be used in an expression).
        %asm {{
                ; need to copy the the cx16 virtual registers to zeropage to be compatible with C64...
		; substr(source, target, start, length)
		sta  P8ZP_SCRATCH_B1
		lda  cx16.r0
		sta  P8ZP_SCRATCH_W1
		lda  cx16.r0+1
		sta  P8ZP_SCRATCH_W1+1
		lda  cx16.r1
		sta  P8ZP_SCRATCH_W2
		lda  cx16.r1+1
		sta  P8ZP_SCRATCH_W2+1

		; adjust src location
		clc
		lda  P8ZP_SCRATCH_W1
		adc  P8ZP_SCRATCH_B1
		sta  P8ZP_SCRATCH_W1
		bcc  +
		inc  P8ZP_SCRATCH_W1+1
+		lda  #0
		sta  (P8ZP_SCRATCH_W2),y
		beq  _startloop
-		lda  (P8ZP_SCRATCH_W1),y
		sta  (P8ZP_SCRATCH_W2),y
_startloop	dey
		cpy  #$ff
		bne  -
		rts
        }}
    }

    asmsub find(uword string @R0, ubyte character @A) -> uword @AY {
        ; Locates the first position of the given character in the string,
        ;  returns the string starting with this character or $0000 if the character is not found.
        %asm {{
                ; need to copy the the cx16 virtual registers to zeropage to be compatible with C64...
                sta  P8ZP_SCRATCH_B1
		lda  cx16.r0
		ldy  cx16.r0+1
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  _notfound
		cmp  P8ZP_SCRATCH_B1
		beq  _found
		iny
		bne  -
_notfound	lda  #0
		ldy  #0
		rts
_found		sty  P8ZP_SCRATCH_B1
		ldy  P8ZP_SCRATCH_W1+1
		lda  P8ZP_SCRATCH_W1
		clc
		adc  P8ZP_SCRATCH_B1
		bcc  +
		iny
+		rts

        }}
    }

    asmsub copy(uword source @R0, uword target @AY) clobbers(A) -> ubyte @Y {
        ; Copy a string to another, overwriting that one.
        ; Returns the length of the string that was copied.
        ; Often you don’t have to call this explicitly and can just write string1 = string2
        ; but this function is useful if you’re dealing with addresses for instance.
        %asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  cx16.r0
		ldy  cx16.r0+1
		jmp  prog8_lib.strcpy
        }}
    }

    asmsub compare(uword string1 @R0, uword string2 @AY) clobbers(Y) -> byte @A {
        ; Compares two strings for sorting.
        ; Returns -1 (255), 0 or 1 depeding on wether string1 sorts before, equal or after string2.
        ; Note that you can also directly compare strings and string values with eachother using
        ; comparison operators ==, < etcetera (it will use strcmp for you under water automatically).
        %asm {{
		sta  P8ZP_SCRATCH_W2
		sty  P8ZP_SCRATCH_W2+1
		lda  cx16.r0
		ldy  cx16.r0+1
		jmp  prog8_lib.strcmp_mem
        }}
    }

    asmsub lower(uword st @AY) -> ubyte @Y {
        ; Lowercases the petscii string in-place. Returns length of the string.
        ; (for efficiency, non-letter characters > 128 will also not be left intact,
        ;  but regular text doesn't usually contain those characters anyway.)
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  _done
            and  #$7f
            cmp  #97
            bcc  +
            cmp  #123
            bcs  +
            and  #%11011111
+           sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
_done       rts
        }}
    }

    asmsub upper(uword st @AY) -> ubyte @Y {
        ; Uppercases the petscii string in-place. Returns length of the string.
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  _done
            cmp  #65
            bcc  +
            cmp  #91
            bcs  +
            ora  #%00100000
+           sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
_done       rts
        }}
    }
}
