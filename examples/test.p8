%import floats
%import textio

main {
    struct Node {
        bool bb
        float f
        word w
        ^^Node next
    }

    sub start() {
        ^^Node n1 = Node(false, 1.1, 1111, 0)
        ^^Node n2 = Node(false, 2.2, 2222, 0)
        ^^Node n3 = Node(true, 3.3, 3333, 0)

        n1.next = n2
        n2.next = n3
        n3.next = 0

        txt.print_bool(n1.bb)
        txt.spc()
        txt.print_f(n1.f)
        txt.spc()
        txt.print_uw(n1.next)
        txt.nl()

        txt.print_bool(n2.bb)
        txt.spc()
        txt.print_f(n2.f)
        txt.spc()
        txt.print_uw(n2.next)
        txt.nl()

        txt.print_bool(n3.bb)
        txt.spc()
        txt.print_f(n3.f)
        txt.spc()
        txt.print_uw(n3.next)
        txt.nl()

        n1.next.next.bb = false
        n1.next.next.f = 42.999999

        txt.print_bool(n1.next.next.bb)
        txt.spc()
        txt.print_f(n1.next.next.f)
        txt.nl()
    }
}
