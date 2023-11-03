%import syslib
%import textio
%zeropage basicsafe

main {
    sub start() {
        uword[] wordarray = [0,1,2,3,4,5,6,7,8,9]
        ubyte n = 5
        n = lsb(wordarray[n])
        n = wordarray[n] as ubyte
        test(wordarray[n] as ubyte,wordarray[n] as ubyte,0)
        test(lsb(wordarray[n]), lsb(wordarray[n]),0)
    }

    asmsub test(ubyte a1 @A, ubyte a2 @X, ubyte a3 @Y) {
        %asm {{
            phy
            phx
            jsr  txt.print_ub
            jsr  txt.spc
            pla
            jsr  txt.print_ub
            jsr  txt.spc
            pla
            jsr  txt.print_ub
            jsr  txt.nl
            rts
        }}
    }
}
