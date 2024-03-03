%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        cx16.r0L = true
        cx16.r1L = false

        bool @shared bzz = 1
        ubyte @shared ubb = true

        bool @shared bb1, bb2
        bb1 = 0
        bb2 = 44
        bb1 = cx16.r0L

        bb2 = bb1 and cx16.r0L
        cx16.r0L = bb1 ^ cx16.r0L

        ; bool[3] barr1 = 42
        byte[3] @shared sbarr1 = true
        ubyte[3] @shared ubarr1 = true
        ubyte[3] @shared ubarr2 = bb2

        bool[] @shared boolarray = [1,0]
        bool[] @shared boolarray2 = [42,0,false]
        byte[] @shared sba = [true, false]
        byte[] @shared sba2 = [true, false, 42]
        ubyte[] @shared uba = [true, false]
        ubyte[] @shared uba2 = [true, false, 42]

        if cx16.r0L >= 44 and not cx16.r0L
            cx16.r0L++

        txt.print_ubhex(bb1, 1)
        txt.print_ubhex(bb2, 42)
        txt.print_ubhex(bb2, cx16.r0L)

        if cx16.r0L {
            cx16.r0L++
        }

        if cx16.r0 {
            cx16.r0L++
        }

        kapoof()
        kapoof2()
    }

    sub kapoof() -> bool {
        cx16.r0L++
        return cx16.r0L
    }

    sub kapoof2() -> ubyte {
        cx16.r0L++
        return cx16.r0L==0
    }
}

