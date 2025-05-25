main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        ^^word @shared wptr

        cx16.r1 = l1.s^^
        cx16.r0 = l1.s[0]
        cx16.r2 = l1^^.s^^
        l1.s[0] = 4242
        cx16.r1 = l1.s^^

        cx16.r0s = wptr[0]
        cx16.r1s = wptr^^
        wptr[0] = 4242
    }

}
