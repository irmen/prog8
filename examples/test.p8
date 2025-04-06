%import textio
%zeropage basicsafe

main {

    sub start()  {

        @($a000) = 123
        txt.print_ub(@($a000))
        txt.nl()

        cx16.push_rambank(10)
        txt.print_ub(@($a000))
        txt.nl()
        cx16.pop_rambank()

        txt.print_ub(@($a000))
        txt.nl()
    }


}
