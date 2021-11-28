%import textio
%import test_stack

main {

    sub start() {
        test_stack.test()

        ubyte @shared x1 = 10
        ubyte @shared x2 = 20
        ubyte @shared x3 = 30

        test_stack.test()

        repeat {
        }

    }
}
