%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        word x1 = -118
        floats.print(x1 as float)
        txt.nl()
        floats.print(x1 as float/1.9)
        txt.nl()
        xf1 = x1/1.9
        floats.print(xf1)
        txt.nl()

        float @shared xf1 = -118
        floats.print(xf1/1.9)
        txt.nl()
    }
}

