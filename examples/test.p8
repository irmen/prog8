%import c64utils

~ main {

    sub start() {

        foo(1)
        foo2(20)
        bar(2,3)
        baz(3333)
        baz(-3333)

    }

    sub foo(ubyte arg) {
        A=arg
    }

    sub foo2(byte arg) {
        A=33
    }

    sub bar(ubyte arg1, ubyte arg2) {
        A=arg1
    }

    sub baz(word arg) {
        A=lsb(arg)
    }
}
