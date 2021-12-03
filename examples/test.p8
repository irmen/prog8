%import textio
%import test_stack
%import diskio
%zeropage basicsafe

main {

    sub start() {
        test_stack.test()

        str filename="...................."
        uword length

        txt.print("filename? ")
        txt.input_chars(filename)
        txt.nl()

        txt.print("loading at $1000...")
        length = diskio.load(8, filename, $1000)
        txt.print_uw(length)
        txt.nl()

        txt.print("raw loading at $4000...")
        length = diskio.load_raw(8, filename, $4000)
        txt.print_uw(length)
        txt.nl()

        test_stack.test()
    }
}
