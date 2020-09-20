%import textio
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        ubyte v1
        ubyte v2

        v1 = 100
        v2 = 200
        if v1==v2
            txt.print("error in 100==200!\n")
        else
            txt.print("ok: 100 not == 200\n")

        if v1!=v2
            txt.print("ok: 100 != 200\n")
        else
            txt.print("error in 100!=200!\n")

        if v1<v2
            txt.print("ok: 100 < 200\n")
        else
            txt.print("error in 100<200!\n")

        if v1<=v2
            txt.print("ok: 100 <= 200\n")
        else
            txt.print("error in 100<=200!\n")

        if v1>v2
            txt.print("error in 100>200!\n")
        else
            txt.print("ok: 100 is not >200\n")

        if v1>=v2
            txt.print("error in 100>=200!\n")
        else
            txt.print("ok: 100 is not >=200\n")


        v1 = 155
        v2 = 22
        if v1==v2
            txt.print("error in 155==22!\n")
        else
            txt.print("ok: 155 not == 22\n")

        if v1!=v2
            txt.print("ok: 155 != 22\n")
        else
            txt.print("error in 155!=22!\n")

        if v1<v2
            txt.print("error in 155<22!\n")
        else
            txt.print("ok: 155 is not < 22\n")

        if v1<=v2
            txt.print("error in 155<=22!\n")
        else
            txt.print("ok: 155 is not <= 22\n")

        if v1>v2
            txt.print("ok: 155 > 22\n")
        else
            txt.print("error in 155>22!\n")

        if v1>=v2
            txt.print("ok: 155 >= 22\n")
        else
            txt.print("error in 155>=22!\n")

        v1 = 22
        v2 = 22
        if v1==v2
            txt.print("ok: 22 == 22\n")
        else
            txt.print("error in 22==22!\n")

        if v1!=v2
            txt.print("error in 22!=22!\n")
        else
            txt.print("ok: 22 is not != 22\n")

        if v1<v2
            txt.print("error in 22<22!\n")
        else
            txt.print("ok: 22 is not < 22\n")

        if v1<=v2
            txt.print("ok: 22 <= 22\n")
        else
            txt.print("error in 22<=22!\n")

        if v1>v2
            txt.print("error in 22>22!\n")
        else
            txt.print("ok: 22 is not > 22\n")

        if v1>=v2
            txt.print("ok: 22 >= 22\n")
        else
            txt.print("error in 22>=22!\n")
    }
}
