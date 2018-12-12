%import c64utils

~ main {

    sub start()  {

        ubyte ub1
        byte b1
        uword uw1
        word  w1
        float f1

        ub1=0
        f1=ub1
        c64.FPRINTLN()
        ub1=255
        f1=ub1
        c64.FPRINTLN()
        c64.CHROUT('\n')

        b1=0
        f1=b1
        c64.FPRINTLN()
        b1=-123
        f1=b1
        c64.FPRINTLN()
        c64.CHROUT('\n')

        uw1 = 0
        f1 = uw1
        c64.FPRINTLN()
        uw1 = 1
        f1 = uw1
        c64.FPRINTLN()
        uw1 = 255
        f1 = uw1
        c64.FPRINTLN()
        uw1 = 32768
        f1 = uw1
        c64.FPRINTLN()
        uw1 = 65535
        f1 = uw1
        c64.FPRINTLN()
        uw1 = 0
        f1 = uw1
        c64.FPRINTLN()
        c64.CHROUT('\n')

        w1 = 1
        f1 = w1
        c64.FPRINTLN()
        w1 = 255
        f1 = w1
        c64.FPRINTLN()
        w1 = 32767
        f1 = w1
        c64.FPRINTLN()
        w1 = -32768
        f1 = w1
        c64.FPRINTLN()
        w1 = -1
        f1 = w1
        c64.FPRINTLN()
        w1 = -255
        f1 = w1
        c64.FPRINTLN()
        w1 = -256
        f1 = w1
        c64.FPRINTLN()
    }
}

