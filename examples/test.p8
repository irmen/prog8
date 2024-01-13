%zeropage basicsafe
%import textio

main {
    sub start() {
        bool @shared bb = func(true)
    }

    sub func(bool arg) -> bool {
        cx16.r0++
        return arg
    }
}

mainxxxxx {
    sub start() {
        ; const bool zz = true        ; TODO allow const bool;

        bool @shared b1 = true
        bool @shared b2 = false
        bool @shared b3 = true
        ubyte @shared ub

        ubyte[] ubarray = [false, true, false, true]        ; TODO should give type error
        bool[] boolarray = [false, true, false, true]

        b1 = b1 and b2
        b2 = b1 or b2
        b3 = b1 xor b2


        while b1
            cx16.r0++

        while ub!=0
            cx16.r0++

        b1 = returnbool()
        ub = ubarray[2]
        b1 = boolarray[2]

/*
        ub = b1 + 42
        b1 = b2 * 2

        b1 = b1 & b2
        b3 = b1 | b2
        b3 = b1 ^ b2

        b1 = ~b1
        ub = ~b1

        while b1==42        ; TODO should give type error
            cx16.r0++
*/

    }

    sub returnbool() -> bool {
        cx16.r0++
        return true
    }
}
