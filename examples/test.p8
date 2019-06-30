%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte[10] arr1 = [1,2,3,4,5,6,7,8,9,10]
        ubyte[] arr2 = [1,2,3,4,5,6,7,8,9,10]
        ubyte[] arr1h = 1 to 10
        ubyte[10] arr2h = 1 to 10



;        swap(w1, w2)
;        swap(A, Y)
;        swap(arr1[4], arr2[9])
;        ; TODO swap(arr1[4], Y)
;        ; TODO swap(Y, arr2[9])
;        swap(@($d020), @($d021))
    }
}
