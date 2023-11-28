%import textio
%import floats
%import string
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str buffer = "???????????????????????????"
        repeat {
            txt.print("enter number: ")
            void txt.input_chars(buffer)
            txt.print("\nprog8's parse_f: ")
            float value = floats.parse_f(buffer)
            floats.print_f(value)
            txt.print("\nrom val_1: ")
            value = floats.VAL_1(buffer, string.length(buffer))
            floats.print_f(value)
            txt.nl()
        }
    }
}
