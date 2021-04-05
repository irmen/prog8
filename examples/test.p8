%import textio
%import floats
%zeropage floatsafe

main {

    sub start() {
        float f1 = 9.9999
        float f2 = 8.8888
        float f3 = 0.1111

        uword fs

        %asm {{
            phx
            lda  #<f1
            ldy  #>f1
            jsr  floats.MOVFM
            jsr  floats.NEGOP
            jsr  floats.FOUT
            sta  fs
            sty  fs+1
            plx
        }}

        txt.print_uwhex(fs,1)
        txt.nl()
        txt.print(fs)
        txt.nl()

        txt.print("ok!\n")
        sys.wait(2*60)
    }
}
