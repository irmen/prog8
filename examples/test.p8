%import math
%import textio
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        if cx16.r0sL > 10
            cx16.r1L++
        if cx16.r0sL >= 10
            cx16.r1L++
        if cx16.r0sL < 10
            cx16.r1L++
        if cx16.r0sL <= 10
            cx16.r1L++
    }

    sub ub() -> ubyte {
        cx16.r0++
        return cx16.r0L
    }

    sub sb() -> byte {
        cx16.r0++
        return cx16.r0sL
    }
}

