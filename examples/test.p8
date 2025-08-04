%import textio
%option no_sysinit
%zeropage basicsafe


main {
    struct Node {
        uword value
        bool flag
        ^^Node next
    }

    sub start() {
        ^^Node nptr = 30000
        ^^Node nptr2 = Node()
        ^^Node nptr3 = Node(9999, true, 12345)

        txt.print_bool(nptr2.flag)
        txt.spc()
        txt.print_bool(nptr3.flag)
        txt.spc()
        txt.print_uw(nptr2.next)
        txt.spc()
        txt.print_uw(nptr3.next)
    }
}
