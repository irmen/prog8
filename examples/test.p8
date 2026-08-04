; Reproduces the "Signed div/mod zero-extends the dividend" m68k codegen bug
; (m68k-potential-codegen-bugs.md).
;
; The WORD div/mod emitters do:
;     moveq  #0, d0          ; "clear upper word"
;     move.w  <dividend>, d0 ; upper word stays 0 (zero-extended)
;     divs.w  <divisor>, d0
; The dividend ends up ZERO-extended instead of sign-extended, so a negative
; signed dividend is treated as a large positive number -> wrong quotient and
; remainder. (Verified by running under vamos: -1000 / 7 prints q=9219 instead
; of -142, r=3 instead of -6.)
;
; The unsigned cases below are a control: under vamos they are CORRECT, because
; a zero-extended upper word is exactly what unsigned division wants.
;
; NOTE: on a real 68000 `move.w` to a data register sign-extends, so the
; behaviour would flip (signed correct, unsigned >= 0x8000 wrong). The robust
; fix is to extend explicitly: signed -> ext.l, unsigned -> and.l #$ffff.

%import textio

main {
    sub start() {
        check_unsigned(40000, 7, 5714, 2)    ; control: correct under vamos
        check_unsigned(30000, 13, 2307, 9)   ; control: correct under vamos
        check_signed(-1000, 7, -142, -6)     ; BUG: prints q=9219 r=3
        txt.print("done\n")
    }

    sub check_unsigned(uword a, uword b, uword exp_q, uword exp_r) {
        uword q = a / b
        uword r = a % b
        txt.print_uw(a)
        txt.print(" / ")
        txt.print_uw(b)
        txt.print(" u: q=")
        txt.print_uw(q)
        txt.print(" r=")
        txt.print_uw(r)
        txt.print("  (expected q=")
        txt.print_uw(exp_q)
        txt.print(" r=")
        txt.print_uw(exp_r)
        txt.print(")\n")
    }

    sub check_signed(word a, word b, word exp_q, word exp_r) {
        word q = a / b
        word r = a % b
        txt.print_w(a)
        txt.print(" / ")
        txt.print_w(b)
        txt.print(" s: q=")
        txt.print_w(q)
        txt.print(" r=")
        txt.print_w(r)
        txt.print("  (expected q=")
        txt.print_w(exp_q)
        txt.print(" r=")
        txt.print_w(exp_r)
        txt.print(")\n")
    }
}
