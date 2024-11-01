%import math
%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        ubyte v1 = 100
        ubyte v2 = 200

        for cx16.r0L in 0 to 255 step 16 {
            txt.print_ub(math.lerp(v1, v2, cx16.r0L))
            txt.nl()
        }
        txt.print_ub(math.lerp(v1, v2, 255))
        txt.nl()
    }

}
