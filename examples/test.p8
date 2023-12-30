%import textio
%zeropage dontuse

main {
    ubyte @shared a1 = 10
    ubyte @shared a2 = 20
    ubyte @shared x1 = 30
    ubyte @shared x2 = 40
    ubyte @shared zero = 0

    sub start () {
        txt.print("1a:\n")
        if calc_a1()<calc_x1() and calc_a2()<=calc_x2()
            txt.print("* 1a and ok\n")

        txt.print("\n1b:\n")
        if calc_a1()<calc_x1() and calc_a2()>calc_x2()
            txt.print("* 1b and fail\n")

        txt.print("\n1c:\n")
        if calc_a1()>calc_x1() and calc_a2()<=calc_x2()
            txt.print("* 1c and fail\n")

        txt.print("\n2a:\n")
        if calc_a1()<calc_x1() or calc_a2()<=calc_x2()
            txt.print("* 2a or ok\n")

        txt.print("\n2b:\n")
        if calc_a1()<calc_x1() or calc_a2()>calc_x2()
            txt.print("* 2b or ok\n")

        txt.print("\n3a:\n")
        if calc_a1()>calc_x1() or calc_a2()<=calc_x2()
            txt.print("* 3a or ok\n")

        txt.print("\n3b:\n")
        if calc_a1()>calc_x1() or calc_a2()>calc_x2()
            txt.print("* 3b or fail\n")

        txt.print("\n4a:\n")
        bool result = calc_a1()<calc_x1() or calc_a2()>calc_x2()
        txt.print_ub(result)
        txt.nl()
        txt.print("\n4b:\n")
        result = calc_a1()>=calc_x1() and calc_a2()>calc_x2()
        txt.print_ub(result)
        txt.nl()
        @($4000) &= 22

        txt.print("\n5a:\n")
        result = bool_true() or bool_false()
        txt.print("\n5b:\n")
        result = bool_true() and bool_false()
        txt.print("\n5c:\n")
        result = bool_false() and bool_true()
        txt.print("\n5d:\n")
        result = bool_false() xor bool_true()

        txt.print("augmented and shortcut:\n")
        bool @shared b1 = false
        cx16.r0++
        b1 = b1 and bool_true()
        txt.print("augmented and no shortcut:\n")
        b1 = true
        cx16.r0++
        b1 = b1 and bool_true()

        txt.print("augmented or shortcut:\n")
        b1 = true
        cx16.r0++
        b1 = b1 or bool_true()
        txt.print("augmented or no shortcut:\n")
        b1 = false
        cx16.r0++
        b1 = b1 or bool_true()
    }

    sub bool_true() -> bool {
        txt.print("bool_true\n")
        return true
    }
    sub bool_false() -> bool {
        txt.print("bool_false\n")
        return false
    }
    sub calc_a1() -> ubyte {
        txt.print("calc_a1\n")
        return a1+zero
    }
    sub calc_a2() -> ubyte {
        txt.print("calc_a2\n")
        return a2+zero
    }
    sub calc_x1() -> ubyte {
        txt.print("calc_x1\n")
        return x1+zero
    }
    sub calc_x2() -> ubyte {
        txt.print("calc_x2\n")
        return x2+zero
    }
}
