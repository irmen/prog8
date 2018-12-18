%import c64utils
%import mathlib
%option enable_floats


~ main {

    sub start()  {

        word v1
        word v2

        c64scr.print_byte(X)
        c64.CHROUT('\n')

        v1 = 100
        v2 = 30333
        if v1==v2
            c64.STROUT("error in 100==30333!\n")
        else
            c64.STROUT("ok: 100 not == 30333\n")

        if v1!=v2
            c64.STROUT("ok: 100 != 30333\n")
        else
            c64.STROUT("error in 100!=30333!\n")

        if v1<v2
            c64.STROUT("ok: 100 < 30333\n")
        else
            c64.STROUT("error in 100<30333!\n")

        if v1<=v2
            c64.STROUT("ok: 100 <= 30333\n")
        else
            c64.STROUT("error in 100<=30333!\n")

        if v1>v2
            c64.STROUT("error in 100>30333!\n")
        else
            c64.STROUT("ok: 100 is not >30333\n")

        if v1>=v2
            c64.STROUT("error in 100>=30333!\n")
        else
            c64.STROUT("ok: 100 is not >=30333\n")


        v1 = 125
        v2 = -222
        if v1==v2
            c64.STROUT("error in 125==-222!\n")
        else
            c64.STROUT("ok: 125 not == -222\n")

        if v1!=v2
            c64.STROUT("ok: 125 != -222\n")
        else
            c64.STROUT("error in 125!=-222!\n")

        if v1<v2
            c64.STROUT("error in 125<-222!\n")
        else
            c64.STROUT("ok: 125 is not < -222\n")

        if v1<=v2
            c64.STROUT("error in 125<=-222!\n")
        else
            c64.STROUT("ok: 125 is not <= -222\n")

        if v1>v2
            c64.STROUT("ok: 125 > -222\n")
        else
            c64.STROUT("error in 125>-222!\n")

        if v1>=v2
            c64.STROUT("ok: 125 >= -222\n")
        else
            c64.STROUT("error in 125>=-222!\n")

        v1 = -222
        v2 = -222
        if v1==v2
            c64.STROUT("ok: -222 == -222\n")
        else
            c64.STROUT("error in -222==-222!\n")

        if v1!=v2
            c64.STROUT("error in -222!=-222!\n")
        else
            c64.STROUT("ok: -222 is not != -222\n")

        if v1<v2
            c64.STROUT("error in -222<-222!\n")
        else
            c64.STROUT("ok: -222 is not < -222\n")

        if v1<=v2
            c64.STROUT("ok: -222 <= -222\n")
        else
            c64.STROUT("error in -222<=-222!\n")

        if v1>v2
            c64.STROUT("error in -222>-222!\n")
        else
            c64.STROUT("ok: -222 is not > -222\n")

        if v1>=v2
            c64.STROUT("ok: -222 >= -222\n")
        else
            c64.STROUT("error in -222>=-222!\n")

        c64scr.print_byte(X)
        c64.CHROUT('\n')

    }

}
