%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1 = 12345678
        long @shared lv2same = 12345678
        long @shared lv2different = 999999

        if lv1==0
            txt.print("wrong1\n")

        if lv1==0
            txt.print("wrong2\n")
        else
            txt.print("ok2\n")

        if lv1!=0
            txt.print("ok3\n")

        if lv1!=0
            txt.print("ok4\n")
        else
            txt.print("wrong4\n")


        if lv1==999999
            txt.print("wrong5\n")

        if lv1==999999
            txt.print("wrong6\n")
        else
            txt.print("ok6\n")

        if lv1!=999999
            txt.print("ok7\n")

        if lv1!=999999
            txt.print("ok8\n")
        else
            txt.print("wrong8\n")

        if lv1==12345678
            txt.print("ok9\n")

        if lv1==12345678
            txt.print("ok10\n")
        else
            txt.print("wrong10\n")

        if lv1!=12345678
            txt.print("wrong11\n")

        if lv1!=12345678
            txt.print("wrong12\n")
        else
            txt.print("ok12\n")



        if lv1==lv2same
            txt.print("ok13\n")

        if lv1==lv2same
            txt.print("ok14\n")
        else
            txt.print("wrong14\n")

        if lv1!=lv2same
            txt.print("wrong15\n")

        if lv1!=lv2same
            txt.print("wrong16\n")
        else
            txt.print("ok16\n")


        if lv1==lv2different
            txt.print("wrong17\n")

        if lv1==lv2different
            txt.print("wrong18\n")
        else
            txt.print("ok18\n")

        if lv1!=lv2different
            txt.print("ok19\n")

        if lv1!=lv2different
            txt.print("ok20\n")
        else
            txt.print("wrong20\n")
    }
}
