main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        cx16.r1 = l1.s^^
    }
}

; TODO also fix the compilation error in re.p8
