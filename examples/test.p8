%import textio

main {
    sub start() {
        ubyte xx = 30
        ubyte cc

        cc=0
        cc = 30 |> sin8u |> cos8u |> cc
        txt.print_ub(cc)
        txt.nl()
        cc=0
        cc = xx |> sin8u |> cos8u |> cc
        txt.print_ub(cc)
        txt.nl()

        repeat {
        }
    }
}
