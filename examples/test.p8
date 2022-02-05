%import textio

main {
    sub start() {
        word w1 = -10
        byte bb = 2
        w1 -= bb-1
        txt.print_w(w1)
        txt.nl()

        sys.wait(999)
    }
}
