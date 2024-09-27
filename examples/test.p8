%import textio
%import verafx
%zeropage basicsafe
%option no_sysinit

main {

    word @shared w1 = -30
    word @shared w2 = -40
    uword @shared uw1 = 9999
    uword @shared uw2 = 4

    sub start() {
        cx16.r0 = 12345
        txt.print_uw(verafx.mult16(uw1, uw2))
        txt.spc()
        txt.print_uw(uw1 * uw2)
        txt.nl()
    }
}
