%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[] array = [
            11,
            22,
            33,
            44,
        ]
        for cx16.r0L in [1,2,3,4,] {
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0L in array {
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
    }
}
