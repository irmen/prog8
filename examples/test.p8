%import textio

main {

    sub start() {

        ubyte xx = 20
        routine(xx)
        xx++
        routine(xx)
        xx++
        routine(xx)

        repeat {
        }
    }

    sub routine(ubyte x) {
        txt.print_ub(x)
        txt.spc()
    }
}
