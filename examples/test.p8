 %import textio

main {

    sub start() {
        %ir {{
            nop
        }}
        ; should flow into next statements

        ubyte aa = 42
        ubyte bb = 99
        aa += bb
        txt.print_ub(aa)
        txt.spc()
        aa += bb
        txt.print_ub(aa)
        txt.spc()
        aa += bb
        txt.print_ub(aa)
        txt.nl()
    }
}
