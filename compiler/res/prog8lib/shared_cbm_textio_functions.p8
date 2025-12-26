txt {
    ; the textio functions common across CBM-compatible compiler targets (c64, c128, pet32 and cx16)

    %option merge, no_symbol_prefixing, ignore_unused

    sub clear_screen() {
        chrout(147)
    }

    sub cls() {
        chrout(147)
    }

    sub home() {
        chrout(19)
    }

    sub nl() {
        chrout('\n')
    }

    sub spc() {
        chrout(' ')
    }

    sub rvs_on() {
        txt.chrout(18)
    }

    sub rvs_off() {
        txt.chrout(146)
    }


    asmsub  print (str text @ AY) clobbers(A,Y)  {
    	; ---- print zero terminated string, in PETSCII encoding, from A/Y
    	; note: the compiler contains an optimization that will replace
    	;       a call to this subroutine with a string argument of just one char,
    	;       by just one call to cbm.CHROUT of that single char.
    	%asm {{
    		sta  P8ZP_SCRATCH_W2
    		sty  P8ZP_SCRATCH_W2+1
    		ldy  #0
-	    	lda  (P8ZP_SCRATCH_W2),y
    		beq  +
    		jsr  cbm.CHROUT
    		iny
    		bne  -
+	    	rts
    	}}
    }

    asmsub  print_ubhex  (ubyte value @ A, bool prefix @ Pc) clobbers(A,X,Y)  {
        ; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
        %asm {{
            bcc  +
            pha
            lda  #'$'
            jsr  cbm.CHROUT
            pla
+           jsr  conv.internal_ubyte2hex
            jsr  cbm.CHROUT
            tya
            jmp  cbm.CHROUT
        }}
    }

    asmsub  print_ubbin  (ubyte value @ A, bool prefix @ Pc) clobbers(A,X,Y)  {
        ; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
        %asm {{
            sta  P8ZP_SCRATCH_B1
            bcc  +
            lda  #'%'
            jsr  cbm.CHROUT
+           ldy  #8
-           lda  #'0'
            asl  P8ZP_SCRATCH_B1
            bcc  +
            lda  #'1'
+           jsr  cbm.CHROUT
            dey
            bne  -
            rts
        }}
    }

    asmsub  print_uwbin  (uword value @ AY, bool prefix @ Pc) clobbers(A,X,Y)  {
        ; ---- print the uword in A/Y in binary form (if Carry is set, a radix prefix '%' is printed as well)
        %asm {{
            pha
            tya
            jsr  print_ubbin
            pla
            clc
            jmp  print_ubbin
        }}
    }

    asmsub  print_uwhex  (uword value @ AY, bool prefix @ Pc) clobbers(A,X,Y)  {
        ; ---- print the uword in A/Y in hexadecimal form (4 digits)
        ;      (if Carry is set, a radix prefix '$' is printed as well)
        %asm {{
            pha
            tya
            jsr  print_ubhex
            pla
            clc
            jmp  print_ubhex
        }}
    }

    sub print_ulhex(long value, bool prefix) {
        if prefix
            cbm.CHROUT('$')
        print(conv.str_ulhex(value))
    }

    asmsub  print_uw0  (uword value @ AY) clobbers(A,X,Y)  {
        ; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
        %asm {{
            jsr  conv.internal_uword2decimal
            ldy  #0
-           lda  conv.internal_uword2decimal.decTenThousands,y
            beq  +
            jsr  cbm.CHROUT
            iny
            bne  -
+           rts
        }}
    }

    asmsub  print_uw  (uword value @ AY) clobbers(A,X,Y)  {
        ; ---- print the uword in A/Y in decimal form, without left padding 0s
        %asm {{
            jsr  conv.internal_uword2decimal
            ldy  #0
-           lda  conv.internal_uword2decimal.decTenThousands,y
            beq  _allzero
            cmp  #'0'
            bne  _gotdigit
            iny
            bne  -
_gotdigit   jsr  cbm.CHROUT
            iny
            lda  conv.internal_uword2decimal.decTenThousands,y
            bne  _gotdigit
            rts
_allzero    lda  #'0'
            jmp  cbm.CHROUT
        }}
    }

    asmsub  print_w  (word value @ AY) clobbers(A,X,Y)  {
        ; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
        %asm {{
            cpy  #0
            bpl  +
            pha
            lda  #'-'
            jsr  cbm.CHROUT
            tya
            eor  #255
            tay
            pla
            eor  #255
            clc
            adc  #1
            bcc  +
            iny
+           jmp  print_uw
        }}
    }

    sub print_l(long value) {
        ; prints a 32 bit value to the screen
        print(conv.str_l(value))
    }

    asmsub  input_chars  (^^ubyte buffer @ AY) clobbers(A) -> ubyte @ Y  {
        ; ---- Input a string (max. 80 chars) from the keyboard, in PETSCII encoding.
        ;      Returns length in Y. (string is terminated with a 0 byte as well)
        ;      It assumes the keyboard is selected as I/O channel!

        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0				; char counter = 0
-           jsr  cbm.CHRIN
            cmp  #$0d			; return (ascii 13) pressed?
            beq  +				; yes, end.
            sta  (P8ZP_SCRATCH_W1),y	; else store char in buffer
            iny
            bne  -
+           lda  #0
            sta  (P8ZP_SCRATCH_W1),y	; finish string with 0 byte
            rts
        }}
    }

    asmsub petscii2scr(ubyte petscii_char @A) -> ubyte @A {
        ; -- convert petscii character to screencode
        %asm {{
            sta  P8ZP_SCRATCH_REG
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            tax
            lda  _offsets,x
            eor  P8ZP_SCRATCH_REG
            rts
_offsets    .byte  128, 0, 64, 32, 64, 192, 128, 128
            ; !notreached!
        }}
    }

    asmsub petscii2scr_str(str petscii_string @AY) {
        ; -- convert petscii string to screencodes string
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  +
            jsr  petscii2scr
            sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
+           rts
        }}
    }

    sub iso2petscii(ubyte iso_char) -> ubyte {
        ; --converts iso 8859-15 character to petscii character (lowercase)
        if iso_char & $7f <= $20
        	return petscii:' '  ; whitspace
        if iso_char <= $3f
            return iso_char  ; numbers and symbols
        if iso_char < $80
            return translate40to7F[iso_char-$40]
        return translateA0toFF[iso_char-$a0]

        ubyte[$40] translate40to7F = [
            $40, $c1, $c2, $c3, $c4, $c5, $c6, $c7, $c8, $c9, $ca, $cb, $cc, $cd, $ce, $cf,
            $d0, $d1, $d2, $d3, $d4, $d5, $d6, $d7, $d8, $d9, $da, $5b, $3f, $5d, $5e, $e4,
            $27, $41, $42, $43, $44, $45, $46, $47, $48, $49, $4a, $4b, $4c, $4d, $4e, $4f,
            $50, $51, $52, $53, $54, $55, $56, $57, $58, $59, $5a, $f3, $7d, $eb, $3f, $3f,
        ]
        ubyte[$60] translateA0toFF = [
            $20, $21, $20, $5c, $c5, $d9, $3f, $3f, $3f, $c3, $3f, $28, $3f, $3f, $d2, $e3,
            $3f, $3f, $32, $33, $3f, $3f, $ff, $3f, $3f, $31, $3f, $3e, $3f, $3f, $d9, $3f,
            $c1, $c1, $c1, $c1, $c1, $c1, $c1, $c3, $c5, $c5, $c5, $c5, $c9, $c9, $c9, $c9,
            $c4, $ce, $cf, $cf, $cf, $cf, $cf, $58, $cf, $d5, $d5, $d5, $d5, $d9, $3f, $53,
            $41, $41, $41, $41, $41, $41, $41, $43, $45, $45, $45, $45, $49, $49, $49, $49,
            $4f, $4e, $4f, $4f, $4f, $4f, $4f, $3f, $4f, $55, $55, $55, $55, $59, $3f, $59,
        ]
    }

    asmsub iso2petscii_str(str iso_string @AY) {
        ; -- convert iso string to petscii (lowercase) string
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            beq  +
            sty  P8ZP_SCRATCH_REG
            jsr  iso2petscii
            ldy  P8ZP_SCRATCH_REG
            sta  (P8ZP_SCRATCH_W1),y
            iny
            bne  -
+           rts
        }}
    }


    sub print_bool(bool value) {
        if value
            txt.print("true")
        else
            txt.print("false")
    }

    asmsub  print_ub0  (ubyte value @ A) clobbers(A,X,Y)  {
        ; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
        %asm {{
            jsr  conv.internal_ubyte2decimal
            pha
            tya
            jsr  cbm.CHROUT
            txa
            jsr  cbm.CHROUT
            pla
            jmp  cbm.CHROUT
        }}
    }

    asmsub  print_ub  (ubyte value @ A) clobbers(A,X,Y)  {
        ; ---- print the ubyte in A in decimal form, without left padding 0s
        %asm {{
            jsr  conv.internal_ubyte2decimal
_print_byte_digits
            pha
            cpy  #'0'
            beq  +
            tya
            jsr  cbm.CHROUT
            txa
            jsr  cbm.CHROUT
            jmp  _ones
+           cpx  #'0'
            beq  _ones
            txa
            jsr  cbm.CHROUT
_ones       pla
            jmp  cbm.CHROUT
        }}
    }

    asmsub  print_b  (byte value @ A) clobbers(A,X,Y)  {
        ; ---- print the byte in A in decimal form, without left padding 0s
        %asm {{
            pha
            cmp  #0
            bpl  +
            lda  #'-'
            jsr  cbm.CHROUT
+		    pla
            jsr  conv.internal_byte2decimal
            jmp  print_ub._print_byte_digits
        }}
    }

}
