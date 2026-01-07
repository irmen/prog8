%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv
        word @shared wv
        byte @shared bv

        ^^long lptr = 2000
        ^^long lptr2 = 2000
        ^^word wptr = 3000
        ^^word wptr2 = 3000
        ^^byte bptr = 4000
        ^^byte bptr2 = 4000

        lv = lptr^^
        lptr2^^ = lv
        lptr2^^ = lptr^^

        wv = wptr^^
        wptr2^^ = wv
        wptr2^^ = wptr^^

        bv = bptr^^
        bptr2^^ = bv
        bptr2^^ = bptr^^
        bptr2^^ = @(2000) as byte


    }
}
