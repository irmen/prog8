%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        float f1

        floats.rndseedf(-1.2345)
        txt.spc()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.nl()

        floats.rndseedf(1.2345)
        txt.spc()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.nl()
    }
}
