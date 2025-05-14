%import textio

main {
    struct MNode {
        bool flag
        ^^MNode next
    }

    sub func(^^MNode pointer) -> ^^MNode {
        cx16.r0++
        return pointer.next
    }

    sub start() {
        ^^MNode[5] @shared nodes

        nodes[0] = MNode()
        nodes[1] = MNode()
        nodes[2] = MNode()
        nodes[3] = MNode()
        nodes[4] = MNode()

        txt.print_uw(nodes[0])
        txt.spc()
        txt.print_uw(nodes[1])
        txt.spc()
        txt.print_uw(nodes[2])
        txt.spc()
        txt.print_uw(nodes[3])
        txt.spc()
        txt.print_uw(nodes[4])
        txt.nl()

        ^^MNode mn1 = MNode()
        mn1 = func(mn1)

        ^^thing.Node n1 = thing.Node()
        n1 = thing.func(n1)
    }
}

thing {
    struct Node {
        bool flag
        ^^Node next
    }

    sub func(^^Node pointer) -> ^^Node {
        cx16.r0++
        return pointer.next
    }

}
