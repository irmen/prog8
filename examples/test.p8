%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared flag

        cx16.r1=9999
        flag = test(42)
        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r1, flag = test3()
    }

    romsub $8000 = test(ubyte arg @A) -> bool @Pc
    romsub $8002 = test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc
    romsub $8003 = test3() -> uword @R1, bool @Pc
}
