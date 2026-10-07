%import textio

main {
    sub start() {
        ubyte @shared i = 1

        on  i goto (task1,task2,task3)
    }

    sub task1() {
        long @shared z1 = 11111
        txt.print("one ")
        txt.print_l(z1)
        txt.nl()
    }
    sub task2() {
        long @shared z2 = 22222
        txt.print("two ")
        txt.print_l(z2)
        txt.nl()
    }
    sub task3() -> bool {
        long @shared z3 = 33333
        txt.print("three ")
        txt.print_l(z3)
        txt.nl()
        return false
    }
}
