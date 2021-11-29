%import textio
%import test_stack

main {

    sub start() {
        test_stack.test()

        ubyte[50] xpos = 49 to 0 step -1
        ubyte[50] ypos = 49 to 0 step -1

        ubyte ball
        for ball in 0 to len(xpos)-1 {
            txt.print_ub(xpos[ball])
            txt.spc()
            txt.print_ub(ypos[ball])
            txt.nl()
        }

        ubyte @shared x1 = 10
        ubyte @shared x2 = 20
        ubyte @shared x3 = 30

        test_stack.test()

        repeat {
        }

    }
}
