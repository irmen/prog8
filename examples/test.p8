%option no_sysinit
%zeropage dontuse


main {
    sub start() {
        str @alignword @shared name1 = "abc123456789"
        str @alignpage @shared name2 = "def123456789"
        str @alignword @shared @nozp name3 = "ghi123456789"
        str @alignword @shared @nozp name4 = "jkl123456789"
        ubyte[9] @alignword @shared array1
        ubyte[9] @alignword @shared array2
        ubyte[9] @alignpage @shared array3
        ubyte[9] @alignword @shared array4
        ubyte[9] @alignword @shared array5
        ubyte[9] @alignword @shared array6
        ubyte[9] @alignword @shared array7
        ubyte[9] @alignword @shared array8
        ubyte[] @alignword @shared array9 = [1,2,3]
        ubyte[] @alignword @shared array10 = [1,2,3]
        ubyte[] @alignpage @shared array11 = [1,2,3]
        ubyte[] @alignpage @shared array12 = [1,2,3]
        ubyte[] @alignword @shared array13 = [1,2,3]
        ubyte[] @alignword @shared array14 = [1,2,3]
        ubyte[] @alignpage @shared array15 = [1,2,3]
        ubyte[] @alignpage @shared array16 = [1,2,3]
        uword[3] @alignword @split @shared array17
        uword[] @alignword @split @shared array18 = [1111,2222,3333]

        array9[2]++
        array10[2]++
        array11[2]++
        array12[2]++
        array13[2]++
        array14[2]++
        array15[2]++
        array16[2]++
        array17[2]++
        array18[2]++
        name1[2]++
        name2[2]++
        name3[2]++
        name4[2]++

        %align 2
        %align 3
        %align 1000
    }
}
