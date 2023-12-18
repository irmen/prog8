%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        void diskio.f_open("does-not-exist")
        txt.print_ub(cbm.READST())
        txt.nl()
        cbm.CLEARST()
        txt.print_ub(cbm.READST())
        txt.nl()
    }
}
