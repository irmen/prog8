%import textio2
%import test_stack
%zeropage basicsafe

main {
    sub start() {
        %asminclude "fozsdfsdf.asm"

        txt.print("ok")
        test_stack.test()
    }
}
