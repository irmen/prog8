%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte ubb = $f4
        byte bb = -123
        uword uww = $f4a1
        word ww = -12345

        conv.str_ub0($0f)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_ub0($0f)
        txt.nl()
        txt.nl()

        conv.str_ub(ubb)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_ub(ubb)
        txt.nl()
        txt.nl()
        conv.str_ub(8)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_ub(8)
        txt.nl()
        txt.nl()

        conv.str_b(bb)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_b(bb)
        txt.nl()
        txt.nl()
        conv.str_b(-8)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_b(-8)
        txt.nl()
        txt.nl()

        conv.str_ubhex(ubb)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_ubhex(ubb,false)
        txt.nl()
        txt.nl()

        conv.str_ubbin(ubb)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_ubbin(ubb,false)
        txt.nl()
        txt.nl()

        conv.str_uwbin(uww)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_uwbin(uww, false)
        txt.nl()
        txt.nl()

        conv.str_uwhex(uww)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_uwhex(uww, false)
        txt.nl()
        txt.nl()

        conv.str_uw0(987)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_uw0(987)
        txt.nl()
        txt.nl()

        conv.str_uw(uww)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_uw(uww)
        txt.nl()
        txt.nl()
        conv.str_uw(7)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_uw(7)
        txt.nl()
        txt.nl()

        conv.str_w(ww)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_w(ww)
        txt.nl()
        txt.nl()

        conv.str_w(99)
        txt.print(conv.string_out)
        txt.nl()
        txt.print_w(99)
        txt.nl()
    }
}
