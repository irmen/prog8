%import textio
%zeropage basicsafe
%option no_sysinit



main {
    sub start() {
        ubyte @shared ub1=2
        ubyte @shared ub2=3
        word[10] particleX = -9999

        if particleX[ub1]<10
            txt.print("ok1\n")

        if particleX[ub1]<10
            txt.print("ok2\n")
        else
            txt.print("fail2\n")

        if particleX[ub2]>319
            txt.print("fail3\n")

        if particleX[ub2]>319
            txt.print("fail4\n")
        else
            txt.print("ok4\n")

        if particleX[ub1]<10 or particleX[ub2]>319
            txt.print("ok5\n")

        if particleX[ub1]<10 or particleX[ub2]>319
            txt.print("ok6\n")
        else
            txt.print("fail6\n")

    }
}
