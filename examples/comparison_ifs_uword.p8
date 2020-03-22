%import c64utils
%zeropage basicsafe


main {

    sub start()  {

        uword v1
        uword v2

        v1 = 100
        v2 = 64444
        if v1==v2
            c64scr.print("error in 100==64444!\n")
        else
            c64scr.print("ok: 100 not == 64444\n")

        if v1!=v2
            c64scr.print("ok: 100 != 64444\n")
        else
            c64scr.print("error in 100!=64444!\n")

        if v1<v2
            c64scr.print("ok: 100 < 64444\n")
        else
            c64scr.print("error in 100<64444!\n")

        if v1<=v2
            c64scr.print("ok: 100 <= 64444\n")
        else
            c64scr.print("error in 100<=64444!\n")

        if v1>v2
            c64scr.print("error in 100>64444!\n")
        else
            c64scr.print("ok: 100 is not >64444\n")

        if v1>=v2
            c64scr.print("error in 100>=64444!\n")
        else
            c64scr.print("ok: 100 is not >=64444\n")


        v1 = 5555
        v2 = 322
        if v1==v2
            c64scr.print("error in 5555==322!\n")
        else
            c64scr.print("ok: 5555 not == 322\n")

        if v1!=v2
            c64scr.print("ok: 5555 != 322\n")
        else
            c64scr.print("error in 5555!=322!\n")

        if v1<v2
            c64scr.print("error in 5555<322!\n")
        else
            c64scr.print("ok: 5555 is not < 322\n")

        if v1<=v2
            c64scr.print("error in 5555<=322!\n")
        else
            c64scr.print("ok: 5555 is not <= 322\n")

        if v1>v2
            c64scr.print("ok: 5555 > 322\n")
        else
            c64scr.print("error in 5555>322!\n")

        if v1>=v2
            c64scr.print("ok: 5555 >= 322\n")
        else
            c64scr.print("error in 5555>=322!\n")

        v1 = 322
        v2 = 322
        if v1==v2
            c64scr.print("ok: 322 == 322\n")
        else
            c64scr.print("error in 322==322!\n")

        if v1!=v2
            c64scr.print("error in 322!=322!\n")
        else
            c64scr.print("ok: 322 is not != 322\n")

        if v1<v2
            c64scr.print("error in 322<322!\n")
        else
            c64scr.print("ok: 322 is not < 322\n")

        if v1<=v2
            c64scr.print("ok: 322 <= 322\n")
        else
            c64scr.print("error in 322<=322!\n")

        if v1>v2
            c64scr.print("error in 322>322!\n")
        else
            c64scr.print("ok: 322 is not > 322\n")

        if v1>=v2
            c64scr.print("ok: 322 >= 322\n")
        else
            c64scr.print("error in 322>=322!\n")

        check_eval_stack()
    }

    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }

}
