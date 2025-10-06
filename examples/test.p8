%import textio
%zeropage basicsafe

main {
    sub start() {
        long lv1
        long lv2

        txt.print("<\n")
        lv1 = $77777777
        lv2 = $55555555
        txt.print_bool(lv1 < lv2)
        txt.spc()
        if lv1 < lv2
            txt.print("error1 ")
        else
            txt.print("ok1 ")
        txt.nl()

        lv1 = $55555555
        lv2 = $55555555
        txt.print_bool(lv1 < lv2)
        txt.spc()
        if lv1 < lv2
            txt.print("error2 ")
        else
            txt.print("ok2 ")
        txt.nl()

        lv1 = $44444444
        lv2 = $55555555
        txt.print_bool(lv1 < lv2)
        txt.spc()
        if lv1 < lv2
            txt.print("ok3 ")
        else
            txt.print("error3 ")
        txt.nl()




/*
        txt.print("<=\n")
        lv1 = $77777777
        lv2 = $55555555
        txt.print_bool(lv1 <= lv2)
        txt.spc()
        if lv1 <= lv2
            txt.print("error1 ")
        else
            txt.print("ok1 ")
        txt.nl()

        lv1 = $55555555
        lv2 = $55555555
        txt.print_bool(lv1 <= lv2)
        txt.spc()
        if lv1 <= lv2
            txt.print("ok2 ")
        else
            txt.print("error2 ")
        txt.nl()

        lv1 = $44444444
        lv2 = $55555555
        txt.print_bool(lv1 <= lv2)
        txt.spc()
        if lv1 <= lv2
            txt.print("ok3 ")
        else
            txt.print("error3 ")
        txt.nl()
*/


/*
        txt.print(">\n")
        lv1 = $77777777
        lv2 = $55555555
        txt.print_bool(lv1 > lv2)
        txt.spc()
        if lv1 > lv2
            txt.print("ok1 ")
        else
            txt.print("error1 ")
        txt.nl()

        lv1 = $55555555
        lv2 = $55555555
        txt.print_bool(lv1 > lv2)
        txt.spc()
        if lv1 > lv2
            txt.print("error2 ")
        else
            txt.print("ok2 ")
        txt.nl()

        lv1 = $44444444
        lv2 = $55555555
        txt.print_bool(lv1 > lv2)
        txt.spc()
        if lv1 > lv2
            txt.print("error3 ")
        else
            txt.print("ok3 ")
        txt.nl()
*/


        txt.print(">=\n")
        lv1 = $77777777
        lv2 = $55555555
        txt.print_bool(lv1 >= lv2)
        txt.spc()
        if lv1 >= lv2
            txt.print("ok1 ")
        else
            txt.print("error1 ")
        txt.nl()

        lv1 = $55555555
        lv2 = $55555555
        txt.print_bool(lv1 >= lv2)
        txt.spc()
        if lv1 >= lv2
            txt.print("ok2 ")
        else
            txt.print("error2 ")
        txt.nl()

        lv1 = $44444444
        lv2 = $55555555
        txt.print_bool(lv1 >= lv2)
        txt.spc()
        if lv1 >= lv2
            txt.print("error3 ")
        else
            txt.print("ok3 ")
        txt.nl()
    }
}
