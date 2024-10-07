%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_ub(sys.sizeof_byte)
        txt.spc()
        txt.print_ub(sys.sizeof_word)
        txt.spc()
        txt.print_ub(sys.sizeof_float)
        txt.nl()
    }
}
