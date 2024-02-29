%import string
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        ubyte[] barray = [11,22,33]
        uword[] warray = [1111,2222,3333]

        if any(barray)
            cx16.r0++


        if barray[2] == 33
            cx16.r0++
        else
            cx16.r1++

        if warray[2] == 3333
            cx16.r0++
        else
            cx16.r1++

        if barray[cx16.r0L] == 33
            cx16.r0++
        else
            cx16.r1++

        if warray[cx16.r0L] == 3333
            cx16.r0++
        else
            cx16.r1++
    }
}

