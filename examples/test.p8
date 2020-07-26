%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    sub start() {

        ubyte wv
        ubyte wv2

        wv *= wv2

        wv += 10
        wv += 20
        wv += 30

        wv += 1 + wv2
        wv += 2 + wv2
        wv += 3 + wv2

        wv += wv2 + 1
        wv += wv2 + 2
        wv += wv2 + 3

        wv = wv + 1 + wv2
        wv = wv + 2 + wv2
        wv = wv + 3 + wv2

        wv = 1 + wv2 + wv
        wv = 2 + wv2 + wv
        wv = 3 + wv2 + wv

        wv = wv  + wv2  + 1
        wv = wv  + wv2  + 2
        wv = wv  + wv2  + 3

        wv = wv2  + 1 + wv
        wv = wv2  + 2 + wv
        wv = wv2  + 3 + wv

        wv = wv2  + wv + 1
        wv = wv2  + wv + 2
        wv = wv2  + wv + 3
    }
}
