%import math
%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword v0 = 4000
        uword v1 = 60000

        for cx16.r1 in 65400 to 65535 {
            txt.print_uw(cx16.r1)
            txt.spc()
            txt.spc()
            txt.print_uw(math.lerpw(v0, v1, cx16.r1))
            txt.nl()
        }
    }
}
