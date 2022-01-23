%import textio
%import floats

main {
    ubyte[4] @shared @requirezp arr1 = [1,2,3,4]
    uword[2] @shared @requirezp arr2 = [111,222]
    float[2] @shared @requirezp arr3 = [1.111, 2.222]
    sub start() {
        txt.print_ub(arr1[3])
        txt.spc()
        txt.print_uw(arr2[1])
        txt.spc()
        floats.print_f(arr3[1])
        repeat {
        }
    }
}

sprites {
    word[3] sprites_x = [111,222,333]
    word[3] sprites_y = [666,777,888]
}
