main {
    struct List {
        ^^uword s
        ubyte n
        ^^List next
    }
    sub start() {
        ^^List l1 = List()
        ^^List l2 = List()
        l1.s[2] = 1
        l2.n=10

        ^^List l3 = List()
        cx16.r0L = l3.next.n
    }
}
