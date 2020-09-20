%import textio
%import floats
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {

        float v1
        float v2

        v1 = 1.11
        v2 = 699.99
        if v1==v2
            txt.print("error in 1.11==699.99!\n")
        else
            txt.print("ok: 1.11 not == 699.99\n")

        if v1!=v2
            txt.print("ok: 1.11 != 699.99\n")
        else
            txt.print("error in 1.11!=699.99!\n")

        if v1<v2
            txt.print("ok: 1.11 < 699.99\n")
        else
            txt.print("error in 1.11<699.99!\n")

        if v1<=v2
            txt.print("ok: 1.11 <= 699.99\n")
        else
            txt.print("error in 1.11<=699.99!\n")

        if v1>v2
            txt.print("error in 1.11>699.99!\n")
        else
            txt.print("ok: 1.11 is not >699.99\n")

        if v1>=v2
            txt.print("error in 1.11>=699.99!\n")
        else
            txt.print("ok: 1.11 is not >=699.99\n")


        v1 = 555.5
        v2 = -22.2
        if v1==v2
            txt.print("error in 555.5==-22.2!\n")
        else
            txt.print("ok: 555.5 not == -22.2\n")

        if v1!=v2
            txt.print("ok: 555.5 != -22.2\n")
        else
            txt.print("error in 555.5!=-22.2!\n")

        if v1<v2
            txt.print("error in 555.5<-22.2!\n")
        else
            txt.print("ok: 555.5 is not < -22.2\n")

        if v1<=v2
            txt.print("error in 555.5<=-22.2!\n")
        else
            txt.print("ok: 555.5 is not <= -22.2\n")

        if v1>v2
            txt.print("ok: 555.5 > -22.2\n")
        else
            txt.print("error in 555.5>-22.2!\n")

        if v1>=v2
            txt.print("ok: 555.5 >= -22.2\n")
        else
            txt.print("error in 555.5>=-22.2!\n")

        v1 = -22.2
        v2 = -22.2
        if v1==v2
            txt.print("ok: -22.2 == -22.2\n")
        else
            txt.print("error in -22.2==-22.2!\n")

        if v1!=v2
            txt.print("error in -22.2!=-22.2!\n")
        else
            txt.print("ok: -22.2 is not != -22.2\n")

        if v1<v2
            txt.print("error in -22.2<-22.2!\n")
        else
            txt.print("ok: -22.2 is not < -22.2\n")

        if v1<=v2
            txt.print("ok: -22.2 <= -22.2\n")
        else
            txt.print("error in -22.2<=-22.2!\n")

        if v1>v2
            txt.print("error in -22.2>-22.2!\n")
        else
            txt.print("ok: -22.2 is not > -22.2\n")

        if v1>=v2
            txt.print("ok: -22.2 >= -22.2\n")
        else
            txt.print("error in -22.2>=-22.2!\n")
    }
}
