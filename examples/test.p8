%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {
        uword xx = progend()
        txt.print_uwhex(xx, 1)
        txt.print_uwhex(progend(), 1)

        test_stack.test()
    }

}
