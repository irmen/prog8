%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        test_stack.test()

    }

}
