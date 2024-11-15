%import floats

main {
    sub start() {
        thing()
        thang()
        bool status
        cx16.r0L, status = extfunction(42, 11223, 999, 1.22, true)
        cx16.r0L, status = function(42, 11223, 999, 1.22, true)
        func1(42)
        func2(9999)
        func3(42,9999)
        func4(42,9999,12345)
    }

    sub thing() -> bool {
        cx16.r0++
        return true
    }

    sub thang() -> float {

    }

    sub func1(ubyte arg) {
        cx16.r0L +=arg
    }

    sub func2(uword arg) {
        cx16.r0 += arg
    }

    sub func3(ubyte arg1, uword arg2) {
        cx16.r0 += arg2
        cx16.r0L =+ arg1
    }

    sub func4(ubyte arg1, uword arg2, uword arg3) {
        cx16.r0L =+ arg1
        cx16.r0 += arg2
        cx16.r1 += arg3
    }

    extsub $2000 = extfunction(ubyte arg1 @A, uword arg2 @XY, uword arg3 @R0, float frac @FAC1, bool flag @Pc) -> ubyte @Y, bool @Pz

    asmsub function(ubyte arg1 @A, uword arg2 @XY, uword arg3 @R0, float frac @FAC1, bool flag @Pc) -> ubyte @Y, bool @Pz {
        %asm {{
            rts
        }}
    }
}
