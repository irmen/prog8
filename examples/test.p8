%import textio
%zeropage basicsafe

main {
    sub start() {
        long qq
        long @shared iters = 77777

        repeat 77777 {
            qq++
        }
        txt.print_l(qq)
        txt.nl()

        repeat iters {
            qq++
        }
        txt.print_l(qq)
        txt.nl()

        iters++
        repeat iters-10 {
            qq--
        }
        txt.print_l(qq)
        txt.nl()
        txt.print_bool(qq==77786)
        txt.nl()
    }
}
