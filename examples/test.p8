%import textio
%zeropage basicsafe
%option no_sysinit

main {
    struct Node {
        uword data1
        uword data2
        ^^Node next
    }

    sub start() {
        ^^Node n1 = [1111, 1333,0]
        ^^Node n2 = [2222, 2333, 0]
        ^^Node n3 = [3333, 3444, 0]

        n1.next = n2
        n2.next = n3

        txt.print_uw(n1.next.next)
        txt.spc()
        txt.print_uw(n1.next.next.data2)
        txt.nl()
    }
}

