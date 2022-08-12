%import textio
%zeropage basicsafe


main {
    sub start() {

        uword @zp flags_ptr = memory("flags", 200, 0)
        txt.print("calculating...\n")
        txt.print_uwhex(flags_ptr, true)
        txt.nl()

        repeat 10 {
            txt.print("new iter\n")
            txt.print_ub(@($06))
            sys.memset(flags_ptr, 200, 0)
        }

        txt.print("done\n")
    }
}
