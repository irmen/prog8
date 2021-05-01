%import textio
%import diskio
%zeropage basicsafe

main {

    sub start() {

        txt.print(diskio.status(8))
        txt.nl()
        txt.print(diskio.status(9))
        txt.nl()
;        txt.print(diskio.status(10))
;        txt.nl()
;        txt.print(diskio.status(11))
;        txt.nl()

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
