%import textio
%option no_sysinit
%zeropage basicsafe

main {
    ; TODO
    asmsub derp(bool flag @Pc) -> bool @Pc {
        %asm {{
            bcc  +
            lda  #'1'
            jmp  cbm.CHROUT
+           lda  #'0'
            jmp  cbm.CHROUT
        }}
    }
    sub start() {
        ubyte xx = 0
        ubyte yy=0
        void derp(xx+1)
        void derp(xx+42)
        void derp(xx-yy)
    }
}

