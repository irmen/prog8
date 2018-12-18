%import c64utils
%import mathlib
%option enable_floats


~ main {

    sub start()  {

        byte v1
        byte v2

        c64scr.print_byte(X)
        c64.CHROUT('\n')

        v1 = 100
        v2 = 127
        if v1==v2
            c64.STROUT("error in 100==127!\n")
        else
            c64.STROUT("ok: 100 not == 127\n")

        if v1!=v2
            c64.STROUT("ok: 100 != 127\n")
        else
            c64.STROUT("error in 100!=127!\n")

        if v1<v2
            c64.STROUT("ok: 100 < 127\n")
        else
            c64.STROUT("error in 100<127!\n")

        if v1<=v2
            c64.STROUT("ok: 100 <= 127\n")
        else
            c64.STROUT("error in 100<=127!\n")

        if v1>v2
            c64.STROUT("error in 100>127!\n")
        else
            c64.STROUT("ok: 100 is not >127\n")

        if v1>=v2
            c64.STROUT("error in 100>=127!\n")
        else
            c64.STROUT("ok: 100 is not >=127\n")


        v1 = 125
        v2 = 22
        if v1==v2
            c64.STROUT("error in 125==22!\n")
        else
            c64.STROUT("ok: 125 not == 22\n")

        if v1!=v2
            c64.STROUT("ok: 125 != 22\n")
        else
            c64.STROUT("error in 125!=22!\n")

        if v1<v2
            c64.STROUT("error in 125<22!\n")
        else
            c64.STROUT("ok: 125 is not < 22\n")

        if v1<=v2
            c64.STROUT("error in 125<=22!\n")
        else
            c64.STROUT("ok: 125 is not <= 22\n")

        if v1>v2
            c64.STROUT("ok: 125 > 22\n")
        else
            c64.STROUT("error in 125>22!\n")

        if v1>=v2
            c64.STROUT("ok: 125 >= 22\n")
        else
            c64.STROUT("error in 125>=22!\n")

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

        c64scr.print_byte(X)
        c64.CHROUT('\n')

    }

}
