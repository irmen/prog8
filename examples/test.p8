%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print("txt.print        : ")
        txt.print("hello")
        txt.nl()

        txt.print("txt.chrout       : ")
        txt.chrout('A')
        txt.nl()

        txt.print("txt.print_bool   : ")
        txt.print_bool(true)
        txt.nl()

        txt.print("txt.print_ub0    : ")
        txt.print_ub0(42)
        txt.nl()

        txt.print("txt.print_ub     : ")
        txt.print_ub(42)
        txt.nl()

        txt.print("txt.print_b      : ")
        txt.print_b(-42)
        txt.nl()

        txt.print("txt.print_ubhex  : ")
        txt.print_ubhex(255, true)
        txt.nl()

        txt.print("txt.print_ubbin  : ")
        txt.print_ubbin(255, true)
        txt.nl()

        txt.print("txt.print_uwbin  : ")
        txt.print_uwbin($11aa, true)
        txt.nl()

        txt.print("txt.print_uwhex  : ")
        txt.print_uwhex($11aa, true)
        txt.nl()

        txt.print("txt.print_ulhex  : ")
        txt.print_ulhex($1122aabb, true)
        txt.nl()

        txt.print("txt.print_uw0    : ")
        txt.print_uw0(40000)
        txt.nl()

        txt.print("txt.print_uw     : ")
        txt.print_uw(40000)
        txt.nl()

        txt.print("txt.print_w      : ")
        txt.print_w(-30000)
        txt.nl()

        txt.print("txt.print_l      : ")
        txt.print_l(-2000000000)
        txt.nl()
    }
}
