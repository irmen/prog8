%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        txt.lowercase()
        txt.print_ub(txt.width())
        txt.chrout('\n')
        txt.print_ub(txt.height())
        txt.chrout('\n')
        test_stack.test()
    }

}
