%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte[100] arr1
        ubyte[100] arr2

        memcopy(arr1, arr2, len(arr2))

    }

}
