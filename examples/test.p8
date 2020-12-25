%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {

        test_stack.test()
    }
}
