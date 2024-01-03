%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        sub1()
    }

    sub sub1() {
        cx16.r0++
        sub2()
    }
    sub sub2() {
        cx16.r0++
        sub3()
    }
    sub sub3() {
        cx16.r0++
        sys.exit(42)
    }
}
