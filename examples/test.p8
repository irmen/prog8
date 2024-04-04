%zeropage basicsafe
%option no_sysinit

main {
    romsub $2000 = func1() clobbers(X) -> ubyte @A, word @R0, byte @R1
    romsub $3000 = func2() clobbers(X) -> ubyte @A, uword @R0, uword @R1
    romsub $4000 = func3() clobbers(X) -> ubyte @R0

    sub start() {
        bool flag
        void cbm.GETIN()
        flag, cx16.r1L = cbm.GETIN()

        void, cx16.r0s, cx16.r1sL = func1()
        void, cx16.r2, cx16.r1 = func2()
        cx16.r0L = func3()
        cx16.r0H = func3()

        cx16.r0 = readblock()
    }

    sub readblock() -> uword {
        return cx16.MACPTR(0, 2, true)         ; TODO compiler error (number of return values)
    }
}
