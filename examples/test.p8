%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte ib
        uword iw
        ubyte xx

        xx=0

        for ib in 241 to 253 step 2 {
            txt.print_ub(ib)
            txt.nl()
            xx++
        }

        for ib in 10 downto 2 step -2 {
            txt.print_ub(ib)
            txt.nl()
            xx--
        }
        for ib in 6 downto 0 step -2 {
            txt.print_ub(ib)
            txt.nl()
            xx--
        }

        txt.print_ub(xx)
    }
}
