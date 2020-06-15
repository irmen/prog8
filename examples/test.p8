%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub jumpsub() {

        ; goto jumpsub        ; TODO fix compiler loop
        goto blabla
blabla:
        A=99
        return

    }

    sub start() {

        ubyte[] array = [1,2,3]

        ubyte[len(array)] bytesE = 22           ; TODO fix nullpointer error
        float[len(array)] floatsE = 3.33       ; TODO fix error


    }

}


