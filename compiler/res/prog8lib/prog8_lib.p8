; Prog8 internal library routines - always included by the compiler
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

prog8_lib {
	%asminclude "library:prog8_lib.asm", ""

    sub strcmp(uword s1, uword s2) -> byte {
        ; -- convenience wrapper for plain Prog8 to compare strings TODO turn this into a builtin function
        byte result
        %asm {{
            lda  s2
            sta  P8ZP_SCRATCH_W2
            lda  s2+1
            sta  P8ZP_SCRATCH_W2+1
            lda  s1
            ldy  s1+1
            jsr  prog8_lib.strcmp_mem
            sta  result
        }}
        return result
    }
}
