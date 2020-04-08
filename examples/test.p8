%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {
    sub start() {
        ubyte ubb = 44
        byte bbb=44
        uword uww = 4444
        word www = 4444
        float flt = 4.4

        A = ubb
        A = ubb as byte
        A = bbb
        A = bbb as ubyte

        str foo = "foo"
    }
}


