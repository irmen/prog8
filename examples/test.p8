%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        str @alignword @shared name = "wordaligned"
        str @alignpage @shared @nozp name2 = "pagealigned"
        ubyte[20] @alignword @shared array1
        ubyte[20] @alignword @shared array2
        ubyte[20] @alignpage @shared array3
        ubyte[20] @alignpage @shared array4
        ubyte[] @alignword @shared array5 = [1,2,3,4]
        ubyte[] @alignword @shared array6 = [1,2,3,4]
        ubyte[] @alignpage @shared array7 = [1,2,3,4]
        ubyte[] @alignpage @shared array8 = [1,2,3,4]
        uword[20] @alignword @split @shared array9
        uword[] @alignword @split @shared array10 = [1111,2222,3333,4444]
    }
}
