%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword @shared uw = 50000
        word @shared sw = uw as word
        float @shared f = 65536

        uw = 0
        txt.print_f(uw as float)
        txt.nl()
        uw = 1
        txt.print_f(uw as float)
        txt.nl()
        uw = 9999
        txt.print_f(uw as float)
        txt.nl()
        uw = 32767
        txt.print_f(uw as float)
        txt.nl()
        uw = 32768
        txt.print_f(uw as float)
        txt.nl()
        uw = 50000
        txt.print_f(uw as float)
        txt.nl()
        uw = 65535
        txt.print_f(uw as float)
        txt.nl()

    }
}
