%import textio
%zeropage basicsafe

main {

    sub start() {
        uword[] uw_arr = [1111,2222,3333]
        word[] w_arr = [1111,2222,3333]

        ubyte ub = 42
        byte bb = -42
        ubyte ix = 2

        uw_arr[1] = ub
        w_arr[1] = bb

        txt.print_uw(uw_arr[1])
        txt.nl()
        txt.print_w(w_arr[1])
        txt.nl()

        uw_arr[ix] = ub
        w_arr[ix] = bb

        txt.print_uw(uw_arr[1])
        txt.nl()
        txt.print_w(w_arr[1])

    }
}
