%import textio
%import strings
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1,lv2
        ^^long lptr = 20000


        lv2 = $aabbccdd
        lv1 = peekl(2000)
        lv2 = peekl(&lv2)

        txt.print_ulhex(lv2, true)
        txt.nl()

        lv2 = peekl(lptr)
        lv1 = lptr^^
    }
}
