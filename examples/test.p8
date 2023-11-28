%import textio

main {
    sub start() {
        uword size1 = sizeof(22222)
        txt.print_uw(size1)
        txt.nl()
        uword size2 = sizeof(2.2)
        txt.print_uw(size2)
        txt.nl()
        cx16.r0 = sizeof(22222)
        txt.print_uw(cx16.r0)
        txt.nl()
        cx16.r0 = sizeof(2.2)
        txt.print_uw(cx16.r0)
        txt.nl()
    }
}
