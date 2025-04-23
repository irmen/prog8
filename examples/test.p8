%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword[128]  ptrs
        ptrs[0] = &one
        ptrs[1] = &two
        ptrs[2] = &three

        ubyte @shared x =1
        goto ptrs[x]
    }

    sub one() {
        txt.print("one\n")
    }
    sub two() {
        txt.print("two\n")
    }
    sub three() {
        txt.print("three\n")
    }

}
