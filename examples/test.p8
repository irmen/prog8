%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    float[]  fa = [1,2,3,4]

    sub start() {
        wot("asdfasdf")
        wot("asdfasdf")
        wot("asdfasdf1")
    }

    sub wot(uword text) {
        ;c64scr.print(text)          ; TODO better type error
        ;c64.CHROUT('\n')
        c64scr.print_uwhex(text, 1)
        c64.CHROUT('\n')
    }
}
