%import textio
%zeropage dontuse

main {
    sub start() {
        ubyte @shared ubb = 99
        uword @shared uww = 12345
        ubyte[200] @shared barr
        uword @shared ptr = memory("data", $2000, 0)

        %breakpoint

        txt.print_uwhex(sys.progend(), true)
    }
}
