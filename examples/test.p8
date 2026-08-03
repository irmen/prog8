%import textio
%zeropage basicsafe

; Demonstrates the two "wrong-on-m68k" AST rewrites in ExpressionSimplifier:
;   lsb(msw(longvar))  ->  @(&longvar+2)
;   msb(lsw(longvar))  ->  @(&longvar+1)
; These assume a little-endian (6502) memory layout. They are NOT gated, so
; they fire for every target. On 6502 (and the virtual target) the offsets are
; correct; on big-endian m68k the same offsets read a different byte, so the
; printed value is wrong there.
;
; With value = 0x11223344  (LE bytes in memory: 44 33 22 11):
;   lsb(msw(value)) should be bits 16-23 = 0x22  (6502/virtual prints 0x34)
;   msb(lsw(value)) should be bits  8-15 = 0x33  (6502/virtual prints 0x51)
; On m68k (bytes 11 22 33 44) the offset +2/+1 reads the wrong byte, so the
; output is swapped: lsb(msw)=0x51, msb(lsw)=0x34.

main {
    sub start() {
        long @shared value = $11223344

        ubyte @shared a = lsb(msw(value))
        txt.print("lsb(msw)=")
        txt.print_ubhex(a, true)
        txt.print(" ($22 on 6502, wrong on m68k)")
        txt.print("\n")

        ubyte @shared b = msb(lsw(value))
        txt.print("msb(lsw)=")
        txt.print_ubhex(b, true)
        txt.print(" ($33 on 6502, wrong on m68k)")
        txt.print("\n")
    }
}
