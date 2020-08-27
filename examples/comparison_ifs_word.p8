%import c64textio
%zeropage basicsafe


main {

    sub start()  {

        word v1
        word v2

        v1 = 100
        v2 = 30333
        if v1==v2
            txt.print("error in 100==30333!\n")
        else
            txt.print("ok: 100 not == 30333\n")

        if v1!=v2
            txt.print("ok: 100 != 30333\n")
        else
            txt.print("error in 100!=30333!\n")

        if v1<v2
            txt.print("ok: 100 < 30333\n")
        else
            txt.print("error in 100<30333!\n")

        if v1<=v2
            txt.print("ok: 100 <= 30333\n")
        else
            txt.print("error in 100<=30333!\n")

        if v1>v2
            txt.print("error in 100>30333!\n")
        else
            txt.print("ok: 100 is not >30333\n")

        if v1>=v2
            txt.print("error in 100>=30333!\n")
        else
            txt.print("ok: 100 is not >=30333\n")


        v1 = 125
        v2 = -222
        if v1==v2
            txt.print("error in 125==-222!\n")
        else
            txt.print("ok: 125 not == -222\n")

        if v1!=v2
            txt.print("ok: 125 != -222\n")
        else
            txt.print("error in 125!=-222!\n")

        if v1<v2
            txt.print("error in 125<-222!\n")
        else
            txt.print("ok: 125 is not < -222\n")

        if v1<=v2
            txt.print("error in 125<=-222!\n")
        else
            txt.print("ok: 125 is not <= -222\n")

        if v1>v2
            txt.print("ok: 125 > -222\n")
        else
            txt.print("error in 125>-222!\n")

        if v1>=v2
            txt.print("ok: 125 >= -222\n")
        else
            txt.print("error in 125>=-222!\n")

        v1 = -222
        v2 = -222
        if v1==v2
            txt.print("ok: -222 == -222\n")
        else
            txt.print("error in -222==-222!\n")

        if v1!=v2
            txt.print("error in -222!=-222!\n")
        else
            txt.print("ok: -222 is not != -222\n")

        if v1<v2
            txt.print("error in -222<-222!\n")
        else
            txt.print("ok: -222 is not < -222\n")

        if v1<=v2
            txt.print("ok: -222 <= -222\n")
        else
            txt.print("error in -222<=-222!\n")

        if v1>v2
            txt.print("error in -222>-222!\n")
        else
            txt.print("ok: -222 is not > -222\n")

        if v1>=v2
            txt.print("ok: -222 >= -222\n")
        else
            txt.print("error in -222>=-222!\n")

        v1 = 1000
        v2 = 1000
        if v1==v2
            txt.print("ok: 1000 == 1000\n")
        else
            txt.print("error in 1000==1000!\n")

        if v1!=v2
            txt.print("error in 1000!=1000!\n")
        else
            txt.print("ok: 1000 is not != 1000\n")

        if v1<v2
            txt.print("error in 1000<1000!\n")
        else
            txt.print("ok: 1000 is not < 1000\n")

        if v1<=v2
            txt.print("ok: 1000 <= 1000\n")
        else
            txt.print("error in 1000<=1000!\n")

        if v1>v2
            txt.print("error in 1000>1000!\n")
        else
            txt.print("ok: 1000 is not > 1000\n")

        if v1>=v2
            txt.print("ok: 1000 >= 1000\n")
        else
            txt.print("error in 1000>=1000!\n")
    }
}
