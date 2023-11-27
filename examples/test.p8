%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        txt.print("enter number: ")
        str buffer = "???????????????????????????"
        void txt.input_chars(buffer)
        float value = floats.parse_f(buffer)
        txt.nl()
        floats.print_f(value)
        txt.nl()
    }
}
