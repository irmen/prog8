%import custom
%import textio

main {
    sub start() {
        custom.grab_system()
        bool pal = custom.isPAL
        custom.restore_system()

        txt.print("pal? ")
        txt.print_bool(pal)
        txt.nl()
    }
}
