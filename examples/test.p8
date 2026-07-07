%import textio

main {
    sub start() {

            txt.print_l(sys.MAX_LONG)
            txt.spc()
            txt.print_ulhex(sys.MAX_LONG, true)
            txt.nl()
            txt.print_l(sys.MIN_LONG)
            txt.spc()
            txt.print_ulhex(sys.MIN_LONG, true)
            txt.nl()


        txt.print("progstart: ")
        txt.print_ulhex(sys.progstart(), true)
        txt.nl()
        txt.print("progend: ")
        txt.print_ulhex(sys.progend(), true)
        txt.nl()
        txt.print("sizeof pointer: ")
        txt.print_ub(sys.SIZEOF_POINTER)
        txt.nl()

        txt.print("Hello from Prog8 on a m68000 system!\n")
    }
}
