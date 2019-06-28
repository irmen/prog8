%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte[100] arr1
        ubyte[100] arr2

        word w1 = 1111
        word w2 = 2222

        swap(w1, w2)
        swap(A, Y)
        swap(arr1[10], arr2[20])
        swap(arr1[10], Y)
        swap(Y, arr2[10])
        swap(@($d020), @($d021))
    }
}
