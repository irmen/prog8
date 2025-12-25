main {
    sub start() {
        alias func = actualfunc
        func(1,2)       ; expected: "invalid number of arguments"  got: crash

        sub actualfunc(ubyte a) {
            a++
        }
    }
}
