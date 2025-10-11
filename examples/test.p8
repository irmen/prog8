%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1, lv2
        ubyte bb

        lv1 = func1()
        bb, lv1, lv2 = func2()
    }

    sub func1() -> long {
        cx16.r0++
        return 9999999
    }

    sub func2() -> ubyte, long, long {
        cx16.r0++
        return 1, 222222,333333
    }
}
