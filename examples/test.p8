%encoding iso
%option no_sysinit
%zeropage basicsafe
%import textio
%import syslib

; Verifies signed division by a power of two truncates toward zero (not floor).
; A plain arithmetic shift (>> / ASR) floors, which is wrong for negative values:
;   -3 / 2 == -1 (toward zero) but -3 >> 1 == -2 (floor).
;
; This must hold for byte, word AND long, and for powers of two 2 and 4.
; @shared forces runtime evaluation so the compiler's division rewrite is observable.
;
; On the 6502 (cx16 -newcodegen) the IR codegen emits a bias-corrected shift instead of a
; DIVS routine. Other targets keep the DIVS instruction (which is correct once their codegen
; lowers it properly).
;
;   prog8c -target cx16 -newcodegen -emu examples/test.p8
;   optimized -> identical (correct) results as -noopt

main {
    sub start() {
        txt.iso()

        ; ---- byte ----
        byte @shared xb = -3
        byte @shared rb = xb / 2
        txt.print("-3 / 2 (byte, == -1): ")
        txt.print_b(rb)
        txt.print("\n")
        byte @shared yb = 7
        byte @shared sb = yb / 2
        txt.print("7 / 2 (byte, == 3): ")
        txt.print_b(sb)
        txt.print("\n")
        byte @shared zb = -7
        byte @shared tb = zb / 4
        txt.print("-7 / 4 (byte, == -1): ")
        txt.print_b(tb)
        txt.print("\n")

        ; ---- word ----
        word @shared xw = -3
        word @shared rw = xw / 2
        txt.print("-3 / 2 (word, == -1): ")
        txt.print_w(rw)
        txt.print("\n")
        word @shared yw = 7
        word @shared sw = yw / 2
        txt.print("7 / 2 (word, == 3): ")
        txt.print_w(sw)
        txt.print("\n")
        word @shared zw = -7
        word @shared tw = zw / 4
        txt.print("-7 / 4 (word, == -1): ")
        txt.print_w(tw)
        txt.print("\n")

        ; ---- long ----
        long @shared xl = -3
        long @shared rl = xl / 2
        txt.print("-3 / 2 (long, == -1): ")
        txt.print_w(rl as word)
        txt.print("\n")
        long @shared yl = 7
        long @shared sl = yl / 2
        txt.print("7 / 2 (long, == 3): ")
        txt.print_w(sl as word)
        txt.print("\n")
        long @shared zl = -7
        long @shared tl = zl / 4
        txt.print("-7 / 4 (long, == -1): ")
        txt.print_w(tl as word)
        txt.print("\n")

        txt.print("done")
        txt.print("\n")
        ;sys.poweroff_system()
    }
}
