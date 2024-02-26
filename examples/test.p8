%import math
%import textio
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        uword @shared wptr
        str buffer="    "

        ; TODO: these generate LARGE code
        while wptr!=&buffer
            cx16.r0L++
        while wptr==&buffer
            cx16.r0L++

        ; ... these are fine:
        while wptr!=cx16.r0
            cx16.r0L++
        while wptr==cx16.r0
            cx16.r0L++


        if ub() > 5
            cx16.r0L++

        if ub() < 5
            cx16.r0L++

        if sb() > 5
            cx16.r0L++

        if sb() < 5
            cx16.r0L++

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

