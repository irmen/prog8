%import textio
%zeropage basicsafe

; Reproduces the m68k "DIVM/DIVSM swaps operands" bug in codeGenM68k/InstrArithmetic.kt.
; The IR instruction DIVM reg, addr means: memory[addr] = memory[addr] / reg
; (memory is the dividend, reg is the divisor, result is stored back to memory).
; The m68k backend instead computes reg / memory and stores into the register.
; Run on the virtual target for the correct (expected) results, then on
; -target amiga500 to see the wrong results.

main {
    sub start() {
        ; unsigned byte case (DIVM)
        ubyte @shared bvar = 100
        bvar /= 3
        txt.print("ubyte   100 / 3 = ")
        txt.print_ub(bvar)
        txt.print("   (expected 33)")
        txt.nl()

        ; unsigned word case (DIVM)
        uword @shared wvar = 1000
        wvar /= 7
        txt.print("uword   1000 / 7 = ")
        txt.print_uw(wvar)
        txt.print("   (expected 142)")
        txt.nl()

        ; signed byte case (DIVSM)
        byte @shared sbvar = -100
        sbvar /= 3
        txt.print("byte   -100 / 3 = ")
        txt.print_b(sbvar)
        txt.print("   (expected -33)")
        txt.nl()

        ; signed word case (DIVSM)
        word @shared svar = -1000
        svar /= 7
        txt.print("word  -1000 / 7 = ")
        txt.print_w(svar)
        txt.print("   (expected -142)")
        txt.nl()

        ; MULM: memory *= register
        ubyte @shared mbvar = 5
        mbvar *= 7
        txt.print("ubyte  5 * 7 = ")
        txt.print_ub(mbvar)
        txt.print("   (expected 35)")
        txt.nl()

        uword @shared mwvar = 500
        mwvar *= 10
        txt.print("uword  500 * 10 = ")
        txt.print_uw(mwvar)
        txt.print("   (expected 5000)")
        txt.nl()
    }
}
