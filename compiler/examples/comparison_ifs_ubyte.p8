%import c64utils
%option enable_floats


~ main {

    sub start()  {

        ubyte v1
        ubyte v2

        c64scr.print_ub(X)
        c64.CHROUT('\n')

        v1 = 100
        v2 = 200
        if v1==v2
            c64.STROUT("error in 100==200!\n")
        else
            c64.STROUT("ok: 100 not == 200\n")

        if v1!=v2
            c64.STROUT("ok: 100 != 200\n")
        else
            c64.STROUT("error in 100!=200!\n")

        if v1<v2
            c64.STROUT("ok: 100 < 200\n")
        else
            c64.STROUT("error in 100<200!\n")

        if v1<=v2
            c64.STROUT("ok: 100 <= 200\n")
        else
            c64.STROUT("error in 100<=200!\n")

        if v1>v2
            c64.STROUT("error in 100>200!\n")
        else
            c64.STROUT("ok: 100 is not >200\n")

        if v1>=v2
            c64.STROUT("error in 100>=200!\n")
        else
            c64.STROUT("ok: 100 is not >=200\n")


        v1 = 155
        v2 = 22
        if v1==v2
            c64.STROUT("error in 155==22!\n")
        else
            c64.STROUT("ok: 155 not == 22\n")

        if v1!=v2
            c64.STROUT("ok: 155 != 22\n")
        else
            c64.STROUT("error in 155!=22!\n")

        if v1<v2
            c64.STROUT("error in 155<22!\n")
        else
            c64.STROUT("ok: 155 is not < 22\n")

        if v1<=v2
            c64.STROUT("error in 155<=22!\n")
        else
            c64.STROUT("ok: 155 is not <= 22\n")

        if v1>v2
            c64.STROUT("ok: 155 > 22\n")
        else
            c64.STROUT("error in 155>22!\n")

        if v1>=v2
            c64.STROUT("ok: 155 >= 22\n")
        else
            c64.STROUT("error in 155>=22!\n")

        v1 = 22
        v2 = 22
        if v1==v2
            c64.STROUT("ok: 22 == 22\n")
        else
            c64.STROUT("error in 22==22!\n")

        if v1!=v2
            c64.STROUT("error in 22!=22!\n")
        else
            c64.STROUT("ok: 22 is not != 22\n")

        if v1<v2
            c64.STROUT("error in 22<22!\n")
        else
            c64.STROUT("ok: 22 is not < 22\n")

        if v1<=v2
            c64.STROUT("ok: 22 <= 22\n")
        else
            c64.STROUT("error in 22<=22!\n")

        if v1>v2
            c64.STROUT("error in 22>22!\n")
        else
            c64.STROUT("ok: 22 is not > 22\n")

        if v1>=v2
            c64.STROUT("ok: 22 >= 22\n")
        else
            c64.STROUT("error in 22>=22!\n")

        c64scr.print_ub(X)
        c64.CHROUT('\n')

    }

}
