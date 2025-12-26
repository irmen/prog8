main {
    sub start() {
        alias func1 = actualfunc
        alias func2 = mkword
        alias func3 = func1
        alias func4 = func2

        ; all wrong:
        func1(1,2)
        func1()
        func2(1,2,3,4)
        func2()
        func3()
        func4()

        ; all ok:
        func1(1)
        cx16.r0 = func2(1,2)
        func3(1)
        cx16.r0 = func4(1,2)

        sub actualfunc(ubyte a) {
            a++
        }
    }
}
