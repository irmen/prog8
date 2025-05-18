main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        cx16.r1 = l1.s^^            ; TODO fix "undefined symbol" error (and fix the unit test too)
    }
}

; TODO also fix the compilation error in re.p8
