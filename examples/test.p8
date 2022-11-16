%import textio
%zeropage basicsafe

main {

alsostart:
    sub start() {

    internalstart:
        ubyte fact = 10
        uword ww = 1<<fact
        txt.print_uw(ww)
        txt.nl()
        ww++
        ww = 1<<fact
        txt.print_uw(ww)
        txt.nl()
        txt.nl()

        txt.print_uwhex(start, true)
        txt.nl()
        txt.print_uwhex(alsostart, true)
        txt.nl()
        txt.print_uwhex(internalstart, true)
        txt.nl()
        txt.print_uwhex(startend, true)
        txt.nl()
        txt.print_uwhex(internalend, true)
        txt.nl()
    internalend:
    }

startend:

}
