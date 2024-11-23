%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword amount
        uword zero = 0
        uword one = 1
        uword hundred = 100
        uword two55 = 255
        uword two56 = 256
        uword two57 = 257
        uword thousand = 1000
        uword maximum = 65535
        const long maxxx = 65536

        txt.print("expected:\n 0, 1, 100, 255, 256, 257, 1000, 65535, 999\n\n")

        repeat zero {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat one {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat hundred {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat two55 {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat two56 {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat two57 {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat thousand {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=0
        repeat maximum {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

        amount=999
        repeat maxxx {
            amount++
        }
        txt.print_uw(amount)
        txt.nl()

    }
}
