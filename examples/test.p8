; Reproduces the m68k divmod codegen bugs:
;   1. internal error "unknown calling convention slot: s4" - divmod() returns its
;      quotient in the 6502-style AY hardware register (slot s4), which the m68k
;      backend did not map. (Fixed in m68kSlotRegister.)
;   2. "DIVMOD does not push on the stack" - emitDivModOp writes the quotient and
;      remainder into regfile slots instead of pushing them, so the two following
;      POPs overwrite them with stack garbage (see m68k-potential-codegen-bugs.md).
;   3. divmod() stores its remainder in the cx16-only symbol cx16.r15, which does
;      not exist on the m68k target (undefined symbol at assembly time).
;
; Compiling for -target amiga500 currently fails to assemble because of (3).
; The program also prints calculated (divmod) vs expected ('/' and '%') values
; so the push bug (2) shows up as wrong printed results once it assembles.

%import textio
%zeropage basicsafe

main {
    sub start() {
        check(40000, 500)
        check(43211, 2)
        check(65535, 1000)
        check(12345, 777)
        txt.print("done\n")
    }

    sub check(uword a, uword b) {
        ; divmod gives quotient and remainder in one division
        uword q, r = divmod(a, b)
        ; independent reference values
        uword exp_q = a / b
        uword exp_r = a % b

        txt.print_uw(a)
        txt.print(" / ")
        txt.print_uw(b)
        txt.print(" : divmod q=")
        txt.print_uw(q)
        txt.print(" r=")
        txt.print_uw(r)
        txt.print("   (expected q=")
        txt.print_uw(exp_q)
        txt.print(" r=")
        txt.print_uw(exp_r)
        txt.print(")\n")
    }
}
