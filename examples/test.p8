%import textio
%import floats
%zeropage floatsafe

main {

    sub start() {
        ;cx16.rombank(4)
        float f1 = 9.9999
        float f2 = 8.8888
        float f3 = 0.1111

        f3=cos(f3)

        floats.print_f(f1)
        txt.nl()
        floats.print_f(f2)
        txt.nl()
        floats.print_f(f3)
        txt.nl()
        f3 = cos(f3)
        floats.print_f(f3)

        ;cx16.rombank(0)

        txt.print("ok!\n")

        sys.wait(2*60)
    }
}
