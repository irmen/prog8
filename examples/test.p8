%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {
    sub start() {

        ubyte ubb
        byte bb
        uword uww
        word ww

        bb = -1
        ww = -1

        if bb<0
            c64scr.print("1 ok\n")
        else
            c64scr.print("1 fail\n")

        if ww<0
            c64scr.print("2 ok\n")
        else
            c64scr.print("2 fail\n")

        bb = 0
        ww = 0

        if bb>=0
            c64scr.print("4 ok\n")
        else
            c64scr.print("4 fail\n")

        if ww>=0
            c64scr.print("5 ok\n")
        else
            c64scr.print("5 fail\n")

        bb = 0
        ww = 0

        if bb>=0
            c64scr.print("7 ok\n")
        else
            c64scr.print("7 fail\n")

        if ww>=0
            c64scr.print("8 ok\n")
        else
            c64scr.print("8 fail\n")

        ubb = 0
        uww = 0
        if ubb>=0
            c64scr.print("10 ok\n")
        else
            c64scr.print("10 fail\n")
        if uww>=0
            c64scr.print("11 ok\n")
        else
            c64scr.print("11 fail\n")
        if ubb<0
            c64scr.print("12 fail\n")
        else
            c64scr.print("12 ok\n")
        if uww<0
            c64scr.print("13 fail\n")
        else
            c64scr.print("13 ok\n")
        ubb = $ff
        uww = $ffff
        if ubb>=0
            c64scr.print("14 ok\n")
        else
            c64scr.print("14 fail\n")
        if uww>=0
            c64scr.print("15 ok\n")
        else
            c64scr.print("15 fail\n")
        if ubb<0
            c64scr.print("16 fail\n")
        else
            c64scr.print("16 ok\n")
        if uww<0
            c64scr.print("17 fail\n")
        else
            c64scr.print("17 ok\n")

    }
}


