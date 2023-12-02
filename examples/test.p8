%zeropage basicsafe
%import floats
%import textio
%option no_sysinit

main {
    sub start() {
        floats.print_f(0.0)
        txt.nl()
        floats.print_f(1.0)
        txt.nl()
        floats.print_f(11111.0)
        txt.nl()
        floats.print_f(1e10)
        txt.nl()
        floats.print_f(1.234)
        txt.nl()
        floats.print_f(111.234)
        txt.nl()
        floats.print_f(-111.234)
        txt.nl()
        floats.print_f(-111.234)
        txt.nl()

        uword zz
        const ubyte check = 99

        when zz {
            1,2,check -> {
                cx16.r0++
            }
            9999 -> {
                cx16.r0++
            }
            else -> {
                cx16.r0++
            }
        }
    }
}
