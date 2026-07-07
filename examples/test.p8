%output raw
%import textio

; Minimal reproduction of a QEMU m68k divu.w emulation bug.

main {
    sub start() {
        ; --- Test 1: v=392, q=v/10 ---
        ; Expected output: 392
        ; Actual (buggy) output: 41384
        uword v = 392
        uword q = v / 10
        ;txt.nl()            ;  CRITICAL: if this line is removed, the bug appears. If it is present, bug is gone.
        txt.print_uw(v)
        txt.nl()
        ;v = 100             ; CRITICAL: if this line is removed, the bug DISAPPEARS
        sys.poweroff_system()
    }
}
