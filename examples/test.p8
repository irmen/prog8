%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = -44556677

        txt.print_l(abs(lv))

        lv=abs(lv)
        lv=clamp(lv, 1, 100000)
        lv = max(lv, 100000)
        lv = min(lv, 100000)

        pokel(10000, 12345678)
        lv = peekl(10000)
    }
}
