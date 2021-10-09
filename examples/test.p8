%import textio
%import test_stack
%zeropage basicsafe

main {
    sub start() {
        txt.print("ok")
        test_stack.test()
    }
}
