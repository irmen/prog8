%import syslib
; %import graphics
%import textio
%import floats
%zeropage basicsafe


main {

    sub start()  {

        byte bb= -22
        word ww= -2222
        float ff = -1.2345

        ubyte qq = bb==22
        qq = ww==2222
        qq = ff==2.222

        if bb== -22 {
            txt.print("1 ok\n")
        } else {
            txt.print("1 fail\n")
        }

        if bb==99 {
            txt.print("2 fail\n")
        } else {
            txt.print("2 ok\n")
        }

        if bb==0 {
            txt.print("2b fail\n")
        } else {
            txt.print("2b ok\n")
        }

        if ww== -2222 {
            txt.print("3 ok\n")
        } else {
            txt.print("3 fail\n")
        }

        if ww==16384 {
            txt.print("4 fail\n")
        } else {
            txt.print("4 ok\n")
        }

        if ww==0 {
            txt.print("4b fail\n")
        } else {
            txt.print("4b ok\n")
        }

        if ff==-1.2345 {
            txt.print("4c ok\n")
        } else {
            txt.print("4c fail\n")
        }

        if ff==9999.9 {
            txt.print("4d fail\n")
        } else {
            txt.print("4d ok\n")
        }

        if ff==0.0 {
            txt.print("4e fail\n")
        } else {
            txt.print("4e ok\n")
        }


        if bb!= 99 {
            txt.print("5 ok\n")
        } else {
            txt.print("5 fail\n")
        }

        if bb!=-22 {
            txt.print("6 fail\n")
        } else {
            txt.print("6 ok\n")
        }

        if bb!=0 {
            txt.print("6b ok\n")
        } else {
            txt.print("6b fail\n")
        }

        if ww!=16384 {
            txt.print("7 ok\n")
        } else {
            txt.print("7 fail\n")
        }

        if ww!= -2222 {
            txt.print("8 fail\n")
        } else {
            txt.print("8 ok\n")
        }

        if ww!=0 {
            txt.print("8b ok\n")
        } else {
            txt.print("8b fail\n")
        }

        if ff!=-1.2345 {
            txt.print("8c fail\n")
        } else {
            txt.print("8c ok\n")
        }

        if ff==9999.9 {
            txt.print("8d fail\n")
        } else {
            txt.print("8d ok\n")
        }

        if ff!=0.0 {
            txt.print("8e ok\n")
        } else {
            txt.print("8e fail\n")
        }

        ; @($c000) *= 99        ; TODO implement

    }
}
