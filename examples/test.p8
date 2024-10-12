%option no_sysinit, enable_floats
%zeropage basicsafe

main {
    sub start() {
        ubyte[] array = [1,2,3]
        ubyte[3] array2
        float[] flarray = [1.1, 2.2, 3.3]
        float[3] flarray2
        word[] warray = [-2222,42,3333]
        word[3] warray2
        str[] names = ["apple", "banana", "tomato"]
        str[3] names2

        ; 8 array assignments -> 8 arraycopies:
        array = [8,7,6]
        array = array2
        flarray = [99.9, 88.8, 77.7]
        flarray = flarray2
        warray = [4444,5555,6666]
        warray = warray2
        names = ["x1", "x2", "x3"]
        names = names2
    }
}
