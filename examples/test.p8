%import textio
%zeropage basicsafe

main {
    struct Node {
        bool flag
        ^^uword uptr
    }
    sub start() {
        ^^Node @shared n = [true, 4000]

        ; expected output: 12345 9999

        pokew(4000 + 3*2, 12345)
        txt.print_uw(n.uptr[3])
        txt.spc()

        n.uptr[3] = 9999
        txt.print_uw(n.uptr[3])
        txt.nl()

        ; expected output: 9999 11111
        ^^uword @shared normalptr = 4000
        txt.print_uw(normalptr[3])
        txt.spc()
        normalptr[3] = 11111
        txt.print_uw(normalptr[3])
        txt.nl()

        ;sys.poweroff_system()
    }
}
