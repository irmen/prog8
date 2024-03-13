%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[3] uwarray = [1111,2222,3333]
        uword[3] @split uwarray_s = [1111,2222,3333]
        ubyte[3] array = [11,22,33]

        rol(array[1])
        array[1] <<=1
        ror(array[1])
        array[1] >>=1

        rol(uwarray[1])
        uwarray[1] <<=1
        ror(uwarray[1])
        uwarray[1] >>=1

        rol(uwarray_s[1])
        uwarray_s[1] *=3
        ror(uwarray_s[1])
        uwarray_s[1] *=3

    }
}
