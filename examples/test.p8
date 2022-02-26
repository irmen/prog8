%import textio

main {
    sub start() {
        ubyte cc
        for cc in 32 to 124 {
            txt.chrout(cc)
        }
        txt.waitkey()

        txt.clear_screen()
        txt.print("\nHello!\nWorld\n")

        repeat {
        }
    }
}
