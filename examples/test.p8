%import textio

main {
    sub start() {
        ^^thing.Node n1 = thing.Node(true, -42, 0)
        ^^thing.Node n2 = thing.Node(false, 99, 0)
        n1.next = n2
        n2.next = n1
        info(n1)

        word[] @nosplit values = [111,222,-999,-888]

        stringinfo("hello")
        arrayinfo(values)
        arrayinfo(&values[2])
    }

    sub info(^^thing.Node node) {
        txt.print_uw(node)
        txt.nl()
        txt.print_bool(node.flag)
        txt.nl()
        txt.print_b(node.value)
        txt.nl()

        node++
        txt.print_uw(node)
        txt.nl()
        txt.print_bool(node.flag)
        txt.nl()
        txt.print_b(node.value)
        txt.nl()
    }

    sub stringinfo(^^ubyte message) {
        txt.print_uw(message)
        txt.spc()
        txt.print(message)
        txt.spc()
        do {
            txt.chrout(message^^)
            message++
        } until message^^==0
        txt.nl()
    }

    sub arrayinfo(^^word valueptr) {
        txt.print_uw(valueptr)
        txt.spc()
        txt.print_w(valueptr^^)
        txt.nl()
        valueptr++
        txt.print_uw(valueptr)
        txt.spc()
        txt.print_w(valueptr^^)
        txt.nl()
    }
}

thing {
    struct Node {
        bool flag
        byte value
        ^^Node next
    }
}
