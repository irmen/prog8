%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        ubyte[] blaat = 10 to 20

        for ubyte c in 'a' to 'z' {
            c64.CHROUT(blaat[c])
        }
    }

}
