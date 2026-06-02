%import textio
%zeropage basicsafe
%option no_sysinit
%encoding iso

main {
    ubyte @shared v1 = 10
    ubyte @shared v2 = 20

    sub start() {
        ubyte a
        a = get_two(1, 2)
        cx16.r0 = a
    }
    sub get_two(ubyte x, ubyte y @R0) -> ubyte {
        return v1
    }
}
