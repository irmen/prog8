%import textio
%zeropage basicsafe

; Tests x << 16 and x >> 16 for a long value.
; - x >> 16 (unsigned long) is folded to MSIGW in the IR codegen.
; - x << 16 (long) is lowered to `swap; clr.w` in the m68k codegen.
; - 6502 uses MSIGW (byte grab) for x >> 16; x << 16 still unrolls
;   to 16 ASL instructions (not yet optimized on 6502).
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;   rg -E 'lsli|lsri|asri|load.*#|lsln|lsrn|asrn|msigw' /tmp/opencode/test.p8ir
;   prog8c -target qemu68k -emu examples/test.p8

main {
    sub start() {
        test_word8_shifts()
        test_long16_shifts()
    }

    sub test_word8_shifts() {
        ; uword shifted by 8 -- the boundary of the m68k immediate-count
        ; range (1..8). Should emit `lsl.w #8, d0` / `lsr.w #8, d0`.
        uword @shared w = $1234
        uword @shared shl8 = w << 8
        uword @shared shr8 = w >> 8

        txt.print("w        = ")
        txt.print_uwhex(w, true)
        txt.print(" (expected $1234)")
        txt.nl()
        txt.print("w << 8   = ")
        txt.print_uwhex(shl8, true)
        txt.print(" (expected $3400)")
        txt.nl()
        txt.print("w >> 8   = ")
        txt.print_uwhex(shr8, true)
        txt.print(" (expected $0012)")
        txt.nl()
    }

    sub test_long16_shifts() {
        long @shared x = $12345678
        long @shared shr16 = x >> 16
        long @shared shl16 = x << 16

        txt.print("x        = ")
        txt.print_ulhex(x, true)
        txt.print(" (expected $12345678)")
        txt.nl()
        txt.print("x >> 16  = ")
        txt.print_ulhex(shr16, true)
        txt.print(" (expected $00001234)")
        txt.nl()
        txt.print("x << 16  = ")
        txt.print_ulhex(shl16, true)
        txt.print(" (expected $56780000)")
        txt.nl()
    }
}
