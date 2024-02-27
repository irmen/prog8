%import math
%import textio
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        if cx16.r0s > 0
            cx16.r1L++
        if cx16.r0s <= 0
            cx16.r1L++
    }
}

