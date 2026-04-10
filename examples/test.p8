%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        %asm {{
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            stz  cx16.VERA_DATA0
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            lda  cx16.VERA_DATA1
            stz  cx16.VERA_DATA0
        }}
    }
}
