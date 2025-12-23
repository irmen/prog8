%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        long @shared lv = 1234567
        float @shared fl1 = lv as float / 1.234
        float @shared fl2 = (cx16.r0r1sl - cx16.r2r3sl) as float
        float @shared fl3 = -lv as float

        txt.print_f(fl1)
        txt.nl()
        txt.print_f(fl3)
    }
}
