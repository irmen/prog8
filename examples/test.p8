main {
    struct List {
        bool s
        ubyte n
    }
    sub start() {
        ^^List @shared l1 = List()
        l1.s[1] = 4444      ; TODO wrong error message, instead should give an error that you can't index a boolean (only uword or pointer)
        l1.s[1] = true      ; TODO should give an error that you can't index a boolean (only uword or pointer)
    }
}


/*

main {
    struct List {
        bool s
        ubyte n
    }
    sub start() {
        ^^List @shared l1 = List()      ; TODO gets removed as 'unused var' when not @shared
        l1.s[0] = true
    }
}
*/


/*
main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        l1.s[l1.n] = 1
    }
}
*/

/*
main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        l1.s[l1.n] = 1
        l1.s[0] = 2
    }
}
*/

