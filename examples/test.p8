%import textio
%zeropage basicsafe

; TODO this should also compile without optimizations

main {
    sub start() {
        cx16.r0 = 0
        if cx16.r0 < 0
            txt.print(" <0 fail\n")
        else
            txt.print(" <0 ok\n")

        if cx16.r0 <= 0
            txt.print("<=0 ok\n")
        else
            txt.print("<=0 fail\n")

        if cx16.r0 > 0
            txt.print(" >0 fail\n")
        else
            txt.print(" >0 ok\n")

        if cx16.r0 >= 0
            txt.print(">=0 ok\n")
        else
            txt.print(">=0 fail\n")

        bool bb
        bb = cx16.r0<0
        if bb
            txt.print(" <0 fail\n")
        else
            txt.print(" <0 ok\n")

        bb = cx16.r0<=0
        if bb
            txt.print("<=0 ok\n")
        else
            txt.print("<=0 fail\n")

        bb = cx16.r0>0
        if bb
            txt.print(" >0 fail\n")
        else
            txt.print(" >0 ok\n")

        bb = cx16.r0>=0
        if bb
            txt.print(">=0 ok\n")
        else
            txt.print(">=0 fail\n")

    }
}
