main {
    sub start() {
        struct Node {
            ubyte weight
        }
        ^^Node nodes
        nodes^^.zzz1 = 99
        cx16.r0L = nodes^^.zzz2
        cx16.r0L = nodes[2].zzz3
    }
}
