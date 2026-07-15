%import textio
%import graphics

main {

    sub start() {
        str buffer = "?"*80

        repeat 60
            sys.waitvsync()

        txt.print("Enter your name: ")
        txt.print_ub(txt.input_chars(buffer))
        txt.nl()
        txt.print("Hello, ")
        txt.print(buffer)
        txt.print("!\n")
    }
}
