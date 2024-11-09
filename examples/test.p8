%import textio
%zeropage basicsafe


main {
    sub start() {
        txt.print_ubbin(c128.getbanks(), true)
        txt.spc()
        txt.print_ub(@($4000))
        txt.spc()
        txt.print_ub(@($8000))
        txt.nl()

        @($4000)++
        @($8000)++
        txt.print_ub(@($4000))
        txt.spc()
        txt.print_ub(@($8000))
        txt.nl()

        c128.banks(0)
        txt.print_ubbin(c128.getbanks(), true)
        txt.spc()
        @($4000)++
        @($8000)++
        txt.print_ub(@($4000))
        txt.spc()
        txt.print_ub(@($8000))
        txt.nl()
    }
}
