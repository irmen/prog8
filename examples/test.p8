%import textio
%zeropage basicsafe

main {
    struct element {
        ubyte type
        long  x
        long  y
    }

    sub start() {
        ^^element myElement = $6000
        myElement.y = $44444444
        long @shared lv

        myElement.y -= lv

        txt.print_ulhex(myElement.y, true)
    }
}
