%import textio

main {
    sub start() {
        ubyte xx = 30
        byte cc

;        cc=0
;        30 |> sin8u |> cos8u |> cc
;        txt.print_ub(cc)
;        txt.nl()
;        cc=0
;        xx |> sin8u |> cos8u |> cc
;        txt.print_ub(cc)
;        txt.nl()
        100 |> cc
        txt.print_b(cc)
        txt.nl()
        -100 |> abs |> abs |> cc
        txt.print_b(cc)
        txt.nl()
        cc |> abs |> abs |> cc
        txt.print_b(cc)
        txt.nl()
        cc = -100
        cc |> abs |> abs |> cc
        txt.print_b(cc)
        txt.nl()

        repeat {
        }
    }
}
