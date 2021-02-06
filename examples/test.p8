%import textio
%import palette
%import syslib
%zeropage basicsafe

main {

    sub start() {
        ubyte lower2_x_bits=1
        ubyte cbits4
        ubyte operand

        cbits4 &= operand
        cbits4 |= operand

        ;cbits4 &= gfx2.plot.mask4c[lower2_x_bits]       ; TODO why lda..and instead of  and mask,y?
        ;cbits4 |= colorbits[lower2_x_bits]              ; TODO why lda..ora instead of  ora mask,y?

    }
}
