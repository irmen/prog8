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

    sub func(ubyte arg) -> ubyte {
        c64.CHROUT(arg)
        return sin8(arg)
    }

    sub func2(ubyte arg) -> ubyte {
        return sin8(arg)
    }

    sub start() {
        ubyte[] a1 = 3 to 20

;        ubyte[18] a2 = 3 to 20
;        float[18] floats2 = 3 to 20
;        ubyte[11] bytes = 22
;        float[11] floats = 3.33
;
;        float[] array = [1, 1+1, -33]
;        byte[] array2 = [1, 1+1, -33, -2]

        ;   float[] array2 = 42    ; TODO nice error
        ;ubyte[len(array)] bytesE = 22           ; TODO fix error
        ;float[len(array2)] floatsE = 3.33       ; TODO fix error

;        const ubyte q = 123
;        byte bbb
;        float ff
;
;        A = bytes[1]
;        ff = array[1]
;        A = len(array)
;        A = len(array2)
;        str sss = "zzz"
;        str x = "zxcvzxcv"
;
;        ff = 1234.44 + 99.0
;        float ff2 = -3.3333
;        ff=ff2*2
;        A = 123+22+11
;        bbb = -99
;
;        A = q
;        A = func(1+q)
;        func(1+q)
;
;        Y=99
;        A = func2(1+q)
;        func2(1+q)
;
;        x = @(&sss)
    }

}


