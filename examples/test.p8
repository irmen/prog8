main {
    ubyte a,b

    sub start() {
        func1()
        func2()
    }

    sub func1() {
        a = b
        %breakpoint
    }

    sub func2() {
        a = b
        %breakpoint!
    }
}
