%option no_sysinit
%zeropage basicsafe
%import textio

; Explicit on..goto jump table on the m68k target. The compiler lowers
; this directly to a jump table (no optimizer rewrite involved). The
; label array is sized to POINTER_MEM_SIZE (4 bytes per entry on m68k).
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;
; Verify the IR contains a long[7] table and a loadx.l indexing into
; it, and that the asm uses dc.l for the data and an address-register
; indexed load (move.l (a0,d0.w)) for the dispatch.

main {
    sub start() {
        ubyte @shared idx = 3

        on idx goto (lbl0, lbl1, lbl2, lbl3, lbl4, lbl5, lbl6)

        lbl0:
        txt.print("0\n")
        lbl1:
        txt.print("1\n")
        lbl2:
        txt.print("2\n")
        lbl3:
        txt.print("3\n")
        lbl4:
        txt.print("4\n")
        lbl5:
        txt.print("5\n")
        lbl6:
        txt.print("6\n")
    }
}
