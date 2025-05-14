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

        n1.flag = true
        n1.flag = n1.flag or boolfunc()
        txt.print_bool(n1.flag)
        txt.nl()
        n1.flag = false
        n1.flag = n1.flag and boolfunc()
        txt.print_bool(n1.flag)
        txt.nl()
    }

    sub boolfunc() -> bool {
        cx16.r0++
        txt.print("func()\n")
        return true
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
