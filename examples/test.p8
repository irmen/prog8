%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1 = 555555
        long @shared lv2 = -444444
        long @shared lv3 = 999999
        long @shared lv4 = lv1

        cmp(lv1, lv2)
        if_z
            txt.print("cmp 1a: zero\n")
        else
            txt.print("cmp 1a: not zero\n")

        cmp(lv1, lv2)
        if_neg
            txt.print("cmp 1b: neg\n")
        else
            txt.print("cmp 1b: not neg\n")

        cmp(lv1, lv2)
        if_cc
            txt.print("cmp 1c: carry clear\n")
        else
            txt.print("cmp 1c: carry set\n")
        cmp(lv1, lv4)
        if_z
            txt.print("cmp 1d: zero\n")
        else
            txt.print("cmp 1d: not zero\n")

        cmp(lv1, lv3)
        if_z
            txt.print("cmp 2a: zero\n")
        else
            txt.print("cmp 2a: not zero\n")

        cmp(lv1, lv3)
        if_neg
            txt.print("cmp 2b: neg\n")
        else
            txt.print("cmp 2b: not neg\n")
        cmp(lv1, lv3)
        if_cc
            txt.print("cmp 2c: carry clear\n")
        else
            txt.print("cmp 2c: carry set\n")

    }
}
