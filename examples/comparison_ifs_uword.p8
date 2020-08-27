%import c64textio
%zeropage basicsafe


main {

    sub start()  {

        uword v1
        uword v2

        v1 = 100
        v2 = 64444
        if v1==v2
            txt.print("error in 100==64444!\n")
        else
            txt.print("ok: 100 not == 64444\n")

        if v1!=v2
            txt.print("ok: 100 != 64444\n")
        else
            txt.print("error in 100!=64444!\n")

        if v1<v2
            txt.print("ok: 100 < 64444\n")
        else
            txt.print("error in 100<64444!\n")

        if v1<=v2
            txt.print("ok: 100 <= 64444\n")
        else
            txt.print("error in 100<=64444!\n")

        if v1>v2
            txt.print("error in 100>64444!\n")
        else
            txt.print("ok: 100 is not >64444\n")

        if v1>=v2
            txt.print("error in 100>=64444!\n")
        else
            txt.print("ok: 100 is not >=64444\n")


        v1 = 5555
        v2 = 322
        if v1==v2
            txt.print("error in 5555==322!\n")
        else
            txt.print("ok: 5555 not == 322\n")

        if v1!=v2
            txt.print("ok: 5555 != 322\n")
        else
            txt.print("error in 5555!=322!\n")

        if v1<v2
            txt.print("error in 5555<322!\n")
        else
            txt.print("ok: 5555 is not < 322\n")

        if v1<=v2
            txt.print("error in 5555<=322!\n")
        else
            txt.print("ok: 5555 is not <= 322\n")

        if v1>v2
            txt.print("ok: 5555 > 322\n")
        else
            txt.print("error in 5555>322!\n")

        if v1>=v2
            txt.print("ok: 5555 >= 322\n")
        else
            txt.print("error in 5555>=322!\n")

        v1 = 322
        v2 = 322
        if v1==v2
            txt.print("ok: 322 == 322\n")
        else
            txt.print("error in 322==322!\n")

        if v1!=v2
            txt.print("error in 322!=322!\n")
        else
            txt.print("ok: 322 is not != 322\n")

        if v1<v2
            txt.print("error in 322<322!\n")
        else
            txt.print("ok: 322 is not < 322\n")

        if v1<=v2
            txt.print("ok: 322 <= 322\n")
        else
            txt.print("error in 322<=322!\n")

        if v1>v2
            txt.print("error in 322>322!\n")
        else
            txt.print("ok: 322 is not > 322\n")

        if v1>=v2
            txt.print("ok: 322 >= 322\n")
        else
            txt.print("error in 322>=322!\n")
    }
}
