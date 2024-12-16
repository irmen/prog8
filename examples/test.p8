%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[] @split targetcolors = [$f00] * 16
        uword[] @nosplit xtargetcolors = [$f00] * 16

        txt.print_uwhex(&targetcolors, true)
        txt.nl()
        txt.print_uwhex(&<targetcolors, true)
        txt.nl()
        txt.print_uwhex(&>targetcolors, true)
        txt.nl()
    }
}
