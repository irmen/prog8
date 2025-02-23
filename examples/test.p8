%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {
    ubyte[100] @shared array1
    ubyte[100] @shared array2 = [42] *100

    sub start() {
        uword @shared pointer = $4000
        ubyte @shared size = 42

        for cx16.r0L in 5 to size {
            @(pointer)++
            @(pointer)--
        }
    }
}
