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
