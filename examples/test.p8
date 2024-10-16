%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte v0
        ubyte v1
        ubyte v2
        ubyte v3
        v0 = v1 = v2 = 99
        for v3 in 10 to 20 {
            cx16.r0L++
        }
    }
}
