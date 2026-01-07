%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv

        ^^long @nozp lptr = 2000
        ^^long @nozp lptr2 = 2000

        lv = lptr^^
        lptr2^^ = lptr^^
    }
}
