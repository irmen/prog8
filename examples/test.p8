%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    sub start() {
        ubyte xx = 10
        float ff = 4
        float ff2 = 4

;        xx /= 2
;
;        xx /= 3
;
;        xx *= 2
;        xx *= 3

        ;ff **= 2.0
        ;ff **= 3.0

        ff = ff2 ** 2.0
        ff = ff2 ** 3.0

;        xx = xx % 5
;        xx %= 5
    }

}


