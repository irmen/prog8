%import textio
%zeropage basicsafe

main {

    sub start() {
        long @shared lv = $12345678

        ubyte l,m,h = lmh(lv+99999)

        txt.print_ubhex(h, true)
        txt.spc()
        txt.print_ubhex(m, false)
        txt.spc()
        txt.print_ubhex(l, false)
        txt.nl()


        ubyte @shared num = 230
        ubyte @shared div = 13

        ubyte d,r = divmod(num, div)
        txt.print_ub(d)
        txt.spc()
        txt.print_ub(r)
        txt.nl()
    }
}
