%import textio

main {
    sub start() {
        ubyte @shared s = 7


        for i in 20 to 50 step s {
            txt.print_ub(i)
            txt.chrout(',')
        }
        txt.nl()
    }
}
