%import textio
%zeropage basicsafe

main {

    sub start() {
        const long lv = $12345678

        ubyte l,m,h = lmh(lv+99999)     ; TODO should be const-folded and ALL THREE target vars should become consts too

        txt.print_ubhex(h, true)
        txt.spc()
        txt.print_ubhex(m, false)
        txt.spc()
        txt.print_ubhex(l, false)
        txt.nl()


        const ubyte num = 230
        const ubyte div = 13

        ubyte d,r = divmod(num, div)     ; TODO should be const-folded and BOTH target vars should become consts too
        txt.print_ub(d)
        txt.spc()
        txt.print_ub(r)
        txt.nl()
    }
}
