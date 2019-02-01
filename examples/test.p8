%import c64utils

~ main {

    sub start() {

        foo(1)
        bar(1,2)
        baz(3333)
        bzaz(60000)
    }

    sub foo(byte arg) {
        byte local = arg
        A=44
    }

    sub bar(byte arg1, ubyte arg2) {
        byte local1 = arg1
        ubyte local2 = arg2
        A=44
    }

    sub baz(word arg) {
        word local=arg
        A=44
    }
    sub bzaz(uword arg) {
        uword local=arg
        A=44
    }

}

