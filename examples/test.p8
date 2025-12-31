%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

    sub start()  {
        sys.save_prog8_internals()
        sys.restore_prog8_internals()

        txt.print_uwhex(sys.progstart(), true)
        txt.nl()
        txt.print_uwhex(sys.progend(), true)
        txt.nl()

    }
}
