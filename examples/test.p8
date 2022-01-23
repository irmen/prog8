%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        ubyte ccc
        ubyte @shared qq = string.find("irmendejong", ccc)!=0
        word ww
        ww = calculate(6)
        txt.print_w(ww)
        txt.nl()
        ww = calculate(8)
        txt.print_w(ww)
        txt.nl()
        ww = calculate(10)
        txt.print_w(ww)
        txt.nl()
        qq = string.find("irmendejong", ccc)!=0
    }

    sub calculate(ubyte row) -> word {
        return 8-(row as byte)
    }
}
