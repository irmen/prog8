%import textio

main {
    struct Node {
        ^^ubyte s
    }

    sub start() {
        ^^Node[] nodes = [ Node(), Node(), Node() ]
        ^^Node n1 = Node()
        txt.print_uw(n1)
        txt.print_uw(nodes[0])
        txt.print_uw(nodes[1])
        txt.print_uw(nodes[2])
    }
}
