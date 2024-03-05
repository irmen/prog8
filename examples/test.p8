%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared x
        bool @shared leftmost

        if x>=10 and leftmost
            cx16.r0L++

        if x>=10 and not leftmost
            cx16.r0L++
    }
}

