%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
%import textio

main {
    struct element {
        ubyte type
        long  x
        word  w
    }


    sub start() {
        word w = -1111
        long l = -11111111
        ^^element myElement = [1, -11111111, -1111]

        txt.print_w(w)
        txt.spc()
        w >>= 4
        txt.print_w(w)
        txt.spc()
        w <<= 4
        txt.print_w(w)
        txt.nl()

        txt.print_w(myElement.w)
        txt.spc()
        myElement.w >>= 4
        txt.print_w(myElement.w)
        txt.spc()
        myElement.w <<= 4
        txt.print_w(myElement.w)
        txt.nl()

        txt.nl()
        txt.print_l(l)
        txt.spc()
        l >>= 4
        txt.print_l(l)
        txt.spc()
        l <<= 4
        txt.print_l(l)
        txt.nl()
        txt.print_l(myElement.x)
        txt.spc()
        myElement.x >>= 4
        txt.print_l(myElement.x)
        txt.spc()
        myElement.x <<= 4
        txt.print_l(myElement.x)
        txt.nl()
    }
}
