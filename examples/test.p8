%import textio
%zeropage basicsafe

main {

    sub start() {

        uword x=22
        x*=320
        txt.print_uw(x)
        txt.nl()

        x=22
        x*=640
        txt.print_uw(x)
        txt.nl()

        ubyte a=1
        ubyte b=22

        x = (a*b)*640*a
        txt.print_uw(x)
        txt.nl()


    }
}
