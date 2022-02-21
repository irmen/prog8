%import textio

main {
    sub start() {
        ubyte xx = 30
        ubyte cc

;        cc = 30 |> sin8u |> cos8u
;        txt.print_ub(cc)
;        txt.nl()
        cc = xx |> sin8u |> cos8u
        txt.print_ub(cc)
        txt.nl()

        repeat {
        }
    }
}
