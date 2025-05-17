main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()  ; TODO fix unused var removal
        l1.s[2] = 1
    }
}

/*
main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List @shared l1 = List()
        cx16.r0=  l1.s[2]
        ;l1.s[l1.n] = 1
        ;l1.s[0] = 2
    }
}
*/
