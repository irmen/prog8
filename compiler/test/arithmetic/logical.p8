%import textio
%zeropage basicsafe

main  {

    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 128
    }

    sub ffalse(ubyte arg) -> ubyte {
        arg++
        return 0
    }

    sub start() {
        ubyte ub1 = 2
        ubyte ub2 = 4
        ubyte ub3 = 8
        ubyte ub4 = 0
        ubyte bvalue

        txt.print("const not 0: ")
        txt.print_ub(not 129)
        txt.nl()
        txt.print("const not 1: ")
        txt.print_ub(not 0)
        txt.nl()
        txt.print("const inv 126: ")
        txt.print_ub(~ 129)
        txt.nl()
        txt.print("const inv 255: ")
        txt.print_ub(~ 0)
        txt.nl()
        bvalue = 129
        txt.print("bitwise inv 126: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()
        bvalue = 0
        txt.print("bitwise inv 255: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()

        txt.print("bitwise or  14: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4)
        txt.nl()
        txt.print("bitwise or 142: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4 | 128)
        txt.nl()
        txt.print("bitwise and  0: ")
        txt.print_ub(ub1 & ub2 & ub3 & ub4)
        txt.nl()
        txt.print("bitwise and  8: ")
        txt.print_ub(ub3 & ub3 & 127)
        txt.nl()
        txt.print("bitwise xor 14: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ ub4)
        txt.nl()
        txt.print("bitwise xor  6: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ 8)
        txt.nl()
        txt.print("bitwise not 247: ")
        txt.print_ub(~ub3)
        txt.nl()
        txt.print("bitwise not 255: ")
        txt.print_ub(~ub4)
        txt.nl()

        txt.print("not 0: ")
        bvalue = 3 * (ub4 | not (ub3 | ub3 | ub3))
        txt.print_ub(bvalue)
        if 3*(ub4 | not (ub1 | ub1 | ub1))
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 0: ")
        bvalue = not ub3
        txt.print_ub(bvalue)
        if not ub1
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 1: ")
        bvalue = not ub4
        txt.print_ub(bvalue)
        if not ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        bvalue = bvalue and 128
        txt.print("bvl 1: ")
        txt.print_ub(bvalue)
        if bvalue and 128
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and 64
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and 64
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and ftrue(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ub4
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ub4
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ffalse(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ffalse(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ub4
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub4 or ub4 or ub1
        txt.print_ub(bvalue)
        if ub4 or ub4 or ub1
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ftrue(99)
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ffalse(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ffalse(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4 xor true
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4 xor true
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ftrue(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
    }
}
