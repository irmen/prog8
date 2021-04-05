%import textio
%import floats
%zeropage dontuse

main {

    sub start() {
        float f1 = 9.9999
        float f2 = 8.8888
        float f3 = 0.1111

        %asm {{
            phx
            ldy  #<f1
            lda  #>f1
            jsr  $FE42
            jsr  $FE7B
            plx
        }}
        f3=cos(f3)

        floats.print_f(f1)
        txt.nl()
        floats.print_f(f2)
        txt.nl()
        floats.print_f(f3)
        txt.nl()
        f3 = cos(f3)
        floats.print_f(f3)

        txt.print("ok!\n")

        sys.wait(2*60)

    }
}
