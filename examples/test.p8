%option enable_floats

main {
    sub start() {
        cx16.r1 = select1()
        cx16.r2 = select2()
        cx16.r3 = select3()
        cx16.r4 = select4()
        cx16.r5 = select5()
    }

    sub select1() -> uword {
        cx16.r0L++
        return 2000
    }

    sub select2() -> str {
        cx16.r0L++
        return 2000
    }

    sub select3() -> ^^ubyte {
        cx16.r0L++
        return 2000
    }

    sub select4() -> ^^bool {
        cx16.r0L++
        return 2000
    }

    sub select5() -> ^^float {
        cx16.r0L++
        return 2000
    }
}
