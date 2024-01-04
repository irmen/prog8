%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared rnr = $a0
        txt.print_ub(rnr>=$33)
        txt.print_ub(rnr>=$66)
        txt.print_ub(rnr>=$99)
        txt.print_ub(rnr>=$cc)
        txt.nl()

        ubyte wordNr = (rnr >= $33) as ubyte + (rnr >= $66) as ubyte + (rnr >= $99) as ubyte + (rnr >= $CC) as ubyte
        txt.print_uw(wordNr)
        txt.nl()
        wordNr = 100 - (rnr >= $33) - (rnr >= $66) - (rnr >= $99) - (rnr >= $CC)
        txt.print_uw(wordNr)
        txt.nl()
    }
}
