%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword result = callfar(0, $4000, 9999)
        txt.print_uw(result)
    }
}

routine $4000 {
    %option force_output
    sub subroutine(uword arg) -> uword {
        return 11111+arg
    }
}
