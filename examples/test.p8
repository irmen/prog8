%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared bytevar
        uword @shared wordvar
        bool flag

        wordvar, bytevar, void = test4()
        if_cs
            txt.print("true! ")
        else
            txt.print("false! ")

        txt.print_uwhex(wordvar, true)
        txt.spc()
        txt.print_ub(bytevar)
        txt.nl()
    }

    asmsub test4() -> uword @AY, ubyte @X, bool @Pc {
        %asm {{
            lda  #<$11ee
            ldy  #>$11ee
            ldx  #42
            clc
            rts
        }}
    }
}
