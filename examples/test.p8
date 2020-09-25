%import syslib
; %import graphics
%import textio
%import floats
%zeropage basicsafe


main {


    sub keypress(ubyte key) {
        txt.print("keypress:")
        txt.print_ub(key)
        txt.chrout('=')
        when key {
            157, ',' -> txt.chrout('a')
            29, '/'  -> txt.chrout('b')
            17, '.'  -> txt.chrout('c')
            145, ' ' -> txt.chrout('d')
        }
        txt.chrout('\n')
    }

    sub start()  {

        repeat {
            ubyte key=c64.GETIN()
            if key
                keypress(key)
        }

;        byte bb= -22
;        ubyte ubb = 22
;        word ww= -2222
;        uword uww = 2222
;        float ff = -1.2345
;
;        repeat(25)
;            txt.chrout('\n')
;
;        if bb < -1 {
;            txt.print("1 ok\n")
;        } else {
;            txt.print("1 fail\n")
;        }
;
;        if bb < -99 {
;            txt.print("2 fail\n")
;        } else {
;            txt.print("2 ok\n")
;        }
;
;        if bb<0 {
;            txt.print("2b ok\n")
;        } else {
;            txt.print("2b fail\n")
;        }
;
;        if ww < -1 {
;            txt.print("3 ok\n")
;        } else {
;            txt.print("3 fail\n")
;        }
;
;        if ww < -9999 {
;            txt.print("4 fail\n")
;        } else {
;            txt.print("4 ok\n")
;        }
;
;        if ww < 0 {
;            txt.print("4b ok\n")
;        } else {
;            txt.print("4b fail\n")
;        }
;
;        if ff < -1.0 {
;            txt.print("4c ok\n")
;        } else {
;            txt.print("4c fail\n")
;        }
;
;        if ff < -9999.9 {
;            txt.print("4d fail\n")
;        } else {
;            txt.print("4d ok\n")
;        }
;
;        if ff < 0.0 {
;            txt.print("4e ok\n")
;        } else {
;            txt.print("4e fail\n")
;        }
;
;        if ubb < 100 {
;            txt.print("4f ok\n")
;        } else {
;            txt.print("4f fail\n")
;        }
;
;        if ubb < 2 {
;            txt.print("4g fail\n")
;        } else {
;            txt.print("4g ok\n")
;        }
;
;        if ubb<0 {
;            txt.print("4h fail\n")
;        } else {
;            txt.print("4h ok\n")
;        }
;
;        if uww < 10000 {
;            txt.print("4i ok\n")
;        } else {
;            txt.print("4i fail\n")
;        }
;
;        if uww < 2 {
;            txt.print("4j fail\n")
;        } else {
;            txt.print("4j ok\n")
;        }
;
;        if uww < 0 {
;            txt.print("4k fail\n")
;        } else {
;            txt.print("4k ok\n")
;        }
;
;
;
;        if bb > -99 {
;            txt.print("5 ok\n")
;        } else {
;            txt.print("5 fail\n")
;        }
;
;        if bb > -1 {
;            txt.print("6 fail\n")
;        } else {
;            txt.print("6 ok\n")
;        }
;
;        if bb > 0 {
;            txt.print("6b fail\n")
;        } else {
;            txt.print("6b ok\n")
;        }
;
;        if ww > -9999 {
;            txt.print("7 ok\n")
;        } else {
;            txt.print("7 fail\n")
;        }
;
;        if ww > -1 {
;            txt.print("8 fail\n")
;        } else {
;            txt.print("8 ok\n")
;        }
;
;        if ww>0 {
;            txt.print("8b fail\n")
;        } else {
;            txt.print("8b ok\n")
;        }
;
;        if ff > -1.0 {
;            txt.print("8c fail\n")
;        } else {
;            txt.print("8c ok\n")
;        }
;
;        if ff > -9999.9 {
;            txt.print("8d ok\n")
;        } else {
;            txt.print("8d fail\n")
;        }
;
;        if ff > 0.0 {
;            txt.print("8e fail\n")
;        } else {
;            txt.print("8e ok\n")
;        }
;
;        if ubb > 5 {
;            txt.print("8f ok\n")
;        } else {
;            txt.print("8f fail\n")
;        }
;
;        if ubb > 250 {
;            txt.print("8g fail\n")
;        } else {
;            txt.print("8g ok\n")
;        }
;
;        if ubb > 0 {
;            txt.print("8h ok\n")
;        } else {
;            txt.print("8h fail\n")
;        }
;
;        if uww > 5 {
;            txt.print("8i ok\n")
;        } else {
;            txt.print("8i fail\n")
;        }
;
;        if uww > 9999 {
;            txt.print("8j fail\n")
;        } else {
;            txt.print("8j ok\n")
;        }
;
;        if uww>0 {
;            txt.print("8b ok\n")
;        } else {
;            txt.print("8b fail\n")
;        }

        ; @($c000) *= 99        ; TODO implement

    }
}
