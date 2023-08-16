%import textio
%import string
%import floats
%zeropage basicsafe

main {
    sub start() {
        float fl = floats.parse_f("-123.45678e20")
        floats.print_f(fl)
    }
}
