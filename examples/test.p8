%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.r0L = main.dummy.variable
        cx16.r1L = main.dummy.array[1]
        cx16.r0++
    }

    sub dummy() {
        ubyte variable
        ubyte[] array = [1,2,3]

        cx16.r0++
    }
}
