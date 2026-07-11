%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        str input = "?"*80
        txt.print("Enter your name: ")
        ubyte length=txt.input_chars(input)
        txt.print("\nHello, ")
        txt.print(input)
        txt.print("!\n")
        txt.print_ub(length)
        txt.nl()
    }
}
