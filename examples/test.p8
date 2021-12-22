%import textio

main {
    sub start() {
        str input = "?" * 20
        c128.disable_basic()
        txt.lowercase()
        txt.print("Hello There! Enter Your Name: ")
        void txt.input_chars(input)
        txt.nl()
        repeat {
            txt.print(input)
            txt.spc()
        }
    }
}
