%import textio
%import floats
%zeropage basicsafe

main {

    byte bb= -22
    ubyte ubb = 22
    word ww= -2222
    uword uww = 2222
    float ff = -2.2

    sub start()  {

        repeat(25)
            txt.chrout('\n')

        equal()
        txt.chrout('\n')
        notequal()
        txt.chrout('\n')
        less()
        txt.chrout('\n')
        greater()
        txt.chrout('\n')
        lessequal()
        txt.chrout('\n')
        greaterequal()
        txt.chrout('\n')
    }

    sub equal() {

        if bb == -22 {
            txt.print("ok == 1\n")
        } else {
            txt.print("fail == 1\n")
        }

        if bb == -23 {
            txt.print("fail == 2\n")
        } else {
            txt.print("ok == 2\n")
        }

        if bb==0 {
            txt.print("fail == 2b\n")
        } else {
            txt.print("ok == 2b\n")
        }

        if ww == -2222 {
            txt.print("ok == 3\n")
        } else {
            txt.print("fail == 3\n")
        }

        if ww == -2223 {
            txt.print("fail == 4\n")
        } else {
            txt.print("ok == 4\n")
        }

        if ww == 0 {
            txt.print("fail == 4a\n")
        } else {
            txt.print("ok == 4a\n")
        }

        if ff == -2.2 {
            txt.print("ok == 4c\n")
        } else {
            txt.print("fail == 4c\n")
        }

        if ff == -2.3 {
            txt.print("fail == 4d\n")
        } else {
            txt.print("ok == 4d\n")
        }

        if ff == 0.0 {
            txt.print("fail == 4e\n")
        } else {
            txt.print("ok == 4e\n")
        }

        if ubb == 22 {
            txt.print("ok == 4f\n")
        } else {
            txt.print("fail == 4f\n")
        }

        if ubb == 23 {
            txt.print("fail == 4g\n")
        } else {
            txt.print("ok == 4g\n")
        }

        if ubb == 0 {
            txt.print("fail == 4h\n")
        } else {
            txt.print("ok == 4h\n")
        }

        if uww == 2222 {
            txt.print("ok == 4i\n")
        } else {
            txt.print("fail == 4i\n")
        }

        if uww == 2223 {
            txt.print("fail == 4j\n")
        } else {
            txt.print("ok == 4j\n")
        }

        if uww == 0 {
            txt.print("fail == 4k\n")
        } else {
            txt.print("ok == 4k\n")
        }
    }

    sub notequal() {

        if bb != -23 {
            txt.print("ok != 1\n")
        } else {
            txt.print("fail != 1\n")
        }

        if bb != -22 {
            txt.print("fail != 2\n")
        } else {
            txt.print("ok != 2\n")
        }

        if bb!=0 {
            txt.print("ok != 2b\n")
        } else {
            txt.print("fail != 2b\n")
        }

        if ww != -2223 {
            txt.print("ok != 3\n")
        } else {
            txt.print("fail != 3\n")
        }

        if ww != -2222 {
            txt.print("fail != 4\n")
        } else {
            txt.print("ok != 4\n")
        }

        if ww != 0 {
            txt.print("ok != 4a\n")
        } else {
            txt.print("fail != 4a\n")
        }

        if ff != -2.3 {
            txt.print("ok != 4c\n")
        } else {
            txt.print("fail != 4c\n")
        }

        if ff != -2.2 {
            txt.print("fail != 4d\n")
        } else {
            txt.print("ok != 4d\n")
        }

        if ff != 0.0 {
            txt.print("ok != 4e\n")
        } else {
            txt.print("fail != 4e\n")
        }

        if ubb != 23 {
            txt.print("ok != 4f\n")
        } else {
            txt.print("fail != 4f\n")
        }

        if ubb != 22 {
            txt.print("fail != 4g\n")
        } else {
            txt.print("ok != 4g\n")
        }

        if ubb != 0 {
            txt.print("ok != 4h\n")
        } else {
            txt.print("fail != 4h\n")
        }

        if uww != 2223 {
            txt.print("ok != 4i\n")
        } else {
            txt.print("fail != 4i\n")
        }

        if uww != 2222 {
            txt.print("fail != 4j\n")
        } else {
            txt.print("ok != 4j\n")
        }

        if uww != 0 {
            txt.print("ok != 4k\n")
        } else {
            txt.print("fail != 4k\n")
        }
    }

    sub less() {

        if bb < -1 {
            txt.print("ok < 1\n")
        } else {
            txt.print("fail < 1\n")
        }

        if bb < -99 {
            txt.print("fail < 2\n")
        } else {
            txt.print("ok < 2\n")
        }

        if bb < -22 {
            txt.print("fail < 2a\n")
        } else {
            txt.print("ok < 2a\n")
        }

        if bb<0 {
            txt.print("ok < 2b\n")
        } else {
            txt.print("fail < 2b\n")
        }

        if ww < -1 {
            txt.print("ok < 3\n")
        } else {
            txt.print("fail < 3\n")
        }

        if ww < -9999 {
            txt.print("fail < 4\n")
        } else {
            txt.print("ok < 4\n")
        }

        if ww < -2222 {
            txt.print("fail < 4a\n")
        } else {
            txt.print("ok < 4a\n")
        }

        if ww < 0 {
            txt.print("ok < 4b\n")
        } else {
            txt.print("fail < 4b\n")
        }

        if ff < -1.0 {
            txt.print("ok < 4c\n")
        } else {
            txt.print("fail < 4c\n")
        }

        if ff < -9999.9 {
            txt.print("fail < 4d\n")
        } else {
            txt.print("ok < 4d\n")
        }

        if ff < 0.0 {
            txt.print("ok < 4e\n")
        } else {
            txt.print("fail < 4e\n")
        }

        if ff < -2.2 {
            txt.print("fail < 4e2\n")
        } else {
            txt.print("ok < 4e2\n")
        }

        if ubb < 100 {
            txt.print("ok < 4f\n")
        } else {
            txt.print("fail < 4f\n")
        }

        if ubb < 2 {
            txt.print("fail < 4g\n")
        } else {
            txt.print("ok < 4g\n")
        }

        if ubb<0 {
            txt.print("fail < 4h\n")
        } else {
            txt.print("ok < 4h\n")
        }

        if ubb<22 {
            txt.print("fail < 4h2\n")
        } else {
            txt.print("ok < 4h2\n")
        }

        if uww < 10000 {
            txt.print("ok < 4i\n")
        } else {
            txt.print("fail < 4i\n")
        }

        if uww < 2 {
            txt.print("fail < 4j\n")
        } else {
            txt.print("ok < 4j\n")
        }

        if uww < 0 {
            txt.print("fail < 4k\n")
        } else {
            txt.print("ok < 4k\n")
        }

        if uww < 2222 {
            txt.print("fail < 4l\n")
        } else {
            txt.print("ok < 4l\n")
        }

        ; some more special cases for signed comparison
        byte b2 = -100
        word w2 = -30000
        if b2 < -127 {
            txt.print("fail < eb1\n")
        } else {
            txt.print("ok < eb1\n")
        }
        if b2 < -101 {
            txt.print("fail < eb2\n")
        } else {
            txt.print("ok < eb2\n")
        }
        if b2 < -100 {
            txt.print("fail < eb3\n")
        } else {
            txt.print("ok < eb3\n")
        }
        if b2 < -99 {
            txt.print("ok < eb4\n")
        } else {
            txt.print("fail < eb4\n")
        }
        if b2 < 100 {
            txt.print("ok < eb5\n")
        } else {
            txt.print("fail < eb5\n")
        }
        if b2 < 127 {
            txt.print("ok < eb6\n")
        } else {
            txt.print("fail < eb6\n")
        }
        if b2 < 0 {
            txt.print("ok < eb7\n")
        } else {
            txt.print("fail < eb7\n")
        }

        if w2 < -32768 {
            txt.print("fail < ew1\n")
        } else {
            txt.print("ok < ew1\n")
        }
        if w2 < -30001 {
            txt.print("fail < ew2\n")
        } else {
            txt.print("ok < ew2\n")
        }
        if w2 < -30000 {
            txt.print("fail < ew3\n")
        } else {
            txt.print("ok < ew3\n")
        }
        if w2 < -29999 {
            txt.print("ok < ew4\n")
        } else {
            txt.print("fail < ew4\n")
        }
        if w2 < 30000 {
            txt.print("ok < ew5\n")
        } else {
            txt.print("fail < ew5\n")
        }
        if w2 < 32767 {
            txt.print("ok < ew6\n")
        } else {
            txt.print("fail < ew6\n")
        }
        if w2 < 0 {
            txt.print("ok < ew7\n")
        } else {
            txt.print("fail < ew7\n")
        }
    }

    sub greater() {
        if bb > -99 {
            txt.print("ok > 5\n")
        } else {
            txt.print("fail > 5\n")
        }

        if bb > -1 {
            txt.print("fail > 6\n")
        } else {
            txt.print("ok > 6\n")
        }

        if bb > 0 {
            txt.print("fail > 6b\n")
        } else {
            txt.print("ok > 6b\n")
        }

        if bb > -22 {
            txt.print("fail > 6c\n")
        } else {
            txt.print("ok > 6c\n")
        }

        if ww > -9999 {
            txt.print("ok > 7\n")
        } else {
            txt.print("fail > 7\n")
        }

        if ww > -1 {
            txt.print("fail > 8\n")
        } else {
            txt.print("ok > 8\n")
        }

        if ww>0 {
            txt.print("fail > 8b\n")
        } else {
            txt.print("ok > 8b\n")
        }

        if ww>-2222 {
            txt.print("fail > 8b2\n")
        } else {
            txt.print("ok > 8b2\n")
        }

        if ff > -1.0 {
            txt.print("fail > 8c\n")
        } else {
            txt.print("ok > 8c\n")
        }

        if ff > -9999.9 {
            txt.print("ok > 8d\n")
        } else {
            txt.print("fail > 8d\n")
        }

        if ff > 0.0 {
            txt.print("fail > 8e\n")
        } else {
            txt.print("ok > 8e\n")
        }

        if ff > -2.2 {
            txt.print("fail > 8e2\n")
        } else {
            txt.print("ok > 8e2\n")
        }

        if ubb > 5 {
            txt.print("ok > 8f\n")
        } else {
            txt.print("fail > 8f\n")
        }

        if ubb > 250 {
            txt.print("fail > 8g\n")
        } else {
            txt.print("ok > 8g\n")
        }

        if ubb > 0 {
            txt.print("ok > 8h\n")
        } else {
            txt.print("fail > 8h\n")
        }

        if ubb > 22 {
            txt.print("fail > 8h2\n")
        } else {
            txt.print("ok > 8h2\n")
        }

        if uww > 5 {
            txt.print("ok > 8i\n")
        } else {
            txt.print("fail > 8i\n")
        }

        if uww > 9999 {
            txt.print("fail > 8j\n")
        } else {
            txt.print("ok > 8j\n")
        }

        if uww>0 {
            txt.print("ok > 8k\n")
        } else {
            txt.print("fail > 8k\n")
        }

        if uww>2222 {
            txt.print("fail > 8l\n")
        } else {
            txt.print("ok > 8l\n")
        }

        ; some more special cases for signed comparison
        byte b2 = -100
        word w2 = -30000
        if b2 > -127 {
            txt.print("ok > eb1\n")
        } else {
            txt.print("fail > eb1\n")
        }
        if b2 > -101 {
            txt.print("ok > eb2\n")
        } else {
            txt.print("fail > eb2\n")
        }
        if b2 > -100 {
            txt.print("fail > eb3\n")
        } else {
            txt.print("ok > eb3\n")
        }
        if b2 > -99 {
            txt.print("fail > eb4\n")
        } else {
            txt.print("ok > eb4\n")
        }
        if b2 > 100 {
            txt.print("fail > eb5\n")
        } else {
            txt.print("ok > eb5\n")
        }
        if b2 > 127 {
            txt.print("fail > eb6\n")
        } else {
            txt.print("ok > eb6\n")
        }
        if b2 > 0 {
            txt.print("fail > eb7\n")
        } else {
            txt.print("ok > eb7\n")
        }

        if w2 > -32768 {
            txt.print("ok > ew1\n")
        } else {
            txt.print("fail > ew1\n")
        }
        if w2 > -30001 {
            txt.print("ok > ew2\n")
        } else {
            txt.print("fail > ew2\n")
        }
        if w2 > -30000 {
            txt.print("fail > ew3\n")
        } else {
            txt.print("ok > ew3\n")
        }
        if w2 > -29999 {
            txt.print("fail > ew4\n")
        } else {
            txt.print("ok > ew4\n")
        }
        if w2 > 30000 {
            txt.print("fail > ew5\n")
        } else {
            txt.print("ok > ew5\n")
        }
        if w2 > 32767 {
            txt.print("fail > ew6\n")
        } else {
            txt.print("ok > ew6\n")
        }
        if w2 > 0 {
            txt.print("fail > ew6\n")
        } else {
            txt.print("ok > ew6\n")
        }
    }

    sub lessequal() {

        if bb <= -1 {
            txt.print("ok <= 1\n")
        } else {
            txt.print("fail <= 1\n")
        }

        if bb <= -99 {
            txt.print("fail <= 2\n")
        } else {
            txt.print("ok <= 2\n")
        }

        if bb <= -22 {
            txt.print("ok <= 2a\n")
        } else {
            txt.print("fail <= 2a\n")
        }

        if bb<=0 {
            txt.print("ok <= 2b\n")
        } else {
            txt.print("fail <= 2b\n")
        }

        if ww <= -1 {
            txt.print("ok <= 3\n")
        } else {
            txt.print("fail <= 3\n")
        }

        if ww <= -9999 {
            txt.print("fail <= 4\n")
        } else {
            txt.print("ok <= 4\n")
        }

        if ww <= -2222 {
            txt.print("ok <= 4a\n")
        } else {
            txt.print("fail <= 4a\n")
        }

        if ww <= 0 {
            txt.print("ok <= 4b\n")
        } else {
            txt.print("fail <= 4b\n")
        }

        if ff <= -1.0 {
            txt.print("ok <= 4c\n")
        } else {
            txt.print("fail <= 4c\n")
        }

        if ff <= -9999.9 {
            txt.print("fail <= 4d\n")
        } else {
            txt.print("ok <= 4d\n")
        }

        if ff <= 0.0 {
            txt.print("ok <= 4e\n")
        } else {
            txt.print("fail <= 4e\n")
        }

        if ff <= -2.2 {
            txt.print("ok <= 4e2\n")
        } else {
            txt.print("fail <= 4e2\n")
        }

        if ubb <= 100 {
            txt.print("ok <= 4f\n")
        } else {
            txt.print("fail <= 4f\n")
        }

        if ubb <= 2 {
            txt.print("fail <= 4g\n")
        } else {
            txt.print("ok <= 4g\n")
        }

        if ubb<=0 {
            txt.print("fail <= 4h\n")
        } else {
            txt.print("ok <= 4h\n")
        }

        if ubb<=22 {
            txt.print("ok <= 4h2\n")
        } else {
            txt.print("fail <= 4h2\n")
        }

        if uww <= 10000 {
            txt.print("ok <= 4i\n")
        } else {
            txt.print("fail <= 4i\n")
        }

        if uww <= 2 {
            txt.print("fail <= 4j\n")
        } else {
            txt.print("ok <= 4j\n")
        }

        if uww <= 0 {
            txt.print("fail <= 4k\n")
        } else {
            txt.print("ok <= 4k\n")
        }

        if uww <= 2222 {
            txt.print("ok <= 4l\n")
        } else {
            txt.print("fail <= 4l\n")
        }

        ; some more special cases for signed comparison
        byte b2 = -100
        word w2 = -30000
        if b2 <= -127 {
            txt.print("fail <= eb1\n")
        } else {
            txt.print("ok <= eb1\n")
        }
        if b2 <= -101 {
            txt.print("fail <= eb2\n")
        } else {
            txt.print("ok <= eb2\n")
        }
        if b2 <= -100 {
            txt.print("ok <= eb3\n")
        } else {
            txt.print("fail <= eb3\n")
        }
        if b2 <= -99 {
            txt.print("ok <= eb4\n")
        } else {
            txt.print("fail <= eb4\n")
        }
        if b2 <= 100 {
            txt.print("ok <= eb5\n")
        } else {
            txt.print("fail <= eb5\n")
        }
        if b2 <= 127 {
            txt.print("ok <= eb6\n")
        } else {
            txt.print("fail <= eb6\n")
        }
        if b2 <= 0 {
            txt.print("ok <= eb7\n")
        } else {
            txt.print("fail <= eb7\n")
        }

        if w2 <= -32768 {
            txt.print("fail <= ew1\n")
        } else {
            txt.print("ok <= ew1\n")
        }
        if w2 <= -30001 {
            txt.print("fail <= ew2\n")
        } else {
            txt.print("ok <= ew2\n")
        }
        if w2 <= -30000 {
            txt.print("ok <= ew3\n")
        } else {
            txt.print("fail <= ew3\n")
        }
        if w2 <= -29999 {
            txt.print("ok <= ew4\n")
        } else {
            txt.print("fail <= ew4\n")
        }
        if w2 <= 30000 {
            txt.print("ok <= ew5\n")
        } else {
            txt.print("fail <= ew5\n")
        }
        if w2 <= 32767 {
            txt.print("ok <= ew6\n")
        } else {
            txt.print("fail <= ew6\n")
        }
        if w2 <= 0 {
            txt.print("ok <= ew7\n")
        } else {
            txt.print("fail <= ew7\n")
        }

    }

    sub greaterequal() {
        if bb >= -99 {
            txt.print("ok >= 5\n")
        } else {
            txt.print("fail >= 5\n")
        }

        if bb >= -1 {
            txt.print("fail >= 6\n")
        } else {
            txt.print("ok >= 6\n")
        }

        if bb >= 0 {
            txt.print("fail >= 6b\n")
        } else {
            txt.print("ok >= 6b\n")
        }

        if bb >= -22 {
            txt.print("ok >= 6c\n")
        } else {
            txt.print("fail >= 6c\n")
        }

        if ww >= -9999 {
            txt.print("ok >= 7\n")
        } else {
            txt.print("fail >= 7\n")
        }

        if ww >= -1 {
            txt.print("fail >= 8\n")
        } else {
            txt.print("ok >= 8\n")
        }

        if ww>=0 {
            txt.print("fail >= 8b\n")
        } else {
            txt.print("ok >= 8b\n")
        }

        if ww>=-2222 {
            txt.print("ok >= 8b2\n")
        } else {
            txt.print("fail >= 8b2\n")
        }

        if ff >= -1.0 {
            txt.print("fail >= 8c\n")
        } else {
            txt.print("ok >= 8c\n")
        }

        if ff >= -9999.9 {
            txt.print("ok >= 8d\n")
        } else {
            txt.print("fail >= 8d\n")
        }

        if ff >= 0.0 {
            txt.print("fail >= 8e\n")
        } else {
            txt.print("ok >= 8e\n")
        }

        if ff >= -2.2 {
            txt.print("ok >= 8e2\n")
        } else {
            txt.print("fail >= 8e2\n")
        }

        if ubb >= 5 {
            txt.print("ok >= 8f\n")
        } else {
            txt.print("fail >= 8f\n")
        }

        if ubb >= 250 {
            txt.print("fail >= 8g\n")
        } else {
            txt.print("ok >= 8g\n")
        }

        if ubb >= 0 {
            txt.print("ok >= 8h\n")
        } else {
            txt.print("fail >= 8h\n")
        }

        if ubb >= 22 {
            txt.print("ok >= 8h2\n")
        } else {
            txt.print("fail >= 8h2\n")
        }

        if uww >= 5 {
            txt.print("ok >= 8i\n")
        } else {
            txt.print("fail >= 8i\n")
        }

        if uww >= 9999 {
            txt.print("fail >= 8j\n")
        } else {
            txt.print("ok >= 8j\n")
        }

        if uww>=0 {
            txt.print("ok >= 8k\n")
        } else {
            txt.print("fail >= 8k\n")
        }

        if uww>=2222 {
            txt.print("ok >= 8l\n")
        } else {
            txt.print("fail >= 8l\n")
        }

        ; some more special cases for signed comparison
        byte b2 = -100
        word w2 = -30000
        if b2 >= -127 {
            txt.print("ok >= eb1\n")
        } else {
            txt.print("fail >= eb1\n")
        }
        if b2 >= -101 {
            txt.print("ok >= eb2\n")
        } else {
            txt.print("fail >= eb2\n")
        }
        if b2 >= -100 {
            txt.print("ok >= eb3\n")
        } else {
            txt.print("fail >= eb3\n")
        }
        if b2 >= -99 {
            txt.print("fail >= eb4\n")
        } else {
            txt.print("ok >= eb4\n")
        }
        if b2 >= 100 {
            txt.print("fail >= eb5\n")
        } else {
            txt.print("ok >= eb5\n")
        }
        if b2 >= 127 {
            txt.print("fail >= eb6\n")
        } else {
            txt.print("ok >= eb6\n")
        }
        if b2 >= 0 {
            txt.print("fail >= eb7\n")
        } else {
            txt.print("ok >= eb7\n")
        }

        if w2 >= -32768 {
            txt.print("ok >= ew1\n")
        } else {
            txt.print("fail >= ew1\n")
        }
        if w2 >= -30001 {
            txt.print("ok >= ew2\n")
        } else {
            txt.print("fail >= ew2\n")
        }
        if w2 >= -30000 {
            txt.print("ok >= ew3\n")
        } else {
            txt.print("fail >= ew3\n")
        }
        if w2 >= -29999 {
            txt.print("fail >= ew4\n")
        } else {
            txt.print("ok >= ew4\n")
        }
        if w2 >= 30000 {
            txt.print("fail >= ew5\n")
        } else {
            txt.print("ok >= ew5\n")
        }
        if w2 >= 32767 {
            txt.print("fail >= ew6\n")
        } else {
            txt.print("ok >= ew6\n")
        }
        if w2 >= 0 {
            txt.print("fail >= ew7\n")
        } else {
            txt.print("ok >= ew7\n")
        }
    }
}
