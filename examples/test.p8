%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start() {

        test_stack.test()
    }
}
