%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        if cx16.r0L==0 {
            uword[] scores = [10, 25, 50, 100]      ; can never clear more than 4 lines at once
            txt.print_uw(scores[1])
            txt.nl()
        }
    }

}
