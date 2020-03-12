%import c64utils
%zeropage basicsafe


main {

    sub start()  {

        byte v1
        byte v2

        v1 = 100
        v2 = 127
        if v1==v2
            c64scr.print("error in 100==127!\n")
        else
            c64scr.print("ok: 100 not == 127\n")

        if v1!=v2
            c64scr.print("ok: 100 != 127\n")
        else
            c64scr.print("error in 100!=127!\n")

        if v1<v2
            c64scr.print("ok: 100 < 127\n")
        else
            c64scr.print("error in 100<127!\n")

        if v1<=v2
            c64scr.print("ok: 100 <= 127\n")
        else
            c64scr.print("error in 100<=127!\n")

        if v1>v2
            c64scr.print("error in 100>127!\n")
        else
            c64scr.print("ok: 100 is not >127\n")

        if v1>=v2
            c64scr.print("error in 100>=127!\n")
        else
            c64scr.print("ok: 100 is not >=127\n")


        v1 = 125
        v2 = 22
        if v1==v2
            c64scr.print("error in 125==22!\n")
        else
            c64scr.print("ok: 125 not == 22\n")

        if v1!=v2
            c64scr.print("ok: 125 != 22\n")
        else
            c64scr.print("error in 125!=22!\n")

        if v1<v2
            c64scr.print("error in 125<22!\n")
        else
            c64scr.print("ok: 125 is not < 22\n")

        if v1<=v2
            c64scr.print("error in 125<=22!\n")
        else
            c64scr.print("ok: 125 is not <= 22\n")

        if v1>v2
            c64scr.print("ok: 125 > 22\n")
        else
            c64scr.print("error in 125>22!\n")

        if v1>=v2
            c64scr.print("ok: 125 >= 22\n")
        else
            c64scr.print("error in 125>=22!\n")

        v1 = 22
        v2 = 22
        if v1==v2
            c64scr.print("ok: 22 == 22\n")
        else
            c64scr.print("error in 22==22!\n")

        if v1!=v2
            c64scr.print("error in 22!=22!\n")
        else
            c64scr.print("ok: 22 is not != 22\n")

        if v1<v2
            c64scr.print("error in 22<22!\n")
        else
            c64scr.print("ok: 22 is not < 22\n")

        if v1<=v2
            c64scr.print("ok: 22 <= 22\n")
        else
            c64scr.print("error in 22<=22!\n")

        if v1>v2
            c64scr.print("error in 22>22!\n")
        else
            c64scr.print("ok: 22 is not > 22\n")

        if v1>=v2
            c64scr.print("ok: 22 >= 22\n")
        else
            c64scr.print("error in 22>=22!\n")

        check_eval_stack()
    }


    sub check_eval_stack() {
        c64scr.print("stack x=")
        c64scr.print_ub(X)
        if X==255
            c64scr.print(" ok\n")
        else
            c64scr.print(" error!\n")
    }
}
