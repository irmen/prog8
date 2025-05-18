main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        cx16.r0=  l1.s[2]
        l1.s[10] = 2
    }
}
