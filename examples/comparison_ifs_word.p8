%import c64utils
%zeropage basicsafe


~ main {

    sub start()  {

        word v1
        word v2

        v1 = 100
        v2 = 30333
        if v1==v2
            c64scr.print("error in 100==30333!\n")
        else
            c64scr.print("ok: 100 not == 30333\n")

        if v1!=v2
            c64scr.print("ok: 100 != 30333\n")
        else
            c64scr.print("error in 100!=30333!\n")

        if v1<v2
            c64scr.print("ok: 100 < 30333\n")
        else
            c64scr.print("error in 100<30333!\n")

        if v1<=v2
            c64scr.print("ok: 100 <= 30333\n")
        else
            c64scr.print("error in 100<=30333!\n")

        if v1>v2
            c64scr.print("error in 100>30333!\n")
        else
            c64scr.print("ok: 100 is not >30333\n")

        if v1>=v2
            c64scr.print("error in 100>=30333!\n")
        else
            c64scr.print("ok: 100 is not >=30333\n")


        v1 = 125
        v2 = -222
        if v1==v2
            c64scr.print("error in 125==-222!\n")
        else
            c64scr.print("ok: 125 not == -222\n")

        if v1!=v2
            c64scr.print("ok: 125 != -222\n")
        else
            c64scr.print("error in 125!=-222!\n")

        if v1<v2
            c64scr.print("error in 125<-222!\n")
        else
            c64scr.print("ok: 125 is not < -222\n")

        if v1<=v2
            c64scr.print("error in 125<=-222!\n")
        else
            c64scr.print("ok: 125 is not <= -222\n")

        if v1>v2
            c64scr.print("ok: 125 > -222\n")
        else
            c64scr.print("error in 125>-222!\n")

        if v1>=v2
            c64scr.print("ok: 125 >= -222\n")
        else
            c64scr.print("error in 125>=-222!\n")

        v1 = -222
        v2 = -222
        if v1==v2
            c64scr.print("ok: -222 == -222\n")
        else
            c64scr.print("error in -222==-222!\n")

        if v1!=v2
            c64scr.print("error in -222!=-222!\n")
        else
            c64scr.print("ok: -222 is not != -222\n")

        if v1<v2
            c64scr.print("error in -222<-222!\n")
        else
            c64scr.print("ok: -222 is not < -222\n")

        if v1<=v2
            c64scr.print("ok: -222 <= -222\n")
        else
            c64scr.print("error in -222<=-222!\n")

        if v1>v2
            c64scr.print("error in -222>-222!\n")
        else
            c64scr.print("ok: -222 is not > -222\n")

        if v1>=v2
            c64scr.print("ok: -222 >= -222\n")
        else
            c64scr.print("error in -222>=-222!\n")

        ubyte endX = X
        if endX == 255
            c64scr.print("stack x ok!\n")
        else
            c64scr.print("error: stack x != 255 !\n")

    }

}
