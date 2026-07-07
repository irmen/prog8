%import textio

; Minimal reproduction of a QEMU m68k divu.w emulation bug.
;
; Expected output:
;   392
;   100
;
; Actual (buggy) output:
;   41384
;   100

main {
    sub start() {
        ; --- Test 1: v=392, q=v/10 ---
        uword v = 392
        uword q = v / 10
        txt.nl()            ;  CRITICAL: if this line is removed, the bug appears. If it is present, bug is gone.
        txt.print_uw(v)
        txt.nl()

        ; --- Test 2: v=100, q=v/10 ---
        v = 100
        q = v / 10
        txt.print_uw(v)
        txt.nl()
        txt.nl()
        txt.nl()

        sys.poweroff_system()
    }
}
