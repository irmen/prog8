%import textio
%import test_stack

main {

    sub start() {
        test_stack.test()

        ubyte x1 = 10
        ubyte x2 = 20
        ubyte x3 = 30

        x1 += x2+x3         ; TODO WHY SLOW EVAL????
        x1 += x2-x3         ; TODO WHY SLOW EVAL????

        txt.print_ub(x1)

        test_stack.test()

        repeat {
        }

    }
}
