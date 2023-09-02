%import textio
%import floats
%import test_stack
%zeropage dontuse

main {
    sub start()  {
        float value1 = -0.8
        float value2 = 0.3
        float one = 1.0

        float result = value1*one + value2*one  ; TODO FIX: invalid result on c64, ok when the *one is removed or expression is split (it's not caused by pushFAC1/popFAC1)
        ; TODO is it floats.CONUPK??
        floats.print_f(result)
        txt.nl()
        txt.print("-.5 was expected\n\n")       ; on C64: -1.1 is printed :(

;        result = value2*one + value1*one        ; swapped operands around, now it's suddenly fine on C64...
;        floats.print_f(result)
;        txt.nl()
;        txt.print("-.5 was expected\n\n")       ; on C64: correct value is printed


;        value1 = 0.8
;        value2 = 0.3
;        result = value1*one + value2*one
;        floats.print_f(result)
;        txt.nl()
;        txt.print("1.1 was expected\n\n")       ; on C64: correct value is printed
;
;        printFAC1()
;        printFAC1()
;        printFAC1()

        repeat {
        }
    }

    sub printFAC1() {
        ubyte[20] fac_save
        sys.memcopy($60, fac_save, sizeof(fac_save))
        txt.print("fac1=")
        %asm {{
		jsr  floats.FOUT		; fac1 to string in A/Y
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
-		lda  (P8ZP_SCRATCH_W1),y
		beq  +
		jsr  cbm.CHROUT
		iny
		bne  -
+       }}
        txt.nl()
        sys.memcopy(fac_save, $60, sizeof(fac_save))
    }
}
