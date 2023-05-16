%import textio
%zeropage basicsafe

main {

    sub start() {
        word vfrom = $1000
        word vto = $1000

        word xx
        for xx in vfrom to vto step -1 {
            txt.print_w(xx)
            txt.spc()
        }
skip:
        txt.nl()
    }
}

