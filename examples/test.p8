%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        uword xx=$ea31
        xx = lsb(xx)
        uword ww = plot(lsb(xx), msb(xx))
        ww=msb(ww)
        txt.print_uwhex(ww, true)
    }

    inline asmsub  plot(uword plotx @R0, uword ploty @R1) -> uword @AY{
        %asm {{
            lda  cx16.r0
            ldy  cx16.r1
            rts
        }}
    }


}
