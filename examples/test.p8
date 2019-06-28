%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ; TODO array to heap   ubyte[100] arr1 = 1 to 100
        ; TODO array to heap ubyte[100] arr2 = 101 to 200


        &ubyte m1 = $d020
        &uword mw1 = $c000

        ubyte[] arr1 = [1,2,3,4,5,6,7,8,9,10,11]
        ubyte[] arr2 = [11,22,33,44,55,66,77,88,99,100,101]

        word w1 = 1111
        word w2 = 2222

        m1 = 0
        mw1 = 65535

        Y = @($d020)
        @($d020) = A

        ror(w1)
        ror2(w1)
        rol(w1)
        rol2(w1)
        lsr(w1)
        lsl(w1)

        swap(w1, w2)
        swap(A, Y)
        swap(arr1[4], arr2[9])
        ; TODO swap(arr1[4], Y)
        ; TODO swap(Y, arr2[9])
        swap(@($d020), @($d021))
    }
}
