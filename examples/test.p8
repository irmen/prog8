%import textio

main {

    sub start() {

        %asm {{

        lda  $d020
        ldy  $d021
        sta  $d020
        sty  $d021
        lda  $d020
        ldy  $d021
        sta  $d020
        sty  $d021


        lda $d020
        sta $d020
        lda $d020
        sta $d020
        lda $d020
        sta $d020
        lda $d020
        sta $d020
        sta $d020
        sta $d020
        sta $d020
        sta $d020
        sta $d020
        sta $d020

        lda $c020
        sta $c020
        lda $c020
        sta $c020
        lda $c020
        sta $c020
        lda $c020
        sta $c020
        sta $c020
        sta $c020
        sta $c020
        sta $c020
        sta $c020

        }}

        repeat {
        }
    }
}
