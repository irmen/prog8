%zeropage basicsafe
%option no_sysinit, romable

main {
    sub start() {
        uword @shared pointer = $4000
        ubyte @shared size = 42

        for cx16.r0L in 5 to size {
            @(pointer)++
            @(pointer)--
        }
    }
}
