%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword[] @nosplit ptrs = [one, two, three]

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
